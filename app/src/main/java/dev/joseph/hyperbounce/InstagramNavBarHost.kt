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
import androidx.compose.runtime.mutableStateOf
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

private data class InstagramTabData(val resId: Int, val inactiveBitmap: Bitmap?, val activeBitmap: Bitmap?)

private val instagramOrderedTabNames = listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab")

private fun extractInstagramTabs(stockBar: ViewGroup, tabNames: List<String>, isDark: Boolean): List<InstagramTabData> {
    val context = stockBar.context
    val tint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    return tabNames.map { name ->
        val resId = context.resources.getIdentifier(name, "id", context.packageName)
        var inactiveBmp: Bitmap? = null
        var activeBmp: Bitmap? = null
        
        if (resId != 0) {
            val tabView = stockBar.findViewById<View>(resId)
            if (tabView is ViewGroup) {
                val imageView = findImageView(tabView)
                val drawable = imageView?.drawable?.constantState?.newDrawable()?.mutate()
                val isProfile = name == "profile_tab"
                
                if (drawable != null) {
                    if (!isProfile) {
                        drawable.setTint(tint)
                    }
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    
                    drawable.state = intArrayOf(-android.R.attr.state_selected, -android.R.attr.state_activated)
                    inactiveBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(inactiveBmp))
                    
                    drawable.state = intArrayOf(android.R.attr.state_selected, android.R.attr.state_activated)
                    activeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(activeBmp))
                } else if (isProfile && imageView != null) {
                    val w = imageView.width.coerceAtLeast(1)
                    val h = imageView.height.coerceAtLeast(1)
                    if (w > 1 && h > 1) {
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        imageView.draw(Canvas(bmp))
                        inactiveBmp = bmp
                        activeBmp = bmp
                    }
                }
            }
        }
        InstagramTabData(resId, inactiveBmp, activeBmp)
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

    val profileIndex = instagramOrderedTabNames.indexOf("profile_tab")
    val accent = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black

    LiquidNavigationBar(
        bottomPadding = 40.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap },
        customAccentColor = accent,
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
        } catch (e: Exception) {}
    }
}
