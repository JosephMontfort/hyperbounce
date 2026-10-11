package dev.hyperios.app.nav

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.math.abs

class ScrollShrinkState(
    val isShrunk: MutableState<Boolean>,
) {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var hasBounds = false
    var isInteracting = false

    fun updateBounds(l: Float, t: Float, r: Float, b: Float) {
        left = l
        top = t
        right = r
        bottom = b
        hasBounds = true
    }

    fun isInsideNavBar(x: Float, y: Float, marginPx: Float = 0f): Boolean {
        if (!hasBounds) return false
        return x >= (left - marginPx) && x <= (right + marginPx) &&
               y >= (top - marginPx) && y <= (bottom + marginPx)
    }
}

@Composable
fun rememberScrollShrinkState(
    activity: Activity?,
    enabled: Boolean,
    sensitivity: Float = 1.0f,
): ScrollShrinkState {
    val isShrunk = remember { mutableStateOf(false) }
    val state = remember { ScrollShrinkState(isShrunk) }

    DisposableEffect(activity, enabled, sensitivity) {
        if (activity == null || !enabled) {
            isShrunk.value = false
            return@DisposableEffect onDispose {}
        }

        val window = activity.window ?: return@DisposableEffect onDispose {}
        val originalCallback = window.callback ?: return@DisposableEffect onDispose {}
        val baseTouchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
        val touchSlop = baseTouchSlop * (1.5f / sensitivity.coerceIn(0.2f, 5.0f))

        var startY = 0f
        var startX = 0f
        var isTracking = false
        var currentShrunk = isShrunk.value

        val wrappedCallback = object : Window.Callback by originalCallback {
            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                if (event != null) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            // If touch starts on the floating nav bar or user is interacting with it, ignore scroll shrink
                            val onNavBar = state.isInsideNavBar(event.rawX, event.rawY, marginPx = 24f) || state.isInteracting
                            if (onNavBar) {
                                isTracking = false
                            } else {
                                startY = event.rawY
                                startX = event.rawX
                                isTracking = true
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (isTracking && !state.isInteracting) {
                                val dy = event.rawY - startY
                                val dx = event.rawX - startX
                                val absDy = abs(dy)
                                val absDx = abs(dx)
                                if (absDy > touchSlop && absDy > absDx * 1.2f) {
                                    if (dy < 0f) {
                                        // Scrolling DOWN into feed -> shrink
                                        if (!currentShrunk) {
                                            currentShrunk = true
                                            isShrunk.value = true
                                        }
                                    } else {
                                        // Scrolling UP to top -> expand
                                        if (currentShrunk) {
                                            currentShrunk = false
                                            isShrunk.value = false
                                        }
                                    }
                                    startY = event.rawY
                                    startX = event.rawX
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isTracking = false
                        }
                    }
                }
                return originalCallback.dispatchTouchEvent(event)
            }
        }

        window.callback = wrappedCallback
        onDispose {
            if (window.callback === wrappedCallback) {
                window.callback = originalCallback
            }
        }
    }

    return state
}
