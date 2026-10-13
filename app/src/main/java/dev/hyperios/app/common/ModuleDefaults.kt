package dev.hyperios.app.common

/**
 * Unified Single Source of Truth for all default configuration values,
 * dynamic physics curves (row Z, bounce, settle), and UI slider ranges.
 *
 * All hooks, configuration holders, repository delegates, models, and Compose
 * UI screens reference this object exclusively to prevent discrepancies.
 */
object ModuleDefaults {

    object MasterToggles {
        const val MASTER_HOOKS_ENABLED = false
        const val ENGINE_KEYPAD_ENABLED = false
        const val CALLER_SCREEN_ENABLED = false
        const val DIALER_RECENTS_SWIPE_ENABLED = false
        const val LOCK_SHEET_ENABLED = false
        const val NOTIF_MASTER_ENABLED = false
        const val NOTIF_SHEET_ENABLED = false
        const val UNLOCK_ENABLED = false
        const val IOS_ANIM_ENABLED = false
        const val CC_MASTER_ENABLED = false
        const val APP_INSTAGRAM_ENABLED = false
        const val APP_WHATSAPP_ENABLED = false
        const val APP_YOUTUBE_ENABLED = false
        const val MIUIX_APPS_HOOKING_ENABLED = false
    }

    object ScreenUnlock {
        const val MODE: Int = 1
        const val GRID_COLUMNS: Int = 4
        val GRID_COLUMNS_RANGE = 2.0f..8.0f

        const val GRID_ROWS: Int = 6
        val GRID_ROWS_RANGE = 3.0f..12.0f

        // Mode 1: Kinetic Y-Axis Cascade
        const val VELOCITY_INFLUENCE: Float = 1.00f
        val VELOCITY_INFLUENCE_RANGE = 0.0f..5.0f

        const val MAX_VELOCITY: Float = 9000.0f
        val MAX_VELOCITY_RANGE = 1000.0f..25000.0f

        const val CASCADE_DAMPING: Float = 0.86f
        val CASCADE_DAMPING_RANGE = 0.3f..1.5f

        const val DURATION_SCALE: Float = 1.00f
        val DURATION_SCALE_RANGE = 0.1f..3.0f

        const val KINETIC_DOCK_DAMPING: Float = 1.00f
        val KINETIC_DOCK_DAMPING_RANGE = 0.1f..1.5f

        const val KINETIC_DOCK_RESPONSE: Float = 0.55f
        val KINETIC_DOCK_RESPONSE_RANGE = 0.1f..1.5f

        const val KINETIC_DOCK_Z_DEPTH: Float = 800.0f
        val KINETIC_DOCK_Z_DEPTH_RANGE = 0.0f..2000.0f

        // Mode 0: Classic Radial Unlock
        const val DOCK_DAMPING: Float = 0.70f
        val DOCK_DAMPING_RANGE = 0.1f..1.5f

        const val DOCK_RESPONSE: Float = 0.40f
        val DOCK_RESPONSE_RANGE = 0.1f..1.5f

        const val CENTER_RESPONSE: Float = 0.48f
        val CENTER_RESPONSE_RANGE = 0.1f..1.0f

        const val CENTER_DAMPING: Float = 0.58f
        val CENTER_DAMPING_RANGE = 0.1f..1.5f

        const val EDGE_RESPONSE_DELAY: Float = 0.22f
        val EDGE_RESPONSE_DELAY_RANGE = 0.0f..1.0f

        const val EDGE_DAMPING_DROP: Float = 0.10f
        val EDGE_DAMPING_DROP_RANGE = 0.0f..0.5f

        const val Z_DEPTH_MULTIPLIER: Float = 1.60f
        val Z_DEPTH_MULTIPLIER_RANGE = 0.5f..4.0f

        const val MIN_RESPONSE: Float = 0.45f
        val MIN_RESPONSE_RANGE = 0.1f..1.0f

        const val MAX_RESPONSE: Float = 0.75f
        val MAX_RESPONSE_RANGE = 0.2f..1.5f

        const val MIN_DAMPING: Float = 0.42f
        val MIN_DAMPING_RANGE = 0.2f..1.0f

        const val MAX_DAMPING: Float = 0.65f
        val MAX_DAMPING_RANGE = 0.3f..1.5f


