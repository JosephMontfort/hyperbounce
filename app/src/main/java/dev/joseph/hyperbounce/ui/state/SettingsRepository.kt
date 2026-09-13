package dev.joseph.hyperbounce.ui.state

/*
 * Persists every setting from SettingsModels.kt to a plain SharedPreferences file
 * ("hyperbounce_settings") — this app's own always-available source of truth, read
 * synchronously, same as Phase 1. Phase 2 additionally mirrors every write out to the
 * Xposed framework's remote preferences (see ui/state/XposedServiceBridge.kt for the
 * write side, prefs/HyperBouncePrefs.kt for the hook-side read), so the hooks running
 * inside Instagram/WhatsApp/YouTube/HyperOS system apps can see the same values. That
 * mirroring is best-effort and asynchronous — nothing in this class ever waits on it.
 *
 * One primitive SharedPreferences entry per field (no JSON/serialization dependency
 * needed — none is present in this project, and this keeps every value independently
 * readable/writable, which matters since the hook side reads them individually too).
 *
 * Every property below is `var x by ... private set` PLUS an explicit `fun setX(...)`
 * (so callers can pass a bound method reference like `repository::setModuleEnabled`
 * instead of a property-setter reference, which Kotlin doesn't let you take for a
 * private-set property from outside the class). Kotlin compiles a private-set property's
 * setter to a JVM method with the same erased signature as the explicit `setX(...)`
 * function, which the JVM rejects as a duplicate method ("platform declaration clash")
 * regardless of the property setter's Kotlin-level visibility. Every explicit setter here
 * carries `@JvmName(...)` to give it a distinct JVM-level symbol while keeping the Kotlin
 * source name (and therefore every call site) unchanged.
 */

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.joseph.hyperbounce.launcher.ScreenUnlockDefaults
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty

private const val PREFS_NAME = "hyperbounce_settings"

class SettingsRepository(context: Context) {

