package dev.hyperios.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.ui.glass.LiquidSlider
import dev.hyperios.app.ui.glass.LiquidToggle
import dev.hyperios.app.ui.search.searchHighlight
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.hyperios.app.ui.glass.LocalGlassSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Small caps-style section label above a [GlassCard], iOS grouped-list style, or SmallTitle in MIUIX mode. */
@Composable
fun SectionHeader(text: String, colors: HyperBounceColors, modifier: Modifier = Modifier) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        SmallTitle(
            text = text,
            modifier = modifier
        )
    } else {
        BasicText(
            text.uppercase(),
            modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
            style = HyperBounceType.caption.copy(
                color = colors.secondaryLabel,
                letterSpacing = 0.6f.sp()
            )
        )
    }
}

/** Section footers removed per user preference across all tuning screens. */
@Composable
fun SectionFooter(text: String, colors: HyperBounceColors, modifier: Modifier = Modifier) {
    // Intentionally empty
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

/** A tappable row that navigates to a detail screen, with an optional leading icon and trailing chevron. */
@Composable
fun SettingsNavRow(
    title: String,
    colors: HyperBounceColors,
    onClick: () -> Unit,
    iconTint: Color? = null,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    searchKey: String? = null
) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        val displayAlpha = if (enabled) 1f else 0.35f
        ArrowPreference(
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = icon?.let { { IconChip(iconTint ?: Color.Transparent) { icon() } } },
            onClick = { if (enabled) onClick() },
            modifier = modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
        )
    } else {
        val displayAlpha = if (enabled) 1f else 0.35f
        Row(
            modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                IconChip(iconTint ?: Color.Transparent) { icon() }
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = HyperBounceType.headline.copy(color = colors.label, fontWeight = FontWeight.SemiBold))
                if (subtitle != null) {
                    BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
                }
            }
            ChevronIcon(color = colors.tertiaryLabel, size = 11.dp, strokeWidth = 1.8.dp, pointingLeft = false)
        }
    }
}

/** A tappable action row with an optional leading icon, title/subtitle, and no trailing chevron. */
@Composable
fun SettingsClickableRow(
    title: String,
    colors: HyperBounceColors,
    onClick: () -> Unit,
    iconTint: Color? = null,
    subtitle: String? = null,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    searchKey: String? = null
) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        val displayAlpha = if (enabled) 1f else 0.35f
        BasicComponent(
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = icon?.let { { IconChip(iconTint ?: Color.Transparent) { icon() } } },
            endActions = trailing?.let { { trailing() } },
            onClick = { if (enabled) onClick() },
            modifier = modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val bgAlpha by animateFloatAsState(if (isPressed && enabled) 0.15f else 0.0f)
        val displayAlpha = if (enabled) 1f else 0.35f

        Row(
            modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
                .then(
                    if (enabled) {
                        Modifier
                            .background(colors.label.copy(alpha = bgAlpha))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                IconChip(iconTint ?: Color.Transparent) { icon() }
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = HyperBounceType.headline.copy(color = titleColor ?: colors.label, fontWeight = FontWeight.SemiBold))
                if (subtitle != null) {
                    BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/** A row with an optional leading icon, title/subtitle, and a trailing [LiquidToggle]. */
@Composable
fun SettingsToggleRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    searchKey: String? = null
) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        val displayAlpha = if (enabled) 1f else 0.35f
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = icon?.let { { IconChip(iconTint ?: Color.Transparent) { icon() } } },
            modifier = modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
        )
    } else {
        val displayAlpha = if (enabled) 1f else 0.35f
        Row(
            modifier
                .searchHighlight(searchKey ?: title, colors)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .graphicsLayer { alpha = displayAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                IconChip(iconTint ?: Color.Transparent) { icon() }
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = HyperBounceType.headline.copy(color = colors.label, fontWeight = FontWeight.SemiBold))
                if (subtitle != null) {
                    BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
                }
            }
            val isDark = dev.hyperios.app.ui.theme.LocalIsDarkTheme.current
            val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            val localBackdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop { drawRect(cardColor) }
            
            LiquidToggle(
                selected = { checked },
                onSelect = { if (enabled) onCheckedChange(it) },
                backdrop = localBackdrop
            )
        }
    }
}

