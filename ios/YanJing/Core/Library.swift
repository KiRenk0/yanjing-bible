import Foundation

/// A single actor owns the loaded corpora and ONNX session; no inference or TSV parsing runs on the UI thread.
actor Library {
    let root: URL
    private var cache: [String: [Verse]] = [:]
    private var semantic: SemanticSearch?
    private var latinMap: [VerseKey: [VerseKey]]?
    private var bridges: [String: String]?
    init(root: URL) { self.root = root }

    func verses(_ version: TextVersion) throws -> [Verse] {
        if let cached = cache[version.id] { return cached }
        let url = root.appendingPathComponent("bibles/\(version.id).tsv")
        let contents = try String(contentsOf: url, encoding: .utf8)
        let result = try contents.split(whereSeparator: \.isNewline).filter { !$0.hasPrefix("#") }.map { row -> Verse in
            let fields = row.split(separator: "\t", omittingEmptySubsequences: false)
            guard fields.count >= 5, let book = Int(fields[0]), let chapter = Int(fields[1]), let verse = Int(fields[2]) else { throw LibraryError.invalidData(version.id) }
            return Verse(key: VerseKey(book, chapter, verse), text: String(fields[3]), normalized: String(fields[4]), gloss: fields.count > 5 ? String(fields[5]) : "")
        }
        guard !result.isEmpty else { throw LibraryError.invalidData(version.id) }
        // Bound memory while keeping a few comparison versions resident.
        if cache.count >= 3 { cache.removeAll(keepingCapacity: true) }
        cache[version.id] = result
        return result
    }

    func chapter(_ version: TextVersion, book: Int, chapter: Int) throws -> [Verse] {
        try verses(version).filter { $0.key.book == book && $0.key.chapter == chapter }
    }

    func search(_ raw: String, version: TextVersion, modes: Set<SearchMode>, testament: Int) throws -> SearchResult {
        try Task.checkCancellation()
        let all = try verses(version)
        let corpus = all.filter { verse in
            guard version.group == "bible", testament != 0 else { return true }
            return testament == 1 ? verse.key.book < (version.id == "vulgate" ? 147 : 40) : verse.key.book >= (version.id == "vulgate" ? 147 : 40)
        }
        if let ref = try ReferenceParser.parse(raw, version: version, root: root) {
            let matches = corpus.filter { $0.key.book == ref.book && $0.key.chapter == ref.chapter && (ref.start == nil || ($0.key.verse >= ref.start! && $0.key.verse <= (ref.end ?? ref.start!))) }
            if !matches.isEmpty || ref.start == nil { return SearchResult(hits: matches.map { .init(verse: $0, modes: [], score: 1) }, semanticUnavailable: false) }
            if let start = ref.start, start == ref.end, let verse = try resolved(version, key: VerseKey(ref.book, ref.chapter, start)) {
                return SearchResult(hits: [.init(verse: verse, modes: [], score: 1)], semanticUnavailable: false)
            }
            return SearchResult(hits: [], semanticUnavailable: false)
        }
        let query = TextTools.normalize(raw, arabic: version.isRTL, latin: version.id == "vulgate")
        guard !query.isEmpty, raw.count <= 200 else { return SearchResult(hits: [], semanticUnavailable: false) }
        var scores: [VerseKey: Double] = [:]
        var hitsByKey: [VerseKey: Verse] = [:]
        var matches: [VerseKey: Set<SearchMode>] = [:]
        func add(_ verse: Verse, mode: SearchMode, score: Double) {
            scores[verse.key, default: 0] += score
            hitsByKey[verse.key] = verse
            matches[verse.key, default: []].insert(mode)
        }
        if modes.contains(.keyword) || modes.contains(.expression) {
            let queryTokens = TextTools.tokens(query)
            for (i, verse) in corpus.enumerated() {
                if i % 128 == 0 { try Task.checkCancellation() }
                let normalized = verse.normalized
                let exact = normalized.contains(query)
                if modes.contains(.keyword), exact { add(verse, mode: .keyword, score: 2) }
                if modes.contains(.expression) {
                    let candidate = TextTools.tokens(normalized + TextTools.normalize(verse.gloss))
                    let overlap = Double(queryTokens.intersection(candidate).count) / Double(max(1, queryTokens.count))
                    if exact || (queryTokens.count >= 2 && overlap >= 0.55) { add(verse, mode: .expression, score: exact ? 2 : overlap * 0.4) }
                }
            }
            if modes.contains(.expression) {
                let file = root.appendingPathComponent("expressions.json")
                let groups = try JSONDecoder().decode([Expression].self, from: Data(contentsOf: file))
                let byKey = Dictionary(uniqueKeysWithValues: corpus.map { ($0.key, $0) })
                for group in groups where group.version == version.index || (version.group == "bible" && group.version == "bible") {
                    if group.phrases.contains(where: { let p = TextTools.normalize($0); return query.count >= 2 && (query.contains(p) || p.contains(query)) }) {
                        for encoded in group.keys { if let key = VerseKey(encoded: encoded), let verse = byKey[key] { add(verse, mode: .expression, score: 2) } }
                    }
                }
            }
        }
        var unavailable = false
        if modes.contains(.semantic) {
            do {
                if semantic == nil { semantic = try SemanticSearch(root: root) }
                let candidates = try semantic!.nearest(raw, index: version.index)
                let byKey = Dictionary(uniqueKeysWithValues: corpus.map { ($0.key, $0) })
                let mapping = version.id == "vulgate" ? try latinMappings() : [:]
                var reverse: [VerseKey: [VerseKey]] = [:]
                for (key, values) in mapping { for value in values { reverse[value, default: []].append(key) } }
                for (rank, candidate) in candidates.enumerated() {
                    let keys = version.id == "vulgate" ? reverse[candidate.0, default: []] : [candidate.0]
                    for key in keys { if let verse = byKey[key] { add(verse, mode: .semantic, score: 0.7 / (1 + Double(rank) * 0.05)) } }
                }
            } catch is CancellationError { throw CancellationError() }
            catch { unavailable = true }
        }
        try Task.checkCancellation()
        let ranked = scores.keys.sorted { scores[$0]! == scores[$1]! ? $0.encoded < $1.encoded : scores[$0]! > scores[$1]! }.prefix(80)
        return SearchResult(hits: ranked.compactMap { key in hitsByKey[key].map { .init(verse: $0, modes: matches[key, default: []], score: scores[key]!) } }, semanticUnavailable: unavailable)
    }

    private func latinMappings() throws -> [VerseKey: [VerseKey]] {
        if let latinMap { return latinMap }
        let contents = try String(contentsOf: root.appendingPathComponent("latin/mapping.tsv"), encoding: .utf8)
        var result: [VerseKey: [VerseKey]] = [:]
        for row in contents.split(whereSeparator: \.isNewline) where !row.hasPrefix("#") {
            let fields = row.split(separator: "\t")
            if fields.count == 2, let key = VerseKey(encoded: String(fields[0])) { result[key] = fields[1].split(separator: "|").compactMap { VerseKey(encoded: String($0)) } }
        }
        latinMap = result
        return result
    }

    func resolved(_ version: TextVersion, key: VerseKey) throws -> Verse? {
        if bridges == nil { bridges = try JSONDecoder().decode([String: String].self, from: Data(contentsOf: root.appendingPathComponent("bridges.json"))) }
        var actual = key
        if ["cuvs", "cuvt"].contains(version.id), let mapped = bridges?[key.encoded], let bridge = VerseKey(encoded: mapped) { actual = bridge }
        if version.id == "wenli", key == VerseKey(40, 6, 34) { actual = VerseKey(40, 6, 33) }
        return try verses(version).first { $0.key == actual }
    }

    func comparisons(_ version: TextVersion, key: VerseKey) throws -> [Comparison] {
        guard !version.isClassical else { return [] }
        let mapping = try latinMappings()
        let canonical = version.id == "vulgate" ? mapping[key]?.first : key
        return try TextVersion.all.filter { $0.group == version.group }.map { peer in
            let requested: VerseKey?
            if peer.id == "vulgate" { requested = version.id == "vulgate" ? key : mapping.keys.sorted { $0.encoded < $1.encoded }.first { mapping[$0]!.contains(key) } }
            else { requested = canonical }
            return Comparison(version: peer, verse: try requested.flatMap { try resolved(peer, key: $0) })
        }
    }
}

