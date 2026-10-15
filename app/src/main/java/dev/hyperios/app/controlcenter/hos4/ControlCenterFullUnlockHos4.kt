package dev.hyperios.app.controlcenter.hos4

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.hyperios.app.controlcenter.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * HyperOS 4 Full Layout Unlock & Content Distributor.
 * Fully supports all 4 stock 2x1 card tiles (wifi, cell, bt, quietmode),
 * preserves CompactQSCardController, and dynamically maps items into
 * MainPanelAdapter's contentMap.
 */
object ControlCenterFullUnlockHos4 {
    private const val TAG = "HyperBounce_Unlock4"
    const val VIEW_TYPE_LANDSCAPE_DIVIDER = 8399

    val sKnownSlots = ConcurrentHashMap<String, SlotItem>()
    val sUnifiedSlotOrder = Collections.synchronizedList(ArrayList<String>())
    val sProxyCache = ConcurrentHashMap<String, Any>()
    val sProxyHandlers = ConcurrentHashMap<String, ProxyContentHandler>()

    var sDistributor: Any? = null
    var sRightAdapter: Any? = null
    var sLeftAdapter: Any? = null

    val sAdapterHeaders = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    val sAdapterWordless = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    val sAdapterQsFooters = ConcurrentHashMap<Any, Pair<Any, List<Any>>>()
    val sAdapterFooters = ConcurrentHashMap<Any, List<Pair<Any, List<Any>>>>()

    var sCurrentDraggedSlotId: String? = null

