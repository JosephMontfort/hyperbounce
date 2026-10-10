package dev.joseph.hyperbounce.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Feature 3 (Optimized Pipeline): Zero-Drop 120Hz Screen Unlock Animation Hook.
 *
 * Optimizations over base hook:
 * 1. Single-Trigger Synchronization: Intercepts UnlockAnimationStateMachine.showAnimation to prevent
 *    the 2x animation collision when onResume/onWindowFocus fires 100ms after our broadcast.
 * 2. Hardware CPU/RenderThread Boost: Calls BaseLauncher.showPresent() to engage CPUBooster and
 *    BoostRtHelper for 1000ms max performance during icon spring flight.
 * 3. State Machine Neutralization: Automatically resets UnlockAnimationStateMachine to STATE.IDLE
 *    upon broadcast trigger so delayed callbacks never interrupt mid-flight springs.
 */
object ScreenUnlockHook {
    private const val TAG = "HyperBounce_ScreenUnlock"

    private val isAnimating = ThreadLocal.withInitial { false }
    private val isOuterColumn = ThreadLocal.withInitial { false }
    private val isIndicator = ThreadLocal.withInitial { false }
    private val isDockView = ThreadLocal.withInitial { false }
    private val currentRow = ThreadLocal.withInitial { 0 }
    private val currentKineticFactor = ThreadLocal.withInitial { 0.0f }
    private val normalizedDistance = ThreadLocal.withInitial { 0.0f }
    private val activeResponse = ThreadLocal.withInitial { 0.5f }
    private val activeDamping = ThreadLocal.withInitial { 0.6f }
    private val currentAnimatingView = ThreadLocal<View?>()

    @Volatile
    private var latestVelocity = 0.0f
    @Volatile
    private var lastVelocityTime = 0L
    @Volatile
    private var lastTriggeredUnlockAnimTime = 0L

    private var sLauncherRef: WeakReference<android.app.Activity>? = null
    private var sClassLoader: ClassLoader? = null
    private var isReceiverRegistered = false

    fun updateVelocity(velocity: Float) {
        latestVelocity = abs(velocity)
        lastVelocityTime = System.currentTimeMillis()
        Log.d(TAG, "updateVelocity: $velocity (abs=$latestVelocity)")
    }

    private fun findLauncher(userPresentObj: Any?): android.app.Activity? {
        if (userPresentObj == null) return sLauncherRef?.get()
        try {
            var curr: Class<*>? = userPresentObj.javaClass
            while (curr != null && curr != Any::class.java) {
                try {
                    val f = curr.getDeclaredField("mLauncher").apply { isAccessible = true }
                    val l = f.get(userPresentObj) as? android.app.Activity
                    if (l != null) return l
                } catch (_: Throwable) {}
                curr = curr.superclass
            }
        } catch (_: Throwable) {}
        return sLauncherRef?.get()
    }

