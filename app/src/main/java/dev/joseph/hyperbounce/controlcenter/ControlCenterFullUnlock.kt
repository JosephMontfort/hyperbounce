package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.libxposed.api.XposedModule
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ControlCenterFullUnlock
 *
 * Implements 100% Full Unlock for HyperOS Control Center:
 * 1. Arbitrary placement of any tile, card, slider, or media player at any position.
 * 2. Decouples Wi-Fi and Mobile Data cards into independent slots ("card:wifi", "card:cell").
 * 3. Dynamic Proxy Content Map: Wraps each slot in a unique ProxyMainPanelContent so Xiaomi's
 *    LinkedHashMap in MainPanelAdapter can interleave any items without controller boundaries.
 * 4. Dynamic Landscape Flow: Simulates 4-column packing to place the first N rows (up to 5 rows)
 *    on the right panel in landscape, and flows all subsequent/overflowing items to the left panel.
 * 5. Full Drag & Drop Support: Allows live reordering of all active items in Edit Mode with persistence.
 */
object ControlCenterFullUnlock {
    const val TAG = "HyperBounce_CC_FullUnlock"
    const val PREFS_NAME = "hyperbounce_cc_layout"
    const val PREF_UNIFIED_ORDER = "unified_slot_order"
    const val SECURE_KEY_UNIFIED_ORDER = "hyperbounce_unified_slot_order"

    // Maximum rows allowed on the right panel in landscape mode (dynamic 4 or 5 row capacity)
    const val MAX_LANDSCAPE_RIGHT_ROWS = 5

    // Unified slot order persisted across sessions
    val sUnifiedSlotOrder = CopyOnWriteArrayList<String>()
    @Volatile var sOrderLoaded = false

    // Cache of proxies keyed by slot ID
    private val sProxyCache = ConcurrentHashMap<String, Any>()
    private val sProxyHandlers = ConcurrentHashMap<String, ProxyContentHandler>()

    // Global reference to distributor and adapters
    @Volatile var sDistributor: Any? = null
    @Volatile var sLeftAdapter: Any? = null
    @Volatile var sRightAdapter: Any? = null

    @Volatile private var lastMoveTimestamp = 0L

    // Background handler for non-blocking persistence
    private val sBgHandler: Handler by lazy {
        val ht = android.os.HandlerThread("CCFullUnlock-Persistence")
        ht.start()
        Handler(ht.looper)
    }

    /**
     * Unified slot item holding all data needed for layout and adapter binding.
     */
    data class SlotItem(
        val id: String,
        val owner: Any,
        val item: Any,
        val viewType: Int,
        var spanW: Int,
        var spanH: Int
    )

