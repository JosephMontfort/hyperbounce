package dev.joseph.hyperbounce

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.instagram.android") return

        XposedBridge.log("[HyperBounce-IG] Switched to Activity/DecorView hook strategy.")

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val activityName = activity.javaClass.simpleName
                        
                        XposedBridge.log("[HyperBounce-IG] Activity resumed: $activityName")

                        val decorView = activity.window?.decorView ?: return

                        // Monitor the view tree natively, bypassing Xposed limitations
                        decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                try {
                                    val resId = activity.resources.getIdentifier("tab_bar", "id", "com.instagram.android")
                                    if (resId == 0) return

                                    val tabBar = activity.findViewById<View>(resId)
                                    if (tabBar != null) {
                                        // Only log and modify if it's not already eradicated
                                        if (tabBar.visibility != View.GONE || (tabBar.layoutParams?.height ?: -1) != 0) {
                                            XposedBridge.log("[HyperBounce-IG] tab_bar detected in $activityName! Eradicating...")
                                            
                                            tabBar.visibility = View.GONE
                                            tabBar.alpha = 0f
                                            
                                            val lp = tabBar.layoutParams
                                            if (lp != null) {
                                                lp.height = 0
                                                tabBar.layoutParams = lp
                                                tabBar.requestLayout()
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    // Suppress spammy errors during layout passes
                                }
                            }
                        })
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[HyperBounce-IG] Failed to hook Activity.onResume: " + e.message)
        }
    }
}
