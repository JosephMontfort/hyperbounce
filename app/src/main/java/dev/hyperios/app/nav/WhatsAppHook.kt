package dev.hyperios.app.nav

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
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import kotlin.math.roundToInt

class WhatsAppHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageReadyParam
) {
    companion object {
        private const val TAG_ORIG_BOTTOM_MARGIN = 0x7e000001
        private const val TAG_FAB_LISTENER_ATTACHED = 0x7e000002

        private val TARGET_FAB_IDS = setOf(
            0x7f0b1646, // fab
            0x7f0b163c, // extended_mini_fab
            0x7f0b1649, // fab_second
            0x7f0b1648, // fab_request_payment
        )

        private val TARGET_FAB_NAMES = setOf(
            "fab",
            "extended_mini_fab",
            "fab_second",
            "fab_request_payment",
        )
    }

    private var lastUiMode = -1
    private var externalTabState: MutableState<Int>? = null
    private val hyperBouncePrefs = HyperBouncePrefs(module)

    fun onPackageReady() {
        if (!hyperBouncePrefs.appEnabled("whatsapp")) {
            module.log(Log.INFO, "HyperiOS", "disabled for WhatsApp, skipping injection")
            return
        }

        try {
            module.hook(
                View::class.java.getDeclaredMethod("onAttachedToWindow")
            ).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                checkAndAdjustFab(view)
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

    private fun isTargetFab(view: View): Boolean {
        if (view.id in TARGET_FAB_IDS) return true
        try {
            if (view.id != View.NO_ID && view.resources != null) {
                val resName = view.resources.getResourceEntryName(view.id)
                if (resName in TARGET_FAB_NAMES) return true
            }
        } catch (_: Throwable) {}

        val clsName = view.javaClass.name
        if (clsName == "com.whatsapp.ui.wds.components.fab.WDSFab" ||
            clsName == "com.whatsapp.home.ExtendedMiniFab" ||
            clsName.endsWith(".WDSFab") ||
            clsName.endsWith(".ExtendedMiniFab")
        ) {
            return true
        }

        return false
    }

    private fun adjustFabMargin(view: View) {
        if (!isTargetFab(view)) return
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return

        val origMargin = (view.getTag(TAG_ORIG_BOTTOM_MARGIN) as? Int)
            ?: lp.bottomMargin.also { view.setTag(TAG_ORIG_BOTTOM_MARGIN, it) }

        val density = view.resources?.displayMetrics?.density ?: 1f
        val barHeightDp = hyperBouncePrefs.appBarHeight("whatsapp")
        val bottomMarginDp = hyperBouncePrefs.appBottomMargin("whatsapp")
        val shiftDp = ((barHeightDp + bottomMarginDp + 14f) - 16f).coerceAtLeast(0f)
        val shiftPx = (shiftDp * density).roundToInt()

        val targetMargin = origMargin + shiftPx
        if (lp.bottomMargin != targetMargin) {
            lp.bottomMargin = targetMargin
            view.layoutParams = lp
        }
    }

    private fun attachFabLayoutListener(view: View) {
        if (view.getTag(TAG_FAB_LISTENER_ATTACHED) == true) return
        view.setTag(TAG_FAB_LISTENER_ATTACHED, true)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            adjustFabMargin(v)
        }
    }

    private fun checkAndAdjustFab(view: View) {
        if (!isTargetFab(view)) return
        attachFabLayoutListener(view)
        if (view.layoutParams is ViewGroup.MarginLayoutParams) {
            adjustFabMargin(view)
        } else {
            view.post { adjustFabMargin(view) }
        }
    }

    private fun scanAndAdjustFabs(view: View) {
        if (isTargetFab(view)) {
            attachFabLayoutListener(view)
            adjustFabMargin(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanAndAdjustFabs(view.getChildAt(i))
            }
        }
    }

    private fun setupFabContainerListeners(container: View) {
        scanAndAdjustFabs(container)

        container.viewTreeObserver?.addOnGlobalLayoutListener {
            scanAndAdjustFabs(container)
        }

        if (container is ViewGroup) {
            val hierarchyListener = object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (child != null) {
                        scanAndAdjustFabs(child)
                        if (child is ViewGroup) {
                            child.setOnHierarchyChangeListener(this)
                        }
                    }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            }
            container.setOnHierarchyChangeListener(hierarchyListener)
        }
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
            module.log(Log.INFO, "HyperiOS", "Injecting overlay…")

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

            // Attach hierarchy and layout listeners to automatically catch and shift FABs
            setupFabContainerListeners(backdropSource)

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
            module.log(Log.INFO, "HyperiOS", "Overlay attached.")
        } catch (e: Exception) {
            module.log(Log.ERROR, "HyperiOS", "Exception in executeUIHeist: ${e.message ?: "Unknown"}")
        }
    }
}
