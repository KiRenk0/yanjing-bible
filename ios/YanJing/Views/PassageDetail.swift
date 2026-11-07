import SwiftUI

struct PassageDetail: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let passage: SavedPassage
    @State private var section = 0
    @State private var context: [Verse] = []
    @State private var comparisons: [Comparison] = []
    @State private var loading = true
    @State private var failed = false
    private var version: TextVersion { TextVersion.find(passage.versionID) }
    var body: some View {
        NavigationStack {
            List {
                Section {
                    PassageRow(verse: passage.verse, version: version)
                    Text(version.credit).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                    if !passage.verse.gloss.isEmpty {
                        Text(model.text("detail.gloss")).font(.headline)
                        Text(passage.verse.gloss).textSelection(.enabled)
                        Text(model.text("detail.glossHint")).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                Section {
                    Picker(model.text("detail.title"), selection: $section) {
                        Text(model.text("detail.context")).tag(0)
                        if !version.isClassical { Text(model.text("detail.compare")).tag(1) }
                    }.pickerStyle(.segmented)
                }
                if loading { ProgressView(model.text("loading")) }
                else if failed { Text(model.text("error.library")); Button(model.text("retry")) { Task { await load() } } }
                else if section == 0 {
                    Section(model.text("detail.context")) {
                        ForEach(context.filter { abs($0.key.verse - passage.verse.key.verse) <= 3 }) { verse in
                            VStack(alignment: .leading, spacing: 12) {
                                Text(model.reference(verse.key)).font(.caption).foregroundStyle(.tint)
                                PassageText(verse: verse, version: version)
                            }.padding(.vertical, 8).listRowBackground(verse.key == passage.verse.key ? Color.accentColor.opacity(0.09) : Color(.secondarySystemGroupedBackground))
                        }
                        Button(model.text("detail.readChapter"), systemImage: "book") { model.openInReader(passage); dismiss() }
                    }
                } else {
                    Section {
                        Text(model.text("detail.numbering")).font(.footnote).foregroundStyle(.secondary)
                        ForEach(comparisons) { item in
                            VStack(alignment: .leading, spacing: 12) {
                                Text(model.versionTitle(item.version)).font(.headline).foregroundStyle(.tint)
                                if let verse = item.verse {
                                    Text(model.reference(verse.key)).font(.caption).foregroundStyle(.secondary)
                                    PassageText(verse: verse, version: item.version)
                                    ShareLink(item: model.exported(verse, item.version)) { Label(model.text("share"), systemImage: "square.and.arrow.up") }
                                } else { Text(model.text("detail.noCorrespondence")).foregroundStyle(.secondary) }
                            }.padding(.vertical, 8)
                        }
                    }
                }
            }
            .navigationTitle(model.text("detail.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(model.text("done")) { dismiss() } } }
            .task { await load() }
        }
    }
    private func load() async {
        loading = true; failed = false
        do {
            context = try await model.library.chapter(version, book: passage.verse.key.book, chapter: passage.verse.key.chapter)
            comparisons = try await model.library.comparisons(version, key: passage.verse.key)
            loading = false
        } catch is CancellationError { }
        catch { failed = true; loading = false }
    }
}
