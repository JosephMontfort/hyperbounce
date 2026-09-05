package dev.joseph.hyperbounce.controlcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
        val resizeHandle = v.findViewById<View>(0x7f099999)
        if (resizeHandle != null) {
            resizeHandle.visibility = View.GONE
        }
        val selectBadge = v.findViewById<View>(0x7f099998)
        if (selectBadge != null) {
            selectBadge.visibility = View.GONE
        }
    }

    fun setCircularTileLabelVisibility(itemView: View, visible: Boolean) {
        try {
            var shouldShow = visible
            if (visible) {
                try {
                    val getShowLabelM = itemView.javaClass.getMethod("getShowLabel")
                    shouldShow = getShowLabelM.invoke(itemView) as Boolean
                } catch (t: Throwable) {
                    try {
                        val isShowLabelM = itemView.javaClass.getMethod("isShowLabel")
                        shouldShow = isShowLabelM.invoke(itemView) as Boolean
                    } catch (t2: Throwable) {}
                }
            }
            val iconFrameId = itemView.resources.getIdentifier("icon_frame", "id", itemView.context.packageName)
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
                    if (m.name == "set_mode") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.isNotEmpty() && args[0] != null) {
                                    val modeName = args[0].toString()
                                    ControlCenterConfigHolder.sIsEditMode = "EDIT" == modeName
                                    if (!ControlCenterConfigHolder.sIsEditMode) {
                                        try {
                                            val modeCtrl = chain.thisObject
                                            val getCtxMethod = modeCtrl.javaClass.getMethod("getContext")
                                            val ctx = getCtxMethod.invoke(modeCtrl) as? Context
                                            ControlCenterConfigHolder.savePriorities(ctx)
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
                                    if (stateObj != null && "STARTED" == stateObj.toString()) {
                                        if (ControlCenterConfigHolder.sPendingOrientationRebind) {
                                            ControlCenterConfigHolder.sPendingOrientationRebind = false
                                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                                            val windowView = chain.thisObject as? View
                                            if (windowView != null) {
                                                ControlCenterConfigHolder.refreshAdapter(windowView, windowView.context)
                                            }
                                        } else {
                                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
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
                            ControlCenterConfigHolder.wakeAndRefreshAllGroupedTiles()
                            chain.proceed()
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
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            if (ControlCenterConfigHolder.config.jellyEnabled) {
                                try {
                                    val holder = chain.thisObject
                                    val ivField = holderClass.getField("itemView")
                                    val itemView = ivField.get(holder) as? View ?: return@intercept result

                                    val expandScaleRatioField = holderClass.getDeclaredField("expandScaleRatio")
                                    expandScaleRatioField.isAccessible = true
                                    val progress = expandScaleRatioField.getFloat(holder)

                                    val expandTransYField = holderClass.getDeclaredField("expandTransY")
                                    expandTransYField.isAccessible = true
                                    val expandTransY = expandTransYField.getFloat(holder)

                                    val spreadRowField = holderClass.getDeclaredField("spreadRow")
                                    spreadRowField.isAccessible = true
                                    val row = spreadRowField.getInt(holder)

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
                    }
                } catch (t: Throwable) {}
            }

            // Hook QSCardsController.getListItems for Wi-Fi / Cell card swapping
            try {
                val cardsClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardsController", false, classLoader)
                for (m in cardsClass.declaredMethods) {
                    if (m.name == "getListItems") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val list = chain.proceed() as? List<*>
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
                                ControlCenterConfigHolder.sAllQSRecords.clear()
                                for (item in originalList) {
                                    if (item != null) ControlCenterConfigHolder.sAllQSRecords.add(item)
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
                                        if (viewType == 2273) {
                                            val holder = ControlCenterGrouping.createCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        }
                                        if (viewType == 2277) {
                                            val holder = ControlCenterGrouping.createVerticalSingleCardViewHolder(parent, qsListCtrl, classLoader)
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
                                if (vt == 2274 || vt == 2275 || vt == 2276 || vt == 2273 || vt == 2277) {
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
                                        } else if (vt == 2273) {
                                            val holder = ControlCenterGrouping.createCardViewHolder(parent, qsListCtrl, classLoader)
                                            if (holder != null) return@intercept holder
                                        } else if (vt == 2277) {
                                            val holder = ControlCenterGrouping.createVerticalSingleCardViewHolder(parent, qsListCtrl, classLoader)
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
                                        if (g.specs.size == 4) return@intercept 2274
                                        if (g.specs.size == 2) return@intercept if (g.isVertical) 2276 else 2275
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
                                        if (g.specs.size == 4) return@intercept 2
                                        if (g.specs.size == 2) return@intercept if (g.isVertical) 1 else 2
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
                                                            ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                        }
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
                                if (tileViewObj is View) {
                                    val itemView = tileViewObj
                                    val ctx = itemView.context
                                    val density = ctx.resources.displayMetrics.density

                                    if (spec != null) {
                                        itemView.setTag(0x7f099990, spec)
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
                                                        group.isVertical = !group.isVertical
                                                        ControlCenterConfigHolder.savePriorities(ctx)
                                                        ControlCenterConfigHolder.refreshAdapter(itemView.rootView, ctx)
                                                    } catch (t: Throwable) {
                                                        Log.e(TAG, "Rotate group click error", t)
                                                    }
                                                }
                                            } else {
                                                rotateBtn.visibility = View.GONE
                                            }
                                        }

                                        val container = itemView.findViewById<ViewGroup>(0x7f099995)
                                        if (container != null) {
                                            val recordMap = HashMap<String, Any>()
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

                                            if (activeQsListCtrl != null) {
                                                val listFieldNames = arrayOf("addedTiles", "copiedTiles", "systemTiles", "packageTiles", "records")
                                                for (fn in listFieldNames) {
                                                    try {
                                                        val f = activeQsListCtrl.javaClass.getDeclaredField(fn)
                                                        f.isAccessible = true
                                                        val list = f.get(activeQsListCtrl) as? List<*>
                                                        if (list != null) {
                                                            for (r in list) {
                                                                val s = ControlCenterConfigHolder.getRecordSpec(r)
                                                                if (s != null && !recordMap.containsKey(s) && r != null) recordMap[s] = r
                                                            }
                                                        }
                                                    } catch (t: Throwable) {}
                                                }
                                            }

                                            for (r in ControlCenterConfigHolder.sAllQSRecords) {
                                                val s = ControlCenterConfigHolder.getRecordSpec(r)
                                                if (s != null && !recordMap.containsKey(s)) recordMap[s] = r
                                            }

                                            ControlCenterGrouping.applyGroupContainerStyle(itemView, classLoader)

                                            var subHolders = itemView.getTag(0x7f099993) as? Array<Any?>
                                            if (subHolders == null) {
                                                subHolders = arrayOfNulls(4)
                                                var subHolderCtor: Constructor<*>? = null
                                                try {
                                                    val subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader)
                                                    for (c in subHolderClass.declaredConstructors) {
                                                        if (c.parameterTypes.size == 2) {
                                                            subHolderCtor = c
                                                            break
                                                        }
                                                    }
                                                    if (subHolderCtor == null && subHolderClass.declaredConstructors.isNotEmpty()) {
                                                        subHolderCtor = subHolderClass.declaredConstructors[0]
                                                    }
                                                    subHolderCtor?.isAccessible = true
                                                } catch (t: Throwable) {}

                                                var mainPanelController: Any? = null
                                                try {
                                                    val mainPanelControllerField = record.javaClass.getDeclaredField("mainPanelController")
                                                    mainPanelControllerField.isAccessible = true
                                                    val mainPanelCtrlLazy = mainPanelControllerField.get(record)
                                                    val getMethod = mainPanelCtrlLazy?.javaClass?.getMethod("get")
                                                    mainPanelController = getMethod?.invoke(mainPanelCtrlLazy)
                                                } catch (t: Throwable) {}

                                                for (i in 0 until 4) {
                                                    val childTile = ControlCenterGrouping.getChildTileAt(container, i)
                                                    if (childTile != null && subHolderCtor != null) {
                                                        try {
                                                            subHolders[i] = if (subHolderCtor.parameterTypes.size == 2) {
                                                                subHolderCtor.newInstance(childTile, mainPanelController)
                                                            } else {
                                                                subHolderCtor.newInstance(childTile)
                                                            }
                                                        } catch (t: Throwable) {}
                                                    }
                                                }
                                                itemView.setTag(0x7f099993, subHolders)
                                            }

                                            for (i in 0 until Math.min(4, group.specs.size)) {
                                                val childSpec = group.specs[i]
                                                val childView = ControlCenterGrouping.getChildTileAt(container, i)
                                                val childRecord = recordMap[childSpec]
                                                val subHolder = if (i < subHolders.size) subHolders[i] else null

                                                if (childRecord != null && subHolder != null && childView != null) {
                                                    try {
                                                        for (shM in subHolder.javaClass.methods) {
                                                            if (shM.name.startsWith("setItem")) {
                                                                shM.invoke(subHolder, childRecord)
                                                                break
                                                            }
                                                        }
                                                        val setHolderMethod = childRecord.javaClass.getMethod("setHolder", Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader))
                                                        setHolderMethod.invoke(childRecord, subHolder)

                                                        val onBindMethod = childRecord.javaClass.getMethod("onBindViewHolder")
                                                        onBindMethod.invoke(childRecord)

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
                                                            val modeClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController\$Mode", false, classLoader)
                                                            val currentMode = modeClass.getMethod("valueOf", String::class.java).invoke(null, if (isEdit) "EDIT" else "NORMAL")
                                                            val updateModeMethod = childRecord.javaClass.getMethod("updateMode", modeClass, Boolean::class.javaPrimitiveType)
                                                            updateModeMethod.invoke(childRecord, currentMode, false)
                                                        } catch (t: Throwable) {}

                                                        try {
                                                            val addCb = childRecord.javaClass.getMethod("addCallback")
                                                            addCb.invoke(childRecord)
                                                        } catch (t: Throwable) {}

                                                        try {
                                                            val getTileM = childRecord.javaClass.getMethod("getTile")
                                                            val tileObj = getTileM.invoke(childRecord)
                                                            if (tileObj != null) {
                                                                val setLisM = tileObj.javaClass.getMethod("setListening", Any::class.java, Boolean::class.javaPrimitiveType)
                                                                setLisM.invoke(tileObj, childRecord, true)
                                                                val refM = tileObj.javaClass.getMethod("refreshState")
                                                                refM.invoke(tileObj)

                                                                val getStateM = tileObj.javaClass.getMethod("getState")
                                                                val curState = getStateM.invoke(tileObj)
                                                                if (curState != null) {
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

                                                        if (isEdit) {
                                                            childView.isClickable = false
                                                            childView.isLongClickable = false
                                                            childView.isFocusable = false
                                                        } else {
                                                            childView.isClickable = true
                                                            childView.isLongClickable = true
                                                            childView.isFocusable = true
                                                        }
                                                    } catch (t: Throwable) {
                                                        Log.e(TAG, "Sub-tile binding error: $childSpec", t)
                                                    }
                                                }
                                            }
                                        }

                                        // Disassemble Selection Indicator
                                        val selectTag = 0x7f099998
                                        val indicatorBadge = itemView.findViewById<ImageView>(selectTag)

                                        if (ControlCenterConfigHolder.sIsDisassembleMode) {
                                            if (indicatorBadge != null) {
                                                val scd = (indicatorBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                                scd.setChecked(ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id))
                                                indicatorBadge.setImageDrawable(scd)
                                                indicatorBadge.visibility = View.VISIBLE
                                                indicatorBadge.bringToFront()
                                                indicatorBadge.isClickable = true

                                                val toggleDisassembleSelect = View.OnClickListener {
                                                    if (ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id)) {
                                                        ControlCenterConfigHolder.sSelectedDisassembleGroups.remove(group.id)
                                                    } else {
                                                        ControlCenterConfigHolder.sSelectedDisassembleGroups.add(group.id)
                                                    }
                                                    val curScd = indicatorBadge.drawable as? SelectCircleDrawable
                                                    curScd?.setChecked(ControlCenterConfigHolder.sSelectedDisassembleGroups.contains(group.id))
                                                    indicatorBadge.invalidate()
                                                }
                                                indicatorBadge.setOnClickListener(toggleDisassembleSelect)
                                            }
                                            itemView.setOnClickListener(toggleDisassembleSelect)
                                            itemView.isClickable = true
                                        } else {
                                            if (indicatorBadge != null) {
                                                indicatorBadge.visibility = View.GONE
                                                indicatorBadge.setOnClickListener(null)
                                                indicatorBadge.isClickable = false
                                            }
                                            itemView.setOnClickListener(null)
                                            itemView.isClickable = false
                                        }
                                    } else {
                                        // Standard Single Tile / Card Customization Badge
                                        val isExpanded = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)
                                        val isVertical = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                        val isCircular = !isExpanded && !isVertical && !itemView.javaClass.name.contains("Card")
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

                                        if (itemView is ViewGroup && resizeHandle == null && !"wifi".equals(spec, ignoreCase = true) && !"cell".equals(spec, ignoreCase = true)) {
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

                                        if (itemView is ViewGroup && selectBadge == null && !"wifi".equals(spec, ignoreCase = true) && !"cell".equals(spec, ignoreCase = true)) {
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

                                        if (itemView.getTag(0x7f099991) == null) {
                                            itemView.setTag(0x7f099991, true)
                                            itemView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                                                val w = r - l
                                                val h = b - t
                                                if (w > 0 && h > 0) {
                                                    val curHandle = itemView.findViewById<ImageView>(resizeTag)
                                                    if (curHandle != null && curHandle.visibility == View.VISIBLE) {
                                                        val bw = curHandle.measuredWidth.takeIf { it > 0 } ?: badgeSize
                                                        val bh = curHandle.measuredHeight.takeIf { it > 0 } ?: badgeSize
                                                        val curIconFrame = if (iconFrameId != 0) itemView.findViewById<View>(iconFrameId) else null
                                                        val curIsExp = ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec)
                                                        val curIsVert = ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)
                                                        val curIsCirc = !curIsExp && !curIsVert && !itemView.javaClass.name.contains("Card")
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

                                        if (iconFrame != null && iconFrame.getTag(0x7f099991) == null) {
                                            iconFrame.setTag(0x7f099991, true)
                                            iconFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                                val curHandle = itemView.findViewById<ImageView>(resizeTag)
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
                                                    resizeHandle.setOnTouchListener(TileResizeDragTouchListener(ctx, spec, itemView, density))
                                                }
                                            } else {
                                                resizeHandle.visibility = View.GONE
                                            }
                                        }

                                        if (spec != null && ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec)) {
                                            val resHeightId = ctx.resources.getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", ctx.packageName)
                                            val unitH = if (resHeightId != 0) ctx.resources.getDimensionPixelSize(resHeightId) else (76 * density).toInt()
                                            val resMarginId = ctx.resources.getIdentifier("control_center_universal_margin", "dimen", ctx.packageName)
                                            val gap = if (resMarginId != 0) ctx.resources.getDimensionPixelSize(resMarginId) else (10 * density).toInt()
                                            val tallH = unitH * 2 + gap
                                            val curLp = itemView.layoutParams
                                            if (curLp != null && curLp.height != tallH) {
                                                curLp.height = tallH
                                                itemView.layoutParams = curLp
                                            }
                                        }

                                        if (selectBadge != null) {
                                            if (isEdit && ControlCenterConfigHolder.sIsGroupSelectMode) {
                                                val scd = (selectBadge.drawable as? SelectCircleDrawable) ?: SelectCircleDrawable()
                                                scd.setChecked(ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec))
                                                selectBadge.setImageDrawable(scd)
                                                selectBadge.visibility = View.VISIBLE
                                                selectBadge.bringToFront()
                                                selectBadge.isClickable = true

                                                val toggleSelect = View.OnClickListener {
                                                    if (spec != null) {
                                                        if (ControlCenterConfigHolder.sSelectedTileSpecs.contains(spec)) {
                                                            ControlCenterConfigHolder.sSelectedTileSpecs.remove(spec)
                                                        } else {
                                                            ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                        }
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
                                        ControlCenterConfigHolder.sIsEditMode = isEdit

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
                                            if (resizeHandle == null && itemView is ViewGroup && !isChildOfContainer(itemView) && !"wifi".equals(spec, ignoreCase = true) && !"cell".equals(spec, ignoreCase = true)) {
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
                                                    val curIsCirc = !curIsExp && !curIsVert && !itemView.javaClass.name.contains("Card")

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
                                                        resizeHandle.setOnTouchListener(TileResizeDragTouchListener(itemView.context, spec, itemView, density))
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
                                                                ControlCenterConfigHolder.sSelectedTileSpecs.add(spec)
                                                            }
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
                    if (m.name == "onTouchEvent" || m.name == "onInterceptTouchEvent") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val itemView = chain.thisObject as? View
                            if (itemView != null && itemView.findViewById<View>(0x7f099995) != null) {
                                if (ControlCenterConfigHolder.sIsDisassembleMode) {
                                    chain.proceed()
                                } else {
                                    false
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
                }
            } catch (t: Throwable) {}

            // 17. Dynamic Cross-Component Drag & Drop (Zero Boundaries)
            try {
                val helperClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$itemTouchHelper\$2", false, classLoader)
                for (m in helperClass.declaredMethods) {
                    if (m.name == "canDropOver") {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 3) {
                                    val targetViewHolder = args[2]
                                    if (targetViewHolder != null) {
                                        val getItemViewTypeMethod = targetViewHolder.javaClass.getMethod("getItemViewType")
                                        val targetType = getItemViewTypeMethod.invoke(targetViewHolder) as Int
                                        if (targetType == 8398 || targetType == 3484337) {
                                            return@intercept false
                                        }

                                        val getOwnerMethod = targetViewHolder.javaClass.getMethod("getOwner")
                                        val targetOwner = getOwnerMethod.invoke(targetViewHolder)
                                        if (targetOwner != null && targetOwner.javaClass.name.contains("QSListController")) {
                                            val getItemMethod = targetViewHolder.javaClass.getMethod("getItem\$miui_controlcenter_release")
                                            val targetItem = getItemMethod.invoke(targetViewHolder)
                                            if (targetItem != null) {
                                                val addedField = targetItem.javaClass.getDeclaredField("added")
                                                addedField.isAccessible = true
                                                val isTargetAdded = addedField.getBoolean(targetItem)
                                                if (!isTargetAdded) {
                                                    return@intercept false
                                                }
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
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                                if (args != null && args.size >= 3) {
                                    val helperCallback = chain.thisObject
                                    val this0Field = helperClass.getDeclaredField("this$0")
                                    this0Field.isAccessible = true
                                    val adapter = this0Field.get(helperCallback)

                                    val sourceViewHolder = args[1]
                                    val targetViewHolder = args[2]

                                    if (adapter != null && sourceViewHolder != null && targetViewHolder != null) {
                                        val getItemViewTypeMethod = targetViewHolder.javaClass.getMethod("getItemViewType")
                                        val targetType = getItemViewTypeMethod.invoke(targetViewHolder) as Int
                                        if (targetType == 8398 || targetType == 3484337) {
                                            return@intercept false
                                        }

                                        val getOwnerMethod = sourceViewHolder.javaClass.getMethod("getOwner")
                                        val sourceOwner = getOwnerMethod.invoke(sourceViewHolder)

                                        val getTargetOwnerMethod = targetViewHolder.javaClass.getMethod("getOwner")
                                        val targetOwner = getTargetOwnerMethod.invoke(targetViewHolder)

                                        // Wi-Fi and Mobile Data card reordering within QSCardsController
                                        if (sourceOwner != null && targetOwner != null && sourceOwner.javaClass.name.contains("QSCardsController") && sourceOwner == targetOwner) {
                                            ControlCenterConfigHolder.sIsWifiFirst = !ControlCenterConfigHolder.sIsWifiFirst
                                            val ctxField = adapter.javaClass.getDeclaredField("context")
                                            ctxField.isAccessible = true
                                            val ctx = ctxField.get(adapter) as? Context
                                            ControlCenterConfigHolder.savePriorities(ctx)

                                            val notifyChangedMethod = adapter.javaClass.getMethod("notifyChanged", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                            notifyChangedMethod.invoke(adapter, true, true)
                                            return@intercept true
                                        }

                                        // Circular QSList tiles reordering
                                        if (sourceOwner != null && targetOwner != null && sourceOwner == targetOwner) {
                                            val getItemMethod = sourceViewHolder.javaClass.getMethod("getItem\$miui_controlcenter_release")
                                            val sourceItem = getItemMethod.invoke(sourceViewHolder)

                                            val getTargetItemMethod = targetViewHolder.javaClass.getMethod("getItem\$miui_controlcenter_release")
                                            val targetItem = getTargetItemMethod.invoke(targetViewHolder)

                                            val moveElementMethod = sourceOwner.javaClass.getMethod("moveElement", getItemMethod.returnType, getTargetItemMethod.returnType)
                                            return@intercept moveElementMethod.invoke(sourceOwner, sourceItem, targetItem)
                                        }

                                        // Cross-component movement anywhere across ANY modules
                                        val contentListField = adapter.javaClass.getDeclaredField("contentList")
                                        contentListField.isAccessible = true
                                        val contentList = contentListField.get(adapter) as? MutableList<Any>

                                        if (contentList != null && sourceOwner != null && targetOwner != null && sourceOwner != targetOwner) {
                                            val fromIndex = contentList.indexOf(sourceOwner)
                                            val toIndex = contentList.indexOf(targetOwner)
                                            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                                val moved = contentList.removeAt(fromIndex)
                                                contentList.add(toIndex, moved)

                                                for (i in 0 until contentList.size) {
                                                    val c = contentList[i]
                                                    val name = c.javaClass.simpleName
                                                    ControlCenterConfigHolder.sComponentPriorities[name] = 20 + i * 10
                                                }

                                                val ctxField = adapter.javaClass.getDeclaredField("context")
                                                ctxField.isAccessible = true
                                                val ctx = ctxField.get(adapter) as? Context
                                                ControlCenterConfigHolder.savePriorities(ctx)

                                                val notifyChangedMethod = adapter.javaClass.getMethod("notifyChanged", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                notifyChangedMethod.invoke(adapter, true, true)
                                                return@intercept true
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "onMove dynamic rearrange hook error", t)
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {}

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

            hookedPluginLoaders.add(classLoader)
            Log.i(TAG, "Control Center Plugin hooks successfully attached!")
        } catch (t: Throwable) {
            Log.e(TAG, "initPluginHooks fatal error", t)
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
    }

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
    private var cardWidth2x1 = 0f
    private var cardHeight1x2 = 0f

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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestDisallowAll(v, true)

                downRawX = event.rawX
                downRawY = event.rawY
                isDragging = false
                candidateTargetState = -1

                initialState = when {
                    ControlCenterConfigHolder.sExpandedTileSpecs.contains(spec) -> STATE_EXPANDED
                    ControlCenterConfigHolder.sVerticalTileSpecs.contains(spec) -> STATE_VERTICAL
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

                    val gap = 10f * density
                    if (initialState == STATE_NORMAL) {
                        unitWidth1x1 = originWidth
                        cardWidth2x1 = originWidth * 2f + gap
                        cardHeight1x2 = originHeight * 2f + gap
                    } else if (initialState == STATE_EXPANDED) {
                        cardWidth2x1 = originWidth
                        unitWidth1x1 = ((originWidth - gap) / 2f).coerceAtLeast(56f * density)
                        cardHeight1x2 = originHeight * 2f + gap
                    } else { // STATE_VERTICAL
                        cardHeight1x2 = originHeight
                        unitWidth1x1 = originWidth
                        cardWidth2x1 = originWidth * 2f + gap
                    }

                    ghostDrawable = TileResizeGhostDrawable(density).apply {
                        setOriginBounds(originLeft, originTop, originWidth, originHeight)
                    }
                }
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
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
                var targetState = initialState

                val snapThreshold = 0.22f

                when (initialState) {
                    STATE_NORMAL -> {
                        val horizThreshold = (cardWidth2x1 - unitWidth1x1) * snapThreshold
                        val vertThreshold = (cardHeight1x2 - originHeight) * snapThreshold

                        if (Math.abs(dx) >= Math.abs(dy)) {
                            if (dx > 0) {
                                val p = (dx / (cardWidth2x1 - unitWidth1x1)).coerceIn(0f, 1f)
                                previewW = unitWidth1x1 + p * (cardWidth2x1 - unitWidth1x1)
                                if (dx >= horizThreshold) {
                                    targetState = STATE_EXPANDED
                                }
                            }
                        } else {
                            if (dy > 0) {
                                val p = (dy / (cardHeight1x2 - originHeight)).coerceIn(0f, 1f)
                                previewH = originHeight + p * (cardHeight1x2 - originHeight)
                                if (dy >= vertThreshold) {
                                    targetState = STATE_VERTICAL
                                }
                            }
                        }
                    }
                    STATE_EXPANDED -> {
                        val shrinkThreshold = (cardWidth2x1 - unitWidth1x1) * snapThreshold
                        val vertThreshold = (cardHeight1x2 - originHeight) * snapThreshold

                        if (dx < 0 && Math.abs(dx) >= Math.abs(dy)) {
                            val p = (-dx / (cardWidth2x1 - unitWidth1x1)).coerceIn(0f, 1f)
                            previewW = cardWidth2x1 - p * (cardWidth2x1 - unitWidth1x1)
                            if (-dx >= shrinkThreshold) {
                                targetState = STATE_NORMAL
                            }
                        } else if (dy > 0 && dy > Math.abs(dx)) {
                            val p = (dy / (cardHeight1x2 - originHeight)).coerceIn(0f, 1f)
                            previewH = originHeight + p * (cardHeight1x2 - originHeight)
                            if (dy >= vertThreshold) {
                                targetState = STATE_VERTICAL
                            }
                        }
                    }
                    STATE_VERTICAL -> {
                        val shrinkThreshold = (cardHeight1x2 - originHeight / 2f) * snapThreshold
                        val horizThreshold = (cardWidth2x1 - unitWidth1x1) * snapThreshold

                        if (dy < 0 && Math.abs(dy) >= Math.abs(dx)) {
                            val p = (-dy / (cardHeight1x2 - originHeight / 2f)).coerceIn(0f, 1f)
                            previewH = cardHeight1x2 - p * (cardHeight1x2 - originHeight / 2f)
                            if (-dy >= shrinkThreshold) {
                                targetState = STATE_NORMAL
                            }
                        } else if (dx > 0 && dx > Math.abs(dy)) {
                            val p = (dx / (cardWidth2x1 - unitWidth1x1)).coerceIn(0f, 1f)
                            previewW = unitWidth1x1 + p * (cardWidth2x1 - unitWidth1x1)
                            if (dx >= horizThreshold) {
                                targetState = STATE_EXPANDED
                            }
                        }
                    }
                }

                if (targetState != candidateTargetState) {
                    candidateTargetState = targetState
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }

                ghostDrawable?.let { gd ->
                    val isSnapping = candidateTargetState != initialState && candidateTargetState != -1
                    gd.updateMorph(originLeft, originTop, previewW, previewH, isSnapping)
                    targetContainer?.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                requestDisallowAll(v, false)
                tileView.alpha = 1.0f

                ghostDrawable?.let { gd ->
                    targetContainer?.overlay?.remove(gd)
                    targetContainer?.invalidate()
                }

                if (isDragging) {
                    if (candidateTargetState != -1 && candidateTargetState != initialState) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        when (candidateTargetState) {
                            STATE_EXPANDED -> ControlCenterConfigHolder.setTileToExpanded(ctx, spec, tileView)
                            STATE_VERTICAL -> ControlCenterConfigHolder.setTileToVertical(ctx, spec, tileView)
                            STATE_NORMAL -> ControlCenterConfigHolder.setTileToNormal(ctx, spec, tileView)
                        }
                    }
                } else {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    when (initialState) {
                        STATE_NORMAL -> ControlCenterConfigHolder.setTileToExpanded(ctx, spec, tileView)
                        STATE_EXPANDED -> ControlCenterConfigHolder.setTileToVertical(ctx, spec, tileView)
                        STATE_VERTICAL -> ControlCenterConfigHolder.setTileToNormal(ctx, spec, tileView)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                requestDisallowAll(v, false)
                tileView.alpha = 1.0f
                ghostDrawable?.let { gd ->
                    targetContainer?.overlay?.remove(gd)
                    targetContainer?.invalidate()
                }
                return true
            }
        }
        return true
    }
}
