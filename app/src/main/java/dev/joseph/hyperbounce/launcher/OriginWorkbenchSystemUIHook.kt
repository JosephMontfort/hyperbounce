package dev.joseph.hyperbounce.launcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Origin Workbench SystemUI Hook (Xiaomi Freeform Customization & Tuning)
 *
 * Implements the user's explicit architectural requirements:
 * 1. Strips off top-center 3 dots (MiuiDecorationDot).
 * 2. Strips off bottom navigation bar pill / handle (MiuiDecorationBottom).
 * 3. Strips off corner resize handles & border strokes (MultiTaskingCornerTipAndStrokeController).
 * 4. Locks Hero window firmly in place: blocks moving, resizing, and swipe gestures.
 * 5. Suppresses allFreeformFromFreeformToMini & fromFreeformToMini so the Hero window stays
 *    in normal freeform mode at its calibrated hero position and never flies to the corner as a mini-window.
 * 6. Automatically cleans up and exits the freeform task when Workbench deactivates.
 * 7. Fixes the NPE crash by NOT returning null from handleMotionEvents.
 */
object OriginWorkbenchSystemUIHook {
    private const val TAG = "OriginWorkbenchSysUI"

    private const val SETTINGS_WORKBENCH_ACTIVE = "hb_workbench_active"
    private const val SETTINGS_HERO_TASK_ID = "hb_workbench_hero_task_id"

    @Volatile
    private var isHooked = false

    @Volatile
    private var isObserverRegistered = false