    companion object {
        private var appContext: Context? = null

        fun sendSettingsChangedBroadcast(key: String, value: Any?) {
            val ctx = appContext ?: return
            try {
                val intent = Intent("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED").apply {
                    putExtra("key", key)
                    when (value) {
                        is Boolean -> putExtra("value_boolean", value)
                        is Float -> putExtra("value_float", value)
                        is Int -> putExtra("value_int", value)
                        is Long -> putExtra("value_long", value)
                        is String -> putExtra("value_string", value)
                    }
                }
                ctx.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        appContext = context.applicationContext
        // Local prefs (above) are this app's own always-available source of truth — the UI
        // never waits on the Xposed service. This just also mirrors everything out to the
        // hooks once/if that service becomes available: every individual write already
        // mirrors itself (see the three Pref*Delegate classes below), but this catches
        // anything changed *before* the service was bound (e.g. the very first run).
        XposedServiceBridge.init()
        XposedServiceBridge.onBind { pushAllLocalToRemote() }
    }

    private fun pushAllLocalToRemote() {
        val remote = XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME) ?: return
        val editor = remote.edit()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        editor.apply()
    }

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
    val lockScreenSheet = LockScreenSheetHolder()
    val notificationSheet = NotificationSheetHolder()
    val lockScreenKeypad = LockScreenKeypadHolder()
    val screenUnlock = ScreenUnlockHolder()
    val appTransition = AppTransitionHolder()
    val controlCenter = ControlCenterHolder()
    val morphPhysics = MorphPhysicsHolder()
    val safeMode = SafeModeHolder()

    @JvmName("setModuleEnabledPref")
    fun setModuleEnabled(value: Boolean) {
        moduleEnabled = value
    }

    @JvmName("setToggleEnginePref")
    fun setToggleEngine(value: RenderEngine) {
        toggleEngine = value
    }

    @JvmName("setSliderEnginePref")
    fun setSliderEngine(value: RenderEngine) {
        sliderEngine = value
    }

    @JvmName("setKeypadEnabledPref")
    fun setKeypadEnabled(value: Boolean) {
        keypadEnabled = value
        lockScreenKeypad.setEnabled(value)
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
        lockScreenSheet.reloadFromDefaults()
        notificationSheet.reloadFromDefaults()
        lockScreenKeypad.reloadFromDefaults()
        screenUnlock.reloadFromDefaults()
        appTransition.reloadFromDefaults()
        controlCenter.reloadFromDefaults()
        morphPhysics.reloadFromDefaults()
        moduleEnabled = true
        toggleEngine = RenderEngine.LiquidGlass
        sliderEngine = RenderEngine.LiquidGlass
        keypadEnabled = true
    }

    // --- per-app nav bar settings -------------------------------------------------------

    inner class AppSettingsHolder(val app: NavApp) {
        private val prefix = "app_${app.name.lowercase()}"
        // Defaults match each app's actual current hardcoded nav-bar values exactly (see
        // nav/InstagramNavBarHost.kt, nav/WhatsAppNavBarHost.kt, nav/YouTubeNavBarHost.kt)
        // so wiring these into the hooks in Phase 2 doesn't silently change anyone's
        // current behavior — WhatsApp in particular used a taller/wider/more-rounded bar
        // (70/92/36) than Instagram and YouTube's shared defaults (64/64/32).
        private val defaults = when (app) {
            NavApp.WhatsApp -> NavBarAppSettings(barHeight = 70f, tabWidth = 92f, cornerRadius = 36f, bottomMargin = 40f)
            NavApp.YouTube -> NavBarAppSettings(bottomMargin = 28f)
            NavApp.Instagram -> NavBarAppSettings(bottomMargin = 40f)
        }

        var enabled by prefsBooleanState("${prefix}_enabled", defaults.enabled)
            private set
        var liquidGlass by prefsBooleanState("${prefix}_liquid_glass", defaults.liquidGlass)
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
        var bottomMargin by prefsFloatState("${prefix}_bottom_margin", defaults.bottomMargin)
            private set
        var themeMode by prefsEnumState("${prefix}_theme_mode", defaults.themeMode)
            private set
        var accent by prefsEnumState("${prefix}_accent", defaults.accent)
            private set
        var customAccentColor by prefsLongState("${prefix}_custom_accent_color", defaults.customAccentColor)
            private set
        var pillGlassIntensity by prefsFloatState("${prefix}_pill_glass_intensity", defaults.pillGlassIntensity)
            private set
        var pillContainerOpacity by prefsFloatState("${prefix}_pill_container_opacity", defaults.pillContainerOpacity)
            private set
        var pillChromaticAberration by prefsBooleanState("${prefix}_pill_chromatic_aberration", defaults.pillChromaticAberration)
            private set
        var pillShadow by prefsBooleanState("${prefix}_pill_shadow", defaults.pillShadow)
            private set
        var autoShrink by prefsBooleanState("${prefix}_auto_shrink", defaults.autoShrink)
            private set
        var autoShrinkScale by prefsFloatState("${prefix}_auto_shrink_scale", defaults.autoShrinkScale)
            private set
        var autoShrinkSensitivity by prefsFloatState("${prefix}_auto_shrink_sensitivity", defaults.autoShrinkSensitivity)
            private set

        fun snapshot(): NavBarAppSettings = NavBarAppSettings(
            enabled, liquidGlass, glassIntensity, containerOpacity, cornerRadius, barHeight, tabWidth, bottomMargin,
            themeMode, accent, customAccentColor, pillGlassIntensity, pillContainerOpacity, pillChromaticAberration, pillShadow,
            autoShrink, autoShrinkScale, autoShrinkSensitivity
        )

        @JvmName("setEnabledPref")
        fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setLiquidGlassPref")
        fun setLiquidGlass(v: Boolean) { liquidGlass = v }
        @JvmName("setGlassIntensityPref")
        fun setGlassIntensity(v: Float) { glassIntensity = v }
        @JvmName("setContainerOpacityPref")
        fun setContainerOpacity(v: Float) { containerOpacity = v }
        @JvmName("setCornerRadiusPref")
        fun setCornerRadius(v: Float) { cornerRadius = v }
        @JvmName("setBarHeightPref")
        fun setBarHeight(v: Float) { barHeight = v }
        @JvmName("setTabWidthPref")
        fun setTabWidth(v: Float) { tabWidth = v }
        @JvmName("setBottomMarginPref")
        fun setBottomMargin(v: Float) { bottomMargin = v }
        @JvmName("setThemeModePref")
        fun setThemeMode(v: ThemeMode) { themeMode = v }
        @JvmName("setAccentPref")
        fun setAccent(v: AccentPreset) { accent = v }
        @JvmName("setCustomAccentColorPref")
        fun setCustomAccentColor(v: Long) { customAccentColor = v }
        @JvmName("setPillGlassIntensityPref")
        fun setPillGlassIntensity(v: Float) { pillGlassIntensity = v }
        @JvmName("setPillContainerOpacityPref")
        fun setPillContainerOpacity(v: Float) { pillContainerOpacity = v }
        @JvmName("setPillChromaticAberrationPref")
        fun setPillChromaticAberration(v: Boolean) { pillChromaticAberration = v }
        @JvmName("setPillShadowPref")
        fun setPillShadow(v: Boolean) { pillShadow = v }
        @JvmName("setAutoShrinkPref")
        fun setAutoShrink(v: Boolean) { autoShrink = v }
        @JvmName("setAutoShrinkScalePref")
        fun setAutoShrinkScale(v: Float) { autoShrinkScale = v }
        @JvmName("setAutoShrinkSensitivityPref")
        fun setAutoShrinkSensitivity(v: Float) { autoShrinkSensitivity = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            liquidGlass = defaults.liquidGlass
            glassIntensity = defaults.glassIntensity
            containerOpacity = defaults.containerOpacity
            cornerRadius = defaults.cornerRadius
            barHeight = defaults.barHeight
            tabWidth = defaults.tabWidth
            bottomMargin = defaults.bottomMargin
            themeMode = defaults.themeMode
            accent = defaults.accent
            customAccentColor = defaults.customAccentColor
            pillGlassIntensity = defaults.pillGlassIntensity
            pillContainerOpacity = defaults.pillContainerOpacity
            pillChromaticAberration = defaults.pillChromaticAberration
            pillShadow = defaults.pillShadow
            autoShrink = defaults.autoShrink
            autoShrinkScale = defaults.autoShrinkScale
            autoShrinkSensitivity = defaults.autoShrinkSensitivity
        }
    }

    // --- glass "feel" settings shared per engine ------------------------------------------

    inner class GlassFeelHolder(val prefix: String, val defaults: GlassFeel) {
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

        @JvmName("setBlurDpPref")
        fun setBlurDp(v: Float) { blurDp = v }
        @JvmName("setLensSizeDpPref")
        fun setLensSizeDp(v: Float) { lensSizeDp = v }
        @JvmName("setLensDepthDpPref")
        fun setLensDepthDp(v: Float) { lensDepthDp = v }
        @JvmName("setHighlightStrengthPref")
        fun setHighlightStrength(v: Float) { highlightStrength = v }
        @JvmName("setShadowRadiusDpPref")
        fun setShadowRadiusDp(v: Float) { shadowRadiusDp = v }
        @JvmName("setDragSensitivityPref")
        fun setDragSensitivity(v: Float) { dragSensitivity = v }
        @JvmName("setSpringDampingPref")
        fun setSpringDamping(v: Float) { springDamping = v }
        @JvmName("setSpringStiffnessPref")
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

        @JvmName("setEnabledPref")
        fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setBlurDpPref")
        fun setBlurDp(v: Float) { blurDp = v }
        @JvmName("setRefractionHeightDpPref")
        fun setRefractionHeightDp(v: Float) { refractionHeightDp = v }
        @JvmName("setRefractionAmountDpPref")
        fun setRefractionAmountDp(v: Float) { refractionAmountDp = v }
        @JvmName("setChromaticAberrationPref")
        fun setChromaticAberration(v: Boolean) { chromaticAberration = v }
        @JvmName("setHapticsEnabledPref")
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

    // --- lock screen sheet settings (Feature 1) -------------------------------------------

    inner class LockScreenSheetHolder {
        private val defaults = LockScreenSheetSettings()

        var enabled by prefsBooleanState("lock_sheet_enabled", defaults.enabled); private set
        var refractionHeight by prefsFloatState("lock_card_refraction_height", defaults.refractionHeight); private set
        var sideRefraction by prefsFloatState("lock_card_side_refraction", defaults.sideRefraction); private set
        var bottomRefraction by prefsFloatState("lock_card_bottom_refraction", defaults.bottomRefraction); private set
        var bevelWidth by prefsFloatState("lock_card_bevel_width", defaults.bevelWidth); private set
        var leftBevelWidth by prefsFloatState("lock_card_left_bevel", defaults.leftBevelWidth); private set
        var rightBevelWidth by prefsFloatState("lock_card_right_bevel", defaults.rightBevelWidth); private set
        var bottomBevelWidth by prefsFloatState("lock_card_bottom_bevel", defaults.bottomBevelWidth); private set
        var dispersionStrength by prefsFloatState("lock_card_dispersion", defaults.dispersionStrength); private set
        var specularStrength by prefsFloatState("lock_card_specular", defaults.specularStrength); private set
        var hairlineStrength by prefsFloatState("lock_card_hairline", defaults.hairlineStrength); private set
        var innerGlowStrength by prefsFloatState("lock_card_inner_glow", defaults.innerGlowStrength); private set
        var innerShadow by prefsFloatState("lock_card_inner_shadow", defaults.innerShadow); private set
        var saturationFactor by prefsFloatState("lock_card_saturation", defaults.saturationFactor); private set
        var rimSoftness by prefsFloatState("lock_card_rim_softness", defaults.rimSoftness); private set
        var blurRadius by prefsFloatState("lock_card_blur_radius", defaults.blurRadius); private set
        var cornerRadius by prefsFloatState("lock_card_corner_radius", defaults.cornerRadius); private set
        var pillWidth by prefsFloatState("lock_pill_width", defaults.pillWidth); private set
        var pillHeight by prefsFloatState("lock_pill_height", defaults.pillHeight); private set
        var pillBottomMargin by prefsFloatState("lock_pill_bottom_margin", defaults.pillBottomMargin); private set

        fun snapshot(): LockScreenSheetSettings = LockScreenSheetSettings(
            enabled, refractionHeight, sideRefraction, bottomRefraction, bevelWidth, leftBevelWidth, rightBevelWidth, bottomBevelWidth,
            dispersionStrength, specularStrength, hairlineStrength, innerGlowStrength, innerShadow, saturationFactor, rimSoftness, blurRadius,
            cornerRadius, pillWidth, pillHeight, pillBottomMargin
        )

        @JvmName("setSheetEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setSheetRefractionHeightPref") fun setRefractionHeight(v: Float) { refractionHeight = v }
        @JvmName("setSheetSideRefractionPref") fun setSideRefraction(v: Float) { sideRefraction = v }
        @JvmName("setSheetBottomRefractionPref") fun setBottomRefraction(v: Float) { bottomRefraction = v }
        @JvmName("setSheetBevelWidthPref") fun setBevelWidth(v: Float) { bevelWidth = v }
        @JvmName("setSheetLeftBevelWidthPref") fun setLeftBevelWidth(v: Float) { leftBevelWidth = v }
        @JvmName("setSheetRightBevelWidthPref") fun setRightBevelWidth(v: Float) { rightBevelWidth = v }
        @JvmName("setSheetBottomBevelWidthPref") fun setBottomBevelWidth(v: Float) { bottomBevelWidth = v }
        @JvmName("setSheetDispersionStrengthPref") fun setDispersionStrength(v: Float) { dispersionStrength = v }
        @JvmName("setSheetSpecularStrengthPref") fun setSpecularStrength(v: Float) { specularStrength = v }
        @JvmName("setSheetHairlineStrengthPref") fun setHairlineStrength(v: Float) { hairlineStrength = v }
        @JvmName("setSheetInnerGlowStrengthPref") fun setInnerGlowStrength(v: Float) { innerGlowStrength = v }
        @JvmName("setSheetInnerShadowPref") fun setInnerShadow(v: Float) { innerShadow = v }
        @JvmName("setSheetSaturationFactorPref") fun setSaturationFactor(v: Float) { saturationFactor = v }
        @JvmName("setSheetRimSoftnessPref") fun setRimSoftness(v: Float) { rimSoftness = v }
        @JvmName("setSheetBlurRadiusPref") fun setBlurRadius(v: Float) { blurRadius = v }
        @JvmName("setSheetCornerRadiusPref") fun setCornerRadius(v: Float) { cornerRadius = v }
        @JvmName("setSheetPillWidthPref") fun setPillWidth(v: Float) { pillWidth = v }
        @JvmName("setSheetPillHeightPref") fun setPillHeight(v: Float) { pillHeight = v }
        @JvmName("setSheetPillBottomMarginPref") fun setPillBottomMargin(v: Float) { pillBottomMargin = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            refractionHeight = defaults.refractionHeight
            sideRefraction = defaults.sideRefraction
            bottomRefraction = defaults.bottomRefraction
            bevelWidth = defaults.bevelWidth
            leftBevelWidth = defaults.leftBevelWidth
            rightBevelWidth = defaults.rightBevelWidth
            bottomBevelWidth = defaults.bottomBevelWidth
            dispersionStrength = defaults.dispersionStrength
            specularStrength = defaults.specularStrength
            hairlineStrength = defaults.hairlineStrength
            innerGlowStrength = defaults.innerGlowStrength
            innerShadow = defaults.innerShadow
            saturationFactor = defaults.saturationFactor
            rimSoftness = defaults.rimSoftness
            blurRadius = defaults.blurRadius
            cornerRadius = defaults.cornerRadius
            pillWidth = defaults.pillWidth
            pillHeight = defaults.pillHeight
            pillBottomMargin = defaults.pillBottomMargin
        }
    }

    // --- notification center sheet settings (Feature 7) ------------------------------------

    inner class NotificationSheetHolder {
        private val defaults = NotificationSheetSettings()

        var enabled by prefsBooleanState("notif_sheet_enabled", defaults.enabled); private set
        var lockWallpaperEnabled by prefsBooleanState("notif_sheet_lock_wallpaper_enabled", defaults.lockWallpaperEnabled); private set
        var customWallpaperEnabled by prefsBooleanState("notif_sheet_custom_wallpaper_enabled", defaults.customWallpaperEnabled); private set
        var lockWallpaperHomescreenOnly by prefsBooleanState("notif_sheet_lock_wallpaper_homescreen_only", defaults.lockWallpaperHomescreenOnly); private set
        var depthWallpaperEnabled by prefsBooleanState("notif_sheet_depth_wallpaper_enabled", defaults.depthWallpaperEnabled); private set
        var depthCustomSubject by prefsBooleanState("notif_sheet_depth_custom_subject", defaults.depthCustomSubject); private set
        var lockScreenAsNotifCenterEnabled by prefsBooleanState("notif_sheet_lockscreen_enabled", defaults.lockScreenAsNotifCenterEnabled); private set
        var showBottomWidgets by prefsBooleanState("notif_sheet_show_bottom_widgets", defaults.showBottomWidgets); private set
        var clockMorphOnNotifications by prefsBooleanState("notif_sheet_clock_morph", defaults.clockMorphOnNotifications); private set
        var glassEffectsEnabled by prefsBooleanState("notif_sheet_glass_effects_enabled", defaults.glassEffectsEnabled); private set
        var glassOnlyWithoutWallpaper by prefsBooleanState("notif_sheet_glass_only_without_wallpaper", defaults.glassOnlyWithoutWallpaper); private set
        var refractionHeight by prefsFloatState("notif_card_refraction_height", defaults.refractionHeight); private set
        var sideRefraction by prefsFloatState("notif_card_side_refraction", defaults.sideRefraction); private set
        var bottomRefraction by prefsFloatState("notif_card_bottom_refraction", defaults.bottomRefraction); private set
        var bevelWidth by prefsFloatState("notif_card_bevel_width", defaults.bevelWidth); private set
        var leftBevelWidth by prefsFloatState("notif_card_left_bevel", defaults.leftBevelWidth); private set
        var rightBevelWidth by prefsFloatState("notif_card_right_bevel", defaults.rightBevelWidth); private set
        var bottomBevelWidth by prefsFloatState("notif_card_bottom_bevel", defaults.bottomBevelWidth); private set
        var dispersionStrength by prefsFloatState("notif_card_dispersion", defaults.dispersionStrength); private set
        var specularStrength by prefsFloatState("notif_card_specular", defaults.specularStrength); private set
        var hairlineStrength by prefsFloatState("notif_card_hairline", defaults.hairlineStrength); private set
        var innerGlowStrength by prefsFloatState("notif_card_inner_glow", defaults.innerGlowStrength); private set
        var rimSoftness by prefsFloatState("notif_card_rim_softness", defaults.rimSoftness); private set
        var innerShadow by prefsFloatState("notif_card_inner_shadow", defaults.innerShadow); private set
        var saturationFactor by prefsFloatState("notif_card_saturation", defaults.saturationFactor); private set
        var blurRadius by prefsFloatState("notif_card_blur_radius", defaults.blurRadius); private set
        var cornerRadius by prefsFloatState("notif_card_corner_radius", defaults.cornerRadius); private set
        var sideMargin by prefsFloatState("notif_side_margin", defaults.sideMargin); private set

        fun snapshot(): NotificationSheetSettings = NotificationSheetSettings(
            enabled, lockWallpaperEnabled, customWallpaperEnabled, lockWallpaperHomescreenOnly,
            depthWallpaperEnabled, depthCustomSubject,
            lockScreenAsNotifCenterEnabled, showBottomWidgets, clockMorphOnNotifications,
            glassEffectsEnabled, glassOnlyWithoutWallpaper,
            refractionHeight, sideRefraction, bottomRefraction,
            bevelWidth, leftBevelWidth, rightBevelWidth, bottomBevelWidth,
            dispersionStrength, specularStrength, hairlineStrength, innerGlowStrength, rimSoftness,
            innerShadow, saturationFactor, blurRadius,
            cornerRadius, sideMargin
        )

        @JvmName("setNotifSheetEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setNotifSheetLockWallpaperEnabledPref") fun setLockWallpaperEnabled(v: Boolean) { lockWallpaperEnabled = v }
        @JvmName("setNotifSheetCustomWallpaperEnabledPref") fun setCustomWallpaperEnabled(v: Boolean) { customWallpaperEnabled = v }
        @JvmName("setNotifSheetLockWallpaperHomescreenOnlyPref") fun setLockWallpaperHomescreenOnly(v: Boolean) { lockWallpaperHomescreenOnly = v }
        @JvmName("setNotifSheetDepthWallpaperEnabledPref") fun setDepthWallpaperEnabled(v: Boolean) { depthWallpaperEnabled = v }
        @JvmName("setNotifSheetDepthCustomSubjectPref") fun setDepthCustomSubject(v: Boolean) { depthCustomSubject = v }
        @JvmName("setNotifSheetLockScreenAsNotifCenterEnabledPref") fun setLockScreenAsNotifCenterEnabled(v: Boolean) { lockScreenAsNotifCenterEnabled = v }
        @JvmName("setNotifSheetShowBottomWidgetsPref") fun setShowBottomWidgets(v: Boolean) { showBottomWidgets = v }
        @JvmName("setNotifSheetClockMorphOnNotificationsPref") fun setClockMorphOnNotifications(v: Boolean) { clockMorphOnNotifications = v }
        @JvmName("setNotifSheetGlassEffectsEnabledPref") fun setGlassEffectsEnabled(v: Boolean) { glassEffectsEnabled = v }
        @JvmName("setNotifSheetGlassOnlyWithoutWallpaperPref") fun setGlassOnlyWithoutWallpaper(v: Boolean) { glassOnlyWithoutWallpaper = v }
        @JvmName("setNotifSheetRefractionHeightPref") fun setRefractionHeight(v: Float) { refractionHeight = v }
        @JvmName("setNotifSheetSideRefractionPref") fun setSideRefraction(v: Float) { sideRefraction = v }
        @JvmName("setNotifSheetBottomRefractionPref") fun setBottomRefraction(v: Float) { bottomRefraction = v }
        @JvmName("setNotifSheetBevelWidthPref") fun setBevelWidth(v: Float) { bevelWidth = v }
        @JvmName("setNotifSheetLeftBevelWidthPref") fun setLeftBevelWidth(v: Float) { leftBevelWidth = v }
        @JvmName("setNotifSheetRightBevelWidthPref") fun setRightBevelWidth(v: Float) { rightBevelWidth = v }
        @JvmName("setNotifSheetBottomBevelWidthPref") fun setBottomBevelWidth(v: Float) { bottomBevelWidth = v }
        @JvmName("setNotifSheetDispersionStrengthPref") fun setDispersionStrength(v: Float) { dispersionStrength = v }
        @JvmName("setNotifSheetSpecularStrengthPref") fun setSpecularStrength(v: Float) { specularStrength = v }
        @JvmName("setNotifSheetHairlineStrengthPref") fun setHairlineStrength(v: Float) { hairlineStrength = v }
        @JvmName("setNotifSheetInnerGlowStrengthPref") fun setInnerGlowStrength(v: Float) { innerGlowStrength = v }
        @JvmName("setNotifSheetRimSoftnessPref") fun setRimSoftness(v: Float) { rimSoftness = v }
        @JvmName("setNotifSheetInnerShadowPref") fun setInnerShadow(v: Float) { innerShadow = v }
        @JvmName("setNotifSheetSaturationFactorPref") fun setSaturationFactor(v: Float) { saturationFactor = v }
        @JvmName("setNotifSheetBlurRadiusPref") fun setBlurRadius(v: Float) { blurRadius = v }
        @JvmName("setNotifSheetCornerRadiusPref") fun setCornerRadius(v: Float) { cornerRadius = v }
        @JvmName("setNotifSheetSideMarginPref") fun setSideMargin(v: Float) { sideMargin = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            lockWallpaperEnabled = defaults.lockWallpaperEnabled
            customWallpaperEnabled = defaults.customWallpaperEnabled
            lockWallpaperHomescreenOnly = defaults.lockWallpaperHomescreenOnly
            depthWallpaperEnabled = defaults.depthWallpaperEnabled
            depthCustomSubject = defaults.depthCustomSubject
            lockScreenAsNotifCenterEnabled = defaults.lockScreenAsNotifCenterEnabled
            showBottomWidgets = defaults.showBottomWidgets
            clockMorphOnNotifications = defaults.clockMorphOnNotifications
            glassEffectsEnabled = defaults.glassEffectsEnabled
            glassOnlyWithoutWallpaper = defaults.glassOnlyWithoutWallpaper
            refractionHeight = defaults.refractionHeight
            sideRefraction = defaults.sideRefraction
            bottomRefraction = defaults.bottomRefraction
            bevelWidth = defaults.bevelWidth
            leftBevelWidth = defaults.leftBevelWidth
            rightBevelWidth = defaults.rightBevelWidth
            bottomBevelWidth = defaults.bottomBevelWidth
            dispersionStrength = defaults.dispersionStrength
            specularStrength = defaults.specularStrength
            hairlineStrength = defaults.hairlineStrength
            innerGlowStrength = defaults.innerGlowStrength
            rimSoftness = defaults.rimSoftness
            innerShadow = defaults.innerShadow
            saturationFactor = defaults.saturationFactor
            blurRadius = defaults.blurRadius
            cornerRadius = defaults.cornerRadius
            sideMargin = defaults.sideMargin
        }
    }

    // --- lock screen keypad physics settings (Feature 2) ----------------------------------

    inner class LockScreenKeypadHolder {
        private val defaults = LockScreenKeypadSettings()

        var enabled by prefsBooleanState("engine_keypad_enabled", defaults.enabled); private set
        var buttonRefraction by prefsFloatState("pin_btn_refraction", defaults.buttonRefraction); private set
        var buttonBevel by prefsFloatState("pin_btn_bevel", defaults.buttonBevel); private set
        var buttonDispersion by prefsFloatState("pin_btn_dispersion", defaults.buttonDispersion); private set
        var buttonSpecular by prefsFloatState("pin_btn_specular", defaults.buttonSpecular); private set
        var buttonInnerShadow by prefsFloatState("pin_btn_inner_shadow", defaults.buttonInnerShadow); private set
        var buttonBlur by prefsFloatState("pin_btn_blur", defaults.buttonBlur); private set
        var buttonSaturation by prefsFloatState("pin_btn_saturation", defaults.buttonSaturation); private set
        var circleDiameter by prefsFloatState("pin_circle_diameter", defaults.circleDiameter); private set
        var digitTextSize by prefsFloatState("pin_digit_text_size", defaults.digitTextSize); private set
        var showKlondike by prefsBooleanState("pin_show_klondike", defaults.showKlondike); private set
        var pressLiquidScale by prefsFloatState("pin_press_liquid_scale", defaults.pressLiquidScale); private set
        var pressLiquidAmp by prefsFloatState("pin_press_liquid_amp", defaults.pressLiquidAmp); private set

        var row0Offset by prefsFloatState("pin_row0_offset", defaults.row0Offset); private set
        var row1Offset by prefsFloatState("pin_row1_offset", defaults.row1Offset); private set
        var row2Offset by prefsFloatState("pin_row2_offset", defaults.row2Offset); private set
        var row3Offset by prefsFloatState("pin_row3_offset", defaults.row3Offset); private set

        var entryTranslationY by prefsFloatState("pin_entry_translation_y", defaults.entryTranslationY); private set
        var entryInitialScale by prefsFloatState("pin_entry_initial_scale", defaults.entryInitialScale); private set
        var entryDuration by prefsFloatState("pin_entry_duration", defaults.entryDuration); private set
        var rowDelayStep by prefsFloatState("pin_row_delay_step", defaults.rowDelayStep); private set
        var colDelayStep by prefsFloatState("pin_col_delay_step", defaults.colDelayStep); private set
        var entryOvershoot by prefsFloatState("pin_entry_overshoot", defaults.entryOvershoot); private set

        var delayKey1 by prefsFloatState("pin_delay_key1", defaults.delayKey1); private set
        var delayKey2 by prefsFloatState("pin_delay_key2", defaults.delayKey2); private set
        var delayKey3 by prefsFloatState("pin_delay_key3", defaults.delayKey3); private set
        var delayKey4 by prefsFloatState("pin_delay_key4", defaults.delayKey4); private set
        var delayKey5 by prefsFloatState("pin_delay_key5", defaults.delayKey5); private set
        var delayKey6 by prefsFloatState("pin_delay_key6", defaults.delayKey6); private set
        var delayKey7 by prefsFloatState("pin_delay_key7", defaults.delayKey7); private set
        var delayKey8 by prefsFloatState("pin_delay_key8", defaults.delayKey8); private set
        var delayKey9 by prefsFloatState("pin_delay_key9", defaults.delayKey9); private set
        var delayKey0 by prefsFloatState("pin_delay_key0", defaults.delayKey0); private set
        var delayEmergency by prefsFloatState("pin_delay_emergency", defaults.delayEmergency); private set
        var delayCancel by prefsFloatState("pin_delay_cancel", defaults.delayCancel); private set

        fun snapshot(): LockScreenKeypadSettings = LockScreenKeypadSettings(
            enabled, buttonRefraction, buttonBevel, buttonDispersion, buttonSpecular, buttonInnerShadow,
            buttonBlur, buttonSaturation, circleDiameter, digitTextSize, showKlondike, pressLiquidScale,
            pressLiquidAmp, row0Offset, row1Offset, row2Offset, row3Offset, entryTranslationY,
            entryInitialScale, entryDuration, rowDelayStep, colDelayStep, entryOvershoot, delayKey1,
            delayKey2, delayKey3, delayKey4, delayKey5, delayKey6, delayKey7, delayKey8, delayKey9,
            delayKey0, delayEmergency, delayCancel
        )

        @JvmName("setKeypadEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setButtonRefractionPref") fun setButtonRefraction(v: Float) { buttonRefraction = v }
        @JvmName("setButtonBevelPref") fun setButtonBevel(v: Float) { buttonBevel = v }
        @JvmName("setButtonDispersionPref") fun setButtonDispersion(v: Float) { buttonDispersion = v }
        @JvmName("setButtonSpecularPref") fun setButtonSpecular(v: Float) { buttonSpecular = v }
        @JvmName("setButtonInnerShadowPref") fun setButtonInnerShadow(v: Float) { buttonInnerShadow = v }
        @JvmName("setButtonBlurPref") fun setButtonBlur(v: Float) { buttonBlur = v }
        @JvmName("setButtonSaturationPref") fun setButtonSaturation(v: Float) { buttonSaturation = v }
        @JvmName("setCircleDiameterPref") fun setCircleDiameter(v: Float) { circleDiameter = v }
        @JvmName("setDigitTextSizePref") fun setDigitTextSize(v: Float) { digitTextSize = v }
        @JvmName("setShowKlondikePref") fun setShowKlondike(v: Boolean) { showKlondike = v }
        @JvmName("setPressLiquidScalePref") fun setPressLiquidScale(v: Float) { pressLiquidScale = v }
        @JvmName("setPressLiquidAmpPref") fun setPressLiquidAmp(v: Float) { pressLiquidAmp = v }
        @JvmName("setRow0OffsetPref") fun setRow0Offset(v: Float) { row0Offset = v }
        @JvmName("setRow1OffsetPref") fun setRow1Offset(v: Float) { row1Offset = v }
        @JvmName("setRow2OffsetPref") fun setRow2Offset(v: Float) { row2Offset = v }
        @JvmName("setRow3OffsetPref") fun setRow3Offset(v: Float) { row3Offset = v }
        @JvmName("setEntryTranslationYPref") fun setEntryTranslationY(v: Float) { entryTranslationY = v }
        @JvmName("setEntryInitialScalePref") fun setEntryInitialScale(v: Float) { entryInitialScale = v }
        @JvmName("setEntryDurationPref") fun setEntryDuration(v: Float) { entryDuration = v }
        @JvmName("setRowDelayStepPref") fun setRowDelayStep(v: Float) { rowDelayStep = v }
        @JvmName("setColDelayStepPref") fun setColDelayStep(v: Float) { colDelayStep = v }
        @JvmName("setEntryOvershootPref") fun setEntryOvershoot(v: Float) { entryOvershoot = v }

        @JvmName("setDelayKey1Pref") fun setDelayKey1(v: Float) { delayKey1 = v }
        @JvmName("setDelayKey2Pref") fun setDelayKey2(v: Float) { delayKey2 = v }
        @JvmName("setDelayKey3Pref") fun setDelayKey3(v: Float) { delayKey3 = v }
        @JvmName("setDelayKey4Pref") fun setDelayKey4(v: Float) { delayKey4 = v }
        @JvmName("setDelayKey5Pref") fun setDelayKey5(v: Float) { delayKey5 = v }
        @JvmName("setDelayKey6Pref") fun setDelayKey6(v: Float) { delayKey6 = v }
        @JvmName("setDelayKey7Pref") fun setDelayKey7(v: Float) { delayKey7 = v }
        @JvmName("setDelayKey8Pref") fun setDelayKey8(v: Float) { delayKey8 = v }
        @JvmName("setDelayKey9Pref") fun setDelayKey9(v: Float) { delayKey9 = v }
        @JvmName("setDelayKey0Pref") fun setDelayKey0(v: Float) { delayKey0 = v }
        @JvmName("setDelayEmergencyPref") fun setDelayEmergency(v: Float) { delayEmergency = v }
        @JvmName("setDelayCancelPref") fun setDelayCancel(v: Float) { delayCancel = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            buttonRefraction = defaults.buttonRefraction
            buttonBevel = defaults.buttonBevel
            buttonDispersion = defaults.buttonDispersion
            buttonSpecular = defaults.buttonSpecular
            buttonInnerShadow = defaults.buttonInnerShadow
            buttonBlur = defaults.buttonBlur
            buttonSaturation = defaults.buttonSaturation
            circleDiameter = defaults.circleDiameter
            digitTextSize = defaults.digitTextSize
            showKlondike = defaults.showKlondike
            pressLiquidScale = defaults.pressLiquidScale
            pressLiquidAmp = defaults.pressLiquidAmp
            row0Offset = defaults.row0Offset
            row1Offset = defaults.row1Offset
            row2Offset = defaults.row2Offset
            row3Offset = defaults.row3Offset
            entryTranslationY = defaults.entryTranslationY
            entryInitialScale = defaults.entryInitialScale
            entryDuration = defaults.entryDuration
            rowDelayStep = defaults.rowDelayStep
            colDelayStep = defaults.colDelayStep
            entryOvershoot = defaults.entryOvershoot
            delayKey1 = defaults.delayKey1
            delayKey2 = defaults.delayKey2
            delayKey3 = defaults.delayKey3
            delayKey4 = defaults.delayKey4
            delayKey5 = defaults.delayKey5
            delayKey6 = defaults.delayKey6
            delayKey7 = defaults.delayKey7
            delayKey8 = defaults.delayKey8
            delayKey9 = defaults.delayKey9
            delayKey0 = defaults.delayKey0
            delayEmergency = defaults.delayEmergency
            delayCancel = defaults.delayCancel
        }
    }

    // --- screen unlock kinetics settings (Feature 3) --------------------------------------

    inner class ScreenUnlockHolder {
        private val defaults = ScreenUnlockSettings()

        var enabled by prefsBooleanState("unlock_enabled", defaults.enabled); private set
        var animMode by prefsIntState("unlock_anim_mode", defaults.animMode); private set
        var gridColumns by prefsIntState("unlock_grid_columns", defaults.gridColumns); private set
        var gridRows by prefsIntState("unlock_grid_rows", defaults.gridRows); private set

        var dockDamping by prefsFloatState("unlock_dock_damping", defaults.dockDamping); private set
        var dockResponse by prefsFloatState("unlock_dock_response", defaults.dockResponse); private set
        var centerResponse by prefsFloatState("unlock_center_response", defaults.centerResponse); private set
        var centerDamping by prefsFloatState("unlock_center_damping", defaults.centerDamping); private set
        var edgeResponseDelay by prefsFloatState("unlock_edge_response_delay", defaults.edgeResponseDelay); private set
        var edgeDampingDrop by prefsFloatState("unlock_edge_damping_drop", defaults.edgeDampingDrop); private set
        var zDepthMultiplier by prefsFloatState("unlock_z_depth_multiplier", defaults.zDepthMultiplier); private set
        var minResponse by prefsFloatState("unlock_min_response", defaults.minResponse); private set
        var maxResponse by prefsFloatState("unlock_max_response", defaults.maxResponse); private set
        var minDamping by prefsFloatState("unlock_min_damping", defaults.minDamping); private set
        var maxDamping by prefsFloatState("unlock_max_damping", defaults.maxDamping); private set

        var durationScale by prefsFloatState("unlock_duration_scale", defaults.durationScale); private set
        var velocityInfluence by prefsFloatState("unlock_velocity_influence", defaults.velocityInfluence); private set
        var maxVelocity by prefsFloatState("unlock_max_velocity", defaults.maxVelocity); private set
        var cascadeDamping by prefsFloatState("unlock_cascade_damping", defaults.cascadeDamping); private set
        var kineticDockDamping by prefsFloatState("unlock_kinetic_dock_damping", defaults.kineticDockDamping); private set
        var kineticDockResponse by prefsFloatState("unlock_kinetic_dock_response", defaults.kineticDockResponse); private set
        var kineticDockZDepth by prefsFloatState("unlock_kinetic_dock_z_depth", defaults.kineticDockZDepth); private set

        private val rowZStates = mutableStateMapOf<Int, Float>()
        private val rowCenterBounceStates = mutableStateMapOf<Int, Float>()
        private val rowOuterBounceStates = mutableStateMapOf<Int, Float>()
        private val rowCenterSettleStates = mutableStateMapOf<Int, Float>()
        private val rowOuterSettleStates = mutableStateMapOf<Int, Float>()

        fun getRowZ(row: Int): Float {
            return rowZStates.getOrPut(row) {
                val defZ = ScreenUnlockDefaults.rowZ(row, gridRows)
                prefs.getFloat("unlock_r${row}_z", defZ)
            }
        }
        fun setRowZ(row: Int, v: Float) {
            rowZStates[row] = v
            val key = "unlock_r${row}_z"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
            SettingsRepository.sendSettingsChangedBroadcast(key, v)
        }

        fun getRowCenterBounce(row: Int): Float {
            return rowCenterBounceStates.getOrPut(row) {
                val defBounce = ScreenUnlockDefaults.rowBounce(row, gridRows, false)
                prefs.getFloat("unlock_r${row}_center_bounce", defBounce)
            }
        }
        fun setRowCenterBounce(row: Int, v: Float) {
            rowCenterBounceStates[row] = v
            val key = "unlock_r${row}_center_bounce"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
            SettingsRepository.sendSettingsChangedBroadcast(key, v)
        }

        fun getRowOuterBounce(row: Int): Float {
            return rowOuterBounceStates.getOrPut(row) {
                val defBounce = ScreenUnlockDefaults.rowBounce(row, gridRows, true)
                prefs.getFloat("unlock_r${row}_outer_bounce", defBounce)
            }
        }
        fun setRowOuterBounce(row: Int, v: Float) {
            rowOuterBounceStates[row] = v
            val key = "unlock_r${row}_outer_bounce"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
            SettingsRepository.sendSettingsChangedBroadcast(key, v)
        }

        fun getRowCenterSettle(row: Int): Float {
            return rowCenterSettleStates.getOrPut(row) {
                val defSettle = ScreenUnlockDefaults.rowSettle(row, gridRows, false)
                prefs.getFloat("unlock_r${row}_center_settle", defSettle)
            }
        }
        fun setRowCenterSettle(row: Int, v: Float) {
            rowCenterSettleStates[row] = v
            val key = "unlock_r${row}_center_settle"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
            SettingsRepository.sendSettingsChangedBroadcast(key, v)
        }

        fun getRowOuterSettle(row: Int): Float {
            return rowOuterSettleStates.getOrPut(row) {
                val defSettle = ScreenUnlockDefaults.rowSettle(row, gridRows, true)
                prefs.getFloat("unlock_r${row}_outer_settle", defSettle)
            }
        }
        fun setRowOuterSettle(row: Int, v: Float) {
            rowOuterSettleStates[row] = v
            val key = "unlock_r${row}_outer_settle"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
            SettingsRepository.sendSettingsChangedBroadcast(key, v)
        }

        fun snapshot(): ScreenUnlockSettings = ScreenUnlockSettings(
            enabled, animMode, gridColumns, gridRows, dockDamping, dockResponse,
            centerResponse, centerDamping, edgeResponseDelay, edgeDampingDrop, zDepthMultiplier,
            minResponse, maxResponse, minDamping, maxDamping, durationScale, velocityInfluence,
            maxVelocity, cascadeDamping, kineticDockDamping, kineticDockResponse, kineticDockZDepth
        )

        @JvmName("setUnlockEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setUnlockAnimModePref") fun setAnimMode(v: Int) { animMode = v }
        @JvmName("setUnlockGridColumnsPref") fun setGridColumns(v: Int) { gridColumns = v }
        @JvmName("setUnlockGridRowsPref") fun setGridRows(v: Int) { gridRows = v }
        @JvmName("setUnlockDockDampingPref") fun setDockDamping(v: Float) { dockDamping = v }
        @JvmName("setUnlockDockResponsePref") fun setDockResponse(v: Float) { dockResponse = v }
        @JvmName("setUnlockCenterResponsePref") fun setCenterResponse(v: Float) { centerResponse = v }
        @JvmName("setUnlockCenterDampingPref") fun setCenterDamping(v: Float) { centerDamping = v }
        @JvmName("setUnlockEdgeResponseDelayPref") fun setEdgeResponseDelay(v: Float) { edgeResponseDelay = v }
        @JvmName("setUnlockEdgeDampingDropPref") fun setEdgeDampingDrop(v: Float) { edgeDampingDrop = v }
        @JvmName("setUnlockZDepthMultiplierPref") fun setZDepthMultiplier(v: Float) { zDepthMultiplier = v }
        @JvmName("setUnlockMinResponsePref") fun setMinResponse(v: Float) { minResponse = v }
        @JvmName("setUnlockMaxResponsePref") fun setMaxResponse(v: Float) { maxResponse = v }
        @JvmName("setUnlockMinDampingPref") fun setMinDamping(v: Float) { minDamping = v }
        @JvmName("setUnlockMaxDampingPref") fun setMaxDamping(v: Float) { maxDamping = v }
        @JvmName("setUnlockDurationScalePref") fun setDurationScale(v: Float) { durationScale = v }
        @JvmName("setUnlockVelocityInfluencePref") fun setVelocityInfluence(v: Float) { velocityInfluence = v }
        @JvmName("setUnlockMaxVelocityPref") fun setMaxVelocity(v: Float) { maxVelocity = v }
        @JvmName("setUnlockCascadeDampingPref") fun setCascadeDamping(v: Float) { cascadeDamping = v }
        @JvmName("setUnlockKineticDockDampingPref") fun setKineticDockDamping(v: Float) { kineticDockDamping = v }
        @JvmName("setUnlockKineticDockResponsePref") fun setKineticDockResponse(v: Float) { kineticDockResponse = v }
        @JvmName("setUnlockKineticDockZDepthPref") fun setKineticDockZDepth(v: Float) { kineticDockZDepth = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            animMode = defaults.animMode
            gridColumns = defaults.gridColumns
            gridRows = defaults.gridRows
            dockDamping = defaults.dockDamping
            dockResponse = defaults.dockResponse
            centerResponse = defaults.centerResponse
            centerDamping = defaults.centerDamping
            edgeResponseDelay = defaults.edgeResponseDelay
            edgeDampingDrop = defaults.edgeDampingDrop
            zDepthMultiplier = defaults.zDepthMultiplier
            minResponse = defaults.minResponse
            maxResponse = defaults.maxResponse
            minDamping = defaults.minDamping
            maxDamping = defaults.maxDamping
            durationScale = defaults.durationScale
            velocityInfluence = defaults.velocityInfluence
            maxVelocity = defaults.maxVelocity
            cascadeDamping = defaults.cascadeDamping
            kineticDockDamping = defaults.kineticDockDamping
            kineticDockResponse = defaults.kineticDockResponse
            kineticDockZDepth = defaults.kineticDockZDepth

            rowZStates.clear()
            rowCenterBounceStates.clear()
            rowOuterBounceStates.clear()
            rowCenterSettleStates.clear()
            rowOuterSettleStates.clear()

            val editor = prefs.edit()
            val remoteEditor = XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()
            for (i in 0..32) {
                editor.remove("unlock_r${i}_z")
                editor.remove("unlock_r${i}_center_bounce")
                editor.remove("unlock_r${i}_outer_bounce")
                editor.remove("unlock_r${i}_center_settle")
                editor.remove("unlock_r${i}_outer_settle")
                remoteEditor?.remove("unlock_r${i}_z")
                remoteEditor?.remove("unlock_r${i}_center_bounce")
                remoteEditor?.remove("unlock_r${i}_outer_bounce")
                remoteEditor?.remove("unlock_r${i}_center_settle")
                remoteEditor?.remove("unlock_r${i}_outer_settle")
            }
            editor.apply()
            remoteEditor?.apply()
            SettingsRepository.sendSettingsChangedBroadcast("unlock_reload", true)
        }
    }

    // --- iOS App Transitions & Weightless Physics (Feature 4) --------------------------------

    inner class AppTransitionHolder {
        private val defaults = IOSTransitionSettings()

        var enabled by prefsBooleanState("ios_anim_enabled", defaults.enabled); private set
        var arcApexLift by prefsFloatState("ios_anim_arc_apex_lift", defaults.arcApexLift); private set
        var openUpliftSurge by prefsBooleanState("ios_anim_open_uplift_surge", defaults.openUpliftSurge); private set
        var keystoneSqueeze by prefsBooleanState("ios_anim_keystone_squeeze", defaults.keystoneSqueeze); private set
        var animSpeed by prefsFloatState("ios_anim_speed", defaults.animSpeed); private set
        var openSpringDamping by prefsFloatState("ios_anim_open_spring_damping", defaults.openSpringDamping); private set
        var closeSpringDamping by prefsFloatState("ios_anim_close_spring_damping", defaults.closeSpringDamping); private set
        var folderTracking by prefsBooleanState("ios_anim_folder_tracking", defaults.folderTracking); private set
        var recentsCentering by prefsBooleanState("ios_anim_recents_centering", defaults.recentsCentering); private set
        var zeroFrictionDrag by prefsBooleanState("ios_anim_zero_friction_drag", defaults.zeroFrictionDrag); private set
        var landscapeGameOpt by prefsBooleanState("ios_anim_landscape_game_opt", defaults.landscapeGameOpt); private set
        var refinedHaptics by prefsBooleanState("ios_anim_refined_haptics", defaults.refinedHaptics); private set
        var stackRecentsHaptics by prefsBooleanState("ios_anim_stack_recents_haptics", defaults.stackRecentsHaptics); private set

        fun snapshot(): IOSTransitionSettings = IOSTransitionSettings(
            enabled = enabled,
            arcApexLift = arcApexLift,
            openUpliftSurge = openUpliftSurge,
            keystoneSqueeze = keystoneSqueeze,
            animSpeed = animSpeed,
            openSpringDamping = openSpringDamping,
            closeSpringDamping = closeSpringDamping,
            folderTracking = folderTracking,
            recentsCentering = recentsCentering,
            zeroFrictionDrag = zeroFrictionDrag,
            landscapeGameOpt = landscapeGameOpt,
            refinedHaptics = refinedHaptics,
            stackRecentsHaptics = stackRecentsHaptics
        )

        @JvmName("setIOSTransitionsEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setArcApexLiftPref") fun setArcApexLift(v: Float) { arcApexLift = v }
        @JvmName("setOpenUpliftSurgePref") fun setOpenUpliftSurge(v: Boolean) { openUpliftSurge = v }
        @JvmName("setKeystoneSqueezePref") fun setKeystoneSqueeze(v: Boolean) { keystoneSqueeze = v }
        @JvmName("setAnimSpeedPref") fun setAnimSpeed(v: Float) { animSpeed = v }
        @JvmName("setOpenSpringDampingPref") fun setOpenSpringDamping(v: Float) { openSpringDamping = v }
        @JvmName("setCloseSpringDampingPref") fun setCloseSpringDamping(v: Float) { closeSpringDamping = v }
        @JvmName("setFolderTrackingPref") fun setFolderTracking(v: Boolean) { folderTracking = v }
        @JvmName("setRecentsCenteringPref") fun setRecentsCentering(v: Boolean) { recentsCentering = v }
        @JvmName("setZeroFrictionDragPref") fun setZeroFrictionDrag(v: Boolean) { zeroFrictionDrag = v }
        @JvmName("setLandscapeGameOptPref") fun setLandscapeGameOpt(v: Boolean) { landscapeGameOpt = v }
        @JvmName("setRefinedHapticsPref") fun setRefinedHaptics(v: Boolean) { refinedHaptics = v }
        @JvmName("setStackRecentsHapticsPref") fun setStackRecentsHaptics(v: Boolean) { stackRecentsHaptics = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            arcApexLift = defaults.arcApexLift
            openUpliftSurge = defaults.openUpliftSurge
            keystoneSqueeze = defaults.keystoneSqueeze
            animSpeed = defaults.animSpeed
            openSpringDamping = defaults.openSpringDamping
            closeSpringDamping = defaults.closeSpringDamping
            folderTracking = defaults.folderTracking
            recentsCentering = defaults.recentsCentering
            zeroFrictionDrag = defaults.zeroFrictionDrag
            landscapeGameOpt = defaults.landscapeGameOpt
            refinedHaptics = defaults.refinedHaptics
            stackRecentsHaptics = defaults.stackRecentsHaptics
        }
    }

    // --- Control Center & Jelly Physics (Features 5, 6, 7, 8, 9) -----------------------

    inner class ControlCenterHolder {
        private val defaults = ControlCenterSettings()

        var modularEditEnabled by prefsBooleanState("cc_modular_edit_enabled", defaults.modularEditEnabled); private set
        var restartOnEditExit by prefsBooleanState("cc_restart_on_edit_exit", defaults.restartOnEditExit); private set
        var animEnabled by prefsBooleanState("cc_anim_enabled", defaults.animEnabled); private set
        var jellyEnabled by prefsBooleanState("cc_jelly_enabled", defaults.jellyEnabled); private set
        var lowEndAnim by prefsBooleanState("cc_low_end_anim", defaults.lowEndAnim); private set
        var entryInitialScale by prefsFloatState("cc_entry_initial_scale", defaults.entryInitialScale); private set
        var entryTranslationY by prefsFloatState("cc_entry_translation_y", defaults.entryTranslationY); private set
        var waterfallEnabled by prefsBooleanState("cc_waterfall_enabled", defaults.waterfallEnabled); private set
        var waterfallDelayStep by prefsFloatState("cc_waterfall_delay_step", defaults.waterfallDelayStep); private set
        var spreadRatio by prefsFloatState("cc_spread_ratio", defaults.spreadRatio); private set
        var spreadResponse by prefsFloatState("cc_spread_response", defaults.spreadResponse); private set
        var spreadDamping by prefsFloatState("cc_spread_damping", defaults.spreadDamping); private set
        var spreadReleaseResponse by prefsFloatState("cc_spread_release_response", defaults.spreadReleaseResponse); private set
        var spreadReleaseDamping by prefsFloatState("cc_spread_release_damping", defaults.spreadReleaseDamping); private set
        var tileShowResponse by prefsFloatState("cc_tile_show_response", defaults.tileShowResponse); private set
        var tileShowDamping by prefsFloatState("cc_tile_show_damping", defaults.tileShowDamping); private set
        var tileHideResponse by prefsFloatState("cc_tile_hide_response", defaults.tileHideResponse); private set
        var tileHideDamping by prefsFloatState("cc_tile_hide_damping", defaults.tileHideDamping); private set
        var tileShrinkResponse by prefsFloatState("cc_tile_shrink_response", defaults.tileShrinkResponse); private set
        var tileShrinkDamping by prefsFloatState("cc_tile_shrink_damping", defaults.tileShrinkDamping); private set
        var tileDelayScale by prefsFloatState("cc_tile_delay_scale", defaults.tileDelayScale); private set
        var blurResponse by prefsFloatState("cc_blur_response", defaults.blurResponse); private set
        var blurDamping by prefsFloatState("cc_blur_damping", defaults.blurDamping); private set
        var frictionMultiplier by prefsFloatState("cc_friction_multiplier", defaults.frictionMultiplier); private set

        fun snapshot(): ControlCenterSettings = ControlCenterSettings(
            modularEditEnabled, restartOnEditExit, animEnabled, jellyEnabled, lowEndAnim,
            entryInitialScale, entryTranslationY, waterfallEnabled, waterfallDelayStep,
            spreadRatio, spreadResponse, spreadDamping, spreadReleaseResponse, spreadReleaseDamping,
            tileShowResponse, tileShowDamping, tileHideResponse, tileHideDamping,
            tileShrinkResponse, tileShrinkDamping, tileDelayScale, blurResponse, blurDamping, frictionMultiplier
        )

        @JvmName("setCcModularEditEnabledPref") fun setModularEditEnabled(v: Boolean) { modularEditEnabled = v }
        @JvmName("setCcRestartOnEditExitPref") fun setRestartOnEditExit(v: Boolean) { restartOnEditExit = v }
        @JvmName("setCcAnimEnabledPref") fun setAnimEnabled(v: Boolean) { animEnabled = v }
        @JvmName("setCcJellyEnabledPref") fun setJellyEnabled(v: Boolean) { jellyEnabled = v }
        @JvmName("setCcLowEndAnimPref") fun setLowEndAnim(v: Boolean) { lowEndAnim = v }
        @JvmName("setCcEntryInitialScalePref") fun setEntryInitialScale(v: Float) { entryInitialScale = v }
        @JvmName("setCcEntryTranslationYPref") fun setEntryTranslationY(v: Float) { entryTranslationY = v }
        @JvmName("setCcWaterfallEnabledPref") fun setWaterfallEnabled(v: Boolean) { waterfallEnabled = v }
        @JvmName("setCcWaterfallDelayStepPref") fun setWaterfallDelayStep(v: Float) { waterfallDelayStep = v }
        @JvmName("setCcSpreadRatioPref") fun setSpreadRatio(v: Float) { spreadRatio = v }
        @JvmName("setCcSpreadResponsePref") fun setSpreadResponse(v: Float) { spreadResponse = v }
        @JvmName("setCcSpreadDampingPref") fun setSpreadDamping(v: Float) { spreadDamping = v }
        @JvmName("setCcSpreadReleaseResponsePref") fun setSpreadReleaseResponse(v: Float) { spreadReleaseResponse = v }
        @JvmName("setCcSpreadReleaseDampingPref") fun setSpreadReleaseDamping(v: Float) { spreadReleaseDamping = v }
        @JvmName("setCcTileShowResponsePref") fun setTileShowResponse(v: Float) { tileShowResponse = v }
        @JvmName("setCcTileShowDampingPref") fun setTileShowDamping(v: Float) { tileShowDamping = v }
        @JvmName("setCcTileHideResponsePref") fun setTileHideResponse(v: Float) { tileHideResponse = v }
        @JvmName("setCcTileHideDampingPref") fun setTileHideDamping(v: Float) { tileHideDamping = v }
        @JvmName("setCcTileShrinkResponsePref") fun setTileShrinkResponse(v: Float) { tileShrinkResponse = v }
        @JvmName("setCcTileShrinkDampingPref") fun setTileShrinkDamping(v: Float) { tileShrinkDamping = v }
        @JvmName("setCcTileDelayScalePref") fun setTileDelayScale(v: Float) { tileDelayScale = v }
        @JvmName("setCcBlurResponsePref") fun setBlurResponse(v: Float) { blurResponse = v }
        @JvmName("setCcBlurDampingPref") fun setBlurDamping(v: Float) { blurDamping = v }
        @JvmName("setCcFrictionMultiplierPref") fun setFrictionMultiplier(v: Float) { frictionMultiplier = v }

        fun reloadFromDefaults() {
            modularEditEnabled = defaults.modularEditEnabled
            restartOnEditExit = defaults.restartOnEditExit
            animEnabled = defaults.animEnabled
            jellyEnabled = defaults.jellyEnabled
            lowEndAnim = defaults.lowEndAnim
            entryInitialScale = defaults.entryInitialScale
            entryTranslationY = defaults.entryTranslationY
            waterfallEnabled = defaults.waterfallEnabled
            waterfallDelayStep = defaults.waterfallDelayStep
            spreadRatio = defaults.spreadRatio
            spreadResponse = defaults.spreadResponse
            spreadDamping = defaults.spreadDamping
            spreadReleaseResponse = defaults.spreadReleaseResponse
            spreadReleaseDamping = defaults.spreadReleaseDamping
            tileShowResponse = defaults.tileShowResponse
            tileShowDamping = defaults.tileShowDamping
            tileHideResponse = defaults.tileHideResponse
            tileHideDamping = defaults.tileHideDamping
            tileShrinkResponse = defaults.tileShrinkResponse
            tileShrinkDamping = defaults.tileShrinkDamping
            tileDelayScale = defaults.tileDelayScale
            blurResponse = defaults.blurResponse
            blurDamping = defaults.blurDamping
            frictionMultiplier = defaults.frictionMultiplier
        }
    }

    // --- Feature 10: Morph Physics Settings ----------------------------------------------

    inner class MorphPhysicsHolder {
        private val defaults = MorphPhysicsSettings()

        var morphSpeedMultiplier by prefsFloatState("morph_speed_multiplier", defaults.morphSpeedMultiplier)
            private set
        var circleCollapseRatio by prefsFloatState("morph_circle_collapse_ratio", defaults.circleCollapseRatio)
            private set
        var circleDiameterDp by prefsFloatState("morph_circle_diameter_dp", defaults.circleDiameterDp)
            private set
        var zPlungeDepth by prefsFloatState("morph_z_plunge_depth", defaults.zPlungeDepth)
            private set
        var gravitySwoopDp by prefsFloatState("morph_gravity_swoop_dp", defaults.gravitySwoopDp)
            private set
        var yBounceAmountDp by prefsFloatState("morph_y_bounce_amount_dp", defaults.yBounceAmountDp)
            private set
        var jellyOvershoot by prefsFloatState("morph_jelly_overshoot", defaults.jellyOvershoot)
            private set
        var contentStretch by prefsFloatState("morph_content_stretch", defaults.contentStretch)
            private set
        var springDamping by prefsFloatState("morph_spring_damping", defaults.springDamping)
            private set
        var springStiffness by prefsFloatState("morph_spring_stiffness", defaults.springStiffness)
            private set
        var lensSurgeDp by prefsFloatState("morph_lens_surge_dp", defaults.lensSurgeDp)
            private set
        var motionBlurSurgeDp by prefsFloatState("morph_motion_blur_surge_dp", defaults.motionBlurSurgeDp)
            private set

        fun snapshot(): MorphPhysicsSettings = MorphPhysicsSettings(
            morphSpeedMultiplier, circleCollapseRatio, circleDiameterDp, zPlungeDepth,
            gravitySwoopDp, yBounceAmountDp, jellyOvershoot, contentStretch,
            springDamping, springStiffness, lensSurgeDp, motionBlurSurgeDp
        )

        @JvmName("setMorphSpeedMultiplierPref") fun setMorphSpeedMultiplier(v: Float) { morphSpeedMultiplier = v }
        @JvmName("setCircleCollapseRatioPref") fun setCircleCollapseRatio(v: Float) { circleCollapseRatio = v }
        @JvmName("setCircleDiameterDpPref") fun setCircleDiameterDp(v: Float) { circleDiameterDp = v }
        @JvmName("setZPlungeDepthPref") fun setZPlungeDepth(v: Float) { zPlungeDepth = v }
        @JvmName("setGravitySwoopDpPref") fun setGravitySwoopDp(v: Float) { gravitySwoopDp = v }
        @JvmName("setYBounceAmountDpPref") fun setYBounceAmountDp(v: Float) { yBounceAmountDp = v }
        @JvmName("setJellyOvershootPref") fun setJellyOvershoot(v: Float) { jellyOvershoot = v }
        @JvmName("setContentStretchPref") fun setContentStretch(v: Float) { contentStretch = v }
        @JvmName("setSpringDampingPref") fun setSpringDamping(v: Float) { springDamping = v }
        @JvmName("setSpringStiffnessPref") fun setSpringStiffness(v: Float) { springStiffness = v }
        @JvmName("setLensSurgeDpPref") fun setLensSurgeDp(v: Float) { lensSurgeDp = v }
        @JvmName("setMotionBlurSurgeDpPref") fun setMotionBlurSurgeDp(v: Float) { motionBlurSurgeDp = v }

        fun reloadFromDefaults() {
            morphSpeedMultiplier = defaults.morphSpeedMultiplier
            circleCollapseRatio = defaults.circleCollapseRatio
            circleDiameterDp = defaults.circleDiameterDp
            zPlungeDepth = defaults.zPlungeDepth
            gravitySwoopDp = defaults.gravitySwoopDp
            yBounceAmountDp = defaults.yBounceAmountDp
            jellyOvershoot = defaults.jellyOvershoot
            contentStretch = defaults.contentStretch
            springDamping = defaults.springDamping
            springStiffness = defaults.springStiffness
            lensSurgeDp = defaults.lensSurgeDp
            motionBlurSurgeDp = defaults.motionBlurSurgeDp
        }
    }


    inner class SafeModeHolder {
        private val defaults = SafeModeSettings()

        var globalSafeMode by prefsBooleanState("prefs_key_global_safe_mode", defaults.globalSafeMode); private set
        var systemUiSafeMode by prefsBooleanState("prefs_key_systemui_safe_mode_enable", defaults.systemUiSafeMode); private set
        var launcherSafeMode by prefsBooleanState("prefs_key_home_safe_mode_enable", defaults.launcherSafeMode); private set
        var instagramSafeMode by prefsBooleanState("prefs_key_instagram_safe_mode_enable", defaults.instagramSafeMode); private set
        var whatsappSafeMode by prefsBooleanState("prefs_key_whatsapp_safe_mode_enable", defaults.whatsappSafeMode); private set
        var youtubeSafeMode by prefsBooleanState("prefs_key_youtube_safe_mode_enable", defaults.youtubeSafeMode); private set

        fun getCrashingAliases(): Set<String> {
            return dev.joseph.hyperbounce.safemode.CrashScope.getCrashingAliases()
        }

        fun isAliasActive(alias: String): Boolean {
            return dev.joseph.hyperbounce.safemode.CrashScope.isAliasInSafeModeProp(alias)
        }

        @JvmName("setGlobalSafeModePref")
        fun setGlobalSafeMode(v: Boolean) {
            globalSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("all")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("all")
            }
        }

        @JvmName("setSystemUiSafeModePref")
        fun setSystemUiSafeMode(v: Boolean) {
            systemUiSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("systemui")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("systemui")
            }
        }

        @JvmName("setLauncherSafeModePref")
        fun setLauncherSafeMode(v: Boolean) {
            launcherSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("home")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("home")
            }
        }

