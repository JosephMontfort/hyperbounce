package dev.hyperios.app.controlcenter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.prefs.PREFS_GROUP
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Configuration data class for Control Center Modular Layout, Motion Dynamics, and Jelly Physics.
 */
data class ControlCenterConfig(
    var masterEnabled: Boolean = ModuleDefaults.MasterToggles.CC_MASTER_ENABLED,
    var modularEditEnabled: Boolean = ModuleDefaults.ControlCenter.MODULAR_EDIT_ENABLED,
    var animEnabled: Boolean = ModuleDefaults.ControlCenter.ANIM_ENABLED,
    var jellyEnabled: Boolean = ModuleDefaults.ControlCenter.JELLY_ENABLED,
    var animEngineMode: Int = ModuleDefaults.ControlCenter.ANIM_ENGINE_MODE,
    var spreadRatio: Float = ModuleDefaults.ControlCenter.SPREAD_RATIO,
    var stretchThreshold: Float = ModuleDefaults.ControlCenter.STRETCH_THRESHOLD,
    var spreadResponse: Float = ModuleDefaults.ControlCenter.SPREAD_RESPONSE,
    var spreadDamping: Float = ModuleDefaults.ControlCenter.SPREAD_DAMPING,
    var spreadReleaseResponse: Float = ModuleDefaults.ControlCenter.SPREAD_RELEASE_RESPONSE,
    var spreadReleaseDamping: Float = ModuleDefaults.ControlCenter.SPREAD_RELEASE_DAMPING,
    var settlingTime: Float = ModuleDefaults.ControlCenter.SETTLING_TIME,
    var tileDelayScale: Float = ModuleDefaults.ControlCenter.TILE_DELAY_SCALE,
    var frictionMultiplier: Float = ModuleDefaults.ControlCenter.FRICTION_MULTIPLIER,
    var lowEndFlashlightEnabled: Boolean = ModuleDefaults.ControlCenter.LOW_END_FLASHLIGHT_ENABLED,
    var flashlightMaxBrightness: Int = ModuleDefaults.ControlCenter.FLASHLIGHT_MAX_BRIGHTNESS,
    var true2dGridEnabled: Boolean = ModuleDefaults.ControlCenter.TRUE_2D_GRID_ENABLED,
    var fluidResizeEnabled: Boolean = ModuleDefaults.ControlCenter.FLUID_RESIZE_ENABLED,
    var landscapeRightMaxRows: Int = ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS
) {
    val isModularEditActive: Boolean get() = masterEnabled && modularEditEnabled
    val isAnimActive: Boolean get() = masterEnabled && animEnabled
    val isJellyActive: Boolean get() = masterEnabled && jellyEnabled
    val isFlashlightActive: Boolean get() = masterEnabled && lowEndFlashlightEnabled
    val isTrue2dGridActive: Boolean get() = isModularEditActive && true2dGridEnabled
    fun isLowEndAnim(): Boolean = animEngineMode == 1
}

/**
 * Represents a grouped multi-tile container (dynamic 2-column container up to 6 rows).
 */
data class TileGroup(
    val id: String,
    val specs: MutableList<String>,
    var isVertical: Boolean = false,
    var layoutType: String = "",
    var cardSpec: String = "",
    var isTransparent: Boolean = false,
    var rowCount: Int = 2,
    var tileSizes: String = "",
    var colCount: Int = 2
) {
    fun isIosFolderCluster(): Boolean {
        return specs.size == 7 || getEffectiveLayoutType() == "7_IOS_CLUSTER"
    }

    fun getEffectiveColCount(): Int {
        val type = getEffectiveLayoutType()
        if (type == "2_CIRCULAR_VERT") return 1
        if (type == "2_CIRCULAR_HORIZ") return 2
        if (type == "7_IOS_CLUSTER") return 2
        if (colCount in 1..4) return colCount
        return 2
    }

    fun getEffectiveRowCount(): Int {
        val type = getEffectiveLayoutType()
        if (type == "2_CIRCULAR_VERT") return 2
        if (type == "2_CIRCULAR_HORIZ") return 1
        if (type == "7_IOS_CLUSTER") return 2
        if (rowCount in 1..6) return rowCount
        return 2
    }

    fun getEffectiveLayoutType(): String {
        if (layoutType.isNotEmpty()) return layoutType
        if (tileSizes.isNotEmpty()) {
            val sizes = tileSizes.split(",")
            if (sizes.size == 2) {
                if (sizes.all { it == "E" }) return "2_EXPANDED"
                if (sizes.all { it == "V" }) return "2_VERTICAL"
                if (sizes.all { it == "C" }) return if (isVertical) "2_CIRCULAR_VERT" else "2_CIRCULAR_HORIZ"
            }
        }
        return when {
            specs.size == 7 -> "7_IOS_CLUSTER"
            specs.size == 4 -> "4_CIRCULAR"
            specs.size == 2 -> {
                val isExp = specs.all { s -> ControlCenterConfigHolder.sExpandedTileSpecs.contains(s) }
                val isVert = specs.all { s -> ControlCenterConfigHolder.sVerticalTileSpecs.contains(s) }
                if (isExp) "2_EXPANDED"
                else if (isVert) "2_VERTICAL"
                else if (isVertical) "2_CIRCULAR_VERT" else "2_CIRCULAR_HORIZ"
            }
            specs.size == 3 -> "1_EXP_2_CIRC"
            else -> "DYNAMIC_${getEffectiveRowCount()}R"
        }
    }

    fun getSpan(): Pair<Int, Int> {
        val type = getEffectiveLayoutType()
        return when (type) {
            "2_CIRCULAR_VERT" -> Pair(1, 2)
            "2_CIRCULAR_HORIZ" -> Pair(2, 1)
            "2_EXPANDED" -> Pair(2, 2)
            "2_VERTICAL" -> Pair(2, 2)
            "7_IOS_CLUSTER" -> Pair(2, 2)
            else -> Pair(getEffectiveColCount(), getEffectiveRowCount())
        }
    }

    fun is2x2Group(): Boolean {
        val type = getEffectiveLayoutType()
        return type == "4_CIRCULAR" ||
               type == "2_EXPANDED" ||
               type == "2_VERTICAL" ||
               type == "1_EXP_2_CIRC" ||
               type == "1_VERT_2_CIRC" ||
               type == "7_IOS_CLUSTER" ||
               (getEffectiveColCount() == 2 && getEffectiveRowCount() == 2)
    }
}

object GroupedTileReflectionCache {
    var startListeningMethod: java.lang.reflect.Method? = null
    var getTileMethod: java.lang.reflect.Method? = null
    var setListeningMethod: java.lang.reflect.Method? = null
    var refreshStateMethod: java.lang.reflect.Method? = null
    var getStateMethod: java.lang.reflect.Method? = null
    var getHolderMethod: java.lang.reflect.Method? = null
    var setHolderMethod: java.lang.reflect.Method? = null
    var itemViewField: java.lang.reflect.Field? = null
    var updateStateMethod: java.lang.reflect.Method? = null
    var lastChildViewClass: Class<*>? = null
}

object ControlCenterConfigHolder {
    private const val TAG = "HyperBounce_CC"

    @Volatile
    var config: ControlCenterConfig = ControlCenterConfig()

    // State Tracking
    val sTileGroups = CopyOnWriteArrayList<TileGroup>()
    val sExpandedTileSpecs = LinkedHashSet<String>().apply {
        add("wifi")
        add("cell")
    }
    val sVerticalTileSpecs = LinkedHashSet<String>()
    val sLargeTileSpecs = LinkedHashSet<String>()
    val sCircularCardSpecs = LinkedHashSet<String>()
    @Volatile var sMediaPlayerSize: String = "2x2"
    @Volatile var sBrightnessSliderHorizontal: Boolean = false
    @Volatile var sVolumeSliderHorizontal: Boolean = false
    @Volatile var sBrightnessSliderWidth: Int = 2
    @Volatile var sVolumeSliderWidth: Int = 2
    val landscapeRightMaxRows: Int
        get() = config.landscapeRightMaxRows.coerceAtLeast(1)
    val sComponentPriorities = ConcurrentHashMap<String, Int>().apply {
        put("CompactQS", 0)
        put("CompactQSCard", 1)
        put("QSCardsController", 20)
        put("MediaPlayerController", 30)
        put("BrightnessSliderController", 31)
        put("VolumeSliderController", 32)
        put("DeviceCenterEntryController", 50)
        put("DeviceControlsEntryController", 51)
        put("QSListController", 100)
    }

    @Volatile var sIsWifiFirst: Boolean = true
    @Volatile var sIsEditMode: Boolean = false
    @Volatile var sIsGroupSelectMode: Boolean = false
    @Volatile var sIsDisassembleMode: Boolean = false
    @Volatile var sIsDraggingResizeHandle: Boolean = false
    val sSelectedTileSpecs = LinkedHashSet<String>()
    val sSelectedDisassembleGroups = HashSet<String>()

    @Volatile var sCurrentVelocityY: Float = 0.0f
    @Volatile var sLastTouchTime: Long = 0L
    @Volatile var sIsUserFlicking: Boolean = false
    @Volatile var sPendingOrientationRebind: Boolean = false

    @Volatile var sQSListController: Any? = null
    @Volatile var sMainPanelAdapter: Any? = null
    // Typed as View to avoid class loading dependencies on androidx.recyclerview in SystemUI
    @Volatile var sRecyclerView: WeakReference<View>? = null
    @Volatile var sHasLoadedPriorities: Boolean = false
    @Volatile var sFrameCallback: Any? = null
    @Volatile var sLastKnownContext: Context? = null
    @Volatile var sExplicitReset: Boolean = false
    @Volatile private var sCachedSafeMode: Boolean? = null
    @Volatile var sShadeLowEndAnimFlow: Any? = null
    @Volatile var sHasStructuralEditChanges: Boolean = false
    private val sIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    val sAllQSRecords = CopyOnWriteArrayList<Any>()
    val sBindingRecords = object : ThreadLocal<HashSet<Any>>() {
        override fun initialValue(): HashSet<Any> = HashSet()
    }

    val sRemovedComponentSpecs = ConcurrentHashMap.newKeySet<String>()

    @Volatile var sWifiCardRecord: Any? = null
    @Volatile var sCellCardRecord: Any? = null
    @Volatile var sMediaPlayerController: Any? = null
    @Volatile var sBrightnessSliderController: Any? = null
    @Volatile var sVolumeSliderController: Any? = null
    @Volatile var sCachedLineDivider: Any? = null
    @Volatile var sCachedSystemTextDivider: Any? = null

    fun getRemovedComponentItem(spec: String?): Any? {
        if (spec == null) return null
        return when (spec) {
            "wifi" -> sWifiCardRecord ?: findRecordForSpec("wifi")
            "cell" -> sCellCardRecord ?: findRecordForSpec("cell")
            "media" -> sMediaPlayerController
            "brightness" -> sBrightnessSliderController
            "volume" -> sVolumeSliderController
            else -> findRecordForSpec(spec)
        }
    }

    fun isTileAdded(spec: String?): Boolean {
        if (spec == null) return false
        if (sRemovedComponentSpecs.contains(spec)) return false
        val added = getAddedTilesList()
        if (added != null) {
            for (rec in added) {
                if (rec != null && getRecordSpec(rec) == spec) return true
            }
            return false
        }
        return true
    }

    fun getAddedTilesList(): List<*>? {
        val qsList = sQSListController ?: return null
        return try {
            val f = qsList.javaClass.getDeclaredField("addedTiles")
            f.isAccessible = true
            f.get(qsList) as? List<*>
        } catch (t: Throwable) {
            try {
                val m = qsList.javaClass.getMethod("getAddedTiles")
                m.invoke(qsList) as? List<*>
            } catch (t2: Throwable) { null }
        }
    }

