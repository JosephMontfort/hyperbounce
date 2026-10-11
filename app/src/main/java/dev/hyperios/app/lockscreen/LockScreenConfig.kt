package dev.hyperios.app.lockscreen

import android.content.Intent
import android.content.SharedPreferences
import dev.hyperios.app.common.ModuleDefaults

data class LockScreenSheetConfig(
    var enabled: Boolean = ModuleDefaults.MasterToggles.LOCK_SHEET_ENABLED,
    var enableOptimization: Boolean = ModuleDefaults.LockScreenSheet.ENABLE_OPTIMIZATION,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = ModuleDefaults.LockScreenSheet.REFRACTION_HEIGHT,
    var sideRefraction: Float = ModuleDefaults.LockScreenSheet.SIDE_REFRACTION,
    var bottomRefraction: Float = ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION,
    var bevelWidth: Float = ModuleDefaults.LockScreenSheet.BEVEL_WIDTH,
    var leftBevelWidth: Float = ModuleDefaults.LockScreenSheet.LEFT_BEVEL_WIDTH,
    var rightBevelWidth: Float = ModuleDefaults.LockScreenSheet.RIGHT_BEVEL_WIDTH,
    var bottomBevelWidth: Float = ModuleDefaults.LockScreenSheet.BOTTOM_BEVEL_WIDTH,
    var dispersionStrength: Float = ModuleDefaults.LockScreenSheet.DISPERSION_STRENGTH,
    var specularStrength: Float = ModuleDefaults.LockScreenSheet.SPECULAR_STRENGTH,
    var hairlineStrength: Float = ModuleDefaults.LockScreenSheet.HAIRLINE_STRENGTH,
    var innerGlowStrength: Float = ModuleDefaults.LockScreenSheet.INNER_GLOW_STRENGTH,
    var innerShadow: Float = ModuleDefaults.LockScreenSheet.INNER_SHADOW,
    var saturationFactor: Float = ModuleDefaults.LockScreenSheet.SATURATION_FACTOR,
    var rimSoftness: Float = ModuleDefaults.LockScreenSheet.RIM_SOFTNESS,
    var blurRadius: Float = ModuleDefaults.LockScreenSheet.BLUR_RADIUS,
    var cornerRadius: Float = ModuleDefaults.LockScreenSheet.CORNER_RADIUS,
    var edgeStretch: Float = ModuleDefaults.LockScreenSheet.EDGE_STRETCH,
    // Home indicator pill
    var pillEnabled: Boolean = ModuleDefaults.LockScreenSheet.PILL_ENABLED,
    var pillWidth: Float = ModuleDefaults.LockScreenSheet.PILL_WIDTH,
    var pillHeight: Float = ModuleDefaults.LockScreenSheet.PILL_HEIGHT,
    var pillBottomMargin: Float = ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN
)

