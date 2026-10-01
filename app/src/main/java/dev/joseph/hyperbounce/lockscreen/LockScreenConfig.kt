package dev.joseph.hyperbounce.lockscreen

import android.content.Intent
import android.content.SharedPreferences

data class LockScreenSheetConfig(
    var enabled: Boolean = true,
    var enableOptimization: Boolean = true,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = 68.0f,
    var sideRefraction: Float = 32.0f,
    var bottomRefraction: Float = 18.0f,
    var bevelWidth: Float = 38.0f,
    var leftBevelWidth: Float = 38.0f,
    var rightBevelWidth: Float = 38.0f,
    var bottomBevelWidth: Float = 38.0f,
    var dispersionStrength: Float = 0.09f,
    var specularStrength: Float = 0.95f,
    var hairlineStrength: Float = 0.75f,
    var innerGlowStrength: Float = 0.20f,
    var innerShadow: Float = 0.45f,
    var saturationFactor: Float = 125.0f,
    var rimSoftness: Float = 2.0f,
    var blurRadius: Float = 0.0f,
    var cornerRadius: Float = 38.0f,
    var edgeStretch: Float = 28.0f,
    // Home indicator pill
    var pillWidth: Float = 156.0f,
    var pillHeight: Float = 6.0f,
    var pillBottomMargin: Float = 9.0f
)

