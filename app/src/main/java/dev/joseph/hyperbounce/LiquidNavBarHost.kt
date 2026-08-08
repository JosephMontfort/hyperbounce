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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
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

// Deep search to find the icon view inside the ProxyFrameLayout
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
    // Translucency fix: increased opacity and added a subtle border below
    val containerColor = if (isDark) Color(0x77121212) else Color(0x77FAFAFA)
    val accentColor = if (isDark) Color.White else Color.Black
    
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    // Requested Order: Home, Reels, Messages, Search, Profile
    val orderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")
    
    val tabs = remember(stockBar) {
        if (stockBar != null) extractTabs(stockBar, orderedTabNames) else emptyList()
    }

    // Two-Way Sync: Poll Instagram's hidden tabs to detect physical screen swipes
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .width(320.dp)
                .height(64.dp)
                .drawBackdrop(
                    backdrop = rememberLayerBackdrop(),
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(24f.dp.toPx()) // Removed Lens temporarily to guarantee visibility
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .border(1.dp, Color(0x22FFFFFF), Capsule())
        ) {
            if (tabs.isNotEmpty()) {
                val density = LocalDensity.current
                val tabWidthDp = 320f / tabs.size
                val tabWidthPx = with(density) { tabWidthDp.dp.toPx() }
                
                var isPressed by remember { mutableStateOf(false) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                // Determine target offset based on whether user is actively dragging
                val targetOffset = if (isPressed) dragOffset else selectedIndex * tabWidthPx
                val animatedOffsetPx by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
                )
                
                // Expand pill width when long pressed
                val animatedWidthDp by animateFloatAsState(
                    targetValue = if (isPressed) tabWidthDp * 1.5f else tabWidthDp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
                )

                // The Interactive Liquid Pill
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                            .width(animatedWidthDp.dp)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .clip(Capsule())
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x22000000))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    isPressed = true
                                    dragOffset = selectedIndex * tabWidthPx
                                    
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null && change.pressed) {
                                            dragOffset = (dragOffset + change.positionChange().x)
                                                .coerceIn(0f, (tabs.size - 1) * tabWidthPx)
                                            change.consume()
                                        } else {
                                            break
                                        }
                                    }
                                    
                                    isPressed = false
                                    // Snap to nearest tab on release
                                    val nearestIndex = (dragOffset / tabWidthPx).roundToInt().coerceIn(0, tabs.size - 1)
                                    selectedIndex = nearestIndex
                                    stockBar?.findViewById<View>(tabs[nearestIndex].resId)?.performClick()
                                }
                            }
                    )
                }

                // Render the Extracted Icons over the Pill
                Row(
                    modifier = Modifier.fillMaxSize(),
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
                                Image(
                                    bitmap = tab.bitmap,
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape) // Forces profile icon and others into a perfect circle
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
