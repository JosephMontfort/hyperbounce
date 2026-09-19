package dev.joseph.hyperbounce.toggles

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.widget.ProgressBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.FakeLifecycleOwner
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import dev.joseph.hyperbounce.prefs.rememberLiveGlassFeel
import dev.joseph.hyperbounce.prefs.rememberLiveSliderColor
import dev.joseph.hyperbounce.toggles.glass.LiquidSlider
import java.util.WeakHashMap

internal object LiquidSliderHost {

    /** Set once from LiquidTogglesHook.onPackageReady() before any slider is hooked. */
    @JvmStatic
    var hyperBouncePrefs: HyperBouncePrefs? = null
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun syncProgress(target: View, progress: Int) {
        hosts[target]?.updateProgress(progress.toFloat())
    }

    @JvmStatic
    fun ensure(target: View) {
        if (target !is ProgressBar) return
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
    fun syncVisibility(target: View, isVisible: Boolean) {
        hosts[target]?.updateVisibility(isVisible)
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false
        
        val density = target.resources.displayMetrics.density
        val paddingPx = (24f * density).toInt() 
        
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) host.backdrop.start()
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) host.backdrop.stop()
        val offsetEvent = android.view.MotionEvent.obtain(event)
        offsetEvent.offsetLocation(paddingPx.toFloat(), paddingPx.toFloat())
        
        val handled = compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
        return handled
    }

    @JvmStatic
    fun setTrackingTouch(target: View, tracking: Boolean) {
        hosts[target]?.isTrackingTouch = tracking
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    class Host(private val target: View, val parent: ViewGroup) {
        var composeView: ComposeView? = null
        var isTrackingTouch = false
        
        private var sliderValue by mutableFloatStateOf(0f)
        private var sliderMin by mutableFloatStateOf(0f)
        private var sliderMax by mutableFloatStateOf(100f)
        
        private var isVisibleState by mutableStateOf(target.visibility == View.VISIBLE)
        private var isEnabledState by mutableStateOf(target.isEnabled)
        internal val backdrop = LiquidSliderViewBackdrop(parent)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                syncState()
                refreshGeometry()
            }
            override fun onViewDetachedFromWindow(v: View) {
                LiquidSliderHost.remove(v)
            }
        }

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshGeometry()
        }

        /*
         * ViewGroupOverlay does not automatically inherit RecyclerView/ScrollView clipping.
         * Hide the replacement ComposeView when its native target is fully outside the viewport.
         * The listener is attached to the native target, so there is no global host scan every
         * frame.
         */
        private val visibleRect = Rect()
        private val viewportPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            updateViewportVisibility()
            true
        }

        fun updateProgress(progress: Float) {
            if (!isTrackingTouch) {
                sliderValue = progress
            }
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)
            target.viewTreeObserver.addOnPreDrawListener(viewportPreDrawListener)
            disableClipping(target)

            val baseCtx = try {
                val atClass = Class.forName("android.app.ActivityThread")
                val method = atClass.getDeclaredMethod("currentApplication")
                method.isAccessible = true
                method.invoke(null) as? Context
            } catch (e: Throwable) { null } ?: try {
                target.context.createPackageContext("android", 0)
            } catch (e: Throwable) {
                target.context 
            }

            val safeContext = object : android.view.ContextThemeWrapper(baseCtx, 0) {
                override fun getTheme(): android.content.res.Resources.Theme = try { target.context.theme ?: super.getTheme() } catch(e: Throwable) { super.getTheme() }
                override fun getResources(): android.content.res.Resources = try { target.context.resources ?: super.getResources() } catch(e: Throwable) { super.getResources() }
                override fun getClassLoader(): ClassLoader = try { target.context.classLoader ?: super.getClassLoader() } catch(e: Throwable) { super.getClassLoader() }
                override fun getApplicationContext(): Context = try { baseCtx.applicationContext ?: baseCtx } catch (_: Throwable) { baseCtx }
                override fun getSystemService(name: String): Any? = try { target.context.getSystemService(name) ?: super.getSystemService(name) } catch(e: Throwable) { super.getSystemService(name) }
            }

            val sharedRecomposer = InjectedComposeRecomposer.get()

            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidSlider:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(sharedRecomposer)
                setContent {
                    if (isVisibleState) {
                        val targetWidthDp = with(LocalDensity.current) { target.width.toDp() }
                        val targetHeightDp = with(LocalDensity.current) { maxOf(target.height.toDp(), 28.dp) }
                        val isNight = androidx.compose.foundation.isSystemInDarkTheme()
                        val alphaValue = if (isEnabledState) 1f else if (isNight) 0.3f else 0.5f
                        
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                // Removed hardcoded padding to perfectly map X coordinates 1:1
                                modifier = Modifier.size(width = targetWidthDp, height = targetHeightDp).graphicsLayer { alpha = alphaValue },
                                contentAlignment = Alignment.Center
                            ) {
                                val prefs = hyperBouncePrefs
                                val feel = if (prefs != null) rememberLiveGlassFeel(prefs, "slider_glass") else null
                                val sliderColor = if (prefs != null) rememberLiveSliderColor(prefs) else 0xFF007AFFL
                                LiquidSlider(
                                    value = { sliderValue },
                                    onValueChange = { newValue ->
                                        // The native slider handles writing internally via LiquidTogglesHook!
                                        sliderValue = newValue
                                    },
                                    valueRange = sliderMin..sliderMax,
                                    visibilityThreshold = 0.01f,
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxSize(),
                                    activeColor = Color(sliderColor),
                                    blurDp = feel?.blurDp ?: 8f,
                                    lensSizeDp = feel?.lensSizeDp ?: 10f,
                                    lensDepthDp = feel?.lensDepthDp ?: 14f,
                                    highlightStrength = feel?.highlightStrength ?: 0.67f,
                                    shadowRadiusDp = feel?.shadowRadiusDp ?: 4f,
                                    springDamping = feel?.springDamping ?: 0.7f,
                                    springStiffness = feel?.springStiffness ?: 800f
                                )
                            }
                        }
                    }
                }
            }

            target.background = null
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
            syncState()
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
            val paddingPx = (24f * density).toInt()

            val overlayLeftPx = target.left - paddingPx
            val overlayTopPx = target.top - paddingPx
            val overlayWidthPx = target.width + (paddingPx * 2)
            val overlayHeightPx = target.height + (paddingPx * 2)

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
            
            backdrop.setTargetBounds(
                left = target.left.toFloat(), 
                top = target.top.toFloat(), 
                width = target.width, 
                height = target.height, 
                paddingPx = paddingPx
            )
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

        fun syncState() {
            val bar = target as? ProgressBar ?: return
            sliderMax = bar.max.toFloat()
            sliderMin = try { bar.min.toFloat() } catch (e: Throwable) { 0f }
            sliderValue = bar.progress.toFloat()
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