/** A row with an optional leading icon, title/subtitle, and a trailing [DropdownPicker]. */
@Composable
fun SettingsDropdownRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color? = null,
    searchKey: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val componentStyle = LocalComponentStyle.current
    val view = LocalView.current
    if (componentStyle == ComponentStyle.MIUIX) {
        val displayAlpha = if (enabled) 1f else 0.35f
        WindowDropdownPreference(
            items = options,
            selectedIndex = selectedIndex,
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = icon?.let { { IconChip(iconTint ?: Color.Transparent) { icon() } } },
            onSelectedIndexChange = { idx ->
                if (enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSelect(idx)
                }
            },
            modifier = modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
        )
    } else {
        val displayAlpha = if (enabled) 1f else 0.35f
        Row(
            modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha }
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                IconChip(iconTint ?: Color.Transparent) { icon() }
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    title,
                    style = HyperBounceType.headline.copy(color = colors.label, fontWeight = FontWeight.SemiBold)
                )
                if (subtitle != null) {
                    BasicText(subtitle, style = HyperBounceType.footnote.copy(color = colors.secondaryLabel))
                }
            }
            DropdownPicker(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = backdrop,
                enabled = enabled
            )
        }
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
    enabled: Boolean = true,
    step: Float? = null,
    allowCustomInput: Boolean = true,
    modifier: Modifier = Modifier,
    searchKey: String? = null
) {
    val componentStyle = LocalComponentStyle.current
    val customValueHost = dev.hyperios.app.ui.glass.LocalCustomValueHost.current
    val resolveSteppedValue: (Float) -> Float = { v ->
        if (step != null && step > 0f) {
            val stepsCount = Math.round((v - valueRange.start) / step)
            (valueRange.start + stepsCount * step).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            v
        }
    }
    if (componentStyle == ComponentStyle.MIUIX) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isModified = abs(value - defaultValue) > 0.0001f
        val view = LocalView.current
        val glass = LocalGlassSettings.current
        var lastTickLabel by remember { mutableStateOf(valueLabel(value)) }
        var lastTickTime by remember { mutableLongStateOf(0L) }

        val debouncedOnValueChange: (Float) -> Unit = { rawValue ->
            val newValue = resolveSteppedValue(rawValue)
            val newLabel = valueLabel(newValue)
            val now = android.os.SystemClock.uptimeMillis()
            if (glass.hapticsEnabled && newLabel != lastTickLabel && (now - lastTickTime >= 12L)) {
                performSliderTick(context, view)
                lastTickLabel = newLabel
                lastTickTime = now
            }
            onValueChange(newValue)
        }

        val displayAlpha = if (enabled) 1f else 0.35f
        BasicComponent(
            modifier = modifier
                .searchHighlight(searchKey ?: title, colors)
                .graphicsLayer { alpha = displayAlpha },
            title = title,
            summary = subtitle,
            endActions = {
                // Circular Reset Button (resets to defaultValue in real time)
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
                Spacer(Modifier.width(8.dp))
                // Numeric Value Label: Tap OR Long-press to enter custom value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            enabled = enabled && allowCustomInput,
                            onClick = {
                                if (allowCustomInput) {
                                    customValueHost.show(
                                        title = title,
                                        currentValue = value,
                                        defaultValue = defaultValue,
                                        valueRange = valueRange,
                                        onApply = onValueChange
                                    )
                                }
                            },
                            onLongClick = {
                                if (allowCustomInput) {
                                    customValueHost.show(
                                        title = title,
                                        currentValue = value,
                                        defaultValue = defaultValue,
                                        valueRange = valueRange,
                                        onApply = onValueChange
                                    )
                                }
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = valueLabel(value),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant
                    )
                }
            },
            bottomAction = {
                Slider(
                    value = value,
                    onValueChange = debouncedOnValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(value, valueRange) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val thumbRadius = height / 2f
                                val availableWidth = (width - 2f * thumbRadius).coerceAtLeast(0f)
                                val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                                val thumbX = thumbRadius + fraction * availableWidth
                                val hitRadius = thumbRadius * 1.5f + 16.dp.toPx()
                                val isOverThumb = abs(down.position.x - thumbX) <= hitRadius
                                if (isOverThumb) {
                                    SliderThumbTracker.isThumbTouched = true
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            if (event.changes.none { it.id == down.id && it.pressed }) break
                                        }
                                    } finally {
                                        SliderThumbTracker.isThumbTouched = false
                                    }
                                }
                            }
                        },
                    enabled = enabled,
                    valueRange = valueRange,
                    hapticEffect = SliderDefaults.SliderHapticEffect.None
                )
            },
            onClick = if (allowCustomInput && enabled) {
                {
                    customValueHost.show(
                        title = title,
                        currentValue = value,
                        defaultValue = defaultValue,
                        valueRange = valueRange,
                        onApply = onValueChange
                    )
                }
            } else null,
            enabled = enabled
        )
    } else {
        val displayAlpha = if (enabled) 1f else 0.35f
        val context = androidx.compose.ui.platform.LocalContext.current
        val view = LocalView.current
        val glass = LocalGlassSettings.current
        var lastTickLabel by remember { mutableStateOf(valueLabel(value)) }
        var lastTickTime by remember { mutableLongStateOf(0L) }

        val debouncedOnValueChange: (Float) -> Unit = { rawValue ->
            val newValue = resolveSteppedValue(rawValue)
            val newLabel = valueLabel(newValue)
            val now = android.os.SystemClock.uptimeMillis()
            if (glass.hapticsEnabled && newLabel != lastTickLabel && (now - lastTickTime >= 12L)) {
                performSliderTick(context, view)
                lastTickLabel = newLabel
                lastTickTime = now
            }
            onValueChange(newValue)
        }

        Column(
            modifier
                .searchHighlight(searchKey ?: title, colors)
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
                        .then(
                            if (allowCustomInput && enabled) {
                                Modifier.clickable {
                                    customValueHost.show(
                                        title = title,
                                        currentValue = value,
                                        defaultValue = defaultValue,
                                        valueRange = valueRange,
                                        onApply = onValueChange
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    BasicText(
                        title,
                        style = HyperBounceType.headline.copy(color = colors.label.copy(alpha = displayAlpha), fontWeight = FontWeight.SemiBold)
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

                    // Numeric Value Label: Tap OR Long-press to enter custom value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                enabled = enabled && allowCustomInput,
                                onClick = {
                                    if (allowCustomInput) {
                                        customValueHost.show(
                                            title = title,
                                            currentValue = value,
                                            defaultValue = defaultValue,
                                            valueRange = valueRange,
                                            onApply = onValueChange
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (allowCustomInput) {
                                        customValueHost.show(
                                            title = title,
                                            currentValue = value,
                                            defaultValue = defaultValue,
                                            valueRange = valueRange,
                                            onApply = onValueChange
                                        )
                                    }
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                    val isDark = dev.hyperios.app.ui.theme.LocalIsDarkTheme.current
                    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                    val localBackdrop = com.kyant.backdrop.backdrops.rememberCanvasBackdrop { drawRect(cardColor) }

                    LiquidSlider(
                        value = { value },
                        onValueChange = debouncedOnValueChange,
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
}