data class LockScreenKeypadConfig(
    var enabled: Boolean = ModuleDefaults.MasterToggles.ENGINE_KEYPAD_ENABLED,
    // Liquid Glass 2.0 Button Optics
    var buttonRefraction: Float = ModuleDefaults.LockScreenKeypad.BTN_REFRACTION,
    var buttonBevel: Float = ModuleDefaults.LockScreenKeypad.BTN_BEVEL,
    var buttonDispersion: Float = ModuleDefaults.LockScreenKeypad.BTN_DISPERSION,
    var buttonSpecular: Float = ModuleDefaults.LockScreenKeypad.BTN_SPECULAR,
    var buttonInnerShadow: Float = ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW,
    var buttonBlur: Float = ModuleDefaults.LockScreenKeypad.BTN_BLUR,
    var buttonSaturation: Float = ModuleDefaults.LockScreenKeypad.BTN_SATURATION,
    var circleDiameter: Float = ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER,
    var digitTextSize: Float = ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE,
    var showKlondike: Boolean = ModuleDefaults.LockScreenKeypad.SHOW_KLONDIKE,
    var pressLiquidScale: Float = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE,
    var pressLiquidAmp: Float = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_AMP,
    // PIN Pad Grid Layout & Spacing
    var pinpadLift: Float = ModuleDefaults.LockScreenKeypad.PAD_LIFT,
    var row0Offset: Float = ModuleDefaults.LockScreenKeypad.ROW0_OFFSET,
    var rowSpacing: Float = ModuleDefaults.LockScreenKeypad.ROW_SPACING,
    var columnSpacing: Float = ModuleDefaults.LockScreenKeypad.COL_SPACING,
    // Legacy fine-tune offsets
    var row1Offset: Float = ModuleDefaults.LockScreenKeypad.ROW1_OFFSET,
    var row2Offset: Float = ModuleDefaults.LockScreenKeypad.ROW2_OFFSET,
    var row3Offset: Float = ModuleDefaults.LockScreenKeypad.ROW3_OFFSET,
    var col1Offset: Float = ModuleDefaults.LockScreenKeypad.COL1_OFFSET,
    var col3Offset: Float = ModuleDefaults.LockScreenKeypad.COL3_OFFSET,
    // Per-Key Custom Delays (ms)
    var delayKey1: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY1,
    var delayKey2: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY2,
    var delayKey3: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY3,
    var delayKey4: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY4,
    var delayKey5: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY5,
    var delayKey6: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY6,
    var delayKey7: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY7,
    var delayKey8: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY8,
    var delayKey9: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY9,
    var delayKey0: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY0,
    var delayEmergency: Float = ModuleDefaults.LockScreenKeypad.DELAY_EMERGENCY,
    var delayCancel: Float = ModuleDefaults.LockScreenKeypad.DELAY_CANCEL,
    // Liquid Dynamics & Physics Tuning
    var dragMaxThreshold: Float = ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD,
    var dragInitialStiffness: Float = ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS,
    var dragResistancePower: Float = ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER,
    var dragVolumeStretch: Float = ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH,
    var releaseDuration: Float = ModuleDefaults.LockScreenKeypad.RELEASE_DURATION,
    var releaseSquishAmp: Float = ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP,
    var releaseOvershoot: Float = ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT,
    var iosBloomDuration: Float = ModuleDefaults.LockScreenKeypad.BLOOM_DURATION,
    var iosBloomClusterFactor: Float = ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR,
    var iosBloomOvershoot: Float = ModuleDefaults.LockScreenKeypad.BLOOM_OVERSHOOT,
    var iosBloomDamping: Float = ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING,
    var iosBloomStiffness: Float = ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS,
    var iosBloomRadialSquish: Float = ModuleDefaults.LockScreenKeypad.BLOOM_RADIAL_SQUISH,
    var iosBloomVelocityStretch: Float = ModuleDefaults.LockScreenKeypad.BLOOM_VELOCITY_STRETCH,
    var iosBloomWaveStagger: Float = ModuleDefaults.LockScreenKeypad.BLOOM_WAVE_STAGGER,
    var iosBloomDecoupleText: Boolean = ModuleDefaults.LockScreenKeypad.BLOOM_DECOUPLE_TEXT
) {
    fun getKeyDelay(keyName: String): Long {
        val delay = when (keyName) {
            "key1" -> delayKey1
            "key2" -> delayKey2
            "key3" -> delayKey3
            "key4" -> delayKey4
            "key5" -> delayKey5
            "key6" -> delayKey6
            "key7" -> delayKey7
            "key8" -> delayKey8
            "key9" -> delayKey9
            "key0" -> delayKey0
            "emergency_call_button" -> delayEmergency
            "delete_button", "cancel_button", "delete_container", "zero_delete_button", "zero_cancel_button" -> delayCancel
            else -> 0f
        }
        return delay.toLong().coerceAtLeast(0L)
    }
}

object LockScreenConfigHolder {
    val sheet = LockScreenSheetConfig()
    val keypad = LockScreenKeypadConfig()

