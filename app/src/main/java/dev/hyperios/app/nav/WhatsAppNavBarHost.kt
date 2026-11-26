package dev.hyperios.app.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.hyperios.app.R
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings

private data class WhatsAppTabData(
    val inactiveBitmap: Bitmap?,
    val activeBitmap: Bitmap?,
    val label: String,
)

private data class IosIconDef(
    @DrawableRes val resId: Int,
    val name: String,
)

private val ICON_CHATS_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_chats_outline, "ic_wa_ios_chats_outline")
private val ICON_CHATS_FILLED = IosIconDef(R.drawable.ic_wa_ios_chats_filled, "ic_wa_ios_chats_filled")

private val ICON_UPDATES_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_updates_outline, "ic_wa_ios_updates_outline")
private val ICON_UPDATES_FILLED = IosIconDef(R.drawable.ic_wa_ios_updates_filled, "ic_wa_ios_updates_filled")

private val ICON_STATUS_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_status_outline, "ic_wa_ios_status_outline")
private val ICON_STATUS_FILLED = IosIconDef(R.drawable.ic_wa_ios_status_filled, "ic_wa_ios_status_filled")

private val ICON_COMMUNITIES_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_communities_outline, "ic_wa_ios_communities_outline")
private val ICON_COMMUNITIES_FILLED = IosIconDef(R.drawable.ic_wa_ios_communities_filled, "ic_wa_ios_communities_filled")

private val ICON_CALLS_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_calls_outline, "ic_wa_ios_calls_outline")
private val ICON_CALLS_FILLED = IosIconDef(R.drawable.ic_wa_ios_calls_filled, "ic_wa_ios_calls_filled")

private val ICON_SETTINGS_OUTLINE = IosIconDef(R.drawable.ic_wa_ios_settings_outline, "ic_wa_ios_settings_outline")
private val ICON_SETTINGS_FILLED = IosIconDef(R.drawable.ic_wa_ios_settings_filled, "ic_wa_ios_settings_filled")

@Volatile
private var cachedModuleContext: Context? = null
private val contextLock = Any()

