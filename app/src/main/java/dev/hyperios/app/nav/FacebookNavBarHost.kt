package dev.hyperios.app.nav

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
    var activeDrawable: Drawable? = null
    var inactiveDrawable: Drawable? = null

    // Strategy 1: Reflect field A0T (C03LV) which holds A00 (active) and A01 (inactive)
    try {
        val fieldA0T = child.javaClass.findFieldRecursively("A0T")
        if (fieldA0T != null) {
            val obj = fieldA0T.get(child)
            if (obj != null) {
                val fA00 = obj.javaClass.findFieldRecursively("A00")
                activeDrawable = fA00?.get(obj) as? Drawable

                val fA01 = obj.javaClass.findFieldRecursively("A01")
                inactiveDrawable = fA01?.get(obj) as? Drawable
            }
        }
    } catch (_: Throwable) {}

    // Strategy 2: Call DxG() method
    if (activeDrawable == null && inactiveDrawable == null) {
        try {
            val dxGMethod = child.javaClass.getMethod("DxG")
            val d = dxGMethod.invoke(child) as? Drawable
            if (d != null) {
                activeDrawable = d
                inactiveDrawable = d
            }
        } catch (_: Throwable) {}
    }

    // Strategy 3: Reflect field A02
    if (activeDrawable == null && inactiveDrawable == null) {
        try {
            val fieldA02 = child.javaClass.findFieldRecursively("A02")
            val d = fieldA02?.get(child) as? Drawable
            if (d != null) {
                activeDrawable = d
                inactiveDrawable = d
            }
        } catch (_: Throwable) {}
    }

    // Strategy 4: Fallback to ImageView child
    if (activeDrawable == null && inactiveDrawable == null && child is ViewGroup) {
        val iv = findImageView(child)
        val d = iv?.drawable
        if (d != null) {
            activeDrawable = d
            inactiveDrawable = d
        }
    }

    return Pair(inactiveDrawable, activeDrawable ?: inactiveDrawable)
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
        bmp
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
    accentColor: Color
): List<FacebookTabData> {
    val out = mutableListOf<FacebookTabData>()
    val tabViews = findFacebookTabViews(tabBarContainer)

    val inactiveTint = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#65676B")
    val activeTint = accentColor.toArgb()

    for (child in tabViews) {
        val (inactiveDrawable, activeDrawable) = extractTabDrawables(child)

        var inactiveBmp = inactiveDrawable?.let { drawableToBitmap(it, tint = inactiveTint) }
        var activeBmp = activeDrawable?.let {
            if (activeDrawable !== inactiveDrawable) {
                drawableToBitmap(it, tint = null) ?: drawableToBitmap(it, tint = activeTint)
            } else {
                drawableToBitmap(it, tint = activeTint)
            }
        }

        if (inactiveBmp == null && child is ViewGroup) {
            val iv = findImageView(child)
            if (iv?.drawable != null) {
                inactiveBmp = drawableToBitmap(iv.drawable, tint = inactiveTint)
                activeBmp = drawableToBitmap(iv.drawable, tint = activeTint)
            } else if (child.width > 0 && child.height > 0) {
                try {
                    val bmp = Bitmap.createBitmap(
                        child.width.coerceAtLeast(1),
                        child.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    child.draw(Canvas(bmp))
                    inactiveBmp = bmp
                    activeBmp = bmp
                } catch (_: Throwable) {}
            }
        }

        val label = child.contentDescription?.toString()
        val (badgeCount, hasBadgeDot) = extractBadge(child)

        out.add(
            FacebookTabData(
                tabView = child,
                inactiveBitmap = inactiveBmp,
                activeBitmap = activeBmp ?: inactiveBmp,
                label = label,
                badgeCount = badgeCount,
                hasBadgeDot = hasBadgeDot
            )
        )
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
    val activity = (backdropSource.context as? Activity) ?: (tabBarContainer.context as? Activity)
    val scrollShrinkState = rememberScrollShrinkState(
        activity = activity,
        enabled = live.autoShrink,
        sensitivity = live.autoShrinkSensitivity
    )

    LaunchedEffect(tabBarContainer, isDark, accentColor) {
        tabs = extractFacebookTabs(tabBarContainer, isDark, accentColor)
        selectedIndex = extractSelectedIndex(tabBarContainer, tabs)

        while (true) {
            delay(150L)
            if (tabs.isEmpty() || tabs.any { it.inactiveBitmap == null }) {
                val extracted = extractFacebookTabs(tabBarContainer, isDark, accentColor)
                if (extracted.isNotEmpty()) {
                    tabs = extracted
                }
            } else {
                val curSelected = extractSelectedIndex(tabBarContainer, tabs)
                if (curSelected != selectedIndex && curSelected in tabs.indices) {
                    selectedIndex = curSelected
                }
                var badgesChanged = false
                val updatedTabs = tabs.map { tab ->
                    val (count, dot) = extractBadge(tab.tabView)
                    if (count != tab.badgeCount || dot != tab.hasBadgeDot) {
                        badgesChanged = true
                        tab.copy(badgeCount = count, hasBadgeDot = dot)
                    } else {
                        tab
                    }
                }
                if (badgesChanged) {
                    tabs = updatedTabs
                }
            }
        }
    }

    if (tabs.isEmpty()) return

    val profileIndex = tabs.indexOfLast {
        val desc = it.label?.lowercase() ?: ""
        desc.contains("menu") || desc.contains("profile") || desc.contains("account")
    }.takeIf { it >= 0 } ?: if (tabs.size >= 5) tabs.size - 1 else -1

    LiquidNavigationBar(
        bottomPadding = live.bottomMargin.dp,
        tabs = tabs.map { it.inactiveBitmap },
        activeTabs = tabs.map { it.activeBitmap ?: it.inactiveBitmap },
        selectedIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onTabSelected = { index ->
            selectedIndex = index
            if (index in tabs.indices) {
                val tabView = tabs[index].tabView
                clickFacebookTab(tabView)
            }
        },
        sourceView = backdropSource,
        circularIndices = if (profileIndex >= 0) setOf(profileIndex) else emptySet(),
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
        enablePixelCopy = false
    )
}
