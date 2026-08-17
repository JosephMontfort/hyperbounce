package dev.joseph.hyperbounce.toggles.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Controls the toggle thumb's press, drag and settle animation.
 *
 * Performance notes:
 * - Drag targets are fed directly into the Animatable. The old LiquidToggle
 *   implementation mirrored every pointer-move into a Compose mutable state,
 *   forcing a recomposition for every touch sample.
 * - Deliberate state changes (tap/programmatic toggle) use a slower spring so
 *   the press/bloom has time to fully play instead of snapping immediately.
 * - Drag settling uses a softer spring to give the thumb a small, premium
 *   amount of inertia without becoming detached from the finger.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    // Deliberately softer than the original 800 stiffness: the thumb follows
    // the finger with a very small visual delay instead of looking 1:1 rigid.
    private val valueAnimationSpec =
        spring(dampingRatio = 0.88f, stiffness = 520f, visibilityThreshold = visibilityThreshold)

    // Intentional click/programmatic-toggle motion. This is separate from drag
    // motion so taps do not reuse the extremely fast drag spring.
    private val clickValueAnimationSpec =
        spring(dampingRatio = 0.92f, stiffness = 260f, visibilityThreshold = visibilityThreshold)

    private val velocityAnimationSpec =
        spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold * 10f)

    // Slower press bloom for tap interactions so the bloom has time to reach
    // its full state before the thumb settles.
    private val clickPressProgressAnimationSpec =
        spring(dampingRatio = 0.92f, stiffness = 420f, visibilityThreshold = 0.001f)

    private val pressProgressAnimationSpec =
        spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)

    private val scaleXAnimationSpec =
        spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)

    private val scaleYAnimationSpec =
        spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)

    private val clickScaleXAnimationSpec =
        spring(dampingRatio = 0.8f, stiffness = 240f, visibilityThreshold = 0.001f)

    private val clickScaleYAnimationSpec =
        spring(dampingRatio = 0.85f, stiffness = 240f, visibilityThreshold = 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            releaseInternal(pressProgressAnimationSpec)
        }
    }

    private suspend fun releaseInternal(
        pressSpec: androidx.compose.animation.core.AnimationSpec<Float> = pressProgressAnimationSpec,
    ) {
        if (value != targetValue) {
            val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
            snapshotFlow { valueAnimation.value }
                .filter { abs(it - valueAnimation.targetValue) < threshold }
                .first()
        }
        coroutineScope {
            launch {
                pressProgressAnimation.animateTo(0f, pressSpec)
            }
            launch {
                scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec)
            }
            launch {
                scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec)
            }
        }
    }

    /**
     * Updates the drag target without introducing an intermediate Compose state.
     * This is intentionally cheap because it can be called for every pointer
     * move delivered by the platform.
     */
    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(targetValue, valueAnimationSpec) {
                updateVelocity()
            }
        }
    }

    /**
     * Used by tap/programmatic state changes. The press bloom is allowed to reach
     * its full state first, then the thumb settles, then the press state returns.
     * This deliberately produces a slower, more premium transition than dragging.
     */
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                val targetValue = value.coerceIn(valueRange)

                launch {
                    scaleXAnimation.animateTo(pressedScale, clickScaleXAnimationSpec)
                }
                launch {
                    scaleYAnimation.animateTo(pressedScale, clickScaleYAnimationSpec)
                }

                // Wait for the bloom to develop instead of immediately starting
                // and cancelling it again on the same frame.
                pressProgressAnimation.animateTo(
                    1f,
                    clickPressProgressAnimationSpec
                )

                valueAnimation.animateTo(targetValue, clickValueAnimationSpec)

                launch {
                    velocityAnimation.animateTo(0f, clickValueAnimationSpec)
                }

                // Give the release animation its own deliberate tail.
                pressProgressAnimation.animateTo(
                    0f,
                    clickPressProgressAnimationSpec
                )
                launch {
                    scaleXAnimation.animateTo(initialScale, clickScaleXAnimationSpec)
                }
                launch {
                    scaleYAnimation.animateTo(initialScale, clickScaleYAnimationSpec)
                }
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val targetVelocity =
            velocityTracker.calculateVelocity().x /
                    (valueRange.endInclusive - valueRange.start)
        animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}
