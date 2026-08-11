package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

private data class WhatsAppTabData(val bitmap: Bitmap?)

private fun extractWhatsAppTabs(stockBar: ViewGroup, isDark: Boolean): List<WhatsAppTabData> {
    val out = mutableListOf<WhatsAppTabData>()
    val menu = stockBar.getChildAt(0) as? ViewGroup ?: return out
    for (i in 0 until menu.childCount) {
        val tab = menu.getChildAt(i) as? ViewGroup ?: continue
        val icon = findImageView(tab)
        val drawable = icon?.drawable?.mutate()
        var bmp: Bitmap? = null
        if (drawable != null) {
            if (isDark) {
                drawable.setTint(android.graphics.Color.WHITE)
            } else {
                drawable.setTintList(null)
            }
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bmp))
        }
        out.add(WhatsAppTabData(bmp))
    }
    return out
}

private fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

private fun clickStockTab(stockBar: ViewGroup, index: Int) {
    (stockBar.getChildAt(0) as? ViewGroup)?.getChildAt(index)?.performClick()
}

@Composable
fun WhatsAppNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
    backdropSource: View,
) {
    val isDark = isSystemInDarkTheme()

    val tabs = remember(stockBar, isDark) { extractWhatsAppTabs(stockBar, isDark) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    // Sync WhatsApp swipe navigation to our indicator
    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) {
            selectedIndex = externalIdx
        }
    }

    LiquidNavigationBar(
        bottomPadding = 40.dp,
        tabs = tabs.map { it.bitmap },
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            // Fix: Trigger the actual WhatsApp tab switch only if it isn't already there
            if (externalTabState.value != index) {
                clickStockTab(stockBar, index)
            }
        },
        sourceView = backdropSource,
        containerAlpha = 0.15f,
        effectMultiplier = 1.6f,
    )
}
