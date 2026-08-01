package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.drawscope.DrawScope
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var selectedIndex by remember { mutableStateOf(0) }
    val tabs = remember { extractTabs(stockBar) }
    
    // CRITICAL FIX: Dummy Backdrop executes NO-OP. 
    // It prevents StackOverflow while allowing AGSL interactiveHighlights to render.
    val dummyBackdrop = remember {
        object : Backdrop {
            override val isCoordinatesDependent = false
            override val offsetResidualX = 0f
            override val offsetResidualY = 0f
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
                downscaleFactor: Int
            ) {
                // NO-OP. Do not draw UI tree.
            }
        }
    }
    
    MiuixTheme {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingBottomBar(
                selectedIndex = { selectedIndex },
                onSelected = { idx -> 
                    selectedIndex = idx
                    clickStockTab(stockBar, idx)
                },
                modifier = Modifier.fillMaxWidth(0.85f),
                backdrop = dummyBackdrop,
                tabsCount = tabs.size,
                isBlurEnabled = true
            ) {
                tabs.forEachIndexed { index, tabData ->
                    FloatingBottomBarItem(
                        onClick = {
                            selectedIndex = index
                            clickStockTab(stockBar, index)
                        }
                    ) {
                        if (tabData.bitmap != null) {
                            Image(
                                bitmap = tabData.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                colorFilter = ColorFilter.tint(LocalContentColor.current)
                            )
                        }
                    }
                }
            }
        }
    }
}