    fun updateFromPrefs(prefs: SharedPreferences) {
        // Sheet configs (Liquid Glass 2.0)
        sheet.enabled = prefs.getBoolean("lock_sheet_enabled", ModuleDefaults.MasterToggles.LOCK_SHEET_ENABLED)
        sheet.enableOptimization = prefs.getBoolean("lock_sheet_enable_optimization", ModuleDefaults.LockScreenSheet.ENABLE_OPTIMIZATION)
        sheet.refractionHeight = prefs.getFloat("lock_card_refraction_height", ModuleDefaults.LockScreenSheet.REFRACTION_HEIGHT)
        sheet.sideRefraction = prefs.getFloat("lock_card_side_refraction", ModuleDefaults.LockScreenSheet.SIDE_REFRACTION)
        sheet.bottomRefraction = prefs.getFloat("lock_card_bottom_refraction", ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION)
        sheet.bevelWidth = prefs.getFloat("lock_card_bevel_width", ModuleDefaults.LockScreenSheet.BEVEL_WIDTH)
        sheet.leftBevelWidth = prefs.getFloat("lock_card_left_bevel", ModuleDefaults.LockScreenSheet.LEFT_BEVEL_WIDTH)
        sheet.rightBevelWidth = prefs.getFloat("lock_card_right_bevel", ModuleDefaults.LockScreenSheet.RIGHT_BEVEL_WIDTH)
        sheet.bottomBevelWidth = prefs.getFloat("lock_card_bottom_bevel", ModuleDefaults.LockScreenSheet.BOTTOM_BEVEL_WIDTH)
        sheet.dispersionStrength = prefs.getFloat("lock_card_dispersion", ModuleDefaults.LockScreenSheet.DISPERSION_STRENGTH)
        sheet.specularStrength = prefs.getFloat("lock_card_specular", ModuleDefaults.LockScreenSheet.SPECULAR_STRENGTH)
        sheet.hairlineStrength = prefs.getFloat("lock_card_hairline", ModuleDefaults.LockScreenSheet.HAIRLINE_STRENGTH)
        sheet.innerGlowStrength = prefs.getFloat("lock_card_inner_glow", ModuleDefaults.LockScreenSheet.INNER_GLOW_STRENGTH)
        sheet.innerShadow = prefs.getFloat("lock_card_inner_shadow", ModuleDefaults.LockScreenSheet.INNER_SHADOW)
        sheet.saturationFactor = prefs.getFloat("lock_card_saturation", ModuleDefaults.LockScreenSheet.SATURATION_FACTOR)
        sheet.rimSoftness = prefs.getFloat("lock_card_rim_softness", ModuleDefaults.LockScreenSheet.RIM_SOFTNESS)
        sheet.blurRadius = prefs.getFloat("lock_card_blur_radius", ModuleDefaults.LockScreenSheet.BLUR_RADIUS)
        sheet.cornerRadius = prefs.getFloat("lock_card_corner_radius", ModuleDefaults.LockScreenSheet.CORNER_RADIUS)
        sheet.edgeStretch = prefs.getFloat("lock_card_edge_stretch", 28.0f)
        sheet.pillEnabled = prefs.getBoolean("lock_pill_enabled", ModuleDefaults.LockScreenSheet.PILL_ENABLED)
        sheet.pillWidth = prefs.getFloat("lock_pill_width", ModuleDefaults.LockScreenSheet.PILL_WIDTH)
        sheet.pillHeight = prefs.getFloat("lock_pill_height", ModuleDefaults.LockScreenSheet.PILL_HEIGHT)
        sheet.pillBottomMargin = prefs.getFloat("lock_pill_bottom_margin", ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN)

        // Keypad configs (Liquid Glass 2.0 Buttons)
        keypad.enabled = prefs.getBoolean("engine_keypad_enabled", ModuleDefaults.MasterToggles.ENGINE_KEYPAD_ENABLED)
        keypad.buttonRefraction = prefs.getFloat("pin_btn_refraction", ModuleDefaults.LockScreenKeypad.BTN_REFRACTION)
        keypad.buttonBevel = prefs.getFloat("pin_btn_bevel", ModuleDefaults.LockScreenKeypad.BTN_BEVEL)
        keypad.buttonDispersion = prefs.getFloat("pin_btn_dispersion", ModuleDefaults.LockScreenKeypad.BTN_DISPERSION)
        keypad.buttonSpecular = prefs.getFloat("pin_btn_specular", ModuleDefaults.LockScreenKeypad.BTN_SPECULAR)
        keypad.buttonInnerShadow = prefs.getFloat("pin_btn_inner_shadow", ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW)
        keypad.buttonBlur = prefs.getFloat("pin_btn_blur", ModuleDefaults.LockScreenKeypad.BTN_BLUR)
        keypad.buttonSaturation = prefs.getFloat("pin_btn_saturation", ModuleDefaults.LockScreenKeypad.BTN_SATURATION)
        keypad.circleDiameter = prefs.getFloat("pin_circle_diameter", ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER)
        keypad.digitTextSize = prefs.getFloat("pin_digit_text_size", ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE)
        keypad.showKlondike = prefs.getBoolean("pin_show_klondike", ModuleDefaults.LockScreenKeypad.SHOW_KLONDIKE)
        keypad.pressLiquidScale = prefs.getFloat("pin_press_liquid_scale", ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE)
        keypad.pressLiquidAmp = prefs.getFloat("pin_press_liquid_amp", ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_AMP)

        keypad.pinpadLift = prefs.getFloat("pin_pad_lift", ModuleDefaults.LockScreenKeypad.PAD_LIFT)
        keypad.row0Offset = prefs.getFloat("pin_row0_offset", ModuleDefaults.LockScreenKeypad.ROW0_OFFSET)
        keypad.rowSpacing = prefs.getFloat("pin_row_spacing", ModuleDefaults.LockScreenKeypad.ROW_SPACING)
        keypad.columnSpacing = prefs.getFloat("pin_col_spacing", ModuleDefaults.LockScreenKeypad.COL_SPACING)
        keypad.row1Offset = prefs.getFloat("pin_row1_offset", ModuleDefaults.LockScreenKeypad.ROW1_OFFSET)
        keypad.row2Offset = prefs.getFloat("pin_row2_offset", ModuleDefaults.LockScreenKeypad.ROW2_OFFSET)
        keypad.row3Offset = prefs.getFloat("pin_row3_offset", ModuleDefaults.LockScreenKeypad.ROW3_OFFSET)
        keypad.col1Offset = prefs.getFloat("pin_col1_offset", ModuleDefaults.LockScreenKeypad.COL1_OFFSET)
        keypad.col3Offset = prefs.getFloat("pin_col3_offset", ModuleDefaults.LockScreenKeypad.COL3_OFFSET)

        // Per-Key Custom Entrance Delays (ms)
        keypad.delayKey1 = prefs.getFloat("pin_delay_key1", ModuleDefaults.LockScreenKeypad.DELAY_KEY1)
        keypad.delayKey2 = prefs.getFloat("pin_delay_key2", ModuleDefaults.LockScreenKeypad.DELAY_KEY2)
        keypad.delayKey3 = prefs.getFloat("pin_delay_key3", ModuleDefaults.LockScreenKeypad.DELAY_KEY3)
        keypad.delayKey4 = prefs.getFloat("pin_delay_key4", ModuleDefaults.LockScreenKeypad.DELAY_KEY4)
        keypad.delayKey5 = prefs.getFloat("pin_delay_key5", ModuleDefaults.LockScreenKeypad.DELAY_KEY5)
        keypad.delayKey6 = prefs.getFloat("pin_delay_key6", ModuleDefaults.LockScreenKeypad.DELAY_KEY6)
        keypad.delayKey7 = prefs.getFloat("pin_delay_key7", ModuleDefaults.LockScreenKeypad.DELAY_KEY7)
        keypad.delayKey8 = prefs.getFloat("pin_delay_key8", ModuleDefaults.LockScreenKeypad.DELAY_KEY8)
        keypad.delayKey9 = prefs.getFloat("pin_delay_key9", ModuleDefaults.LockScreenKeypad.DELAY_KEY9)
        keypad.delayKey0 = prefs.getFloat("pin_delay_key0", ModuleDefaults.LockScreenKeypad.DELAY_KEY0)
        keypad.delayEmergency = prefs.getFloat("pin_delay_emergency", ModuleDefaults.LockScreenKeypad.DELAY_EMERGENCY)
        keypad.delayCancel = prefs.getFloat("pin_delay_cancel", ModuleDefaults.LockScreenKeypad.DELAY_CANCEL)

        // Liquid Dynamics & Physics Tuning
        keypad.dragMaxThreshold = prefs.getFloat("pin_drag_max_threshold", ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD)
        keypad.dragInitialStiffness = prefs.getFloat("pin_drag_initial_stiffness", ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS)
        keypad.dragResistancePower = prefs.getFloat("pin_drag_resistance_power", ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER)
        keypad.dragVolumeStretch = prefs.getFloat("pin_drag_volume_stretch", ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH)
        keypad.releaseDuration = prefs.getFloat("pin_release_duration", ModuleDefaults.LockScreenKeypad.RELEASE_DURATION)
        keypad.releaseSquishAmp = prefs.getFloat("pin_release_squish_amp", ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP)
        keypad.releaseOvershoot = prefs.getFloat("pin_release_overshoot", ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT)
        keypad.iosBloomDuration = prefs.getFloat("pin_ios_bloom_duration", ModuleDefaults.LockScreenKeypad.BLOOM_DURATION)
        keypad.iosBloomClusterFactor = prefs.getFloat("pin_ios_bloom_cluster_factor", ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR)
        keypad.iosBloomOvershoot = prefs.getFloat("pin_ios_bloom_overshoot", ModuleDefaults.LockScreenKeypad.BLOOM_OVERSHOOT)
        keypad.iosBloomDamping = prefs.getFloat("pin_ios_bloom_damping", ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING)
        keypad.iosBloomStiffness = prefs.getFloat("pin_ios_bloom_stiffness", ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS)
        keypad.iosBloomRadialSquish = prefs.getFloat("pin_ios_bloom_radial_squish", ModuleDefaults.LockScreenKeypad.BLOOM_RADIAL_SQUISH)
        keypad.iosBloomVelocityStretch = prefs.getFloat("pin_ios_bloom_velocity_stretch", ModuleDefaults.LockScreenKeypad.BLOOM_VELOCITY_STRETCH)
        keypad.iosBloomWaveStagger = prefs.getFloat("pin_ios_bloom_wave_stagger", ModuleDefaults.LockScreenKeypad.BLOOM_WAVE_STAGGER)
        keypad.iosBloomDecoupleText = prefs.getBoolean("pin_ios_bloom_decouple_text", ModuleDefaults.LockScreenKeypad.BLOOM_DECOUPLE_TEXT)
    }

