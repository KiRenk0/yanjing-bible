import XCTest
@testable import YanJing

final class DevotionsTests: XCTestCase {
    private var root: URL { Bundle.main.resourceURL!.appendingPathComponent("Corpus") }
    func testSharedPrayersWeekdaysAndCompleteRosarySequence() throws {
        let catalog = try DevotionCatalog.load(root: root)
        XCTAssertEqual(catalog.prayers.count, 12)
        XCTAssertEqual(catalog.sets.count, 4)
        XCTAssertEqual((1...7).map { catalog.recommended(isoWeekday: $0)?.id }, ["joyful", "sorrowful", "glorious", "luminous", "sorrowful", "joyful", "glorious"])
        for set in catalog.sets {
            let steps = catalog.steps(set)
            XCTAssertEqual(steps.count, 80)
            XCTAssertEqual(steps.filter { $0.prayerID == "hail_mary" }.count, 53)
            XCTAssertEqual(steps.filter { $0.prayerID == "our_father" }.count, 6)
            XCTAssertEqual(steps.filter { $0.prayerID == "glory_be" }.count, 6)
            XCTAssertEqual(steps.filter { $0.prayerID == nil }.count, 5)
            XCTAssertEqual(steps.filter(\.optional).count, 5)
            for decade in 1...5 {
                XCTAssertEqual(steps.filter { $0.decade == decade && $0.prayerID == "hail_mary" }.map(\.repetition), Array(1...10))
            }
            XCTAssertEqual(steps.suffix(3).map(\.prayerID), ["hail_holy_queen", "rosary_closing", "sign_of_cross"])
            for language in ["en", "zh-Hans", "zh-Hant"] {
                XCTAssertTrue(set.mysteries.allSatisfy { !$0.meditation.resolve(language).isEmpty && !$0.reference.resolve(language).isEmpty })
                XCTAssertTrue(catalog.prayers.allSatisfy { !$0.title.resolve(language).isEmpty && !$0.text.resolve(language).isEmpty })
            }
        }
        let prayer = try XCTUnwrap(catalog.prayers.first { $0.id == "hail_mary" })
        XCTAssertEqual(prayer.title.resolve("zh-Hant"), "聖母經")
    }
    @MainActor func testProgressSurvivesRelaunchAndStaysIndependentOfBibleAndLanguage() throws {
        let suite = "DevotionsTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set("vulgate", forKey: "version")
        defaults.set([121, 22], forKey: "position.vulgate")
        let store = DevotionStore(defaults: defaults, root: root)
        let catalog = try XCTUnwrap(store.catalog)
        let joyful = try XCTUnwrap(catalog.sets.first { $0.id == "joyful" })
        let luminous = try XCTUnwrap(catalog.sets.first { $0.id == "luminous" })
        store.move(joyful, to: 15)
        store.move(luminous, to: 80)
        let restored = DevotionStore(defaults: defaults, root: root)
        XCTAssertEqual(restored.position(joyful), 15)
        XCTAssertEqual(restored.position(luminous), 80)
        restored.move(joyful, to: -1)
        XCTAssertEqual(restored.position(joyful), 0)
        restored.move(joyful, to: 999)
        XCTAssertEqual(restored.position(joyful), 80)
        XCTAssertEqual(defaults.string(forKey: "version"), "vulgate")
        XCTAssertEqual(defaults.array(forKey: "position.vulgate") as? [Int], [121, 22])
        defaults.set(999, forKey: "devotions.position.joyful")
        XCTAssertEqual(DevotionStore(defaults: defaults, root: root).position(joyful), 80)
    }
}
