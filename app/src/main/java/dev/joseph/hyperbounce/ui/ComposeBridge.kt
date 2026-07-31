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
        anchorView: View, 
        tabs: List<ExtractedTab>,
        onTabSelected: (Int) -> Unit
    ): View {
        val composeView = ComposeView(context)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        
        val fakeOwner = FakeLifecycleOwner()
        
        // 1. Tag the anchor view and its current hierarchy instantly
        tagEntireTree(anchorView, fakeOwner)
        tagEntireTree(composeView, fakeOwner)
        
        // 2. Aggressively re-tag when attached to the window (This explicitly intercepts app:id/root_view)
        composeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                tagEntireTree(v, fakeOwner)
                tagEntireTree(v.rootView, fakeOwner)
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        
        composeView.setContent {
            KernelFloatingBottomBar(
                tabs = tabs,
                onTabSelected = onTabSelected
            )
        }
        return composeView
    }
    
    // Recursively climbs up the Android UI tree and forcefully injects the LifecycleOwner
    private fun tagEntireTree(startView: View, owner: FakeLifecycleOwner) {
        try {
            var current: View? = startView
            while (current != null) {
                owner.attachToView(current)
                current = current.parent as? View
            }
        } catch (e: Exception) {
            // Ignore failsafe
        }
    }
}
