package dev.joseph.hyperbounce.notification

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.joseph.hyperbounce.notification.glass.NotificationGlassSheetView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Feature 7: iOS-Style Notification Center Container Sheet Hook.
 * Converts the HyperOS Notification Center into a single cohesive iOS-style glass container sheet:
 * - Slides down smoothly from the top as an integrated unit.
 * - Eliminates horizontal rolling and staggered row animations inside notifications so they sit fully static.
 * - Frames the container with a 3-edge AGSL Liquid Glass 2.0 lens (left, right, bottom edges).
 * - Refracts the live blurred shade background layer (ShadeBackgroundView) without touching wallpaper.
 * - Leaves the stock status bar clock animation from the left edge completely untouched.
 * - Insets notification items inward slightly so glass bevels frame them cleanly.
 */
object NotificationSheetHook {

    private const val TAG = "HyperBounce_NotifSheet"
    private const val TAG_GLASS_SHEET = "hyperbounce_notif_glass_sheet"
    private const val TAG_ORIG_MARGIN_START = 0x7E010001
    private const val TAG_ORIG_MARGIN_END = 0x7E010002

    @Volatile
    var currentStatusBarState: Int = 0 // 0 = SHADE, 1 = KEYGUARD, 2 = SHADE_LOCKED

    private var containerRef: WeakReference<ViewGroup>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook StatusBarStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)

        // 2. Hook MiuiNQSContainer (notification_container_parent) for lifecycle and glass injection
        hookContainerParent(module, classLoader)

        // 3. Hook NotificationPanelExpandController to drive iOS-style container translation
        hookExpandController(module, classLoader)

        // 4. Hook NotificationPanelViewControllerInjector as secondary expansion observer
        hookNpvcInjector(module, classLoader)

        // 5. Hook ViewStateInjectorImpl to eliminate stock rolling and row scaling animations
        hookViewStateInjector(module, classLoader)
    }

    private fun hookStatusBarState(module: XposedModule, classLoader: ClassLoader) {
        try {
            val sbClass = Class.forName("com.android.systemui.statusbar.StatusBarStateControllerImpl", false, classLoader)
            for (m: Method in sbClass.declaredMethods) {
                if (m.name == "setState" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val newState = (args[0] as Number).toInt()
                                currentStatusBarState = newState

                                if (newState != 0) {
                                    // Not in unlocked shade (e.g. keyguard active): restore container translation
                                    mainHandler.post {
                                        val container = containerRef?.get()
                                        if (container != null && !isLockScreenManaging(container)) {
                                            container.translationY = 0.0f
                                            val sheet = container.findViewWithTag<View>(TAG_GLASS_SHEET)
                                            sheet?.visibility = View.GONE
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in setState hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook StatusBarStateControllerImpl", t)
        }
    }

    private fun isLockScreenManaging(container: ViewGroup): Boolean {
        // On lockscreen, LockScreenSheetHook manages translationY if active
        return currentStatusBarState == 1
    }

    private fun hookContainerParent(module: XposedModule, classLoader: ClassLoader) {
        try {
            val containerClass = Class.forName("com.android.systemui.shade.MiuiNQSContainer", false, classLoader)
            for (m: Method in containerClass.declaredMethods) {
                if (m.name == "onAttachedToWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup ?: return result
                                containerRef = WeakReference(container)
                                mainHandler.post {
                                    ensureGlassSheetInjected(container)
                                    adjustNotificationMargins(container)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onAttachedToWindow hook", t)
                            }
                            return result
                        }
                    })
                } else if (m.name == "onLayout" && m.parameterTypes.size == 5) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup ?: return result
                                containerRef = WeakReference(container)
                                ensureGlassSheetInjected(container)
                                adjustNotificationMargins(container)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onLayout hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNQSContainer", t)
        }
    }

    private fun ensureGlassSheetInjected(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            var sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)

            if (!cfg.enabled) {
                sheet?.visibility = View.GONE
                return
            }

            if (sheet == null) {
                sheet = NotificationGlassSheetView(container.context).apply {
                    tag = TAG_GLASS_SHEET
                    visibility = View.INVISIBLE
                }
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                applyConstraintParentParams(lp, container)
                container.addView(sheet, 0, lp)
            }

            // Bind the real-time blurred shade background layer (ShadeBackgroundView)
            val parent = container.parent as? ViewGroup
            if (parent != null && sheet.shadeBackgroundView == null) {
                val shadeBgId = container.resources.getIdentifier("shade_background", "id", "com.android.systemui")
                val shadeBg = if (shadeBgId != 0) parent.findViewById<View>(shadeBgId) else null
                sheet.shadeBackgroundView = shadeBg
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject NotificationGlassSheetView", t)
        }
    }

    private fun applyConstraintParentParams(lp: ViewGroup.LayoutParams, container: ViewGroup) {
        try {
            val lpClass = lp.javaClass
            val fields = arrayOf("leftToLeft", "rightToRight", "topToTop", "bottomToBottom", "startToStart", "endToEnd")
            for (fieldName in fields) {
                try {
                    val f: Field = lpClass.getField(fieldName)
                    f.setInt(lp, 0) // ConstraintLayout.LayoutParams.PARENT_ID = 0
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun adjustNotificationMargins(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<View>(nsslId) else null ?: return

            val lp = nssl.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val density = container.resources.displayMetrics.density

            val origStart = (nssl.getTag(TAG_ORIG_MARGIN_START) as? Int) ?: run {
                val s = lp.marginStart
                nssl.setTag(TAG_ORIG_MARGIN_START, s)
                s
            }
            val origEnd = (nssl.getTag(TAG_ORIG_MARGIN_END) as? Int) ?: run {
                val e = lp.marginEnd
                nssl.setTag(TAG_ORIG_MARGIN_END, e)
                e
            }

            val extraMargin = if (cfg.enabled) (cfg.sideMargin * density).toInt() else 0
            val targetStart = origStart + extraMargin
            val targetEnd = origEnd + extraMargin

            if (lp.marginStart != targetStart || lp.marginEnd != targetEnd) {
                lp.marginStart = targetStart
                lp.marginEnd = targetEnd
                lp.leftMargin = targetStart
                lp.rightMargin = targetEnd
                nssl.layoutParams = lp
                nssl.requestLayout()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust notification margins", t)
        }
    }

    private fun hookExpandController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val expandControllerClass = Class.forName("com.android.systemui.shade.NotificationPanelExpandController", false, classLoader)
            for (m: Method in expandControllerClass.declaredMethods) {
                if (m.name.startsWith("notifyExpandHeightChanged") && m.parameterTypes.size == 4) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (!cfg.enabled || currentStatusBarState != 0) {
                                    return result
                                }

                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val expandHeight = (args[0] as Number).toFloat()
                                val expandThresh = (args[1] as Number).toFloat()

                                val fraction = if (expandThresh > 0f) (expandHeight / expandThresh).coerceIn(0.0f, 1.0f) else 0.0f
                                applyExpansionFraction(fraction)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in notifyExpandHeightChanged hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelExpandController", t)
        }
    }

    private fun hookNpvcInjector(module: XposedModule, classLoader: ClassLoader) {
        try {
            val npvcInjectorClass = Class.forName("com.android.systemui.shade.NotificationPanelViewControllerInjector", false, classLoader)
            for (m: Method in npvcInjectorClass.declaredMethods) {
                if (m.name.startsWith("setExpandedHeightInternal") && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (!cfg.enabled || currentStatusBarState != 0) {
                                    return result
                                }
                                val injector = chain.thisObject
                                val npvcField = injector.javaClass.getDeclaredField("panelViewControllerLazy")
                                npvcField.isAccessible = true
                                val lazyObj = npvcField.get(injector)
                                val getMethod = lazyObj?.javaClass?.getMethod("get")
                                val npvc = getMethod?.invoke(lazyObj)
                                if (npvc != null) {
                                    val fracField = npvc.javaClass.getDeclaredField("mExpandedFraction")
                                    fracField.isAccessible = true
                                    val fraction = fracField.getFloat(npvc).coerceIn(0.0f, 1.0f)
                                    applyExpansionFraction(fraction)
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelViewControllerInjector", t)
        }
    }

    private fun applyExpansionFraction(fraction: Float) {
        val container = containerRef?.get() ?: return
        var viewHeight = container.height.toFloat()
        if (viewHeight <= 0f) {
            viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
        }
        if (viewHeight <= 0f) viewHeight = 2400f

        val upwardOffset = (1.0f - fraction) * -viewHeight
        container.translationY = upwardOffset
        container.scaleX = 1.0f
        container.scaleY = 1.0f

        val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
        if (sheet != null) {
            sheet.visibility = if (fraction > 0.005f) View.VISIBLE else View.INVISIBLE
            if (sheet.visibility == View.VISIBLE) {
                sheet.invalidate()
            }
        }
    }

    private fun hookViewStateInjector(module: XposedModule, classLoader: ClassLoader) {
        try {
            val vsiClass = Class.forName("com.android.systemui.statusbar.notification.stack.ViewStateInjectorImpl", false, classLoader)

            for (m: Method in vsiClass.declaredMethods) {
                when {
                    // Stop horizontal rolling from the left edge: force translationX = 0.0f
                    m.name == "setFolmeTranslationXForType" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[1] = 0.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop individual row scale animations: keep scale = 1.0f for type 11018
                    (m.name == "setFolmeScaleXForType" || m.name == "setFolmeScaleYForType") &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop staggered row vertical translation: keep translationY = 0.0f for type 11018
                    m.name == "setFolmeTranslationYForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 0.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Keep full alpha for static rows in sheet container: keep alpha = 1.0f for type 11018
                    m.name == "setFolmeAlphaForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ViewStateInjectorImpl", t)
        }
    }
}