    fun updateKey(key: String, intent: Intent) {
        when (key) {
            "lock_sheet_enabled" -> sheet.enabled = intent.getBooleanExtra("value_boolean", sheet.enabled)
            "lock_pill_enabled" -> sheet.pillEnabled = intent.getBooleanExtra("value_boolean", sheet.pillEnabled)
            "lock_pill_width" -> sheet.pillWidth = intent.getFloatExtra("value_float", sheet.pillWidth)
            "lock_pill_height" -> sheet.pillHeight = intent.getFloatExtra("value_float", sheet.pillHeight)
            "lock_pill_bottom_margin" -> sheet.pillBottomMargin = intent.getFloatExtra("value_float", sheet.pillBottomMargin)
            "engine_keypad_enabled" -> keypad.enabled = intent.getBooleanExtra("value_boolean", keypad.enabled)
            "pin_btn_refraction" -> keypad.buttonRefraction = intent.getFloatExtra("value_float", keypad.buttonRefraction)
            "pin_btn_bevel" -> keypad.buttonBevel = intent.getFloatExtra("value_float", keypad.buttonBevel)
            "pin_btn_dispersion" -> keypad.buttonDispersion = intent.getFloatExtra("value_float", keypad.buttonDispersion)
            "pin_btn_specular" -> keypad.buttonSpecular = intent.getFloatExtra("value_float", keypad.buttonSpecular)
            "pin_btn_inner_shadow" -> keypad.buttonInnerShadow = intent.getFloatExtra("value_float", keypad.buttonInnerShadow)
            "pin_btn_blur" -> keypad.buttonBlur = intent.getFloatExtra("value_float", keypad.buttonBlur)
            "pin_btn_saturation" -> keypad.buttonSaturation = intent.getFloatExtra("value_float", keypad.buttonSaturation)
            "pin_circle_diameter" -> keypad.circleDiameter = intent.getFloatExtra("value_float", keypad.circleDiameter)
            "pin_digit_text_size" -> keypad.digitTextSize = intent.getFloatExtra("value_float", keypad.digitTextSize)
            "pin_show_klondike" -> keypad.showKlondike = intent.getBooleanExtra("value_boolean", keypad.showKlondike)
            "pin_press_liquid_scale" -> keypad.pressLiquidScale = intent.getFloatExtra("value_float", keypad.pressLiquidScale)
            "pin_press_liquid_amp" -> keypad.pressLiquidAmp = intent.getFloatExtra("value_float", keypad.pressLiquidAmp)
            "pin_drag_max_threshold" -> keypad.dragMaxThreshold = intent.getFloatExtra("value_float", keypad.dragMaxThreshold)
            "pin_drag_initial_stiffness" -> keypad.dragInitialStiffness = intent.getFloatExtra("value_float", keypad.dragInitialStiffness)
            "pin_drag_resistance_power" -> keypad.dragResistancePower = intent.getFloatExtra("value_float", keypad.dragResistancePower)
            "pin_drag_volume_stretch" -> keypad.dragVolumeStretch = intent.getFloatExtra("value_float", keypad.dragVolumeStretch)
            "pin_release_duration" -> keypad.releaseDuration = intent.getFloatExtra("value_float", keypad.releaseDuration)
            "pin_release_squish_amp" -> keypad.releaseSquishAmp = intent.getFloatExtra("value_float", keypad.releaseSquishAmp)
            "pin_release_overshoot" -> keypad.releaseOvershoot = intent.getFloatExtra("value_float", keypad.releaseOvershoot)
            "pin_pad_lift" -> keypad.pinpadLift = intent.getFloatExtra("value_float", keypad.pinpadLift)
            "pin_row_spacing" -> keypad.rowSpacing = intent.getFloatExtra("value_float", keypad.rowSpacing)
            "pin_col_spacing" -> keypad.columnSpacing = intent.getFloatExtra("value_float", keypad.columnSpacing)
            "pin_ios_bloom_duration" -> keypad.iosBloomDuration = intent.getFloatExtra("value_float", keypad.iosBloomDuration)
            "pin_ios_bloom_cluster_factor" -> keypad.iosBloomClusterFactor = intent.getFloatExtra("value_float", keypad.iosBloomClusterFactor)
            "pin_ios_bloom_overshoot" -> keypad.iosBloomOvershoot = intent.getFloatExtra("value_float", keypad.iosBloomOvershoot)
            "pin_ios_bloom_damping" -> keypad.iosBloomDamping = intent.getFloatExtra("value_float", keypad.iosBloomDamping)
            "pin_ios_bloom_stiffness" -> keypad.iosBloomStiffness = intent.getFloatExtra("value_float", keypad.iosBloomStiffness)
            "pin_ios_bloom_radial_squish" -> keypad.iosBloomRadialSquish = intent.getFloatExtra("value_float", keypad.iosBloomRadialSquish)
            "pin_ios_bloom_velocity_stretch" -> keypad.iosBloomVelocityStretch = intent.getFloatExtra("value_float", keypad.iosBloomVelocityStretch)
            "pin_ios_bloom_wave_stagger" -> keypad.iosBloomWaveStagger = intent.getFloatExtra("value_float", keypad.iosBloomWaveStagger)
            "pin_ios_bloom_decouple_text" -> keypad.iosBloomDecoupleText = intent.getBooleanExtra("value_boolean", keypad.iosBloomDecoupleText)

            // Per-Key Entrance Delays (ms) - Preserved in backend. If UI is restored, dynamic broadcast updates work out of the box.
            "pin_delay_key1" -> keypad.delayKey1 = intent.getFloatExtra("value_float", keypad.delayKey1)
            "pin_delay_key2" -> keypad.delayKey2 = intent.getFloatExtra("value_float", keypad.delayKey2)
            "pin_delay_key3" -> keypad.delayKey3 = intent.getFloatExtra("value_float", keypad.delayKey3)
            "pin_delay_key4" -> keypad.delayKey4 = intent.getFloatExtra("value_float", keypad.delayKey4)
            "pin_delay_key5" -> keypad.delayKey5 = intent.getFloatExtra("value_float", keypad.delayKey5)
            "pin_delay_key6" -> keypad.delayKey6 = intent.getFloatExtra("value_float", keypad.delayKey6)
            "pin_delay_key7" -> keypad.delayKey7 = intent.getFloatExtra("value_float", keypad.delayKey7)
            "pin_delay_key8" -> keypad.delayKey8 = intent.getFloatExtra("value_float", keypad.delayKey8)
            "pin_delay_key9" -> keypad.delayKey9 = intent.getFloatExtra("value_float", keypad.delayKey9)
            "pin_delay_key0" -> keypad.delayKey0 = intent.getFloatExtra("value_float", keypad.delayKey0)
            "pin_delay_emergency" -> keypad.delayEmergency = intent.getFloatExtra("value_float", keypad.delayEmergency)
            "pin_delay_cancel" -> keypad.delayCancel = intent.getFloatExtra("value_float", keypad.delayCancel)
        }
    }
}
