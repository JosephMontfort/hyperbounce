package dev.joseph.hyperbounce.liquidglass

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

@Composable
fun LiquidKeypadOverlay(
    nativeKeys: Map<String, View>,
    keyBounds: Map<String, Rect>
) {
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        keyBounds.forEach { (digit, bounds) ->
            if (bounds.width() > 0 && bounds.height() > 0) {
                // iOS 26 cascade entrance delay mapping
                val numericVal = digit.toIntOrNull() ?: 10
                val delayMs = if (numericVal == 0) 315L else (numericVal - 1) * 35L

                val widthDp = with(density) { bounds.width().toDp() }
                val heightDp = with(density) { bounds.height().toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(bounds.left, bounds.top) }
                        .size(widthDp, heightDp)
                ) {
                    JellyPinButton(
                        digit = digit,
                        delayMs = delayMs,
                        onClick = {
                            nativeKeys[digit]?.performClick() 
                        }
                    )
                }
            }
        }
    }
}
