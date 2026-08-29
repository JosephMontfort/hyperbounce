package dev.joseph.hyperbounce.toggles

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
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.FakeLifecycleOwner
import dev.joseph.hyperbounce.prefs.HyperBouncePrefs
import dev.joseph.hyperbounce.prefs.rememberLiveGlassFeel
import dev.joseph.hyperbounce.toggles.glass.LiquidToggle
import java.util.WeakHashMap

internal object LiquidToggleHost {
    private val hosts = WeakHashMap<View, Host>()

    /** Set once from LiquidTogglesHook.onPackageReady() before any toggle is hooked. */
    @JvmStatic
    var hyperBouncePrefs: HyperBouncePrefs? = null

    @JvmStatic
    fun ensure(target: View) {
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
                override fun getTheme(): android.content.res.Resources.Theme {
                    return try { target.context.theme ?: super.getTheme() } catch(e: Throwable) { super.getTheme() }
                }

                override fun getResources(): android.content.res.Resources {
                    return try { target.context.resources ?: super.getResources() } catch(e: Throwable) { super.getResources() }
                }

                override fun getClassLoader(): ClassLoader {
                    return try { target.context.classLoader ?: super.getClassLoader() } catch(e: Throwable) { super.getClassLoader() }
                }

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

            val sharedRecomposer = InjectedComposeRecomposer.get()

            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(sharedRecomposer)
                setContent {
                    LiquidToggleOverlay(
                        target = target,
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
            composeView?.let { parent.overlay?.remove(it) }
            composeView = null
        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        target: View,
        visible: Boolean,
        enabled: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
        val isNight = androidx.compose.foundation.isSystemInDarkTheme()
        val alphaValue = if (enabled) 1f else if (isNight) 0.3f else 0.5f
        val prefs = hyperBouncePrefs
        val feel = if (prefs != null) rememberLiveGlassFeel(prefs, "toggle_glass") else null
        val cardColor = remember(target, isNight) { resolveUnderlyingCardColor(target, isNight) }
        val backdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop {
            drawRect(cardColor)
        }
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

    internal fun resolveUnderlyingCardColor(target: View, isNight: Boolean): androidx.compose.ui.graphics.Color {
        try {
            var p: View? = target.parent as? View
            while (p != null) {
                val name = p.javaClass.name
                if (name.contains("DecorView") || p.id == android.R.id.content || name.contains("ContentFrameLayout")) {
                    break
                }

                val bg = p.background
                if (bg != null && p.width > 0 && p.height > 0) {
                    val bmp = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val loc = IntArray(2)
                    target.getLocationInWindow(loc)
                    val pLoc = IntArray(2)
                    p.getLocationInWindow(pLoc)
                    val sampleX = (loc[0] - pLoc[0] - 8).coerceIn(0, p.width - 1)
                    val sampleY = (loc[1] - pLoc[1] + target.height / 2).coerceIn(0, p.height - 1)
                    
                    canvas.translate(-sampleX.toFloat(), -sampleY.toFloat())
                    bg.setBounds(0, 0, p.width, p.height)
                    bg.draw(canvas)
                    
                    val pixel = bmp.getPixel(0, 0)
                    bmp.recycle()
                    val a = android.graphics.Color.alpha(pixel)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    // If it's a solid non-black surface pixel:
                    if (a > 200 && !(isNight && r == 0 && g == 0 && b == 0)) {
                        return androidx.compose.ui.graphics.Color(pixel)
                    }
                }
                p = p.parent as? View
            }

            val ctx = target.context
            val tv = android.util.TypedValue()
            
            val surfaceAttrId = ctx.resources.getIdentifier("colorSurface", "attr", ctx.packageName)
            if (surfaceAttrId != 0 && ctx.theme.resolveAttribute(surfaceAttrId, tv, true) && tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                val c = tv.data
                val r = android.graphics.Color.red(c)
                val g = android.graphics.Color.green(c)
                val b = android.graphics.Color.blue(c)
                if (!(isNight && r == 0 && g == 0 && b == 0)) {
                    return androidx.compose.ui.graphics.Color(c)
                }
            }
            
            val cardBgAttrId = ctx.resources.getIdentifier("cardBackgroundColor", "attr", ctx.packageName)
            if (cardBgAttrId != 0 && ctx.theme.resolveAttribute(cardBgAttrId, tv, true) && tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return androidx.compose.ui.graphics.Color(tv.data)
            }
        } catch (_: Throwable) {}

        return if (isNight) androidx.compose.ui.graphics.Color(0xFF242424) else androidx.compose.ui.graphics.Color.White
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
