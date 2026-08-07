package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowInsets
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
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object NavSync {
    val externalSelectedTab = mutableIntStateOf(0)
}

class PhysicsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.packageName.contains("instagram")) return

        Log.e("HyperBounce-IG", "Target acquired: ${lpparam.packageName}")

        // 1. Lie to Litho about Tab Bar Dimensions globally
        try {
            XposedHelpers.findAndHookMethod(
                Resources::class.java,
                "getDimensionPixelSize",
                Int::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val res = param.thisObject as Resources
                        val id = param.args[0] as Int
                        try {
                            val resName = res.getResourceEntryName(id)
                            if (resName.contains("tab_bar_height") || 
                                resName.contains("bottom_nav") || 
                                resName.contains("bottom_padding")) {
                                return 0
                            }
                        } catch (e: Exception) {}
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    }
                }
            )
        } catch (t: Throwable) {}

        // 2. Direct Class Hook for TouchInterceptorLinearLayout (The Stock Bar)
        try {
            val touchLayoutClass = XposedHelpers.findClassIfExists(
                "com.instagram.common.ui.widget.touchinterceptorlayout.TouchInterceptorLinearLayout",
                lpparam.classLoader
            )
            
            if (touchLayoutClass != null) {
                XposedBridge.hookAllConstructors(touchLayoutClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stockBar = param.thisObject as ViewGroup
                        
                        stockBar.post {
                            // Verify this is actually the tab_bar by checking resource name or height bounds
                            try {
                                val resName = stockBar.resources.getResourceEntryName(stockBar.id)
                                if (resName == "tab_bar") {
                                    Log.e("HyperBounce-IG", "[+] Direct TouchInterceptorLinearLayout constructor caught: tab_bar")
                                    applyBarNuking(stockBar)
                                }
                            } catch (e: Throwable) {
                                // Fallback: if ID check fails, check if parent/dimensions match bottom bar profile
                                if (stockBar.height > 0 && stockBar.height < 200) {
                                    applyBarNuking(stockBar)
                                }
                            }
                        }
                    }
                })
                Log.e("HyperBounce-IG", "[+] Successfully hooked TouchInterceptorLinearLayout constructors!")
            }
        } catch (t: Throwable) {
            Log.e("HyperBounce-IG", "[-] Failed to hook TouchInterceptorLinearLayout: ${t.message}")
        }
    }

    private fun applyBarNuking(stockBar: ViewGroup) {
        stockBar.translationY = 5000f
        stockBar.alpha = 0f
        stockBar.isClickable = false
        stockBar.isEnabled = false

        stockBar.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                stockBar.translationY = 5000f
                stockBar.alpha = 0f
                stockBar.isClickable = false
                stockBar.isEnabled = false
                
                stockBar.layoutParams?.let { lp ->
                    if (lp.height != 0) {
                        lp.height = 0
                        stockBar.requestLayout()
                    }
                }
                return true
            }
        })

        val context = stockBar.context
        if (context is Activity) {
            executeUIHeist(context, context.window.decorView as ViewGroup, stockBar)
        }
    }

    private fun executeUIHeist(activity: Activity, rootGroup: ViewGroup, stockBar: ViewGroup) {
        try {
            if (rootGroup.findViewWithTag<View>("HyperFloatingBar") != null) return

            val context = activity
            val density = context.resources.displayMetrics.density
            val displayWidth = context.resources.displayMetrics.widthPixels

            val lifecycleOwner = FakeLifecycleOwner()
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            try {
                rootGroup.setViewTreeLifecycleOwner(lifecycleOwner)
                rootGroup.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                rootGroup.setViewTreeViewModelStoreOwner(lifecycleOwner)
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

            rootGroup.clipChildren = false
            rootGroup.clipToPadding = false
            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            
            Log.e("HyperBounce-IG", "[*] Custom iOS Liquid Glass Bar Injected Successfully!")
        } catch (e: Exception) {
            Log.e("HyperBounce-IG", "Heist Exception: " + e.message)
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
