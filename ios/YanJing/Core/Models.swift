import Foundation

struct VerseKey: Hashable, Codable, Sendable {
    let book: Int
    let chapter: Int
    let verse: Int
    var encoded: String { "\(book):\(chapter):\(verse)" }
    init(_ book: Int, _ chapter: Int, _ verse: Int) { self.book = book; self.chapter = chapter; self.verse = verse }
    init?(encoded: String) {
        let parts = encoded.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 3, parts.allSatisfy({ $0 > 0 }) else { return nil }
        self.init(parts[0], parts[1], parts[2])
    }
}

struct Verse: Hashable, Codable, Identifiable, Sendable {
    let key: VerseKey
    let text: String
    let normalized: String
    let gloss: String
    var id: String { key.encoded }
}

struct TextVersion: Identifiable, Hashable, Codable, Sendable {
    let id: String
    let titleKey: String
    let group: String
    let defaultBook: Int
    let index: String
    let credit: String
    var isRTL: Bool { id == "quran_ar" }
    var isClassical: Bool { !["bible", "quran"].contains(group) }
    static let all: [TextVersion] = [
        .init(id: "cuvs", titleKey: "version.cuvs", group: "bible", defaultBook: 43, index: "verses", credit: "Chinese Union Version · Scrollmapper · Public domain"),
        .init(id: "cuvt", titleKey: "version.cuvt", group: "bible", defaultBook: 43, index: "verses", credit: "Chinese Union Version · Scrollmapper · Public domain"),
        .init(id: "wenli", titleKey: "version.wenli", group: "bible", defaultBook: 43, index: "verses", credit: "Chinese Union Version (Literary) · Scrollmapper · Public domain"),
        .init(id: "kjv", titleKey: "version.kjv", group: "bible", defaultBook: 43, index: "verses", credit: "King James Version · Scrollmapper · Public domain"),
        .init(id: "vulgate", titleKey: "version.vulgate", group: "bible", defaultBook: 150, index: "verses", credit: "Vulgata Clementina · Scrollmapper · Public domain; CrossWire versification · GPL-2.0-only"),
        .init(id: "quran_zh", titleKey: "version.quran_zh", group: "quran", defaultBook: 67, index: "quran", credit: "Muhammad Ma Jian · QuranEnc.com v1.0.2 · https://quranenc.com/zh/browse/chinese_makin"),
        .init(id: "quran_ar", titleKey: "version.quran_ar", group: "quran", defaultBook: 67, index: "quran", credit: "Tanzil Project · Uthmani v1.1 · CC BY 3.0 · https://tanzil.net"),
        .init(id: "analects", titleKey: "version.analects", group: "confucian", defaultBook: 68, index: "analects", credit: "chinese-poetry/chinese-poetry · MIT"),
        .init(id: "daxue", titleKey: "version.daxue", group: "confucian", defaultBook: 69, index: "daxue", credit: "chinese-poetry/chinese-poetry · MIT"),
        .init(id: "zhongyong", titleKey: "version.zhongyong", group: "confucian", defaultBook: 70, index: "zhongyong", credit: "chinese-poetry/chinese-poetry · MIT"),
        .init(id: "mengzi", titleKey: "version.mengzi", group: "confucian", defaultBook: 71, index: "mengzi", credit: "chinese-poetry/chinese-poetry · MIT"),
        .init(id: "heart_sutra", titleKey: "version.heart_sutra", group: "buddhist", defaultBook: 72, index: "heart_sutra", credit: "Rushi scriptures · CC0"),
        .init(id: "diamond_sutra", titleKey: "version.diamond_sutra", group: "buddhist", defaultBook: 73, index: "diamond_sutra", credit: "Rushi scriptures · CC0"),
        .init(id: "lotus_sutra", titleKey: "version.lotus_sutra", group: "buddhist", defaultBook: 76, index: "lotus_sutra", credit: "CBETA T0262 · Kumārajīva · CC BY-NC-SA 3.0 TW · https://cbeta.org/copyright.php"),
        .init(id: "tao_te_ching", titleKey: "version.tao_te_ching", group: "taoist", defaultBook: 74, index: "tao_te_ching", credit: "Chinese-Classical-Literature-Android · MIT"),
        .init(id: "zhuangzi", titleKey: "version.zhuangzi", group: "taoist", defaultBook: 75, index: "zhuangzi", credit: "Chinese-Classical-Literature-Android · MIT")
    ]
    static func find(_ id: String) -> TextVersion { all.first { $0.id == id } ?? all[0] }
}

struct BookInfo: Codable, Identifiable, Sendable {
    let id: Int
    let zh: String
    let traditional: String
    let en: String
    let aliases: [String]
    let chapters: [String]
}

enum SearchMode: String, CaseIterable, Codable, Identifiable, Sendable {
    case semantic, expression, keyword
    var id: String { rawValue }
    var titleKey: String { "mode.\(rawValue)" }
}

struct SearchHit: Identifiable, Sendable {
    let verse: Verse
    let modes: Set<SearchMode>
    let score: Double
    var id: String { verse.id }
}

struct SearchResult: Sendable { let hits: [SearchHit]; let semanticUnavailable: Bool }

struct SavedPassage: Identifiable, Codable, Sendable {
    let versionID: String
    let verse: Verse
    let date: Date
    var id: String { "\(versionID)/\(verse.id)" }
}

enum LibraryError: Error { case missingResource(String), invalidData(String) }