    fun isWorkbenchActive(context: Context?): Boolean {
        if (context == null) return false
        return try {
            Settings.System.getInt(context.contentResolver, SETTINGS_WORKBENCH_ACTIVE, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun getHeroTaskId(context: Context?): Int {
        if (context == null) return -1
        return try {
            Settings.System.getInt(context.contentResolver, SETTINGS_HERO_TASK_ID, -1)
        } catch (_: Throwable) {
            -1
        }
    }

    private fun ensureObserverRegistered(context: Context, classLoader: ClassLoader) {
        if (isObserverRegistered) return
        isObserverRegistered = true
        try {
            val uri = Settings.System.getUriFor(SETTINGS_WORKBENCH_ACTIVE)
            context.contentResolver.registerContentObserver(uri, false, object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val active = isWorkbenchActive(context)
                    if (!active) {
                        try {
                            val heroId = getHeroTaskId(context)
                            val controllerStubClass = Class.forName("com.android.wm.shell.dagger.MultiTaskingControllerStub", false, classLoader)
                            val getInstanceMethod = controllerStubClass.getMethod("getInstance")
                            val stub = getInstanceMethod.invoke(null)
                            val getControllerMethod = stub.javaClass.getMethod("getMiuiFreeformModeController")
                            val controller = getControllerMethod.invoke(stub)
                            if (controller != null && heroId != -1) {
                                val exitMethod = controller.javaClass.getMethod("exitFreeformTask", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                exitMethod.invoke(controller, heroId, false)
                                Log.i(TAG, "Exited freeform task $heroId because Workbench deactivated")
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed cleaning freeform on exit", t)
                        }
                    }
                }
            })
            Log.i(TAG, "Registered Workbench exit observer in SystemUI")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register observer", t)
        }
    }

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        if (isHooked) return
        isHooked = true

        hookWindowDecorations(module, classLoader)
        hookFreeformModeController(module, classLoader)
        hookGestureAndMoveHandlers(module, classLoader)
        hookResizeAndCornerHandlers(module, classLoader)
    }

    /**
     * Hook 1: Strip off 3 dots (MiuiDecorationDot) and bottom nav bar pill (MiuiDecorationBottom).
     */
    private fun hookWindowDecorations(module: XposedInterface, classLoader: ClassLoader) {
        // 1. Hook MiuiDecorationDot.needCaption
        try {
            val dotClass = Class.forName("com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot", false, classLoader)
            val needCaptionMethod = dotClass.declaredMethods.firstOrNull {
                it.name == "needCaption" && it.parameterTypes.isEmpty()
            }
            if (needCaptionMethod != null) {
                needCaptionMethod.isAccessible = true
                module.hook(needCaptionMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val decor = chain.thisObject
                        val context = runCatching {
                            val f = decor.javaClass.superclass?.getDeclaredField("mContext")
                                ?: decor.javaClass.getDeclaredField("mContext")
                            f.isAccessible = true
                            f.get(decor) as? Context
                        }.getOrNull()

                        if (context != null) {
                            ensureObserverRegistered(context, classLoader)
                        }

                        if (isWorkbenchActive(context)) {
                            return false // Strip off top 3 dots!
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Successfully hooked MiuiDecorationDot.needCaption (Stripped 3 dots)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiDecorationDot", t)
        }

        // 2. Hook MiuiDecorationBottom.needCaption
        try {
            val bottomClass = Class.forName("com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationBottom", false, classLoader)
            val needCaptionMethod = bottomClass.declaredMethods.firstOrNull {
                it.name == "needCaption" && it.parameterTypes.isEmpty()
            }
            if (needCaptionMethod != null) {
                needCaptionMethod.isAccessible = true
                module.hook(needCaptionMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val decor = chain.thisObject
                        val context = runCatching {
                            val f = decor.javaClass.superclass?.getDeclaredField("mContext")
                                ?: decor.javaClass.getDeclaredField("mContext")
                            f.isAccessible = true
                            f.get(decor) as? Context
                        }.getOrNull()

                        if (context != null) {
                            ensureObserverRegistered(context, classLoader)
                        }

                        if (isWorkbenchActive(context)) {
                            return false // Strip off bottom nav pill!
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Successfully hooked MiuiDecorationBottom.needCaption (Stripped bottom pill)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiDecorationBottom", t)
        }
    }

    /**
     * Hook 2: Suppress converting the Hero freeform window into a mini-window when recents opens.
     */
    private fun hookFreeformModeController(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val controllerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeController", false, classLoader)
            for (m in controllerClass.declaredMethods) {
                if (m.name == "allFreeformFromFreeformToMini" || m.name == "fromFreeformToMini") {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val ctrl = chain.thisObject
                            val context = runCatching {
                                val f = ctrl.javaClass.getDeclaredField("mContext")
                                f.isAccessible = true
                                f.get(ctrl) as? Context
                            }.getOrNull()

                            if (context != null) {
                                ensureObserverRegistered(context, classLoader)
                            }

                            if (isWorkbenchActive(context)) {
                                Log.i(TAG, "Suppressed ${m.name} to preserve normal Hero Freeform window")
                                return null // Prevent converting Hero freeform window into mini window!
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Successfully hooked MiuiFreeformModeController (Suppressed mini-window conversion)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiFreeformModeController", t)
        }
    }

    /**
     * Hook 3: Prevent window moving and gesture hijacking.
     * Note: We intentionally do NOT hook handleMotionEvents directly to prevent NPE in MulWinSwitchEventHandler.
     */
    private fun hookGestureAndMoveHandlers(module: XposedInterface, classLoader: ClassLoader) {
        // Hook MiuiFreeformModeGestureHandler.isGestureControlled
        try {
            val gestureHandlerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler", false, classLoader)

            val isGestureControlledMethod = gestureHandlerClass.declaredMethods.firstOrNull {
                it.name == "isGestureControlled" && it.parameterTypes.size == 1
            }
            if (isGestureControlledMethod != null) {
                isGestureControlledMethod.isAccessible = true
                module.hook(isGestureControlledMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val handler = chain.thisObject
                        val context = runCatching {
                            val f = handler.javaClass.getDeclaredField("mContext")
                            f.isAccessible = true
                            f.get(handler) as? Context
                        }.getOrNull()

                        if (context != null) {
                            ensureObserverRegistered(context, classLoader)
                        }

                        if (isWorkbenchActive(context)) {
                            return false // Hero window is NOT gesture controlled
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Successfully hooked MiuiFreeformModeGestureHandler.isGestureControlled")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiFreeformModeGestureHandler", t)
        }

        // Hook MiuiFreeformModeMoveHandler (void return types)
        try {
            val moveHandlerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeMoveHandler", false, classLoader)

            for (m in moveHandlerClass.declaredMethods) {
                if (m.name == "handleMotionEvents" || m.name == "onBottomCaptionHandleMotionEvents") {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val handler = chain.thisObject
                            val context = runCatching {
                                val f = handler.javaClass.getDeclaredField("mContext")
                                f.isAccessible = true
                                f.get(handler) as? Context
                            }.getOrNull()

                            if (context != null) {
                                ensureObserverRegistered(context, classLoader)
                            }

                            if (isWorkbenchActive(context)) {
                                return null // Prevent window moving and bottom caption swipe actions!
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Successfully hooked MiuiFreeformModeMoveHandler (Blocked window moving)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiFreeformModeMoveHandler", t)
        }
    }

    /**
     * Hook 4: Prevent resizing and suppress corner resize handles/strokes.
     */
    private fun hookResizeAndCornerHandlers(module: XposedInterface, classLoader: ClassLoader) {
        // Hook MiuiFreeformModeResizeHandler.handleResize (void return type)
        try {
            val resizeHandlerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeResizeHandler", false, classLoader)
            for (m in resizeHandlerClass.declaredMethods) {
                if (m.name == "handleResize") {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val handler = chain.thisObject
                            val context = runCatching {
                                val f = handler.javaClass.getDeclaredField("mContext")
                                f.isAccessible = true
                                f.get(handler) as? Context
                            }.getOrNull()

                            if (context != null) {
                                ensureObserverRegistered(context, classLoader)
                            }

                            if (isWorkbenchActive(context)) {
                                return null // Block resize!
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Successfully hooked MiuiFreeformModeResizeHandler (Blocked window resizing)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiFreeformModeResizeHandler", t)
        }

        // Hook MultiTaskingCornerTipAndStrokeController
        try {
            val cornerClass = Class.forName("com.android.wm.shell.multitasking.common.corner.MultiTaskingCornerTipAndStrokeController", false, classLoader)
            for (m in cornerClass.declaredMethods) {
                if (m.name.contains("CornerAnim") || m.name.contains("showCorner") || m.name.contains("startGuide")) {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val controller = chain.thisObject
                            val context = runCatching {
                                val f = controller.javaClass.getDeclaredField("mContext")
                                f.isAccessible = true
                                f.get(controller) as? Context
                            }.getOrNull()

                            if (context != null) {
                                ensureObserverRegistered(context, classLoader)
                            }

                            if (isWorkbenchActive(context)) {
                                return null // Strip off corner resize handles & animations!
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Successfully hooked MultiTaskingCornerTipAndStrokeController (Stripped corner handles)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MultiTaskingCornerTipAndStrokeController", t)
        }
    }
}
