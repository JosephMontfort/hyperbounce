package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.LiquidSlider
import dev.joseph.hyperbounce.ui.glass.LiquidToggle
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.roundToInt

/** Small caps-style section label above a [GlassCard], iOS grouped-list style. */
@Composable
fun SectionHeader(text: String, colors: HyperBounceColors, modifier: Modifier = Modifier) {
    BasicText(
        text.uppercase(),
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
        style = HyperBounceType.caption.copy(
            color = colors.secondaryLabel,
            letterSpacing = 0.6.sp()
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
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop
        )
    }
}

/** A title + live value label, with a [LiquidSlider] beneath it. Used for every backdrop
 *  effect parameter (blur radius, lens size, vibrancy, etc.) across the app. */
@Composable
fun SettingsSliderRow(
    title: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val displayAlpha = if (enabled) 1f else 0.35f
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
            BasicText(
                title,
                style = HyperBounceType.body.copy(color = colors.label.copy(alpha = displayAlpha))
            )
            BasicText(
                valueLabel(value),
                style = HyperBounceType.monoValue.copy(color = colors.secondaryLabel.copy(alpha = displayAlpha))
            )
        }
        if (subtitle != null) {
            BasicText(
                subtitle,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                style = HyperBounceType.footnote.copy(color = colors.secondaryLabel.copy(alpha = displayAlpha))
            )
        }
        Box(Modifier.padding(top = 8.dp)) {
            if (enabled) {
                LiquidSlider(
                    value = { value },
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
                    backdrop = backdrop,
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
