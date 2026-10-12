package com.midnightdancer.app.ui.motion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback

/**
 * Android Stage 4 haptic engine (C-30): [VibrationEffect.Composition] primitives when available,
 * View haptic fallback otherwise. Prefer firing after spring settle (zero velocity).
 */
class AndroidVibrationHaptics(
    private val vibrator: Vibrator?,
    private val fallbackView: View? = null,
) : HapticFeedback {
    override fun perform(event: HapticEvent) {
        if (tryComposition(event)) return
        fallbackView?.let { view ->
            val constant = when (event) {
                HapticEvent.Selection -> HapticFeedbackConstants.CLOCK_TICK
                HapticEvent.LightImpact -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticEvent.MediumImpact,
                HapticEvent.HeavyImpact,
                -> HapticFeedbackConstants.LONG_PRESS
                HapticEvent.Success -> HapticFeedbackConstants.CONFIRM
                HapticEvent.Warning -> HapticFeedbackConstants.REJECT
            }
            view.performHapticFeedback(constant)
            return
        }
        // Last resort predefined one-shots (API 29+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            val effect = when (event) {
                HapticEvent.Selection,
                HapticEvent.LightImpact,
                -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticEvent.MediumImpact -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticEvent.HeavyImpact,
                HapticEvent.Success,
                -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticEvent.Warning -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
    }

    private fun tryComposition(event: HapticEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val vib = vibrator ?: return false
        if (!vib.hasVibrator()) return false
        val (primitive, scale) = when (event) {
            HapticEvent.Selection -> VibrationEffect.Composition.PRIMITIVE_TICK to 0.45f
            HapticEvent.LightImpact -> VibrationEffect.Composition.PRIMITIVE_TICK to 0.65f
            HapticEvent.MediumImpact -> VibrationEffect.Composition.PRIMITIVE_CLICK to 0.7f
            HapticEvent.HeavyImpact -> VibrationEffect.Composition.PRIMITIVE_CLICK to 1.0f
            HapticEvent.Success -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                ) {
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.75f
                } else {
                    VibrationEffect.Composition.PRIMITIVE_CLICK to 0.85f
                }
            }
            HapticEvent.Warning -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
                ) {
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to 0.8f
                } else {
                    VibrationEffect.Composition.PRIMITIVE_CLICK to 0.55f
                }
            }
        }
        if (!vib.areAllPrimitivesSupported(primitive)) return false
        return try {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(primitive, scale)
                .compose()
            vib.vibrate(composition)
            true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        fun from(context: Context, fallbackView: View? = null): AndroidVibrationHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            return AndroidVibrationHaptics(vibrator, fallbackView)
        }
    }
}

@Composable
fun rememberMdHaptics(): HapticFeedback {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        AndroidVibrationHaptics.from(context, view)
    }
}
