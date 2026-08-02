package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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

// HOME CLICK FIX: Simulates a literal hardware finger touch on the exact center of the tab
fun simulateHardwareTouch(view: View) {
    val x = view.width / 2f
    val y = view.height / 2f
    val downTime = SystemClock.uptimeMillis()
    
    val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    view.dispatchTouchEvent(downEvent)
    downEvent.recycle()

    val upEvent = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
    view.dispatchTouchEvent(upEvent)
    upEvent.recycle()
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
            
            // BACK BUTTON SYNC FIX: Constantly checks YouTube's internal activation state
            if (tabs.isNotEmpty()) {
                for (i in tabs.indices) {
                    if (tabs[i].sourceView.isSelected || tabs[i].sourceView.isActivated) {
                        if (selectedIndex != i) {
                            selectedIndex = i
                        }
                        break
                    }
                }
            }
            delay(100) // 10fps sync loop
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    // Force strict contrast colors to prevent neon green Miuix defaults
    val isDark = isSystemInDarkTheme()
    val forcedColors = if (isDark) darkColorScheme(primary = Color.White, onSurface = Color.White) 
                       else lightColorScheme(primary = Color.Black, onSurface = Color.Black)

    MiuixTheme(colors = forcedColors) {
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
                    // FAUX-GLASS PILL: Beautiful translucency without GPU green-screen crashes
                    val glassColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
                    
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .shadow(12.dp, CircleShape)
                            .background(glassColor, CircleShape)
                    ) {
                        FloatingBottomBar(
                            selectedIndex = { selectedIndex },
                            onSelected = { idx ->
                                selectedIndex = idx
                                if (externalTabState.value != idx && idx < tabs.size) {
                                    simulateHardwareTouch(tabs[idx].sourceView)
                                }
                            },
                            backdrop = rememberLayerBackdrop(),
                            tabsCount = tabs.size,
                            isBlurEnabled = false, // True blur causes Qualcomm DRM Green-Screen
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
                                        // BRIGHTNESS FIX: Force icons to pure White/Black
                                        colorFilter = ColorFilter.tint(if (isDark) Color.White else Color.Black)
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
