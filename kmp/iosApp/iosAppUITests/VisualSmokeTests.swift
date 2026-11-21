import XCTest

/// Simulator evidence on a fresh installation; no private device data or seeded product changes.
final class VisualSmokeTests: XCTestCase {
    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func element(_ identifier: String, app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    func testColdSplashTransitionAndFiveTabsAndSettings() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        defer { app.terminate() }
        let splash = element("splash.video", app: app)
        XCTAssertTrue(splash.waitForExistence(timeout: 10), "Cold launch must expose the real splash video")
        let ready = expectation(for: NSPredicate(format: "value == %@", "ready"), evaluatedWith: splash)
        wait(for: [ready], timeout: 10)
        capture("01-cold-splash-ready", app: app)
        // The real five-second media controls its transition. No skip/fast-forward launch argument.
        let firstTab = app.buttons["tab.elements"]
        XCTAssertTrue(firstTab.waitForExistence(timeout: 30), "Splash must transition to the main dock")
        capture("02-after-splash-transition", app: app)
        let tabs = ["elements", "music", "choreography", "trainer", "exchange"]
        for (index, tab) in tabs.enumerated() {
            let button = app.buttons["tab.\(tab)"]
            XCTAssertTrue(button.waitForExistence(timeout: 5))
            XCTAssertTrue(button.isHittable, "Tab \(tab) must be reachable")
            button.tap()
            XCTAssertTrue(element("screen.\(tab)", app: app).waitForExistence(timeout: 10),
                          "Tap must show the corresponding content, not merely retain the dock")
            capture(String(format: "%02d-tab-%@", index + 3, tab), app: app)
        }
        let settings = app.buttons["settings.open"]
        XCTAssertTrue(settings.isHittable)
        settings.tap()
        XCTAssertTrue(element("screen.settings", app: app).waitForExistence(timeout: 10))
        capture("08-settings-top", app: app)
        app.swipeUp()
        capture("09-settings-scrolled", app: app)
        let close = app.buttons["settings.close"]
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        if !close.isHittable { app.swipeDown() }
        XCTAssertTrue(close.isHittable)
        close.tap()
        XCTAssertTrue(app.buttons["settings.open"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["settings.open"].isHittable)
        capture("10-settings-closed", app: app)
        app.buttons["tab.elements"].tap()
        let add = app.buttons["element.add"]
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        add.tap()
        XCTAssertTrue(app.buttons["element.save"].waitForExistence(timeout: 10))
        capture("11-element-editor", app: app)
        app.buttons["element.cancel"].tap()
        XCTAssertTrue(add.waitForExistence(timeout: 5))

        app.buttons["tab.exchange"].tap()
        XCTAssertTrue(element("screen.exchange", app: app).waitForExistence(timeout: 5))
        let createSet = app.buttons["sets.create"]
        XCTAssertTrue(createSet.waitForExistence(timeout: 5))
        createSet.tap()
        let saveSet = app.buttons["sets.save"]
        XCTAssertTrue(saveSet.waitForExistence(timeout: 10))
        let readyToValidate = expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: saveSet)
        wait(for: [readyToValidate], timeout: 10)
        capture("13-set-editor", app: app)
        saveSet.tap()
        XCTAssertTrue(element("sets.editor.error", app: app).waitForExistence(timeout: 10))
        capture("14-set-empty-validation", app: app)
        app.buttons["sets.closeEditor"].tap()
        XCTAssertTrue(createSet.waitForExistence(timeout: 5))

        app.buttons["tab.choreography"].tap()
        let create = app.buttons["choreo.add"]
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        create.tap()
        XCTAssertTrue(app.buttons["choreo.save"].waitForExistence(timeout: 10))
        XCTAssertTrue(element("choreo.timeline.ruler", app: app).exists)
        capture("12-choreography-editor", app: app)

    }

    func testPhotoImportAndCatalogControlsAfterRestart() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        defer { app.terminate() }
        let add = app.buttons["element.add"]
        XCTAssertTrue(add.waitForExistence(timeout: 35))

        for (tab, prefix) in [("elements", "element"), ("music", "music"), ("choreography", "choreo")] {
            app.buttons["tab.\(tab)"].tap()
            XCTAssertTrue(app.buttons["\(prefix).filter"].waitForExistence(timeout: 8))
            let sort = app.buttons["\(prefix).sort"]
            XCTAssertTrue(sort.isHittable)
            sort.tap()
            let alpha = app.buttons["По алфавиту"]
            XCTAssertTrue(alpha.waitForExistence(timeout: 6))
            alpha.tap()
            XCTAssertTrue(app.textFields["\(prefix).search"].exists)
        }

        app.buttons["tab.elements"].tap()
        add.tap()
        XCTAssertTrue(app.buttons["element.save"].waitForExistence(timeout: 8))
        let name = app.textFields["element.name"]
        let photo = app.buttons["element.choosePhoto"]
        for _ in 0..<4 where !photo.isHittable { app.swipeUp() }
        XCTAssertTrue(photo.isHittable)
        photo.tap()

        let collectionItem = app.collectionViews.cells.firstMatch
        let videoLabel = NSPredicate(format: "label BEGINSWITH[c] %@ OR identifier CONTAINS[c] %@", "Video", "VideoCell")
        let accessibleItem = app.descendants(matching: .any).matching(videoLabel).firstMatch
        let loaded = NSPredicate { _, _ in collectionItem.isHittable || accessibleItem.isHittable }
        let pickerReady = XCTNSPredicateExpectation(predicate: loaded, object: nil)
        let waiting = XCTWaiter.wait(for: [pickerReady], timeout: 40)
        capture("18-photos-picker", app: app)
        if waiting != .completed {
            let tree = XCTAttachment(string: app.debugDescription)
            tree.name = "Photos picker accessibility on timeout"
            tree.lifetime = .keepAlways
            add(tree)
        }
        XCTAssertEqual(waiting, .completed, "The disposable video must appear in the system Photos picker")
        (collectionItem.isHittable ? collectionItem : accessibleItem).tap()
        let confirmPhoto = app.navigationBars.buttons["Add"]
        if confirmPhoto.exists && confirmPhoto.isHittable { confirmPhoto.tap() }

        for _ in 0..<3 where !element("element.player", app: app).isHittable { app.swipeDown() }
        XCTAssertTrue(element("element.player", app: app).waitForExistence(timeout: 25))
        capture("15-photo-imported-preview", app: app)
        for _ in 0..<3 where !name.isHittable { app.swipeDown() }
        XCTAssertTrue(name.isHittable)
        name.tap()
        let title = "QA media parity"
        name.typeText(title)
        let save = app.buttons["element.save"]
        if !save.isHittable { app.swipeUp() }
        XCTAssertTrue(save.isHittable)
        save.tap()
        XCTAssertTrue(add.waitForExistence(timeout: 12))

        app.terminate()
        app.launch()
        XCTAssertTrue(add.waitForExistence(timeout: 35))
        let card = app.staticTexts[title]
        XCTAssertTrue(card.waitForExistence(timeout: 12))
        capture("16-photo-library-after-restart", app: app)
        card.tap()
        XCTAssertTrue(element("element.player", app: app).waitForExistence(timeout: 12))
        capture("17-photo-reopened-after-restart", app: app)
    }

}
