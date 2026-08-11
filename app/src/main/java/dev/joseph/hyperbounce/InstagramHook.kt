package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.WeakHashMap

/**
 * Instagram-specific hooking logic. Ported 1:1 from the original standalone
 * PhysicsHook.java - every check, magic number, and exclusion list preserved.
 */
class InstagramHook {

    private val sOverlayParents = WeakHashMap<ViewGroup, Boolean>()

    fun handleLoadPackage(lpparam: LoadPackageParam) {
        log("==================================================")
        log("[INIT] HyperBounce: Ultimate Smart Shifter Final")
        log("==================================================")

        // 1. DIMENSION SPOOFER: Force video to bottom & fix DM Layouts
        try {
            XposedHelpers.findAndHookMethod(
                Resources::class.java,
                "getDimensionPixelSize",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val res = param.thisObject as Resources
                        try {
                            val name = res.getResourceEntryName(param.args[0] as Int)
                            if (name == "tab_bar_height") {
                                param.result = 0
                            }
                        } catch (e: Exception) {}
                    }
                },
            )
        } catch (t: Throwable) {}

        // 2. GHOST TAB BAR, INJECT UI, STRETCH VIDEO, & CACHE PARENTS
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (view.id == View.NO_ID || view.resources == null) return

                        try {
                            val resName = view.resources.getResourceEntryName(view.id)

                            // Cache the exact parent holding the overlays
                            if (resName == "clips_item_overlay_component") {
                                val parent = view.parent
                                if (parent is ViewGroup) {
                                    sOverlayParents[parent] = true
                                }
                            }

                            // Hide stock elements, but keep tab_bar's visibility state intact for tracking
                            if (resName == "tab_bar" || resName == "tab_bar_shadow" || resName == "layout_bottom_searchbar") {
                                if (resName == "tab_bar") {
                                    view.alpha = 0f
                                    view.translationY = 9999f
                                } else {
                                    view.visibility = View.GONE
                                }
                                val lp = view.layoutParams
                                if (lp != null) {
                                    lp.height = 0
                                    view.layoutParams = lp
                                }
                            }

                            // THE STRETCHER: Force the video containers to fill the black void
                            if (resName == "clips_video_container" || resName == "clips_viewer_video_layout") {
                                val lp = view.layoutParams
                                if (lp != null && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                                    view.layoutParams = lp
                                }
                            }

                            // Trigger Injection & UI Management
                            if (resName == "layout_container_main_wrapper") {
                                if (view.context is Activity) {
                                    val activity = view.context as Activity
                                    view.post {
                                        try {
                                            val stockBarId = activity.resources.getIdentifier("tab_bar", "id", activity.packageName)
                                            val decorView = activity.window.decorView as ViewGroup
                                            val stockBar = decorView.findViewById<View>(stockBarId)

                                            injectLiquidGlass(activity, view as ViewGroup, stockBar as? ViewGroup)

                                            // Lightweight listener: Liquid Bar purely mimics stock bar visibility
                                            decorView.viewTreeObserver.addOnGlobalLayoutListener {
                                                val injectedBar = decorView.findViewWithTag<View>("LiquidGlassInjected")
                                                val isTabBarActive = (stockBar != null && stockBar.visibility == View.VISIBLE)

                                                if (injectedBar != null) {
                                                    injectedBar.visibility = if (isTabBarActive) View.VISIBLE else View.GONE
                                                }

                                                // STRICT SHIFT LOGIC: Only shift if Tab Bar is visible AND we are on the Reels tab
                                                var isReelsTabActive = false
                                                if (isTabBarActive) {
                                                    val clipsTabId = activity.resources.getIdentifier("clips_tab", "id", activity.packageName)
                                                    val clipsTab = stockBar?.findViewById<View>(clipsTabId)
                                                    if (clipsTab != null && (clipsTab.isSelected || clipsTab.isActivated)) {
                                                        isReelsTabActive = true
                                                    }
                                                }

                                                // Determine target shift (Finalized to -180px)
                                                val shiftY = if (isReelsTabActive) -180.0f else 0.0f

                                                // INSTANT O(1) SHIFT: Loop strictly through the cached parents
                                                for (overlayParent in sOverlayParents.keys) {
                                                    if (overlayParent != null && overlayParent.isAttachedToWindow) {
                                                        for (c in 0 until overlayParent.childCount) {
                                                            val sibling = overlayParent.getChildAt(c)
                                                            val sibId = sibling.id
                                                            var sibName = ""
                                                            if (sibId != View.NO_ID && sibling.resources != null) {
                                                                try {
                                                                    sibName = sibling.resources.getResourceEntryName(sibId)
                                                                } catch (e: Exception) {}
                                                            }

                                                            if (sibName == "clips_bottom_legibility_gradient_component" ||
                                                                sibName == "clips_touchable_background" ||
                                                                sibName == "clips_item_overlay_component" ||
                                                                sibName == "clips_pause_and_mute_component"
                                                            ) {
                                                                continue
                                                            }

                                                            if (containsScrubber(sibling, 0)) continue

                                                            if (sibling.translationY != shiftY) {
                                                                sibling.translationY = shiftY
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                },
            )
        } catch (t: Throwable) {}

        // 3. THE MARGIN ASSASSIN
        try {
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java,
                "measureChildWithMargins",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val child = param.args[0] as View
                        if (child.id == View.NO_ID || child.resources == null) return

                        try {
                            val resName = child.resources.getResourceEntryName(child.id)
                            if (resName == "layout_container_main" ||
                                resName == "layout_container_main_wrapper" ||
                                resName == "swipeable_tab_view_pager"
                            ) {
                                val lp = child.layoutParams
                                if (lp is ViewGroup.MarginLayoutParams) {
                                    if (lp.bottomMargin > 50) lp.bottomMargin = 0
                                }
                            }
                        } catch (e: Exception) {}
                    }
                },
            )
        } catch (t: Throwable) {}
    }

    private fun containsScrubber(view: View?, depth: Int): Boolean {
        if (view == null || depth > 4) return false
        val id = view.id
        if (id != View.NO_ID && view.resources != null) {
            try {
                if (view.resources.getResourceEntryName(id) == "scrubber") return true
            } catch (e: Exception) {}
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsScrubber(view.getChildAt(i), depth + 1)) return true
            }
        }
        return false
    }

    private fun log(msg: String) {
        Log.e("HyperBounce-Tracer", msg)
        XposedBridge.log("[HyperBounce-Tracer] $msg")
    }
}
