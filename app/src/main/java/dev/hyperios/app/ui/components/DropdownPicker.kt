package dev.hyperios.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.ui.theme.HyperBounceType
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DropdownPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val componentStyle = dev.hyperios.app.ui.state.LocalComponentStyle.current
    val isDark = dev.hyperios.app.ui.theme.LocalIsDarkTheme.current
    var expanded by remember { mutableStateOf(false) }

    val view = androidx.compose.ui.platform.LocalView.current

    val entry = remember(options, selectedIndex) {
        DropdownEntry(
            items = options.mapIndexed { idx, opt ->
                DropdownItem(
                    text = opt,
                    selected = idx == selectedIndex,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        )
    }

    val displayAlpha = if (enabled) 1f else 0.35f

    Box(modifier = modifier) {
        if (componentStyle == dev.hyperios.app.ui.state.ComponentStyle.MIUIX) {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = displayAlpha }
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    expanded = !expanded
                                }
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                DropdownArrowEndAction(
                    actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = displayAlpha }
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    expanded = !expanded
                                }
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    options.getOrElse(selectedIndex) { "" },
                    style = HyperBounceType.subhead.copy(
                        color = if (isDark) Color.White else Color.Black.copy(alpha = 0.82f)
                    )
                )
                ChevronIcon(
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f),
                    size = 9.dp,
                    strokeWidth = 1.6.dp,
                    pointingLeft = false
                )
            }
        }

        if (enabled) {
            WindowDropdownPopup(
                entry = entry,
                show = expanded,
                onDismiss = { expanded = false },
                onDismissFinished = {},
                maxHeight = 280.dp,
                dropdownColors = if (componentStyle == dev.hyperios.app.ui.state.ComponentStyle.MIUIX) {
                    DropdownDefaults.dropdownColors()
                } else {
                    DropdownDefaults.dropdownColors(
                        containerColor = if (isDark) Color(0xFF222224) else Color(0xFFF7F7F8),
                        contentColor = if (isDark) Color.White else Color.Black,
                        selectedContentColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
                    )
                }
            )
        }
    }
}
