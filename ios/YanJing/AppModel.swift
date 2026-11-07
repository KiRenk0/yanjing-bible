import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case system, english = "en", simplified = "zh-Hans", traditional = "zh-Hant"
    var id: String { rawValue }
    var nativeName: String {
        switch self { case .system: return "language.system"; case .english: return "English"; case .simplified: return "简体中文"; case .traditional: return "繁體中文" }
    }
    var resolved: String {
        if self != .system { return rawValue }
        for language in Locale.preferredLanguages {
            if language.hasPrefix("zh") { return language.contains("Hant") || language.contains("TW") || language.contains("HK") || language.contains("MO") ? "zh-Hant" : "zh-Hans" }
            if language.hasPrefix("en") { return "en" }
        }
        return "en"
    }
}

@MainActor final class AppModel: ObservableObject {
    @Published var language: AppLanguage { didSet { defaults.set(language.rawValue, forKey: "language") } }
    @Published var appearance: String { didSet { defaults.set(appearance, forKey: "appearance") } }
    @Published var fontSize: Double { didSet { defaults.set(fontSize, forKey: "fontSize") } }
    @Published var version: TextVersion
    @Published var modes: Set<SearchMode> { didSet { defaults.set(modes.map(\.rawValue), forKey: "modes") } }
    @Published var testament = 0
    @Published var query = ""
    @Published var hits: [SearchHit] = []
    @Published var loading = true
    @Published var searching = false
    @Published var semanticUnavailable = false
    @Published var errorKey: String?
    @Published var history: [String]
    @Published var saved: [SavedPassage]
    @Published var readerBook: Int
    @Published var readerChapter: Int
    @Published var readerVerses: [Verse] = []
    @Published var books: [BookInfo] = []
    @Published var chapterCounts: [Int: Int] = [:]
    @Published var selection: SavedPassage?
    @Published var tab = "search"
    let library: Library
    let root: URL
    private let defaults: UserDefaults
    private var work: Task<Void, Never>?
    private var searchWork: Task<Void, Never>?

    init(defaults: UserDefaults = .standard, root: URL? = nil) {
        self.defaults = defaults
        self.root = root ?? Bundle.main.resourceURL!.appendingPathComponent("Corpus")
        library = Library(root: self.root)
        let initialLanguage = AppLanguage(rawValue: defaults.string(forKey: "language") ?? "system") ?? .system
        language = initialLanguage
        appearance = defaults.string(forKey: "appearance") ?? "system"
        let font = defaults.double(forKey: "fontSize")
        fontSize = font.isFinite && (16...32).contains(font) ? font : 20
        let initialVersion = TextVersion.find(defaults.string(forKey: "version") ?? (initialLanguage.resolved == "en" ? "kjv" : initialLanguage.resolved == "zh-Hant" ? "cuvt" : "cuvs"))
        version = initialVersion
        let initialModes = Set((defaults.stringArray(forKey: "modes") ?? ["semantic", "expression"]).compactMap(SearchMode.init(rawValue:)))
        modes = initialModes.isEmpty ? [.semantic, .expression] : initialModes
        history = defaults.stringArray(forKey: "history") ?? []
        saved = defaults.data(forKey: "saved").flatMap { try? JSONDecoder().decode([SavedPassage].self, from: $0) } ?? []
        readerBook = initialVersion.defaultBook; readerChapter = 1
        do { books = try JSONDecoder().decode([BookInfo].self, from: Data(contentsOf: self.root.appendingPathComponent("books.json"))) }
        catch { errorKey = "error.library" }
    }

