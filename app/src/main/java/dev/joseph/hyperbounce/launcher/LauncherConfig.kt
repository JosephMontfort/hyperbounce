package dev.joseph.hyperbounce.launcher

import android.content.SharedPreferences

object ScreenUnlockDefaults {
    fun rowZ(row: Int, totalRows: Int = 7): Float {
        val norm = if (totalRows <= 1) 0.0f else (row.toFloat() / (totalRows - 1).toFloat()).coerceIn(0.0f, 1.0f)
        return 2000.0f - (norm * 1350.0f)
    }

    fun rowBounce(row: Int, totalRows: Int = 7, isOuter: Boolean = false): Float {
        val norm = if (totalRows <= 1) 0.0f else (row.toFloat() / (totalRows - 1).toFloat()).coerceIn(0.0f, 1.0f)
        val factor = (1.0f - norm) * (1.0f - norm)
        val maxBounce = if (isOuter) 0.50f else 0.55f
        return (maxBounce * factor).coerceIn(0.0f, 1.0f)
    }

    fun rowSettle(row: Int, totalRows: Int = 7, isOuter: Boolean = false): Float {
        val norm = if (totalRows <= 1) 0.0f else (row.toFloat() / (totalRows - 1).toFloat()).coerceIn(0.0f, 1.0f)
        val base = 0.52f - (norm * 0.17f)
        return if (isOuter) base + 0.02f else base
    }
}

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
    val cascadeDamping: Float = 0.98f,
    val kineticDockDamping: Float = 0.95f,
    val kineticDockResponse: Float = 0.36f,
    val kineticDockZDepth: Float = 600.0f,

    // Row-by-row dynamics (r0_z..r6_z, r0_center_bounce..r6_center_bounce, etc.)
    val dyn: Map<String, Float> = emptyMap()
) {
    fun getDyn(key: String, defVal: Float): Float = dyn[key] ?: defVal
}

data class IOSTransitionConfig(
    val enabled: Boolean = true,
    val arcApexLift: Float = 1.0f,
    val openUpliftSurge: Boolean = true,
    val keystoneSqueeze: Boolean = true,
    val animSpeed: Float = 1.0f,
    val openSpringDamping: Float = 0.98f,
    val closeSpringDamping: Float = 0.84f,
    val folderTracking: Boolean = true,
    val recentsCentering: Boolean = true,
    val zeroFrictionDrag: Boolean = true,
    val landscapeGameOpt: Boolean = true,
    val refinedHaptics: Boolean = true,
    val stackRecentsHaptics: Boolean = true
)

object LauncherConfigHolder {
    @Volatile
    var unlockConfig: ScreenUnlockConfig = ScreenUnlockConfig()
        private set

    @Volatile
    private var sPrefs: SharedPreferences? = null

    @Volatile
    var iosAnimConfig: IOSTransitionConfig = IOSTransitionConfig()
        private set

    // Backward compatibility accessor
    val transitionConfig: IOSTransitionConfig
        get() = iosAnimConfig

    fun reload() {
        sPrefs?.let { update(it) }
    }

    fun update(prefs: SharedPreferences) {
        sPrefs = prefs
        val rows = prefs.getInt("unlock_grid_rows", 7).coerceIn(1, 32)
        val cols = prefs.getInt("unlock_grid_columns", 4).coerceIn(1, 32)

        val dynMap = mutableMapOf<String, Float>()
        for (i in 0 until rows) {
            val defZ = ScreenUnlockDefaults.rowZ(i, rows)
            val defCenterBounce = ScreenUnlockDefaults.rowBounce(i, rows, false)
            val defOuterBounce = ScreenUnlockDefaults.rowBounce(i, rows, true)
            val defCenterSettle = ScreenUnlockDefaults.rowSettle(i, rows, false)
            val defOuterSettle = ScreenUnlockDefaults.rowSettle(i, rows, true)

            dynMap["r${i}_z"] = prefs.getFloat("unlock_r${i}_z", defZ)
            dynMap["r${i}_center_bounce"] = prefs.getFloat("unlock_r${i}_center_bounce", defCenterBounce)
            dynMap["r${i}_outer_bounce"] = prefs.getFloat("unlock_r${i}_outer_bounce", defOuterBounce)
            dynMap["r${i}_center_settle"] = prefs.getFloat("unlock_r${i}_center_settle", defCenterSettle)
            dynMap["r${i}_outer_settle"] = prefs.getFloat("unlock_r${i}_outer_settle", defOuterSettle)
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
            cascadeDamping = prefs.getFloat("unlock_cascade_damping", 0.98f),
            kineticDockDamping = prefs.getFloat("unlock_kinetic_dock_damping", 0.95f),
            kineticDockResponse = prefs.getFloat("unlock_kinetic_dock_response", 0.36f),
            kineticDockZDepth = prefs.getFloat("unlock_kinetic_dock_z_depth", 600.0f),
            dyn = dynMap
        )

        iosAnimConfig = IOSTransitionConfig(
            enabled = prefs.getBoolean("ios_anim_enabled", true),
            arcApexLift = prefs.getFloat("ios_anim_arc_apex_lift", 1.0f),
            openUpliftSurge = prefs.getBoolean("ios_anim_open_uplift_surge", true),
            keystoneSqueeze = prefs.getBoolean("ios_anim_keystone_squeeze", true),
            animSpeed = prefs.getFloat("ios_anim_speed", 1.0f),
            openSpringDamping = prefs.getFloat("ios_anim_open_spring_damping", 0.98f),
            closeSpringDamping = prefs.getFloat("ios_anim_close_spring_damping", 0.84f),
            folderTracking = prefs.getBoolean("ios_anim_folder_tracking", true),
            recentsCentering = prefs.getBoolean("ios_anim_recents_centering", true),
            zeroFrictionDrag = prefs.getBoolean("ios_anim_zero_friction_drag", true),
            landscapeGameOpt = prefs.getBoolean("ios_anim_landscape_game_opt", true),
            refinedHaptics = prefs.getBoolean("ios_anim_refined_haptics", true),
            stackRecentsHaptics = prefs.getBoolean("ios_anim_stack_recents_haptics", true)
        )
    }
}
