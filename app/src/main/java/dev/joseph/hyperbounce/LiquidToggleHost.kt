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

// CRITICAL FIX: Swallows SystemUI's broken ContextWrapper NullPointerExceptions
// and provides a valid applicationContext fallback for Compose.
internal class SafeContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context {
        return super.getApplicationContext() ?: this
    }
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {
        try { super.registerComponentCallbacks(callback) } catch (e: Throwable) {}
    }
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {
        try { super.unregisterComponentCallbacks(callback) } catch (e: Throwable) {}
    }
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
    fun syncVisibility(target: View, isVisible: Boolean) {
        hosts[target]?.setVisible(isVisible)
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    private class Host(private val target: View, private val parent: ViewGroup) {
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

            // NUCLEAR REPLACEMENT STRATEGY
            // 1. Copy exact layout constraints from the native toggle
            target.layoutParams?.let { compose.layoutParams = it }
            
            // 2. Hide native toggle entirely so ComposeView seamlessly occupies its space
            val index = parent.indexOfChild(target)
            target.visibility = View.GONE
            
            // 3. Inject Owners and Add to actual View Hierarchy (bypassing Overlay limits entirely)
            installOwnersIfNeeded(parent)
            installOwnersIfNeeded(compose)
            
            parent.addView(compose, index + 1)
            composeView = compose

            target.post { backdrop.start() }
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
        }

        fun setVisible(isVisible: Boolean) {
            visible = isVisible
            composeView?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            backdrop.release()

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
        // Ascend the tree to find the nearest scrollable container to use as a backdrop.
        // This captures the row background completely, eliminating the deep black DecorView issue.
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
