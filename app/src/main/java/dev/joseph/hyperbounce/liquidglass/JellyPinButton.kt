package dev.joseph.hyperbounce.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Staggered iOS 26 Entrance Animation (Left to right, top to bottom cascade)
    LaunchedEffect(Unit) {
        delay(delayMs)
        isVisible = true
        enterAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 250f))
    }

    // Reuse AndroidLiquidGlass library's touch tracking, but we handle the deformation manually below
    val dampedDrag = remember(animationScope) {
        com.kyant.backdrop.catalog.utils.DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 0.88f, // Shrink inward on press for depth
            onDragStarted = {},
            onDragStopped = { onClick() },
            onDrag = { _, _ -> }
        )
    }

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(dampedDrag.modifier)
                .graphicsLayer {
                    val progress = dampedDrag.pressProgress
                    val depthScale = androidx.compose.ui.util.lerp(1f, 0.88f, progress)
                    
                    // Pinned Jelly Physics (Squash and Stretch without sliding out of bounds)
                    val stretch = 0.25f * progress // Maximum 25% deformation
                    
                    // Combine entrance pop with the drag deformation
                    scaleX = (depthScale + stretch) * enterAnim.value
                    scaleY = (depthScale - (stretch * 0.5f)) * enterAnim.value
                    alpha = enterAnim.value
                }
                .background(
                    color = Color.White.copy(alpha = 0.15f + (0.12f * dampedDrag.pressProgress)), 
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
