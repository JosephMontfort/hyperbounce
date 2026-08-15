package dev.joseph.hyperbounce.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun JellyPinButton(
    digit: String,
    delayMs: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }
    
    val enterAnim = remember { Animatable(0f) }
    val pressAnim = remember { Animatable(0f) }
    val dragOffsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Staggered iOS 26 Entrance Animation
    LaunchedEffect(Unit) {
        delay(delayMs)
        isVisible = true
        enterAnim.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 250f))
    }

    // Custom 2D Jelly Pointer Input
    val jellyGestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            animationScope.launch { pressAnim.animateTo(1f, spring(0.5f, 300f)) }
            
            var dragOffset = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) {
                    dragOffset = change.position - down.position
                    animationScope.launch { dragOffsetAnim.snapTo(dragOffset) }
                }
            } while (change != null && change.pressed)

            // Trigger actual PIN click
            if (hypot(dragOffset.x, dragOffset.y) < size.width * 0.6f) {
                onClick()
            }

            // Bouncy snap back
            animationScope.launch {
                launch { pressAnim.animateTo(0f, spring(0.6f, 250f)) }
                launch { dragOffsetAnim.animateTo(Offset.Zero, spring(0.4f, 350f)) }
            }
        }
    }

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(jellyGestures)
                .graphicsLayer {
                    val progress = pressAnim.value
                    val dragOffset = dragOffsetAnim.value
                    
                    // Depth compression on press
                    val baseScale = androidx.compose.ui.util.lerp(1f, 0.88f, progress)
                    
                    // Pinned Jelly Math: Zero translation, maximum stretching
                    val dragDist = hypot(dragOffset.x, dragOffset.y)
                    val maxOffset = size.minDimension * 0.8f
                    val stretch = 0.35f * (dragDist / maxOffset).coerceAtMost(1f) // 35% max jelly stretch
                    val angle = atan2(dragOffset.y, dragOffset.x)
                    
                    // Apply entrance pop + squash & stretch
                    scaleX = (baseScale + stretch * abs(cos(angle)) - stretch * 0.5f * abs(sin(angle))) * enterAnim.value
                    scaleY = (baseScale + stretch * abs(sin(angle)) - stretch * 0.5f * abs(cos(angle))) * enterAnim.value
                    alpha = enterAnim.value
                }
                .background(
                    color = Color.White.copy(alpha = 0.15f + (0.05f * pressAnim.value)), 
                    shape = CircleShape
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
}
