import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @State private var chooseVersion = false
    var body: some View {
        TabView(selection: $model.tab) {
            Tab(model.text("tab.read"), systemImage: "book", value: "read") { navigation { ReaderView() } }
            Tab(model.text("tab.saved"), systemImage: "bookmark", value: "saved") { navigation { SavedView() } }
            Tab(model.text("tab.settings"), systemImage: "gearshape", value: "settings") { NavigationStack { SettingsView() } }
            Tab(model.text("tab.search"), systemImage: "magnifyingglass", value: "search", role: .search) { navigation { SearchView() } }
        }
        .tint(Color(red: 0.22, green: 0.43, blue: 0.35))
        .sheet(isPresented: $chooseVersion) { VersionPicker() }
        .sheet(item: $model.selection) { passage in PassageDetail(passage: passage).presentationDragIndicator(.visible) }
    }
    private func navigation<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        NavigationStack {
            content()
                .toolbar {
                    if model.tab != "saved" {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button { chooseVersion = true } label: { Label(model.versionTitle(model.version), systemImage: "books.vertical") }
                                .accessibilityLabel(model.text("version.choose"))
                                .accessibilityIdentifier("chooseVersion")
                        }
                    }
                }
        }
    }
}

struct VersionPicker: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section { Text(model.text("version.offline")).foregroundStyle(.secondary) }
                ForEach(["bible", "quran", "confucian", "buddhist", "taoist"], id: \.self) { group in
                    Section(model.text("group.\(group)")) {
                        ForEach(TextVersion.all.filter { $0.group == group }) { version in
                            Button { model.load(version); dismiss() } label: {
                                HStack { Text(model.versionTitle(version)).foregroundStyle(.primary); Spacer(); if model.version == version { Image(systemName: "checkmark").accessibilityLabel(model.text("selected")) } }
                            }.accessibilityIdentifier("version.\(version.id)")
                        }
                    }
                }
            }
            .navigationTitle(model.text("version.choose"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(model.text("done")) { dismiss() } } }
        }
    }
}

struct PassageText: View {
    @EnvironmentObject private var model: AppModel
    let verse: Verse
    let version: TextVersion
    @ScaledMetric(relativeTo: .body) private var scale = 1.0
    var body: some View {
        Text(verse.text)
            .font(.system(size: model.fontSize * scale, design: .serif))
            .lineSpacing(version.isRTL ? 12 : 8)
            .frame(maxWidth: .infinity, alignment: version.isRTL ? .trailing : .leading)
            .environment(\.layoutDirection, version.isRTL ? .rightToLeft : .leftToRight)
            .textSelection(.enabled)
    }
}

struct PassageRow: View {
    @EnvironmentObject private var model: AppModel
    let verse: Verse
    let version: TextVersion
    var modes: Set<SearchMode> = []
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Button { open() } label: { Text(model.reference(verse.key)).font(.headline) }
                    .accessibilityIdentifier("passage.\(verse.key.encoded)")
                Spacer()
                Button { model.toggleSave(verse, version) } label: { Image(systemName: model.isSaved(verse, version) ? "bookmark.fill" : "bookmark").frame(minWidth: 44, minHeight: 44) }
                    .accessibilityLabel(model.text(model.isSaved(verse, version) ? "save.remove" : "save.add"))
            }
            PassageText(verse: verse, version: version)
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.versionTitle(version)).font(.caption)
                    if !modes.isEmpty { Text(modes.sorted { $0.rawValue < $1.rawValue }.map { model.text($0.titleKey) }.joined(separator: " · ")).font(.caption2) }
                }.foregroundStyle(.secondary)
                Spacer()
                Button { open() } label: { Image(systemName: "text.book.closed").frame(minWidth: 44, minHeight: 44) }.accessibilityLabel(model.text("detail.title"))
                ShareLink(item: model.exported(verse, version)) { Image(systemName: "square.and.arrow.up").frame(minWidth: 44, minHeight: 44) }.accessibilityLabel(model.text("share"))
            }
        }
        .padding(.vertical, 8)
        .buttonStyle(.borderless)
        .contextMenu {
            Button(model.text("copy"), systemImage: "doc.on.doc") { UIPasteboard.general.string = model.exported(verse, version) }
            Button(model.text(model.isSaved(verse, version) ? "save.remove" : "save.add"), systemImage: "bookmark") { model.toggleSave(verse, version) }
            ShareLink(item: model.exported(verse, version)) { Label(model.text("share"), systemImage: "square.and.arrow.up") }
        }
    }
    private func open() { model.selection = SavedPassage(versionID: version.id, verse: verse, date: .now) }
}