    private fun extractItemInfo(tag: Any?): Triple<Int, Int, Long>? {
        if (tag == null) return null
        var curr: Class<*>? = tag.javaClass
        var cx: Int? = null
        var cy: Int? = null
        var cont: Long? = null
        while (curr != null && curr != Any::class.java) {
            if (cx == null) {
                try {
                    val f = curr.getDeclaredField("cellX")
                    f.isAccessible = true
                    cx = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (cy == null) {
                try {
                    val f = curr.getDeclaredField("cellY")
                    f.isAccessible = true
                    cy = f.getInt(tag)
                } catch (_: Throwable) {}
            }
            if (cont == null) {
                try {
                    val f = curr.getDeclaredField("container")
                    f.isAccessible = true
                    cont = f.getLong(tag)
                } catch (_: Throwable) {}
            }
            if (cx != null && cy != null && cont != null) break
            curr = curr.superclass
        }
        return Triple(cx ?: -1, cy ?: -1, cont ?: 0L)
    }

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return
        sClassLoader = classLoader

        hookBaseLauncher(module, classLoader)

        try {
            val appClass = Class.forName("com.miui.home.launcher.Application", false, classLoader)
            for (m in appClass.declaredMethods) {
                if (m.name == "onCreate" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val app = chain.thisObject as? Context
                                if (app != null) {
                                    registerUnlockReceiver(app)
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
            val appMethod = appClass.getMethod("getLauncherApplication")
            val app = appMethod.invoke(null) as? Context
            if (app != null) {
                registerUnlockReceiver(app)
            }
        } catch (_: Throwable) {}

        // Hook UnlockAnimationStateMachine to prevent duplicate 2x execution on lockscreen unlock
        try {
            val stateMachineClass = Class.forName("com.miui.home.launcher.common.UnlockAnimationStateMachine", false, classLoader)
            for (m in stateMachineClass.declaredMethods) {
                if (m.name == "showAnimation" && m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val now = SystemClock.uptimeMillis()
                            val delta = now - lastTriggeredUnlockAnimTime
                            if (lastTriggeredUnlockAnimTime > 0L && delta < 1000L) {
                                val reason = chain.args?.let { if (it is List<*>) it.firstOrNull() else (it as? Array<*>)?.firstOrNull() } ?: "unknown"
                                Log.d(TAG, "Suppressed duplicate showAnimation('$reason') from state machine (within ${delta}ms)")
                                try {
                                    val fState = chain.thisObject.javaClass.getDeclaredField("mState").apply { isAccessible = true }
                                    val stateEnumClass = fState.type
                                    @Suppress("UNCHECKED_CAST")
                                    val idleEnum = java.lang.Enum.valueOf(stateEnumClass as Class<out Enum<*>>, "IDLE")
                                    fState.set(chain.thisObject, idleEnum)
                                } catch (_: Throwable) {}
                                return null
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked UnlockAnimationStateMachine.showAnimation successfully")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook UnlockAnimationStateMachine.showAnimation", t)
        }

        try {
            val userPresentClass = Class.forName(
                "com.miui.home.launcher.compat.UserPresentAnimationCompatPhone",
                false,
                classLoader
            )

            // Hook showUserPresentAnimation(View, int, boolean)
            val showMethod: Method = userPresentClass.getDeclaredMethod(
                "showUserPresentAnimation",
                View::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            module.hook(showMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val rawArgs = chain.args
                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                    val view = args[0] as? View
                    val cfg = LauncherConfigHolder.unlockConfig

                    if (!cfg.enabled) return chain.proceed()

                    if (view != null) {
                        try {
                            val loc = IntArray(2)
                            view.getLocationOnScreen(loc)
                            val vx = loc[0] + (view.width / 2.0f)
                            val vy = loc[1] + (view.height / 2.0f)
                            val dm = view.context.resources.displayMetrics
                            val screenW = dm.widthPixels.toFloat()
                            val screenH = dm.heightPixels.toFloat()

                            val cols = max(1, cfg.gridColumns)
                            val rows = max(1, cfg.gridRows)

                            val tagInfo = extractItemInfo(view.tag)
                            val cellX = tagInfo?.first ?: -1
                            val cellY = tagInfo?.second ?: -1
                            val container = tagInfo?.third ?: 0L

                            val parentClassName = view.parent?.javaClass?.name ?: ""
                            val viewClassName = view.javaClass.name

                            val isHotseat = (container == -101L) ||
                                (args[2] as? Boolean ?: false) ||
                                parentClassName.contains("HotSeat", ignoreCase = true) ||
                                viewClassName.contains("HotSeat", ignoreCase = true)

                            val isInd = viewClassName.contains("Indicator", ignoreCase = true) ||
                                parentClassName.contains("Indicator", ignoreCase = true)

                            isDockView.set(isHotseat && !isInd)
                            isIndicator.set(isInd)

                            val colIndex: Int = if (cellX in 0 until cols) {
                                cellX
                            } else {
                                val cellWidth = screenW / cols.toFloat()
                                (vx / cellWidth).toInt().coerceIn(0, cols - 1)
                            }
                            isOuterColumn.set(colIndex == 0 || colIndex == cols - 1)

                            val calculatedRow = if (cellY in 0 until rows && !isHotseat) {
                                cellY
                            } else {
                                floor((vy / screenH) * rows).toInt().coerceIn(0, rows - 1)
                            }
                            currentRow.set(calculatedRow)

                            val timeDelta = System.currentTimeMillis() - lastVelocityTime
                            if (timeDelta < 2500L && latestVelocity > 0f) {
                                val normV = (latestVelocity / cfg.maxVelocity).coerceIn(0.0f, 1.0f)
                                currentKineticFactor.set(normV)
                            } else {
                                currentKineticFactor.set(0.0f)
                            }

                            if (cfg.animMode == 0) {
                                val centerX = screenW / 2.0f
                                val centerY = screenH * 0.42f
                                val dx = (vx - centerX) / (screenW / 2.0f)
                                val dy = (vy - centerY) / (screenH / 2.0f)
                                normalizedDistance.set(sqrt(dx * dx + dy * dy))
                            } else {
                                val safeRow = calculatedRow.coerceIn(0, rows - 1)
                                val zOrder = if (isHotseat || isInd) (rows + 2) * 0.5f else safeRow * 0.5f
                                view.translationZ = zOrder
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    view.outlineAmbientShadowColor = Color.TRANSPARENT
                                    view.outlineSpotShadowColor = Color.TRANSPARENT
                                }
                                view.postDelayed({
                                    view.translationZ = 0.0f
                                }, 1200L)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error calculating icon position", t)
                        }
                    }

                    // Mode 2: Complete 1:1 Replacement with XOS 16.3 Staggered Wave Pipeline
                    if (cfg.animMode == 2) {
                        if (view != null) {
                            animateXos163StaggeredView(
                                userPresentObj = chain.thisObject,
                                view = view,
                                isHotseat = isDockView.get() || isIndicator.get(),
                                row = currentRow.get(),
                                totalRows = max(1, cfg.gridRows),
                                cfg = cfg
                            )
                        }
                        return null
                    }

                    isAnimating.set(true)
                    currentAnimatingView.set(view)
                    val result = try {
                        chain.proceed()
                    } finally {
                        isAnimating.set(false)
                        currentAnimatingView.set(null)
                    }
                    return result
                }
            })

            // Hook getDampingResponse()
            val getDR: Method = userPresentClass.getDeclaredMethod("getDampingResponse")
            module.hook(getDR).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.unlockConfig
                    if (!isAnimating.get() || !cfg.enabled) return chain.proceed()

                    return if (cfg.animMode == 0) {
                        if (isDockView.get() || isIndicator.get()) {
                            activeDamping.set(cfg.dockDamping)
                            activeResponse.set(cfg.dockResponse)
                            floatArrayOf(cfg.dockDamping, cfg.dockResponse)
                        } else {
                            val dist = normalizedDistance.get()
                            val rawResponse = cfg.centerResponse + (dist * cfg.edgeResponseDelay)
                            val rawDamping = cfg.centerDamping - (dist * cfg.edgeDampingDrop)
                            val response = rawResponse.coerceIn(0.10f, 2.0f)
                            val damping = rawDamping.coerceIn(0.10f, 1.2f)
                            activeDamping.set(damping)
                            activeResponse.set(response)
                            floatArrayOf(damping, response)
                        }
                    } else {
                        val kF = currentKineticFactor.get()
                        val vInf = cfg.velocityInfluence
                        val velocityBoost = kF * vInf

                        if (isDockView.get() || isIndicator.get()) {
                            val resp = (cfg.kineticDockResponse * cfg.durationScale / (1.0f + (velocityBoost * 0.25f))).coerceIn(0.15f, 1.5f)
                            val damp = cfg.kineticDockDamping.coerceIn(0.20f, 1.0f)
                            activeResponse.set(resp)
                            activeDamping.set(damp)
                            floatArrayOf(damp, resp)
                        } else {
                            val safeRow = currentRow.get().coerceIn(0, max(0, cfg.gridRows - 1))
                            val outer = isOuterColumn.get()

                            val customBounce = if (outer) {
                                cfg.getDyn("r${safeRow}_outer_bounce", ScreenUnlockDefaults.rowBounce(safeRow, cfg.gridRows, true))
                            } else {
                                cfg.getDyn("r${safeRow}_center_bounce", ScreenUnlockDefaults.rowBounce(safeRow, cfg.gridRows, false))
                            }
                            val customSettle = if (outer) {
                                cfg.getDyn("r${safeRow}_outer_settle", ScreenUnlockDefaults.rowSettle(safeRow, cfg.gridRows, true))
                            } else {
                                cfg.getDyn("r${safeRow}_center_settle", ScreenUnlockDefaults.rowSettle(safeRow, cfg.gridRows, false))
                            }

                            val baseZ = cfg.getDyn("r${safeRow}_z", ScreenUnlockDefaults.rowZ(safeRow, cfg.gridRows)).coerceAtLeast(100.0f)
                            val zMult = 1.0f + (velocityBoost * 0.35f)
                            val targetZ = (baseZ * zMult).coerceAtLeast(10.0f)
                            val zRatio = (500.0f / targetZ).coerceIn(0.20f, 2.0f)

                            val finalDamping: Float
                            if (customBounce <= 0.0f) {
                                finalDamping = 1.0f
                            } else {
                                val dampingSensitivity = ((1.0f - cfg.cascadeDamping) / 0.14f).coerceAtLeast(0.1f)
                                val effectiveBounce = customBounce * (1.0f + (velocityBoost * 1.5f))
                                val dampingDrop = effectiveBounce * zRatio * dampingSensitivity * 1.35f
                                finalDamping = (1.0f - dampingDrop).coerceIn(0.25f, 1.0f)
                            }

                            val scaledResponse = customSettle * cfg.durationScale
                            val finalResponse = (scaledResponse / (1.0f + (velocityBoost * 0.40f))).coerceIn(0.15f, 1.5f)

                            activeResponse.set(finalResponse)
                            activeDamping.set(finalDamping)
                            floatArrayOf(finalDamping, finalResponse)
                        }
                    }
                }
            })

            // Hook SpringAnimator(float, float, float, float)
            val springAnimClass = Class.forName("com.miui.home.common.animate.SpringAnimator", false, classLoader)
            val constructor: Constructor<*> = springAnimClass.getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            module.hook(constructor).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.unlockConfig
                    if (isAnimating.get() && cfg.enabled) {
                        val rawArgs = chain.args
                        val args: Array<Any?> = Array(rawArgs.size) { i -> rawArgs[i] }

                        if (args.size < 4) return chain.proceed()

                        val startVal = (args[2] as? Number)?.toFloat() ?: 0.0f
                        val endVal = (args[3] as? Number)?.toFloat() ?: 0.0f

                        if (endVal == 0.0f && abs(startVal) > 5.0f) {
                            args[0] = activeDamping.get()
                            args[1] = activeResponse.get()

                            if (cfg.animMode == 0) {
                                args[2] = startVal * cfg.zDepthMultiplier
                            } else {
                                val kF = currentKineticFactor.get()
                                val vInf = cfg.velocityInfluence
                                val velocityBoost = kF * vInf
                                val safeRow = currentRow.get().coerceIn(0, max(0, cfg.gridRows - 1))

                                val targetZ: Float = if (isDockView.get() || isIndicator.get()) {
                                    val zMult = 1.0f + (velocityBoost * 0.25f)
                                    (cfg.kineticDockZDepth * zMult).coerceAtLeast(10.0f)
                                } else {
                                    val baseZ = cfg.getDyn("r${safeRow}_z", ScreenUnlockDefaults.rowZ(safeRow, cfg.gridRows))
                                    val zMult = 1.0f + (velocityBoost * 0.35f)
                                    (baseZ * zMult).coerceAtLeast(10.0f)
                                }

                                args[2] = -abs(targetZ)
                            }
                        }
                        val animResult = chain.proceed(args)
                        val v = currentAnimatingView.get()
                        if (v != null) {
                            try {
                                val getMethod = chain.thisObject.javaClass.getMethod("getSpringAnimation")
                                val springAnim = getMethod.invoke(chain.thisObject)
                                if (springAnim != null) {
                                    val listenerClass = Class.forName("androidx.dynamicanimation.animation.DynamicAnimation\$OnAnimationEndListener", false, springAnim.javaClass.classLoader)
                                    val addEndListenerMethod = springAnim.javaClass.getMethod("addEndListener", listenerClass)
                                    val weakV = WeakReference(v)
                                    val proxy = java.lang.reflect.Proxy.newProxyInstance(
                                        springAnim.javaClass.classLoader,
                                        arrayOf(listenerClass)
                                    ) { _, method, _ ->
                                        if (method.name == "onAnimationEnd") {
                                            weakV.get()?.translationZ = 0.0f
                                        }
                                        null
                                    }
                                    addEndListenerMethod.invoke(springAnim, proxy)
                                }
                            } catch (_: Throwable) {}
                        }
                        return animResult
                    }
                    return chain.proceed()
                }
            })

            try {
                val complexClass = Class.forName("com.miui.home.launcher.compat.UserPresentAnimationCompatComplex", false, classLoader)
                for (m in complexClass.declaredMethods) {
                    if (m.name == "resetAnimation" && m.parameterTypes.isEmpty()) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val res = chain.proceed()
                                try {
                                    fun resetZ(v: View?) {
                                        if (v == null) return
                                        v.translationZ = 0.0f
                                        if (v is ViewGroup) {
                                            for (k in 0 until v.childCount) {
                                                resetZ(v.getChildAt(k))
                                            }
                                        }
                                    }
                                    val wsMethod = chain.thisObject.javaClass.getMethod("getWorkspace")
                                    val ws = wsMethod.invoke(chain.thisObject) as? ViewGroup
                                    resetZ(ws)
                                } catch (_: Throwable) {}
                                return res
                            }
                        })
                    }
                }
            } catch (_: Throwable) {}

            Log.i(TAG, "Hooked UserPresentAnimationCompatPhone and SpringAnimator successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook UserPresentAnimationCompatPhone", t)
        }
    }

    private fun hookBaseLauncher(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val baseLauncherClass = Class.forName("com.miui.home.launcher.BaseLauncher", false, classLoader)
            for (m in baseLauncherClass.declaredMethods) {
                if ((m.name == "onCreate" && m.parameterTypes.size == 1 && m.parameterTypes[0] == android.os.Bundle::class.java) ||
                    (m.name == "onStart" && m.parameterTypes.isEmpty()) ||
                    (m.name == "onResume" && m.parameterTypes.isEmpty()) ||
                    (m.name == "onWindowFocusChanged" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType) ||
                    (m.name == "dispatchTouchEvent" && m.parameterTypes.size == 1 && m.parameterTypes[0] == android.view.MotionEvent::class.java) ||
                    (m.name == "onAttachedToWindow" && m.parameterTypes.isEmpty())
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val launcher = chain.thisObject as? android.app.Activity
                                if (launcher != null) {
                                    sLauncherRef = java.lang.ref.WeakReference(launcher)
                                    registerUnlockReceiver(launcher)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in BaseLauncher lifecycle hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook BaseLauncher", t)
        }
    }

    private fun getLauncherActivity(context: android.content.Context?): android.app.Activity? {
        sLauncherRef?.get()?.let { return it }
        val cl = sClassLoader ?: context?.classLoader
        if (cl != null) {
            try {
                val appClass = Class.forName("com.miui.home.launcher.Application", false, cl)
                val method = appClass.getMethod("getLauncher")
                val launcher = method.invoke(null) as? android.app.Activity
                if (launcher != null) {
                    sLauncherRef = java.lang.ref.WeakReference(launcher)
                    return launcher
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun registerUnlockReceiver(context: Context) {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION")
                addAction("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED")
            }
            val receiver = object : BroadcastReceiver() {
                private var lastReceivedUnlockTime = 0L

                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        "dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED" -> {
                            val key = intent.getStringExtra("key")
                            if (key?.startsWith("unlock_") == true) {
                                LauncherConfigHolder.reload()
                            }
                        }
                        "dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION" -> {
                            val cfg = LauncherConfigHolder.unlockConfig
                            if (!cfg.enabled) return
                            val velocity = intent.getFloatExtra("EXTRA_VELOCITY", 0.0f)
                            if (velocity != 0.0f) {
                                updateVelocity(velocity)
                            }
                            val now = SystemClock.uptimeMillis()
                            if (now - lastReceivedUnlockTime < 400L) {
                                Log.d(TAG, "ACTION_TRIGGER_UNLOCK_ANIMATION debounced on receiver (within 400ms)")
                                return
                            }
                            lastReceivedUnlockTime = now

                            Log.d(TAG, "Received ACTION_TRIGGER_UNLOCK_ANIMATION broadcast (velocity=$velocity)")
                            val launcher = getLauncherActivity(c)
                            if (launcher == null) {
                                Log.w(TAG, "Cannot trigger unlock animation: launcher activity is null")
                                return
                            }
                            launcher.runOnUiThread {
                                triggerUnlockAnimationOnLauncher(launcher)
                            }
                        }
                    }
                }
            }
            val appCtx = context.applicationContext ?: context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appCtx.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered ACTION_TRIGGER_UNLOCK_ANIMATION receiver successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register unlock receiver", t)
        }
    }

    private fun triggerUnlockAnimationOnLauncher(launcher: android.app.Activity) {
        try {
            if (launcher.isFinishing || launcher.isDestroyed) return

            val now = SystemClock.uptimeMillis()
            lastTriggeredUnlockAnimTime = now
            Log.d(TAG, "triggerUnlockAnimationOnLauncher: executing on $launcher (time=$now)")

            val cfg = LauncherConfigHolder.unlockConfig
            if (!cfg.enabled) return

            // 1. Prepare animation on current screen icons
            var prepared = false
            try {
                val prepMethod = launcher.javaClass.getMethod("prepareAnimationForUnlock")
                prepared = (prepMethod.invoke(launcher) as? Boolean) ?: false
            } catch (_: Throwable) {}

            val getUserPresentAnimMethod = try {
                launcher.javaClass.getMethod("getUserPresentAnimation")
            } catch (_: Throwable) { null }

            val userPresentAnim = getUserPresentAnimMethod?.invoke(launcher)

            if (!prepared && userPresentAnim != null) {
                try {
                    userPresentAnim.javaClass.getMethod("prepareAnimation").invoke(userPresentAnim)
                } catch (_: Throwable) {}
            }

            // 2. Play animation with native CPU/RenderThread boost
            var played = false
            try {
                val showPresentMethod = launcher.javaClass.getMethod("showPresent")
                showPresentMethod.invoke(launcher)
                played = true
                Log.d(TAG, "Triggered launcher.showPresent() with CPU/RT boost successfully")
            } catch (_: Throwable) {}

            if (!played && userPresentAnim != null) {
                try {
                    userPresentAnim.javaClass.getMethod("showAnimation").invoke(userPresentAnim)
                    Log.d(TAG, "Triggered showAnimation successfully (fallback)")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to call showAnimation", e)
                }
            }

            // 3. Mark state machine as IDLE so onResume/onWindowFocus won't re-trigger
            try {
                val fStateMachine = launcher.javaClass.getDeclaredField("mUnlockAnimationStateMachine").apply { isAccessible = true }
                val stateMachine = fStateMachine.get(launcher)
                if (stateMachine != null) {
                    val fState = stateMachine.javaClass.getDeclaredField("mState").apply { isAccessible = true }
                    val stateEnumClass = fState.type
                    @Suppress("UNCHECKED_CAST")
                    val idleEnum = java.lang.Enum.valueOf(stateEnumClass as Class<out Enum<*>>, "IDLE")
                    fState.set(stateMachine, idleEnum)
                }
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "Error triggering unlock animation on launcher", t)
        }
    }

    /**
     * Mode 2: 1:1 Pure XOS 16.3 Staggered Icon Wave Animation.
     * Replaces Xiaomi HyperOS 3D radial camera perspective with:
     * - Pure 1D vertical displacement (zero lateral X-drift)
     * - Bottom-to-top staggered latency: ((totalRows - effectiveRow) + 1) * 10ms
     * - Under-damped spring oscillator (stiffness = 150.0, dampingRatio = 0.70)
     * - Physical velocity flick modulation of start displacement & upward velocity
     * - Linear 250ms alpha ramp
     */
    private fun animateXos163StaggeredView(
        userPresentObj: Any?,
        view: View,
        isHotseat: Boolean,
        row: Int,
        totalRows: Int,
        cfg: ScreenUnlockConfig
    ) {
        try {
            // 1. Completely neutralize 3D perspective / camera translation from Xiaomi prepare stage
            view.translationX = 0.0f
            view.scaleX = 1.0f
            view.scaleY = 1.0f
            view.pivotX = view.width / 2.0f
            view.pivotY = view.height / 2.0f

            // 2. Compute 1:1 XOS Staggered Row Delay
            // In XOS (StaggeredWorkspaceAnim.java):
            // Hotseat / Dock is considered the bottom row (effectiveRow = totalRows)
            // Workspace rows are 1-indexed (effectiveRow = row + 1)
            val effectiveRow = if (isHotseat) totalRows else (row + 1).coerceIn(1, totalRows)
            val startDelay = ((totalRows - effectiveRow) + 1) * cfg.xosRowDelayMs

            // 3. Compute 1:1 XOS Velocity Flick Initial Displacement & Velocity
            // In XOS (StaggeredWorkspaceAnim.java):
            // mSpringTransY = (((abs(f2) * 0.9f) / MAX_VELOCITY) + 0.2f) * maxTransY
            val dm = view.resources.displayMetrics
            val maxTransYPx = 70.0f * dm.density // ~210px on xxhdpi (matching XOS swipe_up_max_workspace_trans_y)
            val timeDelta = System.currentTimeMillis() - lastVelocityTime
            val effectiveVelocity = if (timeDelta < 2500L && latestVelocity > 0.0f) latestVelocity else 0.0f
            val normV = (effectiveVelocity / cfg.maxVelocity).coerceIn(0.0f, 1.0f)
            val velocityMultiplier = (normV * 0.9f) + 0.2f
            val springTransY = velocityMultiplier * maxTransYPx

            // Upward initial velocity (- signum towards 0)
            val startVelocity = -1.0f * (normV * 22.0f * 100.0f * dm.density)

            view.translationY = springTransY
            view.alpha = 0.0f

            // 4. Build and start XOS analytical spring animator
            val springAnimator = XosSpringAnimationBuilder(view.context)
                .setStiffness(cfg.xosStiffness)
                .setDampingRatio(cfg.xosDamping)
                .setStartValue(springTransY)
                .setEndValue(0.0f)
                .setStartVelocity(startVelocity)
                .setMinimumVisibleChange(1.0f)
                .build(view, startDelay) {
                    view.translationY = 0.0f
                    notifyEnd(userPresentObj, view)
                }

            // 5. Build and start XOS linear alpha animator (250ms with same row startDelay)
            val alphaAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f).apply {
                duration = cfg.xosAlphaDurationMs
                this.startDelay = startDelay
                interpolator = LinearInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.alpha = 1.0f
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        view.alpha = 1.0f
                    }
                })
            }

            springAnimator.start()
            alphaAnimator.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Error in animateXos163StaggeredView", t)
            view.translationX = 0.0f
            view.translationY = 0.0f
            view.scaleX = 1.0f
            view.scaleY = 1.0f
            view.alpha = 1.0f
            notifyEnd(userPresentObj, view)
        }
    }

    private fun notifyEnd(userPresentObj: Any?, view: View) {
        if (userPresentObj == null) return
        try {
            val endMethod = userPresentObj.javaClass.getMethod("endAnimation", View::class.java)
            endMethod.invoke(userPresentObj, view)
        } catch (_: Throwable) {
            try {
                val m = userPresentObj.javaClass.getDeclaredMethod("endAnimation", View::class.java).apply { isAccessible = true }
                m.invoke(userPresentObj, view)
            } catch (_: Throwable) {}
        }
    }
}
