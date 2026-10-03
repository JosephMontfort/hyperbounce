package dev.joseph.hyperbounce.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.CopyIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsClickableRow
import dev.joseph.hyperbounce.ui.components.SettingsDropdownRow
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.AppTheme
import dev.joseph.hyperbounce.ui.state.ComponentStyle
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onNavigate: (Destination) -> Unit
) {
    val context = LocalContext.current

    // Backup ActivityResultLauncher: Exports JSON file
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = repository.exportSettingsToJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Settings backed up successfully", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "Backup failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Restore ActivityResultLauncher: Imports JSON file
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (json != null) {
                    val ok = repository.importSettingsFromJson(json)
                    if (ok) {
                        Toast.makeText(context, "Settings restored successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid settings file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Restore failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    RootScaffold(
        title = "Settings",
        subtitle = "",
        colors = colors,
        backdrop = backdrop,
        repository = repository,
        onAbout = { onNavigate(Destination.About) }
    ) {
        // --- Card 1: Appearance ---
        SectionHeader("Appearance", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            // App Theme Dropdown
            SettingsDropdownRow(
                title = "App Theme",
                subtitle = "Follow System, Light, or Dark",
                options = listOf("Follow System", "Light", "Dark"),
                selectedIndex = repository.appTheme.ordinal,
                onSelect = { index ->
                    val theme = when (index) {
                        0 -> AppTheme.FollowSystem
                        1 -> AppTheme.Light
                        else -> AppTheme.Dark
                    }
                    repository.setAppTheme(theme)
                },
                colors = colors,
                backdrop = backdrop,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )

            RowDivider(colors.separator)

            // Component Style Dropdown
            SettingsDropdownRow(
                title = "Component Style",
                subtitle = "Visual design aesthetic",
                options = listOf("Liquid Glass", "MIUIX"),
                selectedIndex = repository.componentStyle.ordinal,
                onSelect = { index ->
                    val style = when (index) {
                        0 -> ComponentStyle.LiquidGlass
                        else -> ComponentStyle.MIUIX
                    }
                    repository.setComponentStyle(style)
                },
                colors = colors,
                backdrop = backdrop,
                iconTint = colors.accentPurple.copy(alpha = 0.85f),
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )
        }
        SectionFooter("Choose the global appearance theme and component styling.", colors)

        // --- Card 2: System Integration ---
        SectionHeader("System Integration", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Add entry in Settings",
                subtitle = "Display HyperiOS in system Settings app",
                colors = colors,
                backdrop = backdrop,
                checked = repository.settingsAppEntryEnabled,
                onCheckedChange = repository::setSettingsAppEntryEnabled,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_nav_settings),
                            contentDescription = "Settings",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            )
        }
        SectionFooter("Inserts a HyperiOS shortcut right below 'My device' inside the stock HyperOS Settings app.", colors)

        // --- Card 3: Backup & Restore ---
        SectionHeader("Backup & Restore", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsClickableRow(
                title = "Backup Module Settings",
                subtitle = "Export current configurations to JSON",
                colors = colors,
                onClick = {
                    backupLauncher.launch("hyperbounce_backup_${System.currentTimeMillis() / 1000}.json")
                },
                iconTint = colors.accentGreen.copy(alpha = 0.85f),
                icon = { CopyIcon(color = Color.White, size = 14.dp, strokeWidth = 1.6.dp) }
            )

            RowDivider(colors.separator)

            SettingsClickableRow(
                title = "Restore Module Settings",
                subtitle = "Import and apply configurations from JSON",
                colors = colors,
                onClick = {
                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                },
                iconTint = colors.accentOrange.copy(alpha = 0.85f),
                icon = { ResetArrowIcon(color = Color.White, size = 14.dp, strokeWidth = 1.8.dp) }
            )
        }
        SectionFooter("Save and restore your complete preferences across updates or device re-flashes.", colors)

        // --- Card 4: Diagnostics & Safety ---
        SectionHeader("Diagnostics & Safety", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            val activeCount = repository.safeMode.getCrashingAliases().size
            val isSafeEngaged = repository.safeMode.globalSafeMode || activeCount > 0 ||
                    repository.safeMode.systemUiSafeMode || repository.safeMode.launcherSafeMode ||
                    repository.safeMode.settingsSafeMode

            SettingsNavRow(
                title = "Safe Mode & Crash Recovery",
                subtitle = if (isSafeEngaged) "Safe Mode ACTIVE (Bypass enabled)" else "Auto crash watchdog & emergency recovery",
                colors = colors,
                iconTint = if (isSafeEngaged) colors.accentRed else colors.accentGreen,
                onClick = { onNavigate(Destination.SafeMode) },
                icon = { PowerIcon(color = Color.White, size = 14.dp) }
            )

            RowDivider(colors.separator)

            SettingsToggleRow(
                title = "Haptic Feedback",
                subtitle = "Tactile response on adjustments",
                colors = colors,
                iconTint = colors.accentOrange.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = repository.globalGlass.hapticsEnabled,
                onCheckedChange = repository.globalGlass::setHapticsEnabled,
                icon = { PowerIcon(color = Color.White, size = 14.dp) }
            )
        }
        SectionFooter("Emergency crash watchdog prevents bootloops if a system update changes internal APIs.", colors)
    }
}
