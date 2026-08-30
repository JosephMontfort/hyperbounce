package dev.joseph.hyperbounce.lockscreen

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Feature 1: Lock Screen Sheet & AGSL Liquid Glass Shaders Hook.
 * Intercepts KeyguardPanelViewController and KeyguardClockContainer in com.android.systemui.
 */
object LockScreenSheetHook {

    private const val TAG = "HyperBounce_LockSheet"

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook KeyguardPanelViewController (HyperOS Lockscreen Controller)
        try {
            val kpvcClass = Class.forName("com.android.keyguard.panel.KeyguardPanelViewController", false, classLoader)
            for (m: Method in kpvcClass.declaredMethods) {
                // Hook updateKeyguardElementsExpansionInternal(float)
                if (m.name.startsWith("updateKeyguardElementsExpansionInternal") &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val f = (args[0] as Number).toFloat()
                                val kpvc = chain.thisObject

                                val rootViewField: Field = kpvc.javaClass.getDeclaredField("keyguardRootView")
                                rootViewField.isAccessible = true
                                val rootView = rootViewField.get(kpvc) as? View

                                var notifView: View? = null
                                try {
                                    val nsslField: Field = kpvc.javaClass.getDeclaredField("notificationStackScrollLayout")
                                    nsslField.isAccessible = true
                                    notifView = nsslField.get(kpvc) as? View
                                } catch (_: Throwable) {}

                                if (notifView == null) {
                                    try {
                                        val ncpField: Field = kpvc.javaClass.getDeclaredField("notificationContainerParent")
                                        ncpField.isAccessible = true
                                        notifView = ncpField.get(kpvc) as? View
                                    } catch (_: Throwable) {}
                                }

                                val finalNotifView = notifView

                                if (rootView != null) {
                                    val cfg = LockScreenConfigHolder.sheet
                                    if (!cfg.enabled) return result

                                    val fraction = f.coerceIn(0.0f, 1.0f)
                                    var viewHeight = rootView.height.toFloat()
                                    if (viewHeight == 0f) viewHeight = 2400f
                                    val upwardOffset = (1.0f - fraction) * -viewHeight

                                    val updateUI = Runnable {
                                        // 1. Move root lockscreen layer upward smoothly (120Hz native performance, zero lag)
                                        rootView.translationY = upwardOffset
                                        rootView.scaleX = 1.0f
                                        rootView.scaleY = 1.0f

                                        // AndroidLiquidGlass: 4-Sided True Refraction Shader on Lockscreen Card Sheet
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            try {
                                                val cardW = if (rootView.width > 0) rootView.width.toFloat() else 1080f
                                                val cardH = if (rootView.height > 0) rootView.height.toFloat() else 2400f
                                                val density = rootView.context.resources.displayMetrics.density
                                                val cornerR = 28f * density

                                                val refractionShader = RuntimeShader(LockScreenShaders.REFRACTION_SHADER_SRC)
                                                refractionShader.setFloatUniform("size", cardW, cardH)
                                                refractionShader.setFloatUniform("offset", 0f, 0f)
                                                refractionShader.setFloatUniform("cornerRadii", 0f, 0f, cornerR, cornerR)
                                                refractionShader.setFloatUniform("refractionHeight", cfg.refractionHeight.coerceAtLeast(1f) * density)
                                                refractionShader.setFloatUniform("refractionAmount", cfg.refractionAmount.coerceAtLeast(1f) * density)
                                                refractionShader.setFloatUniform("depthEffect", cfg.depthEffect.coerceAtLeast(0f))

                                                val shaderEffect = RenderEffect.createRuntimeShaderEffect(refractionShader, "content")
                                                rootView.setRenderEffect(shaderEffect)
                                            } catch (_: Throwable) {}
                                        }

                                        // 2. Move notification container upward in sync
                                        if (finalNotifView != null) {
                                            finalNotifView.translationY = upwardOffset
                                            finalNotifView.scaleX = 1.0f
                                            finalNotifView.scaleY = 1.0f
                                        }

                                        // 3. Inject Full Container Sheet Outline with Physical Fresnel Specular Highlights
                                        if (rootView is ViewGroup) {
                                            var fullFrame = rootView.findViewWithTag<View>("hyperbounce_full_sheet_frame")
                                            if (fullFrame == null) {
                                                val context = rootView.context
                                                fullFrame = View(context).apply {
                                                    id = View.generateViewId()
                                                    tag = "hyperbounce_full_sheet_frame"
                                                    val density = context.resources.displayMetrics.density
                                                    background = LiquidGlassFrameDrawable(density)
                                                }

                                                var added = false
                                                try {
                                                    val clpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, rootView.context.classLoader)
                                                    val clpCons: Constructor<*> = clpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                                                    val clp = clpCons.newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) as ViewGroup.MarginLayoutParams
                                                    clpClass.getField("topToTop").set(clp, 0)
                                                    clpClass.getField("bottomToBottom").set(clp, 0)
                                                    clpClass.getField("startToStart").set(clp, 0)
                                                    clpClass.getField("endToEnd").set(clp, 0)
                                                    rootView.addView(fullFrame, 0, clp)
                                                    added = true
                                                } catch (_: Throwable) {}

                                                if (!added) {
                                                    try {
                                                        val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                                        rootView.addView(fullFrame, 0, flp)
                                                    } catch (_: Throwable) {}
                                                }
                                            }
                                        }

                                        // 4. Inject Bottom Glass Lip & Dynamic Pill into KeyguardBottomAreaView
                                        try {
                                            val bottomAreaField: Field = kpvc.javaClass.getDeclaredField("keyguardBottomAreaInjector")
                                            bottomAreaField.isAccessible = true
                                            val bottomAreaInjector = bottomAreaField.get(kpvc)
                                            if (bottomAreaInjector != null) {
                                                val bViewField: Field = bottomAreaInjector.javaClass.getDeclaredField("mKeyguardBottomAreaView")
                                                bViewField.isAccessible = true
                                                val bottomAreaView = bViewField.get(bottomAreaInjector) as? ViewGroup
                                                if (bottomAreaView != null) {
                                                    val context = bottomAreaView.context
                                                    val density = context.resources.displayMetrics.density

                                                    // A. Bottom Specular Glass Lip
                                                    var glassRim = bottomAreaView.findViewWithTag<View>("hyperbounce_glass_rim")
                                                    if (glassRim == null) {
                                                        glassRim = View(context).apply {
                                                            id = View.generateViewId()
                                                            tag = "hyperbounce_glass_rim"

                                                            val rimHeight = (cfg.lipHeight.coerceAtLeast(10f) * density).toInt()
                                                            val cornerR = (28 * density).toInt()
                                                            val strokeA = (cfg.borderAlpha.coerceIn(0f, 1f) * 255).toInt()
                                                            val strokeColor = 0x00FFFFFF or (strokeA shl 24)
                                                            val strokeW = (cfg.borderWidth.coerceAtLeast(0.5f) * density).toInt()

                                                            val rimDrawable = GradientDrawable().apply {
                                                                shape = GradientDrawable.RECTANGLE
                                                                colors = intArrayOf(0x03FFFFFF, 0x25FFFFFF)
                                                                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                                                                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerR.toFloat(), cornerR.toFloat(), cornerR.toFloat(), cornerR.toFloat())
                                                                setStroke(strokeW, strokeColor)
                                                            }

                                                            background = rimDrawable
                                                            elevation = 4 * density
                                                        }

                                                        val rlp = FrameLayout.LayoutParams(
                                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                                            (cfg.lipHeight.coerceAtLeast(10f) * density).toInt(),
                                                            Gravity.BOTTOM
                                                        )
                                                        bottomAreaView.addView(glassRim, 0, rlp)
                                                    }

                                                    // B. Dynamic iOS Home Indicator Bar
                                                    var pill = bottomAreaView.findViewWithTag<View>("hyperbounce_home_pill")
                                                    if (pill == null) {
                                                        val pillW = (cfg.pillWidth.coerceAtLeast(40f) * density).toInt()
                                                        val pillH = (cfg.pillHeight.coerceAtLeast(2f) * density).toInt()
                                                        val radius = pillH / 2
                                                        val bMargin = (cfg.pillBottomMargin * density).toInt()

                                                        pill = View(context).apply {
                                                            id = View.generateViewId()
                                                            tag = "hyperbounce_home_pill"

                                                            val pillDrawable = GradientDrawable().apply {
                                                                shape = GradientDrawable.RECTANGLE
                                                                setColor(0xF0FFFFFF.toInt())
                                                                cornerRadius = radius.toFloat()
                                                            }
                                                            background = pillDrawable
                                                            elevation = 8 * density
                                                        }

                                                        val flp = FrameLayout.LayoutParams(
                                                            pillW,
                                                            pillH,
                                                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                                        ).apply {
                                                            bottomMargin = bMargin
                                                        }
                                                        bottomAreaView.addView(pill, flp)
                                                    }
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            Log.e(TAG, "Rim/Pill injection error", t)
                                        }
                                    }

                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        updateUI.run()
                                    } else {
                                        Handler(Looper.getMainLooper()).post(updateUI)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "KPVC expansion hook error", t)
                            }
                            return result
                        }
                    })
                }

                // Hook doScale / doScale$1 to completely disable shrinking towards center
                if (m.name.startsWith("doScale") &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    View::class.java.isAssignableFrom(m.parameterTypes[1])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val v = args[1] as? View
                                if (v != null) {
                                    val resetScale = Runnable {
                                        v.scaleX = 1.0f
                                        v.scaleY = 1.0f
                                    }
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        resetScale.run()
                                    } else {
                                        Handler(Looper.getMainLooper()).post(resetScale)
                                    }
                                }
                            } catch (_: Throwable) {}
                            return null
                        }
                    })
                }

                // Hook startUnlockKeyguardDismissAnim for smooth unlock dismissal upward
                if (m.name == "startUnlockKeyguardDismissAnim") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val kpvc = chain.thisObject
                                val rootViewField: Field = kpvc.javaClass.getDeclaredField("keyguardRootView")
                                rootViewField.isAccessible = true
                                val rootView = rootViewField.get(kpvc) as? View

                                var notifView: View? = null
                                try {
                                    val nsslField: Field = kpvc.javaClass.getDeclaredField("notificationStackScrollLayout")
                                    nsslField.isAccessible = true
                                    notifView = nsslField.get(kpvc) as? View
                                } catch (_: Throwable) {}
                                if (notifView == null) {
                                    try {
                                        val ncpField: Field = kpvc.javaClass.getDeclaredField("notificationContainerParent")
                                        ncpField.isAccessible = true
                                        notifView = ncpField.get(kpvc) as? View
                                    } catch (_: Throwable) {}
                                }

                                val finalNotifView = notifView

                                if (rootView != null) {
                                    val animUI = Runnable {
                                        var viewHeight = rootView.height.toFloat()
                                        if (viewHeight == 0f) viewHeight = 2400f
                                        rootView.animate()
                                            .translationY(-viewHeight)
                                            .alpha(0.0f)
                                            .setDuration(220)
                                            .setInterpolator(DecelerateInterpolator(1.5f))
                                            .start()
                                        finalNotifView?.animate()
                                            ?.translationY(-viewHeight)
                                            ?.alpha(0.0f)
                                            ?.setDuration(220)
                                            ?.setInterpolator(DecelerateInterpolator(1.5f))
                                            ?.start()
                                    }
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        animUI.run()
                                    } else {
                                        Handler(Looper.getMainLooper()).post(animUI)
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "KPVC class hook error", t)
        }

        // 2. Disable clock scaling flag in KeyguardClockContainer
        try {
            val clockContainerClass = Class.forName("com.android.keyguard.clock.KeyguardClockContainer", false, classLoader)
            for (m: Method in clockContainerClass.declaredMethods) {
                if (m.name == "updateMagazineView" || m.name == "onFinishInflate") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject
                                val canScaleField: Field = container.javaClass.getDeclaredField("mClockCanScale")
                                canScaleField.isAccessible = true
                                canScaleField.setBoolean(container, false)
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }
}
