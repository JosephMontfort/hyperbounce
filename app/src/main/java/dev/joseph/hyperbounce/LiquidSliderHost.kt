package dev.joseph.hyperbounce

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.Recomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidSlider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Attaches the library LiquidSlider as a ViewGroup-overlay child above a native
 * MIUI SeekBar. The native control remains the source of truth for min/max/
 * progress and receives changes through setProgress(), while its rendering is
 * hidden so only the Compose liquid-glass surface is visible.
 *
 * The overlay is deliberately owned by target.parent.overlay rather than by a
 * detached window/root overlay. This keeps the slider anchored to the same
 * parent coordinate space while SystemUI control-center content scrolls.
 */
internal object LiquidSliderHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        val existing = hosts[target]
        if (existing != null) {
            existing.refreshGeometry()
            existing.syncProgress()
            return
        }

        val parent = target.parent as? ViewGroup ?: return
        val host = Host(target, parent)
        hosts[target] = host
        host.attach()
    }

    @JvmStatic
    fun syncProgress(target: View) {
        hosts[target]?.syncProgress()
    }

    @JvmStatic
    fun syncVisibility(target: View) {
        hosts[target]?.updateVisibility(target.visibility == View.VISIBLE)
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    private class Host(
        private val target: View,
        private val parent: ViewGroup,
    ) {
        var composeView: ComposeView? = null
            private set

        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        private var currentProgress by mutableFloatStateOf(0f)
        private var minProgress by mutableIntStateOf(0)
        private var maxProgress by mutableIntStateOf(100)
        private var isVisibleState by mutableStateOf(target.visibility == View.VISIBLE)

        private var getProgressMethod: Method? = null
        private var getMinMethod: Method? = null
        private var getMaxMethod: Method? = null
        private var setProgressMethod: Method? = null

        private val backdrop = LiquidSliderViewBackdrop(parent, target)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                refreshGeometry()
                syncProgress()
            }

            override fun onViewDetachedFromWindow(v: View) {
                LiquidSliderHost.remove(v)
            }
        }

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshGeometry()
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)
            resolveMethods()
            syncProgress()
            disableClipping(target)

            val baseCtx = findSafeContext(target.context)
            val safeContext = object : android.view.ContextThemeWrapper(baseCtx, 0) {
                override fun getTheme(): android.content.res.Resources.Theme =
                    try { target.context.theme } catch (_: Throwable) { super.getTheme() }

                override fun getResources(): android.content.res.Resources =
                    try { target.context.resources } catch (_: Throwable) { super.getResources() }

                override fun getClassLoader(): ClassLoader =
                    try { target.context.classLoader } catch (_: Throwable) { super.getClassLoader() }

                override fun getApplicationContext(): Context =
                    try { baseCtx.applicationContext ?: baseCtx } catch (_: Throwable) { baseCtx }

                override fun getSystemService(name: String): Any? =
                    try { target.context.getSystemService(name) ?: super.getSystemService(name) }
                    catch (_: Throwable) { super.getSystemService(name) }
            }

            // Keep this Compose composition isolated from HyperOS/SystemUI's
            // WindowRecomposer, for the same reason as the LiquidToggle host.
            val isolatedMotionScale = object : MotionDurationScale {
                override val scaleFactor: Float = 1f
            }
            val isolatedRecomposer = Recomposer(
                AndroidUiDispatcher.Main + isolatedMotionScale
            )
            val isolatedScope = CoroutineScope(
                SupervisorJob() + AndroidUiDispatcher.Main
            )
            recomposer = isolatedRecomposer
            recomposerScope = isolatedScope
            isolatedScope.launch {
                try {
                    isolatedRecomposer.runRecomposeAndApplyChanges()
                } catch (_: Throwable) {
                    // Expected during disposal/cancellation.
                }
            }

            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidSlider:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = false
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(isolatedRecomposer)
                setContent {
                    LiquidSliderOverlay(
                        backdrop = backdrop,
                        visible = isVisibleState,
                        value = { currentProgress },
                        valueRange = minProgress.toFloat()..maxProgress.toFloat(),
                        onValueChange = ::setProgressFromCompose,
                    )
                }
            }

            // Preserve all native behavior/listeners/progress logic, but let the
            // new Compose surface own the visual rendering.
            target.alpha = 0f

            val overlay = parent.overlay
            installOwnersIfNeeded(compose)
            overlay.add(compose)

            composeView = compose
            backdrop.overlayViewToHide = compose
            backdrop.start()
            refreshGeometry()
        }

        private fun setProgressFromCompose(newValue: Float) {
            val min = minProgress
            val max = maxProgress
            val clamped = newValue.coerceIn(min.toFloat(), max.toFloat())
            currentProgress = clamped
            val progressInt = clamped.roundToInt().coerceIn(min, max)
            try {
                setProgressMethod?.invoke(target, progressInt)
            } catch (_: Throwable) {
                // Native control is best-effort; Compose state still tracks the value.
            }
        }

        fun syncProgress() {
            resolveMethodsIfNeeded()
            try {
                minProgress = (getMinMethod?.invoke(target) as? Int) ?: 0
                maxProgress = (getMaxMethod?.invoke(target) as? Int) ?: 100
                val nativeProgress = (getProgressMethod?.invoke(target) as? Int) ?: minProgress
                currentProgress = nativeProgress.coerceIn(minProgress, maxProgress).toFloat()
            } catch (_: Throwable) {
                minProgress = 0
                maxProgress = 100
                currentProgress = currentProgress.coerceIn(0f, 100f)
            }
        }

        fun updateVisibility(isVisible: Boolean) {
            isVisibleState = isVisible
            composeView?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val left = target.left
            val top = target.top
            val width = target.width.coerceAtLeast(1)
            val height = target.height.coerceAtLeast(1)
            val overlayParent = target.parent as? ViewGroup
            if (overlayParent !== parent) return

            compose.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            compose.layout(left, top, left + width, top + height)

            val density = target.resources.displayMetrics.density
            backdrop.setTargetBounds(
                left = left.toFloat(),
                top = top.toFloat(),
                width = width,
                height = height,
                paddingPx = (32f * density).toInt()
            )
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)
            target.alpha = 1f

            backdrop.release()
            composeView?.setContent { }
            composeView?.let { parent.overlay.remove(it) }
            composeView = null

            recomposer?.cancel()
            recomposer = null
            recomposerScope?.cancel()
            recomposerScope = null
        }

        private fun resolveMethods() {
            getProgressMethod = findMethod(target.javaClass, "getProgress")
            getMinMethod = findMethod(target.javaClass, "getMin")
            getMaxMethod = findMethod(target.javaClass, "getMax")
            setProgressMethod = findMethod(target.javaClass, "setProgress", Int::class.javaPrimitiveType!!)
        }

        private fun resolveMethodsIfNeeded() {
            if (getProgressMethod == null || getMinMethod == null ||
                getMaxMethod == null || setProgressMethod == null
            ) {
                resolveMethods()
            }
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return try {
            clazz.getMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findSafeContext(fallback: Context): Context {
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        } catch (_: Throwable) {
            null
        } ?: try {
            fallback.createPackageContext("android", 0)
        } catch (_: Throwable) {
            fallback
        }
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

    @Composable
    private fun LiquidSliderOverlay(
        backdrop: com.kyant.backdrop.Backdrop,
        visible: Boolean,
        value: () -> Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (visible) {
                LiquidSlider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    visibilityThreshold = 0.01f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize()
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

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}
