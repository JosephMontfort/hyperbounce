package dev.joseph.hyperbounce

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FakeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    fun start() {
        savedStateRegistryController.performRestore(Bundle())
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}

fun injectLiquidGlass(activity: Activity, mainWrapper: ViewGroup, stockBar: ViewGroup?) {
    if (mainWrapper.findViewWithTag<ComposeView>("LiquidGlassInjected") != null) return

    activity.runOnUiThread {
        try {
            val decorView = activity.window.decorView
            
            // Use Kotlin extension functions to check and attach the lifecycle
            if (decorView.findViewTreeLifecycleOwner() == null) {
                val owner = FakeLifecycleOwner().apply { start() }
                decorView.setViewTreeLifecycleOwner(owner)
                decorView.setViewTreeSavedStateRegistryOwner(owner)
                decorView.setViewTreeViewModelStoreOwner(owner)
            }

            val composeView = ComposeView(activity).apply {
                tag = "LiquidGlassInjected"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                
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
