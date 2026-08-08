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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class TabData(val resId: Int, val bitmap: ImageBitmap?)

// Scrapes the original icons from Instagram's hidden tab bar
fun extractTabs(stockBar: ViewGroup, tabNames: List<String>): List<TabData> {
    val context = stockBar.context
    return tabNames.map { name ->
        val resId = context.resources.getIdentifier(name, "id", context.packageName)
        var imageBitmap: ImageBitmap? = null
        if (resId != 0) {
            val tabView = stockBar.findViewById<View>(resId)
            if (tabView is ViewGroup) {
                val imageView = findImageView(tabView)
                imageView?.drawable?.let { drawable ->
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    imageBitmap = bmp.asImageBitmap()
                }
            }
        }
        TabData(resId, imageBitmap)
    }
}

// Deep search to find the icon view
fun findImageView(group: ViewGroup): ImageView? {
    for (i in 0 until group.childCount) {
        val v = group.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) {
            val iv = findImageView(v)
            if (iv != null) return iv
        }
    }
    return null
}

@Composable
fun LiquidNavBarHost(stockBar: ViewGroup?) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) Color(0x66121212) else Color(0x66FAFAFA)
    val accentColor = if (isDark) Color.White else Color.Black
    
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    // Requested Order: Home, Reels, Messages, Search, Profile
    val orderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")
    
    val tabs = remember(stockBar) {
        if (stockBar != null) extractTabs(stockBar, orderedTabNames) else emptyList()
    }

    // Two-Way Sync: Poll Instagram's hidden tabs
    LaunchedEffect(stockBar) {
        while (true) {
            delay(100)
            if (stockBar != null) {
                for (i in tabs.indices) {
                    val view = stockBar.findViewById<View>(tabs[i].resId)
                    if (view != null && (view.isSelected || view.isActivated)) {
                        if (selectedIndex != i) {
                            selectedIndex = i
                        }
                        break
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .widthIn(max = 344.dp)
                .fillMaxWidth(0.88f)
                .height(64.dp)
                .drawBackdrop(
                    backdrop = rememberLayerBackdrop(),
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(32f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .background(containerColor, Capsule())
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), Capsule())
        ) {
            if (tabs.isNotEmpty()) {
                val density = LocalDensity.current
                val tabWidthDp = maxWidth / tabs.size
                val tabWidthPx = with(density) { tabWidthDp.toPx() }
                
                var isPressed by remember { mutableStateOf(false) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                val targetOffset = if (isPressed) dragOffset else selectedIndex * tabWidthPx
                val animatedOffsetPx by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
                )
                
                val animatedWidthDp by animateFloatAsState(
                    targetValue = if (isPressed) tabWidthDp * 1.6f else tabWidthDp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
                )

                // The Interactive Liquid Pill
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                            .width(animatedWidthDp)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .clip(Capsule())
                            .background(if (isDark) Color(0x44FFFFFF) else Color(0x33000000))
                    )
                }

                // Render Icons & Detect Long Press Scrubbing
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    isPressed = true
                                    dragOffset = selectedIndex * tabWidthPx
                                },
                                onDragEnd = {
                                    isPressed = false
                                    val nearestIndex = (dragOffset / tabWidthPx).roundToInt().coerceIn(0, tabs.size - 1)
                                    selectedIndex = nearestIndex
                                    stockBar?.findViewById<View>(tabs[nearestIndex].resId)?.performClick()
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
                                    stockBar?.findViewById<View>(tab.resId)?.performClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (tab.bitmap != null) {
                                val scale by animateFloatAsState(
                                    targetValue = if (selectedIndex == index) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                                )
                                
                                val isProfile = orderedTabNames[index] == "profile_tab"
                                
                                Image(
                                    bitmap = tab.bitmap,
                                    contentDescription = null,
                                    // Strip the color filter ONLY for the profile tab to avoid the square bug
                                    colorFilter = if (isProfile) null else ColorFilter.tint(
                                        if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
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
}
