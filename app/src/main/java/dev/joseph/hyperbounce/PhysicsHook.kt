package dev.joseph.hyperbounce

import android.app.Activity
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.packageName.contains("instagram")) return

        log("==================================================")
        log("[INIT] HyperBounce Aggressive Tracer Active in: ${lpparam.packageName}")
        log("==================================================")

        // 1. Hook Activity.dispatchTouchEvent for aggressive touch tracing
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as? MotionEvent ?: return
                        val activity = param.thisObject as? Activity ?: return

                        if (event.action == MotionEvent.ACTION_DOWN) {
                            val displayMetrics = activity.resources.displayMetrics
                            val screenHeight = displayMetrics.heightPixels
                            val touchY = event.rawY
                            val touchX = event.rawX

                            // Target the bottom 20% of the screen (Navigation Area)
                            if (touchY > screenHeight * 0.80f) {
                                log("\n==================================================")
                                log("[TOUCH IN NAV BAR ZONE] RawX: $touchX, RawY: $touchY / ScreenH: $screenHeight")
                                log("Activity: ${activity.javaClass.name}")
                                log("==================================================")

                                val decorView = activity.window?.decorView as? ViewGroup
                                if (decorView != null) {
                                    log("--- DUMPING VIEW INTERSECTIONS AT TOUCH POINT ---")
                                    dumpViewsAtPoint(decorView, touchX, touchY)
                                }

                                log("\n--- TOUCH EVENT STACKTRACE ---")
                                val stackTrace = Log.getStackTraceString(Throwable("NavTouchStack"))
                                log(stackTrace)
                                log("==================================================\n")
                            }
                        }
                    }
                }
            )
            log("[SUCCESS] Hooked Activity.dispatchTouchEvent")
        } catch (t: Throwable) {
            log("[ERROR] Failed to hook dispatchTouchEvent: ${t.message}")
        }

        // 2. Hook View.onAttachedToWindow to catch inflating components
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val resName = safeGetResourceName(view)
                        val className = view.javaClass.name

                        if (resName.contains("tab", ignoreCase = true) ||
                            resName.contains("nav", ignoreCase = true) ||
                            resName.contains("bottom", ignoreCase = true) ||
                            className.contains("TouchInterceptor", ignoreCase = true) ||
                            className.contains("Litho", ignoreCase = true) ||
                            className.contains("ComponentHost", ignoreCase = true)
                        ) {
                            log("\n[VIEW ATTACHED] Class: $className | ID: $resName | Vis: ${view.visibility} | Size: ${view.width}x${view.height}")
                            log("Parent: ${view.parent?.javaClass?.name ?: "null"}")
                            log("Stacktrace:\n" + Log.getStackTraceString(Throwable("ViewAttachStack")))
                        }
                    }
                }
            )
            log("[SUCCESS] Hooked View.onAttachedToWindow")
        } catch (t: Throwable) {
            log("[ERROR] Failed to hook View.onAttachedToWindow: ${t.message}")
        }
    }

    private fun dumpViewsAtPoint(view: View, x: Float, y: Float, depth: Int = 0) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val vx = location[0]
        val vy = location[1]
        val vw = view.width
        val vh = view.height

        // Intersects touch point
        if (x >= vx && x <= vx + vw && y >= vy && y <= vy + vh) {
            val indent = "  ".repeat(depth)
            val resName = safeGetResourceName(view)
            val visStr = when (view.visibility) {
                View.VISIBLE -> "VISIBLE"
                View.INVISIBLE -> "INVISIBLE"
                View.GONE -> "GONE"
                else -> "UNKNOWN"
            }

            log("$indent-> [DEPTH $depth] ${view.javaClass.name} | ID: $resName | Rect: ($vx, $vy, ${vx + vw}, ${vy + vh}) | Vis: $visStr | Alpha: ${view.alpha} | TransY: ${view.translationY}")

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    dumpViewsAtPoint(view.getChildAt(i), x, y, depth + 1)
                }
            }
        }
    }

    private fun safeGetResourceName(view: View): String {
        if (view.id == View.NO_ID || view.resources == null) return "NO_ID"
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            "0x${Integer.toHexString(view.id)}"
        }
    }

    private fun log(msg: String) {
        Log.e("HyperBounce-Tracer", msg)
        XposedBridge.log("[HyperBounce-Tracer] $msg")
    }
}
