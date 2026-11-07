import XCTest

final class AppFlowTests: XCTestCase {
    func testThreeLanguagesLaunchSearchReadAndSettings() throws {
        for (language, read, settings, name) in [("en", "Read", "Settings", "YanJing"), ("zh-Hans", "阅读", "设置", "盐筋"), ("zh-Hant", "閱讀", "設置", "盐筋")] {
            let app = XCUIApplication()
            app.launchArguments = ["-AppleLanguages", "(\(language))", "-AppleLocale", language, "-language", language]
            app.launch()
            XCTAssertTrue(app.navigationBars[name].waitForExistence(timeout: 30))
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
            app.tabBars.buttons[read].tap()
            XCTAssertTrue(app.navigationBars[read].waitForExistence(timeout: 10))
            capture("\(language)-reader", app: app)
            app.tabBars.buttons[settings].tap()
            XCTAssertTrue(app.buttons["appLanguage"].exists || app.otherElements["appLanguage"].exists)
            capture("\(language)-settings", app: app)
            app.terminate()
        }
    }
    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
}
