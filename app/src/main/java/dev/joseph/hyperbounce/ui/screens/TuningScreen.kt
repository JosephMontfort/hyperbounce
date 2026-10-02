package dev.joseph.hyperbounce.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun TuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    RootScaffold(
        title = "Tuning",
        subtitle = "What the hooks actually do",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = { onNavigate(Destination.About) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TuningTile(
                    title = "Floating Navigation Bar",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppsList) },
                    modifier = Modifier.weight(1f)
                )
                TuningTile(
                    title = "Toggles & Sliders",
                    colors = colors,
                    onClick = { onNavigate(Destination.Engine) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TuningTile(
                    title = "Lock Screen Sheet",
                    colors = colors,
                    onClick = { onNavigate(Destination.LockScreenSheet) },
                    modifier = Modifier.weight(1f)
                )
                TuningTile(
                    title = "Notification Center",
                    colors = colors,
                    onClick = { onNavigate(Destination.NotificationCenterSheet) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TuningTile(
                    title = "PIN Pad Physics",
                    colors = colors,
                    onClick = { onNavigate(Destination.Keypad) },
                    modifier = Modifier.weight(1f)
                )
                TuningTile(
                    title = "Screen Unlock",
                    colors = colors,
                    onClick = { onNavigate(Destination.ScreenUnlock) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TuningTile(
                    title = "App Transitions",
                    colors = colors,
                    onClick = { onNavigate(Destination.AppTransitions) },
                    modifier = Modifier.weight(1f)
                )
                TuningTile(
                    title = "Control Center",
                    colors = colors,
                    onClick = { onNavigate(Destination.ControlCenter) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TuningTile(
                    title = "iOS Caller Screen",
                    colors = colors,
                    onClick = { onNavigate(Destination.CallerScreen) },
                    modifier = Modifier.weight(1f)
                )
                TuningTile(
                    title = "Ringtones & Haptics",
                    colors = colors,
                    onClick = { onNavigate(Destination.HapticStudio) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TuningTile(
    title: String,
    colors: HyperBounceColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "tileScale"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = tween(120),
        label = "tileHighlight"
    )
    val cardColor = if (colors.isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .height(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(cardColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = colors.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 2
        )
        if (highlightAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.label.copy(alpha = highlightAlpha))
            )
        }
    }
}
