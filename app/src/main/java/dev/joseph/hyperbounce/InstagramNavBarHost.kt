package dev.joseph.hyperbounce

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
import androidx.compose.ui.geometry.Offset
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
fun InstagramNavBarHost(
    stockBar: ViewGroup?,
    backdropSource: View,
    bottomPadding: androidx.compose.ui.unit.Dp = 40.dp,
    backdropWindowOriginProvider: (() -> Offset)? = null,
) {
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

    val profileIndex = tabs.indexOfFirst { it.resId == stockBar?.context?.resources?.getIdentifier("profile_tab", "id", stockBar?.context?.packageName) }
    val accent = if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    val backdropWindowOrigin = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(backdropWindowOriginProvider) {
        if (backdropWindowOriginProvider != null) {
            while (true) {
                backdropWindowOrigin.value = backdropWindowOriginProvider()
                kotlinx.coroutines.delay(250)
            }
        }
    }

    LiquidNavigationBar(
        bottomPadding = bottomPadding,
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
        backdropWindowOrigin = backdropWindowOrigin.value,
    )
}

internal fun injectLiquidGlass(
    activity: Activity,
    mainWrapper: ViewGroup,
    stockBar: ViewGroup?,
): InstagramOverlayWindow? {
    activity.runOnUiThread {
        // The Instagram glass bar lives in a separate application-panel surface.
        // This keeps it out of the activity SurfaceControl tree used for backdrop
        // capture, while the panel remains directly touchable at the bar bounds.
        try {
            val existing = InstagramOverlayRegistry.get(activity)
            if (existing != null) {
                existing.setVisible(true)
                return@runOnUiThread
            }

            val controller = InstagramOverlayWindow(activity, stockBar)
            InstagramOverlayRegistry.put(activity, controller)
            controller.setVisible(true)
        } catch (_: Throwable) {}
    }
    return InstagramOverlayRegistry.get(activity)
}

internal object InstagramOverlayRegistry {
    private val controllers = java.util.WeakHashMap<Activity, InstagramOverlayWindow>()

    @Synchronized
    fun get(activity: Activity): InstagramOverlayWindow? = controllers[activity]

    @Synchronized
    fun put(activity: Activity, controller: InstagramOverlayWindow) {
        controllers[activity] = controller
    }
}
