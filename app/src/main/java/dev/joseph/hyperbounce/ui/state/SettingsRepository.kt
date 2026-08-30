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
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty

private const val PREFS_NAME = "hyperbounce_settings"

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
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
    val lockScreenKeypad = LockScreenKeypadHolder()
    val screenUnlock = ScreenUnlockHolder()
    val appTransition = AppTransitionHolder()
    val controlCenter = ControlCenterHolder()

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
        lockScreenKeypad.reloadFromDefaults()
        screenUnlock.reloadFromDefaults()
        appTransition.reloadFromDefaults()
        controlCenter.reloadFromDefaults()
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
            NavApp.WhatsApp -> NavBarAppSettings(barHeight = 70f, tabWidth = 92f, cornerRadius = 36f)
            else -> NavBarAppSettings()
        }

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

        @JvmName("setEnabledPref")
        fun setEnabled(v: Boolean) { enabled = v }
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
        @JvmName("setThemeModePref")
        fun setThemeMode(v: ThemeMode) { themeMode = v }
        @JvmName("setAccentPref")
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
        var borderWidth by prefsFloatState("lock_card_border_width", defaults.borderWidth); private set
        var borderAlpha by prefsFloatState("lock_card_border_alpha", defaults.borderAlpha); private set
        var refractionHeight by prefsFloatState("lock_card_refraction_height", defaults.refractionHeight); private set
        var refractionAmount by prefsFloatState("lock_card_refraction_amount", defaults.refractionAmount); private set
        var depthEffect by prefsFloatState("lock_card_depth_effect", defaults.depthEffect); private set
        var pillWidth by prefsFloatState("lock_pill_width", defaults.pillWidth); private set
        var pillHeight by prefsFloatState("lock_pill_height", defaults.pillHeight); private set
        var pillBottomMargin by prefsFloatState("lock_pill_bottom_margin", defaults.pillBottomMargin); private set
        var lipHeight by prefsFloatState("lock_lip_height", defaults.lipHeight); private set

        fun snapshot(): LockScreenSheetSettings = LockScreenSheetSettings(
            enabled, borderWidth, borderAlpha, refractionHeight, refractionAmount, depthEffect,
            pillWidth, pillHeight, pillBottomMargin, lipHeight
        )

        @JvmName("setSheetEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setSheetBorderWidthPref") fun setBorderWidth(v: Float) { borderWidth = v }
        @JvmName("setSheetBorderAlphaPref") fun setBorderAlpha(v: Float) { borderAlpha = v }
        @JvmName("setSheetRefractionHeightPref") fun setRefractionHeight(v: Float) { refractionHeight = v }
        @JvmName("setSheetRefractionAmountPref") fun setRefractionAmount(v: Float) { refractionAmount = v }
        @JvmName("setSheetDepthEffectPref") fun setDepthEffect(v: Float) { depthEffect = v }
        @JvmName("setSheetPillWidthPref") fun setPillWidth(v: Float) { pillWidth = v }
        @JvmName("setSheetPillHeightPref") fun setPillHeight(v: Float) { pillHeight = v }
        @JvmName("setSheetPillBottomMarginPref") fun setPillBottomMargin(v: Float) { pillBottomMargin = v }
        @JvmName("setSheetLipHeightPref") fun setLipHeight(v: Float) { lipHeight = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            borderWidth = defaults.borderWidth
            borderAlpha = defaults.borderAlpha
            refractionHeight = defaults.refractionHeight
            refractionAmount = defaults.refractionAmount
            depthEffect = defaults.depthEffect
            pillWidth = defaults.pillWidth
            pillHeight = defaults.pillHeight
            pillBottomMargin = defaults.pillBottomMargin
            lipHeight = defaults.lipHeight
        }
    }

    // --- lock screen keypad physics settings (Feature 2) ----------------------------------

    inner class LockScreenKeypadHolder {
        private val defaults = LockScreenKeypadSettings()

        var enabled by prefsBooleanState("engine_keypad_enabled", defaults.enabled); private set
        var circleDiameter by prefsFloatState("pin_circle_diameter", defaults.circleDiameter); private set
        var fillAlpha by prefsFloatState("pin_circle_fill_alpha", defaults.fillAlpha); private set
        var strokeWidth by prefsFloatState("pin_circle_stroke_width", defaults.strokeWidth); private set
        var strokeAlpha by prefsFloatState("pin_circle_stroke_alpha", defaults.strokeAlpha); private set
        var showKlondike by prefsBooleanState("pin_show_klondike", defaults.showKlondike); private set
        var digitTextSize by prefsFloatState("pin_digit_text_size", defaults.digitTextSize); private set
        var row0Offset by prefsFloatState("pin_row0_offset", defaults.row0Offset); private set
        var row1Offset by prefsFloatState("pin_row1_offset", defaults.row1Offset); private set
        var row2Offset by prefsFloatState("pin_row2_offset", defaults.row2Offset); private set
        var row3Offset by prefsFloatState("pin_row3_offset", defaults.row3Offset); private set
        var tapSquishScale by prefsFloatState("pin_tap_squish_scale", defaults.tapSquishScale); private set
        var releasePlunge by prefsFloatState("pin_release_plunge", defaults.releasePlunge); private set
        var jellyMaxStretch by prefsFloatState("pin_jelly_max_stretch", defaults.jellyMaxStretch); private set
        var jellyElasticLimit by prefsFloatState("pin_jelly_elastic_limit", defaults.jellyElasticLimit); private set
        var springStiffness by prefsFloatState("pin_spring_stiffness", defaults.springStiffness); private set
        var springDamping by prefsFloatState("pin_spring_damping", defaults.springDamping); private set
        var pressGlowAlpha by prefsFloatState("pin_press_glow_alpha", defaults.pressGlowAlpha); private set
        var glowRadius by prefsFloatState("pin_glow_radius", defaults.glowRadius); private set
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
            enabled, circleDiameter, fillAlpha, strokeWidth, strokeAlpha, showKlondike, digitTextSize,
            row0Offset, row1Offset, row2Offset, row3Offset, tapSquishScale, releasePlunge,
            jellyMaxStretch, jellyElasticLimit, springStiffness, springDamping, pressGlowAlpha,
            glowRadius, entryTranslationY, entryInitialScale, entryDuration, rowDelayStep, colDelayStep,
            entryOvershoot, delayKey1, delayKey2, delayKey3, delayKey4, delayKey5, delayKey6,
            delayKey7, delayKey8, delayKey9, delayKey0, delayEmergency, delayCancel
        )

        @JvmName("setKeypadEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setCircleDiameterPref") fun setCircleDiameter(v: Float) { circleDiameter = v }
        @JvmName("setFillAlphaPref") fun setFillAlpha(v: Float) { fillAlpha = v }
        @JvmName("setStrokeWidthPref") fun setStrokeWidth(v: Float) { strokeWidth = v }
        @JvmName("setStrokeAlphaPref") fun setStrokeAlpha(v: Float) { strokeAlpha = v }
        @JvmName("setShowKlondikePref") fun setShowKlondike(v: Boolean) { showKlondike = v }
        @JvmName("setDigitTextSizePref") fun setDigitTextSize(v: Float) { digitTextSize = v }
        @JvmName("setRow0OffsetPref") fun setRow0Offset(v: Float) { row0Offset = v }
        @JvmName("setRow1OffsetPref") fun setRow1Offset(v: Float) { row1Offset = v }
        @JvmName("setRow2OffsetPref") fun setRow2Offset(v: Float) { row2Offset = v }
        @JvmName("setRow3OffsetPref") fun setRow3Offset(v: Float) { row3Offset = v }
        @JvmName("setTapSquishScalePref") fun setTapSquishScale(v: Float) { tapSquishScale = v }
        @JvmName("setReleasePlungePref") fun setReleasePlunge(v: Float) { releasePlunge = v }
        @JvmName("setJellyMaxStretchPref") fun setJellyMaxStretch(v: Float) { jellyMaxStretch = v }
        @JvmName("setJellyElasticLimitPref") fun setJellyElasticLimit(v: Float) { jellyElasticLimit = v }
        @JvmName("setSpringStiffnessPref") fun setSpringStiffness(v: Float) { springStiffness = v }
        @JvmName("setSpringDampingPref") fun setSpringDamping(v: Float) { springDamping = v }
        @JvmName("setPressGlowAlphaPref") fun setPressGlowAlpha(v: Float) { pressGlowAlpha = v }
        @JvmName("setGlowRadiusPref") fun setGlowRadius(v: Float) { glowRadius = v }
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
            circleDiameter = defaults.circleDiameter
            fillAlpha = defaults.fillAlpha
            strokeWidth = defaults.strokeWidth
            strokeAlpha = defaults.strokeAlpha
            showKlondike = defaults.showKlondike
            digitTextSize = defaults.digitTextSize
            row0Offset = defaults.row0Offset
            row1Offset = defaults.row1Offset
            row2Offset = defaults.row2Offset
            row3Offset = defaults.row3Offset
            tapSquishScale = defaults.tapSquishScale
            releasePlunge = defaults.releasePlunge
            jellyMaxStretch = defaults.jellyMaxStretch
            jellyElasticLimit = defaults.jellyElasticLimit
            springStiffness = defaults.springStiffness
            springDamping = defaults.springDamping
            pressGlowAlpha = defaults.pressGlowAlpha
            glowRadius = defaults.glowRadius
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

        fun getRowZ(row: Int): Float {
            val defZ = (2000.0f - (row * 200.0f)).coerceAtLeast(100.0f)
            return prefs.getFloat("unlock_r${row}_z", defZ)
        }
        fun setRowZ(row: Int, v: Float) {
            val key = "unlock_r${row}_z"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
        }

        fun getRowCenterBounce(row: Int): Float {
            val defBounce = (0.45f - (row * 0.05f)).coerceAtLeast(0.0f)
            return prefs.getFloat("unlock_r${row}_center_bounce", defBounce)
        }
        fun setRowCenterBounce(row: Int, v: Float) {
            val key = "unlock_r${row}_center_bounce"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
        }

        fun getRowOuterBounce(row: Int): Float {
            val defBounce = (0.45f - (row * 0.05f)).coerceAtLeast(0.0f)
            return prefs.getFloat("unlock_r${row}_outer_bounce", defBounce)
        }
        fun setRowOuterBounce(row: Int, v: Float) {
            val key = "unlock_r${row}_outer_bounce"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
        }

        fun getRowCenterSettle(row: Int): Float {
            val defSettle = 0.55f + (row * 0.04f)
            return prefs.getFloat("unlock_r${row}_center_settle", defSettle)
        }
        fun setRowCenterSettle(row: Int, v: Float) {
            val key = "unlock_r${row}_center_settle"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
        }

        fun getRowOuterSettle(row: Int): Float {
            val defSettle = 0.55f + (row * 0.04f)
            return prefs.getFloat("unlock_r${row}_outer_settle", defSettle)
        }
        fun setRowOuterSettle(row: Int, v: Float) {
            val key = "unlock_r${row}_outer_settle"
            prefs.edit().putFloat(key, v).apply()
            XposedServiceBridge.remotePreferencesOrNull(PREFS_NAME)?.edit()?.putFloat(key, v)?.apply()
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
        }
    }

    // --- app open/close spring transitions (Feature 4) -----------------------------------

    inner class AppTransitionHolder {
        private val defaults = AppTransitionSettings()

        var enabled by prefsBooleanState("app_anim_enabled", defaults.enabled); private set
        var posDamping by prefsFloatState("app_pos_damping", defaults.posDamping); private set
        var posResponse by prefsFloatState("app_pos_response", defaults.posResponse); private set
        var scaleDamping by prefsFloatState("app_scale_damping", defaults.scaleDamping); private set
        var scaleResponse by prefsFloatState("app_scale_response", defaults.scaleResponse); private set
        var cornerDamping by prefsFloatState("app_corner_damping", defaults.cornerDamping); private set
        var cornerResponse by prefsFloatState("app_corner_response", defaults.cornerResponse); private set
        var alphaDamping by prefsFloatState("app_alpha_damping", defaults.alphaDamping); private set
        var alphaResponse by prefsFloatState("app_alpha_response", defaults.alphaResponse); private set
        var appYArc by prefsFloatState("app_y_arc", defaults.appYArc); private set
        var appBottomZWarp by prefsFloatState("app_bottom_z_warp", defaults.appBottomZWarp); private set
        var appXSway by prefsFloatState("app_x_sway", defaults.appXSway); private set
        var appInterruptSmooth by prefsFloatState("app_interrupt_smooth", defaults.appInterruptSmooth); private set
        var appIconPitchDeg by prefsFloatState("app_icon_pitch_deg", defaults.appIconPitchDeg); private set
        var appIconCameraDist by prefsFloatState("app_icon_camera_dist", defaults.appIconCameraDist); private set

        fun snapshot(): AppTransitionSettings = AppTransitionSettings(
            enabled, posDamping, posResponse, scaleDamping, scaleResponse,
            cornerDamping, cornerResponse, alphaDamping, alphaResponse,
            appYArc, appBottomZWarp, appXSway, appInterruptSmooth, appIconPitchDeg, appIconCameraDist
        )

        @JvmName("setAppAnimEnabledPref") fun setEnabled(v: Boolean) { enabled = v }
        @JvmName("setAppPosDampingPref") fun setPosDamping(v: Float) { posDamping = v }
        @JvmName("setAppPosResponsePref") fun setPosResponse(v: Float) { posResponse = v }
        @JvmName("setScaleDampingPref") fun setScaleDamping(v: Float) { scaleDamping = v }
        @JvmName("setScaleResponsePref") fun setScaleResponse(v: Float) { scaleResponse = v }
        @JvmName("setCornerDampingPref") fun setCornerDamping(v: Float) { cornerDamping = v }
        @JvmName("setCornerResponsePref") fun setCornerResponse(v: Float) { cornerResponse = v }
        @JvmName("setAlphaDampingPref") fun setAlphaDamping(v: Float) { alphaDamping = v }
        @JvmName("setAlphaResponsePref") fun setAlphaResponse(v: Float) { alphaResponse = v }
        @JvmName("setAppYArcPref") fun setAppYArc(v: Float) { appYArc = v }
        @JvmName("setAppBottomZWarpPref") fun setAppBottomZWarp(v: Float) { appBottomZWarp = v }
        @JvmName("setAppXSwayPref") fun setAppXSway(v: Float) { appXSway = v }
        @JvmName("setAppInterruptSmoothPref") fun setAppInterruptSmooth(v: Float) { appInterruptSmooth = v }
        @JvmName("setAppIconPitchDegPref") fun setAppIconPitchDeg(v: Float) { appIconPitchDeg = v }
        @JvmName("setAppIconCameraDistPref") fun setAppIconCameraDist(v: Float) { appIconCameraDist = v }

        fun reloadFromDefaults() {
            enabled = defaults.enabled
            posDamping = defaults.posDamping
            posResponse = defaults.posResponse
            scaleDamping = defaults.scaleDamping
            scaleResponse = defaults.scaleResponse
            cornerDamping = defaults.cornerDamping
            cornerResponse = defaults.cornerResponse
            alphaDamping = defaults.alphaDamping
            alphaResponse = defaults.alphaResponse
            appYArc = defaults.appYArc
            appBottomZWarp = defaults.appBottomZWarp
            appXSway = defaults.appXSway
            appInterruptSmooth = defaults.appInterruptSmooth
            appIconPitchDeg = defaults.appIconPitchDeg
            appIconCameraDist = defaults.appIconCameraDist
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


    // --- small property-delegate helpers over SharedPreferences --------------------------

    private fun prefsBooleanState(key: String, default: Boolean) =
        PrefBooleanDelegate(prefs, key, default)

    private fun prefsIntState(key: String, default: Int) =
        PrefIntDelegate(prefs, key, default)

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
    }
}
