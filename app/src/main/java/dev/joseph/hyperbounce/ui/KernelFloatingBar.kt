package dev.joseph.hyperbounce.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// The AGSL shader is now kept strictly for the Liquid Lens / Jelly refraction inside the moving pill
private const val LiquidGlassShader = """
    uniform shader composable;
    uniform float2 size;
    uniform float blurAmount;
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float2 center = float2(0.5, 0.5);
        float dist = distance(uv, center);
        float2 refracted = fragCoord - (uv - center) * (dist * blurAmount * 0.1);
        half4 color = composable.eval(refracted);
        return color;
    }
"""

@Composable
fun KernelFloatingBottomBar(
    tabs: List<ExtractedTab>,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val dragPosition = remember { Animatable(0f) }

    val tabsCount = tabs.size
    // Matches the 260dp fixed container width from Java
    val tabWidthPx = with(LocalDensity.current) { (260.dp / tabsCount).toPx() } 

    Box(
        modifier = modifier
            .fillMaxSize() // Fills the 80dp height from Java, giving room for vertical overflow
            .pointerInput(Unit) {
                // INSTANT TAP ENGINE
                detectTapGestures(
                    onTap = { offset ->
                        val clickedIndex = (offset.x / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
                        selectedIndex = clickedIndex
                        onTabSelected(clickedIndex)
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                clickedIndex.toFloat(), 
                                spring(dampingRatio = 0.5f, stiffness = 400f)
                            )
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // HOLD & DRAG ENGINE
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val targetIndex = dragPosition.value.roundToInt().coerceIn(0, tabsCount - 1)
                        selectedIndex = targetIndex
                        onTabSelected(targetIndex)
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                targetIndex.toFloat(), 
                                spring(dampingRatio = 0.5f, stiffness = 300f)
                            )
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                selectedIndex.toFloat(), 
                                spring(dampingRatio = 0.5f, stiffness = 300f)
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dragPosition.snapTo(dragPosition.value + (dragAmount / tabWidthPx))
                        }
                    }
                )
            }
    ) {
        // Jelly / Water-bubble Deformation Physics
        val velocity = dragPosition.velocity
        val v = velocity / 10f
        val scaleX = 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
        val scaleY = 1f + (v * 0.25f).coerceIn(-0.2f, 0.2f)

        // The Sliding Indicator Pill
        Box(
            modifier = Modifier
                .height(48.dp) // Base height sits neatly inside the 56dp glass
                .width((260.dp / tabsCount))
                .align(Alignment.CenterStart)
                .offset { IntOffset(x = (dragPosition.value * tabWidthPx).roundToInt(), y = 0) }
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY // When dragged fast, stretches vertically past 56dp for overflow!
                    clip = true
                    shape = CircleShape
                }
                .drawWithCache {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val shader = RuntimeShader(LiquidGlassShader)
                        shader.setFloatUniform("size", size.width, size.height)
                        shader.setFloatUniform("blurAmount", 15f)
                        val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "composable")
                        onDrawWithContent {
                            drawContent()
                        }
                    } else {
                        onDrawWithContent { drawContent() }
                    }
                }
                .background(Color(0xFF2A3942)) // Inner Pill Color
        )

        // Transparent Overlay - Icons Only!
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val distance = abs(dragPosition.value - index)
                val tintAlpha = 1f - (distance * 0.5f).coerceIn(0f, 0.6f)
                val color = Color.White.copy(alpha = tintAlpha)

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx -> ImageView(ctx).apply { setImageDrawable(tab.icon) } },
                        update = { imageView ->
                            imageView.setColorFilter(android.graphics.Color.argb(
                                (color.alpha * 255).toInt(), (color.red * 255).toInt(),
                                (color.green * 255).toInt(), (color.blue * 255).toInt()
                            ))
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
