package dev.joseph.hyperbounce.haptics.dsp

import android.os.Build
import android.os.VibrationEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object HapticSynthesizer {

    private const val OUTPUT_SAMPLE_RATE = 24000 // 24 kHz standard for Qualcomm/Xiaomi RTP

    suspend fun synthesizeAll(
        audio: AudioDecoder.DecodedAudio,
        config: PipelineConfig = PipelineConfig(),
        onProgress: (Float) -> Unit = {}
    ): List<SynthesizedHaptic> = withContext(Dispatchers.Default) {
        val results = mutableListOf<SynthesizedHaptic>()
        val pipelines = PipelineType.entries.toTypedArray()
        val total = pipelines.size

        for ((index, pipeline) in pipelines.withIndex()) {
            val haptic = synthesize(audio, pipeline, config)
            results.add(haptic)
            val progress = 0.45f + ((index + 1).toFloat() / total) * 0.55f
            onProgress(progress)
        }

        results
    }

    fun synthesize(
        audio: AudioDecoder.DecodedAudio,
        pipelineType: PipelineType,
        config: PipelineConfig
    ): SynthesizedHaptic {
        val maxSamples = ((config.maxDurationMs * audio.sampleRate) / 1000L).toInt()
        val input = if (audio.samples.size > maxSamples) {
            audio.samples.copyOf(maxSamples)
        } else {
            audio.samples
        }

        val outputDurationMs = if (audio.sampleRate > 0) {
            (input.size.toLong() * 1000L) / audio.sampleRate
        } else 0L

        val outputLength = ((outputDurationMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
        val rtpFloats = FloatArray(outputLength)
        val events = mutableListOf<HapticEvent>()

        when (pipelineType) {
            PipelineType.SharpTransient -> {
                processSharpTransient(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.ResonantPunch -> {
                processResonantPunch(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.HarmonicBeatSnap -> {
                processHarmonicBeatSnap(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.GranularTexture -> {
                processGranularTexture(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.AdaptiveHybrid -> {
                processAdaptiveHybrid(input, audio.sampleRate, rtpFloats, events, config)
            }
        }

        // Convert rtpFloats (-1.0f to 1.0f) to signed 8-bit PCM byte array (-128 to 127)
        val rtpRawBytes = ByteArray(rtpFloats.size)
        for (i in rtpFloats.indices) {
            val clamped = rtpFloats[i].coerceIn(-1.0f, 1.0f)
            val s8 = (clamped * 127.0f).roundToInt().coerceIn(-128, 127)
            rtpRawBytes[i] = s8.toByte()
        }

        // Downsampled envelope points for live UI visualizer (150 bars)
        val numDisplayPoints = 150
        val envelopePoints = FloatArray(numDisplayPoints)
        val step = max(1, rtpFloats.size / numDisplayPoints)
        for (i in 0 until numDisplayPoints) {
            val start = i * step
            val end = min(start + step, rtpFloats.size)
            var maxVal = 0f
            for (j in start until end) {
                val a = abs(rtpFloats[j])
                if (a > maxVal) maxVal = a
            }
            envelopePoints[i] = maxVal
        }

        val vibrationEffect = buildAndroidVibrationEffect(events, outputDurationMs)

        return SynthesizedHaptic(
            pipelineType = pipelineType,
            rtpRawBytes = rtpRawBytes,
            vibrationEffect = vibrationEffect,
            envelopePoints = envelopePoints,
            events = events,
            durationMs = outputDurationMs,
            peakCount = events.size,
            sampleRateHz = OUTPUT_SAMPLE_RATE
        )
    }

    /**
     * Recreates the exact 15.8ms active-braking excitation pulse found in Xiaomi's
     * vendor firmware (/vendor/firmware/0_click_P_RTP.bin).
     * Single-period 160Hz sinusoidal displacement + 180° inverted braking half-wave.
     */
    private fun stampHardwareClickPulse(output: FloatArray, startIndex: Int, amp: Float, brakingStrength: Float) {
        val periodSamples = (OUTPUT_SAMPLE_RATE / 160f).toInt() // ~150 samples at 24kHz
        val halfPeriod = periodSamples / 2 // ~75 samples
        val totalSamples = periodSamples + (halfPeriod * brakingStrength).toInt() // ~210 samples (~8.8 to 16ms)

        val limit = minOf(output.size, startIndex + totalSamples)
        var idx = startIndex
        for (s in 0 until totalSamples) {
            if (idx >= limit) break
            val value = if (s < periodSamples) {
                sin(2.0 * PI * s / periodSamples).toFloat() * amp
            } else {
                val brakeS = s - periodSamples
                -sin(PI * brakeS / halfPeriod).toFloat() * amp * brakingStrength
            }
            output[idx] = (output[idx] + value).coerceIn(-1.0f, 1.0f)
            idx++
        }
    }

    /**
     * Pipeline 1: Sharp Transient (Glass Click & Micro-Impact)
     * High-frequency spectral flux onset detection mapped to Effect 0 (EFFECT_CLICK).
     */
    private fun processSharpTransient(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val windowSize = 256
        val hopSize = 128
        var prevEnergy = 0f
        val minIntervalMs = 70L
        var lastBeatMs = -minIntervalMs

        var i = 0
        while (i + windowSize < input.size) {
            var highFreqEnergy = 0f
            // High-pass difference filter for transients
            for (j in 1 until windowSize) {
                val diff = input[i + j] - input[i + j - 1]
                highFreqEnergy += diff * diff
            }
            highFreqEnergy = sqrt(highFreqEnergy / windowSize)

            val flux = max(0f, highFreqEnergy - prevEnergy)
            prevEnergy = highFreqEnergy

            val threshold = (0.045f / config.sensitivity.coerceAtLeast(0.2f))
            val currentMs = (i.toLong() * 1000L) / inRate

            if (flux > threshold && (currentMs - lastBeatMs >= minIntervalMs)) {
                lastBeatMs = currentMs
                events.add(HapticEvent(timestampMs = currentMs, effectId = 0)) // EFFECT_CLICK

                val outIdx = ((currentMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                val amp = (flux * 4.0f * config.sensitivity).coerceIn(0.4f, 1.0f)
                stampHardwareClickPulse(output, outIdx, amp, config.brakingStrength)
            }
            i += hopSize
        }
    }

    /**
     * Pipeline 2: Resonant Punch (Deep Bass & Kick Impact)
     * Bandpass filtered low-end onset mapped to Effect 5 (EFFECT_HEAVY_CLICK) and Effect 3 (EFFECT_THUD).
     */
    private fun processResonantPunch(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val windowSize = 512
        val hopSize = 256
        var runningAvgEnergy = 0.05f
        val minIntervalMs = 90L
        var lastBeatMs = -minIntervalMs

        var i = 0
        while (i + windowSize < input.size) {
            var bassEnergy = 0f
            for (j in 0 until windowSize) {
                val s = input[i + j]
                bassEnergy += s * s
            }
            bassEnergy = sqrt(bassEnergy / windowSize)

            val currentMs = (i.toLong() * 1000L) / inRate
            val multiplier = (1.8f / config.sensitivity.coerceAtLeast(0.2f)).coerceIn(1.2f, 2.8f)

            if (bassEnergy > (runningAvgEnergy * multiplier) && (currentMs - lastBeatMs >= minIntervalMs)) {
                lastBeatMs = currentMs
                events.add(HapticEvent(timestampMs = currentMs, effectId = 5)) // EFFECT_HEAVY_CLICK

                val outIdx = ((currentMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                val amp = (bassEnergy * 3.5f * config.sensitivity).coerceIn(0.5f, 1.0f)
                stampHardwareClickPulse(output, outIdx, amp, config.brakingStrength)
            }

            runningAvgEnergy = runningAvgEnergy * 0.92f + bassEnergy * 0.08f
            i += hopSize
        }
    }

    /**
     * Pipeline 3: Harmonic Beat-Snap (Rhythmic Pitch Tracking)
     * Multi-band onset detection. Downbeats trigger Heavy Click (5), upbeats trigger Click (0).
     */
    private fun processHarmonicBeatSnap(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val windowSize = 384
        val hopSize = 192
        var prevRms = 0f
        var beatCounter = 0
        val minIntervalMs = 80L
        var lastBeatMs = -minIntervalMs

        var i = 0
        while (i + windowSize < input.size) {
            var sum = 0f
            for (j in 0 until windowSize) {
                val s = input[i + j]
                sum += s * s
            }
            val rms = sqrt(sum / windowSize)
            val flux = max(0f, rms - prevRms)
            prevRms = rms

            val threshold = (0.04f / config.sensitivity.coerceAtLeast(0.2f))
            val currentMs = (i.toLong() * 1000L) / inRate

            if (flux > threshold && (currentMs - lastBeatMs >= minIntervalMs)) {
                lastBeatMs = currentMs
                val isDownbeat = (beatCounter % 2 == 0)
                val effectId = if (isDownbeat) 5 else 0 // Heavy Click on downbeat, Click on off-beat
                events.add(HapticEvent(timestampMs = currentMs, effectId = effectId))
                beatCounter++

                val outIdx = ((currentMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                val amp = (flux * 3.8f * config.sensitivity).coerceIn(0.4f, 1.0f)
                stampHardwareClickPulse(output, outIdx, amp, config.brakingStrength)
            }
            i += hopSize
        }
    }

    /**
     * Pipeline 4: Granular Texture & Friction
     * Micro-rhythm tracking mapped to tactile micro-ticks (Effect 2: EFFECT_TICK).
     */
    private fun processGranularTexture(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val windowSize = 256
        val hopSize = 128
        val minIntervalMs = 45L
        var lastBeatMs = -minIntervalMs

        var i = 0
        while (i + windowSize < input.size) {
            var zcr = 0
            for (j in 1 until windowSize) {
                if ((input[i + j] >= 0f && input[i + j - 1] < 0f) ||
                    (input[i + j] < 0f && input[i + j - 1] >= 0f)) {
                    zcr++
                }
            }

            val currentMs = (i.toLong() * 1000L) / inRate
            val thresholdZcr = (25 / config.sensitivity.coerceAtLeast(0.2f)).toInt()

            if (zcr > thresholdZcr && (currentMs - lastBeatMs >= minIntervalMs)) {
                lastBeatMs = currentMs
                events.add(HapticEvent(timestampMs = currentMs, effectId = 2)) // EFFECT_TICK

                val outIdx = ((currentMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                stampHardwareClickPulse(output, outIdx, 0.45f * config.sensitivity, config.brakingStrength)
            }
            i += hopSize
        }
    }

    /**
     * Pipeline 5: Adaptive Dynamic Hybrid
     * Alternates between Bass Kick (Effect 5) and High Snare/Click (Effect 0).
     */
    private fun processAdaptiveHybrid(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val windowSize = 384
        val hopSize = 192
        var prevTotalEnergy = 0f
        val minIntervalMs = 75L
        var lastBeatMs = -minIntervalMs

        var i = 0
        while (i + windowSize < input.size) {
            var lowEnergy = 0f
            var highEnergy = 0f

            for (j in 1 until windowSize) {
                val s = input[i + j]
                val diff = s - input[i + j - 1]
                lowEnergy += s * s
                highEnergy += diff * diff
            }

            val totalEnergy = sqrt((lowEnergy + highEnergy) / windowSize)
            val flux = max(0f, totalEnergy - prevTotalEnergy)
            prevTotalEnergy = totalEnergy

            val currentMs = (i.toLong() * 1000L) / inRate
            val threshold = (0.04f / config.sensitivity.coerceAtLeast(0.2f))

            if (flux > threshold && (currentMs - lastBeatMs >= minIntervalMs)) {
                lastBeatMs = currentMs
                val isBassDominant = (lowEnergy > highEnergy * 1.5f)
                val effectId = if (isBassDominant) 5 else 0 // Heavy Click for bass, Click for treble

                events.add(HapticEvent(timestampMs = currentMs, effectId = effectId))

                val outIdx = ((currentMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                val amp = (flux * 4.0f * config.sensitivity).coerceIn(0.45f, 1.0f)
                stampHardwareClickPulse(output, outIdx, amp, config.brakingStrength)
            }
            i += hopSize
        }
    }

    /**
     * Generates Android VibrationEffect with silence between discrete beat events,
     * preventing continuous motor buzzing.
     */
    private fun buildAndroidVibrationEffect(events: List<HapticEvent>, durationMs: Long): VibrationEffect {
        if (events.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }

        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()

        var lastTimeMs = 0L
        for (event in events.sortedBy { it.timestampMs }) {
            val pauseMs = (event.timestampMs - lastTimeMs).coerceAtLeast(0L)
            if (pauseMs > 0) {
                timings.add(pauseMs)
                amplitudes.add(0) // Dead silence between beats!
            }
            val pulseMs = 16L
            timings.add(pulseMs)
            val amp = when (event.effectId) {
                5 -> 255
                3 -> 200
                2 -> 90
                else -> 180
            }
            amplitudes.add(amp)
            lastTimeMs = event.timestampMs + pulseMs
        }

        if (durationMs > lastTimeMs) {
            timings.add(durationMs - lastTimeMs)
            amplitudes.add(0)
        }

        return try {
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
        } catch (t: Throwable) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }
    }
}
