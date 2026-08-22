package dev.joseph.hyperbounce.ui.components

/*
 * Every "choose one of several options" control in this app uses this same dropdown-menu
 * style (tap or long-press-drag-release to pick, built on ExpandableGlassMenu) instead of
 * a segmented control, so pickers look and behave exactly like the top-right "..." menu.
 * Self-contained: owns its own Animatable progress and coroutine scope, so each instance
 * on a screen opens/closes independently.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.ExpandableGlassMenu
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.glass.MenuAlignment
import dev.joseph.hyperbounce.ui.glass.MenuAnimationPreset
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch

@Composable
fun DropdownPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    alignment: MenuAlignment = MenuAlignment.TopTrailing
) {
    val glass = LocalGlassSettings.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val expanded = progress.value > 0.05f || progress.targetValue > 0.5f

    fun close() {
        scope.launch { progress.animateTo(0f, MenuAnimationPreset.Bouncy.getSpec(isClosing = true)) }
    }

    Box(modifier) {
        if (expanded) {
            Box(
                Modifier
                    .pointerInput(Unit) { detectTapGestures { close() } }
            )
        }
        ExpandableGlassMenu(
            animatableProgress = progress,
            animationPreset = MenuAnimationPreset.Bouncy,
            alignment = alignment,
            backdrop = backdrop,
            isGlassEnabled = glass.enabled,
            isHapticsEnabled = glass.hapticsEnabled,
            cornerRadius = 16.dp,
            blurRadius = glass.blurDp,
            refractionHeight = glass.refractionHeightDp,
            refractionAmount = glass.refractionAmountDp,
            chromaticAberration = glass.chromaticAberration,
            horizontalOffset = 0f,
            verticalOffset = 10f,
            labelSize = Size(120f, 36f),
            onRowSelected = { index -> onSelect(index) },
            label = {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        options.getOrElse(selectedIndex) { "" },
                        style = HyperBounceType.subhead.copy(color = Color.White)
                    )
                    ChevronIcon(
                        color = Color.White.copy(alpha = 0.7f),
                        size = 9.dp,
                        strokeWidth = 1.6.dp,
                        pointingLeft = false
                    )
                }
            }
        ) { globalTouch, closeMenu, hoveredIndex, setHovered ->
            Column {
                options.forEachIndexed { index, option ->
                    MenuRow(
                        title = option,
                        description = "",
                        contentColor = Color.White,
                        secondaryColor = Color.White.copy(alpha = 0.7f),
                        isHovered = hoveredIndex == index,
                        onHoverChange = { hovering -> if (hovering) setHovered(index) else if (hoveredIndex == index) setHovered(null) },
                        globalTouchPosition = globalTouch,
                        onClick = {
                            closeMenu()
                            onSelect(index)
                        },
                        icon = {
                            if (index == selectedIndex) {
                                CheckmarkIcon(color = Color.White, size = 13.dp, strokeWidth = 2.dp)
                            }
                        }
                    )
                }
            }
        }
    }
}
