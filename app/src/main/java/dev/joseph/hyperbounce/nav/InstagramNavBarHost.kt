package dev.joseph.hyperbounce.nav

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.FakeLifecycleOwner
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import dev.joseph.hyperbounce.prefs.rememberLiveNavBarSettings
import kotlinx.coroutines.delay

private data class InstagramTabData(val resId: Int, val inactiveBitmap: Bitmap?, val activeBitmap: Bitmap?)

private val instagramOrderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")

private fun extractInstagramTabs(stockBar: ViewGroup, tabNames: List<String>, isDark: Boolean): List<InstagramTabData> {
    val context = stockBar.context
    val tint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    return tabNames.mapNotNull { name ->
        val resId = context.resources.getIdentifier(name, "id", context.packageName)
        if (resId == 0) return@mapNotNull null
        val tabView = stockBar.findViewById<View>(resId) as? ViewGroup ?: return@mapNotNull null
        if (tabView.visibility == View.GONE) return@mapNotNull null

        var inactiveBmp: Bitmap? = null
        var activeBmp: Bitmap? = null
        val isProfile = name == "profile_tab"

        val ivs = mutableListOf<ImageView>()
        fun findImageViews(v: View) {
            if (v is ImageView) ivs.add(v)
            else if (v is ViewGroup) {
                for (i in 0 until v.childCount) findImageViews(v.getChildAt(i))
            }
        }
        findImageViews(tabView)

        if (isProfile && ivs.isNotEmpty()) {
            val img = ivs.first()
            val w = img.width.coerceAtLeast(1)
            val h = img.height.coerceAtLeast(1)
            if (w > 1 && h > 1) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                img.draw(Canvas(bmp))
                inactiveBmp = bmp
                activeBmp = bmp
            }
        } else if (ivs.isNotEmpty()) {
            val img = ivs.first()
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

        InstagramTabData(resId, inactiveBmp, activeBmp)
    }
}

@Composable
fun InstagramNavBarHost(stockBar: ViewGroup?, backdropSource: View, hyperBouncePrefs: HyperBouncePrefs) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "instagram")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var tabs by remember { mutableStateOf(emptyList<InstagramTabData>()) }

    LaunchedEffect(stockBar, isDark) {
        while (true) {
            if (stockBar != null) {
                tabs = extractInstagramTabs(stockBar, instagramOrderedTabNames, isDark)
                for (i in tabs.indices) {
                    val view = stockBar.findViewById<View>(tabs[i].resId)
                    if (view != null && (view.isSelected || view.isActivated)) {
                        if (selectedIndex != i) {
                            selectedIndex = i
                        }
                        break
                    }
                }
            }
            delay(500)
        }
    }

    val profileIndex = tabs.indexOfFirst { it.resId == stockBar?.context?.resources?.getIdentifier("profile_tab", "id", stockBar?.context?.packageName) }
    val accent = accentColorFor(live.accent, isDark)

    LiquidNavigationBar(
        bottomPadding = 40.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap },
        customAccentColor = accent,
        forceLightTheme = live.themeMode == "Light",
        forceDarkTheme = live.themeMode == "Dark",
        effectMultiplier = live.glassIntensity,
        containerAlpha = live.containerOpacity,
        cornerRadius = live.cornerRadius.dp,
        barHeight = live.barHeight.dp,
        tabWidth = live.tabWidth.dp,
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            if (tabs.isNotEmpty()) {
                stockBar?.findViewById<View>(tabs[index].resId)?.performClick()
            }
        },
        circularIndices = if (profileIndex >= 0) setOf(profileIndex) else emptySet(),
        sourceView = backdropSource,
    )
}

private fun accentColorFor(preset: String, isDark: Boolean): androidx.compose.ui.graphics.Color = when (preset) {
    "Blue" -> androidx.compose.ui.graphics.Color(0xFF0088FF)
    "Pink" -> androidx.compose.ui.graphics.Color(0xFFFF2D78)
    "Purple" -> androidx.compose.ui.graphics.Color(0xFFAF52DE)
    "Orange" -> androidx.compose.ui.graphics.Color(0xFFFF9500)
    "Green" -> androidx.compose.ui.graphics.Color(0xFF34C759)
    else -> if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
}

fun injectLiquidGlass(activity: Activity, mainWrapper: ViewGroup, stockBar: ViewGroup?, hyperBouncePrefs: HyperBouncePrefs) {
    val decorView = activity.window.decorView as ViewGroup
    if (decorView.findViewWithTag<ComposeView>("LiquidGlassInjected") != null) return

    activity.runOnUiThread {
        try {
            val owner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            decorView.setViewTreeLifecycleOwner(owner)
            decorView.setViewTreeSavedStateRegistryOwner(owner)
            decorView.setViewTreeViewModelStoreOwner(owner)

            val composeView = ComposeView(activity).apply {
                tag = "LiquidGlassInjected"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    InstagramNavBarHost(stockBar, mainWrapper, hyperBouncePrefs)
                }
            }

            val parentGroup = mainWrapper.parent as? ViewGroup
            if (parentGroup != null) {
                parentGroup.addView(
                    composeView,
                    parentGroup.indexOfChild(mainWrapper) + 1,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            } else {
                mainWrapper.addView(
                    composeView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        } catch (e: Exception) {}
    }
}
