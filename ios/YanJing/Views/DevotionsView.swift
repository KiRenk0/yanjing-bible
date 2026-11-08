import SwiftUI

struct DevotionsView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var store = DevotionStore()
    var body: some View {
        Group {
            if let catalog = store.catalog {
                List {
                    if let today = catalog.recommended(isoWeekday: (Calendar.current.component(.weekday, from: .now) + 5) % 7 + 1) {
                        Section(model.text("devotions.today")) {
                            NavigationLink { RosaryView(catalog: catalog, set: today, store: store) } label: {
                                VStack(alignment: .leading, spacing: 12) {
                                    Label(text(today.title), systemImage: "cross.circle").font(.title2)
                                    Text(text(today.schedule)).foregroundStyle(.secondary)
                                    Text(model.text(store.position(today) > 0 ? "devotions.resume" : "devotions.begin")).font(.subheadline).foregroundStyle(.tint)
                                }.padding(.vertical, 12)
                            }.accessibilityIdentifier("devotions.today")
                        }
                    }
                    Section {
                        NavigationLink { PrayerListView(catalog: catalog) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                Label(model.text("devotions.prayers"), systemImage: "text.book.closed").font(.headline)
                                Text(model.text("devotions.prayersHint")).font(.subheadline).foregroundStyle(.secondary)
                            }.padding(.vertical, 8)
                        }.accessibilityIdentifier("devotions.prayers")
                    }
                    Section(model.text("devotions.allMysteries")) {
                        ForEach(catalog.sets) { set in
                            NavigationLink { RosaryView(catalog: catalog, set: set, store: store) } label: {
                                VStack(alignment: .leading, spacing: 6) { Text(text(set.title)); Text(text(set.schedule)).font(.caption).foregroundStyle(.secondary) }.padding(.vertical, 6)
                            }.accessibilityIdentifier("rosary.\(set.id)")
                        }
                    }
                    Section(model.text("devotions.guide")) { Text(text(catalog.guide)).font(.body).padding(.vertical, 6) }
                    Section {
                        DisclosureGroup(model.text("devotions.sources")) {
                            Text(text(catalog.edition)).font(.footnote).foregroundStyle(.secondary)
                            ForEach(catalog.sources, id: \.url) { source in
                                if let url = URL(string: source.url) { Link(source.title, destination: url).font(.footnote) }
                            }
                        }
                    }
                }
            } else {
                ContentUnavailableView {
                    Label(model.text("error.library"), systemImage: "exclamationmark.triangle")
                } actions: { Button(model.text("retry")) { store.load() } }
            }
        }.navigationTitle(model.text("devotions.title"))
    }
    private func text(_ value: DevotionText) -> String { value.resolve(model.language.resolved) }
}

struct PrayerListView: View {
    @EnvironmentObject private var model: AppModel
    let catalog: DevotionCatalog
    var body: some View {
        List {
            ForEach(["rosary", "daily"], id: \.self) { category in
                Section(model.text(category == "rosary" ? "devotions.rosaryPrayers" : "devotions.dailyPrayers")) {
                    ForEach(catalog.prayers.filter { $0.category == category }) { prayer in
                        NavigationLink(prayer.title.resolve(model.language.resolved)) { PrayerView(prayer: prayer, edition: catalog.edition) }.accessibilityIdentifier("prayer.\(prayer.id)")
                    }
                }
            }
        }.navigationTitle(model.text("devotions.prayers"))
    }
}

