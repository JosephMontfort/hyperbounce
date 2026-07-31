package dev.joseph.hyperbounce.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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

class FakeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun attachToView(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}

data class ExtractedTab(val label: String, val icon: Drawable, val index: Int, val stockView: View)

object ComposeBridge {
    @JvmStatic
    fun createFloatingBar(
        context: Context,
        rootView: View, // NEW: We must intercept the DecorView
        tabs: List<ExtractedTab>,
        onTabSelected: (Int) -> Unit
    ): View {
        val composeView = ComposeView(context)
        
        // Prevent Compose from crashing when WhatsApp destroys the view natively
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        
        val fakeOwner = FakeLifecycleOwner()
        
        // Attach to the local compose view
        fakeOwner.attachToView(composeView)
        
        // CRITICAL FIX: The WindowRecomposer walks up the tree. 
        // We must forcefully tag WhatsApp's root DecorView with our spoofed lifecycle.
        try {
            fakeOwner.attachToView(rootView)
        } catch (e: Exception) {
            // Failsafe in case it's locked, but it usually isn't.
        }
        
        composeView.setContent {
            KernelFloatingBottomBar(
                tabs = tabs,
                onTabSelected = onTabSelected
            )
        }
        return composeView
    }
}