    fun findRecordForSpec(spec: String?): Any? {
        if (spec == null) return null
        if (spec == "wifi" && sWifiCardRecord != null) return sWifiCardRecord
        if (spec == "cell" && sCellCardRecord != null) return sCellCardRecord
        for (rec in sAllQSRecords) {
            if (rec != null && getRecordSpec(rec) == spec) return rec
        }
        val added = getAddedTilesList()
        if (added != null) {
            for (rec in added) {
                if (rec != null && getRecordSpec(rec) == spec) return rec
            }
        }
        val qsList = sQSListController
        if (qsList != null) {
            try {
                val sysF = qsList.javaClass.getDeclaredField("systemTiles")
                sysF.isAccessible = true
                val sysList = sysF.get(qsList) as? List<*>
                if (sysList != null) {
                    for (rec in sysList) {
                        if (rec != null && getRecordSpec(rec) == spec) return rec
                    }
                }
                val pkgF = qsList.javaClass.getDeclaredField("packageTiles")
                pkgF.isAccessible = true
                val pkgList = pkgF.get(qsList) as? List<*>
                if (pkgList != null) {
                    for (rec in pkgList) {
                        if (rec != null && getRecordSpec(rec) == spec) return rec
                    }
                }
            } catch (t: Throwable) {}
        }
        return null
    }

    @Volatile var pendingResetLayout: Boolean = false

    fun updateFromPrefs(prefs: SharedPreferences) {
        val defaultMode = DevicePipelineDetector.getDefaultAnimEngineMode()
        val engineMode = if (prefs.contains("cc_anim_engine_mode")) {
            prefs.getInt("cc_anim_engine_mode", defaultMode)
        } else if (prefs.contains("cc_low_end_anim")) {
            if (prefs.getBoolean("cc_low_end_anim", false)) 1 else 0
        } else {
            defaultMode
        }
        pendingResetLayout = prefs.getBoolean("cc_pending_reset_layout", ModuleDefaults.ControlCenter.PENDING_RESET_LAYOUT)
        config = ControlCenterConfig(
            masterEnabled = prefs.getBoolean("cc_master_enabled", ModuleDefaults.MasterToggles.CC_MASTER_ENABLED),
            modularEditEnabled = prefs.getBoolean("cc_modular_edit_enabled", ModuleDefaults.ControlCenter.MODULAR_EDIT_ENABLED),
            animEnabled = prefs.getBoolean("cc_anim_enabled", ModuleDefaults.ControlCenter.ANIM_ENABLED),
            jellyEnabled = prefs.getBoolean("cc_jelly_enabled", ModuleDefaults.ControlCenter.JELLY_ENABLED),
            animEngineMode = engineMode,
            spreadRatio = prefs.getFloat("cc_spread_ratio", ModuleDefaults.ControlCenter.SPREAD_RATIO),
            stretchThreshold = prefs.getFloat("cc_stretch_threshold", ModuleDefaults.ControlCenter.STRETCH_THRESHOLD),
            spreadResponse = prefs.getFloat("cc_spread_response", ModuleDefaults.ControlCenter.SPREAD_RESPONSE),
            spreadDamping = prefs.getFloat("cc_spread_damping", ModuleDefaults.ControlCenter.SPREAD_DAMPING),
            spreadReleaseResponse = prefs.getFloat("cc_spread_release_response", ModuleDefaults.ControlCenter.SPREAD_RELEASE_RESPONSE),
            spreadReleaseDamping = prefs.getFloat("cc_spread_release_damping", ModuleDefaults.ControlCenter.SPREAD_RELEASE_DAMPING),
            settlingTime = prefs.getFloat("cc_settling_time", ModuleDefaults.ControlCenter.SETTLING_TIME),
            tileDelayScale = prefs.getFloat("cc_tile_delay_scale", ModuleDefaults.ControlCenter.TILE_DELAY_SCALE),
            frictionMultiplier = prefs.getFloat("cc_friction_multiplier", ModuleDefaults.ControlCenter.FRICTION_MULTIPLIER),
            lowEndFlashlightEnabled = prefs.getBoolean("cc_low_end_flashlight_enabled", ModuleDefaults.ControlCenter.LOW_END_FLASHLIGHT_ENABLED),
            flashlightMaxBrightness = prefs.getInt("cc_flashlight_max_brightness", ModuleDefaults.ControlCenter.FLASHLIGHT_MAX_BRIGHTNESS),
            true2dGridEnabled = prefs.getBoolean("cc_true_2d_grid_enabled", ModuleDefaults.ControlCenter.TRUE_2D_GRID_ENABLED),
            fluidResizeEnabled = prefs.getBoolean("cc_fluid_resize_enabled", ModuleDefaults.ControlCenter.FLUID_RESIZE_ENABLED),
            landscapeRightMaxRows = prefs.getInt("cc_landscape_right_max_rows", ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS)
        )
    }

    fun clearPendingReset(ctx: Context?) {
        if (ctx == null) return
        try {
            ctx.contentResolver.call(
                Uri.parse("content://dev.hyperios.app.provider"),
                "clearPendingResetLayout", null, null
            )
        } catch (t: Throwable) {}
    }

