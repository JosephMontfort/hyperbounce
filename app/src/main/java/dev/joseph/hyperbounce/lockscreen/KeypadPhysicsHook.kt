package dev.joseph.hyperbounce.lockscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import dev.joseph.hyperbounce.lockscreen.glass.LiquidGlassKeyOptimizedDrawable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Feature 2 (Optimized Pipeline): 100% Real-Time Liquid Glass 2.0 Lockscreen PIN Pad Engine.
 *
 * Optimizations:
 * 1. Wallpaper Blur Removal: Hooks KeyguardPanelViewController.setBouncerShowingFraction to 0.0f
 *    and needWallPagerBlur to false, stopping SurfaceFlinger 100px GPU blur and IPC wallpaper calls.
 * 2. Native Hardware Dimming: Hooks ScrimState.BOUNCER.prepare to set mBehindAlpha = 0.45f,
 *    engaging SystemUI's native ScrimView for zero-overhead, buttery 120 FPS dimming.
 * 3. Pure Wallpaper Refraction: Feeds razor-sharp unblurred wallpaper into LiquidGlassKeyOptimizedDrawable
 *    so buttons refract crisp underlying pixels in real time without muddy double-blur.
 * 4. iOS Tactile Calibration & AndroidLiquidGlass Jelly Stretch Physics:
 *    Zero-lag 60ms decelerate tactile compression on press, volume-preserved rubberband stretch along drag,
 *    and slight spring overshoot bounce on release.
 */
object KeypadPhysicsHook {

    private const val TAG = "HyperBounce_Keypad"

    private class KeypadTouchState(
        val downRawX: Float,
        val downRawY: Float,
        var isDragging: Boolean = false
    )

    private val touchStates = WeakHashMap<View, KeypadTouchState>()
    private val appearAnimators = WeakHashMap<View, ValueAnimator>()
    private val releaseAnimators = WeakHashMap<View, ValueAnimator>()

