package dev.joseph.hyperbounce.launcher

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import io.github.libxposed.api.XposedInterface

/**
 * Origin Workbench SystemUI Hook (Xiaomi Freeform Customization & Tuning)
 *
 * Implements the user's explicit architectural requirement:
 * "implement xiaomi's floating window for the hero app. but the floating window cannot be moved.
 *  examine the architecture of floating windows in ciaomi. implement that but heavily customize
 *  amd tune it for our use case. it should'nt be resizeable and it shouldnt move by pulling the
 *  three dots at top centre. basically use xiaomi's floating windows but strip off the tgree dots
 *  and resize handles and nav bar pill at bottom centre of floating window. so it shoes the app
 *  content in real time. if a reel is playing in hero aoo then it will play in workbench.
 *  but the hero app cont be resized or closed or moved. implement this."
 *
 * Intercepts SystemUI Multitasking & Freeform subsystems:
 * 1. [MiuiDecorationDot.needCaption]: Strips off the 3 dots at top center.
 * 2. [MiuiDecorationBottom.needCaption]: Strips off the bottom navigation bar pill / handle.
 * 3. [MiuiFreeformModeGestureHandler.isGestureControlled]: Prevents gesture recognition.
 * 4. [MiuiFreeformModeGestureHandler.handleMotionEvents]: Skips touch events for freeform gestures.
 * 5. [MiuiFreeformModeMoveHandler.handleMotionEvents]: Blocks window moving by dragging.
 * 6. [MiuiFreeformModeMoveHandler.onBottomCaptionHandleMotionEvents]: Blocks bottom handle swipe gestures.
 * 7. [MiuiFreeformModeResizeHandler.handleResize]: Blocks window resizing.
 * 8. [MultiTaskingCornerTipAndStrokeController]: Suppresses corner resize indicators and strokes.
 */
object OriginWorkbenchSystemUIHook {
    private const val TAG = "OriginWorkbenchSysUI"

    private const val SETTINGS_WORKBENCH_ACTIVE = "hb_workbench_active"
    private const val SETTINGS_HERO_TASK_ID = "hb_workbench_hero_task_id"

    @Volatile
    private var isHooked = false

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

    fun init(module: XposedInterface, classLoader: ClassLoader) {
        if (isHooked) return
        isHooked = true

        hookWindowDecorations(module, classLoader)
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

                        if (isWorkbenchActive(context)) {
                            // Strip off top 3 dots!
                            return false
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

                        if (isWorkbenchActive(context)) {
                            // Strip off bottom nav pill!
                            return false
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
     * Hook 2: Prevent window moving and freeform gestures.
     */
    private fun hookGestureAndMoveHandlers(module: XposedInterface, classLoader: ClassLoader) {
        // Hook MiuiFreeformModeGestureHandler
        try {
            val gestureHandlerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler", false, classLoader)

            // isGestureControlled(MiuiFreeformModeTaskInfo)
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

                        if (isWorkbenchActive(context)) {
                            return false // Not gesture controlled when workbench is active!
                        }
                        return chain.proceed()
                    }
                })
            }

            // handleMotionEvents(MotionEvent, InputMonitor)
            val handleMotionMethod = gestureHandlerClass.declaredMethods.firstOrNull {
                it.name == "handleMotionEvents" && it.parameterTypes.size == 2
            }
            if (handleMotionMethod != null) {
                handleMotionMethod.isAccessible = true
                module.hook(handleMotionMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val handler = chain.thisObject
                        val context = runCatching {
                            val f = handler.javaClass.getDeclaredField("mContext")
                            f.isAccessible = true
                            f.get(handler) as? Context
                        }.getOrNull()

                        if (isWorkbenchActive(context)) {
                            return null // Do not handle gestures!
                        }
                        return chain.proceed()
                    }
                })
            }
            Log.i(TAG, "Successfully hooked MiuiFreeformModeGestureHandler (Blocked gesture hijacking)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking MiuiFreeformModeGestureHandler", t)
        }

        // Hook MiuiFreeformModeMoveHandler
        try {
            val moveHandlerClass = Class.forName("com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeMoveHandler", false, classLoader)

            // handleMotionEvents
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
     * Hook 3: Prevent resizing and suppress corner resize handles/strokes.
     */
    private fun hookResizeAndCornerHandlers(module: XposedInterface, classLoader: ClassLoader) {
        // Hook MiuiFreeformModeResizeHandler.handleResize
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
