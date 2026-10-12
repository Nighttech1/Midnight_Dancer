package com.midnightdancer.core.designsystem

/**
 * Named haptic events. Platform engines:
 * - Android: `VibrationEffect.Composition` (androidApp `AndroidVibrationHaptics`, C-30)
 * - iOS: Core Haptics (`MdHaptics`, C-30)
 *
 * Trigger should align with spring zero-velocity (after underdamped settle), not raw onClick.
 */
enum class HapticEvent {
    LightImpact,
    MediumImpact,
    HeavyImpact,
    Selection,
    Success,
    Warning,
}

/**
 * Shared haptic contract — platform performers live in androidApp / iosApp (C-30).
 */
interface HapticFeedback {
    fun perform(event: HapticEvent)

    fun performLight() = perform(HapticEvent.LightImpact)
}

/** Empty performer for DI / previews / tests. */
object NoOpHapticFeedback : HapticFeedback {
    override fun perform(event: HapticEvent) = Unit
}

/**
 * Approximate delay until underdamped/critical spring is near rest (for UIKit/SwiftUI
 * paths that cannot await Animatable completion). Prefer Animatable.animateTo completion on Compose.
 */
fun SpringToken.approxSettleDelayMs(): Long {
    val factor = if (dampingRatio >= 0.99f) 0.95f else 1.05f
    return (responseSeconds * factor * 1000f).toLong().coerceAtLeast(16L)
}
