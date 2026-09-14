package dev.joseph.hyperbounce.controlcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
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

object ControlCenterOptimizedHook {
    private const val TAG = "HyperBounce_CC_Opt"
    private val hookedPluginLoaders = CopyOnWriteArrayList<ClassLoader>()
    @Volatile private var sysuiHooked = false
    @Volatile var sMediaPlayerViewHolder: java.lang.ref.WeakReference<Any>? = null
    @Volatile var sLastMediaPlayerMetaData: Any? = null
    private val sInChangeVisible = ThreadLocal<Boolean>()

    // Static reflection field cache for onFrameCallback to eliminate ~7,200 reflection calls/sec at 120Hz
    private var sHolderItemViewField: Field? = null
    private var sHolderExpandScaleRatioField: Field? = null
    private var sHolderExpandTransYField: Field? = null
    private var sHolderSpreadRowField: Field? = null
    @Volatile private var sHolderFieldsInitialized = false

    private fun initHolderFields(holderClass: Class<*>) {
        if (sHolderFieldsInitialized) return
        try {
            sHolderItemViewField = holderClass.getField("itemView")
            sHolderExpandScaleRatioField = holderClass.getDeclaredField("expandScaleRatio").apply { isAccessible = true }
            sHolderExpandTransYField = holderClass.getDeclaredField("expandTransY").apply { isAccessible = true }
            sHolderSpreadRowField = holderClass.getDeclaredField("spreadRow").apply { isAccessible = true }
            sHolderFieldsInitialized = true
        } catch (t: Throwable) {}
    }

