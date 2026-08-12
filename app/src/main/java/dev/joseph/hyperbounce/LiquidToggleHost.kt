package dev.joseph.hyperbounce

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidToggle
import kotlin.math.roundToInt
import java.util.WeakHashMap

// CRITICAL FIX 1: Provide a Fake Application context. 
// Compose specifically unwraps ContextWrappers looking for an Application class.
// This safely intercepts that lookup and prevents SystemUI's NPE.
internal class FakeApplication(base: Context) : android.app.Application() {
    init { attachBaseContext(base) }
    override fun getApplicationContext(): Context = this
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
}

internal class SafeContext(base: Context) : ContextWrapper(base) {
    private val fakeApp by lazy { FakeApplication(base) }
    override fun getApplicationContext(): Context = fakeApp
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
}

internal object LiquidToggleHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        if (hosts.containsKey(target)) return

        val parent = target.parent as? ViewGroup ?: return
        val host = Host(target, parent)
        hosts[target] = host
        host.attach()
    }

    @JvmStatic
    fun syncChecked(target: View) {
        hosts[target]?.syncChecked()
    }

    @JvmStatic
    fun syncVisibility(target: View) {
        hosts[target]?.syncVisibility()
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    private class Host(private val target: View, private val parent: ViewGroup) {
        // CRITICAL FIX 2: Stop capturing the black DecorView void. 
        // Capture the scrolling list surface instead.
        private val captureSource: View = findCaptureSource(target)
        private val backdrop = LiquidToggleViewBackdrop(captureSource)

        private var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var visible by mutableStateOf(target.visibility == View.VISIBLE)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                syncChecked()
            }
            override fun onViewDetachedFromWindow(v: View) {
                LiquidToggleHost.remove(v)
            }
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)

            // CRITICAL FIX 3: Universally strip layout clipping bounds
            disableClipping(target)

            val safeContext = SafeContext(target.context)
            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = visible,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null) {
                                if (button.isChecked != value) {
                                    button.isChecked = value
                                    button.performClick()
                                }
                                syncChecked()
                            }
                        },
                    )
                }
            }

            // CRITICAL FIX 4: UNIVERSAL NUCLEAR REPLACEMENT
            // Replaces the physical hierarchy slot exactly like the Dialer hack.
            target.layoutParams?.let { compose.layoutParams = it }
            
            // Keep target completely alive for native app touches, but visually cloaked.
            target.alpha = 0.001f
            
            // Inject Owners directly into the real View hierarchy's roots
            installOwnersIfNeeded(target.rootView)
            installOwnersIfNeeded(parent)
            installOwnersIfNeeded(compose)
            
            val index = parent.indexOfChild(target)
            parent.addView(compose, index + 1)
            composeView = compose

            target.post { backdrop.start() }
        }

        private fun disableClipping(view: View) {
            var p: View? = view
            while (p != null) {
                p.clipToOutline = false
                if (p is ViewGroup) {
                    p.clipChildren = false
                    p.clipToPadding = false
                }
                p = p.parent as? View
            }
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
        }

        fun syncVisibility() {
            val isVisible = target.visibility == View.VISIBLE
            visible = isVisible
            composeView?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            backdrop.release()
            target.alpha = 1f // Safely restore if module unhooks

            val compose = composeView
            composeView = null
            compose?.setContent { }
            parent.removeView(compose)
        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        backdrop: LiquidToggleViewBackdrop,
        visible: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (visible) {
                LiquidToggle(
                    selected = selected,
                    onSelect = onSelect,
                    backdrop = backdrop,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = LIQUID_TOGGLE_SCALE
                            scaleY = LIQUID_TOGGLE_SCALE
                        }
                        .onGloballyPositioned { coordinates ->
                            val padding = with(density) { 24.dp.toPx().roundToInt() }
                            backdrop.setTargetBounds(
                                left = coordinates.positionInWindow().x,
                                top = coordinates.positionInWindow().y,
                                width = coordinates.size.width,
                                height = coordinates.size.height,
                                paddingPx = padding,
                            )
                        },
                )
            }
        }
    }

    private fun findCaptureSource(target: View): View {
        var p = target.parent
        var candidate: View = target
        while (p is View) {
            val name = p.javaClass.simpleName
            if (name.contains("RecyclerView") || name.contains("ListView") || name.contains("ScrollView")) {
                return p
            }
            if (p.parent !is View) return candidate
            candidate = p
            p = p.parent
        }
        return candidate
    }

    private val ownerMap = WeakHashMap<View, FakeLifecycleOwner>()

    private fun installOwnersIfNeeded(attachTarget: View) {
        val owner = ownerMap[attachTarget] ?: FakeLifecycleOwner().also {
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            ownerMap[attachTarget] = it
        }
        attachTarget.setViewTreeLifecycleOwner(owner)
        attachTarget.setViewTreeSavedStateRegistryOwner(owner)
        attachTarget.setViewTreeViewModelStoreOwner(owner)
    }

    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
