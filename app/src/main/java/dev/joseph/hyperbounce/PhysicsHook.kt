package dev.joseph.hyperbounce

import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {
    private var lastUiMode = -1

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp") return

        val wdsBottomBarClass = XposedHelpers.findClassIfExists(
            "com.whatsapp.ui.wds.components.bottombar.WDSBottomBar",
            lpparam.classLoader
        ) ?: return

        XposedBridge.hookAllConstructors(wdsBottomBarClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val stockBar = param.thisObject as ViewGroup
                stockBar.alpha = 0f

                stockBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        stockBar.post { executeUIHeist(stockBar) }

                        stockBar.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                val p = stockBar.parent
                                val parentVis = if (p is View) p.visibility else -1
                                val transY = stockBar.translationY

                                val injectedRootView = stockBar.rootView as ViewGroup
                                val customBar = injectedRootView.findViewWithTag<View>("HyperFloatingBar")

                                if (customBar != null) {
                                    val shouldHide = stockBar.visibility == View.GONE || parentVis == View.GONE || transY > 50f
                                    customBar.visibility = if (shouldHide) View.GONE else View.VISIBLE
                                }

                                stockBar.alpha = 0f
                                stockBar.layoutParams?.let { lp ->
                                    if (lp.height != 0) {
                                        lp.height = 0
                                        stockBar.requestLayout()
                                    }
                                }

                                val ctx = stockBar.context
                                if (ctx != null) {
                                    val currentUiMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        lastUiMode = currentUiMode
                                        if (customBar != null) {
                                            injectedRootView.removeView(customBar)
                                        }
                                        executeUIHeist(stockBar)
                                    } else if (lastUiMode == -1) {
                                        lastUiMode = currentUiMode
                                    }
                                }
                                return true
                            }
                        })
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        })
    }

    private fun executeUIHeist(stockBar: ViewGroup) {
        try {
            val rootView = stockBar.rootView as ViewGroup
            if (rootView.findViewWithTag<View>("HyperFloatingBar") != null) return

            val context = stockBar.context
            
            // Xposed Compose Initialization
            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                
                // Construct fake lifecycle required to run Jetpack Compose outside an Activity
                val lifecycleOwner = FakeLifecycleOwner()
                lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                ViewTreeLifecycleOwner.set(this, lifecycleOwner)
                ViewTreeSavedStateRegistryOwner.set(this, lifecycleOwner)
                lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

                setContent {
                    LiquidNavBarHost(stockBar)
                }
            }

            rootView.addView(composeView)
        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.message)
        }
    }
}

class FakeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
