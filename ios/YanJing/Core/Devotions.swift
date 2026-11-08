import Foundation
import Combine

struct DevotionText: Codable, Sendable {
    let en: String
    let zhHans: String
    let zhHant: String
    func resolve(_ language: String) -> String {
        switch language { case "zh-Hans": return zhHans; case "zh-Hant": return zhHant; default: return en }
    }
}
struct Prayer: Codable, Identifiable, Sendable {
    let id: String
    let category: String
    let title: DevotionText
    let text: DevotionText
}
struct RosaryMystery: Codable, Sendable {
    let title: DevotionText
    let reference: DevotionText
    let meditation: DevotionText
}
struct RosarySet: Codable, Identifiable, Sendable {
    let id: String
    let days: [Int]
    let title: DevotionText
    let schedule: DevotionText
    let mysteries: [RosaryMystery]
}
struct DevotionSource: Codable, Sendable {
    let title: String
    let url: String
}
struct RosaryStep: Sendable {
    var prayerID: String? = nil
    var decade: Int = 0
    var repetition: Int = 0
    var optional = false
}
struct DevotionCatalog: Codable, Sendable {
    let schema: Int
    let edition: DevotionText
    let guide: DevotionText
    let opening: [String]
    let closing: [String]
    let prayers: [Prayer]
    let sets: [RosarySet]
    let sources: [DevotionSource]

    func recommended(isoWeekday: Int) -> RosarySet? { sets.first { $0.days.contains(isoWeekday) } }
    func steps(_ set: RosarySet) -> [RosaryStep] {
        var result = opening.map { RosaryStep(prayerID: $0) }
        for index in set.mysteries.indices {
            let decade = index + 1
            result.append(RosaryStep(decade: decade))
            result.append(RosaryStep(prayerID: "our_father", decade: decade))
            result += (1...10).map { RosaryStep(prayerID: "hail_mary", decade: decade, repetition: $0) }
            result.append(RosaryStep(prayerID: "glory_be", decade: decade))
            result.append(RosaryStep(prayerID: "fatima", decade: decade, optional: true))
        }
        result += closing.map { RosaryStep(prayerID: $0, decade: set.mysteries.count + 1) }
        return result
    }
    static func load(root: URL) throws -> DevotionCatalog {
        let catalog = try JSONDecoder().decode(Self.self, from: Data(contentsOf: root.appendingPathComponent("devotions/catalog.json")))
        guard catalog.schema == 1, catalog.sets.count == 4, catalog.sets.allSatisfy({ $0.mysteries.count == 5 }),
              catalog.sets.flatMap(\.days).sorted() == Array(1...7), Set(catalog.prayers.map(\.id)).count == catalog.prayers.count,
              catalog.sets.allSatisfy({ set in catalog.steps(set).allSatisfy { step in step.prayerID == nil || catalog.prayers.contains { $0.id == step.prayerID } } })
        else { throw LibraryError.invalidData("devotions") }
        return catalog
    }
}

@MainActor final class DevotionStore: ObservableObject {
    @Published private(set) var catalog: DevotionCatalog?
    @Published private(set) var positions: [String: Int] = [:]
    private let defaults: UserDefaults
    private let root: URL
    init(defaults: UserDefaults = .standard, root: URL? = nil) {
        self.defaults = defaults
        self.root = root ?? Bundle.main.resourceURL!.appendingPathComponent("Corpus")
        load()
    }
    func load() {
        catalog = try? DevotionCatalog.load(root: root)
        for set in catalog?.sets ?? [] { positions[set.id] = min(max(defaults.integer(forKey: "devotions.position.\(set.id)"), 0), catalog!.steps(set).count) }
    }
    func position(_ set: RosarySet) -> Int { positions[set.id] ?? 0 }
    func move(_ set: RosarySet, to value: Int) {
        let position = min(max(0, value), catalog?.steps(set).count ?? 0)
        positions[set.id] = position
        defaults.set(position, forKey: "devotions.position.\(set.id)")
    }
}
