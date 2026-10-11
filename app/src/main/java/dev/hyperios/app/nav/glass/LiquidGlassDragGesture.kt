package dev.hyperios.app.nav.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

/**
 * Small, permissive drag recognizer used by the liquid tab indicator.
 *
 * A tap is allowed to pass through to the tab's clickable surface. Dragging
 * only starts after a small touch-slop threshold, so the indicator no longer
 * feels like it requires a long/high-distance pull before responding.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragging = false
        var cancelled = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                cancelled = true
                break
            }

            if (change.changedToUpIgnoreConsumed()) {
                if (!dragging) {
                    // Not a drag; leave this pointer to the tab click target.
                    return@awaitEachGesture
                }
                onDragEnd(change)
                return@awaitEachGesture
            }

            val delta = change.positionChange()
            if (!dragging) {
                if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) >=
                    4f
                ) {
                    dragging = true
                    onDragStart(down)
                }
            }

            if (dragging) {
                if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                    onDrag(change, delta)
                    change.consume()
                }
            }
        }

        if (cancelled) onDragCancel()
    }
}
