package dev.joseph.hyperbounce.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.LiquidSlider
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorPickerDialog(
    initialColor: Long,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    val hsv = remember(initialColor) {
        val array = FloatArray(3)
        val argb = initialColor.toInt()
        AndroidColor.colorToHSV(argb, array)
        array
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceIn(0.01f, 1f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.1f, 1f)) }

    val currentColorInt by remember {
        derivedStateOf {
            AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        }
    }
    val currentColor by remember {
        derivedStateOf {
            Color(currentColorInt)
        }
    }

    var hexInput by remember {
        mutableStateOf(String.format("%06X", 0xFFFFFF and initialColor.toInt()))
    }

    fun syncHexFromHsv(h: Float, s: Float, v: Float) {
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        hexInput = String.format("%06X", 0xFFFFFF and colorInt)
    }

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF242424) else Color(0xFFFFFFFF)
    val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
        ),
        label = "colorPickerDialogAnim"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * animProgress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 360.dp)
                    .graphicsLayer {
                        alpha = animProgress
                        scaleX = 0.85f + 0.15f * animProgress
                        scaleY = 0.85f + 0.15f * animProgress
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(cardBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(24.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BasicText(
                        "Custom Accent Color",
                        style = HyperBounceType.title2.copy(
                            color = colors.label,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    // Circular Color Wheel
                    Box(
                        Modifier
                            .size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    fun updateFromOffset(pos: Offset, size: androidx.compose.ui.unit.IntSize) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = pos.x - center.x
                                        val dy = pos.y - center.y
                                        val radius = minOf(size.width, size.height) / 2f
                                        val dist = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                                        hue = angle
                                        saturation = dist.coerceIn(0.01f, 1f)
                                        syncHexFromHsv(hue, saturation, value)
                                    }

                                    detectTapGestures { pos ->
                                        updateFromOffset(pos, size)
                                    }
                                }
                                .pointerInput(Unit) {
                                    fun updateFromOffset(pos: Offset, size: androidx.compose.ui.unit.IntSize) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = pos.x - center.x
                                        val dy = pos.y - center.y
                                        val radius = minOf(size.width, size.height) / 2f
                                        val dist = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                                        hue = angle
                                        saturation = dist.coerceIn(0.01f, 1f)
                                        syncHexFromHsv(hue, saturation, value)
                                    }

                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        updateFromOffset(change.position, size)
                                    }
                                }
                        ) {
                            val radius = size.minDimension / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)

                            val sweepColors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                            drawCircle(
                                brush = Brush.sweepGradient(sweepColors, center),
                                radius = radius,
                                center = center
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color.White, Color.Transparent),
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center
                            )
                            // Brightness shading
                            if (value < 1f) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 1f - value),
                                    radius = radius,
                                    center = center
                                )
                            }

                            // Thumb position
                            val angleRad = Math.toRadians(hue.toDouble())
                            val thumbDist = saturation * radius
                            val thumbX = center.x + (thumbDist * cos(angleRad)).toFloat()
                            val thumbY = center.y + (thumbDist * sin(angleRad)).toFloat()

                            drawCircle(
                                color = Color.Black.copy(alpha = 0.4f),
                                radius = 13.dp.toPx(),
                                center = Offset(thumbX, thumbY + 1.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY)
                            )
                            drawCircle(
                                color = currentColor,
                                radius = 9.dp.toPx(),
                                center = Offset(thumbX, thumbY)
                            )
                        }
                    }

                    // Brightness slider
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText("Brightness", style = HyperBounceType.subhead.copy(color = colors.secondaryLabel))
                            BasicText("${(value * 100).toInt()}%", style = HyperBounceType.subhead.copy(color = colors.label))
                        }
                        LiquidSlider(
                            value = { value },
                            onValueChange = {
                                value = it
                                syncHexFromHsv(hue, saturation, value)
                            },
                            valueRange = 0.1f..1f,
                            visibilityThreshold = 0.01f,
                            backdrop = backdrop
                        )
                    }

                    // Preview Swatch & Hex Display
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.elevatedSurfaceTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, colors.separator, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    "#",
                                    style = HyperBounceType.body.copy(
                                        color = colors.secondaryLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Spacer(Modifier.width(2.dp))
                                BasicTextField(
                                    value = hexInput,
                                    onValueChange = { rawInput ->
                                        val stripped = rawInput.replace("#", "").trim()
                                        val filtered = stripped.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
                                        hexInput = filtered
                                        if (filtered.length == 6) {
                                            try {
                                                val parsed = AndroidColor.parseColor("#$filtered")
                                                val hsvArr = FloatArray(3)
                                                AndroidColor.colorToHSV(parsed, hsvArr)
                                                hue = hsvArr[0]
                                                saturation = hsvArr[1].coerceIn(0.01f, 1f)
                                                value = hsvArr[2].coerceIn(0.1f, 1f)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(colors.accentBlue),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Ascii
                                    ),
                                    modifier = Modifier.width(72.dp)
                                )
                            }
                        }
                        BasicText(
                            "HEX",
                            style = HyperBounceType.caption.copy(
                                color = colors.secondaryLabel,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Action buttons
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .background(cancelBtnBg)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                "Cancel",
                                style = TextStyle(
                                    color = colors.label,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .clickable {
                                    val finalArgb = (0xFF000000L or (currentColorInt.toLong() and 0xFFFFFFFFL))
                                    onColorSelected(finalArgb)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor = if (value > 0.5f && saturation < 0.5f) Color.Black else Color.White
                            BasicText(
                                "Apply",
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = 15.5.sp,
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