struct PrayerView: View {
    @EnvironmentObject private var model: AppModel
    let prayer: Prayer
    let edition: DevotionText
    var body: some View {
        List {
            Section { DevotionParagraph(text: prayer.text.resolve(model.language.resolved)).padding(.vertical, 12) }
            Section { PrayerActions(title: prayer.title.resolve(model.language.resolved), text: prayer.text.resolve(model.language.resolved)) }
            Section { Text(edition.resolve(model.language.resolved)).font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle(prayer.title.resolve(model.language.resolved)).navigationBarTitleDisplayMode(.inline)
    }
}

struct RosaryView: View {
    @EnvironmentObject private var model: AppModel
    let catalog: DevotionCatalog
    let set: RosarySet
    @ObservedObject var store: DevotionStore
    @State private var restart = false
    private var steps: [RosaryStep] { catalog.steps(set) }
    private var position: Int { store.position(set) }
    private var step: RosaryStep? { steps.indices.contains(position) ? steps[position] : nil }
    private var mystery: RosaryMystery? { guard let step, set.mysteries.indices.contains(step.decade - 1) else { return nil }; return set.mysteries[step.decade - 1] }
    private var prayer: Prayer? { catalog.prayers.first { $0.id == step?.prayerID } }
    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(model.text(step == nil ? "devotions.completed" : "devotions.step", position + 1, steps.count)).accessibilityIdentifier("rosary.progress")
                        ProgressView(value: Double(position), total: Double(steps.count))
                    }.padding(.vertical, 6).id("rosaryTop")
                }
                if let step {
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(phase(step)).font(.headline).foregroundStyle(.tint)
                            if let mystery { Text(text(mystery.reference)).font(.subheadline).foregroundStyle(.secondary) }
                            if let title = prayer?.title ?? mystery?.title { Text(text(title)).font(.title2).fontDesign(.serif) }
                            if step.repetition > 0 { Text(model.text("devotions.repetition", step.repetition)).font(.title2.monospacedDigit()).accessibilityIdentifier("rosary.repetition") }
                            if step.optional { Text(model.text("devotions.optional")).font(.subheadline).foregroundStyle(.secondary) }
                            if let content = prayer?.text ?? mystery?.meditation { DevotionParagraph(text: text(content)) }
                        }.padding(.vertical, 12)
                    }
                    if let prayer { Section { PrayerActions(title: text(prayer.title), text: text(prayer.text)) } }
                } else { Section { Text(model.text("devotions.completeHint")).padding(.vertical, 12) } }
                Section { Button(model.text("devotions.restart")) { restart = true }.accessibilityIdentifier("rosary.restart") }
            }
            .onChange(of: position) { _, _ in proxy.scrollTo("rosaryTop", anchor: .top) }
        }
        .navigationTitle(text(set.title)).navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 16) {
                Button(model.text("devotions.previous")) { store.move(set, to: position - 1) }.disabled(position <= 0).buttonStyle(.bordered).accessibilityIdentifier("rosary.previous")
                Spacer()
                Button(model.text(position == steps.count - 1 ? "devotions.finish" : "devotions.next")) { store.move(set, to: position + 1) }.disabled(step == nil).buttonStyle(.borderedProminent).accessibilityIdentifier("rosary.next")
            }.padding().background(.bar)
        }
        .alert(model.text("devotions.restart"), isPresented: $restart) {
            Button(model.text("cancel"), role: .cancel) { }
            Button(model.text("devotions.restart"), role: .destructive) { store.move(set, to: 0) }
        } message: { Text(model.text("devotions.restartHint")) }
    }
    private func text(_ value: DevotionText) -> String { value.resolve(model.language.resolved) }
    private func phase(_ step: RosaryStep) -> String {
        if step.decade == 0 { return model.text("devotions.opening") }
        if let mystery { return model.text("devotions.mystery", step.decade) + " · " + text(mystery.title) }
        return model.text("devotions.closing")
    }
}

private struct DevotionParagraph: View {
    @EnvironmentObject private var model: AppModel
    @ScaledMetric(relativeTo: .body) private var scale = 1.0
    let text: String
    var body: some View { Text(text).font(.system(size: model.fontSize * scale, design: .serif)).lineSpacing(8).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }
}

private struct PrayerActions: View {
    @EnvironmentObject private var model: AppModel
    let title: String
    let text: String
    var body: some View {
        HStack {
            Button(model.text("copy"), systemImage: "doc.on.doc") { UIPasteboard.general.string = title + "\n" + text }.accessibilityIdentifier("devotions.copy")
            Spacer()
            ShareLink(item: title + "\n" + text) { Label(model.text("share"), systemImage: "square.and.arrow.up") }
        }.buttonStyle(.borderless)
    }
}
