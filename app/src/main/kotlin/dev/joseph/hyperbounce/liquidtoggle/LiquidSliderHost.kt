/*
 * Bridges the Java/Xposed side of HyperBounce (PhysicsHook.java) to the real,
 * library-backed LiquidSlider composable, the same way LiquidToggleHost does for
 * LiquidToggle. See ComposeHostSupport.kt for the shared attachment/lifecycle/
 * touch-passthrough/backdrop plumbing this builds on.
 *
 * The native slider widget's exact API (getMax/getMin/getProgress/setProgress,
 * whatever its OnSeekBarChangeListener field is called) is a Java/reflection
 * concern -- see PhysicsHook.applySliderFraction and friends -- so this file only
 * ever deals with a normalized 0f..1f fraction, never the widget's real min/max.
 */
package dev.joseph.hyperbounce.liquidtoggle

import android.view.View
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import dev.joseph.hyperbounce.PhysicsHook
import java.util.WeakHashMap

object LiquidSliderHost {

    private class Entry(
        val fractionState: MutableFloatState,
        val refresher: BackdropRefresher
    )

    private val entries = WeakHashMap<View, Entry>()

    /** Idempotent: safe to call from onAttachedToWindow every time the widget is (re)bound. */
    @JvmStatic
    fun attach(model: View, initialFraction: Float) {
        entries[model]?.let { it.refresher.start(); return }

        val overlay = createOverlayWrapper(model) ?: return
        val fractionState = mutableFloatStateOf(initialFraction.coerceIn(0f, 1f))
        val backdrop = ViewSnapshotBackdrop()

        overlay.composeView.setContent {
            LiquidSlider(
                value = { fractionState.floatValue },
                onValueChange = { fraction -> PhysicsHook.applySliderFraction(model, fraction) },
                backdrop = backdrop.asBackdrop(),
                onTrackingStart = { PhysicsHook.notifySliderTrackingStart(model) },
                onTrackingStop = { PhysicsHook.notifySliderTrackingStop(model) }
            )
        }

        // Same clip-children/clip-to-outline/elevation workaround HyperBounce
        // already used for the toggle (PhysicsHook.disableClipping), now applied
        // to the slider's overlay too so its glass isn't clipped by a scrolling
        // parent either.
        PhysicsHook.disableClipping(overlay.wrapper)

        val refresher = BackdropRefresher(overlay.wrapper, backdrop)
        entries[model] = Entry(fractionState, refresher)
        refresher.start()
    }

    /** Called from the setProgress() hook so external/programmatic value changes
     *  (not driven by our own onValueChange) are reflected visually. */
    @JvmStatic
    fun syncFraction(model: View, fraction: Float) {
        entries[model]?.let { entry ->
            val clamped = fraction.coerceIn(0f, 1f)
            if (entry.fractionState.floatValue != clamped) {
                entry.fractionState.floatValue = clamped
            }
        }
    }

    /** Called from onDetachedFromWindow; pauses the backdrop refresh loop but keeps state cached. */
    @JvmStatic
    fun onModelDetached(model: View) {
        entries[model]?.refresher?.stop()
    }
}
