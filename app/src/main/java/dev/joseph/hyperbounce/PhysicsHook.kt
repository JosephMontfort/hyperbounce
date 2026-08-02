package dev.joseph.hyperbounce

import android.app.Activity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    // Prevent log spam by only logging when the tab count actually changes
    private var lastChildCount = -1
    private val hookedActivities = mutableSetOf<String>()

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val activityName = activity.javaClass.simpleName
                    
                    if (!hookedActivities.contains(activityName)) {
                        XposedBridge.log("[HyperBounce] Attached to Activity: $activityName")
                        hookedActivities.add(activityName)
                    }

                    val res = activity.resources
                    val decorView = activity.window.decorView as? ViewGroup ?: return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")
                            if (pivotId == 0) return

                            val pivot = activity.findViewById<ViewGroup>(pivotId) ?: return
                            val currentChildCount = pivot.childCount

                            if (currentChildCount != lastChildCount) {
                                lastChildCount = currentChildCount
                                XposedBridge.log("[HyperBounce] pivot_bar updated! New child count: $currentChildCount")
                                
                                for (i in 0 until currentChildCount) {
                                    val child = pivot.getChildAt(i)
                                    XposedBridge.log("[HyperBounce] ---> child[$i]: ${child.javaClass.name}")
                                }
                            }
                        }
                    })
                }
            }
        )
    }
}
