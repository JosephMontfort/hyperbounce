package dev.joseph.hyperbounce.ui.state

/*
 * Persists every setting from SettingsModels.kt to a plain SharedPreferences file
 * ("hyperbounce_settings"). This file is the natural handoff point for Phase 2: the
 * Xposed hooks will read the same file via XSharedPreferences once wiring begins (see
 * docs/UI_DESIGN_NOTES.md). Nothing on the hook side reads it yet in this Phase 1 build.
 *
 * One primitive SharedPreferences entry per field (no JSON/serialization dependency
 * needed — none is present in this project, and this keeps every value independently
 * readable/writable, which matters once XSharedPreferences reads it from another process).
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

private const val PREFS_NAME = "hyperbounce_settings"

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var moduleEnabled by prefsBooleanState("module_enabled", true)
        private set

    val instagram = AppSettingsHolder(NavApp.Instagram)
    val whatsapp = AppSettingsHolder(NavApp.WhatsApp)
    val youtube = AppSettingsHolder(NavApp.YouTube)

    fun appSettings(app: NavApp): AppSettingsHolder = when (app) {
        NavApp.Instagram -> instagram
        NavApp.WhatsApp -> whatsapp
        NavApp.YouTube -> youtube
    }

    var toggleEngine by prefsEnumState("engine_toggle_render", RenderEngine.LiquidGlass)
        private set
    var sliderEngine by prefsEnumState("engine_slider_render", RenderEngine.LiquidGlass)
        private set
    var keypadEnabled by prefsBooleanState("engine_keypad_enabled", true)
        private set

    val toggleGlass = GlassFeelHolder("toggle_glass", EngineSettings().toggleGlass)
    val sliderGlass = GlassFeelHolder("slider_glass", EngineSettings().sliderGlass)

    val globalGlass = GlobalGlassHolder()

    fun setModuleEnabled(value: Boolean) {
        moduleEnabled = value
    }

    fun setToggleEngine(value: RenderEngine) {
        toggleEngine = value
    }

    fun setSliderEngine(value: RenderEngine) {
        sliderEngine = value
    }

    fun setKeypadEnabled(value: Boolean) {
        keypadEnabled = value
    }

    /** Clears every stored key, restoring every field to the defaults in SettingsModels.kt. */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        instagram.reloadFromDefaults()
        whatsapp.reloadFromDefaults()
        youtube.reloadFromDefaults()
        toggleGlass.reloadFromDefaults()
        sliderGlass.reloadFromDefaults()
        globalGlass.reloadFromDefaults()
        moduleEnabled = true
        toggleEngine = RenderEngine.LiquidGlass
        sliderEngine = RenderEngine.LiquidGlass
        keypadEnabled = true
    }

    // --- per-app nav bar settings -------------------------------------------------------

    inner class AppSettingsHolder(val app: NavApp) {
        private val prefix = "app_${app.name.lowercase()}"
        private val defaults = NavBarAppSettings()

        var enabled by prefsBooleanState("${prefix}_enabled", defaults.enabled)
            private set
        var glassIntensity by prefsFloatState("${prefix}_glass_intensity", defaults.glassIntensity)
            private set
        var containerOpacity by prefsFloatState("${prefix}_container_opacity", defaults.containerOpacity)
            private set
        var cornerRadius by prefsFloatState("${prefix}_corner_radius", defaults.cornerRadius)
            private set
        var barHeight by prefsFloatState("${prefix}_bar_height", defaults.barHeight)
            private set
        var tabWidth by prefsFloatState("${prefix}_tab_width", defaults.tabWidth)
            private set
        var themeMode by prefsEnumState("${prefix}_theme_mode", defaults.themeMode)
            private set
        var accent by prefsEnumState("${prefix}_accent", defaults.accent)
            private set

        fun snapshot(): NavBarAppSettings = NavBarAppSettings(
            enabled, glassIntensity, containerOpacity, cornerRadius, barHeight, tabWidth, themeMode, accent
        )

        fun setEnabled(v: Boolean) { enabled = v }
        fun setGlassIntensity(v: Float) { glassIntensity = v }
        fun setContainerOpacity(v: Float) { containerOpacity = v }
        fun setCornerRadius(v: Float) { cornerRadius = v }
        fun setBarHeight(v: Float) { barHeight = v }
        fun setTabWidth(v: Float) { tabWidth = v }
        fun setThemeMode(v: ThemeMode) { themeMode = v }
        fun setAccent(v: AccentPreset) { accent = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            glassIntensity = defaults.glassIntensity
            containerOpacity = defaults.containerOpacity
            cornerRadius = defaults.cornerRadius
            barHeight = defaults.barHeight
            tabWidth = defaults.tabWidth
            themeMode = defaults.themeMode
            accent = defaults.accent
        }
    }

    // --- glass "feel" settings shared per engine ------------------------------------------

    inner class GlassFeelHolder(private val prefix: String, private val defaults: GlassFeel) {
        var blurDp by prefsFloatState("${prefix}_blur", defaults.blurDp)
            private set
        var lensSizeDp by prefsFloatState("${prefix}_lens_size", defaults.lensSizeDp)
            private set
        var lensDepthDp by prefsFloatState("${prefix}_lens_depth", defaults.lensDepthDp)
            private set
        var highlightStrength by prefsFloatState("${prefix}_highlight", defaults.highlightStrength)
            private set
        var shadowRadiusDp by prefsFloatState("${prefix}_shadow", defaults.shadowRadiusDp)
            private set
        var dragSensitivity by prefsFloatState("${prefix}_drag_sensitivity", defaults.dragSensitivity)
            private set
        var springDamping by prefsFloatState("${prefix}_spring_damping", defaults.springDamping)
            private set
        var springStiffness by prefsFloatState("${prefix}_spring_stiffness", defaults.springStiffness)
            private set

        fun snapshot(): GlassFeel = GlassFeel(
            blurDp, lensSizeDp, lensDepthDp, highlightStrength, shadowRadiusDp,
            dragSensitivity, springDamping, springStiffness
        )

        fun setBlurDp(v: Float) { blurDp = v }
        fun setLensSizeDp(v: Float) { lensSizeDp = v }
        fun setLensDepthDp(v: Float) { lensDepthDp = v }
        fun setHighlightStrength(v: Float) { highlightStrength = v }
        fun setShadowRadiusDp(v: Float) { shadowRadiusDp = v }
        fun setDragSensitivity(v: Float) { dragSensitivity = v }
        fun setSpringDamping(v: Float) { springDamping = v }
        fun setSpringStiffness(v: Float) { springStiffness = v }

        fun reloadFromDefaults() {
            blurDp = defaults.blurDp
            lensSizeDp = defaults.lensSizeDp
            lensDepthDp = defaults.lensDepthDp
            highlightStrength = defaults.highlightStrength
            shadowRadiusDp = defaults.shadowRadiusDp
            dragSensitivity = defaults.dragSensitivity
            springDamping = defaults.springDamping
            springStiffness = defaults.springStiffness
        }
    }

    // --- global glass appearance (app chrome only — never toggles/sliders) ---------------

    inner class GlobalGlassHolder {
        private val defaults = GlobalGlassSettings()

        var enabled by prefsBooleanState("global_glass_enabled", defaults.enabled)
            private set
        var blurDp by prefsFloatState("global_glass_blur", defaults.blurDp)
            private set
        var refractionHeightDp by prefsFloatState("global_glass_refraction_height", defaults.refractionHeightDp)
            private set
        var refractionAmountDp by prefsFloatState("global_glass_refraction_amount", defaults.refractionAmountDp)
            private set
        var chromaticAberration by prefsBooleanState("global_glass_chromatic_aberration", defaults.chromaticAberration)
            private set
        var hapticsEnabled by prefsBooleanState("global_glass_haptics", defaults.hapticsEnabled)
            private set

        fun snapshot(): GlobalGlassSettings = GlobalGlassSettings(
            enabled, blurDp, refractionHeightDp, refractionAmountDp, chromaticAberration, hapticsEnabled
        )

        fun setEnabled(v: Boolean) { enabled = v }
        fun setBlurDp(v: Float) { blurDp = v }
        fun setRefractionHeightDp(v: Float) { refractionHeightDp = v }
        fun setRefractionAmountDp(v: Float) { refractionAmountDp = v }
        fun setChromaticAberration(v: Boolean) { chromaticAberration = v }
        fun setHapticsEnabled(v: Boolean) { hapticsEnabled = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            blurDp = defaults.blurDp
            refractionHeightDp = defaults.refractionHeightDp
            refractionAmountDp = defaults.refractionAmountDp
            chromaticAberration = defaults.chromaticAberration
            hapticsEnabled = defaults.hapticsEnabled
        }
    }

    // --- small property-delegate helpers over SharedPreferences --------------------------

    private fun prefsBooleanState(key: String, default: Boolean) =
        PrefBooleanDelegate(prefs, key, default)

    private fun prefsFloatState(key: String, default: Float) =
        PrefFloatDelegate(prefs, key, default)

    private inline fun <reified E : Enum<E>> prefsEnumState(key: String, default: E) =
        PrefEnumDelegate(prefs, key, default, enumValues<E>())
}

private class PrefBooleanDelegate(
    private val prefs: SharedPreferences,
    private val key: String,
    default: Boolean
) {
    private var state by mutableStateOf(prefs.getBoolean(key, default))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        state = value
        prefs.edit().putBoolean(key, value).apply()
    }
}

private class PrefFloatDelegate(
    private val prefs: SharedPreferences,
    private val key: String,
    default: Float
) {
    private var state by mutableStateOf(prefs.getFloat(key, default))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Float = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        state = value
        prefs.edit().putFloat(key, value).apply()
    }
}

private class PrefEnumDelegate<E : Enum<E>>(
    private val prefs: SharedPreferences,
    private val key: String,
    default: E,
    private val values: Array<E>
) {
    private var state by mutableStateOf(
        prefs.getString(key, default.name)?.let { name -> values.find { it.name == name } } ?: default
    )

    operator fun getValue(thisRef: Any?, property: KProperty<*>): E = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: E) {
        state = value
        prefs.edit().putString(key, value.name).apply()
    }
}
