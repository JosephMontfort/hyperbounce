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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveNavBarSettings
import kotlinx.coroutines.delay
import java.lang.reflect.Field

private data class FacebookTabData(
    val tabView: View,
    val inactiveBitmap: Bitmap?,
    val activeBitmap: Bitmap?,
    val label: String? = null,
    val badgeCount: String? = null,
    val hasBadgeDot: Boolean = false,
)

private fun Class<*>.findFieldRecursively(name: String): Field? {
    var c: Class<*>? = this
    while (c != null && c != Any::class.java) {
        try {
            val f = c.getDeclaredField(name)
            f.isAccessible = true
            return f
        } catch (_: Throwable) {
            c = c.superclass
        }
    }
    return null
}

private fun findImageView(g: ViewGroup): ImageView? {
    for (i in 0 until g.childCount) {
        val v = g.getChildAt(i)
        if (v is ImageView) return v
        if (v is ViewGroup) {
            val nested = findImageView(v)
            if (nested != null) return nested
        }
    }
    return null
}

private fun extractTabDrawables(child: View): Pair<Drawable?, Drawable?> {
    var outlineDrawable: Drawable? = null
    var filledDrawable: Drawable? = null

    // Strategy 1: Reflective C02LI.A00(a0s, variant, child)
    try {
        val a0sField = child.javaClass.findFieldRecursively("A0s")
            ?: child.javaClass.findFieldRecursively("A0S")
            ?: child.javaClass.findFieldRecursively("A0T")
        val a0sObj = a0sField?.get(child)
        val allMethods = child.javaClass.declaredMethods + child.javaClass.methods
        val a00Method = allMethods.firstOrNull { m ->
            m.name == "A00" && m.parameterTypes.size in 2..3 && m.parameterTypes.any { pt ->
                pt.isEnum && pt.enumConstants?.any { it.toString().contains("FILLED") } == true
            }
        }
        if (a00Method != null) {
            a00Method.isAccessible = true
            val enumParamIdx = a00Method.parameterTypes.indexOfFirst { pt ->
                pt.isEnum && pt.enumConstants?.any { it.toString().contains("FILLED") } == true
            }
            val enumConstants = a00Method.parameterTypes[enumParamIdx].enumConstants
            if (enumConstants != null) {
                var filledConstant: Any? = null
                var outlineConstant: Any? = null
                for (ec in enumConstants) {
                    val name = ec.toString().uppercase()
                    if (name.contains("FILLED")) filledConstant = ec
                    if (name.contains("OUTLINE")) outlineConstant = ec
                }
                fun callMethod(variant: Any?): Drawable? {
                    if (variant == null) return null
                    val args = arrayOfNulls<Any>(a00Method.parameterTypes.size)
                    for (i in a00Method.parameterTypes.indices) {
                        if (i == enumParamIdx) {
                            args[i] = variant
                        } else if (a00Method.parameterTypes[i].isAssignableFrom(child.javaClass) || a00Method.parameterTypes[i] == View::class.java) {
                            args[i] = child
                        } else if (a0sObj != null && a00Method.parameterTypes[i].isAssignableFrom(a0sObj.javaClass)) {
                            args[i] = a0sObj
                        } else {
                            args[i] = a0sObj
                        }
                    }
                    return (try { a00Method.invoke(null, *args) } catch (_: Throwable) { a00Method.invoke(child, *args) }) as? Drawable
                }
                if (filledConstant != null) {
                    filledDrawable = callMethod(filledConstant)
                }
                if (outlineConstant != null) {
                    outlineDrawable = callMethod(outlineConstant)
                }
            }
        }
    } catch (_: Throwable) {}

    // Strategy 2: Reflect field A0T (C03LV) which holds A00 (active) and A01 (inactive)
    if (filledDrawable == null || outlineDrawable == null) {
        val fieldA0T = child.javaClass.findFieldRecursively("A0T")
        val c03lv = fieldA0T?.get(child)
        if (c03lv != null) {
            try {
                val fieldA00 = c03lv.javaClass.findFieldRecursively("A00")
                val fieldA01 = c03lv.javaClass.findFieldRecursively("A01")
                val activeD = fieldA00?.get(c03lv) as? Drawable
                val inactiveD = fieldA01?.get(c03lv) as? Drawable
                if (activeD != null && filledDrawable == null) filledDrawable = activeD
                if (inactiveD != null && outlineDrawable == null) outlineDrawable = inactiveD
            } catch (_: Throwable) {}
        }
    }

    // Strategy 3: Call DxG() method if filledDrawable is missing
    if (filledDrawable == null) {
        try {
            val dxGMethod = child.javaClass.getMethod("DxG")
            val d = dxGMethod.invoke(child) as? Drawable
            if (d != null) filledDrawable = d
        } catch (_: Throwable) {}
    }

    // Strategy 4: Reflect field A02
    try {
        val fieldA02 = child.javaClass.findFieldRecursively("A02")
        val d = fieldA02?.get(child) as? Drawable
        if (d != null) {
            if (child.isSelected) {
                if (filledDrawable == null) filledDrawable = d
            } else {
                if (outlineDrawable == null) outlineDrawable = d
            }
        }
    } catch (_: Throwable) {}

    // Strategy 5: Fallback to ImageView child
    if ((filledDrawable == null || outlineDrawable == null) && child is ViewGroup) {
        val iv = findImageView(child)
        val d = iv?.drawable
        if (d != null) {
            if (child.isSelected) {
                if (filledDrawable == null) filledDrawable = d
            } else {
                if (outlineDrawable == null) outlineDrawable = d
            }
        }
    }

    return Pair(outlineDrawable, filledDrawable)
}

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

