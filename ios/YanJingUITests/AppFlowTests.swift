import XCTest

final class AppFlowTests: XCTestCase {
    func testThreeLanguagesLaunchSearchReadAndSettings() throws {
        for (language, read, settings, name) in [("en", "Read", "Settings", "YanJing"), ("zh-Hans", "阅读", "设置", "盐筋"), ("zh-Hant", "閱讀", "設置", "盐筋")] {
            let app = XCUIApplication()
            app.launchArguments = ["-AppleLanguages", "(\(language))", "-AppleLocale", language, "-language", language]
            app.launch()
            XCTAssertTrue(app.navigationBars[name].waitForExistence(timeout: 30))
            let size = app.screenshot().image.size
            XCTAssertGreaterThan(size.height / size.width, 1.6, "The iPhone app must use the full screen instead of a legacy compatibility viewport")
            XCTAssertTrue(app.buttons["chooseVersion"].exists)
            capture("\(language)-search", app: app)
            if language == "en" {
                let example = app.buttons["John 3:16"].firstMatch
                XCTAssertTrue(example.waitForExistence(timeout: 30))
                example.tap()
                let result = app.buttons["passage.43:3:16"].firstMatch
                XCTAssertTrue(result.waitForExistence(timeout: 30))
                capture("en-search-results", app: app)
                result.tap()
                XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 10))
                capture("en-context", app: app)
                app.buttons["Done"].tap()
            }
            selectTab(read, app: app)
            XCTAssertTrue(app.navigationBars[read].waitForExistence(timeout: 10))
            XCTAssertTrue(app.buttons["reader.chooseChapter"].isHittable)
            if language == "en" {
                XCTAssertTrue(app.buttons["reader.next"].isHittable)
                app.buttons["reader.next"].tap()
                XCTAssertTrue(app.staticTexts["John 2"].waitForExistence(timeout: 10))
            }
            capture("\(language)-reader", app: app)
            let devotions = language == "en" ? "Devotions" : language == "zh-Hant" ? "讀經" : "读经"
            selectTab(devotions, app: app)
            XCTAssertTrue(app.navigationBars[devotions].waitForExistence(timeout: 10))
            capture("\(language)-devotions", app: app)
            app.buttons["devotions.prayers"].tap()
            app.buttons["prayer.our_father"].tap()
            let prayerText = language == "en" ? "Our Father, who art in heaven" : language == "zh-Hant" ? "我們在天上的父" : "我们在天上的父"
            XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", prayerText)).firstMatch.waitForExistence(timeout: 10))
            XCTAssertTrue(app.buttons["devotions.copy"].exists)
            capture("\(language)-prayer", app: app)
            app.navigationBars.buttons.element(boundBy: 0).tap()
            app.navigationBars.buttons.element(boundBy: 0).tap()
            let joyful = app.buttons["rosary.joyful"]
            if !joyful.isHittable { app.swipeUp() }
            joyful.tap()
            XCTAssertFalse(app.buttons["rosary.previous"].isEnabled)
            for _ in 0..<9 { app.buttons["rosary.next"].tap() }
            XCTAssertTrue(app.staticTexts["rosary.repetition"].waitForExistence(timeout: 10))
            capture("\(language)-rosary", app: app)
            app.terminate()
            app.launch()
            selectTab(devotions, app: app)
            let restored = app.buttons["rosary.joyful"]
            if !restored.isHittable { app.swipeUp() }
            restored.tap()
            XCTAssertTrue(app.staticTexts["rosary.repetition"].waitForExistence(timeout: 10))
            app.buttons["rosary.previous"].tap()
            XCTAssertFalse(app.staticTexts["rosary.repetition"].exists)
            let restart = app.buttons["rosary.restart"]
            if !restart.isHittable { app.swipeUp() }
            restart.tap()
            let resetLabel = language == "en" ? "Start again" : "重新开始"
            let traditionalResetLabel = language == "zh-Hant" ? "重新開始" : resetLabel
            app.alerts.buttons[traditionalResetLabel].tap()
            app.navigationBars.buttons.element(boundBy: 0).tap()
            selectTab(settings, app: app)
            XCTAssertTrue(app.buttons["appLanguage"].exists || app.otherElements["appLanguage"].exists)
            capture("\(language)-settings", app: app)
            app.terminate()
        }
    }
    private func selectTab(_ label: String, app: XCUIApplication) {
        let bar = app.tabBars.firstMatch
        XCTAssertTrue(bar.waitForExistence(timeout: 10))
        let tab = bar.buttons[label]
        // The system can restore its compact tab bar on relaunch. Tapping the
        // visible selected tab expands it before choosing another destination.
        if !tab.exists { bar.buttons.firstMatch.tap() }
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
    }
    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
}