        @JvmName("setInstagramSafeModePref")
        fun setInstagramSafeMode(v: Boolean) {
            instagramSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("instagram")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("instagram")
            }
        }

        @JvmName("setWhatsappSafeModePref")
        fun setWhatsappSafeMode(v: Boolean) {
            whatsappSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("whatsapp")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("whatsapp")
            }
        }

        @JvmName("setYoutubeSafeModePref")
        fun setYoutubeSafeMode(v: Boolean) {
            youtubeSafeMode = v
            if (v) {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.updateCrashProp("youtube")
            } else {
                dev.joseph.hyperbounce.safemode.SafeModeHandler.removeCrashProp("youtube")
            }
        }

        fun clearAll() {
            setGlobalSafeMode(false)
            setSystemUiSafeMode(false)
            setLauncherSafeMode(false)
            setInstagramSafeMode(false)
            setWhatsappSafeMode(false)
            setYoutubeSafeMode(false)
            dev.joseph.hyperbounce.safemode.SafeModeHandler.clearAllSafeModes()
        }
    }

    // --- small property-delegate helpers over SharedPreferences --------------------------

    private fun prefsBooleanState(key: String, default: Boolean) =
        PrefBooleanDelegate(prefs, key, default)

    private fun prefsIntState(key: String, default: Int) =
        PrefIntDelegate(prefs, key, default)

    private fun prefsLongState(key: String, default: Long) =
        PrefLongDelegate(prefs, key, default)

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
        XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putBoolean(key, value)?.apply()
        SettingsRepository.sendSettingsChangedBroadcast(key, value)
    }
}

private class PrefLongDelegate(
    private val prefs: SharedPreferences,
    private val key: String,
    default: Long
) {
    private var state by mutableStateOf(prefs.getLong(key, default))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        state = value
        prefs.edit().putLong(key, value).apply()
        XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putLong(key, value)?.apply()
        SettingsRepository.sendSettingsChangedBroadcast(key, value)
    }
}

private class PrefIntDelegate(
    private val prefs: SharedPreferences,
    private val key: String,
    default: Int
) {
    private var state by mutableStateOf(prefs.getInt(key, default))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = state
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        state = value
        prefs.edit().putInt(key, value).apply()
        XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putInt(key, value)?.apply()
        SettingsRepository.sendSettingsChangedBroadcast(key, value)
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
        XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, value)?.apply()
        SettingsRepository.sendSettingsChangedBroadcast(key, value)
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
        XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putString(key, value.name)?.apply()
        SettingsRepository.sendSettingsChangedBroadcast(key, value.name)
    }
}