private fun drawableToBitmap(
    d: Drawable,
    tint: Int? = null,
    targetWidth: Int = 0,
    targetHeight: Int = 0
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

private fun extractBadge(child: View): Pair<String?, Boolean> {
    var countStr: String? = null
    var hasDot = false

    // Method 1: Spec method HNF()
    try {
        val hnfMethod = child.javaClass.getMethod("HNF")
        val res = hnfMethod.invoke(child)
        val count = when (res) {
            is Number -> res.toInt()
            is CharSequence -> res.toString().toIntOrNull()
            else -> null
        }
        if (count != null && count > 0) {
            countStr = if (count > 99) "99+" else count.toString()
        }
    } catch (_: Throwable) {}

    // Method 2: Spec field A0K.A07
    if (countStr == null) {
        try {
            val fA0K = child.javaClass.findFieldRecursively("A0K")
            val a0kObj = fA0K?.get(child)
            if (a0kObj != null) {
                val fA07 = a0kObj.javaClass.findFieldRecursively("A07")
                val res = fA07?.get(a0kObj)
                val count = when (res) {
                    is Number -> res.toInt()
                    is CharSequence -> res.toString().toIntOrNull()
                    else -> null
                }
                if (count != null && count > 0) {
                    countStr = if (count > 99) "99+" else count.toString()
                }
            }
        } catch (_: Throwable) {}
    }

    // Method 3: Spec method CP0()
    try {
        val cp0Method = child.javaClass.getMethod("CP0")
        val dotResult = cp0Method.invoke(child)
        if (dotResult == "dot" || dotResult == true) {
            hasDot = true
        }
    } catch (_: Throwable) {}

    // Method 4: Visual inspection fallback
    if (countStr == null && !hasDot && child is ViewGroup) {
        for (i in 0 until child.childCount) {
            val v = child.getChildAt(i)
            if (v is android.widget.TextView && v.visibility == View.VISIBLE) {
                val txt = v.text?.toString()?.trim()
                if (!txt.isNullOrEmpty() && txt.any { it.isDigit() }) {
                    countStr = txt
                    break
                }
            }
        }
    }

    return Pair(countStr, hasDot)
}

private fun extractSelectedIndex(tabBarContainer: ViewGroup, tabs: List<FacebookTabData>): Int {
    try {
        val fA00 = tabBarContainer.javaClass.findFieldRecursively("A00")
        if (fA00 != null) {
            val obj = fA00.get(tabBarContainer)
            val idx = (obj as? Number)?.toInt()
            if (idx != null && idx in tabs.indices) return idx
        }
    } catch (_: Throwable) {}

    for (i in tabs.indices) {
        if (tabs[i].tabView.isSelected || tabs[i].tabView.isActivated) {
            return i
        }
    }
    return 0
}

private fun clickFacebookTab(child: View) {
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
                val up = android.view.MotionEvent.obtain(now, now + 20, android.view.MotionEvent.ACTION_UP, x, y, 0)
                child.dispatchTouchEvent(down)
                child.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }
        } catch (_: Throwable) {}
    }
}

