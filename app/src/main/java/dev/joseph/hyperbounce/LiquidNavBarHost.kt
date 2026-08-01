package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop

data class TabData(val bitmap: Bitmap?)

fun extractTabs(stockBar: ViewGroup): List<TabData> {
    val tabs = mutableListOf<TabData>()
    val menuContainer = stockBar.getChildAt(0) as? ViewGroup ?: return tabs
    for (i in 0 until menuContainer.childCount) {
        val tabView = menuContainer.getChildAt(i) as? ViewGroup ?: continue
        val iconView = findImageView(tabView)
        val drawable = iconView?.drawable?.mutate()
        var bmp: Bitmap? = null
        if (drawable != null) {
            drawable.setTintList(null)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        tabs.add(TabData(bmp))
    }
    return tabs
}

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

fun clickStockTab(stockBar: ViewGroup, index: Int) {
    val menuContainer = stockBar.getChildAt(0) as? ViewGroup ?: return
    val tab = menuContainer.getChildAt(index)
    tab?.performClick()
}

@Composable
fun LiquidNavBarHost(stockBar: ViewGroup) {
    var selectedIndex by remember { mutableIntStateOf(NavSync.externalSelectedTab.intValue) }
    val tabs = remember { extractTabs(stockBar) }
    
    // Sync external swipes from WhatsApp
    LaunchedEffect(NavSync.externalSelectedTab.intValue) {
        val externalIdx = NavSync.externalSelectedTab.intValue
        if (selectedIndex != externalIdx) {
            selectedIndex = externalIdx
        }
    }

    val dummyBackdrop = emptyBackdrop()

    Box(
        modifier = Modifier.fillMaxSize(), // Matches LayoutParams exact bounds
        contentAlignment = Alignment.Center
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = { idx -> 
                selectedIndex = idx
                if (NavSync.externalSelectedTab.intValue != idx) {
                    NavSync.externalSelectedTab.intValue = idx
                    clickStockTab(stockBar, idx)
                }
            },
            backdrop = dummyBackdrop,
            tabsCount = tabs.size,
            modifier = Modifier.fillMaxSize()
        ) {
            tabs.forEachIndexed { index, tabData ->
                LiquidBottomTab(
                    onClick = {
                        selectedIndex = index
                        if (NavSync.externalSelectedTab.intValue != index) {
                            NavSync.externalSelectedTab.intValue = index
                            clickStockTab(stockBar, index)
                        }
                    }
                ) {
                    if (tabData.bitmap != null) {
                        Image(
                            bitmap = tabData.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            // LocalTabColor allows the liquid layer to transition smoothly
                            colorFilter = ColorFilter.tint(LocalTabColor.current)
                        )
                    }
                }
            }
        }
    }
}
