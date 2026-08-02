package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TabData(val sourceView: View, val bitmap: Bitmap)

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
        val uiMode = pivotBar.resources.configuration.uiMode
        val isDark = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        // VISIBILITY FIX: Hardware level tint to force icons to pure White/Black!
        val tintColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        for (i in 0 until wrapper.childCount) {
            val tab = wrapper.getChildAt(i) as? ViewGroup ?: continue
            val thumbnailLayout = findViewByName(tab, "thumbnail_layout") as? ViewGroup ?: continue
            
            var imgView = findViewByName(thumbnailLayout, "image") as? ImageView
            if (imgView == null) {
                imgView = findViewByName(thumbnailLayout, "pivot_bar_thumbnail") as? ImageView
            }
            
            val liveDrawable = imgView?.drawable
            if (liveDrawable != null) {
                val safeClone = liveDrawable.constantState?.newDrawable()?.mutate()
                if (safeClone != null) {
                    safeClone.setTint(tintColor) // Apply the bright tint!
                    val w = safeClone.intrinsicWidth.coerceAtLeast(1)
                    val h = safeClone.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    safeClone.setBounds(0, 0, w, h)
                    safeClone.draw(canvas)
                    
                    out.add(TabData(tab, bmp)) 
                }
            }
        }
    } catch (e: Exception) {}
    return out
}

// HOME BUG FIX 1: Recursively check all children because YouTube hides the "selected" state deep inside the view tree!
fun isViewOrChildSelected(view: View): Boolean {
    if (view.isSelected || view.isActivated) return true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            if (isViewOrChildSelected(view.getChildAt(i))) return true
        }
    }
    return false
}

// HOME BUG FIX 2: Accessibility click bypasses touch interception
fun forceClick(view: View) {
    view.performClick()
    view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            child.performClick()
            child.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        }
    }
}

@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
    isVisible: State<Boolean>
) {
    var tabs by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    LaunchedEffect(stockBar) {
        var attempts = 0
        while (true) {
            if (tabs.size < 4 && attempts < 20) {
                val extracted = extractYouTubeTabs(stockBar)
                if (extracted.size > tabs.size) {
                    tabs = extracted 
                }
                attempts++
            }
            
            // Sync with YouTube's deep native state to stop the snap-back bug!
            if (tabs.isNotEmpty()) {
                for (i in tabs.indices) {
                    if (isViewOrChildSelected(tabs[i].sourceView)) {
                        if (selectedIndex != i) {
                            selectedIndex = i
                        }
                        break
                    }
                }
            }
            delay(100)
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        AnimatedVisibility(
            visible = isVisible.value,
            enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(tween(300))
        ) {
            if (tabs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val isDark = isSystemInDarkTheme()
                    // VISIBILITY & OUTLINE FIX: 95% opacity for readability, sharp shadow removed, clean 1.5dp border added!
                    val pillColor = if (isDark) Color(0xF21C1C1E) else Color(0xF2F2F2F7)
                    val outlineColor = if (isDark) Color(0x4DFFFFFF) else Color(0x4D000000)
                    
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .border(1.5.dp, outlineColor, CircleShape)
                            .background(pillColor, CircleShape)
                    ) {
                        FloatingBottomBar(
                            selectedIndex = { selectedIndex },
                            onSelected = { idx ->
                                selectedIndex = idx
                                if (externalTabState.value != idx && idx < tabs.size) {
                                    forceClick(tabs[idx].sourceView) // Fire the ultimate clicker
                                }
                            },
                            backdrop = rememberLayerBackdrop(),
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
                                        modifier = Modifier.size(24.dp),
                                        alpha = if (selectedIndex == index) 1f else 0.5f
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
