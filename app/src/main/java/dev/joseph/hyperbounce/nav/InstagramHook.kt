package dev.joseph.hyperbounce.nav

import android.app.Activity
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.WeakHashMap

class InstagramHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageReadyParam
) {
    private val sOverlayParents = WeakHashMap<ViewGroup, Boolean>()

    fun onPackageReady() {
        val hyperBouncePrefs = HyperBouncePrefs(module)
        if (!hyperBouncePrefs.moduleEnabled || !hyperBouncePrefs.appEnabled("instagram")) {
            log("HyperBounce: disabled for Instagram, skipping injection")
            return
        }

        log("==================================================")
        log("[INIT] HyperBounce: Ultimate Smart Shifter Final")
        log("==================================================")

        try {
            val svlClass = param.classLoader.loadClass("com.instagram.ui.simplevideolayout.SimpleVideoLayout")
            module.hook(
                svlClass.getDeclaredMethod("getEnforceTextureView")
            ).intercept { true }
            for (ctor in svlClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val m = svlClass.getMethod("setEnforceTextureView", Boolean::class.javaPrimitiveType)
                        m.invoke(chain.thisObject, true)
                    } catch (_: Throwable) {}
                    result
                }
            }
        } catch (t: Throwable) {}

        try {
            val rcflClass = param.classLoader.loadClass("com.instagram.ui.widget.roundedcornerlayout.RoundedCornerFrameLayout")
            module.hook(
                rcflClass.getDeclaredMethod("getEnforceTextureView")
            ).intercept { true }
            for (ctor in rcflClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val m = rcflClass.getMethod("setEnforceTextureView", Boolean::class.javaPrimitiveType)
                        m.invoke(chain.thisObject, true)
                    } catch (_: Throwable) {}
                    result
                }
            }
        } catch (t: Throwable) {}

        try {
            val mflClass = param.classLoader.loadClass("com.instagram.ui.widget.framelayout.MediaFrameLayout")
            module.hook(
                mflClass.getDeclaredMethod("getEnforceTextureView")
            ).intercept { true }
            for (ctor in mflClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val m = mflClass.getMethod("setEnforceTextureView", Boolean::class.javaPrimitiveType)
                        m.invoke(chain.thisObject, true)
                    } catch (_: Throwable) {}
                    result
                }
            }
        } catch (t: Throwable) {}

        try {
            module.hook(
                Resources::class.java.getDeclaredMethod("getDimensionPixelSize", Int::class.javaPrimitiveType)
            ).intercept { chain ->
                val res = chain.thisObject as Resources
                try {
                    val name = res.getResourceEntryName(chain.args[0] as Int)
                    if (name == "tab_bar_height") {
                        return@intercept 0
                    }
                } catch (e: Exception) {}
                chain.proceed()
            }
        } catch (t: Throwable) {}

        try {
            module.hook(
                View::class.java.getDeclaredMethod("onAttachedToWindow")
            ).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View

                try {
                    val setMethod = view.javaClass.getMethod("setEnforceTextureView", Boolean::class.javaPrimitiveType)
                    setMethod.invoke(view, true)
                } catch (_: Throwable) {}
                
                if (view.id == View.NO_ID || view.resources == null) return@intercept result

                try {
                    val resName = view.resources.getResourceEntryName(view.id)

                    if (resName == "clips_item_overlay_component") {
                        val parent = view.parent
                        if (parent is ViewGroup) {
                            sOverlayParents[parent] = true
                        }
                    }

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

                    if (resName == "clips_video_container" || resName == "clips_viewer_video_layout") {
                        try {
                            val setMethod = view.javaClass.getMethod("setEnforceTextureView", Boolean::class.javaPrimitiveType)
                            setMethod.invoke(view, true)
                        } catch (_: Throwable) {}

                        if (hyperBouncePrefs.reelsFullscreenZoom) {
                            val lp = view.layoutParams
                            if (lp != null && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                                view.layoutParams = lp
                            }
                        }
                    }

                    if (resName == "layout_container_main_wrapper") {
                        if (view.context is Activity) {
                            val activity = view.context as Activity
                            view.post {
                                try {
                                    val stockBarId = activity.resources.getIdentifier("tab_bar", "id", activity.packageName)
                                    val decorView = activity.window.decorView as ViewGroup
                                    val stockBar = decorView.findViewById<View>(stockBarId)

                                    injectLiquidGlass(activity, view as ViewGroup, stockBar as? ViewGroup, hyperBouncePrefs)

                                    val res = activity.resources
                                    val pkg = activity.packageName
                                    val clipsTabId = res.getIdentifier("clips_tab", "id", pkg)
                                    val clipsGradientId = res.getIdentifier("clips_bottom_legibility_gradient_component", "id", pkg)
                                    val clipsTouchBgId = res.getIdentifier("clips_touchable_background", "id", pkg)
                                    val clipsOverlayCompId = res.getIdentifier("clips_item_overlay_component", "id", pkg)
                                    val clipsPauseMuteId = res.getIdentifier("clips_pause_and_mute_component", "id", pkg)

                                    val layoutListenerKey = 0x7F0B8888
                                    if (decorView.getTag(layoutListenerKey) == null) {
                                        decorView.setTag(layoutListenerKey, true)
                                        decorView.viewTreeObserver.addOnGlobalLayoutListener {
                                            val injectedBar = decorView.findViewWithTag<View>("LiquidGlassInjected")
                                            val isTabBarActive = (stockBar != null && stockBar.visibility == View.VISIBLE)

                                            if (injectedBar != null) {
                                                injectedBar.visibility = if (isTabBarActive) View.VISIBLE else View.GONE
                                            }

                                            var isReelsTabActive = false
                                            if (isTabBarActive && clipsTabId != 0) {
                                                val clipsTab = stockBar?.findViewById<View>(clipsTabId)
                                                if (clipsTab != null && (clipsTab.isSelected || clipsTab.isActivated)) {
                                                    isReelsTabActive = true
                                                }
                                            }

                                            val shiftY = if (isReelsTabActive) -180.0f else 0.0f

                                            for (overlayParent in sOverlayParents.keys) {
                                                if (overlayParent != null && overlayParent.isAttachedToWindow) {
                                                    for (c in 0 until overlayParent.childCount) {
                                                        val sibling = overlayParent.getChildAt(c)
                                                        val sibId = sibling.id
                                                        if (sibId != View.NO_ID) {
                                                            if (sibId == clipsGradientId ||
                                                                sibId == clipsTouchBgId ||
                                                                sibId == clipsOverlayCompId ||
                                                                sibId == clipsPauseMuteId
                                                            ) {
                                                                continue
                                                            }
                                                        }

                                                        // Elevate progress bar / scrubber and overlay elements uniformly above floating bar
                                                        if (sibling.translationY != shiftY) {
                                                            sibling.translationY = shiftY
                                                        }
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
                
                result
            }
        } catch (t: Throwable) {}

        try {
            module.hook(
                ViewGroup::class.java.getDeclaredMethod(
                    "measureChildWithMargins",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            ).intercept { chain ->
                val child = chain.args[0] as View
                if (child.id != View.NO_ID && child.resources != null) {
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
                chain.proceed()
            }
        } catch (t: Throwable) {}
    }

    private fun log(msg: String) {
        module.log(Log.ERROR, "HyperBounce-Tracer", msg)
    }
}
