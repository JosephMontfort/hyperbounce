package dev.joseph.hyperbounce.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.InfoIcon
import dev.joseph.hyperbounce.ui.components.MenuRow
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    val glass = LocalGlassSettings.current
    val menuAnimationScope = rememberCoroutineScope()
    val menuProgress = remember { Animatable(0f) }
    val menuExpanded = menuProgress.value > 0.05f || menuProgress.targetValue > 0.5f

    fun closeMenu() {
        menuAnimationScope.launch {
            menuProgress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    BasicText("HyperBounce", style = HyperBounceType.largeTitle.copy(color = colors.label))
                    BasicText(
                        "Liquid glass, everywhere",
                        style = HyperBounceType.subhead.copy(color = colors.secondaryLabel)
                    )
                }
                Spacer(Modifier.size(44.dp)) // reserved footprint; the real menu is drawn as an overlay below
            }

            Spacer(Modifier.height(20.dp))

            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "HyperBounce Enabled",
                    subtitle = if (repository.moduleEnabled) "All liquid glass effects are active" else "Effects paused everywhere",
                    colors = colors,
                    iconTint = colors.accentGreen.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = repository.moduleEnabled,
                    onCheckedChange = repository::setModuleEnabled,
                    icon = { PowerIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
            }

            SectionHeader("Appearance", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass Effects",
                    subtitle = if (glass.enabled) "Real blur & refraction everywhere" else "Flat translucent surfaces",
                    colors = colors,
                    iconTint = colors.accentBlue.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = glass.enabled,
                    onCheckedChange = repository.globalGlass::setEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 14.dp) }
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Blur",
                    colors = colors,
                    backdrop = backdrop,
                    value = glass.blurDp,
                    onValueChange = repository.globalGlass::setBlurDp,
                    valueRange = 0f..16f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = glass.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Refraction Height",
                    colors = colors,
                    backdrop = backdrop,
                    value = glass.refractionHeightDp,
                    onValueChange = repository.globalGlass::setRefractionHeightDp,
                    valueRange = 0f..24f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = glass.enabled
                )
                RowDivider(colors.separator, insetStart = 16.dp)
                SettingsSliderRow(
                    title = "Refraction Amount",
                    colors = colors,
                    backdrop = backdrop,
                    value = glass.refractionAmountDp,
                    onValueChange = repository.globalGlass::setRefractionAmountDp,
                    valueRange = 0f..30f,
                    valueLabel = { "${it.roundToInt()}dp" },
                    enabled = glass.enabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Chromatic Aberration",
                    colors = colors,
                    iconTint = colors.accentOrange.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = glass.chromaticAberration,
                    onCheckedChange = repository.globalGlass::setChromaticAberration,
                    icon = { SlidersIcon(color = Color.White, size = 14.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Haptic Feedback",
                    subtitle = "Felt when dragging over the menu",
                    colors = colors,
                    iconTint = colors.accentGreen.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = glass.hapticsEnabled,
                    onCheckedChange = repository.globalGlass::setHapticsEnabled,
                    icon = { PowerIcon(color = Color.White, size = 14.dp) }
                )
            }
            SectionFooter(
                "Applies to buttons, cards, and the menu above. Toggles and sliders always render with full glass.",
                colors
            )

            SectionHeader("Apps", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                NavApp.entries.forEachIndexed { index, app ->
                    SettingsNavRow(
                        title = app.displayName,
                        subtitle = navSummary(repository, app),
                        colors = colors,
                        iconTint = appTint(app),
                        onClick = { onNavigate(Destination.AppDetail(app)) },
                        icon = { AppMonogram(app) }
                    )
                    if (index != NavApp.entries.lastIndex) {
                        RowDivider(colors.separator)
                    }
                }
            }

            SectionHeader("Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "Toggles & Sliders",
                    subtitle = engineSummary(repository),
                    colors = colors,
                    iconTint = colors.accentBlue.copy(alpha = 0.85f),
                    onClick = { onNavigate(Destination.Engine) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        // Full-screen scrim to dismiss the menu when tapping outside it — declared after
        // the scrollable content but BEFORE the menu itself, so it sits above the list and
        // below the menu in z-order (later declarations draw on top in a Box).
        if (menuExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { closeMenu() }
                    }
            )
        }

        // Top-right expandable glass menu overlay. verticalOffset = 30dp drops the
        // expanded panel a deliberate gap below the button rather than growing flush
        // against it.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 8.dp, end = 20.dp)
        ) {
            ExpandableGlassMenu(
                animatableProgress = menuProgress,
                animationPreset = MenuAnimationPreset.Bouncy,
                alignment = MenuAlignment.TopTrailing,
                backdrop = backdrop,
                isGlassEnabled = glass.enabled,
                isHapticsEnabled = glass.hapticsEnabled,
                cornerRadius = 22.dp,
                blurRadius = glass.blurDp,
                refractionHeight = glass.refractionHeightDp,
                refractionAmount = glass.refractionAmountDp,
                chromaticAberration = glass.chromaticAberration,
                horizontalOffset = 0f,
                verticalOffset = 30f,
                labelSize = Size(44f, 44f),
                label = { AppGridIcon(color = Color.White, size = 17.dp) }
            ) { globalTouch, closeMenuFromRow, hoveredIndex, setHovered ->
                MenuRow(
                    title = "Reset to Defaults",
                    description = "Restore every slider and toggle",
                    contentColor = Color.White,
                    secondaryColor = Color.White.copy(alpha = 0.7f),
                    isHovered = hoveredIndex == 0,
                    onHoverChange = { hovering -> if (hovering) setHovered(0) else if (hoveredIndex == 0) setHovered(null) },
                    globalTouchPosition = globalTouch,
                    onClick = {
                        closeMenuFromRow()
                        repository.resetToDefaults()
                    },
                    icon = { ResetArrowIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                MenuRow(
                    title = "About HyperBounce",
                    description = "Version, credits",
                    contentColor = Color.White,
                    secondaryColor = Color.White.copy(alpha = 0.7f),
                    isHovered = hoveredIndex == 1,
                    onHoverChange = { hovering -> if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) },
                    globalTouchPosition = globalTouch,
                    onClick = {
                        closeMenuFromRow()
                        onNavigate(Destination.About)
                    },
                    icon = { InfoIcon(color = Color.White, size = 15.dp, strokeWidth = 1.6.dp) }
                )
            }
        }
    }
}

@Composable
private fun AppMonogram(app: NavApp) {
    Box(
        Modifier
            .size(29.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.001f)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            app.displayName.take(1),
            style = HyperBounceType.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold)
        )
    }
}

private fun appTint(app: NavApp): Color = when (app) {
    NavApp.Instagram -> Color(0xFFE1306C).copy(alpha = 0.85f)
    NavApp.WhatsApp -> Color(0xFF25D366).copy(alpha = 0.85f)
    NavApp.YouTube -> Color(0xFFFF0000).copy(alpha = 0.85f)
}

private fun navSummary(repository: SettingsRepository, app: NavApp): String {
    val s = repository.appSettings(app)
    return if (!s.enabled) "Off" else "Glass ${(s.glassIntensity * 100).toInt()}% · ${s.cornerRadius.toInt()}dp corners"
}

private fun engineSummary(repository: SettingsRepository): String {
    val toggle = repository.toggleEngine.label
    val slider = repository.sliderEngine.label
    return if (toggle == slider) "Both using $toggle" else "Toggle: $toggle · Slider: $slider"
}
