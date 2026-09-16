package dev.joseph.hyperbounce.lockscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
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

    private fun getBaseTranslationX(view: View, cfg: LockScreenKeypadConfig, density: Float): Float {
        val entryName = try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            ""
        }
        return when (entryName) {
            "key1", "key4", "key7" -> -cfg.col1Offset * density
            "key3", "key6", "key9" -> cfg.col3Offset * density
            else -> 0f
        }
    }

    /**
     * Computes the normalized inertia-based liquid volume deformation delta_y for cascade entrance.
     * Continuous C1-smoothstep cubic splines guarantee zero-derivative knot transitions:
     * - In-flight (u in [0.00, 0.44]): vertical elongation along motion trajectory (delta_y > 0)
     * - Impact landing (u in [0.44, 0.70]): momentum squishes bubble flat horizontally (delta_y < 0)
     * - Surface tension rebound (u in [0.70, 0.86]): elastic rebound (delta_y > 0)
     * - Damped settle (u in [0.86, 1.00]): gentle dying wobble settling to 0
     */
    private fun computeLiquidSquishDelta(u: Float): Float {
        val knots = floatArrayOf(0.00f, 0.22f, 0.44f, 0.58f, 0.74f, 0.88f, 1.00f)
        val values = floatArrayOf(0.00f, 0.18f, 0.00f, -0.17f, 0.07f, -0.02f, 0.00f)

        if (u <= 0f) return values[0]
        if (u >= 1f) return values[values.size - 1]

        var i = 0
        while (i < knots.size - 1 && u > knots[i + 1]) {
            i++
        }
        val t0 = knots[i]
        val t1 = knots[i + 1]
        val p = (u - t0) / (t1 - t0)
        // Smoothstep Hermite curve: s(0)=0, s(1)=1, s'(0)=0, s'(1)=0
        val s = p * p * (3f - 2f * p)
        return values[i] + s * (values[i + 1] - values[i])
    }

    private fun computeEntranceTranslationY(u: Float, transY: Float): Float {
        return if (u < 0.54f) {
            val p = u / 0.54f
            val s = 1f - (1f - p) * (1f - p)
            transY * (1f - s)
        } else if (u < 0.78f) {
            val p = (u - 0.54f) / 0.24f
            -0.055f * transY * sin((p * Math.PI).toDouble()).toFloat() * (1f - p)
        } else {
            0f
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

                                            // Maintain topmost Z-order and unclipping during active dragging
                                            view.translationZ = 120f * density
                                            (view.parent as? View)?.translationZ = 120f * density
                                            (view.parent as? ViewGroup)?.clipChildren = false
                                            (view.parent as? ViewGroup)?.clipToPadding = false

                                            val w = view.width.toFloat().coerceAtLeast(1f)
                                            val h = view.height.toFloat().coerceAtLeast(1f)
                                            val minDim = min(w, h)

                                            // 1. Featherlight liquid bubble displacement (threshold kept as is: maxOffset = minDim * 0.40f)
                                            val maxOffset = minDim * 0.40f
                                            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                            val jellyTx: Float
                                            val jellyTy: Float
                                            val stretchRatio: Float
                                            val dirX: Float
                                            val dirY: Float

                                            if (dist > 0.001f) {
                                                dirX = dx / dist
                                                dirY = dy / dist
                                                // Featherlight, natural fluid response (initialDerivative = 0.72f):
                                                // Instant tracking with zero sluggishness, smoothly capping at maxOffset
                                                val disp = (maxOffset * tanh((0.72f * dist / maxOffset).toDouble())).toFloat()
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

                                            // 2. Physical Volume-Preserved Liquid Bubble Stretch along drag angle
                                            // As the bubble stretches along (dirX, dirY), it elongates longitudinally by (1 + stretchAmount)
                                            // and contracts transversely by (1 / (1 + stretchAmount)) to strictly preserve 2D liquid volume.
                                            val maxStretchFactor = 0.28f
                                            val stretchAmount = stretchRatio * maxStretchFactor
                                            val compAmount = 1f - (1f / (1f + stretchAmount))

                                            val baseScale = cfg.pressLiquidScale + (1.0f - cfg.pressLiquidScale) * (stretchRatio * 0.45f)

                                            val dirX2 = dirX * dirX
                                            val dirY2 = dirY * dirY
                                            val liquidScaleX = baseScale * (1f + stretchAmount * dirX2 - compAmount * dirY2)
                                            val liquidScaleY = baseScale * (1f + stretchAmount * dirY2 - compAmount * dirX2)

                                            view.translationX = baseTransX + jellyTx
                                            view.translationY = jellyTy
                                            view.scaleX = liquidScaleX.coerceIn(0.65f, 1.45f)
                                            view.scaleY = liquidScaleY.coerceIn(0.65f, 1.45f)
                                        }

                                        liquidDrawable?.onTouchMove(event.x, event.y)
                                        view.invalidate()
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        touchStates.remove(view)
                                        liquidDrawable?.onTouchUp()

                                        view.animate().cancel()
                                        val rowParent = view.parent as? View

                                        view.animate()
                                            .translationX(baseTransX)
                                            .translationY(0f)
                                            .translationZ(0f)
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setStartDelay(0L)
                                            .setDuration(280L)
                                            .setInterpolator(OvershootInterpolator(2.2f))
                                            .setUpdateListener { view.invalidate() }
                                            .withEndAction {
                                                view.translationZ = 0f
                                                rowParent?.translationZ = 0f
                                            }
                                            .start()

                                        rowParent?.animate()
                                            ?.translationZ(0f)
                                            ?.setDuration(280L)
                                            ?.start()
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
                    // Custom Cascade Glide & Staggered Sequential Entrance Animation Engine
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
                                val pairs = Array(4) { Array(3) { Pair(0, 0) } }
                                val keysAnimField = try { pinViewObj.javaClass.getField("keysAnimStartState") } catch (_: Throwable) { null }
                                if (keysAnimField != null) {
                                    keysAnimField.isAccessible = true
                                    keysAnimField.set(pinViewObj, pairs)
                                }
                            } catch (_: Throwable) {}

                            val keys = arrayOf(
                                arrayOf("key1", "key2", "key3"),
                                arrayOf("key4", "key5", "key6"),
                                arrayOf("key7", "key8", "key9"),
                                arrayOf("emergency_call_button", "key0", "delete_button")
                            )

                            val animDuration = cfg.entryDuration.toLong().coerceAtLeast(150L)
                            val transY = cfg.entryTranslationY * density
                            val initialScale = cfg.entryInitialScale.coerceIn(0.1f, 1.0f)
                            val col1TransX = -cfg.col1Offset * density
                            val col3TransX = cfg.col3Offset * density
                            val entranceDuration = (animDuration + 60L).coerceAtLeast(460L)

                            for (row in keys.indices) {
                                for (col in keys[row].indices) {
                                    val name = keys[row][col]
                                    var id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                                    if (id == 0 && name == "delete_button") {
                                        val fallbackNames = arrayOf("cancel_button", "zero_delete_button", "zero_cancel_button")
                                        for (fb in fallbackNames) {
                                            val fbId = ctx.resources.getIdentifier(fb, "id", "com.android.systemui")
                                            if (fbId != 0) {
                                                id = fbId
                                                break
                                            }
                                        }
                                    }
                                    if (id == 0) continue

                                    val keyView = pinView.findViewById<View>(id) ?: continue
                                    val delay = cfg.getKeyDelay(name, row, col).coerceAtLeast(0L)

                                    val optDrawable = keyView.background as? LiquidGlassKeyOptimizedDrawable
                                    optDrawable?.startLiveTracking()

                                    val targetTransX = when (col) {
                                        0 -> col1TransX
                                        2 -> col3TransX
                                        else -> 0f
                                    }

                                    appearAnimators.remove(keyView)?.cancel()
                                    keyView.animate().cancel()
                                    keyView.alpha = 0f
                                    keyView.translationY = transY
                                    keyView.translationX = targetTransX
                                    keyView.scaleX = initialScale
                                    keyView.scaleY = initialScale

                                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = entranceDuration
                                        startDelay = delay
                                        interpolator = LinearInterpolator()

                                        addUpdateListener { va ->
                                            val u = va.animatedFraction

                                            val alpha = (u / 0.18f).coerceIn(0f, 1f)
                                            val currY = computeEntranceTranslationY(u, transY)

                                            val deltaY = computeLiquidSquishDelta(u)
                                            val squishY = (1.0f + deltaY).coerceAtLeast(0.4f)
                                            val squishX = 1.0f / squishY

                                            val scaleProg = (u / 0.20f).coerceIn(0f, 1f)
                                            val smoothBaseProg = scaleProg * scaleProg * (3f - 2f * scaleProg)
                                            val baseScale = initialScale + (1.0f - initialScale) * smoothBaseProg

                                            keyView.alpha = alpha
                                            keyView.translationY = currY
                                            keyView.translationX = targetTransX
                                            keyView.scaleX = baseScale * squishX
                                            keyView.scaleY = baseScale * squishY
                                            keyView.invalidate()
                                        }

                                        addListener(object : AnimatorListenerAdapter() {
                                            private var isCancelled = false
                                            override fun onAnimationCancel(animation: Animator) {
                                                isCancelled = true
                                                appearAnimators.remove(keyView)
                                            }
                                            override fun onAnimationEnd(animation: Animator) {
                                                if (!isCancelled) {
                                                    keyView.alpha = 1f
                                                    keyView.translationY = 0f
                                                    keyView.translationX = targetTransX
                                                    keyView.scaleX = 1f
                                                    keyView.scaleY = 1f
                                                    keyView.invalidate()
                                                }
                                                appearAnimators.remove(keyView)
                                            }
                                        })
                                    }
                                    appearAnimators[keyView] = animator
                                    animator.start()
                                }
                            }

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
