import SwiftUI

struct ReaderView: View {
    @EnvironmentObject private var model: AppModel
    @State private var picker = false
    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(model.chapterTitle(book: model.readerBook, chapter: model.readerChapter)).font(.title.weight(.medium)).fontDesign(.serif).id("chapterTop")
                        Text(model.versionTitle(model.version)).font(.subheadline).foregroundStyle(.tint)
                        Text(model.version.credit).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                    }.padding(.vertical, 16)
                }
                if model.loading { ProgressView(model.text("loading")) }
                if let error = model.errorKey { Text(model.text(error)); Button(model.text("retry")) { model.load() } }
                if model.version.isRTL, model.readerChapter != 1, model.readerChapter != 9 {
                    Text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ").font(.title2).frame(maxWidth: .infinity).padding(.vertical).accessibilityLabel("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")
                }
                Section {
                    ForEach(model.readerVerses) { verse in
                        Button { model.selection = SavedPassage(versionID: model.version.id, verse: verse, date: .now) } label: {
                            HStack(alignment: .firstTextBaseline, spacing: 16) {
                                Text(String(verse.key.verse)).font(.caption.monospacedDigit()).foregroundStyle(.tint).frame(minWidth: 22)
                                PassageText(verse: verse, version: model.version).foregroundStyle(.primary)
                                if model.isSaved(verse, model.version) { Image(systemName: "bookmark.fill").font(.caption).foregroundStyle(.tint).accessibilityLabel(model.text("saved")) }
                            }.padding(.vertical, 10)
                        }.buttonStyle(.plain).id(verse.id)
                    }
                }
                Section { Text(model.text("reader.hint")).font(.footnote).foregroundStyle(.secondary) }
            }
            .listStyle(.plain)
            .navigationTitle(model.text("tab.read"))
            .toolbar {
                ToolbarItemGroup(placement: .bottomBar) {
                    Button(model.text("reader.previous"), systemImage: "chevron.left") { model.read(book: model.readerBook, chapter: model.readerChapter - 1) }.disabled(model.loading || model.readerChapter <= 1)
                    Spacer()
                    Button { picker = true } label: { Label("\(model.readerChapter) / \(model.chapterCounts[model.readerBook] ?? 1)", systemImage: "list.bullet") }.disabled(model.loading).accessibilityLabel(model.text("reader.choose"))
                    Spacer()
                    Button(model.text("reader.next"), systemImage: "chevron.right") { model.read(book: model.readerBook, chapter: model.readerChapter + 1) }.disabled(model.loading || model.readerChapter >= (model.chapterCounts[model.readerBook] ?? 1))
                }
            }
            .onChange(of: model.readerVerses) { _, _ in proxy.scrollTo("chapterTop", anchor: .top) }
            .sheet(isPresented: $picker) { ChapterPicker() }
        }
    }
}

struct ChapterPicker: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var book = 0
    var body: some View {
        NavigationStack {
            Form {
                if model.chapterCounts.count > 1 {
                    Picker(model.text("reader.book"), selection: $book) {
                        ForEach(model.chapterCounts.keys.sorted(), id: \.self) { id in Text(model.bookName(id)).tag(id) }
                    }
                }
                Section(model.text("reader.chapters")) {
                    ForEach(1...max(1, model.chapterCounts[book] ?? 1), id: \.self) { chapter in
                        Button { model.read(book: book, chapter: chapter); dismiss() } label: {
                            HStack { Text(model.chapterTitle(book: book, chapter: chapter)); Spacer(); if book == model.readerBook && chapter == model.readerChapter { Image(systemName: "checkmark") } }
                        }
                    }
                }
            }.navigationTitle(model.text("reader.choose"))
                .onAppear { book = model.readerBook }
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(model.text("cancel")) { dismiss() } } }
        }
    }
}

struct SavedView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Group {
            if model.saved.isEmpty { ContentUnavailableView(model.text("saved.empty"), systemImage: "bookmark", description: Text(model.text("saved.hint"))) }
            else {
                List {
                    ForEach(model.saved) { passage in
                        PassageRow(verse: passage.verse, version: TextVersion.find(passage.versionID))
                            .swipeActions { Button(model.text("save.remove"), role: .destructive) { model.toggleSave(passage.verse, TextVersion.find(passage.versionID)) } }
                    }
                }.listStyle(.insetGrouped)
            }
        }.navigationTitle(model.text("tab.saved"))
    }
}
