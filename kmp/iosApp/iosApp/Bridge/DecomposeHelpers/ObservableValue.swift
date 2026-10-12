import Foundation
import Shared

/// Observes a Decompose `Value<T>` and publishes changes for SwiftUI.
/// Copied from Decompose sample helpers (bridge layer only — imports Shared).
final class ObservableValue<T: AnyObject>: ObservableObject {
    @Published
    private(set) var value: T

    private var cancellation: Cancellation?

    init(_ value: Value<T>) {
        self.value = value.value
        cancellation = value.subscribe { [weak self] newValue in
            self?.value = newValue
        }
    }

    deinit {
        cancellation?.cancel()
    }
}