        // Dynamic Row Ranges
        val ROW_Z_RANGE = 0.0f..4000.0f
        val ROW_BOUNCE_RANGE = 0.0f..1.0f
        val ROW_SETTLE_RANGE = 0.1f..2.0f

        fun rowZ(row: Int, totalRows: Int = 6): Float {
            return when (row) {
                0 -> 650.0f
                1 -> 600.0f
                else -> 600.0f
            }
        }

        fun rowBounce(row: Int, totalRows: Int = 6, isOuter: Boolean = false): Float {
            return when (row) {
                0 -> if (isOuter) 0.23f else 0.21f
                1 -> if (isOuter) 0.24f else 0.24f
                2 -> if (isOuter) 0.11f else 0.05f
                3 -> if (isOuter) 0.10f else 0.02f
                4 -> if (isOuter) 0.13f else 0.02f
                5 -> if (isOuter) 0.10f else 0.05f
                6 -> if (isOuter) 0.08f else 0.02f
                else -> 0.02f
            }
        }

        fun rowSettle(row: Int, totalRows: Int = 6, isOuter: Boolean = false): Float {
            return when (row) {
                0 -> if (isOuter) 1.00f else 0.70f
                1 -> if (isOuter) 0.71f else 0.65f
                2 -> if (isOuter) 0.56f else 0.43f
                3 -> if (isOuter) 0.60f else 0.44f
                4 -> if (isOuter) 0.70f else 0.56f
                5 -> if (isOuter) 0.80f else 0.66f
                6 -> if (isOuter) 0.63f else 0.58f
                else -> if (isOuter) 0.65f else 0.55f
            }
        }
    }

    object LockScreenKeypad {
        // Button Optics
        const val BTN_REFRACTION: Float = 48.0f
        val BTN_REFRACTION_RANGE = 0.0f..120.0f

        const val BTN_BEVEL: Float = 24.0f
        val BTN_BEVEL_RANGE = 0.0f..60.0f

        const val BTN_DISPERSION: Float = 0.09f
        val BTN_DISPERSION_RANGE = 0.0f..0.5f

        const val BTN_SPECULAR: Float = 0.0f
        val BTN_SPECULAR_RANGE = 0.0f..2.0f

        const val BTN_INNER_SHADOW: Float = 1.0f
        val BTN_INNER_SHADOW_RANGE = 0.0f..1.0f

        const val BTN_BLUR: Float = 0.0f
        val BTN_BLUR_RANGE = 0.0f..50.0f

        const val BTN_SATURATION: Float = 150.0f
        val BTN_SATURATION_RANGE = 50.0f..250.0f

        // Button Geometry
        const val CIRCLE_DIAMETER: Float = 100.0f
        val CIRCLE_DIAMETER_RANGE = 60.0f..120.0f

        const val DIGIT_TEXT_SIZE: Float = 40.0f
        val DIGIT_TEXT_SIZE_RANGE = 24.0f..56.0f

        const val SHOW_KLONDIKE: Boolean = false

        const val PRESS_LIQUID_SCALE: Float = 1.15f
        val PRESS_LIQUID_SCALE_RANGE = 0.9f..1.5f

        const val PRESS_LIQUID_AMP: Float = 0.60f
        val PRESS_LIQUID_AMP_RANGE = 0.0f..1.5f

        // PIN Pad Grid Layout
        const val PAD_LIFT: Float = 65.0f
        val PAD_LIFT_RANGE = 0.0f..150.0f

        const val ROW0_OFFSET: Float = -155.0f
        val ROW0_OFFSET_RANGE = -250.0f..50.0f

        const val ROW_SPACING: Float = 40.0f
        val ROW_SPACING_RANGE = 0.0f..80.0f

        const val COL_SPACING: Float = 3.3f
        val COL_SPACING_RANGE = 0.0f..40.0f

        const val ROW1_OFFSET: Float = -150.0f
        const val ROW2_OFFSET: Float = -100.0f
        const val ROW3_OFFSET: Float = -50.0f
        val ROW_OFFSET_RANGE = -250.0f..50.0f

        const val COL1_OFFSET: Float = 15.0f
        const val COL3_OFFSET: Float = 15.0f
        val COL_OFFSET_RANGE = -50.0f..100.0f