    data class SlotItem(
        val id: String,
        val owner: Any,
        val item: Any,
        val viewType: Int,
        var spanW: Int,
        var spanH: Int
    )

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
                "toString" -> return "ProxyMainPanelContent4($id -> ${target.javaClass.simpleName})"
            }
            return forwardCall(target, method, args)
        }
    }

    private fun forwardCall(target: Any, method: Method, args: Array<out Any?>?): Any? {
        try {
            val targetMethod = target.javaClass.getMethod(method.name, *method.parameterTypes)
            targetMethod.isAccessible = true
            return targetMethod.invoke(target, *(args ?: emptyArray()))
        } catch (_: NoSuchMethodException) {}

        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.name == method.name && m.parameterTypes.size == method.parameterTypes.size) {
                    var match = true
                    for (i in m.parameterTypes.indices) {
                        if (!m.parameterTypes[i].isAssignableFrom(method.parameterTypes[i])) {
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

    fun createLandscapeDividerViewHolder(parent: ViewGroup, classLoader: ClassLoader): Any? {
        try {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val h = (36 * density).toInt()

            val dividerView = FrameLayout(ctx).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
                val line = View(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1)).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundColor(0x30FFFFFF.toInt())
                }
                addView(line)
            }

            val vhClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader)
            for (c in vhClass.declaredConstructors) {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(View::class.java)) {
                    c.isAccessible = true
                    return c.newInstance(dividerView)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createLandscapeDividerViewHolder error", t)
        }
        return null
    }

    /**
     * Discovers all slots across controllers and re-maps contentMap.
     */
    fun reorganizeContentMap(adapter: Any, classLoader: ClassLoader) {
        try {
            val adapterClass = adapter.javaClass
            val contentMapField = adapterClass.getDeclaredField("contentMap")
            contentMapField.isAccessible = true
            val currentMap = contentMapField.get(adapter) as? Map<*, *> ?: return

            val ctxField = adapterClass.getDeclaredField("context")
            ctxField.isAccessible = true
            val context = ctxField.get(adapter) as? Context ?: ControlCenterConfigHolder.sLastKnownContext
            if (context != null) {
                ControlCenterTileDetectorHos4.inspectResources(context)
            }

            var qsListCtrl: Any? = null
            var addedTilesList: List<*>? = null

            for ((controller, items) in currentMap) {
                if (controller == null || items !is List<*>) continue
                val ctrlName = controller.javaClass.name

                if (ctrlName.contains("QSListController")) {
                    qsListCtrl = controller
                    try {
                        val addedField = controller.javaClass.getDeclaredField("addedTiles")
                        addedField.isAccessible = true
                        addedTilesList = addedField.get(controller) as? List<*>
                    } catch (_: Throwable) {}
                }

                // Check for QSController to discover card specs
                try {
                    val qsCtrlField = controller.javaClass.getDeclaredField("qsController")
                    qsCtrlField.isAccessible = true
                    val lazy = qsCtrlField.get(controller)
                    val getM = lazy?.javaClass?.getMethod("get")
                    val qsc = getM?.invoke(lazy)
                    ControlCenterTileDetectorHos4.inspectQSController(qsc)
                } catch (_: Throwable) {}
            }

            // Inspect all controllers and collect slot items
            for ((controller, items) in currentMap) {
                if (controller == null || items !is List<*>) continue
                val ctrlName = controller.javaClass.name

                // PRESERVE CompactQSCardController in HOS 4!
                if (ctrlName.contains("CompactQSListController")) continue

                if (ctrlName.contains("QSCardsController") || ctrlName.contains("CompactQSCardController")) {
                    val recordList = if (ctrlName.contains("CompactQSCardController")) {
                        try {
                            val f = controller.javaClass.getDeclaredField("qsRecords")
                            f.isAccessible = true
                            (f.get(controller) as? List<*>) ?: items
                        } catch (_: Throwable) { items }
                    } else {
                        items
                    }
                    for (item in recordList) {
                        if (item == null) continue
                        val spec = ControlCenterTileDetectorHos4.inspectRecord(item) ?: continue

                        val group = ControlCenterConfigHolder.findGroupForSpec(spec)
                        if (group != null) {
                            if (ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                val id = "group:${group.id}"
                                val spans = group.getSpan()
                                sKnownSlots[id] = SlotItem(id, controller, item, 2274, spans.first, spans.second)
                            }
                            sKnownSlots.remove("card:$spec")
                            sKnownSlots.remove("tile:$spec")
                            sUnifiedSlotOrder.remove("card:$spec")
                            sUnifiedSlotOrder.remove("tile:$spec")
                            continue
                        }

                        val id = "card:$spec"
                        sKnownSlots[id] = SlotItem(id, controller, item, 2273, 2, 1)
                    }
                } else if (ctrlName.contains("QSListController")) {
                    for (item in items) {
                        if (item == null) continue
                        val spec = ControlCenterTileDetectorHos4.inspectRecord(item)

                        var isDivider = false
                        try {
                            val getTypeM = item.javaClass.getMethod("getType")
                            val vt = getTypeM.invoke(item) as? Int
                            if (vt == 8398 || vt == 3484337) isDivider = true
                        } catch (_: Throwable) {}

                        if (isDivider || spec == null) continue

                        val isAdded = addedTilesList == null || addedTilesList.contains(item)
                        if (isAdded) {
                            val group = ControlCenterConfigHolder.findGroupForSpec(spec)
                            if (group != null) {
                                if (ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                    val id = "group:${group.id}"
                                    val spans = group.getSpan()
                                    sKnownSlots[id] = SlotItem(id, controller, item, 2274, spans.first, spans.second)
                                }
                                sKnownSlots.remove("tile:$spec")
                                sKnownSlots.remove("card:$spec")
                                sUnifiedSlotOrder.remove("tile:$spec")
                                sUnifiedSlotOrder.remove("card:$spec")
                                continue
                            }

                            val isCard = ControlCenterTileDetectorHos4.isCard(spec)
                            val id = if (isCard) "card:$spec" else "tile:$spec"
                            val spanW = if (isCard) 2 else 1
                            val vt = if (isCard) 2273 else 8453
                            sKnownSlots[id] = SlotItem(id, controller, item, vt, spanW, 1)
                        }
                    }
                } else if (ctrlName.contains("MediaPlayerController")) {
                    val item = items.firstOrNull() ?: controller
                    sKnownSlots["component:media"] = SlotItem("component:media", controller, item, 3484336, 2, 2)
                } else if (ctrlName.contains("BrightnessSliderController")) {
                    val item = items.firstOrNull() ?: controller
                    sKnownSlots["component:brightness"] = SlotItem("component:brightness", controller, item, 3484338, 1, 2)
                } else if (ctrlName.contains("VolumeSliderController")) {
                    val item = items.firstOrNull() ?: controller
                    sKnownSlots["component:volume"] = SlotItem("component:volume", controller, item, 3484339, 1, 2)
                } else if (ctrlName.contains("DeviceCenterEntryController")) {
                    val item = items.firstOrNull() ?: controller
                    sKnownSlots["component:device_center"] = SlotItem("component:device_center", controller, item, 338423, 2, 1)
                } else if (ctrlName.contains("DeviceControlsEntryController")) {
                    val item = items.firstOrNull() ?: controller
                    sKnownSlots["component:device_controls"] = SlotItem("component:device_controls", controller, item, 2668765, 2, 1)
                }
            }

            buildAndSetContentMap(adapter, classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "reorganizeContentMap error", t)
        }
    }

    /**
     * Builds and sets new ContentMap with all 4 cards and custom elements.
     */
    fun buildAndSetContentMap(adapter: Any, classLoader: ClassLoader) {
        try {
            val adapterClass = adapter.javaClass
            val contentMapField = adapterClass.getDeclaredField("contentMap")
            contentMapField.isAccessible = true

            // Initialize default HyperOS 4 stock sequence if empty
            if (sUnifiedSlotOrder.isEmpty()) {
                val stockPrio = listOf(
                    "card:wifi",
                    "card:cell",
                    "card:bt",
                    "card:quietmode",
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

            // Order active slots
            val orderedSlots = ArrayList<SlotItem>()
            val remaining = LinkedHashMap(sKnownSlots)

            for (savedId in sUnifiedSlotOrder) {
                val slot = remaining.remove(savedId)
                if (slot != null) {
                    orderedSlots.add(slot)
                }
            }

            // Append any remaining discovered slots
            for ((newId, slot) in remaining) {
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
            }

            val newContentMap = LinkedHashMap<Any, List<Any>>()

            // 1. Top header spacer if present
            val headerEntry = sAdapterHeaders[adapter]
            if (headerEntry != null) {
                newContentMap[headerEntry.first] = headerEntry.second
            }

            // 2. Active slots as proxies
            for (i in orderedSlots.indices) {
                val slot = orderedSlots[i]
                val proxy = getOrCreateProxy(slot, classLoader, priority = 10 + i * 2, isRight = true)
                newContentMap[proxy] = listOf(slot.item)
            }

            // 3. Edit mode footer pool if in edit mode
            if (ControlCenterConfigHolder.sIsEditMode) {
                val qsFooter = sAdapterQsFooters[adapter]
                if (qsFooter != null) {
                    newContentMap[qsFooter.first] = qsFooter.second
                }
            }

            // 4. Other footers
            val footers = sAdapterFooters[adapter]
            if (footers != null) {
                for ((ctrl, fItems) in footers) {
                    newContentMap[ctrl] = fItems
                }
            }

            contentMapField.set(adapter, newContentMap)
        } catch (t: Throwable) {
            Log.e(TAG, "buildAndSetContentMap error", t)
        }
    }

    /**
     * Group creation notification.
     */
    fun onGroupCreated(groupId: String, specs: List<String>, context: Context?) {
        try {
            val groupSlotId = "group:$groupId"
            val anchorSpec = specs.firstOrNull()
            val anchorSlot = if (anchorSpec != null) {
                val isCard = ControlCenterTileDetectorHos4.isCard(anchorSpec)
                sKnownSlots[if (isCard) "card:$anchorSpec" else "tile:$anchorSpec"]
            } else null

            var insertIdx = -1
            for (spec in specs) {
                val isCard = ControlCenterTileDetectorHos4.isCard(spec)
                val id = if (isCard) "card:$spec" else "tile:$spec"
                val idx = sUnifiedSlotOrder.indexOf(id)
                if (idx >= 0 && (insertIdx == -1 || idx < insertIdx)) {
                    insertIdx = idx
                }
                sKnownSlots.remove(id)
                sProxyCache.remove(id)
                sProxyHandlers.remove(id)
                sUnifiedSlotOrder.remove(id)
            }

            if (insertIdx >= 0 && insertIdx <= sUnifiedSlotOrder.size) {
                sUnifiedSlotOrder.add(insertIdx, groupSlotId)
            } else {
                sUnifiedSlotOrder.add(groupSlotId)
            }

            if (anchorSlot != null) {
                val group = ControlCenterConfigHolder.sTileGroups.find { it.id == groupId }
                val spans = group?.getSpan() ?: Pair(2, 2)
                sKnownSlots[groupSlotId] = SlotItem(groupSlotId, anchorSlot.owner, anchorSlot.item, 2274, spans.first, spans.second)
            }

            if (context != null) {
                ControlCenterConfigHolder.savePriorities(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onGroupCreated error", t)
        }
    }

    /**
     * Group disassembly notification.
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

                val memberTileIds = g.specs.map {
                    val isCard = ControlCenterTileDetectorHos4.isCard(it)
                    if (isCard) "card:$it" else "tile:$it"
                }
                if (idx >= 0 && idx <= sUnifiedSlotOrder.size) {
                    sUnifiedSlotOrder.addAll(idx, memberTileIds)
                } else {
                    sUnifiedSlotOrder.addAll(memberTileIds)
                }
            }
            if (context != null) {
                ControlCenterConfigHolder.savePriorities(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onGroupsDisassembled error", t)
        }
    }

    /**
     * Resets unified slot ordering to stock HyperOS 4 sequence.
     */
    fun resetOrder(context: Context?) {
        try {
            val stockList = mutableListOf(
                "card:wifi",
                "card:cell",
                "card:bt",
                "card:quietmode",
                "component:media",
                "component:brightness",
                "component:volume",
                "component:device_center",
                "component:device_controls"
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
            if (context != null) {
                ControlCenterConfigHolder.savePriorities(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "resetOrder error", t)
        }
    }
}
