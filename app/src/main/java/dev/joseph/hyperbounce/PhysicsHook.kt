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

    // Shared state written by the pre-draw tab-detection loop,
    // read by the Compose composition inside LiquidNavBarHost.
    // Exposed as State<Int> so Compose observes changes reactively.
    private var externalTabState: MutableState<Int>? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp") return

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

                                    // ── Visibility mirror ─────────────────
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

                                    // Keep stock bar invisible + zero-height
                                    stockBar.alpha = 0f
                                    stockBar.layoutParams?.let { lp ->
                                        if (lp.height != 0) {
                                            lp.height = 0
                                            stockBar.requestLayout()
                                        }
                                    }

                                    // ── Tab detection ─────────────────────
                                    // Detect which WhatsApp tab is visually
                                    // selected so swipe-based navigation
                                    // updates our pill indicator too.
                                    detectWhatsAppTab(stockBar)?.let { idx ->
                                        val state = externalTabState
                                        if (state != null && state.value != idx) {
                                            state.value = idx
                                        }
                                    }

                                    // ── Theme-change re-inject ────────────
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

    /**
     * Returns the index of the tab WhatsApp currently considers selected,
     * or null if detection fails.  Checks isSelected → isActivated → alpha
     * so it works across different WhatsApp versions.
     */
    private fun detectWhatsAppTab(stockBar: ViewGroup): Int? {
        val menu = stockBar.getChildAt(0) as? ViewGroup ?: return null
        // isSelected
        for (i in 0 until menu.childCount) {
            if (menu.getChildAt(i).isSelected) return i
        }
        // isActivated
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

            // ── Lifecycle owner ───────────────────────────────────────────
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

            // ── Tab state ─────────────────────────────────────────────────
            // mutableStateOf is safe to create off-composition; Compose will
            // observe it reactively inside LiquidNavBarHost.
            val tabState = mutableStateOf(0)
            externalTabState = tabState

            // ── WhatsApp content view ─────────────────────────────────────
            // The DecorView's first child is WhatsApp's entire UI, added by
            // the framework before our injection.  Drawing it gives us the
            // actual chat content WITHOUT our Compose overlay.
            val whatsAppContent: View? = rootGroup.getChildAt(0)

            // ── Compose overlay (full-screen, transparent background) ─────
            // No separate blur View is needed: ContentCaptureBackdrop handles
            // the real-time capture + downscale-blur, and miuix's drawBackdrop
            // composites it correctly with the glass effects.
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
                        stockBar      = stockBar,
                        externalTabState = tabState,
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