        // Key Delays
        const val DELAY_KEY1: Float = 0f
        const val DELAY_KEY2: Float = 25f
        const val DELAY_KEY3: Float = 50f
        const val DELAY_KEY4: Float = 55f
        const val DELAY_KEY5: Float = 80f
        const val DELAY_KEY6: Float = 105f
        const val DELAY_KEY7: Float = 110f
        const val DELAY_KEY8: Float = 135f
        const val DELAY_KEY9: Float = 160f
        const val DELAY_KEY0: Float = 215.0f
        const val DELAY_EMERGENCY: Float = 270.0f
        const val DELAY_CANCEL: Float = 270.0f
        val DELAY_RANGE = 0.0f..500.0f

        // Physics & Drag
        const val DRAG_MAX_THRESHOLD: Float = 0.16f
        val DRAG_MAX_THRESHOLD_RANGE = 0.05f..0.50f

        const val DRAG_INITIAL_STIFFNESS: Float = 0.22f
        val DRAG_INITIAL_STIFFNESS_RANGE = 0.05f..1.00f

        const val DRAG_RESISTANCE_POWER: Float = 1.8f
        val DRAG_RESISTANCE_POWER_RANGE = 0.5f..4.0f

        const val DRAG_VOLUME_STRETCH: Float = 0.11f
        val DRAG_VOLUME_STRETCH_RANGE = 0.0f..0.5f

        // Release
        const val RELEASE_DURATION: Float = 600.0f
        val RELEASE_DURATION_RANGE = 100.0f..1500.0f

        const val RELEASE_SQUISH_AMP: Float = 0.35f
        val RELEASE_SQUISH_AMP_RANGE = 0.0f..1.0f

        const val RELEASE_OVERSHOOT: Float = 2.0f
        val RELEASE_OVERSHOOT_RANGE = 0.5f..5.0f

        // Bloom
        const val BLOOM_DURATION: Float = 900.0f
        val BLOOM_DURATION_RANGE = 200.0f..2000.0f

        const val BLOOM_CLUSTER_FACTOR: Float = 0.60f
        val BLOOM_CLUSTER_FACTOR_RANGE = 0.0f..1.0f

        const val BLOOM_OVERSHOOT: Float = 0.16f
        val BLOOM_OVERSHOOT_RANGE = 0.0f..1.0f

        const val BLOOM_DAMPING: Float = 0.40f
        val BLOOM_DAMPING_RANGE = 0.1f..1.0f

        const val BLOOM_STIFFNESS: Float = 18.0f
        val BLOOM_STIFFNESS_RANGE = 5.0f..50.0f

        const val BLOOM_RADIAL_SQUISH: Float = 0.28f
        val BLOOM_RADIAL_SQUISH_RANGE = 0.0f..1.0f

        const val BLOOM_VELOCITY_STRETCH: Float = 0.16f
        val BLOOM_VELOCITY_STRETCH_RANGE = 0.0f..1.0f

        const val BLOOM_WAVE_STAGGER: Float = 22.0f
        val BLOOM_WAVE_STAGGER_RANGE = 0.0f..100.0f

        const val BLOOM_DECOUPLE_TEXT: Boolean = false
    }

    object LockScreenSheet {
        const val REFRACTION_HEIGHT: Float = 77.0f
        val REFRACTION_HEIGHT_RANGE = 0.0f..120.0f

        const val SIDE_REFRACTION: Float = 80.0f
        val SIDE_REFRACTION_RANGE = 0.0f..120.0f

        const val BOTTOM_REFRACTION: Float = 80.0f
        val BOTTOM_REFRACTION_RANGE = 0.0f..120.0f

        const val LEFT_BEVEL_WIDTH: Float = 25.0f
        val LEFT_BEVEL_RANGE = 0.0f..120.0f

        const val RIGHT_BEVEL_WIDTH: Float = 25.0f
        val RIGHT_BEVEL_RANGE = 0.0f..120.0f

        const val BOTTOM_BEVEL_WIDTH: Float = 30.0f
        val BOTTOM_BEVEL_RANGE = 0.0f..120.0f