struct Comparison: Identifiable, Sendable { let version: TextVersion; let verse: Verse?; var id: String { version.id } }
private struct Expression: Codable { let version: String; let phrases: [String]; let keys: [String] }

enum TextTools {
    static func simplified(_ text: String) -> String { text.precomposedStringWithCompatibilityMapping.applyingTransform(StringTransform("Traditional-Simplified"), reverse: false) ?? text }
    static func normalize(_ text: String, arabic: Bool = false, latin: Bool = false) -> String {
        var value = simplified(text).lowercased()
        if arabic || latin { value = value.folding(options: [.diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX")) }
        if latin { value = value.replacingOccurrences(of: "æ", with: "ae").replacingOccurrences(of: "œ", with: "oe") }
        if arabic { for c in ["ٱ", "أ", "إ", "آ"] { value = value.replacingOccurrences(of: c, with: "ا") }; value = value.replacingOccurrences(of: "ى", with: "ي") }
        return String(value.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) && (!arabic || !["ـ", "ۥ", "ۦ"].contains(String($0))) })
    }
    static func tokens(_ text: String) -> Set<String> {
        let chars = Array(text)
        guard chars.count > 1 else { return Set(chars.map(String.init)) }
        return Set((0..<chars.count - 1).map { String(chars[$0...$0+1]) })
    }
}