    private fun getBaseTranslationX(view: View, cfg: LockScreenKeypadConfig, density: Float): Float {
        val entryName = try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            ""
        }
        return when (entryName) {
            "key1", "key4", "key7", "emergency_call_button" -> -cfg.col1Offset * density
            "key3", "key6", "key9", "delete_container", "delete_button", "zero_delete_button", "cancel_button", "zero_cancel_button" -> cfg.col3Offset * density
            else -> 0f
        }
    }


    private var cachedDigitField: Field? = null
    private var cachedKlondikeField: Field? = null
    private var hasCachedFields = false

    private fun ensureCachedKeyFields(keyClass: Class<*>) {
        if (hasCachedFields) return
        hasCachedFields = true
        try {
            cachedDigitField = keyClass.getDeclaredField("mDigitText").apply { isAccessible = true }
        } catch (_: Throwable) {}
        try {
            cachedKlondikeField = keyClass.getDeclaredField("mKlondikeText").apply { isAccessible = true }
        } catch (_: Throwable) {}
    }

    private fun startIos26RadialBloomAnimation(
        pinView: View,
        cfg: LockScreenKeypadConfig,
        density: Float
    ) {
        val ctx = pinView.context

        // Position emergency and delete buttons at their resting places without blooming
        val emerId = ctx.resources.getIdentifier("emergency_call_button", "id", "com.android.systemui")
        if (emerId != 0) {
            val emerView = pinView.findViewById<View>(emerId)
            emerView?.animate()?.cancel()
            emerView?.translationX = -cfg.col1Offset * density
            emerView?.translationY = 0f
            emerView?.scaleX = 1f
            emerView?.scaleY = 1f
            emerView?.alpha = 1f
        }
        val delNames = arrayOf("delete_button", "cancel_button", "zero_delete_button", "zero_cancel_button")
        for (dn in delNames) {
            val delId = ctx.resources.getIdentifier(dn, "id", "com.android.systemui")
            if (delId != 0) {
                val delView = pinView.findViewById<View>(delId)
                delView?.animate()?.cancel()
                delView?.translationX = cfg.col3Offset * density
                delView?.translationY = 0f
                delView?.scaleX = 1f
                delView?.scaleY = 1f
                delView?.alpha = 1f
            }
        }

        // Only numeric digits (1-9 and 0) bloom outward from center Key 5
        val keys = arrayOf(
            arrayOf("key1", "key2", "key3"),
            arrayOf("key4", "key5", "key6"),
            arrayOf("key7", "key8", "key9"),
            arrayOf("", "key0", "")
        )

        val pitchX = (cfg.circleDiameter + 24f + cfg.columnSpacing) * density
        val pitchY = (cfg.circleDiameter + 30f + cfg.rowSpacing) * density
        val cluster = cfg.iosBloomClusterFactor.coerceIn(0.30f, 0.95f)

        val totalDuration = cfg.iosBloomDuration.toLong().coerceIn(300L, 1000L)
        val zeta = cfg.iosBloomDamping.toDouble().coerceIn(0.30, 0.85)
        val omega = cfg.iosBloomStiffness.toDouble().coerceIn(8.0, 22.0)
        val T = totalDuration / 1000.0
        val sqrtTerm = Math.sqrt(Math.max(0.01, 1.0 - zeta * zeta))
        val wd = omega * sqrtTerm

        val kv = cfg.iosBloomVelocityStretch.coerceIn(0.02f, 0.40f)
        val ka = cfg.iosBloomRadialSquish.coerceIn(0.05f, 0.60f)

        val col1TransX = -cfg.col1Offset * density
        val col3TransX = cfg.col3Offset * density

        for (row in keys.indices) {
            for (col in keys[row].indices) {
                val name = keys[row][col]
                if (name.isEmpty()) continue
                val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                if (id == 0) continue

                val keyView = pinView.findViewById<View>(id) ?: continue
                ensureCachedKeyFields(keyView.javaClass)

                val optDrawable = keyView.background as? LiquidGlassKeyOptimizedDrawable
                optDrawable?.startLiveTracking()

                val targetTransX = when (col) {
                    0 -> col1TransX
                    2 -> col3TransX
                    else -> 0f
                }

                val relCol = col - 1
                val relRow = row - 1
                val distUnits = Math.hypot(relCol.toDouble(), relRow.toDouble()).toFloat()
                val vecX = relCol * pitchX
                val vecY = relRow * pitchY

                val startOffsetX = -vecX * cluster
                val startOffsetY = -vecY * cluster

                val angleRad = if (distUnits > 0.001f) Math.atan2(relRow.toDouble(), relCol.toDouble()) else 0.0
                val dirX = if (distUnits > 0.001f) Math.cos(angleRad).toFloat() else 0f
                val dirY = if (distUnits > 0.001f) Math.sin(angleRad).toFloat() else 0f

                val customDelay = cfg.getKeyDelay(name, row, col)
                val delay = if (customDelay >= 0L) customDelay else (distUnits * cfg.iosBloomWaveStagger).toLong().coerceAtLeast(0L)

                appearAnimators.remove(keyView)?.cancel()
                keyView.animate().cancel()

                keyView.alpha = 0f
                keyView.translationX = targetTransX + startOffsetX
                keyView.translationY = startOffsetY
                keyView.scaleX = 0.82f
                keyView.scaleY = 0.82f

                val digitText = if (cfg.iosBloomDecoupleText) {
                    try { cachedDigitField?.get(keyView) as? View } catch (_: Throwable) { null }
                } else null
                val klondikeText = if (cfg.iosBloomDecoupleText) {
                    try { cachedKlondikeField?.get(keyView) as? View } catch (_: Throwable) { null }
                } else null

                if (digitText != null) {
                    digitText.translationX = -startOffsetX
                    digitText.translationY = -startOffsetY
                    digitText.scaleX = 1f / 0.82f
                    digitText.scaleY = 1f / 0.82f
                    digitText.alpha = 0f
                }
                if (klondikeText != null) {
                    klondikeText.translationX = -startOffsetX
                    klondikeText.translationY = -startOffsetY
                    klondikeText.scaleX = 1f / 0.82f
                    klondikeText.scaleY = 1f / 0.82f
                    klondikeText.alpha = 0f
                }

                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = totalDuration
                    startDelay = delay
                    interpolator = LinearInterpolator()

                    addUpdateListener { va ->
                        val u = va.animatedFraction
                        val t = u * T
                        val decay = Math.exp(-zeta * omega * t)
                        val cosW = Math.cos(wd * t)
                        val sinW = Math.sin(wd * t)

                        // Harmonic spring progress with overshoot:
                        val s = 1.0 - decay * (cosW + (zeta / sqrtTerm) * sinW)
                        val sDot = (omega / sqrtTerm) * decay * sinW
                        val sDdot = decay * (omega * omega * cosW - (zeta * omega * omega / sqrtTerm) * sinW)

                        val currDispX = (startOffsetX * (1.0 - s)).toFloat()
                        val currDispY = (startOffsetY * (1.0 - s)).toFloat()
                        val currTransX = targetTransX + currDispX
                        val currTransY = currDispY

                        val scaleX: Float
                        val scaleY: Float

                        if (distUnits < 0.001f) {
                            // Center button 5: isotropic breathing bloom
                            val s5 = (1.0 - 0.32 * decay * cosW).toFloat().coerceIn(0.70f, 1.20f)
                            scaleX = s5
                            scaleY = s5
                        } else {
                            val velNorm = (sDot / omega).toFloat()
                            val accelBrake = (Math.max(0.0, -sDdot) / (omega * omega)).toFloat()

                            val deltaR = kv * velNorm - ka * accelBrake
                            val deltaT = -deltaR * 0.70f

                            val baseScale = (0.82f + 0.18f * Math.min(1.0, Math.max(0.0, s))).toFloat()
                            scaleX = ((1.0f + (dirX * dirX * deltaR + dirY * dirY * deltaT)).coerceIn(0.65f, 1.45f) * baseScale).coerceIn(0.50f, 1.50f)
                            scaleY = ((1.0f + (dirY * dirY * deltaR + dirX * dirX * deltaT)).coerceIn(0.65f, 1.45f) * baseScale).coerceIn(0.50f, 1.50f)
                        }

                        keyView.alpha = (u / 0.15f).coerceIn(0f, 1f)
                        keyView.translationX = currTransX
                        keyView.translationY = currTransY
                        keyView.scaleX = scaleX
                        keyView.scaleY = scaleY

                        if (digitText != null) {
                            digitText.translationX = -currDispX
                            digitText.translationY = -currDispY
                            digitText.scaleX = 1f / scaleX.coerceAtLeast(0.1f)
                            digitText.scaleY = 1f / scaleY.coerceAtLeast(0.1f)
                            digitText.alpha = (u / 0.12f).coerceIn(0f, 1f)
                        }
                        if (klondikeText != null) {
                            klondikeText.translationX = -currDispX
                            klondikeText.translationY = -currDispY
                            klondikeText.scaleX = 1f / scaleX.coerceAtLeast(0.1f)
                            klondikeText.scaleY = 1f / scaleY.coerceAtLeast(0.1f)
                            klondikeText.alpha = (u / 0.12f).coerceIn(0f, 1f)
                        }

                        keyView.invalidate()
                    }

                    addListener(object : AnimatorListenerAdapter() {
                        private var isCancelled = false
                        override fun onAnimationCancel(animation: Animator) {
                            isCancelled = true
                            optDrawable?.stopLiveTracking()
                            appearAnimators.remove(keyView)
                        }
                        override fun onAnimationEnd(animation: Animator) {
                            if (!isCancelled) {
                                keyView.translationX = targetTransX
                                keyView.translationY = 0f
                                keyView.scaleX = 1f
                                keyView.scaleY = 1f
                                keyView.alpha = 1f
                                if (digitText != null) {
                                    digitText.translationX = 0f
                                    digitText.translationY = 0f
                                    digitText.scaleX = 1f
                                    digitText.scaleY = 1f
                                    digitText.alpha = 1f
                                }
                                if (klondikeText != null) {
                                    klondikeText.translationX = 0f
                                    klondikeText.translationY = 0f
                                    klondikeText.scaleX = 1f
                                    klondikeText.scaleY = 1f
                                    klondikeText.alpha = 1f
                                }
                                optDrawable?.stopLiveTracking()
                                keyView.invalidate()
                                appearAnimators.remove(keyView)
                            }
                        }
                    })
                }

                appearAnimators[keyView] = animator
                animator.start()
            }
        }
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook MiuiNumPadKey touch events for Liquid Glass 2.0 Press Optics & Jelly Physics
        try {
            val numPadKeyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
            for (m: Method in numPadKeyClass.declaredMethods) {
                if (m.name == "onTouchEvent" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val view = chain.thisObject as? View
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                            val event = args[0] as? MotionEvent
                            val cfg = LockScreenConfigHolder.keypad

                            if (view != null && event != null && cfg.enabled) {
                                val liquidDrawable = view.background as? LiquidGlassKeyOptimizedDrawable
                                val density = view.context.resources.displayMetrics.density
                                val baseTransX = getBaseTranslationX(view, cfg, density)

                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        appearAnimators.remove(view)?.cancel()
                                        releaseAnimators.remove(view)?.cancel()
                                        view.animate().cancel()
                                        touchStates[view] = KeypadTouchState(
                                            downRawX = event.rawX,
                                            downRawY = event.rawY,
                                            isDragging = false
                                        )

                                        // Ensure ancestors do not clip dragged button across row bounds
                                        (view.parent as? ViewGroup)?.clipChildren = false
                                        (view.parent as? ViewGroup)?.clipToPadding = false
                                        (view.parent?.parent as? ViewGroup)?.clipChildren = false
                                        (view.parent?.parent as? ViewGroup)?.clipToPadding = false

                                        // Elevate touched key and its row so they are at the topmost layer of the keypad
                                        view.translationZ = 120f * density
                                        (view.parent as? View)?.translationZ = 120f * density

                                        // Zero-lag iOS-calibrated tactile compression (60ms decelerate curve)
                                        view.animate()
                                            .scaleX(cfg.pressLiquidScale)
                                            .scaleY(cfg.pressLiquidScale)
                                            .setDuration(60L)
                                            .setInterpolator(DecelerateInterpolator(2.2f))
                                            .setUpdateListener { view.invalidate() }
                                            .start()

                                        liquidDrawable?.onTouchDown(event.x, event.y)
                                        view.invalidate()
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        val state = touchStates[view]
                                        val downRawX = state?.downRawX ?: event.rawX
                                        val downRawY = state?.downRawY ?: event.rawY
                                        val dx = event.rawX - downRawX
                                        val dy = event.rawY - downRawY
                                        val distSq = dx * dx + dy * dy

                                        val slop = 2.0f * density
                                        if (distSq > slop * slop || state?.isDragging == true) {
                                            state?.isDragging = true
                                            view.animate().cancel()
                                            releaseAnimators.remove(view)?.cancel()

                                            // Maintain topmost Z-order and unclipping during active dragging
                                            view.translationZ = 120f * density
                                            (view.parent as? View)?.translationZ = 120f * density
                                            (view.parent as? ViewGroup)?.clipChildren = false
                                            (view.parent as? ViewGroup)?.clipToPadding = false

                                            val w = view.width.toFloat().coerceAtLeast(1f)
                                            val h = view.height.toFloat().coerceAtLeast(1f)
                                            val minDim = min(w, h)

                                            // Configurable, reduced allowed stretch threshold
                                            val maxOffset = minDim * cfg.dragMaxThreshold.coerceIn(0.15f, 0.50f)
                                            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                            val jellyTx: Float
                                            val jellyTy: Float
                                            val stretchRatio: Float
                                            val dirX: Float
                                            val dirY: Float

                                            if (dist > 0.001f) {
                                                dirX = dx / dist
                                                dirY = dy / dist

                                                // Non-linear increasing resistance model:
                                                // k0 gives tactile initial firmness (no zero-resistance floatiness).
                                                // Power p ramps up resistance with pull distance.
                                                val k0 = cfg.dragInitialStiffness.coerceIn(0.15f, 0.85f).toDouble()
                                                val p = cfg.dragResistancePower.coerceIn(1.0f, 3.5f).toDouble()
                                                val xNorm = (dist / maxOffset).coerceAtLeast(0f).toDouble()
                                                val kx = k0 * xNorm
                                                val denom = Math.pow(1.0 + Math.pow(kx, p), 1.0 / p)
                                                val disp = (maxOffset * (kx / denom)).toFloat().coerceAtMost(maxOffset)

                                                jellyTx = dirX * disp
                                                jellyTy = dirY * disp
                                                stretchRatio = (disp / maxOffset).coerceIn(0f, 1f)
                                            } else {
                                                dirX = 0f
                                                dirY = 0f
                                                jellyTx = 0f
                                                jellyTy = 0f
                                                stretchRatio = 0f
                                            }

                                            // Physical Volume-Preserved Liquid Bubble Stretch along drag angle
                                            val stretchFactor = cfg.dragVolumeStretch.coerceIn(0.05f, 0.50f)
                                            val stretchAmount = stretchRatio * stretchFactor
                                            val compAmount = 1f - (1f / (1f + stretchAmount))

                                            val baseScale = cfg.pressLiquidScale + (1.0f - cfg.pressLiquidScale) * (stretchRatio * 0.40f)

                                            val dirX2 = dirX * dirX
                                            val dirY2 = dirY * dirY
                                            val liquidScaleX = baseScale * (1f + stretchAmount * dirX2 - compAmount * dirY2)
                                            val liquidScaleY = baseScale * (1f + stretchAmount * dirY2 - compAmount * dirX2)

                                            view.translationX = baseTransX + jellyTx
                                            view.translationY = jellyTy
                                            view.scaleX = liquidScaleX.coerceIn(0.60f, 1.45f)
                                            view.scaleY = liquidScaleY.coerceIn(0.60f, 1.45f)
                                        }

                                        liquidDrawable?.onTouchMove(event.x, event.y)
                                        view.invalidate()
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        touchStates.remove(view)
                                        liquidDrawable?.onTouchUp()

                                        view.animate().cancel()
                                        releaseAnimators.remove(view)?.cancel()

                                        val rowParent = view.parent as? View
                                        val currTx = view.translationX - baseTransX
                                        val currTy = view.translationY
                                        val releaseDist = hypot(currTx.toDouble(), currTy.toDouble()).toFloat()
                                        val minDim = min(view.width.toFloat().coerceAtLeast(1f), view.height.toFloat().coerceAtLeast(1f))
                                        val maxOffset = minDim * cfg.dragMaxThreshold.coerceIn(0.15f, 0.50f)
                                        val releaseNorm = (releaseDist / maxOffset).coerceIn(0f, 1f)

                                        if (releaseDist < 1.5f * density) {
                                            view.animate()
                                                .translationX(baseTransX)
                                                .translationY(0f)
                                                .translationZ(0f)
                                                .scaleX(1.0f)
                                                .scaleY(1.0f)
                                                .setDuration(180L)
                                                .setInterpolator(DecelerateInterpolator(1.8f))
                                                .withEndAction {
                                                    view.translationZ = 0f
                                                    rowParent?.translationZ = 0f
                                                }
                                                .start()
                                            rowParent?.animate()?.translationZ(0f)?.setDuration(180L)?.start()
                                        } else {
                                            // Directional Inertia Release Snapback Engine
                                            val uX = currTx / releaseDist
                                            val uY = currTy / releaseDist
                                            val durationMs = cfg.releaseDuration.toLong().coerceIn(150L, 600L)
                                            val squishAmp = cfg.releaseSquishAmp.coerceIn(0.0f, 0.70f)
                                            val overshootTension = cfg.releaseOvershoot.coerceIn(1.0f, 3.5f)

                                            val releaseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                                                duration = durationMs
                                                interpolator = LinearInterpolator()

                                                addUpdateListener { va ->
                                                    val t = va.animatedFraction

                                                    // 1. Directional position snapback along vector (uX, uY)
                                                    val omega = (Math.PI * overshootTension).toDouble()
                                                    val dampingCoeff = 3.8
                                                    val decay = Math.exp(-dampingCoeff * t)
                                                    val posRatio = (decay * Math.cos(omega * t)).toFloat()

                                                    val currentTx = baseTransX + uX * releaseDist * posRatio
                                                    val currentTy = uY * releaseDist * posRatio

                                                    // 2. Directional Liquid Inertia Mass Transfer Squish along (uX, uY)
                                                    val squishFreq = omega * 1.05
                                                    val squishDecay = Math.exp(-3.5 * t)
                                                    val deltaParallel = (releaseNorm * squishAmp * squishDecay * Math.cos(squishFreq * t)).toFloat()

                                                    val lambdaParallel = (1.0f + deltaParallel).coerceIn(0.55f, 1.65f)
                                                    val lambdaPerp = 1.0f / lambdaParallel

                                                    val diffParallel = lambdaParallel - 1.0f
                                                    val diffPerp = 1.0f - lambdaPerp

                                                    val uX2 = uX * uX
                                                    val uY2 = uY * uY
                                                    val scaleX = 1.0f + diffParallel * uX2 - diffPerp * uY2
                                                    val scaleY = 1.0f + diffParallel * uY2 - diffPerp * uX2

                                                    view.translationX = currentTx
                                                    view.translationY = currentTy
                                                    view.scaleX = scaleX.coerceIn(0.60f, 1.50f)
                                                    view.scaleY = scaleY.coerceIn(0.60f, 1.50f)
                                                    view.invalidate()
                                                }

                                                addListener(object : AnimatorListenerAdapter() {
                                                    private var isCancelled = false
                                                    override fun onAnimationCancel(animation: Animator) {
                                                        isCancelled = true
                                                        releaseAnimators.remove(view)
                                                    }
                                                    override fun onAnimationEnd(animation: Animator) {
                                                        if (!isCancelled) {
                                                            view.translationX = baseTransX
                                                            view.translationY = 0f
                                                            view.translationZ = 0f
                                                            view.scaleX = 1f
                                                            view.scaleY = 1f
                                                            rowParent?.translationZ = 0f
                                                            view.invalidate()
                                                        }
                                                        releaseAnimators.remove(view)
                                                    }
                                                })
                                            }
                                            releaseAnimators[view] = releaseAnim
                                            releaseAnim.start()

                                            rowParent?.animate()?.translationZ(0f)?.setDuration(durationMs)?.start()
                                        }
                                    }
                                }
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MiuiNumPadKey Liquid Glass touch hook error", t)
        }

        // 2. Hook MiuiKeyguardPINView for Liquid Glass 2.0 Button Injection, Sizing & Layout
        try {
            val pinViewClass = Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader)

            val styleKeyAsLiquidGlass: (View?) -> Unit = { keyView ->
                if (keyView != null) {
                    try {
                        val ctx = keyView.context
                        val density = ctx.resources.displayMetrics.density
                        val cfg = LockScreenConfigHolder.keypad

                        try {
                            val setAnimMethod = keyView.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                            setAnimMethod.isAccessible = true
                            setAnimMethod.invoke(keyView, false)
                        } catch (_: Throwable) {}

                        keyView.clipToOutline = false
                        (keyView.parent as? ViewGroup)?.clipChildren = false
                        (keyView.parent?.parent as? ViewGroup)?.clipChildren = false

                        // Apply custom button diameter to layout params (Layout Spacing Hack)
                        val customDiameterPx = (cfg.circleDiameter * density).toInt()
                        val lp = keyView.layoutParams
                        if (lp != null && (lp.width != customDiameterPx || lp.height != customDiameterPx)) {
                            lp.width = customDiameterPx
                            lp.height = customDiameterPx
                            keyView.layoutParams = lp
                        }

                        // Set Liquid Glass 2.0 Circular Button Lens Background (Optimized Unblurred)
                        if (keyView.background !is LiquidGlassKeyOptimizedDrawable) {
                            keyView.background = LiquidGlassKeyOptimizedDrawable(keyView, density)
                        }
                        keyView.elevation = 4 * density

                        // Letters text (Klondike)
                        try {
                            val klondikeField: Field = keyView.javaClass.getDeclaredField("mKlondikeText")
                            klondikeField.isAccessible = true
                            val klondikeText = klondikeField.get(keyView) as? TextView
                            if (klondikeText != null) {
                                klondikeText.visibility = if (cfg.showKlondike) View.VISIBLE else View.GONE
                                if (cfg.showKlondike) {
                                    klondikeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                                    klondikeText.setTextColor(0xCCFFFFFF.toInt())
                                }
                            }
                        } catch (_: Throwable) {}

                        // Digit text appearance
                        try {
                            val digitField: Field = keyView.javaClass.getDeclaredField("mDigitText")
                            digitField.isAccessible = true
                            val digitText = digitField.get(keyView) as? TextView
                            if (digitText != null) {
                                digitText.setTextColor(0xFFFFFFFF.toInt())
                                digitText.setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.digitTextSize)
                                digitText.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                digitText.setShadowLayer(0f, 0f, 0f, 0)
                            }
                        } catch (_: Throwable) {}
                    } catch (_: Throwable) {}
                }
            }

            val applyPinLayoutAndStyles: (View?) -> Unit = { pinView ->
                if (pinView != null) {
                    try {
                        val ctx = pinView.context
                        val density = ctx.resources.displayMetrics.density
                        val cfg = LockScreenConfigHolder.keypad

                        // Ensure keyguard_bouncer_bg does not show stale blurred preview bitmap
                        val root = pinView.rootView
                        if (root != null) {
                            val bouncerBgId = ctx.resources.getIdentifier("keyguard_bouncer_bg", "id", "com.android.systemui")
                            if (bouncerBgId != 0) {
                                val iv = root.findViewById<View>(bouncerBgId) as? ImageView
                                iv?.setImageDrawable(null)
                                iv?.visibility = View.GONE
                            }
                        }

                        // Ensure parent ViewGroup doesn't clip when lifting or expanding
                        (pinView as? ViewGroup)?.clipChildren = false
                        (pinView as? ViewGroup)?.clipToPadding = false

                        // 1. Container Horizontal Width, Column Spacing & Vertical Lift
                        val pinContainerId = ctx.resources.getIdentifier("pin_container", "id", "com.android.systemui")
                        val pinContainer = if (pinContainerId != 0) pinView.findViewById<ViewGroup>(pinContainerId) else null
                        if (pinContainer != null) {
                            pinContainer.clipChildren = false
                            pinContainer.clipToPadding = false

                            val dimenId = ctx.resources.getIdentifier("miui_keyguard_pin_view_rows_layout_width", "dimen", "com.android.systemui")
                            val defaultWidthPx = if (dimenId != 0) ctx.resources.getDimensionPixelOffset(dimenId) else (310 * density).toInt()
                            val extraWidthPx = (3 * cfg.columnSpacing * density).toInt()
                            val maxAllowedWidth = ctx.resources.displayMetrics.widthPixels
                            val targetWidth = (defaultWidthPx + extraWidthPx).coerceIn((180 * density).toInt(), maxAllowedWidth)
                            val lp = pinContainer.layoutParams
                            if (lp != null && lp.width != targetWidth) {
                                lp.width = targetWidth
                                pinContainer.layoutParams = lp
                            }

                            // Lift entire keypad upwards to position bottom row (0)
                            pinContainer.translationY = -cfg.pinpadLift * density
                        }

                        // 2. Uniform Vertical Row Spacing (Layout Spacing Hack)
                        val row1Id = ctx.resources.getIdentifier("row1", "id", "com.android.systemui")
                        val row2Id = ctx.resources.getIdentifier("row2", "id", "com.android.systemui")
                        val row3Id = ctx.resources.getIdentifier("row3", "id", "com.android.systemui")
                        val row4Id = ctx.resources.getIdentifier("row4", "id", "com.android.systemui")
                        val row0Id = ctx.resources.getIdentifier("row0", "id", "com.android.systemui")

                        val rowStep = cfg.rowSpacing * density
                        if (row1Id != 0) pinView.findViewById<View>(row1Id)?.translationY = -3f * rowStep
                        if (row2Id != 0) pinView.findViewById<View>(row2Id)?.translationY = -2f * rowStep
                        if (row3Id != 0) pinView.findViewById<View>(row3Id)?.translationY = -1f * rowStep
                        if (row4Id != 0) pinView.findViewById<View>(row4Id)?.translationY = 0f
                        if (row0Id != 0) pinView.findViewById<View>(row0Id)?.translationY = cfg.row0Offset * density

                        // 3. Fine-Tune Column Horizontal Offsets (translationX)
                        val col1TransX = -cfg.col1Offset * density
                        val col3TransX = cfg.col3Offset * density

                        val col1KeyNames = arrayOf("key1", "key4", "key7", "emergency_call_button")
                        for (name in col1KeyNames) {
                            val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                            if (id != 0) {
                                pinView.findViewById<View>(id)?.translationX = col1TransX
                            }
                        }

                        val col3KeyNames = arrayOf("key3", "key6", "key9", "delete_container", "delete_button", "zero_delete_button", "cancel_button", "zero_cancel_button")
                        for (name in col3KeyNames) {
                            val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                            if (id != 0) {
                                pinView.findViewById<View>(id)?.translationX = col3TransX
                            }
                        }

                        // 4. Numeric Digit Keys 1-9 and 0
                        val numKeyIds = intArrayOf(
                            ctx.resources.getIdentifier("key1", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key2", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key3", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key4", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key5", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key6", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key7", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key8", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key9", "id", "com.android.systemui"),
                            ctx.resources.getIdentifier("key0", "id", "com.android.systemui")
                        )
                        for (id in numKeyIds) {
                            if (id != 0) {
                                styleKeyAsLiquidGlass(pinView.findViewById(id))
                            }
                        }

                        // 5. Cleanly style Action Buttons (delete, cancel, emergency) without breaking LinearLayout weights
                        val styleActionButton: (View?) -> Unit = { btn ->
                            if (btn != null) {
                                try {
                                    btn.background = null
                                    if (btn is TextView) {
                                        btn.setTextColor(0xEEFFFFFF.toInt())
                                        btn.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                        btn.gravity = Gravity.CENTER
                                        btn.setShadowLayer(0f, 0f, 0f, 0)
                                    }
                                } catch (_: Throwable) {}
                            }
                        }

                        val delContainerId = ctx.resources.getIdentifier("delete_container", "id", "com.android.systemui")
                        if (delContainerId != 0) pinView.findViewById<View>(delContainerId)?.background = null

                        val actionButtonNames = arrayOf(
                            "delete_button", "zero_delete_button",
                            "cancel_button", "zero_cancel_button",
                            "emergency_call_button"
                        )
                        for (name in actionButtonNames) {
                            val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                            if (id != 0) {
                                styleActionButton(pinView.findViewById(id))
                            }
                        }

                        // 6. Unclip All Rows
                        for (rId in intArrayOf(row0Id, row1Id, row2Id, row3Id, row4Id)) {
                            if (rId != 0) {
                                (pinView.findViewById<View>(rId) as? ViewGroup)?.clipChildren = false
                                (pinView.findViewById<View>(rId) as? ViewGroup)?.clipToPadding = false
                            }
                        }

                        // 7. Direct Touch Routing for Elevated / Negative-Translated Rows (e.g. Row 1: Keys 1, 2, 3)
                        val attachKeyTouchRouter: (View?) -> Unit = { targetView ->
                            if (targetView != null) {
                                var activeForwardKey: View? = null
                                val tempLoc = IntArray(2)

                                targetView.setOnTouchListener { _, ev ->
                                    when (ev.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            activeForwardKey = null
                                            for (id in numKeyIds) {
                                                if (id != 0) {
                                                    val key = pinView.findViewById<View>(id)
                                                    if (key != null && key.visibility == View.VISIBLE) {
                                                        key.getLocationOnScreen(tempLoc)
                                                        val l = tempLoc[0].toFloat()
                                                        val t = tempLoc[1].toFloat()
                                                        val r = l + key.width
                                                        val b = t + key.height
                                                        if (ev.rawX >= l && ev.rawX <= r && ev.rawY >= t && ev.rawY <= b) {
                                                            activeForwardKey = key
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                            val targetKey = activeForwardKey
                                            if (targetKey != null) {
                                                val localEvent = MotionEvent.obtain(ev)
                                                targetKey.getLocationOnScreen(tempLoc)
                                                localEvent.setLocation(ev.rawX - tempLoc[0], ev.rawY - tempLoc[1])
                                                val handled = targetKey.dispatchTouchEvent(localEvent)
                                                localEvent.recycle()
                                                handled
                                            } else {
                                                false
                                            }
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val targetKey = activeForwardKey
                                            if (targetKey != null) {
                                                val localEvent = MotionEvent.obtain(ev)
                                                targetKey.getLocationOnScreen(tempLoc)
                                                localEvent.setLocation(ev.rawX - tempLoc[0], ev.rawY - tempLoc[1])
                                                targetKey.dispatchTouchEvent(localEvent)
                                                localEvent.recycle()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            val targetKey = activeForwardKey
                                            activeForwardKey = null
                                            if (targetKey != null) {
                                                val localEvent = MotionEvent.obtain(ev)
                                                targetKey.getLocationOnScreen(tempLoc)
                                                localEvent.setLocation(ev.rawX - tempLoc[0], ev.rawY - tempLoc[1])
                                                targetKey.dispatchTouchEvent(localEvent)
                                                localEvent.recycle()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                }
                            }
                        }

                        val spacePattrenId = ctx.resources.getIdentifier("space_pattren", "id", "com.android.systemui")
                        if (spacePattrenId != 0) attachKeyTouchRouter(pinView.findViewById(spacePattrenId))
                        if (row0Id != 0) attachKeyTouchRouter(pinView.findViewById(row0Id))
                        attachKeyTouchRouter(pinView)
                    } catch (_: Throwable) {}
                }
            }

            for (m: Method in pinViewClass.declaredMethods) {
                if (m.name == "onFinishInflate" || m.name == "updatePositionForFod" || m.name == "updateLayoutParams" || m.name == "onAttachedToWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val pinView = chain.thisObject as? View
                                applyPinLayoutAndStyles(pinView)
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                } else if (m.name == "startAppearAnimation") {
                    // iOS 26 Liquid Bloom Radial Spring Entrance Animation Engine
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val pinViewObj = chain.thisObject
                            val pinView = pinViewObj as? View ?: return chain.proceed()
                            val cfg = LockScreenConfigHolder.keypad
                            if (!cfg.enabled) return chain.proceed()

                            try {
                                applyPinLayoutAndStyles(pinView)
                            } catch (_: Throwable) {}

                            val ctx = pinView.context
                            val density = ctx.resources.displayMetrics.density

                            try {
                                val pairs = Array(4) { Array(3) { android.util.Pair(0, 0) } }
                                val keysAnimField = try { pinViewObj.javaClass.getField("keysAnimStartState") } catch (_: Throwable) { null }
                                if (keysAnimField != null) {
                                    keysAnimField.isAccessible = true
                                    keysAnimField.set(pinViewObj, pairs)
                                }
                            } catch (_: Throwable) {}

                            startIos26RadialBloomAnimation(pinView, cfg, density)

                            val row0Id = ctx.resources.getIdentifier("row0", "id", "com.android.systemui")
                            if (row0Id != 0) {
                                val row0 = pinView.findViewById<View>(row0Id)
                                if (row0 != null) {
                                    row0.animate().cancel()
                                    row0.alpha = 0f
                                    row0.translationY = -30f * density
                                    row0.animate()
                                        .alpha(1f)
                                        .translationY(cfg.row0Offset * density)
                                        .setDuration(350L)
                                        .setInterpolator(DecelerateInterpolator(1.5f))
                                        .start()
                                }
                            }

                            return null
                        }
                    })
                } else if (m.name == "startDisappearAnimation") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val pinViewObj = chain.thisObject
                            val pinView = pinViewObj as? View ?: return chain.proceed()
                            val cfg = LockScreenConfigHolder.keypad
                            if (!cfg.enabled) return chain.proceed()

                            for (anim in appearAnimators.values) {
                                anim.cancel()
                            }
                            appearAnimators.clear()
                            for (anim in releaseAnimators.values) {
                                anim.cancel()
                            }
                            releaseAnimators.clear()

                            val ctx = pinView.context
                            val density = ctx.resources.displayMetrics.density

                            val actionNames = arrayOf("emergency_call_button", "delete_button", "cancel_button", "zero_cancel_button")
                            for (name in actionNames) {
                                val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                                if (id != 0) {
                                    pinView.findViewById<View>(id)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                                }
                            }

                            val numKeyNames = arrayOf("key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key0")
                            for (name in numKeyNames) {
                                val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                                if (id != 0) {
                                    val keyView = pinView.findViewById<View>(id)
                                    (keyView?.background as? LiquidGlassKeyOptimizedDrawable)?.stopLiveTracking()
                                    keyView?.animate()
                                        ?.alpha(0f)
                                        ?.scaleX(0.70f)
                                        ?.scaleY(0.70f)
                                        ?.translationY(40f * density)
                                        ?.setDuration(180L)
                                        ?.setInterpolator(AccelerateInterpolator(1.5f))
                                        ?.start()
                                }
                            }

                            val row0Id = ctx.resources.getIdentifier("row0", "id", "com.android.systemui")
                            if (row0Id != 0) {
                                pinView.findViewById<View>(row0Id)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                            }

                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val finishField = pinViewObj.javaClass.getDeclaredField("mDisappearAnimationFinishRunnable")
                                    finishField.isAccessible = true
                                    val runnable = finishField.get(pinViewObj) as? Runnable
                                    runnable?.run()
                                } catch (_: Throwable) {}
                            }, 200L)

                            return null
                        }
                    })
                } else if (m.name == "onDetachedFromWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                for (anim in appearAnimators.values) {
                                    anim.cancel()
                                }
                                appearAnimators.clear()
                                for (anim in releaseAnimators.values) {
                                    anim.cancel()
                                }
                                releaseAnimators.clear()
                                val pinView = chain.thisObject as? View
                                if (pinView != null) {
                                    val ctx = pinView.context
                                    val numKeyIds = intArrayOf(
                                        ctx.resources.getIdentifier("key1", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key2", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key3", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key4", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key5", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key6", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key7", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key8", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key9", "id", "com.android.systemui"),
                                        ctx.resources.getIdentifier("key0", "id", "com.android.systemui")
                                    )
                                    for (id in numKeyIds) {
                                        if (id != 0) {
                                            (pinView.findViewById<View>(id)?.background as? LiquidGlassKeyOptimizedDrawable)?.release()
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MiuiKeyguardPINView Liquid Glass hook error", t)
        }

        // 3. Remove Stock Bouncer Wallpaper Blur: KeyguardPanelViewController
        try {
            val panelCtrlClass = Class.forName("com.android.keyguard.panel.KeyguardPanelViewController", false, classLoader)

            // Intercept setBouncerShowingFraction$default to force fraction = 0.0f
            for (m in panelCtrlClass.declaredMethods) {
                if (m.name == "setBouncerShowingFraction\$default") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LockScreenConfigHolder.keypad
                            if (!cfg.enabled) return chain.proceed()

                            val rawArgs = chain.args
                            val list = if (rawArgs is List<*>) rawArgs.toMutableList() else (rawArgs as Array<*>).toMutableList()
                            if (list.size >= 2) {
                                // Force bouncer fraction to 0.0f to eliminate SurfaceFlinger blur and IPC ratio updates
                                list[1] = 0.0f
                                return chain.proceed(list.toTypedArray())
                            }
                            return chain.proceed()
                        }
                    })
                } else if (m.name == "needWallPagerBlur" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LockScreenConfigHolder.keypad
                            if (!cfg.enabled) return chain.proceed()
                            // Skip unlock blur animators and bouncer wallpaper blur
                            return false
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "KeyguardPanelViewController blur removal hook error", t)
        }

        // 4. Native Hardware-Accelerated Bouncer Dimming: ScrimState.BOUNCER
        try {
            val scrimStateClass = Class.forName("com.android.systemui.statusbar.phone.ScrimState", false, classLoader)
            val bouncerField = scrimStateClass.getField("BOUNCER")
            val bouncerObj = bouncerField.get(null)
            if (bouncerObj != null) {
                val bouncerClass = bouncerObj.javaClass
                for (m in bouncerClass.declaredMethods) {
                    if (m.name == "prepare" && m.parameterTypes.size == 1) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                val cfg = LockScreenConfigHolder.keypad
                                if (cfg.enabled) {
                                    try {
                                        val behindAlphaField = scrimStateClass.getDeclaredField("mBehindAlpha").apply { isAccessible = true }
                                        val behindTintField = scrimStateClass.getDeclaredField("mBehindTint").apply { isAccessible = true }
                                        val target = chain.thisObject ?: bouncerObj
                                        // 0.45f dimming (45% pure black scrim)
                                        behindAlphaField.setFloat(target, 0.45f)
                                        behindTintField.setInt(target, 0xFF000000.toInt())
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "Failed to apply ScrimState.BOUNCER dimming", t)
                                    }
                                }
                                return result
                            }
                        })
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ScrimState.BOUNCER dimming hook error", t)
        }
    }
}
