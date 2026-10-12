import SwiftUI
import Shared

/// Property wrapper that binds a Decompose `Value<T>` into SwiftUI.
/// Copied from Decompose sample helpers (bridge layer only — imports Shared).
@propertyWrapper
struct StateValue<T: AnyObject>: DynamicProperty {
    @ObservedObject
    private var obj: ObservableValue<T>

    var wrappedValue: T { obj.value }

    init(_ value: Value<T>) {
        obj = ObservableValue(value)
    }
}
