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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class TabData(val sourceView: View, val bitmap: Bitmap)

fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

fun findViewByName(view: View, name: String): View? {
    if (view.id != View.NO_ID) {
        try { if (view.resources.getResourceEntryName(view.id) == name) return view }
        catch (e: Exception) {}
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findViewByName(view.getChildAt(i), name)?.let { return it }
        }
    }
    return null
}

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val uiMode = pivotBar.resources.configuration.uiMode
        val isDark = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val tint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        
        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue
            
            // Search for standard images or the profile thumbnail
            var img = findViewByName(tab, "image") as? ImageView
            if (img == null) img = findViewByName(tab, "pivot_bar_thumbnail") as? ImageView
            if (img == null) img = findImageView(tab)
            
            val d = img?.drawable?.constantState?.newDrawable()?.mutate() ?: continue
            d.setTint(tint)
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            out.add(TabData(tab, bmp))
        }
    } catch (e: Exception) {}
    return out
}

fun clickStockTab(pivotBar: ViewGroup, index: Int) {
    val wrapper = pivotBar.getChildAt(0) as? ViewGroup
    wrapper?.getChildAt(index)?.performClick()
}

@Composable
fun LiquidNavBarHost(
    pivotBar: ViewGroup,
    externalTabState: State<Int>
) {
    val isDark = isSystemInDarkTheme()
    
    // Theme-Aware Colors matching Instagram / WhatsApp builds
    val containerColor = if (isDark) Color(0xE6121212) else Color(0xE6FAFAFA)
    val accentColor = if (isDark) Color.White else Color.Black
    val borderColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f)
    val pillShape = RoundedCornerShape(50)

    var tabs by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Real-time tab extraction & state sync
    LaunchedEffect(pivotBar, isDark) {
        while (true) {
            val extracted = extractYouTubeTabs(pivotBar)
            if (extracted.isNotEmpty() && extracted.size != tabs.size) {
                tabs = extracted
            }
            if (tabs.isNotEmpty()) {
                for (i in tabs.indices) {
                    if (tabs[i].sourceView.isSelected || tabs[i].sourceView.isActivated) {
                        if (selectedIndex != i) selectedIndex = i
                        break
                    }
                }
            }
            delay(100)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 40.dp) // Exact padding specified
                .width(320.dp)
                .height(64.dp)
                .background(containerColor, pillShape)
                .border(1.dp, borderColor, pillShape)
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

                // Crisp active pill highlight (No blur distortion)
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
                                    clickStockTab(pivotBar, nearestIndex)
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
                                    clickStockTab(pivotBar, index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
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
                            )
                        }
                    }
                }
            }
        }
    }
}
