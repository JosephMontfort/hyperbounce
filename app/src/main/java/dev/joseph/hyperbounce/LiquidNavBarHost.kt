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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

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
    val containerColor = if (isDark) Color(0xFF121212).copy(alpha = 0.45f) else Color(0xFFFAFAFA).copy(alpha = 0.65f)
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
        ) {
            val pillBackdrop = rememberLayerBackdrop()
            val tabWidthDp = 320f / (if (tabs.isNotEmpty()) tabs.size else 1)
            
            val indicatorOffsetDp by animateFloatAsState(
                targetValue = selectedIndex * tabWidthDp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Render the animated Kyant Liquid Pill
                if (tabs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffsetDp.dp)
                            .width(tabWidthDp.dp)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .drawBackdrop(
                                backdrop = pillBackdrop,
                                shape = { Capsule() },
                                effects = {
                                    vibrancy()
                                    blur(16f.dp.toPx())
                                    lens(24f.dp.toPx(), 36f.dp.toPx(), depthEffect = true)
                                },
                                onDrawSurface = { drawRect(containerColor) }
                            )
                    )
                }

                // Render the Extracted Icons
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
                                    targetValue = if (selectedIndex == index) 1.2f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                                )
                                Image(
                                    bitmap = tab.bitmap,
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.5f)
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
}