        const val DISPERSION_STRENGTH: Float = 1.0f
        val DISPERSION_RANGE = 0.0f..2.0f

        const val SPECULAR_STRENGTH: Float = 0.0f
        val SPECULAR_RANGE = 0.0f..2.0f

        const val HAIRLINE_STRENGTH: Float = 2.0f
        val HAIRLINE_RANGE = 0.0f..5.0f

        const val INNER_GLOW_STRENGTH: Float = 0.20f
        val INNER_GLOW_RANGE = 0.0f..1.0f

        const val INNER_SHADOW: Float = 0.45f
        val INNER_SHADOW_RANGE = 0.0f..1.0f

        const val SATURATION_FACTOR: Float = 125.0f
        val SATURATION_RANGE = 50.0f..250.0f

        const val RIM_SOFTNESS: Float = 2.0f
        val RIM_SOFTNESS_RANGE = 0.0f..10.0f

        const val BLUR_RADIUS: Float = 0.0f
        val BLUR_RADIUS_RANGE = 0.0f..50.0f

        const val CORNER_RADIUS: Float = 38.0f
        val CORNER_RADIUS_RANGE = 0.0f..80.0f

        const val EDGE_STRETCH: Float = 28.0f
        val EDGE_STRETCH_RANGE = 0.0f..60.0f

        const val PILL_ENABLED: Boolean = true
        const val PILL_WIDTH: Float = 156.0f
        val PILL_WIDTH_RANGE = 50.0f..300.0f

        const val PILL_HEIGHT: Float = 6.0f
        val PILL_HEIGHT_RANGE = 2.0f..20.0f

        const val PILL_BOTTOM_MARGIN: Float = 9.0f
        val PILL_BOTTOM_MARGIN_RANGE = 0.0f..50.0f
    }

    object NotificationSheet {
        const val LOCK_WALLPAPER_ENABLED: Boolean = true
        const val CUSTOM_WALLPAPER_ENABLED: Boolean = false
        const val LOCK_WALLPAPER_HOMESCREEN_ONLY: Boolean = false
        const val DEPTH_WALLPAPER_ENABLED: Boolean = false
        const val DEPTH_CUSTOM_SUBJECT: Boolean = true
        const val ONE_TO_ONE_TRACKING_ENABLED: Boolean = false
        const val LOCK_SCREEN_AS_NOTIF_CENTER_ENABLED: Boolean = true
        const val SHOW_BOTTOM_WIDGETS: Boolean = false
        const val GLASS_EFFECTS_ENABLED: Boolean = true

        const val REFRACTION_HEIGHT: Float = 15.0f
        val REFRACTION_HEIGHT_RANGE = 0.0f..80.0f

        const val SIDE_REFRACTION: Float = 20.0f
        val SIDE_REFRACTION_RANGE = 0.0f..120.0f

        const val BOTTOM_REFRACTION: Float = 18.0f
        val BOTTOM_REFRACTION_RANGE = 0.0f..80.0f

        const val LEFT_BEVEL_WIDTH: Float = 32.0f
        val LEFT_BEVEL_RANGE = 0.0f..80.0f

        const val RIGHT_BEVEL_WIDTH: Float = 32.0f
        val RIGHT_BEVEL_RANGE = 0.0f..80.0f

        const val BOTTOM_BEVEL_WIDTH: Float = 45.0f
        val BOTTOM_BEVEL_RANGE = 0.0f..80.0f

        const val DISPERSION_STRENGTH: Float = 0.03f
        val DISPERSION_RANGE = 0.0f..1.0f

        const val SPECULAR_STRENGTH: Float = 0.90f
        val SPECULAR_RANGE = 0.0f..2.0f

        const val HAIRLINE_STRENGTH: Float = 0.75f
        val HAIRLINE_RANGE = 0.0f..5.0f

        const val INNER_GLOW_STRENGTH: Float = 0.20f
        val INNER_GLOW_RANGE = 0.0f..1.0f

        const val RIM_SOFTNESS: Float = 2.0f
        val RIM_SOFTNESS_RANGE = 0.0f..10.0f

        const val INNER_SHADOW: Float = 0.40f
        val INNER_SHADOW_RANGE = 0.0f..1.0f

        const val SATURATION_FACTOR: Float = 120.0f
        val SATURATION_RANGE = 50.0f..200.0f

