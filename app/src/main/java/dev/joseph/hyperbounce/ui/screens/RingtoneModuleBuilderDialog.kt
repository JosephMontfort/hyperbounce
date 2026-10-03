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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.haptics.ringtone.HapticPresetStorage
import dev.joseph.hyperbounce.haptics.ringtone.RingtoneModuleBuilder
import dev.joseph.hyperbounce.haptics.ringtone.SavedHapticPreset
import dev.joseph.hyperbounce.ui.components.CheckmarkIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PencilIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
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
    var isClosing by remember { mutableStateOf(false) }
    var displayedOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            displayedOpen = true
            isClosing = false
        } else if (displayedOpen) {
            isClosing = true
        }
    }

    val isVisible = displayedOpen && !isClosing
    val animProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "moduleDialogAnimProgress",
        finishedListener = { p ->
            if (p == 0f && isClosing) {
                displayedOpen = false
                isClosing = false
            }
        }
    )

    if (!displayedOpen && animProgress <= 0.001f) return

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

    val packageItems = remember { mutableStateListOf<RingtoneModuleBuilder.RingtonePackageItem>() }

    LaunchedEffect(selectedPresets) {
        packageItems.clear()
        if (selectedPresets.isNotEmpty()) {
            packageItems.addAll(RingtoneModuleBuilder.previewAllocations(context, selectedPresets))
        }
    }

    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * animProgress))
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!isBuilding) onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    alpha = animProgress
                    scaleX = 0.85f + 0.15f * animProgress
                    scaleY = 0.85f + 0.15f * animProgress
                }
                .clip(RoundedCornerShape(32.dp))
                .background(cardBg)
                .pointerInput(Unit) {
                    detectTapGestures { /* consume taps inside */ }
                }
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                                items = packageItems.toList(),
                                isBuilding = isBuilding,
                                errorMessage = errorMessage,
                                colors = colors,
                                backdrop = backdrop,
                                onBack = { step = ModuleBuilderStep.SELECT },
                                onRenameItem = { presetId, newTitle ->
                                    val idx = packageItems.indexOfFirst { it.presetId == presetId }
                                    if (idx != -1) {
                                        val old = packageItems[idx]
                                        val clean = newTitle.filter { it.isLetterOrDigit() }.ifBlank { "CustomRingtone" }
                                        packageItems[idx] = old.copy(title = newTitle, cleanName = clean)
                                    }
                                },
                                onConfirm = {
                                    scope.launch {
                                        isBuilding = true
                                        errorMessage = null
                                        val result = RingtoneModuleBuilder.buildModule(
                                            context = context,
                                            selectedPresets = selectedPresets,
                                            customItems = packageItems.toList()
                                        )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(colors.accentBlue)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "Close",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.5.sp,
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
                                    CheckmarkIcon(
                                        color = Color.White,
                                        size = 12.dp,
                                        strokeWidth = 2.dp
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
                                                color = colors.secondaryLabel,
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

            val isDark = isSystemInDarkTheme()
            val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(cancelBtnBg)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "Cancel",
                        style = TextStyle(
                            color = colors.label,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                val hasSelection = selectedIds.isNotEmpty()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(if (hasSelection) colors.accentBlue else colors.accentBlue.copy(alpha = 0.4f))
                        .clickable(enabled = hasSelection) { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "Review Pack (${selectedIds.size})",
                        style = TextStyle(
                            color = if (hasSelection) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold
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
    onRenameItem: (presetId: String, newTitle: String) -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val dialogHost = dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost.current

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
                .heightIn(max = 260.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { clip = false },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = item.title,
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
                                    text = "Slot #${item.effectId}",
                                    style = HyperBounceType.caption.copy(
                                        color = colors.secondaryLabel,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.graphicsLayer { clip = false }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentBlue)
                                    .clickable {
                                        val cleanAudioName = item.title.filter { it.isLetterOrDigit() }.ifBlank { "CustomRingtone" }
                                        dialogHost.showTextInput(
                                            title = "Rename Ringtone",
                                            message = "Customize ringtone title for this pack. Target audio: ${cleanAudioName}.ogg",
                                            initialValue = item.title,
                                            confirmText = "Done",
                                            confirmColor = colors.accentBlue,
                                            onConfirm = { newTitle ->
                                                val trimmed = newTitle.trim()
                                                if (trimmed.isNotBlank()) {
                                                    onRenameItem(item.presetId, trimmed)
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PencilIcon(color = Color.White, size = 15.dp)
                            }
                        }
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

        val isDark = isSystemInDarkTheme()
        val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(cancelBtnBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "Back",
                    style = TextStyle(
                        color = colors.label,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(if (isBuilding) colors.accentPink.copy(alpha = 0.6f) else colors.accentPink)
                    .clickable(enabled = !isBuilding) {
                        if (!RingtoneModuleBuilder.hasStoragePermission(context)) {
                            RingtoneModuleBuilder.requestStoragePermission(context)
                        } else {
                            onConfirm()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (isBuilding) "Generating Pack..." else "Confirm & Build (${items.size})",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(colors.accentGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen),
                contentAlignment = Alignment.Center
            ) {
                CheckmarkIcon(
                    color = Color.White,
                    size = 20.dp,
                    strokeWidth = 2.6.dp
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        BasicText(
            text = "Module Packaged",
            style = HyperBounceType.headline.copy(
                color = colors.label,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "$itemCount ringtone(s) ready to flash via Magisk or KernelSU",
            style = HyperBounceType.footnote.copy(
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(18.dp))

        // Archive Details Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.separator.copy(alpha = 0.10f))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = "SAVED ARCHIVE",
                        style = HyperBounceType.caption.copy(
                            color = colors.secondaryLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                    BasicText(
                        text = "Download/",
                        style = HyperBounceType.caption.copy(
                            color = colors.accentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = RingtoneModuleBuilder.MODULE_NAME,
                    style = HyperBounceType.body.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                )

                Spacer(Modifier.height(12.dp))

                // Actions: Copy & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.separator.copy(alpha = 0.22f))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Module Path", zipPath)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Copy Path",
                            style = TextStyle(
                                color = colors.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentBlue)
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Share Zip",
                            style = TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Flashing instructions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.separator.copy(alpha = 0.08f))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = "Installation Instructions",
                    style = HyperBounceType.caption.copy(
                        color = colors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
                InstructionBullet(colors, "1. Open Magisk, KernelSU, or APatch manager")
                InstructionBullet(colors, "2. Go to Modules tab and tap Install from storage")
                InstructionBullet(colors, "3. Select HyperiOS_Haptic_Ringtones_Module.zip in Download")
                InstructionBullet(colors, "4. Reboot device to activate")
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .background(colors.accentGreen)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "Done",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
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

