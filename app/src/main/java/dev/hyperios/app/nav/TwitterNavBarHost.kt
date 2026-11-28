package dev.hyperios.app.nav

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class TwitterTabSpec(
    val inactiveName: String,
    val inactiveStaticId: Int,
    val activeName: String,
    val activeStaticId: Int
)

private val TWITTER_TAB_SPECS = listOf(
    // Tab 0: Home
    TwitterTabSpec(
        inactiveName = "ic_vector_home_stroke",
        inactiveStaticId = 0x7f0805c9,
        activeName = "ic_vector_home",
        activeStaticId = 0x7f0805c8
    ),
    // Tab 1: Explore / Search
    TwitterTabSpec(
        inactiveName = "ic_vector_search_stroke",
        inactiveStaticId = 0x7f080722,
        activeName = "ic_vector_search",
        activeStaticId = 0x7f08071b
    ),
    // Tab 2: Authentic Grok Tab
    TwitterTabSpec(
        inactiveName = "ic_vector_xai_grok_tab_stroke",
        inactiveStaticId = 0x7f080816,
        activeName = "ic_vector_xai_grok_tab",
        activeStaticId = 0x7f080815
    ),
    // Tab 3: Notifications
    TwitterTabSpec(
        inactiveName = "ic_vector_notifications_stroke",
        inactiveStaticId = 0x7f080675,
        activeName = "ic_vector_notifications",
        activeStaticId = 0x7f080669
    ),
    // Tab 4: Messages / DMs
    TwitterTabSpec(
        inactiveName = "ic_vector_chat_stroke",
        inactiveStaticId = 0x7f0804ca,
        activeName = "ic_vector_chat",
        activeStaticId = 0x7f0804b9
    )
)

/**
 * Loads authentic vector drawables directly from Twitter's own resources.
 * Immune to Android 11+ Package Visibility restrictions.
 */
private fun loadTwitterHostDrawable(context: Context, resName: String, staticResId: Int): Drawable? {
    // 1. Dynamic lookup by name in host package
    try {
        val id = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (id != 0) {
            val d = ResourcesCompat.getDrawable(context.resources, id, context.theme)
            if (d != null) return d
        }
    } catch (_: Throwable) {}

    // 2. Direct static resource ID fallback
    try {
        val d = ResourcesCompat.getDrawable(context.resources, staticResId, context.theme)
        if (d != null) return d
    } catch (_: Throwable) {}

    // 3. Fallback to alternative Twitter vector resource names
    val fallbackNames = when (resName) {
        "ic_vector_chat" -> listOf("ic_vector_messages")
        "ic_vector_chat_stroke" -> listOf("ic_vector_messages_stroke")
        "ic_vector_xai_grok_tab_stroke" -> listOf(
            "ic_vector_grok_stroke",
            "ic_vector_grok_icon",
            "ic_vector_grok_shortcut",
            "ic_vector_grok_bot_logo"
        )
        "ic_vector_xai_grok_tab" -> listOf(
            "ic_vector_grok_icon_filled",
            "ic_vector_grok_logo"
        )
        else -> emptyList()
    }
    for (name in fallbackNames) {
        try {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) {
                val d = ResourcesCompat.getDrawable(context.resources, id, context.theme)
                if (d != null) return d
            }
        } catch (_: Throwable) {}
    }

    return null
}

/**
 * Density-aware vector rasterizer.
 * - Inactive: Rendered in authentic X gray/slate (#E7E9EA in dark, #0F1419 in light).
 * - Active: Rendered as a pure white stencil (#FFFFFF) with alpha channel for Compose ColorFilter tinting.
 */
private fun renderTabBitmap(
    context: Context,
    resName: String,
    staticResId: Int,
    isDark: Boolean,
    isActive: Boolean
): Bitmap? {
    val drawable = loadTwitterHostDrawable(context, resName, staticResId)?.mutate() ?: return null

    val density = context.resources.displayMetrics.density
    val targetSizePx = (28f * density).roundToInt().coerceAtLeast(72)

    val tintColor = if (!isActive) {
        if (isDark) android.graphics.Color.parseColor("#E7E9EA")
        else android.graphics.Color.parseColor("#0F1419")
    } else {
        android.graphics.Color.WHITE
    }

    drawable.setTint(tintColor)
    drawable.setTintMode(PorterDuff.Mode.SRC_IN)

    val bmp = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, targetSizePx, targetSizePx)
    drawable.draw(canvas)
    return bmp
}

private fun resolveAccentColor(preset: String, customColor: Long): Color = when (preset) {
    "Blue" -> Color(0xFF1D9BF0) // Authentic Twitter Blue
    "Pink" -> Color(0xFFFF2D78)
    "Purple" -> Color(0xFFAF52DE)
    "Orange" -> Color(0xFFFF9500)
    "Green" -> Color(0xFF34C759)
    "Custom" -> Color(customColor)
    else -> Color(0xFF1D9BF0)
}

/**
 * Android View Host for Twitter LiquidNavigationBar.
 */
class TwitterNavBarHost @JvmOverloads constructor(
    context: Context,
    val prefs: HyperBouncePrefs,
    val backdropSource: View = (context as? Activity)?.findViewById(android.R.id.content) ?: View(context),
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        clipChildren = false
        clipToPadding = false
        val composeView = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                TwitterNavBarHost(
                    backdropSource = backdropSource,
                    hyperBouncePrefs = prefs,
                    window = (context as? Activity)?.window
                )
            }
        }
        addView(composeView)
    }
}

@Composable
fun TwitterNavBarHost(
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs,
    window: Window? = (backdropSource.context as? Activity)?.window ?: (backdropSource.rootView.context as? Activity)?.window
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "twitter")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val context = backdropSource.context

    // Native host vector extraction
    val inactiveTabs = remember(context, isDark) {
        TWITTER_TAB_SPECS.map { spec ->
            renderTabBitmap(context, spec.inactiveName, spec.inactiveStaticId, isDark = isDark, isActive = false)
        }
    }

    val activeTabs = remember(context, isDark) {
        TWITTER_TAB_SPECS.map { spec ->
            renderTabBitmap(context, spec.activeName, spec.activeStaticId, isDark = isDark, isActive = true)
        }
    }

    val currentTabIndex by TwitterHook.currentTabIndexState
    val badgeCounts by TwitterHook.badgeCountsState
    val badgeDots by TwitterHook.badgeDotsState

    // 350ms StateFlow poller for real-time badge updates
    LaunchedEffect(Unit) {
        while (true) {
            TwitterHook.activeTabController?.let { controller ->
                TwitterHook.pollBadgesFromController(controller)
            }
            delay(350)
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val tabCount = 5
    val maxSafeTabWidth = ((screenWidthDp - 16f) / tabCount).coerceAtLeast(40f)
    val effectiveTabWidth = live.tabWidth.coerceAtMost(maxSafeTabWidth)

    val isBarVisible by TwitterHook.isBarVisibleState

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Bottom),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isBarVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            ) + fadeIn(animationSpec = tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(150))
        ) {
            LiquidNavigationBar(
                bottomPadding = live.bottomMargin.dp,
                tabs = inactiveTabs,
                activeTabs = activeTabs,
                selectedIndex = currentTabIndex.coerceIn(0, 4),
                onTabSelected = { index ->
                    TwitterHook.dispatchTabClick(index)
                },
                sourceView = LocalView.current,
                enablePixelCopy = true,
                enableWindowPixelCopy = true,
                window = window,
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
    }
}
