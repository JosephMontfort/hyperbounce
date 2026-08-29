package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.NavBarPreview
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.AccentPreset
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.state.ThemeMode
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.roundToInt

@Composable
fun AppDetailScreen(
    app: NavApp,
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val holder = repository.appSettings(app)
    val s = holder.snapshot()
    val accentColor = accentColorFor(s.accent, colors)

    ScreenScaffold(
        title = app.displayName,
        subtitle = app.packageName,
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        NavBarPreview(
            backdrop = backdrop,
            accentColor = accentColor,
            glassIntensity = s.glassIntensity,
            containerOpacity = s.containerOpacity,
            cornerRadiusDp = s.cornerRadius,
            barHeightDp = s.barHeight,
            tabWidthDp = s.tabWidth,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsToggleRow(
                title = "Floating Nav Bar",
                subtitle = if (s.enabled) "Liquid glass replaces the stock bar" else "Stock nav bar restored",
                colors = colors,
                iconTint = accentColor.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = s.enabled,
                onCheckedChange = holder::setEnabled,
                icon = { PowerIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
            )
        }

        SectionHeader("Glass Effect", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Glass Intensity",
                colors = colors,
                backdrop = backdrop,
                value = s.glassIntensity,
                onValueChange = holder::setGlassIntensity,
                valueRange = 0.4f..2.0f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                enabled = s.enabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "Container Opacity",
                colors = colors,
                backdrop = backdrop,
                value = s.containerOpacity,
                onValueChange = holder::setContainerOpacity,
                valueRange = 0f..1f,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                enabled = s.enabled
            )
        }
        SectionFooter(
            "Glass Intensity scales blur and refraction together. Container Opacity is the tint behind the glass.",
            colors
        )

        SectionHeader("Shape & Size", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Corner Radius",
                colors = colors,
                backdrop = backdrop,
                value = s.cornerRadius,
                onValueChange = holder::setCornerRadius,
                valueRange = 12f..40f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = s.enabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "Bar Height",
                colors = colors,
                backdrop = backdrop,
                value = s.barHeight,
                onValueChange = holder::setBarHeight,
                valueRange = 48f..88f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = s.enabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "Tab Width",
                colors = colors,
                backdrop = backdrop,
                value = s.tabWidth,
                onValueChange = holder::setTabWidth,
                valueRange = 48f..88f,
                valueLabel = { "${it.roundToInt()}dp" },
                enabled = s.enabled
            )
        }

        SectionHeader("Appearance", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("Theme", style = HyperBounceType.body.copy(color = colors.label))
                DropdownPicker(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = ThemeMode.entries.indexOf(s.themeMode),
                    onSelect = { holder.setThemeMode(ThemeMode.entries[it]) },
                    backdrop = backdrop
                )
            }
            RowDivider(colors.separator, insetStart = 16.dp)

            BasicText(
                "Accent Color",
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
                style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccentPreset.entries.forEach { preset ->
                    AccentSwatch(
                        preset = preset,
                        colors = colors,
                        selected = preset == s.accent,
                        onClick = { holder.setAccent(preset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    colors: HyperBounceColors,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = accentColorFor(preset, colors)
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            CheckmarkIcon(color = Color.White, size = 14.dp, strokeWidth = 2.dp)
        }
    }
}

private fun accentColorFor(preset: AccentPreset, colors: HyperBounceColors): Color =
    preset.argb?.let { Color(it) } ?: colors.accentBlue
