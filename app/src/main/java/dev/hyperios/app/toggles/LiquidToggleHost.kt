package dev.hyperios.app.toggles

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.compose.ui.graphics.Color
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import dev.hyperios.app.prefs.rememberLiveGlassFeel
import dev.hyperios.app.prefs.rememberLiveToggleColor
import dev.hyperios.app.toggles.glass.LiquidToggle
import java.util.WeakHashMap

internal object LiquidToggleHost {
    private val hosts = WeakHashMap<View, Host>()

    /** Set once from LiquidTogglesHook.onPackageReady() before any toggle is hooked. */
    @JvmStatic
    var hyperBouncePrefs: HyperBouncePrefs? = null

    @JvmStatic
    fun ensure(target: View) {
        if (target !is CompoundButton) return
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        if (hosts.containsKey(target)) {
            // refreshGeometry() removed to prevent layout thrashing
            return
        }

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
        hosts[target]?.updateVisibility(isVisible)
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) host.backdrop.start()
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) host.backdrop.stop()
        val offsetEvent = android.view.MotionEvent.obtain(event)
        offsetEvent.offsetLocation(-host.composeLeftInTarget.toFloat(), -host.composeTopInTarget.toFloat())
        val handled = compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
        return handled
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    private class Host(private val target: View, private val parent: ViewGroup) {
        var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var isVisibleState by mutableStateOf(target.visibility == View.VISIBLE)
        private var isEnabledState by mutableStateOf(target.isEnabled)
        
        var composeLeftInTarget by mutableIntStateOf(0)
        var composeTopInTarget by mutableIntStateOf(0)
        internal val backdrop = LiquidToggleViewBackdrop(parent)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                syncChecked()
                refreshGeometry()
            }
            override fun onViewDetachedFromWindow(v: View) {
                LiquidToggleHost.remove(v)
            }
        }

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshGeometry()
        }

        /*
         * Keep the Compose overlay out of the render traversal when the native target is
         * clipped completely out of the viewport (RecyclerView/ScrollView/etc.).
         *
         * The original Android widget is naturally culled by its parent. Our replacement
         * lives in ViewGroupOverlay, so it does not automatically inherit that culling.
         * Without this guard, off-screen liquid widgets continue to be rasterized while
         * the list scrolls.
         *
         * A pre-draw listener is intentionally attached to the target itself: it runs only
         * while the native target participates in drawing, avoiding a global per-frame host
         * scan.
         */
        private val visibleRect = Rect()
        private val viewportPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            updateViewportVisibility()
            true
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)
            target.viewTreeObserver.addOnPreDrawListener(viewportPreDrawListener)
            disableClipping(target)
            // Obtain a safe application context without depending on the legacy Xposed helper API.
            val baseCtx = try {
                val atClass = Class.forName("android.app.ActivityThread")
                val method = atClass.getDeclaredMethod("currentApplication")
                method.isAccessible = true
                method.invoke(null) as? Context
            } catch (e: Throwable) { null } ?: try {
                // Failsafe: Generate a pure framework context
                target.context.createPackageContext("android", 0)
            } catch (e: Throwable) {
                target.context 
            }

            // THE UNWRAP-PROOF WRAPPER:
            // By passing 'baseCtx' into ContextThemeWrapper, Jetpack Compose will unwrap 
            // the wrapper and hit our healthy 'baseCtx' instead of the broken MIUI plugin context.
            val safeContext = object : android.view.ContextThemeWrapper(baseCtx, 0) {
                override fun getTheme(): android.content.res.Resources.Theme {
                    return try { target.context.theme ?: super.getTheme() } catch(e: Throwable) { super.getTheme() }
                }

                override fun getResources(): android.content.res.Resources {
                    return try { target.context.resources ?: super.getResources() } catch(e: Throwable) { super.getResources() }
                }

                override fun getClassLoader(): ClassLoader {
                    return try { target.context.classLoader ?: super.getClassLoader() } catch(e: Throwable) { super.getClassLoader() }
                }

                // Compose's WindowRecomposer reads context.applicationContext when an
                // animation first starts. Some HyperOS/SystemUI plugin Contexts return
                // null here. Modern Compose treats this as a non-null platform type and
                // therefore crashes with an NPE inside getAnimationScaleFlowFor().
                //
                // Always provide a non-null application context for injected Compose.
                override fun getApplicationContext(): Context {
                    return try {
                        baseCtx.applicationContext ?: baseCtx
                    } catch (_: Throwable) {
                        baseCtx
                    }
                }

                override fun getSystemService(name: String): Any? {
                    return try { target.context.getSystemService(name) ?: super.getSystemService(name) } catch(e: Throwable) { super.getSystemService(name) }
                }
            }

            /*
             * IMPORTANT: Do not let this injected ComposeView inherit SystemUI's
             * window/tree Recomposer.
             *
             * HyperOS SystemUI can expose a Compose composition context whose
             * MotionDurationScale implementation reaches a null applicationContext.
             * Compose's Animatable then crashes at:
             *   WindowRecomposer -> getAnimationScaleFlowFor ->
             *   applicationContext.contentResolver
             *
             * setParentCompositionContext() is the supported Compose escape hatch:
             * an explicit parent context wins over the ancestor/window Recomposer.
             * We therefore use a shared injected Recomposer with the same fixed
             * MotionDurationScale. This keeps the LiquidToggle animation pipeline
             * isolated from SystemUI's Recomposer without creating one scheduler per widget.
             */
            val sharedRecomposer = InjectedComposeRecomposer.get()

            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(sharedRecomposer)
                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = isVisibleState,
                        enabled = isEnabledState,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null && button.isChecked != value) {
                                button.performClick()
                            }
                        }
                    )
                }
            }

            target.alpha = 0.0f
            
            val overlay = parent.overlay
            if (overlay != null) {
                val dummy = View(target.context)
                overlay.add(dummy)
                val overlayViewGroup = dummy.parent as? View
                if (overlayViewGroup != null) installOwnersIfNeeded(overlayViewGroup)
                overlay.remove(dummy)

                installOwnersIfNeeded(compose)
                overlay.add(compose)
            }

            composeView = compose
            backdrop.overlayViewToHide = compose
            refreshGeometry()
            updateViewportVisibility()
        }

        private fun disableClipping(view: View) {
            var p: View? = view
            while (p != null) {
                val className = p.javaClass.name
                if (className.contains("ActionBar") ||
                    className.contains("RecyclerView") ||
                    p is android.widget.AbsListView ||
                    p is android.widget.ScrollView ||
                    className.contains("DecorView")
                ) {
                    break
                }
                p.clipToOutline = false
                if (p is ViewGroup) {
                    p.clipChildren = false
                    p.clipToPadding = false
                }
                p = p.parent as? View
            }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val density = target.resources.displayMetrics.density
            val overlayWidthPx = (80f * density).toInt()
            val overlayHeightPx = (48f * density).toInt()

            composeLeftInTarget = (target.width - overlayWidthPx) / 2
            composeTopInTarget = (target.height - overlayHeightPx) / 2

            val overlayLeftPx = target.left + composeLeftInTarget
            val overlayTopPx = target.top + composeTopInTarget

            compose.measure(
                View.MeasureSpec.makeMeasureSpec(overlayWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(overlayHeightPx, View.MeasureSpec.EXACTLY)
            )
            compose.layout(
                overlayLeftPx,
                overlayTopPx,
                overlayLeftPx + overlayWidthPx,
                overlayTopPx + overlayHeightPx
            )
            backdrop.setTargetBounds(left = target.left.toFloat(), top = target.top.toFloat(), width = target.width, height = target.height, paddingPx = (24f * density).toInt())
        }

        private fun updateViewportVisibility() {
            val compose = composeView ?: return
            if (!isVisibleState || !target.isShown) {
                if (compose.visibility != View.GONE) compose.visibility = View.GONE
                return
            }

            val visible = try {
                target.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty
            } catch (_: Throwable) {
                true
            }
            val desired = if (visible) View.VISIBLE else View.GONE
            if (compose.visibility != desired) {
                compose.visibility = desired
            }
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
            isEnabledState = target.isEnabled
        }

        fun updateVisibility(isVisible: Boolean) {
            isVisibleState = isVisible
            updateViewportVisibility()
        }

        fun dispose() {
            if (target.viewTreeObserver.isAlive) {
                target.viewTreeObserver.removeOnPreDrawListener(viewportPreDrawListener)
            }
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)
            target.alpha = 1f 

            val compose = composeView
            composeView = null
            backdrop.release()
            compose?.setContent { }
            compose?.let { parent.overlay?.remove(it) }

        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        backdrop: com.kyant.backdrop.Backdrop,
        visible: Boolean,
        enabled: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
        val isNight = androidx.compose.foundation.isSystemInDarkTheme()
        val alphaValue = if (enabled) 1f else if (isNight) 0.3f else 0.5f
        val prefs = hyperBouncePrefs
        val feel = if (prefs != null) rememberLiveGlassFeel(prefs, "toggle_glass") else null
        val toggleColor = if (prefs != null) rememberLiveToggleColor(prefs) else 0xFF34C759L
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (visible) {
                LiquidToggle(
                    selected = selected,
                    onSelect = onSelect,
                    backdrop = backdrop,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.975f
                        scaleY = 0.975f
                        alpha = alphaValue
                    },
                    activeColor = Color(toggleColor),
                    blurDp = feel?.blurDp ?: 8f,
                    lensSizeDp = feel?.lensSizeDp ?: 5f,
                    lensDepthDp = feel?.lensDepthDp ?: 10f,
                    highlightStrength = feel?.highlightStrength ?: 0.67f,
                    shadowRadiusDp = feel?.shadowRadiusDp ?: 4f,
                    dragSensitivity = feel?.dragSensitivity ?: 0.72f,
                    springDamping = feel?.springDamping ?: 0.82f,
                    springStiffness = feel?.springStiffness ?: 400f
                )
            }
        }
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
}
