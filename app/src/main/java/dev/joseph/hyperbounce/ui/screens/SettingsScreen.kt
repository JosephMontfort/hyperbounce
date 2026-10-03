package dev.joseph.hyperbounce.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsClickableRow
import dev.joseph.hyperbounce.ui.components.SettingsDropdownRow
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
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
        showTopAction = false,
        onAbout = { onNavigate(Destination.About) }
    ) {
        // --- Card 1: Appearance ---
        SectionHeader("Appearance", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            // App Theme Dropdown
            SettingsDropdownRow(
                title = "App Theme",
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
                backdrop = backdrop
            )

            RowDivider(colors.separator)

            // Component Style Dropdown
            SettingsDropdownRow(
                title = "Component Style",
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
                backdrop = backdrop
            )

            RowDivider(colors.separator)

            SettingsToggleRow(
                title = "Haptic Feedback",
                colors = colors,
                backdrop = backdrop,
                checked = repository.globalGlass.hapticsEnabled,
                onCheckedChange = repository.globalGlass::setHapticsEnabled
            )
        }

        // --- Card 2: System Integration ---
        SectionHeader("System Integration", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Add entry in Settings",
                subtitle = "Display HyperiOS in system Settings app",
                colors = colors,
                backdrop = backdrop,
                checked = repository.settingsAppEntryEnabled,
                onCheckedChange = repository::setSettingsAppEntryEnabled
            )
        }

        // --- Card 3: Backup & Restore ---
        SectionHeader("Backup & Restore", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsClickableRow(
                title = "Backup Module Settings",
                colors = colors,
                onClick = {
                    backupLauncher.launch("hyperios_backup_${System.currentTimeMillis() / 1000}.json")
                }
            )

            RowDivider(colors.separator)

            SettingsClickableRow(
                title = "Restore Module Settings",
                colors = colors,
                onClick = {
                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                }
            )
        }

        // --- Card 4: Diagnostics & Safety ---
        SectionHeader("Diagnostics & Safety", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsNavRow(
                title = "Safe Mode & Crash Recovery",
                colors = colors,
                onClick = { onNavigate(Destination.SafeMode) }
            )
        }
    }
}
