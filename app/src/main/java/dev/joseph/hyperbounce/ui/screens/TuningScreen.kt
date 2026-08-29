package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

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
        onReset = repository::resetToDefaults,
        onAbout = { onNavigate(Destination.About) }
    ) {
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
        SectionFooter(
            "Floating nav-bar glass for Instagram, WhatsApp, and YouTube — each app's " +
                "settings are stored and applied independently.",
            colors
        )

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
        SectionFooter(
            "HyperOS system toggles, sliders, and keypad — pick the rendering engine and " +
                "tune the glass feel for each.",
            colors
        )
    }
}

@Composable
private fun AppIcon(app: NavApp) {
    val context = LocalContext.current
    val pm = context.packageManager
    var bitmap by remember(app) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(app) {
        kotlinx.coroutines.Dispatchers.IO.invoke {
            try {
                val drawable = pm.getApplicationIcon(app.packageName)
                val bmp = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val canvasBmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(canvasBmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    canvasBmp
                }
                bitmap = bmp.asImageBitmap()
            } catch (e: Exception) {
                bitmap = null
            }
        }
    }
    
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = app.displayName,
            modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        )
    } else {
        // Fallback to monogram if not installed
        Box(
            Modifier
                .size(28.dp)
                .background(appTint(app), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                app.displayName.take(1),
                style = HyperBounceType.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
        }
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
