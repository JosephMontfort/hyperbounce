package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun SafeModeScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val safe = repository.safeMode
    val activeAliases = safe.getCrashingAliases()
    val isAnyActive = safe.globalSafeMode || safe.systemUiSafeMode || safe.launcherSafeMode ||
            safe.instagramSafeMode || safe.whatsappSafeMode || safe.youtubeSafeMode || activeAliases.isNotEmpty()

    ScreenScaffold(
        title = "Safe Mode & Recovery",
        subtitle = "Crash watchdog and hook bypass",
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
                                text = "Reset Crash Flags & Disable Safe Mode",
                                style = HyperBounceType.headline.copy(color = Color.White, fontSize = 14.sp)
                            )
                        }
                    }
                }
            }
        }

        // --- Master Kill Switch ---
        item {
            SectionHeader("Master Kill Switch", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Global Safe Mode",
                    subtitle = if (safe.globalSafeMode) "All LSPosed hooks bypassed system-wide" else "Normal operation",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.globalSafeMode,
                    onCheckedChange = safe::setGlobalSafeMode
                )
            }
            SectionFooter("Bypasses all hooks across SystemUI, Launcher, and third-party apps instantly.", colors)
        }

        // --- Per-Subsystem Safe Mode Toggles ---
        item {
            SectionHeader("Subsystem Bypass Toggles", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "System UI Safe Mode",
                    subtitle = if (safe.systemUiSafeMode || activeAliases.contains("systemui")) "SystemUI hooks DISABLED" else "Lockscreen sheet, PIN keys, Control Center active",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.systemUiSafeMode || activeAliases.contains("systemui"),
                    onCheckedChange = safe::setSystemUiSafeMode
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "HyperOS Launcher Safe Mode",
                    subtitle = if (safe.launcherSafeMode || activeAliases.contains("home")) "Launcher hooks DISABLED" else "Unlock grid kinetics & app open/close active",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.launcherSafeMode || activeAliases.contains("home"),
                    onCheckedChange = safe::setLauncherSafeMode
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Instagram Safe Mode",
                    subtitle = if (safe.instagramSafeMode || activeAliases.contains("instagram")) "Instagram nav hook DISABLED" else "Liquid floating nav bar active",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.instagramSafeMode || activeAliases.contains("instagram"),
                    onCheckedChange = safe::setInstagramSafeMode
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "WhatsApp Safe Mode",
                    subtitle = if (safe.whatsappSafeMode || activeAliases.contains("whatsapp")) "WhatsApp nav hook DISABLED" else "Liquid floating nav bar active",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.whatsappSafeMode || activeAliases.contains("whatsapp"),
                    onCheckedChange = safe::setWhatsappSafeMode
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "YouTube Safe Mode",
                    subtitle = if (safe.youtubeSafeMode || activeAliases.contains("youtube")) "YouTube nav hook DISABLED" else "Liquid floating nav bar active",
                    colors = colors,
                    backdrop = backdrop,
                    checked = safe.youtubeSafeMode || activeAliases.contains("youtube"),
                    onCheckedChange = safe::setYoutubeSafeMode
                )
            }
        }

        // --- Emergency Recovery Instructions ---
        item {
            SectionHeader("Emergency Shell Rescue", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        text = "If SystemUI or Launcher ever crashes repeatedly, you can clear safe mode or manually engage it via Termux or ADB shell:",
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
