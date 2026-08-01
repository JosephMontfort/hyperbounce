package dev.joseph.hyperbounce

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {
    private var lastUiMode = -1

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp") return

        val wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader) ?: return

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
                                val blurView = injectedRootView.findViewWithTag<View>("HyperFloatingBarBlur")

                                if (customBar != null) {
                                    val shouldHide = stockBar.visibility == View.GONE || parentVis == View.GONE || transY > 50f
                                    customBar.visibility = if (shouldHide) View.GONE else View.VISIBLE
                                    blurView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
                                }

                                stockBar.alpha = 0f
                                stockBar.layoutParams?.let { lp ->
                                    if (lp.height != 0) { lp.height = 0; stockBar.requestLayout() }
                                }

                                val ctx = stockBar.context
                                if (ctx != null) {
                                    val currentUiMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        lastUiMode = currentUiMode
                                        if (customBar != null) injectedRootView.removeView(customBar)
                                        if (blurView != null) injectedRootView.removeView(blurView)
                                        executeUIHeist(stockBar)
                                    } else if (lastUiMode == -1) lastUiMode = currentUiMode
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
            val rootView = stockBar.rootView as View
            if (rootView.findViewWithTag<View>("HyperFloatingBar") != null) return

            val context = stockBar.context
            val density = context.resources.displayMetrics.density
            val displayWidth = context.resources.displayMetrics.widthPixels

            val lifecycleOwner = FakeLifecycleOwner()
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            try {
                rootView.setViewTreeLifecycleOwner(lifecycleOwner)
                rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                rootView.setViewTreeViewModelStoreOwner(lifecycleOwner)
            } catch (t: Throwable) {}

            val barWidth = (displayWidth * 0.85f).toInt()
            val barHeight = (64 * density).toInt()
            val marginBot = (32 * density).toInt()

            val blurView = View(context).apply {
                tag = "HyperFloatingBarBlur"
                layoutParams = FrameLayout.LayoutParams(barWidth, barHeight).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = marginBot
                }
                val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                // Amped up opacity to 40% (#66 hex) to create a stronger resting frosted glass base
                setBackgroundColor(Color.parseColor(if (isDark) "#661F2C34" else "#66FFFFFF"))
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(40f * density, 40f * density, android.graphics.Shader.TileMode.CLAMP))
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                    }
                }
                clipToOutline = true
            }

            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                clipChildren = false // Allow Compose shadows to bleed safely
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = marginBot
                }
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setContent { LiquidNavBarHost(stockBar) }
            }

            val rootGroup = rootView as ViewGroup
            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            
        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.message)
        }
    }
}

class FakeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    init { savedStateRegistryController.performRestore(null) }
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}
