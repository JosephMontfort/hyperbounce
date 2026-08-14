/*
 * Bridges the Java/Xposed side of HyperBounce (PhysicsHook.java) to the real,
 * library-backed LiquidToggle composable. See ComposeHostSupport.kt for the
 * shared attachment/lifecycle/touch-passthrough/backdrop plumbing this builds on.
 */
package dev.joseph.hyperbounce.liquidtoggle

import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.CompoundButton
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.joseph.hyperbounce.PhysicsHook
import java.util.WeakHashMap

object LiquidToggleHost {

    private class Entry(
        val checkedState: MutableState<Boolean>,
        val rowPressedState: MutableState<Boolean>,
        val refresher: BackdropRefresher
    )

    private val entries = WeakHashMap<CompoundButton, Entry>()

    /** Idempotent: safe to call from onAttachedToWindow every time the widget is (re)bound. */
    @JvmStatic
    fun attach(model: CompoundButton) {
        entries[model]?.let { it.refresher.start(); return }

        val overlay = createOverlayWrapper(model) ?: return
        val checkedState = mutableStateOf(model.isChecked)
        val rowPressedState = mutableStateOf(false)
        val backdrop = ViewSnapshotBackdrop()

        overlay.composeView.setContent {
            LiquidToggle(
                selected = { checkedState.value },
                onSelect = { newChecked ->
                    if (model.isChecked != newChecked) model.isChecked = newChecked
                },
                backdrop = backdrop.asBackdrop(),
                externalPressed = { rowPressedState.value },
                onEdgeReached = {
                    if (overlay.composeView.isAttachedToWindow) {
                        overlay.composeView.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
                            else HapticFeedbackConstants.CLOCK_TICK
                        )
                    }
                }
            )
        }

        // Row-tap bloom, matching the Canvas engine's "row spy".
        attachRowSpy(overlay.wrapper, rowPressedState)

        // Elevation/clip-children workaround for the row's RecyclerView clipping,
        // reused as-is from the Canvas engine (see PhysicsHook.disableClipping).
        PhysicsHook.disableClipping(overlay.wrapper)

        val refresher = BackdropRefresher(overlay.wrapper, backdrop)
        entries[model] = Entry(checkedState, rowPressedState, refresher)
        refresher.start()
    }

    /** Called from the setChecked() hook so external/programmatic checked-state
     *  changes (not driven by our own onSelect) are reflected visually. */
    @JvmStatic
    fun syncChecked(model: CompoundButton) {
        entries[model]?.let { entry ->
            if (entry.checkedState.value != model.isChecked) {
                entry.checkedState.value = model.isChecked
            }
        }
    }

    /** Called from onDetachedFromWindow; pauses the backdrop refresh loop but keeps state cached. */
    @JvmStatic
    fun onModelDetached(model: CompoundButton) {
        entries[model]?.refresher?.stop()
    }
}
