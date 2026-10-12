import SwiftUI

@main
struct MidnightDancerApp: App {
    init() {
        MdHaptics.prepare()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
