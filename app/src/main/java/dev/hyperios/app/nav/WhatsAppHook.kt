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
import java.util.concurrent.ConcurrentHashMap

class WhatsAppHook(
    private val module: XposedModule,
    private val param: XposedModuleInterface.PackageReadyParam
) {
    companion object {
        private const val ID_EXTENDED_MINI_FAB = 0x7f0b163c // extended_mini_fab (Ask Meta AI)
        private const val ID_MAIN_FAB = 0x7f0b1646          // fab
        private const val ID_FAB_TEXT = 0x7f0b1647          // fabText (WDSExtendedFab)
        private const val ID_FAB_REQUEST_PAYMENT = 0x7f0b1648 // fab_request_payment
        private const val ID_FAB_SECOND = 0x7f0b1649        // fab_second
        private const val ID_FAB_TOOL_TIP = 0x7f0b164b      // fab_tool_tip
        private const val ID_SECOND_FAB_TOOL_TIP = 0x7f0b3489 // second_fab_tool_tip

        private val TARGET_FAB_IDS = setOf(
            ID_MAIN_FAB,
            ID_EXTENDED_MINI_FAB,
            ID_FAB_TEXT,
            ID_FAB_SECOND,
            ID_FAB_REQUEST_PAYMENT,
            ID_FAB_TOOL_TIP,
            ID_SECOND_FAB_TOOL_TIP,
        )

        private val TARGET_FAB_NAMES = setOf(
            "fab",
            "extended_mini_fab",
            "fabText",
            "fab_second",
            "fab_request_payment",
            "fab_tool_tip",
            "second_fab_tool_tip",
        )
    }

    private var lastUiMode = -1
    private var externalTabState: MutableState<Int>? = null
    private val hyperBouncePrefs = HyperBouncePrefs(module)
    private val fabIdCache = ConcurrentHashMap<Int, Boolean>()

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
            module.hook(
                View::class.java.getDeclaredMethod("setTranslationY", Float::class.javaPrimitiveType)
            ).intercept { chain ->
                val view = chain.thisObject as? View
                if (view != null && isTargetFab(view)) {
                    val ty = chain.args[0] as? Float ?: 0f
                    if (ty >= 0f) {
                        computeFabShiftPx(view)?.let { shiftPx ->
                            chain.args[0] = -shiftPx
                        }
                    }
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "Failed to hook View.setTranslationY: ${t.message}")
        }

        try {
            val tabConfigClass = param.classLoader.loadClass("X.0wf")
            try {
                val a05 = tabConfigClass.getDeclaredMethod("A05")
                module.hook(a05).intercept { true }
            } catch (t: Throwable) {
                module.log(Log.WARN, "HyperiOS", "Failed to hook X.0wf.A05: ${t.message}")
            }
            try {
                val a01 = tabConfigClass.getDeclaredMethod("A01")
                module.hook(a01).intercept { 7 }
            } catch (t: Throwable) {
                module.log(Log.WARN, "HyperiOS", "Failed to hook X.0wf.A01: ${t.message}")
            }
        } catch (t: Throwable) {
            module.log(Log.WARN, "HyperiOS", "X.0wf not found (WhatsApp version mismatch?): ${t.message}")
        }

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
        val id = view.id
        if (id in TARGET_FAB_IDS) return true

        if (id != View.NO_ID) {
            fabIdCache[id]?.let { return it }
            val clsName = view.javaClass.name
            val matched = clsName.endsWith(".WDSFab") ||
                clsName.endsWith(".ExtendedMiniFab") ||
                clsName.endsWith(".WDSExtendedFab") ||
                try {
                    val resName = view.resources?.getResourceEntryName(id)
                    resName != null && (resName in TARGET_FAB_NAMES || resName.contains("meta", ignoreCase = true))
                } catch (_: Throwable) {
                    false
                }
            fabIdCache[id] = matched
            return matched
        }

        val clsName = view.javaClass.name
        return clsName.endsWith(".WDSFab") ||
            clsName.endsWith(".ExtendedMiniFab") ||
            clsName.endsWith(".WDSExtendedFab")
    }

    private fun computeFabShiftPx(view: View): Float? {
        val density = view.resources?.displayMetrics?.density ?: return null
        val barHeightDp = hyperBouncePrefs.appBarHeight("whatsapp")
        val bottomMarginDp = hyperBouncePrefs.appBottomMargin("whatsapp")
        // The floating bar top edge is at barHeightDp + bottomMarginDp (default 70 + 40 = 110dp).
        // Stock FAB bottom margin is 16dp.
        // To place the FAB bottom 14dp above the floating bar (at 124dp from bottom of screen):
        val fabShiftDp = ((barHeightDp + bottomMarginDp + 14f) - 16f).coerceAtLeast(0f)
        return fabShiftDp * density
    }

    private fun adjustFabTranslation(view: View) {
        val targetTy = -(computeFabShiftPx(view) ?: return)
        if (view.translationY != targetTy) {
            view.translationY = targetTy
        }
    }

    private fun checkAndAdjustFab(view: View) {
        if (!isTargetFab(view)) return
        adjustFabTranslation(view)
    }

    private fun scanAndAdjustFabs(view: View) {
        if (isTargetFab(view)) {
            adjustFabTranslation(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanAndAdjustFabs(view.getChildAt(i))
            }
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

            // Direct scan and translationY adjustment for FABs already in rootGroup
            scanAndAdjustFabs(rootGroup)

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
