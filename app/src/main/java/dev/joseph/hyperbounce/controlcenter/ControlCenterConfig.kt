package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
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
 * Represents a grouped multi-tile container (2x2 with 4 tiles, 2x1 horizontal, or 1x2 vertical).
 */
data class TileGroup(
    val id: String,
    val specs: MutableList<String>,
    var isVertical: Boolean = false
)

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
    val sSelectedTileSpecs = LinkedHashSet<String>()
    val sSelectedDisassembleGroups = HashSet<String>()

    @Volatile var sCurrentVelocityY: Float = 0.0f
    @Volatile var sLastTouchTime: Long = 0L
    @Volatile var sIsUserFlicking: Boolean = false
    @Volatile var sPendingOrientationRebind: Boolean = false

    @Volatile var sQSListController: Any? = null
    @Volatile var sMainPanelAdapter: Any? = null
    @Volatile var sRecyclerView: WeakReference<RecyclerView>? = null
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

                    val grpV2 = json.optString("tile_groups_v2", "")
                    if (!TextUtils.isEmpty(grpV2)) {
                        val newGroups = ArrayList<TileGroup>()
                        for (gEntry in grpV2.split(";")) {
                            val parts = gEntry.split(":")
                            if (parts.size >= 3) {
                                val gId = parts[0]
                                val isVert = "V".equals(parts[1], ignoreCase = true)
                                val gSpecs = mutableListOf<String>()
                                for (sp in parts[2].split(",")) {
                                    if (sp.trim().isNotEmpty()) gSpecs.add(sp.trim())
                                }
                                if (gSpecs.size == 4 || gSpecs.size == 2) {
                                    newGroups.add(TileGroup(gId, gSpecs, isVert))
                                }
                            }
                        }
                        if (newGroups.isNotEmpty()) {
                            sTileGroups.clear()
                            sTileGroups.addAll(newGroups)
                        }
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

                val groupsStrV2 = prefs.getString("tile_groups_v2", "")
                if (!TextUtils.isEmpty(groupsStrV2)) {
                    val newGroups = ArrayList<TileGroup>()
                    for (gEntry in groupsStrV2!!.split(";")) {
                        val parts = gEntry.split(":")
                        if (parts.size >= 3) {
                            val gId = parts[0]
                            val isVert = "V".equals(parts[1], ignoreCase = true)
                            val gSpecs = mutableListOf<String>()
                            for (sp in parts[2].split(",")) {
                                if (sp.trim().isNotEmpty()) gSpecs.add(sp.trim())
                            }
                            if (gSpecs.size == 4 || gSpecs.size == 2) {
                                newGroups.add(TileGroup(gId, gSpecs, isVert))
                            }
                        }
                    }
                    if (newGroups.isNotEmpty()) {
                        sTileGroups.clear()
                        sTileGroups.addAll(newGroups)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loadSavedPriorities error", t)
        }
    }

    @Synchronized
    fun savePriorities(ctx: Context?) {
        val targetCtx = ctx ?: sLastKnownContext ?: return
        try {
            if (!sExplicitReset && sTileGroups.isEmpty() && sExpandedTileSpecs.size <= 2) {
                try {
                    val existing = android.provider.Settings.Secure.getString(
                        targetCtx.contentResolver, "sysui_hyperbounce_cc_layout"
                    )
                    if (!TextUtils.isEmpty(existing) && existing.contains("tile_groups_v2") && !existing.contains("\"tile_groups_v2\":\"\"")) {
                        return
                    }
                } catch (t: Throwable) {}
            }

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

            val groupsSb = StringBuilder()
            for (g in sTileGroups) {
                if (g.specs.size == 4 || g.specs.size == 2) {
                    if (groupsSb.isNotEmpty()) groupsSb.append(";")
                    groupsSb.append(g.id).append(":").append(if (g.isVertical) "V" else "H").append(":")
                    for (i in 0 until g.specs.size) {
                        if (i > 0) groupsSb.append(",")
                        groupsSb.append(g.specs[i])
                    }
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

    fun isGroupAnchor(spec: String?): TileGroup? {
        if (spec == null) return null
        if (sTileGroups.isEmpty() && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
        for (g in sTileGroups) {
            if ((g.specs.size == 4 || g.specs.size == 2) && spec == g.specs[0]) {
                return g
            }
        }
        return null
    }

    fun isGroupChild(spec: String?): Boolean {
        if (spec == null) return false
        if (sTileGroups.isEmpty() && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
        for (g in sTileGroups) {
            if (g.specs.size == 4 || g.specs.size == 2) {
                for (i in 1 until g.specs.size) {
                    if (spec == g.specs[i]) return true
                }
            }
        }
        return false
    }

    fun findGroupForSpec(spec: String?): TileGroup? {
        if (spec == null) return null
        if (sTileGroups.isEmpty() && sLastKnownContext != null) loadSavedPriorities(sLastKnownContext)
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
                null
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

        try {
            var cls: Class<*>? = itemView.javaClass
            while (cls != null && cls != Any::class.java) {
                for (f in cls.declaredFields) {
                    if (f.name == "spec" || f.name == "mSpec") {
                        f.isAccessible = true
                        val s = f.get(itemView) as? String
                        if (!s.isNullOrEmpty()) {
                            itemView.setTag(0x7f099990, s)
                            return s
                        }
                    }
                    if (f.name == "record" || f.name == "mRecord") {
                        f.isAccessible = true
                        val rec = f.get(itemView)
                        if (rec != null) {
                            try {
                                val sf = rec.javaClass.getDeclaredField("spec")
                                sf.isAccessible = true
                                val s = sf.get(rec) as? String
                                if (!s.isNullOrEmpty()) {
                                    itemView.setTag(0x7f099990, s)
                                    return s
                                }
                            } catch (t: Throwable) {}
                        }
                    }
                }
                cls = cls.superclass
            }
        } catch (t: Throwable) {}
        return null
    }

    fun updateAllVisibleTileBadges() {
        val rv = sRecyclerView?.get() ?: return
        rv.post {
            try {
                val childCount = rv.childCount
                for (i in 0 until childCount) {
                    val itemView = rv.getChildAt(i) ?: continue
                    if (ControlCenterHook.isChildOfContainer(itemView)) continue

                    val spec = getOrResolveTileSpec(itemView)
                    val isExpanded = spec != null && sExpandedTileSpecs.contains(spec)
                    val isVertical = spec != null && sVerticalTileSpecs.contains(spec)
                    val isCircular = !isExpanded && !isVertical && !itemView.javaClass.name.contains("Card")
                    val density = itemView.resources.displayMetrics.density

                    val iconFrameId = itemView.resources.getIdentifier("icon_frame", "id", itemView.context.packageName)
                    val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null

                    val resizeTag = 0x7f099999
                    val selectTag = 0x7f099998
                    var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
                    var selectBadge = itemView.findViewById<ImageView>(selectTag)

                    if (!sIsEditMode) {
                        if (resizeHandle != null) {
                            resizeHandle.visibility = View.GONE
                        }
                        if (selectBadge != null) {
                            selectBadge.visibility = View.GONE
                            selectBadge.setOnClickListener(null)
                            selectBadge.isClickable = false
                        }
                        itemView.isClickable = true
                        itemView.isLongClickable = true
                        if (isCircular) {
                            ControlCenterHook.setCircularTileLabelVisibility(itemView, true)
                        }
                        continue
                    }

                    if (isCircular) {
                        ControlCenterHook.setCircularTileLabelVisibility(itemView, false)
                    }

                    if (sIsGroupSelectMode) {
                        if (resizeHandle != null) {
                            resizeHandle.visibility = View.GONE
                        }
                        if (spec != null && !"wifi".equals(spec, ignoreCase = true) && !"cell".equals(spec, ignoreCase = true)) {
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
                                        sSelectedTileSpecs.add(spec)
                                    }
                                    val curScd = selectBadge.drawable as? SelectCircleDrawable
                                    curScd?.setChecked(sSelectedTileSpecs.contains(spec))
                                    selectBadge.invalidate()
                                }
                                selectBadge.setOnClickListener(toggleSelect)

                                if (isCircular && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                    val bw2 = selectBadge.measuredWidth.takeIf { it > 0 } ?: (26 * density).toInt()
                                    val bh2 = selectBadge.measuredHeight.takeIf { it > 0 } ?: (26 * density).toInt()
                                    val iLeft = curIconFrame.left
                                    val iTop = curIconFrame.top
                                    selectBadge.layout(iLeft, iTop, iLeft + bw2, iTop + bh2)
                                }
                            }
                        }
                    } else if (sIsDisassembleMode) {
                        if (resizeHandle != null) {
                            resizeHandle.visibility = View.GONE
                        }
                        val group = isGroupAnchor(spec)
                        if (group != null) {
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
                                    val curScd = selectBadge.drawable as? SelectCircleDrawable
                                    curScd?.setChecked(sSelectedDisassembleGroups.contains(group.id))
                                    selectBadge.invalidate()
                                }
                                selectBadge.setOnClickListener(toggleDisassembleSelect)
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
                        if (spec != null && !"wifi".equals(spec, ignoreCase = true) && !"cell".equals(spec, ignoreCase = true)) {
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
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "updateAllVisibleTileBadges error", t)
            }
        }
    }

    fun refreshAdapter(root: View?, ctx: Context?, isStructuralChange: Boolean = false) {
        try {
            val targetCtx = ctx ?: root?.context ?: sLastKnownContext ?: return
            loadSavedPriorities(targetCtx)
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
        var parent: View? = tileView
        while (parent != null && !parent.javaClass.name.contains("RecyclerView")) {
            parent = if (parent.parent is View) parent.parent as View else break
        }
        val rv = (parent as? RecyclerView) ?: sRecyclerView?.get()
        if (rv != null) {
            rv.post {
                try {
                    val getAdapterMethod = rv.javaClass.getMethod("getAdapter")
                    val adp = getAdapterMethod.invoke(rv)
                    if (adp != null) {
                        val notifyDataSetChangedMethod = adp.javaClass.getMethod("notifyDataSetChanged")
                        notifyDataSetChangedMethod.invoke(adp)
                    }
                } catch (t: Throwable) {}
            }
        }
    }

    fun setTileToExpanded(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null || "wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) return
        sExpandedTileSpecs.add(spec)
        sVerticalTileSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun setTileToVertical(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null || "wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) return
        sVerticalTileSpecs.add(spec)
        sExpandedTileSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun setTileToNormal(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null || "wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) return
        sExpandedTileSpecs.remove(spec)
        sVerticalTileSpecs.remove(spec)
        savePriorities(ctx)
        refreshRecyclerView(tileView)
    }

    fun toggleTileSize(ctx: Context, spec: String?, tileView: View?) {
        if (spec == null || "wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) return
        when {
            sExpandedTileSpecs.contains(spec) -> {
                sExpandedTileSpecs.remove(spec)
                sVerticalTileSpecs.add(spec)
            }
            sVerticalTileSpecs.contains(spec) -> {
                sVerticalTileSpecs.remove(spec)
            }
            else -> {
                sExpandedTileSpecs.add(spec)
                sVerticalTileSpecs.remove(spec)
            }
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
