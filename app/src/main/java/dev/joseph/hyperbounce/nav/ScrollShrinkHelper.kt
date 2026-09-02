package dev.joseph.hyperbounce.nav

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

@Composable
fun rememberScrollShrinkState(
    activity: Activity?,
    enabled: Boolean,
    sensitivity: Float = 1.0f,
): MutableState<Boolean> {
    val isShrunk = remember { mutableStateOf(false) }

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
                            startY = event.rawY
                            startX = event.rawX
                            isTracking = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (isTracking) {
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

    return isShrunk
}
