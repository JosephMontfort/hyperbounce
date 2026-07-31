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

// AGSL Refraction Shader for the internal pill
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
    // Matches the premium compact 240dp width from Java
    val tabWidthPx = with(LocalDensity.current) { (240.dp / tabsCount).toPx() } 

    Box(
        modifier = modifier
            .fillMaxSize() // Fills the 80dp container to allow vertical jelly overflow
            .pointerInput(Unit) {
                // INSTANT TAP ENGINE
                detectTapGestures(
                    onTap = { offset ->
                        val clickedIndex = (offset.x / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
                        if (selectedIndex != clickedIndex) {
                            selectedIndex = clickedIndex
                            onTabSelected(clickedIndex)
                            coroutineScope.launch {
                                dragPosition.animateTo(
                                    clickedIndex.toFloat(), 
                                    spring(dampingRatio = 0.55f, stiffness = 400f)
                                )
                            }
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
                                spring(dampingRatio = 0.55f, stiffness = 400f)
                            )
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                selectedIndex.toFloat(), 
                                spring(dampingRatio = 0.55f, stiffness = 400f)
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
        // --- 1. THE TRANSLUCENT GLASS BACKGROUND ---
        // Sits perfectly centered, 56dp tall. Leaves room for the pill to stretch outside it!
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0x99111B21), CircleShape) // Premium sleek translucency
        )

        // Jelly / Water-bubble Deformation Physics
        val velocity = dragPosition.velocity
        val v = velocity / 10f
        val scaleX = 1f - (v * 0.75f).coerceIn(-0.25f, 0.25f)
        val scaleY = 1f + (v * 0.35f).coerceIn(-0.3f, 0.35f) // High vertical stretch factor

        // --- 2. THE SLIDING INDICATOR PILL ---
        Box(
            modifier = Modifier
                .height(48.dp) // Base height
                .width((240.dp / tabsCount))
                .align(Alignment.CenterStart)
                .offset { IntOffset(x = (dragPosition.value * tabWidthPx).roundToInt(), y = 0) }
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY // WILL stretch beyond 48dp and overflow the 56dp glass!
                    clip = true
                    shape = CircleShape
                }
                .drawWithCache {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val shader = RuntimeShader(LiquidGlassShader)
                        shader.setFloatUniform("size", size.width, size.height)
                        shader.setFloatUniform("blurAmount", 20f)
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

        // --- 3. TRANSPARENT ICONS OVERLAY (NO TEXT) ---
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(56.dp),
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
                        factory = { ctx -> 
                            ImageView(ctx).apply { 
                                setImageDrawable(tab.icon) 
                                setBackground(null) // Double-tap insurance against native ripples
                            } 
                        },
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