private fun findFacebookTabViews(container: ViewGroup): List<View> {
    val result = mutableListOf<View>()
    for (i in 0 until container.childCount) {
        val child = container.getChildAt(i)
        if (child.javaClass.name.contains("C02LI") || child.javaClass.name.endsWith("Tab")) {
            result.add(child)
        }
    }
    if (result.isEmpty()) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is ViewGroup) {
                for (j in 0 until child.childCount) {
                    val subChild = child.getChildAt(j)
                    if (subChild.javaClass.name.contains("C02LI") || subChild.javaClass.name.endsWith("Tab")) {
                        result.add(subChild)
                    }
                }
            }
        }
    }
    if (result.isEmpty()) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility != View.GONE) {
                result.add(child)
            }
        }
    }
    return result
}

private fun extractFacebookTabs(
    tabBarContainer: ViewGroup,
    isDark: Boolean,
    accentColor: Color,
    outlineCache: MutableMap<Int, Bitmap>,
    filledCache: MutableMap<Int, Bitmap>
): List<FacebookTabData> {
    val out = mutableListOf<FacebookTabData>()
    val tabViews = findFacebookTabViews(tabBarContainer)
    Log.i("HyperBounce", "FacebookNavBarHost: found ${tabViews.size} tab views in container")

    val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val activeTint = accentColor.toArgb()

    for ((index, child) in tabViews.withIndex()) {
        val (outlineDrawable, filledDrawable) = extractTabDrawables(child)

        if (outlineDrawable != null && !outlineCache.containsKey(index)) {
            drawableToBitmap(outlineDrawable, tint = inactiveTint)?.let { outlineCache[index] = it }
        }
        if (filledDrawable != null && !filledCache.containsKey(index)) {
            drawableToBitmap(filledDrawable, tint = activeTint)?.let { filledCache[index] = it }
        }

        var inactiveBmp = outlineCache[index]
        var activeBmp = filledCache[index]

        if (inactiveBmp == null && child is ViewGroup) {
            val iv = findImageView(child)
            if (iv?.drawable != null) {
                val bmpInactive = drawableToBitmap(iv.drawable, tint = inactiveTint)
                val bmpActive = drawableToBitmap(iv.drawable, tint = activeTint)
                if (child.isSelected) {
                    if (bmpActive != null && !filledCache.containsKey(index)) filledCache[index] = bmpActive
                } else {
                    if (bmpInactive != null && !outlineCache.containsKey(index)) outlineCache[index] = bmpInactive
                }
                inactiveBmp = outlineCache[index]
                activeBmp = filledCache[index]
            } else if (child.width > 0 && child.height > 0) {
                try {
                    val bmp = Bitmap.createBitmap(
                        child.width.coerceAtLeast(1),
                        child.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    child.draw(Canvas(bmp))
                    val targetTint = if (child.isSelected) activeTint else inactiveTint
                    val tintedBmp = tintBitmap(bmp, targetTint)
                    if (child.isSelected) {
                        if (!filledCache.containsKey(index)) filledCache[index] = tintedBmp
                    } else {
                        if (!outlineCache.containsKey(index)) outlineCache[index] = tintedBmp
                    }
                    inactiveBmp = outlineCache[index]
                    activeBmp = filledCache[index]
                } catch (_: Throwable) {}
            }
        }

        val label = child.contentDescription?.toString()
        val (badgeCount, hasBadgeDot) = extractBadge(child)

        val tabData = FacebookTabData(
            tabView = child,
            inactiveBitmap = inactiveBmp,
            activeBitmap = activeBmp ?: inactiveBmp,
            label = label,
            badgeCount = badgeCount,
            hasBadgeDot = hasBadgeDot
        )
        Log.i(
            "HyperBounce",
            "FacebookNavBarHost: tab[$index]: label='$label', badgeCount=$badgeCount, hasBadgeDot=$hasBadgeDot, inactiveBmp=${inactiveBmp != null}, activeBmp=${activeBmp != null}"
        )
        out.add(tabData)
    }

    return out
}

private fun accentColorFor(preset: String, customColor: Long, isDark: Boolean): Color = when (preset) {
    "Blue" -> Color(0xFF1877F2)
    "Pink" -> Color(0xFFFF2D78)
    "Purple" -> Color(0xFFAF52DE)
    "Orange" -> Color(0xFFFF9500)
    "Green" -> Color(0xFF34C759)
    "Custom" -> Color(customColor)
    else -> Color(0xFF1877F2)
}

@Composable
fun FacebookNavBarHost(
    tabBarContainer: ViewGroup,
    backdropSource: View,
    hyperBouncePrefs: HyperBouncePrefs,
) {
    val live = rememberLiveNavBarSettings(hyperBouncePrefs, "facebook")
    val isDark = when (live.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accentColor = accentColorFor(live.accent, live.customAccentColor, isDark)

    var tabs by remember { mutableStateOf(emptyList<FacebookTabData>()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val outlineBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }
    val filledBitmapsCache = remember { mutableMapOf<Int, Bitmap>() }
    val activity = (backdropSource.context as? Activity) ?: (tabBarContainer.context as? Activity)
    val scrollShrinkState = rememberScrollShrinkState(
        activity = activity,
        enabled = live.autoShrink,
        sensitivity = live.autoShrinkSensitivity
    )

    val currentTab = tabs.getOrNull(selectedIndex)
    val label = currentTab?.label?.lowercase() ?: ""
    val isReelsTab = (currentTab != null) && (label.contains("video") || label.contains("reel") || label.contains("watch"))
    LaunchedEffect(isReelsTab) {
        FacebookHook.setReelsActive(isReelsTab)
    }

    LaunchedEffect(tabBarContainer, isDark, accentColor) {
        outlineBitmapsCache.clear()
        filledBitmapsCache.clear()
        val activeTint = accentColor.toArgb()
        val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val initialTabs = extractFacebookTabs(tabBarContainer, isDark, accentColor, outlineBitmapsCache, filledBitmapsCache)
        tabs = initialTabs
        selectedIndex = extractSelectedIndex(tabBarContainer, tabs)

        while (true) {
            delay(150L)
            if (tabs.isEmpty() || tabs.any { it.inactiveBitmap == null }) {
                val extracted = extractFacebookTabs(tabBarContainer, isDark, accentColor, outlineBitmapsCache, filledBitmapsCache)
                if (extracted.isNotEmpty()) {
                    tabs = extracted
                }
            } else {
                val curSelected = extractSelectedIndex(tabBarContainer, tabs)
                val selectionChanged = (curSelected != selectedIndex && curSelected in tabs.indices)
                if (selectionChanged) {
                    selectedIndex = curSelected
                }

                var tabsMutated = false
                val updatedTabs = tabs.mapIndexed { idx, tab ->
                    val (count, dot) = extractBadge(tab.tabView)
                    var activeBmp = tab.activeBitmap
                    var inactiveBmp = tab.inactiveBitmap

                    if (idx == curSelected) {
                        if (!filledBitmapsCache.containsKey(idx)) {
                            val (_, activeD) = extractTabDrawables(tab.tabView)
                            if (activeD != null) {
                                val bmp = drawableToBitmap(activeD, tint = activeTint)
                                if (bmp != null) {
                                    filledBitmapsCache[idx] = bmp
                                }
                            }
                        }
                        if (filledBitmapsCache.containsKey(idx) && activeBmp != filledBitmapsCache[idx]) {
                            activeBmp = filledBitmapsCache[idx]
                            tabsMutated = true
                        }
                    } else {
                        // Unselected tab: ensure outline icon is used for inactiveBitmap
                        if (!outlineBitmapsCache.containsKey(idx)) {
                            val (outlineD, _) = extractTabDrawables(tab.tabView)
                            if (outlineD != null) {
                                val bmp = drawableToBitmap(outlineD, tint = inactiveTint)
                                if (bmp != null) {
                                    outlineBitmapsCache[idx] = bmp
                                }
                            }
                        }
                        if (outlineBitmapsCache.containsKey(idx) && inactiveBmp != outlineBitmapsCache[idx]) {
                            inactiveBmp = outlineBitmapsCache[idx]
                            tabsMutated = true
                        }
                    }

                    if (count != tab.badgeCount || dot != tab.hasBadgeDot || activeBmp != tab.activeBitmap || inactiveBmp != tab.inactiveBitmap) {
                        tabsMutated = true
                        tab.copy(badgeCount = count, hasBadgeDot = dot, activeBitmap = activeBmp, inactiveBitmap = inactiveBmp)
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

    val drawerProgress = FacebookHook.drawerProgressState.floatValue

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap ?: it.inactiveBitmap },
        selectedIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onTabSelected = { index ->
            selectedIndex = index
            Log.i("HyperBounce", "FacebookNavBarHost: tab selected: index=$index, performing click on child tab view")
            if (index in tabs.indices) {
                val tabView = tabs[index].tabView
                clickFacebookTab(tabView)
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
        drawerProgress = drawerProgress,
        onDrawerCloseClick = { FacebookHook.closeDrawer() },
    )
}
