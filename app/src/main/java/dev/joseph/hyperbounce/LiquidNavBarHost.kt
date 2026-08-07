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

// ── View helpers ──────────────────────────────────────────────────────────────

fun findViewByName(view: View, name: String): View? {
    if (view.id != View.NO_ID) {
        try { if (view.resources.getResourceEntryName(view.id) == name) return view }
        catch (_: Exception) {}
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount)
            findViewByName(view.getChildAt(i), name)?.let { return it }
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
            val tab   = wrapper.getChildAt(i) as? ViewGroup ?: continue
            val thumb = findViewByName(tab, "thumbnail_layout") as? ViewGroup ?: continue
            var img   = findViewByName(thumb, "image")              as? ImageView
            if (img == null) img = findViewByName(thumb, "pivot_bar_thumbnail") as? ImageView
            val d = img?.drawable?.constantState?.newDrawable()?.mutate() ?: continue
            d.setTint(tint)
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h); d.draw(Canvas(bmp))
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

// ── THE DEFINITIVE HOME-TAB FIX ──────────────────────────────────────────────
//
// Diagnostic logs proved that all click approaches (performClick,
// performAccessibilityAction, simulateRootTouch) fail for Home because they
// bypass PivotBar's internal gesture chain.
//
// Natural tap chain (from logs):
//   PivotBar.dispatchTouchEvent(PG:88)
//   → atca.f (gesture helper, sets state owd.onClick depends on)
//   → GestureDetector.onTouchEvent
//   → aucq.onSingleTapUp          ← determines WHICH tab from X coordinate
//   → Flienmp.performClick
//   → owd.onClick                 ← the navigation handler
//
// Fix: dispatch a real MotionEvent (DOWN + UP) directly to PivotBar at the
// target tab's LOCAL X coordinate.  This triggers the complete native chain
// identically to a real finger tap — for ALL tabs including Home.
//
// Notes on collapsed container (container.layoutParams.height = 0):
// - X positions of tabs are unaffected (only height was changed)
// - pivotBar.dispatchTouchEvent is called directly, bypassing window
//   hit-testing entirely, so alpha=0f and height=0 are irrelevant
// - PivotBar's custom dispatchTouchEvent override runs regardless
// - The GestureDetector uses only X to identify which tab was tapped

fun simulatePivotBarTap(pivotBar: ViewGroup, targetTab: View) {
    try {
        // Compute tab centre in PivotBar's local coordinate space.
        val pivotLoc = IntArray(2)
        pivotBar.getLocationInWindow(pivotLoc)
        val tabLoc = IntArray(2)
        targetTab.getLocationInWindow(tabLoc)

        val localX = (tabLoc[0] - pivotLoc[0] + targetTab.width / 2).toFloat()
        // Y only needs to be a non-negative number; PivotBar uses X for tab
        // identification.  When the container is collapsed to 0 height we
        // use 0f — PivotBar's custom code still runs.
        val localY = if (pivotBar.height > 0) pivotBar.height / 2f else 0f

        val t    = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t,       MotionEvent.ACTION_DOWN, localX, localY, 0)
        val up   = MotionEvent.obtain(t, t + 80L, MotionEvent.ACTION_UP,   localX, localY, 0)
        pivotBar.dispatchTouchEvent(down)
        pivotBar.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
    } catch (_: Exception) {
        // Fallback: at least fire a raw click so something happens
        targetTab.performClick()
    }
}

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun LiquidNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
    isVisible: State<Boolean>,
) {
    var tabs          by remember { mutableStateOf(emptyList<TabData>()) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }

    // Re-extract icons whenever the theme flips so tint colour is correct
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { tabs = emptyList() }

    // Debounce: suppress native-state sync for 700 ms after a user tap so
    // YouTube's internal selection state has time to settle.
    var lastClickMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(stockBar) {
        var stableCount = 0
        while (true) {
            val extracted = extractYouTubeTabs(stockBar)
            if (extracted.size > tabs.size) { tabs = extracted; stableCount = 0 }
            else stableCount++

            // After 10 stable cycles (1 s) slow down to a 5 s re-check.
            if (tabs.isNotEmpty() && stableCount >= 10) {
                delay(5_000); stableCount = 0; continue
            }

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
                    // Light-mode outline: gives the pill a visible edge on bright
                    // backgrounds.  Transparent (invisible) in dark mode.
                    val pillOutline = if (isDark) Color.Transparent
                                     else        Color.Black.copy(alpha = 0.20f)

                    // padding(top = 14.dp): extra headroom so the indicator pill's
                    // press-scale animation doesn't clip at the top of the ComposeView.
                    Box(
                        modifier = Modifier
                            .padding(top = 14.dp, bottom = 12.dp)
                            .border(1.dp, pillOutline, CircleShape)
                    ) {
                        FloatingBottomBar(
                            selectedIndex = { selectedIndex },
                            onSelected    = { idx ->
                                lastClickMs   = System.currentTimeMillis()
                                selectedIndex = idx
                                if (externalTabState.value != idx && idx < tabs.size) {
                                    // Dispatch directly into PivotBar's gesture chain.
                                    // This is the ONLY approach that works for Home tab
                                    // (and all other tabs) — see simulatePivotBarTap above.
                                    simulatePivotBarTap(
                                        pivotBar  = stockBar,
                                        targetTab = tabs[idx].sourceView,
                                    )
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
