import SwiftUI
@preconcurrency import Shared

/// Stage 1 shell (C-07/C-09): one button; state from shared RootComponent via SKIE AsyncSequence.
struct ContentView: View {
    @StateObject private var bridge = RootBridge()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 24) {
                Text(bridge.label)
                    .font(.title2)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                Button("Hello") {
                    bridge.onHelloClick()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .task {
            await bridge.start()
        }
    }
}

@MainActor
final class RootBridge: ObservableObject {
    @Published private(set) var label: String = "Hello Midnight Dancer"

    private var root: RootComponent?
    private var lifecycle: LifecycleRegistry?

    func start() async {
        let lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        lifecycle.onStart()
        lifecycle.onResume()
        self.lifecycle = lifecycle

        let root = DefaultRootComponent(
            componentContext: DefaultComponentContext(lifecycle: lifecycle)
        )
        self.root = root

        // SKIE: StateFlow → AsyncSequence
        for await state in root.uiState {
            label = state.displayLabel
        }
    }

    func onHelloClick() {
        root?.onHelloClick()
    }
}

#Preview {
    ContentView()
}
