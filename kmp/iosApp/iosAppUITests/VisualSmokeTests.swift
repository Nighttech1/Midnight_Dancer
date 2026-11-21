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
        app.buttons["tab.trainer"].tap()
        let choreoMode = app.buttons["trainer.mode.choreography"]
        XCTAssertTrue(choreoMode.waitForExistence(timeout: 8))
        choreoMode.tap()
        let chooseStep = app.buttons["trainer.step.2"]
        XCTAssertTrue(chooseStep.waitForExistence(timeout: 8))
        chooseStep.tap()
        let gatedPart = app.buttons["trainer.part.open"]
        XCTAssertTrue(gatedPart.waitForExistence(timeout: 8))
        XCTAssertFalse(gatedPart.isEnabled, "The part of a choreography is locked without Pro")
        capture("20-trainer-choreography-free", app: app)

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

    func testSettingsStyleAppearsInElementsAndCanBeRetired() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        defer { app.terminate() }
        let settings = app.buttons["settings.open"]
        XCTAssertTrue(settings.waitForExistence(timeout: 35))
        settings.tap()
        let newStyle = app.buttons["settings.style.add"]
        XCTAssertTrue(newStyle.waitForExistence(timeout: 8))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: newStyle)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 10), .completed)
        newStyle.tap()
        let field = app.textFields["settings.style.name"]
        XCTAssertTrue(field.waitForExistence(timeout: 8))
        field.tap()
        let name = "QA Style"
        field.typeText(name)
        app.buttons["settings.style.confirm"].tap()
        XCTAssertTrue(app.buttons["settings.close"].waitForExistence(timeout: 8))
        app.buttons["settings.close"].tap()

        let next = app.buttons["Следующий стиль"]
        XCTAssertTrue(next.waitForExistence(timeout: 8))
        for _ in 0..<18 where !app.staticTexts[name].exists { next.tap() }
        XCTAssertTrue(app.staticTexts[name].exists, "Elements must see the style saved by Settings")
        capture("19-settings-style-in-elements", app: app)

        settings.tap()
        let found = app.buttons["settings.style.delete.\(name)"]
        let list = element("settings.styles.list", app: app)
        XCTAssertTrue(list.waitForExistence(timeout: 8))
        for _ in 0..<8 where !found.isHittable { list.swipeUp() }
        XCTAssertTrue(found.isHittable)
        found.tap()
        let confirmation = app.alerts.buttons["Удалить"]
        XCTAssertTrue(confirmation.waitForExistence(timeout: 8))
        confirmation.tap()
        app.buttons["settings.close"].tap()
        let removed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.staticTexts[name])
        XCTAssertEqual(XCTWaiter.wait(for: [removed], timeout: 8), .completed)
        for _ in 0..<18 {
            XCTAssertFalse(app.staticTexts[name].exists)
            next.tap()
        }
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

        let videos = app.segmentedControls["Videos"]
        let cells = app.cells.firstMatch
        let mediaLabel = NSPredicate(format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@ OR identifier CONTAINS[c] %@",
                                     "0:05", "5 seconds", "VideoCell")
        let accessibleItem = app.descendants(matching: .any).matching(mediaLabel).firstMatch
        let loaded = NSPredicate { _, _ in cells.isHittable || accessibleItem.isHittable || videos.exists }
        let pickerReady = XCTNSPredicateExpectation(predicate: loaded, object: nil)
        let waiting = XCTWaiter.wait(for: [pickerReady], timeout: 40)
        capture("18-photos-picker", app: app)
        XCTAssertEqual(waiting, .completed, "The system Photos picker must finish loading")

        if cells.isHittable {
            cells.tap()
        } else if accessibleItem.isHittable {
            accessibleItem.tap()
        } else {
            // The remote Photos view can hide thumbnail accessibility. The fresh simulator
            // contains one seeded video, in the first tile of the Videos tab.
            XCTAssertTrue(videos.exists)
            let showsAccessNotice = app.staticTexts["Private Access to Photos"].exists
            let y = showsAccessNotice ? 0.43 : 0.24
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.16, dy: CGFloat(y))).tap()
        }
        let confirmPhoto = app.navigationBars.buttons["Add"]
        if confirmPhoto.exists && confirmPhoto.isHittable { confirmPhoto.tap() }
        let play = app.buttons["element.preview.play"]
        guard play.waitForExistence(timeout: 25) else {
            let tree = XCTAttachment(string: app.debugDescription)
            tree.name = "Photos selection and preview accessibility"
            tree.lifetime = .keepAlways
            self.add(tree)
            capture("photo-selection-failure", app: app)
            XCTFail("The selected video must open in the element player")
            return
        }
        capture("15-photo-imported-preview", app: app)
        for _ in 0..<3 where !name.isHittable { app.swipeDown() }
        XCTAssertTrue(name.isHittable)
        name.tap()
        let title = "QA Media"
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
        XCTAssertTrue(play.waitForExistence(timeout: 12))
        capture("17-photo-reopened-after-restart", app: app)
    }

}
