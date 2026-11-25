package dev.hyperios.app.nav

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings
import kotlinx.coroutines.delay

/**
 * Data representation of an extracted TikTok navigation tab item.
 */
private data class TikTokTabData(
    val tabView: View,
    val inactiveBitmap: Bitmap?,
    val activeBitmap: Bitmap?,
    val label: String? = null,
    val badgeCount: String? = null,
    val hasBadgeDot: Boolean = false,
)

private val TIKTOK_TAB_ENTRY_NAMES = listOf("moa", "mo_", "mo8", "mob", "moc")
private val TIKTOK_TAB_STATIC_IDS = listOf(0x7f0b457f, 0x7f0b457e, 0x7f0b457c, 0x7f0b4580, 0x7f0b4581)
private val TIKTOK_TAB_TAGS = listOf("HOME", "FRIENDS", "PUBLISH", "NOTIFICATION", "USER")
private val TIKTOK_DEFAULT_LABELS = listOf("Home", "Friends", "Create", "Inbox", "Profile")

private val TIKTOK_ICON_NAME_PAIRS = listOf(
    Pair("icon_tab_bottom_home", "icon_tab_bottom_home_fill"),
    Pair("icon_tab_bottom_friends", "icon_tab_bottom_friends_fill"),
    Pair("icon_tab_bottom_create", "icon_tab_bottom_create_fill"),
    Pair("icon_tab_bottom_inbox", "icon_tab_bottom_inbox_fill"),
    Pair("icon_tab_bottom_profile", "icon_tab_bottom_profile_fill"),
)

/**
 * Finds the primary icon ImageView within a tab View, skipping tiny badge dot views.
 */
private fun findTabIconImageView(view: View): ImageView? {
    val candidates = mutableListOf<ImageView>()
    fun collect(v: View) {
        if (v is ImageView) {
            candidates.add(v)
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                collect(v.getChildAt(i))
            }
        }
    }
    collect(view)
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    // Filter out obvious badge / dot ImageViews
    val nonBadges = candidates.filter { iv ->
        val resName = try {
            if (iv.id != View.NO_ID && iv.resources != null) {
                iv.resources.getResourceEntryName(iv.id).lowercase()
            } else ""
        } catch (_: Throwable) { "" }
        !resName.contains("badge") && !resName.contains("dot") && !resName.contains("red_point")
    }
    val pool = if (nonBadges.isNotEmpty()) nonBadges else candidates

    // Pick candidate with largest bounds or layout size
    return pool.maxByOrNull { iv ->
        val w = iv.width.takeIf { it > 0 } ?: iv.drawable?.intrinsicWidth ?: iv.layoutParams?.width ?: 0
        val h = iv.height.takeIf { it > 0 } ?: iv.drawable?.intrinsicHeight ?: iv.layoutParams?.height ?: 0
        w * h
    } ?: pool.first()
}

/**
 * Extracts badge state (unread count or red indicator dot) from a tab view.
 */
private fun extractBadge(child: View, mainIconView: ImageView? = null): Pair<String?, Boolean> {
    var countStr: String? = null
    var hasDot = false

    fun checkView(v: View) {
        if (v.visibility != View.VISIBLE) return

        if (v is TextView) {
            val txt = v.text?.toString()?.trim()
            if (!txt.isNullOrEmpty() && txt.any { it.isDigit() }) {
                countStr = txt
            }
        } else if (v is ImageView) {
            if (v !== mainIconView) {
                val w = v.width
                val h = v.height
                val resName = try {
                    if (v.id != View.NO_ID && v.resources != null) {
                        v.resources.getResourceEntryName(v.id).lowercase()
                    } else ""
                } catch (_: Throwable) { "" }

                if (resName.contains("badge") || resName.contains("dot") || resName.contains("point") || resName.contains("notice")) {
                    hasDot = true
                } else if ((w in 1..40 && h in 1..40) ||
                    (v.layoutParams != null && v.layoutParams.width in 1..45 && v.layoutParams.height in 1..45)) {
                    hasDot = true
                }
            }
        } else if (v !is ViewGroup) {
            val resName = try {
                if (v.id != View.NO_ID && v.resources != null) {
                    v.resources.getResourceEntryName(v.id).lowercase()
                } else ""
            } catch (_: Throwable) { "" }

            if (resName.contains("badge") || resName.contains("dot") || resName.contains("point")) {
                hasDot = true
            } else if (v.width in 4..40 && v.height in 4..40) {
                hasDot = true
            }
        }

        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                checkView(v.getChildAt(i))
            }
        }
    }

    checkView(child)
    return Pair(countStr, hasDot)
}

