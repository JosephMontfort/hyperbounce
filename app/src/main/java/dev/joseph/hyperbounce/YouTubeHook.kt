package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Insets
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class YouTubeHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageLoadedParam
) {
    private val isDarkState = mutableStateOf(false)
    private var cachedInsetTargets = intArrayOf()

    fun onPackageLoaded() {
        try {
            module.hook(
                View::class.java.getDeclaredMethod("dispatchApplyWindowInsets", WindowInsets::class.java)
            ).intercept { chain ->
                val view = chain.thisObject as View
                if (view.id != View.NO_ID && view.resources != null) {
                    try {
                        if (cachedInsetTargets.isEmpty()) {
                            val res = view.resources
                            cachedInsetTargets = intArrayOf(
                                res.getIdentifier("watch_while_layout_coordinator_layout", "id", "com.google.android.youtube"),
                                res.getIdentifier("pane_fragment_container", "id", "com.google.android.youtube"),
                                res.getIdentifier("reel_watch_fragment_root", "id", "com.google.android.youtube"),
                            )
                        }
                        if (view.id in cachedInsetTargets) {
                            val insets = chain.args[0] as WindowInsets
                            val type = WindowInsets.Type.systemBars()
                            val current = insets.getInsets(type)
                            if (current.bottom > 0) {
                                val fakeInsets = WindowInsets.Builder(insets)
                                    .setInsets(type, Insets.of(current.left, current.top, current.right, 0))
                                    .build()
                                chain.args[0] = fakeInsets
                            }
                        }
                    } catch (e: Exception) {}
                }
                chain.proceed()
            }
        } catch (t: Throwable) {}

        try {
            module.hook(
                View::class.java.getDeclaredMethod("onAttachedToWindow")
            ).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                
                if (view.id == View.NO_ID || view.resources == null) return@intercept result

                try {
                    val resName = view.resources.getResourceEntryName(view.id)

                    if (resName == "bottom_bar_container" || resName == "pivot_bar") {
                        view.alpha = 0f
                        view.translationY = 9999f
                        val lp = view.layoutParams
                        if (lp != null && lp.height != 0) {
                            lp.height = 0
                            view.layoutParams = lp
                        }
                    }

                    if (resName == "fab_container") {
                        view.translationY = -190f
                    }

                    if (resName == "reel_player_footer_container" ||
                        resName == "reel_player_delegated_overlay" ||
                        resName == "reel_video_interactions"
                    ) {
                        view.translationY = -190f
                    }

                    if (resName == "watch_while_layout_coordinator_layout") {
                        val activity = view.context as? Activity
                        if (activity != null) {
                            view.post {
                                val decorView = activity.window.decorView as ViewGroup
                                val pivotId = activity.resources.getIdentifier("pivot_bar", "id", activity.packageName)
                                val pivotBar = decorView.findViewById<ViewGroup>(pivotId)
                                if (pivotBar != null) {
                                    executeUIHeist(activity, view as ViewGroup, pivotBar)

                                    activity.window.decorView.viewTreeObserver.addOnPreDrawListener {
                                        val customBar = activity.window.decorView.findViewWithTag<View>("HyperFloatingBar")
                                        if (customBar != null) {
                                            if (pivotBar.visibility != View.VISIBLE && pivotBar.translationY == 0f) {
                                                customBar.visibility = View.GONE
                                            } else {
                                                customBar.visibility = View.VISIBLE
                                            }
                                        }

                                        val currentDark = isYouTubeInDarkMode(activity)
                                        if (isDarkState.value != currentDark) {
                                            isDarkState.value = currentDark
                                        }
                                        true
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
                
                result
            }
        } catch (t: Throwable) {}
    }

    private fun isYouTubeInDarkMode(activity: Activity): Boolean {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            val color = typedValue.data
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            return luminance < 128
        }
        val uiMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun executeUIHeist(activity: Activity, rootGroup: ViewGroup, pivotBar: ViewGroup) {
        try {
            val decorView = activity.window.decorView as ViewGroup

            if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

            module.log("[HyperBounce] Injecting YouTube Liquid Bar...")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            if (decorView.findViewTreeLifecycleOwner() == null) decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            if (decorView.findViewTreeSavedStateRegistryOwner() == null) decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (decorView.findViewTreeViewModelStoreOwner() == null) decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)

            isDarkState.value = isYouTubeInDarkMode(activity)

            val tabState = mutableStateOf(0)
            val backdropSource = rootGroup

            val composeView = ComposeView(activity).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    YouTubeNavBarHost(
                        pivotBar = pivotBar,
                        externalTabState = tabState,
                        isDarkState = isDarkState,
                        backdropSource = backdropSource,
                    )
                }
            }

            val parentGroup = rootGroup.parent as? ViewGroup
            if (parentGroup != null) {
                parentGroup.addView(composeView, parentGroup.indexOfChild(rootGroup) + 1)
            } else {
                decorView.addView(composeView)
            }
            module.log("[HyperBounce] Overlay attached.")
        } catch (e: Exception) {
            module.log("[HyperBounce] Exception: ${e.message}")
        }
    }
}
