package dev.joseph.hyperbounce.launcher

import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
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

    private var sCellXField: Field? = null
    private var sCellYField: Field? = null
    private var sContainerField: Field? = null
    private var sTagFieldsCached = false

    fun updateVelocity(velocity: Float) {
        latestVelocity = velocity
        lastVelocityTime = System.currentTimeMillis()
    }

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

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

                            var cellX = -1
                            var cellY = -1
                            var container = 0L
                            val tag = view.tag
                            if (tag != null) {
                                if (!sTagFieldsCached) {
                                    try { sCellXField = tag.javaClass.getField("cellX") } catch (_: Throwable) {}
                                    try { sCellYField = tag.javaClass.getField("cellY") } catch (_: Throwable) {}
                                    try { sContainerField = tag.javaClass.getField("container") } catch (_: Throwable) {}
                                    sTagFieldsCached = true
                                }
                                sCellXField?.let { try { cellX = it.getInt(tag) } catch (_: Throwable) {} }
                                sCellYField?.let { try { cellY = it.getInt(tag) } catch (_: Throwable) {} }
                                sContainerField?.let { try { container = it.getLong(tag) } catch (_: Throwable) {} }
                            }

                            val colIndex: Int = if (cellX != -1) {
                                max(0, min(cols - 1, cellX))
                            } else {
                                val cellWidth = screenW / cols.toFloat()
                                max(0, min(cols - 1, (vx / cellWidth).toInt()))
                            }
                            isOuterColumn.set(colIndex == 0 || colIndex == cols - 1)

                            val calculatedRow = if (cellY != -1) cellY else floor((vy / screenH) * rows).toInt()
                            val rowIndex = max(0, min(rows - 1, calculatedRow))

                            isIndicator.set(args[2] as? Boolean ?: false)
                            isDockView.set(container == -101L)
                            currentRow.set(rowIndex)

                            if (System.currentTimeMillis() - lastVelocityTime < 2500) {
                                currentKineticFactor.set(min(1.0f, Math.abs(latestVelocity) / cfg.maxVelocity))
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
                            floatArrayOf(cfg.dockDamping, cfg.dockResponse)
                        } else {
                            val dist = normalizedDistance.get()
                            val rawResponse = cfg.centerResponse + (dist * cfg.edgeResponseDelay)
                            val rawDamping = cfg.centerDamping - (dist * cfg.edgeDampingDrop)
                            val minR = min(cfg.minResponse, cfg.maxResponse)
                            val maxR = max(cfg.minResponse, cfg.maxResponse)
                            val minD = min(cfg.minDamping, cfg.maxDamping)
                            val maxD = max(cfg.minDamping, cfg.maxDamping)
                            val response = rawResponse.coerceIn(minR, maxR)
                            val damping = rawDamping.coerceIn(minD, maxD)
                            floatArrayOf(damping, response)
                        }
                    } else {
                        if (isDockView.get() || isIndicator.get()) {
                            activeResponse.set(cfg.kineticDockResponse * cfg.durationScale)
                            activeDamping.set(cfg.kineticDockDamping)
                            floatArrayOf(cfg.kineticDockDamping, cfg.kineticDockResponse * cfg.durationScale)
                        } else {
                            val kF = currentKineticFactor.get()
                            val vInf = cfg.velocityInfluence
                            val safeRow = max(0, min(cfg.gridRows - 1, currentRow.get()))
                            val outer = isOuterColumn.get()

                            val customBounce = if (outer) {
                                cfg.getDyn("r${safeRow}_outer_bounce", 0.2f)
                            } else {
                                cfg.getDyn("r${safeRow}_center_bounce", 0.2f)
                            }
                            val customSettle = if (outer) {
                                cfg.getDyn("r${safeRow}_outer_settle", 0.5f)
                            } else {
                                cfg.getDyn("r${safeRow}_center_settle", 0.5f)
                            }

                            var response = (customSettle * cfg.durationScale) * (1.0f - (0.30f * kF * vInf))
                            val bounceDrop = customBounce * kF * vInf
                            var damping = cfg.cascadeDamping * (1.0f - bounceDrop)

                            response = response.coerceIn(0.15f, 1.5f)
                            damping = damping.coerceIn(0.15f, 1.0f)

                            activeResponse.set(response)
                            activeDamping.set(damping)
                            floatArrayOf(damping, response)
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

                        if (cfg.animMode == 0) {
                            if (endVal == 0.0f && startVal > 5.0f) {
                                args[2] = startVal * cfg.zDepthMultiplier
                            }
                        } else {
                            val kF = currentKineticFactor.get()
                            val vInf = cfg.velocityInfluence
                            val safeRow = max(0, min(cfg.gridRows - 1, currentRow.get()))

                            if (startVal == 0.0f && endVal == 1.0f) {
                                args[0] = 1.0f
                                args[1] = 0.15f * cfg.durationScale
                            } else if (startVal > 0.5f && startVal < 1.0f && endVal == 1.0f) {
                                args[2] = 0.85f - (0.20f * kF * vInf)
                                args[0] = activeDamping.get()
                                args[1] = activeResponse.get()
                            } else if (endVal == 0.0f && startVal > 5.0f) {
                                if (isDockView.get() || isIndicator.get()) {
                                    args[2] = max(1.0f, cfg.kineticDockZDepth)
                                } else {
                                    val baseZ = cfg.getDyn("r${safeRow}_z", 1000.0f)
                                    val zMult = 1.0f + (kF * vInf * 0.4f)
                                    args[2] = max(1.0f, baseZ * zMult)
                                }
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
}
