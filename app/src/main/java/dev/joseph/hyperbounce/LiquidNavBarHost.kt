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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        val isDark = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                     android.content.res.Configuration.UI_MODE_NIGHT_YES
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
                    safeClone.setTint(tintColor)
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

fun isViewOrChildSelected(view: View): Boolean {
    if (view.isSelected || view.isActivated) return true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            if (isViewOrChildSelected(view.getChildAt(i))) return true
        }
    }
    return false
}

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

    // Bug 3 – light-theme icon colours:
    // extractYouTubeTabs tints icons WHITE (dark) or BLACK (light) at capture time.
    // When the user flips the theme we must re-capture with the new tint colour.
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { tabs = emptyList() }

    // Bug 4 – home snap-back:
    // After the user taps a tab, YouTube's internal selected-state lags ~300 ms behind.
    // If we keep polling isViewOrChildSelected during that window we read "Shorts" and
    // snap selectedIndex back.  Suppress native-sync for 700 ms after any user tap.
    var lastClickMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(stockBar) {
        var attempts = 0
        while (true) {
            // Keep trying until we have all tabs (max 20 attempts × 100 ms = 2 s).
            if (tabs.size < 4 && attempts < 20) {
                val extracted = extractYouTubeTabs(stockBar)
                if (extracted.size > tabs.size) tabs = extracted
                attempts++
            }

            // Native-state sync — debounced so a user tap isn't overridden immediately.
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
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) selectedIndex = externalIdx
    }

    MiuixTheme {
        AnimatedVisibility(
            visible = isVisible.value,
            enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
            exit  = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(tween(300)),
        ) {
            if (tabs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Bug 1 – indicator clipped at top:
                    // The indicator pill scale-animates beyond its 56 dp measured height.
                    // padding(top = 14.dp) gives it 14 dp of headroom inside the ComposeView
                    // so the expansion can't escape the view bounds upward.
                    // (The ComposeView is WRAP_CONTENT + gravity=BOTTOM, so adding top padding
                    //  simply makes it taller while keeping the pill at the same screen Y.)
                    Box(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)) {
                        FloatingBottomBar(
                            selectedIndex = { selectedIndex },
                            onSelected = { idx ->
                                lastClickMs = System.currentTimeMillis() // start debounce window
                                selectedIndex = idx
                                if (externalTabState.value != idx && idx < tabs.size) {
                                    forceClick(tabs[idx].sourceView)
                                }
                            },
                            backdrop     = rememberLayerBackdrop(),
                            tabsCount    = tabs.size,
                            isBlurEnabled = true,  // liquid glass on
                        ) {
                            tabs.forEachIndexed { index, tabData ->
                                FloatingBottomBarItem(
                                    onClick   = { selectedIndex = index },
                                    modifier  = Modifier.defaultMinSize(minWidth = 60.dp),
                                ) {
                                    Image(
                                        bitmap             = tabData.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier           = Modifier.size(24.dp),
                                        alpha              = if (selectedIndex == index) 1f else 0.5f,
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
