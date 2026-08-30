package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun AppsListScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    ScreenScaffold(
        title = "Navigation Bar",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { contentBackdrop ->
        item {
            SectionHeader("Apps", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                NavApp.entries.forEachIndexed { index, app ->
                    SettingsNavRow(
                        title = app.displayName,
                        subtitle = navSummary(repository, app),
                        colors = colors,
                        iconTint = appTint(app),
                        onClick = { onNavigate(Destination.AppDetail(app)) },
                        icon = { AppIcon(app) }
                    )
                    if (index != NavApp.entries.lastIndex) {
                        RowDivider(colors.separator)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: NavApp) {
    val drawableRes = when (app) {
        NavApp.Instagram -> dev.joseph.hyperbounce.R.drawable.icon_instagram
        NavApp.WhatsApp -> dev.joseph.hyperbounce.R.drawable.icon_whatsapp
        NavApp.YouTube -> dev.joseph.hyperbounce.R.drawable.icon_youtube
    }
    androidx.compose.foundation.Image(
        painter = painterResource(id = drawableRes),
        contentDescription = app.displayName,
        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
    )
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