        const val BLUR_RADIUS: Float = 0.0f
        val BLUR_RADIUS_RANGE = 0.0f..60.0f

        const val CORNER_RADIUS: Float = 36.0f
        val CORNER_RADIUS_RANGE = 0.0f..64.0f

        const val SIDE_MARGIN: Float = 9.7f
        val SIDE_MARGIN_RANGE = 0.0f..50.0f

        const val PILL_WIDTH: Float = 156.0f
        val PILL_WIDTH_RANGE = 50.0f..300.0f

        const val PILL_HEIGHT: Float = 6.0f
        val PILL_HEIGHT_RANGE = 2.0f..20.0f

        const val PILL_BOTTOM_MARGIN: Float = 9.0f
        val PILL_BOTTOM_MARGIN_RANGE = 0.0f..50.0f

        const val IOS_HUN_ANIMATION_ENABLED: Boolean = true

        const val IOS_HUN_DURATION_MS: Float = 600.0f
        val IOS_HUN_DURATION_RANGE = 150.0f..1500.0f

        const val IOS_HUN_START_SCALE: Float = 0.20f
        val IOS_HUN_START_SCALE_RANGE = 0.05f..1.0f

        const val IOS_HUN_SQUEEZE_STRENGTH: Float = 1.50f
        val IOS_HUN_SQUEEZE_RANGE = 0.0f..4.0f

        const val NOTIF_START_MARGIN_TOP: Float = 250.0f
        val NOTIF_START_MARGIN_TOP_RANGE = 0.0f..500.0f

        const val NOTIF_CLOCK_SCALE: Float = 0.60f
        val NOTIF_CLOCK_SCALE_RANGE = 0.3f..1.2f

        const val NOTIF_STACK_ENABLED: Boolean = true
        const val LAUNCHER_UNLOCK_ON_DISMISS: Boolean = true
    }

    object ControlCenter {
        const val ANIM_ENGINE_MODE: Int = 1
        const val MODULAR_EDIT_ENABLED: Boolean = true
        const val ANIM_ENABLED: Boolean = false
        const val JELLY_ENABLED: Boolean = true

        const val SPREAD_RATIO: Float = 1.00f
        val SPREAD_RATIO_RANGE = 0.20f..2.50f

        const val STRETCH_THRESHOLD: Float = 0.38f
        val STRETCH_THRESHOLD_RANGE = 0.10f..1.00f

        const val SPREAD_RESPONSE: Float = 0.20f
        val SPREAD_RESPONSE_RANGE = 0.05f..1.0f

        const val SPREAD_DAMPING: Float = 0.10f
        val SPREAD_DAMPING_RANGE = 0.05f..1.5f

        const val SPREAD_RELEASE_RESPONSE: Float = 0.88f
        val SPREAD_RELEASE_RESPONSE_RANGE = 0.40f..1.50f

        const val SPREAD_RELEASE_DAMPING: Float = 0.38f
        val SPREAD_RELEASE_DAMPING_RANGE = 0.20f..0.80f

        const val TILE_DELAY_SCALE: Float = 0.80f
        val TILE_DELAY_SCALE_RANGE = 0.00f..2.00f

        const val FRICTION_MULTIPLIER: Float = 0.40f
        val FRICTION_MULTIPLIER_RANGE = 0.1f..1.0f

        const val LOW_END_FLASHLIGHT_ENABLED: Boolean = true
        const val FLASHLIGHT_MAX_BRIGHTNESS: Int = 500
        val FLASHLIGHT_MAX_BRIGHTNESS_RANGE = 100.0f..1000.0f

        const val TRUE_2D_GRID_ENABLED: Boolean = true
        const val FLUID_RESIZE_ENABLED: Boolean = true
        const val LANDSCAPE_RIGHT_MAX_ROWS: Int = 3
        val LANDSCAPE_RIGHT_MAX_ROWS_RANGE = 1.0f..6.0f
        const val PENDING_RESET_LAYOUT: Boolean = false
    }

    object IOSTransitions {
        const val OPEN_SPRING_DAMPING: Float = 1.0f
        val OPEN_SPRING_DAMPING_RANGE = 0.3f..1.5f

