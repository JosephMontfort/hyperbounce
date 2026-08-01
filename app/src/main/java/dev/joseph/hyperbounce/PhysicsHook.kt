package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.instagram.android") return

        XposedBridge.log("[HyperBounce-IG] Instagram package loaded! Injecting diagnostic hooks...")

        // Hook View.onAttachedToWindow to inspect attached views
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        inspectView(view, "onAttachedToWindow")
                    }
                }
            )
            XposedBridge.log("[HyperBounce-IG] Hooked View.onAttachedToWindow successfully.")
        } catch (e: Throwable) {
            XposedBridge.log("[HyperBounce-IG] Failed to hook View.onAttachedToWindow: " + e.message)
        }

        // Hook ViewGroup.addView to catch dynamic UI additions
        try {
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java,
                "addView",
                View::class.java,
                Int::class.javaPrimitiveType,
                ViewGroup.LayoutParams::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val child = param.args[0] as? View ?: return
                        inspectView(child, "ViewGroup.addView")
                    }
                }
            )
            XposedBridge.log("[HyperBounce-IG] Hooked ViewGroup.addView successfully.")
        } catch (e: Throwable) {
            XposedBridge.log("[HyperBounce-IG] Failed to hook ViewGroup.addView: " + e.message)
        }
    }

    private fun inspectView(view: View, source: String) {
        try {
            var resEntryName = ""
            if (view.id != View.NO_ID && view.id > 0) {
                try {
                    resEntryName = view.resources.getResourceEntryName(view.id)
                } catch (_: Throwable) {}
            }

            val contentDesc = view.contentDescription?.toString() ?: ""
            val className = view.javaClass.name

            val isNavKeyword = resEntryName.contains("tab", ignoreCase = true) ||
                               resEntryName.contains("nav", ignoreCase = true) ||
                               resEntryName.contains("bottom", ignoreCase = true)

            val isNavContentDesc = contentDesc == "Home" ||
                                   contentDesc == "Reels" ||
                                   contentDesc == "Profile" ||
                                   contentDesc == "Message" ||
                                   contentDesc.contains("Search", ignoreCase = true)

            if (isNavKeyword || isNavContentDesc) {
                val parentName = view.parent?.javaClass?.name ?: "null"
                val childCount = if (view is ViewGroup) view.childCount else 0

                XposedBridge.log(
                    "[HyperBounce-IG] [$source] FOUND TARGET VIEW!\n" +
                    "  -> Class: $className\n" +
                    "  -> Resource ID Name: '$resEntryName' (Raw ID: ${view.id})\n" +
                    "  -> Content Desc: '$contentDesc'\n" +
                    "  -> Parent Class: $parentName\n" +
                    "  -> Child Count: $childCount"
                )

                // Eradication test for tab_bar container
                if (resEntryName == "tab_bar") {
                    XposedBridge.log("[HyperBounce-IG] Attempting to eradicate 'tab_bar'...")
                    view.post {
                        view.visibility = View.GONE
                        view.alpha = 0f
                        val lp = view.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            view.layoutParams = lp
                        }
                        XposedBridge.log("[HyperBounce-IG] 'tab_bar' forced to GONE, alpha 0, height 0.")
                    }
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("[HyperBounce-IG] Error during view inspection: " + e.message)
        }
    }
}
