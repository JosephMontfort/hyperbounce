package dev.joseph.hyperbounce.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.glass.LiquidToggle
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.state.ComponentStyle
import dev.joseph.hyperbounce.ui.state.LocalComponentStyle
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import dev.joseph.hyperbounce.ui.theme.LocalIsDarkTheme

private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val w = drawable.intrinsicWidth.coerceAtLeast(96)
        val h = drawable.intrinsicHeight.coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun SafeModeScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val safe = repository.safeMode
    val activeAliases = safe.getCrashingAliases()

    val sysUiIcon = remember { loadAppIcon(context, "com.android.systemui") }
    val launcherIcon = remember { loadAppIcon(context, "com.miui.home") }
    val settingsIcon = remember { loadAppIcon(context, "com.android.settings") }

    val isDark = LocalIsDarkTheme.current
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val toggleBackdrop = rememberCanvasBackdrop { drawRect(cardColor) }

    ScreenScaffold(
        title = "Safe Mode",
        subtitle = "",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                // 1. System UI
                SafeModeAppRow(
                    title = "System UI",
                    subtitle = "com.android.systemui",
                    iconBitmap = sysUiIcon,
                    fallbackRes = R.drawable.ic_feature_notification,
                    checked = safe.systemUiSafeMode || activeAliases.contains("systemui"),
                    onCheckedChange = safe::setSystemUiSafeMode,
                    colors = colors,
                    toggleBackdrop = toggleBackdrop
                )

                RowDivider(colors.separator)

                // 2. HyperOS Launcher
                SafeModeAppRow(
                    title = "HyperOS Launcher",
                    subtitle = "com.miui.home",
                    iconBitmap = launcherIcon,
                    fallbackRes = R.drawable.ic_feature_launcher,
                    checked = safe.launcherSafeMode || activeAliases.contains("home"),
                    onCheckedChange = safe::setLauncherSafeMode,
                    colors = colors,
                    toggleBackdrop = toggleBackdrop
                )

                RowDivider(colors.separator)

                // 3. System Settings
                SafeModeAppRow(
                    title = "System Settings",
                    subtitle = "com.android.settings",
                    iconBitmap = settingsIcon,
                    fallbackRes = R.drawable.ic_nav_settings,
                    checked = safe.settingsSafeMode || activeAliases.contains("settings"),
                    onCheckedChange = safe::setSettingsSafeMode,
                    colors = colors,
                    toggleBackdrop = toggleBackdrop
                )
            }
        }
    }
}

@Composable
private fun SafeModeAppRow(
    title: String,
    subtitle: String,
    iconBitmap: ImageBitmap?,
    fallbackRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: HyperBounceColors,
    toggleBackdrop: Backdrop
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(fallbackRes),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = HyperBounceType.headline.copy(
                    color = colors.label,
                    fontWeight = FontWeight.SemiBold
                )
            )
            BasicText(
                text = subtitle,
                style = HyperBounceType.footnote.copy(
                    color = colors.secondaryLabel
                )
            )
        }

        val componentStyle = LocalComponentStyle.current
        if (componentStyle == ComponentStyle.MIUIX) {
            top.yukonga.miuix.kmp.basic.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        } else {
            LiquidToggle(
                selected = { checked },
                onSelect = onCheckedChange,
                backdrop = toggleBackdrop
            )
        }
    }
}
