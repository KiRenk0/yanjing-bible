import Foundation

struct PassageReference { let book: Int; let chapter: Int; let start: Int?; let end: Int? }

enum ReferenceParser {
    static func parse(_ raw: String, version: TextVersion, root: URL) throws -> PassageReference? {
        let books = try JSONDecoder().decode([BookInfo].self, from: Data(contentsOf: root.appendingPathComponent("books.json")))
        var input = TextTools.simplified(raw).lowercased().replacingOccurrences(of: "《", with: "").replacingOccurrences(of: "》", with: "")
        input = input.replacingOccurrences(of: "[\\s·]", with: "", options: .regularExpression)
        input = input.replacingOccurrences(of: "[–—－]", with: "-", options: .regularExpression)
        let numbers = try NSRegularExpression(pattern: "[〇零一二三四五六七八九十百两]+")
        for match in numbers.matches(in: input, range: NSRange(input.startIndex..., in: input)).reversed() {
            guard let range = Range(match.range, in: input), let value = chineseNumber(String(input[range])) else { continue }
            // Convert chapter numerals after 第, never numerals embedded in a title.
            if range.lowerBound > input.startIndex, input[input.index(before: range.lowerBound)] == "第" { input.replaceSubrange(range, with: String(value)) }
        }
        let allowed = books.filter { book in
            if version.isClassical || version.group == "quran" { return book.id == version.defaultBook }
            return version.id == "vulgate" ? (101...173).contains(book.id) : (1...66).contains(book.id)
        }
        for book in allowed {
            let aliases = (book.aliases + [book.zh, book.traditional, book.en]).map { TextTools.simplified($0).lowercased().replacingOccurrences(of: " ", with: "") }.sorted { $0.count > $1.count }
            var remainder: String?
            for alias in aliases where input.hasPrefix(alias) { remainder = String(input.dropFirst(alias.count)); break }
            if remainder == nil, version.isClassical {
                for (i, title) in book.chapters.enumerated() where !title.isEmpty {
                    let normalized = TextTools.simplified(title).lowercased()
                    if input.hasPrefix(normalized) {
                        let tail = String(input.dropFirst(normalized.count)).replacingOccurrences(of: "篇", with: "")
                        if tail.isEmpty { return .init(book: book.id, chapter: i + 1, start: nil, end: nil) }
                        if let parts = numeric(tail), let start = parts.0 { return .init(book: book.id, chapter: i + 1, start: start, end: parts.1 ?? start) }
                    }
                }
            }
            if remainder == nil, version.isClassical || version.group == "quran" { remainder = input }
            guard var rest = remainder else { continue }
            rest = rest.replacingOccurrences(of: "第", with: "")
            rest = rest.replacingOccurrences(of: "(?:章|篇|品|分)(?=\\d)", with: ":", options: .regularExpression)
            rest = rest.replacingOccurrences(of: "[章节篇品分段]$", with: "", options: .regularExpression)
            let pattern = try NSRegularExpression(pattern: "^(\\d+)(?::(\\d+)(?:-(\\d+))?)?$")
            guard let match = pattern.firstMatch(in: rest, range: NSRange(rest.startIndex..., in: rest)) else { continue }
            func number(_ group: Int) -> Int? { Range(match.range(at: group), in: rest).flatMap { Int(rest[$0]) } }
            guard let chapter = number(1) else { return .init(book: book.id, chapter: -1, start: nil, end: nil) }
            let start = number(2), end = number(3) ?? start
            if (match.range(at: 2).location != NSNotFound && start == nil) || (match.range(at: 3).location != NSNotFound && number(3) == nil) { return .init(book: book.id, chapter: -1, start: nil, end: nil) }
            // Invalid recognized references return no results instead of falling through to lexical search.
            guard chapter > 0, start.map({ $0 > 0 }) ?? true, end.map({ $0 >= (start ?? 1) }) ?? true else { return .init(book: book.id, chapter: -1, start: nil, end: nil) }
            return .init(book: book.id, chapter: chapter, start: start, end: end)
        }
        return nil
    }
    private static func numeric(_ text: String) -> (Int?, Int?)? {
        let parts = text.split(separator: "-", omittingEmptySubsequences: false)
        guard (1...2).contains(parts.count), let start = Int(parts[0]), start > 0 else { return nil }
        let end = parts.count == 2 ? Int(parts[1]) : start
        guard let end, end >= start else { return nil }
        return (start, end)
    }
    private static func chineseNumber(_ text: String) -> Int? {
        let digits: [Character: Int] = ["〇": 0, "零": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9]
        var value = 0, current = 0
        for character in text {
            if let digit = digits[character] { current = digit }
            else if character == "十" { value += max(current, 1) * 10; current = 0 }
            else if character == "百" { value += max(current, 1) * 100; current = 0 }
            else { return nil }
        }
        return value + current
    }
}
