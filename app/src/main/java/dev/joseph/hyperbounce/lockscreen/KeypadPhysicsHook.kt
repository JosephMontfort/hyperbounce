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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import dev.joseph.hyperbounce.lockscreen.glass.LiquidGlassKeyDrawable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Feature 2: 100% Real-Time Liquid Glass 2.0 Lockscreen PIN Pad Engine.
 * Replaces legacy jelly physics with true Liquid Glass 2.0 circular lens buttons.
 *
 * NOTE: Legacy spring / jelly squish physics engine is preserved below in a dormant state
 * for future reference and A/B comparison.
 */
object KeypadPhysicsHook {

    private const val TAG = "HyperBounce_Keypad"

    // =========================================================================
    // DORMANT LEGACY JELLY / SPRING PHYSICS ENGINE (Preserved for reference)
    // =========================================================================
    /*
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
    */
    // =========================================================================

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook MiuiNumPadKey touch events for Liquid Glass 2.0 Press Optics & Liquid Bulge
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
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        liquidDrawable?.onTouchDown(event.x, event.y)
                                        // Dynamic smooth liquid scale on press
                                        view.animate()
                                            .scaleX(cfg.pressLiquidScale)
                                            .scaleY(cfg.pressLiquidScale)
                                            .setDuration(140)
                                            .setInterpolator(DecelerateInterpolator(1.5f))
                                            .start()
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        liquidDrawable?.onTouchMove(event.x, event.y)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        liquidDrawable?.onTouchUp()
                                        // Spring back smoothly
                                        view.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(280)
                                            .setInterpolator(OvershootInterpolator(1.4f))
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

                        // Apply custom button diameter to layout params
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
                        keyView.elevation = 6 * density

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

                        // 1. Vertical Row Offsets
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

                        // 2. Numeric Digit Keys 1-9 and 0
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

                        // 3. Clear delete / cancel buttons
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
                                val keyNames = arrayOf(
                                    arrayOf("key1", "key2", "key3"),
                                    arrayOf("key4", "key5", "key6"),
                                    arrayOf("key7", "key8", "key9"),
                                    arrayOf("emergency_call_button", "key0", "delete_button")
                                )

                                for (row in 0..3) {
                                    for (col in 0..2) {
                                        val keyName = keyNames[row][col]
                                        val resId = ctx.resources.getIdentifier(keyName, "id", "com.android.systemui")
                                        val keyView = if (resId != 0) pinView.findViewById<View>(resId) else null

                                        if (keyView != null) {
                                            val delay = cfg.getKeyDelay(keyName, row, col)
                                            val startTY = cfg.entryTranslationY * density
                                            val startScale = cfg.entryInitialScale
                                            val duration = cfg.entryDuration.toLong().coerceAtLeast(100L)
                                            val overshoot = cfg.entryOvershoot.coerceAtLeast(0.5f)

                                            keyView.translationY = startTY
                                            keyView.scaleX = startScale
                                            keyView.scaleY = startScale
                                            keyView.alpha = 0f

                                            keyView.animate()
                                                .translationY(0f)
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .alpha(1f)
                                                .setStartDelay(delay)
                                                .setDuration(duration)
                                                .setInterpolator(OvershootInterpolator(overshoot))
                                                .start()
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}

                            return null
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MiuiKeyguardPINView hook error", t)
        }
    }
}
