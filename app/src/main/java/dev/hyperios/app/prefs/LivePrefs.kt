package dev.hyperios.app.prefs

/*
 * Wraps HyperBouncePrefs' reads in a SharedPreferences.OnSharedPreferenceChangeListener so
 * the injected Compose UI (LiquidNavigationBar, the toggle/slider glass engines) updates
 * live when the user changes a setting in the companion app — no need to force-stop and
 * reopen the hooked app. Structural checks (module enabled, per-app enabled, which render
 * engine, keypad enabled) are intentionally NOT live — those gate whether a hook installs
 * at all, which libxposed's hook API applies once per package load, so changing them still
 * needs an app restart. Only the visual "how it looks" parameters are live.
 */

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class NavBarLiveSettings(
    val liquidGlass: Boolean,
    val glassIntensity: Float,
    val barBlurIntensity: Float,
    val containerOpacity: Float,
    val cornerRadius: Float,
    val barHeight: Float,
    val tabWidth: Float,
    val bottomMargin: Float,
    val themeMode: String,
    val accent: String,
    val customAccentColor: Long,
    val pillGlassIntensity: Float,
    val pillContainerOpacity: Float,
    val pillChromaticAberration: Boolean,
    val pillShadow: Boolean,
    val autoShrink: Boolean,
    val autoShrinkScale: Float,
    val autoShrinkSensitivity: Float
)

private fun readNavBarSettings(hyperBouncePrefs: HyperBouncePrefs, appKey: String) = NavBarLiveSettings(
    liquidGlass = hyperBouncePrefs.appLiquidGlass(appKey),
    glassIntensity = hyperBouncePrefs.appGlassIntensity(appKey),
    barBlurIntensity = hyperBouncePrefs.appBarBlurIntensity(appKey),
    containerOpacity = hyperBouncePrefs.appContainerOpacity(appKey),
    cornerRadius = hyperBouncePrefs.appCornerRadius(appKey),
    barHeight = hyperBouncePrefs.appBarHeight(appKey),
    tabWidth = hyperBouncePrefs.appTabWidth(appKey),
    bottomMargin = hyperBouncePrefs.appBottomMargin(appKey),
    themeMode = hyperBouncePrefs.appThemeMode(appKey),
    accent = hyperBouncePrefs.appAccent(appKey),
    customAccentColor = hyperBouncePrefs.appCustomAccentColor(appKey),
    pillGlassIntensity = hyperBouncePrefs.appPillGlassIntensity(appKey),
    pillContainerOpacity = hyperBouncePrefs.appPillContainerOpacity(appKey),
    pillChromaticAberration = hyperBouncePrefs.appPillChromaticAberration(appKey),
    pillShadow = hyperBouncePrefs.appPillShadow(appKey),
    autoShrink = hyperBouncePrefs.appAutoShrink(appKey),
    autoShrinkScale = hyperBouncePrefs.appAutoShrinkScale(appKey),
    autoShrinkSensitivity = 1.0f
)

@Composable
fun rememberLiveNavBarSettings(hyperBouncePrefs: HyperBouncePrefs, appKey: String): NavBarLiveSettings {
    var settings by remember(hyperBouncePrefs, appKey) {
        mutableStateOf(readNavBarSettings(hyperBouncePrefs, appKey))
    }
    DisposableEffect(hyperBouncePrefs, appKey) {
        val prefix = "app_${appKey}_"
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey != null && changedKey.startsWith(prefix)) {
                settings = readNavBarSettings(hyperBouncePrefs, appKey)
            }
        }
        hyperBouncePrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { hyperBouncePrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

@Composable
fun rememberLiveGlassFeel(hyperBouncePrefs: HyperBouncePrefs, prefix: String): GlassFeelValues {
    var feel by remember(hyperBouncePrefs, prefix) {
        mutableStateOf(hyperBouncePrefs.glassFeel(prefix))
    }
    val isHotReload = remember(hyperBouncePrefs) { hyperBouncePrefs.hotReloadEnabled }
    if (isHotReload) {
        DisposableEffect(hyperBouncePrefs, prefix) {
            val keyPrefix = "${prefix}_"
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey != null && changedKey.startsWith(keyPrefix)) {
                    feel = hyperBouncePrefs.glassFeel(prefix)
                }
            }
            hyperBouncePrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { hyperBouncePrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
    return feel
}

@Composable
fun rememberLiveToggleColor(hyperBouncePrefs: HyperBouncePrefs): Long {
    var color by remember(hyperBouncePrefs) {
        mutableStateOf(hyperBouncePrefs.toggleAccentColor())
    }
    val isHotReload = remember(hyperBouncePrefs) { hyperBouncePrefs.hotReloadEnabled }
    if (isHotReload) {
        DisposableEffect(hyperBouncePrefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == "toggle_accent" || changedKey == "toggle_custom_color") {
                    color = hyperBouncePrefs.toggleAccentColor()
                }
            }
            hyperBouncePrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { hyperBouncePrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
    return color
}

@Composable
fun rememberLiveSliderColor(hyperBouncePrefs: HyperBouncePrefs): Long {
    var color by remember(hyperBouncePrefs) {
        mutableStateOf(hyperBouncePrefs.sliderAccentColor())
    }
    val isHotReload = remember(hyperBouncePrefs) { hyperBouncePrefs.hotReloadEnabled }
    if (isHotReload) {
        DisposableEffect(hyperBouncePrefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == "slider_accent" || changedKey == "slider_custom_color") {
                    color = hyperBouncePrefs.sliderAccentColor()
                }
            }
            hyperBouncePrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { hyperBouncePrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }
    return color
}