        const val CLOSE_SPRING_DAMPING: Float = 0.8f
        val CLOSE_SPRING_DAMPING_RANGE = 0.3f..1.5f

        const val ARC_APEX_LIFT: Float = 1.0f
        val ARC_APEX_LIFT_RANGE = 0.5f..2.0f

        const val OPEN_SURGE_LIFT: Float = 1.0f
        val OPEN_SURGE_LIFT_RANGE = 0.5f..2.0f

        const val ANIM_SPEED: Float = 1.0f
        val ANIM_SPEED_RANGE = 0.5f..2.5f

        const val FOLDER_TRACKING: Boolean = false
        const val RECENTS_CENTERING: Boolean = false
        const val ZERO_FRICTION_DRAG: Boolean = false
        const val OPEN_UPLIFT_SURGE: Boolean = true
        const val CLOSE_UPLIFT_SURGE: Boolean = true
        const val KEYSTONE_SQUEEZE: Boolean = true
        const val LANDSCAPE_GAME_OPT: Boolean = true
        const val REFINED_HAPTICS: Boolean = true
        const val STACK_RECENTS_HAPTICS: Boolean = true
    }

    object CallerScreen {
        const val BUTTON_SIZE: Float = 85.4f
        val BUTTON_SIZE_RANGE = 60.0f..120.0f

        const val BOTTOM_OFFSET: Float = 55.0f
        val BOTTOM_OFFSET_RANGE = 0.0f..150.0f

        const val DANCE_ENABLED: Boolean = true
    }

    object DialerRecents {
        const val CALL_THRESHOLD: Float = 96.0f
        val CALL_THRESHOLD_RANGE = 50.0f..200.0f

        const val DELETE_WIDTH: Float = 84.0f
        val DELETE_WIDTH_RANGE = 50.0f..150.0f

        const val MORPH_DURATION: Float = 480.0f
        val MORPH_DURATION_RANGE = 150.0f..1000.0f

        const val CONFIRM_DELETE: Boolean = true
    }

    object NavApps {
        const val GLASS_INTENSITY: Float = 1.0f
        val GLASS_INTENSITY_RANGE = 0.0f..2.0f

        const val BAR_BLUR_INTENSITY: Float = 1.0f
        val BAR_BLUR_INTENSITY_RANGE = 0.0f..2.0f

        const val CONTAINER_OPACITY: Float = 0.40f
        val CONTAINER_OPACITY_RANGE = 0.0f..1.0f

        const val PILL_CONTAINER_OPACITY: Float = 1.0f
        val PILL_CONTAINER_OPACITY_RANGE = 0.0f..1.0f

        const val PILL_GLASS_INTENSITY: Float = 1.0f
        val PILL_GLASS_INTENSITY_RANGE = 0.0f..2.0f

        val BAR_HEIGHT_RANGE = 40.0f..100.0f
        val TAB_WIDTH_RANGE = 40.0f..120.0f
        val CORNER_RADIUS_RANGE = 0.0f..50.0f
        val BOTTOM_MARGIN_RANGE = 0.0f..80.0f
        val AUTO_SHRINK_SCALE_RANGE = 0.50f..1.00f

        object Instagram {
            const val BAR_HEIGHT: Float = 50.0f

            const val TAB_WIDTH: Float = 74.6f
            val TAB_WIDTH_RANGE = 40.0f..120.0f

            const val CORNER_RADIUS: Float = 32.0f
            val CORNER_RADIUS_RANGE = 0.0f..50.0f

            const val BOTTOM_MARGIN: Float = 20.0f
            val BOTTOM_MARGIN_RANGE = 0.0f..80.0f

            const val CONTAINER_OPACITY: Float = 0.40f
            val CONTAINER_OPACITY_RANGE = 0.0f..1.0f

            const val PILL_CONTAINER_OPACITY: Float = 1.0f
            val PILL_CONTAINER_OPACITY_RANGE = 0.0f..1.0f

            const val PILL_BLUR_INTENSITY: Float = 0.0f
            val PILL_BLUR_INTENSITY_RANGE = 0.0f..30.0f

            const val AUTO_SHRINK: Boolean = false
            const val AUTO_SHRINK_SCALE: Float = 0.85f

