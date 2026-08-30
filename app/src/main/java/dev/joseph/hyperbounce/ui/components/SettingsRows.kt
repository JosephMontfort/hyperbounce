package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.LiquidSlider
import dev.joseph.hyperbounce.ui.glass.LiquidToggle
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Small caps-style section label above a [GlassCard], iOS grouped-list style. */
@Composable
fun SectionHeader(text: String, colors: HyperBounceColors, modifier: Modifier = Modifier) {
    BasicText(
        text.uppercase(),
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
        style = HyperBounceType.caption.copy(
            color = colors.secondaryLabel,
            letterSpacing = 0.6f.sp()
        )
    )
}

/** Small footnote below a [GlassCard] explaining what the section controls. */
@Composable
fun SectionFooter(text: String, colors: HyperBounceColors, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
        style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
    )
}

private fun Float.sp() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun IconChip(tint: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(29.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** A tappable row that navigates to a detail screen, with a leading icon and trailing chevron. */
@Composable
fun SettingsNavRow(
    title: String,
    colors: HyperBounceColors,
    iconTint: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconChip(iconTint) { icon() }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = HyperBounceType.body.copy(color = colors.label))
            if (subtitle != null) {
                BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
            }
        }
        ChevronIcon(color = colors.tertiaryLabel, size = 11.dp, strokeWidth = 1.8.dp, pointingLeft = false)
    }
}

/** A tappable action row with a leading icon, title/subtitle, and no trailing chevron. */
@Composable
fun SettingsClickableRow(
    title: String,
    colors: HyperBounceColors,
    iconTint: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color? = null,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconChip(iconTint) { icon() }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = HyperBounceType.body.copy(color = titleColor ?: colors.label))
            if (subtitle != null) {
                BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
            }
        }
    }
}

/** A row with a leading icon, title/subtitle, and a trailing [LiquidToggle]. */
@Composable
fun SettingsToggleRow(
    title: String,
    colors: HyperBounceColors,
    iconTint: Color,
    backdrop: Backdrop,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconChip(iconTint) { icon() }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = HyperBounceType.body.copy(color = colors.label))
            if (subtitle != null) {
                BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
            }
        }
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        val localBackdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop { drawRect(cardColor) }
        
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = localBackdrop
        )
    }
}

/** A title + live value label, with a circular reset button and [LiquidSlider] beneath it.
 *  Long-pressing the value label opens a dialog to set an exact custom numeric value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSliderRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    defaultValue: Float = valueRange.start,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val displayAlpha = if (enabled) 1f else 0.35f
    var showCustomValueDialog by remember { mutableStateOf(false) }

    if (showCustomValueDialog) {
        CustomValueDialog(
            title = title,
            currentValue = value,
            defaultValue = defaultValue,
            valueRange = valueRange,
            colors = colors,
            onDismiss = { showCustomValueDialog = false },
            onApply = { newValue ->
                onValueChange(newValue)
                showCustomValueDialog = false
            }
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
            ) {
                BasicText(
                    title,
                    style = HyperBounceType.body.copy(color = colors.label.copy(alpha = displayAlpha))
                )
                if (subtitle != null) {
                    BasicText(
                        subtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = HyperBounceType.footnote.copy(color = colors.secondaryLabel.copy(alpha = displayAlpha))
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Circular Reset Button (resets to defaultValue in real time)
                val isModified = abs(value - defaultValue) > 0.0001f
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isModified) colors.accentBlue.copy(alpha = 0.2f)
                            else colors.separator.copy(alpha = 0.25f)
                        )
                        .clickable(enabled = enabled) {
                            onValueChange(defaultValue)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ResetArrowIcon(
                        color = if (isModified) colors.accentBlue else colors.secondaryLabel.copy(alpha = 0.5f),
                        size = 11.dp,
                        strokeWidth = 1.6.dp
                    )
                }

                // Numeric Value Label (Long-press to enter custom value)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            enabled = enabled,
                            onClick = {},
                            onLongClick = { showCustomValueDialog = true }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    BasicText(
                        valueLabel(value),
                        style = HyperBounceType.monoValue.copy(color = colors.secondaryLabel.copy(alpha = displayAlpha))
                    )
                }
            }
        }

        Box(Modifier.padding(top = 8.dp)) {
            if (enabled) {
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                val localBackdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop { drawRect(cardColor) }

                LiquidSlider(
                    value = { value },
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
                    backdrop = localBackdrop,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Static disabled track — no interaction, no backdrop capture needed.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.separator.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
fun CustomValueDialog(
    title: String,
    currentValue: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: HyperBounceColors,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    var textValue by remember {
        mutableStateOf(
            if (currentValue % 1f == 0f && valueRange.endInclusive > 10f) {
                currentValue.toInt().toString()
            } else {
                String.format(Locale.US, "%.3f", currentValue).trimEnd('0').trimEnd('.')
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val cardBg = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7)
        val inputBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(22.dp))
                .background(cardBg)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BasicText(
                    text = "Custom Value",
                    style = HyperBounceType.headline.copy(color = colors.label)
                )

                BasicText(
                    text = "$title\nStock Range: [${valueRange.start} .. ${valueRange.endInclusive}]  •  Default: $defaultValue",
                    style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
                )

                // Input box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(inputBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        textStyle = HyperBounceType.body.copy(color = colors.label),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accentBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = "Cancel",
                            style = HyperBounceType.body.copy(color = colors.secondaryLabel)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentBlue)
                            .clickable {
                                val parsed = textValue.toFloatOrNull()
                                if (parsed != null) {
                                    onApply(parsed)
                                } else {
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = "Apply",
                            style = HyperBounceType.body.copy(color = Color.White)
                        )
                    }
                }
            }
        }
    }
}
