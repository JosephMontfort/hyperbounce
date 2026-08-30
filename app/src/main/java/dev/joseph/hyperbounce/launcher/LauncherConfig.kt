package dev.joseph.hyperbounce.launcher

import android.content.SharedPreferences

data class ScreenUnlockConfig(
    val enabled: Boolean = true,
    val animMode: Int = 1, // 0 = iOS Radial Dispersion, 1 = Kinetic Y-Axis Cascade
    val gridColumns: Int = 4,
    val gridRows: Int = 7,

    // Mode 0: Classic Radial Unlock
    val dockDamping: Float = 0.70f,
    val dockResponse: Float = 0.40f,
    val centerResponse: Float = 0.48f,
    val centerDamping: Float = 0.58f,
    val edgeResponseDelay: Float = 0.22f,
    val edgeDampingDrop: Float = 0.10f,
    val zDepthMultiplier: Float = 1.60f,
    val minResponse: Float = 0.45f,
    val maxResponse: Float = 0.75f,
    val minDamping: Float = 0.42f,
    val maxDamping: Float = 0.65f,

    // Mode 1: Kinetic Y-Axis Cascade
    val durationScale: Float = 1.0f,
    val velocityInfluence: Float = 1.0f,
    val maxVelocity: Float = 16000.0f,
    val cascadeDamping: Float = 0.95f,
    val kineticDockDamping: Float = 0.85f,
    val kineticDockResponse: Float = 0.38f,
    val kineticDockZDepth: Float = 800.0f,

    // Row-by-row dynamics (r0_z..r6_z, r0_center_bounce..r6_center_bounce, etc.)
    val dyn: Map<String, Float> = emptyMap()
) {
    fun getDyn(key: String, defVal: Float): Float = dyn[key] ?: defVal
}

data class AppTransitionConfig(
    val enabled: Boolean = true,

    // Spring Channel Remapping
    val posDamping: Float = 0.80f,
    val posResponse: Float = 0.40f,
    val scaleDamping: Float = 0.80f,
    val scaleResponse: Float = 0.40f,
    val cornerDamping: Float = 1.00f,
    val cornerResponse: Float = 0.35f,
    val alphaDamping: Float = 1.00f,
    val alphaResponse: Float = 0.30f,

    // Spatial Trajectory & 3D Depth
    val appYArc: Float = 0.0f,
    val appBottomZWarp: Float = 0.0f,
    val appXSway: Float = 0.0f,
    val appInterruptSmooth: Float = 1.0f,
    val appIconPitchDeg: Float = 0.0f,
    val appIconCameraDist: Float = 8000.0f
)

object LauncherConfigHolder {
    @Volatile
    var unlockConfig: ScreenUnlockConfig = ScreenUnlockConfig()
        private set

    @Volatile
    var transitionConfig: AppTransitionConfig = AppTransitionConfig()
        private set

    fun update(prefs: SharedPreferences) {
        val rows = prefs.getInt("unlock_grid_rows", 7).coerceIn(1, 32)
        val cols = prefs.getInt("unlock_grid_columns", 4).coerceIn(1, 32)

        val dynMap = mutableMapOf<String, Float>()
        for (i in 0 until rows) {
            val defZ = (2000.0f - (i * 200.0f)).coerceAtLeast(100.0f)
            val defBounce = (0.45f - (i * 0.05f)).coerceAtLeast(0.0f)
            val defSettle = 0.55f + (i * 0.04f)

            dynMap["r${i}_z"] = prefs.getFloat("unlock_r${i}_z", defZ)
            dynMap["r${i}_center_bounce"] = prefs.getFloat("unlock_r${i}_center_bounce", defBounce)
            dynMap["r${i}_outer_bounce"] = prefs.getFloat("unlock_r${i}_outer_bounce", defBounce)
            dynMap["r${i}_center_settle"] = prefs.getFloat("unlock_r${i}_center_settle", defSettle)
            dynMap["r${i}_outer_settle"] = prefs.getFloat("unlock_r${i}_outer_settle", defSettle)
        }

        unlockConfig = ScreenUnlockConfig(
            enabled = prefs.getBoolean("unlock_enabled", true),
            animMode = prefs.getInt("unlock_anim_mode", 1),
            gridColumns = cols,
            gridRows = rows,
            dockDamping = prefs.getFloat("unlock_dock_damping", 0.70f),
            dockResponse = prefs.getFloat("unlock_dock_response", 0.40f),
            centerResponse = prefs.getFloat("unlock_center_response", 0.48f),
            centerDamping = prefs.getFloat("unlock_center_damping", 0.58f),
            edgeResponseDelay = prefs.getFloat("unlock_edge_response_delay", 0.22f),
            edgeDampingDrop = prefs.getFloat("unlock_edge_damping_drop", 0.10f),
            zDepthMultiplier = prefs.getFloat("unlock_z_depth_multiplier", 1.60f),
            minResponse = prefs.getFloat("unlock_min_response", 0.45f),
            maxResponse = prefs.getFloat("unlock_max_response", 0.75f),
            minDamping = prefs.getFloat("unlock_min_damping", 0.42f),
            maxDamping = prefs.getFloat("unlock_max_damping", 0.65f),
            durationScale = prefs.getFloat("unlock_duration_scale", 1.0f),
            velocityInfluence = prefs.getFloat("unlock_velocity_influence", 1.0f),
            maxVelocity = prefs.getFloat("unlock_max_velocity", 16000.0f),
            cascadeDamping = prefs.getFloat("unlock_cascade_damping", 0.95f),
            kineticDockDamping = prefs.getFloat("unlock_kinetic_dock_damping", 0.85f),
            kineticDockResponse = prefs.getFloat("unlock_kinetic_dock_response", 0.38f),
            kineticDockZDepth = prefs.getFloat("unlock_kinetic_dock_z_depth", 800.0f),
            dyn = dynMap
        )

        transitionConfig = AppTransitionConfig(
            enabled = prefs.getBoolean("app_anim_enabled", true),
            posDamping = prefs.getFloat("app_pos_damping", 0.80f),
            posResponse = prefs.getFloat("app_pos_response", 0.40f),
            scaleDamping = prefs.getFloat("app_scale_damping", 0.80f),
            scaleResponse = prefs.getFloat("app_scale_response", 0.40f),
            cornerDamping = prefs.getFloat("app_corner_damping", 1.00f),
            cornerResponse = prefs.getFloat("app_corner_response", 0.35f),
            alphaDamping = prefs.getFloat("app_alpha_damping", 1.00f),
            alphaResponse = prefs.getFloat("app_alpha_response", 0.30f),
            appYArc = prefs.getFloat("app_y_arc", 0.0f),
            appBottomZWarp = prefs.getFloat("app_bottom_z_warp", 0.0f),
            appXSway = prefs.getFloat("app_x_sway", 0.0f),
            appInterruptSmooth = prefs.getFloat("app_interrupt_smooth", 1.0f),
            appIconPitchDeg = prefs.getFloat("app_icon_pitch_deg", 0.0f),
            appIconCameraDist = prefs.getFloat("app_icon_camera_dist", 8000.0f)
        )
    }
}
