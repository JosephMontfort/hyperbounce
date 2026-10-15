package dev.hyperios.app.controlcenter.hos4

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.github.kyuubiran.ezxhelper.utils.*
import dev.hyperios.app.controlcenter.*
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated Control Centre Hook for HyperOS 4.
 * Coordinates intelligent tile sizing, 4-card layout preservation,
 * crash-safe ViewHolder distribution, and group management.
 */
object ControlCenterHookHos4 {
    private const val TAG = "HyperBounce_CCHook4"

    @Volatile private var pluginHooked = false
    private val hookedLoaders = Collections.synchronizedSet(HashSet<ClassLoader>())

    @Synchronized
    fun initPluginHooks(module: XposedModule, classLoader: ClassLoader?) {
        if (classLoader == null || hookedLoaders.contains(classLoader)) return
        if (!ControlCenterConfigHolder.config.masterEnabled) {
            Log.i(TAG, "Control Center master toggle disabled. Skipping HOS 4 CC hooks.")
            return
        }

        try {
            Log.i(TAG, "Initializing Control Center Hooks for HyperOS 4 on $classLoader")
            hookedLoaders.add(classLoader)

            // 0. Flashlight brightness
            try {
                FlashlightBrightnessHook.init(module, classLoader)
            } catch (t: Throwable) {
                Log.w(TAG, "FlashlightBrightnessHook error", t)
            }

            // 1. Hook Low-End Animation switch
            try {
                val companionClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController\$Companion", false, classLoader)
                for (m in companionClass.declaredMethods) {
                    if (m.name == "getLowEndAnim") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (!ControlCenterConfigHolder.config.masterEnabled) return@intercept chain.proceed()
                            ControlCenterConfigHolder.config.isLowEndAnim()
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Hook MainPanelModeController for Edit Mode State Changes
            try {
                val modeControllerClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelModeController", false, classLoader)
                for (m in modeControllerClass.declaredMethods) {
                    if (m.name == "changeMode") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            val result = chain.proceed()
                            try {
                                if (args != null && args.isNotEmpty()) {
                                    val modeObj = args[0]
                                    val isEdit = modeObj?.toString()?.contains("EDIT") == true
                                    ControlCenterConfigHolder.sIsEditMode = isEdit
                                    if (!isEdit) {
                                        ControlCenterConfigHolder.sIsGroupSelectMode = false
                                        ControlCenterConfigHolder.sIsDisassembleMode = false
                                        ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                                        ControlCenterConfigHolder.sSelectedDisassembleGroups.clear()
                                    }
                                }
                            } catch (_: Throwable) {}
                            result
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 3. Hook MainPanelContentDistributor: DO NOT remove CompactQSCardController in HOS 4!
            try {
                val distClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", false, classLoader)
                for (m in distClass.declaredMethods) {
                    if (m.name == "distributePanels") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            ControlCenterFullUnlockHos4.sDistributor = chain.thisObject
                            val result = chain.proceed()
                            try {
                                val dist = chain.thisObject
                                val rightContentField = distClass.getDeclaredField("rightPanelContent")
                                rightContentField.isAccessible = true
                                val rightList = rightContentField.get(dist) as? MutableList<Any>

                                val leftContentField = distClass.getDeclaredField("leftPanelContent")
                                leftContentField.isAccessible = true
                                val leftList = leftContentField.get(dist) as? MutableList<Any>

                                // Ensure CompactQSCardController is NEVER removed
                                // Only filter out legacy flip CompactQSListController if present
                                val removePredicate = { item: Any ->
                                    val name = item.javaClass.name
                                    name.contains("CompactQSListController")
                                }
                                rightList?.removeAll(removePredicate)
                                leftList?.removeAll(removePredicate)
                            } catch (t: Throwable) {
                                Log.w(TAG, "distributePanels hook error", t)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed hooking MainPanelContentDistributor", t)
            }

            // 4. Hook MainPanelAdapter: Crash-Safe onCreateViewHolder and onBindViewHolder
            try {
                val mainAdapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader)
                for (m in mainAdapterClass.declaredMethods) {
                    if (m.name == "onCreateViewHolder" && m.parameterTypes.size >= 2 && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            if (args != null && args.size >= 2) {
                                val parent = args[0] as ViewGroup
                                val vt = args[1] as Int

                                val holder = ControlCenterGroupingHos4.createViewHolderSafe(
                                    parent,
                                    vt,
                                    ControlCenterConfigHolder.sQSListController,
                                    classLoader
                                )
                                if (holder != null) {
                                    return@intercept holder
                                }
                            }
                            chain.proceed()
                        }
                    }

                    if (m.name == "onBindViewHolder" && m.parameterTypes.size >= 2 && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = try { chain.proceed() } catch (_: Throwable) { null }
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty()) {
                                    val holder = args[0]
                                    if (holder != null) {
                                        bindCustomModuleHolder(holder, classLoader)
                                    }
                                }
                            } catch (_: Throwable) {}
                            result
                        }
                    }

                    if (m.name == "distributeContent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                ControlCenterConfigHolder.sMainPanelAdapter = chain.thisObject
                                ControlCenterFullUnlockHos4.reorganizeContentMap(chain.thisObject, classLoader)
                            } catch (t: Throwable) {
                                Log.w(TAG, "distributeContent hook error", t)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed hooking MainPanelAdapter", t)
            }

            // 5. Hook QSListController
            try {
                val qsListClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSListController", false, classLoader)
                for (m in qsListClass.declaredMethods) {
                    if (m.name == "createViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (result == null) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 2) {
                                    val parent = args[0] as ViewGroup
                                    val vt = args[1] as Int
                                    val h = ControlCenterGroupingHos4.createViewHolderSafe(
                                        parent,
                                        vt,
                                        chain.thisObject,
                                        classLoader
                                    )
                                    if (h != null) return@intercept h
                                }
                            }
                            result
                        }
                    }

                    if (m.name == "getListItems") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            ControlCenterConfigHolder.sQSListController = chain.thisObject
                            val originalList = chain.proceed() as? List<*>
                            if (originalList == null || ControlCenterConfigHolder.sTileGroups.isEmpty()) {
                                return@intercept originalList
                            }
                            try {
                                val filtered = ArrayList<Any>()
                                for (item in originalList) {
                                    if (item != null) {
                                        val spec = ControlCenterTileDetectorHos4.inspectRecord(item)
                                        if (spec != null && ControlCenterConfigHolder.isGroupChild(spec)) {
                                            continue
                                        }
                                        filtered.add(item)
                                    }
                                }
                                filtered
                            } catch (_: Throwable) {
                                originalList
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed hooking QSListController", t)
            }

            // 6. Hook CompactQSCardController and QSCardsController for Edit Mode Availability
            try {
                val compactClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.CompactQSCardController", false, classLoader)
                for (m in compactClass.declaredMethods) {
                    if (m.name == "available") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.masterEnabled) true else chain.proceed()
                        }
                    }
                    if (m.name == "createViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val res = chain.proceed()
                            if (res == null) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 2) {
                                    val h = ControlCenterGroupingHos4.createViewHolderSafe(
                                        args[0] as ViewGroup,
                                        args[1] as Int,
                                        chain.thisObject,
                                        classLoader
                                    )
                                    if (h != null) return@intercept h
                                }
                            }
                            res
                        }
                    }
                }
            } catch (_: Throwable) {}

            try {
                val cardsClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardsController", false, classLoader)
                for (m in cardsClass.declaredMethods) {
                    if (m.name == "available") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.masterEnabled) true else chain.proceed()
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 7. Hook QSRecord: Dynamic getType() and getSpanSize()
            try {
                val qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader)
                for (m in qsRecordClass.declaredMethods) {
                    if (m.name == "getType") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val record = chain.thisObject
                            val spec = ControlCenterTileDetectorHos4.inspectRecord(record)
                            if (spec != null) {
                                val g = ControlCenterConfigHolder.isGroupAnchor(spec)
                                if (g != null) {
                                    val h = g.getEffectiveRowCount()
                                    when (g.getEffectiveLayoutType()) {
                                        "2_EXPANDED" -> return@intercept 2281
                                        "2_VERTICAL" -> return@intercept 2282
                                        "1_EXP_2_CIRC" -> return@intercept 2283
                                        "1_VERT_2_CIRC" -> return@intercept 2284
                                        "4_CIRCULAR" -> return@intercept 2274
                                        "2_CIRCULAR_VERT" -> return@intercept 2276
                                        "2_CIRCULAR_HORIZ" -> return@intercept 2275
                                        else -> {
                                            val clampedH = Math.max(1, Math.min(6, h))
                                            return@intercept (2284 + clampedH)
                                        }
                                    }
                                }
                                if (ControlCenterConfigHolder.sCircularCardSpecs.contains(spec)) {
                                    return@intercept 8453
                                }
                                if (ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)) {
                                    return@intercept 2278
                                }
                                if (ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)) {
                                    return@intercept 2277
                                }
                                if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) ||
                                    ControlCenterTileDetectorHos4.isCard(spec)) {
                                    return@intercept 2273
                                }
                            }
                            chain.proceed()
                        }
                    }

                    if (m.name == "getSpanSize") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val record = chain.thisObject
                            val spec = ControlCenterTileDetectorHos4.inspectRecord(record)
                            if (spec != null) {
                                val g = ControlCenterConfigHolder.isGroupAnchor(spec)
                                if (g != null) {
                                    return@intercept g.getEffectiveColCount().coerceIn(1, 4)
                                }
                                if (ControlCenterConfigHolder.sCircularCardSpecs.contains(spec)) {
                                    return@intercept 1
                                }
                                if (ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)) {
                                    return@intercept 2
                                }
                                if (ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)) {
                                    return@intercept 1
                                }
                                if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) ||
                                    ControlCenterTileDetectorHos4.isCard(spec)) {
                                    return@intercept 2
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed hooking QSRecord", t)
            }

            // 8. Hook CustomizeHeaderController for Group / Ungroup buttons in Edit Mode
            try {
                val headerCtrlClass = Class.forName("miui.systemui.controlcenter.panel.main.header.CustomizeHeaderController", false, classLoader)
                for (m in headerCtrlClass.declaredMethods) {
                    if (m.name == "onCreate" || m.name == "transitionFrom") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val ctrl = chain.thisObject
                                ControlCenterGrouping.ensureGroupButtonInjected(ctrl, classLoader)
                            } catch (_: Throwable) {}
                            result
                        }
                    }
                }
            } catch (_: Throwable) {}

            pluginHooked = true
            Log.i(TAG, "Control Center Hooks for HyperOS 4 successfully initialized!")

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ControlCenterHookHos4", t)
        }
    }

    fun bindCustomModuleHolder(holder: Any, classLoader: ClassLoader) {
        try {
            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
            if (itemView != null && itemView.findViewById<ViewGroup>(0x7f099995) != null) {
                var group = itemView.getTag(0x7f099994) as? TileGroup
                if (group == null) {
                    val spec = itemView.getTag(0x7f099990) as? String
                        ?: try {
                            val itemF = holder.javaClass.getDeclaredField("item")
                            itemF.isAccessible = true
                            ControlCenterTileDetectorHos4.inspectRecord(itemF.get(holder))
                        } catch (t: Throwable) { null }
                    if (spec != null) {
                        itemView.setTag(0x7f099990, spec)
                        group = ControlCenterConfigHolder.isGroupAnchor(spec)
                    }
                }
                if (group != null) {
                    ControlCenterGroupingHos4.rebindGroupContainer(
                        itemView,
                        group,
                        ControlCenterConfigHolder.sQSListController,
                        classLoader
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "bindCustomModuleHolder error", t)
        }
    }
}
