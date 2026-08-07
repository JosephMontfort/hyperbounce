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

        Log.e("HyperBounce-IG", "Target acquired: \${lpparam.packageName}")

        // 1. Lie to Litho about Tab Bar Dimensions globally (The Matrix Architect)
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
            Log.e("HyperBounce-IG", "[+] Hooked Resources: Litho now believes bottom UI is 0px.")
        } catch (t: Throwable) {
            Log.e("HyperBounce-IG", "[-] Failed to hook Resources: " + t.message)
        }

        // 2. Lie about OS Navigation Bar Insets (nullify system-level bottom padding)
        try {
            XposedHelpers.findAndHookMethod(
                WindowInsets::class.java,
                "getSystemWindowInsetBottom",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return 0
                    }
                }
            )
            Log.e("HyperBounce-IG", "[+] Hooked WindowInsets: OS Nav Bar inset nullified.")
        } catch (t: Throwable) {}

        // 3. Activity Lifecycle Hook for Ghosting & UI Injection
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val className = activity.javaClass.name
                        
                        if (className.contains("MainActivity") || className.contains("MainTabActivity")) {
                            val decorView = activity.window.decorView as ViewGroup
                            
                            decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                                override fun onPreDraw(): Boolean {
                                    ghostTabBar(activity)
                                    return true
                                }
                            })
                            
                            // Inject our custom Liquid Glass bar
                            executeUIHeist(activity, decorView)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e("HyperBounce-IG", "[-] Failed to hook Activity: " + e.message)
        }
    }

    private fun ghostTabBar(activity: Activity) {
        val res = activity.resources
        val pkg = activity.packageName
        
        val hideIds = arrayOf("tab_bar", "tab_bar_shadow")
        for (idName in hideIds) {
            val resId = res.getIdentifier(idName, "id", pkg)
            if (resId != 0) {
                val view = activity.findViewById<View>(resId)
                if (view != null && view.translationY != 5000f) {
                    view.translationY = 5000f
                    view.alpha = 0f
                    view.isClickable = false
                    view.isEnabled = false
                }
            }
        }
    }

    private fun executeUIHeist(activity: Activity, rootGroup: ViewGroup) {
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

            // Fetch the stockBar to pass to LiquidNavBarHost for ghost clicks
            val resId = activity.resources.getIdentifier("tab_bar", "id", activity.packageName)
            val stockBar = if (resId != 0) activity.findViewById<ViewGroup>(resId) else null

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
                setContent { 
                    if (stockBar != null) {
                        LiquidNavBarHost(stockBar) 
                    }
                }
            }

            rootGroup.clipChildren = false
            rootGroup.clipToPadding = false
            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            
            Log.e("HyperBounce-IG", "[*] Custom iOS Liquid Glass Bar Injected!")
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
