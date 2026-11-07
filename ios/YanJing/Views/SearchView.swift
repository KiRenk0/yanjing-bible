import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var model: AppModel
    @State private var filters = false
    var body: some View {
        List {
            if model.query.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 16) {
                        Image(systemName: "sparkle").font(.largeTitle).foregroundStyle(.tint).accessibilityHidden(true)
                        Text(model.text("search.hero")).font(.largeTitle.weight(.semibold)).fontDesign(.serif)
                        Text(model.text("search.subtitle")).foregroundStyle(.secondary)
                        Label(model.text("search.private"), systemImage: "lock.shield").font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 20)
                }
                if !model.history.isEmpty {
                    Section(model.text("search.recent")) { ForEach(model.history, id: \.self) { value in Button { model.query = value; model.submit() } label: { Label(value, systemImage: "clock") } } }
                }
                Section(model.text("search.try")) {
                    ForEach(examples, id: \.self) { example in Button { model.query = example; model.submit() } label: { HStack { Text(example); Spacer(); Image(systemName: "arrow.up.left").accessibilityHidden(true) } } }
                }
                Section { Text(model.text("search.limits")).font(.footnote).foregroundStyle(.secondary) }
            } else {
                if model.searching || model.loading { Section { HStack { ProgressView(); Text(model.text(model.loading ? "loading" : "search.searching")) } } }
                if let error = model.errorKey { Section { Text(model.text(error)); Button(model.text("retry")) { model.load() } } }
                if model.semanticUnavailable { Section { Label(model.text("search.semanticUnavailable"), systemImage: "info.circle").font(.footnote) } }
                if !model.searching, !model.loading, model.hits.isEmpty, model.errorKey == nil {
                    ContentUnavailableView(model.text("search.empty"), systemImage: "magnifyingglass", description: Text(model.text("search.emptyHint")))
                }
                if !model.hits.isEmpty {
                    Section(model.text("search.results", model.hits.count)) {
                        ForEach(model.hits) { hit in PassageRow(verse: hit.verse, version: model.version, modes: hit.modes) }
                    }
                    Section { Text(model.text("search.resultHint")).font(.footnote).foregroundStyle(.secondary) }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(model.text("app.name"))
        .searchable(text: $model.query, prompt: Text(model.text("search.prompt")))
        .onSubmit(of: .search) { model.submit() }
        .onChange(of: model.query) { _, _ in model.search(debounce: true) }
        .toolbar { ToolbarItem(placement: .topBarLeading) { Button(model.text("search.filters"), systemImage: "slider.horizontal.3") { filters = true }.accessibilityIdentifier("searchFilters") } }
        .sheet(isPresented: $filters) { filterSheet.presentationDetents([.medium, .large]).presentationDragIndicator(.visible) }
    }
    private var examples: [String] {
        if model.version.group == "quran" { return ["2:255", "94:5-6", "困难之后会有容易"] }
        if model.version.isClassical { return [model.bookName(model.version.defaultBook) + " 1:1", "学习要结合思考", "放下执着和恐惧"] }
        return model.language.resolved == "en" ? ["John 3:16", "Psalms 23", "love thy neighbour"] : ["约 3:16", "诗篇 23", "有困难的时候朋友才最重要"]
    }
    private var filterSheet: some View {
        NavigationStack {
            Form {
                Section(model.text("search.modes")) {
                    ForEach(SearchMode.allCases) { mode in
                        Toggle(isOn: Binding(get: { model.modes.contains(mode) }, set: { enabled in
                            if enabled { model.modes.insert(mode) } else if model.modes.count > 1 { model.modes.remove(mode) }; model.search()
                        })) { VStack(alignment: .leading) { Text(model.text(mode.titleKey)); Text(model.text(mode.titleKey + ".hint")).font(.caption).foregroundStyle(.secondary) } }
                    }
                }
                if model.version.group == "bible" {
                    Picker(model.text("search.scope"), selection: $model.testament) {
                        Text(model.text("scope.all")).tag(0); Text(model.text("scope.old")).tag(1); Text(model.text("scope.new")).tag(2)
                    }.onChange(of: model.testament) { _, _ in model.search() }
                }
                Section { Text(model.text("search.modeHint")).font(.footnote).foregroundStyle(.secondary) }
            }.navigationTitle(model.text("search.filters"))
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(model.text("done")) { filters = false } } }
        }
    }
}
