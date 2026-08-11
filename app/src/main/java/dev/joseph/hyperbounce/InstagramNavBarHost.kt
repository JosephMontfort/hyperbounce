package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

private data class InstagramTabData(val resId: Int, val bitmap: Bitmap?)

private val instagramOrderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")

private fun extractInstagramTabs(stockBar: ViewGroup, tabNames: List<String>): List<InstagramTabData> {
    val context = stockBar.context
    return tabNames.map { name ->
        val resId = context.resources.getIdentifier(name, "id", context.packageName)
        var bitmap: Bitmap? = null
        if (resId != 0) {
            val tabView = stockBar.findViewById<View>(resId)
            if (tabView is ViewGroup) {
                val imageView = findImageView(tabView)
                imageView?.drawable?.let { drawable ->
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap = bmp
                }
            }
        }
        InstagramTabData(resId, bitmap)
    }
}

private fun findImageView(group: ViewGroup): ImageView? {
    for (i in 0 until group.childCount) {
        val v = group.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) {
            val iv = findImageView(v)
            if (iv != null) return iv
        }
    }
    return null
}

@Composable
fun InstagramNavBarHost(stockBar: ViewGroup?, backdropSource: View) {
    val isDark = isSystemInDarkTheme()

    var selectedIndex by remember { mutableIntStateOf(0) }

    val tabs = remember(stockBar) {
        if (stockBar != null) extractInstagramTabs(stockBar, instagramOrderedTabNames) else emptyList()
    }

    LaunchedEffect(stockBar) {
        while (true) {
            delay(100)
            if (stockBar != null) {
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
        }
    }

    val profileIndex = instagramOrderedTabNames.indexOf("profile_tab")

    LiquidNavigationBar(
        bottomPadding = 40.dp,
        tabs = tabs.map { it.bitmap },
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            stockBar?.findViewById<View>(tabs[index].resId)?.performClick()
        },
        circularIndices = if (profileIndex >= 0) setOf(profileIndex) else emptySet(),
        sourceView = backdropSource,
    )
}

/** Injects the Compose overlay into Instagram's main content wrapper. */
fun injectLiquidGlass(activity: Activity, mainWrapper: ViewGroup, stockBar: ViewGroup?) {
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
                    InstagramNavBarHost(stockBar, mainWrapper)
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
            android.util.Log.e("HyperBounce-Tracer", "[SUCCESS] Liquid Glass Compose UI Injected!")
        } catch (e: Exception) {
            android.util.Log.e("HyperBounce-Tracer", "[ERROR] Compose Injection Failed: ${e.message}")
        }
    }
}