data class LockScreenKeypadConfig(
    var enabled: Boolean = true,
    // Liquid Glass 2.0 Button Optics
    var buttonRefraction: Float = 48.0f,
    var buttonBevel: Float = 24.0f,
    var buttonDispersion: Float = 0.09f,
    var buttonSpecular: Float = 0.95f,
    var buttonInnerShadow: Float = 0.40f,
    var buttonBlur: Float = 0.0f,
    var buttonSaturation: Float = 125.0f,
    var circleDiameter: Float = 82.0f,
    var digitTextSize: Float = 36.0f,
    var showKlondike: Boolean = false,
    var pressLiquidScale: Float = 1.15f,
    var pressLiquidAmp: Float = 0.60f,
    // PIN Pad Grid Layout & Spacing
    var pinpadLift: Float = 0.0f,
    var row0Offset: Float = -115.0f,
    var rowSpacing: Float = 30.0f,
    var columnSpacing: Float = 0.0f,
    // Legacy fine-tune offsets
    var row1Offset: Float = -90.0f,
    var row2Offset: Float = -60.0f,
    var row3Offset: Float = -30.0f,
    var col1Offset: Float = 0.0f,
    var col3Offset: Float = 0.0f,
    // Per-Key Custom Delays (ms) - Calibrated values from user profile (preserved in backend)
    // Key 1: 0ms, Key 2: 25ms, Key 3: 50ms, Key 4: 55ms, Key 5: 80ms, Key 6: 105ms
    // Key 7: 110ms, Key 8: 135ms, Key 9: 160ms, Key 0: 215ms, Emergency/Cancel: 270ms
    var delayKey1: Float = 0f,
    var delayKey2: Float = 25f,
    var delayKey3: Float = 50f,
    var delayKey4: Float = 55f,
    var delayKey5: Float = 80f,
    var delayKey6: Float = 105f,
    var delayKey7: Float = 110f,
    var delayKey8: Float = 135f,
    var delayKey9: Float = 160f,
    var delayKey0: Float = 215f,
    var delayEmergency: Float = 270f,
    var delayCancel: Float = 270f,
    // Liquid Dynamics & Physics Tuning
    var dragMaxThreshold: Float = 0.16f,
    var dragInitialStiffness: Float = 0.22f,
    var dragResistancePower: Float = 1.8f,
    var dragVolumeStretch: Float = 0.22f,
    var releaseDuration: Float = 320.0f,
    var releaseSquishAmp: Float = 0.35f,
    var releaseOvershoot: Float = 2.0f,
    var iosBloomDuration: Float = 520.0f,
    var iosBloomClusterFactor: Float = 0.70f,
    var iosBloomOvershoot: Float = 0.16f,
    var iosBloomDamping: Float = 0.54f,
    var iosBloomStiffness: Float = 13.5f,
    var iosBloomRadialSquish: Float = 0.28f,
    var iosBloomVelocityStretch: Float = 0.16f,
    var iosBloomWaveStagger: Float = 22.0f,
    var iosBloomDecoupleText: Boolean = true
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
        sheet.enabled = prefs.getBoolean("lock_sheet_enabled", true)
        sheet.enableOptimization = prefs.getBoolean("lock_sheet_enable_optimization", true)
        sheet.refractionHeight = prefs.getFloat("lock_card_refraction_height", 68.0f)
        sheet.sideRefraction = prefs.getFloat("lock_card_side_refraction", 32.0f)
        sheet.bottomRefraction = prefs.getFloat("lock_card_bottom_refraction", 18.0f)
        sheet.bevelWidth = prefs.getFloat("lock_card_bevel_width", 38.0f)
        sheet.leftBevelWidth = prefs.getFloat("lock_card_left_bevel", 38.0f)
        sheet.rightBevelWidth = prefs.getFloat("lock_card_right_bevel", 38.0f)
        sheet.bottomBevelWidth = prefs.getFloat("lock_card_bottom_bevel", 38.0f)
        sheet.dispersionStrength = prefs.getFloat("lock_card_dispersion", 0.09f)
        sheet.specularStrength = prefs.getFloat("lock_card_specular", 0.95f)
        sheet.hairlineStrength = prefs.getFloat("lock_card_hairline", 0.75f)
        sheet.innerGlowStrength = prefs.getFloat("lock_card_inner_glow", 0.20f)
        sheet.innerShadow = prefs.getFloat("lock_card_inner_shadow", 0.45f)
        sheet.saturationFactor = prefs.getFloat("lock_card_saturation", 125.0f)
        sheet.rimSoftness = prefs.getFloat("lock_card_rim_softness", 2.0f)
        sheet.blurRadius = prefs.getFloat("lock_card_blur_radius", 0.0f)
        sheet.cornerRadius = prefs.getFloat("lock_card_corner_radius", 38.0f)
        sheet.edgeStretch = prefs.getFloat("lock_card_edge_stretch", 28.0f)
        sheet.pillWidth = prefs.getFloat("lock_pill_width", 156.0f)
        sheet.pillHeight = prefs.getFloat("lock_pill_height", 6.0f)
        sheet.pillBottomMargin = prefs.getFloat("lock_pill_bottom_margin", 9.0f)

        // Keypad configs (Liquid Glass 2.0 Buttons)
        keypad.enabled = prefs.getBoolean("engine_keypad_enabled", true)
        keypad.buttonRefraction = prefs.getFloat("pin_btn_refraction", 48.0f)
        keypad.buttonBevel = prefs.getFloat("pin_btn_bevel", 24.0f)
        keypad.buttonDispersion = prefs.getFloat("pin_btn_dispersion", 0.09f)
        keypad.buttonSpecular = prefs.getFloat("pin_btn_specular", 0.95f)
        keypad.buttonInnerShadow = prefs.getFloat("pin_btn_inner_shadow", 0.40f)
        keypad.buttonBlur = prefs.getFloat("pin_btn_blur", 0.0f)
        keypad.buttonSaturation = prefs.getFloat("pin_btn_saturation", 125.0f)
        keypad.circleDiameter = prefs.getFloat("pin_circle_diameter", 82.0f)
        keypad.digitTextSize = prefs.getFloat("pin_digit_text_size", 36.0f)
        keypad.showKlondike = prefs.getBoolean("pin_show_klondike", false)
        keypad.pressLiquidScale = prefs.getFloat("pin_press_liquid_scale", 1.15f)
        keypad.pressLiquidAmp = prefs.getFloat("pin_press_liquid_amp", 0.60f)

        keypad.pinpadLift = prefs.getFloat("pin_pad_lift", 0.0f)
        keypad.row0Offset = prefs.getFloat("pin_row0_offset", -115.0f)
        keypad.rowSpacing = prefs.getFloat("pin_row_spacing", 30.0f)
        keypad.columnSpacing = prefs.getFloat("pin_col_spacing", 0.0f)
        keypad.row1Offset = prefs.getFloat("pin_row1_offset", -90.0f)
        keypad.row2Offset = prefs.getFloat("pin_row2_offset", -60.0f)
        keypad.row3Offset = prefs.getFloat("pin_row3_offset", -30.0f)
        keypad.col1Offset = prefs.getFloat("pin_col1_offset", 0.0f)
        keypad.col3Offset = prefs.getFloat("pin_col3_offset", 0.0f)

        // Per-Key Custom Entrance Delays (ms) - Calibrated values from user profile
        keypad.delayKey1 = prefs.getFloat("pin_delay_key1", 0f)
        keypad.delayKey2 = prefs.getFloat("pin_delay_key2", 25f)
        keypad.delayKey3 = prefs.getFloat("pin_delay_key3", 50f)
        keypad.delayKey4 = prefs.getFloat("pin_delay_key4", 55f)
        keypad.delayKey5 = prefs.getFloat("pin_delay_key5", 80f)
        keypad.delayKey6 = prefs.getFloat("pin_delay_key6", 105f)
        keypad.delayKey7 = prefs.getFloat("pin_delay_key7", 110f)
        keypad.delayKey8 = prefs.getFloat("pin_delay_key8", 135f)
        keypad.delayKey9 = prefs.getFloat("pin_delay_key9", 160f)
        keypad.delayKey0 = prefs.getFloat("pin_delay_key0", 215f)
        keypad.delayEmergency = prefs.getFloat("pin_delay_emergency", 270f)
        keypad.delayCancel = prefs.getFloat("pin_delay_cancel", 270f)

        // Liquid Dynamics & Physics Tuning
        keypad.dragMaxThreshold = prefs.getFloat("pin_drag_max_threshold", 0.16f)
        keypad.dragInitialStiffness = prefs.getFloat("pin_drag_initial_stiffness", 0.22f)
        keypad.dragResistancePower = prefs.getFloat("pin_drag_resistance_power", 1.8f)
        keypad.dragVolumeStretch = prefs.getFloat("pin_drag_volume_stretch", 0.22f)
        keypad.releaseDuration = prefs.getFloat("pin_release_duration", 320.0f)
        keypad.releaseSquishAmp = prefs.getFloat("pin_release_squish_amp", 0.35f)
        keypad.releaseOvershoot = prefs.getFloat("pin_release_overshoot", 2.0f)
        keypad.iosBloomDuration = prefs.getFloat("pin_ios_bloom_duration", 520.0f)
        keypad.iosBloomClusterFactor = prefs.getFloat("pin_ios_bloom_cluster_factor", 0.70f)
        keypad.iosBloomOvershoot = prefs.getFloat("pin_ios_bloom_overshoot", 0.16f)
        keypad.iosBloomDamping = prefs.getFloat("pin_ios_bloom_damping", 0.54f)
        keypad.iosBloomStiffness = prefs.getFloat("pin_ios_bloom_stiffness", 13.5f)
        keypad.iosBloomRadialSquish = prefs.getFloat("pin_ios_bloom_radial_squish", 0.28f)
        keypad.iosBloomVelocityStretch = prefs.getFloat("pin_ios_bloom_velocity_stretch", 0.16f)
        keypad.iosBloomWaveStagger = prefs.getFloat("pin_ios_bloom_wave_stagger", 22.0f)
        keypad.iosBloomDecoupleText = prefs.getBoolean("pin_ios_bloom_decouple_text", true)
    }

    fun updateKey(key: String, intent: Intent) {
        when (key) {
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
