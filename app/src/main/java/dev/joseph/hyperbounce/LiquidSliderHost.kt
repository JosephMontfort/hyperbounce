package dev.joseph.hyperbounce

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidSlider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

internal object LiquidSliderHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        if (hosts.containsKey(target)) {
            hosts[target]?.refreshGeometry()
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
        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        
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

        private val frameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // Instantly sync visual state if hardware keys change the volume externally
                if (!isTrackingTouch) {
                    syncState()
                }
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
            
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

            val isolatedMotionScale = object : MotionDurationScale { override val scaleFactor: Float = 1f }
            val isolatedRecomposer = Recomposer(AndroidUiDispatcher.Main + isolatedMotionScale)
            val isolatedScope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
            recomposer = isolatedRecomposer
            recomposerScope = isolatedScope
            isolatedScope.launch {
                try { isolatedRecomposer.runRecomposeAndApplyChanges() } catch (_: Throwable) {}
            }

            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidSlider:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(isolatedRecomposer)
                setContent {
                    if (isVisibleState) {
                        val targetWidthDp = with(LocalDensity.current) { target.width.toDp() }
                        val targetHeightDp = with(LocalDensity.current) { target.height.toDp() }
                        val isNight = androidx.compose.foundation.isSystemInDarkTheme()
                        val alphaValue = if (isEnabledState) 1f else if (isNight) 0.3f else 0.5f
                        
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                // Removed hardcoded padding to perfectly map X coordinates 1:1
                                modifier = Modifier.size(width = targetWidthDp, height = targetHeightDp).graphicsLayer { alpha = alphaValue },
                                contentAlignment = Alignment.Center
                            ) {
                                LiquidSlider(
                                    value = { sliderValue },
                                    onValueChange = { newValue ->
                                        // The native slider handles writing internally via PhysicsHook!
                                        sliderValue = newValue
                                    },
                                    valueRange = sliderMin..sliderMax,
                                    visibilityThreshold = 0.01f,
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            try {
                val seekbar = target as? android.widget.AbsSeekBar
                seekbar?.thumb = null
                seekbar?.progressDrawable = null
            } catch (e: Throwable) {}
            
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

        fun syncState() {
            val bar = target as? ProgressBar ?: return
            sliderMax = bar.max.toFloat()
            sliderMin = try { bar.min.toFloat() } catch (e: Throwable) { 0f }
            sliderValue = bar.progress.toFloat()
            isEnabledState = target.isEnabled
        }

        fun updateVisibility(isVisible: Boolean) {
            isVisibleState = isVisible
            composeView?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)
            android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
            target.alpha = 1f 

            val compose = composeView
            composeView = null
            backdrop.release()
            compose?.setContent { }
            compose?.let { parent.overlay?.remove(it) }

            recomposer?.cancel()
            recomposer = null
            recomposerScope?.cancel()
            recomposerScope = null
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
