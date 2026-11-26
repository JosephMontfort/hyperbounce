package dev.hyperios.app.nav

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.hyperios.app.R
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings

private data class TwitterIconDef(
    @DrawableRes val resId: Int,
    val name: String
)

private val ICON_HOME_INACTIVE = TwitterIconDef(R.drawable.hb_twitter_home_inactive, "hb_twitter_home_inactive")
private val ICON_HOME_ACTIVE = TwitterIconDef(R.drawable.hb_twitter_home_active, "hb_twitter_home_active")

private val ICON_EXPLORE_INACTIVE = TwitterIconDef(R.drawable.hb_twitter_explore_inactive, "hb_twitter_explore_inactive")
private val ICON_EXPLORE_ACTIVE = TwitterIconDef(R.drawable.hb_twitter_explore_active, "hb_twitter_explore_active")

private val ICON_GROK_INACTIVE = TwitterIconDef(R.drawable.hb_twitter_grok_inactive, "hb_twitter_grok_inactive")
private val ICON_GROK_ACTIVE = TwitterIconDef(R.drawable.hb_twitter_grok_active, "hb_twitter_grok_active")

private val ICON_NOTIFICATIONS_INACTIVE = TwitterIconDef(R.drawable.hb_twitter_notifications_inactive, "hb_twitter_notifications_inactive")
private val ICON_NOTIFICATIONS_ACTIVE = TwitterIconDef(R.drawable.hb_twitter_notifications_active, "hb_twitter_notifications_active")

private val ICON_MESSAGES_INACTIVE = TwitterIconDef(R.drawable.hb_twitter_messages_inactive, "hb_twitter_messages_inactive")
private val ICON_MESSAGES_ACTIVE = TwitterIconDef(R.drawable.hb_twitter_messages_active, "hb_twitter_messages_active")

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
                Context.CONTEXT_IGNORE_SECURITY
            )
            cachedModuleContext = modCtx
            modCtx
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Loads authentic vector drawables from HyperBounce package resources,
 * tinting for inactive (outline/stroke) states and active (stencil for Compose tint) states.
 */
private fun loadTwitterTabBitmap(
    moduleContext: Context,
    iconDef: TwitterIconDef,
    isDark: Boolean,
    isActive: Boolean
): Bitmap? {
    val drawable = try {
        ResourcesCompat.getDrawable(
            moduleContext.resources,
            iconDef.resId,
            moduleContext.theme
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
    // In Dark Mode: authentic X light gray (#E7E9EA)
    // In Light Mode: authentic X dark gray (#0F1419)
    // For active state:
    // White stencil (#FFFFFF) so Compose ColorFilter.tint(accentColor) applies cleanly.
    val tintColor = if (!isActive) {
        if (isDark) android.graphics.Color.parseColor("#E7E9EA")
        else android.graphics.Color.parseColor("#0F1419")
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

private fun resolveAccentColor(preset: String, customColor: Long): Color = when (preset) {
    "Blue" -> Color(0xFF1D9BF0)
    "Pink" -> Color(0xFFFF2D78)
    "Purple" -> Color(0xFFAF52DE)
    "Orange" -> Color(0xFFFF9500)
    "Green" -> Color(0xFF34C759)
    "Custom" -> Color(customColor)
    else -> Color(0xFF1D9BF0) // Authentic X / Twitter Blue
}

/**
 * Android View Host for X (Twitter) LiquidNavigationBar.
 * Attached to `activity.findViewById(android.R.id.content)` to achieve scroll immunity.
 */
class TwitterNavBarHost @JvmOverloads constructor(
    context: Context,
    val prefs: HyperBouncePrefs,
    val backdropSource: View = (context as? Activity)?.findViewById(android.R.id.content) ?: View(context),
    attrs: AttributeSet? = null
) : ComposeView(context, attrs) {

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            TwitterNavBarHost(
                backdropSource = backdropSource,
                hyperBouncePrefs = prefs
            )
        }
    }
}

/**
 * Composable root rendering the floating LiquidNavigationBar for X (Twitter).
 */
@Composable
fun TwitterNavBarHost(
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "twitter")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val context = backdropSource.context
    val moduleContext = remember(context) { getModuleContext(context) ?: context }

    val iconPairs = remember {
        listOf(
            Pair(ICON_HOME_INACTIVE, ICON_HOME_ACTIVE),
            Pair(ICON_EXPLORE_INACTIVE, ICON_EXPLORE_ACTIVE),
            Pair(ICON_GROK_INACTIVE, ICON_GROK_ACTIVE),
            Pair(ICON_NOTIFICATIONS_INACTIVE, ICON_NOTIFICATIONS_ACTIVE),
            Pair(ICON_MESSAGES_INACTIVE, ICON_MESSAGES_ACTIVE)
        )
    }

    val inactiveTabs = remember(moduleContext, isDark) {
        iconPairs.map { pair ->
            loadTwitterTabBitmap(moduleContext, pair.first, isDark = isDark, isActive = false)
        }
    }

    val activeTabs = remember(moduleContext, isDark) {
        iconPairs.map { pair ->
            loadTwitterTabBitmap(moduleContext, pair.second, isDark = isDark, isActive = true)
        }
    }

    val currentTabIndex by TwitterHook.currentTabIndexState
    val badgeCounts by TwitterHook.badgeCountsState
    val badgeDots by TwitterHook.badgeDotsState

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val tabCount = 5
    val maxSafeTabWidth = ((screenWidthDp - 16f) / tabCount).coerceAtLeast(40f)
    val effectiveTabWidth = live.tabWidth.coerceAtMost(maxSafeTabWidth)

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = inactiveTabs,
        activeTabs = activeTabs,
        selectedIndex = currentTabIndex.coerceIn(0, 4),
        onTabSelected = { index ->
            TwitterHook.dispatchTabClick(index)
        },
        sourceView = backdropSource,
        forceLightTheme = live.themeMode == "Light",
        forceDarkTheme = live.themeMode == "Dark",
        containerAlpha = live.containerOpacity,
        effectMultiplier = live.glassIntensity,
        barBlurIntensity = live.barBlurIntensity,
        labels = if (live.showLabels) listOf("Home", "Explore", "Grok", "Notifications", "Messages") else emptyList(),
        customAccentColor = resolveAccentColor(live.accent, live.customAccentColor),
        barHeight = live.barHeight.dp,
        tabWidth = effectiveTabWidth.dp,
        cornerRadius = live.cornerRadius.dp,
        pillGlassIntensity = live.pillGlassIntensity,
        pillContainerOpacity = live.pillContainerOpacity,
        pillChromaticAberration = live.pillChromaticAberration,
        pillShadow = live.pillShadow,
        liquidGlass = live.liquidGlass,
        isShrunk = false,
        shrinkScale = 1.0f,
        scrollShrinkState = null,
        badgeCounts = badgeCounts,
        badgeDots = badgeDots
    )
}
