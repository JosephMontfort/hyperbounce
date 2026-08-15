package dev.joseph.hyperbounce.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun JellyPinButton(
    digit: String,
    delayMs: Long,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pressAnim = remember { Animatable(0f) }
    val enterAnim = remember { Animatable(0f) }

    // Staggered iOS 26 Entrance Animation
    LaunchedEffect(Unit) {
        delay(delayMs)
        enterAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 250f))
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            scope.launch { pressAnim.animateTo(1f, spring(0.5f, 300f)) }
            
            var dragOffset = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) {
                    dragOffset = change.position - down.position
                    scope.launch { offsetAnim.snapTo(dragOffset) }
                }
            } while (change != null && change.pressed)

            // Trigger click if released without dragging out of bounds
            if (hypot(dragOffset.x, dragOffset.y) < size.width * 0.8f) {
                onClick()
            }

            scope.launch {
                launch { pressAnim.animateTo(0f, spring(0.6f, 250f)) }
                launch { offsetAnim.animateTo(Offset.Zero, spring(0.5f, 300f)) } // Bouncy snap back
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val enterScale = enterAnim.value
                scaleX = enterScale
                scaleY = enterScale
                alpha = enterScale.coerceIn(0f, 1f)
            }
            .then(gestureModifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx()) // Subtle glass blur
                    lens(12f.dp.toPx(), 24f.dp.toPx()) // Refraction edge
                },
                layerBlock = {
                    val progress = pressAnim.value
                    val baseScale = androidx.compose.ui.util.lerp(1f, 1.15f, progress) // Pop effect
                    
                    val maxOffset = size.width * 1.2f
                    val offset = offsetAnim.value
                    
                    // Liquid tanh physics for smooth drag asymptote
                    val dragX = maxOffset * tanh(offset.x / maxOffset)
                    val dragY = maxOffset * tanh(offset.y / maxOffset)
                    translationX = dragX
                    translationY = dragY

                    // Squash and stretch deformation
                    val dragDist = hypot(dragX, dragY)
                    val maxDragScale = 0.2f
                    val stretch = maxDragScale * (dragDist / maxOffset)
                    val angle = atan2(dragY, dragX)
                    
                    scaleX = baseScale + stretch * abs(cos(angle)) - stretch * 0.5f * abs(sin(angle))
                    scaleY = baseScale + stretch * abs(sin(angle)) - stretch * 0.5f * abs(cos(angle))
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.15f))
                    if (pressAnim.value > 0f) {
                        drawRect(Color.White.copy(alpha = 0.1f * pressAnim.value)) // Brighten on press
                    }
                }
            )
    ) {
        BasicText(
            text = digit,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
