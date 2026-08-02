package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

    if (tabs.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // ZERO SUSPECTS: A purely opaque, hardcoded dark grey pill. No blur libraries.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF1E1E1E)), // Solid Dark Grey
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabData ->
                    val interactionSource = remember { MutableInteractionSource() }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                selectedIndex = index
                                if (externalTabState.value != index) {
                                    tabData.sourceView.performClick()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = tabData.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            alpha = if (selectedIndex == index) 1f else 0.6f // Simple opacity state
                        )
                    }
                }
            }
        }
    }
}
