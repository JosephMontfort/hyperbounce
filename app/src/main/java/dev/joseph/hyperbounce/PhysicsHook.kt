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
                                    val p = stockBar.parent
                                    val parentVis = if (p is View) p.visibility else -1
                                    val transY = stockBar.translationY

                                    val injectedRoot = stockBar.rootView as ViewGroup
                                    val customBar = injectedRoot.findViewWithTag<View>("HyperFloatingBar")
                                    val blurView = injectedRoot.findViewWithTag<View>("HyperFloatingBarBlur")

                                    if (customBar != null) {
                                        val shouldHide =
                                            stockBar.visibility == View.GONE ||
                                            parentVis == View.GONE ||
                                            transY > 50f
                                        val vis = if (shouldHide) View.GONE else View.VISIBLE
                                        customBar.visibility = vis
                                        blurView?.visibility = vis
                                    }

                                    // Keep stock bar invisible and collapsed
                                    stockBar.alpha = 0f
                                    stockBar.layoutParams?.let { lp ->
                                        if (lp.height != 0) {
                                            lp.height = 0
                                            stockBar.requestLayout()
                                        }
                                    }

                                    // Re-inject after theme change
                                    val ctx = stockBar.context
                                    if (ctx != null) {
                                        val nightMask = Configuration.UI_MODE_NIGHT_MASK
                                        val current = ctx.resources.configuration.uiMode and nightMask
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

            val context = stockBar.context
            val density = context.resources.displayMetrics.density
            val displayWidth = context.resources.displayMetrics.widthPixels
            val displayHeight = context.resources.displayMetrics.heightPixels

            XposedBridge.log("[HyperBounce] Injecting. w=$displayWidth h=$displayHeight d=$density")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            if (rootView.findViewTreeLifecycleOwner() == null)
                rootView.setViewTreeLifecycleOwner(lifecycleOwner)
            if (rootView.findViewTreeSavedStateRegistryOwner() == null)
                rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (rootView.findViewTreeViewModelStoreOwner() == null)
                rootView.setViewTreeViewModelStoreOwner(lifecycleOwner)

            val isDark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // ── Layer 1: Hardware-blur pill ───────────────────────────────
            // Provides the real "content-behind-the-pill" frosted-glass look.
            // The Compose layer on top adds specular highlights, lens edges,
            // and the indicator pill's glass effects on top of this.
            //
            // Width ~60% of screen, height 64dp (matches pill height).
            // A pill-radius outline clip keeps the blur contained.
            val pillHeightPx = (64 * density).toInt()
            val pillWidthPx  = (displayWidth * 0.60f).toInt()

            // Nav bar height: derive from insets if possible, else fallback 48dp
            val navBarHeightPx = getNavBarHeight(context, density)
            // Pill bottom margin = 12dp above nav bar (matches Compose padding in LiquidNavBarHost)
            val pillBottomMargin = navBarHeightPx + (12 * density).toInt()

            val blurView = View(context).apply {
                tag = "HyperFloatingBarBlur"
                layoutParams = FrameLayout.LayoutParams(pillWidthPx, pillHeightPx).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = pillBottomMargin
                }

                // Tinted frosted background — very translucent, colour matches theme
                setBackgroundColor(
                    Color.parseColor(if (isDark) "#1AFFFFFF" else "#1A000000")
                )

                // Hardware Gaussian blur (API 31+) — blurs whatever WhatsApp
                // renders behind this view through the OS compositor.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(
                            28f * density,
                            28f * density,
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                    )
                }

                // Clip to pill shape so the blur doesn't bleed outside the pill.
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                    }
                }
                clipToOutline = true
            }

            // ── Layer 2: Compose UI ───────────────────────────────────────
            // Full-width, wraps content height. The FloatingBottomBar places
            // itself at BottomCenter with the correct bottom padding.
            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                clipChildren = true
                clipToPadding = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,  // full-screen so WindowInsets work
                )

                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent { LiquidNavBarHost(stockBar) }
            }

            val rootGroup = rootView as ViewGroup
            rootGroup.addView(blurView)
            rootGroup.addView(composeView)
            XposedBridge.log("[HyperBounce] Layers attached.")

        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception: ${e.message}")
        }
    }

    /** Best-effort nav bar height in px. Falls back to 48dp if unavailable. */
    private fun getNavBarHeight(context: android.content.Context, density: Float): Int {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
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

    override val lifecycle: Lifecycle           get() = registry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}
