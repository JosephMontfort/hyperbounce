package dev.hyperios.app.controlcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

object ControlCenterHook {
    private const val TAG = "HyperBounce_CC_Hook"
    private val hookedPluginLoaders = CopyOnWriteArrayList<ClassLoader>()
    @Volatile private var sysuiHooked = false
    @Volatile var sMediaPlayerViewHolder: java.lang.ref.WeakReference<Any>? = null
    @Volatile var sLastMediaPlayerMetaData: Any? = null
    private val sInChangeVisible = ThreadLocal<Boolean>()
    @Volatile private var sItemViewField: Field? = null
    @Volatile private var sExpandScaleRatioField: Field? = null
    @Volatile private var sExpandTransYField: Field? = null
    @Volatile private var sSpreadRowField: Field? = null
    @Volatile private var sShrinkXEaseField: Field? = null
    @Volatile private var sShrinkCenterXField: Field? = null
    @Volatile private var sShrinkCenterYField: Field? = null
    @Volatile private var sDelayPerField: Field? = null
    @Volatile private var sReleaseTransYEaseField: Field? = null
    @Volatile private var sShowEaseField: Field? = null
    @Volatile private var sHideEaseField: Field? = null
    @Volatile private var sShrinkYEaseField: Field? = null
    @Volatile private var sSetFactorsMethod: Method? = null
    @Volatile private var sGetContextMethod: Method? = null