    fun initSystemUIHooks(module: XposedModule, classLoader: ClassLoader?) {
        if (classLoader == null || sysuiHooked) return

        try {
            Log.i(TAG, "Initializing Control Center SystemUI Hooks...")

            try {
                val factoryClass = Class.forName("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", false, classLoader)
                for (m in factoryClass.declaredMethods) {
                    if (m.name == "createPlugin") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val plugin = chain.proceed()
                            if (plugin != null) {
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
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val miuiConfigsClass = Class.forName("com.miui.utils.configs.MiuiConfigs", false, classLoader)
                for (m in miuiConfigsClass.declaredMethods) {
                    if (m.name == "isLowEndDevice" || m.name == "isLiteOrLowDevice") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.lowEndAnim) true else chain.proceed()
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
                            if (ControlCenterConfigHolder.config.lowEndAnim) {
                                val target = chain.thisObject
                                if (target == ControlCenterConfigHolder.sShadeLowEndAnimFlow || (ControlCenterConfigHolder.sShadeLowEndAnimFlow == null && chain.proceed() is Boolean)) {
                                    return@intercept java.lang.Boolean.TRUE
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

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
                                    val filter = IntentFilter("dev.joseph.hyperbounce.RESET_CC_LAYOUT")
                                    app.registerReceiver(object : BroadcastReceiver() {
                                        override fun onReceive(context: Context?, intent: Intent?) {
                                            Log.i(TAG, "Broadcast received: dev.joseph.hyperbounce.RESET_CC_LAYOUT")
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

            sysuiHooked = true
        } catch (t: Throwable) {
            Log.e(TAG, "initSystemUIHooks fatal error", t)
        }
    }

    fun isChildOfContainer(view: View?): Boolean {
        var cur = view?.parent
        while (cur != null) {
            if (cur is View && (cur.id == 0x7f099995 || cur.getTag(0x7f099995) != null || cur.getTag(0x7f099993) != null)) {
                return true
            }
            if (cur is View && cur.javaClass.name.contains("RecyclerView")) return false
            cur = cur.parent
        }
        return false
    }

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

        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val ch = v.getChildAt(i) ?: continue
                if (ch.id == resizeTag || ch.id == selectTag || ch.id == rotateTag) {
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
                    childTile.isClickable = true
                    childTile.isLongClickable = true
                    childTile.isFocusable = true
                }
            }
        }
    }

    object TileLabelCache {
        var textModeEnumClass: Class<*>? = null
        var onTextModeChangedMethod: java.lang.reflect.Method? = null
        var getShowLabelMethod: java.lang.reflect.Method? = null
        var lastWordlessQueryTime: Long = 0L
        var cachedIsWordless: Boolean = false
        var iconFrameId: Int = 0
    }

    fun setCircularTileLabelVisibility(itemView: View, visible: Boolean = true) {
        try {
            val cName = itemView.javaClass.name
            if (!cName.contains("Tile") || cName.contains("Card")) {
                return
            }
            val ctx = itemView.context
            var shouldShow = true
            var resolved = false

            // 1. Try getShowLabel via cached reflection
            try {
                if (TileLabelCache.getShowLabelMethod == null) {
                    TileLabelCache.getShowLabelMethod = try {
                        val m = itemView.javaClass.getDeclaredMethod("getShowLabel")
                        m.isAccessible = true
                        m
                    } catch (t: Throwable) {
                        itemView.javaClass.getMethod("getShowLabel")
                    }
                }
                shouldShow = TileLabelCache.getShowLabelMethod?.invoke(itemView) as Boolean
                resolved = true
            } catch (t: Throwable) {}

            // 2. Fallback: query Settings.Secure "wordless_mode" (cached for 2s)
            val now = android.os.SystemClock.uptimeMillis()
            if (!resolved || now - TileLabelCache.lastWordlessQueryTime > 2000L) {
                TileLabelCache.lastWordlessQueryTime = now
                TileLabelCache.cachedIsWordless = try {
                    android.provider.Settings.Secure.getInt(ctx.contentResolver, "wordless_mode", 0) != 0
                } catch (t: Throwable) { false }
            }
            if (!resolved) {
                shouldShow = if (ControlCenterConfigHolder.sIsEditMode) true else !TileLabelCache.cachedIsWordless
            }

            // 3. Keep QSTileItemView internal textMode in sync using cached reflection
            try {
                if (TileLabelCache.textModeEnumClass == null) {
                    TileLabelCache.textModeEnumClass = itemView.javaClass.classLoader?.loadClass("miui.systemui.controlcenter.panel.main.qs.QSListController\$TextMode")
                }
                val enumCls = TileLabelCache.textModeEnumClass
                if (enumCls != null) {
                    val targetModeName = if (TileLabelCache.cachedIsWordless) "NO_TEXT" else "TEXT"
                    val targetMode = java.lang.Enum.valueOf(enumCls as Class<out Enum<*>>, targetModeName)
                    if (TileLabelCache.onTextModeChangedMethod == null) {
                        TileLabelCache.onTextModeChangedMethod = itemView.javaClass.methods.firstOrNull { it.name == "onTextModeChanged" }
                    }
                    TileLabelCache.onTextModeChangedMethod?.invoke(itemView, targetMode, false)
                }
            } catch (t: Throwable) {}

            if (TileLabelCache.iconFrameId == 0) {
                TileLabelCache.iconFrameId = ctx.resources.getIdentifier("icon_frame", "id", ctx.packageName)
            }
            val iconFrameId = TileLabelCache.iconFrameId
            fun process(v: View) {
                if (iconFrameId != 0 && v.id == iconFrameId) return
                if (v is TextView) {
                    v.visibility = if (shouldShow) View.VISIBLE else View.GONE
                    v.alpha = if (shouldShow) 1.0f else 0.0f
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
                            if (ControlCenterConfigHolder.config.lowEndAnim) true else chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

            try {
                val deviceUtilsClass = Class.forName("miui.systemui.util.DeviceUtils", false, classLoader)
                for (m in deviceUtilsClass.declaredMethods) {
                    if (m.name == "isLowEndDevice") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (ControlCenterConfigHolder.config.lowEndAnim) true else chain.proceed()
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
                                            ControlCenterConfigHolder.savePrioritiesAuto(ctx)
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
                                        } else if ("STOPPED" == stateStr || "DESTROYED" == stateStr) {
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
                }
            } catch (t: Throwable) {}

            // 4. Hook ControlCenterExpandController for Velocity and Hide Handlers
            try {
                val expandControllerClass = Class.forName("miui.systemui.controlcenter.windowview.ControlCenterExpandController", false, classLoader)
                for (m in expandControllerClass.declaredMethods) {
                    if (m.name == "showPanel" || m.name == "onStart") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            val windowView = chain.thisObject as? View
                            if (windowView != null) {
                                windowView.post {
                                    ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                                }
                            } else {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                                }
                            }
                            result
                        }
                    }
                    if (m.name == "hidePanel") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (System.currentTimeMillis() - ControlCenterConfigHolder.sLastTouchTime > 350) {
                                ControlCenterConfigHolder.sIsUserFlicking = false
                                ControlCenterConfigHolder.sCurrentVelocityY = 0.0f
                            }
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
                                    val rowsField = try {
                                        spreadAnimatorClass.getDeclaredField("animators")
                                    } catch (e: NoSuchFieldException) {
                                        spreadAnimatorClass.getDeclaredField("rows")
                                    }
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
                                        rotateBtn.visibility = if (isEdit) View.VISIBLE else View.GONE
                                    }
                                }
                            } catch (t: Throwable) {}
                            result
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
                            val result = try { chain.proceed() } catch (t: Throwable) { null }
                            if (ControlCenterConfigHolder.config.lowEndAnim || !ControlCenterConfigHolder.config.animEnabled) return@intercept result

                            try {
                                val holder = chain.thisObject
                                var ctx: Context? = null
                                try {
                                    val getCtxMethod = holder.javaClass.getMethod("getContext")
                                    ctx = getCtxMethod.invoke(holder) as? Context
                                } catch (t: Throwable) {
                                    try {
                                        val ivField = holder.javaClass.getField("itemView")
                                        val iv = ivField.get(holder) as? View
                                        if (iv != null) ctx = iv.context
                                    } catch (t2: Throwable) {}
                                }
                                if (ctx == null) return@intercept result

                                val screenW = ctx.resources.displayMetrics.widthPixels.toFloat()
                                val screenH = ctx.resources.displayMetrics.heightPixels.toFloat()

                                val shrinkCenterXField = holderClass.getDeclaredField("shrinkCenterX")
                                shrinkCenterXField.isAccessible = true
                                shrinkCenterXField.setFloat(holder, screenW / 2.0f)

                                val shrinkCenterYField = holderClass.getDeclaredField("shrinkCenterY")
                                shrinkCenterYField.isAccessible = true
                                shrinkCenterYField.setFloat(holder, screenH / 2.0f)

                                val delayPerField = holderClass.getDeclaredField("delayPer")
                                delayPerField.isAccessible = true
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
                            val result = try { chain.proceed() } catch (t: Throwable) { null }
                            try {
                                val holder = chain.thisObject

                                val delayPerField = holderClass.getDeclaredField("delayPer")
                                delayPerField.isAccessible = true
                                delayPerField.setFloat(holder, 0.0f)

                                val bounceEase = getStyleMethod.invoke(null, -2, floatArrayOf(0.88f, 0.38f))
                                val releaseTransYEaseField = holderClass.getDeclaredField("releaseTransYEase")
                                releaseTransYEaseField.isAccessible = true
                                releaseTransYEaseField.set(holder, bounceEase)

                                if (!ControlCenterConfigHolder.config.lowEndAnim && ControlCenterConfigHolder.config.animEnabled) {
                                    val showEaseField = holderClass.getDeclaredField("showEase")
                                    showEaseField.isAccessible = true
                                    val showEase = showEaseField.get(holder)
                                    if (showEase != null) {
                                        val setFactorsMethod = showEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                                        setFactorsMethod.isAccessible = true
                                        setFactorsMethod.invoke(showEase, ControlCenterConfigHolder.config.tileShowDamping, ControlCenterConfigHolder.config.tileShowResponse)
                                    }

                                    val hideEaseField = holderClass.getDeclaredField("hideEase")
                                    hideEaseField.isAccessible = true
                                    val hideEase = hideEaseField.get(holder)
                                    if (hideEase != null) {
                                        val setFactorsMethod = hideEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                                        setFactorsMethod.isAccessible = true
                                        setFactorsMethod.invoke(hideEase, ControlCenterConfigHolder.config.tileHideDamping, ControlCenterConfigHolder.config.tileHideResponse)
                                    }

                                    val shrinkXEaseField = holderClass.getDeclaredField("shrinkXEase")
                                    shrinkXEaseField.isAccessible = true
                                    val shrinkXEase = shrinkXEaseField.get(holder)
                                    if (shrinkXEase != null) {
                                        val setFactorsMethod = shrinkXEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                                        setFactorsMethod.isAccessible = true
                                        setFactorsMethod.invoke(shrinkXEase, ControlCenterConfigHolder.config.tileShrinkDamping, ControlCenterConfigHolder.config.tileShrinkResponse)
                                    }

                                    val shrinkYEaseField = holderClass.getDeclaredField("shrinkYEase")
                                    shrinkYEaseField.isAccessible = true
                                    val shrinkYEase = shrinkYEaseField.get(holder)
                                    if (shrinkYEase != null) {
                                        val setFactorsMethod = shrinkYEase.javaClass.getMethod("setFactors", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                                        setFactorsMethod.isAccessible = true
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
                            if (ControlCenterConfigHolder.config.modularEditEnabled) {
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
                        initHolderFields(holderClass)
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.jellyEnabled) {
                                try {
                                    val holder = chain.thisObject
                                    val itemView = (sHolderItemViewField?.get(holder) ?: holderClass.getField("itemView").get(holder)) as? View ?: return@intercept result

                                    val progress = sHolderExpandScaleRatioField?.getFloat(holder) ?: 1.0f
                                    val expandTransY = sHolderExpandTransYField?.getFloat(holder) ?: 0.0f
                                    val row = sHolderSpreadRowField?.getInt(holder) ?: 0

                                    val viewWidth = itemView.width
                                    val viewHeight = itemView.height
                                    if (viewWidth <= 0 || viewHeight <= 0) return@intercept result

                                    val isRecentGesture = (System.currentTimeMillis() - ControlCenterConfigHolder.sLastTouchTime) < 700
                                    val isClosing = (progress < 0.99f && ControlCenterConfigHolder.sCurrentVelocityY < -100.0f)

                                    if (!ControlCenterConfigHolder.sIsUserFlicking && !isRecentGesture && Math.abs(expandTransY) < 1.0f) {
                                        itemView.scaleX = 1.0f
                                        itemView.scaleY = 1.0f
                                        return@intercept result
                                    }

                                    val absVel = Math.abs(ControlCenterConfigHolder.sCurrentVelocityY)
                                    val velFactor = if (absVel > 0.1f) Math.max(0.35f, Math.min(1.20f, absVel / 3500.0f)) else 0.70f

                                    val density = itemView.context.resources.displayMetrics.density
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

                                    itemView.pivotX = viewWidth / 2.0f
                                    if (expandTransY > 3.0f || (!isClosing && progress > 0.05f && progress < 0.95f)) {
                                        itemView.pivotY = viewHeight * 0.10f
                                    } else if (expandTransY < -3.0f || isClosing) {
                                        itemView.pivotY = viewHeight * 0.90f
                                    } else {
                                        itemView.pivotY = viewHeight / 2.0f
                                    }

                                    itemView.scaleX = Math.max(0.78f, Math.min(1.30f, totalScaleX))
                                    itemView.scaleY = Math.max(0.78f, Math.min(1.40f, totalScaleY))
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
                                if (ControlCenterConfigHolder.config.modularEditEnabled && ControlCenterConfigHolder.sIsEditMode) {
                                    true
                                } else {
                                    orig
                                }
                            }
                        }

                        if (m.name == "getPriority" && m.parameterTypes.isEmpty()) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                if (ControlCenterConfigHolder.config.modularEditEnabled && ControlCenterConfigHolder.sComponentPriorities.containsKey(simpleName)) {
                                    ControlCenterConfigHolder.sComponentPriorities[simpleName]
                                } else {
                                    chain.proceed()
                                }
                            }
                        }

                        if (m.name == "getSpanSize" && m.parameterTypes.isEmpty()) {
                            m.isAccessible = true
                            module.hook(m).intercept { chain ->
                                when (simpleName) {
                                    "BrightnessSliderController" -> if (ControlCenterConfigHolder.sBrightnessSliderHorizontal) ControlCenterConfigHolder.sBrightnessSliderWidth else 1
                                    "VolumeSliderController" -> if (ControlCenterConfigHolder.sVolumeSliderHorizontal) ControlCenterConfigHolder.sVolumeSliderWidth else 1
                                    "MediaPlayerController" -> when (ControlCenterConfigHolder.sMediaPlayerSize) {
                                        "1x2", "1x1" -> 1
                                        "4x1", "4x2" -> 4
                                        else -> 2
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
                                ControlCenterConfigHolder.updateAllVisibleTileBadges()
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
                                    if (item != null && !ControlCenterConfigHolder.sAllQSRecords.contains(item)) {
                                        ControlCenterConfigHolder.sAllQSRecords.add(item)
                                    }
                                }
                            }
                            if (list != null && list.size >= 2) {
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
                            if (args != null && args.size >= 2) {
                                val parent = args[0] as ViewGroup
                                val vt = args[1] as Int
                                if (vt == 8453) {
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

                                if (ControlCenterConfigHolder.config.modularEditEnabled && ControlCenterConfigHolder.sIsEditMode) {
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
                            if (ControlCenterConfigHolder.config.modularEditEnabled) 5 else chain.proceed()
                        }
                    }
                    if (m.name == "onBindViewHolder") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.modularEditEnabled) {
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
                                if (vt == 2274 || vt == 2275 || vt == 2276 || vt in 2281..2284 || vt in 2285..2290 || vt == 2273 || vt == 2277 || vt == 2278) {
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
                            if (ControlCenterConfigHolder.config.modularEditEnabled) {
                                try {
                                    val distributor = chain.thisObject
                                    val getCtxMethod = distributorClass.getMethod("getContext")
                                    val ctx = getCtxMethod.invoke(distributor) as? Context
                                    ControlCenterConfigHolder.loadSavedPriorities(ctx)

                                    if (ctx != null) {
                                        try {
                                            val resetFilter = IntentFilter("dev.joseph.hyperbounce.RESET_CC_LAYOUT")
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
                                        if (g.getEffectiveLayoutType() == "2_CIRCULAR_VERT") return@intercept 1
                                        return@intercept 2
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
                                if ("wifi" == spec || "cell" == spec) return@intercept true
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
                                if ("wifi" == spec || "cell" == spec) return@intercept false
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

                                    if ("wifi" == spec || "cell" == spec) {
                                        return@intercept null
                                    }

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
                                val holderObj = chain.args.getOrNull(0)
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
                                        try {
                                            val setAddedM = record.javaClass.getMethod("setAdded", Boolean::class.javaPrimitiveType)
                                            setAddedM.invoke(record, true)
                                        } catch (t: Throwable) {
                                            try {
                                                val addedF = record.javaClass.getDeclaredField("added")
                                                addedF.isAccessible = true
                                                addedF.setBoolean(record, true)
                                            } catch (t2: Throwable) {}
                                        }
                                        if (tileViewObj != null) {
                                            try {
                                                val updateAddedM = tileViewObj.javaClass.getMethod("updateAdded", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                updateAddedM.invoke(tileViewObj, true, false)
                                            } catch (t: Throwable) {
                                                try {
                                                    val setAddedM = tileViewObj.javaClass.getDeclaredMethod("setAdded", Boolean::class.javaPrimitiveType)
                                                    setAddedM.isAccessible = true
                                                    setAddedM.invoke(tileViewObj, true)
                                                } catch (t2: Throwable) {}
                                            }
                                        }
                                    }

                                    val isSubTile = ControlCenterConfigHolder.isGroupChild(spec) || isChildOfContainer(itemView)
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
                                    val group = ControlCenterConfigHolder.isGroupAnchor(spec)
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
                                            if (isEdit) {
                                                rotateBtn.visibility = View.VISIBLE
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
                                                        ControlCenterConfigHolder.savePriorities(ctx)
                                                        ControlCenterConfigHolder.refreshRecyclerView(itemView)
                                                        ControlCenterConfigHolder.updateAllVisibleTileBadges()
                                                        ControlCenterConfigHolder.refreshAdapter(itemView.rootView, ctx, true)
                                                    } catch (t: Throwable) {
                                                        Log.e(TAG, "Rotate group click error", t)
                                                    }
                                                }
                                            } else {
                                                rotateBtn.visibility = View.GONE
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
                                                    val curIsExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)
                                                    val curIsVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                                    val curIsLarge = ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)
                                                    val curIsCirc = ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) || (!curIsExp && !curIsVert && !curIsLarge && !itemView.javaClass.name.contains("Card"))

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
                                                        if (!ControlCenterConfigHolder.sIsDisassembleMode) {
                                                            curHandle.visibility = View.VISIBLE
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

                                        if (iconFrame != null && iconFrame.getTag(0x7f099991) == null) {
                                            iconFrame.setTag(0x7f099991, true)
                                            iconFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                                val curHandle = itemView.findViewById<ImageView>(resizeTag)
                                                if (!ControlCenterConfigHolder.sIsEditMode) {
                                                    if (curHandle != null) {
                                                        curHandle.visibility = View.GONE
                                                        curHandle.setOnTouchListener(null)
                                                        curHandle.setOnClickListener(null)
                                                        curHandle.isClickable = false
                                                    }
                                                    return@addOnLayoutChangeListener
                                                }
                                                if (curHandle != null && curHandle.visibility == View.VISIBLE) {
                                                    val bw = curHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                    val bh = curHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                    val iRight = iconFrame.right
                                                    val iBottom = iconFrame.bottom
                                                    if (iRight > 0 && iBottom > 0) {
                                                        curHandle.layout(iRight - bw, iBottom - bh, iRight, iBottom)
                                                        curHandle.bringToFront()
                                                    }
                                                }
                                            }
                                        }

                                        if (resizeHandle != null) {
                                            if (isEdit && !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode) {
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
                                                    resizeHandle.setOnTouchListener(OptimizedTileResizeDragTouchListener(ctx, spec, itemView, density))
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
                                            } else if (ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) || spec == "wifi" || spec == "cell") {
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
                                                val shouldShow = !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
                                                if (shouldShow) {
                                                    resizeHandle.visibility = View.VISIBLE
                                                    val bw = resizeHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                    val bh = resizeHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                    val iconFrameId = itemView.resources.getIdentifier("icon_frame", "id", itemView.context.packageName)
                                                    val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null
                                                    val curIsExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)
                                                    val curIsVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                                    val curIsLarge = ControlCenterConfigHolder.sLargeTileSpecs.contains(spec)
                                                    val curIsCirc = ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) || (!curIsExp && !curIsVert && !curIsLarge && !itemView.javaClass.name.contains("Card") && (itemView.javaClass.name.contains("Tile") || itemView.javaClass.name.contains("QSTileItemView")))

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
                                                        resizeHandle.setOnTouchListener(OptimizedTileResizeDragTouchListener(itemView.context, spec, itemView, density))
                                                    }
                                                } else {
                                                    resizeHandle.visibility = View.GONE
                                                    resizeHandle.setOnTouchListener(null)
                                                    resizeHandle.isClickable = false
                                                }
                                            }

                                            val selectBadge = itemView.findViewById<ImageView>(0x7f099998)
                                            if (selectBadge != null && !isChildOfContainer(itemView)) {
                                                if (!ControlCenterConfigHolder.sIsGroupSelectMode) {
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
                                                rotateBtn.visibility = View.VISIBLE
                                                rotateBtn.bringToFront()
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
                    if (m.name == "updateBlurBlendBackground" || m.name == "updateDrawableBackground" || m.name == "updateBackground" || m.name == "updateClip") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                ControlCenterGrouping.applyGroupContainerStyle(itemView, classLoader)
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
                                            if (subHolder != null) {
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
                            val result = chain.proceed()
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

            // 16d. Hook MediaPanelAnimator for secondary panel expand/collapse persistence
            try {
                val mediaAnimClass = Class.forName("miui.systemui.controlcenter.panel.secondary.media.MediaPanelAnimator", false, classLoader)
                for (m in mediaAnimClass.declaredMethods) {
                    if (m.name == "onAnimComplete") {
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

            // 17. Full 100% Unlock (Zero Boundaries & Dynamic Landscape Flow)
            try {
                ControlCenterFullUnlock.init(module, classLoader)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize ControlCenterFullUnlock", t)
            }

            // 17b. Hook BlurController
            try {
                val blurClass = Class.forName("miui.systemui.controlcenter.windowview.BlurController", false, classLoader)
                for (m in blurClass.declaredMethods) {
                    if (m.name == "onCreate") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.lowEndAnim || !ControlCenterConfigHolder.config.animEnabled) return@intercept result
                            try {
                                val blurController = chain.thisObject
                                val animConfigField = blurClass.getDeclaredField("animConfig")
                                animConfigField.isAccessible = true
                                val animConfig = animConfigField.get(blurController)
                                if (animConfig != null) {
                                    val easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader)
                                    val getStyleMethod = easeManagerClass.getMethod("getStyle", Int::class.javaPrimitiveType, FloatArray::class.java)
                                    val customEase = getStyleMethod.invoke(null, -2, floatArrayOf(ControlCenterConfigHolder.config.blurDamping, ControlCenterConfigHolder.config.blurResponse))
                                    val setEaseMethod = animConfig.javaClass.getMethod("setEase", easeManagerClass.declaredClasses[0])
                                    setEaseMethod.isAccessible = true
                                    setEaseMethod.invoke(animConfig, customEase)
                                }
                            } catch (t: Throwable) {}
                            result
                        }
                    }
                }
            } catch (t: Throwable) {}

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
                    "miui.systemui.controlcenter.widget.ClickableRecyclerView"
                )
                for (clsName in rvClasses) {
                    try {
                        val rvCls = Class.forName(clsName, false, classLoader)
                        for (m in rvCls.declaredMethods) {
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
                                applyExpandedBrightnessLayout(chain.thisObject)
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
                        setVertM.invoke(injector, !isHorizontal)
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
            val progressRadius = 16f * density

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
                                outline.setRoundRect(0, 0, w, view.height, progressRadius)
                            } else {
                                val top = (view.height * (1f - frac)).toInt()
                                outline.setRoundRect(0, top, view.width, view.height, progressRadius)
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

            val rotateTag = 0x7f099992
            var rotateBtn = itemView.findViewById<ImageView>(rotateTag)
            if (ControlCenterConfigHolder.sIsEditMode) {
                val shouldShow = !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
                if (rotateBtn == null && itemView is ViewGroup) {
                    val rSize = (32 * density).toInt()
                    rotateBtn = ImageView(ctx).apply {
                        id = rotateTag
                        val lp = FrameLayout.LayoutParams(rSize, rSize).apply {
                            gravity = Gravity.TOP or Gravity.END
                            val m = (6 * density).toInt()
                            setMargins(0, m, m, 0)
                        }
                        layoutParams = lp
                        setImageDrawable(RotateIconDrawable())
                        setOnClickListener {
                            ControlCenterConfigHolder.toggleSliderOrientation(ctx, isBrightness, itemView)
                        }
                    }
                    itemView.addView(rotateBtn)
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
        } catch (t: Throwable) {
            Log.e(TAG, "applySliderLayoutAndTouch error", t)
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

            val isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            var size = if (isLandscape) "4x1" else ControlCenterConfigHolder.sMediaPlayerSize
            if (size == "1x1") size = "2x2"

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

            val curLp = itemView.layoutParams
            if (curLp != null) {
                curLp.width = ViewGroup.LayoutParams.MATCH_PARENT
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

            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
            val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (14 * density).toInt()
            val fourRowsH = 4 * cardH + 3 * gap

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
                val rad = if (size == "4x2" || size == "2x4") 18f * density else 12f * density
                cover.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, rad)
                    }
                }
                cover.clipToOutline = true
            }

            listOf(cover, play, prev, next, audioContainer).forEach { v ->
                v?.alpha = 1f
                v?.scaleX = 1f
                v?.scaleY = 1f
                v?.rotationY = 0f
                v?.translationX = 0f
                v?.translationY = 0f
            }

            cover?.visibility = View.VISIBLE
            prev?.visibility = View.VISIBLE
            play?.visibility = View.VISIBLE
            next?.visibility = View.VISIBLE
            audioContainer?.visibility = View.VISIBLE

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
                    val prevLp = prev?.layoutParams
                    val playLp = play?.layoutParams
                    val nextLp = next?.layoutParams
                    val tLp = title?.layoutParams
                    val aLp = artist?.layoutParams
                    val eLp = emptyState?.layoutParams
                    val diLp = deviceIcon?.layoutParams

                    when (size) {
                        "2x1" -> {
                            if (curLp != null && curLp.height != cardH) {
                                curLp.height = cardH
                                itemView.layoutParams = curLp
                            }
                            title?.visibility = View.GONE
                            artist?.visibility = View.GONE
                            emptyState?.visibility = View.GONE
                            deviceIcon?.visibility = View.GONE

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

                            val btnSize = (34 * density).toInt()
                            val playSize = (38 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToStart", playId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToBottom", pId)
                                setField(prevLp, "horizontalChainStyle", 0)
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
                                next.layoutParams = nextLp
                            }
                        }

                        "1x2" -> {
                            if (curLp != null && curLp.height != tallH) {
                                curLp.height = tallH
                                itemView.layoutParams = curLp
                            }
                            title?.visibility = View.GONE
                            artist?.visibility = View.GONE
                            emptyState?.visibility = View.GONE
                            deviceIcon?.visibility = View.GONE

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

                            val btnSize = (32 * density).toInt()
                            val playSize = (38 * density).toInt()
                            if (prevLp != null && prevLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(prevLp)
                                prevLp.width = btnSize
                                prevLp.height = btnSize
                                setField(prevLp, "startToStart", pId)
                                setField(prevLp, "endToEnd", pId)
                                setField(prevLp, "topToTop", pId)
                                setField(prevLp, "bottomToTop", playId)
                                setField(prevLp, "verticalChainStyle", 0)
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
                                next.layoutParams = nextLp
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
                                setField(cLp, "topToTop", pId)
                                setField(cLp, "bottomToBottom", pId)
                                if (cLp is ViewGroup.MarginLayoutParams) {
                                    cLp.marginStart = (14 * density).toInt()
                                }
                                cover.layoutParams = cLp
                            }

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "topToTop", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.marginEnd = (14 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
                            }

                            val btnSize = (34 * density).toInt()
                            val playSize = (38 * density).toInt()
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

                        "4x2" -> {
                            if (curLp != null && curLp.height != tallH) {
                                curLp.height = tallH
                                itemView.layoutParams = curLp
                            }
                            deviceIcon?.visibility = View.VISIBLE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
                            }

                            val coverSize = (129 * density).toInt()
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
                                    acLp.bottomMargin = (18 * density).toInt()
                                }
                                audioContainer.layoutParams = acLp
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
                                        tLp.topMargin = (20 * density).toInt()
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
                                    setField(aLp, "bottomToTop", audioContainerId)
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

                        "2x4" -> {
                            if (curLp != null && curLp.height != fourRowsH) {
                                curLp.height = fourRowsH
                                itemView.layoutParams = curLp
                            }
                            deviceIcon?.visibility = View.VISIBLE
                            if (hasTrack) {
                                title?.visibility = View.VISIBLE
                                artist?.visibility = View.VISIBLE
                                emptyState?.visibility = View.GONE
                            } else {
                                title?.visibility = View.GONE
                                artist?.visibility = View.GONE
                                emptyState?.visibility = View.VISIBLE
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
                                        tLp.topMargin = (16 * density).toInt()
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

                            if (acLp != null && acLp.javaClass.name.contains("ConstraintLayout")) {
                                resetConstraints(acLp)
                                acLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                acLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                setField(acLp, "startToStart", pId)
                                setField(acLp, "endToEnd", pId)
                                setField(acLp, "bottomToBottom", pId)
                                if (acLp is ViewGroup.MarginLayoutParams) {
                                    acLp.bottomMargin = (22 * density).toInt()
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
                    val isLand = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val curSize = if (isLand) "4x1" else ControlCenterConfigHolder.sMediaPlayerSize
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

                    if (curSize == "2x1" || curSize == "1x2") {
                        if (title?.visibility != View.GONE) title?.visibility = View.GONE
                        if (artist?.visibility != View.GONE) artist?.visibility = View.GONE
                        if (emptyState?.visibility != View.GONE) emptyState?.visibility = View.GONE
                        if (emptyState?.layoutParams != null && emptyState.layoutParams.width != 0) {
                            emptyState.layoutParams.width = 0
                        }
                        if (deviceIcon?.visibility != View.GONE) deviceIcon?.visibility = View.GONE
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
                        if (curSize != "4x1" && deviceIcon?.visibility != View.VISIBLE) {
                            deviceIcon?.visibility = View.VISIBLE
                        }
                    }
                    if (cover?.visibility != View.VISIBLE) cover?.visibility = View.VISIBLE
                    if (prev?.visibility != View.VISIBLE) prev?.visibility = View.VISIBLE
                    if (play?.visibility != View.VISIBLE) play?.visibility = View.VISIBLE
                    if (next?.visibility != View.VISIBLE) next?.visibility = View.VISIBLE
                    if (cover is ImageView && cover.drawable == null) {
                        val defCoverId = ctx.resources.getIdentifier("miplay_cover_default", "drawable", ctx.packageName)
                        if (defCoverId != 0) cover.setImageResource(defCoverId)
                    }
                }
                itemView.addOnLayoutChangeListener(layoutListener)
                itemView.setTag(listenerTag, layoutListener)
            }

            val resizeTag = 0x7f099999
            var resizeHandle = itemView.findViewById<ImageView>(resizeTag)
            if (ControlCenterConfigHolder.sIsEditMode) {
                val shouldShow = !ControlCenterConfigHolder.sIsGroupSelectMode && !ControlCenterConfigHolder.sIsDisassembleMode
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
                        resizeHandle.setOnTouchListener(OptimizedTileResizeDragTouchListener(ctx, "media", itemView, density))
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
            val progressRadius = 16f * density

            if (isHorizontal) {
                val lp = toggleSliderView.layoutParams
                if (lp != null) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = cardH
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.topMargin = (72 * density).toInt()
                        lp.marginStart = 0
                        lp.marginEnd = 0
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
                            outline.setRoundRect(0, 0, w, view.height, progressRadius)
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
                        lp.topMargin = 0
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
class OptimizedTileResizeDragTouchListener(
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
    private var row4H = 0f

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
                    isMediaSpec -> when (ControlCenterConfigHolder.sMediaPlayerSize) {
                        "2x2" -> STATE_LARGE
                        "2x1" -> STATE_EXPANDED
                        "1x2" -> STATE_VERTICAL
                        "4x1" -> STATE_WIDE_1
                        "4x2" -> STATE_WIDE_2
                        "2x4" -> STATE_TALL_4
                        else -> STATE_LARGE
                    }
                    ControlCenterConfigHolder.sLargeTileSpecs.contains(spec) -> STATE_LARGE
                    ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_EXPANDED
                    ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_VERTICAL
                    ("wifi".equals(spec, ignoreCase = true) || "cell".equals(spec, ignoreCase = true)) && !ControlCenterConfigHolder.sCircularCardSpecs.contains(spec) -> STATE_EXPANDED
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
                    row4H = 4f * cardH + 3f * gap

                    if (isSliderSpec) {
                        originWidth = when (initialState) {
                            3 -> threeColW
                            4 -> fourColW
                            else -> twoColW
                        }
                        originHeight = row1H
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

                    val initialRadius = if (initialState == STATE_NORMAL && !isMediaSpec && !isSliderSpec) Math.min(originWidth, originHeight) / 2f else cardRadius
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
                    val snapDistX = (oneColW + 10f * density) * 0.35f
                    val snapDistY = (row1H + 10f * density) * 0.35f

                    when (initialState) {
                        STATE_LARGE -> { // 2x2
                            if (dx > snapDistX) {
                                targetState = if (dy < -snapDistY * 0.6f) STATE_WIDE_1 else STATE_WIDE_2
                            } else if (dx < -snapDistX) {
                                targetState = STATE_VERTICAL
                            } else if (dy > snapDistY) {
                                targetState = STATE_TALL_4
                            } else if (dy < -snapDistY) {
                                targetState = STATE_EXPANDED
                            }
                        }
                        STATE_EXPANDED -> { // 2x1
                            if (dx > snapDistX) {
                                targetState = if (dy > snapDistY * 0.6f) STATE_WIDE_2 else STATE_WIDE_1
                            } else if (dy > snapDistY) {
                                targetState = if (dy > snapDistY * 2.0f) STATE_TALL_4 else STATE_LARGE
                            } else if (dx < -snapDistX && dy > snapDistY * 0.5f) {
                                targetState = STATE_VERTICAL
                            }
                        }
                        STATE_VERTICAL -> { // 1x2
                            if (dx > snapDistX) {
                                if (dx > snapDistX * 2.0f) {
                                    targetState = if (dy < -snapDistY * 0.6f) STATE_WIDE_1 else STATE_WIDE_2
                                } else {
                                    targetState = if (dy < -snapDistY * 0.6f) STATE_EXPANDED else STATE_LARGE
                                }
                            } else if (dy > snapDistY) {
                                targetState = STATE_TALL_4
                            } else if (dy < -snapDistY && dx > snapDistX * 0.5f) {
                                targetState = STATE_EXPANDED
                            }
                        }
                        STATE_WIDE_1 -> { // 4x1
                            if (dx < -snapDistX && dy > snapDistY * 1.2f) {
                                targetState = STATE_TALL_4
                            } else if (dy > snapDistY) {
                                targetState = if (dx < -snapDistX * 1.5f) STATE_LARGE else STATE_WIDE_2
                            } else if (dx < -snapDistX) {
                                targetState = if (dy > snapDistY * 0.5f) STATE_LARGE else STATE_EXPANDED
                            }
                        }
                        STATE_WIDE_2 -> { // 4x2
                            if (dy < -snapDistY) {
                                targetState = if (dx < -snapDistX) STATE_EXPANDED else STATE_WIDE_1
                            } else if (dx < -snapDistX) {
                                if (dy > snapDistY) {
                                    targetState = STATE_TALL_4
                                } else if (dy < -snapDistY) {
                                    targetState = STATE_EXPANDED
                                } else if (dx < -snapDistX * 2.2f) {
                                    targetState = STATE_VERTICAL
                                } else {
                                    targetState = STATE_LARGE
                                }
                            } else if (dy > snapDistY && dx < -snapDistX * 0.5f) {
                                targetState = STATE_TALL_4
                            }
                        }
                        STATE_TALL_4 -> { // 2x4
                            if (dx > snapDistX && dy < -snapDistY * 1.2f) {
                                targetState = STATE_WIDE_1
                            } else if (dy < -snapDistY) {
                                if (dy < -snapDistY * 2.0f) {
                                    targetState = if (dx > snapDistX) STATE_WIDE_1 else STATE_EXPANDED
                                } else {
                                    if (dx > snapDistX) {
                                        targetState = STATE_WIDE_2
                                    } else if (dx < -snapDistX) {
                                        targetState = STATE_VERTICAL
                                    } else {
                                        targetState = STATE_LARGE
                                    }
                                }
                            } else if (dx > snapDistX && dy < -snapDistY * 0.5f) {
                                targetState = STATE_WIDE_2
                            } else if (dx < -snapDistX && dy < -snapDistY * 0.5f) {
                                targetState = STATE_VERTICAL
                            } else if (dx > snapDistX) {
                                targetState = STATE_WIDE_2
                            } else if (dx < -snapDistX) {
                                targetState = STATE_VERTICAL
                            }
                        }
                        else -> {
                            targetState = STATE_LARGE
                        }
                    }

                    val (targetW, targetH) = when (targetState) {
                        STATE_EXPANDED -> Pair(twoColW, row1H)     // 2x1
                        STATE_VERTICAL -> Pair(oneColW, row2H)     // 1x2
                        STATE_LARGE -> Pair(twoColW, row2H)        // 2x2
                        STATE_WIDE_1 -> Pair(fourColW, row1H)      // 4x1
                        STATE_WIDE_2 -> Pair(fourColW, row2H)      // 4x2
                        STATE_TALL_4 -> Pair(twoColW, row4H)       // 2x4
                        else -> Pair(originWidth, originHeight)
                    }

                    val rawW = (originWidth + dx).coerceIn(oneColW * 0.75f, fourColW * 1.05f)
                    val rawH = (originHeight + dy).coerceIn(row1H * 0.75f, row4H * 1.05f)

                    if (targetState != initialState) {
                        val snapFactor = 0.60f
                        previewW = lerp(rawW, targetW, snapFactor)
                        previewH = lerp(rawH, targetH, snapFactor)
                    } else {
                        previewW = rawW
                        previewH = rawH
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
                tileView.alpha = 1.0f

                ghostDrawable?.let { gd ->
                    targetContainer?.overlay?.remove(gd)
                    targetContainer?.invalidate()
                }

                if (isDragging) {
                    if (candidateTargetState != -1 && candidateTargetState != initialState) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

                        // 1. Immediately apply the target dimensions to tileView's layout params so it snaps without waiting
                        val (targetW, targetH) = when (candidateTargetState) {
                            STATE_EXPANDED -> Pair(twoColW, row1H)     // 2x1
                            STATE_VERTICAL -> Pair(oneColW, row2H)     // 1x2
                            STATE_LARGE -> Pair(twoColW, row2H)        // 2x2
                            STATE_WIDE_1 -> Pair(fourColW, row1H)      // 4x1
                            STATE_WIDE_2 -> Pair(fourColW, row2H)      // 4x2
                            STATE_TALL_4 -> Pair(twoColW, row4H)       // 2x4
                            STATE_NORMAL -> Pair(unitWidth1x1, unitHeight1x1) // 1x1
                            else -> Pair(originWidth, originHeight)
                        }

                        if (targetW > 0f && targetH > 0f) {
                            tileView.layoutParams?.let { lp ->
                                lp.width = targetW.toInt()
                                lp.height = targetH.toInt()
                                tileView.layoutParams = lp
                            }
                        }

                        // 2. Un-suppress layout on tileView and its parent cards
                        try {
                            (tileView as? ViewGroup)?.suppressLayout(false)
                            var p = tileView.parent
                            while (p != null && p is ViewGroup) {
                                p.suppressLayout(false)
                                if (p.javaClass.name.contains("RecyclerView")) break
                                p = p.parent
                            }
                        } catch (t: Throwable) {}

                        // 3. Mutate sizes in config and trigger optimized refresh
                        if (isSliderSpec) {
                            ControlCenterConfigHolder.setSliderWidth(ctx, isBrightnessSpec, candidateTargetState, tileView)
                        } else if (isMediaSpec) {
                            val targetMediaSize = when (candidateTargetState) {
                                STATE_EXPANDED -> "2x1"
                                STATE_VERTICAL -> "1x2"
                                STATE_LARGE -> "2x2"
                                STATE_WIDE_1 -> "4x1"
                                STATE_WIDE_2 -> "4x2"
                                STATE_TALL_4 -> "2x4"
                                else -> "2x2"
                            }
                            ControlCenterConfigHolder.setMediaSize(ctx, targetMediaSize, tileView)
                        } else {
                            when (candidateTargetState) {
                                STATE_EXPANDED -> ControlCenterConfigHolder.setTileToExpanded(ctx, spec, tileView)
                                STATE_VERTICAL -> ControlCenterConfigHolder.setTileToVertical(ctx, spec, tileView)
                                STATE_LARGE -> ControlCenterConfigHolder.setTileToLarge(ctx, spec, tileView)
                                STATE_NORMAL -> ControlCenterConfigHolder.setTileToNormal(ctx, spec, tileView)
                            }
                        }
                    }
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (isSliderSpec) {
                        ControlCenterConfigHolder.cycleSliderWidth(ctx, isBrightnessSpec, tileView)
                    } else {
                        ControlCenterConfigHolder.toggleTileSize(ctx, spec, tileView)
                    }
                }
                targetContainer?.requestLayout()
                isDragging = false
                candidateTargetState = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                ControlCenterConfigHolder.sIsDraggingResizeHandle = false
                requestDisallowAll(v, false)
                tileView.alpha = 1.0f
                ghostDrawable?.let { gd ->
                    targetContainer?.overlay?.remove(gd)
                    targetContainer?.invalidate()
                }
                isDragging = false
                candidateTargetState = -1
                return true
            }
        }
        return true
    }
}
