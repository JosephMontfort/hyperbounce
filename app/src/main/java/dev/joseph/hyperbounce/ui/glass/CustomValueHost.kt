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

sealed class GlassDialogRequest {
    class CustomValue(
        val title: String,
        val currentValue: Float,
        val defaultValue: Float,
        val valueRange: ClosedFloatingPointRange<Float>,
        val onApply: (Float) -> Unit
    ) : GlassDialogRequest()

    class Confirmation(
        val title: String,
        val message: String,
        val confirmText: String = "Confirm",
        val cancelText: String = "Cancel",
        val confirmColor: Color? = null,
        val onConfirm: () -> Unit,
        val onCancel: (() -> Unit)? = null
    ) : GlassDialogRequest()

    class TextInput(
        val title: String,
        val message: String? = null,
        val initialValue: String = "",
        val placeholder: String = "",
        val confirmText: String = "Save",
        val cancelText: String = "Cancel",
        val confirmColor: Color? = null,
        val onConfirm: (String) -> Unit,
        val onCancel: (() -> Unit)? = null
    ) : GlassDialogRequest()

    class Custom(
        val title: String,
        val content: @Composable (dismiss: () -> Unit) -> Unit
    ) : GlassDialogRequest()
}

// Backward compatibility alias for custom value requests
typealias CustomValueRequest = GlassDialogRequest.CustomValue

class GlassDialogHostState {
    var activeRequest by mutableStateOf<GlassDialogRequest?>(null)
        private set

    fun show(
        title: String,
        currentValue: Float,
        defaultValue: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onApply: (Float) -> Unit
    ) {
        activeRequest = GlassDialogRequest.CustomValue(title, currentValue, defaultValue, valueRange, onApply)
    }

    fun showCustomValue(
        title: String,
        currentValue: Float,
        defaultValue: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onApply: (Float) -> Unit
    ) {
        activeRequest = GlassDialogRequest.CustomValue(title, currentValue, defaultValue, valueRange, onApply)
    }

