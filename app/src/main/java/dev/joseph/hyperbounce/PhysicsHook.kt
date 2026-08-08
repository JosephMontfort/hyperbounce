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
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object NavSync {
    val externalSelectedTab = mutableIntStateOf(0)
}

class PhysicsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.packageName.contains("instagram")) return

        Log.e("HyperBounce-IG", "[*] Targeted Instagram process: ${lpparam.packageName}")

        try {
            // Hook low-level View.onAttachedToWindow to intercept tab_bar inflation instantly
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (view.id == View.NO_ID || view.resources == null) return

                        try {
                            val entryName = view.resources.getResourceEntryName(view.id)

                            if (entryName == "tab_bar" || entryName == "tab_bar_shadow") {
                                Log.e("HyperBounce-IG", "[+] BINGO! Intercepted $entryName attached to window!")

                                // 1. Ghost the target view natively
                                view.visibility = View.GONE
                                view.translationY = 5000f
                                view.alpha = 0f
                                view.isClickable = false
                                view.isEnabled = false

                                // 2. If this is the main tab bar, trigger the Compose UI heist
                                if (entryName == "tab_bar" && view is ViewGroup) {
                                    val context = view.context
                                    if (context is Activity) {
                                        val decorView = context.window?.decorView as? ViewGroup
                                        if (decorView != null) {
                                            view.post {
                                                executeUIHeist(context, decorView, view)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            )
            Log.e("HyperBounce-IG", "[*] View.onAttachedToWindow hook active and waiting...")
        } catch (t: Throwable) {
            Log.e("HyperBounce-IG", "[-] Failed to hook View.onAttachedToWindow: ${t.message}")
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
