package dev.joseph.hyperbounce.lockscreen

import android.content.Context
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
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Feature 2: Fluid iOS PIN Pad Geometry & Jelly Squish Engine.
 * Hooks MiuiNumPadKey and MiuiKeyguardPINView in com.android.systemui.
 */
object KeypadPhysicsHook {

    private const val TAG = "HyperBounce_Keypad"

    class Spring(var current: Float = 1f, var target: Float = 1f, var velocity: Float = 0f) {
        fun update(stiffness: Float, damping: Float): Boolean {
            val displacement = current - target
            val acceleration = (-stiffness * displacement) + (-damping * velocity)
            velocity += acceleration * 0.016f
            current += velocity * 0.016f
            return abs(velocity) > 0.001f || abs(displacement) > 0.001f
        }

        fun snap(value: Float) {
            current = value
            target = value
            velocity = 0f
        }
    }

    class KeyState {
        val scale = Spring(1f, 1f)
        val highlight = Spring(0f, 0f)
        val dragX = Spring(0f, 0f)
        val dragY = Spring(0f, 0f)
        var touchDownX = 0f
        var touchDownY = 0f
        var isPressed = false
        var isRunning = false
    }

    private val keyStateMap = WeakHashMap<View, KeyState>()

    private fun startSpringAnimation(view: View, state: KeyState) {
        if (state.isRunning) return
        state.isRunning = true

        val springStep = object : Runnable {
            override fun run() {
                val cfg = LockScreenConfigHolder.keypad
                val stiffness = cfg.springStiffness.coerceAtLeast(5f)
                val damping = cfg.springDamping.coerceAtLeast(0.5f)

                val activeScale = state.scale.update(stiffness, damping)
                val activeHighlight = state.highlight.update(stiffness * 1.5f, damping * 1.2f)
                val activeDragX = state.dragX.update(stiffness * 0.85f, damping * 0.90f)
                val activeDragY = state.dragY.update(stiffness * 0.85f, damping * 0.90f)

                // AndroidLiquidGlass: Hyperbolic tangent elastic limits for jelly drag
                var maxOffset = min(view.width, view.height) * cfg.jellyElasticLimit.coerceAtLeast(0.10f)
                if (maxOffset <= 0f) maxOffset = 40f
                val initialDerivative = 0.25f
                val tx = (maxOffset * tanh((initialDerivative * state.dragX.current / maxOffset).toDouble())).toFloat()
                val ty = (maxOffset * tanh((initialDerivative * state.dragY.current / maxOffset).toDouble())).toFloat()
                view.translationX = tx
                view.translationY = ty

                // AndroidLiquidGlass: Anisotropic angle stretch & squash (atan2)
                val dist = hypot(state.dragX.current.toDouble(), state.dragY.current.toDouble()).toFloat()
                val offsetAngle = atan2(state.dragY.current.toDouble(), state.dragX.current.toDouble()).toFloat()
                val maxDragScale = cfg.jellyMaxStretch.coerceAtLeast(0.0f)
                val stretchMag = min(maxDragScale, (dist / maxOffset) * maxDragScale)

                val sx = state.scale.current + (stretchMag * abs(cos(offsetAngle.toDouble()))).toFloat()
                val sy = state.scale.current + (stretchMag * abs(sin(offsetAngle.toDouble()))).toFloat()
                view.scaleX = sx
                view.scaleY = sy

                val bg = view.background
                if (bg is CircleGlassDrawable) {
                    bg.highlightAlpha = state.highlight.current.coerceIn(0f, 1f)
                    view.invalidate()
                }

                if (activeScale || activeHighlight || activeDragX || activeDragY || state.isPressed) {
                    view.postOnAnimation(this)
                } else {
                    view.scaleX = state.scale.target
                    view.scaleY = state.scale.target
                    view.translationX = 0f
                    view.translationY = 0f
                    if (bg is CircleGlassDrawable) {
                        bg.highlightAlpha = state.highlight.target
                        view.invalidate()
                    }
                    state.isRunning = false
                }
            }
        }
        view.postOnAnimation(springStep)
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Instant Touch Squish on MiuiNumPadKey (Reacts Immediately on Hardware Touch Down/Up)
        try {
            val keyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
            for (m: Method in keyClass.declaredMethods) {
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

                            if (view != null && event != null) {
                                val cfg = LockScreenConfigHolder.keypad
                                if (!cfg.enabled) return chain.proceed()

                                var state = keyStateMap[view]
                                if (state == null) {
                                    state = KeyState()
                                    keyStateMap[view] = state
                                }

                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        var p = view.parent
                                        while (p != null) {
                                            p.requestDisallowInterceptTouchEvent(true)
                                            p = p.parent
                                        }
                                        try {
                                            val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                            setAnimMethod.isAccessible = true
                                            setAnimMethod.invoke(view, false)
                                        } catch (_: Throwable) {}

                                        state.touchDownX = event.x
                                        state.touchDownY = event.y
                                        state.isPressed = true
                                        state.scale.target = cfg.tapSquishScale
                                        state.highlight.target = 1.0f
                                        state.dragX.target = 0f
                                        state.dragY.target = 0f

                                        val bg = view.background
                                        if (bg is CircleGlassDrawable) {
                                            bg.touchX = event.x
                                            bg.touchY = event.y
                                        }
                                        startSpringAnimation(view, state)
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        val dx = event.x - state.touchDownX
                                        val dy = event.y - state.touchDownY
                                        state.dragX.target = dx
                                        state.dragY.target = dy

                                        val bg = view.background
                                        if (bg is CircleGlassDrawable) {
                                            bg.touchX = event.x
                                            bg.touchY = event.y
                                        }
                                        startSpringAnimation(view, state)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        state.isPressed = false
                                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                                            state.scale.current = cfg.tapSquishScale - cfg.releasePlunge
                                        }
                                        state.scale.target = 1.0f
                                        state.highlight.target = 0.0f
                                        state.dragX.target = 0f
                                        state.dragY.target = 0f
                                        startSpringAnimation(view, state)
                                    }
                                }
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MiuiNumPadKey touch squish hook error", t)
        }

