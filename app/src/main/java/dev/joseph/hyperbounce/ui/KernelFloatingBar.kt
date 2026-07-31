package dev.joseph.hyperbounce.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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
        color.rgb *= 0.95;
        return color;
    }
"""

@Composable
fun KernelFloatingBottomBar(
    tabs: List<ExtractedTab>,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit
) {
    // Compiler bug workaround: Manually inject the scope instead of rememberCoroutineScope()
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()) }
    
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    val dragPosition = remember { Animatable(0f) }
    var pressOffset by remember { mutableStateOf<Offset?>(null) }
    var isPressed by remember { mutableStateOf(false) }

    val tabsCount = tabs.size
    val density = LocalDensity.current.density
    val contentWidthPx = totalWidthPx - (8f * density)
    val tabWidthPx = if (tabsCount > 0 && contentWidthPx > 0) contentWidthPx / tabsCount else 1f

    // Rubber-band Edge Pull Math
    val fraction = if (tabsCount > 0) ((dragPosition.value - selectedIndex) / tabsCount.toFloat()).coerceIn(-1f, 1f) else 0f
    val easeOutFraction = 1f - (1f - abs(fraction)) * (1f - abs(fraction))
    val panelOffset = (4f * density) * fraction.sign * easeOutFraction

    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .height(64.dp)
            .graphicsLayer {
                translationX = panelOffset
                clip = true
                shape = CircleShape
            }
            // AGSL Glass / Liquid Backdrop Pipeline
            .drawWithCache {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val shader = RuntimeShader(LiquidGlassShader)
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("blurAmount", 30f)
                    
                    val blurEffect = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP)
                    val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "composable")
                    val combinedEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect)
                    
                    onDrawWithContent {
                        drawContent()
                    }
                } else {
                    onDrawWithContent { drawContent() }
                }
            }
            .background(Color(0xC01F2C34))
            .onGloballyPositioned { totalWidthPx = it.size.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isPressed = true
                        pressOffset = offset
                    },
                    onDragEnd = {
                        isPressed = false
                        pressOffset = null
                        val targetIndex = dragPosition.value.roundToInt().coerceIn(0, tabsCount - 1)
                        selectedIndex = targetIndex
                        onTabSelected(targetIndex)
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                targetValue = targetIndex.toFloat(),
                                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
                            )
                        }
                    },
                    onDragCancel = {
                        isPressed = false
                        pressOffset = null
                        coroutineScope.launch {
                            dragPosition.animateTo(
                                selectedIndex.toFloat(),
                                spring(dampingRatio = 0.5f, stiffness = 300f)
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        pressOffset = change.position
                        coroutineScope.launch {
                            val newPos = dragPosition.value + (dragAmount / tabWidthPx)
                            dragPosition.snapTo(newPos)
                        }
                    }
                )
            }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // Jelly / Water-bubble Deformation
        val velocity = dragPosition.velocity
        val v = velocity / 10f
        val scaleX = 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
        val scaleY = 1f + (v * 0.25f).coerceIn(-0.2f, 0.2f)

        // The Sliding Pill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((tabWidthPx / density).dp)
                .offset { IntOffset(x = (dragPosition.value * tabWidthPx).roundToInt(), y = 0) }
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                }
                .background(Color(0xFF2A3942), CircleShape)
                // Specular Highlight via BlendMode.Plus
                .drawWithContent {
                    drawContent()
                    if (isPressed && pressOffset != null) {
                        val localX = pressOffset!!.x - (dragPosition.value * tabWidthPx)
                        if (localX in 0f..tabWidthPx) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.15f),
                                topLeft = Offset(localX - 30f, pressOffset!!.y - 30f),
                                size = Size(60f, 60f),
                                cornerRadius = CornerRadius(30f, 30f),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                }
        )

        // Transparent Content Overlay
        Row(
            modifier = Modifier.matchParentSize(),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply { setImageDrawable(tab.icon) }
                            },
                            update = { imageView ->
                                imageView.setColorFilter(android.graphics.Color.argb(
                                    (color.alpha * 255).toInt(), (color.red * 255).toInt(),
                                    (color.green * 255).toInt(), (color.blue * 255).toInt()
                                ))
                            },
                            modifier = Modifier.size(24.dp).padding(bottom = 2.dp)
                        )
                        if (distance < 0.5f) {
                            Text(text = tab.label, color = color, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
