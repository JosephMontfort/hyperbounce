package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class YouTubeTabData(val sourceView: View, val bitmap: Bitmap)

private fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

private fun findViewByName(view: View, name: String): View? {
    if (view.id != View.NO_ID) {
        try {
            if (view.resources.getResourceEntryName(view.id) == name) return view
        } catch (e: Exception) {}
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findViewByName(view.getChildAt(i), name)?.let { return it }
        }
    }
    return null
}

private fun extractYouTubeTabs(pivotBar: ViewGroup, isDark: Boolean): List<YouTubeTabData> {
    val out = mutableListOf<YouTubeTabData>()
    try {
        val tint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out

        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue

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
            out.add(YouTubeTabData(tab, bmp))
        }
    } catch (e: Exception) {}
    return out
}

private fun clickStockTab(pivotBar: ViewGroup, index: Int) {
    val wrapper = pivotBar.getChildAt(0) as? ViewGroup
    wrapper?.getChildAt(index)?.performClick()
}

@Composable
fun YouTubeNavBarHost(
    pivotBar: ViewGroup,
    externalTabState: State<Int>,
    isDarkState: State<Boolean>,
    backdropSource: View,
) {
    val isDark = isDarkState.value

    var tabs by remember { mutableStateOf(emptyList<YouTubeTabData>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(pivotBar, isDark) {
        while (true) {
            val extracted = extractYouTubeTabs(pivotBar, isDark)
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

    LiquidNavigationBar(
        bottomPadding = 28.dp,
        tabs = tabs.map { it.bitmap },
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            clickStockTab(pivotBar, index)
        },
        sourceView = backdropSource,
        forceLightTheme = !isDark,
    )
}