/**
 * Tints a Bitmap with the given color filter.
 */
private fun tintBitmap(src: Bitmap, tint: Int): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}

/**
 * Converts a Drawable into a Bitmap, optionally tinting it.
 */
private fun drawableToBitmap(
    d: Drawable,
    tint: Int? = null,
    targetWidth: Int = 0,
    targetHeight: Int = 0,
): Bitmap? {
    return try {
        val mutated = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
        if (tint != null) {
            mutated.setTint(tint)
        }
        val w = if (targetWidth > 0) targetWidth else mutated.intrinsicWidth.coerceAtLeast(64)
        val h = if (targetHeight > 0) targetHeight else mutated.intrinsicHeight.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        mutated.setBounds(0, 0, w, h)
        mutated.draw(canvas)
        if (tint != null) tintBitmap(bmp, tint) else bmp
    } catch (_: Throwable) {
        null
    }
}

/**
 * Dispatches a simulated tap onto the TikTok stock tab view with performClick and fallback motion events.
 */
private fun clickTikTokTab(child: View) {
    child.post {
        try {
            val handled = child.performClick()
            if (!handled) {
                val now = android.os.SystemClock.uptimeMillis()
                val w = child.width.coerceAtLeast(10)
                val h = child.height.coerceAtLeast(10)
                val x = w / 2f
                val y = h / 2f
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                val up = android.view.MotionEvent.obtain(now, now + 10, android.view.MotionEvent.ACTION_UP, x, y, 0)
                child.dispatchTouchEvent(down)
                child.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }
        } catch (t: Throwable) {
            Log.e("HyperBounce", "clickTikTokTab error", t)
        }
    }
}

/**
 * Discovers the 5 tab views of TikTok bottom navigation bar:
 * moa (Home), mo_ (Friends), mo8 (Create), mob (Inbox), moc (Profile).
 */
private fun findTikTokTabViews(container: ViewGroup): List<View> {
    val context = container.context
    val byRes = mutableListOf<View>()
    for (i in TIKTOK_TAB_ENTRY_NAMES.indices) {
        val staticId = TIKTOK_TAB_STATIC_IDS[i]
        var v: View? = container.findViewById(staticId)
        if (v == null && context != null) {
            val entryName = TIKTOK_TAB_ENTRY_NAMES[i]
            val dynamicId = try {
                context.resources.getIdentifier(entryName, "id", context.packageName)
            } catch (_: Throwable) { 0 }
            if (dynamicId != 0) {
                v = container.findViewById(dynamicId)
            }
        }
        if (v == null) {
            val tag = TIKTOK_TAB_TAGS[i]
            v = container.findViewWithTag(tag)
        }
        if (v != null) byRes.add(v)
    }
    if (byRes.size == 5) return byRes

    if (container.childCount == 5) {
        return (0 until 5).map { container.getChildAt(it) }
    }

    // Look for X.0eaq stock bar (id 0x7f0b4587 / @id/moi)
    val stockBar = container.findViewById<ViewGroup>(0x7f0b4587)
        ?: run {
            val dynamicBarId = try {
                context.resources.getIdentifier("moi", "id", context.packageName)
            } catch (_: Throwable) { 0 }
            if (dynamicBarId != 0) container.findViewById<ViewGroup>(dynamicBarId) else null
        }
    if (stockBar != null && stockBar.childCount == 5) {
        return (0 until 5).map { stockBar.getChildAt(it) }
    }

    for (i in 0 until container.childCount) {
        val child = container.getChildAt(i)
        if (child is ViewGroup && child.childCount == 5) {
            return (0 until 5).map { child.getChildAt(it) }
        }
    }

    val visibleChildren = mutableListOf<View>()
    for (i in 0 until container.childCount) {
        val child = container.getChildAt(i)
        if (child.visibility != View.GONE) {
            visibleChildren.add(child)
        }
    }
    return visibleChildren
}

/**
 * Extracts the currently selected tab index from TabHost or View state.
 */
