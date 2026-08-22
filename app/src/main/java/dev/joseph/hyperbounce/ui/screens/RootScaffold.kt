package dev.joseph.hyperbounce.ui.screens

/*
 * Shared by both root tabs (Tuning, Settings): the collapsing large title, the progressive
 * blur behind the fixed top bar, and the top-right ExpandableGlassMenu (Reset to Defaults /
 * Copy Debug Info / About) — factored out so both tabs get identical behavior without
 * duplicating the Animatable/gesture-scrim/menu wiring.
 *
 * The menu supports both interaction styles at once: a plain tap on a row (MenuRow's own
 * .clickable), and iOS-context-menu-style long-press → drag across rows → release over one
 * to select it (ExpandableGlassMenu's onRowSelected, fired with the row index that was
 * hovered when the finger lifted).
 */

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.CopyIcon
import dev.joseph.hyperbounce.ui.components.InfoIcon
import dev.joseph.hyperbounce.ui.components.MenuRow
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.glass.ProgressiveBlurHeader
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch

@Composable
fun RootScaffold(
    title: String,
    subtitle: String,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    repository: SettingsRepository,
    onReset: () -> Unit,
    onAbout: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassSettings.current
    val clipboard = LocalClipboardManager.current
    val menuAnimationScope = rememberCoroutineScope()
    val menuProgress = remember { Animatable(0f) }
    val menuExpanded = menuProgress.value > 0.05f || menuProgress.targetValue > 0.5f

    val scrollState = rememberScrollState()
    val collapseProgress = (scrollState.value / 160f).fastCoerceIn(0f, 1f)
    val contentBackdrop = rememberLayerBackdrop()

    fun closeMenu() {
        menuAnimationScope.launch {
            menuProgress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true))
        }
    }

    fun copyDebugInfo() {
        val text = buildString {
            appendLine("HyperBounce 1.0.0 (UI Phase 1)")
            appendLine("Module: ${if (repository.moduleEnabled) "On" else "Off"}")
            appendLine("Toggle engine: ${repository.toggleEngine.label}")
            appendLine("Slider engine: ${repository.sliderEngine.label}")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
        clipboard.setText(AnnotatedString(text))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
                .safeDrawingPadding()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(56.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    Modifier.graphicsLayer {
                        alpha = 1f - collapseProgress * 0.85f
                        translationY = -collapseProgress * 6.dp.toPx()
                    }
                ) {
                    BasicText(title, style = HyperBounceType.largeTitle.copy(color = colors.label))
                    BasicText(subtitle, style = HyperBounceType.subhead.copy(color = colors.secondaryLabel))
                }
                Spacer(Modifier.size(44.dp)) // reserved footprint; the real menu is drawn as an overlay below
            }

            Spacer(Modifier.height(20.dp))

            content()

            // Clearance so the last card isn't hidden behind the floating bottom tab bar.
            Spacer(Modifier.height(110.dp))
        }

        ProgressiveBlurHeader(
            backdrop = contentBackdrop,
            tintColor = colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        // Small collapsed title, fades in behind the menu as the large title scrolls away.
        BasicText(
            title,
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = 20.dp, top = 14.dp)
                .graphicsLayer { alpha = collapseProgress },
            style = HyperBounceType.headline.copy(color = colors.label)
        )

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
                onRowSelected = { index ->
                    when (index) {
                        0 -> onReset()
                        1 -> copyDebugInfo()
                        2 -> onAbout()
                    }
                },
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
                        onReset()
                    },
                    icon = { ResetArrowIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                MenuRow(
                    title = "Copy Debug Info",
                    description = "Version & current engine state",
                    contentColor = Color.White,
                    secondaryColor = Color.White.copy(alpha = 0.7f),
                    isHovered = hoveredIndex == 1,
                    onHoverChange = { hovering -> if (hovering) setHovered(1) else if (hoveredIndex == 1) setHovered(null) },
                    globalTouchPosition = globalTouch,
                    onClick = {
                        closeMenuFromRow()
                        copyDebugInfo()
                    },
                    icon = { CopyIcon(color = Color.White, size = 14.dp, strokeWidth = 1.6.dp) }
                )
                MenuRow(
                    title = "About HyperBounce",
                    description = "Version, credits",
                    contentColor = Color.White,
                    secondaryColor = Color.White.copy(alpha = 0.7f),
                    isHovered = hoveredIndex == 2,
                    onHoverChange = { hovering -> if (hovering) setHovered(2) else if (hoveredIndex == 2) setHovered(null) },
                    globalTouchPosition = globalTouch,
                    onClick = {
                        closeMenuFromRow()
                        onAbout()
                    },
                    icon = { InfoIcon(color = Color.White, size = 15.dp, strokeWidth = 1.6.dp) }
                )
            }
        }
    }
}
