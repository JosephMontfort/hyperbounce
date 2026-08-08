package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.delay

data class TabData(val resId: Int, val bitmap: ImageBitmap?)

// Scrapes the original icons from Instagram's hidden tab bar in the requested order
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

// Deep search to find the ColorFilterAlphaImageView inside the ProxyFrameLayout
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
    val backdrop = rememberLayerBackdrop()
    val isDark = isSystemInDarkTheme()
    val accentColor = if (isDark) Color.White else Color.Black
    
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    // The exact visual order you requested
    val orderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")
    
    val tabs = remember(stockBar) {
        if (stockBar != null) extractTabs(stockBar, orderedTabNames) else emptyList()
    }

    // TWO-WAY SYNC: Poll Instagram's hidden tabs to detect swipe changes
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
        // Pushed slightly higher to clear the gesture pill
        Box(modifier = Modifier.padding(bottom = 16.dp)) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedIndex },
                onTabSelected = { index ->
                    selectedIndex = index
                    stockBar?.findViewById<View>(tabs[index].resId)?.performClick()
                },
                backdrop = backdrop,
                tabsCount = tabs.size,
                modifier = Modifier.width(320.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    LiquidBottomTab(
                        onClick = {
                            selectedIndex = index
                            stockBar?.findViewById<View>(tab.resId)?.performClick()
                        }
                    ) {
                        if (tab.bitmap != null) {
                            Image(
                                bitmap = tab.bitmap,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(
                                    if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
