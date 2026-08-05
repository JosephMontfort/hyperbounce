package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
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

// ── Data ─────────────────────────────────────────────────────────────────────
data class TabData(val sourceView: View, val bitmap: Bitmap)

// ── View helpers ─────────────────────────────────────────────────────────────
fun findViewByName(view: View, name: String): View? {
    if (view.id != View.NO_ID) {
        try { if (view.resources.getResourceEntryName(view.id) == name) return view }
        catch (_: Exception) {}
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findViewByName(view.getChildAt(i), name)?.let { return it }
        }
    }
    return null
}

fun extractYouTubeTabs(pivotBar: ViewGroup): List<TabData> {
    val out = mutableListOf<TabData>()
    try {
        val uiMode = pivotBar.resources.configuration.uiMode
        val isDark  = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                       android.content.res.Configuration.UI_MODE_NIGHT_YES
        val tint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val wrapper = pivotBar.getChildAt(0) as? ViewGroup ?: return out
        for (i in 0 until wrapper.childCount) {
            val tab    = wrapper.getChildAt(i) as? ViewGroup ?: continue
            val thumb  = findViewByName(tab, "thumbnail_layout") as? ViewGroup ?: continue
            var imgView = findViewByName(thumb, "image") as? ImageView
            if (imgView == null) imgView = findViewByName(thumb, "pivot_bar_thumbnail") as? ImageView
            val drawable = imgView?.drawable?.constantState?.newDrawable()?.mutate() ?: continue
            drawable.setTint(tint)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(Canvas(bmp))
            out.add(TabData(tab, bmp))
        }
    } catch (_: Exception) {}
    return out
}

fun isViewOrChildSelected(view: View): Boolean {
    if (view.isSelected || view.isActivated) return true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount)
            if (isViewOrChildSelected(view.getChildAt(i))) return true
    }
    return false
}

// ── Home-tab fix (from ZIP5) ─────────────────────────────────────────────────
// performClick/performAccessibilityAction fail for YouTube's Home tab because
// the host intercepts accessibility events.  Injecting a real MotionEvent pair
// at the tab's window coordinates bypasses all of that.
fun simulateRootTouch(sourceView: View) {
    val root = sourceView.rootView ?: return
    val loc  = IntArray(2)
    sourceView.getLocationInWindow(loc)
    val x = loc[0] + sourceView.width  / 2f
    val y = loc[1] + sourceView.height / 2f
    val t = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(t, t,      MotionEvent.ACTION_DOWN, x, y, 0)
    val up   = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP,   x, y, 0)
    root.dispatchTouchEvent(down)
    root.dispatchTouchEvent(up)
    down.recycle(); up.recycle()
}

// Kept as fallback for non-Home tabs where accessibility events work fine
fun forceClick(view: View) {
    view.performClick()
    view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            view.getChildAt(i).performClick()
            view.getChildAt(i).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        }
    }
}

// ── Composable ───────────────────────────────────────────────────────────────
@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
    isVisible: State<Boolean>,
) {
    var tabs         by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    // Re-extract icons whenever the theme flips so tint colour stays correct
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { tabs = emptyList() }

    // Home-tab snap-back guard: suppress native-sync for 700 ms after any user tap
    var lastClickMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(stockBar) {
        var stableCount = 0
        while (true) {
            // Keep extracting until tab count stabilises for 10 consecutive cycles.
            // No hard "must reach 5" limit — YouTube's Create (+) button has no
            // thumbnail_layout and is correctly skipped, leaving 4 real nav tabs.
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.size > tabs.size) {
                tabs = extracted
                stableCount = 0           // reset when we find more tabs
            } else {
                stableCount++
            }
            // After 10 stable cycles (1 s) with at least 1 tab, stop hammering the tree.
            // We will still respond to any future increase in tab count.
            if (tabs.isNotEmpty() && stableCount < 10) {
                // still stabilising — loop quickly
            } else if (tabs.isNotEmpty()) {
                // Stable — do a slow re-check every 5 s in case YouTube re-inflates tabs
                delay(5000)
                stableCount = 0
                continue
            }

            // Native-state sync — debounced so user taps are not overridden mid-navigation
            if (tabs.isNotEmpty() && System.currentTimeMillis() - lastClickMs > 700L) {
                for (i in tabs.indices) {
                    if (isViewOrChildSelected(tabs[i].sourceView)) {
                        if (selectedIndex != i) selectedIndex = i
                        break
                    }
                }
            }
            delay(100)
        }
    }

    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) { if (externalIdx != selectedIndex) selectedIndex = externalIdx }

    MiuixTheme {
        AnimatedVisibility(
            visible = isVisible.value,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit  = slideOutVertically(tween(300)) { it } + fadeOut(tween(300)),
        ) {
            if (tabs.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Light-mode outline: gives the frosted pill a visible edge on
                    // bright backgrounds without needing an opaque border in dark mode.
                    val pillOutline = if (isDark) Color.Transparent
                                     else        Color.Black.copy(alpha = 0.20f)

                    // padding(top=14.dp) gives the indicator pill 14 dp of headroom
                    // so its press-scale animation doesn't clip at the top of the
                    // ComposeView. The pill's absolute screen position is unchanged
                    // because the ComposeView is gravity=BOTTOM + WRAP_CONTENT.
                    Box(
                        modifier = Modifier
                            .padding(top = 14.dp, bottom = 12.dp)
                            .border(width = 1.dp, color = pillOutline, shape = CircleShape)
                    ) {
                        FloatingBottomBar(
                            selectedIndex = { selectedIndex },
                            onSelected    = { idx ->
                                lastClickMs   = System.currentTimeMillis()
                                selectedIndex = idx
                                if (externalTabState.value != idx && idx < tabs.size) {
                                    // forceClick uses performClick + AccessibilityAction — it works
                                    // through the accessibility framework and is unaffected by the
                                    // container height=0 collapse (unlike simulateRootTouch which
                                    // needs correct window coordinates).
                                    forceClick(tabs[idx].sourceView)
                                    // Home tab (idx 0) sometimes ignores accessibility clicks.
                                    // Fire a real MotionEvent as an extra belt-and-suspenders attempt.
                                    if (idx == 0) simulateRootTouch(tabs[idx].sourceView)
                                }
                            },
                            backdrop      = rememberLayerBackdrop(),
                            tabsCount     = tabs.size,
                            isBlurEnabled = true,
                        ) {
                            tabs.forEachIndexed { index, tabData ->
                                FloatingBottomBarItem(
                                    onClick  = { selectedIndex = index },
                                    modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                                ) {
                                    Image(
                                        bitmap             = tabData.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier           = Modifier.size(24.dp),
                                        alpha = if (selectedIndex == index) 1f else 0.5f,
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