    /**
     * Proxy InvocationHandler implementing MainPanelContent.
     */
    class ProxyContentHandler(
        val id: String,
        @Volatile var target: Any,
        @Volatile var item: Any,
        @Volatile var priority: Int = 100,
        @Volatile var isRight: Boolean = true
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "getListItems" -> return listOf(item)
                "getPriority" -> return priority
                "getRightOrLeft" -> return isRight
                "equals" -> return proxy === args?.getOrNull(0)
                "hashCode" -> return System.identityHashCode(proxy)
                "toString" -> return "ProxyMainPanelContent($id -> ${target.javaClass.simpleName})"
            }
            return forwardCall(target, method, args)
        }
    }

    /**
     * Forward method invocation to target controller safely.
     */
    private fun forwardCall(target: Any, method: Method, args: Array<out Any?>?): Any? {
        try {
            val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
            targetMethod.isAccessible = true
            return targetMethod.invoke(target, *(args ?: emptyArray()))
        } catch (e: NoSuchMethodException) {}

        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.name == method.name && m.parameterTypes.size == method.parameterTypes.size) {
                    var match = true
                    for (i in m.parameterTypes.indices) {
                        if (!isTypeCompatible(m.parameterTypes[i], method.parameterTypes[i])) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        m.isAccessible = true
                        return m.invoke(target, *(args ?: emptyArray()))
                    }
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun isTypeCompatible(targetType: Class<*>, argType: Class<*>): Boolean {
        if (targetType.isAssignableFrom(argType) || argType.isAssignableFrom(targetType)) return true
        if (targetType.isPrimitive || argType.isPrimitive) {
            return boxType(targetType) == boxType(argType)
        }
        return false
    }

    private fun boxType(c: Class<*>): Class<*> = when (c) {
        Int::class.javaPrimitiveType -> Integer::class.java
        Boolean::class.javaPrimitiveType -> java.lang.Boolean::class.java
        Float::class.javaPrimitiveType -> java.lang.Float::class.java
        Long::class.javaPrimitiveType -> java.lang.Long::class.java
        Double::class.javaPrimitiveType -> java.lang.Double::class.java
        Byte::class.javaPrimitiveType -> java.lang.Byte::class.java
        Short::class.javaPrimitiveType -> java.lang.Short::class.java
        Char::class.javaPrimitiveType -> java.lang.Character::class.java
        else -> c
    }

    /**
     * Obtains or creates a ProxyMainPanelContent for the given slot.
     */
    fun getOrCreateProxy(slot: SlotItem, classLoader: ClassLoader, priority: Int, isRight: Boolean): Any {
        val existing = sProxyCache[slot.id]
        if (existing != null) {
            val handler = sProxyHandlers[slot.id]
            if (handler != null) {
                handler.target = slot.owner
                handler.item = slot.item
                handler.priority = priority
                handler.isRight = isRight
            }
            return existing
        }

        val contentInterface = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContent", false, classLoader)
        val handler = ProxyContentHandler(slot.id, slot.owner, slot.item, priority, isRight)
        val proxy = Proxy.newProxyInstance(classLoader, arrayOf(contentInterface), handler)
        sProxyCache[slot.id] = proxy
        sProxyHandlers[slot.id] = handler
        return proxy
    }

    /**
     * Loads the saved unified slot order from persistent storage.
     */
    @Synchronized
    fun loadOrder(context: Context?) {
        if (sOrderLoaded || context == null) return
        sOrderLoaded = true
        try {
            if (!ControlCenterConfigHolder.sHasLoadedPriorities) {
                ControlCenterConfigHolder.loadSavedPriorities(context)
            }

            // 1. Settings.Secure
            var savedStr = try {
                Settings.Secure.getString(context.contentResolver, SECURE_KEY_UNIFIED_ORDER)
            } catch (t: Throwable) { null }

            // 2. SharedPreferences fallback
            if (savedStr.isNullOrBlank()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                savedStr = prefs.getString(PREF_UNIFIED_ORDER, null)
            }

            if (!savedStr.isNullOrBlank()) {
                val list = savedStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) {
                    sUnifiedSlotOrder.clear()
                    sUnifiedSlotOrder.addAll(list)
                    cleanStaleSlots()
                    Log.i(TAG, "Loaded saved unified slot order (${sUnifiedSlotOrder.size} items): $sUnifiedSlotOrder")
                    return
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load unified slot order", t)
        }
    }

    /**
     * Asynchronously persists the unified slot order.
     */
    fun saveOrder(context: Context?) {
        val targetCtx = context ?: ControlCenterConfigHolder.sLastKnownContext ?: return
        val orderSnapshot = ArrayList(sUnifiedSlotOrder)
        if (orderSnapshot.isEmpty()) return

        sBgHandler.post {
            try {
                val joined = orderSnapshot.joinToString(",")
                // 1. SharedPreferences
                val prefs = targetCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(PREF_UNIFIED_ORDER, joined).apply()

                // 2. Settings.Secure
                try {
                    Settings.Secure.putString(targetCtx.contentResolver, SECURE_KEY_UNIFIED_ORDER, joined)
                } catch (t: Throwable) {}

                Log.d(TAG, "Saved unified slot order (${orderSnapshot.size} items)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save unified slot order", t)
            }
        }
    }

    /**
     * Purges stale groups, stale member tiles, and duplicate slots from both
     * sKnownSlots and sUnifiedSlotOrder to guarantee consistent layout state.
     */
    fun cleanStaleSlots() {
        try {
            val activeGroupIds = ControlCenterConfigHolder.sTileGroups.map { "group:${it.id}" }.toSet()
            val groupedTileSpecs = HashSet<String>()
            for (g in ControlCenterConfigHolder.sTileGroups) {
                groupedTileSpecs.addAll(g.specs)
            }

            // 1. Remove group slots that are no longer active
            sKnownSlots.keys.removeIf { it.startsWith("group:") && !activeGroupIds.contains(it) }
            sUnifiedSlotOrder.removeIf { it.startsWith("group:") && !activeGroupIds.contains(it) }
            sProxyCache.keys.removeIf { it.startsWith("group:") && !activeGroupIds.contains(it) }
            sProxyHandlers.keys.removeIf { it.startsWith("group:") && !activeGroupIds.contains(it) }

            // 2. Remove standalone tile slots for tiles that belong to an active group
            for (spec in groupedTileSpecs) {
                sKnownSlots.remove("tile:$spec")
                sUnifiedSlotOrder.remove("tile:$spec")
                sProxyCache.remove("tile:$spec")
                sProxyHandlers.remove("tile:$spec")
                sKnownSlots.remove("card:$spec")
                sUnifiedSlotOrder.remove("card:$spec")
                sProxyCache.remove("card:$spec")
                sProxyHandlers.remove("card:$spec")
            }

            // 3. Deduplicate sUnifiedSlotOrder while preserving order
            val seen = HashSet<String>()
            val deduped = ArrayList<String>()
            for (id in sUnifiedSlotOrder) {
                if (seen.add(id)) {
                    deduped.add(id)
                }
            }
            if (deduped.size != sUnifiedSlotOrder.size) {
                sUnifiedSlotOrder.clear()
                sUnifiedSlotOrder.addAll(deduped)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "cleanStaleSlots error", t)
        }
    }

    /**
     * Called when a new TileGroup is created. Replaces member tiles in sUnifiedSlotOrder
     * with the new group slot and purges standalone slots.
     */
    fun onGroupCreated(groupId: String, specs: List<String>, context: Context?) {
        try {
            val groupSlotId = "group:$groupId"
            var insertIdx = -1
            for (spec in specs) {
                val tileId = "tile:$spec"
                val cardId = "card:$spec"
                val idx1 = sUnifiedSlotOrder.indexOf(tileId)
                val idx2 = sUnifiedSlotOrder.indexOf(cardId)
                val idx = if (idx1 >= 0) idx1 else idx2
                if (idx >= 0 && (insertIdx == -1 || idx < insertIdx)) {
                    insertIdx = idx
                }
                sKnownSlots.remove(tileId)
                sKnownSlots.remove(cardId)
                sProxyCache.remove(tileId)
                sProxyCache.remove(cardId)
                sProxyHandlers.remove(tileId)
                sProxyHandlers.remove(cardId)
            }
            for (spec in specs) {
                sUnifiedSlotOrder.remove("tile:$spec")
                sUnifiedSlotOrder.remove("card:$spec")
            }
            if (insertIdx >= 0 && insertIdx <= sUnifiedSlotOrder.size) {
                sUnifiedSlotOrder.add(insertIdx, groupSlotId)
            } else {
                sUnifiedSlotOrder.add(groupSlotId)
            }
            cleanStaleSlots()
            saveOrder(context)
        } catch (t: Throwable) {
            Log.e(TAG, "onGroupCreated error", t)
        }
    }

    /**
     * Called when TileGroups are disassembled. Replaces the group slot in sUnifiedSlotOrder
     * with the member tiles and purges the group slot.
     */
    fun onGroupsDisassembled(removedGroups: List<TileGroup>, context: Context?) {
        try {
            for (g in removedGroups) {
                val groupSlotId = "group:${g.id}"
                sKnownSlots.remove(groupSlotId)
                sProxyCache.remove(groupSlotId)
                sProxyHandlers.remove(groupSlotId)
                val idx = sUnifiedSlotOrder.indexOf(groupSlotId)
                sUnifiedSlotOrder.remove(groupSlotId)

                val memberTileIds = g.specs.map { if (it == "wifi" || it == "cell") "card:$it" else "tile:$it" }
                if (idx >= 0 && idx <= sUnifiedSlotOrder.size) {
                    sUnifiedSlotOrder.addAll(idx, memberTileIds)
                } else {
                    sUnifiedSlotOrder.addAll(memberTileIds)
                }
            }
            cleanStaleSlots()
            saveOrder(context)
        } catch (t: Throwable) {
            Log.e(TAG, "onGroupsDisassembled error", t)
        }
    }

    /**
     * Clears all unified order state and caches on reset.
     */
    fun resetOrder(context: Context?) {
        try {
            sUnifiedSlotOrder.clear()
            sKnownSlots.clear()
            sProxyCache.clear()
            sProxyHandlers.clear()
            sOrderLoaded = false
            val targetCtx = context ?: ControlCenterConfigHolder.sLastKnownContext
            if (targetCtx != null) {
                try {
                    val prefs = targetCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().remove(PREF_UNIFIED_ORDER).apply()
                } catch (t: Throwable) {}
                try {
                    Settings.Secure.putString(targetCtx.contentResolver, SECURE_KEY_UNIFIED_ORDER, "")
                } catch (t: Throwable) {}
            }
            True2DGridEngine.invalidateAll()
        } catch (t: Throwable) {
            Log.e(TAG, "resetOrder error", t)
        }
    }

    fun isNonDraggableSystemItem(vt: Int): Boolean {
        return vt == 8398 || vt == 3484337 || vt == 8397 || vt == 432337 || vt == 366837 || vt == 334833 || vt == 3348 || vt == 732873
    }

    /**
     * Calculates width and height spans for any item.
     */
    fun getItemSpans(item: Any, viewType: Int, spec: String?): Pair<Int, Int> {
        // 0. Full-width system items -> 4x1
        if (True2DGridEngine.isFullWidthSystemItem(item, viewType)) {
            return Pair(4, 1)
        }
        // Header Space, Footer Space, Dividers, Security Footer -> 4x1
        if (viewType == 432337 || viewType == 366837 || viewType == 8398 || viewType == 3484337 || viewType == 8397 || viewType == 732873) {
            return Pair(4, 1)
        }
        // 1. Media Player -> dynamic size
        if (viewType == 3484336 || item.javaClass.name.contains("MediaPlayer")) {
            return when (ControlCenterConfigHolder.sMediaPlayerSize) {
                "2x1" -> Pair(2, 1)
                "1x2" -> Pair(1, 2)
                "4x1" -> Pair(4, 1)
                "4x2" -> Pair(4, 2)
                "2x4" -> Pair(2, 4)
                else -> Pair(2, 2)
            }
        }
        // 2. Sliders -> 1x2 vertical or 2..4x1 horizontal
        if (viewType == 3484338 || item.javaClass.name.contains("Brightness")) {
            return if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) Pair(ControlCenterConfigHolder.sBrightnessSliderWidth, 1) else Pair(1, 2)
        }
        if (viewType == 3484339 || item.javaClass.name.contains("Volume")) {
            return if (ControlCenterConfigHolder.sVolumeSliderHorizontal) Pair(ControlCenterConfigHolder.sVolumeSliderWidth, 1) else Pair(1, 2)
        }
        if (item.javaClass.name.contains("Slider")) {
            return Pair(1, 2)
        }
        // 3. Smart home -> 2x1
        if (viewType == 338423 || viewType == 2668765) {
            return Pair(2, 1)
        }
        // 4. Cards (Wi-Fi, Cell) & Circular tiles
        if (spec != null) {
            val g = ControlCenterConfigHolder.isGroupAnchor(spec)
            if (g != null) {
                if (g.is2x2Group()) return Pair(2, 2)
                if (g.specs.size == 2) return if (g.isVertical) Pair(1, 2) else Pair(2, 1)
            }
            if (ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)) return Pair(2, 2)
            if (ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)) return Pair(1, 2)
            if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)) return Pair(2, 1)
            if (ControlCenterConfigHolder.sCircularCardSpecs.contains(spec)) return Pair(1, 1)
            if (spec == "wifi" || spec == "cell") {
                return Pair(2, 1)
            }
        }
        // 5. Default circular tile -> 1x1
        return Pair(1, 1)
    }

    /**
     * Dynamic Landscape Row Split Simulation:
     * Simulates 4-column bin-packing to place the first N rows (up to maxRightRows = 5)
     * on the right panel, and flows all overflowing items to the left panel.
     */
    fun calculateLandscapeRowSplit(
        activeSlots: List<SlotItem>,
        maxRightRows: Int = MAX_LANDSCAPE_RIGHT_ROWS
    ): Pair<List<SlotItem>, List<SlotItem>> {
        val rightList = ArrayList<SlotItem>()
        val leftList = ArrayList<SlotItem>()

        // 4-column occupancy grid for up to 60 rows
        val grid = Array(60) { BooleanArray(4) { false } }

        fun canFit(startRow: Int, startCol: Int, w: Int, h: Int): Boolean {
            if (startCol + w > 4) return false
            for (r in startRow until (startRow + h)) {
                if (r >= 60) return false
                for (c in startCol until (startCol + w)) {
                    if (grid[r][c]) return false
                }
            }
            return true
        }

        fun markOccupied(startRow: Int, startCol: Int, w: Int, h: Int) {
            for (r in startRow until (startRow + h)) {
                for (c in startCol until (startCol + w)) {
                    grid[r][c] = true
                }
            }
        }

        fun findEarliestFit(w: Int, h: Int): Pair<Int, Int>? {
            for (r in 0 until 60) {
                for (c in 0..4 - w) {
                    if (canFit(r, c, w, h)) {
                        return Pair(r, c)
                    }
                }
            }
            return null
        }

        var overflowedToLeft = false

        for (slot in activeSlots) {
            if (overflowedToLeft) {
                leftList.add(slot)
                continue
            }

            val fit = findEarliestFit(slot.spanW, slot.spanH)
            if (fit == null) {
                overflowedToLeft = true
                leftList.add(slot)
                continue
            }

            val (row, col) = fit
            val endRow = row + slot.spanH

            if (endRow <= maxRightRows) {
                markOccupied(row, col, slot.spanW, slot.spanH)
                rightList.add(slot)
            } else {
                overflowedToLeft = true
                leftList.add(slot)
            }
        }

        return Pair(rightList, leftList)
    }

    // Cache of all active slots
    private val sKnownSlots = ConcurrentHashMap<String, SlotItem>()

    // Per-adapter header and footer entries
    private val sAdapterHeaders = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    private val sAdapterWordless = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    private val sAdapterQsFooters = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    private val sAdapterFooters = ConcurrentHashMap<Any, MutableList<Pair<Any, List<Any>>>>()

    /**
     * Resolves the SlotItem ID from a ViewHolder instance.
     */
    fun getSlotIdFromViewHolder(vh: Any?): String? {
        if (vh == null) return null
        try {
            val getOwnerMethod = try { vh.javaClass.getMethod("getOwner") } catch (e: Throwable) { null }
            val owner = getOwnerMethod?.invoke(vh)
            if (owner != null && Proxy.isProxyClass(owner.javaClass)) {
                val handler = Proxy.getInvocationHandler(owner) as? ProxyContentHandler
                if (handler != null) return handler.id
            }
        } catch (t: Throwable) {}

        // Fallback via getItem and viewType
        try {
            val getItemMethod = try { vh.javaClass.getMethod("getItem\$miui_controlcenter_release") } catch (e: Throwable) { null }
            val item = getItemMethod?.invoke(vh)
            val spec = item?.let { ControlCenterConfigHolder.getRecordSpec(it) }
            val getItemViewTypeMethod = vh.javaClass.getMethod("getItemViewType")
            val vt = getItemViewTypeMethod.invoke(vh) as Int

            if (spec == "wifi") return "card:wifi"
            if (spec == "cell") return "card:cell"
            if (spec != null) {
                val g = ControlCenterConfigHolder.isGroupAnchor(spec)
                if (g != null) return "group:${g.id}"
                return "tile:$spec"
            }
            if (vt == 3484336 || vt == 63342) return "component:media"
            if (vt == 3484338 || vt == 274442) return "component:brightness"
            if (vt == 3484339 || vt == 865269) return "component:volume"
            if (vt == 338423) return "component:device_center"
            if (vt == 2668765) return "component:device_controls"
        } catch (t: Throwable) {}
        return null
    }

    /**
     * Intercepts and reorganizes MainPanelAdapter.contentMap.
     */
    fun reorganizeContentMap(adapter: Any, classLoader: ClassLoader) {
        if (!ControlCenterConfigHolder.config.modularEditEnabled) return

        try {
            val adapterClass = adapter.javaClass
            val contentMapField = adapterClass.getDeclaredField("contentMap")
            contentMapField.isAccessible = true
            val stockMap = contentMapField.get(adapter) as? LinkedHashMap<Any, List<Any>>
            if (stockMap == null || stockMap.isEmpty()) return

            val ctxField = adapterClass.getDeclaredField("context")
            ctxField.isAccessible = true
            val context = ctxField.get(adapter) as? Context ?: ControlCenterConfigHolder.sLastKnownContext
            if (context != null) loadOrder(context)

            cleanStaleSlots()

            val isLandscape = context?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
            ControlCenterConfigHolder.sMainPanelAdapter = adapter

            // Check if stockMap already consists of our Proxies
            val hasProxies = stockMap.keys.any { Proxy.isProxyClass(it.javaClass) }

            if (!hasProxies) {
                // Fresh stock map from HyperOS: parse active slots & footer entries for this adapter
                val footers = ArrayList<Pair<Any, List<Any>>>()
                var headerEntry: Pair<Any, List<Any>>? = null
                var wordlessEntry: Pair<Any, List<Any>>? = null
                var qsFooterEntry: Pair<Any, List<Any>>? = null

                for ((controller, items) in stockMap) {
                    val realController = if (Proxy.isProxyClass(controller.javaClass)) {
                        (Proxy.getInvocationHandler(controller) as? ProxyContentHandler)?.target ?: controller
                    } else controller
                    val ctrlName = realController.javaClass.simpleName

                    // 1. NEVER include compact QS cards (they create duplicate circular wifi/cell)
                    if (ctrlName.contains("CompactQS") || ctrlName.contains("CompactQSCard")) {
                        continue
                    }

                    // 2. Header space controller -> MUST be placed at TOP
                    if (ctrlName.contains("HeaderSpaceController")) {
                        headerEntry = Pair(controller, items)
                        continue
                    }

                    // 3. Wordless mode controller ("Show icon labels")
                    if (ctrlName.contains("WordlessModeController")) {
                        wordlessEntry = Pair(controller, items)
                        continue
                    }

                    // 4. Footer space controller -> placed at BOTTOM
                    if (ctrlName.contains("FooterSpaceController")) {
                        footers.add(Pair(controller, items))
                        continue
                    }

                    // 5. Active content controllers
                    if (ctrlName.contains("QSCardsController")) {
                        for (i in items.indices) {
                            val item = items[i]
                            val spec = ControlCenterConfigHolder.getRecordSpec(item)
                            var vt = 2273
                            try {
                                val getTypeM = item.javaClass.getMethod("getType")
                                vt = getTypeM.invoke(item) as Int
                            } catch (t: Throwable) {}

                            if (spec != null) {
                                val group = ControlCenterConfigHolder.findGroupForSpec(spec)
                                if (group != null) {
                                    if (ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                        val id = "group:${group.id}"
                                        val spans = getItemSpans(item, vt, spec)
                                        sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                                    }
                                    sKnownSlots.remove("card:$spec")
                                    sKnownSlots.remove("tile:$spec")
                                    sUnifiedSlotOrder.remove("card:$spec")
                                    sUnifiedSlotOrder.remove("tile:$spec")
                                    continue
                                }
                            }

                            val id = if (spec == "wifi") "card:wifi" else if (spec == "cell") "card:cell" else "card:$spec"
                            val spans = getItemSpans(item, vt, spec)
                            sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                        }
                    } else if (ctrlName.contains("MediaPlayerController")) {
                        val item = items.firstOrNull() ?: controller
                        val spans = getItemSpans(item, 3484336, null)
                        sKnownSlots["component:media"] = SlotItem("component:media", controller, item, 3484336, spans.first, spans.second)
                    } else if (ctrlName.contains("BrightnessSliderController")) {
                        val item = items.firstOrNull() ?: controller
                        val spans = getItemSpans(item, 3484338, null)
                        sKnownSlots["component:brightness"] = SlotItem("component:brightness", controller, item, 3484338, spans.first, spans.second)
                    } else if (ctrlName.contains("VolumeSliderController")) {
                        val item = items.firstOrNull() ?: controller
                        val spans = getItemSpans(item, 3484339, null)
                        sKnownSlots["component:volume"] = SlotItem("component:volume", controller, item, 3484339, spans.first, spans.second)
                    } else if (ctrlName.contains("DeviceCenterEntryController")) {
                        // Check if available in settings
                        val isAvail = try {
                            val availM = realController.javaClass.getMethod("available", Boolean::class.javaPrimitiveType)
                            availM.invoke(realController, isLandscape) as Boolean
                        } catch (t: Throwable) { false }
                        if (isAvail) {
                            val item = items.firstOrNull() ?: controller
                            val spans = getItemSpans(item, 338423, null)
                            sKnownSlots["component:device_center"] = SlotItem("component:device_center", controller, item, 338423, spans.first, spans.second)
                        }
                    } else if (ctrlName.contains("DeviceControlsEntryController")) {
                        // Check if available in settings
                        val isAvail = try {
                            val availM = realController.javaClass.getMethod("available", Boolean::class.javaPrimitiveType)
                            availM.invoke(realController, isLandscape) as Boolean
                        } catch (t: Throwable) { false }
                        if (isAvail) {
                            val item = items.firstOrNull() ?: controller
                            val spans = getItemSpans(item, 2668765, null)
                            sKnownSlots["component:device_controls"] = SlotItem("component:device_controls", controller, item, 2668765, spans.first, spans.second)
                        }
                    } else if (ctrlName.contains("QSListController")) {
                        val rawFooterItems = ArrayList<Any>()
                        val seenAvailableSpecs = HashSet<String>()

                        for (item in items) {
                            val spec = ControlCenterConfigHolder.getRecordSpec(item)
                            var isAdded = true
                            try {
                                val addedField = item.javaClass.getDeclaredField("added")
                                addedField.isAccessible = true
                                isAdded = addedField.getBoolean(item)
                            } catch (t: Throwable) {}

                            var vt = 2272
                            try {
                                val getTypeM = item.javaClass.getMethod("getType")
                                vt = getTypeM.invoke(item) as Int
                            } catch (t: Throwable) {}

                            val isDividerOrHeader = (vt == 8398 || vt == 3484337 || vt == 8397)

                            if (isAdded && !isDividerOrHeader) {
                                if (spec != null) {
                                    val group = ControlCenterConfigHolder.findGroupForSpec(spec)
                                    if (group != null) {
                                        if (ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                            val id = "group:${group.id}"
                                            val spans = getItemSpans(item, vt, spec)
                                            sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                                        }
                                        sKnownSlots.remove("tile:$spec")
                                        sUnifiedSlotOrder.remove("tile:$spec")
                                        continue
                                    }
                                }
                                val id = "tile:$spec"
                                val spans = getItemSpans(item, vt, spec)
                                sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                            } else {
                                if (spec != null) {
                                    sKnownSlots.remove("tile:$spec")
                                    sUnifiedSlotOrder.remove("tile:$spec")
                                }
                                // Potential footer item: either divider/header or unadded available tile
                                if (isDividerOrHeader) {
                                    rawFooterItems.add(item)
                                } else if (spec != null) {
                                    // Check if tile is ALREADY an active tile in our unified slots!
                                    val isAlreadyActive = sKnownSlots.containsKey("tile:$spec") ||
                                            sUnifiedSlotOrder.contains("tile:$spec") ||
                                            ControlCenterConfigHolder.isGroupMember(spec)

                                    // Deduplicate available tiles so the same spec never appears multiple times
                                    if (!isAlreadyActive && !seenAvailableSpecs.contains(spec)) {
                                        seenAvailableSpecs.add(spec)
                                        rawFooterItems.add(item)
                                    }
                                }
                            }
                        }

                        // Clean up consecutive or empty dividers from rawFooterItems
                        val cleanedFooterItems = ArrayList<Any>()
                        for (idx in rawFooterItems.indices) {
                            val item = rawFooterItems[idx]
                            var vt = 2272
                            try {
                                val getTypeM = item.javaClass.getMethod("getType")
                                vt = getTypeM.invoke(item) as Int
                            } catch (t: Throwable) {}

                            val isDividerText = (vt == 8398) // "System toggles" or "App toggles"
                            val isDividerLine = (vt == 3484337)

                            if (isDividerText) {
                                // Check if there is at least one tile following this header before the next divider
                                var hasTilesBelow = false
                                for (nextIdx in (idx + 1) until rawFooterItems.size) {
                                    val nextItem = rawFooterItems[nextIdx]
                                    var nextVt = 2272
                                    try {
                                        val getNextTypeM = nextItem.javaClass.getMethod("getType")
                                        nextVt = getNextTypeM.invoke(nextItem) as Int
                                    } catch (t: Throwable) {}
                                    if (nextVt == 8398 || nextVt == 3484337) {
                                        break
                                    }
                                    hasTilesBelow = true
                                    break
                                }
                                if (hasTilesBelow) {
                                    cleanedFooterItems.add(item)
                                }
                            } else if (isDividerLine) {
                                val lastItem = cleanedFooterItems.lastOrNull()
                                var lastVt = 0
                                if (lastItem != null) {
                                    try {
                                        lastVt = lastItem.javaClass.getMethod("getType").invoke(lastItem) as Int
                                    } catch (t: Throwable) {}
                                }
                                if (lastVt != 3484337 && idx < rawFooterItems.size - 1) {
                                    cleanedFooterItems.add(item)
                                }
                            } else {
                                cleanedFooterItems.add(item)
                            }
                        }

                        while (cleanedFooterItems.isNotEmpty()) {
                            val last = cleanedFooterItems.last()
                            var lastVt = 0
                            try { lastVt = last.javaClass.getMethod("getType").invoke(last) as Int } catch (t: Throwable) {}
                            if (lastVt == 3484337 || lastVt == 8398) {
                                cleanedFooterItems.removeAt(cleanedFooterItems.size - 1)
                            } else {
                                break
                            }
                        }

                        if (cleanedFooterItems.isNotEmpty()) {
                            qsFooterEntry = Pair(controller, cleanedFooterItems)
                        }
                    } else {
                        // EditButtonController, SecurityFooterController, etc.
                        val isAvail = try {
                            val availM = realController.javaClass.getMethod("available", Boolean::class.javaPrimitiveType)
                            availM.invoke(realController, isLandscape) as Boolean
                        } catch (t: Throwable) { true }
                        if (isAvail) {
                            footers.add(Pair(controller, items))
                        }
                    }
                }

                if (headerEntry != null) sAdapterHeaders[adapter] = headerEntry else sAdapterHeaders.remove(adapter)
                if (wordlessEntry != null) sAdapterWordless[adapter] = wordlessEntry else sAdapterWordless.remove(adapter)
                if (qsFooterEntry != null && ControlCenterConfigHolder.sIsEditMode) {
                    sAdapterQsFooters[adapter] = qsFooterEntry
                } else {
                    sAdapterQsFooters.remove(adapter)
                }
                sAdapterFooters[adapter] = footers
            }

            buildAndSetContentMap(adapter, classLoader)

        } catch (t: Throwable) {
            Log.e(TAG, "reorganizeContentMap error", t)
        }
    }

    /**
     * Rebuilds and applies the new ContentMap to the adapter based on sUnifiedSlotOrder.
     */
    fun buildAndSetContentMap(adapter: Any, classLoader: ClassLoader) {
        try {
            val adapterClass = adapter.javaClass
            val contentMapField = adapterClass.getDeclaredField("contentMap")
            contentMapField.isAccessible = true

            val ctxField = adapterClass.getDeclaredField("context")
            ctxField.isAccessible = true
            val context = ctxField.get(adapter) as? Context ?: ControlCenterConfigHolder.sLastKnownContext
            val isLandscape = context?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE

            // Distinguish Left Adapter vs Right Adapter
            var isLeftPanel = false
            var isRightPanel = false
            if (sDistributor != null) {
                try {
                    val getLeftMethod = sDistributor!!.javaClass.getMethod("getLeftAdapter")
                    val leftAdp = getLeftMethod.invoke(sDistributor)
                    val getRightMethod = sDistributor!!.javaClass.getMethod("getRightAdapter")
                    val rightAdp = getRightMethod.invoke(sDistributor)
                    if (adapter === leftAdp) isLeftPanel = true
                    if (adapter === rightAdp) isRightPanel = true
                } catch (t: Throwable) {}
            }

            // Fallback panel detection via contentList contents
            if (!isLeftPanel && !isRightPanel) {
                val contentListField = adapterClass.getDeclaredField("contentList")
                contentListField.isAccessible = true
                val cList = contentListField.get(adapter) as? List<*>
                if (cList != null) {
                    val hasCards = cList.any { it?.javaClass?.name?.contains("QSCardsController") == true }
                    val hasQSList = cList.any { it?.javaClass?.name?.contains("QSListController") == true }
                    if (hasCards && !hasQSList) isRightPanel = true
                    if (hasQSList && !hasCards) isLeftPanel = true
                }
            }

            cleanStaleSlots()

            // Dynamically refresh slot spans to reflect any recent size/rotation changes
            for ((slotId, slot) in sKnownSlots) {
                val spec = when {
                    slotId.startsWith("tile:") -> slotId.substring(5)
                    slotId.startsWith("card:") -> slotId.substring(5)
                    else -> null
                }
                val currentSpans = getItemSpans(slot.item, slot.viewType, spec)
                slot.spanW = currentSpans.first
                slot.spanH = currentSpans.second
            }

            // Order active slots according to sUnifiedSlotOrder
            val orderedSlots = ArrayList<SlotItem>()
            val remainingAvailable = LinkedHashMap(sKnownSlots)

            for (savedId in sUnifiedSlotOrder) {
                val slot = remainingAvailable.remove(savedId)
                if (slot != null) {
                    orderedSlots.add(slot)
                }
            }

            // Append any new active items not yet in sUnifiedSlotOrder
            var orderUpdated = false
            for ((newId, slot) in remainingAvailable) {
                if (newId.startsWith("tile:")) {
                    val spec = newId.substring(5)
                    if (ControlCenterConfigHolder.isGroupMember(spec)) continue
                }
                if (newId.startsWith("group:")) {
                    val gId = newId.substring(6)
                    val activeIds = ControlCenterConfigHolder.sTileGroups.map { it.id }.toSet()
                    if (!activeIds.contains(gId)) continue
                }
                orderedSlots.add(slot)
                sUnifiedSlotOrder.add(newId)
                orderUpdated = true
            }

            if (orderUpdated && context != null) {
                saveOrder(context)
            }

            // Build new ContentMap based on orientation and panel
            val newContentMap = LinkedHashMap<Any, List<Any>>()

            // 1. TOP HEADER SPACER: ALWAYS FIRST (index 0) so tiles start below clock/status bar!
            val headerEntry = sAdapterHeaders[adapter]
            if (headerEntry != null) {
                newContentMap[headerEntry.first] = headerEntry.second
            }

            if (!isLandscape) {
                // Portrait mode:
                // 2. WordlessMode ("Show icon labels") - ONLY in Edit Mode!
                if (ControlCenterConfigHolder.sIsEditMode) {
                    val wordless = sAdapterWordless[adapter]
                    if (wordless != null) {
                        newContentMap[wordless.first] = wordless.second
                    }
                }

                // 3. All ordered active slots flow into this single adapter
                for (i in orderedSlots.indices) {
                    val slot = orderedSlots[i]
                    val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = true)
                    newContentMap[proxy] = listOf(slot.item)
                }

                // 4. Edit Divider & Inactive Available tiles pool - ONLY in Edit Mode!
                if (ControlCenterConfigHolder.sIsEditMode) {
                    val qsFooter = sAdapterQsFooters[adapter]
                    if (qsFooter != null) {
                        newContentMap[qsFooter.first] = qsFooter.second
                    }
                }

                // 5. Other footers (edit button, footer space)
                val footers = sAdapterFooters[adapter]
                if (footers != null) {
                    for ((ctrl, fItems) in footers) {
                        newContentMap[ctrl] = fItems
                    }
                }
            } else {
                // Landscape Mode: Dynamic Row Split Simulation (up to 5 rows on Right Side)
                val (rightSlots, leftSlots) = calculateLandscapeRowSplit(orderedSlots, maxRightRows = MAX_LANDSCAPE_RIGHT_ROWS)

                if (isRightPanel) {
                    // Right Panel: gets rightSlots (first N rows, max 5 rows)
                    for (i in rightSlots.indices) {
                        val slot = rightSlots[i]
                        val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = true)
                        newContentMap[proxy] = listOf(slot.item)
                    }

                    // Right panel footer spacer (if present)
                    val footers = sAdapterFooters[adapter]
                    if (footers != null) {
                        for ((ctrl, fItems) in footers) {
                            if (ctrl.javaClass.simpleName.contains("FooterSpaceController")) {
                                newContentMap[ctrl] = fItems
                            }
                        }
                    }
                } else {
                    // Left Panel:
                    // Show icon labels ONLY in edit mode!
                    if (ControlCenterConfigHolder.sIsEditMode) {
                        val wordless = sAdapterWordless[adapter]
                        if (wordless != null) {
                            newContentMap[wordless.first] = wordless.second
                        }
                    }

                    // Left Panel gets leftSlots (overflowing active slots)
                    for (i in leftSlots.indices) {
                        val slot = leftSlots[i]
                        val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = false)
                        newContentMap[proxy] = listOf(slot.item)
                    }

                    // In edit mode: QS Divider and available tiles pool
                    if (ControlCenterConfigHolder.sIsEditMode) {
                        val qsFooter = sAdapterQsFooters[adapter]
                        if (qsFooter != null) {
                            newContentMap[qsFooter.first] = qsFooter.second
                        }
                    }

                    // Left Panel other footers (edit button & footer space)
                    val footers = sAdapterFooters[adapter]
                    if (footers != null) {
                        for ((ctrl, fItems) in footers) {
                            newContentMap[ctrl] = fItems
                        }
                    }
                }
            }

            // Apply new ContentMap to adapter
            contentMapField.set(adapter, newContentMap)
            True2DGridEngine.invalidate(adapter)

        } catch (t: Throwable) {
            Log.e(TAG, "buildAndSetContentMap error", t)
        }
    }

    /**
     * Handles live item move during drag & drop.
     */
    fun onItemMove(adapter: Any, fromPos: Int, toPos: Int, sourceVh: Any?, targetVh: Any?, classLoader: ClassLoader): Boolean {
        try {
            if (fromPos < 0 || toPos < 0 || fromPos == toPos) return false

            val fromId = getSlotIdFromViewHolder(sourceVh)
            val toId = getSlotIdFromViewHolder(targetVh)

            if (fromId == null || toId == null || fromId == toId) return false
            val fromIdx = sUnifiedSlotOrder.indexOf(fromId)
            val toIdx = sUnifiedSlotOrder.indexOf(toId)
            if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return false

            val moved = sUnifiedSlotOrder.removeAt(fromIdx)
            sUnifiedSlotOrder.add(toIdx, moved)

            // Rebuild contentMap immediately
            buildAndSetContentMap(adapter, classLoader)
            if (sDistributor != null) {
                val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                if (otherAdapter != null) {
                    buildAndSetContentMap(otherAdapter, classLoader)
                }
            }

            // Invalidate 2D layout cache so that recalculation uses the new contentMap
            True2DGridEngine.invalidate(adapter)
            if (sDistributor != null) {
                val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                if (otherAdapter != null) {
                    True2DGridEngine.invalidate(otherAdapter)
                }
            }

            // Reset translation on all visible non-dragged ViewHolders to eliminate stuck translation offsets
            val rv = ControlCenterConfigHolder.sRecyclerView?.get()
            if (rv != null) {
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    val vh = rv.getChildViewHolder(child)
                    if (vh != null && vh !== sourceVh) {
                        try {
                            val setHolderTransXM = vh.javaClass.getMethod("setHolderTransX", Float::class.javaPrimitiveType)
                            val setHolderTransYM = vh.javaClass.getMethod("setHolderTransY", Float::class.javaPrimitiveType)
                            setHolderTransXM.invoke(vh, 0f)
                            setHolderTransYM.invoke(vh, 0f)
                            child.translationX = 0f
                            child.translationY = 0f
                        } catch (t: Throwable) {}
                    }
                }
            }

            // Notify adapter of visual move
            val notifyItemMovedMethod = adapter.javaClass.getMethod("notifyItemMoved", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            notifyItemMovedMethod.invoke(adapter, fromPos, toPos)

            // Sync Wi-Fi first flag if cards were moved
            val wifiIdx = sUnifiedSlotOrder.indexOf("card:wifi")
            val cellIdx = sUnifiedSlotOrder.indexOf("card:cell")
            if (wifiIdx >= 0 && cellIdx >= 0) {
                ControlCenterConfigHolder.sIsWifiFirst = (wifiIdx < cellIdx)
            }

            // Sync QSListController addedTiles in memory without calling moveElement (which triggers DiffUtil / distributor.notifyChanged)
            try {
                if (sourceVh != null && targetVh != null) {
                    val getOwnerMethod = try { sourceVh.javaClass.getMethod("getOwner") } catch (e: Throwable) { null }
                    val srcOwner = getOwnerMethod?.invoke(sourceVh)
                    val tgtOwner = getOwnerMethod?.invoke(targetVh)
                    val srcTarget = if (srcOwner != null && Proxy.isProxyClass(srcOwner.javaClass)) {
                        (Proxy.getInvocationHandler(srcOwner) as? ProxyContentHandler)?.target
                    } else srcOwner
                    val tgtTarget = if (tgtOwner != null && Proxy.isProxyClass(tgtOwner.javaClass)) {
                        (Proxy.getInvocationHandler(tgtOwner) as? ProxyContentHandler)?.target
                    } else tgtOwner

                    if (srcTarget != null && tgtTarget != null && srcTarget.javaClass.name.contains("QSListController") && srcTarget == tgtTarget) {
                        val getItemMethod = sourceVh.javaClass.getMethod("getItem\$miui_controlcenter_release")
                        val sourceItem = getItemMethod.invoke(sourceVh)
                        val getTargetItemMethod = targetVh.javaClass.getMethod("getItem\$miui_controlcenter_release")
                        val targetItem = getTargetItemMethod.invoke(targetVh)
                        if (sourceItem != null && targetItem != null) {
                            val addedField = srcTarget.javaClass.getDeclaredField("addedTiles")
                            addedField.isAccessible = true
                            @Suppress("UNCHECKED_CAST")
                            val added = addedField.get(srcTarget) as? MutableList<Any>
                            if (added != null) {
                                val sIdx = added.indexOf(sourceItem)
                                val tIdx = added.indexOf(targetItem)
                                if (sIdx >= 0 && tIdx >= 0 && sIdx != tIdx) {
                                    val item = added.removeAt(sIdx)
                                    added.add(tIdx, item)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "onItemMove error", t)
            return false
        }
    }

    fun onExitEditMode() {
        sAdapterQsFooters.clear()
        sAdapterWordless.clear()
        cleanStaleSlots()
        val adapter = ControlCenterConfigHolder.sMainPanelAdapter
        if (adapter != null) {
            val cl = adapter.javaClass.classLoader
            if (cl != null) {
                try {
                    buildAndSetContentMap(adapter, cl)
                    val notifyDataSetChangedM = adapter.javaClass.getMethod("notifyDataSetChanged")
                    notifyDataSetChangedM.invoke(adapter)
                } catch (t: Throwable) {}
            }
        }
        if (sDistributor != null) {
            val left = sLeftAdapter
            val right = sRightAdapter
            if (left != null) {
                val cl = left.javaClass.classLoader
                if (cl != null) {
                    try {
                        buildAndSetContentMap(left, cl)
                        left.javaClass.getMethod("notifyDataSetChanged").invoke(left)
                    } catch (t: Throwable) {}
                }
            }
            if (right != null) {
                val cl = right.javaClass.classLoader
                if (cl != null) {
                    try {
                        buildAndSetContentMap(right, cl)
                        right.javaClass.getMethod("notifyDataSetChanged").invoke(right)
                    } catch (t: Throwable) {}
                }
            }
        }
    }

    /**
     * Initializes all hooks for 100% Full Unlock.
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        try {
            Log.i(TAG, "Initializing ControlCenterFullUnlock hooks...")

            // 1. Hook MainPanelAdapter.distributeContent
            try {
                val adapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader)
                for (m in adapterClass.declaredMethods) {
                    if (m.name == "distributeContent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val adapter = chain.thisObject
                                True2DGridEngine.invalidate(adapter)
                                reorganizeContentMap(adapter, classLoader)
                            } catch (t: Throwable) {
                                Log.e(TAG, "distributeContent post-intercept error", t)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook distributeContent", t)
            }

            // 2. Hook MainPanelContentDistributor.distributePanels
            try {
                val distributorClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", false, classLoader)
                for (m in distributorClass.declaredMethods) {
                    if (m.name == "distributePanels") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val distributor = chain.thisObject
                            sDistributor = distributor
                            try {
                                val leftAdapterMethod = distributorClass.getMethod("getLeftAdapter")
                                sLeftAdapter = leftAdapterMethod.invoke(distributor)
                                val rightAdapterMethod = distributorClass.getMethod("getRightAdapter")
                                sRightAdapter = rightAdapterMethod.invoke(distributor)
                            } catch (t: Throwable) {}

                            val inLandscape = (chain.args.getOrNull(0) as? Boolean) ?: false

                            if (inLandscape && ControlCenterConfigHolder.config.modularEditEnabled) {
                                // In landscape, ensure both left and right panel contents receive all available controllers
                                try {
                                    val rightPanelField = distributorClass.getDeclaredField("rightPanelContent")
                                    rightPanelField.isAccessible = true
                                    val rightContent = rightPanelField.get(distributor) as? MutableList<Any>

                                    val leftPanelField = distributorClass.getDeclaredField("leftPanelContent")
                                    leftPanelField.isAccessible = true
                                    val leftContent = leftPanelField.get(distributor) as? MutableList<Any>

                                    val getChildControllersMethod = distributorClass.getMethod("getChildControllers")
                                    val allChildren = getChildControllersMethod.invoke(distributor) as? List<*>

                                    if (rightContent != null && leftContent != null && allChildren != null) {
                                        rightContent.clear()
                                        leftContent.clear()

                                        for (child in allChildren) {
                                            if (child == null) continue
                                            val cName = child.javaClass.name

                                            // 1. NEVER include compact QS cards (they create duplicate circular wifi/cell)
                                            if (cName.contains("CompactQS") || cName.contains("CompactQSCard")) continue

                                            // 2. Check if the controller is available for landscape
                                            val isAvail = try {
                                                val availMethod = child.javaClass.getMethod("available", Boolean::class.javaPrimitiveType)
                                                availMethod.invoke(child, true) as Boolean
                                            } catch (t: Throwable) { true }

                                            if (!isAvail) continue

                                            // 3. For HeaderSpace and FooterSpace, distribute to respective panel based on getRightOrLeft()
                                            if (cName.contains("HeaderSpaceController") || cName.contains("FooterSpaceController")) {
                                                val isRight = try {
                                                    val getRightMethod = child.javaClass.getMethod("getRightOrLeft")
                                                    getRightMethod.invoke(child) as Boolean
                                                } catch (t: Throwable) { true }

                                                if (isRight) {
                                                    rightContent.add(child)
                                                } else {
                                                    leftContent.add(child)
                                                }
                                                continue
                                            }

                                            // 4. For content controllers, add to both panels
                                            rightContent.add(child)
                                            leftContent.add(child)
                                        }
                                        return@intercept null
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "distributePanels landscape override error", t)
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook distributePanels", t)
            }

            // 3. Hook ItemTouchHelper.Callback methods
            try {
                val helperClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$itemTouchHelper\$2", false, classLoader)
                for (m in helperClass.declaredMethods) {
                    if (m.name == "getMovementFlags") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                if (ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val vh = chain.args.getOrNull(1)
                                    if (vh != null) {
                                        val getItemViewTypeMethod = vh.javaClass.getMethod("getItemViewType")
                                        val vt = getItemViewTypeMethod.invoke(vh) as Int
                                        if (isNonDraggableSystemItem(vt)) {
                                            return@intercept 0
                                        }
                                        // Check if it's an inactive tile in the available pool
                                        val getItemMethod = try { vh.javaClass.getMethod("getItem\$miui_controlcenter_release") } catch (e: Throwable) { null }
                                        val item = getItemMethod?.invoke(vh)
                                        if (item != null) {
                                            val addedField = try { item.javaClass.getDeclaredField("added") } catch (e: Throwable) { null }
                                            if (addedField != null) {
                                                addedField.isAccessible = true
                                                val isAdded = addedField.getBoolean(item)
                                                if (!isAdded) {
                                                    return@intercept chain.proceed()
                                                }
                                            }
                                        }
                                        // Return DRAG_FLAG: 15 shl 16 (UP | DOWN | LEFT | RIGHT)
                                        return@intercept (15 shl 16)
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "canDropOver") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                if (ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val targetVh = chain.args.getOrNull(2)
                                    if (targetVh != null) {
                                        val getItemViewTypeMethod = targetVh.javaClass.getMethod("getItemViewType")
                                        val vt = getItemViewTypeMethod.invoke(targetVh) as Int
                                        if (isNonDraggableSystemItem(vt)) {
                                            return@intercept false
                                        }
                                        val getItemMethod = try { targetVh.javaClass.getMethod("getItem\$miui_controlcenter_release") } catch (e: Throwable) { null }
                                        val targetItem = getItemMethod?.invoke(targetVh)
                                        if (targetItem != null) {
                                            val addedField = try { targetItem.javaClass.getDeclaredField("added") } catch (e: Throwable) { null }
                                            if (addedField != null) {
                                                addedField.isAccessible = true
                                                val isAdded = addedField.getBoolean(targetItem)
                                                if (!isAdded) return@intercept false
                                            }
                                        }
                                        return@intercept true
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "onMove") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                if (ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val helperCallback = chain.thisObject
                                    val this0Field = helperClass.getDeclaredField("this$0")
                                    this0Field.isAccessible = true
                                    val adapter = this0Field.get(helperCallback)

                                    val sourceVh = chain.args.getOrNull(1)
                                    val targetVh = chain.args.getOrNull(2)

                                    if (adapter != null && sourceVh != null && targetVh != null) {
                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastMoveTimestamp < 120L) {
                                            return@intercept false
                                        }

                                        val getPosMethod = try {
                                            sourceVh.javaClass.getMethod("getBindingAdapterPosition")
                                        } catch (e: Throwable) {
                                            sourceVh.javaClass.getMethod("getAdapterPosition")
                                        }
                                        val fromPos = getPosMethod.invoke(sourceVh) as Int
                                        val toPos = getPosMethod.invoke(targetVh) as Int

                                        if (fromPos >= 0 && toPos >= 0 && fromPos != toPos) {
                                            val success = onItemMove(adapter, fromPos, toPos, sourceVh, targetVh, classLoader)
                                            if (success) {
                                                lastMoveTimestamp = now
                                                return@intercept true
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "onMove full unlock error", t)
                            }
                            chain.proceed()
                        }
                    }

                    if (m.name == "clearView") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val helperCallback = chain.thisObject
                                val this0Field = helperClass.getDeclaredField("this$0")
                                this0Field.isAccessible = true
                                val adapter = this0Field.get(helperCallback)
                                if (adapter != null) {
                                    val ctxField = adapter.javaClass.getDeclaredField("context")
                                    ctxField.isAccessible = true
                                    val ctx = ctxField.get(adapter) as? Context
                                    saveOrder(ctx)
                                    True2DGridEngine.invalidate(adapter)
                                }
                                val draggedVh = chain.args.getOrNull(1)
                                if (draggedVh != null) {
                                    try {
                                        val vhClass = draggedVh.javaClass
                                        val setHolderTransXM = vhClass.getMethod("setHolderTransX", Float::class.javaPrimitiveType)
                                        val setHolderTransYM = vhClass.getMethod("setHolderTransY", Float::class.javaPrimitiveType)
                                        setHolderTransXM.invoke(draggedVh, 0f)
                                        setHolderTransYM.invoke(draggedVh, 0f)
                                        val itemViewField = vhClass.getField("itemView")
                                        val itemView = itemViewField.get(draggedVh) as? View
                                        itemView?.translationX = 0f
                                        itemView?.translationY = 0f
                                    } catch (t: Throwable) {}
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook itemTouchHelper", t)
            }

            // 4. Hook ControlCenterItemAnimator.animateMove & ControlCenterViewHolder.prepareMove
            try {
                val animatorClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterItemAnimator", false, classLoader)
                val vhClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.ControlCenterViewHolder", false, classLoader)
                for (m in animatorClass.declaredMethods) {
                    if (m.name == "animateMove") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.sIsEditMode) {
                                val holder = chain.args.getOrNull(0)
                                if (holder != null) {
                                    try {
                                        val setHolderTransXM = vhClass.getMethod("setHolderTransX", Float::class.javaPrimitiveType)
                                        val setHolderTransYM = vhClass.getMethod("setHolderTransY", Float::class.javaPrimitiveType)
                                        setHolderTransXM.invoke(holder, 0f)
                                        setHolderTransYM.invoke(holder, 0f)
                                        val itemViewField = vhClass.getField("itemView")
                                        val itemView = itemViewField.get(holder) as? View
                                        itemView?.translationX = 0f
                                        itemView?.translationY = 0f
                                    } catch (t: Throwable) {}
                                    try {
                                        val dispatchMoveFinishedM = animatorClass.getMethod("dispatchMoveFinished", Class.forName("androidx.recyclerview.widget.RecyclerView\$ViewHolder", false, classLoader))
                                        dispatchMoveFinishedM.invoke(chain.thisObject, holder)
                                    } catch (t: Throwable) {}
                                    return@intercept false
                                }
                            }
                            chain.proceed()
                        }
                    }
                }

                for (m in vhClass.declaredMethods) {
                    if (m.name == "prepareMove") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.sIsEditMode) {
                                val holder = chain.thisObject
                                try {
                                    val setHolderTransXM = vhClass.getMethod("setHolderTransX", Float::class.javaPrimitiveType)
                                    val setHolderTransYM = vhClass.getMethod("setHolderTransY", Float::class.javaPrimitiveType)
                                    setHolderTransXM.invoke(holder, 0f)
                                    setHolderTransYM.invoke(holder, 0f)
                                    val itemViewField = vhClass.getField("itemView")
                                    val itemView = itemViewField.get(holder) as? View
                                    itemView?.translationX = 0f
                                    itemView?.translationY = 0f
                                } catch (t: Throwable) {}
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook ControlCenterItemAnimator / ControlCenterViewHolder", t)
            }

            Log.i(TAG, "ControlCenterFullUnlock hooks initialized successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ControlCenterFullUnlock", t)
        }
    }
}
