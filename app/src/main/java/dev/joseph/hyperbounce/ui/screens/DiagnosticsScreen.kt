package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun DiagnosticsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "System Diagnostics",
        subtitle = "Control Center and Lockscreen experiments",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { contentBackdrop ->
        val combinedBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)

        SectionHeader("Control Center", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SettingsToggleRow(
                title = "ccLowEndAnim",
                colors = colors,
                backdrop = combinedBackdrop,
                iconTint = colors.accentOrange,
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = androidx.compose.ui.graphics.Color.White, size = 14.dp, strokeWidth = 1.8.dp) },
                checked = repository.ccLowEndAnim,
                onCheckedChange = repository::setCcLowEndAnim
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsToggleRow(
                title = "ccAnimEnabled",
                colors = colors,
                backdrop = combinedBackdrop,
                iconTint = colors.accentOrange,
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = androidx.compose.ui.graphics.Color.White, size = 14.dp, strokeWidth = 1.8.dp) },
                checked = repository.ccAnimEnabled,
                onCheckedChange = repository::setCcAnimEnabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsToggleRow(
                title = "ccModularEditEnabled",
                colors = colors,
                backdrop = combinedBackdrop,
                iconTint = colors.accentOrange,
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = androidx.compose.ui.graphics.Color.White, size = 14.dp, strokeWidth = 1.8.dp) },
                checked = repository.ccModularEditEnabled,
                onCheckedChange = repository::setCcModularEditEnabled
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsToggleRow(
                title = "ccJellyEnabled",
                colors = colors,
                backdrop = combinedBackdrop,
                iconTint = colors.accentOrange,
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = androidx.compose.ui.graphics.Color.White, size = 14.dp, strokeWidth = 1.8.dp) },
                checked = repository.ccJellyEnabled,
                onCheckedChange = repository::setCcJellyEnabled
            )
        }
        
        SectionHeader("Lockscreen Card", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SettingsSliderRow(
                title = "lockCardBorderWidth",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockCardBorderWidth,
                onValueChange = repository::setLockCardBorderWidth,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockCardBorderAlpha",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockCardBorderAlpha,
                onValueChange = repository::setLockCardBorderAlpha,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockCardRefractionHeight",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockCardRefractionHeight,
                onValueChange = repository::setLockCardRefractionHeight,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockCardRefractionAmount",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockCardRefractionAmount,
                onValueChange = repository::setLockCardRefractionAmount,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockCardDepthEffect",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockCardDepthEffect,
                onValueChange = repository::setLockCardDepthEffect,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockPillWidth",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockPillWidth,
                onValueChange = repository::setLockPillWidth,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockPillHeight",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockPillHeight,
                onValueChange = repository::setLockPillHeight,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockPillBottomMargin",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockPillBottomMargin,
                onValueChange = repository::setLockPillBottomMargin,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "lockLipHeight",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.lockLipHeight,
                onValueChange = repository::setLockLipHeight,
                valueRange = 0f..100f,
                valueLabel = { "${it.roundToInt()}" }
            )
        }
        
        SectionHeader("Pinpad", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SettingsSliderRow(
                title = "pinDigitTextSize",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinDigitTextSize,
                onValueChange = repository::setPinDigitTextSize,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinRow1Offset",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinRow1Offset,
                onValueChange = repository::setPinRow1Offset,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinRow2Offset",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinRow2Offset,
                onValueChange = repository::setPinRow2Offset,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinRow3Offset",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinRow3Offset,
                onValueChange = repository::setPinRow3Offset,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinRow0Offset",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinRow0Offset,
                onValueChange = repository::setPinRow0Offset,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinTapSquishScale",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinTapSquishScale,
                onValueChange = repository::setPinTapSquishScale,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinReleasePlunge",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinReleasePlunge,
                onValueChange = repository::setPinReleasePlunge,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsToggleRow(
                title = "pinShowKlondike",
                colors = colors,
                backdrop = combinedBackdrop,
                iconTint = colors.accentOrange,
                icon = { dev.joseph.hyperbounce.ui.components.InfoIcon(color = androidx.compose.ui.graphics.Color.White, size = 14.dp, strokeWidth = 1.8.dp) },
                checked = repository.pinShowKlondike,
                onCheckedChange = repository::setPinShowKlondike
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinEntryTranslationY",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinEntryTranslationY,
                onValueChange = repository::setPinEntryTranslationY,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinEntryInitialScale",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinEntryInitialScale,
                onValueChange = repository::setPinEntryInitialScale,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinEntryDuration",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinEntryDuration,
                onValueChange = repository::setPinEntryDuration,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinRowDelayStep",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinRowDelayStep,
                onValueChange = repository::setPinRowDelayStep,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinColDelayStep",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinColDelayStep,
                onValueChange = repository::setPinColDelayStep,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            SettingsSliderRow(
                title = "pinEntryOvershoot",
                colors = colors,
                backdrop = combinedBackdrop,
                value = repository.pinEntryOvershoot,
                onValueChange = repository::setPinEntryOvershoot,
                valueRange = 0f..500f,
                valueLabel = { "${it.roundToInt()}" }
            )
        }
    }
}