            const val REELS_FULLSCREEN_ZOOM: Boolean = true
            const val CUSTOM_ACCENT_COLOR: Long = 4278196740L
        }

        object WhatsApp {
            const val BAR_HEIGHT: Float = 70.0f

            const val TAB_WIDTH: Float = 92.0f
            val TAB_WIDTH_RANGE = 40.0f..120.0f

            const val CORNER_RADIUS: Float = 36.0f
            val CORNER_RADIUS_RANGE = 0.0f..50.0f

            const val BOTTOM_MARGIN: Float = 40.0f
            val BOTTOM_MARGIN_RANGE = 0.0f..80.0f

            const val PILL_GLASS_INTENSITY: Float = 1.0f
            val PILL_GLASS_INTENSITY_RANGE = 0.0f..2.0f

            const val AUTO_SHRINK: Boolean = false
            const val AUTO_SHRINK_SCALE: Float = 0.70f
            const val LIQUID_GLASS: Boolean = true
            const val ACCENT: String = "Green"
            const val THEME_MODE: String = "System"
        }

        object YouTube {
            const val BAR_HEIGHT: Float = 50.0f

            const val TAB_WIDTH: Float = 74.7f
            val TAB_WIDTH_RANGE = 40.0f..120.0f

            const val CORNER_RADIUS: Float = 32.0f
            val CORNER_RADIUS_RANGE = 0.0f..50.0f

            const val BOTTOM_MARGIN: Float = 20.0f
            val BOTTOM_MARGIN_RANGE = 0.0f..80.0f

            const val CONTAINER_OPACITY: Float = 0.40f
            val CONTAINER_OPACITY_RANGE = 0.0f..1.0f

            const val AUTO_SHRINK: Boolean = true
            const val AUTO_SHRINK_SCALE: Float = 0.90f
            val AUTO_SHRINK_SCALE_RANGE = 0.50f..1.00f

            const val SAFE_MODE_ENABLE: Boolean = false
        }
    }

    object Haptics {
        const val ACTIVE_PIPELINE: String = "XiaomiMelodicCrisp"
        const val ACTIVE_FREQ: Float = 160.0f
        val ACTIVE_FREQ_RANGE = 50.0f..300.0f

        const val AUDIO_SENSITIVITY: Float = 0.3f
        val AUDIO_SENSITIVITY_RANGE = 0.05f..1.0f

        const val AUDIO_REFRACTORY_MS: Long = 80L
        val AUDIO_REFRACTORY_MS_RANGE = 20.0f..300.0f
    }

    object MorphPhysics {
        const val MORPH_SPEED_MULTIPLIER: Float = 0.78f
        val MORPH_SPEED_RANGE = 0.10f..2.00f

        const val CIRCLE_COLLAPSE_RATIO: Float = 0.35f
        val CIRCLE_COLLAPSE_RANGE = 0.15f..0.60f

        const val CIRCLE_DIAMETER_DP: Float = 44.0f
        val CIRCLE_DIAMETER_RANGE = 20.0f..100.0f

        const val Z_PLUNGE_DEPTH: Float = 0.50f
        val Z_PLUNGE_RANGE = 0.0f..1.0f

        const val GRAVITY_SWOOP_DP: Float = 60.0f
        val GRAVITY_SWOOP_RANGE = 0.0f..150.0f

        const val Y_BOUNCE_AMOUNT_DP: Float = 0.0f
        val Y_BOUNCE_RANGE = 0.0f..50.0f

        const val JELLY_OVERSHOOT: Float = 0.0f
        val JELLY_OVERSHOOT_RANGE = 0.0f..0.15f

        const val CONTENT_STRETCH: Float = 0.0f
        val CONTENT_STRETCH_RANGE = 0.0f..0.15f

        const val SPRING_DAMPING: Float = 0.70f
        val SPRING_DAMPING_RANGE = 0.40f..1.00f

        const val SPRING_STIFFNESS: Float = 220.0f
        val SPRING_STIFFNESS_RANGE = 20.0f..600.0f

        const val LENS_SURGE_DP: Float = 20.0f
        val LENS_SURGE_RANGE = 0.0f..60.0f

        const val MOTION_BLUR_SURGE_DP: Float = 30.0f
        val MOTION_BLUR_RANGE = 0.0f..80.0f
    }