        // 2. Hook MiuiKeyguardPINView for Row Spacing Hijack, Dynamic Sized Circle Glass Backgrounds, Centered Numbers & Y-Axis Glide Entry
        try {
            val pinViewClass = Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader)

            val styleKeyAsCircle: (View?) -> Unit = { keyView ->
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

                        // Set Perfect Circle Glass Background (Custom Dynamic Circle Drawable)
                        keyView.background = CircleGlassDrawable(density)
                        keyView.elevation = 6 * density

                        // Toggle klondike letters (ABC, DEF, etc.) based on config
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

                        // Center and style digit text (numbers)
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

                        // 1. Apply row translation vertical spacing from config
                        val row1Id = ctx.resources.getIdentifier("row1", "id", "com.android.systemui")
                        val row2Id = ctx.resources.getIdentifier("row2", "id", "com.android.systemui")
                        val row3Id = ctx.resources.getIdentifier("row3", "id", "com.android.systemui")
                        val row4Id = ctx.resources.getIdentifier("row4", "id", "com.android.systemui")
                        val row0Id = ctx.resources.getIdentifier("row0", "id", "com.android.systemui")

                        if (row1Id != 0) pinView.findViewById<View>(row1Id)?.translationY = cfg.row1Offset * density
                        if (row2Id != 0) pinView.findViewById<View>(row2Id)?.translationY = cfg.row2Offset * density
                        if (row3Id != 0) pinView.findViewById<View>(row3Id)?.translationY = cfg.row3Offset * density
                        if (row4Id != 0) pinView.findViewById<View>(row4Id)?.translationY = 0f
                        if (row0Id != 0) pinView.findViewById<View>(row0Id)?.translationY = cfg.row0Offset * density

                        // 2. Style numeric digit keys 1-9 and 0 with circular frosted glass
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
                                styleKeyAsCircle(pinView.findViewById(id))
                            }
                        }

                        // 3. Keep Delete, Cancel, and Emergency transparent
                        val delId = ctx.resources.getIdentifier("delete_button", "id", "com.android.systemui")
                        if (delId != 0) pinView.findViewById<View>(delId)?.background = null
                        val cancelId = ctx.resources.getIdentifier("cancel_button", "id", "com.android.systemui")
                        if (cancelId != 0) pinView.findViewById<View>(cancelId)?.background = null
                        val emerId = ctx.resources.getIdentifier("emergency_call_button", "id", "com.android.systemui")
                        if (emerId != 0) pinView.findViewById<View>(emerId)?.background = null
                    } catch (_: Throwable) {}
                }
            }

            for (m: Method in pinViewClass.declaredMethods) {
                if (m.name == "onFinishInflate" || m.name == "updatePositionForFod" || m.name == "updateLayoutParams") {
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
                }

                // Hook startAppearAnimation for Y-Axis Glide & Staggered Sequential Entrance
                if (m.name == "startAppearAnimation") {
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
                                val keysAnimField: Field = pinViewObj.javaClass.getField("keysAnimStartState")
                                keysAnimField.isAccessible = true
                                keysAnimField.set(pinViewObj, pairs)

                                val appearAnimField: Field = pinViewObj.javaClass.getField("mAppearAnimating")
                                appearAnimField.isAccessible = true
                                appearAnimField.setBoolean(pinViewObj, true)

                                val initAppearField: Field = pinViewObj.javaClass.getField("initAppearAnimed")
                                initAppearField.isAccessible = true
                                initAppearField.setBoolean(pinViewObj, true)

                                val canceledField: Field = pinViewObj.javaClass.getField("appearAnimCanceled")
                                canceledField.isAccessible = true
                                canceledField.setBoolean(pinViewObj, false)
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

                                    // 2. Y-Axis Glide & Settling Matrix for Keys
                                    val gridNames = arrayOf(
                                        arrayOf("key1", "key2", "key3"),
                                        arrayOf("key4", "key5", "key6"),
                                        arrayOf("key7", "key8", "key9"),
                                        arrayOf("delete_button", "key0", "cancel_button")
                                    )

                                    val initialYDrop = cfg.entryTranslationY * density
                                    val animDuration = cfg.entryDuration.coerceAtLeast(150f).toLong()
                                    val overshootFactor = cfg.entryOvershoot.coerceAtLeast(0.0f)
                                    val iosGlideSpring: Interpolator = if (overshootFactor > 0.01f) {
                                        OvershootInterpolator(overshootFactor)
                                    } else {
                                        PathInterpolator(0.18f, 0.90f, 0.25f, 1.0f)
                                    }

                                    for (row in gridNames.indices) {
                                        for (col in gridNames[row].indices) {
                                            val name = gridNames[row][col]
                                            val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
                                            if (id == 0) continue
                                            val key = pinView.findViewById<View>(id)
                                            if (key != null) {
                                                val delay = cfg.getKeyDelay(name, row, col)

                                                // Start position: deep below resting place (glide up along Y-axis into position)
                                                key.alpha = 0.0f
                                                key.scaleX = cfg.entryInitialScale
                                                key.scaleY = cfg.entryInitialScale
                                                key.translationY = initialYDrop

                                                key.animate()
                                                    .alpha(1.0f)
                                                    .scaleX(1.0f)
                                                    .scaleY(1.0f)
                                                    .translationY(0.0f)
                                                    .setDuration(animDuration)
                                                    .setStartDelay(delay)
                                                    .setInterpolator(iosGlideSpring)
                                                    .start()
                                            }
                                        }
                                    }

                                    val emerId = ctx.resources.getIdentifier("emergency_call_button", "id", "com.android.systemui")
                                    if (emerId != 0) {
                                        val emer = pinView.findViewById<View>(emerId)
                                        if (emer != null) {
                                            val emerDelay = cfg.getKeyDelay("emergency_call_button", 4, 0)
                                            emer.alpha = 0.0f
                                            emer.translationY = 25f * density
                                            emer.animate()
                                                .alpha(1.0f)
                                                .translationY(0.0f)
                                                .setDuration(animDuration)
                                                .setStartDelay(emerDelay)
                                                .setInterpolator(iosGlideSpring)
                                                .start()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Custom appear animation error", t)
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
                }

                // Hook startBackAnimation for clean crash-free dismiss on BACK press
                if (m.name == "startBackAnimation") {
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
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PIN view hook error", t)
        }
    }
}