private fun extractSelectedIndex(tabBarContainer: ViewGroup, tabs: List<TikTokTabData>): Int {
    try {
        var parent = tabBarContainer.parent
        while (parent != null) {
            if (parent is android.widget.TabHost) {
                val cur = parent.currentTab
                if (cur in tabs.indices) return cur
            }
            try {
                val m = parent.javaClass.getMethod("getCurrentTab")
                val res = m.invoke(parent) as? Int
                if (res != null && res in tabs.indices) return res
            } catch (_: Throwable) {}
            parent = parent.parent
        }
    } catch (_: Throwable) {}

    for (i in tabs.indices) {
        val v = tabs[i].tabView
        if (v.isSelected || v.isActivated) {
            return i
        }
    }
    return 0
}

/**
 * Extracts and prepares TikTok tab data models, populating outline and filled bitmap caches.
 */
private fun extractTikTokTabs(
    tabBarContainer: ViewGroup,
    isDark: Boolean,
    accentColor: Color,
    outlineCache: MutableMap<Int, Bitmap>,
    filledCache: MutableMap<Int, Bitmap>,
): List<TikTokTabData> {
    val out = mutableListOf<TikTokTabData>()
    val tabViews = findTikTokTabViews(tabBarContainer)
    Log.i("HyperBounce", "TikTokNavBarHost: found ${tabViews.size} tab views in container")

    val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val activeTint = accentColor.toArgb()
    val context = tabBarContainer.context

    for ((index, child) in tabViews.withIndex()) {
        val isCreateTab = (index == 2)
        val iconView = findTabIconImageView(child)

        // Attempt resolving direct vector drawables from resources if available
        val (outlineName, filledName) = TIKTOK_ICON_NAME_PAIRS.getOrNull(index) ?: Pair(null, null)
        if (outlineName != null && context != null && !outlineCache.containsKey(index)) {
            val resId = try { context.resources.getIdentifier(outlineName, "drawable", context.packageName) } catch (_: Throwable) { 0 }
            if (resId != 0) {
                val d = try { androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, resId, context.theme) } catch (_: Throwable) { null }
                if (d != null) {
                    val bmp = drawableToBitmap(d, tint = if (isCreateTab) null else inactiveTint)
                    if (bmp != null) outlineCache[index] = bmp
                }
            }
        }
        if (filledName != null && context != null && !filledCache.containsKey(index)) {
            val resId = try { context.resources.getIdentifier(filledName, "drawable", context.packageName) } catch (_: Throwable) { 0 }
            if (resId != 0) {
                val d = try { androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, resId, context.theme) } catch (_: Throwable) { null }
                if (d != null) {
                    val bmp = drawableToBitmap(d, tint = if (isCreateTab) null else activeTint)
                    if (bmp != null) filledCache[index] = bmp
                }
            }
        }

        // Stateful drawable handling
        val currentDrawable = iconView?.drawable
        if (currentDrawable != null) {
            if (currentDrawable.isStateful) {
                val statefulCopy = currentDrawable.constantState?.newDrawable()?.mutate() ?: currentDrawable
                if (!outlineCache.containsKey(index)) {
                    statefulCopy.state = intArrayOf(-android.R.attr.state_selected, -android.R.attr.state_activated)
                    val bmp = drawableToBitmap(statefulCopy, tint = if (isCreateTab) null else inactiveTint)
                    if (bmp != null) outlineCache[index] = bmp
                }
                if (!filledCache.containsKey(index)) {
                    statefulCopy.state = intArrayOf(android.R.attr.state_selected, android.R.attr.state_activated)
                    val bmp = drawableToBitmap(statefulCopy, tint = if (isCreateTab) null else activeTint)
                    if (bmp != null) filledCache[index] = bmp
                }
            } else {
                if (child.isSelected) {
                    if (!filledCache.containsKey(index)) {
                        val bmp = drawableToBitmap(currentDrawable, tint = if (isCreateTab) null else activeTint)
                        if (bmp != null) filledCache[index] = bmp
                    }
                } else {
                    if (!outlineCache.containsKey(index)) {
                        val bmp = drawableToBitmap(currentDrawable, tint = if (isCreateTab) null else inactiveTint)
                        if (bmp != null) outlineCache[index] = bmp
                    }
                }
            }
        }

        // Fallback: draw view directly if bitmaps are missing
        if (!outlineCache.containsKey(index) && !filledCache.containsKey(index) && child.width > 0 && child.height > 0) {
            try {
                val bmp = Bitmap.createBitmap(
                    child.width.coerceAtLeast(1),
                    child.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                child.draw(Canvas(bmp))
                if (isCreateTab) {
                    outlineCache[index] = bmp
                    filledCache[index] = bmp
                } else {
                    val targetTint = if (child.isSelected) activeTint else inactiveTint
                    val tintedBmp = tintBitmap(bmp, targetTint)
                    if (child.isSelected) {
                        filledCache[index] = tintedBmp
                    } else {
                        outlineCache[index] = tintedBmp
                    }
                }
            } catch (_: Throwable) {}
        }

        var inactiveBmp = outlineCache[index]
        var activeBmp = filledCache[index]
        if (inactiveBmp == null && activeBmp != null) inactiveBmp = activeBmp
        if (activeBmp == null && inactiveBmp != null) activeBmp = inactiveBmp

        val label = child.contentDescription?.toString() ?: TIKTOK_DEFAULT_LABELS.getOrNull(index)
        val (badgeCount, hasBadgeDot) = extractBadge(child, iconView)

        val tabData = TikTokTabData(
            tabView = child,
            inactiveBitmap = inactiveBmp,
            activeBitmap = activeBmp ?: inactiveBmp,
            label = label,
            badgeCount = badgeCount,
            hasBadgeDot = hasBadgeDot
        )
        Log.i(
            "HyperBounce",
            "TikTokNavBarHost: tab[$index]: label='$label', badgeCount=$badgeCount, hasBadgeDot=$hasBadgeDot, inactiveBmp=${inactiveBmp != null}, activeBmp=${activeBmp != null}"
        )
        out.add(tabData)
    }

    return out
}

