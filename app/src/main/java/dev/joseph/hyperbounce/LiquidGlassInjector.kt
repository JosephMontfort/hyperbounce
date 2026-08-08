package dev.joseph.hyperbounce

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*

class FakeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateRegistryController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    companion object {
        fun attachTo(view: View) {
            val owner = FakeLifecycleOwner()
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
        }
    }
}

fun injectLiquidGlass(activity: Activity, mainWrapper: ViewGroup, stockBar: ViewGroup?) {
    if (mainWrapper.findViewWithTag<ComposeView>("LiquidGlassInjected") != null) return

    activity.runOnUiThread {
        try {
            val composeView = ComposeView(activity).apply {
                tag = "LiquidGlassInjected"
                FakeLifecycleOwner.attachTo(this)
                setContent {
                    LiquidNavBarHost(stockBar)
                }
            }
            mainWrapper.addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            android.util.Log.e("HyperBounce-Tracer", "[SUCCESS] Liquid Glass Compose UI Injected!")
        } catch (e: Exception) {
            android.util.Log.e("HyperBounce-Tracer", "[ERROR] Compose Injection Failed: ${e.message}")
        }
    }
}
