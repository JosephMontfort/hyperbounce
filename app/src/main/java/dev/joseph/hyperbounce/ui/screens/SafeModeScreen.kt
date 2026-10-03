package dev.joseph.hyperbounce.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

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
    val isAnyActive = safe.globalSafeMode || safe.systemUiSafeMode || safe.launcherSafeMode ||
            safe.settingsSafeMode || activeAliases.isNotEmpty()

    val sysUiIcon = remember { loadAppIcon(context, "com.android.systemui") }
    val launcherIcon = remember { loadAppIcon(context, "com.miui.home") }
    val settingsIcon = remember { loadAppIcon(context, "com.android.settings") }

    ScreenScaffold(
        title = "Safe Mode",
        subtitle = "Crash recovery and hook bypass",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        // --- Live Status Banner ---
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isAnyActive) colors.accentRed else colors.accentGreen)
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicText(
                            text = if (isAnyActive) "SAFE MODE ENGAGED" else "ALL SUBSYSTEMS PROTECTED",
                            style = HyperBounceType.headline.copy(
                                color = if (isAnyActive) colors.accentRed else colors.accentGreen,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = if (isAnyActive) {
                            "One or more subsystems are currently bypassed due to crash watchdog triggers or manual bypass. Hooks will NOT execute for: " +
                                    (if (safe.globalSafeMode || activeAliases.contains("all")) "ALL APPS (Global)" else activeAliases.joinToString(", ").ifEmpty { "Selected apps" })
                        } else {
                            "Automatic crash watchdog is active. If any system app crashes twice within 60s of launch, Safe Mode engages automatically to prevent bootloops."
                        },
                        style = HyperBounceType.subhead.copy(
                            color = colors.secondaryLabel,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )

                    if (isAnyActive) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accentRed.copy(alpha = 0.85f))
                                .clickable { safe.clearAll() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "Reset Crash Flags & Restore Normal Operation",
                                style = HyperBounceType.headline.copy(color = Color.White, fontSize = 14.sp)
                            )
                        }
                    }
                }
            }
        }

        // --- Per-Subsystem Safe Mode Toggles (HyperCeiler Architecture) ---
        item {
            SectionHeader("Subsystem Bypass Toggles", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                // 1. System UI
                SettingsToggleRow(
                    title = "System UI",
                    subtitle = "com.android.systemui",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.systemUiSafeMode || activeAliases.contains("systemui"),
                    onCheckedChange = safe::setSystemUiSafeMode,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sysUiIcon != null) {
                                Image(
                                    bitmap = sysUiIcon,
                                    contentDescription = "System UI",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_feature_notification),
                                    contentDescription = "System UI",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                )

                RowDivider(colors.separator)

                // 2. HyperOS Launcher
                SettingsToggleRow(
                    title = "HyperOS Launcher",
                    subtitle = "com.miui.home",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.launcherSafeMode || activeAliases.contains("home"),
                    onCheckedChange = safe::setLauncherSafeMode,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (launcherIcon != null) {
                                Image(
                                    bitmap = launcherIcon,
                                    contentDescription = "HyperOS Launcher",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_feature_launcher),
                                    contentDescription = "HyperOS Launcher",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                )

                RowDivider(colors.separator)

                // 3. System Settings
                SettingsToggleRow(
                    title = "System Settings",
                    subtitle = "com.android.settings",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.settingsSafeMode || activeAliases.contains("settings"),
                    onCheckedChange = safe::setSettingsSafeMode,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (settingsIcon != null) {
                                Image(
                                    bitmap = settingsIcon,
                                    contentDescription = "System Settings",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_nav_settings),
                                    contentDescription = "System Settings",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                )
            }
            SectionFooter("Enable bypass for any subsystem to prevent hooks from running inside that process.", colors)
        }

        // --- Master Kill Switch ---
        item {
            SectionHeader("Master Kill Switch", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Global Safe Mode",
                    subtitle = if (safe.globalSafeMode || activeAliases.contains("all")) "All hooks bypassed system-wide" else "Normal operation",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.globalSafeMode || activeAliases.contains("all"),
                    onCheckedChange = safe::setGlobalSafeMode
                )
            }
            SectionFooter("Bypasses all hooks across System UI, Launcher, Settings, and all apps immediately.", colors)
        }

        // --- Emergency Shell Rescue ---
        item {
            SectionHeader("Emergency Shell Rescue", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        text = "If System UI or Launcher crashes repeatedly, you can clear safe mode or manually engage it via Termux or ADB shell:",
                        style = HyperBounceType.subhead.copy(
                            color = colors.secondaryLabel,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141416))
                            .padding(12.dp)
                    ) {
                        Column {
                            BasicText(
                                text = "# Clear all safe modes (Restore normal operation):",
                                style = TextStyle(
                                    color = Color(0xFF8E8E93),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            BasicText(
                                text = "su -c setprop persist.hyperbounce.safemode \"\"",
                                style = TextStyle(
                                    color = Color(0xFF30D158),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                text = "# Emergency disable SystemUI hooks:",
                                style = TextStyle(
                                    color = Color(0xFF8E8E93),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            BasicText(
                                text = "su -c setprop persist.hyperbounce.safemode \"systemui\"",
                                style = TextStyle(
                                    color = Color(0xFFFF9F0A),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
            SectionFooter("Changes to system properties take effect on the next app or SystemUI process restart.", colors)
        }
    }
}
