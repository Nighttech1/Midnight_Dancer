import CoreHaptics
import SwiftUI
import UIKit

/// Mirrors KMP `HapticEvent` (C-20) — Stage 4 Core Haptics engine (C-30).
enum MdHapticEvent {
    case lightImpact
    case mediumImpact
    case heavyImpact
    case selection
    case success
    case warning
}

/// Core Haptics when the device supports it; UIKit generators otherwise.
enum MdHaptics {
    private static var engine: CHHapticEngine?
    private static var engineStarted = false

    static func prepare() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        guard engine == nil else {
            try? engine?.start()
            engineStarted = true
            return
        }
        do {
            let created = try CHHapticEngine()
            created.resetHandler = {
                engineStarted = false
                try? created.start()
                engineStarted = true
            }
            created.stoppedHandler = { _ in
                engineStarted = false
            }
            try created.start()
            engine = created
            engineStarted = true
        } catch {
            engine = nil
            engineStarted = false
        }
    }

    static func perform(_ event: MdHapticEvent) {
        if CHHapticEngine.capabilitiesForHardware().supportsHaptics {
            if !engineStarted { prepare() }
            if playCoreHaptic(event) { return }
        }
        playUIKitFallback(event)
    }

    /// Fire after InteractiveUnderdamped settle ≈ zero velocity (SwiftUI cannot await spring end).
    /// Mirrors KMP `SpringToken.approxSettleDelayMs()` for response 0.28 / ζ 0.72 → ~294 ms.
    static func performAfterSpringSettle(
        _ event: MdHapticEvent,
        responseSeconds: Double = 0.28,
        dampingFraction: Double = 0.72
    ) {
        let factor = dampingFraction >= 0.99 ? 0.95 : 1.05
        let delay = max(0.016, responseSeconds * factor)
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            perform(event)
        }
    }

    @discardableResult
    private static func playCoreHaptic(_ event: MdHapticEvent) -> Bool {
        guard let engine else { return false }
        let (intensity, sharpness): (Float, Float) = {
            switch event {
            case .selection: return (0.35, 0.55)
            case .lightImpact: return (0.45, 0.4)
            case .mediumImpact: return (0.65, 0.5)
            case .heavyImpact: return (0.9, 0.65)
            case .success: return (0.7, 0.35)
            case .warning: return (0.75, 0.85)
            }
        }()
        do {
            let intensityParam = CHHapticEventParameter(parameterID: .hapticIntensity, value: intensity)
            let sharpnessParam = CHHapticEventParameter(parameterID: .hapticSharpness, value: sharpness)
            let hapticEvent = CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [intensityParam, sharpnessParam],
                relativeTime: 0
            )
            let pattern = try CHHapticPattern(events: [hapticEvent], parameters: [])
            let player = try engine.makePlayer(with: pattern)
            try player.start(atTime: 0)
            return true
        } catch {
            return false
        }
    }

    private static func playUIKitFallback(_ event: MdHapticEvent) {
        switch event {
        case .selection:
            UISelectionFeedbackGenerator().selectionChanged()
        case .lightImpact:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .mediumImpact:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .heavyImpact:
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        case .success:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .warning:
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        }
    }
}
