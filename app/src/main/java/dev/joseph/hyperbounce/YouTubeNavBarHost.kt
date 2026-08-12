package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class YouTubeTabData(val sourceView: View, val inactiveBitmap: Bitmap?, val activeBitmap: Bitmap?)

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
            if (tab.visibility == View.GONE) continue

            var img = findViewByName(tab, "image") as? ImageView
            if (img == null) img = findViewByName(tab, "pivot_bar_thumbnail") as? ImageView
            if (img == null) img = findImageView(tab)

            var inactiveBmp: Bitmap? = null
            var activeBmp: Bitmap? = null
            val isProfile = i == wrapper.childCount - 1

            if (isProfile && img != null) {
                val w = img.width.coerceAtLeast(1)
                val h = img.height.coerceAtLeast(1)
                if (w > 1 && h > 1) {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    img.draw(Canvas(bmp))
                    inactiveBmp = bmp
                    activeBmp = bmp
                }
            } else if (img != null) {
                val d = img.drawable?.constantState?.newDrawable()?.mutate()
                if (d != null) {
                    d.setTint(tint)
                    val w = d.intrinsicWidth.coerceAtLeast(1)
                    val h = d.intrinsicHeight.coerceAtLeast(1)
                    
                    d.state = intArrayOf(-android.R.attr.state_selected, -android.R.attr.state_activated)
                    inactiveBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(inactiveBmp))
                    
                    d.state = intArrayOf(android.R.attr.state_selected, android.R.attr.state_activated)
                    activeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, w, h)
                    d.draw(Canvas(activeBmp))
                }
            }
            
            out.add(YouTubeTabData(tab, inactiveBmp, activeBmp))
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
            delay(500)
        }
    }
    
    val accent = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    val profileIndex = if (tabs.isNotEmpty()) tabs.size - 1 else -1

    LiquidNavigationBar(
        bottomPadding = 28.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap },
        customAccentColor = accent,
        circularIndices = if (profileIndex >= 0) setOf(profileIndex) else emptySet(),
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            clickStockTab(pivotBar, index)
        },
        sourceView = backdropSource,
        forceLightTheme = !isDark,
    )
}
