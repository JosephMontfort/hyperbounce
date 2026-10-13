package dev.hyperios.app.launcher

import android.content.SharedPreferences
import dev.hyperios.app.common.ModuleDefaults

object ScreenUnlockDefaults {
    fun rowZ(row: Int, totalRows: Int = 6): Float = ModuleDefaults.ScreenUnlock.rowZ(row, totalRows)
    fun rowBounce(row: Int, totalRows: Int = 6, isOuter: Boolean = false): Float = ModuleDefaults.ScreenUnlock.rowBounce(row, totalRows, isOuter)
    fun rowSettle(row: Int, totalRows: Int = 6, isOuter: Boolean = false): Float = ModuleDefaults.ScreenUnlock.rowSettle(row, totalRows, isOuter)
}

data class ScreenUnlockConfig(
    val enabled: Boolean = ModuleDefaults.MasterToggles.UNLOCK_ENABLED,
    val animMode: Int = ModuleDefaults.ScreenUnlock.MODE, // 0 = iOS Radial Dispersion, 1 = Kinetic Y-Axis Cascade
    val gridColumns: Int = ModuleDefaults.ScreenUnlock.GRID_COLUMNS,
    val gridRows: Int = ModuleDefaults.ScreenUnlock.GRID_ROWS,

    // Mode 0: Classic Radial Unlock
    val dockDamping: Float = ModuleDefaults.ScreenUnlock.DOCK_DAMPING,
    val dockResponse: Float = ModuleDefaults.ScreenUnlock.DOCK_RESPONSE,
    val centerResponse: Float = ModuleDefaults.ScreenUnlock.CENTER_RESPONSE,
    val centerDamping: Float = ModuleDefaults.ScreenUnlock.CENTER_DAMPING,
    val edgeResponseDelay: Float = ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY,
    val edgeDampingDrop: Float = ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP,
    val zDepthMultiplier: Float = ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER,
    val minResponse: Float = ModuleDefaults.ScreenUnlock.MIN_RESPONSE,
    val maxResponse: Float = ModuleDefaults.ScreenUnlock.MAX_RESPONSE,
    val minDamping: Float = ModuleDefaults.ScreenUnlock.MIN_DAMPING,
    val maxDamping: Float = ModuleDefaults.ScreenUnlock.MAX_DAMPING,

    // Mode 1: Kinetic Y-Axis Cascade
    val durationScale: Float = ModuleDefaults.ScreenUnlock.DURATION_SCALE,
    val velocityInfluence: Float = ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE,
    val maxVelocity: Float = ModuleDefaults.ScreenUnlock.MAX_VELOCITY,
    val cascadeDamping: Float = ModuleDefaults.ScreenUnlock.CASCADE_DAMPING,
    val kineticDockDamping: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING,
    val kineticDockResponse: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE,
    val kineticDockZDepth: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH,

    // Row-by-row dynamics (r0_z..r6_z, r0_center_bounce..r6_center_bounce, etc.)
    val dyn: Map<String, Float> = emptyMap()
) {
    fun getDyn(key: String, defVal: Float): Float = dyn[key] ?: defVal
}

