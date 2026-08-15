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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun JellyPinButton(
    digitStr: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val animationScope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pressAnim = remember { Animatable(0f) }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPosition = down.position
            animationScope.launch {
                pressAnim.animateTo(1f, spring(0.5f, 300f))
            }
            
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) {
                    val dragAmount = change.position - startPosition
                    animationScope.launch { offsetAnim.snapTo(dragAmount) }
                }
            } while (change != null && change.pressed)
            
            animationScope.launch {
                launch { pressAnim.animateTo(0f, spring(0.6f, 250f)) }
                launch { offsetAnim.animateTo(Offset.Zero, spring(0.6f, 250f)) }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .then(gestureModifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = {
                    val progress = pressAnim.value
                    // Scale down slightly on press to simulate depth before stretch
                    val baseScale = androidx.compose.ui.util.lerp(1f, 0.85f, progress)
                    
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = offsetAnim.value
                    
                    // Liquid Glass tanh physics for drag bounds
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    
                    // Squash and stretch deformation
                    scaleX = baseScale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension)
                    scaleY = baseScale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension)
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.18f))
                }
            )
    ) {
        BasicText(
            text = digitStr,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