    fun showConfirmation(
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        confirmColor: Color? = null,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        activeRequest = GlassDialogRequest.Confirmation(
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            confirmColor = confirmColor,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
    }

    fun showTextInput(
        title: String,
        message: String? = null,
        initialValue: String = "",
        placeholder: String = "",
        confirmText: String = "Save",
        cancelText: String = "Cancel",
        confirmColor: Color? = null,
        onConfirm: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        activeRequest = GlassDialogRequest.TextInput(
            title = title,
            message = message,
            initialValue = initialValue,
            placeholder = placeholder,
            confirmText = confirmText,
            cancelText = cancelText,
            confirmColor = confirmColor,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
    }

    fun showCustom(
        title: String,
        content: @Composable (dismiss: () -> Unit) -> Unit
    ) {
        activeRequest = GlassDialogRequest.Custom(title, content)
    }

    fun dismiss() {
        activeRequest = null
    }
}

typealias CustomValueHostState = GlassDialogHostState

val LocalGlassDialogHost = compositionLocalOf<GlassDialogHostState> {
    GlassDialogHostState()
}

val LocalCustomValueHost = LocalGlassDialogHost

@Composable
fun GlassDialogHost(
    colors: HyperBounceColors,
    content: @Composable () -> Unit
) {
    val hostState = remember { GlassDialogHostState() }
    val currentRequest = hostState.activeRequest

    var displayedRequest by remember { mutableStateOf<GlassDialogRequest?>(null) }
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
        label = "glassDialogAnimProgress",
        finishedListener = { progress ->
            if (progress == 0f && isClosing) {
                displayedRequest = null
                isClosing = false
            }
        }
    )

    val componentStyle = dev.joseph.hyperbounce.ui.state.LocalComponentStyle.current
    if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX && displayedRequest != null) {
        val req = displayedRequest!!
        when (req) {
            is GlassDialogRequest.CustomValue -> {
                var textValue by remember(req) {
                    mutableStateOf(
                        if (req.currentValue % 1f == 0f && req.valueRange.endInclusive > 10f) {
                            req.currentValue.toInt().toString()
                        } else {
                            String.format(Locale.US, "%.3f", req.currentValue).trimEnd('0').trimEnd('.')
                        }
                    )
                }
                fun requestApply() {
                    val parsed = textValue.toFloatOrNull()
                    if (parsed != null) {
                        req.onApply(parsed)
                    }
                    hostState.dismiss()
                }
                top.yukonga.miuix.kmp.window.WindowDialog(
                    show = isVisible,
                    title = "Custom Value",
                    summary = "${req.title}\nStock Range: [${req.valueRange.start} .. ${req.valueRange.endInclusive}] • Default: ${req.defaultValue}",
                    onDismissRequest = { hostState.dismiss() },
                    onDismissFinished = {
                        displayedRequest = null
                        isClosing = false
                    },
                    content = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "Cancel",
                                    onClick = { hostState.dismiss() },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(16.dp))
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "Apply",
                                    onClick = { requestApply() },
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                )
            }
            is GlassDialogRequest.Confirmation -> {
                top.yukonga.miuix.kmp.window.WindowDialog(
                    show = isVisible,
                    title = req.title,
                    summary = req.message,
                    onDismissRequest = {
                        req.onCancel?.invoke()
                        hostState.dismiss()
                    },
                    onDismissFinished = {
                        displayedRequest = null
                        isClosing = false
                    },
                    content = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = req.cancelText,
                                onClick = {
                                    req.onCancel?.invoke()
                                    hostState.dismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(16.dp))
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = req.confirmText,
                                onClick = {
                                    req.onConfirm()
                                    hostState.dismiss()
                                },
                                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                )
            }
            is GlassDialogRequest.TextInput -> {
                var inputVal by remember(req) { mutableStateOf(req.initialValue) }
                top.yukonga.miuix.kmp.window.WindowDialog(
                    show = isVisible,
                    title = req.title,
                    summary = req.message ?: "",
                    onDismissRequest = {
                        req.onCancel?.invoke()
                        hostState.dismiss()
                    },
                    onDismissFinished = {
                        displayedRequest = null
                        isClosing = false
                    },
                    content = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = inputVal,
                                onValueChange = { inputVal = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = req.cancelText,
                                    onClick = {
                                        req.onCancel?.invoke()
                                        hostState.dismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(16.dp))
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = req.confirmText,
                                    onClick = {
                                        req.onConfirm(inputVal)
                                        hostState.dismiss()
                                    },
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                )
            }
            is GlassDialogRequest.Custom -> {
                top.yukonga.miuix.kmp.window.WindowDialog(
                    show = isVisible,
                    title = req.title,
                    onDismissRequest = { hostState.dismiss() },
                    onDismissFinished = {
                        displayedRequest = null
                        isClosing = false
                    },
                    content = {
                        req.content { hostState.dismiss() }
                    }
                )
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassDialogHost provides hostState
    ) {
        Box(Modifier.fillMaxSize()) {
            // Main content underneath with dynamic smooth animated blur
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (animProgress > 0.001f && componentStyle != dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                            Modifier.blur((24.dp * animProgress))
                        } else {
                            Modifier
                        }
                    )
            ) {
                content()
            }

            // Full-screen overlay with smooth dimming and AndroidLiquidGlass styled Dialog
            if (animProgress > 0.001f && displayedRequest != null && componentStyle != dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
                val req = displayedRequest!!
                val isDark = isSystemInDarkTheme()
                val cardBg = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF)
                val inputBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                val accentColor = colors.accentBlue

                fun requestClose() {
                    if (req is GlassDialogRequest.Confirmation) {
                        req.onCancel?.invoke()
                    } else if (req is GlassDialogRequest.TextInput) {
                        req.onCancel?.invoke()
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
                            .padding(26.dp)
                    ) {
                        when (req) {
                            is GlassDialogRequest.CustomValue -> {
                                var textValue by remember(req) {
                                    mutableStateOf(
                                        if (req.currentValue % 1f == 0f && req.valueRange.endInclusive > 10f) {
                                            req.currentValue.toInt().toString()
                                        } else {
                                            String.format(Locale.US, "%.3f", req.currentValue).trimEnd('0').trimEnd('.')
                                        }
                                    )
                                }

                                fun requestApply() {
                                    val parsed = textValue.toFloatOrNull()
                                    if (parsed != null) {
                                        req.onApply(parsed)
                                    }
                                    hostState.dismiss()
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    BasicText(
                                        text = "Custom Value",
                                        style = HyperBounceType.headline.copy(
                                            color = colors.label,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    BasicText(
                                        text = "${req.title}\nStock Range: [${req.valueRange.start} .. ${req.valueRange.endInclusive}]  •  Default: ${req.defaultValue}",
                                        style = HyperBounceType.footnote.copy(
                                            color = colors.secondaryLabel,
                                            lineHeight = 20.sp
                                        )
                                    )

                                    // Input box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(inputBg)
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(cancelBtnBg)
                                                .clickable { requestClose() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "Cancel",
                                                style = TextStyle(
                                                    color = colors.label,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                                .clickable { requestApply() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "Apply",
                                                style = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            is GlassDialogRequest.Confirmation -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    BasicText(
                                        text = req.title,
                                        style = HyperBounceType.headline.copy(
                                            color = colors.label,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    BasicText(
                                        text = req.message,
                                        style = HyperBounceType.footnote.copy(
                                            color = colors.secondaryLabel,
                                            fontSize = 14.5.sp,
                                            lineHeight = 21.sp
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(cancelBtnBg)
                                                .clickable { requestClose() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = req.cancelText,
                                                style = TextStyle(
                                                    color = colors.label,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }

                                        val btnConfirmColor = req.confirmColor ?: accentColor
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(btnConfirmColor)
                                                .clickable {
                                                    req.onConfirm()
                                                    hostState.dismiss()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = req.confirmText,
                                                style = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            is GlassDialogRequest.TextInput -> {
                                var textValue by remember(req) { mutableStateOf(req.initialValue) }

                                fun requestApply() {
                                    val trimmed = textValue.trim()
                                    if (trimmed.isNotBlank()) {
                                        req.onConfirm(trimmed)
                                    }
                                    hostState.dismiss()
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    BasicText(
                                        text = req.title,
                                        style = HyperBounceType.headline.copy(
                                            color = colors.label,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    if (!req.message.isNullOrBlank()) {
                                        BasicText(
                                            text = req.message,
                                            style = HyperBounceType.footnote.copy(
                                                color = colors.secondaryLabel,
                                                fontSize = 14.5.sp,
                                                lineHeight = 21.sp
                                            )
                                        )
                                    }

                                    // Input box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(inputBg)
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        BasicTextField(
                                            value = textValue,
                                            onValueChange = { textValue = it },
                                            textStyle = HyperBounceType.body.copy(
                                                color = colors.label,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            singleLine = true,
                                            cursorBrush = SolidColor(accentColor),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Non-stretchable capsule buttons matching Confirmation dialog
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(cancelBtnBg)
                                                .clickable { requestClose() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = req.cancelText,
                                                style = TextStyle(
                                                    color = colors.label,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }

                                        val btnConfirmColor = req.confirmColor ?: accentColor
                                        val isEnabled = textValue.trim().isNotBlank()
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .clip(CircleShape)
                                                .background(if (isEnabled) btnConfirmColor else btnConfirmColor.copy(alpha = 0.4f))
                                                .clickable(enabled = isEnabled) { requestApply() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = req.confirmText,
                                                style = TextStyle(
                                                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            is GlassDialogRequest.Custom -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    BasicText(
                                        text = req.title,
                                        style = HyperBounceType.headline.copy(
                                            color = colors.label,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )

                                    req.content { hostState.dismiss() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Backward compatibility alias for CustomValueDialogHost
@Composable
fun CustomValueDialogHost(
    colors: HyperBounceColors,
    content: @Composable () -> Unit
) {
    GlassDialogHost(colors = colors, content = content)
}