/**
 * Resolves the tab accent color, defaulting to TikTok's iconic pink/red (0xFFFE2C55).
 */
private fun accentColorFor(preset: String, customColor: Long, isDark: Boolean): Color = when (preset) {
    "Default", "TikTok" -> Color(0xFFFE2C55)
    "Blue" -> Color(0xFF00F2FE)
    "Pink" -> Color(0xFFFE2C55)
    "Purple" -> Color(0xFFAF52DE)
    "Orange" -> Color(0xFFFF9500)
    "Green" -> Color(0xFF25F4EE)
    "Custom" -> Color(customColor)
    else -> Color(0xFFFE2C55)
}

/**
 * Composable navigation bar host for TikTok (com.ss.android.ugc.trill / com.zhiliaoapp.musically).
 * Floats the HyperBounce liquid glass navigation bar above TikTok's feed interface.
 */
@Composable
fun TikTokNavBarHost(
    tabBarContainer: ViewGroup,
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs,
    externalTabState: State<Int>? = null,
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "tiktok")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accentColor = accentColorFor(live.accent, live.customAccentColor, isDark)

    var tabs by remember { mutableStateOf(emptyList<TikTokTabData>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var lastUserSelectionTime by remember { mutableLongStateOf(0L) }

    val outlineBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }
    val filledBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }

    val activity = (backdropSource.context as? Activity) ?: (tabBarContainer.context as? Activity)
    val scrollShrinkState = rememberScrollShrinkState(
        activity = activity,
        enabled = live.autoShrink,
        sensitivity = live.autoShrinkSensitivity
    )

    // Synchronize with externalTabState if supplied
    externalTabState?.value?.let { extIdx ->
        if (extIdx in tabs.indices && extIdx != selectedIndex &&
            android.os.SystemClock.uptimeMillis() - lastUserSelectionTime > 400L) {
            selectedIndex = extIdx
        }
    }

    LaunchedEffect(tabBarContainer, isDark, accentColor) {
        outlineBitmapsCache.clear()
        filledBitmapsCache.clear()
        val activeTint = accentColor.toArgb()
        val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        val initialTabs = extractTikTokTabs(tabBarContainer, isDark, accentColor, outlineBitmapsCache, filledBitmapsCache)
        tabs = initialTabs
        selectedIndex = extractSelectedIndex(tabBarContainer, tabs)

        while (true) {
            delay(150L)
            if (tabs.isEmpty() || tabs.any { it.inactiveBitmap == null }) {
                val extracted = extractTikTokTabs(tabBarContainer, isDark, accentColor, outlineBitmapsCache, filledBitmapsCache)
                if (extracted.isNotEmpty()) {
                    tabs = extracted
                }
            } else {
                val now = android.os.SystemClock.uptimeMillis()
                val curSelected = extractSelectedIndex(tabBarContainer, tabs)

                if (now - lastUserSelectionTime > 500L) {
                    if (curSelected != selectedIndex && curSelected in tabs.indices) {
                        selectedIndex = curSelected
                    }
                }

                var tabsMutated = false
                val updatedTabs = tabs.mapIndexed { idx, tab ->
                    val isCreateTab = (idx == 2)
                    val iconView = findTabIconImageView(tab.tabView)
                    val (count, dot) = extractBadge(tab.tabView, iconView)
                    var activeBmp = tab.activeBitmap
                    var inactiveBmp = tab.inactiveBitmap

                    if (idx == curSelected) {
                        if (!filledBitmapsCache.containsKey(idx)) {
                            val d = iconView?.drawable
                            if (d != null) {
                                val bmp = drawableToBitmap(d, tint = if (isCreateTab) null else activeTint)
                                if (bmp != null) filledBitmapsCache[idx] = bmp
                            }
                        }
                        if (filledBitmapsCache.containsKey(idx) && activeBmp != filledBitmapsCache[idx]) {
                            activeBmp = filledBitmapsCache[idx]
                            tabsMutated = true
                        }
                    } else {
                        if (!outlineBitmapsCache.containsKey(idx)) {
                            val d = iconView?.drawable
                            if (d != null) {
                                val bmp = drawableToBitmap(d, tint = if (isCreateTab) null else inactiveTint)
                                if (bmp != null) outlineBitmapsCache[idx] = bmp
                            }
                        }
                        if (outlineBitmapsCache.containsKey(idx) && inactiveBmp != outlineBitmapsCache[idx]) {
                            inactiveBmp = outlineBitmapsCache[idx]
                            tabsMutated = true
                        }
                    }

                    if (count != tab.badgeCount || dot != tab.hasBadgeDot ||
                        activeBmp != tab.activeBitmap || inactiveBmp != tab.inactiveBitmap) {
                        tabsMutated = true
                        tab.copy(
                            badgeCount = count,
                            hasBadgeDot = dot,
                            activeBitmap = activeBmp,
                            inactiveBitmap = inactiveBmp
                        )
                    } else {
                        tab
                    }
                }

                if (tabsMutated) {
                    tabs = updatedTabs
                }
            }
        }
    }

    if (tabs.isEmpty()) return

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap ?: it.inactiveBitmap },
        selectedIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onTabSelected = { index ->
            selectedIndex = index
            lastUserSelectionTime = android.os.SystemClock.uptimeMillis()
            if (index == 0) {
                scrollShrinkState.isShrunk.value = false
            }
            Log.i("HyperBounce", "TikTokNavBarHost: tab selected: index=$index, delegating click to child tab view")
            if (index in tabs.indices) {
                clickTikTokTab(tabs[index].tabView)
            }
        },
        sourceView = backdropSource,
        circularIndices = emptySet(),
        forceLightTheme = (live.themeMode == "Light"),
        forceDarkTheme = (live.themeMode == "Dark"),
        containerAlpha = live.containerOpacity,
        effectMultiplier = live.glassIntensity,
        barBlurIntensity = live.barBlurIntensity,
        labels = emptyList(),
        customAccentColor = accentColor,
        barHeight = live.barHeight.dp,
        tabWidth = live.tabWidth.dp,
        cornerRadius = live.cornerRadius.dp,
        pillGlassIntensity = live.pillGlassIntensity,
        pillContainerOpacity = live.pillContainerOpacity,
        pillChromaticAberration = live.pillChromaticAberration,
        pillShadow = live.pillShadow,
        liquidGlass = live.liquidGlass,
        isShrunk = scrollShrinkState.isShrunk.value,
        shrinkScale = live.autoShrinkScale,
        scrollShrinkState = scrollShrinkState,
        badgeCounts = tabs.map { it.badgeCount },
        badgeDots = tabs.map { it.hasBadgeDot },
        enablePixelCopy = false,
    )
}
