package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
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

object NavSync {
    val externalSelectedTab = mutableIntStateOf(0)
}

class PhysicsHook : IXposedHookLoadPackage {
    private var lastUiMode = -1

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // ALWAYS use standard Log.e for critical diagnostics to bypass LSPosed log swallowers
        Log.e("HyperBounce-Tracer", "Injected into: ${lpparam.packageName}")

        if (lpparam.packageName.contains("instagram")) {
            Log.e("HyperBounce-IG", "Target acquired: ${lpparam.packageName}")
            hookInstagram()
        } else if (lpparam.packageName == "com.whatsapp") {
            hookWhatsApp(lpparam)
        }
    }

    private fun hookInstagram() {
        try {
            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val hasFocus = param.args[0] as Boolean
                        if (!hasFocus) return
                        
                        val activity = param.thisObject as Activity
                        if (!activity.packageName.contains("instagram")) return
                        
                        val activityName = activity.javaClass.simpleName
                        val window = activity.window ?: return
                        val decorView = window.decorView
                        
                        // Only run the dump once per activity instantiation
                        if (decorView.getTag(activityName.hashCode()) != null) return
                        decorView.setTag(activityName.hashCode(), true)
                        
                        Log.e("HyperBounce-IG", "=== VIEW DUMP FOR: $activityName ===")
                        scanViewTreeForNavBar(decorView, 0)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e("HyperBounce-IG", "Failed to hook Activity: " + e.message)
        }
    }

    private fun scanViewTreeForNavBar(view: View?, depth: Int) {
        if (view == null) return
        
        val idName = try {
            if (view.id != View.NO_ID && view.resources != null) {
                view.resources.getResourceEntryName(view.id)
            } else {
                "NO_ID"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }

        val className = view.javaClass.simpleName

        // Flag anything that sounds remotely like a navigation bar
        if (idName.contains("tab", true) || idName.contains("bottom", true) || idName.contains("nav", true)) {
            val indent = "  ".repeat(depth)
            Log.e("HyperBounce-IG", "$indent[+] FOUND POTENTIAL TARGET: Class=[$className], ID=[$idName], Height=[${view.height}]")
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanViewTreeForNavBar(view.getChildAt(i), depth + 1)
            }
        }
    }

    private fun hookWhatsApp(lpparam: LoadPackageParam) {
        val wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader) ?: return
        XposedBridge.hookAllConstructors(wdsBottomBarClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                setupStockBarHook(param.thisObject as ViewGroup)
            }
        })
    }

    private fun setupStockBarHook(stockBar: ViewGroup) {
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

                        val menuContainer = stockBar.getChildAt(0) as? ViewGroup
                        if (menuContainer != null) {
                            for (i in 0 until menuContainer.childCount) {
                                val tab = menuContainer.getChildAt(i)
                                if (tab.isSelected || tab.isActivated) {
                                    if (NavSync.externalSelectedTab.intValue != i) {
                                        NavSync.externalSelectedTab.intValue = i
                                    }
                                    break
                                }
                            }
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

            val barWidth = (displayWidth * 0.68f).toInt()
            val barHeight = (64 * density).toInt()
            val marginBot = (28 * density).toInt()

            val blurView = View(context).apply {
                tag = "HyperFloatingBarBlur"
                layoutParams = FrameLayout.LayoutParams(barWidth, barHeight).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = marginBot
                }
                val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                setBackgroundColor(Color.parseColor(if (isDark) "#6018222A" else "#60FFFFFF"))
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(35f * density, 35f * density, android.graphics.Shader.TileMode.CLAMP))
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                    }
                }
                clipToOutline = true
            }

            val composeContainerHeight = (120 * density).toInt()
            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(barWidth, composeContainerHeight).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = marginBot - ((120 - 64) / 2 * density).toInt() 
                }
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setContent { LiquidNavBarHost(stockBar) }
            }

            val rootGroup = rootView as ViewGroup
            rootGroup.clipChildren = false
            rootGroup.clipToPadding = false
            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            
        } catch (e: Exception) {
            Log.e("HyperBounce", "Heist Exception: " + e.message)
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
