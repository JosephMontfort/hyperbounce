package dev.joseph.hyperbounce

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    private var lastUiMode = -1
    private var externalTabState: MutableState<Int>? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp") return

        // 1. --- THE FAB SHIFTER ---
        // Hunts down the Meta AI and New Chat buttons to clear the Liquid Nav Bar
        try {
            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.id == View.NO_ID || view.resources == null) return
                    try {
                        val resName = view.resources.getResourceEntryName(view.id)
                        if (resName == "fab" || resName == "extended_mini_fab") {
                            // Using margin natively preserves WhatsApp's scroll-to-hide animations
                            val lp = view.layoutParams
                            if (lp is ViewGroup.MarginLayoutParams) {
                                if (lp.bottomMargin < 200) {
                                    lp.bottomMargin += 200
                                    view.layoutParams = lp
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
        } catch (t: Throwable) {}

        // 2. --- WDS BOTTOM BAR HOOK (GHOST TAB BAR & INJECT UI) ---
        val wdsClass = XposedHelpers.findClassIfExists(
            "com.whatsapp.ui.wds.components.bottombar.WDSBottomBar",
            lpparam.classLoader,
        ) ?: return

        XposedBridge.hookAllConstructors(wdsClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val stockBar = param.thisObject as ViewGroup
                stockBar.alpha = 0f

                stockBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        stockBar.post { executeUIHeist(stockBar) }

                        stockBar.viewTreeObserver.addOnPreDrawListener(
                            object : ViewTreeObserver.OnPreDrawListener {
                                override fun onPreDraw(): Boolean {
                                    val parentVis = (stockBar.parent as? View)?.visibility ?: -1
                                    val injected  = stockBar.rootView as ViewGroup
                                    val customBar = injected.findViewWithTag<View>("HyperFloatingBar")

                                    if (customBar != null) {
                                        val hide =
                                            stockBar.visibility == View.GONE ||
                                            parentVis == View.GONE ||
                                            stockBar.translationY > 50f
                                        customBar.visibility = if (hide) View.GONE else View.VISIBLE
                                    }

                                    stockBar.alpha = 0f
                                    stockBar.layoutParams?.let { lp ->
                                        if (lp.height != 0) {
                                            lp.height = 0
                                            stockBar.requestLayout()
                                        }
                                    }

                                    detectWhatsAppTab(stockBar)?.let { idx ->
                                        val state = externalTabState
                                        if (state != null && state.value != idx) {
                                            state.value = idx
                                        }
                                    }

                                    val ctx = stockBar.context
                                    if (ctx != null) {
                                        val nightMask = Configuration.UI_MODE_NIGHT_MASK
                                        val cur = ctx.resources.configuration.uiMode and nightMask
                                        if (lastUiMode != -1 && lastUiMode != cur) {
                                            lastUiMode = cur
                                            injected.removeView(customBar)
                                            executeUIHeist(stockBar)
                                        } else if (lastUiMode == -1) {
                                            lastUiMode = cur
                                        }
                                    }
                                    return true
                                }
                            }
                        )
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        })
    }

    private fun detectWhatsAppTab(stockBar: ViewGroup): Int? {
        val menu = stockBar.getChildAt(0) as? ViewGroup ?: return null
        for (i in 0 until menu.childCount) {
            if (menu.getChildAt(i).isSelected) return i
        }
        for (i in 0 until menu.childCount) {
            if (menu.getChildAt(i).isActivated) return i
        }
        return null
    }

    private fun executeUIHeist(stockBar: ViewGroup) {
        try {
            val rootView = stockBar.rootView as View
            if (rootView.findViewWithTag<View>("HyperFloatingBar") != null) return

            val context = stockBar.context
            XposedBridge.log("[HyperBounce] Injecting overlay…")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            val rootGroup = rootView as ViewGroup
            if (rootGroup.findViewTreeLifecycleOwner() == null)
                rootGroup.setViewTreeLifecycleOwner(lifecycleOwner)
            if (rootGroup.findViewTreeSavedStateRegistryOwner() == null)
                rootGroup.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (rootGroup.findViewTreeViewModelStoreOwner() == null)
                rootGroup.setViewTreeViewModelStoreOwner(lifecycleOwner)

            val tabState = mutableStateOf(0)
            externalTabState = tabState

            val composeView = ComposeView(context).apply {
                tag           = "HyperFloatingBar"
                clipChildren  = false
                clipToPadding = false
                layoutParams  = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                
                setContent {
                    LiquidNavBarHost(
                        stockBar = stockBar,
                        externalTabState = tabState
                    )
                }
            }

            rootGroup.addView(composeView)
            XposedBridge.log("[HyperBounce] Overlay attached.")

        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception in executeUIHeist: ${e.message}")
        }
    }
}

class FakeLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init { savedStateRegistryController.performRestore(null) }

    override val lifecycle: Lifecycle
        get() = registry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = store
}
