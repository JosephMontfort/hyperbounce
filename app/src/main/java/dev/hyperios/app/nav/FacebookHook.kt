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
    companion object {
        private const val TAG = "HyperiOS"
        val sReelsOverlayParents = java.util.WeakHashMap<ViewGroup, Boolean>()
        @Volatile var isReelsActive: Boolean = false
    }

    private val hyperBouncePrefs = HyperBouncePrefs(module)
    private var isOverlayHidden = false
    private var activeFloatingBar: ComposeView? = null
    private val shiftedReelContainers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ViewGroup, Boolean>())

    fun onPackageReady() {
        val enabled = hyperBouncePrefs.appEnabled("facebook")
        Log.i("HyperBounce", "FacebookHook: onPackageReady called, enabled=$enabled")
        if (!enabled) {
            module.log(Log.INFO, TAG, "disabled for Facebook, skipping injection")
            return
        }

        Log.i("HyperBounce", "FacebookHook: Initializing Facebook Floating Liquid Navigation Bar Hook")
        module.log(Log.INFO, TAG, "Initializing Facebook Floating Liquid Navigation Bar Hook")

        // 1. Hook dispatchApplyWindowInsets to eliminate duplicate bottom padding on main content
        try {
            Log.i("HyperBounce", "FacebookHook: Registering dispatchApplyWindowInsets hook")
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
            Log.e("HyperBounce", "FacebookHook: Failed to hook dispatchApplyWindowInsets: ${t.message}", t)
            module.log(Log.WARN, TAG, "Failed to hook dispatchApplyWindowInsets: ${t.message}")
        }

        // 2. Hook TabBarContainerLayout constructors and onMeasure directly
        try {
            Log.i("HyperBounce", "FacebookHook: Registering TabBarContainerLayout hooks")
            val tabBarClass = param.classLoader.loadClass("com.facebook.navigation.tabbar.ui.TabBarContainerLayout")
            for (ctor in tabBarClass.declaredConstructors) {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    val tabBar = chain.thisObject as? ViewGroup ?: return@intercept result
                    Log.i("HyperBounce", "FacebookHook: TabBarContainerLayout constructor intercepted: $tabBar")
                    suppressNativeTabBar(tabBar)
                    tabBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            Log.i("HyperBounce", "FacebookHook: TabBarContainerLayout onViewAttachedToWindow listener fired")
                            suppressNativeTabBar(tabBar)
                            tabBar.post { executeUIHeist(tabBar) }
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    })
                    result
                }
            }

            try {
                val onMeasureMethod = tabBarClass.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                module.hook(onMeasureMethod).intercept { chain ->
                    chain.proceed()
                    val view = chain.thisObject as? View
                    val setMeasuredDimension = View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    setMeasuredDimension.isAccessible = true
                    setMeasuredDimension.invoke(view, 0, 0)
                }
            } catch (t: Throwable) {
                module.log(Log.WARN, TAG, "Failed to hook TabBarContainerLayout.onMeasure: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.w("HyperBounce", "FacebookHook: TabBarContainerLayout class hook not ready or not found: ${t.message}")
            module.log(Log.WARN, TAG, "TabBarContainerLayout class hook not ready or not found: ${t.message}")
        }

        // 3. Hook FbMainTabActivity for fullscreen overlays, video player, and dialog autohide
        try {
            Log.i("HyperBounce", "FacebookHook: Registering FbMainTabActivity lifecycle/overlay hooks")
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
            module.log(Log.WARN, TAG, "FbMainTabActivity hooks: ${t.message}")
        }
    }

    private fun suppressNativeTabBar(tabBar: ViewGroup) {
        tabBar.visibility = View.GONE
        tabBar.alpha = 0f
        tabBar.translationY = -9999f
        tabBar.layoutParams?.let { lp ->
            if (lp.height != 0) {
                lp.height = 0
                tabBar.requestLayout()
            }
        }
        val parent = tabBar.parent as? ViewGroup
        if (parent != null && parent.id != android.R.id.content) {
            parent.visibility = View.GONE
            parent.alpha = 0f
            parent.translationY = -9999f
            parent.layoutParams?.let { lp ->
                if (lp.height != 0) {
                    lp.height = 0
                    parent.requestLayout()
                }
            }
            val grandParent = parent.parent as? ViewGroup
            if (grandParent != null) {
                val idx = grandParent.indexOfChild(parent)
                if (idx != -1 && idx + 1 < grandParent.childCount) {
                    val nextSibling = grandParent.getChildAt(idx + 1)
                    if (nextSibling != null) {
                        val h = nextSibling.height
                        val lph = nextSibling.layoutParams?.height ?: 0
                        if (h in 1..4 || lph in 1..4) {
                            nextSibling.visibility = View.GONE
                            nextSibling.layoutParams?.let { lp ->
                                if (lp.height != 0) {
                                    lp.height = 0
                                    nextSibling.requestLayout()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setBarVisibility(activity: Activity?, visibility: Int, isOverlay: Boolean = false) {
        if (isOverlay) {
            isOverlayHidden = (visibility == View.GONE)
        }
        activity?.runOnUiThread {
            val bar = activeFloatingBar ?: activity.window?.decorView?.findViewWithTag<View>("HyperFloatingBar")
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
        val activity = findActivity(tabBarContainer) ?: run {
            Log.w("HyperBounce", "FacebookHook: executeUIHeist failed - could not find activity for $tabBarContainer")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w("HyperBounce", "FacebookHook: executeUIHeist aborted - activity is finishing or destroyed")
            return
        }

        val decorView = (activity.window?.decorView as? ViewGroup) ?: run {
            Log.w("HyperBounce", "FacebookHook: executeUIHeist aborted - decorView is null")
            return
        }
        if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) {
            Log.d("HyperBounce", "FacebookHook: HyperFloatingBar already attached to decorView")
            return
        }

        Log.i("HyperBounce", "FacebookHook: executeUIHeist running on activity ${activity.javaClass.name}")

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

                val backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView

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
                            backdropSource = backdropSource,
                            hyperBouncePrefs = hyperBouncePrefs
                        )
                    }
                }

                activeFloatingBar = composeView
                decorView.addView(composeView)
                Log.i("HyperBounce", "FacebookHook: composeView attached to decorView successfully (backdropSource=${backdropSource.javaClass.simpleName})")
                module.log(Log.INFO, TAG, "Attached Facebook Liquid Navigation Bar overlay to decorView")

                // Handle keyboard / IME via listener instead of per-frame OnPreDrawListener
                composeView.setOnApplyWindowInsetsListener { v, insets ->
                    val imeBottom = try {
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                    } catch (_: Throwable) { 0 }

                    val targetVisibility = if (imeBottom > 200 || isOverlayHidden) View.GONE else View.VISIBLE
                    if (v.visibility != targetVisibility) {
                        v.visibility = targetVisibility
                    }
                    insets
                }

                setupReelsWatcher(activity)
            } catch (e: Exception) {
                Log.e("HyperBounce", "FacebookHook: Exception in executeUIHeist: ${e.message}", e)
                module.log(Log.ERROR, TAG, "Exception in executeUIHeist: ${e.message}")
            }
        }
    }

    private fun setupReelsWatcher(activity: Activity) {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val key = 0x7F0B9902
        if (contentView.getTag(key) != null) return
        contentView.setTag(key, true)

        val updateReelsTranslation = {
            applyReelsTranslation(contentView)
        }

        contentView.viewTreeObserver.addOnScrollChangedListener(updateReelsTranslation)
        contentView.viewTreeObserver.addOnGlobalLayoutListener(updateReelsTranslation)
    }

    private fun findReelContainers(root: ViewGroup, outList: MutableList<ViewGroup>) {
        if (root.visibility != View.VISIBLE) return
        if (root.tag == "HyperFloatingBar") return

        val desc = root.contentDescription?.toString()
        if (desc != null && (desc.equals("Reel", ignoreCase = true) || desc.startsWith("Reel", ignoreCase = true))) {
            outList.add(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ViewGroup) {
                findReelContainers(child, outList)
            }
        }
    }

    private fun applyReelsTranslation(root: ViewGroup) {
        if (isReelsActive) {
            val containers = mutableListOf<ViewGroup>()
            findReelContainers(root, containers)
            for (reelButton in containers) {
                reelButton.clipChildren = false
                reelButton.clipToPadding = false
                (reelButton.parent as? ViewGroup)?.clipChildren = false

                if (reelButton.childCount >= 3) {
                    val child0 = reelButton.getChildAt(0)
                    val child1 = reelButton.getChildAt(1)
                    val child2 = reelButton.getChildAt(2)

                    if (child0.translationY != 0f) child0.translationY = 0f
                    if (child1.translationY != 0f) child1.translationY = 0f
                    if (child2.translationY != -190f) {
                        child2.translationY = -190f
                    }
                    shiftedReelContainers.add(reelButton)
                }
            }
        } else {
            if (shiftedReelContainers.isNotEmpty()) {
                val it = shiftedReelContainers.iterator()
                while (it.hasNext()) {
                    val container = it.next()
                    if (container != null && container.childCount >= 3) {
                        val child2 = container.getChildAt(2)
                        if (child2.translationY != 0f) {
                            child2.translationY = 0f
                        }
                    }
                }
                shiftedReelContainers.clear()
            }
        }
    }
}
