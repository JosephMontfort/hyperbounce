package dev.joseph.hyperbounce.nav

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import dev.joseph.hyperbounce.prefs.rememberLiveNavBarSettings

private data class WhatsAppTabData(
    val inactiveBitmap: Bitmap?,
    val activeBitmap: Bitmap?,
    val label: String,
)

private fun extractWhatsAppTabs(stockBar: ViewGroup, isDark: Boolean): List<WhatsAppTabData> {
    val out = mutableListOf<WhatsAppTabData>()
    val menu = stockBar.getChildAt(0) as? ViewGroup ?: return out
    for (i in 0 until menu.childCount) {
        val tab = menu.getChildAt(i) as? ViewGroup ?: continue
        if (tab.visibility == View.GONE) continue
        val icon = findImageView(tab)
        var inactiveBmp: Bitmap? = null
        var activeBmp: Bitmap? = null
        val sourceDrawable = icon?.drawable?.constantState?.newDrawable()?.mutate()
        if (sourceDrawable != null) {
            if (isDark) {
                sourceDrawable.setTint(android.graphics.Color.WHITE)
            } else {
                sourceDrawable.setTintList(null)
            }

            val w = sourceDrawable.intrinsicWidth.coerceAtLeast(1)
            val h = sourceDrawable.intrinsicHeight.coerceAtLeast(1)

            sourceDrawable.state = intArrayOf(
                -android.R.attr.state_selected,
                -android.R.attr.state_activated,
            )
            inactiveBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            sourceDrawable.setBounds(0, 0, w, h)
            sourceDrawable.draw(Canvas(inactiveBmp))

            sourceDrawable.state = intArrayOf(
                android.R.attr.state_selected,
                android.R.attr.state_activated,
            )
            activeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            sourceDrawable.setBounds(0, 0, w, h)
            sourceDrawable.draw(Canvas(activeBmp))
        }
        val tv = findTextView(tab)
        val label = tv?.text?.toString() ?: ""
        out.add(WhatsAppTabData(inactiveBmp, activeBmp, label))
    }
    return out
}

private fun findTextView(g: ViewGroup): android.widget.TextView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is android.widget.TextView) return v
        if (v is ViewGroup) findTextView(v)?.let { return it }
    }
    return null
}

private fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) findImageView(v)?.let { return it }
    }
    return null
}

private fun clickStockTab(stockBar: ViewGroup, index: Int) {
    (stockBar.getChildAt(0) as? ViewGroup)?.getChildAt(index)?.performClick()
}

@Composable
fun WhatsAppNavBarHost(
    stockBar: ViewGroup,
    externalTabState: State<Int>,
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs,
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "whatsapp")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val tabs = remember(stockBar, isDark) { extractWhatsAppTabs(stockBar, isDark) }
    var selectedIndex by remember { mutableIntStateOf(externalTabState.value) }
    val activity = (backdropSource.context as? Activity) ?: (stockBar.context as? Activity)
    val isShrunkState = rememberScrollShrinkState(
        activity = activity,
        enabled = live.autoShrink,
        sensitivity = live.autoShrinkSensitivity
    )

    // Sync WhatsApp swipe navigation to our indicator
    val externalIdx by externalTabState
    LaunchedEffect(externalIdx) {
        if (externalIdx != selectedIndex) {
            selectedIndex = externalIdx
        }
    }

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap },
        labels = tabs.map { it.label },
        customAccentColor = accentColorFor(live.accent, live.customAccentColor),
        forceLightTheme = live.themeMode == "Light",
        forceDarkTheme = live.themeMode == "Dark",
        effectMultiplier = live.glassIntensity,
        containerAlpha = live.containerOpacity,
        barHeight = live.barHeight.dp,
        tabWidth = live.tabWidth.dp,
        cornerRadius = live.cornerRadius.dp,
        pillGlassIntensity = live.pillGlassIntensity,
        pillContainerOpacity = live.pillContainerOpacity,
        pillChromaticAberration = live.pillChromaticAberration,
        pillShadow = live.pillShadow,
        liquidGlass = live.liquidGlass,
        isShrunk = isShrunkState.value,
        shrinkScale = live.autoShrinkScale,
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            if (index == 0) {
                isShrunkState.value = false
            }
            // Fix: Trigger the actual WhatsApp tab switch only if it isn't already there
            if (externalTabState.value != index) {
                clickStockTab(stockBar, index)
            }
        },
        sourceView = backdropSource,
    )
}

/** WhatsApp's "Auto" accent is its own brand green — that's what this bar always used
 *  before this setting existed, so it stays the default rather than falling back to a
 *  generic black/white adaptive color. */
private fun accentColorFor(preset: String, customColor: Long): androidx.compose.ui.graphics.Color = when (preset) {
    "Blue" -> androidx.compose.ui.graphics.Color(0xFF0088FF)
    "Pink" -> androidx.compose.ui.graphics.Color(0xFFFF2D78)
    "Purple" -> androidx.compose.ui.graphics.Color(0xFFAF52DE)
    "Orange" -> androidx.compose.ui.graphics.Color(0xFFFF9500)
    "Green" -> androidx.compose.ui.graphics.Color(0xFF34C759)
    "Custom" -> androidx.compose.ui.graphics.Color(customColor)
    else -> androidx.compose.ui.graphics.Color(0xFF21C063)
}
