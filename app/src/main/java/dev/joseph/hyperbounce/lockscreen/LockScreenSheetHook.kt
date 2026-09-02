package dev.joseph.hyperbounce.lockscreen

import android.content.Context
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
import dev.joseph.hyperbounce.lockscreen.glass.LockScreenGlassSheetView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Feature 1: Lock Screen Liquid Glass Sheet Hook.
 * Injects a real-time AGSL Liquid Glass Sheet with 3-edge refraction & chromatic aberration
 * behind the lockscreen elements, synchronized to 120Hz swipe translation.
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
                                    if (!cfg.enabled) {
                                        if (rootView is ViewGroup) {
                                            val existingSheet = rootView.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                                            if (existingSheet != null) {
                                                rootView.removeView(existingSheet)
                                            }
                                        }
                                        return result
                                    }

                                    val fraction = f.coerceIn(0.0f, 1.0f)
                                    var viewHeight = rootView.height.toFloat()
                                    if (viewHeight == 0f) viewHeight = 2400f
                                    val upwardOffset = (1.0f - fraction) * -viewHeight

                                    val updateUI = Runnable {
                                        // 1. Move root lockscreen layer upward smoothly (120Hz native performance)
                                        rootView.translationY = upwardOffset
                                        rootView.scaleX = 1.0f
                                        rootView.scaleY = 1.0f

                                        // 2. Ensure real-time AGSL Liquid Glass Sheet Card is at index 0 (behind clock & notifs)
                                        ensureInjected(kpvc)

                                        // 3. Move notification container upward in sync
                                        if (finalNotifView != null) {
                                            finalNotifView.translationY = upwardOffset
                                            finalNotifView.scaleX = 1.0f
                                            finalNotifView.scaleY = 1.0f
                                        }

                                        // 4. Dynamic iOS Home Indicator Bar in KeyguardBottomAreaView
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
                                            Log.e(TAG, "Pill injection error", t)
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

                // Hook onViewAttachedToWindow or updateKeyguardElementsVisibility to ensure instant injection on lockscreen display
                if (m.name == "onViewAttachedToWindow" || m.name == "updateKeyguardElementsVisibility") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val kpvc = chain.thisObject
                                val attachUI = Runnable { ensureInjected(kpvc) }
                                if (Looper.myLooper() == Looper.getMainLooper()) {
                                    attachUI.run()
                                } else {
                                    Handler(Looper.getMainLooper()).post(attachUI)
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }

                // Hook doScale / doScale$1 to disable shrinking towards center
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

        // 2. Hook KeyguardRootView & HyperOSKeyguardRootView onLayout to maintain full bounds
        val rootViewClasses = arrayOf(
            "com.android.keyguard.widget.HyperOSKeyguardRootView",
            "com.android.systemui.keyguard.ui.view.KeyguardRootView"
        )
        for (className in rootViewClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "onLayout") {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                try {
                                    val v = chain.thisObject as? ViewGroup ?: return result
                                    val sheet = v.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                                    if (sheet != null && v.width > 0 && v.height > 0) {
                                        sheet.layout(0, 0, v.width, v.height)
                                    }
                                } catch (_: Throwable) {}
                                return result
                            }
                        })
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. Disable clock scaling flag in KeyguardClockContainer
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

    private fun ensureInjected(kpvc: Any) {
        try {
            val rootViewField: Field = kpvc.javaClass.getDeclaredField("keyguardRootView")
            rootViewField.isAccessible = true
            val rootView = rootViewField.get(kpvc) as? ViewGroup ?: return
            val cfg = LockScreenConfigHolder.sheet
            if (!cfg.enabled) {
                val existing = rootView.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                if (existing != null) rootView.removeView(existing)
                return
            }

            var glassSheet = rootView.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
            if (glassSheet == null) {
                val context = rootView.context
                glassSheet = LockScreenGlassSheetView(context).apply {
                    id = View.generateViewId()
                    tag = "hyperbounce_lock_glass_sheet"
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
                    rootView.addView(glassSheet, 0, clp)
                    added = true
                } catch (_: Throwable) {}

                if (!added) {
                    try {
                        val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        rootView.addView(glassSheet, 0, flp)
                    } catch (_: Throwable) {}
                }
            }

            if (rootView.width > 0 && rootView.height > 0) {
                glassSheet.layout(0, 0, rootView.width, rootView.height)
            }
            glassSheet.invalidate()
        } catch (_: Throwable) {}
    }
}
