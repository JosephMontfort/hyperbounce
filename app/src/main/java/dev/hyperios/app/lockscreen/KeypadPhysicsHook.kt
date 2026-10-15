package dev.hyperios.app.lockscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Build
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
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import dev.hyperios.app.lockscreen.glass.LiquidGlassKeyOptimizedDrawable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
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

    private fun isNormalPinPadKey(view: View): Boolean {
        if (view.background !is LiquidGlassKeyOptimizedDrawable) return false
        var p = view.parent
        while (p != null) {
            val name = p.javaClass.name
            if (name.contains("SimPin") || name.contains("SimPuk") || name.contains("KeyguardSim")) {
                return false
            }
            if (name == "com.android.keyguard.widget.MiuiKeyguardPINView" ||
                name.endsWith(".MiuiKeyguardPINView") ||
                name == "com.android.keyguard.KeyguardPINView" ||
                name.endsWith(".KeyguardPINView")
            ) {
                return true
            }
            p = (p as? View)?.parent
        }
        return false
    }

    private var sModule: XposedModule? = null
    private var isSettingsReceiverRegistered = false

    fun registerSettingsReceiver(context: Context) {
        if (isSettingsReceiverRegistered) return
        try {
            val prefs = try {
                sModule?.getRemotePreferences(dev.hyperios.app.prefs.PREFS_GROUP)
            } catch (_: Throwable) {
                null
            }
            val isHotReload = prefs?.getBoolean("hot_reload_enabled", false) ?: false
            if (!isHotReload) return

            val filter = IntentFilter("dev.hyperios.app.ACTION_SETTINGS_CHANGED")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val key = intent?.getStringExtra("key") ?: return
                    LockScreenConfigHolder.updateKey(key, intent)
                }
            }
            val appContext = context.applicationContext ?: context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            isSettingsReceiverRegistered = true
            Log.d(TAG, "Registered ACTION_SETTINGS_CHANGED receiver for Keypad physics")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register Keypad settings receiver", t)
        }
    }

    private var sRow0Id = 0
    private var sRow1Id = 0
    private var sRow2Id = 0
    private var sRow3Id = 0
    private var sRow4Id = 0
    private var sPinContainerId = 0
    private var sEmergencyCallId = 0
    private var sDeleteContainerId = 0
    private var sSpacePatternId = 0
    private var sBouncerBgId = 0
    private var sRowsLayoutWidthDimenId = 0
    private var sActionBtnIds = IntArray(0)
    private var sCancelIds = IntArray(0)
    private var sNumKeyIds = IntArray(0)
    private var sResourceIdsInitialized = false

    private fun ensureResourceIds(ctx: Context) {
        if (sResourceIdsInitialized) return
        try {
            val res = ctx.resources
            val pkg = "com.android.systemui"
            sRow0Id = res.getIdentifier("row0", "id", pkg)
            sRow1Id = res.getIdentifier("row1", "id", pkg)
            sRow2Id = res.getIdentifier("row2", "id", pkg)
            sRow3Id = res.getIdentifier("row3", "id", pkg)
            sRow4Id = res.getIdentifier("row4", "id", pkg)
            sPinContainerId = res.getIdentifier("pin_container", "id", pkg)
            sEmergencyCallId = res.getIdentifier("emergency_call_button", "id", pkg)
            sDeleteContainerId = res.getIdentifier("delete_container", "id", pkg)
            sSpacePatternId = res.getIdentifier("space_pattren", "id", pkg)
            sBouncerBgId = res.getIdentifier("keyguard_bouncer_bg", "id", pkg)
            sRowsLayoutWidthDimenId = res.getIdentifier("miui_keyguard_pin_view_rows_layout_width", "dimen", pkg)

            val actionNames = arrayOf(
                "delete_button", "zero_delete_button",
                "cancel_button", "zero_cancel_button",
                "emergency_call_button"
            )
            sActionBtnIds = actionNames.map { res.getIdentifier(it, "id", pkg) }.filter { it != 0 }.toIntArray()

            val cancelNames = arrayOf("delete_button", "cancel_button", "zero_delete_button", "zero_cancel_button")
            sCancelIds = cancelNames.map { res.getIdentifier(it, "id", pkg) }.filter { it != 0 }.toIntArray()

            val numNames = arrayOf("key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key0")
            sNumKeyIds = numNames.map { res.getIdentifier(it, "id", pkg) }.filter { it != 0 }.toIntArray()

            sResourceIdsInitialized = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize cached resource IDs", t)
        }
    }

    private fun getBaseTranslationX(view: View, cfg: LockScreenKeypadConfig, density: Float): Float {
        val id = view.id
        if (id != 0) {
            if (id == sEmergencyCallId) return -cfg.col1Offset * density
            if (id == sDeleteContainerId) return cfg.col3Offset * density
            for (cid in sCancelIds) {
                if (id == cid) return cfg.col3Offset * density
            }
        }
        val parent = view.parent as? ViewGroup ?: return 0f
        val count = parent.childCount
        val idx = parent.indexOfChild(view)
        return when {
            count == 3 && idx == 0 -> -cfg.col1Offset * density
            count == 3 && idx == 2 -> cfg.col3Offset * density
            count == 2 && idx == 0 -> -cfg.col1Offset * density
            count == 2 && idx == 1 -> cfg.col3Offset * density
            else -> 0f
        }
    }

    private var cachedDigitField: Field? = null
    private var cachedKlondikeField: Field? = null
    private var hasCachedKeyFields = false

    private fun ensureCachedKeyFields(keyClass: Class<*>) {
        if (hasCachedKeyFields) return
        hasCachedKeyFields = true
        try {
            cachedDigitField = keyClass.getDeclaredField("mDigitText").apply { isAccessible = true }
        } catch (_: Throwable) {}
        try {
            cachedKlondikeField = keyClass.getDeclaredField("mKlondikeText").apply { isAccessible = true }
        } catch (_: Throwable) {}
    }

    private var cachedSetAnimEnabledMethod: Method? = null
    private var hasCachedSetAnimMethod = false

    private fun ensureCachedSetAnimMethod(keyClass: Class<*>) {
        if (hasCachedSetAnimMethod) return
        hasCachedSetAnimMethod = true
        try {
            cachedSetAnimEnabledMethod = keyClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (_: Throwable) {}
    }

    private var cachedKeysAnimStartStateField: Field? = null
    private var cachedDisappearRunnableField: Field? = null
    private var cachedInitAppearAnimedField: Field? = null
    private var cachedAppearAnimatingField: Field? = null
    private var hasCachedPinViewFields = false

    private fun ensureCachedPinViewFields(pinViewClass: Class<*>) {
        if (hasCachedPinViewFields) return
        hasCachedPinViewFields = true
        try {
            cachedKeysAnimStartStateField = pinViewClass.getField("keysAnimStartState").apply { isAccessible = true }
        } catch (_: Throwable) {
            try {
                cachedKeysAnimStartStateField = pinViewClass.getDeclaredField("keysAnimStartState").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        try {
            cachedInitAppearAnimedField = pinViewClass.getField("initAppearAnimed").apply { isAccessible = true }
        } catch (_: Throwable) {
            try {
                cachedInitAppearAnimedField = pinViewClass.getDeclaredField("initAppearAnimed").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        try {
            cachedAppearAnimatingField = pinViewClass.getDeclaredField("mAppearAnimating").apply { isAccessible = true }
        } catch (_: Throwable) {}
        try {
            cachedDisappearRunnableField = pinViewClass.getDeclaredField("mDisappearAnimationFinishRunnable").apply { isAccessible = true }
        } catch (_: Throwable) {}
    }

    private fun startIos26RadialBloomAnimation(
        pinView: View,
        cfg: LockScreenKeypadConfig,
        density: Float
    ) {
        val ctx = pinView.context
        ensureResourceIds(ctx)

        val col1TransX = -cfg.col1Offset * density
        val col3TransX = cfg.col3Offset * density

        // Position emergency and delete buttons at their resting places without blooming
        if (sEmergencyCallId != 0) {
            val emerView = pinView.findViewById<View>(sEmergencyCallId)
            emerView?.animate()?.cancel()
            emerView?.translationX = col1TransX
            emerView?.translationY = 0f
            emerView?.scaleX = 1f
            emerView?.scaleY = 1f
            emerView?.alpha = 1f
        }
        if (sDeleteContainerId != 0) {
            val delCont = pinView.findViewById<View>(sDeleteContainerId)
            delCont?.animate()?.cancel()
            delCont?.translationX = col3TransX
            delCont?.translationY = 0f
            delCont?.scaleX = 1f
            delCont?.scaleY = 1f
            delCont?.alpha = 1f
        }
        for (delId in sCancelIds) {
            val delView = pinView.findViewById<View>(delId)
            delView?.animate()?.cancel()
            delView?.translationX = col3TransX
            delView?.translationY = 0f
            delView?.scaleX = 1f
            delView?.scaleY = 1f
            delView?.alpha = 1f
        }

        // Dynamically discover numeric keys in the 4x3 matrix from row ViewGroups
        val rowIds = intArrayOf(sRow1Id, sRow2Id, sRow3Id, sRow4Id)
        val matrix = Array(4) { arrayOfNulls<View>(3) }

        for (r in 0 until 4) {
            val rId = rowIds[r]
            if (rId == 0) continue
            val rowGroup = pinView.findViewById<ViewGroup>(rId) ?: continue
            val childCount = rowGroup.childCount
            if (r < 3) {
                // Rows 0, 1, 2 correspond to row1, row2, row3 in the PIN pad
                for (c in 0 until min(3, childCount)) {
                    val child = rowGroup.getChildAt(c)
                    if (child != null && child.id != sEmergencyCallId && child.id != sDeleteContainerId) {
                        matrix[r][c] = child
                    }
                }
            } else {
                // Row 3 corresponds to row4 (bottom row with center key)
                for (c in 0 until childCount) {
                    val child = rowGroup.getChildAt(c) ?: continue
                    if (child.id != sEmergencyCallId && child.id != sDeleteContainerId) {
                        var isCancel = false
                        for (cid in sCancelIds) {
                            if (child.id == cid) { isCancel = true; break }
                        }
                        if (!isCancel) {
                            var isNumKey = false
                            for (nid in sNumKeyIds) {
                                if (child.id == nid) { isNumKey = true; break }
                            }
                            if (isNumKey || child.background is LiquidGlassKeyOptimizedDrawable || child.javaClass.name.contains("NumPadKey")) {
                                matrix[3][1] = child
                                break
                            }
                        }
                    }
                }
            }
        }

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

        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val keyView = matrix[row][col] ?: continue
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

                // Calibrated delay per physical matrix position
                val delay = cfg.getPositionDelay(row, col)

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
                            // Center button (row 1, col 1): isotropic breathing bloom
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
        sModule = module
        // 1. Hook NumPadKey touch events for Liquid Glass 2.0 Press Optics & Jelly Physics (HyperOS 3 & HyperOS 4)
        val numPadClassNames = listOf(
            "com.android.keyguard.widget.MiuiNumPadKey",
            "com.android.keyguard.NumPadKey"
        )
        for (numPadClassName in numPadClassNames) {
            try {
                val numPadKeyClass = Class.forName(numPadClassName, false, classLoader)
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

                            if (!cfg.enabled) {
                                return chain.proceed()
                            }

                            if (view != null && event != null) {
                                if (!isNormalPinPadKey(view)) {
                                    return chain.proceed()
                                }
                                val liquidDrawable = view.background as? LiquidGlassKeyOptimizedDrawable
                                val density = view.context.resources.displayMetrics.density
                                val baseTransX = getBaseTranslationX(view, cfg, density)

                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        registerSettingsReceiver(view.context)
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

                                        // Zero-lag iOS-calibrated tactile expansion (150ms smooth curve)
                                        view.animate()
                                            .scaleX(cfg.pressLiquidScale)
                                            .scaleY(cfg.pressLiquidScale)
                                            .setDuration(150L)
                                            .setInterpolator(PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f))
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

                                            // Configurable, authentic iOS rubber-band stretch threshold
                                            val maxOffset = minDim * cfg.dragMaxThreshold.coerceIn(0.10f, 0.40f)
                                            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                            val jellyTx: Float
                                            val jellyTy: Float
                                            val stretchRatio: Float
                                            val dirX: Float
                                            val dirY: Float

                                            if (dist > 0.001f) {
                                                dirX = dx / dist
                                                dirY = dy / dist

                                                // Authentic iOS Non-Linear Rubber-Band Model:
                                                // k0 directly controls tactile stiffness on pixel 1 (~20% of finger displacement).
                                                // p governs the non-linear curvature / progressive resistance ramp.
                                                // The displacement smoothly and asymptotically approaches maxOffset without peaking too fast.
                                                val k0 = cfg.dragInitialStiffness.coerceIn(0.10f, 0.60f).toDouble()
                                                val p = cfg.dragResistancePower.coerceIn(1.0f, 3.5f).toDouble()
                                                val xNorm = (k0 * dist.toDouble()) / maxOffset.toDouble()
                                                val denom = Math.pow(1.0 + Math.pow(xNorm, p), 1.0 / p)
                                                val disp = (maxOffset * (xNorm / denom)).toFloat().coerceAtMost(maxOffset)

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
                                            val compAmount = stretchAmount / (1f + stretchAmount)

                                            val baseScale = cfg.pressLiquidScale

                                            val dirX2 = dirX * dirX
                                            val dirY2 = dirY * dirY
                                            val liquidScaleX = baseScale * (1f + stretchAmount * dirX2 - compAmount * dirY2)
                                            val liquidScaleY = baseScale * (1f + stretchAmount * dirY2 - compAmount * dirX2)

                                            view.translationX = baseTransX + jellyTx
                                            view.translationY = jellyTy
                                            view.scaleX = liquidScaleX.coerceIn(0.60f, 1.80f)
                                            view.scaleY = liquidScaleY.coerceIn(0.60f, 1.80f)
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
                                        val maxOffset = minDim * cfg.dragMaxThreshold.coerceIn(0.10f, 0.40f)
                                        val releaseNorm = (releaseDist / maxOffset).coerceIn(0f, 1f)

                                        if (releaseDist < 1.5f * density) {
                                            view.animate()
                                                .translationX(baseTransX)
                                                .translationY(0f)
                                                .translationZ(0f)
                                                .scaleX(1.0f)
                                                .scaleY(1.0f)
                                                .setDuration(180L)
                                                .setInterpolator(PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f))
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
                                            val durationMs = cfg.releaseDuration.toLong().coerceIn(150L, 800L)
                                            val squishAmp = cfg.releaseSquishAmp.coerceIn(0.0f, 0.70f)
                                            val overshootTension = cfg.releaseOvershoot.coerceIn(1.0f, 3.5f)

                                            val releaseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                                                duration = durationMs
                                                interpolator = LinearInterpolator()

                                                addUpdateListener { va ->
                                                    val t = va.animatedFraction

                                                    // 1. Directional position snapback along vector (uX, uY)
                                                    val omega = (Math.PI * overshootTension).toDouble()
                                                    val dampingCoeff = 4.2
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
                                                    val scaleBase = 1.0f + (cfg.pressLiquidScale - 1.0f) * Math.exp(-4.0 * t).toFloat()
                                                    val scaleX = scaleBase * (1.0f + diffParallel * uX2 - diffPerp * uY2)
                                                    val scaleY = scaleBase * (1.0f + diffParallel * uY2 - diffPerp * uX2)

                                                    view.translationX = currentTx
                                                    view.translationY = currentTy
                                                    view.scaleX = scaleX.coerceIn(0.60f, 1.80f)
                                                    view.scaleY = scaleY.coerceIn(0.60f, 1.80f)
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
            Log.d(TAG, "Successfully hooked NumPadKey touch events on $numPadClassName")
        } catch (ignored: ClassNotFoundException) {
            // Expected when class does not exist in the current OS version
        } catch (t: Throwable) {
            Log.e(TAG, "NumPadKey touch hook error on $numPadClassName", t)
        }
    }

        // 2. Hook KeyguardPINView for Liquid Glass 2.0 Button Injection, Sizing & Layout (HyperOS 3 & HyperOS 4)

            val styleKeyAsLiquidGlass: (View?) -> Unit = { keyView ->
                val cfg = LockScreenConfigHolder.keypad
                if (keyView != null && cfg.enabled) {
                    try {
                        val ctx = keyView.context
                        val density = ctx.resources.displayMetrics.density

                        ensureCachedSetAnimMethod(keyView.javaClass)
                        try {
                            cachedSetAnimEnabledMethod?.invoke(keyView, false)
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

                        ensureCachedKeyFields(keyView.javaClass)

                        // Letters text (Klondike)
                        try {
                            val klondikeText = cachedKlondikeField?.get(keyView) as? TextView
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
                            val digitText = cachedDigitField?.get(keyView) as? TextView
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
                val cfg = LockScreenConfigHolder.keypad
                if (pinView != null && cfg.enabled) {
                    try {
                        val ctx = pinView.context
                        ensureResourceIds(ctx)
                        val density = ctx.resources.displayMetrics.density

                        // Ensure keyguard_bouncer_bg does not show stale blurred preview bitmap
                        val root = pinView.rootView
                        if (root != null && sBouncerBgId != 0) {
                            val iv = root.findViewById<View>(sBouncerBgId) as? ImageView
                            iv?.setImageDrawable(null)
                            iv?.visibility = View.GONE
                        }

                        // Ensure parent ViewGroup doesn't clip when lifting or expanding
                        (pinView as? ViewGroup)?.clipChildren = false
                        (pinView as? ViewGroup)?.clipToPadding = false

                        // 1. Container Horizontal Width, Column Spacing & Vertical Lift
                        val pinContainer = if (sPinContainerId != 0) pinView.findViewById<ViewGroup>(sPinContainerId) else null
                        if (pinContainer != null) {
                            pinContainer.clipChildren = false
                            pinContainer.clipToPadding = false

                            val defaultWidthPx = if (sRowsLayoutWidthDimenId != 0) ctx.resources.getDimensionPixelOffset(sRowsLayoutWidthDimenId) else (310 * density).toInt()
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
                        val rowStep = cfg.rowSpacing * density
                        if (sRow1Id != 0) pinView.findViewById<View>(sRow1Id)?.translationY = -3f * rowStep
                        if (sRow2Id != 0) pinView.findViewById<View>(sRow2Id)?.translationY = -2f * rowStep
                        if (sRow3Id != 0) pinView.findViewById<View>(sRow3Id)?.translationY = -1f * rowStep
                        if (sRow4Id != 0) pinView.findViewById<View>(sRow4Id)?.translationY = 0f
                        if (sRow0Id != 0) pinView.findViewById<View>(sRow0Id)?.translationY = cfg.row0Offset * density

                        // 3. Matrix-Based Column Horizontal Offsets (translationX)
                        val col1TransX = -cfg.col1Offset * density
                        val col3TransX = cfg.col3Offset * density

                        val rowIds = intArrayOf(sRow1Id, sRow2Id, sRow3Id, sRow4Id)
                        for (rId in rowIds) {
                            if (rId == 0) continue
                            val rowGroup = pinView.findViewById<ViewGroup>(rId) ?: continue
                            val childCount = rowGroup.childCount
                            for (c in 0 until childCount) {
                                val child = rowGroup.getChildAt(c) ?: continue
                                val transX = when {
                                    childCount == 3 && c == 0 -> col1TransX
                                    childCount == 3 && c == 2 -> col3TransX
                                    childCount == 2 && c == 0 -> col1TransX
                                    childCount == 2 && c == 1 -> col3TransX
                                    else -> 0f
                                }
                                child.translationX = transX
                            }
                        }

                        if (sEmergencyCallId != 0) {
                            pinView.findViewById<View>(sEmergencyCallId)?.translationX = col1TransX
                        }
                        if (sDeleteContainerId != 0) {
                            pinView.findViewById<View>(sDeleteContainerId)?.translationX = col3TransX
                        }
                        for (cid in sCancelIds) {
                            pinView.findViewById<View>(cid)?.translationX = col3TransX
                        }

                        // 4. Numeric Digit Keys Styling
                        for (id in sNumKeyIds) {
                            if (id != 0) {
                                styleKeyAsLiquidGlass(pinView.findViewById(id))
                            }
                        }

                        // 5. Cleanly style Action Buttons without breaking LinearLayout weights
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

                        if (sDeleteContainerId != 0) pinView.findViewById<View>(sDeleteContainerId)?.background = null

                        for (id in sActionBtnIds) {
                            styleActionButton(pinView.findViewById(id))
                        }

                        // 6. Unclip All Rows
                        for (rId in intArrayOf(sRow0Id, sRow1Id, sRow2Id, sRow3Id, sRow4Id)) {
                            if (rId != 0) {
                                (pinView.findViewById<View>(rId) as? ViewGroup)?.clipChildren = false
                                (pinView.findViewById<View>(rId) as? ViewGroup)?.clipToPadding = false
                            }
                        }

                        // 7. Direct Touch Routing for Elevated / Negative-Translated Rows
                        val activeNumKeys = mutableListOf<View>()
                        for (id in sNumKeyIds) {
                            if (id != 0) {
                                val key = pinView.findViewById<View>(id)
                                if (key != null) activeNumKeys.add(key)
                            }
                        }
                        val attachKeyTouchRouter: (View?) -> Unit = { targetView ->
                            if (targetView != null) {
                                var activeForwardKey: View? = null
                                val tempLoc = IntArray(2)

                                targetView.setOnTouchListener { _, ev ->
                                    when (ev.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            activeForwardKey = null
                                            for (key in activeNumKeys) {
                                                if (key.visibility == View.VISIBLE) {
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

                        if (sSpacePatternId != 0) attachKeyTouchRouter(pinView.findViewById(sSpacePatternId))
                        if (sRow0Id != 0) attachKeyTouchRouter(pinView.findViewById(sRow0Id))
                        attachKeyTouchRouter(pinView)
                    } catch (_: Throwable) {}
                }
            }

            val pinViewClassNames = listOf(
                "com.android.keyguard.widget.MiuiKeyguardPINView",
                "com.android.keyguard.KeyguardPINView",
                "com.android.keyguard.tinySecurity.TinyKeyguardPINView"
            )
            for (pinViewClassName in pinViewClassNames) {
                try {
                    val pinViewClass = Class.forName(pinViewClassName, false, classLoader)
                    for (m: Method in pinViewClass.declaredMethods) {
                if (m.name == "onFinishInflate" || m.name == "updatePositionForFod" || m.name == "updateLayoutParams" || m.name == "onAttachedToWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            val cfg = LockScreenConfigHolder.keypad
                            if (!cfg.enabled) {
                                return result
                            }
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

                            // Ensure pinView itself is visible and fully opaque
                            pinView.visibility = View.VISIBLE
                            pinView.alpha = 1f

                            try {
                                applyPinLayoutAndStyles(pinView)
                            } catch (_: Throwable) {}

                            val ctx = pinView.context
                            ensureResourceIds(ctx)
                            val density = ctx.resources.displayMetrics.density

                            try {
                                val pairs = Array(4) { Array(3) { android.util.Pair(0, 0) } }
                                ensureCachedPinViewFields(pinViewObj.javaClass)
                                cachedKeysAnimStartStateField?.set(pinViewObj, pairs)
                                cachedInitAppearAnimedField?.setBoolean(pinViewObj, true)
                            } catch (_: Throwable) {}

                            startIos26RadialBloomAnimation(pinView, cfg, density)

                            if (sRow0Id != 0) {
                                val row0 = pinView.findViewById<View>(sRow0Id)
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

                            val bloomDuration = cfg.iosBloomDuration.toLong().coerceIn(300L, 1000L)
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    ensureCachedPinViewFields(pinViewObj.javaClass)
                                    cachedAppearAnimatingField?.setBoolean(pinViewObj, false)
                                } catch (_: Throwable) {}
                            }, bloomDuration + 50L)

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

                            val rawArgs = chain.args
                            val runnableArg = if (rawArgs is List<*>) rawArgs.firstOrNull() as? Runnable else (rawArgs as? Array<*>)?.firstOrNull() as? Runnable
                            val finishRunnable = runnableArg ?: (try {
                                ensureCachedPinViewFields(pinViewObj.javaClass)
                                cachedDisappearRunnableField?.get(pinViewObj) as? Runnable
                            } catch (_: Throwable) { null })

                            for (anim in appearAnimators.values) {
                                anim.cancel()
                            }
                            appearAnimators.clear()
                            for (anim in releaseAnimators.values) {
                                anim.cancel()
                            }
                            releaseAnimators.clear()

                            val ctx = pinView.context
                            ensureResourceIds(ctx)
                            val density = ctx.resources.displayMetrics.density

                            if (sEmergencyCallId != 0) {
                                pinView.findViewById<View>(sEmergencyCallId)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                            }
                            if (sDeleteContainerId != 0) {
                                pinView.findViewById<View>(sDeleteContainerId)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                            }
                            for (id in sCancelIds) {
                                pinView.findViewById<View>(id)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                            }

                            for (id in sNumKeyIds) {
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

                            if (sRow0Id != 0) {
                                pinView.findViewById<View>(sRow0Id)?.animate()?.alpha(0f)?.setDuration(120L)?.start()
                            }

                            try {
                                LockScreenSheetHook.startDismissReturnAnimation()
                            } catch (_: Throwable) {}

                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    finishRunnable?.run()
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
                                    ensureResourceIds(pinView.context)
                                    for (id in sNumKeyIds) {
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
            Log.d(TAG, "Successfully hooked KeyguardPINView on $pinViewClassName")
        } catch (ignored: ClassNotFoundException) {
            // Expected when class does not exist in the current OS version
        } catch (t: Throwable) {
            Log.e(TAG, "KeyguardPINView Liquid Glass hook error on $pinViewClassName", t)
        }
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