    fun initSystemUIHooks(module: XposedModule, classLoader: ClassLoader?) {
        if (classLoader == null || sysuiHooked) return

        try {
            Log.i(TAG, "Initializing Control Center SystemUI Hooks...")

            // 1. Hook SystemUIApplication.onCreate to handle startup state and queued stock reset
            try {
                val sysuiAppClass = Class.forName("com.android.systemui.SystemUIApplication", false, classLoader)
                for (m in sysuiAppClass.declaredMethods) {
                    if (m.name == "onCreate") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val app = chain.thisObject as? Context
                                if (app != null) {
                                    ControlCenterConfigHolder.sLastKnownContext = app.applicationContext
                                    ControlCenterConfigHolder.loadSavedPriorities(app.applicationContext)
                                    val filter = IntentFilter("dev.hyperios.app.RESET_CC_LAYOUT")
                                    app.registerReceiver(object : BroadcastReceiver() {
                                        override fun onReceive(context: Context?, intent: Intent?) {
                                            Log.i(TAG, "Broadcast received: dev.hyperios.app.RESET_CC_LAYOUT")
                                            ControlCenterConfigHolder.resetLayoutToStock(context)
                                        }
                                    }, filter, Context.RECEIVER_EXPORTED)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "SystemUIApplication.onCreate hook error", t)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook SystemUIApplication.onCreate", t)
            }

            if (!ControlCenterConfigHolder.config.masterEnabled) {
                Log.i(TAG, "Control Center master toggle disabled. Skipping all other SystemUI CC hooks.")
                sysuiHooked = true
                return
            }

            try {
                val factoryClass = Class.forName("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", false, classLoader)
                for (m in factoryClass.declaredMethods) {
                    if (m.name == "createPlugin") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val plugin = chain.proceed()
                            if (ControlCenterConfigHolder.config.masterEnabled && plugin != null) {
                                val pluginLoader = plugin.javaClass.classLoader
                                if (pluginLoader != null) {
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        initPluginHooks(module, pluginLoader)
                                    } else {
                                        Handler(Looper.getMainLooper()).post {
                                            initPluginHooks(module, pluginLoader)
                                        }
                                    }
                                }
                            }
                            plugin
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook PluginFactory.createPlugin", t)
            }

            try {
                val pluginManagerClass = Class.forName("com.android.systemui.plugins.PluginManager", false, classLoader)
                for (m in pluginManagerClass.declaredMethods) {
                    if (m.name == "onPluginConnected") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                if (ControlCenterConfigHolder.config.masterEnabled) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    if (args != null && args.isNotEmpty() && args[0] != null) {
                                        val pluginLoader = args[0]!!.javaClass.classLoader
                                        if (pluginLoader != null) {
                                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                                initPluginHooks(module, pluginLoader)
                                            } else {
                                                Handler(Looper.getMainLooper()).post {
                                                    initPluginHooks(module, pluginLoader)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onPluginConnected hook", t)
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook PluginManager.onPluginConnected", t)
            }

            try {
                val cccClass = Class.forName("com.miui.systemui.controlcenter.container.ControlCenterContentController", false, classLoader)
                for (m in cccClass.declaredMethods) {
                    if (m.name == "setPlugin" || m.name == "onPluginLoaded") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                if (ControlCenterConfigHolder.config.masterEnabled) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    if (args != null && args.isNotEmpty() && args[0] != null) {
                                        val pluginLoader = args[0]!!.javaClass.classLoader
                                        if (pluginLoader != null) {
                                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                                initPluginHooks(module, pluginLoader)
                                            } else {
                                                Handler(Looper.getMainLooper()).post {
                                                    initPluginHooks(module, pluginLoader)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val shadeInteractorClass = Class.forName("com.miui.systemui.shade.MiuiShadeInteractor", false, classLoader)
                for (c in shadeInteractorClass.declaredConstructors) {
                    c.isAccessible = true
                    module.hook(c).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val interactor = chain.thisObject
                            val lowEndAnimField = shadeInteractorClass.getDeclaredField("lowEndAnim")
                            lowEndAnimField.isAccessible = true
                            ControlCenterConfigHolder.sShadeLowEndAnimFlow = lowEndAnimField.get(interactor)
                        } catch (t: Throwable) {}
                        result
                    }
                }
            } catch (t: Throwable) {}

            try {
                val readonlyStateFlowClass = Class.forName("kotlinx.coroutines.flow.ReadonlyStateFlow", false, classLoader)
                for (m in readonlyStateFlowClass.declaredMethods) {
                    if (m.name == "getValue") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val target = chain.thisObject
                            if (target == ControlCenterConfigHolder.sShadeLowEndAnimFlow) {
                                if (!ControlCenterConfigHolder.config.masterEnabled) return@intercept chain.proceed()
                                return@intercept java.lang.Boolean.valueOf(ControlCenterConfigHolder.config.isLowEndAnim())
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            sysuiHooked = true
        } catch (t: Throwable) {
            Log.e(TAG, "initSystemUIHooks fatal error", t)
        }
    }

    fun isChildOfContainer(view: View?): Boolean {
        if (view == null) return false
        if (view.getTag(0x7f09999c) == true || view.getTag(0x7f09999b) == true) return true
        var cur = view.parent
        while (cur != null) {
            if (cur is View && (cur.id == 0x7f099995 || cur.getTag(0x7f099995) != null || cur.getTag(0x7f099993) != null || cur is IosTileGroupFolder.IosClusterContainer || cur is IosTileGroupFolder.MiniClusterView || cur is IosTileGroupFolder.IosFolderExpandedOverlay)) {
                return true
            }
            if (cur is View && cur.javaClass.name.contains("RecyclerView")) return false
            cur = cur.parent
        }
        return false
    }

    const val UNIVERSAL_MARK_TAG = 0x7f09998f

    fun hideAllCustomBadges(v: View?) {
        if (v == null) return
        val resizeTag = 0x7f099999
        val selectTag = 0x7f099998
        val rotateTag = 0x7f099992

        fun clearBadge(badge: View?) {
            if (badge != null) {
                badge.visibility = View.GONE
                badge.setOnTouchListener(null)
                badge.setOnClickListener(null)
                badge.isClickable = false
            }
        }

        clearBadge(v.findViewById(resizeTag))
        clearBadge(v.findViewById(selectTag))
        clearBadge(v.findViewById(rotateTag))
        clearBadge(v.findViewById(UNIVERSAL_MARK_TAG))

        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val ch = v.getChildAt(i) ?: continue
                if (ch.id == resizeTag || ch.id == selectTag || ch.id == rotateTag || ch.id == UNIVERSAL_MARK_TAG) {
                    clearBadge(ch)
                }
            }
        }

        val container = v.findViewById<ViewGroup>(0x7f099995)
        if (container != null) {
            container.isClickable = false
            for (i in 0 until 4) {
                val childTile = ControlCenterGrouping.getChildTileAt(container, i)
                if (childTile != null) {
                    clearBadge(childTile.findViewById(resizeTag))
                    clearBadge(childTile.findViewById(selectTag))
                    clearBadge(childTile.findViewById(rotateTag))
                    clearBadge(childTile.findViewById(UNIVERSAL_MARK_TAG))
                    childTile.isClickable = true
                    childTile.isLongClickable = true
                    childTile.isFocusable = true
                }
            }
        }
    }

    fun applyUniversalMarkButton(itemView: View, spec: String, isAdded: Boolean, record: Any? = null) {
        if (!ControlCenterConfigHolder.config.isModularEditActive ||
            isChildOfContainer(itemView) ||
            itemView.findViewById<View>(0x7f099995) != null ||
            ControlCenterConfigHolder.isGroupAnchor(spec) != null ||
            ControlCenterConfigHolder.isGroupMember(spec)) {
            val mark = itemView.findViewById<View>(UNIVERSAL_MARK_TAG)
            if (mark != null) {
                mark.visibility = View.GONE
                mark.setOnClickListener(null)
                mark.isClickable = false
            }
            return
        }

        val markBtn = itemView.findViewById<ImageView>(UNIVERSAL_MARK_TAG)
        if (!ControlCenterConfigHolder.sIsEditMode ||
            ControlCenterConfigHolder.sIsGroupSelectMode ||
            ControlCenterConfigHolder.sIsDisassembleMode ||
            ControlCenterConfigHolder.sIsDraggingResizeHandle) {
            markBtn?.visibility = View.GONE
            markBtn?.setOnClickListener(null)
            markBtn?.isClickable = false
            return
        }

        if (itemView !is ViewGroup) return

        val density = itemView.context.resources.displayMetrics.density
        val markSize = (26 * density).toInt()
        val margin = (6 * density).toInt()

        val targetMark = markBtn ?: ImageView(itemView.context).apply {
            id = UNIVERSAL_MARK_TAG
            val lp = FrameLayout.LayoutParams(markSize, markSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, margin, margin, 0)
            }
            layoutParams = lp
            itemView.addView(this)
        }

        val removeRes = targetMark.context.resources.getIdentifier("ic_qs_tile_mark_remove", "drawable", targetMark.context.packageName).takeIf { it != 0 } ?: 0x7f080634
        val addRes = targetMark.context.resources.getIdentifier("ic_qs_tile_mark_add", "drawable", targetMark.context.packageName).takeIf { it != 0 } ?: 0x7f080633

        targetMark.setImageResource(if (isAdded) removeRes else addRes)
        targetMark.contentDescription = if (isAdded) "Remove" else "Add"
        targetMark.visibility = View.VISIBLE
        targetMark.isClickable = true

        fun applyMarkLayout() {
            val bw = targetMark.measuredWidth.takeIf { it > 0 } ?: markSize
            val bh = targetMark.measuredHeight.takeIf { it > 0 } ?: markSize
            val w = itemView.width
            if (w > 0) {
                targetMark.layout(w - bw - margin, margin, w - margin, margin + bh)
            }
            targetMark.bringToFront()
        }

        applyMarkLayout()

        if (itemView.getTag(0x7f09998d) == null) {
            itemView.setTag(0x7f09998d, true)
            itemView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                val curMark = itemView.findViewById<ImageView>(UNIVERSAL_MARK_TAG)
                if (curMark != null && curMark.visibility == View.VISIBLE) {
                    val w = r - l
                    val bw = curMark.measuredWidth.takeIf { it > 0 } ?: markSize
                    val bh = curMark.measuredHeight.takeIf { it > 0 } ?: markSize
                    if (w > 0) {
                        curMark.layout(w - bw - margin, margin, w - margin, margin + bh)
                    }
                    curMark.bringToFront()
                }
            }
        }

        targetMark.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (isAdded) {
                removeComponent(itemView.context, spec, record, itemView)
            } else {
                addComponent(itemView.context, spec, record, itemView)
            }
        }
    }

    fun removeComponent(context: Context, spec: String, record: Any?, itemView: View) {
        try {
            ControlCenterConfigHolder.sRemovedComponentSpecs.add(spec)
            ControlCenterConfigHolder.savePriorities(context)
            if (spec == "wifi" || spec == "cell" || spec == "media" || spec == "brightness" || spec == "volume") {
                // Specially managed components: state is handled strictly by sRemovedComponentSpecs
                // and reorganized into footer system items by reorganizeContentMap.
                // Do NOT invoke QSListController.removeTile as it pollutes QSListController with card records or non-tiles.
            } else {
                val targetRecord = record ?: ControlCenterConfigHolder.findRecordForSpec(spec)
                if (targetRecord != null && ControlCenterConfigHolder.sQSListController != null) {
                    try {
                        val m = ControlCenterConfigHolder.sQSListController!!.javaClass.getMethod("removeTile", targetRecord.javaClass)
                        m.invoke(ControlCenterConfigHolder.sQSListController, targetRecord)
                    } catch (t: Throwable) {
                        try {
                            val markActionF = targetRecord.javaClass.getDeclaredField("markClickAction")
                            markActionF.isAccessible = true
                            val fn = markActionF.get(targetRecord) as? kotlin.jvm.functions.Function1<View, *>
                            fn?.invoke(itemView)
                        } catch (t2: Throwable) {}
                    }
                }
            }
            ControlCenterConfigHolder.refreshAdapter(itemView.rootView, context, isStructuralChange = true)
            ControlCenterConfigHolder.updateAllVisibleTileBadges()
        } catch (t: Throwable) {
            Log.e(TAG, "removeComponent error for spec $spec", t)
        }
    }

    fun addComponent(context: Context, spec: String, record: Any?, itemView: View) {
        try {
            ControlCenterConfigHolder.sRemovedComponentSpecs.remove(spec)
            ControlCenterFullUnlock.restoreComponentSlotOrder(spec)
            ControlCenterConfigHolder.savePriorities(context)
            if (spec == "wifi" || spec == "cell" || spec == "media" || spec == "brightness" || spec == "volume") {
                // Specially managed components: slot order is restored into sUnifiedSlotOrder
                // and rendered by QSCardsController / respective controllers.
                // Do NOT invoke QSListController.addTile as it creates duplicate tile records.
            } else {
                val targetRecord = record ?: ControlCenterConfigHolder.findRecordForSpec(spec)
                if (targetRecord != null && ControlCenterConfigHolder.sQSListController != null) {
                    try {
                        val m = ControlCenterConfigHolder.sQSListController!!.javaClass.getMethod("addTile", targetRecord.javaClass)
                        m.invoke(ControlCenterConfigHolder.sQSListController, targetRecord)
                    } catch (t: Throwable) {
                        try {
                            val markActionF = targetRecord.javaClass.getDeclaredField("markClickAction")
                            markActionF.isAccessible = true
                            val fn = markActionF.get(targetRecord) as? kotlin.jvm.functions.Function1<View, *>
                            fn?.invoke(itemView)
                        } catch (t2: Throwable) {}
                    }
                }
            }
            ControlCenterConfigHolder.refreshAdapter(itemView.rootView, context, isStructuralChange = true)
            ControlCenterConfigHolder.updateAllVisibleTileBadges()
        } catch (t: Throwable) {
            Log.e(TAG, "addComponent error for spec $spec", t)
        }
    }

    fun setCircularTileLabelVisibility(itemView: View, visible: Boolean = true) {
        try {
            val cName = itemView.javaClass.name
            if (!cName.contains("Tile") || cName.contains("Card")) {
                return
            }
            val ctx = itemView.context
            val isWordless = try {
                android.provider.Settings.Secure.getInt(ctx.contentResolver, "wordless_mode", 0) != 0
            } catch (t: Throwable) { false }

            // If feature is turned off (wordless_mode != 0), labels are NEVER shown
            // If feature is turned on (wordless_mode == 0), labels are shown unless hidden by visible == false
            val shouldShow = visible && !isWordless

            // 1. Keep QSTileItemView internal textMode in sync
            try {
                val textModeEnumClass = itemView.javaClass.classLoader?.loadClass("miui.systemui.controlcenter.panel.main.qs.QSListController\$TextMode")
                if (textModeEnumClass != null) {
                    val targetModeName = if (shouldShow) "TEXT" else "NO_TEXT"
                    val targetMode = java.lang.Enum.valueOf(textModeEnumClass as Class<out Enum<*>>, targetModeName)
                    val onTextModeChangedM = itemView.javaClass.methods.firstOrNull { it.name == "onTextModeChanged" }
                    onTextModeChangedM?.invoke(itemView, targetMode, false)
                }
            } catch (t: Throwable) {}

            val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
            val tileLabelId = ctx.resources.getIdentifier("tile_label", "id", ctx.packageName)
            val tileLabel = if (tileLabelId != 0) itemView.findViewById<View>(tileLabelId) else null
            if (tileLabel != null) {
                tileLabel.visibility = if (shouldShow) View.VISIBLE else View.GONE
                tileLabel.alpha = if (shouldShow) 1.0f else 0.0f
                if (shouldShow) {
                    tileLabel.translationY = 0.0f
                }
            }

            fun process(v: View) {
                if (iconFrameId != 0 && v.id == iconFrameId) return
                if (v is TextView) {
                    v.visibility = if (shouldShow) View.VISIBLE else View.GONE
                    v.alpha = if (shouldShow) 1.0f else 0.0f
                    if (shouldShow) {
                        v.translationY = 0.0f
                    }
                } else if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        process(v.getChildAt(i))
                    }
                }
            }
            process(itemView)
        } catch (t: Throwable) {}
    }

    fun findRecyclerView(view: View?): ViewGroup? {
        var cur = view?.parent
        while (cur != null) {
            if (cur is ViewGroup && cur.javaClass.name.contains("RecyclerView")) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }

    @Synchronized
    fun initPluginHooks(module: XposedModule, classLoader: ClassLoader?) {
        if (classLoader == null || hookedPluginLoaders.contains(classLoader)) return
        if (!ControlCenterConfigHolder.config.masterEnabled) {
            Log.i(TAG, "Control Center master toggle disabled. Skipping initPluginHooks.")
            return
        }

        try {
            Log.i(TAG, "Initializing Control Center Plugin hooks on ClassLoader: $classLoader")

            // 0a. Hook Low-End Flashlight Brightness Control
            try {
                FlashlightBrightnessHook.init(module, classLoader)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize FlashlightBrightnessHook", t)
            }

            // 0b. Hook QSTileItemView.getShowLabel to return false in EDIT mode
            try {
                val qstileItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemView", false, classLoader)
                for (m in qstileItemViewClass.declaredMethods) {
                    if (m.name == "getShowLabel" || m.name == "isShowLabel") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val v = chain.thisObject as? View
                            if (ControlCenterConfigHolder.sIsEditMode) {
                                val isSubTile = isChildOfContainer(v)
                                if (!isSubTile) {
                                    return@intercept false
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 1. Hook Low-End Animation Switch
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
            } catch (t: Throwable) {}

            // 2. Hook Touch Tracking & Velocity
            try {
                val touchClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelTouchController", false, classLoader)
                for (m in touchClass.declaredMethods) {
                    if (m.name == "handleMotionEvent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] is MotionEvent) {
                                    val ev = args[0] as MotionEvent
                                    val action = ev.actionMasked
                                    if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
                                        ControlCenterConfigHolder.sIsUserFlicking = true
                                        ControlCenterConfigHolder.sLastTouchTime = System.currentTimeMillis()
                                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                        ControlCenterConfigHolder.sLastTouchTime = System.currentTimeMillis()
                                        ControlCenterConfigHolder.sIsUserFlicking = false
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                    if (m.name == "getVelocityY") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val v = chain.proceed() as Float
                            ControlCenterConfigHolder.sCurrentVelocityY = v
                            v
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 3. Hook MainPanelModeController for EDIT vs NORMAL mode state changes & persist on exit
            try {
                val modeControllerClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelModeController", false, classLoader)
                for (m in modeControllerClass.declaredMethods) {
                    if (m.name == "set_mode" || m.name == "setMode" || m.name == "update_mode" || m.name == "updateMode") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] != null) {
                                    val modeName = args[0].toString()
                                    val isEdit = "EDIT" == modeName
                                    if (ControlCenterConfigHolder.sIsEditMode && !isEdit) {
                                        val modeCtrl = chain.thisObject
                                        val ctx = try {
                                            val getCtxMethod = modeCtrl.javaClass.getMethod("getContext")
                                            getCtxMethod.invoke(modeCtrl) as? Context
                                        } catch (t: Throwable) { null }
                                        ControlCenterConfigHolder.onExitEditMode(context = ctx)
                                    } else if (isEdit) {
                                        val wasEdit = ControlCenterConfigHolder.sIsEditMode
                                        ControlCenterConfigHolder.sIsEditMode = true
                                        if (!wasEdit) {
                                            val modeCtrl = chain.thisObject
                                            val ctx = try {
                                                val getCtxMethod = modeCtrl.javaClass.getMethod("getContext")
                                                getCtxMethod.invoke(modeCtrl) as? Context
                                            } catch (t: Throwable) { null }
                                            val modeView = try {
                                                val getViewM = modeCtrl.javaClass.getMethod("getView")
                                                getViewM.invoke(modeCtrl) as? View
                                            } catch (t: Throwable) { null }
                                            val rvObj = try {
                                                val f = modeCtrl.javaClass.getDeclaredField("rightMainPanel")
                                                f.isAccessible = true
                                                f.get(modeCtrl) as? ViewGroup
                                            } catch (t: Throwable) { null } ?: try {
                                                val f = modeCtrl.javaClass.getDeclaredField("leftMainPanel")
                                                f.isAccessible = true
                                                f.get(modeCtrl) as? ViewGroup
                                            } catch (t: Throwable) { null }
                                            if (rvObj != null) {
                                                ControlCenterConfigHolder.sRecyclerView = java.lang.ref.WeakReference(rvObj)
                                            }
                                            ControlCenterConfigHolder.onEnterEditMode(rootView = modeView ?: rvObj, context = ctx)
                                        }
                                    } else {
                                        ControlCenterConfigHolder.sIsEditMode = false
                                    }
                                    if (!ControlCenterConfigHolder.sIsEditMode) {
                                        try {
                                            val modeCtrl = chain.thisObject
                                            val getCtxMethod = modeCtrl.javaClass.getMethod("getContext")
                                            val ctx = getCtxMethod.invoke(modeCtrl) as? Context
                                            ControlCenterConfigHolder.savePriorities(ctx)
                                            sMediaPlayerViewHolder?.get()?.let { applyMediaPlayerLayout(it) }
                                        } catch (t: Throwable) {}
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 3b. Hook ControlCenterWindowViewImpl for Lifecycle Transitions
            try {
                val windowViewImplClass = Class.forName("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", false, classLoader)
                for (m in windowViewImplClass.declaredMethods) {
                    if (m.name == "notifyLifecycleStateChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 2) {
                                    val stateObj = args[1]
                                    if (stateObj != null) {
                                        val stateStr = stateObj.toString()
                                        if ("STARTED" == stateStr) {
                                            ControlCenterConfigHolder.sPendingOrientationRebind = false
                                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                                        } else if ("CREATED" == stateStr || "STOPPED" == stateStr || "DESTROYED" == stateStr) {
                                            try {
                                                IosTileGroupFolder.dismissActiveOverlay(chain.thisObject as? View, animated = false)
                                            } catch (t: Throwable) {}
                                            if (ControlCenterConfigHolder.sIsEditMode) {
                                                val windowView = chain.thisObject as? View
                                                ControlCenterConfigHolder.onExitEditMode(windowView, windowView?.context)
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                    if (m.name == "onVisibilityAggregated" || m.name == "onWindowVisibilityChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val firstArg = chain.args.getOrNull(0)
                                val isVisible = when (firstArg) {
                                    is Boolean -> firstArg
                                    is Int -> firstArg == View.VISIBLE
                                    else -> true
                                }
                                if (!isVisible) {
                                    IosTileGroupFolder.dismissActiveOverlay(chain.thisObject as? View, animated = false)
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 4. Hook ControlCenterExpandController for Velocity and Hide Handlers
            try {
                val expandControllerClass = Class.forName("miui.systemui.controlcenter.windowview.ControlCenterExpandController", false, classLoader)
                for (m in expandControllerClass.declaredMethods) {
                    if (m.name == "showPanel" || m.name == "onStart") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val v = (chain.thisObject.javaClass.getMethod("getView").invoke(chain.thisObject) as? View)
                                IosTileGroupFolder.dismissActiveOverlay(v, animated = false)
                            } catch (t: Throwable) {
                                try { IosTileGroupFolder.dismissActiveOverlay(animated = false) } catch (t2: Throwable) {}
                            }
                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                            chain.proceed()
                        }
                    }
                    if (m.name == "hidePanel" || m.name == "onStop") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val v = (chain.thisObject.javaClass.getMethod("getView").invoke(chain.thisObject) as? View)
                                IosTileGroupFolder.dismissActiveOverlay(v, animated = false)
                            } catch (t: Throwable) {
                                try { IosTileGroupFolder.dismissActiveOverlay(animated = false) } catch (t2: Throwable) {}
                            }
                            if (System.currentTimeMillis() - ControlCenterConfigHolder.sLastTouchTime > 350) {
                                ControlCenterConfigHolder.sIsUserFlicking = false
                                ControlCenterConfigHolder.sCurrentVelocityY = 0.0f
                            }
                            chain.proceed()
                        }
                    }
                    if (m.name == "notifyExpansionChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val exp = chain.args.getOrNull(0) as? Float
                                if (exp != null && exp < 0.95f) {
                                    val v = (chain.thisObject.javaClass.getMethod("getView").invoke(chain.thisObject) as? View)
                                    IosTileGroupFolder.dismissActiveOverlay(v, animated = false)
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                    if (m.name == "notifyAppearanceChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val appeared = chain.args.getOrNull(0) as? Boolean
                                if (appeared == false) {
                                    val v = (chain.thisObject.javaClass.getMethod("getView").invoke(chain.thisObject) as? View)
                                    IosTileGroupFolder.dismissActiveOverlay(v, animated = false)
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                    if (m.name == "onExpandFinish") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] is Float) {
                                    ControlCenterConfigHolder.sCurrentVelocityY = args[0] as Float
                                    ControlCenterConfigHolder.sLastTouchTime = System.currentTimeMillis()
                                    ControlCenterConfigHolder.sIsUserFlicking = true
                                }
                                val appearanceM = chain.thisObject.javaClass.getMethod("getAppearance")
                                val isAppeared = appearanceM.invoke(chain.thisObject) as? Boolean ?: true
                                if (!isAppeared) {
                                    IosTileGroupFolder.dismissActiveOverlay(animated = false)
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 5. Hook LowEndItemHolderAnimator for Eased-Out Overshoot Release Bounce
            try {
                val lowEndAnimatorClass = Class.forName("miui.systemui.controlcenter.panel.main.anim.LowEndItemHolderAnimator", false, classLoader)
                val easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader)
                val getStyleMethod = easeManagerClass.getMethod("getStyle", Int::class.javaPrimitiveType, FloatArray::class.java)

                for (m in lowEndAnimatorClass.declaredMethods) {
                    if (m.name == "expandedStretch") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 4 && (args[3] as Boolean)) {
                                    val bounceEase = getStyleMethod.invoke(null, -2, floatArrayOf(0.88f, 0.38f))
                                    val releaseField = lowEndAnimatorClass.getDeclaredField("releaseTransYEase")
                                    releaseField.isAccessible = true
                                    releaseField.set(null, bounceEase)
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 6. Hook SpreadRowsAnimator for Eased-Out Overshoot Release Bounce
            try {
                val spreadAnimatorClass = Class.forName("miui.systemui.controlcenter.panel.main.anim.SpreadRowsAnimator", false, classLoader)
                val easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader)
                val getStyleMethod = easeManagerClass.getMethod("getStyle", Int::class.javaPrimitiveType, FloatArray::class.java)

                for (m in spreadAnimatorClass.declaredMethods) {
                    if (m.name == "changeExpand") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 4 && (args[3] as Boolean)) {
                                    val bounceEase = getStyleMethod.invoke(null, -2, floatArrayOf(0.88f, 0.38f))
                                    val rowsField = spreadAnimatorClass.getDeclaredField("rows")
                                    rowsField.isAccessible = true
                                    val rows = rowsField.get(chain.thisObject) as? Array<*>
                                    if (rows != null) {
                                        for (row in rows) {
                                            if (row != null) {
                                                val releaseEaseField = row.javaClass.getDeclaredField("releaseEase")
                                                releaseEaseField.isAccessible = true
                                                releaseEaseField.set(row, bounceEase)
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 7. Hook MainPanelItemViewHolder (Center-Bounce, Spring Easing & iOS 26 Jelly)
            try {
                val holderClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader)
                val easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader)
                val getStyleMethod = easeManagerClass.getMethod("getStyle", Int::class.javaPrimitiveType, FloatArray::class.java)

                for (m in holderClass.declaredMethods) {
                    if (m.name == "updateMode") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val holder = chain.thisObject
                                val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                                if (itemView != null) {
                                    val rotateBtn = itemView.findViewById<View>(0x7f099992)
                                    if (rotateBtn != null) {
                                        val group = itemView.getTag(0x7f099994) as? TileGroup
                                            ?: ControlCenterConfigHolder.isGroupAnchor(itemView.getTag(0x7f099990) as? String)
                                        val isIosCluster = (group != null && group.isIosFolderCluster())
                                        val rawArgs = chain.args
                                        val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                        var isEdit = false
                                        if (args != null) {
                                            for (arg in args) {
                                                if (arg != null && "EDIT" == arg.toString()) {
                                                    isEdit = true
                                                    break
                                                }
                                            }
                                        }
                                        val canRotate = ControlCenterConfigHolder.config.isModularEditActive && isEdit && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode && !isIosCluster
                                        rotateBtn.visibility = if (canRotate) View.VISIBLE else View.GONE
                                        if (canRotate) rotateBtn.bringToFront()
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }

                    if (m.name == "updateBlendBlur") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val holder = chain.thisObject
                            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                            if (itemView != null && (itemView.findViewById<View>(0x7f099995) != null || itemView.getTag(0x7f099994) != null)) {
                                // Group container: preserve normal mode blur / container styling and prevent applying CC_TILE_DEFAULT_BLEND_COLORS
                                ControlCenterGrouping.applyGroupContainerStyle(itemView, classLoader)
                                null
                            } else {
                                chain.proceed()
                            }
                        }
                    }

                    if (m.name == "changeVisible" && m.parameterTypes.size == 3) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (sInChangeVisible.get() == true) return@intercept result
                            try {
                                sInChangeVisible.set(true)
                                val holder = chain.thisObject
                                val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                                if (itemView != null) {
                                    val subHolders = itemView.getTag(0x7f099993) as? Array<*>
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    val z2 = (args?.getOrNull(0) as? Boolean) ?: true
                                    val z3 = (args?.getOrNull(1) as? Boolean) ?: true
                                    val z4 = (args?.getOrNull(2) as? Boolean) ?: false

                                    if (subHolders != null) {
                                        for (subHolder in subHolders) {
                                            if (subHolder != null) {
                                                try {
                                                    val cvM = subHolder.javaClass.getMethod(
                                                        "changeVisible",
                                                        Boolean::class.javaPrimitiveType,
                                                        Boolean::class.javaPrimitiveType,
                                                        Boolean::class.javaPrimitiveType
                                                    )
                                                    cvM.isAccessible = true
                                                    cvM.invoke(subHolder, z2, z3, z4)
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                    }

                                    if (z2) {
                                        val container = itemView.findViewById<ViewGroup>(0x7f099995)
                                        if (container != null) {
                                            container.visibility = View.VISIBLE
                                            container.alpha = 1.0f
                                            for (i in 0 until container.childCount) {
                                                val c = container.getChildAt(i)
                                                if (c != null) {
                                                    c.visibility = View.VISIBLE
                                                    c.alpha = 1.0f
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {} finally {
                                sInChangeVisible.set(false)
                            }
                            result
                        }
                    }

                    if (m.name == "prepareLocation") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val holder = chain.thisObject
                            if (holder != null && ControlCenterGrouping.sGroupChildHolders.contains(holder)) {
                                return@intercept null
                            }
                            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                            if (itemView != null && ControlCenterGrouping.isChildOfGroup(itemView)) {
                                if (holder != null) ControlCenterGrouping.sGroupChildHolders.add(holder)
                                return@intercept null
                            }
                            val result = try { chain.proceed() } catch (t: Throwable) { null }
                            if (ControlCenterConfigHolder.config.isLowEndAnim() || !ControlCenterConfigHolder.config.isAnimActive) return@intercept result

                            try {
                                var ctx: Context? = null
                                try {
                                    val getCtxMethod = sGetContextMethod ?: holder.javaClass.getMethod("getContext").apply { sGetContextMethod = this }
                                    ctx = getCtxMethod.invoke(holder) as? Context
                                } catch (t: Throwable) {
                                    try {
                                        val ivField = sItemViewField ?: holder.javaClass.getField("itemView").apply { sItemViewField = this }
                                        val iv = ivField.get(holder) as? View
                                        if (iv != null) ctx = iv.context
                                    } catch (t2: Throwable) {}
                                }
                                if (ctx == null) return@intercept result

                                val screenW = ctx.resources.displayMetrics.widthPixels.toFloat()
                                val screenH = ctx.resources.displayMetrics.heightPixels.toFloat()

                                val shrinkCenterXField = sShrinkCenterXField ?: holderClass.getDeclaredField("shrinkCenterX").apply {
                                    isAccessible = true
                                    sShrinkCenterXField = this
                                }
                                shrinkCenterXField.setFloat(holder, screenW / 2.0f)

                                val shrinkCenterYField = sShrinkCenterYField ?: holderClass.getDeclaredField("shrinkCenterY").apply {
                                    isAccessible = true
                                    sShrinkCenterYField = this
                                }
                                shrinkCenterYField.setFloat(holder, screenH / 2.0f)

                                val delayPerField = sDelayPerField ?: holderClass.getDeclaredField("delayPer").apply {
                                    isAccessible = true
                                    sDelayPerField = this
                                }
                                delayPerField.setFloat(holder, 0.0f)
                            } catch (t: Throwable) {
                                Log.e(TAG, "prepareLocation hook error", t)
                            }
                            result
                        }
                    }

                    if (m.name == "updateEases") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val holder = chain.thisObject
                            if (holder != null && ControlCenterGrouping.sGroupChildHolders.contains(holder)) {
                                return@intercept null
                            }
                            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                            if (itemView != null && ControlCenterGrouping.isChildOfGroup(itemView)) {
                                if (holder != null) ControlCenterGrouping.sGroupChildHolders.add(holder)
                                return@intercept null
                            }
                            val result = try { chain.proceed() } catch (t: Throwable) { null }
                            if (ControlCenterConfigHolder.config.isLowEndAnim() || !ControlCenterConfigHolder.config.isAnimActive) return@intercept result
                            try {
                                val holder = chain.thisObject

                                val delayPerField = sDelayPerField ?: holderClass.getDeclaredField("delayPer").apply {
                                    isAccessible = true
                                    sDelayPerField = this
                                }
                                delayPerField.setFloat(holder, 0.0f)

                                val bounceEase = getStyleMethod.invoke(null, -2, floatArrayOf(0.88f, 0.38f))
                                val releaseTransYEaseField = sReleaseTransYEaseField ?: holderClass.getDeclaredField("releaseTransYEase").apply {
                                    isAccessible = true
                                    sReleaseTransYEaseField = this
                                }
                                releaseTransYEaseField.set(holder, bounceEase)

                                if (!ControlCenterConfigHolder.config.isLowEndAnim() && ControlCenterConfigHolder.config.isAnimActive) {
                                    val showEaseField = sShowEaseField ?: holderClass.getDeclaredField("showEase").apply {
                                        isAccessible = true
                                        sShowEaseField = this
                                    }
                                    val showEase = showEaseField.get(holder)
                                    if (showEase != null) {
                                        val setFactorsMethod = sSetFactorsMethod ?: showEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply {
                                            isAccessible = true
                                            sSetFactorsMethod = this
                                        }
                                        setFactorsMethod.invoke(showEase, ControlCenterConfigHolder.config.tileShowDamping, ControlCenterConfigHolder.config.tileShowResponse)
                                    }

                                    val hideEaseField = sHideEaseField ?: holderClass.getDeclaredField("hideEase").apply {
                                        isAccessible = true
                                        sHideEaseField = this
                                    }
                                    val hideEase = hideEaseField.get(holder)
                                    if (hideEase != null) {
                                        val setFactorsMethod = sSetFactorsMethod ?: hideEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply {
                                            isAccessible = true
                                            sSetFactorsMethod = this
                                        }
                                        setFactorsMethod.invoke(hideEase, ControlCenterConfigHolder.config.tileHideDamping, ControlCenterConfigHolder.config.tileHideResponse)
                                    }

                                    val shrinkXEaseField = sShrinkXEaseField ?: holderClass.getDeclaredField("shrinkXEase").apply {
                                        isAccessible = true
                                        sShrinkXEaseField = this
                                    }
                                    val shrinkXEase = shrinkXEaseField.get(holder)
                                    if (shrinkXEase != null) {
                                        val setFactorsMethod = sSetFactorsMethod ?: shrinkXEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply {
                                            isAccessible = true
                                            sSetFactorsMethod = this
                                        }
                                        setFactorsMethod.invoke(shrinkXEase, ControlCenterConfigHolder.config.tileShrinkDamping, ControlCenterConfigHolder.config.tileShrinkResponse)
                                    }

                                    val shrinkYEaseField = sShrinkYEaseField ?: holderClass.getDeclaredField("shrinkYEase").apply {
                                        isAccessible = true
                                        sShrinkYEaseField = this
                                    }
                                    val shrinkYEase = shrinkYEaseField.get(holder)
                                    if (shrinkYEase != null) {
                                        val setFactorsMethod = sSetFactorsMethod ?: shrinkYEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply {
                                            isAccessible = true
                                            sSetFactorsMethod = this
                                        }
                                        setFactorsMethod.invoke(shrinkYEase, ControlCenterConfigHolder.config.tileShrinkDamping, ControlCenterConfigHolder.config.tileShrinkResponse)
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }

                    if (m.name == "getDraggable") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.isModularEditActive) {
                                try {
                                    val holder = chain.thisObject
                                    val modeField = holderClass.getDeclaredField("mode")
                                    modeField.isAccessible = true
                                    val mode = modeField.get(holder)
                                    if (mode != null && mode.toString() == "EDIT") {
                                        return@intercept true
                                    }
                                } catch (t: Throwable) {}
                            }
                            chain.proceed()
                        }
                    }


                    if (m.name == "onFrameCallback") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val holder = chain.thisObject
                            if (holder != null && ControlCenterGrouping.sGroupChildHolders.contains(holder)) {
                                return@intercept null
                            }
                            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
                            if (itemView != null && ControlCenterGrouping.isChildOfGroup(itemView)) {
                                if (holder != null) ControlCenterGrouping.sGroupChildHolders.add(holder)
                                return@intercept null
                            }
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.isJellyActive) {
                                try {
                                    val targetView = itemView ?: (sItemViewField ?: holderClass.getField("itemView").apply {
                                        sItemViewField = this
                                    }).get(holder) as? View ?: return@intercept result

                                    val expandScaleRatioField = sExpandScaleRatioField ?: holderClass.getDeclaredField("expandScaleRatio").apply {
                                        isAccessible = true
                                        sExpandScaleRatioField = this
                                    }
                                    val progress = expandScaleRatioField.getFloat(holder)

                                    val expandTransYField = sExpandTransYField ?: holderClass.getDeclaredField("expandTransY").apply {
                                        isAccessible = true
                                        sExpandTransYField = this
                                    }
                                    val expandTransY = expandTransYField.getFloat(holder)

                                    val spreadRowField = sSpreadRowField ?: holderClass.getDeclaredField("spreadRow").apply {
                                        isAccessible = true
                                        sSpreadRowField = this
                                    }
                                    val row = spreadRowField.getInt(holder)

                                    val viewWidth = targetView.width
                                    val viewHeight = targetView.height
                                    if (viewWidth <= 0 || viewHeight <= 0) return@intercept result

                                    val isRecentGesture = (System.currentTimeMillis() - ControlCenterConfigHolder.sLastTouchTime) < 700
                                    val isClosing = (progress < 0.99f && ControlCenterConfigHolder.sCurrentVelocityY < -100.0f)

                                    if (!ControlCenterConfigHolder.sIsUserFlicking && !isRecentGesture && Math.abs(expandTransY) < 1.0f) {
                                        targetView.scaleX = 1.0f
                                        targetView.scaleY = 1.0f
                                        return@intercept result
                                    }

                                    val absVel = Math.abs(ControlCenterConfigHolder.sCurrentVelocityY)
                                    val velFactor = if (absVel > 0.1f) Math.max(0.35f, Math.min(1.20f, absVel / 3500.0f)) else 0.70f

                                    val density = targetView.context.resources.displayMetrics.density
                                    val standardCardHeight = 110.0f * density
                                    val heightDampener = Math.max(0.42f, Math.min(1.0f, standardCardHeight / viewHeight))

                                    var progressStretchY = 0.0f
                                    var progressSquishX = 0.0f

                                    if (!isClosing && progress in 0.01f..0.99f && (ControlCenterConfigHolder.sIsUserFlicking || isRecentGesture)) {
                                        val momentum = Math.sin(progress * Math.PI).toFloat()
                                        val rowStagger = 1.0f + (row * 0.040f)
                                        progressStretchY = momentum * 0.090f * velFactor * heightDampener * rowStagger
                                        progressSquishX = progressStretchY * 0.38f
                                    } else if (isClosing && (ControlCenterConfigHolder.sIsUserFlicking || isRecentGesture)) {
                                        val momentum = Math.sin(progress * Math.PI).toFloat()
                                        progressStretchY = -momentum * 0.050f * velFactor * heightDampener
                                        progressSquishX = -progressStretchY * 0.38f
                                    }

                                    val strain = Math.max(-0.22f, Math.min(0.50f, expandTransY / 850.0f))
                                    val pullStretchY = strain * 0.32f * heightDampener
                                    val pullSquishX = strain * 0.14f * heightDampener

                                    val baseScale = Math.min(1.0f, Math.max(0.89f, 0.91f + (progress * 0.09f)))
                                    val totalScaleY = baseScale + progressStretchY + pullStretchY
                                    val totalScaleX = baseScale - progressSquishX - pullSquishX

                                    targetView.pivotX = viewWidth / 2.0f
                                    if (expandTransY > 3.0f || (!isClosing && progress > 0.05f && progress < 0.95f)) {
                                        targetView.pivotY = viewHeight * 0.10f
                                    } else if (expandTransY < -3.0f || isClosing) {
                                        targetView.pivotY = viewHeight * 0.90f
                                    } else {
                                        targetView.pivotY = viewHeight / 2.0f
                                    }

                                    targetView.scaleX = Math.max(0.78f, Math.min(1.30f, totalScaleX))
                                    targetView.scaleY = Math.max(0.78f, Math.min(1.40f, totalScaleY))
                                } catch (t: Throwable) {}
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}


            // 8. Hook Major Modules (Edit Unlock & Dynamic Priority)
            val majorModuleClassNames = arrayOf(
                "miui.systemui.controlcenter.panel.main.qs.QSCardsController",
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerController",
                "miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController",
                "miui.systemui.controlcenter.panel.main.volume.VolumeSliderController"
            )

            for (className in majorModuleClassNames) {
                try {
                    val targetClass = Class.forName(className, false, classLoader)
                    val simpleName = targetClass.simpleName

                    for (m in targetClass.declaredMethods) {
                        if (m.name == "available" && m.parameterTypes.size == 1) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                val orig = chain.proceed() as Boolean
                                val spec = when (simpleName) {
                                    "MediaPlayerController" -> "media"
                                    "BrightnessSliderController" -> "brightness"
                                    "VolumeSliderController" -> "volume"
                                    else -> null
                                }
                                if (spec != null && ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec)) {
                                    if (ControlCenterConfigHolder.sIsEditMode) {
                                        true
                                    } else {
                                        false
                                    }
                                } else if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sIsEditMode) {
                                    true
                                } else {
                                    orig
                                }
                            }
                        }

                        if (m.name == "getPriority" && m.parameterTypes.isEmpty()) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sComponentPriorities.containsKey(simpleName)) {
                                    ControlCenterConfigHolder.sComponentPriorities[simpleName]
                                } else {
                                    chain.proceed()
                                }
                            }
                        }

                        if (m.name == "getSpanSize" && m.parameterTypes.isEmpty()) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                if (!ControlCenterConfigHolder.config.isModularEditActive) {
                                    return@intercept chain.proceed()
                                }
                                when (simpleName) {
                                    "BrightnessSliderController" -> if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) ControlCenterConfigHolder.sBrightnessSliderWidth else 1
                                    "VolumeSliderController" -> if (ControlCenterConfigHolder.sVolumeSliderHorizontal) ControlCenterConfigHolder.sVolumeSliderWidth else 1
                                    "MediaPlayerController" -> {
                                        val parts = ControlCenterConfigHolder.sMediaPlayerSize.split("x")
                                        parts.firstOrNull()?.toIntOrNull()?.coerceIn(1, 4) ?: 2
                                    }
                                    else -> chain.proceed()
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }

            // Hook WordlessModeController.updateTextMode to immediately update circular card labels on toggle
            try {
                val wordlessClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.WordlessModeController", false, classLoader)
                for (m in wordlessClass.declaredMethods) {
                    if (m.name == "updateTextMode") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                True2DGridEngine.invalidateAll()
                                ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                val rv = ControlCenterConfigHolder.sRecyclerView?.get()
                                rv?.post {
                                    rv.requestLayout()
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // Hook QSCardsController.getListItems for Wi-Fi / Cell card swapping and sAllQSRecords tracking
            try {
                val cardsClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardsController", false, classLoader)
                for (m in cardsClass.declaredMethods) {
                    if (m.name == "getListItems") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val list = chain.proceed() as? List<*>
                            if (list != null) {
                                for (item in list) {
                                    if (item != null) {
                                        val spec = ControlCenterConfigHolder.getRecordSpec(item)
                                        if (spec == "wifi") ControlCenterConfigHolder.sWifiCardRecord = item
                                        if (spec == "cell") ControlCenterConfigHolder.sCellCardRecord = item
                                        if (!ControlCenterConfigHolder.sAllQSRecords.contains(item)) {
                                            ControlCenterConfigHolder.sAllQSRecords.add(item)
                                        }
                                    }
                                }
                            }
                            if (list != null && !ControlCenterConfigHolder.sIsEditMode && ControlCenterConfigHolder.sRemovedComponentSpecs.isNotEmpty()) {
                                val filtered = list.filter { item ->
                                    val spec = ControlCenterConfigHolder.getRecordSpec(item)
                                    spec == null || !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec)
                                }
                                if (filtered.isEmpty()) return@intercept emptyList<Any>()
                                if (ControlCenterConfigHolder.config.isModularEditActive && filtered.size >= 2) {
                                    val resultList = ArrayList(filtered)
                                    val first = filtered[0]
                                    val second = filtered[1]
                                    if (!ControlCenterConfigHolder.sIsWifiFirst) {
                                        resultList[0] = second
                                        resultList[1] = first
                                    } else {
                                        resultList[0] = first
                                        resultList[1] = second
                                    }
                                    return@intercept resultList
                                }
                                return@intercept filtered
                            }
                            if (ControlCenterConfigHolder.config.isModularEditActive && list != null && list.size >= 2) {
                                val resultList = ArrayList(list)
                                val first = list[0]
                                val second = list[1]
                                if (!ControlCenterConfigHolder.sIsWifiFirst) {
                                    resultList[0] = second
                                    resultList[1] = first
                                } else {
                                    resultList[0] = first
                                    resultList[1] = second
                                }
                                resultList
                            } else {
                                list
                            }
                        }
                    }

                    if (m.name == "createViewHolder" && m.parameterTypes.size == 2 && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            if (ControlCenterConfigHolder.config.isModularEditActive && args != null && args.size >= 2) {
                                val parent = args[0] as ViewGroup
                                val vt = args[1] as Int
                                if (vt == 8399) {
                                    val holder = ControlCenterFullUnlock.createLandscapeDividerViewHolder(parent, classLoader)
                                    if (holder != null) return@intercept holder
                                } else if (vt == 8453) {
                                    val qsListCtrl = ControlCenterConfigHolder.sQSListController
                                    if (qsListCtrl != null) {
                                        try {
                                            val mCreate = qsListCtrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                            val h = mCreate.invoke(qsListCtrl, parent, 8453)
                                            if (h != null) return@intercept h
                                        } catch (t: Throwable) {}
                                    }
                                } else if (vt == 2277) {
                                    val holder = ControlCenterGrouping.createVerticalSingleCardViewHolder(parent, ControlCenterConfigHolder.sQSListController, classLoader)
                                    if (holder != null) return@intercept holder
                                } else if (vt == 2278) {
                                    val holder = ControlCenterGrouping.createLargeCardViewHolder(parent, ControlCenterConfigHolder.sQSListController, classLoader)
                                    if (holder != null) return@intercept holder
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // Hook Smart Home / Google Home
            val homeEntryClasses = arrayOf(
                "miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryController",
                "miui.systemui.controlcenter.panel.main.devicecontrol.DeviceControlsEntryController"
            )
            for (className in homeEntryClasses) {
                try {
                    val targetClass = Class.forName(className, false, classLoader)
                    for (m in targetClass.declaredMethods) {
                        if (m.name == "available" && m.parameterTypes.size == 1) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                val controller = chain.thisObject
                                var settingsAvail = false
                                try {
                                    val f = controller.javaClass.getDeclaredField("availableOfSettings")
                                    f.isAccessible = true
                                    settingsAvail = f.getBoolean(controller)
                                } catch (t: Throwable) {}

                                if (!settingsAvail) return@intercept false

                                try {
                                    if (controller.javaClass.name.contains("DeviceCenter")) {
                                        val fMiLink = controller.javaClass.getDeclaredField("miLinkController")
                                        fMiLink.isAccessible = true
                                        val miLink = fMiLink.get(controller)
                                        if (miLink != null) {
                                            val mAvail = miLink.javaClass.getMethod("getMiLinkAvailable")
                                            if (!(mAvail.invoke(miLink) as Boolean)) return@intercept false
                                        }
                                    } else if (controller.javaClass.name.contains("DeviceControls")) {
                                        val fInfo = controller.javaClass.getDeclaredField("currentDCEntryInfo")
                                        fInfo.isAccessible = true
                                        val info = fInfo.get(controller)
                                        if (info != null) {
                                            val mAvail = info.javaClass.getMethod("getAvailable")
                                            if (!(mAvail.invoke(info) as Boolean)) return@intercept false
                                        }
                                    }
                                } catch (t: Throwable) {}

                                if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sIsEditMode) {
                                    true
                                } else {
                                    chain.proceed()
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }

            // 9. Hook WordlessModeController: Priority 5 & compact margin
            try {
                val wordlessClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.WordlessModeController", false, classLoader)
                for (m in wordlessClass.declaredMethods) {
                    if (m.name == "getPriority") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.isModularEditActive) 5 else chain.proceed()
                        }
                    }
                    if (m.name == "onBindViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.isModularEditActive) {
                                try {
                                    val wordlessCtrl = chain.thisObject
                                    val getHolderMethod = wordlessClass.getMethod("getHolder")
                                    val holder = getHolderMethod.invoke(wordlessCtrl)
                                    if (holder != null) {
                                        val ivField = holder.javaClass.getField("itemView")
                                        val itemView = ivField.get(holder) as? View
                                        if (itemView != null) {
                                            val lp = itemView.layoutParams
                                            if (lp is ViewGroup.MarginLayoutParams) {
                                                lp.topMargin = 0
                                                itemView.layoutParams = lp
                                            }
                                            fun restoreWordlessViews(v: View) {
                                                v.visibility = View.VISIBLE
                                                v.alpha = 1.0f
                                                if (v is ViewGroup) {
                                                    for (ci in 0 until v.childCount) {
                                                        restoreWordlessViews(v.getChildAt(ci))
                                                    }
                                                }
                                            }
                                            restoreWordlessViews(itemView)
                                        }
                                    }
                                } catch (t: Throwable) {}
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 10. Hook QSListController
            try {
                val qsListClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSListController", false, classLoader)
                for (m in qsListClass.declaredMethods) {
                    if (m.name == "getPriority") {
                        m.isAccessible = true
                        module.hook(m).intercept { 100 }
                    }
                    if (m.name == "onStart") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                            result
                        }
                    }
                    if (m.name == "onStop") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                for (r in ControlCenterConfigHolder.sAllQSRecords) {
                                    if (r != null) {
                                        val spec = ControlCenterConfigHolder.getRecordSpec(r)
                                        if (ControlCenterConfigHolder.isGroupChild(spec)) {
                                            try {
                                                val setLisM = r.javaClass.getMethod("setListening", Boolean::class.javaPrimitiveType)
                                                setLisM.invoke(r, false)
                                            } catch (t: Throwable) {}
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                    if (m.name == "setListening") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && java.lang.Boolean.TRUE == args[0]) {
                                    for (r in ControlCenterConfigHolder.sAllQSRecords) {
                                        if (r != null) {
                                            val spec = ControlCenterConfigHolder.getRecordSpec(r)
                                            if (ControlCenterConfigHolder.isGroupChild(spec)) {
                                                try {
                                                    val getTileM = r.javaClass.getMethod("getTile")
                                                    val tileObj = getTileM.invoke(r)
                                                    if (tileObj != null) {
                                                        val setLisM = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                                        setLisM.invoke(tileObj, r, true)
                                                        val refM = tileObj.javaClass.getMethod("refreshState")
                                                        refM.invoke(tileObj)
                                                    }
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                    if (m.name == "distributeTiles") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                ControlCenterConfigHolder.sQSListController = chain.thisObject
                                val qsListCtrl = chain.thisObject
                                val addedField = qsListClass.getDeclaredField("addedTiles")
                                addedField.isAccessible = true
                                val added = addedField.get(qsListCtrl) as? MutableList<*>
                                if (added != null && added.size > 1) {
                                    val seen = HashSet<String>()
                                    val it = added.iterator()
                                    while (it.hasNext()) {
                                        val rec = it.next()
                                        if (rec != null) {
                                            val sp = ControlCenterConfigHolder.getRecordSpec(rec)
                                            if (sp != null) {
                                                if (seen.contains(sp)) {
                                                    it.remove()
                                                } else {
                                                    seen.add(sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }
                    if (m.name == "getListItems") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            ControlCenterConfigHolder.sQSListController = chain.thisObject
                            val originalList = chain.proceed() as? List<*>
                            if (originalList != null) {
                                val cardRecords = ControlCenterConfigHolder.sAllQSRecords.filter { r ->
                                    val sp = ControlCenterConfigHolder.getRecordSpec(r)
                                    sp == "wifi" || sp == "cell"
                                }
                                ControlCenterConfigHolder.sAllQSRecords.clear()
                                ControlCenterConfigHolder.sAllQSRecords.addAll(cardRecords)
                                for (item in originalList) {
                                    if (item != null && !ControlCenterConfigHolder.sAllQSRecords.contains(item)) {
                                        ControlCenterConfigHolder.sAllQSRecords.add(item)
                                    }
                                }
                            }
                            if (originalList == null || ControlCenterConfigHolder.sTileGroups.isEmpty()) return@intercept originalList
                            try {
                                val filtered = ArrayList<Any>()
                                for (item in originalList) {
                                    if (item != null) {
                                        val spec = ControlCenterConfigHolder.getRecordSpec(item)
                                        if (spec != null && ControlCenterConfigHolder.isGroupChild(spec)) {
                                            continue
                                        }
                                        filtered.add(item)
                                    }
                                }
                                filtered
                            } catch (t: Throwable) {
                                originalList
                            }
                        }
                    }
                    if (m.name == "createViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (result == null) {
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    if (args != null && args.size >= 2) {
                                        val viewType = args[1] as Int
                                        val parent = args[0] as ViewGroup
                                        val qsListCtrl = chain.thisObject

                                        if (viewType == 8399) {
                                            val holder = ControlCenterFullUnlock.createLandscapeDividerViewHolder(parent, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2274) {
                                            val holder = ControlCenterGrouping.createGroupedCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2275) {
                                            val holder = ControlCenterGrouping.createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, false)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2276) {
                                            val holder = ControlCenterGrouping.createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, true)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2281) {
                                            val holder = ControlCenterGrouping.createGroupedExpandedViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2282) {
                                            val holder = ControlCenterGrouping.createGroupedVerticalCardsViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2283) {
                                            val holder = ControlCenterGrouping.createGroupedMixedExpCircViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2284) {
                                            val holder = ControlCenterGrouping.createGroupedMixedVertCircViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType in 2285..2290) {
                                            val holder = ControlCenterGrouping.createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, viewType - 2284)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2273) {
                                            val holder = ControlCenterGrouping.createCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2277) {
                                            val holder = ControlCenterGrouping.createVerticalSingleCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2278) {
                                            val holder = ControlCenterGrouping.createLargeCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 3484336 || viewType == 63342) {
                                            val ctrl = ControlCenterConfigHolder.sMediaPlayerController
                                            if (ctrl != null) {
                                                try {
                                                    val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                    val h = m.invoke(ctrl, parent, viewType)
                                                    if (h != null) return@intercept h
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                        if (viewType == 3484338 || viewType == 274442) {
                                            val ctrl = ControlCenterConfigHolder.sBrightnessSliderController
                                            if (ctrl != null) {
                                                try {
                                                    val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                    val h = m.invoke(ctrl, parent, viewType)
                                                    if (h != null) return@intercept h
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                        if (viewType == 3484339 || viewType == 865269) {
                                            val ctrl = ControlCenterConfigHolder.sVolumeSliderController
                                            if (ctrl != null) {
                                                try {
                                                    val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                    val h = m.invoke(ctrl, parent, viewType)
                                                    if (h != null) return@intercept h
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "QSListController.createViewHolder inflation error", t)
                                }
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 10b. Hook MainPanelAdapter.onCreateViewHolder
            try {
                val mainAdapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader)
                for (m in mainAdapterClass.declaredMethods) {
                    if (m.name == "onCreateViewHolder" && m.parameterTypes.size == 2 && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            if (args != null && args.size >= 2) {
                                val parent = args[0] as ViewGroup
                                val vt = args[1] as Int
                                if (vt == 8399) {
                                    val holder = ControlCenterFullUnlock.createLandscapeDividerViewHolder(parent, classLoader)
                                    if (holder != null) return@intercept holder
                                }
                                if (vt == 2274 || vt == 2275 || vt == 2276 || vt in 2281..2284 || vt in 2285..2290 || vt == 2273 || vt == 2277 || vt == 2278 ||
                                    vt == 3484336 || vt == 63342 || vt == 3484338 || vt == 274442 || vt == 3484339 || vt == 865269) {
                                    try {
                                        val adapterObj = chain.thisObject
                                        ControlCenterConfigHolder.sMainPanelAdapter = adapterObj
                                        var qsListCtrl: Any? = null
                                        try {
                                            val qsListField = mainAdapterClass.getDeclaredField("qsListController")
                                            qsListField.isAccessible = true
                                            qsListCtrl = qsListField.get(adapterObj)
                                        } catch (t: Throwable) {}
                                        if (qsListCtrl == null) qsListCtrl = ControlCenterConfigHolder.sQSListController

                                        if (vt == 2274) {
                                            val holder = ControlCenterGrouping.createGroupedCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2275) {
                                            val holder = ControlCenterGrouping.createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, false)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2276) {
                                            val holder = ControlCenterGrouping.createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, true)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2281) {
                                            val holder = ControlCenterGrouping.createGroupedExpandedViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2282) {
                                            val holder = ControlCenterGrouping.createGroupedVerticalCardsViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2283) {
                                            val holder = ControlCenterGrouping.createGroupedMixedExpCircViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2284) {
                                            val holder = ControlCenterGrouping.createGroupedMixedVertCircViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt in 2285..2290) {
                                            val holder = ControlCenterGrouping.createDynamicGroupViewHolder(parent, qsListCtrl, classLoader, vt - 2284)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2273) {
                                            val holder = ControlCenterGrouping.createCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2277) {
                                            val holder = ControlCenterGrouping.createVerticalSingleCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2278) {
                                            val holder = ControlCenterGrouping.createLargeCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 3484336 || vt == 63342) {
                                            val ctrl = ControlCenterConfigHolder.sMediaPlayerController
                                            if (ctrl != null) {
                                                val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                val h = m.invoke(ctrl, parent, vt)
                                                if (h != null) return@intercept h
                                            }
                                        } else if (vt == 3484338 || vt == 274442) {
                                            val ctrl = ControlCenterConfigHolder.sBrightnessSliderController
                                            if (ctrl != null) {
                                                val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                val h = m.invoke(ctrl, parent, vt)
                                                if (h != null) return@intercept h
                                            }
                                        } else if (vt == 3484339 || vt == 865269) {
                                            val ctrl = ControlCenterConfigHolder.sVolumeSliderController
                                            if (ctrl != null) {
                                                val m = ctrl.javaClass.getMethod("createViewHolder", ViewGroup::class.java, Int::class.javaPrimitiveType)
                                                val h = m.invoke(ctrl, parent, vt)
                                                if (h != null) return@intercept h
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "MainPanelAdapter.onCreateViewHolder $vt intercept error", t)
                                        val fallbackHolder = ControlCenterGrouping.createCardViewHolder(parent, null, classLoader)
                                        if (fallbackHolder != null) return@intercept fallbackHolder
                                    }
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 11. Hook MainPanelContentDistributor
            try {
                val distributorClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", false, classLoader)
                for (m in distributorClass.declaredMethods) {
                    if (m.name == "onConfigurationChanged") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            ControlCenterConfigHolder.sPendingOrientationRebind = true
                            chain.proceed()
                        }
                    }
                    if (m.name == "distributePanels") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.isModularEditActive) {
                                try {
                                    val distributor = chain.thisObject
                                    val getCtxMethod = distributorClass.getMethod("getContext")
                                    val ctx = getCtxMethod.invoke(distributor) as? Context
                                    ControlCenterConfigHolder.loadSavedPriorities(ctx)

                                    if (ctx != null) {
                                        try {
                                            val resetFilter = IntentFilter("dev.hyperios.app.RESET_CC_LAYOUT")
                                            ctx.registerReceiver(object : BroadcastReceiver() {
                                                override fun onReceive(rCtx: Context?, intent: Intent?) {
                                                    ControlCenterConfigHolder.resetLayoutToStock(rCtx)
                                                }
                                            }, resetFilter, Context.RECEIVER_EXPORTED)
                                        } catch (t: Throwable) {}
                                    }

                                    val rightPanelContentField = distributorClass.getDeclaredField("rightPanelContent")
                                    rightPanelContentField.isAccessible = true
                                    val rightList = rightPanelContentField.get(distributor) as? MutableList<Any>

                                    if (ControlCenterConfigHolder.sIsEditMode) {
                                        val getChildControllersMethod = distributorClass.getDeclaredMethod("getChildControllers")
                                        getChildControllersMethod.isAccessible = true
                                        val allChildren = getChildControllersMethod.invoke(distributor) as? List<*>

                                        if (rightList != null && allChildren != null) {
                                            for (child in allChildren) {
                                                if (child != null) {
                                                    val cName = child.javaClass.name
                                                    if (cName.contains("CompactQS") || cName.contains("CompactQSCard") || cName.contains("QSCardsController")) continue
                                                    try {
                                                        val availMethod = child.javaClass.getMethod("available", Boolean::class.javaPrimitiveType)
                                                        val avail = availMethod.invoke(child, false) as Boolean
                                                        if (avail && !rightList.contains(child)) {
                                                            rightList.add(child)
                                                        }
                                                    } catch (t: Throwable) {}
                                                }
                                            }
                                        }
                                    }

                                    try {
                                        val childControllersField = distributorClass.getDeclaredField("childControllers")
                                        childControllersField.isAccessible = true
                                        val childControllers = childControllersField.get(distributor) as? MutableList<Any>
                                        if (childControllers != null && childControllers.size > 1) {
                                            Collections.sort(childControllers) { o1, o2 ->
                                                try {
                                                    val p1 = o1.javaClass.getMethod("getPriority").invoke(o1) as Int
                                                    val p2 = o2.javaClass.getMethod("getPriority").invoke(o2) as Int
                                                    p1.compareTo(p2)
                                                } catch (t: Throwable) { 0 }
                                            }
                                        }
                                    } catch (t: Throwable) {}

                                    try {
                                        val leftPanelContentField = distributorClass.getDeclaredField("leftPanelContent")
                                        leftPanelContentField.isAccessible = true
                                        val leftList = leftPanelContentField.get(distributor) as? MutableList<Any>
                                        if (leftList != null && leftList.size > 1) {
                                            Collections.sort(leftList) { o1, o2 ->
                                                try {
                                                    val p1 = o1.javaClass.getMethod("getPriority").invoke(o1) as Int
                                                    val p2 = o2.javaClass.getMethod("getPriority").invoke(o2) as Int
                                                    p1.compareTo(p2)
                                                } catch (t: Throwable) { 0 }
                                            }
                                        }
                                    } catch (t: Throwable) {}

                                    if (rightList != null && rightList.size > 1) {
                                        Collections.sort(rightList) { o1, o2 ->
                                            try {
                                                val p1 = o1.javaClass.getMethod("getPriority").invoke(o1) as Int
                                                val p2 = o2.javaClass.getMethod("getPriority").invoke(o2) as Int
                                                p1.compareTo(p2)
                                            } catch (t: Throwable) { 0 }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "distributePanels hook error", t)
                                }
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 12. Hook QSRecord
            try {
                val qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader)
                for (m in qsRecordClass.declaredMethods) {
                    if (m.name == "getType") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val record = chain.thisObject
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String
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
                                    if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)) {
                                        return@intercept 2273
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "getSpanSize") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val record = chain.thisObject
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String
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
                                    if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)) {
                                        return@intercept 2
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "getAdded") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val record = chain.thisObject
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String
                                if ("wifi" == spec || "cell" == spec) {
                                    return@intercept !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec)
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "getRemovable") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val record = chain.thisObject
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String
                                if (spec != null) {
                                    if (ControlCenterConfigHolder.isGroupMember(spec) || ControlCenterConfigHolder.isGroupAnchor(spec) != null) {
                                        return@intercept false
                                    }
                                    return@intercept true
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "setListening" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val record = chain.thisObject
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String
                                val group = ControlCenterConfigHolder.isGroupAnchor(spec)
                                if (group != null) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    val listening = args != null && args.isNotEmpty() && (args[0] as Boolean)
                                    var addedList: List<*>? = null
                                    if (ControlCenterConfigHolder.sQSListController != null) {
                                        try {
                                            val getAddedMethod = ControlCenterConfigHolder.sQSListController!!.javaClass.getMethod("getAddedTiles")
                                            addedList = getAddedMethod.invoke(ControlCenterConfigHolder.sQSListController) as? List<*>
                                        } catch (t: Throwable) {}
                                    }
                                    if (addedList != null) {
                                        for (r in addedList) {
                                            if (r != null) {
                                                try {
                                                    val sF = r.javaClass.getDeclaredField("spec")
                                                    sF.isAccessible = true
                                                    val s = sF.get(r) as? String
                                                    if (group.specs.contains(s)) {
                                                        val getTileM = r.javaClass.getMethod("getTile")
                                                        val tileObj = getTileM.invoke(r)
                                                        if (tileObj != null) {
                                                            val setLisM = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                                            setLisM.invoke(tileObj, r, listening)
                                                            if (listening) {
                                                                val addCb = r.javaClass.getMethod("addCallback")
                                                                addCb.invoke(r)
                                                                val refM = tileObj.javaClass.getMethod("refreshState")
                                                                refM.invoke(tileObj)
                                                            }
                                                        }
                                                    }
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 13. Hook QSController.removeTile & addTile
            try {
                val qsControllerClass = Class.forName("miui.systemui.controlcenter.qs.QSController", false, classLoader)
                for (m in qsControllerClass.declaredMethods) {
                    if (m.name == "addTile") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] != null) {
                                    val record = args[0]!!
                                    val specField = record.javaClass.getDeclaredField("spec")
                                    specField.isAccessible = true
                                    val spec = specField.get(record) as? String
                                    if ("wifi" == spec || "cell" == spec) {
                                        ControlCenterConfigHolder.sRemovedComponentSpecs.remove(spec)
                                        ControlCenterFullUnlock.restoreComponentSlotOrder(spec)
                                        val ctxField = try { record.javaClass.getDeclaredField("context") } catch (e: Throwable) { null }
                                        ctxField?.isAccessible = true
                                        val ctx = (ctxField?.get(record) as? Context) ?: ControlCenterConfigHolder.sLastKnownContext
                                        if (ctx != null) {
                                            ControlCenterConfigHolder.savePriorities(ctx)
                                            ControlCenterConfigHolder.refreshAdapter(null, ctx, isStructuralChange = true)
                                            ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                        }
                                        return@intercept null
                                    }
                                }
                            } catch (t: Throwable) {}
                            chain.proceed()
                        }
                    }

                    if (m.name == "removeTile") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] != null) {
                                    val record = args[0]!!
                                    val specField = record.javaClass.getDeclaredField("spec")
                                    specField.isAccessible = true
                                    val spec = specField.get(record) as? String

                                    val t = Throwable()
                                    for (elem in t.stackTrace) {
                                        if (elem.className.contains("clickAction") && !elem.className.contains("markClickAction")) {
                                            try {
                                                val ctxField = record.javaClass.getDeclaredField("context")
                                                ctxField.isAccessible = true
                                                val ctx = ctxField.get(record) as? Context

                                                var tileView: View? = null
                                                try {
                                                    val getTileViewMethod = record.javaClass.getMethod("getTileView")
                                                    val tileViewObj = getTileViewMethod.invoke(record)
                                                    if (tileViewObj is View) tileView = tileViewObj
                                                } catch (t2: Throwable) {}

                                                if (spec != null && ctx != null) {
                                                    if (ControlCenterConfigHolder.sIsGroupSelectMode) {
                                                        if (ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec)) {
                                                            ControlCenterConfigHolder.sSelectedTileSpecs.remove(spec)
                                                        } else {
                                                            val currentArea = ControlCenterConfigHolder.sSelectedTileSpecs.sumOf { ControlCenterConfigHolder.getTileArea(it) }
                                                            val tileArea = ControlCenterConfigHolder.getTileArea(spec)
                                                            if (currentArea + tileArea <= 12) {
                                                                ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                            } else {
                                                                tileView?.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                                                                return@intercept null
                                                            }
                                                        }
                                                        tileView?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                        ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                                        return@intercept null
                                                    }
                                                    if (ControlCenterConfigHolder.sIsDisassembleMode) {
                                                        val g = ControlCenterConfigHolder.isGroupAnchor(spec)
                                                        if (g != null) {
                                                            if (ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(g.id)) {
                                                                ControlCenterConfigHolder.sSelectedDisassembleGroups.remove(g.id)
                                                            } else {
                                                                ControlCenterConfigHolder.sSelectedDisassembleGroups.add(g.id)
                                                            }
                                                            ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                                        }
                                                        return@intercept null
                                                    }
                                                    ControlCenterConfigHolder.toggleTileSize(ctx, spec, tileView)
                                                }
                                            } catch (t3: Throwable) {
                                                Log.e(TAG, "removeTile clickAction handling error", t3)
                                            }
                                            return@intercept null
                                        }
                                    }

                                    if ("wifi" == spec || "cell" == spec) {
                                        ControlCenterConfigHolder.sRemovedComponentSpecs.add(spec)
                                        val ctxField = try { record.javaClass.getDeclaredField("context") } catch (e: Throwable) { null }
                                        ctxField?.isAccessible = true
                                        val ctx = (ctxField?.get(record) as? Context) ?: ControlCenterConfigHolder.sLastKnownContext
                                        if (ctx != null) {
                                            ControlCenterConfigHolder.savePriorities(ctx)
                                            ControlCenterConfigHolder.refreshAdapter(null, ctx, isStructuralChange = true)
                                            ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                        }
                                        return@intercept null
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "removeTile intercept error", t)
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 14. Inject Resize Handle Badge & Grouped Card Binding on QSRecord.onBindViewHolder
            try {
                val qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader)
                for (m in qsRecordClass.declaredMethods) {
                    if (m.name == "onBindViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val record = chain.thisObject
                            if (ControlCenterConfigHolder.sBindingRecords.get().contains(record)) {
                                return@intercept chain.proceed()
                            }
                            val result = chain.proceed()
                            try {
                                ControlCenterConfigHolder.sBindingRecords.get().add(record)
                                val specField = qsRecordClass.getDeclaredField("spec")
                                specField.isAccessible = true
                                val spec = specField.get(record) as? String

                                val modeField = qsRecordClass.getDeclaredField("mode")
                                modeField.isAccessible = true
                                val mode = modeField.get(record)
                                val isEdit = (mode != null && "EDIT" == mode.toString()) || ControlCenterConfigHolder.sIsEditMode

                                val getTileViewMethod = qsRecordClass.getMethod("getTileView")
                                val tileViewObj = getTileViewMethod.invoke(record)
                                val holderObj = try {
                                    record.javaClass.getMethod("getHolder").invoke(record)
                                } catch (t: Throwable) { null } ?: chain.args.getOrNull(0)
                                val holderView = if (holderObj != null) {
                                    try {
                                        val f = holderObj.javaClass.getField("itemView")
                                        f.get(holderObj) as? View
                                    } catch (t: Throwable) {
                                        try {
                                            val f = holderObj.javaClass.getDeclaredField("itemView")
                                            f.isAccessible = true
                                            f.get(holderObj) as? View
                                        } catch (t2: Throwable) { null }
                                    }
                                } else null
                                val itemView = holderView ?: (tileViewObj as? View)
                                if (itemView is View) {
                                    if (ControlCenterConfigHolder.sRecyclerView?.get() == null) {
                                        var p = itemView.parent
                                        while (p != null) {
                                            if (p is ViewGroup && p.javaClass.name.contains("RecyclerView")) {
                                                ControlCenterConfigHolder.sRecyclerView = java.lang.ref.WeakReference(p as View)
                                                break
                                            }
                                            p = p.parent
                                        }
                                    }
                                    val ctx = itemView.context
                                    val density = ctx.resources.displayMetrics.density

                                    if (spec != null) {
                                        itemView.setTag(0x7f099990, spec)
                                    }

                                    if (spec == "wifi" || spec == "cell") {
                                        val isCardAdded = !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec)
                                        try {
                                            val setAddedM = record.javaClass.getMethod("setAdded", Boolean::class.javaPrimitiveType)
                                            setAddedM.invoke(record, isCardAdded)
                                        } catch (t: Throwable) {
                                            try {
                                                val addedF = record.javaClass.getDeclaredField("added")
                                                addedF.isAccessible = true
                                                addedF.setBoolean(record, isCardAdded)
                                            } catch (t2: Throwable) {}
                                        }
                                        if (tileViewObj != null) {
                                            try {
                                                val updateAddedM = tileViewObj.javaClass.getMethod("updateAdded", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                updateAddedM.invoke(tileViewObj, isCardAdded, false)
                                            } catch (t: Throwable) {
                                                try {
                                                    val setAddedM = tileViewObj.javaClass.getDeclaredMethod("setAdded", Boolean::class.javaPrimitiveType)
                                                    setAddedM.isAccessible = true
                                                    setAddedM.invoke(tileViewObj, isCardAdded)
                                                } catch (t2: Throwable) {}
                                            }
                                        }
                                    }

                                    val isSubTile = ControlCenterConfigHolder.isGroupChild(spec) || isChildOfContainer(itemView) || itemView.getTag(0x7f09999c) == true || itemView.getTag(0x7f09999b) == true
                                    if (isSubTile) {
                                        hideAllCustomBadges(itemView)
                                        if (isEdit) {
                                            itemView.isClickable = false
                                            itemView.isLongClickable = false
                                            itemView.isFocusable = false
                                        } else {
                                            itemView.isClickable = true
                                            itemView.isLongClickable = true
                                            itemView.isFocusable = true
                                        }
                                        return@intercept result
                                    }

                                    // Grouped Card Binding (4-Tile & 2-Tile)
                                    val isActualAnchorContainer = (itemView.findViewById<View>(0x7f099995) != null || itemView.getTag(0x7f099994) != null) && itemView.getTag(0x7f09999c) != true
                                    val group = if (isActualAnchorContainer) ControlCenterConfigHolder.isGroupAnchor(spec) else null
                                    if (group != null) {
                                        if (!isEdit) {
                                            val container = itemView.findViewById<ViewGroup>(0x7f099995)
                                            if (container != null) {
                                                for (i in 0 until 4) {
                                                    val childTile = ControlCenterGrouping.getChildTileAt(container, i)
                                                    if (childTile != null) {
                                                        hideAllCustomBadges(childTile)
                                                        childTile.isClickable = true
                                                        childTile.isLongClickable = true
                                                        childTile.isFocusable = true
                                                    }
                                                }
                                            }
                                        }
                                        val rotateBtn = itemView.findViewById<View>(0x7f099992)
                                        if (rotateBtn != null) {
                                            val isIosCluster = group.isIosFolderCluster()
                                            val canRotate = ControlCenterConfigHolder.config.isModularEditActive && isEdit && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode && !isIosCluster
                                            if (canRotate) {
                                                rotateBtn.visibility = View.VISIBLE
                                                rotateBtn.bringToFront()
                                                rotateBtn.isClickable = true
                                                rotateBtn.setOnClickListener {
                                                    ControlCenterGrouping.rotateTileGroup(group, ctx, itemView)
                                                }
                                            } else {
                                                rotateBtn.visibility = View.GONE
                                                rotateBtn.isClickable = false
                                                rotateBtn.setOnClickListener(null)
                                            }
                                        }

                                        var activeQsListCtrl: Any? = null
                                        try {
                                            val qsListCtrlField = qsRecordClass.getDeclaredField("qsListController")
                                            qsListCtrlField.isAccessible = true
                                            val lazyQsList = qsListCtrlField.get(record)
                                            if (lazyQsList != null) {
                                                val getM = lazyQsList.javaClass.getMethod("get")
                                                activeQsListCtrl = getM.invoke(lazyQsList)
                                            }
                                        } catch (t: Throwable) {}
                                        if (activeQsListCtrl == null) activeQsListCtrl = ControlCenterConfigHolder.sQSListController

                                        ControlCenterGrouping.rebindGroupContainer(itemView, group, activeQsListCtrl, classLoader)

                                        // Disassemble Selection Indicator
                                        val selectTag = 0x7f099998
                                        val indicatorBadge = itemView.findViewById<ImageView>(selectTag)

                                        if (ControlCenterConfigHolder.sIsDisassembleMode) {
                                            val toggleDisassembleSelect = View.OnClickListener {
                                                if (ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id)) {
                                                    ControlCenterConfigHolder.sSelectedDisassembleGroups.remove(group.id)
                                                } else {
                                                    ControlCenterConfigHolder.sSelectedDisassembleGroups.add(group.id)
                                                }
                                                val curScd = indicatorBadge?.drawable as? SelectCircleDrawable
                                                curScd?.setChecked(ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id))
                                                indicatorBadge?.invalidate()
                                            }

                                            if (indicatorBadge != null) {
                                                val scd = (indicatorBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                                scd.setChecked(ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id))
                                                indicatorBadge.setImageDrawable(scd)
                                                indicatorBadge.visibility = View.VISIBLE
                                                indicatorBadge.bringToFront()
                                                indicatorBadge.isClickable = true
                                                indicatorBadge.setOnClickListener(toggleDisassembleSelect)
                                            }
                                            itemView.setOnClickListener(toggleDisassembleSelect)
                                            itemView.isClickable = true
                                        } else if (isEdit && !ControlCenterConfigHolder.sIsGroupSelectMode) {
                                            if (indicatorBadge != null) {
                                                indicatorBadge.visibility = View.GONE
                                                indicatorBadge.setOnClickListener(null)
                                                indicatorBadge.isClickable = false
                                            }
                                            val toggleTransparent = View.OnClickListener {
                                                group.isTransparent = !group.isTransparent
                                                ControlCenterGrouping.applyGroupContainerStyle(itemView, classLoader, group)
                                                ControlCenterConfigHolder.savePriorities(ctx)
                                                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            itemView.setOnClickListener(toggleTransparent)
                                            itemView.isClickable = true
                                            val wrapper = itemView.findViewById<View>(0x7f099996)
                                            wrapper?.setOnClickListener(toggleTransparent)
                                            wrapper?.isClickable = true
                                        } else {
                                            if (indicatorBadge != null) {
                                                indicatorBadge.visibility = View.GONE
                                                indicatorBadge.setOnClickListener(null)
                                                indicatorBadge.isClickable = false
                                            }
                                            itemView.setOnClickListener(null)
                                            itemView.isClickable = false
                                            val wrapper = itemView.findViewById<View>(0x7f099996)
                                            wrapper?.setOnClickListener(null)
                                            wrapper?.isClickable = false
                                        }
                                    } else {
                                        // Standard Single Tile / Card Customization Badge
                                        val isExpanded = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)
                                        val isVertical = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                        val isLarge = ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)
                                        val isCircular = ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) || (!isExpanded && !isVertical && !isLarge && !itemView.javaClass.name.contains("Card") && (itemView.javaClass.name.contains("Tile") || itemView.javaClass.name.contains("QSTileItemView")))
                                        val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                                        val iconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null

                                        if (isCircular) {
                                            setCircularTileLabelVisibility(itemView, !isEdit)
                                        }

                                        if (itemView is ViewGroup) {
                                            itemView.clipChildren = false
                                            itemView.clipToPadding = false
                                        }

                                        val resizeTag = 0x7f099999
                                        val badgeSize = (38 * density).toInt()

                                        var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
                                        val selectTag = 0x7f099998
                                        var selectBadge = itemView.findViewById<ImageView>(selectTag)

                                        if (itemView is ViewGroup && resizeHandle == null) {
                                            resizeHandle = ImageView(ctx).apply {
                                                id = resizeTag
                                                val lp = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                                                    gravity = Gravity.BOTTOM or Gravity.END
                                                }
                                                layoutParams = lp
                                                setImageDrawable(CurvedResizeHandleDrawable(density))
                                            }
                                            itemView.addView(resizeHandle)
                                        }

                                        if (itemView is ViewGroup && selectBadge == null) {
                                            selectBadge = ImageView(ctx).apply {
                                                id = selectTag
                                                val badgeSize2 = (26 * density).toInt()
                                                val lp = FrameLayout.LayoutParams(badgeSize2, badgeSize2).apply {
                                                    gravity = Gravity.TOP or Gravity.START
                                                    val margin = (4 * density).toInt()
                                                    setMargins(margin, margin, 0, 0)
                                                }
                                                layoutParams = lp
                                                setImageDrawable(SelectCircleDrawable())
                                            }
                                            itemView.addView(selectBadge)
                                        }

                                        if (spec != null) {
                                            itemView.setTag(0x7f099990, spec)
                                        }

                                        if (itemView.getTag(0x7f099991) == null) {
                                            itemView.setTag(0x7f099991, true)
                                            itemView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                                                val curHandle = itemView.findViewById<ImageView>(resizeTag)
                                                val curBadge = itemView.findViewById<ImageView>(selectTag)
                                                if (!ControlCenterConfigHolder.sIsEditMode) {
                                                    if (curHandle != null) {
                                                        curHandle.visibility = View.GONE
                                                        curHandle.setOnTouchListener(null)
                                                        curHandle.setOnClickListener(null)
                                                        curHandle.isClickable = false
                                                    }
                                                    if (curBadge != null) {
                                                        curBadge.visibility = View.GONE
                                                        curBadge.setOnClickListener(null)
                                                        curBadge.isClickable = false
                                                    }
                                                    return@addOnLayoutChangeListener
                                                }
                                                val w = r - l
                                                val h = b - t
                                                if (w > 0 && h > 0) {
                                                    val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null
                                                    val curSpec = (itemView.getTag(0x7f099990) as? String) ?: ControlCenterConfigHolder.getOrResolveTileSpec(itemView)
                                                    val curIsExp = if (curSpec != null) ControlCenterConfigHolder.sExpandedTileSpecs.contains(curSpec) else false
                                                    val curIsVert = if (curSpec != null) ControlCenterConfigHolder.sVerticalTileSpecs.contains(curSpec) else false
                                                    val curIsLarge = if (curSpec != null) ControlCenterConfigHolder.sLargeTileSpecs.contains(curSpec) else false
                                                    val isCardClass = itemView.javaClass.name.contains("Card")
                                                    val curIsCirc = if (curSpec != null && ControlCenterConfigHolder.sCircularCardSpecs.contains(curSpec)) {
                                                        true
                                                    } else {
                                                        !curIsExp && !curIsVert && !curIsLarge && !isCardClass
                                                    }

                                                    if (ControlCenterConfigHolder.sIsGroupSelectMode) {
                                                        if (curBadge != null && curBadge.visibility == View.VISIBLE) {
                                                             val bw2 = (26 * density).toInt()
                                                             val bh2 = (26 * density).toInt()
                                                             if (curIsCirc && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                                                 val iLeft = curIconFrame.left
                                                                 val iTop = curIconFrame.top
                                                                 curBadge.layout(iLeft, iTop, iLeft + bw2, iTop + bh2)
                                                             } else {
                                                                 val m = (8 * density).toInt()
                                                                 curBadge.layout(m, m, m + bw2, m + bh2)
                                                             }
                                                             curBadge.bringToFront()
                                                         }
                                                     } else if (curHandle != null) {
                                                         val curSpecStr = (itemView.getTag(0x7f099990) as? String) ?: curSpec
                                                         val isCurCard = curSpecStr == "wifi" || curSpecStr == "cell"
                                                         val isCurAdded = if (isCurCard) !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(curSpecStr) else ControlCenterConfigHolder.isTileAdded(curSpecStr)
                                                         if (isCurAdded && !ControlCenterConfigHolder.sIsDisassembleMode) {
                                                             curHandle.visibility = View.VISIBLE
                                                         } else {
                                                             curHandle.visibility = View.GONE
                                                         }
                                                         if (curHandle.visibility == View.VISIBLE) {
                                                             val bw = curHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                             val bh = curHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                             if (curIsCirc && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                                                 val iRight = curIconFrame.right
                                                                 val iBottom = curIconFrame.bottom
                                                                 curHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                                             } else {
                                                                 curHandle.layout(w - bw, h - bh, w, h)
                                                             }
                                                             curHandle.bringToFront()
                                                         }
                                                     }
                                                 }
                                             }
                                         }

                                        val isCard = spec == "wifi" || spec == "cell"
                                        val isAdded = if (isCard) !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec) else ControlCenterConfigHolder.isTileAdded(spec)
                                        if (isEdit && spec != null && (!isCircular || isCard)) {
                                            applyUniversalMarkButton(itemView, spec, isAdded, record)
                                        } else {
                                            val mark = itemView.findViewById<View>(UNIVERSAL_MARK_TAG)
                                            if (mark != null) {
                                                mark.visibility = View.GONE
                                                mark.setOnClickListener(null)
                                                mark.isClickable = false
                                            }
                                        }

                                        if (resizeHandle != null) {
                                            if (ControlCenterConfigHolder.config.isModularEditActive && isEdit && isAdded && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode) {
                                                resizeHandle.visibility = View.VISIBLE
                                                val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                if (isCircular && iconFrame != null && iconFrame.width > 0 && iconFrame.height > 0) {
                                                    val iRight = iconFrame.right
                                                    val iBottom = iconFrame.bottom
                                                    resizeHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                                } else if (itemView.width > 0 && itemView.height > 0) {
                                                    resizeHandle.layout(itemView.width - bw, itemView.height - bh, itemView.width, itemView.height)
                                                }
                                                resizeHandle.bringToFront()
                                                resizeHandle.isClickable = true
                                                if (spec != null) {
                                                    FluidTileResizeHandler.attach(resizeHandle, ctx, spec, itemView, density)
                                                }
                                            } else {
                                                resizeHandle.visibility = View.GONE
                                                resizeHandle.setOnTouchListener(null)
                                                resizeHandle.setOnClickListener(null)
                                                resizeHandle.isClickable = false
                                            }
                                        }

                                        if (spec != null) {
                                            val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
                                            val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId) else (71.5f * density).toInt()
                                            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
                                            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
                                            val curLp = itemView.layoutParams
                                            if (ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) || ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)) {
                                                if (curLp != null && curLp.height != tallH) {
                                                    curLp.height = tallH
                                                    itemView.layoutParams = curLp
                                                }
                                            } else if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) ||
                                                       ((spec == "wifi" || spec == "cell") && !ControlCenterConfigHolder.sCircularCardSpecs.contains(spec))) {
                                                if (curLp != null && curLp.height != cardH) {
                                                    curLp.height = cardH
                                                    itemView.layoutParams = curLp
                                                }
                                            } else {
                                                val unitRowHId = ctx.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", ctx.packageName)
                                                val unitRowH = if (unitRowHId != 0) ctx.resources.getDimensionPixelSize(unitRowHId) else (80 * density).toInt()
                                                if (curLp != null && (curLp.height > unitRowH || curLp.height == tallH)) {
                                                    curLp.height = unitRowH
                                                    itemView.layoutParams = curLp
                                                }
                                            }
                                        }

                                        if (selectBadge != null) {
                                            if (isEdit && ControlCenterConfigHolder.sIsGroupSelectMode) {
                                                val scd = (selectBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                                scd.setChecked(ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec))
                                                selectBadge.setImageDrawable(scd)
                                                selectBadge.visibility = View.VISIBLE
                                                val bw2 = (26 * density).toInt()
                                                val bh2 = (26 * density).toInt()
                                                if (isCircular && iconFrame != null && iconFrame.width > 0 && iconFrame.height > 0) {
                                                    val iLeft = iconFrame.left
                                                    val iTop = iconFrame.top
                                                    selectBadge.layout(iLeft, iTop, iLeft + bw2, iTop + bh2)
                                                } else {
                                                    val m = (8 * density).toInt()
                                                    selectBadge.layout(m, m, m + bw2, m + bh2)
                                                }
                                                selectBadge.bringToFront()
                                                selectBadge.isClickable = true

                                                val toggleSelect = View.OnClickListener {
                                                    if (spec != null) {
                                                        if (ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec)) {
                                                            ControlCenterConfigHolder.sSelectedTileSpecs.remove(spec)
                                                        } else {
                                                            val currentArea = ControlCenterConfigHolder.sSelectedTileSpecs.sumOf { ControlCenterConfigHolder.getTileArea(it) }
                                                            val tileArea = ControlCenterConfigHolder.getTileArea(spec)
                                                            if (currentArea + tileArea <= 12) {
                                                                ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                            } else {
                                                                it.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                                                                return@OnClickListener
                                                            }
                                                        }
                                                        it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                        val curScd = selectBadge.drawable as? SelectCircleDrawable
                                                        curScd?.setChecked(ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec))
                                                        selectBadge.invalidate()
                                                    }
                                                }
                                                selectBadge.setOnClickListener(toggleSelect)
                                            } else {
                                                selectBadge.visibility = View.GONE
                                                selectBadge.setOnClickListener(null)
                                                selectBadge.isClickable = false
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "onBindViewHolder intercept error", t)
                            } finally {
                                ControlCenterConfigHolder.sBindingRecords.get().remove(record)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 14b. Hook QSTileItemView and QSCardItemView.onModeChanged
            try {
                val viewClassNames = arrayOf(
                    "miui.systemui.controlcenter.qs.tileview.QSTileItemView",
                    "miui.systemui.controlcenter.qs.tileview.QSCardItemView"
                )
                for (vName in viewClassNames) {
                    val vClass = Class.forName(vName, false, classLoader)
                    for (m in vClass.declaredMethods) {
                        if (m.name == "onModeChanged") {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                val result = chain.proceed()
                                try {
                                    val itemView = chain.thisObject as? View
                                    if (itemView != null) {
                                        val rawArgs = chain.args
                                        val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                        val isEdit = args != null && args.isNotEmpty() && args[0] != null && "EDIT" == args[0].toString()
                                        if (ControlCenterConfigHolder.sIsEditMode && !isEdit) {
                                            ControlCenterConfigHolder.onExitEditMode(itemView, itemView.context)
                                        } else if (isEdit) {
                                            val wasEdit = ControlCenterConfigHolder.sIsEditMode
                                            ControlCenterConfigHolder.sIsEditMode = true
                                            if (!wasEdit) {
                                                ControlCenterConfigHolder.onEnterEditMode(itemView, itemView.context)
                                            }
                                        } else {
                                            ControlCenterConfigHolder.sIsEditMode = false
                                        }

                                        val spec = ControlCenterConfigHolder.getOrResolveTileSpec(itemView)
                                        val density = itemView.context.resources.displayMetrics.density
                                        val isCircularTile = itemView.javaClass.name.contains("QSTileItemView") && !isChildOfContainer(itemView)

                                        if (isEdit) {
                                            if (isCircularTile) {
                                                setCircularTileLabelVisibility(itemView, false)
                                            }
                                            if (itemView is ViewGroup) {
                                                itemView.clipChildren = false
                                                itemView.clipToPadding = false
                                            }

                                            val resizeTag = 0x7f099999
                                            val badgeSize = (38 * density).toInt()
                                            var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
                                            if (resizeHandle == null && itemView is ViewGroup && !isChildOfContainer(itemView)) {
                                                resizeHandle = ImageView(itemView.context).apply {
                                                    id = resizeTag
                                                    val lp = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                                                        gravity = Gravity.BOTTOM or Gravity.END
                                                    }
                                                    layoutParams = lp
                                                    setImageDrawable(CurvedResizeHandleDrawable(density))
                                                }
                                                itemView.addView(resizeHandle)
                                            }

                                            if (resizeHandle != null && !isChildOfContainer(itemView)) {
                                                val curSpec = (itemView.getTag(0x7f099990) as? String) ?: spec
                                                val curIsExp = if (curSpec != null) ControlCenterConfigHolder.sExpandedTileSpecs.contains(curSpec) else false
                                                val curIsVert = if (curSpec != null) ControlCenterConfigHolder.sVerticalTileSpecs.contains(curSpec) else false
                                                val curIsLarge = if (curSpec != null) ControlCenterConfigHolder.sLargeTileSpecs.contains(curSpec) else false
                                                val isCardClass = itemView.javaClass.name.contains("Card")
                                                val curIsCirc = if (curSpec != null && ControlCenterConfigHolder.sCircularCardSpecs.contains(curSpec)) {
                                                    true
                                                } else {
                                                    !curIsExp && !curIsVert && !curIsLarge && !isCardClass
                                                }

                                                val isCurCard = curSpec == "wifi" || curSpec == "cell"
                                                val isCurAdded = if (isCurCard) !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(curSpec) else ControlCenterConfigHolder.isTileAdded(curSpec)
                                                if (curSpec != null && (!curIsCirc || isCurCard)) {
                                                    applyUniversalMarkButton(itemView, curSpec, isCurAdded, null)
                                                } else {
                                                    val mark = itemView.findViewById<View>(UNIVERSAL_MARK_TAG)
                                                    if (mark != null) {
                                                        mark.visibility = View.GONE
                                                        mark.setOnClickListener(null)
                                                        mark.isClickable = false
                                                    }
                                                }

                                                val shouldShow = ControlCenterConfigHolder.config.isModularEditActive && isCurAdded && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
                                                if (shouldShow) {
                                                    resizeHandle.visibility = View.VISIBLE
                                                    val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                    val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                    val iconFrameId = itemView.resources.getIdentifier("icon_frame", "id", itemView.context.packageName)
                                                    val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null

                                                    fun applyHandleLayout() {
                                                        if (curIsCirc && curIconFrame != null && curIconFrame.width > 0 && curIconFrame.height > 0) {
                                                            val iRight = curIconFrame.right
                                                            val iBottom = curIconFrame.bottom
                                                            resizeHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                                        } else if (itemView.width > 0 && itemView.height > 0) {
                                                            resizeHandle.layout(itemView.width - bw, itemView.height - bh, itemView.width, itemView.height)
                                                        }
                                                        resizeHandle.bringToFront()
                                                    }

                                                    applyHandleLayout()
                                                    itemView.post { applyHandleLayout() }

                                                    resizeHandle.isClickable = true
                                                    if (spec != null) {
                                                        FluidTileResizeHandler.attach(resizeHandle, itemView.context, spec, itemView, density)
                                                    }
                                                } else {
                                                    resizeHandle.visibility = View.GONE
                                                    resizeHandle.setOnTouchListener(null)
                                                    resizeHandle.isClickable = false
                                                }
                                            }

                                            val selectBadge = itemView.findViewById<ImageView>(0x7f099998)
                                            if (selectBadge != null && !isChildOfContainer(itemView)) {
                                                if (!ControlCenterConfigHolder.config.isModularEditActive || !ControlCenterConfigHolder.sIsGroupSelectMode) {
                                                    selectBadge.visibility = View.GONE
                                                    selectBadge.setOnClickListener(null)
                                                    selectBadge.isClickable = false
                                                } else {
                                                    val scd = (selectBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                                    if (spec != null) {
                                                        scd.setChecked(ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec))
                                                         val toggleSelect = View.OnClickListener {
                                                            if (ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec)) {
                                                                ControlCenterConfigHolder.sSelectedTileSpecs.remove(spec)
                                                            } else {
                                                                val currentArea = ControlCenterConfigHolder.sSelectedTileSpecs.sumOf { ControlCenterConfigHolder.getTileArea(it) }
                                                                val tileArea = ControlCenterConfigHolder.getTileArea(spec)
                                                                if (currentArea + tileArea <= 12) {
                                                                    ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                                } else {
                                                                    it.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                                                                    return@OnClickListener
                                                                }
                                                            }
                                                            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                                            val curScd = selectBadge.drawable as? SelectCircleDrawable
                                                            curScd?.setChecked(ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec))
                                                            selectBadge.invalidate()
                                                        }
                                                        selectBadge.setOnClickListener(toggleSelect)
                                                        selectBadge.isClickable = true
                                                    }
                                                    selectBadge.setImageDrawable(scd)
                                                    selectBadge.visibility = View.VISIBLE
                                                    selectBadge.bringToFront()
                                                }
                                            }

                                            val rotateBtn = itemView.findViewById<View>(0x7f099992)
                                            if (rotateBtn != null) {
                                                val group = itemView.getTag(0x7f099994) as? TileGroup
                                                    ?: ControlCenterConfigHolder.isGroupAnchor(itemView.getTag(0x7f099990) as? String)
                                                val isIosCluster = (group != null && group.isIosFolderCluster())
                                                val canRotate = ControlCenterConfigHolder.config.isModularEditActive && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode && !isIosCluster
                                                rotateBtn.visibility = if (canRotate) View.VISIBLE else View.GONE
                                                if (canRotate) rotateBtn.bringToFront()
                                            }
                                        } else {
                                            if (isCircularTile) {
                                                setCircularTileLabelVisibility(itemView, true)
                                            }
                                            hideAllCustomBadges(itemView)
                                            itemView.isClickable = true
                                            itemView.isLongClickable = true
                                            itemView.isFocusable = true

                                            val container = itemView.findViewById<ViewGroup>(0x7f099995)
                                            if (container != null) {
                                                val childTiles = ControlCenterGrouping.getAllChildTiles(container)
                                                for (childTile in childTiles) {
                                                    hideAllCustomBadges(childTile)
                                                    childTile.isClickable = true
                                                    childTile.isLongClickable = true
                                                    childTile.isFocusable = true
                                                }
                                            }

                                            ControlCenterConfigHolder.sIsGroupSelectMode = false
                                            ControlCenterConfigHolder.sIsDisassembleMode = false
                                            ControlCenterConfigHolder.sSelectedTileSpecs.clear()
                                            ControlCenterConfigHolder.sSelectedDisassembleGroups.clear()
                                        }
                                    }
                                } catch (t: Throwable) {}
                                result
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 14c. Keep Grouped Container styled in QSCardItemView
            try {
                val cardViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader)
                for (m in cardViewClass.declaredMethods) {
                    if (m.name == "updateBlurBlendBackground" || m.name == "updateDrawableBackground" || m.name == "updateBackground") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                ControlCenterGrouping.applyGroupContainerStyle(itemView, classLoader)
                                null
                            } else if (itemView != null && isChildOfContainer(itemView)) {
                                if (m.name == "updateBlurBlendBackground" || m.name == "updateBackground") {
                                    val state = try {
                                        val f = itemView.javaClass.getDeclaredField("state").apply { isAccessible = true }
                                        f.get(itemView)
                                    } catch (t: Throwable) { null }
                                    ControlCenterGrouping.applyNonAdvancedStyleToChildTile(itemView, state, classLoader)
                                    null
                                } else {
                                    chain.proceed()
                                }
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    if (m.name == "updateClip") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? ViewGroup
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                itemView.clipChildren = false
                                itemView.clipToOutline = false
                                null
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    if (m.name == "onInterceptTouchEvent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                if (ControlCenterConfigHolder.sIsDisassembleMode || (ControlCenterConfigHolder.sIsEditMode && !ControlCenterConfigHolder.sIsGroupSelectMode)) {
                                    chain.proceed()
                                } else {
                                    false
                                }
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    if (m.name == "onTouchEvent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                if (ControlCenterConfigHolder.sIsDisassembleMode || (ControlCenterConfigHolder.sIsEditMode && !ControlCenterConfigHolder.sIsGroupSelectMode)) {
                                    chain.proceed()
                                } else {
                                    true
                                }
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val iconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader)
                for (m in iconViewClass.declaredMethods) {
                    if (m.name == "applyBlendColor") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val iconView = chain.thisObject as? View
                            if (iconView != null && isChildOfContainer(iconView)) {
                                val state = chain.args.getOrNull(0) ?: try {
                                    val f = iconView.javaClass.getDeclaredField("state").apply { isAccessible = true }
                                    f.get(iconView)
                                } catch (t: Throwable) { null }
                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(iconView, state, classLoader)
                                null
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 14c-2. Ensure child tiles inside grouped containers update visually on state changes
            try {
                val tileViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemView", false, classLoader)
                for (m in tileViewClass.declaredMethods) {
                    if (m.name == "updateBlurBlendBackground" || m.name == "updateDrawableBackground" || m.name == "updateBackground") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && ControlCenterGrouping.isChildOfIosCluster(itemView)) {
                                itemView.background = null
                                itemView.clipToOutline = false
                                null
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    if (m.name == "updateState") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            val itemView = chain.thisObject as? View
                            if (itemView != null && isChildOfContainer(itemView)) {
                                val state = chain.args.getOrNull(0) ?: try {
                                    val f = itemView.javaClass.getDeclaredField("state").apply { isAccessible = true }
                                    f.get(itemView)
                                } catch (t: Throwable) { null }
                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(itemView, state, classLoader)
                            }
                            if (itemView != null && ControlCenterGrouping.isChildOfIosCluster(itemView)) {
                                itemView.background = null
                                itemView.clipToOutline = false
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val cardViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader)
                for (m in cardViewClass.declaredMethods) {
                    if (m.name == "updateState") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            val itemView = chain.thisObject as? View
                            if (itemView != null && isChildOfContainer(itemView)) {
                                val state = chain.args.getOrNull(0) ?: try {
                                    val f = itemView.javaClass.getDeclaredField("state").apply { isAccessible = true }
                                    f.get(itemView)
                                } catch (t: Throwable) { null }
                                ControlCenterGrouping.applyNonAdvancedStyleToChildTile(itemView, state, classLoader)
                            }
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 14d. Forward QSCardViewHolder Animations, Attachments & Frame Callbacks
            try {
                val cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader)
                for (m in cardHolderClass.declaredMethods) {
                    if (m.name == "onFrameCallback") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val cardHolder = chain.thisObject
                                val itemView = ControlCenterGrouping.getViewHolderItemView(cardHolder)
                                if (itemView != null) {
                                    val subHolders = itemView.getTag(0x7f099993) as? Array<*>
                                    if (subHolders != null) {
                                        for (subHolder in subHolders) {
                                            if (subHolder != null && !ControlCenterGrouping.sGroupChildHolders.contains(subHolder)) {
                                                ControlCenterGrouping.updateChildHolderFrame(cardHolder, subHolder)
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                    if (m.name == "onViewAttachedToWindow") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val cardHolder = chain.thisObject
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 2) {
                                    ControlCenterConfigHolder.sMainPanelAdapter = args[0]
                                    ControlCenterConfigHolder.sFrameCallback = args[1]
                                }
                                val itemView = ControlCenterGrouping.getViewHolderItemView(cardHolder)
                                if (itemView != null) {
                                    val container = itemView.findViewById<ViewGroup>(0x7f099995)
                                    if (container != null) {
                                        container.visibility = View.VISIBLE
                                        for (i in 0 until container.childCount) {
                                            val c = container.getChildAt(i)
                                            if (c != null) {
                                                c.visibility = View.VISIBLE
                                                if (!ControlCenterConfigHolder.sIsEditMode) {
                                                    c.alpha = 1.0f
                                                }
                                            }
                                        }
                                    }
                                    val subHolders = itemView.getTag(0x7f099993) as? Array<*>
                                    if (subHolders != null) {
                                        for (subHolder in subHolders) {
                                            if (subHolder != null) {
                                                var vhCls: Class<*>? = subHolder.javaClass
                                                while (vhCls != null) {
                                                    try {
                                                        val af = vhCls.getDeclaredField("attached")
                                                        af.isAccessible = true
                                                        af.setBoolean(subHolder, true)
                                                        break
                                                    } catch (t: Throwable) { vhCls = vhCls.superclass }
                                                }
                                                try {
                                                    if (args != null && args.size >= 2) {
                                                        val childOnAttach = subHolder.javaClass.getMethod("onViewAttachedToWindow", args[0]!!.javaClass, args[1]!!.javaClass)
                                                        childOnAttach.invoke(subHolder, args[0], args[1])
                                                    } else {
                                                        val childOnAttach = subHolder.javaClass.getMethod("onViewAttachedToWindow")
                                                        childOnAttach.invoke(subHolder)
                                                    }
                                                } catch (t: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 15. Disable Slider Touch during EDIT mode
            try {
                val verticalSeekBarClass = Class.forName("miui.systemui.controlcenter.widget.VerticalSeekBar", false, classLoader)
                for (m in verticalSeekBarClass.declaredMethods) {
                    if (m.name == "onTouchEvent" || m.name == "dispatchTouchEvent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.sIsEditMode) false else chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 16. Attach SpannedGridLayoutManager
            try {
                val adapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader)
                for (m in adapterClass.declaredMethods) {
                    if (m.name == "onAttachedToRecyclerView") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val adapter = chain.thisObject
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] is View) {
                                    val rv = args[0] as View
                                    ControlCenterConfigHolder.sRecyclerView = java.lang.ref.WeakReference(rv)
                                    ControlCenterConfigHolder.sMainPanelAdapter = adapter
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed in onAttachedToRecyclerView intercept", t)
                            }
                            result
                        }
                    }
                    if (m.name == "onBindViewHolder" && m.parameterTypes.size >= 2 && m.parameterTypes[1] == Int::class.javaPrimitiveType) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = try {
                                chain.proceed()
                            } catch (t: Throwable) {
                                null
                            }
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty()) {
                                    val holder = args[0]
                                    if (holder != null) {
                                        bindCustomModuleHolder(holder, classLoader)
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 16b. Hook ToggleSliderViewHolder.updateSize for horizontal slider sizing
            try {
                val sliderHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder", false, classLoader)
                for (m in sliderHolderClass.declaredMethods) {
                    if (m.name == "updateSize") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                applySliderLayoutAndTouch(chain.thisObject)
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 16c. Hook MediaPlayerViewHolder for persistent layout on updateMetaData, updateSize, setDetailAvailable
            try {
                val mediaHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.media.MediaPlayerController\$MediaPlayerViewHolder", false, classLoader)
                for (m in mediaHolderClass.declaredMethods) {
                    if (m.name == "updateMetaData") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty()) {
                                    sLastMediaPlayerMetaData = args[0]
                                }
                            } catch (t: Throwable) {}
                            val result = chain.proceed()
                            try {
                                applyMediaPlayerLayout(chain.thisObject)
                            } catch (t: Throwable) {}
                            result
                        }
                    } else if (m.name == "updateSize" || m.name == "setDetailAvailable") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                applyMediaPlayerLayout(chain.thisObject)
                            } catch (t: Throwable) {}
                            result
                        }
                    } else if (m.name == "performFlip") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val holder = chain.thisObject
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            val bitmap = args?.getOrNull(0) as? Bitmap
                            try {
                                val bField = holder.javaClass.getDeclaredField("binding")
                                bField.isAccessible = true
                                val binding = bField.get(holder)
                                val cField = binding?.javaClass?.getField("cover")
                                val cover = cField?.get(binding) as? View
                                cover?.let {
                                    it.rotationY = 0f
                                    it.scaleX = 1f
                                    it.scaleY = 1f
                                }
                                val setCoverM = holder.javaClass.getDeclaredMethod("setCover", Bitmap::class.java, Boolean::class.javaPrimitiveType)
                                setCoverM.isAccessible = true
                                setCoverM.invoke(holder, bitmap, false)
                            } catch (t: Throwable) {
                                Log.e(TAG, "performFlip hook error", t)
                            }
                            null
                        }
                    } else if (m.name == "setCover") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                val holder = chain.thisObject
                                val bField = holder.javaClass.getDeclaredField("binding")
                                bField.isAccessible = true
                                val binding = bField.get(holder)
                                val cField = binding?.javaClass?.getField("cover")
                                val cover = cField?.get(binding) as? View
                                cover?.let {
                                    it.rotationY = 0f
                                    it.scaleX = 1f
                                    it.scaleY = 1f
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 16d. Hook MediaPanelAnimator for secondary panel expand/collapse persistence and smooth one-to-one transitions
            try {
                val mediaAnimClass = Class.forName("miui.systemui.controlcenter.panel.secondary.media.MediaPanelAnimator", false, classLoader)
                for (m in mediaAnimClass.declaredMethods) {
                    if (m.name == "calculateViewValues" || m.name == "expand" || m.name == "collapse") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val lastField = mediaAnimClass.getDeclaredField("lastAnimValue").apply { isAccessible = true }
                                lastField.set(chain.thisObject, null)
                            } catch (t: Throwable) {}
                            if (m.name == "collapse") {
                                try {
                                    sMediaPlayerViewHolder?.get()?.let { applyMediaPlayerLayout(it) }
                                } catch (t: Throwable) {}
                            }
                            chain.proceed()
                        }
                    } else if (m.name == "frameCallback") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                handleMediaPanelTransitionFrame(chain.thisObject, classLoader)
                            } catch (t: Throwable) {}
                            result
                        }
                    } else if (m.name == "onAnimComplete") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                sMediaPlayerViewHolder?.get()?.let { applyMediaPlayerLayout(it) }
                            } catch (t: Throwable) {}
                            result
                        }
                    } else if (m.name == "relayoutNoPlayView") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val size = ControlCenterConfigHolder.sMediaPlayerSize
                            if (size != "2x2") {
                                try {
                                    sMediaPlayerViewHolder?.get()?.let { applyMediaPlayerLayout(it) }
                                } catch (t: Throwable) {}
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 16d. Hook True 2D Grid Layout in GridLayoutManager
            try {
                val glmClass = Class.forName("androidx.recyclerview.widget.GridLayoutManager", false, classLoader)
                for (m in glmClass.declaredMethods) {
                    if (m.name == "layoutChunk") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (!ControlCenterConfigHolder.config.isTrue2dGridActive) {
                                return@intercept chain.proceed()
                            }
                            try {
                                val glm = chain.thisObject
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 4) {
                                    val recycler = args[0]
                                    val state = args[1]
                                    val layoutState = args[2]
                                    val layoutChunkResult = args[3]
                                    if (recycler != null && state != null && layoutState != null && layoutChunkResult != null) {
                                        val handled = True2DGridEngine.handleLayoutChunk(
                                            glm, recycler, state, layoutState, layoutChunkResult, classLoader
                                        )
                                        if (handled) return@intercept null
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in True2DGridEngine layoutChunk intercept", t)
                            }
                            chain.proceed()
                        }
                    }
                }

                val spanSizeLookupClass = Class.forName("androidx.recyclerview.widget.GridLayoutManager\$SpanSizeLookup", false, classLoader)
                for (m in spanSizeLookupClass.declaredMethods) {
                    if (m.name == "getSpanGroupIndex" || m.name == "getCachedSpanGroupIndex") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.isTrue2dGridActive) {
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    val pos = (args?.getOrNull(0) as? Int) ?: -1
                                    if (pos >= 0) {
                                        val row = True2DGridEngine.getItemRowForActiveAdapter(pos)
                                        if (row >= 0) return@intercept row
                                    }
                                } catch (t: Throwable) {}
                            }
                            chain.proceed()
                        }
                    }
                    if (m.name == "getSpanIndex" || m.name == "getCachedSpanIndex") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.isTrue2dGridActive) {
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    val pos = (args?.getOrNull(0) as? Int) ?: -1
                                    if (pos >= 0) {
                                        val col = True2DGridEngine.getItemColForActiveAdapter(pos)
                                        if (col >= 0) return@intercept col
                                    }
                                } catch (t: Throwable) {}
                            }
                            chain.proceed()
                        }
                    }
                    if (m.name == "getSpanSize") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.isTrue2dGridActive) {
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                    val pos = (args?.getOrNull(0) as? Int) ?: -1
                                    if (pos >= 0) {
                                        val spanW = True2DGridEngine.getItemSpanWForActiveAdapter(pos)
                                        if (spanW > 0) return@intercept spanW
                                    }
                                } catch (t: Throwable) {}
                            }
                            chain.proceed()
                        }
                    }
                }

                try {
                    val adapterLookupClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$layoutManager\$1\$1", false, classLoader)
                    for (m in adapterLookupClass.declaredMethods) {
                        if (m.name == "getSpanSize") {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                if (ControlCenterConfigHolder.config.isTrue2dGridActive) {
                                    try {
                                        val rawArgs = chain.args
                                        val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                        val pos = (args?.getOrNull(0) as? Int) ?: -1
                                        if (pos >= 0) {
                                            val spanW = True2DGridEngine.getItemSpanWForActiveAdapter(pos)
                                            if (spanW > 0) return@intercept spanW
                                        }
                                    } catch (t: Throwable) {}
                                }
                                chain.proceed()
                            }
                        }
                    }
                } catch (t: Throwable) {}
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook GridLayoutManager for True2DGridEngine", t)
            }

            // 16e. Suppress prepareForDrop in LinearLayoutManager during Edit Mode
            // Suppressing prepareForDrop completely eliminates the violent up-and-down screen scrolling
            // caused by LinearLayoutManager calling scrollToPositionWithOffset when dragging multi-row components.
            try {
                val llmClass = Class.forName("androidx.recyclerview.widget.LinearLayoutManager", false, classLoader)
                for (m in llmClass.declaredMethods) {
                    if (m.name == "prepareForDrop") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.sIsEditMode) {
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook prepareForDrop on LinearLayoutManager", t)
            }

            // 17. Full 100% Unlock (Zero Boundaries & Dynamic Landscape Flow)
            try {
                ControlCenterFullUnlock.init(module, classLoader)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize ControlCenterFullUnlock", t)
            }


            // 18. Inject "Group / Select" Button in CustomizeHeaderController
            try {
                val customizeHeaderClass = Class.forName("miui.systemui.controlcenter.panel.main.header.CustomizeHeaderController", false, classLoader)
                for (c in customizeHeaderClass.declaredConstructors) {
                    c.isAccessible = true
                    module.hook(c).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            ControlCenterGrouping.ensureGroupButtonInjected(chain.thisObject, classLoader)
                        } catch (t: Throwable) {}
                        result
                    }
                }

                for (m in customizeHeaderClass.declaredMethods) {
                    val mName = m.name
                    if (mName == "onCreate" || mName == "transitionFrom" || mName == "updateResources" || mName == "updateBlendStyle") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            try {
                                ControlCenterGrouping.ensureGroupButtonInjected(chain.thisObject, classLoader)
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 20. Prevent RecyclerView from stealing touch gestures during curved resize handle dragging
            try {
                val rvClasses = arrayOf(
                    "miui.systemui.controlcenter.widget.MainPanelRecyclerView",
                    "miui.systemui.controlcenter.widget.ClickableRecyclerView",
                    "androidx.recyclerview.widget.RecyclerView"
                )
                for (clsName in rvClasses) {
                    try {
                        val rvCls = Class.forName(clsName, false, classLoader)
                        var curCls: Class<*>? = rvCls
                        while (curCls != null && curCls != Any::class.java) {
                            for (m in curCls.declaredMethods) {
                                if (m.name == "onInterceptTouchEvent") {
                                    m.isAccessible = true
                                    module.hook(m).intercept { chain ->
                                        if (ControlCenterConfigHolder.sIsDraggingResizeHandle) {
                                            return@intercept false
                                        }
                                        chain.proceed()
                                    }
                                }
                            }
                            curCls = curCls.superclass
                        }
                    } catch (t: Throwable) {}
                }
            } catch (t: Throwable) {}

            // 21. Secondary detail window (e.g. Bluetooth) card adaptation so collapse animation matches card
            try {
                val delegateClass = Class.forName("miui.systemui.controlcenter.panel.secondary.detail.DetailPanelTilesDelegate", false, classLoader)
                for (m in delegateClass.declaredMethods) {
                    if (m.name == "prepareShow") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val delegate = chain.thisObject
                                val getPanelParamsM = delegate.javaClass.getDeclaredMethod("getPanelParams")
                                getPanelParamsM.isAccessible = true
                                val panelParams = getPanelParamsM.invoke(delegate)
                                if (panelParams != null) {
                                    val getAdapterM = panelParams.javaClass.getMethod("getAdapter")
                                    val adapter = getAdapterM.invoke(panelParams)
                                    if (adapter != null) {
                                        val secParamsClass = Class.forName("miui.systemui.controlcenter.panel.secondary.SecondaryParamsKt", false, classLoader)
                                        val fromM = secParamsClass.getMethod("from", Class.forName("miui.systemui.controlcenter.panel.secondary.DetailAdapter", false, classLoader))
                                        val strFrom = fromM.invoke(null, adapter) as? String
                                        if (strFrom != null) {
                                            val isVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(strFrom)
                                            val isExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(strFrom)
                                            val isLarge = ControlCenterConfigHolder.sLargeTileSpecs.contains(strFrom)
                                            val getViewsM = delegate.javaClass.getDeclaredMethod("getViews")
                                            getViewsM.isAccessible = true
                                            @Suppress("UNCHECKED_CAST")
                                            val viewsMap = getViewsM.invoke(delegate) as? HashMap<String, ViewGroup>
                                            if (viewsMap != null) {
                                                val currentView = viewsMap[strFrom]
                                                val isCard = currentView != null && currentView.javaClass.name.contains("QSCardItemView")
                                                if (isVert || isExp || isLarge) {
                                                    if (!isCard) {
                                                        val getCtxM = delegate.javaClass.getMethod("getContext")
                                                        val ctx = getCtxM.invoke(delegate) as Context
                                                        val sysUIContextF = delegate.javaClass.getDeclaredField("sysUIContext")
                                                        sysUIContextF.isAccessible = true
                                                        val sysUIContext = (sysUIContextF.get(delegate) as? Context) ?: ctx

                                                        val cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader)
                                                        val inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater::class.java)
                                                        val binding = inflateMethod.invoke(null, LayoutInflater.from(ctx))
                                                        val getRootM = cardBindingClass.getMethod("getRoot")
                                                        val rootCard = getRootM.invoke(binding) as ViewGroup

                                                        val cardIconClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader)
                                                        var iconCtor: java.lang.reflect.Constructor<*>? = null
                                                        for (c in cardIconClass.declaredConstructors) {
                                                            if (c.parameterTypes.size == 5) {
                                                                iconCtor = c
                                                                break
                                                            }
                                                        }
                                                        if (iconCtor == null && cardIconClass.declaredConstructors.isNotEmpty()) {
                                                            iconCtor = cardIconClass.declaredConstructors[0]
                                                        }
                                                        iconCtor?.isAccessible = true
                                                        val iconView = if (iconCtor?.parameterTypes?.size == 5) {
                                                            iconCtor.newInstance(rootCard.context, sysUIContext, null, 4, null)
                                                        } else {
                                                            iconCtor?.newInstance(rootCard.context, sysUIContext)
                                                        }
                                                        if (iconView != null) {
                                                            val initM = rootCard.javaClass.getMethod("init", cardIconClass)
                                                            initM.invoke(rootCard, iconView)
                                                        }

                                                        if (isVert) {
                                                            val density = ctx.resources.displayMetrics.density
                                                            val scaleResId = ctx.resources.getIdentifier("scale_content", "id", ctx.packageName)
                                                            val scaleContent = if (scaleResId != 0) rootCard.findViewById<android.widget.LinearLayout>(scaleResId) else null
                                                            if (scaleContent != null) {
                                                                scaleContent.orientation = android.widget.LinearLayout.VERTICAL
                                                                scaleContent.gravity = Gravity.CENTER_HORIZONTAL
                                                                if (scaleContent.childCount > 0) {
                                                                    val iconChild = scaleContent.getChildAt(0)
                                                                    val iconLp = iconChild.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                                    if (iconLp != null) {
                                                                        iconLp.gravity = Gravity.CENTER_HORIZONTAL
                                                                        iconLp.topMargin = (16 * density).toInt()
                                                                        iconLp.bottomMargin = (4 * density).toInt()
                                                                        iconChild.layoutParams = iconLp
                                                                    }
                                                                }
                                                                val lblResId = ctx.resources.getIdentifier("label_container", "id", ctx.packageName)
                                                                val labelCont = if (lblResId != 0) rootCard.findViewById<android.widget.LinearLayout>(lblResId) else null
                                                                if (labelCont != null) {
                                                                    val lblLp = labelCont.layoutParams as? android.widget.LinearLayout.LayoutParams
                                                                    if (lblLp != null) {
                                                                        lblLp.gravity = Gravity.CENTER_HORIZONTAL
                                                                        lblLp.marginStart = (4 * density).toInt()
                                                                        lblLp.marginEnd = (4 * density).toInt()
                                                                        labelCont.layoutParams = lblLp
                                                                    }
                                                                    val tId = ctx.resources.getIdentifier("title", "id", ctx.packageName)
                                                                    labelCont.findViewById<TextView>(tId)?.gravity = Gravity.CENTER
                                                                    val sId = ctx.resources.getIdentifier("status", "id", ctx.packageName)
                                                                    labelCont.findViewById<TextView>(sId)?.gravity = Gravity.CENTER
                                                                }
                                                            }
                                                        }
                                                        viewsMap[strFrom] = rootCard
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "prepareShow secondary detail card adaptation error", t)
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            // 21. Secondary Panel Slider Synchronization: Brightness
            try {
                val bDelegateClass = Class.forName("miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelSliderDelegate", false, classLoader)
                for (m in bDelegateClass.declaredMethods) {
                    if (m.name == "prepareShow" || m.name == "updateSize") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val res = chain.proceed()
                            try {
                                if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) {
                                    applyExpandedBrightnessLayout(chain.thisObject)
                                }
                            } catch (t: Throwable) {}
                            res
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val bAnimClass = Class.forName("miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelAnimator", false, classLoader)
                for (m in bAnimClass.declaredMethods) {
                    if (m.name == "prepareExpand") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val res = chain.proceed()
                            try {
                                if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) {
                                    val anim = chain.thisObject
                                    val delegateField = anim.javaClass.getDeclaredField("sliderDelegate")
                                    delegateField.isAccessible = true
                                    val delegate = delegateField.get(anim)
                                    if (delegate != null) {
                                        applyExpandedBrightnessLayout(delegate)
                                    }
                                }
                            } catch (t: Throwable) {}
                            res
                        }
                    }
                }
            } catch (t: Throwable) {}


            hookedPluginLoaders.add(classLoader)
            Log.i(TAG, "Control Center Plugin hooks successfully attached!")
        } catch (t: Throwable) {
            Log.e(TAG, "initPluginHooks fatal error", t)
        }
    }

    fun bindCustomModuleHolder(holder: Any, classLoader: ClassLoader) {
        val hName = holder.javaClass.name
        if (hName.contains("ToggleSliderViewHolder")) {
            applySliderLayoutAndTouch(holder)
        } else if (hName.contains("MediaPlayerViewHolder")) {
            applyMediaPlayerLayout(holder)
        } else {
            val itemView = ControlCenterGrouping.getViewHolderItemView(holder)
            if (itemView != null && itemView.findViewById<ViewGroup>(0x7f099995) != null) {
                var group = itemView.getTag(0x7f099994) as? TileGroup
                if (group == null) {
                    val spec = itemView.getTag(0x7f099990) as? String
                        ?: try {
                            val itemF = holder.javaClass.getDeclaredField("item")
                            itemF.isAccessible = true
                            ControlCenterConfigHolder.getRecordSpec(itemF.get(holder))
                        } catch (t: Throwable) { null }
                    if (spec != null) {
                        itemView.setTag(0x7f099990, spec)
                        group = ControlCenterConfigHolder.isGroupAnchor(spec)
                    }
                }
                if (group != null) {
                    ControlCenterGrouping.rebindGroupContainer(itemView, group, null, classLoader)
                }
            }
        }
    }

    fun applySliderLayoutAndTouch(holder: Any) {
        try {
            val holderClass = holder.javaClass
            var binding: Any? = null
            try {
                val bindingField = holderClass.getDeclaredField("binding")
                bindingField.isAccessible = true
                binding = bindingField.get(holder)
            } catch (t: Throwable) {
                try {
                    val getBindingM = holderClass.getMethod("getBinding")
                    binding = getBindingM.invoke(holder)
                } catch (t2: Throwable) {}
            }
            if (binding == null) return

            val itemView = try {
                holderClass.getField("itemView").get(holder) as? View
            } catch (t: Throwable) {
                try {
                    val f = holderClass.getDeclaredField("itemView")
                    f.isAccessible = true
                    f.get(holder) as? View
                } catch (t2: Throwable) { null }
            } ?: return

            var isBrightness = false
            try {
                val getOwnerM = holderClass.getMethod("getOwner")
                val owner = getOwnerM.invoke(holder)
                if (owner != null && owner.javaClass.name.contains("Brightness")) {
                    isBrightness = true
                }
            } catch (t: Throwable) {}
            if (!isBrightness) {
                try {
                    val itemM = holderClass.methods.firstOrNull { it.name.startsWith("getItem") }
                    val item = itemM?.invoke(holder)
                    if (item != null) {
                        if (item.javaClass.name.contains("Brightness")) {
                            isBrightness = true
                        } else {
                            val getTypeM = item.javaClass.getMethod("getType")
                            if (getTypeM.invoke(item) == 274442) isBrightness = true
                        }
                    }
                } catch (t: Throwable) {}
            }

            itemView.setTag(0x7f099990, if (isBrightness) "brightness" else "volume")
            val isHorizontal = if (isBrightness) ControlCenterConfigHolder.sBrightnessSliderHorizontal else ControlCenterConfigHolder.sVolumeSliderHorizontal

            var slider: android.widget.SeekBar? = null
            try {
                val sliderField = binding.javaClass.getField("slider")
                slider = sliderField.get(binding) as? android.widget.SeekBar
            } catch (t: Throwable) {
                try {
                    val getSliderM = binding.javaClass.getMethod("getSlider")
                    slider = getSliderM.invoke(binding) as? android.widget.SeekBar
                } catch (t2: Throwable) {}
            }

            if (slider != null) {
                try {
                    val getInjectorM = slider.javaClass.getDeclaredMethod("getInjector")
                    getInjectorM.isAccessible = true
                    val injector = getInjectorM.invoke(slider)
                    if (injector != null) {
                        val setVertM = injector.javaClass.getMethod("setVertical", Boolean::class.javaPrimitiveType)
                        // Both horizontal (rotation 0) and vertical (rotation 270) sliders track progress along
                        // the SeekBar's local X-axis. Keeping vertical = false ensures RelativeSeekBarInjector computes
                        // relative offset on X, preventing progress from jumping on touch.
                        setVertM.invoke(injector, false)
                    }
                } catch (t: Throwable) {}

                try {
                    val getDragAnimM = slider.javaClass.getMethod("getDragAnim")
                    val dragAnim = getDragAnimM.invoke(slider)
                    if (dragAnim != null) {
                        val setDragEnabledM = dragAnim.javaClass.getMethod("setDragEnabled", Boolean::class.javaPrimitiveType)
                        setDragEnabledM.invoke(dragAnim, !isHorizontal)
                    }
                } catch (t: Throwable) {}

                try {
                    val ghField = slider.javaClass.getDeclaredField("gestureHelper")
                    ghField.isAccessible = true
                    val gh = ghField.get(slider)
                    if (gh != null) {
                        var c: Class<*>? = gh.javaClass
                        while (c != null && c != Any::class.java) {
                            try {
                                val vf = c.getDeclaredField("vertical")
                                vf.isAccessible = true
                                vf.set(gh, java.lang.Boolean.valueOf(!isHorizontal))
                                break
                            } catch (e: NoSuchFieldException) {
                                c = c.superclass
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }

            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
            val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId) else (71.5f * density).toInt()
            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()
            val clipRoundId = ctx.resources.getIdentifier("toggle_slider_clip_round_corner_radius", "dimen", ctx.packageName)
            val stockProgressRadius = if (clipRoundId != 0) ctx.resources.getDimension(clipRoundId) else 0f

            try {
                val progressField = binding.javaClass.getField("progress")
                val progressView = progressField.get(binding) as? View
                if (progressView != null && slider != null) {
                    progressView.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val maxVal = slider.max.coerceAtLeast(1)
                            val curVal = slider.progress.coerceIn(0, maxVal)
                            val frac = curVal.toFloat() / maxVal
                            if (isHorizontal) {
                                val w = (view.width * frac).toInt()
                                outline.setRoundRect(0, 0, w, view.height, stockProgressRadius)
                            } else {
                                val top = (view.height * (1f - frac)).toInt()
                                outline.setRoundRect(0, top, view.width, view.height, stockProgressRadius)
                            }
                        }
                    }
                    progressView.invalidateOutline()
                }
            } catch (t: Throwable) {}

            val curLp = itemView.layoutParams
            if (curLp != null) {
                curLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                curLp.height = if (isHorizontal) cardH else tallH
                itemView.layoutParams = curLp
            }

            if (slider != null) {
                slider.setTag(0x7f099991, isHorizontal)
                val sLp = slider.layoutParams
                if (sLp != null) {
                    if (isHorizontal) {
                        slider.rotation = 0f
                        sLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        sLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        slider.rotation = 270f
                        sLp.width = tallH
                        sLp.height = cardH
                    }
                    slider.layoutParams = sLp
                }
            }

            if (isHorizontal) {
                try {
                    val iconField = binding.javaClass.getField("icon")
                    val iconView = iconField.get(binding) as? View
                    if (iconView != null) {
                        val iLp = iconView.layoutParams as? FrameLayout.LayoutParams
                        if (iLp != null) {
                            iLp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                            iLp.marginStart = (16 * density).toInt()
                            iLp.bottomMargin = 0
                            iconView.layoutParams = iLp
                        }
                    }
                } catch (t: Throwable) {}
            } else {
                try {
                    val iconField = binding.javaClass.getField("icon")
                    val iconView = iconField.get(binding) as? View
                    if (iconView != null) {
                        val iLp = iconView.layoutParams as? FrameLayout.LayoutParams
                        if (iLp != null) {
                            iLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                            iLp.marginStart = 0
                            iLp.bottomMargin = (16 * density).toInt()
                            iconView.layoutParams = iLp
                        }
                    }
                } catch (t: Throwable) {}
            }

            val spec = if (isBrightness) "brightness" else "volume"
            val isAdded = !ControlCenterConfigHolder.sRemovedComponentSpecs.contains(spec)

            if (ControlCenterConfigHolder.sIsEditMode) {
                applyUniversalMarkButton(itemView, spec, isAdded, holder)
            } else {
                val mark = itemView.findViewById<View>(UNIVERSAL_MARK_TAG)
                if (mark != null) {
                    mark.visibility = View.GONE
                    mark.setOnClickListener(null)
                    mark.isClickable = false
                }
            }

            val rotateTag = 0x7f099992
            var rotateBtn = itemView.findViewById<ImageView>(rotateTag)
            if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sIsEditMode) {
                val shouldShow = isAdded && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
                if (rotateBtn == null && itemView is ViewGroup) {
                    val rSize = (26 * density).toInt()
                    rotateBtn = ImageView(ctx).apply {
                        id = rotateTag
                        val lp = FrameLayout.LayoutParams(rSize, rSize).apply {
                            gravity = Gravity.TOP or Gravity.START
                            val m = (6 * density).toInt()
                            setMargins(m, m, 0, 0)
                        }
                        layoutParams = lp
                        setImageDrawable(RotateIconDrawable(density))
                        setOnClickListener {
                            ControlCenterConfigHolder.toggleSliderOrientation(ctx, isBrightness, itemView)
                        }
                    }
                    itemView.addView(rotateBtn)
                } else if (rotateBtn != null) {
                    val lp = rotateBtn.layoutParams as? FrameLayout.LayoutParams
                    if (lp != null && lp.gravity != (Gravity.TOP or Gravity.START)) {
                        lp.gravity = Gravity.TOP or Gravity.START
                        val m = (6 * density).toInt()
                        lp.setMargins(m, m, 0, 0)
                        rotateBtn.layoutParams = lp
                    }
                }
                if (rotateBtn != null) {
                    rotateBtn.visibility = if (shouldShow) View.VISIBLE else View.GONE
                    if (shouldShow) {
                        rotateBtn.bringToFront()
                        rotateBtn.isClickable = true
                        rotateBtn.setOnClickListener {
                            ControlCenterConfigHolder.toggleSliderOrientation(ctx, isBrightness, itemView)
                        }
                    } else {
                        rotateBtn.isClickable = false
                        rotateBtn.setOnClickListener(null)
                    }
                }
            } else {
                rotateBtn?.visibility = View.GONE
                rotateBtn?.setOnClickListener(null)
                rotateBtn?.isClickable = false
            }

            val resizeTag = 0x7f099999
            var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
            if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sIsEditMode && isHorizontal) {
                val badgeSize = (38 * density).toInt()
                if (resizeHandle == null && itemView is ViewGroup) {
                    resizeHandle = ImageView(ctx).apply {
                        id = resizeTag
                        layoutParams = ControlCenterConfigHolder.createMediaBadgeLayoutParams(itemView, badgeSize)
                        setImageDrawable(CurvedResizeHandleDrawable(density))
                    }
                    itemView.addView(resizeHandle)
                } else if (resizeHandle != null && ControlCenterConfigHolder.isConstraintLayout(itemView) && !resizeHandle.layoutParams.javaClass.name.contains("ConstraintLayout")) {
                    resizeHandle.layoutParams = ControlCenterConfigHolder.createMediaBadgeLayoutParams(itemView as ViewGroup, badgeSize)
                }
                if (resizeHandle != null) {
                    val shouldShowResize = isAdded && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
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
                            ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightness, itemView)
                        }
                        FluidTileResizeHandler.attach(resizeHandle, ctx, if (isBrightness) "brightness" else "volume", itemView, density)
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

            if (itemView.getTag(0x7f099993) == null) {
                itemView.setTag(0x7f099993, true)
                itemView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                    val curHandle = itemView.findViewById<ImageView>(0x7f099999)
                    if (curHandle != null && curHandle.visibility == View.VISIBLE) {
                        val w = r - l
                        val h = b - t
                        val bw = curHandle.measuredWidth.takeIf { it > 0 } ?: (38 * density).toInt()
                        val bh = curHandle.measuredHeight.takeIf { it > 0 } ?: (38 * density).toInt()
                        if (w > 0 && h > 0) {
                            curHandle.layout(w - bw, h - bh, w, h)
                            curHandle.bringToFront()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applySliderLayoutAndTouch error", t)
        }
    }

    class CollapsedMediaSeekBar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : View(context, attrs, defStyleAttr) {

        private val density = context.resources.displayMetrics.density
        var isVertical = false
        private var progressRatio = 0f
        private var isDragging = false
        private var dragProgress = 0f

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.FILL
        }

        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val bgRect = RectF()
        private val progressRect = RectF()

        private val updateRunnable = object : Runnable {
            override fun run() {
                if (!isDragging && isAttachedToWindow && visibility == View.VISIBLE) {
                    updateProgressFromSystem()
                }
                if (isAttachedToWindow) {
                    postDelayed(this, 500)
                }
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            post(updateRunnable)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            removeCallbacks(updateRunnable)
        }

        fun setProgressRatio(ratio: Float) {
            if (!isDragging) {
                val clamped = ratio.coerceIn(0f, 1f)
                if (Math.abs(progressRatio - clamped) > 0.001f) {
                    progressRatio = clamped
                    postInvalidateOnAnimation()
                }
            }
        }

        fun updateProgressFromSystem() {
            try {
                val cl = context.classLoader
                val vmClass = Class.forName("com.android.systemui.MiPlayDetailViewModel", false, cl)
                val vm = vmClass.getDeclaredField("INSTANCE").get(null)
                val posLiveData = vmClass.getMethod("getMPosition").invoke(vm)
                val pos = if (posLiveData != null) {
                    val getValM = posLiveData.javaClass.getMethod("getValue")
                    (getValM.invoke(posLiveData) as? Long) ?: 0L
                } else 0L

                val metaLiveData = vmClass.getMethod("getMMediaMetaData").invoke(vm)
                val meta = if (metaLiveData != null) {
                    val getValM = metaLiveData.javaClass.getMethod("getValue")
                    getValM.invoke(metaLiveData)
                } else ControlCenterHook.sLastMediaPlayerMetaData

                val duration = if (meta != null) {
                    try {
                        meta.javaClass.getMethod("getDuration").invoke(meta) as? Long ?: 0L
                    } catch (t: Throwable) { 0L }
                } else 0L

                if (duration > 0L) {
                    setProgressRatio(pos.toFloat() / duration.toFloat())
                } else {
                    setProgressRatio(0f)
                }
            } catch (t: Throwable) {}
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            if (isVertical) {
                val barW = if (isDragging) 5f * density else 3.5f * density
                val left = (w - barW) / 2f
                val right = left + barW
                val corner = barW / 2f

                bgRect.set(left, 0f, right, h)
                canvas.drawRoundRect(bgRect, corner, corner, bgPaint)

                val active = if (isDragging) dragProgress else progressRatio
                val fillH = (h * active).coerceIn(0f, h)
                val fillTop = h - fillH
                if (fillH > 0f) {
                    progressRect.set(left, fillTop, right, h)
                    canvas.drawRoundRect(progressRect, corner, corner, progressPaint)
                }

                if (isDragging) {
                    val thumbRadius = 4.5f * density
                    val thumbX = w / 2f
                    val thumbY = fillTop.coerceIn(thumbRadius, h - thumbRadius)
                    canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
                }
            } else {
                val barH = if (isDragging) 5f * density else 3.5f * density
                val top = (h - barH) / 2f
                val bottom = top + barH
                val corner = barH / 2f

                bgRect.set(0f, top, w, bottom)
                canvas.drawRoundRect(bgRect, corner, corner, bgPaint)

                val active = if (isDragging) dragProgress else progressRatio
                val fillW = (w * active).coerceIn(0f, w)
                if (fillW > 0f) {
                    progressRect.set(0f, top, fillW, bottom)
                    canvas.drawRoundRect(progressRect, corner, corner, progressPaint)
                }

                if (isDragging) {
                    val thumbRadius = 4.5f * density
                    val thumbX = fillW.coerceIn(thumbRadius, w - thumbRadius)
                    val thumbY = h / 2f
                    canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
                }
            }
        }

        private fun requestDisallowAll(disallow: Boolean) {
            var p = parent
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow)
                p = p.parent
            }
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                requestDisallowAll(true)
            }
            return super.dispatchTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val progressFromTouch = if (isVertical) {
                (1f - (event.y / height.toFloat())).coerceIn(0f, 1f)
            } else {
                (event.x / width.toFloat()).coerceIn(0f, 1f)
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    requestDisallowAll(true)
                    isDragging = true
                    dragProgress = progressFromTouch
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    requestDisallowAll(true)
                    dragProgress = progressFromTouch
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val finalRatio = progressFromTouch
                        progressRatio = finalRatio
                        isDragging = false
                        invalidate()
                        requestDisallowAll(false)
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            seekToRatio(finalRatio)
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun seekToRatio(ratio: Float) {
            try {
                val cl = context.classLoader
                val vmClass = Class.forName("com.android.systemui.MiPlayDetailViewModel", false, cl)
                val vm = vmClass.getDeclaredField("INSTANCE").get(null)
                val metaLiveData = vmClass.getMethod("getMMediaMetaData").invoke(vm)
                val meta = if (metaLiveData != null) {
                    val getValM = metaLiveData.javaClass.getMethod("getValue")
                    getValM.invoke(metaLiveData)
                } else ControlCenterHook.sLastMediaPlayerMetaData

                val duration = if (meta != null) {
                    try {
                        meta.javaClass.getMethod("getDuration").invoke(meta) as? Long ?: 0L
                    } catch (t: Throwable) { 0L }
                } else 0L

                if (duration > 0L) {
                    val targetMs = (ratio * duration).toLong()
                    seekMedia(targetMs, cl)
                }
            } catch (t: Throwable) {
                Log.e("CollapsedMediaSeekBar", "seek error", t)
            }
        }

        private fun seekMedia(targetMs: Long, cl: ClassLoader) {
            try {
                val miplayClass = Class.forName("miui.systemui.controlcenter.panel.main.media.MiPlayController", false, cl)
                val miplayInst = miplayClass.getDeclaredField("INSTANCE").get(null)
                val getMgrM = miplayClass.getMethod("getMIPLAY_AUDIO_MANAGER")
                val mgr = getMgrM.invoke(miplayInst)
                if (mgr != null) {
                    val listM = mgr.javaClass.getMethod("i")
                    val list = listM.invoke(mgr) as? List<*>
                    val firstSession = list?.firstOrNull()
                    if (firstSession != null) {
                        val bM = firstSession.javaClass.getMethod("b")
                        val w = bM.invoke(firstSession)
                        val xM = w.javaClass.getMethod("x", Long::class.javaPrimitiveType)
                        xM.invoke(w, targetMs)
                        return
                    }
                }
            } catch (t: Throwable) {}

            try {
                val mediaSessionMgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                val controllers = mediaSessionMgr?.getActiveSessions(null)
                controllers?.firstOrNull()?.transportControls?.seekTo(targetMs)
            } catch (t: Throwable) {}
        }
    }

    fun getHolderView(holder: Any, name: String): View? {
        return try {
            val bField = holder.javaClass.getDeclaredField("binding").apply { isAccessible = true }
            val binding = bField.get(holder) ?: return null
            val f = binding.javaClass.getField(name)
            f.get(binding) as? View
        } catch (t: Throwable) {
            try {
                val bField = holder.javaClass.getDeclaredField("binding").apply { isAccessible = true }
                val binding = bField.get(holder) ?: return null
                val m = binding.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
                m.invoke(binding) as? View
            } catch (t2: Throwable) { null }
        }
    }

    fun getHolderItemView(holder: Any): View? {
        return try {
            holder.javaClass.getField("itemView").get(holder) as? View
        } catch (t: Throwable) {
            try {
                val f = holder.javaClass.getDeclaredField("itemView").apply { isAccessible = true }
                f.get(holder) as? View
            } catch (t2: Throwable) { null }
        }
    }

    fun getChildView(root: View, name: String): View? {
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val entryName = try { root.resources.getResourceEntryName(child.id) } catch (t: Throwable) { "" }
            if (entryName == name) return child
            if (child is ViewGroup) {
                val nested = getChildView(child, name)
                if (nested != null) return nested
            }
        }
        return null
    }

    fun handleMediaPanelTransitionFrame(animator: Any, classLoader: ClassLoader) {
        try {
            val animClass = animator.javaClass
            val animValueField = animClass.getDeclaredField("animValue").apply { isAccessible = true }
            val animValue = animValueField.get(animator) ?: return

            val contentCtrlField = animClass.getDeclaredField("contentController").apply { isAccessible = true }
            val contentCtrl = contentCtrlField.get(animator) ?: return

            val sizeField = animClass.getDeclaredField("size").apply { isAccessible = true }
            val progress = sizeField.getFloat(animator)

            val alphaProgField = animClass.getDeclaredField("alphaProgress").apply { isAccessible = true }
            val alphaProgress = alphaProgField.getFloat(animator)

            val alphaDevField = animClass.getDeclaredField("alphaDeviceIcon").apply { isAccessible = true }
            val alphaDeviceIcon = alphaDevField.getFloat(animator)

            val currentTileSize = ControlCenterConfigHolder.sMediaPlayerSize

            fun getCtrlView(methodName: String): View? {
                return try {
                    contentCtrl.javaClass.getMethod(methodName).invoke(contentCtrl) as? View
                } catch (t: Throwable) { null }
            }

            fun getAnimVal(methodName: String): Any? {
                return try {
                    animValue.javaClass.getMethod(methodName).invoke(animValue)
                } catch (t: Throwable) { null }
            }

            // 1. Components never in collapsed: VolumeBar, TvContainer, ListView
            val volView = getCtrlView("getVolumeBarContainerView")
            val toVol = getAnimVal("getToVolumeContainer")
            if (volView != null && toVol != null) {
                val top = toVol.javaClass.getMethod("getTop").invoke(toVol) as Int
                volView.scaleX = 1f
                volView.scaleY = 1f
                volView.top = top
                volView.alpha = alphaProgress
            }

            val tvView = getCtrlView("getTvContainerView")
            val toTv = getAnimVal("getToTvContainer")
            if (tvView != null && toTv != null && tvView.visibility == View.VISIBLE) {
                val top = toTv.javaClass.getMethod("getTop").invoke(toTv) as Int
                tvView.scaleX = 1f
                tvView.scaleY = 1f
                tvView.top = top
                tvView.alpha = alphaProgress
            }

            val listView = getCtrlView("getListView")
            if (listView != null) {
                listView.scaleX = 1f
                listView.scaleY = 1f
                listView.alpha = alphaProgress
            }

            // 2. Progress container (Seek Bar)
            val progView = getCtrlView("getProgressContainerView")
            val toProg = getAnimVal("getToProgressContainer")
            val pCols = currentTileSize.split("x").firstOrNull()?.toIntOrNull() ?: 2
            val pRows = currentTileSize.split("x").getOrNull(1)?.toIntOrNull() ?: 2
            val hasCollapsedProgress = (pCols > 1 && (pCols >= 3 || pRows >= 3))
            if (progView != null && toProg != null) {
                val toTop = toProg.javaClass.getMethod("getTop").invoke(toProg) as Int
                if (!hasCollapsedProgress) {
                    progView.scaleX = 1f
                    progView.scaleY = 1f
                    progView.top = toTop
                    progView.alpha = alphaProgress
                } else {
                    val holder = sMediaPlayerViewHolder?.get()
                    val itemView = holder?.let { getHolderItemView(it) }
                    val collapsedSeek = itemView?.findViewById<View>(0x7f099988)
                    if (collapsedSeek != null && collapsedSeek.visibility == View.VISIBLE && collapsedSeek.width > 0) {
                        val locC = IntArray(2)
                        collapsedSeek.getLocationInWindow(locC)
                        val parentView = progView.parent as? View
                        if (parentView != null) {
                            val locParent = IntArray(2)
                            parentView.getLocationInWindow(locParent)
                            val fromTopInParent = (locC[1] - locParent[1]).toFloat()
                            val fromLeftInParent = (locC[0] - locParent[0]).toFloat()
                            val fromW = collapsedSeek.width.toFloat()
                            val toW = (toProg.javaClass.getMethod("getWidth").invoke(toProg) as Int).toFloat()
                            val toLeft = (try { toProg.javaClass.getMethod("getLeft").invoke(toProg) as Int } catch (t: Throwable) { progView.left }).toFloat()

                            val curTop = fromTopInParent + (toTop.toFloat() - fromTopInParent) * progress
                            val curLeft = fromLeftInParent + (toLeft - fromLeftInParent) * progress
                            val curScaleX = if (toW > 0f) (fromW + (toW - fromW) * progress) / toW else 1f

                            progView.layout(curLeft.toInt(), curTop.toInt(), (curLeft + toW).toInt(), curTop.toInt() + progView.height)
                            progView.pivotX = 0f
                            progView.pivotY = 0f
                            progView.scaleX = curScaleX
                            progView.scaleY = 1f
                            progView.alpha = 1f
                        }
                    }
                }
            }

            // 3. Views not present in 2x1 and 1x2 (title, subtitle, emptyState)
            if (currentTileSize == "2x1" || currentTileSize == "1x2") {
                val titleView = getCtrlView("getTitleView")
                val toTitle = getAnimVal("getToTitle")
                if (titleView != null && toTitle != null) {
                    val tL = toTitle.javaClass.getMethod("getLeft").invoke(toTitle) as Int
                    val tT = toTitle.javaClass.getMethod("getTop").invoke(toTitle) as Int
                    val tW = toTitle.javaClass.getMethod("getWidth").invoke(toTitle) as Int
                    val tH = toTitle.javaClass.getMethod("getHeight").invoke(toTitle) as Int
                    titleView.layout(tL, tT, tL + tW, tT + tH)
                    titleView.scaleX = 1f
                    titleView.scaleY = 1f
                    titleView.alpha = alphaProgress
                }

                val subView = getCtrlView("getSubtitleView")
                val toSub = getAnimVal("getToSubtitle")
                if (subView != null && toSub != null) {
                    val sL = toSub.javaClass.getMethod("getLeft").invoke(toSub) as Int
                    val sT = toSub.javaClass.getMethod("getTop").invoke(toSub) as Int
                    val sW = toSub.javaClass.getMethod("getWidth").invoke(toSub) as Int
                    val sH = toSub.javaClass.getMethod("getHeight").invoke(toSub) as Int
                    subView.layout(sL, sT, sL + sW, sT + sH)
                    subView.scaleX = 1f
                    subView.scaleY = 1f
                    subView.alpha = alphaProgress
                }

                val noPlayView = getCtrlView("getNoPlayView")
                val toNoPlay = getAnimVal("getToNoPlay")
                if (noPlayView != null && toNoPlay != null) {
                    val nL = toNoPlay.javaClass.getMethod("getLeft").invoke(toNoPlay) as Int
                    val nT = toNoPlay.javaClass.getMethod("getTop").invoke(toNoPlay) as Int
                    val nW = toNoPlay.javaClass.getMethod("getWidth").invoke(toNoPlay) as Int
                    val nH = toNoPlay.javaClass.getMethod("getHeight").invoke(toNoPlay) as Int
                    noPlayView.layout(nL, nT, nL + nW, nT + nH)
                    noPlayView.scaleX = 1f
                    noPlayView.scaleY = 1f
                    noPlayView.alpha = alphaProgress
                }
            }

            // 4. Device icon
            if (currentTileSize == "2x1" || currentTileSize == "1x2" || currentTileSize == "4x1") {
                val diView = getCtrlView("getDeviceIconView")
                if (diView != null) {
                    diView.scaleX = 1f
                    diView.scaleY = 1f
                    diView.alpha = alphaDeviceIcon
                }
            }

            val holder = sMediaPlayerViewHolder?.get()
            val itemView = holder?.let { getHolderItemView(it) }
            val prevBtn = itemView?.let { getChildView(it, "prev") }
            val playBtn = itemView?.let { getChildView(it, "play") }
            val nextBtn = itemView?.let { getChildView(it, "next") }

            val avPrev = getCtrlView("getAvPrevView")
            val avPlay = getCtrlView("getAvPlayView")
            val avNext = getCtrlView("getAvNextView")

            val density = (itemView?.context ?: (progView ?: volView)?.context)?.resources?.displayMetrics?.density ?: 3f
            val toBtnW = 40f * density

            if (currentTileSize == "4x1" || currentTileSize == "4x2" || currentTileSize == "2x4") {
                val fromBtnW = prevBtn?.width?.toFloat()?.takeIf { it > 0f } ?: (26f * density)
                if (toBtnW > 0f) {
                    val curBtnScale = (fromBtnW + (toBtnW - fromBtnW) * progress) / toBtnW
                    avPrev?.scaleX = curBtnScale
                    avPrev?.scaleY = curBtnScale
                    avPlay?.scaleX = curBtnScale
                    avPlay?.scaleY = curBtnScale
                    avNext?.scaleX = curBtnScale
                    avNext?.scaleY = curBtnScale
                }
            }

            // 5. 1x2 Vertical Buttons interpolation
            if (currentTileSize == "1x2") {
                if (avPrev != null && prevBtn != null) {
                    val topP = (prevBtn.top * (1f - progress)).toInt()
                    avPrev.layout(avPrev.left, topP, avPrev.right, topP + avPrev.height)
                }
                if (avPlay != null && playBtn != null) {
                    val topPl = (playBtn.top * (1f - progress)).toInt()
                    avPlay.layout(avPlay.left, topPl, avPlay.right, topPl + avPlay.height)
                }
                if (avNext != null && nextBtn != null) {
                    val topN = (nextBtn.top * (1f - progress)).toInt()
                    avNext.layout(avNext.left, topN, avNext.right, topN + avNext.height)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleMediaPanelTransitionFrame error", t)
        }
    }

    fun applyMediaPlayerLayout(holder: Any) {
        try {
            val holderClass = holder.javaClass
            var binding: Any? = null
            try {
                val bindingField = holderClass.getDeclaredField("binding")
                bindingField.isAccessible = true
                binding = bindingField.get(holder)
            } catch (t: Throwable) {
                try {
                    val getBindingM = holderClass.getMethod("getBinding")
                    binding = getBindingM.invoke(holder)
                } catch (t2: Throwable) {}
            }
            if (binding == null) return

            val itemView = try {
                holderClass.getField("itemView").get(holder) as? View
            } catch (t: Throwable) {
                try {
                    val f = holderClass.getDeclaredField("itemView")
                    f.isAccessible = true
                    f.get(holder) as? View
                } catch (t2: Throwable) { null }
            } ?: return

            itemView.setTag(0x7f099990, "media")
            sMediaPlayerViewHolder = java.lang.ref.WeakReference(holder)

            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
            val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId) else (71.5f * density).toInt()
            val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
            val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId) else (157 * density).toInt()

            var size = ControlCenterConfigHolder.sMediaPlayerSize
            val mediaCols = size.split("x").firstOrNull()?.toIntOrNull()?.coerceIn(1, 4) ?: 2
            val mediaRows = size.split("x").getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 2

            fun getView(name: String): View? {
                return try {
                    val f = binding.javaClass.getField(name)
                    f.get(binding) as? View
                } catch (t: Throwable) {
                    try {
                        val m = binding.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
                        m.invoke(binding) as? View
                    } catch (t2: Throwable) { null }
                }
            }

            val cover = getView("cover")
            val title = getView("title")
            val artist = getView("artist")
            val play = getView("play")
            val prev = getView("prev")
            val next = getView("next")
            val deviceIcon = getView("deviceIcon")
            val emptyState = getView("emptyState")
            val audioContainer = getView("audioContainer")

            val progressTag = 0x7f099988
            var progressBar = itemView.findViewById<View>(progressTag)
            if (progressBar == null && itemView is ViewGroup) {
                progressBar = CollapsedMediaSeekBar(ctx).apply {
                    id = progressTag
                }
                itemView.addView(progressBar)
            }

            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
            val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (14 * density).toInt()
            val threeRowsH = 3 * cardH + 2 * gap
            val fourRowsH = 4 * cardH + 3 * gap
            val fiveRowsH = 5 * cardH + 4 * gap

            val rowHeight = when (mediaRows) {
                1 -> cardH
                2 -> tallH
                3 -> threeRowsH
                4 -> fourRowsH
                5 -> fiveRowsH
                else -> tallH
            }

            val curLp = itemView.layoutParams
            if (curLp != null) {
                curLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                if (curLp.height != rowHeight) {
                    curLp.height = rowHeight
                    itemView.layoutParams = curLp
                }
            }
            fun ensureId(v: View?): Int {
                if (v == null) return View.NO_ID
                if (v.id == View.NO_ID || v.id == 0) {
                    v.id = View.generateViewId()
                }
                return v.id
            }

            val coverId = ensureId(cover)
            val playId = ensureId(play)
            val prevId = ensureId(prev)
            val nextId = ensureId(next)
            val titleId = ensureId(title)
            val artistId = ensureId(artist)
            val deviceIconId = ensureId(deviceIcon)
            val emptyStateId = ensureId(emptyState)
            val audioContainerId = ensureId(audioContainer)
            val progressBarId = ensureId(progressBar)

            val meta = sLastMediaPlayerMetaData
            var hasTrack = false
            if (meta != null) {
                try {
                    val titleProp = meta.javaClass.getMethod("getTitle").invoke(meta) as? CharSequence
                    if (!titleProp.isNullOrBlank()) {
                        hasTrack = true
                    }
                } catch (t: Throwable) {
                    hasTrack = true
                }
            }
            if (!hasTrack && title is TextView && !title.text.isNullOrBlank() && title.visibility == View.VISIBLE) {
                hasTrack = true
            }

            if (emptyState is TextView) {
                val noSongResId = ctx.resources.getIdentifier("miplay_detail_header_no_song", "string", ctx.packageName)
                if (noSongResId != 0) {
                    emptyState.setText(noSongResId)
                } else {
                    emptyState.text = "Not playing"
                }
            }

            if (cover is ImageView) {
                if (cover.drawable == null || !hasTrack) {
                    val defCoverId = ctx.resources.getIdentifier("miplay_cover_default", "drawable", ctx.packageName)
                    if (defCoverId != 0) cover.setImageResource(defCoverId)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && cover != null) {
                val isCircular = (mediaCols == 1 || mediaRows == 1)
                val rad = if (mediaCols >= 4 && mediaRows >= 2) 18f * density else 14f * density
                cover.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        if (isCircular) {
                            val minDim = Math.min(v.width, v.height)
                            val left = (v.width - minDim) / 2
                            val top = (v.height - minDim) / 2
                            o.setRoundRect(left, top, left + minDim, top + minDim, minDim / 2f)
                        } else {
                            o.setRoundRect(0, 0, v.width, v.height, rad)
                        }
                    }
                }
                cover.clipToOutline = true
            }

            listOf(cover, play, prev, next, audioContainer, progressBar).forEach { v ->
                v?.alpha = 1f
                v?.scaleX = 1f
                v?.scaleY = 1f
                v?.rotationY = 0f
                v?.translationX = 0f
                v?.translationY = 0f
            }

            if (size == "1x1") {
                cover?.visibility = View.VISIBLE
                prev?.visibility = View.GONE
                play?.visibility = View.GONE
                next?.visibility = View.GONE
                audioContainer?.visibility = View.GONE
                title?.visibility = View.GONE
                artist?.visibility = View.GONE
                emptyState?.visibility = View.GONE
                deviceIcon?.visibility = View.GONE
                progressBar?.visibility = View.GONE
            } else {
                cover?.visibility = View.VISIBLE
                prev?.visibility = View.VISIBLE
                play?.visibility = View.VISIBLE
                next?.visibility = View.VISIBLE
                audioContainer?.visibility = View.VISIBLE
            }

            if (ControlCenterConfigHolder.isConstraintLayout(itemView)) {
                try {
                    val clpClass = itemView.javaClass.classLoader?.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                        ?: Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams")
                    val pId = clpClass.getField("PARENT_ID").getInt(null)
                    val unset = clpClass.getField("UNSET").getInt(null)

                    fun setField(obj: Any, field: String, value: Int) {
                        try { clpClass.getField(field).setInt(obj, value) } catch (t: Throwable) {}
                    }

                    fun resetConstraints(lp: Any) {
                        listOf(
                            "startToStart", "startToEnd", "endToStart", "endToEnd",
                            "topToTop", "topToBottom", "bottomToTop", "bottomToBottom",
                            "horizontalChainStyle", "verticalChainStyle"
                        ).forEach { fName ->
                            setField(lp, fName, unset)
                        }
                        if (lp is ViewGroup.MarginLayoutParams) {
                            lp.marginStart = 0
                            lp.marginEnd = 0
                            lp.topMargin = 0
                            lp.bottomMargin = 0
                        }
                    }

                    val cLp = cover?.layoutParams
                    val acLp = audioContainer?.layoutParams
                    val pbLp = progressBar?.layoutParams
                    val prevLp = prev?.layoutParams
                    val playLp = play?.layoutParams
                    val nextLp = next?.layoutParams
                    val tLp = title?.layoutParams
                    val aLp = artist?.layoutParams
                    val eLp = emptyState?.layoutParams
                    val diLp = deviceIcon?.layoutParams

                    if (size != "1x4" && size != "1x5") {
                        (progressBar as? CollapsedMediaSeekBar)?.isVertical = false
                    }

                    when (size) {
                        "1x1" -> {
                            title?.visibility = View.GONE
                            artist?.visibility = View.GONE
                            emptyState?.visibility = View.GONE
                            deviceIcon?.visibility = View.GONE
                            progressBar?.visibility = View.GONE
                            audioContainer?.visibility = View.GONE
                            prev?.visibility = View.GONE
                            play?.visibility = View.GONE
                            next?.visibility = View.GONE

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "endToEnd", pId)
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                cover.layoutParams = cLp
                            }
                        }

                        "2x1" -> {
                            title?.visibility = View.GONE
                            artist?.visibility = View.GONE
                            emptyState?.visibility = View.GONE
                            deviceIcon?.visibility = View.GONE
                            progressBar?.visibility = View.GONE

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = 0
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToEnd", coverId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "topToTop", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.marginStart = (8 * density).toInt()
                                    acLp.marginEnd = (14 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (27 * density).toInt()
                            val playSize = (28 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (5 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (5 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }
                        }

                        "1x2" -> {
                            title?.visibility = View.GONE
                            artist?.visibility = View.GONE
                            emptyState?.visibility = View.GONE
                            deviceIcon?.visibility = View.GONE
                            progressBar?.visibility = View.GONE

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "endToEnd", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.topMargin = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                acLp.height = 0
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "topToBottom", coverId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.topMargin = (8 * density).toInt()
                                    acLp.bottomMargin = (12 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (27 * density).toInt()
                            val playSize = (28 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToEnd", pId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToTop", playId)
                                setField(prevLp, "verticalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.bottomMargin = (6 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToStart", pId)
                                setField(playLp, "endToEnd", pId)
                                setField(playLp, "topToBottom", prevId)
                                setField(playLp, "bottomToTop", nextId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToStart", pId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToBottom", playId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.topMargin = (6 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }
                        }

                        "1x3" -> {
                            deviceIcon?.visibility = View.GONE
                            progressBar?.visibility = View.GONE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                            }

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "endToEnd", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.topMargin = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (14 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (27 * density).toInt()
                            val playSize = (28 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToEnd", pId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToTop", playId)
                                setField(prevLp, "verticalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.bottomMargin = (6 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToStart", pId)
                                setField(playLp, "endToEnd", pId)
                                setField(playLp, "topToBottom", prevId)
                                setField(playLp, "bottomToTop", nextId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToStart", pId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToBottom", playId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.topMargin = (6 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToStart", pId)
                                    setField(tLp, "endToEnd", pId)
                                    setField(tLp, "topToBottom", coverId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (6 * density).toInt()
                                        tLp.marginEnd = (6 * density).toInt()
                                        tLp.topMargin = (10 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        title.gravity = Gravity.CENTER
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToStart", pId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", audioContainerId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (6 * density).toInt()
                                        aLp.marginEnd = (6 * density).toInt()
                                        aLp.topMargin = (2 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        artist.gravity = Gravity.CENTER
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToStart", pId)
                                    setField(eLp, "endToEnd", pId)
                                    setField(eLp, "topToBottom", coverId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (6 * density).toInt()
                                        eLp.marginEnd = (6 * density).toInt()
                                        eLp.topMargin = (10 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        emptyState.gravity = Gravity.CENTER
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }
                        }

                        "1x4", "1x5" -> {
                            deviceIcon?.visibility = View.GONE
                            (progressBar as? CollapsedMediaSeekBar)?.isVertical = true
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                                (progressBar as? CollapsedMediaSeekBar)?.updateProgressFromSystem()
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "endToEnd", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.topMargin = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (14 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (27 * density).toInt()
                            val playSize = (28 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToEnd", pId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToTop", playId)
                                setField(prevLp, "verticalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.bottomMargin = (6 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToStart", pId)
                                setField(playLp, "endToEnd", pId)
                                setField(playLp, "topToBottom", prevId)
                                setField(playLp, "bottomToTop", nextId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToStart", pId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToBottom", playId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.topMargin = (6 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToStart", pId)
                                    setField(tLp, "endToEnd", pId)
                                    setField(tLp, "topToBottom", coverId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (6 * density).toInt()
                                        tLp.marginEnd = (6 * density).toInt()
                                        tLp.topMargin = (8 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        title.gravity = Gravity.CENTER
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToStart", pId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", progressBarId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (6 * density).toInt()
                                        aLp.marginEnd = (6 * density).toInt()
                                        aLp.topMargin = (2 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        artist.gravity = Gravity.CENTER
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                                if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout") && progressBar != null) {
                                    resetConstraints(pbLp)
                                    pbLp.width = (32 * density).toInt()
                                    val pbH = if (size == "1x5") (90 * density).toInt() else (56 * density).toInt()
                                    pbLp.height = pbH
                                    setField(pbLp, "startToStart", pId)
                                    setField(pbLp, "endToEnd", pId)
                                    setField(pbLp, "topToBottom", artistId)
                                    setField(pbLp, "bottomToTop", audioContainerId)
                                    if (pbLp is ViewGroup.MarginLayoutParams) {
                                        pbLp.topMargin = (6 * density).toInt()
                                        pbLp.bottomMargin = (6 * density).toInt()
                                    }
                                    progressBar.layoutParams = pbLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToStart", pId)
                                    setField(eLp, "endToEnd", pId)
                                    setField(eLp, "topToBottom", coverId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (6 * density).toInt()
                                        eLp.marginEnd = (6 * density).toInt()
                                        eLp.topMargin = (10 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        emptyState.gravity = Gravity.CENTER
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }
                        }

                        "3x1" -> {
                            deviceIcon?.visibility = View.GONE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (46 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            val btnSize = (26 * density).toInt()
                            val playSize = (28 * density).toInt()

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "topToTop", pId)
                                if (hasTrack) {
                                    setField(acLp, "bottomToTop", progressBarId)
                                    setField(acLp, "verticalChainStyle", 2)
                                } else {
                                    setField(acLp, "bottomToBottom", pId)
                                }
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.marginEnd = (12 * density).toInt()
                                    acLp.topMargin = if (hasTrack) (6 * density).toInt() else 0
                                }
                                audioContainer.layoutParams = acLp
                            }

                            if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(pbLp)
                                pbLp.width = 0
                                pbLp.height = (12 * density).toInt()
                                setField(pbLp, "startToStart", audioContainerId)
                                setField(pbLp, "endToEnd", audioContainerId)
                                setField(pbLp, "topToBottom", audioContainerId)
                                setField(pbLp, "bottomToBottom", pId)
                                if (pbLp is ViewGroup.MarginLayoutParams) {
                                    pbLp.bottomMargin = (6 * density).toInt()
                                }
                                progressBar.layoutParams = pbLp
                            }

                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (4 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (4 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToEnd", coverId)
                                    setField(tLp, "endToStart", audioContainerId)
                                    setField(tLp, "topToTop", pId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (10 * density).toInt()
                                        tLp.marginEnd = (8 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToEnd", coverId)
                                    setField(aLp, "endToStart", audioContainerId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToBottom", pId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (10 * density).toInt()
                                        aLp.marginEnd = (8 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToEnd", coverId)
                                    setField(eLp, "endToStart", audioContainerId)
                                    setField(eLp, "topToTop", pId)
                                    setField(eLp, "bottomToBottom", pId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (10 * density).toInt()
                                        eLp.marginEnd = (8 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }
                        }

                        "4x1" -> {
                            if (curLp != null && curLp.height != cardH) {
                                curLp.height = cardH
                                itemView.layoutParams = curLp
                            }
                            deviceIcon?.visibility = View.GONE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (48 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            val btnSize = (26 * density).toInt()
                            val playSize = (30 * density).toInt()

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "topToTop", pId)
                                if (hasTrack) {
                                    setField(acLp, "bottomToTop", progressBarId)
                                    setField(acLp, "verticalChainStyle", 2)
                                } else {
                                    setField(acLp, "bottomToBottom", pId)
                                }
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.marginEnd = (14 * density).toInt()
                                    acLp.topMargin = if (hasTrack) (6 * density).toInt() else 0
                                }
                                audioContainer.layoutParams = acLp
                            }

                            if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(pbLp)
                                pbLp.width = 0
                                pbLp.height = (12 * density).toInt()
                                setField(pbLp, "startToStart", audioContainerId)
                                setField(pbLp, "endToEnd", audioContainerId)
                                setField(pbLp, "topToBottom", audioContainerId)
                                setField(pbLp, "bottomToBottom", pId)
                                if (pbLp is ViewGroup.MarginLayoutParams) {
                                    pbLp.bottomMargin = (6 * density).toInt()
                                }
                                progressBar.layoutParams = pbLp
                            }

                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (4 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (4 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToEnd", coverId)
                                    setField(tLp, "endToStart", audioContainerId)
                                    setField(tLp, "topToTop", pId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (12 * density).toInt()
                                        tLp.marginEnd = (10 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToEnd", coverId)
                                    setField(aLp, "endToStart", audioContainerId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToBottom", pId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (12 * density).toInt()
                                        aLp.marginEnd = (10 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToEnd", coverId)
                                    setField(eLp, "endToStart", audioContainerId)
                                    setField(eLp, "topToTop", pId)
                                    setField(eLp, "bottomToBottom", pId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (12 * density).toInt()
                                        eLp.marginEnd = (10 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }
                        }

                        "2x3" -> {
                            deviceIcon?.visibility = View.VISIBLE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            val coverSize = (64 * density).toInt()
                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                cLp.width = coverSize
                                cLp.height = coverSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                    cLp.topMargin = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (diLp != null && diLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(diLp)
                                diLp.width = (24 * density).toInt()
                                diLp.height = (24 * density).toInt()
                                setField(diLp, "topToTop", pId)
                                setField(diLp, "endToEnd", pId)
                                if (diLp is ViewGroup.MarginLayoutParams) {
                                    diLp.topMargin = (14 * density).toInt()
                                    diLp.marginEnd = (14 * density).toInt()
                                }
                                deviceIcon.layoutParams = diLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToStart", pId)
                                    setField(tLp, "endToEnd", pId)
                                    setField(tLp, "topToBottom", coverId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (14 * density).toInt()
                                        tLp.marginEnd = (14 * density).toInt()
                                        tLp.topMargin = (12 * density).toInt()
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToStart", pId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", progressBarId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (14 * density).toInt()
                                        aLp.marginEnd = (14 * density).toInt()
                                        aLp.topMargin = (2 * density).toInt()
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToStart", pId)
                                    setField(eLp, "endToEnd", pId)
                                    setField(eLp, "topToBottom", coverId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (14 * density).toInt()
                                        eLp.marginEnd = (14 * density).toInt()
                                        eLp.topMargin = (14 * density).toInt()
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }

                            if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(pbLp)
                                pbLp.width = 0
                                pbLp.height = (12 * density).toInt()
                                setField(pbLp, "startToStart", pId)
                                setField(pbLp, "endToEnd", pId)
                                setField(pbLp, "bottomToTop", audioContainerId)
                                if (pbLp is ViewGroup.MarginLayoutParams) {
                                    pbLp.marginStart = (16 * density).toInt()
                                    pbLp.marginEnd = (16 * density).toInt()
                                    pbLp.bottomMargin = (6 * density).toInt()
                                }
                                progressBar.layoutParams = pbLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (12 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (28 * density).toInt()
                            val playSize = (34 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (10 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (10 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }
                        }

                        "3x2", "3x3", "4x2", "4x3" -> {
                            deviceIcon?.visibility = View.VISIBLE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            val coverSize = if (mediaCols >= 4) (129 * density).toInt() else (100 * density).toInt()
                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                cLp.width = coverSize
                                cLp.height = coverSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                    cLp.topMargin = (14 * density).toInt()
                                    cLp.bottomMargin = (14 * density).toInt()
                                }
                                if (cover is ImageView) {
                                    cover.scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                                cover.layoutParams = cLp
                            }

                            if (diLp != null && diLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(diLp)
                                diLp.width = (30 * density).toInt()
                                diLp.height = (30 * density).toInt()
                                setField(diLp, "topToTop", pId)
                                setField(diLp, "endToEnd", pId)
                                if (diLp is ViewGroup.MarginLayoutParams) {
                                    diLp.topMargin = (14 * density).toInt()
                                    diLp.marginEnd = (14 * density).toInt()
                                }
                                deviceIcon.layoutParams = diLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = 0
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToEnd", coverId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.marginStart = (16 * density).toInt()
                                    acLp.marginEnd = (16 * density).toInt()
                                    acLp.bottomMargin = (14 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(pbLp)
                                pbLp.width = 0
                                pbLp.height = (14 * density).toInt()
                                setField(pbLp, "startToEnd", coverId)
                                setField(pbLp, "endToEnd", pId)
                                setField(pbLp, "bottomToTop", audioContainerId)
                                if (pbLp is ViewGroup.MarginLayoutParams) {
                                    pbLp.marginStart = (16 * density).toInt()
                                    pbLp.marginEnd = (16 * density).toInt()
                                    pbLp.bottomMargin = (4 * density).toInt()
                                }
                                progressBar.layoutParams = pbLp
                            }

                            val btnSize = (36 * density).toInt()
                            val playSize = (42 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (6 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (6 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToEnd", coverId)
                                    setField(tLp, "endToStart", deviceIconId)
                                    setField(tLp, "topToTop", pId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (16 * density).toInt()
                                        tLp.marginEnd = (8 * density).toInt()
                                        tLp.topMargin = (16 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToEnd", coverId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", progressBarId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (16 * density).toInt()
                                        aLp.marginEnd = (16 * density).toInt()
                                        aLp.topMargin = (4 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToEnd", coverId)
                                    setField(eLp, "endToStart", deviceIconId)
                                    setField(eLp, "topToTop", pId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (16 * density).toInt()
                                        eLp.marginEnd = (8 * density).toInt()
                                        eLp.topMargin = (24 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }
                        }

                        "2x4", "2x5" -> {
                            deviceIcon?.visibility = View.VISIBLE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                                progressBar?.visibility = View.VISIBLE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                                progressBar?.visibility = View.GONE
                            }

                            val coverH = (130 * density).toInt()
                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                cLp.width = 0
                                cLp.height = coverH
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "endToEnd", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                    cLp.marginEnd = (14 * density).toInt()
                                    cLp.topMargin = (14 * density).toInt()
                                }
                                if (cover is ImageView) {
                                    cover.scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                                cover.layoutParams = cLp
                            }

                            if (diLp != null && diLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(diLp)
                                diLp.width = (30 * density).toInt()
                                diLp.height = (30 * density).toInt()
                                setField(diLp, "topToTop", coverId)
                                setField(diLp, "endToEnd", coverId)
                                if (diLp is ViewGroup.MarginLayoutParams) {
                                    diLp.topMargin = (8 * density).toInt()
                                    diLp.marginEnd = (8 * density).toInt()
                                }
                                deviceIcon.layoutParams = diLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToStart", pId)
                                    setField(tLp, "endToEnd", pId)
                                    setField(tLp, "topToBottom", coverId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (14 * density).toInt()
                                        tLp.marginEnd = (14 * density).toInt()
                                        tLp.topMargin = (14 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        title.gravity = Gravity.CENTER
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToStart", pId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", progressBarId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (14 * density).toInt()
                                        aLp.marginEnd = (14 * density).toInt()
                                        aLp.topMargin = (4 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        artist.gravity = Gravity.CENTER
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToStart", pId)
                                    setField(eLp, "endToEnd", pId)
                                    setField(eLp, "topToBottom", coverId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (14 * density).toInt()
                                        eLp.marginEnd = (14 * density).toInt()
                                        eLp.topMargin = (20 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.textAlignment = View.TEXT_ALIGNMENT_CENTER
                                        emptyState.gravity = Gravity.CENTER
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }

                            if (pbLp != null && pbLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(pbLp)
                                pbLp.width = 0
                                pbLp.height = (14 * density).toInt()
                                setField(pbLp, "startToStart", pId)
                                setField(pbLp, "endToEnd", pId)
                                setField(pbLp, "topToBottom", artistId)
                                setField(pbLp, "bottomToTop", audioContainerId)
                                if (pbLp is ViewGroup.MarginLayoutParams) {
                                    pbLp.marginStart = (20 * density).toInt()
                                    pbLp.marginEnd = (20 * density).toInt()
                                    pbLp.topMargin = (4 * density).toInt()
                                    pbLp.bottomMargin = (6 * density).toInt()
                                }
                                progressBar.layoutParams = pbLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (20 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (34 * density).toInt()
                            val playSize = (40 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 2)
                                if (prevLp is ViewGroup.MarginLayoutParams) {
                                    prevLp.marginEnd = (6 * density).toInt()
                                }
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = playSize
                                playLp.height = playSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                if (nextLp is ViewGroup.MarginLayoutParams) {
                                    nextLp.marginStart = (6 * density).toInt()
                                }
                                next.layoutParams = nextLp
                            }
                        }

                        else -> { // "2x2" Stock
                            if (curLp != null && curLp.height != tallH) {
                                curLp.height = tallH
                                itemView.layoutParams = curLp
                            }
                            deviceIcon?.visibility = View.VISIBLE
                            progressBar?.visibility = View.GONE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                            }

                            if (cLp != null && cLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(cLp)
                                val cSize = (42 * density).toInt()
                                cLp.width = cSize
                                cLp.height = cSize
                                setField(cLp, "startToStart", pId)
                                setField(cLp, "topToTop", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (15 * density).toInt()
                                    cLp.topMargin = (15 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (diLp != null && diLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(diLp)
                                diLp.width = (30 * density).toInt()
                                diLp.height = (30 * density).toInt()
                                setField(diLp, "topToTop", pId)
                                setField(diLp, "endToEnd", pId)
                                if (diLp is ViewGroup.MarginLayoutParams) {
                                    diLp.topMargin = (15 * density).toInt()
                                    diLp.marginEnd = (15 * density).toInt()
                                }
                                deviceIcon.layoutParams = diLp
                            }

                            if (hasTrack) {
                                if (tLp != null && tLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(tLp)
                                    tLp.width = 0
                                    tLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(tLp, "startToStart", pId)
                                    setField(tLp, "endToEnd", pId)
                                    setField(tLp, "topToBottom", coverId)
                                    setField(tLp, "bottomToTop", artistId)
                                    setField(tLp, "verticalChainStyle", 2)
                                    if (tLp is ViewGroup.MarginLayoutParams) {
                                        tLp.marginStart = (15 * density).toInt()
                                        tLp.marginEnd = (15 * density).toInt()
                                        tLp.topMargin = (10 * density).toInt()
                                    }
                                    if (title is TextView) {
                                        title.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                                        title.gravity = Gravity.START
                                        title.ellipsize = android.text.TextUtils.TruncateAt.END
                                        title.maxLines = 1
                                    }
                                    title.layoutParams = tLp
                                }
                                if (aLp != null && aLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(aLp)
                                    aLp.width = 0
                                    aLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(aLp, "startToStart", pId)
                                    setField(aLp, "endToEnd", pId)
                                    setField(aLp, "topToBottom", titleId)
                                    setField(aLp, "bottomToTop", audioContainerId)
                                    if (aLp is ViewGroup.MarginLayoutParams) {
                                        aLp.marginStart = (15 * density).toInt()
                                        aLp.marginEnd = (15 * density).toInt()
                                    }
                                    if (artist is TextView) {
                                        artist.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                                        artist.gravity = Gravity.START
                                        artist.ellipsize = android.text.TextUtils.TruncateAt.END
                                        artist.maxLines = 1
                                    }
                                    artist.layoutParams = aLp
                                }
                            } else {
                                if (eLp != null && eLp.javaClass.name.contains("ConstraintLayout")) {
                                    resetConstraints(eLp)
                                    eLp.width = 0
                                    eLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    setField(eLp, "startToStart", pId)
                                    setField(eLp, "endToEnd", pId)
                                    setField(eLp, "topToBottom", coverId)
                                    setField(eLp, "bottomToTop", audioContainerId)
                                    if (eLp is ViewGroup.MarginLayoutParams) {
                                        eLp.marginStart = (15 * density).toInt()
                                        eLp.marginEnd = (15 * density).toInt()
                                        eLp.topMargin = (10 * density).toInt()
                                    }
                                    if (emptyState is TextView) {
                                        emptyState.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                                        emptyState.gravity = Gravity.START
                                        emptyState.ellipsize = android.text.TextUtils.TruncateAt.END
                                        emptyState.maxLines = 1
                                    }
                                    emptyState.layoutParams = eLp
                                }
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (11 * density).toInt()
                                    acLp.marginStart = (9.5f * density).toInt()
                                    acLp.marginEnd = (9.5f * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (40 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 1)
                                prev.layoutParams = prevLp
                            }
                            if (playLp != null && playLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(playLp)
                                playLp.width = btnSize
                                playLp.height = btnSize
                                setField(playLp, "startToEnd", prevId)
                                setField(playLp, "endToStart", nextId)
                                setField(playLp, "topToTop", pId)
                                setField(playLp, "bottomToBottom", pId)
                                play.layoutParams = playLp
                            }
                            if (nextLp != null && nextLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(nextLp)
                                nextLp.width = btnSize
                                nextLp.height = btnSize
                                setField(nextLp, "startToEnd", playId)
                                setField(nextLp, "endToEnd", pId)
                                setField(nextLp, "topToTop", pId)
                                setField(nextLp, "bottomToBottom", pId)
                                next.layoutParams = nextLp
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "applyMediaPlayerLayout constraint setup error", t)
                }
            }

            val listenerTag = 0x7f099991
            if (itemView.getTag(listenerTag) == null) {
                val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val curSize = ControlCenterConfigHolder.sMediaPlayerSize
                    val curCols = curSize.split("x").firstOrNull()?.toIntOrNull()?.coerceIn(1, 4) ?: 2
                    val curRows = curSize.split("x").getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 2

                    val metaNow = sLastMediaPlayerMetaData
                    var hasTrackNow = false
                    if (metaNow != null) {
                        try {
                            val titleProp = metaNow.javaClass.getMethod("getTitle").invoke(metaNow) as? CharSequence
                            if (!titleProp.isNullOrBlank()) hasTrackNow = true
                        } catch (t: Throwable) { hasTrackNow = true }
                    }
                    if (!hasTrackNow && title is TextView && !title.text.isNullOrBlank() && title.visibility == View.VISIBLE) {
                        hasTrackNow = true
                    }

                    val pb = itemView.findViewById<View>(progressTag)
                    val isVerticalProgress = (curCols == 1 && (curRows == 4 || curRows == 5))
                    val hasCollapsedProgress = (curCols > 1 && (curCols >= 3 || curRows >= 3)) || isVerticalProgress

                    if (curCols == 1 && curRows == 1) { // 1x1
                        if (title?.visibility != View.GONE) title?.visibility = View.GONE
                        if (artist?.visibility != View.GONE) artist?.visibility = View.GONE
                        if (emptyState?.visibility != View.GONE) emptyState?.visibility = View.GONE
                        if (deviceIcon?.visibility != View.GONE) deviceIcon?.visibility = View.GONE
                        if (pb?.visibility != View.GONE) pb?.visibility = View.GONE
                        if (prev?.visibility != View.GONE) prev?.visibility = View.GONE
                        if (play?.visibility != View.GONE) play?.visibility = View.GONE
                        if (next?.visibility != View.GONE) next?.visibility = View.GONE
                        if (audioContainer?.visibility != View.GONE) audioContainer?.visibility = View.GONE
                    } else if (curSize == "2x1" || curSize == "1x2") {
                        if (title?.visibility != View.GONE) title?.visibility = View.GONE
                        if (artist?.visibility != View.GONE) artist?.visibility = View.GONE
                        if (emptyState?.visibility != View.GONE) emptyState?.visibility = View.GONE
                        if (emptyState?.layoutParams != null && emptyState.layoutParams.width != 0) {
                            emptyState.layoutParams.width = 0
                        }
                        if (deviceIcon?.visibility != View.GONE) deviceIcon?.visibility = View.GONE
                        if (pb?.visibility != View.GONE) pb?.visibility = View.GONE
                        if (prev?.visibility != View.VISIBLE) prev?.visibility = View.VISIBLE
                        if (play?.visibility != View.VISIBLE) play?.visibility = View.VISIBLE
                        if (next?.visibility != View.VISIBLE) next?.visibility = View.VISIBLE
                        if (audioContainer?.visibility != View.VISIBLE) audioContainer?.visibility = View.VISIBLE
                    } else {
                        if (hasTrackNow) {
                            if (title?.visibility != View.VISIBLE) title?.visibility = View.VISIBLE
                            if (artist?.visibility != View.VISIBLE) artist?.visibility = View.VISIBLE
                            if (emptyState?.visibility != View.GONE) emptyState?.visibility = View.GONE
                        } else {
                            if (title?.visibility != View.GONE) title?.visibility = View.GONE
                            if (artist?.visibility != View.GONE) artist?.visibility = View.GONE
                            if (emptyState?.visibility != View.VISIBLE) emptyState?.visibility = View.VISIBLE
                        }
                        if (hasCollapsedProgress && hasTrackNow) {
                            if (pb?.visibility != View.VISIBLE) {
                                pb?.visibility = View.VISIBLE
                            }
                            (pb as? CollapsedMediaSeekBar)?.isVertical = isVerticalProgress
                            (pb as? CollapsedMediaSeekBar)?.updateProgressFromSystem()
                        } else {
                            if (pb?.visibility != View.GONE) pb?.visibility = View.GONE
                        }

                        val showDeviceIcon = (curCols >= 2 && curRows >= 2)
                        if (showDeviceIcon) {
                            if (deviceIcon?.visibility != View.VISIBLE) deviceIcon?.visibility = View.VISIBLE
                        } else {
                            if (deviceIcon?.visibility != View.GONE) deviceIcon?.visibility = View.GONE
                        }

                        if (prev?.visibility != View.VISIBLE) prev?.visibility = View.VISIBLE
                        if (play?.visibility != View.VISIBLE) play?.visibility = View.VISIBLE
                        if (next?.visibility != View.VISIBLE) next?.visibility = View.VISIBLE
                        if (audioContainer?.visibility != View.VISIBLE) audioContainer?.visibility = View.VISIBLE
                    }

                    if (cover?.visibility != View.VISIBLE) cover?.visibility = View.VISIBLE
                    if (cover is ImageView && cover.drawable == null) {
                        val defCoverId = ctx.resources.getIdentifier("miplay_cover_default", "drawable", ctx.packageName)
                        if (defCoverId != 0) cover.setImageResource(defCoverId)
                    }
                }
                itemView.addOnLayoutChangeListener(layoutListener)
                itemView.setTag(listenerTag, layoutListener)
            }

            val isAdded = !ControlCenterConfigHolder.sRemovedComponentSpecs.contains("media")
            if (ControlCenterConfigHolder.sIsEditMode) {
                applyUniversalMarkButton(itemView, "media", isAdded, holder)
            } else {
                val mark = itemView.findViewById<View>(UNIVERSAL_MARK_TAG)
                if (mark != null) {
                    mark.visibility = View.GONE
                    mark.setOnClickListener(null)
                    mark.isClickable = false
                }
            }

            val resizeTag = 0x7f099999
            var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
            if (ControlCenterConfigHolder.config.isModularEditActive && ControlCenterConfigHolder.sIsEditMode) {
                val shouldShow = isAdded && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
                val badgeSize = (38 * density).toInt()
                if (resizeHandle == null && itemView is ViewGroup) {
                    resizeHandle = ImageView(ctx).apply {
                        id = resizeTag
                        val lp = ControlCenterConfigHolder.createMediaBadgeLayoutParams(itemView, badgeSize)
                        layoutParams = lp
                        setImageDrawable(CurvedResizeHandleDrawable(density))
                        setOnClickListener {
                            ControlCenterConfigHolder.cycleMediaPlayerSize(ctx, itemView)
                        }
                    }
                    itemView.addView(resizeHandle)
                } else if (resizeHandle != null && ControlCenterConfigHolder.isConstraintLayout(itemView) && !resizeHandle.layoutParams.javaClass.name.contains("ConstraintLayout")) {
                    resizeHandle.layoutParams = ControlCenterConfigHolder.createMediaBadgeLayoutParams(itemView as ViewGroup, badgeSize)
                }
                if (resizeHandle != null) {
                    resizeHandle.visibility = if (shouldShow) View.VISIBLE else View.GONE
                    if (shouldShow) {
                        resizeHandle.bringToFront()
                        resizeHandle.isClickable = true
                        resizeHandle.setOnClickListener {
                            ControlCenterConfigHolder.cycleMediaPlayerSize(ctx, itemView)
                        }
                        FluidTileResizeHandler.attach(resizeHandle, ctx, "media", itemView, density)
                    } else {
                        resizeHandle.isClickable = false
                        resizeHandle.setOnClickListener(null)
                        resizeHandle.setOnTouchListener(null)
                    }
                }
            } else {
                resizeHandle?.visibility = View.GONE
                resizeHandle?.setOnClickListener(null)
                resizeHandle?.setOnTouchListener(null)
                resizeHandle?.isClickable = false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyMediaPlayerLayout error", t)
        }
    }

    fun applyExpandedBrightnessLayout(delegate: Any) {
        try {
            val isHorizontal = ControlCenterConfigHolder.sBrightnessSliderHorizontal
            val delClass = delegate.javaClass
            var binding: Any? = null
            try {
                val bField = delClass.getDeclaredField("binding")
                bField.isAccessible = true
                binding = bField.get(delegate)
            } catch (t: Throwable) {}

            var toggleSliderView: View? = null
            var slider: SeekBar? = null
            var icon: View? = null
            var progress: View? = null
            var progressBg: View? = null

            if (binding != null) {
                try {
                    val tsField = binding.javaClass.getField("toggleSlider")
                    val tsBinding = tsField.get(binding)
                    if (tsBinding != null) {
                        toggleSliderView = tsBinding.javaClass.getField("toggleSlider").get(tsBinding) as? View
                        slider = tsBinding.javaClass.getField("slider").get(tsBinding) as? SeekBar
                        icon = tsBinding.javaClass.getField("icon").get(tsBinding) as? View
                        progress = tsBinding.javaClass.getField("progress").get(tsBinding) as? View
                        progressBg = tsBinding.javaClass.getField("progressBg").get(tsBinding) as? View
                    }
                } catch (t: Throwable) {}
            }

            if (toggleSliderView == null) return

            val ctx = toggleSliderView.context
            val density = ctx.resources.displayMetrics.density
            val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
            val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId) else (71.5f * density).toInt()
            val stockHId = ctx.resources.getIdentifier("brightness_panel_slider_height_large", "dimen", ctx.packageName)
            val stockH = if (stockHId != 0) ctx.resources.getDimensionPixelSize(stockHId) else (195 * density).toInt()
            val stockMarginTopId = ctx.resources.getIdentifier("brightness_panel_toggle_slider_margin_top_large", "dimen", ctx.packageName)
            val stockMarginTop = if (stockMarginTopId != 0) ctx.resources.getDimensionPixelSize(stockMarginTopId) else (134 * density).toInt()
            val clipRoundId = ctx.resources.getIdentifier("toggle_slider_clip_round_corner_radius", "dimen", ctx.packageName)
            val stockProgressRadius = if (clipRoundId != 0) ctx.resources.getDimension(clipRoundId) else 0f

            if (isHorizontal) {
                val lp = toggleSliderView.layoutParams
                if (lp != null) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = cardH
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.topMargin = stockMarginTop
                        val horizMargin = (16 * density).toInt()
                        lp.marginStart = horizMargin
                        lp.marginEnd = horizMargin
                    }
                    if (lp is LinearLayout.LayoutParams) {
                        lp.gravity = Gravity.CENTER_HORIZONTAL
                    } else if (lp is FrameLayout.LayoutParams) {
                        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    }
                    toggleSliderView.layoutParams = lp
                }

                if (slider != null) {
                    slider.rotation = 0f
                    slider.setTag(0x7f099991, true)
                    val sLp = slider.layoutParams
                    if (sLp != null) {
                        sLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        sLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                        slider.layoutParams = sLp
                    }
                    try {
                        val injM = slider.javaClass.getDeclaredMethod("getInjector")
                        injM.isAccessible = true
                        val inj = injM.invoke(slider)
                        if (inj != null) {
                            val setVertM = inj.javaClass.getMethod("setVertical", Boolean::class.javaPrimitiveType)
                            setVertM.invoke(inj, false)
                        }
                    } catch (t: Throwable) {}
                    try {
                        val getDragAnimM = slider.javaClass.getMethod("getDragAnim")
                        val dragAnim = getDragAnimM.invoke(slider)
                        if (dragAnim != null) {
                            val setDragEnabledM = dragAnim.javaClass.getMethod("setDragEnabled", Boolean::class.javaPrimitiveType)
                            setDragEnabledM.invoke(dragAnim, false)
                        }
                    } catch (t: Throwable) {}
                    try {
                        val ghField = slider.javaClass.getDeclaredField("gestureHelper")
                        ghField.isAccessible = true
                        val gh = ghField.get(slider)
                        if (gh != null) {
                            var c: Class<*>? = gh.javaClass
                            while (c != null && c != Any::class.java) {
                                try {
                                    val vf = c.getDeclaredField("vertical")
                                    vf.isAccessible = true
                                    vf.set(gh, java.lang.Boolean.FALSE)
                                    break
                                } catch (e: NoSuchFieldException) {
                                    c = c.superclass
                                }
                            }
                        }
                    } catch (t: Throwable) {}
                }

                if (progress != null && slider != null) {
                    val pLp = progress.layoutParams
                    if (pLp != null) {
                        pLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        pLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                        progress.layoutParams = pLp
                    }
                    progress.clipToOutline = true
                    progress.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val maxVal = slider.max.coerceAtLeast(1)
                            val curVal = slider.progress.coerceIn(0, maxVal)
                            val frac = curVal.toFloat() / maxVal
                            val w = (view.width * frac).toInt()
                            outline.setRoundRect(0, 0, w, view.height, stockProgressRadius)
                        }
                    }
                    progress.invalidateOutline()
                }

                if (progressBg != null) {
                    val bLp = progressBg.layoutParams
                    if (bLp != null) {
                        bLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        bLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                        progressBg.layoutParams = bLp
                    }
                }

                if (icon != null) {
                    val iLp = icon.layoutParams as? FrameLayout.LayoutParams
                    if (iLp != null) {
                        iLp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        iLp.marginStart = (18 * density).toInt()
                        iLp.bottomMargin = 0
                        icon.layoutParams = iLp
                    }
                }
            } else {
                val stockWId = ctx.resources.getIdentifier("brightness_panel_slider_width_large", "dimen", ctx.packageName)
                val stockW = if (stockWId != 0) ctx.resources.getDimensionPixelSize(stockWId) else (76 * density).toInt()

                val lp = toggleSliderView.layoutParams
                if (lp != null) {
                    lp.width = stockW
                    lp.height = stockH
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.topMargin = stockMarginTop
                        lp.marginStart = 0
                        lp.marginEnd = 0
                    }
                    toggleSliderView.layoutParams = lp
                }

                if (slider != null) {
                    slider.rotation = 270f
                    slider.setTag(0x7f099991, false)
                    val sLp = slider.layoutParams
                    if (sLp != null) {
                        sLp.width = stockH
                        sLp.height = stockW
                        slider.layoutParams = sLp
                    }
                    try {
                        val injM = slider.javaClass.getDeclaredMethod("getInjector")
                        injM.isAccessible = true
                        val inj = injM.invoke(slider)
                        if (inj != null) {
                            val setVertM = inj.javaClass.getMethod("setVertical", Boolean::class.javaPrimitiveType)
                            setVertM.invoke(inj, false)
                        }
                    } catch (t: Throwable) {}
                    try {
                        val getDragAnimM = slider.javaClass.getMethod("getDragAnim")
                        val dragAnim = getDragAnimM.invoke(slider)
                        if (dragAnim != null) {
                            val setDragEnabledM = dragAnim.javaClass.getMethod("setDragEnabled", Boolean::class.javaPrimitiveType)
                            setDragEnabledM.invoke(dragAnim, true)
                        }
                    } catch (t: Throwable) {}
                    try {
                        val ghField = slider.javaClass.getDeclaredField("gestureHelper")
                        ghField.isAccessible = true
                        val gh = ghField.get(slider)
                        if (gh != null) {
                            var c: Class<*>? = gh.javaClass
                            while (c != null && c != Any::class.java) {
                                try {
                                    val vf = c.getDeclaredField("vertical")
                                    vf.isAccessible = true
                                    vf.set(gh, java.lang.Boolean.TRUE)
                                    break
                                } catch (e: NoSuchFieldException) {
                                    c = c.superclass
                                }
                            }
                        }
                    } catch (t: Throwable) {}
                }

                if (progress != null && slider != null) {
                    val pLp = progress.layoutParams
                    if (pLp != null) {
                        pLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        pLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                        progress.layoutParams = pLp
                    }
                    progress.clipToOutline = true
                    progress.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val maxVal = slider.max.coerceAtLeast(1)
                            val curVal = slider.progress.coerceIn(0, maxVal)
                            val frac = curVal.toFloat() / maxVal
                            val top = (view.height * (1f - frac)).toInt()
                            outline.setRoundRect(0, top, view.width, view.height, stockProgressRadius)
                        }
                    }
                    progress.invalidateOutline()
                }

                if (icon != null) {
                    val iLp = icon.layoutParams as? FrameLayout.LayoutParams
                    if (iLp != null) {
                        iLp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                        iLp.marginStart = 0
                        iLp.bottomMargin = (18 * density).toInt()
                        icon.layoutParams = iLp
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyExpandedBrightnessLayout error", t)
        }
    }
}

/**
 * Interactive Drag Touch Listener for Tile Resizing.
 * Handles live dragging and morphing between:
 * - 1x1 circular tile (Normal)
 * - 2x1 horizontal card (Expanded)
 * - 1x2 vertical card (Vertical)
 */
class TileResizeDragTouchListener(
    private val ctx: Context,
    private val spec: String,
    private val tileView: View,
    private val density: Float
) : View.OnTouchListener {

    companion object {
        private const val STATE_NORMAL = 0
        private const val STATE_EXPANDED = 1
        private const val STATE_VERTICAL = 2
        private const val STATE_LARGE = 3
        private const val STATE_WIDE_1 = 4
        private const val STATE_WIDE_2 = 5
        private const val STATE_TALL_4 = 6
    }

    private val isBrightnessSpec = "brightness".equals(spec, ignoreCase = true)
    private val isVolumeSpec = "volume".equals(spec, ignoreCase = true)
    private val isSliderSpec = isBrightnessSpec || isVolumeSpec
    private val isMediaSpec = "media".equals(spec, ignoreCase = true)
    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private var initialState = STATE_NORMAL
    private var candidateTargetState = -1

    private var targetContainer: ViewGroup? = null
    private var ghostDrawable: TileResizeGhostDrawable? = null

    private var originLeft = 0f
    private var originTop = 0f
    private var originWidth = 0f
    private var originHeight = 0f
    private var unitWidth1x1 = 0f
    private var unitHeight1x1 = 0f
    private var cardWidth2x1 = 0f
    private var cardHeight1x2 = 0f
    private var oneColW = 0f
    private var twoColW = 0f
    private var threeColW = 0f
    private var fourColW = 0f
    private var row1H = 0f
    private var row2H = 0f
    private var threeRowsH = 0f
    private var row4H = 0f
    private var fiveRowsH = 0f
    private val cardRadius = 26f * density
    private var initialRadius = 26f * density

    private fun getMediaDims(state: Int): Pair<Float, Float> {
        return when (state) {
            FluidTileResizeHandler.MEDIA_1X1 -> Pair(oneColW, row1H)
            FluidTileResizeHandler.MEDIA_1X2 -> Pair(oneColW, row2H)
            FluidTileResizeHandler.MEDIA_1X3 -> Pair(oneColW, threeRowsH)
            FluidTileResizeHandler.MEDIA_1X4 -> Pair(oneColW, row4H)
            FluidTileResizeHandler.MEDIA_1X5 -> Pair(oneColW, fiveRowsH)
            FluidTileResizeHandler.MEDIA_2X1 -> Pair(twoColW, row1H)
            FluidTileResizeHandler.MEDIA_2X2 -> Pair(twoColW, row2H)
            FluidTileResizeHandler.MEDIA_2X3 -> Pair(twoColW, threeRowsH)
            FluidTileResizeHandler.MEDIA_2X4 -> Pair(twoColW, row4H)
            FluidTileResizeHandler.MEDIA_2X5 -> Pair(twoColW, fiveRowsH)
            FluidTileResizeHandler.MEDIA_3X1 -> Pair(threeColW, row1H)
            FluidTileResizeHandler.MEDIA_3X2 -> Pair(threeColW, row2H)
            FluidTileResizeHandler.MEDIA_3X3 -> Pair(threeColW, threeRowsH)
            FluidTileResizeHandler.MEDIA_4X1 -> Pair(fourColW, row1H)
            FluidTileResizeHandler.MEDIA_4X2 -> Pair(fourColW, row2H)
            FluidTileResizeHandler.MEDIA_4X3 -> Pair(fourColW, threeRowsH)
            else -> Pair(twoColW, row2H)
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction.coerceIn(0f, 1f)
    }

    private fun requestDisallowAll(view: View?, disallow: Boolean) {
        var p = view?.parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = p.parent
        }
    }

    private fun findRecyclerView(view: View?): ViewGroup? {
        var cur = view?.parent
        while (cur != null) {
            if (cur is ViewGroup && cur.javaClass.name.contains("RecyclerView")) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!ControlCenterConfigHolder.sIsEditMode) {
            v.visibility = View.GONE
            v.setOnTouchListener(null)
            v.setOnClickListener(null)
            v.isClickable = false
            return false
        }
        if (isSliderSpec) {
            val isHoriz = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderHorizontal else ControlCenterConfigHolder.sVolumeSliderHorizontal
            if (!isHoriz) {
                v.visibility = View.GONE
                v.setOnTouchListener(null)
                v.setOnClickListener(null)
                v.isClickable = false
                return false
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ControlCenterConfigHolder.sIsDraggingResizeHandle = true
                requestDisallowAll(v, true)

                downRawX = event.rawX
                downRawY = event.rawY
                isDragging = false
                candidateTargetState = -1

                initialState = when {
                    isSliderSpec -> {
                        val w = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                        w.coerceIn(2, 4)
                    }
                    isMediaSpec -> FluidTileResizeHandler.mediaSizeStringToState(ControlCenterConfigHolder.sMediaPlayerSize)
                    ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) -> STATE_NORMAL
                    ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_LARGE
                    ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_EXPANDED
                    ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_VERTICAL
                    ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) -> STATE_EXPANDED
                    else -> STATE_NORMAL
                }

                val rv = findRecyclerView(tileView) ?: (tileView.rootView as? ViewGroup)
                targetContainer = rv
                if (rv != null) {
                    val iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
                    val iconFrame = if (iconFrameId != 0) tileView.findViewById<View>(iconFrameId) else null
                    val visualView = if (initialState == STATE_NORMAL && iconFrame != null) iconFrame else tileView

                    val locTile = IntArray(2)
                    val locRv = IntArray(2)
                    visualView.getLocationInWindow(locTile)
                    rv.getLocationInWindow(locRv)
                    originLeft = (locTile[0] - locRv[0]).toFloat()
                    originTop = (locTile[1] - locRv[1]).toFloat()
                    originWidth = visualView.width.toFloat().coerceAtLeast(56f * density)
                    originHeight = visualView.height.toFloat().coerceAtLeast(56f * density)

                    val cardHId = ctx.resources.getIdentifier("qs_card_item_height", "dimen", ctx.packageName)
                    val cardH = if (cardHId != 0) ctx.resources.getDimensionPixelSize(cardHId).toFloat() else (71.5f * density)
                    val res2RowsId = ctx.resources.getIdentifier("control_center_universal_2_rows_size", "dimen", ctx.packageName)
                    val tallH = if (res2RowsId != 0) ctx.resources.getDimensionPixelSize(res2RowsId).toFloat() else (157f * density)
                    val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
                    val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId).toFloat() else (10f * density)

                    val cardRadius = 26f * density

                    val rvContentW = (rv.width - rv.paddingLeft - rv.paddingRight).toFloat()
                    val colW = if (rvContentW > 100f * density) {
                        (rvContentW - 3f * gap) / 4f
                    } else {
                        originWidth
                    }
                    val calcTwoColW = colW * 2f + gap

                    oneColW = colW
                    twoColW = calcTwoColW
                    threeColW = colW * 3f + 2f * gap
                    fourColW = if (rvContentW > 100f * density) rvContentW else (colW * 4f + 3f * gap)
                    row1H = cardH
                    row2H = tallH
                    threeRowsH = 3f * cardH + 2f * gap
                    row4H = 4f * cardH + 3f * gap
                    fiveRowsH = 5f * cardH + 4f * gap

                    if (isSliderSpec) {
                        originWidth = when (initialState) {
                            3 -> threeColW
                            4 -> fourColW
                            else -> twoColW
                        }
                        originHeight = row1H
                    } else if (isMediaSpec) {
                        val (sW, sH) = when (initialState) {
                            FluidTileResizeHandler.MEDIA_1X1 -> Pair(oneColW, row1H)
                            FluidTileResizeHandler.MEDIA_1X2 -> Pair(oneColW, row2H)
                            FluidTileResizeHandler.MEDIA_1X3 -> Pair(oneColW, threeRowsH)
                            FluidTileResizeHandler.MEDIA_1X4 -> Pair(oneColW, row4H)
                            FluidTileResizeHandler.MEDIA_1X5 -> Pair(oneColW, fiveRowsH)
                            FluidTileResizeHandler.MEDIA_2X1 -> Pair(twoColW, row1H)
                            FluidTileResizeHandler.MEDIA_2X2 -> Pair(twoColW, row2H)
                            FluidTileResizeHandler.MEDIA_2X3 -> Pair(twoColW, threeRowsH)
                            FluidTileResizeHandler.MEDIA_2X4 -> Pair(twoColW, row4H)
                            FluidTileResizeHandler.MEDIA_2X5 -> Pair(twoColW, fiveRowsH)
                            FluidTileResizeHandler.MEDIA_3X1 -> Pair(threeColW, row1H)
                            FluidTileResizeHandler.MEDIA_3X2 -> Pair(threeColW, row2H)
                            FluidTileResizeHandler.MEDIA_3X3 -> Pair(threeColW, threeRowsH)
                            FluidTileResizeHandler.MEDIA_4X1 -> Pair(fourColW, row1H)
                            FluidTileResizeHandler.MEDIA_4X2 -> Pair(fourColW, row2H)
                            FluidTileResizeHandler.MEDIA_4X3 -> Pair(fourColW, threeRowsH)
                            else -> Pair(twoColW, row2H)
                        }
                        originWidth = sW
                        originHeight = sH
                    } else {
                        when (initialState) {
                            STATE_NORMAL -> {
                                unitWidth1x1 = originWidth
                                unitHeight1x1 = originHeight
                                cardWidth2x1 = twoColW.coerceAtLeast(originWidth * 2f + gap)
                                cardHeight1x2 = tallH
                            }
                            STATE_EXPANDED -> {
                                cardWidth2x1 = originWidth
                                unitHeight1x1 = originHeight
                                unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                cardHeight1x2 = tallH
                            }
                            STATE_VERTICAL -> {
                                unitWidth1x1 = originWidth
                                cardHeight1x2 = originHeight
                                unitHeight1x1 = colW.coerceAtLeast(56f * density)
                                cardWidth2x1 = twoColW.coerceAtLeast(originWidth * 2f + gap)
                            }
                            STATE_LARGE -> {
                                cardWidth2x1 = originWidth
                                cardHeight1x2 = originHeight
                                unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                unitHeight1x1 = colW.coerceAtLeast(56f * density)
                            }
                            STATE_WIDE_1, STATE_WIDE_2, STATE_TALL_4 -> {
                                cardWidth2x1 = twoColW
                                cardHeight1x2 = tallH
                                unitWidth1x1 = colW.coerceAtLeast(56f * density)
                                unitHeight1x1 = colW.coerceAtLeast(56f * density)
                            }
                        }
                    }

                    initialRadius = if (initialState == STATE_NORMAL && !isMediaSpec && !isSliderSpec) Math.min(originWidth, originHeight) / 2f else cardRadius
                    ghostDrawable = TileResizeGhostDrawable(density).apply {
                        setOriginBounds(originLeft, originTop, originWidth, originHeight, initialRadius)
                    }
                }
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                ControlCenterConfigHolder.sIsDraggingResizeHandle = true
                requestDisallowAll(v, true)

                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val slop = 5f * density

                if (dist > slop) {
                    if (!isDragging) {
                        isDragging = true
                        ghostDrawable?.let { targetContainer?.overlay?.add(it) }
                        tileView.alpha = 0.60f
                    }
                }

                if (!isDragging) return true

                var previewW = originWidth
                var previewH = originHeight
                val cardRadius = 26f * density
                var previewRadius = cardRadius
                var targetState = initialState

                if (isSliderSpec) {
                    val rawW = (originWidth + dx).coerceIn(twoColW * 0.85f, fourColW * 1.05f)
                    targetState = when {
                        rawW > (threeColW + fourColW) / 2f -> 4
                        rawW > (twoColW + threeColW) / 2f -> 3
                        else -> 2
                    }
                    val targetW = when (targetState) {
                        3 -> threeColW
                        4 -> fourColW
                        else -> twoColW
                    }
                    if (targetState != initialState) {
                        val snapFactor = 0.60f
                        previewW = lerp(rawW, targetW, snapFactor)
                    } else {
                        previewW = rawW
                    }
                    previewH = row1H
                    previewRadius = cardRadius
                } else if (isMediaSpec) {
                    val rawW = (originWidth + dx).coerceIn(oneColW * 0.75f, fourColW * 1.05f)
                    val rawH = (originHeight + dy).coerceIn(row1H * 0.75f, fiveRowsH * 1.05f)

                    val (initCols, initRows) = FluidTileResizeHandler.mediaStateToSpans(initialState)
                    val absDx = Math.abs(dx)
                    val absDy = Math.abs(dy)

                    val desiredCols = when {
                        rawW < (oneColW + twoColW) / 2f -> 1
                        rawW < (twoColW + threeColW) / 2f -> 2
                        rawW < (threeColW + fourColW) / 2f -> 3
                        else -> 4
                    }
                    val desiredRows = when {
                        rawH < (row1H + row2H) / 2f -> 1
                        rawH < (row2H + threeRowsH) / 2f -> 2
                        rawH < (threeRowsH + row4H) / 2f -> 3
                        rawH < (row4H + fiveRowsH) / 2f -> 4
                        else -> 5
                    }

                    // If pulling predominantly horizontally from a tall component (e.g. 2x4 -> 4x2)
                    val isPredominantlyHorizontal = absDx > absDy * 1.25f
                    // If pulling predominantly vertically from a wide component (e.g. 4x2 -> 2x4)
                    val isPredominantlyVertical = absDy > absDx * 1.25f

                    targetState = when {
                        isPredominantlyHorizontal && initRows >= 4 && dx > 0 -> {
                            when (desiredCols) {
                                4 -> FluidTileResizeHandler.MEDIA_4X2
                                3 -> FluidTileResizeHandler.MEDIA_3X2
                                else -> FluidTileResizeHandler.MEDIA_2X4
                            }
                        }
                        isPredominantlyVertical && initCols >= 4 && dy > 0 -> {
                            when (desiredRows) {
                                5 -> FluidTileResizeHandler.MEDIA_2X5
                                4 -> FluidTileResizeHandler.MEDIA_2X4
                                3 -> if (desiredCols >= 3) FluidTileResizeHandler.MEDIA_4X3 else FluidTileResizeHandler.MEDIA_2X3
                                else -> FluidTileResizeHandler.MEDIA_4X2
                            }
                        }
                        isPredominantlyHorizontal && initRows == 2 && dx < 0 && desiredCols == 1 -> {
                            FluidTileResizeHandler.MEDIA_1X2
                        }
                        isPredominantlyVertical && initCols == 2 && dy < 0 && desiredRows == 1 -> {
                            FluidTileResizeHandler.MEDIA_2X1
                        }
                        else -> {
                            when (desiredCols) {
                                1 -> when (desiredRows) {
                                    1 -> FluidTileResizeHandler.MEDIA_1X1
                                    2 -> FluidTileResizeHandler.MEDIA_1X2
                                    3 -> FluidTileResizeHandler.MEDIA_1X3
                                    4 -> FluidTileResizeHandler.MEDIA_1X4
                                    else -> FluidTileResizeHandler.MEDIA_1X5
                                }
                                2 -> when (desiredRows) {
                                    1 -> FluidTileResizeHandler.MEDIA_2X1
                                    2 -> FluidTileResizeHandler.MEDIA_2X2
                                    3 -> FluidTileResizeHandler.MEDIA_2X3
                                    4 -> FluidTileResizeHandler.MEDIA_2X4
                                    else -> FluidTileResizeHandler.MEDIA_2X5
                                }
                                3 -> when (desiredRows) {
                                    1 -> FluidTileResizeHandler.MEDIA_3X1
                                    2 -> FluidTileResizeHandler.MEDIA_3X2
                                    else -> FluidTileResizeHandler.MEDIA_3X3
                                }
                                else -> when (desiredRows) {
                                    1 -> FluidTileResizeHandler.MEDIA_4X1
                                    2 -> FluidTileResizeHandler.MEDIA_4X2
                                    else -> FluidTileResizeHandler.MEDIA_4X3
                                }
                            }
                        }
                    }

                    val (targetW, targetH) = when (targetState) {
                        FluidTileResizeHandler.MEDIA_1X1 -> Pair(oneColW, row1H)
                        FluidTileResizeHandler.MEDIA_1X2 -> Pair(oneColW, row2H)
                        FluidTileResizeHandler.MEDIA_1X3 -> Pair(oneColW, threeRowsH)
                        FluidTileResizeHandler.MEDIA_1X4 -> Pair(oneColW, row4H)
                        FluidTileResizeHandler.MEDIA_1X5 -> Pair(oneColW, fiveRowsH)
                        FluidTileResizeHandler.MEDIA_2X1 -> Pair(twoColW, row1H)
                        FluidTileResizeHandler.MEDIA_2X2 -> Pair(twoColW, row2H)
                        FluidTileResizeHandler.MEDIA_2X3 -> Pair(twoColW, threeRowsH)
                        FluidTileResizeHandler.MEDIA_2X4 -> Pair(twoColW, row4H)
                        FluidTileResizeHandler.MEDIA_2X5 -> Pair(twoColW, fiveRowsH)
                        FluidTileResizeHandler.MEDIA_3X1 -> Pair(threeColW, row1H)
                        FluidTileResizeHandler.MEDIA_3X2 -> Pair(threeColW, row2H)
                        FluidTileResizeHandler.MEDIA_3X3 -> Pair(threeColW, threeRowsH)
                        FluidTileResizeHandler.MEDIA_4X1 -> Pair(fourColW, row1H)
                        FluidTileResizeHandler.MEDIA_4X2 -> Pair(fourColW, row2H)
                        FluidTileResizeHandler.MEDIA_4X3 -> Pair(fourColW, threeRowsH)
                        else -> Pair(twoColW, row2H)
                    }

                    val dW = targetW - originWidth
                    val dH = targetH - originHeight
                    val distSq = dW * dW + dH * dH

                    val progress = if (targetState != initialState && distSq > 1f) {
                        val proj = (dx * dW + dy * dH) / distSq
                        proj.coerceIn(0f, 1f)
                    } else 0f

                    if (targetState != initialState) {
                        previewW = originWidth + progress * dW
                        previewH = originHeight + progress * dH
                    } else {
                        previewW = (originWidth + dx * 0.25f).coerceIn(originWidth - 12f * density, originWidth + 12f * density)
                        previewH = (originHeight + dy * 0.25f).coerceIn(originHeight - 12f * density, originHeight + 12f * density)
                    }
                    previewRadius = cardRadius
                } else {
                    val deltaW = (cardWidth2x1 - unitWidth1x1).coerceAtLeast(40f * density)
                    val deltaH = (cardHeight1x2 - unitHeight1x1).coerceAtLeast(40f * density)
                    val snapThreshold = 0.22f
                    val snapDistX = deltaW * snapThreshold
                    val snapDistY = deltaH * snapThreshold

                    when (initialState) {
                        STATE_NORMAL -> {
                            val px = (dx / deltaW).coerceIn(0f, 1f)
                            val py = (dy / deltaH).coerceIn(0f, 1f)
                            val isDiagonal = dx > snapDistX * 0.65f && dy > snapDistY * 0.65f

                            if (isDiagonal) {
                                previewW = unitWidth1x1 + px * deltaW
                                previewH = unitHeight1x1 + py * deltaH
                                val pMax = Math.max(px, py)
                                previewRadius = lerp(unitWidth1x1 / 2f, cardRadius, pMax)
                                if (dx >= snapDistX && dy >= snapDistY) {
                                    targetState = STATE_LARGE
                                }
                            } else if (dx >= dy) {
                                if (dx > 0) {
                                    previewW = unitWidth1x1 + px * deltaW
                                    previewH = unitHeight1x1
                                    previewRadius = lerp(unitHeight1x1 / 2f, cardRadius, px)
                                    if (dx >= snapDistX) {
                                        targetState = STATE_EXPANDED
                                    }
                                } else {
                                    previewW = unitWidth1x1
                                    previewH = unitHeight1x1
                                    previewRadius = unitWidth1x1 / 2f
                                }
                            } else {
                                if (dy > 0) {
                                    previewW = unitWidth1x1
                                    previewH = unitHeight1x1 + py * deltaH
                                    previewRadius = lerp(unitWidth1x1 / 2f, cardRadius, py)
                                    if (dy >= snapDistY) {
                                        targetState = STATE_VERTICAL
                                    }
                                } else {
                                    previewW = unitWidth1x1
                                    previewH = unitHeight1x1
                                    previewRadius = unitWidth1x1 / 2f
                                }
                            }
                        }

                        STATE_EXPANDED -> {
                            val shrinkX = (-dx / deltaW).coerceIn(0f, 1f)
                            val expandY = (dy / deltaH).coerceIn(0f, 1f)
                            val isDownLeft = -dx > snapDistX * 0.55f && dy > snapDistY * 0.55f

                            if (isDownLeft) {
                                previewW = cardWidth2x1 - shrinkX * deltaW
                                previewH = unitHeight1x1 + expandY * deltaH
                                previewRadius = cardRadius
                                if (-dx >= snapDistX && dy >= snapDistY) {
                                    targetState = STATE_VERTICAL
                                }
                            } else if (dy > snapDistY * 0.5f && dy > -dx) {
                                previewW = cardWidth2x1
                                previewH = unitHeight1x1 + expandY * deltaH
                                previewRadius = cardRadius
                                if (dy >= snapDistY) {
                                    targetState = STATE_LARGE
                                }
                            } else if (dx < 0) {
                                previewW = cardWidth2x1 - shrinkX * deltaW
                                previewH = unitHeight1x1
                                previewRadius = lerp(cardRadius, unitHeight1x1 / 2f, shrinkX)
                                if (-dx >= snapDistX) {
                                    targetState = STATE_NORMAL
                                }
                            } else {
                                previewW = cardWidth2x1
                                previewH = unitHeight1x1
                                previewRadius = cardRadius
                            }
                        }

                        STATE_VERTICAL -> {
                            val shrinkY = (-dy / deltaH).coerceIn(0f, 1f)
                            val expandX = (dx / deltaW).coerceIn(0f, 1f)
                            val isUpRight = dx > snapDistX * 0.55f && -dy > snapDistY * 0.55f

                            if (isUpRight) {
                                previewW = unitWidth1x1 + expandX * deltaW
                                previewH = cardHeight1x2 - shrinkY * deltaH
                                previewRadius = cardRadius
                                if (dx >= snapDistX && -dy >= snapDistY) {
                                    targetState = STATE_EXPANDED
                                }
                            } else if (dx > snapDistX * 0.5f && dx > -dy) {
                                previewW = unitWidth1x1 + expandX * deltaW
                                previewH = cardHeight1x2
                                previewRadius = cardRadius
                                if (dx >= snapDistX) {
                                    targetState = STATE_LARGE
                                }
                            } else if (dy < 0) {
                                previewW = unitWidth1x1
                                previewH = cardHeight1x2 - shrinkY * deltaH
                                previewRadius = lerp(cardRadius, unitWidth1x1 / 2f, shrinkY)
                                if (-dy >= snapDistY) {
                                    targetState = STATE_NORMAL
                                }
                            } else {
                                previewW = unitWidth1x1
                                previewH = cardHeight1x2
                                previewRadius = cardRadius
                            }
                        }

                        STATE_LARGE -> {
                            val shrinkX = (-dx / deltaW).coerceIn(0f, 1f)
                            val shrinkY = (-dy / deltaH).coerceIn(0f, 1f)
                            val isUpLeft = -dx > snapDistX * 0.55f && -dy > snapDistY * 0.55f

                            if (isUpLeft) {
                                previewW = cardWidth2x1 - shrinkX * deltaW
                                previewH = cardHeight1x2 - shrinkY * deltaH
                                val pMax = Math.max(shrinkX, shrinkY)
                                previewRadius = lerp(cardRadius, unitWidth1x1 / 2f, pMax)
                                if (-dx >= snapDistX && -dy >= snapDistY) {
                                    targetState = STATE_NORMAL
                                }
                            } else if (-dy >= -dx && -dy > snapDistY * 0.4f) {
                                previewW = cardWidth2x1
                                previewH = cardHeight1x2 - shrinkY * deltaH
                                previewRadius = cardRadius
                                if (-dy >= snapDistY) {
                                    targetState = STATE_EXPANDED
                                }
                            } else if (-dx > 0) {
                                previewW = cardWidth2x1 - shrinkX * deltaW
                                previewH = cardHeight1x2
                                previewRadius = cardRadius
                                if (-dx >= snapDistX) {
                                    targetState = STATE_VERTICAL
                                }
                            } else {
                                previewW = cardWidth2x1
                                previewH = cardHeight1x2
                                previewRadius = cardRadius
                            }
                        }
                    }
                }

                if (targetState != candidateTargetState) {
                    candidateTargetState = targetState
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }

                val minLeft = targetContainer?.paddingLeft?.toFloat() ?: 0f
                val maxRight = targetContainer?.let { (it.width - it.paddingRight).toFloat() } ?: (originLeft + previewW)
                val drawLeft = if (originLeft + previewW > maxRight) {
                    (maxRight - previewW).coerceAtLeast(minLeft)
                } else {
                    originLeft
                }

                ghostDrawable?.let { gd ->
                    val isSnapping = candidateTargetState != initialState && candidateTargetState != -1
                    gd.updateMorph(drawLeft, originTop, previewW, previewH, previewRadius, isSnapping)
                    targetContainer?.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                requestDisallowAll(v, false)

                val gd = ghostDrawable
                if (isDragging) {
                    val finalState = if (candidateTargetState != -1) candidateTargetState else initialState
                    var targetW = originWidth
                    var targetH = originHeight
                    var targetRadius = cardRadius
                    var targetDrawLeft = originLeft

                    if (isSliderSpec) {
                        targetW = when (finalState) {
                            3 -> threeColW
                            4 -> fourColW
                            else -> twoColW
                        }
                        targetH = originHeight
                        targetRadius = cardRadius
                    } else if (isMediaSpec) {
                        val (tw, th) = getMediaDims(finalState)
                        targetW = tw
                        targetH = th
                        targetRadius = cardRadius
                    } else {
                        targetW = when (finalState) {
                            STATE_EXPANDED, STATE_LARGE -> twoColW
                            else -> unitWidth1x1
                        }
                        targetH = when (finalState) {
                            STATE_VERTICAL, STATE_LARGE -> cardHeight1x2
                            STATE_EXPANDED -> unitHeight1x1
                            else -> if (initialState == STATE_NORMAL) originHeight else unitWidth1x1
                        }
                        targetRadius = if (finalState == STATE_NORMAL) (targetW / 2f) else cardRadius
                    }

                    val minLeft = targetContainer?.paddingLeft?.toFloat() ?: 0f
                    val maxRight = targetContainer?.let { (it.width - it.paddingRight).toFloat() } ?: (originLeft + targetW)
                    targetDrawLeft = if (originLeft + targetW > maxRight) {
                        (maxRight - targetW).coerceAtLeast(minLeft)
                    } else {
                        originLeft
                    }

                    if (finalState != -1 && finalState != initialState) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (isSliderSpec) {
                            ControlCenterConfigHolder.setSliderWidth(ctx, isBrightnessSpec, finalState, tileView)
                        } else if (isMediaSpec) {
                            val targetMediaSize = FluidTileResizeHandler.mediaStateToSizeString(finalState)
                            ControlCenterConfigHolder.setMediaSize(ctx, targetMediaSize, tileView)
                        } else {
                            when (finalState) {
                                STATE_EXPANDED -> ControlCenterConfigHolder.setTileToExpanded(ctx, spec, tileView)
                                STATE_VERTICAL -> ControlCenterConfigHolder.setTileToVertical(ctx, spec, tileView)
                                STATE_LARGE -> ControlCenterConfigHolder.setTileToLarge(ctx, spec, tileView)
                                STATE_NORMAL -> ControlCenterConfigHolder.setTileToNormal(ctx, spec, tileView)
                            }
                        }
                    }

                    if (gd != null) {
                        gd.animateToAndFadeOut(targetDrawLeft, originTop, targetW, targetH, targetRadius, 180L) {
                            targetContainer?.overlay?.remove(gd)
                            targetContainer?.invalidate()
                            ghostDrawable = null
                        }
                    }
                    tileView.animate().alpha(1.0f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (isSliderSpec) {
                        val currentCols = if (isBrightnessSpec) ControlCenterConfigHolder.sBrightnessSliderWidth else ControlCenterConfigHolder.sVolumeSliderWidth
                        val nextCols = when (currentCols) {
                            2 -> 3
                            3 -> 4
                            else -> 2
                        }
                        val nextW = when (nextCols) {
                            3 -> threeColW
                            4 -> fourColW
                            else -> twoColW
                        }
                        val minLeft = targetContainer?.paddingLeft?.toFloat() ?: 0f
                        val maxRight = targetContainer?.let { (it.width - it.paddingRight).toFloat() } ?: (originLeft + nextW)
                        val drawL = if (originLeft + nextW > maxRight) (maxRight - nextW).coerceAtLeast(minLeft) else originLeft

                        val previewGd = gd ?: TileResizeGhostDrawable(density).apply {
                            setOriginBounds(originLeft, originTop, originWidth, originHeight, cardRadius)
                            targetContainer?.overlay?.add(this)
                        }
                        previewGd.animateToAndFadeOut(drawL, originTop, nextW, originHeight, cardRadius, 180L) {
                            targetContainer?.overlay?.remove(previewGd)
                            targetContainer?.invalidate()
                            ghostDrawable = null
                        }
                        tileView.alpha = 0f
                        tileView.animate().alpha(1.0f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightnessSpec, tileView)
                    } else if (isMediaSpec) {
                        val nextState = when (initialState) {
                            FluidTileResizeHandler.MEDIA_2X2 -> FluidTileResizeHandler.MEDIA_2X1
                            FluidTileResizeHandler.MEDIA_2X1 -> FluidTileResizeHandler.MEDIA_1X1
                            FluidTileResizeHandler.MEDIA_1X1 -> FluidTileResizeHandler.MEDIA_1X2
                            FluidTileResizeHandler.MEDIA_1X2 -> FluidTileResizeHandler.MEDIA_2X2
                            else -> FluidTileResizeHandler.MEDIA_2X2
                        }
                        val (nextW, nextH) = getMediaDims(nextState)
                        val minLeft = targetContainer?.paddingLeft?.toFloat() ?: 0f
                        val maxRight = targetContainer?.let { (it.width - it.paddingRight).toFloat() } ?: (originLeft + nextW)
                        val drawL = if (originLeft + nextW > maxRight) (maxRight - nextW).coerceAtLeast(minLeft) else originLeft

                        val previewGd = gd ?: TileResizeGhostDrawable(density).apply {
                            setOriginBounds(originLeft, originTop, originWidth, originHeight, cardRadius)
                            targetContainer?.overlay?.add(this)
                        }
                        previewGd.animateToAndFadeOut(drawL, originTop, nextW, nextH, cardRadius, 180L) {
                            targetContainer?.overlay?.remove(previewGd)
                            targetContainer?.invalidate()
                            ghostDrawable = null
                        }
                        tileView.alpha = 0f
                        tileView.animate().alpha(1.0f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        ControlCenterConfigHolder.cycleMediaPlayerSize(ctx, tileView)
                    } else {
                        val isCard = "wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)
                        val nextState = if (isCard) {
                            when {
                                ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_VERTICAL
                                ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_NORMAL
                                ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) -> STATE_EXPANDED
                                else -> STATE_LARGE
                            }
                        } else {
                            when {
                                ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_LARGE
                                ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_VERTICAL
                                ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_NORMAL
                                else -> STATE_EXPANDED
                            }
                        }
                        val nextW = when (nextState) {
                            STATE_EXPANDED, STATE_LARGE -> twoColW
                            else -> unitWidth1x1
                        }
                        val nextH = when (nextState) {
                            STATE_VERTICAL, STATE_LARGE -> cardHeight1x2
                            STATE_EXPANDED -> unitHeight1x1
                            else -> if (initialState == STATE_NORMAL) originHeight else unitWidth1x1
                        }
                        val nextRadius = if (nextState == STATE_NORMAL) (nextW / 2f) else cardRadius
                        val minLeft = targetContainer?.paddingLeft?.toFloat() ?: 0f
                        val maxRight = targetContainer?.let { (it.width - it.paddingRight).toFloat() } ?: (originLeft + nextW)
                        val drawL = if (originLeft + nextW > maxRight) (maxRight - nextW).coerceAtLeast(minLeft) else originLeft

                        val previewGd = gd ?: TileResizeGhostDrawable(density).apply {
                            setOriginBounds(originLeft, originTop, originWidth, originHeight, initialRadius)
                            targetContainer?.overlay?.add(this)
                        }
                        previewGd.animateToAndFadeOut(drawL, originTop, nextW, nextH, nextRadius, 180L) {
                            targetContainer?.overlay?.remove(previewGd)
                            targetContainer?.invalidate()
                            ghostDrawable = null
                        }
                        tileView.alpha = 0f
                        tileView.animate().alpha(1.0f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        ControlCenterConfigHolder.toggleTileSize(ctx, spec, tileView)
                    }
                }
                isDragging = false
                candidateTargetState = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                requestDisallowAll(v, false)
                tileView.alpha = 1.0f
                ghostDrawable?.let { gd ->
                    gd.animateToAndFadeOut(originLeft, originTop, originWidth, originHeight, initialRadius, 180L) {
                        targetContainer?.overlay?.remove(gd)
                        targetContainer?.invalidate()
                        ghostDrawable = null
                    }
                }
                isDragging = false
                candidateTargetState = -1
                return true
            }
        }
        return true
    }
}
