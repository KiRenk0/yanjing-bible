import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var clearHistory = false
    @State private var versions = false
    var body: some View {
        Form {
            Section {
                HStack(spacing: 16) {
                    Image(systemName: "book.closed.fill").font(.largeTitle).foregroundStyle(.tint).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 6) { Text(model.text("app.name")).font(.title2.bold()); Text(model.text("settings.tagline")).foregroundStyle(.secondary); Text("\(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.7.0") · \(model.text("settings.native"))").font(.caption).foregroundStyle(.secondary) }
                }.padding(.vertical, 8)
            }
            Section(model.text("settings.language")) {
                Picker(model.text("settings.language"), selection: $model.language) {
                    ForEach(AppLanguage.allCases) { language in Text(language == .system ? model.text(language.nativeName) : language.nativeName).tag(language) }
                }.accessibilityIdentifier("appLanguage")
                Text(model.text("language.hint")).font(.footnote).foregroundStyle(.secondary)
            }
            Section(model.text("settings.reading")) {
                Picker(model.text("settings.appearance"), selection: $model.appearance) {
                    Text(model.text("appearance.system")).tag("system"); Text(model.text("appearance.light")).tag("light"); Text(model.text("appearance.dark")).tag("dark")
                }
                LabeledContent(model.text("settings.font"), value: "\(Int(model.fontSize))")
                Slider(value: $model.fontSize, in: 16...32, step: 2).accessibilityLabel(model.text("settings.font"))
                Text(model.text("settings.sample")).font(.system(size: model.fontSize, design: .serif)).lineSpacing(8)
            }
            Section(model.text("settings.library")) {
                Button(model.text("version.choose"), systemImage: "books.vertical") { versions = true }
                Button(model.text("history.clear"), systemImage: "clock", role: .destructive) { clearHistory = true }
                NavigationLink(model.text("settings.licenses")) { ScrollView { Text(model.legalText()).font(.footnote).textSelection(.enabled).padding() }.navigationTitle(model.text("settings.licenses")) }
            }
            Section(model.text("settings.about")) {
                Text(model.text("settings.privacy")).font(.footnote)
                Text(model.text("search.limits")).font(.footnote).foregroundStyle(.secondary)
                Link(destination: URL(string: "https://github.com/KiRenk0/yanjing-bible/releases/latest")!) { Label(model.text("settings.releases"), systemImage: "arrow.up.right.square") }
                Text(model.text("settings.sideload")).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(model.text("tab.settings"))
        .confirmationDialog(model.text("history.confirm"), isPresented: $clearHistory, titleVisibility: .visible) {
            Button(model.text("history.clear"), role: .destructive) { model.clearHistory() }
            Button(model.text("cancel"), role: .cancel) { }
        }
        .sheet(isPresented: $versions) { VersionPicker() }
    }
}
