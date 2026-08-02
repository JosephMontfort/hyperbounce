package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TabData(val bitmap: Bitmap, val sourceView: View)

fun findViewByName(view: View, name: String): View? {
    if (view.id != View.NO_ID) {
        try {
            if (view.resources.getResourceEntryName(view.id) == name) return view
        } catch (e: Exception) {}
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findViewByName(view.getChildAt(i), name)
            if (found != null) return found
        }
    }
    return null
}

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        
        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue
            val thumbnailLayout = findViewByName(tab, "thumbnail_layout") as? ViewGroup ?: continue
            
            var imgView = findViewByName(thumbnailLayout, "image") as? ImageView
            if (imgView == null) {
                imgView = findViewByName(thumbnailLayout, "pivot_bar_thumbnail") as? ImageView
            }
            
            if (imgView?.drawable != null) {
                val drawable = imgView.drawable.mutate()
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                
                out.add(TabData(bmp, tab)) 
            }
        }
    } catch (e: Exception) {}
    return out
}

@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
) {
    var tabs by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    LaunchedEffect(stockBar) {
        while (tabs.size < 4) {
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.size > tabs.size) {
                tabs = extracted
            }
            delay(300)
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        if (tabs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingBottomBar(
                    selectedIndex = { selectedIndex },
                    onSelected = { idx ->
                        selectedIndex = idx
                        if (externalTabState.value != idx && idx < tabs.size) {
                            tabs[idx].sourceView.performClick()
                        }
                    },
                    backdrop = rememberLayerBackdrop(), // CRITICAL FIX: Add missing backdrop parameter
                    tabsCount = tabs.size,
                    isBlurEnabled = false,
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        FloatingBottomBarItem(
                            onClick = { selectedIndex = index },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                        ) {
                            Image(
                                bitmap = tabData.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
