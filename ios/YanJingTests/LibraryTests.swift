import XCTest
@testable import YanJing

final class LibraryTests: XCTestCase {
    private var root: URL { Bundle.main.resourceURL!.appendingPathComponent("Corpus") }
    func testAllBundledTextsAreCompleteAndKeepSourceNumbering() async throws {
        let library = Library(root: root)
        let expected = ["cuvs": 31034, "cuvt": 31034, "wenli": 31095, "kjv": 31102, "vulgate": 35809, "quran_zh": 6236, "quran_ar": 6236, "analects": 512, "lotus_sutra": 504]
        for version in TextVersion.all {
            let verses = try await library.verses(version)
            XCTAssertFalse(verses.isEmpty, version.id)
            XCTAssertEqual(Set(verses.map(\.key)).count, verses.count, version.id)
            if let count = expected[version.id] { XCTAssertEqual(verses.count, count, version.id) }
            XCTAssertTrue(verses.allSatisfy { !$0.text.isEmpty && $0.key.chapter > 0 && $0.key.verse > 0 })
        }
    }
    func testReferencesInEnglishSimplifiedTraditionalAndFullWidth() async throws {
        let library = Library(root: root)
        for raw in ["John 3:16", "约 3:16", "約翰福音３：１６"] {
            let result = try await library.search(raw, version: TextVersion.find("cuvs"), modes: [.keyword], testament: 0)
            XCTAssertEqual(result.hits.first?.verse.key, VerseKey(43, 3, 16), raw)
        }
        let lotus = try await library.search("《妙法蓮華經》第二十五品第一段", version: TextVersion.find("lotus_sutra"), modes: [.keyword], testament: 0)
        XCTAssertEqual(lotus.hits.first?.verse.key, VerseKey(76, 25, 1))
        let range = try await library.search("Quran 94:5-6", version: TextVersion.find("quran_ar"), modes: [.keyword], testament: 0)
        XCTAssertEqual(range.hits.map(\.verse.key), [VerseKey(67,94,5), VerseKey(67,94,6)])
        for raw in ["John 3:0", "John 3:18-16", "John 999:1", "John 3:999"] {
            let invalid = try await library.search(raw, version: TextVersion.find("kjv"), modes: [.keyword], testament: 0)
            XCTAssertTrue(invalid.hits.isEmpty, raw)
        }
    }
    func testLatinComparisonUsesMappedPsalmNumber() async throws {
        let library = Library(root: root)
        let comparisons = try await library.comparisons(TextVersion.find("vulgate"), key: VerseKey(121,22,1))
        let chinese = comparisons.first { $0.version.id == "cuvs" }?.verse
        XCTAssertEqual(chinese?.key, VerseKey(19,23,1))
        XCTAssertTrue(chinese?.text.contains("牧者") == true)
    }
    func testMergedVerseIsResolvedToActualSource() async throws {
        let library = Library(root: root)
        let verse = try await library.resolved(TextVersion.find("wenli"), key: VerseKey(40,6,34))
        XCTAssertEqual(verse?.key, VerseKey(40,6,33))
    }
    func testTokenizerMatchesAndroidFixturesExactly() throws {
        struct Fixture: Decodable { let text: String; let ids: [Int64] }
        let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: root.appendingPathComponent("tokenizer-fixtures.json")))
        let tokenizer = try WordPieceTokenizer(vocabulary: String(contentsOf: root.appendingPathComponent("semantic/vocab.txt"), encoding: .utf8).components(separatedBy: "\n"))
        for fixture in fixtures { XCTAssertEqual(tokenizer.encode(fixture.text), fixture.ids, fixture.text) }
    }
    func testActualOfflineModelRetrievalAndCorpusSwitch() throws {
        let semantic = try SemanticSearch(root: root)
        let vector = try semantic.embedding("我想喝水")
        XCTAssertEqual(vector.count, 512)
        XCTAssertEqual(vector.reduce(Float(0)) { $0 + $1*$1 }, 1, accuracy: 0.001)
        for index in ["verses", "quran", "analects", "lotus_sutra", "verses"] {
            let hits = try semantic.nearest(index == "analects" ? "学习要结合思考" : "帮助别人", index: index)
            XCTAssertFalse(hits.isEmpty, index)
            XCTAssertTrue(hits.allSatisfy { $0.1.isFinite })
            if index == "analects" { XCTAssertTrue(hits.prefix(5).contains { $0.0 == VerseKey(68,2,15) }) }
        }
    }
    @MainActor func testLanguageChangesKeepSavedDataAndTextVersionIndependent() async throws {
        let suite = "YanJingTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let model = AppModel(defaults: defaults, root: root)
        let version = TextVersion.find("cuvs")
        let verses = try await model.library.chapter(version, book: 43, chapter: 3)
        let verse = try XCTUnwrap(verses.first)
        model.toggleSave(verse, version)
        for language in [AppLanguage.english, .simplified, .traditional] {
            model.language = language
            XCTAssertNotEqual(model.text("tab.settings"), "tab.settings")
            XCTAssertEqual(model.saved.first?.verse.text, verse.text)
        }
        let restored = AppModel(defaults: defaults, root: root)
        XCTAssertEqual(restored.language, .traditional)
        XCTAssertEqual(restored.saved.first?.verse, verse)
    }
}
