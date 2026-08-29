import re

model_fields = [
    ("ccLowEndAnim", "Boolean"), ("ccAnimEnabled", "Boolean"), ("ccModularEditEnabled", "Boolean"), ("ccJellyEnabled", "Boolean"),
    ("lockCardBorderWidth", "Float"), ("lockCardBorderAlpha", "Float"), ("lockCardRefractionHeight", "Float"), ("lockCardRefractionAmount", "Float"), ("lockCardDepthEffect", "Float"),
    ("lockPillWidth", "Float"), ("lockPillHeight", "Float"), ("lockPillBottomMargin", "Float"), ("lockLipHeight", "Float"),
    ("pinDigitTextSize", "Float"), ("pinRow1Offset", "Float"), ("pinRow2Offset", "Float"), ("pinRow3Offset", "Float"), ("pinRow0Offset", "Float"),
    ("pinTapSquishScale", "Float"), ("pinReleasePlunge", "Float"), ("pinShowKlondike", "Boolean"), ("pinEntryTranslationY", "Float"), ("pinEntryInitialScale", "Float"),
    ("pinEntryDuration", "Float"), ("pinRowDelayStep", "Float"), ("pinColDelayStep", "Float"), ("pinEntryOvershoot", "Float")
]

screen_code = """package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import kotlin.math.roundToInt

@Composable
fun DiagnosticsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val diagnostics by repository.diagnostics.collectAsState()

    ScreenScaffold(
        title = "System Diagnostics",
        subtitle = "Control Center and Lockscreen experiments",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { contentBackdrop ->
        val combinedBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)

        SectionHeader("Control Center", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
"""
first = True
for name, typ in model_fields:
    if name.startswith("cc"):
        if not first:
            screen_code += "            RowDivider(colors.separator, insetStart = 16.dp)\n"
        first = False
        if typ == "Boolean":
            screen_code += f"""            SettingsToggleRow(
                title = "{name}",
                colors = colors,
                backdrop = combinedBackdrop,
                checked = diagnostics.{name},
                onCheckedChange = {{ v -> repository.updateDiagnostics {{ it.copy({name} = v) }} }}
            )
"""

screen_code += """        }
        
        SectionHeader("Lockscreen Card", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
"""
first = True
for name, typ in model_fields:
    if name.startswith("lock"):
        if not first:
            screen_code += "            RowDivider(colors.separator, insetStart = 16.dp)\n"
        first = False
        if typ == "Float":
            screen_code += f"""            SettingsSliderRow(
                title = "{name}",
                colors = colors,
                backdrop = combinedBackdrop,
                value = diagnostics.{name},
                onValueChange = {{ v -> repository.updateDiagnostics {{ it.copy({name} = v) }} }},
                valueRange = 0f..100f,
                valueLabel = {{ "${{it.roundToInt()}}" }}
            )
"""

screen_code += """        }
        
        SectionHeader("Pinpad", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
"""
first = True
for name, typ in model_fields:
    if name.startswith("pin"):
        if not first:
            screen_code += "            RowDivider(colors.separator, insetStart = 16.dp)\n"
        first = False
        if typ == "Boolean":
            screen_code += f"""            SettingsToggleRow(
                title = "{name}",
                colors = colors,
                backdrop = combinedBackdrop,
                checked = diagnostics.{name},
                onCheckedChange = {{ v -> repository.updateDiagnostics {{ it.copy({name} = v) }} }}
            )
"""
        elif typ == "Float":
            screen_code += f"""            SettingsSliderRow(
                title = "{name}",
                colors = colors,
                backdrop = combinedBackdrop,
                value = diagnostics.{name},
                onValueChange = {{ v -> repository.updateDiagnostics {{ it.copy({name} = v) }} }},
                valueRange = 0f..500f,
                valueLabel = {{ "${{it.roundToInt()}}" }}
            )
"""

screen_code += """        }
    }
}
"""

with open("app/src/main/java/dev/joseph/hyperbounce/ui/screens/DiagnosticsScreen.kt", "w") as f:
    f.write(screen_code)

