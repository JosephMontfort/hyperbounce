package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class TabData(val bitmap: Bitmap?)

fun extractTabs(stockBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    val menu = stockBar.getChildAt(0) as? ViewGroup ?: return out
    for (i in 0 until menu.childCount) {
        val tab  = menu.getChildAt(i) as? ViewGroup ?: continue
        val icon = findImageView(tab)
        val drawable = icon?.drawable?.mutate()
        var bmp: Bitmap? = null
        if (drawable != null) {
            drawable.setTintList(null)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bmp))
        }
        out.add(TabData(bmp))
    }
    return out
}

fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

fun clickStockTab(stockBar: ViewGroup, index: Int) {
    (stockBar.getChildAt(0) as? ViewGroup)?.getChildAt(index)?.performClick()
}

@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    whatsAppContent: View?,
    externalTabState: State<Int>
) {
    val isDark = isSystemInDarkTheme()
    
    // Exact Instagram Theme-Aware Colors
    val containerColor = if (isDark) Color(0xE6121212) else Color(0xE6FAFAFA)
    val accentColor = if (isDark) Color.White else Color.Black
    val borderColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f)
    val pillShape = RoundedCornerShape(50)

    val tabs = remember(stockBar) { extractTabs(stockBar) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }
    
    // Sync WhatsApp swipe navigation to our indicator
    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) {
            selectedIndex = externalIdx
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 40.dp) // Finalized padding
                .width(320.dp)
                .height(64.dp)
                .background(containerColor, pillShape)
                .border(1.dp, borderColor, pillShape) // Prominent theme-aware outline
        ) {
            if (tabs.isNotEmpty()) {
                val density = LocalDensity.current
                val tabWidthDp = 320f / tabs.size
                val tabWidthPx = with(density) { tabWidthDp.dp.toPx() }
                
                var isPressed by remember { mutableStateOf(false) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                val targetOffset = if (isPressed) dragOffset else selectedIndex * tabWidthPx
                val animatedOffsetPx by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                    label = "offset"
                )
                
                val animatedWidthDp by animateFloatAsState(
                    targetValue = if (isPressed) tabWidthDp * 1.6f else tabWidthDp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                    label = "width"
                )

                // Crisp active pill highlight (No blur)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                            .width(animatedWidthDp.dp)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .clip(pillShape)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    isPressed = true
                                    dragOffset = selectedIndex * tabWidthPx
                                },
                                onDragEnd = {
                                    isPressed = false
                                    val nearestIndex = (dragOffset / tabWidthPx).roundToInt().coerceIn(0, tabs.size - 1)
                                    selectedIndex = nearestIndex
                                    
                                    // Fix: Trigger the actual WhatsApp tab switch on drag release
                                    if (externalTabState.value != nearestIndex) {
                                        clickStockTab(stockBar, nearestIndex)
                                    }
                                },
                                onDragCancel = { isPressed = false },
                                onDrag = { change, dragAmount ->
                                    dragOffset = (dragOffset + dragAmount.x).coerceIn(0f, (tabs.size - 1) * tabWidthPx)
                                    change.consume()
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedIndex = index
                                    if (externalTabState.value != index) {
                                        clickStockTab(stockBar, index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (tab.bitmap != null) {
                                val scale by animateFloatAsState(
                                    targetValue = if (selectedIndex == index) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                                    label = "scale"
                                )
                                
                                Image(
                                    bitmap = tab.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        // Removed clip(CircleShape) entirely per your request!
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
