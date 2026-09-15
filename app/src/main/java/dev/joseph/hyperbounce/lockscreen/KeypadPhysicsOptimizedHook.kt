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
import android.widget.ImageView
import android.widget.TextView
import dev.joseph.hyperbounce.lockscreen.glass.LiquidGlassKeyOptimizedDrawable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

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
 */
object KeypadPhysicsOptimizedHook {

    private const val TAG = "HyperBounce_KeypadOpt"

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
                                val liquidDrawable = view.background as? LiquidGlassKeyOptimizedDrawable
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        liquidDrawable?.onTouchDown(event.x, event.y)
                                        view.animate()
                                            .scaleX(cfg.pressLiquidScale)
                                            .scaleY(cfg.pressLiquidScale)
                                            .setDuration(120L)
                                            .setInterpolator(DecelerateInterpolator(1.5f))
                                            .setUpdateListener { view.invalidate() }
                                            .start()
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        liquidDrawable?.onTouchMove(event.x, event.y)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        liquidDrawable?.onTouchUp()
                                        view.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(240L)
                                            .setInterpolator(OvershootInterpolator(1.4f))
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

                        // 1. Container Horizontal Width & Column Spacing Hack
                        val pinContainerId = ctx.resources.getIdentifier("pin_container", "id", "com.android.systemui")
                        val pinContainer = if (pinContainerId != 0) pinView.findViewById<ViewGroup>(pinContainerId) else null
                        if (pinContainer != null) {
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
                        }

                        // 2. Vertical Row Offsets (Layout Spacing Hack)
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
                            val overshoot = cfg.entryOvershoot.coerceAtLeast(1.0f)
                            val col1TransX = -cfg.col1Offset * density
                            val col3TransX = cfg.col3Offset * density

                            for (row in keys.indices) {
                                for (col in keys[row].indices) {
                                    val name = keys[row][col]
                                    val id = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
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

                                    keyView.animate().cancel()
                                    keyView.alpha = 0f
                                    keyView.translationY = transY
                                    keyView.translationX = targetTransX
                                    keyView.scaleX = initialScale
                                    keyView.scaleY = initialScale

                                    keyView.animate()
                                        .alpha(1f)
                                        .translationY(0f)
                                        .translationX(targetTransX)
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(animDuration)
                                        .setStartDelay(delay)
                                        .setInterpolator(OvershootInterpolator(overshoot))
                                        .setUpdateListener {
                                            keyView.invalidate()
                                        }
                                        .start()
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
