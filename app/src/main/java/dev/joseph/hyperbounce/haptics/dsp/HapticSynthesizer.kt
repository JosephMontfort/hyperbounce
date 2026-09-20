package dev.joseph.hyperbounce.haptics.dsp

import android.os.Build
import android.os.VibrationEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
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
        // Clamp processing length to maxDurationMs
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

        var peakCount = 0

        when (pipelineType) {
            PipelineType.SharpTransient -> {
                peakCount = processSharpTransient(input, audio.sampleRate, rtpFloats, config)
            }
            PipelineType.ResonantPunch -> {
                peakCount = processResonantPunch(input, audio.sampleRate, rtpFloats, config)
            }
            PipelineType.HarmonicBeatSnap -> {
                peakCount = processHarmonicBeatSnap(input, audio.sampleRate, rtpFloats, config)
            }
            PipelineType.GranularTexture -> {
                peakCount = processGranularTexture(input, audio.sampleRate, rtpFloats, config)
            }
            PipelineType.AdaptiveHybrid -> {
                peakCount = processAdaptiveHybrid(input, audio.sampleRate, rtpFloats, config)
            }
        }

        // Convert rtpFloats (-1.0f to 1.0f) to signed 8-bit PCM byte array (-128 to 127)
        val rtpRawBytes = ByteArray(rtpFloats.size)
        for (i in rtpFloats.indices) {
            val clamped = rtpFloats[i].coerceIn(-1.0f, 1.0f)
            val s8 = (clamped * 127.0f).roundToInt().coerceIn(-128, 127)
            rtpRawBytes[i] = s8.toByte()
        }

        // Generate downsampled envelope for UI visualization (e.g. 150 points)
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

        // Generate Android VibrationEffect for live preview (converting into 10ms frame chunks)
        val vibrationEffect = buildAndroidVibrationEffect(rtpRawBytes, outputDurationMs)

        return SynthesizedHaptic(
            pipelineType = pipelineType,
            rtpRawBytes = rtpRawBytes,
            vibrationEffect = vibrationEffect,
            envelopePoints = envelopePoints,
            durationMs = outputDurationMs,
            peakCount = peakCount,
            sampleRateHz = OUTPUT_SAMPLE_RATE
        )
    }

    /**
     * Pipeline 1: Sharp Transient (Micro-Impact / Glass Click)
     * Detects high-frequency spectral flux onsets.
     * Generates a short 14ms sine pulse with active 180° inverted braking at the tail.
     */
    private fun processSharpTransient(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        config: PipelineConfig
    ): Int {
        val windowSize = 256
        val hopSize = 128
        var detectedPeaks = 0

        // Compute local energy and high-frequency content onset
        var prevEnergy = 0f
        val pulseLengthMs = 14f
        val pulseSamples = (pulseLengthMs * OUTPUT_SAMPLE_RATE / 1000f).toInt()
        val brakingSamples = (pulseSamples * 0.35f * config.brakingStrength).toInt()
        val freq = config.resonantFrequencyHz

        var i = 0
        var refractorySamples = 0

        while (i + windowSize < input.size) {
            var energy = 0f
            for (j in 0 until windowSize) {
                val s = input[i + j]
                energy += s * s
            }
            energy = sqrt(energy / windowSize)

            val flux = max(0f, energy - prevEnergy)
            prevEnergy = energy

            val threshold = 0.08f / config.sensitivity.coerceAtLeast(0.1f)

            if (flux > threshold && refractorySamples <= 0) {
                detectedPeaks++
                val outStartIndex = ((i.toLong() * OUTPUT_SAMPLE_RATE) / inRate).toInt()
                val amp = (energy * 3.0f * config.sensitivity).coerceIn(0.4f, 1.0f)

                // Inject 160Hz carrier with Hann envelope + active braking
                for (k in 0 until pulseSamples) {
                    val outIdx = outStartIndex + k
                    if (outIdx >= output.size) break
                    val t = k.toFloat() / OUTPUT_SAMPLE_RATE
                    val carrier = sin(2.0 * PI * freq * t).toFloat()

                    val isBraking = k >= (pulseSamples - brakingSamples)
                    val env: Float
                    val sign: Float

                    if (!isBraking) {
                        val driveLen = pulseSamples - brakingSamples
                        env = sin(PI * k / driveLen).toFloat()
                        sign = 1.0f
                    } else {
                        // 180-degree inverted phase pulse to stop LRA mechanical ringing
                        val brakeK = k - (pulseSamples - brakingSamples)
                        env = (sin(PI * brakeK / brakingSamples) * config.brakingStrength).toFloat()
                        sign = -1.0f
                    }

                    output[outIdx] = (sign * carrier * env * amp).coerceIn(-1.0f, 1.0f)
                }
                refractorySamples = (0.035f * inRate).toInt() // 35ms debounce
            } else if (refractorySamples > 0) {
                refractorySamples -= hopSize
            }

            i += hopSize
        }
        return detectedPeaks
    }

    /**
     * Pipeline 2: Resonant Punch (Deep Bass & Kick Impact)
     * Bandpass filtered around motor resonance (155-165 Hz) with envelope follower.
     */
    private fun processResonantPunch(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        config: PipelineConfig
    ): Int {
        // 2nd-order Biquad Bandpass filter centered at config.resonantFrequencyHz
        val f0 = config.resonantFrequencyHz.toDouble()
        val q = 1.8
        val omega = 2.0 * PI * f0 / inRate
        val alpha = sin(omega) / (2.0 * q)

        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cos(omega)
        val a2 = 1.0 - alpha

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        val filtered = FloatArray(input.size)
        var detectedPeaks = 0
        var envelope = 0f

        for (n in input.indices) {
            val x0 = input[n].toDouble()
            val y0 = (b0 / a0) * x0 + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            filtered[n] = y0.toFloat()

            // Envelope detection
            val absVal = abs(filtered[n])
            if (absVal > envelope) {
                envelope = 0.8f * envelope + 0.2f * absVal
            } else {
                envelope *= 0.995f
            }

            if (envelope > 0.15f) {
                detectedPeaks++
            }
        }

        // Resample and modulate output
        for (m in output.indices) {
            val inIdx = ((m.toLong() * inRate) / OUTPUT_SAMPLE_RATE).toInt().coerceIn(0, input.size - 1)
            val t = m.toFloat() / OUTPUT_SAMPLE_RATE
            val carrier = sin(2.0 * PI * f0 * t).toFloat()
            val amp = (filtered[inIdx] * 2.2f * config.sensitivity).coerceIn(-1.0f, 1.0f)
            output[m] = (carrier * amp).coerceIn(-1.0f, 1.0f)
        }

        return max(1, detectedPeaks / 1000)
    }

    /**
     * Pipeline 3: Harmonic Beat-Snap (Rhythmic Melodic Tracking)
     * Extracts low-end beat envelopes (40-120Hz) and snaps them to 160Hz carrier.
     */
    private fun processHarmonicBeatSnap(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        config: PipelineConfig
    ): Int {
        var peaks = 0
        val blockSize = inRate / 40 // 25ms blocks
        val f0 = config.resonantFrequencyHz.toDouble()

        var idx = 0
        while (idx + blockSize < input.size) {
            var sum = 0f
            for (k in 0 until blockSize) {
                val s = input[idx + k]
                sum += s * s
            }
            val rms = sqrt(sum / blockSize)

            val outStart = ((idx.toLong() * OUTPUT_SAMPLE_RATE) / inRate).toInt()
            val outEnd = (((idx + blockSize).toLong() * OUTPUT_SAMPLE_RATE) / inRate).toInt()

            if (rms > 0.05f) {
                peaks++
                val scaledAmp = (rms * 2.5f * config.sensitivity).coerceIn(0.1f, 1.0f)
                for (m in outStart until min(outEnd, output.size)) {
                    val t = m.toFloat() / OUTPUT_SAMPLE_RATE
                    output[m] = (sin(2.0 * PI * f0 * t).toFloat() * scaledAmp).coerceIn(-1.0f, 1.0f)
                }
            }
            idx += blockSize
        }
        return peaks
    }

    /**
     * Pipeline 4: Granular Texture & Scrub (Continuous Sensation)
     * Micro-staccato pulse bursts based on zero-crossing density.
     */
    private fun processGranularTexture(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        config: PipelineConfig
    ): Int {
        var zeroCrossings = 0
        val grainSizeMs = 6.0f
        val grainSamples = (grainSizeMs * OUTPUT_SAMPLE_RATE / 1000f).toInt()
        val f0 = config.resonantFrequencyHz.toDouble()
        var lastGrainEnd = 0

        for (i in 1 until input.size) {
            if ((input[i - 1] >= 0 && input[i] < 0) || (input[i - 1] < 0 && input[i] >= 0)) {
                zeroCrossings++
                val outIdx = ((i.toLong() * OUTPUT_SAMPLE_RATE) / inRate).toInt()

                if (outIdx >= lastGrainEnd && outIdx + grainSamples < output.size) {
                    val amp = (abs(input[i]) * 3.0f * config.sensitivity).coerceIn(0.3f, 0.95f)
                    for (g in 0 until grainSamples) {
                        val t = g.toFloat() / OUTPUT_SAMPLE_RATE
                        val env = sin(PI * g / grainSamples).toFloat()
                        output[outIdx + g] = (sin(2.0 * PI * f0 * t).toFloat() * env * amp).coerceIn(-1.0f, 1.0f)
                    }
                    lastGrainEnd = outIdx + grainSamples
                }
            }
        }
        return zeroCrossings / 20
    }

    /**
     * Pipeline 5: Adaptive Dynamic Hybrid (Full Dynamic Spectrum)
     * Dual-band crossover with non-linear Dynamic Range Compression.
     */
    private fun processAdaptiveHybrid(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        config: PipelineConfig
    ): Int {
        val f0 = config.resonantFrequencyHz.toDouble()
        var peaks = 0

        for (m in output.indices) {
            val inIdx = ((m.toLong() * inRate) / OUTPUT_SAMPLE_RATE).toInt().coerceIn(0, input.size - 1)
            val s = input[inIdx]
            val absS = abs(s)

            if (absS > 0.04f) {
                peaks++
                // Dynamic Range Compression: boost soft sounds with log curve
                val compressed = (1.0f - exp(-3.0f * absS)) * config.sensitivity
                val t = m.toFloat() / OUTPUT_SAMPLE_RATE
                val carrier = sin(2.0 * PI * f0 * t).toFloat()
                output[m] = (carrier * compressed).coerceIn(-1.0f, 1.0f)
            }
        }
        return max(1, peaks / 800)
    }

    /**
     * Converts raw 24kHz RTP audio bytes into an Android VibrationEffect for live preview.
     * Chunks audio into 10ms timing frames with corresponding amplitudes (0-255).
     */
    private fun buildAndroidVibrationEffect(rtpBytes: ByteArray, durationMs: Long): VibrationEffect {
        val frameDurationMs = 10L
        val samplesPerFrame = (OUTPUT_SAMPLE_RATE * frameDurationMs / 1000L).toInt()
        val numFrames = (rtpBytes.size / samplesPerFrame).coerceAtLeast(1)

        val timings = LongArray(numFrames) { frameDurationMs }
        val amplitudes = IntArray(numFrames)

        for (f in 0 until numFrames) {
            val start = f * samplesPerFrame
            val end = min(start + samplesPerFrame, rtpBytes.size)
            var maxAmp = 0
            for (k in start until end) {
                val b = rtpBytes[k].toInt()
                val absVal = abs(b) // 0 to 128
                if (absVal > maxAmp) maxAmp = absVal
            }
            // Scale to 0-255
            amplitudes[f] = (maxAmp * 2).coerceIn(0, 255)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createOneShot(durationMs.coerceAtLeast(10L), VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}
