package dev.hyperios.app.nav

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Insets
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class FacebookHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageReadyParam
) {
    private val hyperBouncePrefs = HyperBouncePrefs(module)
    private var isOverlayHidden = false

    fun onPackageReady() {
        if (!hyperBouncePrefs.appEnabled("facebook")) {
            module.log(Log.INFO, "HyperiOS", "disabled for Facebook, skipping injection")
            return
        }

        module.log(Log.INFO, "HyperiOS", "Initializing Facebook Floating Liquid Navigation Bar Hook")

        // 1. Hook dispatchApplyWindowInsets to eliminate duplicate bottom padding on main content
        try {
            module.hook(
                View::class.java.getDeclaredMethod("dispatchApplyWindowInsets", WindowInsets::class.java)
            ).intercept { chain ->
                val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                if (view.id == 0x7f0a13db || view.id == android.R.id.content) {
                    try {
                        val insets = chain.args[0] as? WindowInsets
                        if (insets != null) {
                            val type = WindowInsets.Type.systemBars()
                            val current = insets.getInsets(type)
                            if (current.bottom > 0) {
                                val fakeInsets = WindowInsets.Builder(insets)
                                    .setInsets(type, Insets.of(current.left, current.top, current.right, 0))
                                    .build()
                                chain.args[0] = fakeInsets
                            }
                        }
                    } catch (_: Throwable) {}
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "Failed to hook dispatchApplyWindowInsets: ${t.message}")
        }

        // 2. Hook View.onAttachedToWindow for TabBarContainerLayout, Reels footers, and FABs
        try {
            module.hook(
                View::class.java.getDeclaredMethod("onAttachedToWindow")
            ).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as? View ?: return@intercept result

                try {
                    val className = view.javaClass.name
                    if (className == "com.facebook.navigation.tabbar.ui.TabBarContainerLayout" ||
                        className.endsWith("TabBarContainerLayout")) {
                        val tabBar = view as? ViewGroup
                        if (tabBar != null) {
                            suppressNativeTabBar(tabBar)
                            tabBar.post { executeUIHeist(tabBar) }
                        }
                    }

                    if (view.id != View.NO_ID && view.resources != null) {
                        val resName = try {
                            view.resources.getResourceEntryName(view.id)
                        } catch (_: Throwable) { "" }

                        if (resName.isNotEmpty()) {
                            if (resName == "fb_shorts_viewer_footer" ||
                                resName == "reels_navigation_footer" ||
                                resName == "reels_comments_floating_cta" ||
                                resName.contains("fb_shorts_viewer_footer") ||
                                resName.contains("reels_navigation_footer")
                            ) {
                                view.translationY = -190.0f
                            }

                            if (resName == "fab" || resName == "extended_mini_fab" || resName.contains("floating_action_button")) {
                                val lp = view.layoutParams
                                if (lp is ViewGroup.MarginLayoutParams) {
                                    if (lp.bottomMargin < 200) {
                                        lp.bottomMargin += 200
                                        view.layoutParams = lp
                                    }
                                } else {
                                    view.translationY = -190.0f
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}

                result
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "Failed to hook View.onAttachedToWindow: ${t.message}")
        }

        // 3. Hook TabBarContainerLayout constructors directly to suppress immediately
        try {
            val tabBarClass = param.classLoader.loadClass("com.facebook.navigation.tabbar.ui.TabBarContainerLayout")
            for (ctor in tabBarClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    val tabBar = chain.thisObject as? ViewGroup
                    tabBar?.let { bar ->
                        bar.alpha = 0f
                        bar.translationY = -9999f
                        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                suppressNativeTabBar(bar)
                                bar.post { executeUIHeist(bar) }
                            }
                            override fun onViewDetachedFromWindow(v: View) {}
                        })
                    }
                    result
                }
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "TabBarContainerLayout class hook not ready or not found: ${t.message}")
        }

        // 4. Hook FbMainTabActivity for fullscreen overlays, video player, and dialog autohide
        try {
            val fbMainActivityClass = param.classLoader.loadClass("com.facebook.katana.activity.FbMainTabActivity")
            for (m in fbMainActivityClass.declaredMethods) {
                when {
                    m.name.startsWith("onEnterFullscreenOverlay") -> {
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            setBarVisibility(chain.thisObject as? Activity, View.GONE, isOverlay = true)
                            result
                        }
                    }
                    m.name.startsWith("onReturnFromFullscreenOverlay") -> {
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            setBarVisibility(chain.thisObject as? Activity, View.VISIBLE, isOverlay = true)
                            result
                        }
                    }
                    m.name.startsWith("onDialogShown") -> {
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            setBarVisibility(chain.thisObject as? Activity, View.GONE, isOverlay = true)
                            result
                        }
                    }
                    m.name.startsWith("onDialogDidDismiss") -> {
                        module.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            val activity = chain.thisObject as? Activity
                            val shouldShow = try {
                                val getOpenDialogs = activity?.javaClass?.getMethod("getOpenDialogs")
                                val dialogs = getOpenDialogs?.invoke(activity) as? Collection<*>
                                dialogs?.isEmpty() ?: true
                            } catch (_: Throwable) {
                                true
                            }
                            if (shouldShow) {
                                setBarVisibility(activity, View.VISIBLE, isOverlay = true)
                            }
                            result
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "FbMainTabActivity hooks: ${t.message}")
        }
    }

    private fun suppressNativeTabBar(tabBar: ViewGroup) {
        tabBar.alpha = 0f
        tabBar.translationY = -9999f
        tabBar.layoutParams?.let { lp ->
            if (lp.height != 0) {
                lp.height = 0
                tabBar.requestLayout()
            }
        }

        val key = 0x7F0B9901
        try {
            if (tabBar.getTag(key) == null) {
                tabBar.setTag(key, true)
                tabBar.viewTreeObserver.addOnPreDrawListener {
                    tabBar.alpha = 0f
                    tabBar.translationY = -9999f
                    tabBar.layoutParams?.let { lp ->
                        if (lp.height != 0) {
                            lp.height = 0
                            tabBar.requestLayout()
                        }
                    }
                    true
                }
            }
        } catch (_: Throwable) {}
    }

    private fun setBarVisibility(activity: Activity?, visibility: Int, isOverlay: Boolean = false) {
        if (isOverlay) {
            isOverlayHidden = (visibility == View.GONE)
        }
        activity?.runOnUiThread {
            val bar = activity.window?.decorView?.findViewWithTag<View>("HyperFloatingBar")
            bar?.visibility = visibility
        }
    }

    private fun findActivity(view: View): Activity? {
        var ctx = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun executeUIHeist(tabBarContainer: ViewGroup) {
        val activity = findActivity(tabBarContainer) ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val decorView = (activity.window?.decorView as? ViewGroup) ?: return
        if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

        activity.runOnUiThread {
            try {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return@runOnUiThread

                val lifecycleOwner = FakeLifecycleOwner().apply {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }

                if (decorView.findViewTreeLifecycleOwner() == null) decorView.setViewTreeLifecycleOwner(lifecycleOwner)
                if (decorView.findViewTreeSavedStateRegistryOwner() == null) decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                if (decorView.findViewTreeViewModelStoreOwner() == null) decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)

                val composeView = ComposeView(activity).apply {
                    tag = "HyperFloatingBar"
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setContent {
                        FacebookNavBarHost(
                            tabBarContainer = tabBarContainer,
                            backdropSource = decorView,
                            hyperBouncePrefs = hyperBouncePrefs
                        )
                    }
                }

                decorView.addView(composeView)
                module.log(Log.INFO, "HyperiOS", "Attached Facebook Liquid Navigation Bar overlay to decorView")

                decorView.viewTreeObserver.addOnPreDrawListener {
                    val customBar = decorView.findViewWithTag<View>("HyperFloatingBar")
                    if (customBar != null) {
                        val imeBottom = try {
                            decorView.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
                        } catch (_: Throwable) { 0 }

                        if (imeBottom > 200 || isOverlayHidden) {
                            if (customBar.visibility != View.GONE) customBar.visibility = View.GONE
                        } else {
                            if (customBar.visibility != View.VISIBLE) customBar.visibility = View.VISIBLE
                        }
                    }
                    true
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, "HyperiOS", "Exception in executeUIHeist: ${e.message}")
            }
        }
    }
}
