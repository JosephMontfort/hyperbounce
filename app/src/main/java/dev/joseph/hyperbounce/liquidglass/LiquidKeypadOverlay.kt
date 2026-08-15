package dev.joseph.hyperbounce.liquidglass

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
fun LiquidKeypadOverlay(
    nativeKeys: Map<String, View>,
    backdrop: Backdrop
) {
    val keypadLayout = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "")
    )

    // Position the entire grid in the bottom half of the screen
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.padding(bottom = 120.dp), // Lift grid from fingerprint sensor
            verticalArrangement = Arrangement.spacedBy(20.dp), // Vertical Gap
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            keypadLayout.forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp), // Horizontal Gap
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, digit ->
                        if (digit.isEmpty()) {
                            Spacer(modifier = Modifier.size(80.dp))
                        } else {
                            val delayMs = (rowIndex * 3 + colIndex) * 35L // Staggered left-to-right, top-to-bottom

                            Box(modifier = Modifier.size(80.dp)) {
                                JellyPinButton(
                                    digit = digit,
                                    delayMs = delayMs,
                                    backdrop = backdrop,
                                    onClick = {
                                        // Forward Compose tap exactly to the hidden native view
                                        nativeKeys[digit]?.performClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
