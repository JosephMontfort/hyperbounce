package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Instagram-only overlay host.
 *
 * The liquid bar is intentionally put in its own application-panel window.
 * This is important for the Reel backdrop path: SurfaceControl/SurfaceFlinger
 * capture of Instagram's activity window must not contain our own glass bar,
 * otherwise the backdrop would recursively capture the bar itself.
 */
internal class InstagramOverlayWindow(
    private val activity: Activity,
    stockBar: ViewGroup?,
) {
    private val windowManager = activity.getSystemService(WindowManager::class.java)
    private val composeView: ComposeView
    private val params: WindowManager.LayoutParams
    private var attached = false

    init {
        val owner = FakeLifecycleOwner().apply {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        val barWidthPx = (5 * 64f * activity.resources.displayMetrics.density).toInt()
        val barHeightPx = (70f * activity.resources.displayMetrics.density).toInt()
        val bottomOffsetPx = (40f * activity.resources.displayMetrics.density).toInt()

        composeView = ComposeView(activity).apply {
            tag = "LiquidGlassInjected"
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                InstagramNavBarHost(
                    stockBar = stockBar,
                    backdropSource = activity.window.decorView,
                    bottomPadding = 0.dp,
                    backdropWindowOriginProvider = { windowOriginInActivityWindow() },
                )
            }
        }

        params = WindowManager.LayoutParams(
            barWidthPx,
            barHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            token = activity.window.decorView.windowToken
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomOffsetPx
            title = "HyperBounce Instagram Liquid Glass"
            if (Build.VERSION.SDK_INT >= 31) {
                setFitInsetsTypes(0)
            }
        }
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            if (!attached) {
                try {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    windowManager.addView(composeView, params)
                    attached = true
                } catch (_: Throwable) {}
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Throwable) {}
            }
            composeView.visibility = View.VISIBLE
        } else {
            composeView.visibility = View.INVISIBLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (attached) {
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Throwable) {}
            }
        }
    }

    fun isAttached(): Boolean = attached

    fun release() {
        if (attached) {
            try {
                windowManager.removeViewImmediate(composeView)
            } catch (_: Throwable) {}
            attached = false
        }
    }

    private fun windowOriginInActivityWindow(): androidx.compose.ui.geometry.Offset {
        val decor = activity.window.decorView
        val decorOnScreen = IntArray(2)
        decor.getLocationOnScreen(decorOnScreen)

        val bounds = if (Build.VERSION.SDK_INT >= 30) {
            activity.windowManager.currentWindowMetrics.bounds
        } else {
            android.graphics.Rect(
                0,
                0,
                activity.resources.displayMetrics.widthPixels,
                activity.resources.displayMetrics.heightPixels,
            )
        }

        val width = params.width
        val height = params.height
        val xOnScreen = (bounds.width() - width) / 2
        val yOnScreen = bounds.height() - height - params.y

        return androidx.compose.ui.geometry.Offset(
            xOnScreen - decorOnScreen[0].toFloat(),
            yOnScreen - decorOnScreen[1].toFloat(),
        )
    }
}