data class IOSTransitionConfig(
    val enabled: Boolean = ModuleDefaults.MasterToggles.IOS_ANIM_ENABLED,
    val arcApexLift: Float = ModuleDefaults.IOSTransitions.ARC_APEX_LIFT,
    val openSurgeLift: Float = ModuleDefaults.IOSTransitions.OPEN_SURGE_LIFT,
    val openUpliftSurge: Boolean = ModuleDefaults.IOSTransitions.OPEN_UPLIFT_SURGE,
    val closeUpliftSurge: Boolean = ModuleDefaults.IOSTransitions.CLOSE_UPLIFT_SURGE,
    val keystoneSqueeze: Boolean = ModuleDefaults.IOSTransitions.KEYSTONE_SQUEEZE,
    val animSpeed: Float = ModuleDefaults.IOSTransitions.ANIM_SPEED,
    val openSpringDamping: Float = ModuleDefaults.IOSTransitions.OPEN_SPRING_DAMPING,
    val closeSpringDamping: Float = ModuleDefaults.IOSTransitions.CLOSE_SPRING_DAMPING,
    val folderTracking: Boolean = ModuleDefaults.IOSTransitions.FOLDER_TRACKING,
    val recentsCentering: Boolean = ModuleDefaults.IOSTransitions.RECENTS_CENTERING,
    val zeroFrictionDrag: Boolean = ModuleDefaults.IOSTransitions.ZERO_FRICTION_DRAG,
    val landscapeGameOpt: Boolean = ModuleDefaults.IOSTransitions.LANDSCAPE_GAME_OPT,
    val refinedHaptics: Boolean = ModuleDefaults.IOSTransitions.REFINED_HAPTICS,
    val stackRecentsHaptics: Boolean = ModuleDefaults.IOSTransitions.STACK_RECENTS_HAPTICS
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
        val rows = prefs.getInt("unlock_grid_rows", ModuleDefaults.ScreenUnlock.GRID_ROWS).coerceIn(1, 32)
        val cols = prefs.getInt("unlock_grid_columns", ModuleDefaults.ScreenUnlock.GRID_COLUMNS).coerceIn(1, 32)

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
            enabled = prefs.getBoolean("unlock_enabled", ModuleDefaults.MasterToggles.UNLOCK_ENABLED),
            animMode = prefs.getInt("unlock_anim_mode", ModuleDefaults.ScreenUnlock.MODE),
            gridColumns = cols,
            gridRows = rows,
            dockDamping = prefs.getFloat("unlock_dock_damping", ModuleDefaults.ScreenUnlock.DOCK_DAMPING),
            dockResponse = prefs.getFloat("unlock_dock_response", ModuleDefaults.ScreenUnlock.DOCK_RESPONSE),
            centerResponse = prefs.getFloat("unlock_center_response", ModuleDefaults.ScreenUnlock.CENTER_RESPONSE),
            centerDamping = prefs.getFloat("unlock_center_damping", ModuleDefaults.ScreenUnlock.CENTER_DAMPING),
            edgeResponseDelay = prefs.getFloat("unlock_edge_response_delay", ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY),
            edgeDampingDrop = prefs.getFloat("unlock_edge_damping_drop", ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP),
            zDepthMultiplier = prefs.getFloat("unlock_z_depth_multiplier", ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER),
            minResponse = prefs.getFloat("unlock_min_response", ModuleDefaults.ScreenUnlock.MIN_RESPONSE),
            maxResponse = prefs.getFloat("unlock_max_response", ModuleDefaults.ScreenUnlock.MAX_RESPONSE),
            minDamping = prefs.getFloat("unlock_min_damping", ModuleDefaults.ScreenUnlock.MIN_DAMPING),
            maxDamping = prefs.getFloat("unlock_max_damping", ModuleDefaults.ScreenUnlock.MAX_DAMPING),
            durationScale = prefs.getFloat("unlock_duration_scale", ModuleDefaults.ScreenUnlock.DURATION_SCALE),
            velocityInfluence = prefs.getFloat("unlock_velocity_influence", ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE),
            maxVelocity = prefs.getFloat("unlock_max_velocity", ModuleDefaults.ScreenUnlock.MAX_VELOCITY),
            cascadeDamping = prefs.getFloat("unlock_cascade_damping", ModuleDefaults.ScreenUnlock.CASCADE_DAMPING),
            kineticDockDamping = prefs.getFloat("unlock_kinetic_dock_damping", ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING),
            kineticDockResponse = prefs.getFloat("unlock_kinetic_dock_response", ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE),
            kineticDockZDepth = prefs.getFloat("unlock_kinetic_dock_z_depth", ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH),
            dyn = dynMap
        )

        iosAnimConfig = IOSTransitionConfig(
            enabled = prefs.getBoolean("ios_anim_enabled", ModuleDefaults.MasterToggles.IOS_ANIM_ENABLED),
            arcApexLift = prefs.getFloat("ios_anim_arc_apex_lift", ModuleDefaults.IOSTransitions.ARC_APEX_LIFT),
            openSurgeLift = prefs.getFloat("ios_anim_open_surge_lift", ModuleDefaults.IOSTransitions.OPEN_SURGE_LIFT),
            openUpliftSurge = prefs.getBoolean("ios_anim_open_uplift_surge", ModuleDefaults.IOSTransitions.OPEN_UPLIFT_SURGE),
            closeUpliftSurge = prefs.getBoolean("ios_anim_close_uplift_surge", ModuleDefaults.IOSTransitions.CLOSE_UPLIFT_SURGE),
            keystoneSqueeze = prefs.getBoolean("ios_anim_keystone_squeeze", ModuleDefaults.IOSTransitions.KEYSTONE_SQUEEZE),
            animSpeed = prefs.getFloat("ios_anim_speed", ModuleDefaults.IOSTransitions.ANIM_SPEED),
            openSpringDamping = prefs.getFloat("ios_anim_open_spring_damping", ModuleDefaults.IOSTransitions.OPEN_SPRING_DAMPING),
            closeSpringDamping = prefs.getFloat("ios_anim_close_spring_damping", ModuleDefaults.IOSTransitions.CLOSE_SPRING_DAMPING),
            folderTracking = prefs.getBoolean("ios_anim_folder_tracking", ModuleDefaults.IOSTransitions.FOLDER_TRACKING),
            recentsCentering = prefs.getBoolean("ios_anim_recents_centering", ModuleDefaults.IOSTransitions.RECENTS_CENTERING),
            zeroFrictionDrag = prefs.getBoolean("ios_anim_zero_friction_drag", ModuleDefaults.IOSTransitions.ZERO_FRICTION_DRAG),
            landscapeGameOpt = prefs.getBoolean("ios_anim_landscape_game_opt", ModuleDefaults.IOSTransitions.LANDSCAPE_GAME_OPT),
            refinedHaptics = prefs.getBoolean("ios_anim_refined_haptics", ModuleDefaults.IOSTransitions.REFINED_HAPTICS),
            stackRecentsHaptics = prefs.getBoolean("ios_anim_stack_recents_haptics", ModuleDefaults.IOSTransitions.STACK_RECENTS_HAPTICS)
        )
    }
}
