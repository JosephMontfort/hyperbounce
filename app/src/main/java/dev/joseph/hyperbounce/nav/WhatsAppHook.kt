package dev.joseph.hyperbounce.nav

import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.FakeLifecycleOwner
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor

class WhatsAppHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageReadyParam
) {
    private var lastUiMode = -1
    private var externalTabState: MutableState<Int>? = null
    private val hyperBouncePrefs = HyperBouncePrefs(module)

    fun onPackageReady() {
        if (!hyperBouncePrefs.moduleEnabled || !hyperBouncePrefs.appEnabled("whatsapp")) {
            module.log(Log.INFO, "HyperBounce", "disabled for WhatsApp, skipping injection")
            return
        }

        try {
            module.hook(
                View::class.java.getDeclaredMethod("onAttachedToWindow")
            ).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                
                if (view.id == View.NO_ID || view.resources == null) return@intercept result
                
                try {
                    val resName = view.resources.getResourceEntryName(view.id)
                    if (resName == "fab" || resName == "extended_mini_fab") {
                        val lp = view.layoutParams
                        if (lp is ViewGroup.MarginLayoutParams) {
                            if (lp.bottomMargin < 200) {
                                lp.bottomMargin += 200
                                view.layoutParams = lp
                            }
                        }
                    }
                } catch (e: Exception) {}
                
                result
            }
        } catch (t: Throwable) {}

        try {
            val wdsClass = param.classLoader.loadClass("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar")
            wdsClass.declaredConstructors.forEach { constructor: Constructor<*> ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    val stockBar = chain.thisObject as ViewGroup
                    stockBar.alpha = 0f

                    stockBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            stockBar.post { executeUIHeist(stockBar) }

                            stockBar.viewTreeObserver.addOnPreDrawListener(
                                object : ViewTreeObserver.OnPreDrawListener {
                                    override fun onPreDraw(): Boolean {
                                        val parentVis = (stockBar.parent as? View)?.visibility ?: -1
                                        val injected = stockBar.rootView as ViewGroup
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
                    result
                }
            }
        } catch (t: Throwable) {}
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
            module.log(Log.INFO, "HyperBounce", "Injecting overlay…")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            
            val rootGroup = rootView as ViewGroup
            if (rootGroup.findViewTreeLifecycleOwner() == null) rootGroup.setViewTreeLifecycleOwner(lifecycleOwner)
            if (rootGroup.findViewTreeSavedStateRegistryOwner() == null) rootGroup.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (rootGroup.findViewTreeViewModelStoreOwner() == null) rootGroup.setViewTreeViewModelStoreOwner(lifecycleOwner)

            val tabState = mutableStateOf(0)
            externalTabState = tabState

            val backdropSource = rootGroup.findViewById<View>(android.R.id.content) ?: rootGroup

            val composeView = ComposeView(context).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    WhatsAppNavBarHost(
                        stockBar = stockBar,
                        externalTabState = tabState,
                        backdropSource = backdropSource,
                        hyperBouncePrefs = hyperBouncePrefs,
                    )
                }
            }

            rootGroup.addView(composeView)
            module.log(Log.INFO, "HyperBounce", "Overlay attached.")
        } catch (e: Exception) {
            module.log(Log.ERROR, "HyperBounce", "Exception in executeUIHeist: ${e.message ?: "Unknown"}")
        }
    }
}
