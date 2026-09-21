package dev.joseph.hyperbounce.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.haptics.ringtone.HapticPresetStorage
import dev.joseph.hyperbounce.haptics.ringtone.RingtoneModuleBuilder
import dev.joseph.hyperbounce.haptics.ringtone.SavedHapticPreset
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.glass.LiquidButton
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class ModuleBuilderStep {
    SELECT,
    CONFIRM,
    SUCCESS
}

@Composable
fun RingtoneModuleBuilderDialog(
    isOpen: Boolean,
    savedPresets: List<SavedHapticPreset>,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    initialSelectedPresetId: String? = null,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ModuleBuilderStep.SELECT) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var isBuilding by remember { mutableStateOf(false) }
    var buildResult by remember { mutableStateOf<RingtoneModuleBuilder.BuildResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize selection with all presets or initial target preset
    LaunchedEffect(isOpen, initialSelectedPresetId) {
        selectedIds.clear()
        if (initialSelectedPresetId != null && savedPresets.any { it.id == initialSelectedPresetId }) {
            selectedIds.add(initialSelectedPresetId)
        } else {
            // Default to selecting all saved presets for convenient pack creation
            selectedIds.addAll(savedPresets.map { it.id })
        }
        step = ModuleBuilderStep.SELECT
        isBuilding = false
        buildResult = null
        errorMessage = null
    }

    val selectedPresets = remember(selectedIds.toList(), savedPresets) {
        savedPresets.filter { it.id in selectedIds }
    }

    val previewAllocations = remember(selectedPresets) {
        if (selectedPresets.isNotEmpty()) {
            RingtoneModuleBuilder.previewAllocations(context, selectedPresets)
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isBuilding) onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Crossfade(targetState = step, label = "ModuleStepCrossfade") { currentStep ->
                    when (currentStep) {
                        ModuleBuilderStep.SELECT -> {
                            StepSelectionContent(
                                presets = savedPresets,
                                selectedIds = selectedIds,
                                colors = colors,
                                backdrop = backdrop,
                                onToggle = { id ->
                                    if (selectedIds.contains(id)) {
                                        selectedIds.remove(id)
                                    } else {
                                        selectedIds.add(id)
                                    }
                                },
                                onSelectAll = {
                                    selectedIds.clear()
                                    selectedIds.addAll(savedPresets.map { it.id })
                                },
                                onDeselectAll = {
                                    selectedIds.clear()
                                },
                                onCancel = onDismiss,
                                onNext = {
                                    if (selectedIds.isNotEmpty()) {
                                        step = ModuleBuilderStep.CONFIRM
                                    }
                                }
                            )
                        }

                        ModuleBuilderStep.CONFIRM -> {
                            StepConfirmationContent(
                                items = previewAllocations,
                                isBuilding = isBuilding,
                                errorMessage = errorMessage,
                                colors = colors,
                                backdrop = backdrop,
                                onBack = { step = ModuleBuilderStep.SELECT },
                                onConfirm = {
                                    scope.launch {
                                        isBuilding = true
                                        errorMessage = null
                                        val result = RingtoneModuleBuilder.buildModule(context, selectedPresets)
                                        isBuilding = false
                                        if (result.success) {
                                            buildResult = result
                                            step = ModuleBuilderStep.SUCCESS
                                        } else {
                                            errorMessage = result.errorMessage ?: "Failed to generate module"
                                        }
                                    }
                                }
                            )
                        }

                        ModuleBuilderStep.SUCCESS -> {
                            StepSuccessContent(
                                result = buildResult,
                                colors = colors,
                                backdrop = backdrop,
                                onDone = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepSelectionContent(
    presets: List<SavedHapticPreset>,
    selectedIds: List<String>,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Build Ringtone Module",
                    style = HyperBounceType.headline.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                )
                Spacer(Modifier.height(3.dp))
                BasicText(
                    text = "Select saved presets to bundle into flashable ringtones.",
                    style = HyperBounceType.caption.copy(
                        color = colors.secondaryLabel,
                        fontSize = 12.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.separator.copy(alpha = 0.25f))
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "✕",
                    style = HyperBounceType.caption.copy(
                        color = colors.secondaryLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        RowDivider(colors.separator)
        Spacer(Modifier.height(12.dp))

        if (presets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = "No Saved Presets Yet",
                        style = HyperBounceType.body.copy(
                            color = colors.label,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = "Import an audio track in Studio above and tap \"Save Preset\" to add it to your library.",
                        style = HyperBounceType.caption.copy(
                            color = colors.secondaryLabel,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            LiquidButton(
                onClick = onCancel,
                backdrop = backdrop,
                tint = colors.accentBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    text = "Close",
                    style = HyperBounceType.body.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        } else {
            // Quick select / count header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    BasicText(
                        text = "${selectedIds.size} of ${presets.size} selected",
                        style = HyperBounceType.caption.copy(
                            color = colors.accentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allSelected = selectedIds.size == presets.size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.separator.copy(alpha = 0.2f))
                            .clickable {
                                if (allSelected) onDeselectAll() else onSelectAll()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = if (allSelected) "Deselect All" else "Select All",
                            style = HyperBounceType.caption.copy(
                                color = colors.label,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Scrollable list of presets
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    val isChecked = selectedIds.contains(preset.id)
                    val pipeName = preset.pipelines.getOrNull(preset.selectedPipelineIndex)?.pipelineType?.title
                        ?: "Melodic Crisp"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isChecked) colors.accentBlue.copy(alpha = 0.10f)
                                else colors.separator.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isChecked) colors.accentBlue.copy(alpha = 0.45f)
                                else colors.separator.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggle(preset.id) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Animated checkbox circle
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isChecked) colors.accentBlue else Color.Transparent
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isChecked) colors.accentBlue else colors.secondaryLabel.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    BasicText(
                                        text = "✓",
                                        style = HyperBounceType.caption.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = preset.title,
                                    style = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText(
                                        text = pipeName,
                                        style = HyperBounceType.caption.copy(
                                            color = colors.accentBlue,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                    BasicText(
                                        text = "•",
                                        style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                    )
                                    BasicText(
                                        text = formatTimeMs(preset.durationMs.toInt()),
                                        style = HyperBounceType.caption.copy(
                                            color = colors.secondaryLabel,
                                            fontSize = 11.sp
                                        )
                                    )
                                    if (preset.trimEndMs > preset.trimStartMs) {
                                        BasicText(
                                            text = "•",
                                            style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                        )
                                        BasicText(
                                            text = "Trimmed",
                                            style = HyperBounceType.caption.copy(
                                                color = colors.accentOrange,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            RowDivider(colors.separator)
            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidButton(
                    onClick = onCancel,
                    backdrop = backdrop,
                    tint = colors.separator.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = "Cancel",
                        style = HyperBounceType.body.copy(
                            color = colors.label,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                val hasSelection = selectedIds.isNotEmpty()
                LiquidButton(
                    onClick = { if (hasSelection) onNext() },
                    backdrop = backdrop,
                    tint = if (hasSelection) colors.accentBlue else colors.separator.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1.3f)
                ) {
                    BasicText(
                        text = "Review Pack (${selectedIds.size})",
                        style = HyperBounceType.body.copy(
                            color = if (hasSelection) Color.White else colors.secondaryLabel,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StepConfirmationContent(
    items: List<RingtoneModuleBuilder.RingtonePackageItem>,
    isBuilding: Boolean,
    errorMessage: String?,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val hasStoragePerm = remember { RingtoneModuleBuilder.hasStoragePermission(context) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = "Hardware Slot Allocation",
            style = HyperBounceType.headline.copy(
                color = colors.label,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        )
        Spacer(Modifier.height(3.dp))
        BasicText(
            text = "Allocating non-overlapping LRA effect IDs and system mounting paths.",
            style = HyperBounceType.caption.copy(
                color = colors.secondaryLabel,
                fontSize = 12.sp
            )
        )

        Spacer(Modifier.height(12.dp))
        RowDivider(colors.separator)
        Spacer(Modifier.height(10.dp))

        // Allocation Preview List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.presetId }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.separator.copy(alpha = 0.08f))
                        .border(0.5.dp, colors.separator.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = item.title,
                                style = HyperBounceType.body.copy(
                                    color = colors.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    text = "${item.cleanName}.ogg",
                                    style = HyperBounceType.caption.copy(
                                        color = colors.secondaryLabel,
                                        fontSize = 11.sp
                                    )
                                )
                                BasicText(
                                    text = "•",
                                    style = HyperBounceType.caption.copy(color = colors.secondaryLabel.copy(alpha = 0.5f))
                                )
                                BasicText(
                                    text = "24kHz RTP",
                                    style = HyperBounceType.caption.copy(
                                        color = colors.accentBlue,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentGreen.copy(alpha = 0.12f))
                                .border(0.5.dp, colors.accentGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            BasicText(
                                text = "LRA Slot #${item.effectId}",
                                style = HyperBounceType.caption.copy(
                                    color = colors.accentGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Destination Info Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accentBlue.copy(alpha = 0.06f))
                .border(0.5.dp, colors.accentBlue.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "⚡ Magisk / KernelSU / APatch Module",
                        style = HyperBounceType.caption.copy(
                            color = colors.accentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = "Download/${RingtoneModuleBuilder.MODULE_NAME}",
                    style = HyperBounceType.caption.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = "Non-destructive. Fully isolated overlay with zero modifications to system partition.",
                    style = HyperBounceType.caption.copy(
                        color = colors.secondaryLabel,
                        fontSize = 10.5.sp
                    )
                )
            }
        }

        // Storage Permission Guidance
        if (!hasStoragePerm) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentOrange.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = "Storage Permission",
                            style = HyperBounceType.caption.copy(
                                color = colors.accentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        BasicText(
                            text = "Allow access to write the module zip to Downloads folder.",
                            style = HyperBounceType.caption.copy(
                                color = colors.label,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentOrange)
                            .clickable {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = "Grant",
                            style = HyperBounceType.caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Error: $errorMessage",
                style = HyperBounceType.caption.copy(
                    color = colors.accentRed,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(Modifier.height(14.dp))
        RowDivider(colors.separator)
        Spacer(Modifier.height(12.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                tint = colors.separator.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = "Back",
                    style = HyperBounceType.body.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            LiquidButton(
                onClick = { if (!isBuilding) onConfirm() },
                backdrop = backdrop,
                tint = colors.accentPink,
                modifier = Modifier.weight(1.4f)
            ) {
                BasicText(
                    text = if (isBuilding) "Generating Pack..." else "Confirm & Build (${items.size})",
                    style = HyperBounceType.body.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun StepSuccessContent(
    result: RingtoneModuleBuilder.BuildResult?,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val zipFile = result?.zipFile
    val zipPath = zipFile?.absolutePath ?: "/storage/emulated/0/Download/${RingtoneModuleBuilder.MODULE_NAME}"
    val itemCount = result?.items?.size ?: 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "✓",
                    style = HyperBounceType.headline.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            Column {
                BasicText(
                    text = "Module Created Successfully!",
                    style = HyperBounceType.headline.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.5.sp
                    )
                )
                BasicText(
                    text = "$itemCount ringtone(s) bundled with 24kHz haptics.",
                    style = HyperBounceType.caption.copy(
                        color = colors.secondaryLabel,
                        fontSize = 11.5.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        RowDivider(colors.separator)
        Spacer(Modifier.height(12.dp))

        // File Location Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.separator.copy(alpha = 0.12f))
                .padding(12.dp)
        ) {
            Column {
                BasicText(
                    text = "MODULE SAVED TO DOWNLOADS:",
                    style = HyperBounceType.caption.copy(
                        color = colors.secondaryLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = zipPath,
                    style = HyperBounceType.body.copy(
                        color = colors.accentBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )

                Spacer(Modifier.height(10.dp))

                // Action Row: Copy Path & Share File
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.separator.copy(alpha = 0.25f))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Module Path", zipPath)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Copy Path",
                            style = HyperBounceType.caption.copy(
                                color = colors.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentBlue.copy(alpha = 0.2f))
                            .clickable {
                                try {
                                    val file = File(zipPath)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Ringtone Module"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Share Zip",
                            style = HyperBounceType.caption.copy(
                                color = colors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Flashing instructions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.separator.copy(alpha = 0.08f))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text = "How to Flash & Activate:",
                    style = HyperBounceType.caption.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                )
                InstructionBullet(colors, "1. Open Magisk, KernelSU, or APatch manager")
                InstructionBullet(colors, "2. Modules tab -> Install from storage")
                InstructionBullet(colors, "3. Pick HyperBounce_Haptic_Ringtones_Module.zip in Download")
                InstructionBullet(colors, "4. Reboot device")
                InstructionBullet(colors, "5. Select in Settings > Sound > Ringtone (24kHz haptics activate!)")
            }
        }

        Spacer(Modifier.height(16.dp))
        LiquidButton(
            onClick = onDone,
            backdrop = backdrop,
            tint = colors.accentGreen,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = "Done",
                style = HyperBounceType.body.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun InstructionBullet(colors: HyperBounceColors, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        BasicText(
            text = "•",
            style = HyperBounceType.caption.copy(
                color = colors.accentBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
        BasicText(
            text = text,
            style = HyperBounceType.caption.copy(
                color = colors.secondaryLabel,
                fontSize = 11.sp
            )
        )
    }
}

