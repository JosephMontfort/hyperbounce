package dev.joseph.hyperbounce.lockscreen

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
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import dev.joseph.hyperbounce.lockscreen.glass.LiquidGlassKeyDrawable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Feature 2: 100% Real-Time Liquid Glass 2.0 Lockscreen PIN Pad Engine.
 * Features Apple-grade Liquid Glass circular lens optics, custom cascade entry animations,
 * dynamic layout spacing hacks responsive to companion app sliders,
 * iOS-calibrated tactile compression, and AndroidLiquidGlass volume-preserved jelly stretch.
 */
object KeypadPhysicsHook {

    private const val TAG = "HyperBounce_Keypad"

    private class KeypadTouchState(
        val downRawX: Float,
        val downRawY: Float,
        var isDragging: Boolean = false
    )

    private val touchStates = WeakHashMap<View, KeypadTouchState>()

    private fun getBaseTranslationX(view: View, cfg: KeypadPhysicsConfig, density: Float): Float {
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
                                val liquidDrawable = view.background as? LiquidGlassKeyDrawable
                                val density = view.context.resources.displayMetrics.density
                                val baseTransX = getBaseTranslationX(view, cfg, density)

                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        view.animate().cancel()
                                        touchStates[view] = KeypadTouchState(
                                            downRawX = event.rawX,
                                            downRawY = event.rawY,
                                            isDragging = false
                                        )

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

                                        val slop = 2.5f * density
                                        if (distSq > slop * slop || state?.isDragging == true) {
                                            state?.isDragging = true
                                            view.animate().cancel()

                                            val w = view.width.toFloat().coerceAtLeast(1f)
                                            val h = view.height.toFloat().coerceAtLeast(1f)
                                            val minDim = min(w, h)
                                            val maxDim = max(w, h)

                                            // 1. AndroidLiquidGlass rubberband displacement (tanh tension)
                                            val maxOffset = minDim * 0.40f
                                            val initialDerivative = 0.08f
                                            val jellyTx = (maxOffset * tanh(initialDerivative * dx / maxOffset)).toFloat()
                                            val jellyTy = (maxOffset * tanh(initialDerivative * dy / maxOffset)).toFloat()

                                            // 2. AndroidLiquidGlass volume-preserving anisotropic stretch along drag angle
                                            val maxDragScale = 8f * density / h
                                            val offsetAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()

                                            val stretchX = maxDragScale * abs(cos(offsetAngle) * (dx / maxDim)) * (w / h).coerceAtMost(1f)
                                            val stretchY = maxDragScale * abs(sin(offsetAngle) * (dy / maxDim)) * (h / w).coerceAtMost(1f)

                                            // 3. Poisson volume preservation: perpendicular compression
                                            val volumeCompX = -0.35f * stretchY
                                            val volumeCompY = -0.35f * stretchX

                                            view.translationX = baseTransX + jellyTx
                                            view.translationY = jellyTy
                                            view.scaleX = (cfg.pressLiquidScale + stretchX + volumeCompX).coerceIn(0.70f, 1.35f)
                                            view.scaleY = (cfg.pressLiquidScale + stretchY + volumeCompY).coerceIn(0.70f, 1.35f)
                                        }

                                        liquidDrawable?.onTouchMove(event.x, event.y)
                                        view.invalidate()
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        touchStates.remove(view)
                                        liquidDrawable?.onTouchUp()

                                        view.animate().cancel()
                                        view.animate()
                                            .translationX(baseTransX)
                                            .translationY(0f)
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(260L)
                                            .setInterpolator(OvershootInterpolator(1.35f))
                                            .setUpdateListener { view.invalidate() }
                                            .start()
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

                        // Set Liquid Glass 2.0 Circular Button Lens Background
                        if (keyView.background !is LiquidGlassKeyDrawable) {
                            keyView.background = LiquidGlassKeyDrawable(keyView, density)
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

                        // Digit text (Numbers)
                        try {
                            val digitTextField: Field = keyView.javaClass.getDeclaredField("mDigitText")
                            digitTextField.isAccessible = true
                            val digitText = digitTextField.get(keyView) as? TextView
                            if (digitText != null) {
                                digitText.setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.digitTextSize.coerceAtLeast(18f))
                                digitText.setTextColor(0xFFFFFFFF.toInt())
                                digitText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                                digitText.gravity = Gravity.CENTER
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

                            val ctx = pinView.context
                            val density = ctx.resources.displayMetrics.density

                            try {
                                val pairs = Array(4) { Array(3) { Pair(0, 0) } }
                                val keysAnimField = try { pinViewObj.javaClass.getField("keysAnimStartState") } catch (_: Throwable) { null }
                                keysAnimField?.isAccessible = true
                                keysAnimField?.set(pinViewObj, pairs)

                                val appearAnimField = try { pinViewObj.javaClass.getField("mAppearAnimating") } catch (_: Throwable) { null }
                                appearAnimField?.isAccessible = true
                                appearAnimField?.setBoolean(pinViewObj, true)

                                val initAppearField = try { pinViewObj.javaClass.getField("initAppearAnimed") } catch (_: Throwable) { null }
                                initAppearField?.isAccessible = true
                                initAppearField?.setBoolean(pinViewObj, true)

                                val canceledField = try { pinViewObj.javaClass.getField("appearAnimCanceled") } catch (_: Throwable) { null }
                                canceledField?.isAccessible = true
                                canceledField?.setBoolean(pinViewObj, false)
                            } catch (_: Throwable) {}

                            val runCustomAppear = Runnable {
                                try {
                                    pinView.alpha = 1.0f
                                    applyPinLayoutAndStyles(pinView)

                                    // 1. PIN dots fade in smoothly
                                    val row0Id = ctx.resources.getIdentifier("row0", "id", "com.android.systemui")
                                    if (row0Id != 0) {
                                        val row0 = pinView.findViewById<View>(row0Id)
                                        if (row0 != null) {
                                            row0.alpha = 0.0f
                                            row0.translationY = (cfg.row0Offset - 15f) * density
                                            row0.animate()
                                                .alpha(1.0f)
                                                .translationY(cfg.row0Offset * density)
                                                .setDuration(340)
                                                .setInterpolator(DecelerateInterpolator())
                                                .start()
                                        }
                                    }

                                    // 2. Custom Cascade Animation Engine for Keys
                                    val keyNames = arrayOf(
                                        arrayOf("key1", "key2", "key3"),
                                        arrayOf("key4", "key5", "key6"),
                                        arrayOf("key7", "key8", "key9"),
                                        arrayOf("emergency_call_button", "key0", "delete_button")
                                    )

                                    val startTY = cfg.entryTranslationY * density
                                    val startScale = cfg.entryInitialScale
                                    val duration = cfg.entryDuration.toLong().coerceAtLeast(100L)
                                    val overshoot = cfg.entryOvershoot.coerceAtLeast(0.5f)

                                    val col1TransX = -cfg.col1Offset * density
                                    val col3TransX = cfg.col3Offset * density

                                    for (row in 0..3) {
                                        for (col in 0..2) {
                                            val keyName = keyNames[row][col]
                                            val resId = ctx.resources.getIdentifier(keyName, "id", "com.android.systemui")
                                            val keyView = if (resId != 0) pinView.findViewById<View>(resId) else null

                                            if (keyView != null) {
                                                val delay = cfg.getKeyDelay(keyName, row, col)
                                                val targetTransX = when (col) {
                                                    0 -> col1TransX
                                                    2 -> col3TransX
                                                    else -> 0f
                                                }

                                                keyView.translationY = startTY
                                                keyView.translationX = targetTransX
                                                keyView.scaleX = startScale
                                                keyView.scaleY = startScale
                                                keyView.alpha = 0f

                                                keyView.animate()
                                                    .translationY(0f)
                                                    .translationX(targetTransX)
                                                    .scaleX(1f)
                                                    .scaleY(1f)
                                                    .alpha(1f)
                                                    .setStartDelay(delay)
                                                    .setDuration(duration)
                                                    .setInterpolator(OvershootInterpolator(overshoot))
                                                    .setUpdateListener {
                                                        keyView.invalidate()
                                                    }
                                                    .start()
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Custom cascade appear animation error", t)
                                }
                            }

                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                runCustomAppear.run()
                            } else {
                                Handler(Looper.getMainLooper()).post(runCustomAppear)
                            }

                            return null
                        }
                    })
                } else if (m.name == "startBackAnimation") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val pinView = chain.thisObject as? View
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                            val callback = if (args.isNotEmpty() && args[0] is Runnable) args[0] as Runnable else null

                            try {
                                pinView?.animate()
                                    ?.alpha(0.0f)
                                    ?.setDuration(220)
                                    ?.setInterpolator(AccelerateInterpolator(1.5f))
                                    ?.withEndAction {
                                        callback?.run()
                                    }
                                    ?.start()
                            } catch (_: Throwable) {
                                callback?.run()
                            }
                            return null
                        }
                    })
                } else if (m.name == "onDetachedFromWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val pinView = chain.thisObject as? View
                                if (pinView != null) {
                                    val ctx = pinView.context
                                    for (name in arrayOf("key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key0")) {
                                        val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                                        if (id != 0) {
                                            (pinView.findViewById<View>(id)?.background as? LiquidGlassKeyDrawable)?.release()
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
            Log.e(TAG, "MiuiKeyguardPINView hook error", t)
        }
    }
}
