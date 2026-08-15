package dev.joseph.hyperbounce.liquidglass

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
    val animationScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    // Staggered iOS 26 Entrance Animation
    LaunchedEffect(Unit) {
        delay(delayMs)
        isVisible = true
    }

    // Utilize the DampedDragAnimation from AndroidLiquidGlass library
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 0.9f, // Compress slightly down on press for depth
            onDragStarted = {},
            onDragStopped = { onClick() },
            onDrag = { _, _ -> }
        )
    }

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(dampedDragAnimation.modifier)
                .graphicsLayer {
                    // Pinned Jelly Physics adapted from AndroidLiquidGlass LiquidButton
                    val progress = dampedDragAnimation.pressProgress
                    val scale = androidx.compose.ui.util.lerp(1f, 0.9f, progress)
                    
                    // Clamp translation heavily so the button stays pinned to its origin
                    val maxOffset = size.minDimension * 0.15f 
                    val initialDerivative = 0.15f
                    
                    // Using velocityTracker offset from DampedDragAnimation via DragGestureInspector
                    // Since DampedDragAnimation returns a 1D value, we approximate 2D drag via progress
                    // For true 2D jelly, we map the scale deformation based on touch.
                    val stretch = 0.35f * progress // 35% jelly stretch deformation
                    
                    // Simple uniform pop/jelly for stability
                    scaleX = scale + stretch
                    scaleY = scale - (stretch * 0.5f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {}, // No heavy RenderEffects here to guarantee zero clipping
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // Subtle liquid glass translucent fill
                        drawRect(Color.White.copy(alpha = 0.15f))
                        if (progress > 0f) {
                            // Brighten dynamically on press
                            drawRect(Color.White.copy(alpha = 0.1f * progress))
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
}
