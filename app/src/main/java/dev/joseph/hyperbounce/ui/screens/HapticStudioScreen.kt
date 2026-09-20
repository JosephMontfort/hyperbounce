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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import dev.joseph.hyperbounce.haptics.dsp.AudioDecoder
import dev.joseph.hyperbounce.haptics.dsp.HapticSynthesizer
import dev.joseph.hyperbounce.haptics.dsp.PipelineConfig
import dev.joseph.hyperbounce.haptics.dsp.PipelineType
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic
import dev.joseph.hyperbounce.haptics.player.SynchronizedHapticPlayer
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.glass.LiquidButton
import dev.joseph.hyperbounce.ui.glass.LiquidSlider
import dev.joseph.hyperbounce.ui.glass.LiquidToggle
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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

    // Tuning Parameters
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
                    Toast.makeText(context, "Error processing audio: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    ScreenScaffold(
        title = "Haptic Studio",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        subtitle = "Audio-to-haptic precision engine"
    ) { contentBackdrop ->
        val cardColor = colors.elevatedSurfaceTint
        val localBackdrop = rememberCanvasBackdrop { drawRect(cardColor) }

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
                        text = "Upload any ringtone, UI sound, or music (.wav, .mp3, .ogg, .m4a) to generate 5 distinct precision haptic waveforms.",
                        style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(
                            onClick = {
                                audioPickerLauncher.launch("audio/*")
                            },
                            backdrop = backdrop,
                            tint = colors.accentBlue,
                            modifier = Modifier.weight(1f)
                        ) {
                            BasicText(
                                text = if (audioFileName == null) "Select Audio" else "Choose Different",
                                style = HyperBounceType.body.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        if (selectedAudioUri != null && !isProcessing) {
                            LiquidButton(
                                onClick = {
                                    val uri = selectedAudioUri ?: return@LiquidButton
                                    scope.launch {
                                        isProcessing = true
                                        processingProgress = 0.1f
                                        try {
                                            val decoded = decodedAudio ?: AudioDecoder.decode(context, uri)
                                            decodedAudio = decoded
                                            val config = PipelineConfig(
                                                resonantFrequencyHz = resonantFreq,
                                                sensitivity = sensitivity,
                                                brakingStrength = brakingStrength
                                            )
                                            val results = HapticSynthesizer.synthesizeAll(decoded, config) { p ->
                                                processingProgress = p
                                            }
                                            synthesizedHaptics = results
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                backdrop = backdrop,
                                tint = colors.accentPurple,
                                modifier = Modifier.weight(1f)
                            ) {
                                BasicText(
                                    text = "Re-Synthesize",
                                    style = HyperBounceType.body.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Processing Progress View
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

                            // Custom Progress Bar
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
                                text = "${(processingProgress * 100).toInt()}% • Onset detection & 24kHz RTP generation",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                            )
                        }
                    }
                }
            }
        }

        // Results & Synchronized Playback View
        if (synthesizedHaptics.isNotEmpty() && !isProcessing) {
            val currentHaptic = synthesizedHaptics.getOrNull(selectedPipelineIndex) ?: synthesizedHaptics[0]

            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Generated Haptics (${selectedPipelineIndex + 1} of ${synthesizedHaptics.size})", colors)

                // Pipeline Selector Tabs
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
                                text = "${idx + 1}. ${haptic.pipelineType.title}",
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = currentHaptic.pipelineType.title,
                                    style = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                BasicText(
                                    text = currentHaptic.pipelineType.subtitle,
                                    style = HyperBounceType.caption.copy(color = colors.accentBlue)
                                )
                            }

                            // Pipeline Index Pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.accentBlue.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    text = "${selectedPipelineIndex + 1} / ${synthesizedHaptics.size}",
                                    style = HyperBounceType.caption.copy(
                                        color = colors.accentBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            text = currentHaptic.pipelineType.description,
                            style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Waveform Visualizer
                        WaveformVisualizer(
                            envelopePoints = currentHaptic.envelopePoints,
                            playheadFraction = if (totalDurationMs > 0) playbackProgressMs.toFloat() / totalDurationMs else 0f,
                            colors = colors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F141C))
                        )

                        Spacer(Modifier.height(16.dp))

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
                                                onProgress = { cur, tot ->
                                                    playbackProgressMs = cur
                                                    totalDurationMs = tot
                                                    isPlaying = true
                                                },
                                                onCompletion = {
                                                    isPlaying = false
                                                    playbackProgressMs = 0
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
                                    text = if (isPlaying) "Playing Synchronized Audio + Haptic..." else "Tap to Play Synchronized Preview",
                                    style = HyperBounceType.body.copy(
                                        color = colors.label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                BasicText(
                                    text = "${currentHaptic.durationMs}ms duration • ${currentHaptic.peakCount} peaks • 24kHz RTP",
                                    style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        RowDivider(colors.separator)
                        Spacer(Modifier.height(14.dp))

                        // Actions: Export .bin & Apply
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidButton(
                                onClick = {
                                    exportRtpBinary(context, currentHaptic, audioFileName ?: "effect")
                                },
                                backdrop = backdrop,
                                tint = colors.accentOrange,
                                modifier = Modifier.weight(1f)
                            ) {
                                BasicText(
                                    text = "Export RTP .bin",
                                    style = HyperBounceType.body.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            LiquidButton(
                                onClick = {
                                    repository.audioHaptics.setActivePipelinePref(currentHaptic.pipelineType.name)
                                    repository.audioHaptics.setResonantFrequencyHzPref(resonantFreq)
                                    Toast.makeText(context, "Preset saved for HyperBounce!", Toast.LENGTH_SHORT).show()
                                },
                                backdrop = backdrop,
                                tint = colors.accentGreen,
                                modifier = Modifier.weight(1f)
                            ) {
                                BasicText(
                                    text = "Save Preset",
                                    style = HyperBounceType.body.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Fine-Tune Re-Creation Parameters", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        BasicText(
                            text = "Motor Resonant Frequency: ${resonantFreq.toInt()} Hz",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        BasicText(
                            text = "Tuned to Garnet X-axis LRA sweet spot (~160 Hz)",
                            style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidSlider(
                            value = { resonantFreq },
                            onValueChange = { resonantFreq = it },
                            valueRange = 140f..180f,
                            visibilityThreshold = 0.04f,
                            backdrop = localBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        RowDivider(colors.separator)
                        Spacer(Modifier.height(12.dp))

                        BasicText(
                            text = "Transient Sensitivity: ${(sensitivity * 100).toInt()}%",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidSlider(
                            value = { sensitivity },
                            onValueChange = { sensitivity = it },
                            valueRange = 0.4f..2.0f,
                            visibilityThreshold = 0.01f,
                            backdrop = localBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        RowDivider(colors.separator)
                        Spacer(Modifier.height(12.dp))

                        BasicText(
                            text = "Active Braking Strength: ${(brakingStrength * 100).toInt()}%",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        BasicText(
                            text = "Inverts pulse phase at end of wave to stop mechanical ringing dead",
                            style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidSlider(
                            value = { brakingStrength },
                            onValueChange = { brakingStrength = it },
                            valueRange = 0.0f..1.0f,
                            visibilityThreshold = 0.01f,
                            backdrop = localBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Real-Time Gaming & Media Section
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Real-Time Game & Media Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = "Real-Time AudioTrack Haptics",
                                style = HyperBounceType.body.copy(
                                    color = colors.label,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            BasicText(
                                text = "Intercepts in-game audio buffers and fires ultra-fast micro-ticks on gunshots, beats & impacts",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                            )
                        }
                        LiquidToggle(
                            selected = { repository.audioHaptics.realTimeEnabled },
                            onSelect = { repository.audioHaptics.setRealTimeEnabledPref(it) },
                            backdrop = localBackdrop
                        )
                    }

                    if (repository.audioHaptics.realTimeEnabled) {
                        Spacer(Modifier.height(14.dp))
                        RowDivider(colors.separator)
                        Spacer(Modifier.height(14.dp))

                        BasicText(
                            text = "Gunfire & Beat Sensitivity: ${(repository.audioHaptics.sensitivity * 100).toInt()}%",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidSlider(
                            value = { repository.audioHaptics.sensitivity },
                            onValueChange = { repository.audioHaptics.setSensitivityPref(it) },
                            valueRange = 0.3f..1.8f,
                            visibilityThreshold = 0.01f,
                            backdrop = localBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        BasicText(
                            text = "Anti-Saturation Debounce: ${repository.audioHaptics.refractoryMs} ms",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        BasicText(
                            text = "Prevents high fire-rate weapons from turning into a muddy continuous buzz",
                            style = HyperBounceType.caption.copy(color = colors.secondaryLabel)
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidSlider(
                            value = { repository.audioHaptics.refractoryMs.toFloat() },
                            onValueChange = { repository.audioHaptics.setRefractoryMsPref(it.toLong()) },
                            valueRange = 20f..80f,
                            visibilityThreshold = 0.05f,
                            backdrop = localBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WaveformVisualizer(
    envelopePoints: FloatArray,
    playheadFraction: Float,
    colors: HyperBounceColors,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        if (envelopePoints.isEmpty()) return@Canvas

        val stepX = width / envelopePoints.size.toFloat()

        for (i in envelopePoints.indices) {
            val amp = envelopePoints[i].coerceIn(0f, 1f)
            val barH = amp * (height * 0.42f)
            val x = i * stepX
            val topY = midY - barH
            val botY = midY + barH

            drawLine(
                color = colors.accentBlue.copy(alpha = 0.7f),
                start = Offset(x, topY),
                end = Offset(x, botY),
                strokeWidth = max(2f, stepX * 0.7f),
                cap = StrokeCap.Round
            )
        }

        // Playhead indicator
        if (playheadFraction > 0f) {
            val playheadX = width * playheadFraction.coerceIn(0f, 1f)
            drawLine(
                color = colors.accentPink,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun exportRtpBinary(context: Context, haptic: SynthesizedHaptic, baseName: String) {
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

        Toast.makeText(
            context,
            "Exported 24kHz RTP: Downloads/HyperBounce_Haptics/$fileName",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
