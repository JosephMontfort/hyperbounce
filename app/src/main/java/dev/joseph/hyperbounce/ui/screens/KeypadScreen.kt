package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun KeypadScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad Physics",
        subtitle = "Fluid iOS geometry & jelly engine",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = keypad::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "iOS PIN Pad Physics",
                    subtitle = if (keypad.enabled) "Jelly squish & staggered entrance active" else "Disabled (Stock)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = keypad.enabled,
                    onCheckedChange = repository::setKeypadEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("Geometry & Typography", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Button Circle Diameter",
                    subtitle = "Outer diameter of numeric key circles",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.circleDiameter,
                    onValueChange = keypad::setCircleDiameter,
                    valueRange = 60.0f..94.0f,
                    defaultValue = 82.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Glass Fill Opacity",
                    subtitle = "Base translucent fill opacity",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.fillAlpha,
                    onValueChange = keypad::setFillAlpha,
                    valueRange = 0.0f..0.90f,
                    defaultValue = 0.20f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Specular Rim Width",
                    subtitle = "Directional specular stroke thickness",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.strokeWidth,
                    onValueChange = keypad::setStrokeWidth,
                    valueRange = 0.5f..4.0f,
                    defaultValue = 1.5f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Specular Rim Opacity",
                    subtitle = "Specular highlight rim brightness",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.strokeAlpha,
                    onValueChange = keypad::setStrokeAlpha,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.47f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Digit Font Size",
                    subtitle = "Typeface size for numbers 0-9",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.digitTextSize,
                    onValueChange = keypad::setDigitTextSize,
                    valueRange = 18.0f..48.0f,
                    defaultValue = 36.0f,
                    valueLabel = { String.format(Locale.US, "%.0f sp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Show Klondike Letters",
                    subtitle = "Display ABC, DEF subtitle letters below digits",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = keypad.showKlondike,
                    onCheckedChange = keypad::setShowKlondike,
                    icon = { SlidersIcon(color = Color.White, size = 14.dp) }
                )
            }
        }

        item {
            SectionHeader("Vertical Row Spacing", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "PIN Dots Y-Offset",
                    subtitle = "Vertical position of password dots indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.row0Offset,
                    onValueChange = keypad::setRow0Offset,
                    valueRange = -180.0f..0.0f,
                    defaultValue = -115.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Row 1 Offset (Keys 1, 2, 3)",
                    subtitle = "Vertical position offset for row 1 keys",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.row1Offset,
                    onValueChange = keypad::setRow1Offset,
                    valueRange = -160.0f..0.0f,
                    defaultValue = -90.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Row 2 Offset (Keys 4, 5, 6)",
                    subtitle = "Vertical position offset for row 2 keys",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.row2Offset,
                    onValueChange = keypad::setRow2Offset,
                    valueRange = -140.0f..0.0f,
                    defaultValue = -60.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Row 3 Offset (Keys 7, 8, 9)",
                    subtitle = "Vertical position offset for row 3 keys",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.row3Offset,
                    onValueChange = keypad::setRow3Offset,
                    valueRange = -120.0f..0.0f,
                    defaultValue = -30.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Liquid Glass Jelly Physics & Glow", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Tap Compression Scale",
                    subtitle = "Lower = deeper press squish, Higher = subtle press squish",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.tapSquishScale,
                    onValueChange = keypad::setTapSquishScale,
                    valueRange = 0.65f..0.98f,
                    defaultValue = 0.88f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Release Extra Plunge",
                    subtitle = "Lower = instant rebound snap, Higher = deeper momentary release plunge",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.releasePlunge,
                    onValueChange = keypad::setReleasePlunge,
                    valueRange = 0.0f..0.25f,
                    defaultValue = 0.10f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Anisotropic Jelly Stretch",
                    subtitle = "Lower = subtle stretch on drag, Higher = dramatic jelly stretch on drag",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.jellyMaxStretch,
                    onValueChange = keypad::setJellyMaxStretch,
                    valueRange = 0.0f..0.35f,
                    defaultValue = 0.16f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Elastic Pull Limit",
                    subtitle = "Lower = tight drag constraint, Higher = loose drag stretch range",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.jellyElasticLimit,
                    onValueChange = keypad::setJellyElasticLimit,
                    valueRange = 0.10f..0.80f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spring Bounce Stiffness",
                    subtitle = "Lower = slower oscillation frequency, Higher = snappier bounce frequency",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.springStiffness,
                    onValueChange = keypad::setSpringStiffness,
                    valueRange = 5.0f..150.0f,
                    defaultValue = 50.0f,
                    valueLabel = { String.format(Locale.US, "%.0f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spring Bounce Damping",
                    subtitle = "Lower = more bounce oscillation, Higher = stiffer vibration damping",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.springDamping,
                    onValueChange = keypad::setSpringDamping,
                    valueRange = 0.5f..15.0f,
                    defaultValue = 5.5f,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tap Specular Glow Opacity",
                    subtitle = "Brightness of touch contact highlight",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.pressGlowAlpha,
                    onValueChange = keypad::setPressGlowAlpha,
                    valueRange = 0.0f..0.80f,
                    defaultValue = 0.35f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tap Specular Glow Spread",
                    subtitle = "Radial spread of localized touch highlight",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.glowRadius,
                    onValueChange = keypad::setGlowRadius,
                    valueRange = 0.5f..2.5f,
                    defaultValue = 1.4f,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Sequential Entry Animation (Y-Axis Glide)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Initial Y-Axis Drop",
                    subtitle = "Starting distance below resting position",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryTranslationY,
                    onValueChange = keypad::setEntryTranslationY,
                    valueRange = 20.0f..300.0f,
                    defaultValue = 90.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Initial Entry Scale",
                    subtitle = "Starting scale multiplier during entrance",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryInitialScale,
                    onValueChange = keypad::setEntryInitialScale,
                    valueRange = 0.20f..1.0f,
                    defaultValue = 0.50f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Entry Glide Duration",
                    subtitle = "Total glide animation duration per key",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryDuration,
                    onValueChange = keypad::setEntryDuration,
                    valueRange = 150.0f..850.0f,
                    defaultValue = 420.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Row Stagger Delay Step",
                    subtitle = "Delay offset added per key row",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.rowDelayStep,
                    onValueChange = keypad::setRowDelayStep,
                    valueRange = 0.0f..150.0f,
                    defaultValue = 55.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Column Stagger Delay Step",
                    subtitle = "Delay offset added per key column",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.colDelayStep,
                    onValueChange = keypad::setColDelayStep,
                    valueRange = 0.0f..100.0f,
                    defaultValue = 25.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Glide Overshoot Bounce",
                    subtitle = "Lower = smooth gentle arrival, Higher = bouncy overshoot snap",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryOvershoot,
                    onValueChange = keypad::setEntryOvershoot,
                    valueRange = 0.0f..5.0f,
                    defaultValue = 1.8f,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    enabled = keypad.enabled
                )
            }
        }

        item {
            SectionHeader("Advanced Timing", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "Per-Key Custom Delays",
                    subtitle = "Configure independent millisecond delay per key",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    onClick = { onNavigate(Destination.KeypadDelays) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }
    }
}
