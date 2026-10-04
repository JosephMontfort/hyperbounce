package dev.joseph.hyperbounce.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the user is actively touching or dragging a slider's thumb knob.
 * When true, swipe-to-back gesture handlers yield completely so the slider knob can be moved.
 * Touching any other part of the screen (card background, title, subtitle, reset button, margins)
 * leaves [isThumbTouched] as false, allowing full horizontal swipe-to-back navigation.
 */
object SliderThumbTracker {
    var isThumbTouched: Boolean by mutableStateOf(false)
}

/**
 * Plays an ultra-refined, minimal intensity haptic tick for slider step increments.
 * Uses Android 11+ [VibrationEffect.Composition.PRIMITIVE_LOW_TICK] at 0.22f scale if supported,
 * falling back to [HapticFeedbackConstants.SEGMENT_TICK] (API 34+) or [HapticFeedbackConstants.CLOCK_TICK].
 */
fun performSliderTick(context: Context, view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.22f)
                    .compose()
            )
            return
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
