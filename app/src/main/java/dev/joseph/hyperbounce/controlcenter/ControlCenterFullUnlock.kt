package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
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
    const val VIEW_TYPE_LANDSCAPE_DIVIDER = 8399

    // Unified slot order persisted across sessions
    val sUnifiedSlotOrder = CopyOnWriteArrayList<String>()
    @Volatile var sOrderLoaded = false
    @Volatile var sLastDemotedSlotId: String? = null
    @Volatile var sLastMoveTime: Long = 0L
    @Volatile var sLastMovedPair: String = ""

    // Multi-row drag stabilization and out-of-bounds scroll control
    @Volatile var sCurrentDraggedVh: WeakReference<Any>? = null
    @Volatile var sCurrentDraggedSlotId: String? = null
    @Volatile var sDragHoverTargetId: String? = null
    @Volatile var sDragHoverStartTime: Long = 0L

    // Cache of proxies keyed by slot ID
    private val sProxyCache = ConcurrentHashMap<String, Any>()
    private val sProxyHandlers = ConcurrentHashMap<String, ProxyContentHandler>()

    // Global reference to distributor and adapters
    @Volatile var sDistributor: Any? = null
    @Volatile var sLeftAdapter: Any? = null
    @Volatile var sRightAdapter: Any? = null

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
     * iOS 26 frosted glass visual divider indicating the boundary between
     * Landscape Right Panel (above) and Landscape Left Panel (below).
     */
    class LandscapeSplitDividerView(context: Context) : FrameLayout(context) {
        init {
            val density = context.resources.displayMetrics.density
            val h = (44 * density).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h)
            minimumHeight = h

            val hPadding = (16 * density).toInt()
            val vPadding = (6 * density).toInt()
            setPadding(hPadding, vPadding, hPadding, vPadding)

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }

            // Left frosted line
            val leftLine = View(context).apply {
                val lp = LinearLayout.LayoutParams(0, (1.2f * density).toInt().coerceAtLeast(1), 1.0f)
                lp.gravity = Gravity.CENTER_VERTICAL
                layoutParams = lp
                setBackgroundColor(0x30FFFFFF.toInt())
            }
            container.addView(leftLine)

            // Frosted pill capsule badge
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val marginH = (10 * density).toInt()
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(marginH, 0, marginH, 0)
                }
                layoutParams = lp
                val padH = (14 * density).toInt()
                val padV = (4.5f * density).toInt()
                setPadding(padH, padV, padH, padV)

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14 * density
                    setColor(0x33000000.toInt())
                    setStroke((1 * density).toInt().coerceAtLeast(1), 0x38FFFFFF.toInt())
                }
            }

            val badgeText = TextView(context).apply {
                text = "LANDSCAPE SPLIT"
                textSize = 10.5f
                setTextColor(0xCCFFFFFF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            pill.addView(badgeText)
            container.addView(pill)

            // Right frosted line
            val rightLine = View(context).apply {
                val lp = LinearLayout.LayoutParams(0, (1.2f * density).toInt().coerceAtLeast(1), 1.0f)
                lp.gravity = Gravity.CENTER_VERTICAL
                layoutParams = lp
                setBackgroundColor(0x30FFFFFF.toInt())
            }
            container.addView(rightLine)

            addView(container)
        }
    }

    /**
     * Proxy InvocationHandler for the Landscape Split Divider implementing
     * both MainPanelContent and MainPanelListItem.
     */
    class LandscapeDividerInvocationHandler(
        @Volatile var priority: Int,
        private val classLoader: ClassLoader
    ) : InvocationHandler {
        private var holderRef: Any? = null

        private fun makeHolderVisible(holder: Any?) {
            if (holder == null) return
            try {
                val itemViewField = holder.javaClass.getField("itemView")
                val v = itemViewField.get(holder) as? View
                if (v != null) {
                    v.visibility = View.VISIBLE
                    v.alpha = 1.0f
                    v.translationX = 0f
                    v.translationY = 0f
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            } catch (t: Throwable) {}
            try {
                val setIgnoreAlpha = holder.javaClass.getMethod("setIgnoreHolderAlpha", Boolean::class.javaPrimitiveType)
                setIgnoreAlpha.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreTrans = holder.javaClass.getMethod("setIgnoreHolderTranslation", Boolean::class.javaPrimitiveType)
                setIgnoreTrans.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreScale = holder.javaClass.getMethod("setIgnoreHolderScale", Boolean::class.javaPrimitiveType)
                setIgnoreScale.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setDraggable = holder.javaClass.getMethod("setDraggable", Boolean::class.javaPrimitiveType)
                setDraggable.invoke(holder, false)
            } catch (t: Throwable) {}
        }

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "getType" -> return VIEW_TYPE_LANDSCAPE_DIVIDER
                "getSpanSize" -> return 4
                "getPriority" -> return priority
                "getRightOrLeft" -> return true
                "available" -> {
                    val isLandscape = (args?.getOrNull(0) as? Boolean) ?: false
                    return !isLandscape && ControlCenterConfigHolder.sIsEditMode
                }
                "getListItems" -> return listOf(proxy)
                "getHolder" -> return holderRef
                "setHolder" -> {
                    holderRef = args?.getOrNull(0)
                    makeHolderVisible(holderRef)
                    return null
                }
                "onBindViewHolder" -> {
                    val holder = holderRef ?: args?.getOrNull(0)
                    makeHolderVisible(holder)
                    return null
                }
                "updateMode" -> {
                    val holder = holderRef ?: args?.getOrNull(0)
                    makeHolderVisible(holder)
                    return null
                }
                "updateConfiguration" -> {
                    val holder = holderRef ?: args?.getOrNull(0)
                    makeHolderVisible(holder)
                    return null
                }
                "createViewHolder" -> {
                    val parent = args?.getOrNull(0) as? ViewGroup
                    val vt = (args?.getOrNull(1) as? Int) ?: -1
                    if (vt == VIEW_TYPE_LANDSCAPE_DIVIDER && parent != null) {
                        return createLandscapeDividerViewHolder(parent, classLoader)
                    }
                    return null
                }
                "moveElement" -> return false
                "equals" -> return proxy === args?.getOrNull(0)
                "hashCode" -> return System.identityHashCode(proxy)
                "toString" -> return "LandscapeSplitDividerController"
            }
            return null
        }
    }

    private var sLandscapeDividerProxy: Any? = null

    fun getOrCreateLandscapeDivider(classLoader: ClassLoader, priority: Int): Any {
        val existing = sLandscapeDividerProxy
        if (existing != null) {
            val handler = Proxy.getInvocationHandler(existing) as? LandscapeDividerInvocationHandler
            if (handler != null) {
                handler.priority = priority
            }
            return existing
        }

        val contentInterface = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContent", false, classLoader)
        val listItemInterface = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem", false, classLoader)
        val handler = LandscapeDividerInvocationHandler(priority, classLoader)
        val proxy = Proxy.newProxyInstance(classLoader, arrayOf(contentInterface, listItemInterface), handler)
        sLandscapeDividerProxy = proxy
        return proxy
    }

    fun createLandscapeDividerViewHolder(parent: ViewGroup, classLoader: ClassLoader): Any? {
        val ctx = parent.context
        val dividerView = LandscapeSplitDividerView(ctx)
        val density = ctx.resources.displayMetrics.density
        val dividerHeight = (44 * density).toInt()

        // 1. Primary: EditButtonViewHolder (clean container, no forced navigation bar insets)
        try {
            val btnBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCustomizeButtonBinding", false, classLoader)
            val inflateMethod = btnBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = btnBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup
            root.removeAllViews()
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight)
            root.minimumHeight = dividerHeight

            root.addView(dividerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight))

            val holderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.EditButtonController\$EditButtonViewHolder", false, classLoader)
            val ctor = holderClass.getConstructor(btnBindingClass)
            val holder = ctor.newInstance(binding)

            try {
                val setIgnoreAlpha = holder.javaClass.getMethod("setIgnoreHolderAlpha", Boolean::class.javaPrimitiveType)
                setIgnoreAlpha.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreTrans = holder.javaClass.getMethod("setIgnoreHolderTranslation", Boolean::class.javaPrimitiveType)
                setIgnoreTrans.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreScale = holder.javaClass.getMethod("setIgnoreHolderScale", Boolean::class.javaPrimitiveType)
                setIgnoreScale.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setDraggable = holder.javaClass.getMethod("setDraggable", Boolean::class.javaPrimitiveType)
                setDraggable.invoke(holder, false)
            } catch (t: Throwable) {}

            return holder
        } catch (t: Throwable) {
            Log.w(TAG, "EditButtonViewHolder reflection failed for divider, trying WordlessModeViewHolder", t)
        }

        // 2. Fallback: WordlessModeViewHolder
        try {
            val wordlessBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsEditWordlessModeLayoutBinding", false, classLoader)
            val inflateMethod = wordlessBindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
            val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false)

            val getRootMethod = wordlessBindingClass.getMethod("getRoot")
            val root = getRootMethod.invoke(binding) as ViewGroup
            root.removeAllViews()
            root.layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight)
            root.minimumHeight = dividerHeight

            root.addView(dividerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight))

            val holderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.WordlessModeController\$WordlessModeViewHolder", false, classLoader)
            val ctor = holderClass.getConstructor(wordlessBindingClass)
            val holder = ctor.newInstance(binding)

            try {
                val setIgnoreAlpha = holder.javaClass.getMethod("setIgnoreHolderAlpha", Boolean::class.javaPrimitiveType)
                setIgnoreAlpha.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreTrans = holder.javaClass.getMethod("setIgnoreHolderTranslation", Boolean::class.javaPrimitiveType)
                setIgnoreTrans.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setIgnoreScale = holder.javaClass.getMethod("setIgnoreHolderScale", Boolean::class.javaPrimitiveType)
                setIgnoreScale.invoke(holder, true)
            } catch (t: Throwable) {}
            try {
                val setDraggable = holder.javaClass.getMethod("setDraggable", Boolean::class.javaPrimitiveType)
                setDraggable.invoke(holder, false)
            } catch (t: Throwable) {}

            return holder
        } catch (t: Throwable) {
            Log.e(TAG, "createLandscapeDividerViewHolder failed", t)
            return null
        }
    }

    private var sLastToastTime: Long = 0L

    fun showCapacityToast(context: Context?, maxRows: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - sLastToastTime < 2000L) return
        sLastToastTime = now
        val targetCtx = context ?: ControlCenterConfigHolder.sLastKnownContext ?: return
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(
                    targetCtx,
                    "Arrangement not possible (maximum $maxRows rows on landscape right panel)",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (t: Throwable) {}
        }
    }

    fun getDividerPositionInAdapter(adapter: Any?): Int {
        if (adapter == null) return -1
        try {
            val contentMapField = adapter.javaClass.getDeclaredField("contentMap")
            contentMapField.isAccessible = true
            val map = contentMapField.get(adapter) as? Map<*, *> ?: return -1
            var idx = 0
            for (entry in map.values) {
                if (entry is List<*>) {
                    for (item in entry) {
                        if (item === sLandscapeDividerProxy || item?.toString()?.contains("LandscapeSplitDivider") == true) {
                            return idx
                        }
                        val vt = try {
                            val getTypeMethod = item?.javaClass?.getMethod("getType")
                            getTypeMethod?.invoke(item) as? Int ?: -1
                        } catch (t: Throwable) { -1 }
                        if (vt == VIEW_TYPE_LANDSCAPE_DIVIDER) {
                            return idx
                        }
                        idx++
                    }
                }
            }
        } catch (t: Throwable) {}
        return -1
    }

    fun ensureTileFitsLandscapeAfterResize(spec: String?, ctx: Context?) {
        if (spec == null) return
        try {
            val maxRows = ControlCenterConfigHolder.landscapeRightMaxRows

            val slotId = when {
                spec.startsWith("group:") -> spec
                spec.equals("media", ignoreCase = true) -> "component:media"
                spec.equals("brightness", ignoreCase = true) -> "component:brightness"
                spec.equals("volume", ignoreCase = true) -> "component:volume"
                spec == "wifi" || spec == "cell" -> "card:$spec"
                else -> {
                    val g = ControlCenterConfigHolder.findGroupForSpec(spec)
                    if (g != null) "group:${g.id}" else "tile:$spec"
                }
            }

            val currentSlotIdx = sUnifiedSlotOrder.indexOf(slotId)
            if (currentSlotIdx >= 0) {
                // Check if this slot was above the divider BEFORE this resize
                val orderedSlotsBefore = sUnifiedSlotOrder.mapNotNull { sKnownSlots[it] }
                val (prevRightSlots, _) = calculateLandscapeRowSplit(orderedSlotsBefore, maxRightRows = maxRows)
                val wasAboveDivider = prevRightSlots.any { it.id == slotId } || (currentSlotIdx < prevRightSlots.size)

                // Refresh spans for this slot
                val slot = sKnownSlots[slotId]
                if (slot != null) {
                    val currentSpans = getItemSpans(slot.item, slot.viewType, if (slotId.startsWith("group:")) slotId else spec)
                    slot.spanW = currentSpans.first
                    slot.spanH = currentSpans.second
                } else if (slotId.startsWith("group:")) {
                    val gId = slotId.substring(6)
                    val group = ControlCenterConfigHolder.sTileGroups.find { it.id == gId }
                    if (group != null) {
                        val spans = if (group.specs.size == 2) {
                            if (group.isVertical) Pair(1, 2) else Pair(2, 1)
                        } else {
                            Pair(2, group.getEffectiveRowCount().coerceIn(1, 6))
                        }
                        val anchorSpec = group.specs.firstOrNull()
                        val anchorSlot = if (anchorSpec != null) sKnownSlots["tile:$anchorSpec"] ?: sKnownSlots["card:$anchorSpec"] else null
                        if (anchorSlot != null) {
                            sKnownSlots[slotId] = SlotItem(slotId, anchorSlot.owner, anchorSlot.item, anchorSlot.viewType, spans.first, spans.second)
                        }
                    }
                }

                val updatedSlot = sKnownSlots[slotId]
                val canFitAbove = if (updatedSlot != null && updatedSlot.spanH > maxRows) {
                    false
                } else {
                    val orderedSlotsAfter = sUnifiedSlotOrder.mapNotNull { sKnownSlots[it] }
                    val (newRightSlots, _) = calculateLandscapeRowSplit(orderedSlotsAfter, maxRightRows = maxRows)
                    newRightSlots.any { it.id == slotId }
                }

                if (wasAboveDivider && !canFitAbove) {
                    val remainingOrder = ArrayList(sUnifiedSlotOrder)
                    remainingOrder.remove(slotId)

                    val remainingSlots = remainingOrder.mapNotNull { sKnownSlots[it] }
                    val (remainingRightSlots, _) = calculateLandscapeRowSplit(remainingSlots, maxRightRows = maxRows)
                    val newSplitIndex = remainingRightSlots.size

                    // Insert slotId at newSplitIndex (first item below the divider)
                    remainingOrder.add(newSplitIndex.coerceIn(0, remainingOrder.size), slotId)

                    sUnifiedSlotOrder.clear()
                    sUnifiedSlotOrder.addAll(remainingOrder)
                    Log.i(TAG, "Tile $spec resized and shifted below landscape divider to index $newSplitIndex")
                }
            } else {
                val slot = sKnownSlots[slotId]
                if (slot != null) {
                    val currentSpans = getItemSpans(slot.item, slot.viewType, if (slotId.startsWith("group:")) slotId else spec)
                    slot.spanW = currentSpans.first
                    slot.spanH = currentSpans.second
                }
            }

            val targetCtx = ctx ?: ControlCenterConfigHolder.sLastKnownContext
            if (targetCtx != null) {
                saveOrder(targetCtx)
            }

            // Immediately rebuild contentMap and invalidate grid engine on active adapters!
            val adapter = ControlCenterConfigHolder.sMainPanelAdapter ?: sRightAdapter ?: sLeftAdapter
            if (adapter != null) {
                val cl = adapter.javaClass.classLoader
                if (cl != null) {
                    buildAndSetContentMap(adapter, cl)
                    if (sDistributor != null) {
                        val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                        if (otherAdapter != null) {
                            buildAndSetContentMap(otherAdapter, cl)
                        }
                    }
                }
            }
            True2DGridEngine.invalidateAll()
        } catch (t: Throwable) {
            Log.e(TAG, "ensureTileFitsLandscapeAfterResize error", t)
        }
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
            val anchorSpec = specs.firstOrNull()
            val anchorSlot = if (anchorSpec != null) sKnownSlots["tile:$anchorSpec"] ?: sKnownSlots["card:$anchorSpec"] else null

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

            if (anchorSlot != null) {
                val group = ControlCenterConfigHolder.sTileGroups.find { it.id == groupId }
                val spans = if (group != null) {
                    if (group.specs.size == 2) {
                        if (group.isVertical) Pair(1, 2) else Pair(2, 1)
                    } else {
                        Pair(2, group.getEffectiveRowCount().coerceIn(1, 6))
                    }
                } else Pair(2, 2)
                sKnownSlots[groupSlotId] = SlotItem(groupSlotId, anchorSlot.owner, anchorSlot.item, anchorSlot.viewType, spans.first, spans.second)
            }

            cleanStaleSlots()
            saveOrder(context)
            ensureTileFitsLandscapeAfterResize(groupSlotId, context)
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
            val adapter = ControlCenterConfigHolder.sMainPanelAdapter ?: sRightAdapter ?: sLeftAdapter
            if (adapter != null) {
                val cl = adapter.javaClass.classLoader
                if (cl != null) {
                    buildAndSetContentMap(adapter, cl)
                    if (sDistributor != null) {
                        val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                        if (otherAdapter != null) {
                            buildAndSetContentMap(otherAdapter, cl)
                        }
                    }
                }
            }
            True2DGridEngine.invalidateAll()
        } catch (t: Throwable) {
            Log.e(TAG, "onGroupsDisassembled error", t)
        }
    }

    /**
     * Resets unified order state and restores the exact stock layout sequence live.
     */
    fun resetOrder(context: Context?) {
        try {
            val stockList = mutableListOf(
                "card:wifi",
                "card:cell",
                "component:media",
                "component:brightness",
                "component:volume"
            )
            val seen = stockList.toMutableSet()
            for (id in sUnifiedSlotOrder) {
                if (seen.add(id)) {
                    stockList.add(id)
                }
            }
            for (id in sKnownSlots.keys) {
                if (seen.add(id)) {
                    stockList.add(id)
                }
            }

            sUnifiedSlotOrder.clear()
            sUnifiedSlotOrder.addAll(stockList)
            cleanStaleSlots()

            // Reset spans in sKnownSlots for stock items
            sKnownSlots["card:wifi"]?.apply { spanW = 2; spanH = 1 }
            sKnownSlots["card:cell"]?.apply { spanW = 2; spanH = 1 }
            sKnownSlots["component:media"]?.apply { spanW = 2; spanH = 2 }
            sKnownSlots["component:brightness"]?.apply { spanW = 1; spanH = 2 }
            sKnownSlots["component:volume"]?.apply { spanW = 1; spanH = 2 }

            val targetCtx = context ?: ControlCenterConfigHolder.sLastKnownContext
            if (targetCtx != null) {
                try {
                    val prefs = targetCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val joined = sUnifiedSlotOrder.joinToString(",")
                    prefs.edit().putString(PREF_UNIFIED_ORDER, joined).apply()
                } catch (t: Throwable) {}
                try {
                    val joined = sUnifiedSlotOrder.joinToString(",")
                    Settings.Secure.putString(targetCtx.contentResolver, SECURE_KEY_UNIFIED_ORDER, joined)
                } catch (t: Throwable) {}
            }

            // Immediately rebuild live adapters on main UI thread so UI updates without restart
            Handler(Looper.getMainLooper()).post {
                try {
                    val adapter = ControlCenterConfigHolder.sMainPanelAdapter
                    if (adapter != null) {
                        val cl = adapter.javaClass.classLoader
                        if (cl != null) {
                            buildAndSetContentMap(adapter, cl)
                            try {
                                adapter.javaClass.getMethod("notifyDataSetChanged").invoke(adapter)
                            } catch (t: Throwable) {}
                        }
                    }
                    val leftAdp = sLeftAdapter
                    val rightAdp = sRightAdapter
                    if (leftAdp != null && leftAdp !== adapter) {
                        val cl = leftAdp.javaClass.classLoader
                        if (cl != null) {
                            buildAndSetContentMap(leftAdp, cl)
                            try { leftAdp.javaClass.getMethod("notifyDataSetChanged").invoke(leftAdp) } catch (t: Throwable) {}
                        }
                    }
                    if (rightAdp != null && rightAdp !== adapter) {
                        val cl = rightAdp.javaClass.classLoader
                        if (cl != null) {
                            buildAndSetContentMap(rightAdp, cl)
                            try { rightAdp.javaClass.getMethod("notifyDataSetChanged").invoke(rightAdp) } catch (t: Throwable) {}
                        }
                    }
                    True2DGridEngine.invalidateAll()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error applying reset layout to adapter", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "resetOrder error", t)
        }
    }

    fun isNonDraggableItem(vt: Int): Boolean {
        return vt == VIEW_TYPE_LANDSCAPE_DIVIDER ||
               vt == 432337 || vt == 366837 || vt == 3348 || vt == 334833 ||
               vt == 8398 || vt == 8397 || vt == 3484337 || vt == 732873 ||
               True2DGridEngine.isFullWidthSystemItem(null, vt)
    }

    /**
     * Calculates width and height spans for any item.
     */
    fun getItemSpans(item: Any, viewType: Int, spec: String?): Pair<Int, Int> {
        // 0. Full-width system items & spacers/dividers -> 4x1
        if (viewType == VIEW_TYPE_LANDSCAPE_DIVIDER || viewType == 432337 || viewType == 366837 || viewType == 8398 || viewType == 8397 || viewType == 3484337 || viewType == 732873 ||
            True2DGridEngine.isFullWidthSystemItem(item, viewType)) {
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
        // 4. Cards (Wi-Fi, Cell) & Circular tiles & Groups
        if (spec != null) {
            if (spec.startsWith("group:")) {
                val gId = spec.substring(6)
                val group = ControlCenterConfigHolder.sTileGroups.find { it.id == gId }
                if (group != null) {
                    if (group.specs.size == 2) {
                        return if (group.isVertical) Pair(1, 2) else Pair(2, 1)
                    }
                    return Pair(2, group.getEffectiveRowCount().coerceIn(1, 6))
                }
            }
            val g = ControlCenterConfigHolder.findGroupForSpec(spec)
            if (g != null) {
                if (g.specs.size == 2) {
                    return if (g.isVertical) Pair(1, 2) else Pair(2, 1)
                }
                return Pair(2, g.getEffectiveRowCount().coerceIn(1, 6))
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
     * Calculates the split index in orderedSlots that demarcates the boundary
     * between the Landscape Right Panel (first N rows, maxRows) and the Landscape Left Panel.
     * Evaluates slots sequentially: slots that fit within the 4-column, maxRows 2D grid
     * are placed above the divider, and the first slot that exceeds the row limit marks the split boundary.
     */
    fun calculateSplitIndex(
        orderedSlots: List<SlotItem>,
        maxRows: Int = ControlCenterConfigHolder.landscapeRightMaxRows
    ): Int {
        val effectiveMaxRows = maxRows.coerceIn(1, 6)
        val grid = Array(effectiveMaxRows) { BooleanArray(4) { false } }

        fun canFit(startRow: Int, startCol: Int, w: Int, h: Int): Boolean {
            if (startCol + w > 4) return false
            if (startRow + h > effectiveMaxRows) return false
            for (r in startRow until (startRow + h)) {
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

        fun findSlot(w: Int, h: Int): Pair<Int, Int>? {
            for (r in 0..(effectiveMaxRows - h)) {
                for (c in 0..(4 - w)) {
                    if (canFit(r, c, w, h)) {
                        return Pair(r, c)
                    }
                }
            }
            return null
        }

        var splitIndex = 0
        for (slot in orderedSlots) {
            if (slot.spanH > effectiveMaxRows || slot.spanW > 4) break
            val fit = findSlot(slot.spanW, slot.spanH)
            if (fit != null) {
                markOccupied(fit.first, fit.second, slot.spanW, slot.spanH)
                splitIndex++
            } else {
                break
            }
        }
        return splitIndex
    }

    /**
     * Dynamic Landscape Row Split Simulation:
     * Divides activeSlots into (rightList, leftList) using calculateSplitIndex.
     */
    fun calculateLandscapeRowSplit(
        activeSlots: List<SlotItem>,
        maxRightRows: Int = ControlCenterConfigHolder.landscapeRightMaxRows,
        demotedSlotId: String? = null
    ): Pair<List<SlotItem>, List<SlotItem>> {
        val splitIndex = calculateSplitIndex(activeSlots, maxRightRows)
        val rightList = activeSlots.take(splitIndex)
        val leftList = activeSlots.drop(splitIndex)
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
                                        val spans = getItemSpans(item, vt, id)
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
                        var inActiveSection = true
                        var orderRemoved = false
                        val addedTilesList = try {
                            val f = realController.javaClass.getDeclaredField("addedTiles")
                            f.isAccessible = true
                            f.get(realController) as? List<*>
                        } catch (t: Throwable) { null }

                        for (item in items) {
                            val spec = ControlCenterConfigHolder.getRecordSpec(item)

                            var vt = 2272
                            try {
                                val getTypeM = item.javaClass.getMethod("getType")
                                vt = getTypeM.invoke(item) as Int
                            } catch (t: Throwable) {}

                            val isDividerOrHeader = (vt == 8398 || vt == 3484337 || vt == 8397)
                            if (isDividerOrHeader) {
                                inActiveSection = false
                            }

                            val isAdded = inActiveSection && (addedTilesList == null || addedTilesList.contains(item))

                            if (isAdded && !isDividerOrHeader) {
                                if (spec != null) {
                                    val group = ControlCenterConfigHolder.findGroupForSpec(spec)
                                    if (group != null) {
                                        if (ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                            val id = "group:${group.id}"
                                            val spans = getItemSpans(item, vt, id)
                                            sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                                        }
                                        sKnownSlots.remove("tile:$spec")
                                        sUnifiedSlotOrder.remove("tile:$spec")
                                        sKnownSlots.remove("card:$spec")
                                        sUnifiedSlotOrder.remove("card:$spec")
                                        continue
                                    }
                                }
                                val id = "tile:$spec"
                                val spans = getItemSpans(item, vt, spec)
                                sKnownSlots[id] = SlotItem(id, controller, item, vt, spans.first, spans.second)
                            } else {
                                if (spec != null) {
                                    sKnownSlots.remove("tile:$spec")
                                    if (sUnifiedSlotOrder.remove("tile:$spec")) {
                                        orderRemoved = true
                                    }
                                    sKnownSlots.remove("card:$spec")
                                    if (sUnifiedSlotOrder.remove("card:$spec")) {
                                        orderRemoved = true
                                    }
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

                        if (orderRemoved && context != null) {
                            saveOrder(context)
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
                    slotId.startsWith("group:") -> slotId
                    else -> null
                }
                val currentSpans = getItemSpans(slot.item, slot.viewType, spec)
                slot.spanW = currentSpans.first
                slot.spanH = currentSpans.second
            }

            // If sUnifiedSlotOrder is empty, initialize it with the true stock layout sequence
            if (sUnifiedSlotOrder.isEmpty()) {
                val stockPrio = listOf(
                    "card:wifi",
                    "card:cell",
                    "component:media",
                    "component:brightness",
                    "component:volume",
                    "component:device_center",
                    "component:device_controls"
                )
                for (pId in stockPrio) {
                    if (sKnownSlots.containsKey(pId) && !sUnifiedSlotOrder.contains(pId)) {
                        sUnifiedSlotOrder.add(pId)
                    }
                }
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

                if (ControlCenterConfigHolder.sIsEditMode) {
                    val maxRows = ControlCenterConfigHolder.landscapeRightMaxRows
                    val splitIndex = calculateSplitIndex(orderedSlots, maxRows = maxRows)

                    // 3a. Top Section (Landscape Right Panel items - exactly maxRows occupied)
                    for (i in 0 until splitIndex) {
                        val slot = orderedSlots[i]
                        val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = true)
                        newContentMap[proxy] = listOf(slot.item)
                    }

                    // 3b. Landscape Split Divider (Non-draggable visual boundary)
                    val dividerProxy = getOrCreateLandscapeDivider(classLoader, priority = 10 + splitIndex * 2)
                    newContentMap[dividerProxy] = listOf(dividerProxy)

                    // 3c. Bottom Section (Landscape Left Panel items)
                    for (i in splitIndex until orderedSlots.size) {
                        val slot = orderedSlots[i]
                        val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + (i + 1) * 2, isRight = false)
                        newContentMap[proxy] = listOf(slot.item)
                    }
                } else {
                    // 3. Normal portrait mode: All ordered active slots flow into this single adapter
                    for (i in orderedSlots.indices) {
                        val slot = orderedSlots[i]
                        val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = true)
                        newContentMap[proxy] = listOf(slot.item)
                    }
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
                // Landscape Mode: Dynamic Row Split Simulation based on user configured maxRightRows
                val maxRows = ControlCenterConfigHolder.landscapeRightMaxRows
                val (rightSlots, leftSlots) = calculateLandscapeRowSplit(orderedSlots, maxRightRows = maxRows)

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

            val sourceSlot = sKnownSlots[fromId]
            val sourceHeight = sourceSlot?.spanH ?: 1

            // Multi-row drag stabilization: prevent cascading thrash while spring animations are active
            val rv = try {
                val itemViewField = sourceVh?.javaClass?.getField("itemView")
                val v = itemViewField?.get(sourceVh) as? View
                v?.parent as? androidx.recyclerview.widget.RecyclerView
            } catch (t: Throwable) { null }
            if (sourceHeight > 1 && rv?.isAnimating == true) {
                return false
            }

            val now = SystemClock.uptimeMillis()
            val minMoveInterval = if (sourceHeight > 1) 220L else 90L
            if ((now - sLastMoveTime) < minMoveInterval) {
                return false
            }
            val movePair = if (fromId < toId) "$fromId:$toId" else "$toId:$fromId"
            if (movePair == sLastMovedPair && (now - sLastMoveTime) < (minMoveInterval * 1.5).toLong()) {
                return false
            }
            sLastMoveTime = now
            sLastMovedPair = movePair
            sDragHoverTargetId = null
            sDragHoverStartTime = 0L

            val maxRightRows = ControlCenterConfigHolder.landscapeRightMaxRows

            // Current ordered slots before candidate move
            val currentOrderedSlots = sUnifiedSlotOrder.mapNotNull { sKnownSlots[it] }
            val currentSplitCount = calculateSplitIndex(currentOrderedSlots, maxRightRows)

            // Moving into top section (above the divider)
            val isMovingIntoTopSection = (toIdx < currentSplitCount)

            // A component can only be placed above the divider if its vertical height does not exceed maxRightRows
            if (isMovingIntoTopSection && sourceHeight > maxRightRows) {
                val ctx = try {
                    val itemViewField = sourceVh?.javaClass?.getField("itemView")
                    val v = itemViewField?.get(sourceVh) as? View
                    v?.context
                } catch (t: Throwable) { null } ?: ControlCenterConfigHolder.sLastKnownContext
                showCapacityToast(ctx, maxRightRows)
                return false
            }

            val isCrossingDivider = (fromIdx < currentSplitCount && toIdx >= currentSplitCount) ||
                                    (fromIdx >= currentSplitCount && toIdx < currentSplitCount)

            val oldDividerPos = getDividerPositionInAdapter(adapter)

            val moved = sUnifiedSlotOrder.removeAt(fromIdx)
            sUnifiedSlotOrder.add(toIdx, moved)

            // Sync Wi-Fi first flag if cards were moved
            val wifiIdx = sUnifiedSlotOrder.indexOf("card:wifi")
            val cellIdx = sUnifiedSlotOrder.indexOf("card:cell")
            if (wifiIdx >= 0 && cellIdx >= 0) {
                ControlCenterConfigHolder.sIsWifiFirst = (wifiIdx < cellIdx)
            }

            // Sync QSListController addedTiles in-memory if both are circular tiles
            // WITHOUT calling moveElement (which would trigger distributor.notifyChanged and DiffUtil)
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
                        val addedTilesField = try { srcTarget.javaClass.getDeclaredField("addedTiles") } catch (e: Throwable) { null }
                        if (addedTilesField != null) {
                            addedTilesField.isAccessible = true
                            val addedList = addedTilesField.get(srcTarget) as? MutableList<Any>
                            if (addedList != null) {
                                val i1 = addedList.indexOf(sourceItem)
                                val i2 = addedList.indexOf(targetItem)
                                if (i1 >= 0 && i2 >= 0 && i1 != i2) {
                                    java.util.Collections.swap(addedList, i1, i2)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}

            // Rebuild contentMap immediately and invalidate 2D layout cache
            buildAndSetContentMap(adapter, classLoader)
            if (sDistributor != null) {
                val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                if (otherAdapter != null) {
                    buildAndSetContentMap(otherAdapter, classLoader)
                }
            }

            val newDividerPos = getDividerPositionInAdapter(adapter)

            // Boundary-safe adapter notification:
            // When moving across the divider, notifyItemMoved(fromPos, toPos) causes RecyclerView
            // to shift the divider. We immediately restore the divider to its true position newDividerPos
            // so it never drifts under or gets displaced by tiles.
            val notifyItemMovedMethod = adapter.javaClass.getMethod("notifyItemMoved", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)

            if (isCrossingDivider && oldDividerPos >= 0 && newDividerPos >= 0) {
                notifyItemMovedMethod.invoke(adapter, fromPos, toPos)
                if (fromPos < oldDividerPos && toPos > oldDividerPos) {
                    val shiftedDividerPos = oldDividerPos - 1
                    if (shiftedDividerPos != newDividerPos) {
                        notifyItemMovedMethod.invoke(adapter, shiftedDividerPos, newDividerPos)
                    }
                } else if (fromPos > oldDividerPos && toPos < oldDividerPos) {
                    val shiftedDividerPos = oldDividerPos + 1
                    if (shiftedDividerPos != newDividerPos) {
                        notifyItemMovedMethod.invoke(adapter, shiftedDividerPos, newDividerPos)
                    }
                }
            } else {
                notifyItemMovedMethod.invoke(adapter, fromPos, toPos)
                if (oldDividerPos >= 0 && newDividerPos >= 0 && oldDividerPos != newDividerPos) {
                    notifyItemMovedMethod.invoke(adapter, oldDividerPos, newDividerPos)
                }
            }

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "onItemMove error", t)
            return false
        }
    }

    fun onExitEditMode() {
        sLastDemotedSlotId = null
        sDragHoverTargetId = null
        sDragHoverStartTime = 0L
        sCurrentDraggedVh = null
        sCurrentDraggedSlotId = null
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
                val callbackClass = try {
                    Class.forName("androidx.recyclerview.widget.ItemTouchHelper\$Callback", false, classLoader)
                } catch (t: Throwable) {
                    helperClass.superclass
                }

                // Collect all methods from both helperClass (which overrides getMovementFlags, onMove)
                // and callbackClass (which defines chooseDropTarget, canDropOver, clearView, interpolateOutOfBoundsScroll, getMoveThreshold, onSelectedChanged)
                val allTargetMethods = mutableListOf<Method>()
                allTargetMethods.addAll(helperClass.declaredMethods)
                if (callbackClass != null) {
                    for (cm in callbackClass.declaredMethods) {
                        if (allTargetMethods.none { it.name == cm.name && it.parameterTypes.contentEquals(cm.parameterTypes) }) {
                            allTargetMethods.add(cm)
                        }
                    }
                }

                for (m in allTargetMethods) {
                    if (m.name == "getMovementFlags") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val vh = chain.args.getOrNull(1)
                                    if (vh != null) {
                                        val getItemViewTypeMethod = vh.javaClass.getMethod("getItemViewType")
                                        val vt = getItemViewTypeMethod.invoke(vh) as Int
                                        if (isNonDraggableItem(vt)) {
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

                    if (m.name == "onSelectedChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper) {
                                    val vh = chain.args.getOrNull(0)
                                    val actionState = (chain.args.getOrNull(1) as? Int) ?: 0
                                    if (actionState == 2 && vh != null) { // ACTION_STATE_DRAG
                                        sCurrentDraggedVh = WeakReference(vh)
                                        sCurrentDraggedSlotId = getSlotIdFromViewHolder(vh)
                                        sDragHoverTargetId = null
                                        sDragHoverStartTime = 0L
                                        sLastMoveTime = SystemClock.uptimeMillis()
                                    } else if (actionState == 0) { // ACTION_STATE_IDLE
                                        sCurrentDraggedVh = null
                                        sCurrentDraggedSlotId = null
                                        sDragHoverTargetId = null
                                        sDragHoverStartTime = 0L
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "interpolateOutOfBoundsScroll") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val viewSize = (chain.args.getOrNull(1) as? Int) ?: 0
                                    val viewSizeOutOfBounds = (chain.args.getOrNull(2) as? Int) ?: 0

                                    val draggedSlot = sCurrentDraggedSlotId?.let { sKnownSlots[it] }
                                    val isMultiRow = (draggedSlot?.spanH ?: 1) > 1 || viewSize > 250

                                    // Inverted scroll prevention:
                                    // If dragging a multi-row component (1x2, 2x2), viewSizeOutOfBounds > 0 means the bottom edge
                                    // of the 157dp view extends below the viewport. If the user is dragging upward or in the upper/mid screen,
                                    // Android's ItemTouchHelper would run away scrolling DOWN, fighting the user's drag.
                                    // Suppress downward auto-scroll (return 0) so the screen never pulls downward away from the user!
                                    if (isMultiRow && viewSizeOutOfBounds > 0) {
                                        return@intercept 0
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "getMoveThreshold") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val vh = chain.args.getOrNull(0)
                                    val slotId = getSlotIdFromViewHolder(vh)
                                    val slot = slotId?.let { sKnownSlots[it] }
                                    if ((slot?.spanH ?: 1) > 1) {
                                        return@intercept 0.65f // Require deliberate 65% penetration before initiating move
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
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val targetVh = chain.args.getOrNull(2)
                                    if (targetVh != null) {
                                        val getItemViewTypeMethod = targetVh.javaClass.getMethod("getItemViewType")
                                        val vt = getItemViewTypeMethod.invoke(targetVh) as Int
                                        if (isNonDraggableItem(vt)) {
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

                    if (m.name == "chooseDropTarget") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val selected = chain.args.getOrNull(0)
                                    val dropTargets = chain.args.getOrNull(1) as? List<*>
                                    val curX = (chain.args.getOrNull(2) as? Int) ?: 0
                                    val curY = (chain.args.getOrNull(3) as? Int) ?: 0

                                    if (selected != null && !dropTargets.isNullOrEmpty()) {
                                        val selViewField = try { selected.javaClass.getField("itemView") } catch (e: Throwable) { null }
                                        val selView = selViewField?.get(selected) as? View

                                        if (selView != null) {
                                            val fromId = getSlotIdFromViewHolder(selected)
                                            val sourceSlot = fromId?.let { sKnownSlots[it] }
                                            val isMultiRow = (sourceSlot?.spanH ?: 1) > 1 || selView.height > 250

                                            // Target the primary top cell for tall items (~quarter down the 2-row view, where user's thumb and top icon are)
                                            val selFocusX = curX + selView.width / 2
                                            val selFocusY = if (isMultiRow) {
                                                curY + (selView.height / 4)
                                            } else {
                                                curY + (selView.height / 2)
                                            }

                                            var bestTarget: Any? = null
                                            var minDistanceSq = Long.MAX_VALUE

                                            for (target in dropTargets) {
                                                if (target == null || target === selected) continue
                                                val tgtViewField = try { target.javaClass.getField("itemView") } catch (e: Throwable) { null }
                                                val tgtView = tgtViewField?.get(target) as? View ?: continue

                                                val vt = try {
                                                    val getItemViewTypeMethod = target.javaClass.getMethod("getItemViewType")
                                                    getItemViewTypeMethod.invoke(target) as Int
                                                } catch (t: Throwable) { -1 }

                                                if (isNonDraggableItem(vt)) continue

                                                val tgtCenterX = tgtView.left + tgtView.width / 2
                                                val tgtCenterY = tgtView.top + tgtView.height / 2

                                                val dx = (selFocusX - tgtCenterX).toLong()
                                                val dy = (selFocusY - tgtCenterY).toLong()
                                                val distSq = dx * dx + dy * dy

                                                if (distSq < minDistanceSq) {
                                                    minDistanceSq = distSq
                                                    bestTarget = target
                                                }
                                            }

                                            if (bestTarget != null) {
                                                if (isMultiRow) {
                                                    val rv = selView.parent as? androidx.recyclerview.widget.RecyclerView
                                                    if (rv?.isAnimating == true) {
                                                        return@intercept null
                                                    }
                                                    val now = SystemClock.uptimeMillis()
                                                    val targetId = getSlotIdFromViewHolder(bestTarget)
                                                    if (targetId != null) {
                                                        if (targetId != sDragHoverTargetId) {
                                                            sDragHoverTargetId = targetId
                                                            sDragHoverStartTime = now
                                                            return@intercept null
                                                        } else {
                                                            val hoverDuration = now - sDragHoverStartTime
                                                            if (hoverDuration < 140L) {
                                                                return@intercept null
                                                            }
                                                        }
                                                    }
                                                }
                                                return@intercept bestTarget
                                            }
                                        }
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
                                val helperCallback = chain.thisObject
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper && ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.config.modularEditEnabled) {
                                    val adapter = try {
                                        val this0Field = helperClass.getDeclaredField("this$0")
                                        this0Field.isAccessible = true
                                        this0Field.get(helperCallback)
                                    } catch (t: Throwable) {
                                        ControlCenterConfigHolder.sMainPanelAdapter
                                    }

                                    val sourceVh = chain.args.getOrNull(1)
                                    val targetVh = chain.args.getOrNull(2)

                                    if (adapter != null && sourceVh != null && targetVh != null) {
                                        val getPosMethod = try {
                                            sourceVh.javaClass.getMethod("getBindingAdapterPosition")
                                        } catch (e: Throwable) {
                                            sourceVh.javaClass.getMethod("getAdapterPosition")
                                        }
                                        val fromPos = getPosMethod.invoke(sourceVh) as Int
                                        val toPos = getPosMethod.invoke(targetVh) as Int

                                        if (fromPos >= 0 && toPos >= 0 && fromPos != toPos) {
                                            val success = onItemMove(adapter, fromPos, toPos, sourceVh, targetVh, classLoader)
                                            if (success) return@intercept true
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
                                val isOurHelper = helperCallback != null && (helperCallback.javaClass.name.contains("MainPanelAdapter") || helperCallback.javaClass == helperClass)
                                if (isOurHelper) {
                                    sDragHoverTargetId = null
                                    sDragHoverStartTime = 0L
                                    sCurrentDraggedVh = null
                                    sCurrentDraggedSlotId = null
                                    sLastDemotedSlotId = null
                                    sLastMovedPair = ""
                                    sLastMoveTime = 0L
                                    val adapter = try {
                                        val this0Field = helperClass.getDeclaredField("this$0")
                                        this0Field.isAccessible = true
                                        this0Field.get(helperCallback)
                                    } catch (t: Throwable) {
                                        ControlCenterConfigHolder.sMainPanelAdapter
                                    }
                                    if (adapter != null) {
                                        val ctxField = adapter.javaClass.getDeclaredField("context")
                                        ctxField.isAccessible = true
                                        val ctx = ctxField.get(adapter) as? Context
                                        saveOrder(ctx)
                                        val cl = adapter.javaClass.classLoader
                                        if (cl != null) {
                                            buildAndSetContentMap(adapter, cl)
                                            if (sDistributor != null) {
                                                val otherAdapter = if (adapter === sRightAdapter) sLeftAdapter else if (adapter === sLeftAdapter) sRightAdapter else null
                                                if (otherAdapter != null) {
                                                    buildAndSetContentMap(otherAdapter, cl)
                                                }
                                            }
                                        }
                                        True2DGridEngine.invalidateAll()
                                        val notifyDataSetChangedM = adapter.javaClass.getMethod("notifyDataSetChanged")
                                        try {
                                            val rvField = adapter.javaClass.getDeclaredField("recyclerView")
                                            rvField.isAccessible = true
                                            val rv = rvField.get(adapter) as? ViewGroup
                                            rv?.post {
                                                try {
                                                    True2DGridEngine.invalidateAll()
                                                    notifyDataSetChangedM.invoke(adapter)
                                                } catch (t: Throwable) {}
                                            } ?: notifyDataSetChangedM.invoke(adapter)
                                        } catch (t: Throwable) {
                                            notifyDataSetChangedM.invoke(adapter)
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook itemTouchHelper", t)
            }

            Log.i(TAG, "ControlCenterFullUnlock hooks initialized successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ControlCenterFullUnlock", t)
        }
    }
}
