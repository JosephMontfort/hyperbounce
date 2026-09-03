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

data class OriginOSTransitionConfig(
    val enabled: Boolean = true,
    val curveProfile: Int = 0, // 0 = Swift (轻快), 1 = Gentle (柔和), 2 = Comfort (舒适), 3 = Custom Tuner
    val parallelAnimEnabled: Boolean = true, // Parallel multi-app launches without home gesture
    val unlockHighEndCapabilities: Boolean = true, // Force unlock flagship FastLaunch & bypass lite device throttling
    val delayedAlphaClose: Boolean = true, // OriginOS 120ms alpha hold for solid card collapse

    // Custom Tuner Parameters (Active when curveProfile == 3, or customized)
    val customOpenDamping: Float = 1.00f,
    val customOpenResponse: Float = 0.296f, // k = 450.0f
    val customCloseDamping: Float = 1.00f,
    val customCloseResponse: Float = 0.468f, // k = 180.0f
    val customRecentsDamping: Float = 0.95f,
    val customRecentsResponse: Float = 0.419f, // k = 224.0f
    val customParallelDamping: Float = 0.95f,
    val customParallelResponse: Float = 0.419f, // k = 224.0f
    val customCornerDamping: Float = 1.15f,
    val customCornerResponse: Float = 0.256f, // k = 600.0f
    val customAlphaDamping: Float = 0.60f,
    val customAlphaResponse: Float = 0.268f, // k = 550.0f

    // Spatial & Environmental Parameters
    val wallpaperScaleZoom: Float = 1.15f, // OriginOS wallpaper zoom (1.0 -> 1.15)
    val dragLayerEndScale: Float = 0.90f   // OriginOS desktop scale (1.0 -> 0.90)
)

object LauncherConfigHolder {
    @Volatile
    var unlockConfig: ScreenUnlockConfig = ScreenUnlockConfig()
        private set

    @Volatile
    var originOSConfig: OriginOSTransitionConfig = OriginOSTransitionConfig()
        private set

    // Backward compatibility accessor
    val transitionConfig: OriginOSTransitionConfig
        get() = originOSConfig

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

        originOSConfig = OriginOSTransitionConfig(
            enabled = prefs.getBoolean("originos_anim_enabled", true),
            curveProfile = prefs.getInt("originos_curve_profile", 0),
            parallelAnimEnabled = prefs.getBoolean("originos_parallel_enabled", true),
            unlockHighEndCapabilities = prefs.getBoolean("originos_unlock_highend", true),
            delayedAlphaClose = prefs.getBoolean("originos_delayed_alpha", true),
            customOpenDamping = prefs.getFloat("originos_open_damping", 1.00f),
            customOpenResponse = prefs.getFloat("originos_open_response", 0.296f),
            customCloseDamping = prefs.getFloat("originos_close_damping", 1.00f),
            customCloseResponse = prefs.getFloat("originos_close_response", 0.468f),
            customRecentsDamping = prefs.getFloat("originos_recents_damping", 0.95f),
            customRecentsResponse = prefs.getFloat("originos_recents_response", 0.419f),
            customParallelDamping = prefs.getFloat("originos_parallel_damping", 0.95f),
            customParallelResponse = prefs.getFloat("originos_parallel_response", 0.419f),
            customCornerDamping = prefs.getFloat("originos_corner_damping", 1.15f),
            customCornerResponse = prefs.getFloat("originos_corner_response", 0.256f),
            customAlphaDamping = prefs.getFloat("originos_alpha_damping", 0.60f),
            customAlphaResponse = prefs.getFloat("originos_alpha_response", 0.268f),
            wallpaperScaleZoom = prefs.getFloat("originos_wallpaper_zoom", 1.15f),
            dragLayerEndScale = prefs.getFloat("originos_draglayer_scale", 0.90f)
        )
    }
}
