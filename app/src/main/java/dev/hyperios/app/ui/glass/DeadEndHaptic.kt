package dev.hyperios.app.ui.glass

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity

/**
 * Provides tactile boundary haptic feedback when finger drag overscroll
 * or flings hit dead ends at the top or bottom of a scrollable container.
 * Gated by hapticsEnabled.
 */
fun Modifier.deadEndHaptic(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed Modifier
    val view = LocalView.current

    val nestedScrollConnection = remember(view) {
        object : NestedScrollConnection {
            private var hitTop = false
            private var hitBottom = false
            private var lastHapticTime = 0L
            private val HAPTIC_COOLDOWN_MS = 300L

            private fun triggerHaptic() {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastHapticTime >= HAPTIC_COOLDOWN_MS) {
                    lastHapticTime = now
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If dragging back away from top dead end, reset top latch
                if (hitTop && available.y < -3f) {
                    hitTop = false
                }
                // If dragging back away from bottom dead end, reset bottom latch
                if (hitBottom && available.y > 3f) {
                    hitBottom = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Dragging down at top boundary
                if (available.y > 2.5f && !hitTop) {
                    hitTop = true
                    triggerHaptic()
                }
                // Dragging up at bottom boundary
                else if (available.y < -2.5f && !hitBottom) {
                    hitBottom = true
                    triggerHaptic()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 60f && !hitTop) {
                    hitTop = true
                    triggerHaptic()
                } else if (available.y < -60f && !hitBottom) {
                    hitBottom = true
                    triggerHaptic()
                }
                return Velocity.Zero
            }
        }
    }

    this.nestedScroll(nestedScrollConnection)
}