    func text(_ key: String, _ arguments: CVarArg...) -> String {
        let bundle = Bundle.main.path(forResource: language.resolved, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        let value = bundle.localizedString(forKey: key, value: nil, table: nil)
        return arguments.isEmpty ? value : String(format: value, locale: Locale(identifier: language.resolved), arguments: arguments)
    }
    func versionTitle(_ version: TextVersion) -> String { text(version.titleKey) }
    func bookName(_ id: Int) -> String {
        guard let book = books.first(where: { $0.id == id }) else { return String(id) }
        switch language.resolved { case "en": return book.en; case "zh-Hant": return book.traditional; default: return book.zh }
    }
    func reference(_ key: VerseKey) -> String { "\(bookName(key.book)) \(key.chapter):\(key.verse)" }
    func chapterTitle(book: Int, chapter: Int) -> String {
        let info = books.first { $0.id == book }
        let title = info?.chapters.indices.contains(chapter - 1) == true ? info!.chapters[chapter - 1] : ""
        return "\(bookName(book)) \(chapter)" + (title.isEmpty ? "" : " · \(title)")
    }
    private func positionID(_ version: TextVersion) -> String { version.id == "vulgate" ? "vulgate" : version.index }
    func load(_ next: TextVersion? = nil) {
        work?.cancel(); searchWork?.cancel()
        defaults.set([readerBook, readerChapter], forKey: "position.\(positionID(version))")
        if let next { if version.group != next.group || version.isClassical { testament = 0 }; version = next }
        defaults.set(version.id, forKey: "version")
        loading = true; searching = false; errorKey = nil; hits = []; readerVerses = []
        let snapshot = version
        work = Task {
            do {
                let all = try await library.verses(snapshot)
                try Task.checkCancellation()
                chapterCounts = Dictionary(grouping: all, by: { $0.key.book }).mapValues { $0.map(\.key.chapter).max() ?? 1 }
                let position = defaults.array(forKey: "position.\(positionID(snapshot))") as? [Int]
                readerBook = position?.first.flatMap { chapterCounts[$0] != nil ? $0 : nil } ?? snapshot.defaultBook
                readerChapter = min(max(1, position?.last ?? 1), chapterCounts[readerBook] ?? 1)
                readerVerses = all.filter { $0.key.book == readerBook && $0.key.chapter == readerChapter }
                loading = false
                search()
            } catch is CancellationError { }
            catch { loading = false; errorKey = "error.library" }
        }
    }
    func search(debounce: Bool = false) {
        searchWork?.cancel()
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { hits = []; searching = false; semanticUnavailable = false; return }
        guard !loading else { return }
        if query.count > 200 { searching = false; hits = []; errorKey = "error.queryLength"; return }
        let raw = query, snapshot = version, selectedModes = modes, filter = testament
        searching = true; errorKey = nil; semanticUnavailable = false
        searchWork = Task {
            do {
                if debounce { try await Task.sleep(for: .milliseconds(300)) }
                let result = try await library.search(raw, version: snapshot, modes: selectedModes, testament: filter)
                try Task.checkCancellation()
                hits = result.hits; semanticUnavailable = result.semanticUnavailable; searching = false
            } catch is CancellationError { }
            catch { searching = false; errorKey = "error.search" }
        }
    }
    func submit() {
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.isEmpty { history = Array(([value] + history.filter { $0 != value }).prefix(12)); defaults.set(history, forKey: "history") }
        search()
    }
    func read(book: Int, chapter: Int) {
        guard let count = chapterCounts[book], (1...count).contains(chapter) else { return }
        work?.cancel()
        readerBook = book; readerChapter = chapter
        defaults.set([book, chapter], forKey: "position.\(positionID(version))")
        let snapshot = version
        work = Task {
            do { let verses = try await library.chapter(snapshot, book: book, chapter: chapter); try Task.checkCancellation(); readerVerses = verses }
            catch is CancellationError { }
            catch { errorKey = "error.library" }
        }
    }
    func isSaved(_ verse: Verse, _ version: TextVersion) -> Bool { saved.contains { $0.verse.key == verse.key && $0.versionID == version.id } }
    func toggleSave(_ verse: Verse, _ version: TextVersion) {
        if isSaved(verse, version) { saved.removeAll { $0.verse.key == verse.key && $0.versionID == version.id } }
        else { saved.insert(SavedPassage(versionID: version.id, verse: verse, date: .now), at: 0) }
        if let bytes = try? JSONEncoder().encode(saved) { defaults.set(bytes, forKey: "saved") }
    }
    func clearHistory() { history = []; defaults.removeObject(forKey: "history") }
    func openInReader(_ passage: SavedPassage) {
        work?.cancel(); searchWork?.cancel()
        defaults.set([readerBook, readerChapter], forKey: "position.\(positionID(version))")
        version = TextVersion.find(passage.versionID)
        defaults.set(version.id, forKey: "version")
        defaults.set([passage.verse.key.book, passage.verse.key.chapter], forKey: "position.\(positionID(version))")
        tab = "read"
        readerBook = passage.verse.key.book; readerChapter = passage.verse.key.chapter
        load()
    }
    func exported(_ verse: Verse, _ version: TextVersion) -> String { "\(verse.text)\n— \(reference(verse.key)) · \(versionTitle(version))\n\(version.credit)" }
    func legalText() -> String { (try? String(contentsOf: root.appendingPathComponent("Licenses.txt"), encoding: .utf8)) ?? text("error.library") }
}
