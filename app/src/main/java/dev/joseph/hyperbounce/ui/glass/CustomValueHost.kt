package dev.joseph.hyperbounce.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import java.util.Locale

class CustomValueRequest(
    val title: String,
    val currentValue: Float,
    val defaultValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val onApply: (Float) -> Unit
)

class CustomValueHostState {
    var activeRequest by mutableStateOf<CustomValueRequest?>(null)
        private set

    fun show(
        title: String,
        currentValue: Float,
        defaultValue: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onApply: (Float) -> Unit
    ) {
        activeRequest = CustomValueRequest(title, currentValue, defaultValue, valueRange, onApply)
    }

    fun dismiss() {
        activeRequest = null
    }
}

val LocalCustomValueHost = compositionLocalOf<CustomValueHostState> {
    CustomValueHostState()
}

@Composable
fun CustomValueDialogHost(
    colors: HyperBounceColors,
    content: @Composable () -> Unit
) {
    val hostState = remember { CustomValueHostState() }
    val currentRequest = hostState.activeRequest

    var displayedRequest by remember { mutableStateOf<CustomValueRequest?>(null) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(currentRequest) {
        if (currentRequest != null) {
            displayedRequest = currentRequest
            isClosing = false
        } else if (displayedRequest != null) {
            isClosing = true
        }
    }

    val isVisible = displayedRequest != null && !isClosing
    val animProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "customValueAnimProgress",
        finishedListener = { progress ->
            if (progress == 0f && isClosing) {
                displayedRequest = null
                isClosing = false
            }
        }
    )

    CompositionLocalProvider(LocalCustomValueHost provides hostState) {
        Box(Modifier.fillMaxSize()) {
            // Main content underneath with dynamic smooth animated blur
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (animProgress > 0.001f) {
                            Modifier.blur((24.dp * animProgress))
                        } else {
                            Modifier
                        }
                    )
            ) {
                content()
            }

            // Full-screen overlay with smooth dimming and AndroidLiquidGlass styled Dialog
            if (animProgress > 0.001f && displayedRequest != null) {
                val req = displayedRequest!!
                val isDark = isSystemInDarkTheme()
                val cardBg = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF)
                val inputBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val accentColor = colors.accentBlue

                var textValue by remember(req) {
                    mutableStateOf(
                        if (req.currentValue % 1f == 0f && req.valueRange.endInclusive > 10f) {
                            req.currentValue.toInt().toString()
                        } else {
                            String.format(Locale.US, "%.3f", req.currentValue).trimEnd('0').trimEnd('.')
                        }
                    )
                }

                fun requestClose() {
                    hostState.dismiss()
                }

                fun requestApply() {
                    val parsed = textValue.toFloatOrNull()
                    if (parsed != null) {
                        req.onApply(parsed)
                    }
                    hostState.dismiss()
                }

                // Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f * animProgress))
                        .pointerInput(req) {
                            detectTapGestures { requestClose() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Dialog Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .graphicsLayer {
                                alpha = animProgress
                                scaleX = 0.85f + 0.15f * animProgress
                                scaleY = 0.85f + 0.15f * animProgress
                            }
                            .clip(RoundedCornerShape(32.dp))
                            .background(cardBg)
                            .pointerInput(Unit) {
                                detectTapGestures { /* consume taps inside */ }
                            }
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BasicText(
                                text = "Custom Value",
                                style = HyperBounceType.headline.copy(
                                    color = colors.label,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            BasicText(
                                text = "${req.title}\nStock Range: [${req.valueRange.start} .. ${req.valueRange.endInclusive}]  •  Default: ${req.defaultValue}",
                                style = HyperBounceType.footnote.copy(
                                    color = colors.secondaryLabel,
                                    lineHeight = 18.sp
                                )
                            )

                            // Input box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(inputBg)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                BasicTextField(
                                    value = textValue,
                                    onValueChange = { textValue = it },
                                    textStyle = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(accentColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // AndroidLiquidGlass Capsule Buttons Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel Capsule Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .background(cancelBtnBg)
                                        .clickable { requestClose() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = "Cancel",
                                        style = TextStyle(
                                            color = colors.label,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }

                                // Apply Capsule Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .clickable { requestApply() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = "Apply",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
