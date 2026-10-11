package dev.hyperios.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal synthetic LifecycleOwner/SavedStateRegistryOwner/ViewModelStoreOwner so a
 * ComposeView can be attached to a decor view / injected root that isn't backed by a
 * real Activity or Fragment. Shared by the floating-nav hooks (Instagram, WhatsApp,
 * YouTube — dev.hyperios.app.nav) and the toggle/slider hosts
 * (dev.hyperios.app.toggles). Deduplicated from two originally-identical
 * per-branch copies during the nav + toggles merge; see MERGE_NOTES.md.
 */
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
