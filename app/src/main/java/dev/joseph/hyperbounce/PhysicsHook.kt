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

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp") return

        val wdsBottomBarClass = XposedHelpers.findClassIfExists(
            "com.whatsapp.ui.wds.components.bottombar.WDSBottomBar",
            lpparam.classLoader,
        ) ?: return

        XposedBridge.hookAllConstructors(wdsBottomBarClass, object : XC_MethodHook() {
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
                                    val transY   = stockBar.translationY

                                    val injectedRoot = stockBar.rootView as ViewGroup
                                    val customBar = injectedRoot.findViewWithTag<View>("HyperFloatingBar")
                                    val blurView  = injectedRoot.findViewWithTag<View>("HyperFloatingBarBlur")

                                    if (customBar != null) {
                                        val shouldHide =
                                            stockBar.visibility == View.GONE ||
                                            parentVis == View.GONE ||
                                            transY > 50f
                                        val vis = if (shouldHide) View.GONE else View.VISIBLE
                                        customBar.visibility = vis
                                        blurView?.visibility  = vis
                                    }

                                    // keep stock bar invisible and height-collapsed
                                    stockBar.alpha = 0f
                                    stockBar.layoutParams?.let { lp ->
                                        if (lp.height != 0) {
                                            lp.height = 0
                                            stockBar.requestLayout()
                                        }
                                    }

                                    // reinject after dark/light mode change
                                    val ctx = stockBar.context
                                    if (ctx != null) {
                                        val nightMask = Configuration.UI_MODE_NIGHT_MASK
                                        val current   = ctx.resources.configuration.uiMode and nightMask
                                        if (lastUiMode != -1 && lastUiMode != current) {
                                            lastUiMode = current
                                            injectedRoot.removeView(customBar)
                                            injectedRoot.removeView(blurView)
                                            executeUIHeist(stockBar)
                                        } else if (lastUiMode == -1) {
                                            lastUiMode = current
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

    private fun executeUIHeist(stockBar: ViewGroup) {
        try {
            val rootView = stockBar.rootView as View
            if (rootView.findViewWithTag<View>("HyperFloatingBar") != null) return

            val context     = stockBar.context
            val density     = context.resources.displayMetrics.density
            val displayWidth = context.resources.displayMetrics.widthPixels

            val isDark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // System nav bar height in px — used to align the blur pill with
            // the Compose pill which uses WindowInsets.navigationBars.
            val navBarHeightPx = getNavBarHeight(context, density)

            // Pill metrics (must match FloatingBottomBar defaults):
            //   height = 64dp, bottom clearance = 12dp above nav bar.
            val pillHeightPx     = (64 * density).toInt()
            val pillBottomMargin = navBarHeightPx + (12 * density).toInt()

            // Width: make the blur view wider than the pill so it always
            // covers even if IntrinsicSize.Min resolves slightly wider than
            // our estimate.  Extra width beyond the pill is hidden by the
            // Compose layer's transparency (no pill drawn there).
            // 80 % covers 4-tab pills across all common screen widths.
            val blurWidthPx = (displayWidth * 0.80f).toInt()

            // ── Layer 1: frosted-glass base ───────────────────────────────
            //
            // This View lives BELOW the ComposeView in the Android view
            // hierarchy.  Because the Compose pill is now semi-transparent
            // (40 % containerColor), the OS compositor blends this View's
            // rendered pixels with the pill, producing the frosted look.
            //
            // RenderEffect.createBlurEffect blurs this View's OWN draw
            // output (background colour + children).  The blurry tinted
            // background visible through the 60 % transparent Compose pill
            // reads as real frosted glass to the eye.
            //
            // Colour: 18 % white (light) / 18 % black (dark) — enough
            // tint to separate the pill from the screen without hiding it.
            val blurView = View(context).apply {
                tag = "HyperFloatingBarBlur"
                layoutParams = FrameLayout.LayoutParams(blurWidthPx, pillHeightPx).apply {
                    gravity     = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = pillBottomMargin
                }

                setBackgroundColor(
                    Color.parseColor(if (isDark) "#2DFFFFFF" else "#2D000000")
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Blur radius ≈ 24 dp  (strong enough to read as frosted)
                    val blurRadius = 24f * density
                    setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(
                            blurRadius, blurRadius,
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                    )
                }

                // Clip to pill shape so blurred tint doesn't bleed outside.
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0, 0, view.width, view.height,
                            view.height / 2f,
                        )
                    }
                }
                clipToOutline = true
            }

            // ── Layer 2: Compose UI (glass decorations + physics) ─────────
            //
            // Full-screen MATCH_PARENT so WindowInsets work correctly and
            // the Alignment.BottomCenter in LiquidNavBarHost can place the
            // pill at exactly the right height.
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

            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                clipChildren  = false
                clipToPadding = false
                layoutParams  = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setContent { LiquidNavBarHost(stockBar) }
            }

            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            XposedBridge.log("[HyperBounce] Layers attached. navBar=${navBarHeightPx}px")

        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception: ${e.message}")
        }
    }

    private fun getNavBarHeight(context: android.content.Context, density: Float): Int {
        val resId = context.resources.getIdentifier(
            "navigation_bar_height", "dimen", "android"
        )
        return if (resId > 0) context.resources.getDimensionPixelSize(resId)
               else (48 * density).toInt()
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
