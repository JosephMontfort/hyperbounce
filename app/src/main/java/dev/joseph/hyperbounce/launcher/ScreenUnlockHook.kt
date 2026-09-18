package dev.joseph.hyperbounce.launcher

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

    private var sPivotField: Field? = null

    private fun getPivotField(clazz: Class<*>): Field? {
        if (sPivotField != null) return sPivotField
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            try {
                val f = c.getDeclaredField("mPivot")
                f.isAccessible = true
                sPivotField = f
                return f
            } catch (_: Throwable) {
                c = c.superclass
            }
        }
        return null
    }

    private fun getScreenDimensions(classLoader: ClassLoader?): Pair<Int, Int> {
        try {
            if (classLoader != null) {
                val devConfigsClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
                val getW = devConfigsClass.getMethod("getScreenWidth")
                val getH = devConfigsClass.getMethod("getScreenHeight")
                val w = (getW.invoke(null) as? Number)?.toInt() ?: 0
                val h = (getH.invoke(null) as? Number)?.toInt() ?: 0
                if (w > 0 && h > 0) return Pair(w, h)
            }
        } catch (_: Throwable) {}
        val dm = android.content.res.Resources.getSystem().displayMetrics
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private fun updatePivot(instance: Any?, classLoader: ClassLoader?) {
        if (instance == null) return
        val cfg = LauncherConfigHolder.unlockConfig
        if (!cfg.enabled || cfg.animMode != 1) return
        try {
            val f = getPivotField(instance.javaClass) ?: return
            val (screenW, _) = getScreenDimensions(classLoader)
            val pivotX = screenW / 2
            val pivotY = 0
            f.set(instance, intArrayOf(pivotX, pivotY))
            Log.d(TAG, "Configured top-center mPivot: [$pivotX, $pivotY]")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to update mPivot", t)
        }
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

                    updatePivot(chain.thisObject, classLoader)

                    if (view != null) {
                        try {
                            val loc = IntArray(2)
                            view.getLocationOnScreen(loc)
                            val vx = loc[0] - view.translationX + (view.width / 2.0f)
                            val vy = loc[1] - view.translationY + (view.height / 2.0f)
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
                            val resp = (0.46f * cfg.durationScale / (1.0f + (velocityBoost * 0.25f))).coerceIn(0.35f, 0.70f)
                            val damp = 0.88f
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

                            // Stable iOS damping [0.80, 0.95]: single smooth descent and soft cushion, zero oscillation
                            val finalDamping = (0.88f - (customBounce * 0.10f)).coerceIn(0.80f, 0.95f)
                            val scaledResponse = customSettle * cfg.durationScale * 0.70f
                            val finalResponse = (scaledResponse / (1.0f + (velocityBoost * 0.30f))).coerceIn(0.38f, 0.70f)

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
                                args[2] = -1000.0f
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
                                            val vRef = weakV.get()
                                            vRef?.translationZ = 0.0f
                                            vRef?.translationX = 0.0f
                                            vRef?.translationY = 0.0f
                                            vRef?.scaleX = 1.0f
                                            vRef?.scaleY = 1.0f
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
                                        v.translationX = 0.0f
                                        v.translationY = 0.0f
                                        v.scaleX = 1.0f
                                        v.scaleY = 1.0f
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

            // Hook UserPresentAnimationCompatBase pivot lifecycle and 3D-to-2D projection
            try {
                val baseAnimClass = Class.forName(
                    "com.miui.home.launcher.compat.UserPresentAnimationCompatBase",
                    false,
                    classLoader
                )

                // 1. Hook changePivot to anchor pivot at the top of the display
                try {
                    for (m in baseAnimClass.declaredMethods) {
                        if (m.name == "changePivot" && m.parameterTypes.isEmpty()) {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val res = chain.proceed()
                                    updatePivot(chain.thisObject, classLoader)
                                    return res
                                }
                            })
                            Log.d(TAG, "Hooked UserPresentAnimationCompatBase.changePivot")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook UserPresentAnimationCompatBase.changePivot", t)
                }

                // 2. Hook prepareUserPresentAnimation to ensure pivot is applied before child tags are calculated
                try {
                    for (m in baseAnimClass.declaredMethods) {
                        if (m.name == "prepareUserPresentAnimation" && m.parameterTypes.size == 1 && m.parameterTypes[0] == View::class.java) {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    updatePivot(chain.thisObject, classLoader)
                                    return chain.proceed()
                                }
                            })
                            Log.d(TAG, "Hooked UserPresentAnimationCompatBase.prepareUserPresentAnimation")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook UserPresentAnimationCompatBase.prepareUserPresentAnimation", t)
                }

                // 3. Hook UserPresentAnimationCompatFold.updatePivot for foldable device support
                try {
                    val foldAnimClass = Class.forName(
                        "com.miui.home.launcher.compat.UserPresentAnimationCompatFold",
                        false,
                        classLoader
                    )
                    for (m in foldAnimClass.declaredMethods) {
                        if (m.name == "updatePivot" && m.parameterTypes.isEmpty()) {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val res = chain.proceed()
                                    updatePivot(chain.thisObject, classLoader)
                                    return res
                                }
                            })
                            Log.d(TAG, "Hooked UserPresentAnimationCompatFold.updatePivot")
                        }
                    }
                } catch (_: Throwable) {}

                // 4. Hook conversionValueFrom3DTo2D: true perspective flight from top-center pivot
                val convMethod = baseAnimClass.getDeclaredMethod(
                    "conversionValueFrom3DTo2D",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
                module.hook(convMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed() as? FloatArray ?: return chain.proceed()
                        val cfg = LauncherConfigHolder.unlockConfig
                        if (cfg.enabled) {
                            val rawArgs = chain.args
                            fun getArg(index: Int): Any? {
                                return if (rawArgs is List<*>) rawArgs.getOrNull(index) else (rawArgs as? Array<*>)?.getOrNull(index)
                            }
                            val argI = (getArg(0) as? Number)?.toInt() ?: 0
                            val argI2 = (getArg(1) as? Number)?.toInt() ?: 0
                            val f = (getArg(2) as? Number)?.toFloat() ?: 0.0f

                            if (cfg.animMode == 1) {
                                // Normalized spring displacement fraction:
                                // Starts at 1.0f (f = -1000f), descends to 0.0f (f = 0f), with gentle settle clamp
                                val fraction = (-f / 1000.0f).coerceIn(-0.06f, 1.0f)

                                // Authentic iOS drop: every icon drops from the top, lower rows drop slightly further
                                val posY = argI2.coerceAtLeast(100).toFloat()
                                val initialDropY = -(180.0f + (posY * 0.12f))
                                val initialPinchX = -argI * 0.08f

                                result[0] = initialPinchX * fraction * cfg.pivotXSpread
                                result[1] = initialDropY * fraction
                                result[2] = 1.0f - (0.18f * fraction)
                            } else {
                                val zScale = if (f <= 0.0f) {
                                    (1.0f + (f / 2000.0f)).coerceIn(0.65f, 1.0f)
                                } else {
                                    (1.0f + (f / 1500.0f)).coerceIn(1.0f, 1.08f)
                                }
                                result[0] = (zScale - 1.0f) * argI
                                result[1] = (zScale - 1.0f) * argI2
                                result[2] = zScale
                            }
                        }
                        return result
                    }
                })
                Log.i(TAG, "Hooked UserPresentAnimationCompatBase.conversionValueFrom3DTo2D successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook UserPresentAnimationCompatBase hooks", t)
            }

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
}
