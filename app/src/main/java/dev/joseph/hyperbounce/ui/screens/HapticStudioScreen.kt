package dev.joseph.hyperbounce.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.haptics.dsp.AudioDecoder
import dev.joseph.hyperbounce.haptics.dsp.HapticSynthesizer
import dev.joseph.hyperbounce.haptics.dsp.PipelineConfig
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic
import dev.joseph.hyperbounce.haptics.player.HapticIntensityMode
import dev.joseph.hyperbounce.haptics.player.SynchronizedHapticPlayer
import dev.joseph.hyperbounce.haptics.ringtone.ActiveRingtoneProfile
import dev.joseph.hyperbounce.haptics.ringtone.HapticPresetStorage
import dev.joseph.hyperbounce.haptics.ringtone.HapticRingtoneStorage
import dev.joseph.hyperbounce.haptics.ringtone.RingtoneManagerHelper
import dev.joseph.hyperbounce.haptics.ringtone.SavedHapticPreset
import androidx.compose.ui.graphics.graphicsLayer
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PencilIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.TrashIcon
import dev.joseph.hyperbounce.ui.glass.LiquidButton
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HapticStudioScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { SynchronizedHapticPlayer(context) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioFileName by remember { mutableStateOf<String?>(null) }
    var decodedAudio by remember { mutableStateOf<AudioDecoder.DecodedAudio?>(null) }
    var synthesizedHaptics by remember { mutableStateOf<List<SynthesizedHaptic>>(emptyList()) }
    var selectedPipelineIndex by remember { mutableIntStateOf(0) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgressMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }

    // Haptic Intensity & Ringtone Trimming State
    var intensityMode by remember { mutableStateOf(HapticIntensityMode.Crisp) }
    var isTrimMode by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableIntStateOf(0) }
    var trimEndMs by remember { mutableIntStateOf(0) }
    var isModuleDialogOpen by remember { mutableStateOf(false) }
    var moduleDialogTargetPresetId by remember { mutableStateOf<String?>(null) }
    val dialogHost = dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost.current

    // Saved Library Presets
    var savedPresets by remember { mutableStateOf<List<SavedHapticPreset>>(emptyList()) }

    LaunchedEffect(Unit) {
        savedPresets = HapticPresetStorage.listPresets(context)
    }

    // DSP Re-Creation Parameters
    var resonantFreq by remember { mutableFloatStateOf(repository.audioHaptics.resonantFrequencyHz) }
    var sensitivity by remember { mutableFloatStateOf(1.0f) }
    var brakingStrength by remember { mutableFloatStateOf(0.85f) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedAudioUri = uri
            audioFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected Audio"
            player.stop()
            isPlaying = false

            scope.launch {
                isProcessing = true
                processingProgress = 0.05f
                try {
                    val decoded = AudioDecoder.decode(context, uri) { p ->
                        processingProgress = p
                    }
                    decodedAudio = decoded
                    totalDurationMs = decoded.durationMs.toInt()
                    playbackProgressMs = 0
                    trimStartMs = 0
                    trimEndMs = decoded.durationMs.toInt().coerceAtMost(30000)
                    val config = PipelineConfig(
                        resonantFrequencyHz = resonantFreq,
                        sensitivity = sensitivity,
                        brakingStrength = brakingStrength
                    )
                    val results = HapticSynthesizer.synthesizeAll(decoded, config) { p ->
                        processingProgress = p
                    }
                    synthesizedHaptics = results
                    selectedPipelineIndex = 0
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }
    val moduleDialogAnimProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isModuleDialogOpen) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "moduleDialogBlurAnim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (moduleDialogAnimProgress > 0.001f) {
                        Modifier.blur(24.dp * moduleDialogAnimProgress)
                    } else {
                        Modifier
                    }
                )
        ) {
            ScreenScaffold(
                title = "Ringtones & Haptics",
                colors = colors,
                backdrop = backdrop,
                onBack = onBack
            ) { _ ->
                // Section 1: Audio Input
                item {
                    SectionHeader("Audio Input", colors)
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BasicText(
                                text = if (audioFileName != null) "File: $audioFileName" else "No audio file loaded",
                                style = HyperBounceType.body.copy(
                                    color = colors.label,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = "Select an audio track to synthesize 5 synchronized tactile pipelines.",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                            )
                            Spacer(Modifier.height(14.dp))

                            LiquidButton(
                                onClick = {
                                    audioPickerLauncher.launch("audio/*")
                                },
                                backdrop = backdrop,
                                height = 48.dp,
                                tint = colors.accentBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BasicText(
                                    text = if (audioFileName == null) "Select Audio" else "Choose Different",
                                    style = HyperBounceType.body.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }

            // Section 2: Processing Progress
            item {
                AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val infiniteTransition = rememberInfiniteTransition()
                                val shimmerAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )

                                BasicText(
                                    text = "Synthesizing 5 Haptic Pipelines...",
                                    style = HyperBounceType.body.copy(
                                        color = colors.accentBlue.copy(alpha = shimmerAlpha),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.elevatedSurfaceTint)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = processingProgress.coerceIn(0.01f, 1f))
                                            .height(8.dp)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(colors.accentBlue, colors.accentPurple, colors.accentPink)
                                                )
                                            )
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                BasicText(
                                    text = "${(processingProgress * 100).toInt()}% • Analyzing audio & synthesizing haptics",
                                    style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Synthesized Pipelines & Playback
            if (synthesizedHaptics.isNotEmpty() && !isProcessing) {
                val currentHaptic = synthesizedHaptics.getOrNull(selectedPipelineIndex) ?: synthesizedHaptics[0]

                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Tactile Pipelines", colors)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        synthesizedHaptics.forEachIndexed { idx, haptic ->
                            val isSelected = idx == selectedPipelineIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) colors.accentBlue else colors.elevatedSurfaceTint)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else colors.separator,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        player.stop()
                                        isPlaying = false
                                        selectedPipelineIndex = idx
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                BasicText(
                                    text = haptic.pipelineType.title,
                                    style = HyperBounceType.caption.copy(
                                        color = if (isSelected) Color.White else colors.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BasicText(
                                text = currentHaptic.pipelineType.title,
                                style = HyperBounceType.body.copy(
                                    color = colors.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Spacer(Modifier.height(14.dp))

                            val activeDuration = if (totalDurationMs > 0) totalDurationMs else currentHaptic.durationMs.toInt()

                            // Interactive Waveform Visualizer with Smooth Dragging Trim Handles
                            WaveformVisualizer(
                                envelopePoints = currentHaptic.envelopePoints,
                                currentMs = playbackProgressMs,
                                totalDurationMs = activeDuration,
                                isTrimMode = isTrimMode,
                                trimStartMs = trimStartMs,
                                trimEndMs = if (trimEndMs > 0) trimEndMs else activeDuration.coerceAtMost(30000),
                                colors = colors,
                                onSeek = { fraction ->
                                    val targetMs = (fraction * activeDuration).toInt().coerceIn(0, activeDuration)
                                    playbackProgressMs = targetMs
                                    if (isPlaying) {
                                        player.seekTo(targetMs)
                                    }
                                },
                                onTrimChange = { newStart, newEnd ->
                                    trimStartMs = newStart
                                    trimEndMs = newEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F141C))
                            )

                            Spacer(Modifier.height(14.dp))

                            // Synchronized Playback Control
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                val uri = selectedAudioUri
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(colors.accentBlue, colors.accentPurple)
                                            )
                                        )
                                        .clickable {
                                            if (uri == null) return@clickable
                                            if (isPlaying) {
                                                player.stop()
                                                isPlaying = false
                                            } else {
                                                player.play(
                                                    audioUri = uri,
                                                    haptic = currentHaptic,
                                                    startFromMs = if (isTrimMode) trimStartMs else playbackProgressMs,
                                                    endAtMs = if (isTrimMode) trimEndMs else 0,
                                                    intensityMode = intensityMode,
                                                    onProgress = { cur, tot ->
                                                        playbackProgressMs = cur
                                                        totalDurationMs = tot
                                                        isPlaying = true
                                                    },
                                                    onCompletion = {
                                                        isPlaying = false
                                                        playbackProgressMs = if (isTrimMode) trimStartMs else 0
                                                    }
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = if (isPlaying) "■" else "▶",
                                        style = HyperBounceType.body.copy(
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = if (isPlaying) "Playing Synchronized Preview" else "Tap to Play Preview",
                                        style = HyperBounceType.body.copy(
                                            color = colors.label,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "${currentHaptic.durationMs}ms duration • ${currentHaptic.peakCount} tactile beats",
                                        style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            RowDivider(colors.separator)
                            Spacer(Modifier.height(14.dp))

                            // Ringtone Trimming Studio Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    BasicText(
                                        text = "Ringtone Trimmer",
                                        style = HyperBounceType.body.copy(
                                            color = colors.label,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    BasicText(
                                        text = if (isTrimMode) {
                                            val durSec = ((trimEndMs - trimStartMs) / 1000).coerceAtLeast(0)
                                            "Trimmed: ${formatTimeMs(trimStartMs)} – ${formatTimeMs(trimEndMs)} (${durSec}s)"
                                        } else "Trim bounds for incoming calls",
                                        style = HyperBounceType.caption.copy(
                                            color = if (isTrimMode) colors.accentBlue else colors.secondaryLabel
                                        )
                                    )
                                }

                                LiquidButton(
                                    onClick = {
                                        isTrimMode = !isTrimMode
                                        if (isTrimMode && trimEndMs <= trimStartMs) {
                                            trimEndMs = (trimStartMs + 30000).coerceAtMost(activeDuration)
                                        }
                                    },
                                    backdrop = backdrop,
                                    height = 38.dp,
                                    horizontalPadding = 18.dp,
                                    tint = if (isTrimMode) colors.accentBlue else Color.Unspecified,
                                    surfaceColor = if (isTrimMode) colors.accentBlue.copy(alpha = 0.35f) else colors.separator.copy(alpha = 0.25f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isTrimMode) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                        BasicText(
                                            text = if (isTrimMode) "Trim Active" else "Trim",
                                            style = HyperBounceType.body.copy(
                                                color = if (isTrimMode) Color.White else colors.label,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }

                            if (isTrimMode) {
                                Spacer(Modifier.height(10.dp))
                                // Quick duration presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(15 to "15s", 20 to "20s", 30 to "30s").forEach { (sec, label) ->
                                        val durMs = sec * 1000
                                        val isCurActive = (trimEndMs - trimStartMs) == durMs
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurActive) colors.accentBlue.copy(alpha = 0.35f) else colors.separator.copy(alpha = 0.20f))
                                            .clickable {
                                                trimEndMs = (trimStartMs + durMs).coerceAtMost(activeDuration)
                                            },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = label,
                                                style = HyperBounceType.caption.copy(
                                                    color = if (isCurActive) colors.accentBlue else colors.label,
                                                    fontWeight = if (isCurActive) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.5.sp
                                                )
                                            )
                                        }
                                    }
                                    val isFullActive = trimStartMs == 0 && trimEndMs == activeDuration
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isFullActive) colors.accentBlue.copy(alpha = 0.35f) else colors.separator.copy(alpha = 0.20f))
                                        .clickable {
                                            trimStartMs = 0
                                            trimEndMs = activeDuration
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "Full",
                                            style = HyperBounceType.caption.copy(
                                                color = if (isFullActive) colors.accentBlue else colors.label,
                                                fontWeight = if (isFullActive) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.5.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            RowDivider(colors.separator)
                            Spacer(Modifier.height(14.dp))

                            // Actions: Save Preset & Build Module
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LiquidButton(
                                    onClick = {
                                        val audio = decodedAudio
                                        if (audio == null || synthesizedHaptics.isEmpty()) {
                                            Toast.makeText(context, "No synthesized audio to save", Toast.LENGTH_SHORT).show()
                                            return@LiquidButton
                                        }
                                        val title = audioFileName?.substringBeforeLast('.') ?: "Custom Preset"
                                        val start = trimStartMs.coerceAtLeast(0)
                                        val end = if (trimEndMs > start) trimEndMs else totalDurationMs
                                        HapticPresetStorage.savePreset(
                                            context = context,
                                            title = title,
                                            audioFileName = audioFileName ?: "$title.wav",
                                            decodedAudio = audio,
                                            synthesizedHaptics = synthesizedHaptics,
                                            selectedPipelineIndex = selectedPipelineIndex,
                                            trimStartMs = start,
                                            trimEndMs = end,
                                            intensityMode = intensityMode
                                        )
                                        savedPresets = HapticPresetStorage.listPresets(context)
                                        Toast.makeText(context, "Saved \"$title\" to Library", Toast.LENGTH_SHORT).show()
                                    },
                                    backdrop = backdrop,
                                    height = 48.dp,
                                    tint = colors.accentGreen,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    BasicText(
                                        text = "Save Preset",
                                        style = HyperBounceType.body.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        )
                                    )
                                }

                                LiquidButton(
                                    onClick = {
                                        moduleDialogTargetPresetId = null
                                        isModuleDialogOpen = true
                                    },
                                    backdrop = backdrop,
                                    height = 48.dp,
                                    tint = colors.accentPink,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    BasicText(
                                        text = "Build Module",
                                        style = HyperBounceType.body.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            LiquidButton(
                                onClick = {
                                    exportRtpBinary(context, currentHaptic, audioFileName ?: "effect") { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                backdrop = backdrop,
                                height = 48.dp,
                                tint = colors.accentBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BasicText(
                                    text = "Export RTP .bin",
                                    style = HyperBounceType.body.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Section: Saved Ringtone Library
            item {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Saved Ringtones & Presets", colors)

                if (savedPresets.isEmpty()) {
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(
                                    text = "No Saved Presets",
                                    style = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                BasicText(
                                    text = "Import an audio track above and tap Save Preset to add it here.",
                                    style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                )
                            }
                        }
                    }
                } else {
                    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = "Package Magisk / KSU Module",
                                    style = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                BasicText(
                                    text = "${savedPresets.size} preset(s) ready to export",
                                    style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                )
                            }

                            LiquidButton(
                                onClick = {
                                    moduleDialogTargetPresetId = null
                                    isModuleDialogOpen = true
                                },
                                backdrop = backdrop,
                                height = 38.dp,
                                horizontalPadding = 16.dp,
                                tint = colors.accentPink,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                BasicText(
                                    text = "Build Pack",
                                    style = HyperBounceType.caption.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        savedPresets.forEach { preset ->
                            GlassCard(backdrop, Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = preset.title,
                                                style = HyperBounceType.body.copy(
                                                    color = colors.label,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(preset.createdAt))
                                            BasicText(
                                                text = "$dateStr • ${formatTimeMs(preset.durationMs.toInt())}",
                                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.graphicsLayer { clip = false }
                                        ) {
                                            // Rename Circular Glass Button
                                            LiquidButton(
                                                onClick = {
                                                    dialogHost.showTextInput(
                                                        title = "Rename Preset",
                                                        message = "Enter a new title for this audio preset.",
                                                        initialValue = preset.title,
                                                        confirmText = "Save",
                                                        confirmColor = colors.accentBlue,
                                                        onConfirm = { newTitle ->
                                                            val trimmed = newTitle.trim()
                                                            if (trimmed.isNotBlank() && trimmed != preset.title) {
                                                                val success = HapticPresetStorage.renamePreset(context, preset.id, trimmed)
                                                                if (success) {
                                                                    savedPresets = HapticPresetStorage.listPresets(context)
                                                                    Toast.makeText(context, "Renamed preset to \"$trimmed\"", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    Toast.makeText(context, "Failed to rename preset", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                                backdrop = backdrop,
                                                tint = colors.accentBlue,
                                                size = 36.dp
                                            ) {
                                                PencilIcon(color = Color.White, size = 15.dp)
                                            }

                                            // Delete Circular Glass Button
                                            LiquidButton(
                                                onClick = {
                                                    dialogHost.showConfirmation(
                                                        title = "Delete Preset?",
                                                        message = "Are you sure you want to delete \"${preset.title}\"? This action cannot be undone.",
                                                        confirmText = "Delete",
                                                        confirmColor = colors.accentRed,
                                                        onConfirm = {
                                                            HapticPresetStorage.deletePreset(context, preset.id)
                                                            savedPresets = HapticPresetStorage.listPresets(context)
                                                            Toast.makeText(context, "Deleted \"${preset.title}\"", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                backdrop = backdrop,
                                                tint = colors.accentRed,
                                                size = 36.dp
                                            ) {
                                                TrashIcon(color = Color.White, size = 15.dp)
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Details Row
                                    val pipeName = preset.pipelines.getOrNull(preset.selectedPipelineIndex)?.pipelineType?.title
                                        ?: "Melodic Crisp"
                                    val trimInfo = "${formatTimeMs(preset.trimStartMs)} – ${formatTimeMs(preset.trimEndMs)}"

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.accentBlue.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            BasicText(
                                                text = pipeName,
                                                style = HyperBounceType.caption.copy(
                                                    color = colors.accentBlue,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.separator.copy(alpha = 0.20f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            BasicText(
                                                text = "Trim: $trimInfo",
                                                style = HyperBounceType.caption.copy(
                                                    color = colors.secondaryLabel,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    // Action buttons: Open in Studio & Package Module
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        LiquidButton(
                                            onClick = {
                                                player.stop()
                                                isPlaying = false
                                                val loaded = HapticPresetStorage.loadPresetData(context, preset)
                                                if (loaded != null) {
                                                    selectedAudioUri = loaded.audioUri
                                                    audioFileName = preset.title
                                                    decodedAudio = loaded.decodedAudio
                                                    synthesizedHaptics = loaded.synthesizedHaptics
                                                    selectedPipelineIndex = preset.selectedPipelineIndex.coerceIn(
                                                        0,
                                                        loaded.synthesizedHaptics.lastIndex
                                                    )
                                                    trimStartMs = preset.trimStartMs
                                                    trimEndMs = preset.trimEndMs
                                                    intensityMode = preset.intensityMode
                                                    totalDurationMs = loaded.decodedAudio.durationMs.toInt()
                                                    playbackProgressMs = preset.trimStartMs
                                                    isTrimMode = true
                                                    Toast.makeText(context, "Loaded \"${preset.title}\"", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to load preset", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            backdrop = backdrop,
                                            height = 42.dp,
                                            tint = colors.accentBlue,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            BasicText(
                                                text = "Open in Studio",
                                                style = HyperBounceType.body.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.5.sp
                                                )
                                            )
                                        }

                                        LiquidButton(
                                            onClick = {
                                                moduleDialogTargetPresetId = preset.id
                                                isModuleDialogOpen = true
                                            },
                                            backdrop = backdrop,
                                            height = 42.dp,
                                            tint = colors.accentPink,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            BasicText(
                                                text = "Package Module",
                                                style = HyperBounceType.body.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.5.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(36.dp))
            }
        }

        // Magisk / KernelSU Ringtone Module Packaging Modal
        RingtoneModuleBuilderDialog(
            isOpen = isModuleDialogOpen,
            savedPresets = savedPresets,
            colors = colors,
            backdrop = backdrop,
            initialSelectedPresetId = moduleDialogTargetPresetId,
            onDismiss = {
                isModuleDialogOpen = false
                moduleDialogTargetPresetId = null
            }
        )
    }
}

@Composable
private fun WaveformVisualizer(
    envelopePoints: FloatArray,
    currentMs: Int,
    totalDurationMs: Int,
    isTrimMode: Boolean = false,
    trimStartMs: Int = 0,
    trimEndMs: Int = 0,
    colors: HyperBounceColors,
    onSeek: (Float) -> Unit,
    onTrimChange: (startMs: Int, endMs: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val activeDur = totalDurationMs.coerceAtLeast(1)
    val playheadFraction = (currentMs.toFloat() / activeDur).coerceIn(0f, 1f)

    val trimStartFraction = (trimStartMs.toFloat() / activeDur).coerceIn(0f, 1f)
    val effectiveEndMs = if (trimEndMs > trimStartMs) trimEndMs else activeDur
    val trimEndFraction = (effectiveEndMs.toFloat() / activeDur).coerceIn(trimStartFraction, 1f)

    val currentStartMs by rememberUpdatedState(trimStartMs)
    val currentEndMs by rememberUpdatedState(effectiveEndMs)
    val currentOnTrimChange by rememberUpdatedState(onTrimChange)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(activeDur)
    val currentTrimMode by rememberUpdatedState(isTrimMode)

    val trimHandleColor = colors.accentBlue

    Box(
        modifier = modifier
            .pointerInput(currentTrimMode, currentDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture

                    val downFrac = (down.position.x / width).coerceIn(0f, 1f)
                    val touchTolerancePx = 40.dp.toPx()

                    var target: String? = null
                    if (currentTrimMode) {
                        val startX = width * (currentStartMs.toFloat() / currentDuration)
                        val endX = width * (currentEndMs.toFloat() / currentDuration)
                        val distStart = kotlin.math.abs(down.position.x - startX)
                        val distEnd = kotlin.math.abs(down.position.x - endX)

                        if (distStart <= touchTolerancePx && distStart <= distEnd) {
                            target = "start"
                        } else if (distEnd <= touchTolerancePx) {
                            target = "end"
                        }
                    }

                    if (target == null) {
                        target = "scrub"
                        currentOnSeek(downFrac)
                    }

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()

                        val currentFrac = (change.position.x / width).coerceIn(0f, 1f)
                        val posMs = (currentFrac * currentDuration).toInt()

                        when (target) {
                            "start" -> {
                                val newStart = posMs.coerceIn(0, currentEndMs - 500)
                                currentOnTrimChange(newStart, currentEndMs)
                            }
                            "end" -> {
                                val newEnd = posMs.coerceIn(currentStartMs + 500, currentDuration)
                                currentOnTrimChange(currentStartMs, newEnd)
                            }
                            "scrub" -> {
                                currentOnSeek(currentFrac)
                            }
                        }
                    } while (true)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            if (envelopePoints.isEmpty()) return@Canvas

            val stepX = width / envelopePoints.size.toFloat()

            // 1. Draw envelope waveform bars
            for (i in envelopePoints.indices) {
                val amp = envelopePoints[i].coerceIn(0f, 1f)
                val barH = amp * (height * 0.42f)
                val x = i * stepX
                val topY = midY - barH
                val botY = midY + barH

                val barFrac = x / width
                val isInsideTrim = !isTrimMode || (barFrac in trimStartFraction..trimEndFraction)
                val isPlayed = barFrac <= playheadFraction

                val barColor = when {
                    !isInsideTrim -> colors.accentBlue.copy(alpha = 0.12f)
                    isPlayed -> colors.accentBlue
                    else -> colors.accentBlue.copy(alpha = 0.40f)
                }

                drawLine(
                    color = barColor,
                    start = Offset(x, topY),
                    end = Offset(x, botY),
                    strokeWidth = max(2f, stepX * 0.75f),
                    cap = StrokeCap.Round
                )
            }

            // 2. Dimmed overlay and iOS trim handles (fully bounded to avoid edge clipping)
            if (isTrimMode) {
                val handleW = 12.dp.toPx()
                val cornerR = 8.dp.toPx()
                val startX = (width * trimStartFraction).coerceIn(0f, (width - handleW * 2f).coerceAtLeast(0f))
                val endX = (width * trimEndFraction).coerceIn(startX + handleW * 2f, width)

                // Outside dimming
                if (startX > 0f) {
                    drawRect(
                        color = Color(0x88000000),
                        topLeft = Offset(0f, 0f),
                        size = Size(startX, height)
                    )
                }
                if (endX < width) {
                    drawRect(
                        color = Color(0x88000000),
                        topLeft = Offset(endX, 0f),
                        size = Size(width - endX, height)
                    )
                }

                // Top and bottom boundary connector rails between the handles
                val railStart = startX + handleW
                val railEnd = endX - handleW
                if (railEnd > railStart) {
                    val railThick = 3.dp.toPx()
                    drawRect(
                        color = trimHandleColor,
                        topLeft = Offset(railStart, 0f),
                        size = Size(railEnd - railStart, railThick)
                    )
                    drawRect(
                        color = trimHandleColor,
                        topLeft = Offset(railStart, height - railThick),
                        size = Size(railEnd - railStart, railThick)
                    )
                }

                // Start handle bracket (iOS trim tab style with curved outer top-left & bottom-left)
                val startHandlePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = startX,
                            top = 0f,
                            right = startX + handleW,
                            bottom = height,
                            topLeftCornerRadius = CornerRadius(cornerR, cornerR),
                            bottomLeftCornerRadius = CornerRadius(cornerR, cornerR),
                            topRightCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(startHandlePath, color = trimHandleColor)

                // Grab ridges on start handle
                val ridgeX = startX + handleW / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(ridgeX, height * 0.40f),
                    end = Offset(ridgeX, height * 0.60f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // End handle bracket (iOS trim tab style with curved outer top-right & bottom-right)
                val endHandlePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = endX - handleW,
                            top = 0f,
                            right = endX,
                            bottom = height,
                            topLeftCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero,
                            topRightCornerRadius = CornerRadius(cornerR, cornerR),
                            bottomRightCornerRadius = CornerRadius(cornerR, cornerR)
                        )
                    )
                }
                drawPath(endHandlePath, color = trimHandleColor)

                // Grab ridges on end handle
                val endRidgeX = endX - handleW / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(endRidgeX, height * 0.40f),
                    end = Offset(endRidgeX, height * 0.60f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Playhead scrubber needle
            if (playheadFraction > 0f) {
                val playheadX = width * playheadFraction
                drawLine(
                    color = colors.accentPink.copy(alpha = 0.35f),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.accentPink,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = colors.accentPink,
                    radius = 4.5.dp.toPx(),
                    center = Offset(playheadX, 8.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(playheadX, 8.dp.toPx())
                )
            }
        }

        // Time indicator badge embedded inside the WaveformVisualizer
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xD9000000))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            val badgeText = if (isTrimMode) {
                val clipSec = ((effectiveEndMs - trimStartMs) / 1000).coerceAtLeast(0)
                "${formatTimeMs(trimStartMs)} – ${formatTimeMs(effectiveEndMs)} (${clipSec}s)"
            } else {
                "${formatTimeMs(currentMs)} / ${formatTimeMs(totalDurationMs)}"
            }

            BasicText(
                text = badgeText,
                style = HyperBounceType.caption.copy(
                    color = if (isTrimMode) trimHandleColor else Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

internal fun formatTimeMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun exportRtpBinary(
    context: Context,
    haptic: SynthesizedHaptic,
    baseName: String,
    onStatus: (String) -> Unit = {}
) {
    try {
        val cleanName = baseName.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val fileName = "${cleanName}_${haptic.pipelineType.name}_P_RTP.bin"

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadDir, "HyperBounce_Haptics")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val file = File(targetDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(haptic.rtpRawBytes)
        }

        onStatus("Exported: $fileName")
    } catch (e: Exception) {
        onStatus("Export failed: ${e.message}")
    }
}




