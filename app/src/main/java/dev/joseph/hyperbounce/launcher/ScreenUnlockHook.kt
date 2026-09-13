package dev.joseph.hyperbounce.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
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

object ScreenUnlockHook {
    private const val TAG = "HyperBounce_Unlock"

    private val isAnimating = ThreadLocal.withInitial { false }
    private val isOuterColumn = ThreadLocal.withInitial { false }
    private val isIndicator = ThreadLocal.withInitial { false }
    private val isDockView = ThreadLocal.withInitial { false }
    private val currentRow = ThreadLocal.withInitial { 0 }
    private val currentKineticFactor = ThreadLocal.withInitial { 0.0f }
    private val normalizedDistance = ThreadLocal.withInitial { 0.0f }
    private val activeResponse = ThreadLocal.withInitial { 0.5f }
    private val activeDamping = ThreadLocal.withInitial { 0.6f }

    @Volatile
    private var latestVelocity = 0.0f
    @Volatile
    private var lastVelocityTime = 0L

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

                            val timeSinceVelocity = System.currentTimeMillis() - lastVelocityTime
                            if (timeSinceVelocity < 2500L && latestVelocity > 0f) {
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
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error calculating icon position", t)
                        }
                    }

                    isAnimating.set(true)
                    val result = chain.proceed()
                    isAnimating.set(false)
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
                        if (isDockView.get() || isIndicator.get()) {
                            val resp = (cfg.kineticDockResponse * cfg.durationScale).coerceIn(0.15f, 1.5f)
                            val damp = cfg.kineticDockDamping.coerceIn(0.20f, 1.0f)
                            activeResponse.set(resp)
                            activeDamping.set(damp)
                            floatArrayOf(damp, resp)
                        } else {
                            val kF = currentKineticFactor.get()
                            val vInf = cfg.velocityInfluence
                            val velocityBoost = kF * vInf
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

                            // Baseline damping on normal unlock:
                            // Row with bounce = 0.0 stays at 1.0 (flat landing, no bounce)
                            // Row with bounce = 0.55 drops to 0.78 (underdamped, deep plunge & rebound)
                            val baseDamping = 1.0f - (customBounce * 0.40f)

                            // Universal Resting Damping is the master multiplier
                            val effectiveDamping = cfg.cascadeDamping * baseDamping

                            // Dynamic velocity boost:
                            // Swiping faster further plunges rows that have bounce,
                            // while rows with bounce = 0 remain flat
                            val dynamicDampingDrop = customBounce * velocityBoost * 0.35f
                            val finalDamping = (effectiveDamping * (1.0f - dynamicDampingDrop)).coerceIn(0.20f, 1.0f)

                            // Settle response:
                            // Scaled by universal duration scale, and quickened by swipe velocity:
                            val scaledResponse = customSettle * cfg.durationScale
                            val finalResponse = (scaledResponse / (1.0f + (velocityBoost * 0.35f))).coerceIn(0.15f, 1.5f)

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
                                    cfg.kineticDockZDepth.coerceAtLeast(10.0f)
                                } else {
                                    val baseZ = cfg.getDyn("r${safeRow}_z", ScreenUnlockDefaults.rowZ(safeRow, cfg.gridRows))
                                    val zMult = 1.0f + (velocityBoost * 0.40f)
                                    (baseZ * zMult).coerceAtLeast(10.0f)
                                }

                                // In CameraLite projection, startVal MUST be negative to start outside screen
                                args[2] = -abs(targetZ)
                            }
                        }
                        return chain.proceed(args)
                    }
                    return chain.proceed()
                }
            })

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

            Log.d(TAG, "triggerUnlockAnimationOnLauncher: executing on $launcher")

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

            // 2. Play animation cleanly
            if (userPresentAnim != null) {
                try {
                    userPresentAnim.javaClass.getMethod("showAnimation").invoke(userPresentAnim)
                    Log.d(TAG, "Triggered showAnimation successfully")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to call showAnimation", e)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error triggering unlock animation on launcher", t)
        }
    }
}
