package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    private var hasDumped = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val res = activity.resources
                    val decorView = activity.window.decorView as? ViewGroup ?: return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (hasDumped) return

                            val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")
                            if (pivotId == 0) return

                            val pivot = activity.findViewById<ViewGroup>(pivotId) ?: return
                            
                            // Check the inner wrapper (the LinearLayout we saw in the logs)
                            val wrapper = pivot.getChildAt(0) as? ViewGroup
                            
                            // If the wrapper is fully inflated with the 5 tabs, trigger the aggressive dump
                            if (wrapper != null && wrapper.childCount >= 3) {
                                hasDumped = true // Prevent log flooding
                                
                                XposedBridge.log("[HyperBounce] =========================================")
                                XposedBridge.log("[HyperBounce] INITIATING AGGRESSIVE VIEW DUMP")
                                XposedBridge.log("[HyperBounce] =========================================")
                                dumpView(pivot, "", res)
                                XposedBridge.log("[HyperBounce] =========================================")
                            }
                        }
                    })
                }
            }
        )
    }

    private fun dumpView(view: View, indent: String, res: Resources) {
        val className = view.javaClass.simpleName
        var idName = "NO_ID"
        
        if (view.id != View.NO_ID) {
            try {
                idName = res.getResourceEntryName(view.id)
            } catch (e: Exception) {
                idName = "UNKNOWN_ID"
            }
        }

        var extraInfo = ""
        if (view is TextView) {
            extraInfo = " [TEXT: '${view.text}']"
        } else if (view is ImageView) {
            extraInfo = " [IMAGEVIEW: HasDrawable=${view.drawable != null}]"
        }

        var desc = ""
        if (view.contentDescription != null) {
            desc = " [DESC: '${view.contentDescription}']"
        }

        XposedBridge.log("[HyperBounce] $indent-> $className (id/$idName)$extraInfo$desc")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpView(view.getChildAt(i), "$indent  ", res)
            }
        }
    }
}
