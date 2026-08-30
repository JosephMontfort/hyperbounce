package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.LockIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet

    ScreenScaffold(
        title = "Lock Screen Sheet",
        subtitle = "AGSL Liquid Glass & sheet translation",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Lock Screen Sheet",
                subtitle = if (sheet.enabled) "120Hz translation & AGSL active" else "Disabled (Stock)",
                colors = colors,
                iconTint = colors.accentBlue,
                backdrop = backdrop,
                checked = sheet.enabled,
                onCheckedChange = sheet::setEnabled,
                icon = { LockIcon(color = Color.White, size = 15.dp) }
            )
        }
        SectionFooter(
            "Enables true upward translation of the lockscreen root container on swipe up, completely removing stock shrink-to-center scaling degradation.",
            colors
        )

        SectionHeader("4-Sided AGSL Refraction Shader", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Border Width",
                subtitle = "Specular frame outline stroke width",
                colors = colors,
                backdrop = backdrop,
                value = sheet.borderWidth,
                onValueChange = sheet::setBorderWidth,
                valueRange = 0.5f..8.0f,
                valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Border Specular Alpha",
                subtitle = "Fresnel specular frame opacity",
                colors = colors,
                backdrop = backdrop,
                value = sheet.borderAlpha,
                onValueChange = sheet::setBorderAlpha,
                valueRange = 0.0f..1.0f,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Refraction Height",
                subtitle = "Optical light bending boundary height",
                colors = colors,
                backdrop = backdrop,
                value = sheet.refractionHeight,
                onValueChange = sheet::setRefractionHeight,
                valueRange = 0.0f..60.0f,
                valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Refraction Amount",
                subtitle = "Strength of optical lens distortion along card edges",
                colors = colors,
                backdrop = backdrop,
                value = sheet.refractionAmount,
                onValueChange = sheet::setRefractionAmount,
                valueRange = 0.0f..80.0f,
                valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "3D Depth Warp",
                subtitle = "Spherical warp factor along signed distance fields",
                colors = colors,
                backdrop = backdrop,
                value = sheet.depthEffect,
                onValueChange = sheet::setDepthEffect,
                valueRange = 0.0f..0.40f,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
                enabled = sheet.enabled
            )
        }
        SectionFooter(
            "Uses Android 13+ RuntimeShader AGSL evaluation of sdRoundedRect SDF fields to cast true optical light bending onto keyguard elements.",
            colors
        )

        SectionHeader("Home Indicator Pill & Glass Lip", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Home Indicator Width",
                subtitle = "Horizontal width of iOS home bar pill",
                colors = colors,
                backdrop = backdrop,
                value = sheet.pillWidth,
                onValueChange = sheet::setPillWidth,
                valueRange = 40.0f..280.0f,
                valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Home Indicator Height",
                subtitle = "Vertical thickness of iOS home bar pill",
                colors = colors,
                backdrop = backdrop,
                value = sheet.pillHeight,
                onValueChange = sheet::setPillHeight,
                valueRange = 2.0f..16.0f,
                valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Home Indicator Bottom Margin",
                subtitle = "Distance from screen bottom edge",
                colors = colors,
                backdrop = backdrop,
                value = sheet.pillBottomMargin,
                onValueChange = sheet::setPillBottomMargin,
                valueRange = 0.0f..35.0f,
                valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                enabled = sheet.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Bottom Glass Lip Height",
                subtitle = "Height of frosted specular bottom lip area",
                colors = colors,
                backdrop = backdrop,
                value = sheet.lipHeight,
                onValueChange = sheet::setLipHeight,
                valueRange = 10.0f..70.0f,
                valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                enabled = sheet.enabled
            )
        }
        SectionFooter(
            "Injects a specular glass rim and dynamic rounded pill directly into KeyguardBottomAreaView.",
            colors
        )
    }
}
