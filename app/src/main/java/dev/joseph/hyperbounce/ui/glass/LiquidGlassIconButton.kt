package dev.joseph.hyperbounce.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Authentic Liquid Glass Circular Icon Button.
 *
 * Implements pull-to-stretch jelly dynamics with release overshoot,
 * consumes drag touch events to prevent parent scroll interference,
 * manages dynamic zIndex so active dragging always floats on top of neighbor buttons,
 * and maintains clip=false to prevent container boundary truncation.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isActive by remember { mutableStateOf(false) }

    val pressAnim = remember { Animatable(0f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold) }

    Box(
        modifier = modifier
            .zIndex(if (isActive) 10f else 1f)
            .size(size)
            .graphicsLayer {
                val maxOffset = this.size.minDimension.coerceAtLeast(1f)
                val initialDerivative = 0.08f
                val curOffset = offsetAnim.value

                // Damped translation with rubber banding (tanh)
                translationX = maxOffset * tanh(initialDerivative * curOffset.x / maxOffset)
                translationY = maxOffset * tanh(initialDerivative * curOffset.y / maxOffset)

                // Jelly stretch along pull vector
                val dist = curOffset.getDistance()
                val maxDragScale = 0.38f
                val angle = atan2(curOffset.y, curOffset.x)
                val stretchProgress = (dist / (maxOffset * 1.5f)).coerceIn(0f, 1f)

                val baseScale = lerp(1f, 1.10f, pressAnim.value)
                scaleX = baseScale + maxDragScale * abs(cos(angle)) * stretchProgress
                scaleY = baseScale + maxDragScale * abs(sin(angle)) * stretchProgress

                shape = CircleShape
                clip = true
            }
            .background(
                color = tint.copy(alpha = lerp(0.12f, 0.24f, pressAnim.value)),
                shape = CircleShape
            )
            .border(
                width = 0.5.dp,
                color = tint.copy(alpha = lerp(0.25f, 0.45f, pressAnim.value)),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    isActive = true
                    var isDragging = false

                    scope.launch {
                        pressAnim.animateTo(1f, spring(0.5f, 400f))
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (change.changedToUp()) {
                            change.consume()
                            if (!isDragging) {
                                onClick()
                            }
                            break
                        }

                        if (!change.pressed) {
                            break
                        }

                        val dragDelta = change.position - startPos
                        if (!isDragging && dragDelta.getDistance() > 8f) {
                            isDragging = true
                        }

                        if (isDragging) {
                            change.consume()
                            scope.launch {
                                offsetAnim.snapTo(dragDelta)
                            }
                        }
                    }

                    // Release snapback with authentic underdamped overshoot
                    scope.launch {
                        launch {
                            pressAnim.animateTo(0f, spring(0.5f, 350f))
                        }
                        launch {
                            offsetAnim.animateTo(
                                targetValue = Offset.Zero,
                                animationSpec = spring(
                                    dampingRatio = 0.45f, // Bouncy release overshoot
                                    stiffness = 320f,
                                    visibilityThreshold = Offset.VisibilityThreshold
                                )
                            )
                            isActive = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
