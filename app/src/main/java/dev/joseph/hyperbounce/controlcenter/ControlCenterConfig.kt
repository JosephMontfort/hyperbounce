package dev.joseph.hyperbounce.controlcenter

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
import android.widget.FrameLayout
import android.widget.ImageView
import dev.joseph.hyperbounce.prefs.PREFS_GROUP
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
    var modularEditEnabled: Boolean = true,
    var restartOnEditExit: Boolean = true,
    var animEnabled: Boolean = true,
    var jellyEnabled: Boolean = true,
    var lowEndAnim: Boolean = false,
    var entryInitialScale: Float = 0.50f,
    var entryTranslationY: Float = 120.0f,
    var waterfallEnabled: Boolean = true,
    var waterfallDelayStep: Float = 0.04f,
    var spreadRatio: Float = 0.02f,
    var spreadResponse: Float = 0.85f,
    var spreadDamping: Float = 0.85f,
    var spreadReleaseResponse: Float = 0.70f,
    var spreadReleaseDamping: Float = 0.90f,
    var tileShowResponse: Float = 0.85f,
    var tileShowDamping: Float = 0.85f,
    var tileHideResponse: Float = 0.80f,
    var tileHideDamping: Float = 0.90f,
    var tileShrinkResponse: Float = 0.85f,
    var tileShrinkDamping: Float = 0.85f,
    var tileDelayScale: Float = 60.0f,
    var blurResponse: Float = 0.90f,
    var blurDamping: Float = 0.88f,
    var frictionMultiplier: Float = 0.40f
)

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
    var tileSizes: String = ""
) {
    fun getEffectiveRowCount(): Int {
        if (rowCount in 1..6) return rowCount
        val type = getEffectiveLayoutType()
        return when (type) {
            "2_CIRCULAR_HORIZ" -> 1
            "2_CIRCULAR_VERT" -> 2
            else -> 2
        }
    }

    fun getEffectiveLayoutType(): String {
        if (layoutType.isNotEmpty()) return layoutType
        return when {
            specs.size == 4 -> "4_CIRCULAR"
            specs.size == 2 -> if (isVertical) "2_CIRCULAR_VERT" else "2_CIRCULAR_HORIZ"
            specs.size == 3 -> "1_EXP_2_CIRC"
            else -> "DYNAMIC_${getEffectiveRowCount()}R"
        }
    }

    fun is2x2Group(): Boolean {
        val type = getEffectiveLayoutType()
        return type == "4_CIRCULAR" ||
               type == "2_EXPANDED" ||
               type == "2_VERTICAL" ||
               type == "1_EXP_2_CIRC" ||
               type == "1_VERT_2_CIRC" ||
               (getEffectiveRowCount() == 2)
    }
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

    @Volatile var sBrightnessSliderController: Any? = null
    @Volatile var sVolumeSliderController: Any? = null
    @Volatile var sMediaPlayerController: Any? = null

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

    val sAllQSRecords = CopyOnWriteArrayList<Any>()
    val sBindingRecords = object : ThreadLocal<HashSet<Any>>() {
        override fun initialValue(): HashSet<Any> = HashSet()
    }

    fun updateFromPrefs(prefs: SharedPreferences) {
        config = ControlCenterConfig(
            modularEditEnabled = prefs.getBoolean("cc_modular_edit_enabled", true),
            restartOnEditExit = prefs.getBoolean("cc_restart_on_edit_exit", true),
            animEnabled = prefs.getBoolean("cc_anim_enabled", true),
            jellyEnabled = prefs.getBoolean("cc_jelly_enabled", true),
            lowEndAnim = prefs.getBoolean("cc_low_end_anim", false),
            entryInitialScale = prefs.getFloat("cc_entry_initial_scale", 0.50f),
            entryTranslationY = prefs.getFloat("cc_entry_translation_y", 120.0f),
            waterfallEnabled = prefs.getBoolean("cc_waterfall_enabled", true),
            waterfallDelayStep = prefs.getFloat("cc_waterfall_delay_step", 0.04f),
            spreadRatio = prefs.getFloat("cc_spread_ratio", 0.02f),
            spreadResponse = prefs.getFloat("cc_spread_response", 0.85f),
            spreadDamping = prefs.getFloat("cc_spread_damping", 0.85f),
            spreadReleaseResponse = prefs.getFloat("cc_spread_release_response", 0.70f),
            spreadReleaseDamping = prefs.getFloat("cc_spread_release_damping", 0.90f),
            tileShowResponse = prefs.getFloat("cc_tile_show_response", 0.85f),
            tileShowDamping = prefs.getFloat("cc_tile_show_damping", 0.85f),
            tileHideResponse = prefs.getFloat("cc_tile_hide_response", 0.80f),
            tileHideDamping = prefs.getFloat("cc_tile_hide_damping", 0.90f),
            tileShrinkResponse = prefs.getFloat("cc_tile_shrink_response", 0.85f),
            tileShrinkDamping = prefs.getFloat("cc_tile_shrink_damping", 0.85f),
            tileDelayScale = prefs.getFloat("cc_tile_delay_scale", 60.0f),
            blurResponse = prefs.getFloat("cc_blur_response", 0.90f),
            blurDamping = prefs.getFloat("cc_blur_damping", 0.88f),
            frictionMultiplier = prefs.getFloat("cc_friction_multiplier", 0.40f)
        )
    }

    @Synchronized
    fun loadSavedPriorities(ctx: Context?) {
        if (ctx == null) return
        sLastKnownContext = ctx
        sHasLoadedPriorities = true
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
                        Uri.parse("content://dev.joseph.hyperbounce.provider"),
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

                    val expandedStr = json.optString("expanded_tile_specs", "")
                    if (!TextUtils.isEmpty(expandedStr)) {
                        val newExp = LinkedHashSet<String>()
                        for (spec in expandedStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newExp.add(spec.trim())
                        }
                        if (newExp.isNotEmpty()) {
                            sExpandedTileSpecs.clear()
                            sExpandedTileSpecs.addAll(newExp)
                        }
                    }

                    val verticalStr = json.optString("vertical_tile_specs", "")
                    if (!TextUtils.isEmpty(verticalStr)) {
                        val newVert = LinkedHashSet<String>()
                        for (spec in verticalStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newVert.add(spec.trim())
                        }
                        if (newVert.isNotEmpty()) {
                            sVerticalTileSpecs.clear()
                            sVerticalTileSpecs.addAll(newVert)
                        }
                    }

                    val largeStr = json.optString("large_tile_specs", "")
                    if (!TextUtils.isEmpty(largeStr)) {
                        val newLarge = LinkedHashSet<String>()
                        for (spec in largeStr.split(",")) {
                            if (spec.trim().isNotEmpty()) newLarge.add(spec.trim())
                        }
                        if (newLarge.isNotEmpty()) {
                            sLargeTileSpecs.clear()
                            sLargeTileSpecs.addAll(newLarge)
                        }
                    }

                    val circularCardsStr = json.optString("circular_card_specs", "")
                    sCircularCardSpecs.clear()
                    if (!TextUtils.isEmpty(circularCardsStr)) {
                        for (spec in circularCardsStr.split(",")) {
                            if (spec.trim().isNotEmpty()) sCircularCardSpecs.add(spec.trim())
                        }
                    }
                    sMediaPlayerSize = json.optString("media_player_size", sMediaPlayerSize)
                    if (sMediaPlayerSize == "1x1") sMediaPlayerSize = "2x2"
                    sBrightnessSliderHorizontal = json.optBoolean("brightness_slider_horizontal", sBrightnessSliderHorizontal)
                    sVolumeSliderHorizontal = json.optBoolean("volume_slider_horizontal", sVolumeSliderHorizontal)
                    sBrightnessSliderWidth = json.optInt("brightness_slider_width", sBrightnessSliderWidth).coerceIn(2, 4)
                    sVolumeSliderWidth = json.optInt("volume_slider_width", sVolumeSliderWidth).coerceIn(2, 4)

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
                                    if (gSpecs.size >= 2) {
                                        newGroups.add(TileGroup(gId, gSpecs, isVert, layoutType, cardSpec, isTransparent, rowCount, tileSizes))
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

                val expandedStr = prefs.getString("expanded_tile_specs", "")
                if (!TextUtils.isEmpty(expandedStr)) {
                    val newExp = LinkedHashSet<String>()
                    for (spec in expandedStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) newExp.add(spec.trim())
                    }
                    if (newExp.isNotEmpty()) {
                        sExpandedTileSpecs.clear()
                        sExpandedTileSpecs.addAll(newExp)
                    }
                }

                val verticalStr = prefs.getString("vertical_tile_specs", "")
                if (!TextUtils.isEmpty(verticalStr)) {
                    val newVert = LinkedHashSet<String>()
                    for (spec in verticalStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) newVert.add(spec.trim())
                    }
                    if (newVert.isNotEmpty()) {
                        sVerticalTileSpecs.clear()
                        sVerticalTileSpecs.addAll(newVert)
                    }
                }

                val largeStr = prefs.getString("large_tile_specs", "")
                if (!TextUtils.isEmpty(largeStr)) {
                    val newLarge = LinkedHashSet<String>()
                    for (spec in largeStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) newLarge.add(spec.trim())
                    }
                    if (newLarge.isNotEmpty()) {
                        sLargeTileSpecs.clear()
                        sLargeTileSpecs.addAll(newLarge)
                    }
                }

                val circularCardsStr = prefs.getString("circular_card_specs", "")
                sCircularCardSpecs.clear()
                if (!TextUtils.isEmpty(circularCardsStr)) {
                    for (spec in circularCardsStr!!.split(",")) {
                        if (spec.trim().isNotEmpty()) sCircularCardSpecs.add(spec.trim())
                    }
                }
                sMediaPlayerSize = prefs.getString("media_player_size", sMediaPlayerSize) ?: "2x2"
                sBrightnessSliderHorizontal = prefs.getBoolean("brightness_slider_horizontal", sBrightnessSliderHorizontal)
                sVolumeSliderHorizontal = prefs.getBoolean("volume_slider_horizontal", sVolumeSliderHorizontal)
                sBrightnessSliderWidth = prefs.getInt("brightness_slider_width", sBrightnessSliderWidth).coerceIn(2, 4)
                sVolumeSliderWidth = prefs.getInt("volume_slider_width", sVolumeSliderWidth).coerceIn(2, 4)

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
                                if (gSpecs.size >= 2) {
                                    newGroups.add(TileGroup(gId, gSpecs, isVert, layoutType, cardSpec, isTransparent, rowCount, tileSizes))
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
            json.put("media_player_size", sMediaPlayerSize)
            json.put("brightness_slider_horizontal", sBrightnessSliderHorizontal)
            json.put("volume_slider_horizontal", sVolumeSliderHorizontal)
            json.put("brightness_slider_width", sBrightnessSliderWidth)
            json.put("volume_slider_width", sVolumeSliderWidth)

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
                }
            }
            json.put("tile_groups_v2", groupsSb.toString())

            val jsonStr = json.toString()

            // 1. Settings.Secure
            try {
                android.provider.Settings.Secure.putString(targetCtx.contentResolver, "sysui_hyperbounce_cc_layout", jsonStr)
                android.provider.Settings.Secure.putString(targetCtx.contentResolver, "hyperbounce_cc_layout", jsonStr)
            } catch (t: Throwable) {}

            // 2. ContentProvider
            try {
                targetCtx.contentResolver.call(
                    Uri.parse("content://dev.joseph.hyperbounce.provider"),
                    "saveCCLayout", jsonStr, null
                )
            } catch (t: Throwable) {}

            // 3. SharedPreferences Cache
            val prefs = targetCtx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE)
            if (prefs != null) {
                val editor = prefs.edit()
                for ((k, v) in sComponentPriorities) {
                    editor.putInt("priority_$k", v)
                }
                editor.putBoolean("wifi_card_first", sIsWifiFirst)
                editor.putString("expanded_tile_specs", sb.toString())
                editor.putString("vertical_tile_specs", vertSb.toString())
                editor.putString("large_tile_specs", largeSb.toString())
                editor.putString("circular_card_specs", circCardSb.toString())
                editor.putString("media_player_size", sMediaPlayerSize)
                editor.putBoolean("brightness_slider_horizontal", sBrightnessSliderHorizontal)
                editor.putBoolean("volume_slider_horizontal", sVolumeSliderHorizontal)
                editor.putInt("brightness_slider_width", sBrightnessSliderWidth)
                editor.putInt("volume_slider_width", sVolumeSliderWidth)
                editor.putString("tile_groups_v2", groupsSb.toString())
                editor.apply()
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
                    Uri.parse("content://dev.joseph.hyperbounce.provider"),
                    "resetCCLayout", null, null
                )
            } catch (t: Throwable) {}

            val prefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE)
            prefs?.edit()?.clear()?.apply()

            sTileGroups.clear()
            sExpandedTileSpecs.clear()
            sExpandedTileSpecs.add("wifi")
            sExpandedTileSpecs.add("cell")
            sVerticalTileSpecs.clear()
            sLargeTileSpecs.clear()
            sCircularCardSpecs.clear()
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
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
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
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
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

    fun wakeAndRefreshAllGroupedTiles() {
        try {
            for (r in sAllQSRecords) {
                if (r != null) {
                    val spec = getRecordSpec(r)
                    if (isGroupChild(spec)) {
                        try {
                            val startLisM = r.javaClass.getMethod("startListening")
                            startLisM.invoke(r)
                        } catch (t: Throwable) {}
                        try {
                            val getTileM = r.javaClass.getMethod("getTile")
                            val tileObj = getTileM.invoke(r)
                            if (tileObj != null) {
                                val setLisM = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                setLisM.invoke(tileObj, r, true)
                                val refM = tileObj.javaClass.getMethod("refreshState")
                                refM.invoke(tileObj)
                                try {
                                    val getStateM = tileObj.javaClass.getMethod("getState")
                                    val curState = getStateM.invoke(tileObj)
                                    val getHolderM = r.javaClass.getMethod("getHolder")
                                    val holder = getHolderM.invoke(r)
                                    if (holder != null && curState != null) {
                                        val ivField = holder.javaClass.getDeclaredField("itemView")
                                        ivField.isAccessible = true
                                        val childView = ivField.get(holder) as? View
                                        if (childView != null) {
                                            for (vm in childView.javaClass.methods) {
                                                if (vm.name == "updateState" && vm.parameterTypes.size == 3) {
                                                    vm.isAccessible = true
                                                    vm.invoke(childView, curState, true, false)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {}
                            }
                        } catch (t: Throwable) {}
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "wakeAndRefreshAllGroupedTiles error", t)
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
                    var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
                    var selectBadge = itemView.findViewById<ImageView>(selectTag)
                    var rotateBtn = itemView.findViewById<ImageView>(rotateTag)

                    if (!sIsEditMode) {
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
                        val isBright = "brightness".equals(spec, ignoreCase = true)
                        val isHoriz = if (isBright) sBrightnessSliderHorizontal else sVolumeSliderHorizontal

                        if (rotateBtn == null && itemView is ViewGroup) {
                            val rSize = (32 * density).toInt()
                            rotateBtn = ImageView(itemView.context).apply {
                                id = rotateTag
                                val lp = android.widget.FrameLayout.LayoutParams(rSize, rSize).apply {
                                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                                    val m = (6 * density).toInt()
                                    setMargins(0, m, m, 0)
                                }
                                layoutParams = lp
                                setImageDrawable(RotateIconDrawable())
                            }
                            itemView.addView(rotateBtn)
                        }
                        if (rotateBtn != null) {
                            val shouldShowRotate = !sIsGroupSelectMode && !sIsDisassembleMode
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

                        // Resize handle: ONLY visible when horizontal!
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
                                val shouldShowResize = !sIsGroupSelectMode && !sIsDisassembleMode
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
                                    resizeHandle.setOnTouchListener(TileResizeDragTouchListener(itemView.context, if (isBright) "brightness" else "volume", itemView, density))
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
                            }
                        }

                        // Group Selection Badge for Sliders!
                        val canGroupSlider = sIsGroupSelectMode && !isGroupMember(spec)
                        if (canGroupSlider) {
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
                            if (selectBadge != null) {
                                selectBadge.visibility = View.VISIBLE
                                selectBadge.bringToFront()
                                val isSelected = sSelectedTileSpecs.contains(spec)
                                (selectBadge.drawable as? SelectCircleDrawable)?.setChecked(isSelected)
                                selectBadge.isClickable = true
                                selectBadge.setOnClickListener {
                                    if (sSelectedTileSpecs.contains(spec)) {
                                        sSelectedTileSpecs.remove(spec)
                                    } else {
                                        sSelectedTileSpecs.add(spec)
                                    }
                                    (selectBadge.drawable as? SelectCircleDrawable)?.setChecked(sSelectedTileSpecs.contains(spec))
                                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } else {
                            if (selectBadge != null) selectBadge.visibility = View.GONE
                        }

                        continue
                    }

                    // Special Handling for Media Player
                    if (isMedia) {
                        if (rotateBtn != null) rotateBtn.visibility = View.GONE

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
                            val shouldShowMediaResize = !sIsGroupSelectMode && !sIsDisassembleMode
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
                                resizeHandle.setOnTouchListener(TileResizeDragTouchListener(itemView.context, "media", itemView, density))
                            } else {
                                resizeHandle.isClickable = false
                                resizeHandle.setOnClickListener(null)
                                resizeHandle.setOnTouchListener(null)
                            }
                        }

                        // Group Selection Badge for Media Player!
                        // Allowed ONLY when size is "1x2", "2x1", or "2x2"
                        val isEligibleMediaSize = sMediaPlayerSize in listOf("1x2", "2x1", "2x2")
                        val canGroupMedia = sIsGroupSelectMode && isEligibleMediaSize && !isGroupMember(spec)
                        if (canGroupMedia) {
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
                            if (selectBadge != null) {
                                selectBadge.visibility = View.VISIBLE
                                selectBadge.bringToFront()
                                val isSelected = sSelectedTileSpecs.contains("media")
                                (selectBadge.drawable as? SelectCircleDrawable)?.setChecked(isSelected)
                                selectBadge.isClickable = true
                                selectBadge.setOnClickListener {
                                    if (sSelectedTileSpecs.contains("media")) {
                                        sSelectedTileSpecs.remove("media")
                                    } else {
                                        sSelectedTileSpecs.add("media")
                                    }
                                    (selectBadge.drawable as? SelectCircleDrawable)?.setChecked(sSelectedTileSpecs.contains("media"))
                                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } else {
                            if (selectBadge != null) selectBadge.visibility = View.GONE
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
                            val rSize = (32 * density).toInt()
                            rotateBtn = ImageView(itemView.context).apply {
                                id = rotateTag
                                val lp = android.widget.FrameLayout.LayoutParams(rSize, rSize).apply {
                                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                                    val m = (6 * density).toInt()
                                    setMargins(0, m, m, 0)
                                }
                                layoutParams = lp
                                setImageDrawable(RotateIconDrawable())
                            }
                            itemView.addView(rotateBtn)
                        }
                        if (rotateBtn != null) {
                            val shouldShowRotate = !sIsGroupSelectMode && !sIsDisassembleMode
                            rotateBtn.visibility = if (shouldShowRotate) View.VISIBLE else View.GONE
                            if (shouldShowRotate) {
                                rotateBtn.bringToFront()
                                rotateBtn.isClickable = true
                                rotateBtn.setOnClickListener {
                                    try {
                                        when (group.getEffectiveLayoutType()) {
                                            "2_CIRCULAR_VERT", "2_CIRCULAR_HORIZ" -> {
                                                group.isVertical = !group.isVertical
                                                group.layoutType = if (group.isVertical) "2_CIRCULAR_VERT" else "2_CIRCULAR_HORIZ"
                                            }
                                            "2_EXPANDED", "2_VERTICAL" -> {
                                                java.util.Collections.swap(group.specs, 0, 1)
                                            }
                                            "1_EXP_2_CIRC", "1_VERT_2_CIRC", "4_CIRCULAR" -> {
                                                java.util.Collections.rotate(group.specs, 1)
                                            }
                                            else -> {
                                                if (group.specs.size == 2) {
                                                    group.isVertical = !group.isVertical
                                                } else {
                                                    java.util.Collections.rotate(group.specs, 1)
                                                }
                                            }
                                        }
                                        savePriorities(itemView.context)
                                        refreshRecyclerView(itemView)
                                        updateAllVisibleTileBadges()
                                        refreshAdapter(itemView.rootView, itemView.context, true)
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "Rotate group error", t)
                                    }
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
                        val isGroupable = !isSlider && !isMedia && !isLarge && !isGroupMember(spec)
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
                            resizeHandle.setOnTouchListener(TileResizeDragTouchListener(itemView.context, spec, itemView, density))
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
        updateAllVisibleTileBadges()
        if (rv != null) {
            rv.post {
                updateAllVisibleTileBadges()
                refreshAdapter(rv, ctx, isStructuralChange = false)
            }
        } else if (rootView != null) {
            rootView.post {
                updateAllVisibleTileBadges()
                refreshAdapter(rootView, ctx, isStructuralChange = false)
            }
        }
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

    fun refreshRecyclerView(tileView: View?) {
        try {
            var parent: View? = tileView
            while (parent != null && !parent.javaClass.name.contains("RecyclerView")) {
                parent = if (parent.parent is View) parent.parent as View else break
            }
            // Safely reference View without triggering NoClassDefFoundError for androidx.recyclerview
            val rv = parent ?: sRecyclerView?.get()
            if (rv != null) {
                if (sRecyclerView?.get() != rv) {
                    sRecyclerView = WeakReference(rv)
                }
                rv.post {
                    try {
                        val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                        val adp = getAdapterMethod.invoke(rv)
                        if (adp != null) {
                            try {
                                val distMethod = adp.javaClass.getDeclaredMethod("distributeContent", Boolean::class.javaPrimitiveType)
                                distMethod.isAccessible = true
                                distMethod.invoke(adp, true)
                            } catch (t: Throwable) {}
                            val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                            notifyDataSetChangedMethod.invoke(adp)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "refreshRecyclerView notify error", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refreshRecyclerView error", t)
        }
    }

    fun setMediaSize(ctx: Context, size: String, tileView: View?) {
        sMediaPlayerSize = size
        savePriorities(ctx)
        try {
            ControlCenterHook.sMediaPlayerViewHolder?.get()?.let {
                ControlCenterHook.applyMediaPlayerLayout(it)
            }
        } catch (t: Throwable) {}
        refreshRecyclerView(tileView)
    }

    fun setTileToExpanded(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x1", tileView)
            return
        }
        sExpandedTileSpecs.add(spec)
        sVerticalTileSpecs.remove(spec)
        sLargeTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun setTileToVertical(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "1x2", tileView)
            return
        }
        sVerticalTileSpecs.add(spec)
        sExpandedTileSpecs.remove(spec)
        sLargeTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun setTileToLarge(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x2", tileView)
            return
        }
        sLargeTileSpecs.add(spec)
        sExpandedTileSpecs.remove(spec)
        sVerticalTileSpecs.remove(spec)
        sCircularCardSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun setTileToNormal(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null) return
        if ("media".equals(spec, ignoreCase = true)) {
            setMediaSize(ctx, "2x2", tileView)
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
        refreshRecyclerView(tileView)
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
        refreshRecyclerView(tileView)
    }

    fun cycleMediaPlayerSize(ctx: Context, tileView: View?) {
        val nextSize = when (sMediaPlayerSize) {
            "2x2" -> "2x1"
            "2x1" -> "1x2"
            "1x2" -> "4x1"
            "4x1" -> "4x2"
            "4x2" -> "2x4"
            "2x4" -> "2x2"
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
        savePriorities(ctx)
        refreshRecyclerView(tileView)
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
        refreshRecyclerView(tileView)
        tileView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun setSliderWidth(ctx: Context, isBrightness: Boolean, width: Int, tileView: View?) {
        val clamped = width.coerceIn(2, 4)
        if (isBrightness) {
            sBrightnessSliderWidth = clamped
        } else {
            sVolumeSliderWidth = clamped
        }
        savePriorities(ctx)
        refreshRecyclerView(tileView)
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