    @Synchronized
    fun loadSavedPriorities(ctx: Context?) {
        if (ctx == null) return
        sLastKnownContext = ctx
        sHasLoadedPriorities = true
        if (pendingResetLayout) {
            Log.i(TAG, "Queued reset to stock layout detected on startup. Executing resetLayoutToStock...")
            resetLayoutToStock(ctx)
            clearPendingReset(ctx)
            pendingResetLayout = false
            return
        }
        try {
            // 1. Android Settings.Secure
            var jsonStr = try {
                android.provider.Settings.Secure.getString(ctx.contentResolver, "sysui_hyperbounce_cc_layout")
            } catch (t: Throwable) { null }

            if (TextUtils.isEmpty(jsonStr)) {
                jsonStr = try {
                    android.provider.Settings.Secure.getString(ctx.contentResolver, "hyperbounce_cc_layout")
                } catch (t: Throwable) { null }
            }

            // 2. ContentProvider
            if (TextUtils.isEmpty(jsonStr)) {
                try {
                    val res = ctx.contentResolver.call(
                        Uri.parse("content://dev.hyperios.app.provider"),
                        "getCCLayout", null, null
                    )
                    if (res != null) {
                        jsonStr = res.getString("cc_layout_json")
                    }
                } catch (t: Throwable) {}
            }

            // 3. Parse JSON
            if (!TextUtils.isEmpty(jsonStr)) {
                try {
                    val json = JSONObject(jsonStr!!)
                    val prioObj = json.optJSONObject("priorities")
                    if (prioObj != null) {
                        val keys = prioObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if ("QSListController" == k) continue
                            sComponentPriorities[k] = prioObj.getInt(k)
                        }
                    }
                    sIsWifiFirst = json.optBoolean("wifi_card_first", sIsWifiFirst)

                    if (json.has("expanded_tile_specs")) {
                        val expandedStr = json.optString("expanded_tile_specs", "")
                        val newExp = LinkedHashSet<String>()
                        for (spec in expandedStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newExp.add(spec.trim())
                        }
                        sExpandedTileSpecs.clear()
                        sExpandedTileSpecs.addAll(newExp)
                    }

                    if (json.has("vertical_tile_specs")) {
                        val verticalStr = json.optString("vertical_tile_specs", "")
                        val newVert = LinkedHashSet<String>()
                        for (spec in verticalStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newVert.add(spec.trim())
                        }
                        sVerticalTileSpecs.clear()
                        sVerticalTileSpecs.addAll(newVert)
                    }

                    if (json.has("large_tile_specs")) {
                        val largeStr = json.optString("large_tile_specs", "")
                        val newLarge = LinkedHashSet<String>()
                        for (spec in largeStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newLarge.add(spec.trim())
                        }
                        sLargeTileSpecs.clear()
                        sLargeTileSpecs.addAll(newLarge)
                    }

                    val circularCardsStr = json.optString("circular_card_specs", "")
                    sCircularCardSpecs.clear()
                    if (!TextUtils.isEmpty(circularCardsStr)) {
                        for (spec in circularCardsStr.split(",")) {
                            if (spec.trim().isNotEmpty()) sCircularCardSpecs.add(spec.trim())
                        }
                    }
                    for (cSpec in sCircularCardSpecs) {
                        sExpandedTileSpecs.remove(cSpec)
                        sVerticalTileSpecs.remove(cSpec)
                        sLargeTileSpecs.remove(cSpec)
                    }
                    sMediaPlayerSize = json.optString("media_player_size", sMediaPlayerSize)
                    sBrightnessSliderHorizontal = json.optBoolean("brightness_slider_horizontal", sBrightnessSliderHorizontal)
                    sVolumeSliderHorizontal = json.optBoolean("volume_slider_horizontal", sVolumeSliderHorizontal)
                    sBrightnessSliderWidth = json.optInt("brightness_slider_width", sBrightnessSliderWidth).coerceIn(2, 4)
                    sVolumeSliderWidth = json.optInt("volume_slider_width", sVolumeSliderWidth).coerceIn(2, 4)
                    val removedSpecsStr = json.optString("removed_component_specs", "")
                    sRemovedComponentSpecs.clear()
                    if (!TextUtils.isEmpty(removedSpecsStr)) {
                        for (spec in removedSpecsStr.split(",")) {
                            if (spec.trim().isNotEmpty()) sRemovedComponentSpecs.add(spec.trim())
                        }
                    }
                    if (json.has("landscape_right_max_rows")) {
                        config.landscapeRightMaxRows = json.optInt("landscape_right_max_rows", 3).coerceAtLeast(1)
                    }

                    if (json.has("tile_groups_v2")) {
                        val grpV2 = json.optString("tile_groups_v2", "")
                        val newGroups = ArrayList<TileGroup>()
                        if (!TextUtils.isEmpty(grpV2)) {
                            for (gEntry in grpV2.split(";")) {
                                val parts = gEntry.split(":")
                                if (parts.size >= 3) {
                                    val gId = parts[0]
                                    val isVert = "V".equals(parts[1], ignoreCase = true)
                                    val gSpecs = mutableListOf<String>()
                                    for (sp in parts[2].split(",")) {
                                        if (sp.trim().isNotEmpty()) gSpecs.add(sp.trim())
                                    }
                                    val layoutType = if (parts.size >= 4) parts[3] else ""
                                    val cardSpec = if (parts.size >= 5) parts[4] else ""
                                    val isTransparent = if (parts.size >= 6) "T".equals(parts[5], ignoreCase = true) else false
                                    val rowCount = if (parts.size >= 7) parts[6].toIntOrNull() ?: 2 else 2
                                    val tileSizes = if (parts.size >= 8) parts[7] else ""
                                    val colCount = if (parts.size >= 9) parts[8].toIntOrNull() ?: 2 else 2
                                    if (gSpecs.size >= 2) {
                                        newGroups.add(TileGroup(gId, gSpecs, isVert, layoutType, cardSpec, isTransparent, rowCount, tileSizes, colCount))
                                    }
                                }
                            }
                        }
                        sTileGroups.clear()
                        sTileGroups.addAll(newGroups)
                    }
                    return
                } catch (t: Throwable) {}
            }

            // 4. SharedPreferences Fallback
            val prefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE)
            if (prefs != null) {
                for (key in sComponentPriorities.keys) {
                    if ("QSListController" == key) continue
                    val saved = prefs.getInt("priority_$key", -1)
                    if (saved != -1) {
                        sComponentPriorities[key] = saved
                    }
                }
                sIsWifiFirst = prefs.getBoolean("wifi_card_first", sIsWifiFirst)

                if (prefs.contains("expanded_tile_specs")) {
                    val expandedStr = prefs.getString("expanded_tile_specs", "") ?: ""
                    val newExp = LinkedHashSet<String>()
                    for (spec in expandedStr.split(",")) {
                        if (spec.trim().isNotEmpty()) newExp.add(spec.trim())
                    }
                    sExpandedTileSpecs.clear()
                    sExpandedTileSpecs.addAll(newExp)
                }

                if (prefs.contains("vertical_tile_specs")) {
                    val verticalStr = prefs.getString("vertical_tile_specs", "") ?: ""
                    val newVert = LinkedHashSet<String>()
                    for (spec in verticalStr.split(",")) {
                        if (spec.trim().isNotEmpty()) newVert.add(spec.trim())
                    }
                    sVerticalTileSpecs.clear()
                    sVerticalTileSpecs.addAll(newVert)
                }

                if (prefs.contains("large_tile_specs")) {
                    val largeStr = prefs.getString("large_tile_specs", "") ?: ""
                    val newLarge = LinkedHashSet<String>()
                    for (spec in largeStr.split(",")) {
                        if (spec.trim().isNotEmpty()) newLarge.add(spec.trim())
                    }
                    sLargeTileSpecs.clear()
                    sLargeTileSpecs.addAll(newLarge)
                }

                val circularCardsStr = prefs.getString("circular_card_specs", "")
                sCircularCardSpecs.clear()
                if (!TextUtils.isEmpty(circularCardsStr)) {
                    for (spec in circularCardsStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) sCircularCardSpecs.add(spec.trim())
                    }
                }
                for (cSpec in sCircularCardSpecs) {
                    sExpandedTileSpecs.remove(cSpec)
                    sVerticalTileSpecs.remove(cSpec)
                    sLargeTileSpecs.remove(cSpec)
                }
                sMediaPlayerSize = prefs.getString("media_player_size", sMediaPlayerSize) ?: "2x2"
                sBrightnessSliderHorizontal = prefs.getBoolean("brightness_slider_horizontal", sBrightnessSliderHorizontal)
                sVolumeSliderHorizontal = prefs.getBoolean("volume_slider_horizontal", sVolumeSliderHorizontal)
                sBrightnessSliderWidth = prefs.getInt("brightness_slider_width", sBrightnessSliderWidth).coerceIn(2, 4)
                sVolumeSliderWidth = prefs.getInt("volume_slider_width", sVolumeSliderWidth).coerceIn(2, 4)
                val removedStr = prefs.getString("removed_component_specs", "")
                sRemovedComponentSpecs.clear()
                if (!TextUtils.isEmpty(removedStr)) {
                    for (spec in removedStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) sRemovedComponentSpecs.add(spec.trim())
                    }
                }
                if (prefs.contains("landscape_right_max_rows")) {
                    config.landscapeRightMaxRows = prefs.getInt("landscape_right_max_rows", 3).coerceAtLeast(1)
                }

                if (prefs.contains("tile_groups_v2")) {
                    val groupsStrV2 = prefs.getString("tile_groups_v2", "") ?: ""
                    val newGroups = ArrayList<TileGroup>()
                    if (groupsStrV2.isNotEmpty()) {
                        for (gEntry in groupsStrV2.split(";")) {
                            val parts = gEntry.split(":")
                            if (parts.size >= 3) {
                                val gId = parts[0]
                                val isVert = "V".equals(parts[1], ignoreCase = true)
                                val gSpecs = mutableListOf<String>()
                                for (sp in parts[2].split(",")) {
                                    if (sp.trim().isNotEmpty()) gSpecs.add(sp.trim())
                                }
                                val layoutType = if (parts.size >= 4) parts[3] else ""
                                val cardSpec = if (parts.size >= 5) parts[4] else ""
                                val isTransparent = if (parts.size >= 6) "T".equals(parts[5], ignoreCase = true) else false
                                val rowCount = if (parts.size >= 7) parts[6].toIntOrNull() ?: 2 else 2
                                val tileSizes = if (parts.size >= 8) parts[7] else ""
                                val colCount = if (parts.size >= 9) parts[8].toIntOrNull() ?: 2 else 2
                                if (gSpecs.size >= 2) {
                                    newGroups.add(TileGroup(gId, gSpecs, isVert, layoutType, cardSpec, isTransparent, rowCount, tileSizes, colCount))
                                }
                            }
                        }
                    }
                    sTileGroups.clear()
                    sTileGroups.addAll(newGroups)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loadSavedPriorities error", t)
        }
    }

    @Synchronized
    fun savePriorities(ctx: Context?) {
        val targetCtx = ctx ?: sLastKnownContext ?: return
        sHasLoadedPriorities = true
        try {
            val json = JSONObject()
            val prioObj = JSONObject()
            for ((k, v) in sComponentPriorities) {
                prioObj.put(k, v)
            }
            json.put("priorities", prioObj)
            json.put("wifi_card_first", sIsWifiFirst)

            val sb = StringBuilder()
            for (spec in sExpandedTileSpecs) {
                if (sb.isNotEmpty()) sb.append(",")
                sb.append(spec)
            }
            json.put("expanded_tile_specs", sb.toString())

            val vertSb = StringBuilder()
            for (spec in sVerticalTileSpecs) {
                if (vertSb.isNotEmpty()) vertSb.append(",")
                vertSb.append(spec)
            }
            json.put("vertical_tile_specs", vertSb.toString())

            val largeSb = StringBuilder()
            for (spec in sLargeTileSpecs) {
                if (largeSb.isNotEmpty()) largeSb.append(",")
                largeSb.append(spec)
            }
            json.put("large_tile_specs", largeSb.toString())

            val circCardSb = StringBuilder()
            for (spec in sCircularCardSpecs) {
                if (circCardSb.isNotEmpty()) circCardSb.append(",")
                circCardSb.append(spec)
            }
            json.put("circular_card_specs", circCardSb.toString())

            val removedSb = StringBuilder()
            for (spec in sRemovedComponentSpecs) {
                if (removedSb.isNotEmpty()) removedSb.append(",")
                removedSb.append(spec)
            }
            json.put("removed_component_specs", removedSb.toString())

            json.put("media_player_size", sMediaPlayerSize)
            json.put("brightness_slider_horizontal", sBrightnessSliderHorizontal)
            json.put("volume_slider_horizontal", sVolumeSliderHorizontal)
            json.put("brightness_slider_width", sBrightnessSliderWidth)
            json.put("volume_slider_width", sVolumeSliderWidth)
            json.put("landscape_right_max_rows", landscapeRightMaxRows)

            val groupsSb = StringBuilder()
            for (g in sTileGroups) {
                if (g.specs.size >= 2) {
                    if (groupsSb.isNotEmpty()) groupsSb.append(";")
                    groupsSb.append(g.id).append(":").append(if (g.isVertical) "V" else "H").append(":")
                    for (i in 0 until g.specs.size) {
                        if (i > 0) groupsSb.append(",")
                        groupsSb.append(g.specs[i])
                    }
                    groupsSb.append(":").append(g.layoutType).append(":").append(g.cardSpec)
                    groupsSb.append(":").append(if (g.isTransparent) "T" else "F")
                    groupsSb.append(":").append(g.getEffectiveRowCount())
                    groupsSb.append(":").append(g.tileSizes)
                    groupsSb.append(":").append(g.getEffectiveColCount())
                }
            }
            json.put("tile_groups_v2", groupsSb.toString())

            val jsonStr = json.toString()
            val prioSnapshot = HashMap(sComponentPriorities)
            val wifiFirstSnapshot = sIsWifiFirst
            val expandedStr = sb.toString()
            val vertStr = vertSb.toString()
            val largeStr = largeSb.toString()
            val circCardStr = circCardSb.toString()
            val removedStr = removedSb.toString()
            val mediaSizeSnapshot = sMediaPlayerSize
            val bHoriz = sBrightnessSliderHorizontal
            val vHoriz = sVolumeSliderHorizontal
            val bWidth = sBrightnessSliderWidth
            val vWidth = sVolumeSliderWidth
            val landRows = landscapeRightMaxRows
            val groupsStr = groupsSb.toString()

            sIoExecutor.execute {
                try {
                    // 1. Settings.Secure
                    try {
                        android.provider.Settings.Secure.putString(targetCtx.contentResolver, "sysui_hyperbounce_cc_layout", jsonStr)
                        android.provider.Settings.Secure.putString(targetCtx.contentResolver, "hyperbounce_cc_layout", jsonStr)
                    } catch (t: Throwable) {}

                    // 2. ContentProvider
                    try {
                        targetCtx.contentResolver.call(
                            Uri.parse("content://dev.hyperios.app.provider"),
                            "saveCCLayout", jsonStr, null
                        )
                    } catch (t: Throwable) {}

                    // 3. SharedPreferences Cache
                    val prefs = targetCtx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE)
                    if (prefs != null) {
                        val editor = prefs.edit()
                        for ((k, v) in prioSnapshot) {
                            editor.putInt("priority_$k", v)
                        }
                        editor.putBoolean("wifi_card_first", wifiFirstSnapshot)
                        editor.putString("expanded_tile_specs", expandedStr)
                        editor.putString("vertical_tile_specs", vertStr)
                        editor.putString("large_tile_specs", largeStr)
                        editor.putString("circular_card_specs", circCardStr)
                        editor.putString("removed_component_specs", removedStr)
                        editor.putString("media_player_size", mediaSizeSnapshot)
                        editor.putBoolean("brightness_slider_horizontal", bHoriz)
                        editor.putBoolean("volume_slider_horizontal", vHoriz)
                        editor.putInt("brightness_slider_width", bWidth)
                        editor.putInt("volume_slider_width", vWidth)
                        editor.putInt("landscape_right_max_rows", landRows)
                        editor.putString("tile_groups_v2", groupsStr)
                        editor.apply()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "async savePriorities error", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "savePriorities error", t)
        }
    }

    @Synchronized
    fun resetLayoutToStock(ctx: Context?) {
        if (ctx == null) return
        try {
            sExplicitReset = true
            try {
                android.provider.Settings.Secure.putString(ctx.contentResolver, "sysui_hyperbounce_cc_layout", "")
                android.provider.Settings.Secure.putString(ctx.contentResolver, "hyperbounce_cc_layout", "")
            } catch (t: Throwable) {}
            try {
                ctx.contentResolver.call(
                    Uri.parse("content://dev.hyperios.app.provider"),
                    "resetCCLayout", null, null
                )
            } catch (t: Throwable) {}
            clearPendingReset(ctx)

            val prefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE)
            prefs?.edit()?.clear()?.apply()

            sRemovedComponentSpecs.clear()
            sTileGroups.clear()
            sExpandedTileSpecs.clear()
            sExpandedTileSpecs.add("wifi")
            sExpandedTileSpecs.add("cell")
            sVerticalTileSpecs.clear()
            sLargeTileSpecs.clear()
            sCircularCardSpecs.clear()
            config.landscapeRightMaxRows = 3
            sMediaPlayerSize = "2x2"
            sBrightnessSliderHorizontal = false
            sVolumeSliderHorizontal = false
            sBrightnessSliderWidth = 2
            sVolumeSliderWidth = 2
            sIsWifiFirst = true
            sComponentPriorities.clear()
            sComponentPriorities["CompactQS"] = 0
            sComponentPriorities["CompactQSCard"] = 1
            sComponentPriorities["QSCardsController"] = 20
            sComponentPriorities["MediaPlayerController"] = 30
            sComponentPriorities["BrightnessSliderController"] = 31
            sComponentPriorities["VolumeSliderController"] = 32
            sComponentPriorities["DeviceCenterEntryController"] = 50
            sComponentPriorities["DeviceControlsEntryController"] = 51
            sComponentPriorities["QSListController"] = 100
            ControlCenterFullUnlock.resetOrder(ctx)

            savePriorities(ctx)
            sExplicitReset = false
            Log.i(TAG, "Control Center Layout successfully reset to stock!")
        } catch (t: Throwable) {
            Log.e(TAG, "resetLayoutToStock error", t)
        }
    }

    fun isSafeMode(ctx: Context?): Boolean {
        sCachedSafeMode?.let { return it }
        if (ctx == null) return false
        return try {
            val res = ctx.contentResolver.call(
                Uri.parse("content://dev.hyperios.app.provider"),
                "isSafeMode", null, null
            )
            val isSafe = res?.getBoolean("safeMode", false) ?: false
            sCachedSafeMode = isSafe
            isSafe
        } catch (t: Throwable) {
            sCachedSafeMode = false
            false
        }
    }

    fun tripSafeMode(ctx: Context?, e: Throwable) {
        Log.e(TAG, "CRITICAL ERROR IN CONTROLCENTER HOOK - TRIPPING SAFE MODE", e)
        sCachedSafeMode = true
        if (ctx == null) return
        try {
            ctx.contentResolver.call(
                Uri.parse("content://dev.hyperios.app.provider"),
                "setSafeMode", "true", null
            )
        } catch (t: Throwable) {}
    }

    fun getTileArea(spec: String?): Int {
        if (spec == null) return 1
        val isCard = ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true))
        val isCircCard = isCard && sCircularCardSpecs.contains(spec)
        val isExp = sExpandedTileSpecs.contains(spec) || (isCard && !isCircCard && !sVerticalTileSpecs.contains(spec) && !sLargeTileSpecs.contains(spec))
        val isVert = sVerticalTileSpecs.contains(spec)
        return if (isExp || isVert) 2 else 1
    }

    fun isGroupAnchor(spec: String?): TileGroup? {
        if (spec == null) return null
        if (!sHasLoadedPriorities && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
        for (g in sTileGroups) {
            if (g.specs.size >= 2 && spec == g.specs[0]) {
                return g
            }
        }
        return null
    }

    fun isGroupChild(spec: String?): Boolean {
        if (spec == null) return false
        if (!sHasLoadedPriorities && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
        for (g in sTileGroups) {
            if (g.specs.size >= 2) {
                for (i in 1 until g.specs.size) {
                    if (spec == g.specs[i]) return true
                }
            }
        }
        return false
    }

    fun findGroupForSpec(spec: String?): TileGroup? {
        if (spec == null) return null
        if (!sHasLoadedPriorities && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
        for (g in sTileGroups) {
            if (g.specs.contains(spec)) return g
        }
        return null
    }

    fun isGroupMember(spec: String?): Boolean {
        return findGroupForSpec(spec) != null
    }

    fun getTileGroupBySpec(spec: String?): TileGroup? {
        if (spec == null) return null
        for (g in sTileGroups) {
            if (g.specs.contains(spec)) return g
        }
        return null
    }

    fun getRecordSpec(r: Any?): String? {
        if (r == null) return null
        return try {
            val m = r.javaClass.getMethod("getSpec")
            m.invoke(r) as? String
        } catch (t: Throwable) {
            try {
                val f = r.javaClass.getDeclaredField("spec")
                f.isAccessible = true
                f.get(r) as? String
            } catch (t2: Throwable) {
                try {
                    val getTileM = r.javaClass.getMethod("getTile")
                    val tileObj = getTileM.invoke(r)
                    if (tileObj != null) {
                        val getSpecM = tileObj.javaClass.getMethod("getTileSpec")
                        getSpecM.invoke(tileObj) as? String
                    } else null
                } catch (t3: Throwable) {
                    null
                }
            }
        }
    }

    private var sLastWakeTimestamp: Long = 0L

    fun wakeAndRefreshAllGroupedTiles() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - sLastWakeTimestamp < 250L) {
            return
        }
        sLastWakeTimestamp = now

        try {
            val rv = sRecyclerView?.get() as? ViewGroup
            if (rv != null) {
                for (i in 0 until rv.childCount) {
                    val container = rv.getChildAt(i)?.findViewById<ViewGroup>(0x7f099995)
                    if (container != null) {
                        container.visibility = View.VISIBLE
                        for (cIdx in 0 until container.childCount) {
                            val cv = container.getChildAt(cIdx)
                            if (cv != null) {
                                cv.visibility = View.VISIBLE
                                if (!sIsEditMode) {
                                    cv.alpha = 1.0f
                                }
                            }
                        }
                    }
                }
            }

            for (r in sAllQSRecords) {
                if (r != null) {
                    val spec = getRecordSpec(r)
                    if (isGroupMember(spec)) {
                        try {
                            if (GroupedTileReflectionCache.startListeningMethod == null) {
                                GroupedTileReflectionCache.startListeningMethod = r.javaClass.getMethod("startListening")
                            }
                            GroupedTileReflectionCache.startListeningMethod?.invoke(r)
                        } catch (t: Throwable) {}

                        try {
                            if (GroupedTileReflectionCache.getTileMethod == null) {
                                GroupedTileReflectionCache.getTileMethod = r.javaClass.getMethod("getTile")
                            }
                            val tileObj = GroupedTileReflectionCache.getTileMethod?.invoke(r)
                            if (tileObj != null) {
                                if (GroupedTileReflectionCache.setListeningMethod == null) {
                                    GroupedTileReflectionCache.setListeningMethod = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                }
                                GroupedTileReflectionCache.setListeningMethod?.invoke(tileObj, r, true)

                                if (GroupedTileReflectionCache.refreshStateMethod == null) {
                                    GroupedTileReflectionCache.refreshStateMethod = tileObj.javaClass.getMethod("refreshState")
                                }
                                GroupedTileReflectionCache.refreshStateMethod?.invoke(tileObj)

                                if (GroupedTileReflectionCache.getStateMethod == null) {
                                    GroupedTileReflectionCache.getStateMethod = tileObj.javaClass.getMethod("getState")
                                }
                                val curState = GroupedTileReflectionCache.getStateMethod?.invoke(tileObj)

                                if (GroupedTileReflectionCache.getHolderMethod == null) {
                                    GroupedTileReflectionCache.getHolderMethod = r.javaClass.getMethod("getHolder")
                                }
                                var holder = GroupedTileReflectionCache.getHolderMethod?.invoke(r)

                                if (holder == null && rv != null && spec != null) {
                                    val group = findGroupForSpec(spec)
                                    if (group != null) {
                                        val anchorSpec = group.specs.firstOrNull()
                                        for (i in 0 until rv.childCount) {
                                            val child = rv.getChildAt(i)
                                            if (child != null && (child.getTag(0x7f099990) == anchorSpec || child.getTag(0x7f099994) == group)) {
                                                val subHolders = child.getTag(0x7f099993) as? Array<*>
                                                val idx = group.specs.indexOf(spec)
                                                if (subHolders != null && idx in subHolders.indices && subHolders[idx] != null) {
                                                    holder = subHolders[idx]
                                                    if (GroupedTileReflectionCache.setHolderMethod == null) {
                                                        for (m in r.javaClass.methods) {
                                                            if (m.name == "setHolder" && m.parameterTypes.size == 1) {
                                                                GroupedTileReflectionCache.setHolderMethod = m
                                                                break
                                                            }
                                                        }
                                                    }
                                                    GroupedTileReflectionCache.setHolderMethod?.invoke(r, holder)
                                                }
                                                break
                                            }
                                        }
                                    }
                                }

                                if (holder != null && curState != null) {
                                    if (GroupedTileReflectionCache.itemViewField == null) {
                                        var c: Class<*>? = holder.javaClass
                                        while (c != null) {
                                            try {
                                                val f = c.getDeclaredField("itemView")
                                                f.isAccessible = true
                                                GroupedTileReflectionCache.itemViewField = f
                                                break
                                            } catch (t: Throwable) { c = c.superclass }
                                        }
                                    }
                                    val childView = GroupedTileReflectionCache.itemViewField?.get(holder) as? View
                                    if (childView != null) {
                                        childView.visibility = View.VISIBLE
                                        if (!sIsEditMode) {
                                            childView.alpha = 1.0f
                                        }
                                        val vClass = childView.javaClass
                                        if (GroupedTileReflectionCache.lastChildViewClass != vClass) {
                                            GroupedTileReflectionCache.lastChildViewClass = vClass
                                            GroupedTileReflectionCache.updateStateMethod = null
                                            for (vm in vClass.methods) {
                                                if (vm.name == "updateState" && vm.parameterTypes.size == 3) {
                                                    vm.isAccessible = true
                                                    GroupedTileReflectionCache.updateStateMethod = vm
                                                    break
                                                }
                                            }
                                        }
                                        val isConnected = isTileConnected(tileObj)
                                        GroupedTileReflectionCache.updateStateMethod?.invoke(childView, curState, isConnected, false)
                                    }
                                }
                            }
                        } catch (t: Throwable) {}
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wakeAndRefreshAllGroupedTiles error", t)
        }
    }

    fun isTileConnected(tile: Any?): Boolean {
        if (tile == null) return false
        return try {
            val m = tile.javaClass.getMethod("isConnected")
            m.invoke(tile) as? Boolean ?: false
        } catch (t: Throwable) {
            false
        }
    }

    fun getOrResolveTileSpec(itemView: View): String? {
        val cached = itemView.getTag(0x7f099990) as? String
        if (cached != null) return cached

        // Check for media player
        if (itemView.javaClass.name.contains("MediaPlayer") ||
            itemView.findViewById<View?>(itemView.resources.getIdentifier("audio_container", "id", itemView.context.packageName)) != null) {
            itemView.setTag(0x7f099990, "media")
            return "media"
        }

        // Check for sliders
        if (itemView.javaClass.name.contains("ToggleSlider") ||
            itemView.findViewById<View?>(itemView.resources.getIdentifier("slider", "id", itemView.context.packageName)) != null) {
            val p = itemView.parent
            if (p != null && p.javaClass.name.contains("RecyclerView")) {
                try {
                    val getChildVhMethod = p.javaClass.getMethod("getChildViewHolder", View::class.java)
                    val vh = getChildVhMethod.invoke(p, itemView)
                    if (vh != null) {
                        val vhName = vh.javaClass.name
                        if (vhName.contains("Brightness")) {
                            itemView.setTag(0x7f099990, "brightness")
                            return "brightness"
                        }
                        if (vhName.contains("Volume")) {
                            itemView.setTag(0x7f099990, "volume")
                            return "volume"
                        }
                    }
                } catch (t: Throwable) {}
            }
        }

        try {
            var cls: Class<*>? = itemView.javaClass
            while (cls != null && cls != Any::class.java) {
                for (f in cls.declaredFields) {
                    val fn = f.name
                    if (fn == "spec" || fn == "mSpec" || fn.contains("spec", ignoreCase = true)) {
                        f.isAccessible = true
                        val s = f.get(itemView) as? String
                        if (!s.isNullOrEmpty()) {
                            itemView.setTag(0x7f099990, s)
                            return s
                        }
                    }
                    if (fn == "record" || fn == "mRecord" || fn.contains("item", ignoreCase = true) || fn.contains("record", ignoreCase = true)) {
                        f.isAccessible = true
                        val rec = f.get(itemView)
                        if (rec != null) {
                            val s = getRecordSpec(rec)
                            if (!s.isNullOrEmpty()) {
                                itemView.setTag(0x7f099990, s)
                                return s
                            }
                        }
                    }
                }
                for (m in cls.declaredMethods) {
                    val mn = m.name
                    if ((mn.startsWith("getSpec") || mn == "getSpec") && m.parameterTypes.isEmpty()) {
                        m.isAccessible = true
                        val s = m.invoke(itemView) as? String
                        if (!s.isNullOrEmpty()) {
                            itemView.setTag(0x7f099990, s)
                            return s
                        }
                    }
                    if ((mn.startsWith("getItem") || mn.startsWith("getRecord")) && m.parameterTypes.isEmpty()) {
                        m.isAccessible = true
                        val rec = m.invoke(itemView)
                        if (rec != null) {
                            val s = getRecordSpec(rec)
                            if (!s.isNullOrEmpty()) {
                                itemView.setTag(0x7f099990, s)
                                return s
                            }
                        }
                    }
                }
                cls = cls.superclass
            }
        } catch (t: Throwable) {}

        try {
            val p = itemView.parent
            if (p != null && p.javaClass.name.contains("RecyclerView")) {
                val getChildVhMethod = p.javaClass.getMethod("getChildViewHolder", View::class.java)
                val vh = getChildVhMethod.invoke(p, itemView)
                if (vh != null) {
                    var vhCls: Class<*>? = vh.javaClass
                    while (vhCls != null && vhCls != Any::class.java) {
                        for (f in vhCls.declaredFields) {
                            val fn = f.name
                            if (fn == "record" || fn == "mRecord" || fn.contains("item", ignoreCase = true) || fn.contains("record", ignoreCase = true)) {
                                f.isAccessible = true
                                val rec = f.get(vh)
                                if (rec != null) {
                                    val s = getRecordSpec(rec)
                                    if (!s.isNullOrEmpty()) {
                                        itemView.setTag(0x7f099990, s)
                                        return s
                                    }
                                }
                            }
                        }
                        for (m in vhCls.declaredMethods) {
                            val mn = m.name
                            if ((mn.startsWith("getSpec") || mn == "getSpec") && m.parameterTypes.isEmpty()) {
                                m.isAccessible = true
                                val s = m.invoke(vh) as? String
                                if (!s.isNullOrEmpty()) {
                                    itemView.setTag(0x7f099990, s)
                                    return s
                                }
                            }
                            if ((mn.startsWith("getItem") || mn.startsWith("getRecord")) && m.parameterTypes.isEmpty()) {
                                m.isAccessible = true
                                val rec = m.invoke(vh)
                                if (rec != null) {
                                    val s = getRecordSpec(rec)
                                    if (!s.isNullOrEmpty()) {
                                        itemView.setTag(0x7f099990, s)
                                        return s
                                    }
                                }
                            }
                        }
                        vhCls = vhCls.superclass
                    }
                }
            }
        } catch (t: Throwable) {}

        return null
    }

    fun isConstraintLayout(view: View?): Boolean {
        var c: Class<*>? = view?.javaClass
        while (c != null && c != Any::class.java) {
            if (c.name.contains("ConstraintLayout")) return true
            c = c.superclass
        }
        return false
    }

    fun createMediaBadgeLayoutParams(parent: ViewGroup, badgeSize: Int): ViewGroup.LayoutParams {
        if (isConstraintLayout(parent)) {
            try {
                val clpClass = parent.javaClass.classLoader?.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                    ?: Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                val constructor = clpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                val lp = constructor.newInstance(badgeSize, badgeSize) as ViewGroup.MarginLayoutParams
                val parentIdField = clpClass.getField("PARENT_ID")
                val parentId = parentIdField.getInt(null)
                clpClass.getField("bottomToBottom").setInt(lp, parentId)
                clpClass.getField("endToEnd").setInt(lp, parentId)
                return lp
            } catch (t: Throwable) {
                Log.e(TAG, "Error creating ConstraintLayout.LayoutParams via reflection", t)
            }
        }
        return FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
    }

    fun updateAllVisibleTileBadges() {
        var rv = (sRecyclerView?.get() as? ViewGroup)
        if (rv == null) {
            try {
                val adp = sMainPanelAdapter
                if (adp != null) {
                    val f = adp.javaClass.getDeclaredField("recyclerView")
                    f.isAccessible = true
                    rv = f.get(adp) as? ViewGroup
                    if (rv != null) {
                        sRecyclerView = WeakReference(rv)
                    }
                }
            } catch (t: Throwable) {}
        }
        val targetRv = rv ?: return
        targetRv.post {
            try {
                val rvs = mutableListOf<ViewGroup>(targetRv)
                val decor = targetRv.rootView
                if (decor != null) {
                    val ctx = targetRv.context
                    val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
                    for (pIdStr in panelIds) {
                        val rvId = ctx.resources.getIdentifier(pIdStr, "id", ctx.packageName)
                        if (rvId != 0) {
                            val pRv = decor.findViewById<View>(rvId) as? ViewGroup
                            if (pRv != null && !rvs.contains(pRv)) {
                                rvs.add(pRv)
                            }
                        }
                    }
                }
                for (pRv in rvs) {
                    val childCount = pRv.childCount
                    for (i in 0 until childCount) {
                        val itemView = pRv.getChildAt(i) ?: continue
                        try {
                            if (ControlCenterHook.isChildOfContainer(itemView)) continue

                    val spec = getOrResolveTileSpec(itemView) ?: continue
                    val isCard = ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true))
                    val isSlider = ("brightness".equals(spec, ignoreCase = true) || "volume".equals(spec, ignoreCase = true))
                    val isMedia = ("media".equals(spec, ignoreCase = true))

                    val isExpanded = sExpandedTileSpecs.contains(spec)
                    val isVertical = sVerticalTileSpecs.contains(spec)
                    val isLarge = sLargeTileSpecs.contains(spec)
                    val isCircCard = isCard && sCircularCardSpecs.contains(spec)
                    val isCircular = isCircCard || (!isCard && !isSlider && !isMedia && !isExpanded && !isVertical && !isLarge && !itemView.javaClass.name.contains("Card") && (itemView.javaClass.name.contains("Tile") || itemView.javaClass.name.contains("QSTileItemView")))
                    val density = itemView.resources.displayMetrics.density

                    val iconFrameId = itemView.resources.getIdentifier("icon_frame", "id", itemView.context.packageName)
                    val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null

                    val resizeTag = 0x7f099999
                    val selectTag = 0x7f099998
                    val rotateTag = 0x7f099992
                    var resizeHandle = itemView.findViewById<View>(resizeTag) as? ImageView
                    var selectBadge = itemView.findViewById<View>(selectTag) as? ImageView
                    var rotateBtn = itemView.findViewById<View>(rotateTag) as? ImageView

                    if (!sIsEditMode || !config.isModularEditActive) {
                        ControlCenterHook.hideAllCustomBadges(itemView)
                        hideAllRotateButtons(itemView as? ViewGroup)
                        itemView.isClickable = true
                        itemView.isLongClickable = true
                        itemView.isFocusable = true
                        if (isCircular) {
                            ControlCenterHook.setCircularTileLabelVisibility(itemView, true)
                        }
                        val container = itemView.findViewById<ViewGroup>(0x7f099995)
                        if (container != null) {
                            container.isClickable = false
                            for (cIdx in 0 until 4) {
                                val child = ControlCenterGrouping.getChildTileAt(container, cIdx)
                                if (child != null) {
                                    ControlCenterHook.hideAllCustomBadges(child)
                                    child.isClickable = true
                                    child.isLongClickable = true
                                    child.isFocusable = true
                                }
                            }
                        }
                        continue
                    }

                    if (isCircular) {
                        ControlCenterHook.setCircularTileLabelVisibility(itemView, false)
                    }

                    // Special Handling for Sliders
                    if (isSlider) {
                        val isAdded = !sRemovedComponentSpecs.contains(spec)
                        ControlCenterHook.applyUniversalMarkButton(itemView, spec, isAdded)
                        if (selectBadge != null) selectBadge.visibility = View.GONE

                        if (rotateBtn == null && itemView is ViewGroup) {
                            val rSize = (26 * density).toInt()
                            rotateBtn = ImageView(itemView.context).apply {
                                id = rotateTag
                                val lp = android.widget.FrameLayout.LayoutParams(rSize, rSize).apply {
                                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                    val m = (6 * density).toInt()
                                    setMargins(m, m, 0, 0)
                                }
                                layoutParams = lp
                                setImageDrawable(RotateIconDrawable(density))
                            }
                            itemView.addView(rotateBtn)
                        } else if (rotateBtn != null) {
                            val lp = rotateBtn.layoutParams as? android.widget.FrameLayout.LayoutParams
                            if (lp != null && lp.gravity != (android.view.Gravity.TOP or android.view.Gravity.START)) {
                                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                val m = (6 * density).toInt()
                                lp.setMargins(m, m, 0, 0)
                                rotateBtn.layoutParams = lp
                            }
                        }
                        val isBright = "brightness".equals(spec, ignoreCase = true)
                        val isHoriz = if (isBright) sBrightnessSliderHorizontal else sVolumeSliderHorizontal
                        if (rotateBtn != null) {
                            val shouldShowRotate = isAdded && !sIsGroupSelectMode && !sIsDisassembleMode
                            rotateBtn.visibility = if (shouldShowRotate) View.VISIBLE else View.GONE
                            if (shouldShowRotate) {
                                rotateBtn.bringToFront()
                                rotateBtn.isClickable = true
                                rotateBtn.setOnClickListener {
                                    toggleSliderOrientation(itemView.context, isBright, itemView)
                                }
                            } else {
                                rotateBtn.isClickable = false
                                rotateBtn.setOnClickListener(null)
                            }
                        }

                        // Resize Handle for Sliders (Only when horizontal!)
                        if (isHoriz) {
                            val badgeSize = (38 * density).toInt()
                            if (resizeHandle == null && itemView is ViewGroup) {
                                resizeHandle = ImageView(itemView.context).apply {
                                    id = resizeTag
                                    layoutParams = createMediaBadgeLayoutParams(itemView, badgeSize)
                                    setImageDrawable(CurvedResizeHandleDrawable(density))
                                }
                                itemView.addView(resizeHandle)
                            } else if (resizeHandle != null && isConstraintLayout(itemView) && !resizeHandle.layoutParams.javaClass.name.contains("ConstraintLayout")) {
                                resizeHandle.layoutParams = createMediaBadgeLayoutParams(itemView as ViewGroup, badgeSize)
                            }
                            if (resizeHandle != null) {
                                val shouldShowResize = isAdded && !sIsGroupSelectMode && !sIsDisassembleMode
                                resizeHandle.visibility = if (shouldShowResize) View.VISIBLE else View.GONE
                                if (shouldShowResize) {
                                    val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                    val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                    if (itemView.width > 0 && itemView.height > 0) {
                                        resizeHandle.layout(itemView.width - bw, itemView.height - bh, itemView.width, itemView.height)
                                    }
                                    resizeHandle.bringToFront()
                                    resizeHandle.isClickable = true
                                    resizeHandle.setOnClickListener {
                                        cycleSliderWidth(itemView.context, isBright, itemView)
                                    }
                                    FluidTileResizeHandler.attach(resizeHandle, itemView.context, if (isBright) "brightness" else "volume", itemView, density)
                                } else {
                                    resizeHandle.isClickable = false
                                    resizeHandle.setOnClickListener(null)
                                    resizeHandle.setOnTouchListener(null)
                                }
                            }
                        } else {
                            if (resizeHandle != null) {
                                resizeHandle.visibility = View.GONE
                                resizeHandle.isClickable = false
                                resizeHandle.setOnClickListener(null)
                                resizeHandle.setOnTouchListener(null)
                                (itemView as? ViewGroup)?.removeView(resizeHandle)
                            }
                        }

                        continue
                    }

                    // Special Handling for Media Player
                    if (isMedia) {
                        val isAdded = !sRemovedComponentSpecs.contains("media")
                        ControlCenterHook.applyUniversalMarkButton(itemView, "media", isAdded)
                        if (rotateBtn != null) rotateBtn.visibility = View.GONE
                        if (selectBadge != null) selectBadge.visibility = View.GONE

                        val badgeSize = (38 * density).toInt()
                        if (resizeHandle == null && itemView is ViewGroup) {
                            resizeHandle = ImageView(itemView.context).apply {
                                id = resizeTag
                                layoutParams = createMediaBadgeLayoutParams(itemView, badgeSize)
                                setImageDrawable(CurvedResizeHandleDrawable(density))
                            }
                            itemView.addView(resizeHandle)
                        } else if (resizeHandle != null && isConstraintLayout(itemView) && !resizeHandle.layoutParams.javaClass.name.contains("ConstraintLayout")) {
                            resizeHandle.layoutParams = createMediaBadgeLayoutParams(itemView as ViewGroup, badgeSize)
                        }
                        if (resizeHandle != null) {
                            val shouldShowMediaResize = isAdded && !sIsGroupSelectMode && !sIsDisassembleMode
                            resizeHandle.visibility = if (shouldShowMediaResize) View.VISIBLE else View.GONE
                            if (shouldShowMediaResize) {
                                val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                if (itemView.width > 0 && itemView.height > 0) {
                                    resizeHandle.layout(itemView.width - bw, itemView.height - bh, itemView.width, itemView.height)
                                }
                                resizeHandle.bringToFront()
                                resizeHandle.isClickable = true
                                resizeHandle.setOnClickListener {
                                    cycleMediaPlayerSize(itemView.context, itemView)
                                }
                                FluidTileResizeHandler.attach(resizeHandle, itemView.context, "media", itemView, density)
                            } else {
                                resizeHandle.isClickable = false
                                resizeHandle.setOnClickListener(null)
                                resizeHandle.setOnTouchListener(null)
                            }
                        }
                        continue
                    }

                    // Handling for standard tiles & cards (Wi-Fi, Cell, normal tiles, and groups)
                    val group = isGroupAnchor(spec)
                    if (group != null) {
                        ControlCenterGrouping.applyGroupContainerStyle(itemView, itemView.context.classLoader, group)
                        val wrapper = itemView.findViewById<View>(0x7f099996)
                        if (!sIsGroupSelectMode && !sIsDisassembleMode) {
                            val toggleTransparent = View.OnClickListener {
                                group.isTransparent = !group.isTransparent
                                ControlCenterGrouping.applyGroupContainerStyle(itemView, itemView.context.classLoader, group)
                                savePriorities(itemView.context)
                                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            itemView.setOnClickListener(toggleTransparent)
                            itemView.isClickable = true
                            wrapper?.setOnClickListener(toggleTransparent)
                            wrapper?.isClickable = true
                        }
                        if (rotateBtn == null && itemView is ViewGroup) {
                            val rSize = (26 * density).toInt()
                            rotateBtn = ImageView(itemView.context).apply {
                                id = rotateTag
                                val lp = android.widget.FrameLayout.LayoutParams(rSize, rSize).apply {
                                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                                    val m = (6 * density).toInt()
                                    setMargins(0, m, m, 0)
                                }
                                layoutParams = lp
                                setImageDrawable(RotateIconDrawable(density))
                            }
                            itemView.addView(rotateBtn)
                        }
                        if (rotateBtn != null) {
                            val isIosCluster = group.isIosFolderCluster()
                            val shouldShowRotate = !sIsGroupSelectMode && !sIsDisassembleMode && !isIosCluster
                            rotateBtn.visibility = if (shouldShowRotate) View.VISIBLE else View.GONE
                            if (shouldShowRotate) {
                                rotateBtn.bringToFront()
                                rotateBtn.isClickable = true
                                rotateBtn.setOnClickListener {
                                    ControlCenterGrouping.rotateTileGroup(group, itemView.context, itemView)
                                }
                            } else {
                                rotateBtn.isClickable = false
                                rotateBtn.setOnClickListener(null)
                            }
                        }
                    } else {
                        if (rotateBtn != null) rotateBtn.visibility = View.GONE
                    }

                    if (sIsGroupSelectMode) {
                        if (resizeHandle != null) {
                            resizeHandle.visibility = View.GONE
                            resizeHandle.setOnTouchListener(null)
                            resizeHandle.setOnClickListener(null)
                            resizeHandle.isClickable = false
                        }
                        val isGroupable = !isSlider && !isMedia && !isLarge && (isGroupAnchor(spec) == null) && !isGroupChild(spec)
                        if (isGroupable) {
                            if (itemView is ViewGroup) {
                                itemView.clipChildren = false
                                itemView.clipToPadding = false
                            }
                            if (selectBadge == null && itemView is ViewGroup) {
                                selectBadge = ImageView(itemView.context).apply {
                                    id = selectTag
                                    val badgeSize2 = (26 * density).toInt()
                                    val lp = android.widget.FrameLayout.LayoutParams(badgeSize2, badgeSize2).apply {
                                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                        val margin = (4 * density).toInt()
                                        setMargins(margin, margin, 0, 0)
                                    }
                                    layoutParams = lp
                                    setImageDrawable(SelectCircleDrawable())
                                }
                                itemView.addView(selectBadge)
                            }

                            if (itemView.getTag(0x7f099991) == null) {
                                itemView.setTag(0x7f099991, true)
                                itemView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                                    val curHandle = itemView.findViewById<ImageView>(resizeTag)
                                    val curBadge = itemView.findViewById<ImageView>(selectTag)
                                    if (!sIsEditMode) {
                                        curHandle?.visibility = View.GONE
                                        curBadge?.visibility = View.GONE
                                        return@addOnLayoutChangeListener
                                    }
                                    val w = r - l
                                    val h = b - t
                                    if (w > 0 && h > 0) {
                                        val curIconF = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null
                                        if (sIsGroupSelectMode) {
                                            if (curBadge != null && curBadge.visibility == View.VISIBLE) {
                                                val bw2 = (26 * density).toInt()
                                                val bh2 = (26 * density).toInt()
                                                if (isCircular && curIconF != null && curIconF.width > 0 && curIconF.height > 0) {
                                                    val iLeft = curIconF.left
                                                    val iTop = curIconF.top
                                                    curBadge.layout(iLeft, iTop, iLeft + bw2, iTop + bh2)
                                                } else {
                                                    val m = (8 * density).toInt()
                                                    curBadge.layout(m, m, m + bw2, m + bh2)
                                                }
                                                curBadge.bringToFront()
                                            }
                                        } else if (curHandle != null && curHandle.visibility == View.VISIBLE) {
                                            val bw = curHandle.measuredWidth.takeIf { it > 0 } ?: (38 * density).toInt()
                                            val bh = curHandle.measuredHeight.takeIf { it > 0 } ?: (38 * density).toInt()
                                            if (isCircular && curIconF != null && curIconF.width > 0 && curIconF.height > 0) {
                                                val iRight = curIconF.right
                                                val iBottom = curIconF.bottom
                                                curHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                            } else {
                                                curHandle.layout(w - bw, h - bh, w, h)
                                            }
                                            curHandle.bringToFront()
                                        }
                                    }
                                }
                            }

                            if (selectBadge != null) {
                                val scd = (selectBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                scd.setChecked(sSelectedTileSpecs.contains(spec))
                                selectBadge.setImageDrawable(scd)
                                selectBadge.visibility = View.VISIBLE
                                selectBadge.bringToFront()
                                selectBadge.isClickable = true

                                val toggleSelect = View.OnClickListener {
                                    if (sSelectedTileSpecs.contains(spec)) {
                                        sSelectedTileSpecs.remove(spec)
                                    } else {
                                        val currentArea = sSelectedTileSpecs.sumOf { getTileArea(it) }
                                        val tileArea = getTileArea(spec)
                                        if (currentArea + tileArea <= 12) {
                                            sSelectedTileSpecs.add(spec)
                                        } else {
                                            it.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                                            return@OnClickListener
                                        }
                                    }
                                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    val curScd = selectBadge.drawable as? SelectCircleDrawable
                                    curScd?.setChecked(sSelectedTileSpecs.contains(spec))
                                    selectBadge.invalidate()
                                }
                                selectBadge.setOnClickListener(toggleSelect)

                                val bw2 = (26 * density).toInt()
                                val bh2 = (26 * density).toInt()
                                if (isCircular && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                    val iLeft = curIconFrame.left
                                    val iTop = curIconFrame.top
                                    selectBadge.layout(iLeft, iTop, iLeft + bw2, iTop + bh2)
                                } else {
                                    val m = (8 * density).toInt()
                                    selectBadge.layout(m, m, m + bw2, m + bh2)
                                }
                                selectBadge.bringToFront()
                            }
                        } else if (selectBadge != null) {
                            selectBadge.visibility = View.GONE
                            selectBadge.setOnClickListener(null)
                            selectBadge.isClickable = false
                        }
                    } else if (sIsDisassembleMode) {
                        if (resizeHandle != null) {
                            resizeHandle.visibility = View.GONE
                            resizeHandle.setOnTouchListener(null)
                            resizeHandle.setOnClickListener(null)
                            resizeHandle.isClickable = false
                        }
                        val group = isGroupAnchor(spec)
                        if (group != null) {
                            if (selectBadge == null && itemView is ViewGroup) {
                                selectBadge = ImageView(itemView.context).apply {
                                    id = selectTag
                                    val badgeSize2 = (26 * density).toInt()
                                    val lp = android.widget.FrameLayout.LayoutParams(badgeSize2, badgeSize2).apply {
                                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                        val margin = (6 * density).toInt()
                                        setMargins(margin, margin, 0, 0)
                                    }
                                    layoutParams = lp
                                    setImageDrawable(SelectCircleDrawable())
                                }
                                itemView.addView(selectBadge)
                            }
                            if (selectBadge != null) {
                                val scd = (selectBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                scd.setChecked(sSelectedDisassembleGroups.contains(group.id))
                                selectBadge.setImageDrawable(scd)
                                selectBadge.visibility = View.VISIBLE
                                selectBadge.bringToFront()
                                selectBadge.isClickable = true

                                val toggleDisassembleSelect = View.OnClickListener {
                                    if (sSelectedDisassembleGroups.contains(group.id)) {
                                        sSelectedDisassembleGroups.remove(group.id)
                                    } else {
                                        sSelectedDisassembleGroups.add(group.id)
                                    }
                                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    val curScd = selectBadge.drawable as? SelectCircleDrawable
                                    curScd?.setChecked(sSelectedDisassembleGroups.contains(group.id))
                                    selectBadge.invalidate()
                                }
                                selectBadge.setOnClickListener(toggleDisassembleSelect)
                                val bw2 = selectBadge.measuredWidth.takeIf { it > 0 } ?: (26 * density).toInt()
                                val bh2 = selectBadge.measuredHeight.takeIf { it > 0 } ?: (26 * density).toInt()
                                val m = (6 * density).toInt()
                                selectBadge.layout(m, m, m + bw2, m + bh2)
                            }
                        } else if (selectBadge != null) {
                            selectBadge.visibility = View.GONE
                            selectBadge.setOnClickListener(null)
                            selectBadge.isClickable = false
                        }
                    } else {
                        // Normal Edit Mode
                        val isAdded = if (isCard) !sRemovedComponentSpecs.contains(spec) else isTileAdded(spec)
                        val record = findRecordForSpec(spec)
                        if (!isCircular) {
                            ControlCenterHook.applyUniversalMarkButton(itemView, spec, isAdded, record)
                        }

                        if (selectBadge != null) {
                            selectBadge.visibility = View.GONE
                            selectBadge.setOnClickListener(null)
                            selectBadge.isClickable = false
                        }
                        if (group != null) {
                            if (resizeHandle != null) {
                                resizeHandle.visibility = View.GONE
                                resizeHandle.setOnTouchListener(null)
                                resizeHandle.setOnClickListener(null)
                                resizeHandle.isClickable = false
                            }
                            continue
                        }
                        val badgeSize = (38 * density).toInt()
                        if (resizeHandle == null && itemView is ViewGroup) {
                            resizeHandle = ImageView(itemView.context).apply {
                                id = resizeTag
                                val lp = android.widget.FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                }
                                layoutParams = lp
                                setImageDrawable(CurvedResizeHandleDrawable(density))
                            }
                            itemView.addView(resizeHandle)
                        }
                        if (resizeHandle != null) {
                            if (isAdded) {
                                resizeHandle.visibility = View.VISIBLE
                                val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                if (isCircular && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                    val iRight = curIconFrame.right
                                    val iBottom = curIconFrame.bottom
                                    resizeHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                } else if (itemView.width > 0 && itemView.height > 0) {
                                    resizeHandle.layout(itemView.width - bw, itemView.height - bh, itemView.width, itemView.height)
                                }
                                resizeHandle.bringToFront()
                                resizeHandle.isClickable = true
                                FluidTileResizeHandler.attach(resizeHandle, itemView.context, spec, itemView, density)
                            } else {
                                resizeHandle.visibility = View.GONE
                                resizeHandle.isClickable = false
                                resizeHandle.setOnClickListener(null)
                                resizeHandle.setOnTouchListener(null)
                            }
                        }
                    }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error updating badges for child $i", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "updateAllVisibleTileBadges error", t)
        }
    }
}

    @Volatile private var sLastExitEditTime: Long = 0L

    fun cleanupAllVisibleViews(rv: ViewGroup?) {
        if (rv == null) return
        try {
            val childCount = rv.childCount
            for (i in 0 until childCount) {
                val itemView = rv.getChildAt(i) ?: continue
                ControlCenterHook.hideAllCustomBadges(itemView)
                hideAllRotateButtons(itemView as? ViewGroup)
                itemView.isClickable = true
                itemView.isLongClickable = true
                itemView.isFocusable = true

                val spec = getOrResolveTileSpec(itemView) ?: continue
                val isCard = ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true))
                val isExpanded = sExpandedTileSpecs.contains(spec)
                val isVertical = sVerticalTileSpecs.contains(spec)
                val isLarge = sLargeTileSpecs.contains(spec)
                val isCircCard = isCard && sCircularCardSpecs.contains(spec)
                val isCircular = isCircCard || (!isCard && !isExpanded && !isVertical && !isLarge && !itemView.javaClass.name.contains("Card") && (itemView.javaClass.name.contains("Tile") || itemView.javaClass.name.contains("QSTileItemView")))
                if (isCircular) {
                    ControlCenterHook.setCircularTileLabelVisibility(itemView, true)
                }

                val container = itemView.findViewById<ViewGroup>(0x7f099995)
                if (container != null) {
                    container.isClickable = false
                    val allTiles = ControlCenterGrouping.getAllChildTiles(container)
                    for (child in allTiles) {
                        ControlCenterHook.hideAllCustomBadges(child)
                        child.isClickable = true
                        child.isLongClickable = true
                        child.isFocusable = true
                    }
                    val group = isGroupAnchor(spec) ?: (itemView.getTag(0x7f099994) as? TileGroup)
                    if (group != null) {
                        ControlCenterGrouping.applyGroupContainerStyle(itemView, itemView.context.classLoader, group)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "cleanupAllVisibleViews error", t)
        }
    }

    fun onEnterEditMode(rootView: View? = null, context: Context? = null) {
        sIsEditMode = true
        sIsGroupSelectMode = false
        sIsDisassembleMode = false
        val ctx = context ?: rootView?.context ?: sRecyclerView?.get()?.context ?: sLastKnownContext
        var rv = (sRecyclerView?.get() as? ViewGroup)
        if (rv == null && rootView != null) {
            val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
            val targetCtx = ctx ?: rootView.context
            for (pIdStr in panelIds) {
                val rvId = targetCtx?.resources?.getIdentifier(pIdStr, "id", targetCtx.packageName) ?: 0
                if (rvId != 0) {
                    val foundRv = (rootView.rootView ?: rootView).findViewById<View>(rvId) as? ViewGroup
                    if (foundRv != null) {
                        rv = foundRv
                        sRecyclerView = WeakReference(foundRv)
                        break
                    }
                }
            }
            if (rv == null) {
                var cur: View? = rootView
                while (cur != null) {
                    if (cur is ViewGroup && cur.javaClass.name.contains("RecyclerView")) {
                        rv = cur
                        sRecyclerView = WeakReference(cur)
                        break
                    }
                    cur = cur.parent as? View
                }
            }
        }
        sHasStructuralEditChanges = false
        updateAllVisibleTileBadges()
    }

    fun onExitEditMode(rootView: View? = null, context: Context? = null) {
        val now = System.currentTimeMillis()
        sIsEditMode = false
        sIsGroupSelectMode = false
        sIsDisassembleMode = false
        sSelectedTileSpecs.clear()
        sSelectedDisassembleGroups.clear()
        try {
            ControlCenterHook.sMediaPlayerViewHolder?.get()?.let {
                ControlCenterHook.applyMediaPlayerLayout(it)
            }
        } catch (t: Throwable) {}
        try {
            ControlCenterFullUnlock.onExitEditMode()
        } catch (t: Throwable) {}

        val ctx = context ?: rootView?.context ?: sRecyclerView?.get()?.context ?: sLastKnownContext
        if (ctx != null) {
            savePriorities(ctx)
        }

        var rv = (sRecyclerView?.get() as? ViewGroup)
        if (rv == null && rootView != null) {
            val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
            val targetCtx = ctx ?: rootView.context
            for (pIdStr in panelIds) {
                val rvId = targetCtx?.resources?.getIdentifier(pIdStr, "id", targetCtx.packageName) ?: 0
                if (rvId != 0) {
                    val foundRv = (rootView.rootView ?: rootView).findViewById<View>(rvId) as? ViewGroup
                    if (foundRv != null) {
                        rv = foundRv
                        sRecyclerView = WeakReference(foundRv)
                        break
                    }
                }
            }
            if (rv == null) {
                var cur: View? = rootView
                while (cur != null) {
                    if (cur is ViewGroup && cur.javaClass.name.contains("RecyclerView")) {
                        rv = cur
                        sRecyclerView = WeakReference(cur)
                        break
                    }
                    cur = cur.parent as? View
                }
            }
        }

        cleanupAllVisibleViews(rv)

        if (now - sLastExitEditTime > 300L) {
            sLastExitEditTime = now
            if (sHasStructuralEditChanges) {
                sHasStructuralEditChanges = false
                if (rv != null) {
                    rv.post {
                        cleanupAllVisibleViews(rv)
                        refreshAdapter(rv, ctx, isStructuralChange = true)
                    }
                } else {
                    refreshAdapter(rootView, ctx, isStructuralChange = true)
                }
            }
        }
    }

    fun refreshAdapter(root: View?, ctx: Context?, isStructuralChange: Boolean = false) {
        try {
            val targetCtx = ctx ?: root?.context ?: sLastKnownContext ?: return
            if (!sHasLoadedPriorities) loadSavedPriorities(targetCtx)
            val rvsToProcess = mutableListOf<View>()
            val directRv = sRecyclerView?.get()
            if (directRv != null) {
                rvsToProcess.add(directRv)
            }
            val decor = root?.rootView
            if (decor != null) {
                val panelIds = arrayOf("main_panel", "right_main_panel", "left_main_panel", "right_panel", "left_panel")
                for (pIdStr in panelIds) {
                    val rvId = targetCtx.resources.getIdentifier(pIdStr, "id", targetCtx.packageName)
                    if (rvId != 0) {
                        val rv = decor.findViewById<View>(rvId)
                        if (rv != null && !rvsToProcess.contains(rv)) {
                            rvsToProcess.add(rv)
                        }
                    }
                }
            }
            for (rv in rvsToProcess) {
                val getAdapterMethod = try { rv.javaClass.getMethod("getAdapter") } catch (t: Throwable) { null }
                val adp = getAdapterMethod?.invoke(rv)
                if (adp != null) {
                    sMainPanelAdapter = adp
                    try {
                        val distMethod = adp.javaClass.getDeclaredMethod("distributeContent", Boolean::class.javaPrimitiveType)
                        distMethod.isAccessible = true
                        distMethod.invoke(adp, true)
                    } catch (t: Throwable) {}

                    if (isStructuralChange) {
                        try {
                            val cl = adp.javaClass.classLoader
                            if (cl != null) {
                                ControlCenterFullUnlock.buildAndSetContentMap(adp, cl)
                            }
                        } catch (t: Throwable) {}
                        True2DGridEngine.invalidateAll()
                        try {
                            val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                            notifyDataSetChangedMethod.invoke(adp)
                        } catch (t: Throwable) {}
                    } else {
                        try {
                            val getItemCountMethod = adp.javaClass.getMethod("getItemCount")
                            val count = getItemCountMethod.invoke(adp) as Int
                            val notifyItemRangeChangedMethod = adp.javaClass.getMethod("notifyItemRangeChanged", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            notifyItemRangeChangedMethod.invoke(adp, 0, count)
                        } catch (t: Throwable) {
                            try {
                                val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                                notifyDataSetChangedMethod.invoke(adp)
                            } catch (t2: Throwable) {}
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refreshAdapter error", t)
        }
    }

    fun refreshRecyclerView(tileView: View?, onRefreshed: (() -> Unit)? = null) {
        try {
            var parent: View? = tileView
            while (parent != null && !parent.javaClass.name.contains("RecyclerView")) {
                parent = if (parent.parent is View) parent.parent as View else break
            }
            // Safely reference View without triggering NoClassDefFoundError for androidx.recyclerview
            val rv = (parent ?: sRecyclerView?.get()) as? ViewGroup
            if (rv != null) {
                if (sRecyclerView?.get() != rv) {
                    sRecyclerView = WeakReference(rv)
                }

                val doRefresh = {
                    try {
                        val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                        val adp = getAdapterMethod.invoke(rv)
                        if (adp != null) {
                            try {
                                val cl = adp.javaClass.classLoader
                                if (cl != null) {
                                    ControlCenterFullUnlock.buildAndSetContentMap(adp, cl)
                                }
                            } catch (t: Throwable) {}
                            True2DGridEngine.invalidateAll()
                            try {
                                val distMethod = adp.javaClass.getDeclaredMethod("distributeContent", Boolean::class.javaPrimitiveType)
                                distMethod.isAccessible = true
                                distMethod.invoke(adp, true)
                            } catch (t: Throwable) {}

                            // 1. Cancel animators and zero all translations on current views
                            // before notifyDataSetChanged so scrap views never carry dirty offsets into recycled slots
                            for (i in 0 until rv.childCount) {
                                val child = rv.getChildAt(i) ?: continue
                                child.animate().cancel()
                                child.translationX = 0f
                                child.translationY = 0f
                            }

                            // 2. Temporarily disable ItemAnimator during the layout pass to prevent any item flicker or default shift animations
                            val getItemAnimatorMethod = try { rv.javaClass.getMethod("getItemAnimator") } catch (t: Throwable) { null }
                            val savedItemAnimator = try { getItemAnimatorMethod?.invoke(rv) } catch (t: Throwable) { null }
                            try {
                                val setItemAnimatorMethod = rv.javaClass.methods.firstOrNull { it.name == "setItemAnimator" && it.parameterTypes.size == 1 }
                                setItemAnimatorMethod?.invoke(rv, null)
                            } catch (t: Throwable) {}

                            var hasHandedOff = false
                            val handoff = {
                                if (!hasHandedOff) {
                                    hasHandedOff = true
                                    // Enforce 0 translation on all attached views after layout, before drawing
                                    for (i in 0 until rv.childCount) {
                                        val child = rv.getChildAt(i) ?: continue
                                        child.animate().cancel()
                                        child.translationX = 0f
                                        child.translationY = 0f
                                    }
                                    // Restore saved ItemAnimator
                                    if (savedItemAnimator != null) {
                                        try {
                                            val setItemAnimatorMethod = rv.javaClass.methods.firstOrNull { it.name == "setItemAnimator" && it.parameterTypes.size == 1 }
                                            setItemAnimatorMethod?.invoke(rv, savedItemAnimator)
                                        } catch (t: Throwable) {}
                                    }
                                    onRefreshed?.invoke()
                                }
                            }

                            val vto = rv.viewTreeObserver
                            val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
                                override fun onPreDraw(): Boolean {
                                    try {
                                        if (vto.isAlive) {
                                            vto.removeOnPreDrawListener(this)
                                        } else {
                                            rv.viewTreeObserver.removeOnPreDrawListener(this)
                                        }
                                    } catch (t: Throwable) {}
                                    handoff()
                                    return true
                                }
                            }
                            vto.addOnPreDrawListener(preDrawListener)

                            val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                            notifyDataSetChangedMethod.invoke(adp)

                            // Fallback in case onPreDraw is delayed or skipped
                            rv.postDelayed({
                                handoff()
                            }, 200)
                        } else {
                            onRefreshed?.invoke()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "refreshRecyclerView notify error", t)
                        onRefreshed?.invoke()
                    }
                }

                val isComputing = try {
                    val m = rv.javaClass.getMethod("isComputingLayout")
                    m.invoke(rv) as? Boolean ?: false
                } catch (t: Throwable) { false }

                if (isComputing) {
                    rv.post { doRefresh() }
                } else {
                    doRefresh()
                }
            } else {
                onRefreshed?.invoke()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refreshRecyclerView error", t)
            onRefreshed?.invoke()
        }
    }

    fun setMediaSize(ctx: Context, size: String, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        sMediaPlayerSize = size
        savePriorities(ctx)
        try {
            ControlCenterHook.sMediaPlayerViewHolder?.get()?.let {
                ControlCenterHook.applyMediaPlayerLayout(it)
            }
        } catch (t: Throwable) {}
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize("media", ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun setTileToExpanded(ctx: Context, spec: String?, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x1", tileView, onRefreshed)
            return
        }
        sExpandedTileSpecs.add(spec)
        sVerticalTileSpecs.remove(spec)
        sLargeTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun setTileToVertical(ctx: Context, spec: String?, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "1x2", tileView, onRefreshed)
            return
        }
        sVerticalTileSpecs.add(spec)
        sExpandedTileSpecs.remove(spec)
        sLargeTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun setTileToLarge(ctx: Context, spec: String?, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x2", tileView, onRefreshed)
            return
        }
        sLargeTileSpecs.add(spec)
        sExpandedTileSpecs.remove(spec)
        sVerticalTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun setTileToNormal(ctx: Context, spec: String?, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x2", tileView, onRefreshed)
            return
        }
        sExpandedTileSpecs.remove(spec)
        sVerticalTileSpecs.remove(spec)
        sLargeTileSpecs.remove(spec)
        if ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) {
            sCircularCardSpecs.add(spec)
        } else {
            sCircularCardSpecs.remove(spec)
        }
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun toggleTileSize(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            cycleMediaPlayerSize(ctx, tileView)
            return
        }
        if ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) {
            when {
                sLargeTileSpecs.contains(spec) -> {
                    sLargeTileSpecs.remove(spec)
                    sVerticalTileSpecs.add(spec)
                    sExpandedTileSpecs.remove(spec)
                    sCircularCardSpecs.remove(spec)
                }
                sVerticalTileSpecs.contains(spec) -> {
                    sVerticalTileSpecs.remove(spec)
                    sCircularCardSpecs.add(spec)
                    sExpandedTileSpecs.remove(spec)
                    sLargeTileSpecs.remove(spec)
                }
                sCircularCardSpecs.contains(spec) -> {
                    sCircularCardSpecs.remove(spec)
                    sExpandedTileSpecs.add(spec)
                    sVerticalTileSpecs.remove(spec)
                    sLargeTileSpecs.remove(spec)
                }
                else -> { // Default stock card is 2x1 expanded -> cycle to 2x2 large
                    sExpandedTileSpecs.remove(spec)
                    sLargeTileSpecs.add(spec)
                    sVerticalTileSpecs.remove(spec)
                    sCircularCardSpecs.remove(spec)
                }
            }
        } else {
            when {
                sExpandedTileSpecs.contains(spec) -> {
                    sExpandedTileSpecs.remove(spec)
                    sLargeTileSpecs.add(spec)
                    sVerticalTileSpecs.remove(spec)
                }
                sLargeTileSpecs.contains(spec) -> {
                    sLargeTileSpecs.remove(spec)
                    sVerticalTileSpecs.add(spec)
                    sExpandedTileSpecs.remove(spec)
                }
                sVerticalTileSpecs.contains(spec) -> {
                    sVerticalTileSpecs.remove(spec)
                    sLargeTileSpecs.remove(spec)
                    sExpandedTileSpecs.remove(spec)
                }
                else -> {
                    sExpandedTileSpecs.add(spec)
                    sLargeTileSpecs.remove(spec)
                    sVerticalTileSpecs.remove(spec)
                }
            }
        }
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(spec, ctx)
        refreshRecyclerView(tileView)
    }

    fun cycleMediaPlayerSize(ctx: Context, tileView: View?) {
        val nextSize = when (sMediaPlayerSize) {
            "2x2" -> "2x1"
            "2x1" -> "1x1"
            "1x1" -> "1x2"
            "1x2" -> "1x3"
            "1x3" -> "1x4"
            "1x4" -> "1x5"
            "1x5" -> "2x3"
            "2x3" -> "2x4"
            "2x4" -> "2x5"
            "2x5" -> "3x1"
            "3x1" -> "3x2"
            "3x2" -> "3x3"
            "3x3" -> "4x1"
            "4x1" -> "4x2"
            "4x2" -> "4x3"
            "4x3" -> "2x2"
            else -> "2x2"
        }
        setMediaSize(ctx, nextSize, tileView)
    }

    fun toggleSliderOrientation(ctx: Context, isBrightness: Boolean, tileView: View?) {
        if (isBrightness) {
            sBrightnessSliderHorizontal = !sBrightnessSliderHorizontal
        } else {
            sVolumeSliderHorizontal = !sVolumeSliderHorizontal
        }
        val isHoriz = if (isBrightness) sBrightnessSliderHorizontal else sVolumeSliderHorizontal
        savePriorities(ctx)

        // Immediately update resize handle on the targeted slider view if present
        if (tileView is ViewGroup) {
            val resizeTag = 0x7f099999
            var resizeHandle = tileView.findViewById<ImageView>(resizeTag)
            val density = ctx.resources.displayMetrics.density
            val badgeSize = (38 * density).toInt()
            if (sIsEditMode && isHoriz) {
                if (resizeHandle == null) {
                    resizeHandle = ImageView(ctx).apply {
                        id = resizeTag
                        layoutParams = createMediaBadgeLayoutParams(tileView, badgeSize)
                        setImageDrawable(CurvedResizeHandleDrawable(density))
                    }
                    tileView.addView(resizeHandle)
                } else if (isConstraintLayout(tileView) && !resizeHandle.layoutParams.javaClass.name.contains("ConstraintLayout")) {
                    resizeHandle.layoutParams = createMediaBadgeLayoutParams(tileView, badgeSize)
                }
                val shouldShowResize = !sIsGroupSelectMode && !sIsDisassembleMode
                resizeHandle.visibility = if (shouldShowResize) View.VISIBLE else View.GONE
                if (shouldShowResize) {
                    val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                    val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                    if (tileView.width > 0 && tileView.height > 0) {
                        resizeHandle.layout(tileView.width - bw, tileView.height - bh, tileView.width, tileView.height)
                    }
                    resizeHandle.bringToFront()
                    resizeHandle.isClickable = true
                    resizeHandle.setOnClickListener {
                        cycleSliderWidth(ctx, isBrightness, tileView)
                    }
                    FluidTileResizeHandler.attach(resizeHandle, ctx, if (isBrightness) "brightness" else "volume", tileView, density)
                } else {
                    resizeHandle.isClickable = false
                    resizeHandle.setOnClickListener(null)
                    resizeHandle.setOnTouchListener(null)
                }
            } else {
                if (resizeHandle != null) {
                    resizeHandle.visibility = View.GONE
                    resizeHandle.isClickable = false
                    resizeHandle.setOnClickListener(null)
                    resizeHandle.setOnTouchListener(null)
                    tileView.removeView(resizeHandle)
                }
            }
        }

        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(if (isBrightness) "brightness" else "volume", ctx)
        refreshRecyclerView(tileView)
        updateAllVisibleTileBadges()
        refreshAdapter(tileView?.rootView, ctx, true)
    }

    fun cycleSliderWidth(ctx: Context, isBrightness: Boolean, tileView: View?) {
        if (isBrightness) {
            sBrightnessSliderWidth = when (sBrightnessSliderWidth) {
                2 -> 3
                3 -> 4
                4 -> 2
                else -> 2
            }
        } else {
            sVolumeSliderWidth = when (sVolumeSliderWidth) {
                2 -> 3
                3 -> 4
                4 -> 2
                else -> 2
            }
        }
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(if (isBrightness) "brightness" else "volume", ctx)
        refreshRecyclerView(tileView)
        tileView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun setSliderWidth(ctx: Context, isBrightness: Boolean, width: Int, tileView: View?, onRefreshed: (() -> Unit)? = null) {
        val clamped = width.coerceIn(2, 4)
        if (isBrightness) {
            sBrightnessSliderWidth = clamped
        } else {
            sVolumeSliderWidth = clamped
        }
        savePriorities(ctx)
        ControlCenterFullUnlock.ensureTileFitsLandscapeAfterResize(if (isBrightness) "brightness" else "volume", ctx)
        refreshRecyclerView(tileView, onRefreshed)
    }

    fun triggerTileToggle(tile: Any?, record: Any?, childView: View?, classLoader: ClassLoader) {
        try {
            if (tile != null) {
                try {
                    val m = tile.javaClass.getMethod("click")
                    m.invoke(tile)
                } catch (e: NoSuchMethodException) {
                    try {
                        val m = tile.javaClass.getMethod("click", View::class.java)
                        m.invoke(tile, childView)
                    } catch (t: Throwable) {}
                }
            }
            if (record != null) {
                try {
                    val getClickActionMethod = record.javaClass.getMethod("getClickAction")
                    val clickAction = getClickActionMethod.invoke(record)
                    if (clickAction != null) {
                        for (cm in clickAction.javaClass.methods) {
                            if (cm.name == "invoke" && cm.parameterTypes.size == 1) {
                                cm.invoke(clickAction, childView)
                                break
                            } else if (cm.name == "invoke" && cm.parameterTypes.isEmpty()) {
                                cm.invoke(clickAction)
                                break
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }
            if (childView != null && tile != null) {
                childView.postDelayed({
                    try {
                        val getStateMethod = tile.javaClass.getMethod("getState")
                        val tileState = getStateMethod.invoke(tile)
                        var isConn = false
                        try {
                            val isConnMethod = tile.javaClass.getMethod("isConnected")
                            isConn = isConnMethod.invoke(tile) as Boolean
                        } catch (t: Throwable) {}
                        val updateStateMethod = childView.javaClass.getMethod(
                            "updateState",
                            Class.forName("com.android.systemui.plugins.qs.QSTile\$State", false, classLoader),
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType
                        )
                        updateStateMethod.invoke(childView, tileState, isConn, true)
                    } catch (t: Throwable) {}
                }, 120)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "triggerTileToggle error", t)
        }
    }

    fun triggerTileSecondaryClick(tile: Any?, record: Any?, childView: View?) {
        try {
            if (tile != null) {
                try {
                    val m = tile.javaClass.getMethod("secondaryClick")
                    m.invoke(tile)
                    return
                } catch (e: NoSuchMethodException) {
                    try {
                        val m = tile.javaClass.getMethod("secondaryClick", View::class.java)
                        m.invoke(tile, childView)
                        return
                    } catch (e2: NoSuchMethodException) {
                        try {
                            val m2 = tile.javaClass.getMethod("longClick")
                            m2.invoke(tile)
                            return
                        } catch (t: Throwable) {}
                    }
                }
            }
            if (record != null) {
                try {
                    val getLongClickActionMethod = record.javaClass.getMethod("getLongClickAction")
                    val longClickAction = getLongClickActionMethod.invoke(record)
                    if (longClickAction != null) {
                        for (lm in longClickAction.javaClass.methods) {
                            if (lm.name == "invoke" && lm.parameterTypes.size == 1) {
                                lm.invoke(longClickAction, childView)
                                return
                            } else if (lm.name == "invoke" && lm.parameterTypes.isEmpty()) {
                                lm.invoke(longClickAction)
                                return
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "triggerTileSecondaryClick error", t)
        }
    }

    fun hideAllRotateButtons(root: ViewGroup?) {
        if (root == null) return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child != null) {
                if (child.id == 0x7f099992) {
                    child.visibility = View.GONE
                }
                if (child is ViewGroup) {
                    hideAllRotateButtons(child)
                }
            }
        }
    }
}