private fun getModuleContext(hostContext: Context): Context? {
    cachedModuleContext?.let { return it }
    synchronized(contextLock) {
        cachedModuleContext?.let { return it }
        return try {
            val modCtx = hostContext.createPackageContext(
                "dev.hyperios.app",
                Context.CONTEXT_IGNORE_SECURITY,
            )
            cachedModuleContext = modCtx
            modCtx
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Loads an authentic iOS PNG drawable from HyperBounce's package resources,
 * applying theme-appropriate tinting for inactive (outline) states while preserving
 * full alpha anti-aliasing.
 */
private fun loadIosTabBitmap(
    moduleContext: Context,
    iconDef: IosIconDef,
    isDark: Boolean,
    isActive: Boolean,
): Bitmap? {
    val drawable = try {
        ResourcesCompat.getDrawable(
            moduleContext.resources,
            iconDef.resId,
            moduleContext.theme,
        )?.mutate()
    } catch (_: Throwable) {
        null
    } ?: try {
        val id = moduleContext.resources.getIdentifier(iconDef.name, "drawable", "dev.hyperios.app")
        if (id != 0) {
            ResourcesCompat.getDrawable(moduleContext.resources, id, moduleContext.theme)?.mutate()
        } else null
    } catch (_: Throwable) {
        null
    } ?: return null

    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)

    // For inactive state:
    // In Dark Mode: light gray / white (#FFFFFF)
    // In Light Mode: Apple iOS tab gray (#8E8E93)
    // For active state:
    // White stencil so Compose ColorFilter.tint(accentColor) cleanly tints with
    // WhatsApp brand green (#21C063) or user custom accent.
    val tintColor = if (!isActive) {
        if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    } else {
        android.graphics.Color.WHITE
    }
    drawable.setTint(tintColor)
    drawable.setTintMode(PorterDuff.Mode.SRC_IN)

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}

/**
 * Multi-tier mapping to assign authentic iOS icons to WhatsApp Android tabs.
 *
 * Precedence:
 * Tier 1: Semantic label matching (multilingual substrings: chat, convers, update, atualiz, status, communit, call, setting)
 * Tier 2: View resource ID entry name check
 * Tier 3: Standard WhatsApp Android 4-tab index fallback (0 -> Chats, 1 -> Updates, 2 -> Communities, 3 -> Calls)
 */
private fun resolveIosIconPair(index: Int, label: String, entryName: String): Pair<IosIconDef, IosIconDef>? {
    val l = label.lowercase()
    val e = entryName.lowercase()

    // Tier 1: Semantic label matching (multilingual substrings)
    when {
        l.contains("chat") || l.contains("convers") || l.contains("bate-papo") || l.contains("mensaj") ->
            return Pair(ICON_CHATS_OUTLINE, ICON_CHATS_FILLED)

        l.contains("update") || l.contains("atualiz") || l.contains("novedad") || l.contains("aktuell") ->
            return Pair(ICON_UPDATES_OUTLINE, ICON_UPDATES_FILLED)

        l.contains("status") || l.contains("estados") ->
            return Pair(ICON_STATUS_OUTLINE, ICON_STATUS_FILLED)

        l.contains("communit") || l.contains("comunid") || l.contains("gemeinschaft") ->
            return Pair(ICON_COMMUNITIES_OUTLINE, ICON_COMMUNITIES_FILLED)

        l.contains("call") || l.contains("ligaç") || l.contains("llamad") || l.contains("anruf") ->
            return Pair(ICON_CALLS_OUTLINE, ICON_CALLS_FILLED)

        l.contains("setting") || l.contains("ajuste") || l.contains("configur") || l.contains("einstell") ->
            return Pair(ICON_SETTINGS_OUTLINE, ICON_SETTINGS_FILLED)
    }

    // Tier 2: View resource ID entry name check
    when {
        e.contains("chat") ->
            return Pair(ICON_CHATS_OUTLINE, ICON_CHATS_FILLED)

        e.contains("update") ->
            return Pair(ICON_UPDATES_OUTLINE, ICON_UPDATES_FILLED)

        e.contains("status") ->
            return Pair(ICON_STATUS_OUTLINE, ICON_STATUS_FILLED)

        e.contains("communit") ->
            return Pair(ICON_COMMUNITIES_OUTLINE, ICON_COMMUNITIES_FILLED)

        e.contains("call") ->
            return Pair(ICON_CALLS_OUTLINE, ICON_CALLS_FILLED)

        e.contains("setting") ->
            return Pair(ICON_SETTINGS_OUTLINE, ICON_SETTINGS_FILLED)
    }

    // Tier 3: Standard WhatsApp Android 4-tab index fallback
    return when (index) {
        0 -> Pair(ICON_CHATS_OUTLINE, ICON_CHATS_FILLED)
        1 -> Pair(ICON_UPDATES_OUTLINE, ICON_UPDATES_FILLED)
        2 -> Pair(ICON_COMMUNITIES_OUTLINE, ICON_COMMUNITIES_FILLED)
        3 -> Pair(ICON_CALLS_OUTLINE, ICON_CALLS_FILLED)
        4 -> Pair(ICON_SETTINGS_OUTLINE, ICON_SETTINGS_FILLED)
        else -> null
    }
}

/**
 * Tier 4: Fallback to extracting the stock Android drawable from WhatsApp's view hierarchy
 * if module context fails or an unknown custom tab appears.
 */
private fun extractStockDrawables(tab: ViewGroup, isDark: Boolean): Pair<Bitmap?, Bitmap?> {
    val icon = findImageView(tab) ?: return Pair(null, null)
    var inactiveBmp: Bitmap? = null
    var activeBmp: Bitmap? = null
    val sourceDrawable = icon.drawable?.constantState?.newDrawable()?.mutate()
    if (sourceDrawable != null) {
        val w = sourceDrawable.intrinsicWidth.coerceAtLeast(1)
        val h = sourceDrawable.intrinsicHeight.coerceAtLeast(1)

        val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        sourceDrawable.setTint(inactiveTint)
        sourceDrawable.setTintMode(PorterDuff.Mode.SRC_IN)
        sourceDrawable.state = intArrayOf(
            -android.R.attr.state_selected,
            -android.R.attr.state_activated,
        )
        inactiveBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        sourceDrawable.setBounds(0, 0, w, h)
        sourceDrawable.draw(Canvas(inactiveBmp))

        sourceDrawable.setTint(android.graphics.Color.WHITE)
        sourceDrawable.setTintMode(PorterDuff.Mode.SRC_IN)
        sourceDrawable.state = intArrayOf(
            android.R.attr.state_selected,
            android.R.attr.state_activated,
        )
        activeBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        sourceDrawable.setBounds(0, 0, w, h)
        sourceDrawable.draw(Canvas(activeBmp))
    }
    return Pair(inactiveBmp, activeBmp)
}

private fun extractWhatsAppTabs(stockBar: ViewGroup, isDark: Boolean): List<WhatsAppTabData> {
    val out = mutableListOf<WhatsAppTabData>()
    val menu = stockBar.getChildAt(0) as? ViewGroup ?: return out
    val moduleContext = getModuleContext(stockBar.context)

    for (i in 0 until menu.childCount) {
        val tab = menu.getChildAt(i) as? ViewGroup ?: continue
        if (tab.visibility == View.GONE) continue

        val tv = findTextView(tab)
        val label = tv?.text?.toString() ?: ""
        val contentDesc = tab.contentDescription?.toString()
            ?: findImageView(tab)?.contentDescription?.toString()
            ?: ""
        val semanticText = if (label.isNotBlank()) label else contentDesc

        val entryName = try {
            if (tab.id != View.NO_ID && tab.resources != null) {
                tab.resources.getResourceEntryName(tab.id).lowercase()
            } else {
                val iconView = findImageView(tab)
                if (iconView != null && iconView.id != View.NO_ID && iconView.resources != null) {
                    iconView.resources.getResourceEntryName(iconView.id).lowercase()
                } else ""
            }
        } catch (_: Exception) {
            ""
        }

        // Determine authentic iOS icon pair using multi-tier mapping
        val iconPair = resolveIosIconPair(index = i, label = semanticText, entryName = entryName)

        var inactiveBmp: Bitmap? = null
        var activeBmp: Bitmap? = null

        if (moduleContext != null && iconPair != null) {
            inactiveBmp = loadIosTabBitmap(moduleContext, iconPair.first, isDark = isDark, isActive = false)
            activeBmp = loadIosTabBitmap(moduleContext, iconPair.second, isDark = isDark, isActive = true)
        }

        // Tier 4: Fallback to stock bar drawables if module context fails or unknown tab appears
        if (inactiveBmp == null || activeBmp == null) {
            val (stockInactive, stockActive) = extractStockDrawables(tab, isDark)
            inactiveBmp = inactiveBmp ?: stockInactive
            activeBmp = activeBmp ?: stockActive
        }

        out.add(WhatsAppTabData(inactiveBmp, activeBmp, label))
    }
    return out
}

private fun findTextView(g: ViewGroup): TextView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is TextView) return v
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
        labels = if (live.showLabels) tabs.map { it.label } else emptyList(),
        customAccentColor = accentColorFor(live.accent, live.customAccentColor),
        forceLightTheme = live.themeMode == "Light",
        forceDarkTheme = live.themeMode == "Dark",
        effectMultiplier = live.glassIntensity,
        barBlurIntensity = live.barBlurIntensity,
        containerAlpha = live.containerOpacity,
        barHeight = live.barHeight.dp,
        tabWidth = live.tabWidth.dp,
        cornerRadius = live.cornerRadius.dp,
        pillGlassIntensity = live.pillGlassIntensity,
        pillContainerOpacity = live.pillContainerOpacity,
        pillChromaticAberration = live.pillChromaticAberration,
        pillShadow = live.pillShadow,
        liquidGlass = live.liquidGlass,
        isShrunk = false,
        shrinkScale = 1.0f,
        scrollShrinkState = null,
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            selectedIndex = index
            // Trigger actual WhatsApp tab switch only if it isn't already there
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
