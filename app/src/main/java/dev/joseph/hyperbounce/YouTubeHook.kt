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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class YouTubeHook {
    private val isDarkState = mutableStateOf(false)
    private var cachedInsetTargets = intArrayOf()

    fun handleLoadPackage(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "dispatchApplyWindowInsets",
                WindowInsets::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (view.id == View.NO_ID || view.resources == null) return
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
                                val insets = param.args[0] as WindowInsets
                                val type = WindowInsets.Type.systemBars()
                                val current = insets.getInsets(type)
                                if (current.bottom > 0) {
                                    val fakeInsets = WindowInsets.Builder(insets)
                                        .setInsets(type, Insets.of(current.left, current.top, current.right, 0))
                                        .build()
                                    param.args[0] = fakeInsets
                                }
                            }
                        } catch (e: Exception) {}
                    }
                },
            )
        } catch (t: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.id == View.NO_ID || view.resources == null) return

                    try {
                        val resName = view.resources.getResourceEntryName(view.id)

                        if (resName == "bottom_bar_container" || resName == "pivot_bar") {
                            view.alpha = 0f
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

                                        val bottomBarId = activity.resources.getIdentifier("bottom_bar_container", "id", activity.packageName)
                                        val bottomBar = decorView.findViewById<View>(bottomBarId)

                                        activity.window.decorView.viewTreeObserver.addOnPreDrawListener {
                                            val customBar = activity.window.decorView.findViewWithTag<View>("HyperFloatingBar")
                                            if (customBar != null && bottomBar != null) {
                                                customBar.visibility = if (bottomBar.visibility == View.VISIBLE) View.VISIBLE else View.GONE
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
                }
            })
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

            XposedBridge.log("[HyperBounce] Injecting YouTube Liquid Bar...")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            if (decorView.findViewTreeLifecycleOwner() == null) decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            if (decorView.findViewTreeSavedStateRegistryOwner() == null) decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (decorView.findViewTreeViewModelStoreOwner() == null) decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)

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
            XposedBridge.log("[HyperBounce] Overlay attached.")
        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception: ${e.message}")
        }
    }
}