    object GlobalGlass {
        const val ENABLED: Boolean = true

        const val BLUR_DP: Float = 6.0f
        val BLUR_RANGE = 0.0f..30.0f

        const val REFRACTION_HEIGHT_DP: Float = 10.0f
        val REFRACTION_HEIGHT_RANGE = 0.0f..40.0f

        const val REFRACTION_AMOUNT_DP: Float = 16.0f
        val REFRACTION_AMOUNT_RANGE = 0.0f..40.0f

        const val CHROMATIC_ABERRATION: Boolean = false
        const val HAPTICS_ENABLED: Boolean = true
    }

    object Engine {
        const val COMPONENT_STYLE: String = "MIUIX"
        const val APP_ICON_STYLE: String = "Flat"
        const val TOGGLE_ENGINE: String = "LiquidGlass"
        const val SLIDER_ENGINE: String = "LiquidGlass"
        const val TOGGLE_ACCENT: String = "Green"
        const val SLIDER_ACCENT: String = "Blue"
        const val TOGGLE_CUSTOM_COLOR: Long = 0xFF30D158L
        const val SLIDER_CUSTOM_COLOR: Long = 0xFF30D158L

        // Toggle Glass Feel
        const val TOGGLE_GLASS_BLUR: Float = 8.0f
        val TOGGLE_GLASS_BLUR_RANGE = 0.0f..30.0f

        const val TOGGLE_GLASS_LENS_SIZE: Float = 5.0f
        val TOGGLE_GLASS_LENS_SIZE_RANGE = 0.0f..20.0f

        const val TOGGLE_GLASS_LENS_DEPTH: Float = 10.0f
        val TOGGLE_GLASS_LENS_DEPTH_RANGE = 0.0f..30.0f

        const val TOGGLE_GLASS_HIGHLIGHT: Float = 0.67f
        val TOGGLE_GLASS_HIGHLIGHT_RANGE = 0.0f..1.0f

        const val TOGGLE_GLASS_SHADOW: Float = 4.0f
        val TOGGLE_GLASS_SHADOW_RANGE = 0.0f..20.0f

        const val TOGGLE_GLASS_DRAG_SENSITIVITY: Float = 0.72f
        val TOGGLE_GLASS_DRAG_SENSITIVITY_RANGE = 0.1f..2.0f

        const val TOGGLE_GLASS_SPRING_DAMPING: Float = 0.82f
        val TOGGLE_GLASS_SPRING_DAMPING_RANGE = 0.2f..1.5f

        const val TOGGLE_GLASS_SPRING_STIFFNESS: Float = 400.0f
        val TOGGLE_GLASS_SPRING_STIFFNESS_RANGE = 50.0f..1500.0f

        // Slider Glass Feel
        const val SLIDER_GLASS_BLUR: Float = 8.0f
        val SLIDER_GLASS_BLUR_RANGE = 0.0f..30.0f

        const val SLIDER_GLASS_LENS_SIZE: Float = 10.0f
        val SLIDER_GLASS_LENS_SIZE_RANGE = 0.0f..30.0f

        const val SLIDER_GLASS_LENS_DEPTH: Float = 14.0f
        val SLIDER_GLASS_LENS_DEPTH_RANGE = 0.0f..40.0f

        const val SLIDER_GLASS_HIGHLIGHT: Float = 0.67f
        val SLIDER_GLASS_HIGHLIGHT_RANGE = 0.0f..1.0f

        const val SLIDER_GLASS_SHADOW: Float = 4.0f
        val SLIDER_GLASS_SHADOW_RANGE = 0.0f..20.0f

        const val SLIDER_GLASS_DRAG_SENSITIVITY: Float = 1.0f
        val SLIDER_GLASS_DRAG_SENSITIVITY_RANGE = 0.1f..2.0f

        const val SLIDER_GLASS_SPRING_DAMPING: Float = 0.70f
        val SLIDER_GLASS_SPRING_DAMPING_RANGE = 0.2f..1.5f

        const val SLIDER_GLASS_SPRING_STIFFNESS: Float = 800.0f
        val SLIDER_GLASS_SPRING_STIFFNESS_RANGE = 50.0f..2000.0f
    }
}
