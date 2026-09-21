package dev.joseph.hyperbounce.haptics.dsp

import android.os.Build
import android.os.VibrationEffect
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Authentic Xiaomi / AAC RichTap Audio-to-Haptic Synthesizer.
 *
 * (vendor firmware RTP binaries) and disassembly of libaachaptics.so.
 *
 * Key Architectural Findings:
 * 1. Output stream is strictly 24,000 Hz Signed 8-bit PCM (int8_t).
 * 2. Over 98% of all stock tactile effects are constructed from fixed, handcrafted
 *    hardware primitive grains (0: Click, 1: DoubleClick, 2: Tick, 3: Thud, 4: Pop, 5: HeavyClick,
 *    and the authentic 429-sample Melodic Grain bit-identical across Childhood, Carousel, Candy, and MiRemix).
 * 3. Event pacing enforces a physical refractory window (>= 108ms) ensuring 70-85% dead silence.
 *    This prevents LRA coil overheating, eliminates motor buzzing, and produces razor-sharp tactile definition.
 * 4. Dual-band crossover with dynamic transient ducking (50% attenuation of bass sustain during transient punch).
 */
object HapticSynthesizer {

    private const val OUTPUT_SAMPLE_RATE = 24000 // 24 kHz standard for Qualcomm/Xiaomi RTP

    // ---------------------------------------------------------------------------------------------
    // Exact Hardware Primitive Grains Extracted Bit-For-Bit from Xiaomi Vendor Firmware
    // ---------------------------------------------------------------------------------------------
    private const val XIAOMI_CLICK_B64 =
        "AgECAAECAgIDAwMEBQUFBwcHCQsMDxATFRgbHyMpLjQ8Q0tRVVtfY2VpamxubnBxcXJycnBxcW9vbm5tbGtqaWdmZmNiYWBeXFtZV1ZTUU9NSkdDQD04NC4oIRkRBvzw4dPIvrauqKGemZaUk5GQkJGQkZGTlJWXmpudn6Olp6mtr7K0t7q8v8LFx8rN0NLW2Nve4ebo6+/z9/sBBgwSGSAoLDE1ODo8PT4/Pz4+PDw5NzY0MS8sKSYjIB0aFxQQDQkHBAD9+vf18fDu6ujn5eTj4eHg4eHi5eXn6u/z+f8ECg4TFxseIiUnKi0wMzU4Oj0/QUNESEtNT1JVV1lcXmBjZGdpam1ub3FycnR0dXR0c3Jwbm1oZWBbU0xDOi0hFg0F/fby7Ofj39zZ1tPR0MzNysjHxsTDwsDAwL++vry8u7u6urm4uLe3t7e2uLe3t7m5u7y9vsDCxcjM0NXa3+Pn6ezv8fP09ff4+fv7+vz8/f3+/f3//v7+/v7+"

    private const val XIAOMI_DOUBLE_CLICK_B64 =
        "AAAAAAACAQECAwMEBAUFBgcICQsMDxATFRgbICMoLjU8QUZLUFNXWl1fYWJkZWZoaWpramttbG1tbm1ubm9vbm9wb3Bwb29vbm1ubm5ubGtraWlnZWViYFxaWFRPS0RAODAmHBAD9enf1s/Hwby3s6+sqqakoqCfnpybmZiXlpeWlZOTk5KSk5KRkpKRkJCQkJCQkJCSkZGTkpOUlpeZmpyeoKSmq7C0usDIz9bc4+jr8PL2+fr9/wADBAUGBwgICgoMCw0MDg4PDhARERISExMTFBUWFhYXGBcYGRsbGx0eHyAhIiQmKCosLzM2Oj9DSU5SV1pdYGNlZmloa2tsbG5tb29ub29ub29vb3Bubm9ubm5tbG1sa2tra2ppaGdmZWVkYmBfXltZVlNQTUlDQDkyKiIXDAH37ubf2dTPzMjEwsC+vbu6uLi3trW1tbOzsrOzsrKxsrKysrGxsrGys7KysrSztLS1tbe2tre4uru9vb/AwsXHys7R1tvf4ubo6+7x8fP19/j3+vv7+/z9/P79/v7+////////AAEAAAAAAAAAAQABAgEBAQEAAAABAQABAQABAAEBAQE="

    private const val XIAOMI_TICK_B64 =
        "AQEBAQABAQIBAwIEBAUFBQcHCQoNDQ8RExcZHSImKjE4P0VLUFZZW11hYmNkZGNlZGNjYWBhXlxbWVdWVFFQTkxJR0NCQD06NzYxLysoJCAaFREMBv/37uXbz8O5r6egnJiUkpCPj5CQkZSWmJyfoqarrbO2vMDEyM3R1tvg5Ojt8PX4/AAEBgkMDQ8SExUWFhUVFBIRDw8MCwgGBAL//Pn28/Ds6ebi3dnV0c3JxcG9ubWyrKmmop+cmZWUkY+OjYyNjYyQkJOXnqSpsrzH0dvi6u/3/AEFCAwQEhYYGhweICMlJikrLC4vMTEzNDU3ODk6PDs9Pj4+Pj4+PT09Ozo4NjQxLyomIR4aFhQRDw4LCgoIBwYFBQMEAwMDAgICAgEAAQIBAQABAQEAAQABAQ=="

    private const val XIAOMI_THUD_B64 =
        "AAABAQEBAgICAwMEBAUGBwkLDQ8SFRgcICUqLzQ6QEVLUFZbX2Nnamxub3Bvb25samdkYF1ZVFBLRkE8NzIsJiEbFA4HAPrz6+Pb08vCurKqo5yWkIuHhIKBgYGDhomOk5mfpq62v8jS2+Xw+gMNGCIsNj9IUFhfZmxxdXh6fHx8e3l2c29qZF5YUUlBOTEoIBcOBf306+Pa0snBurKrpJ6Yk46KhoSCgYGChIaKjpOYn6Wss7rCydDX3uXr8vj9AgcMERYaHyMnKy8zNzs+QkVISk1PUFFSUlFQTktHQz44MiwlHxkTDgoGAwEA//7+/v7+//8AAAAAAAAAAAA="

    private const val XIAOMI_POP_B64 =
        "AAEAAQEBAgEDAwMFBQUFBgkJCgwNDxETFRcZGhwdHR8gISIjJCUmJygpKSorLC0uMDAxMjM0NTY3ODs7PDw/P0BBQkRFRkdISkxLTk9RUVNUVlZYWVpcXV5fYWJiY2RmZ2hpaWpsbG5ubm9vcXBycnNzcnNyc3NycnJxcG9ubWxqZ2NiX1xbWVdWVFJRUE5MTElHRkREQUA+PTs4NjUzLy4rKicmIiEdGxgXFBIQDAkHBAH//Pj29PHu7Orn5OHf3drX1NLPzcvIxsPCwL68urm3tLKwr62sq6mopqajo6GhoJ6fnp2dnZ2cnp6enqCfoKKjo6SlpqaoqKqrrK+vsrO0tbe6u72+wMLExsnLzc/S1dfZ3N7i5Ofq7O/y9Pf7/f8DBQgKDRATFxkbHyIkJigrLjEzNDg6PD9AQkRFSElLTU9RUlRVVVhYWltbXF1eX19fYGBgYF9fX15eXlxcW1paWVhXVlNTUVFOTkxJSUZEREJAPTw6ODUzMS4sKSckIR8cGRcTEQ4LCQYDAP78+PXy7+zr5+Xi393a19XS0M3KyMbDwcC+vLm4t7SzsK6urKqpp6ajo6GgoJ+cnJuamZmYl5aVlJOSkpGRkZGQkpGTk5OUlZaXmJmZm5ydnqCio6Slp6iqrK6wsrS1trm6vL/AwcTFyMrLzM/R09XW2drd3+Di5Obo6evs7vDy9Pb3+fr8/f//AQMFBQcJCQoLDA0PEBISExUVFhgaGxseICIkJigpKyssLS0vLi8wMC8vMC8vLy8vLy4vLi4tLSwtKywrKyorKSkoJycmJiYkJSMkIyIhISEgHh8eHh0dHBscGxoZGBkYGBcYFxYVFRQUExMSEhIRERAREA8PDg8PDg0MDAwLCgsJCggIBwUGBAQEAgMCAgIBAQEB"

    private const val XIAOMI_HEAVY_CLICK_B64 =
        "AAAAAAAAAQEBAgIDAwQFBgcICQsMDhETFhkdICUqMDU6PT9AQEBAPz06ODQxLSkkIBwXEgwHAvz27+fg2tLLw7y0rKObkYuHhISEh4uQl5+nsLvG0t7r+gIMFh8oMTpCSlFZX2RqbnJ2eHp7e3t5dnNvamReV09GPjUrIRgOBPru49jNw7qxp6CalI+LiYiIio6Sl5ugpauxt73FzNPb4+vz/QIKERggJSwyOD1BRUhLTE1NTEpGQTw2MCsmIh4aFxMQDAkGAwD//Pr39PLw7evq6Obl5OPj4+Pk5Obo6u3v8fP19vj5+vv7/P39/v7+////"

    // Handcrafted 429-sample Melodic Grain found identical across Childhood, Carousel, Candy, AcousticGuitar, and MiRemix
    private const val XIAOMI_MELODIC_CLICK_429_B64 =
        "LTI3Oz9CRUhKTE1PUFJSU1RVVlZXV1dYWFlZWVlZWVlZWVlZWVlZWVlZWVlYV1dXVlVUU1JRT01LSUZDQDw3MiwlHRULAPbt5N3X0czIxMG+u7m3tbOysbCurq2srKuqqampqKinp6enp6ampqampqampqampqenp6ipqaqsra6wsrW3ur7CxsvS2N/k6O3w8/b5+/3+AAACAwQFBgcHCAkJCQoLCwwMDA0NDg4ODg4PDxAQEBAQEBARERERERERERAQEBAQDw4ODQwLCQgGBAIBAP/+/vz8+/r5+fn4+Pf39/b29fX19PT08/Py8vLy8fHw8PDv7+/u7e3t7Ozr6+rp6Ojn5ubk4+Lg39za2NXTz8vGwr66uLWzsa+urKyqqamoqKenp6enp6ampqenp6enp6enp6ioqampqqqqq6ysrK2ur7CwsrO1tri6vb/CxsrO09nf5/D5AQkPFRofJCcqLS8xMzU2Nzg6Ojs8PDw8PT0+Pj4+Pj4+Pj4+Pj4+Pj4+Pj09PTw8PDw7Ozo6OTg3NjU0MjAuLConJCAcGBUTEA4MCgkHBwUEBAMC"

    private val CLICK_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_CLICK_B64, Base64.DEFAULT) }
    private val DOUBLE_CLICK_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_DOUBLE_CLICK_B64, Base64.DEFAULT) }
    private val TICK_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_TICK_B64, Base64.DEFAULT) }
    private val THUD_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_THUD_B64, Base64.DEFAULT) }
    private val POP_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_POP_B64, Base64.DEFAULT) }
    private val HEAVY_CLICK_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_HEAVY_CLICK_B64, Base64.DEFAULT) }
    private val MELODIC_CLICK_429_GRAIN: ByteArray by lazy { Base64.decode(XIAOMI_MELODIC_CLICK_429_B64, Base64.DEFAULT) }

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
        val input = if (config.maxDurationMs > 0L) {
            val maxSamples = ((config.maxDurationMs * audio.sampleRate) / 1000L).toInt()
            if (audio.samples.size > maxSamples) {
                audio.samples.copyOf(maxSamples)
            } else {
                audio.samples
            }
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
            PipelineType.XiaomiMelodicCrisp -> {
                processXiaomiMelodicCrisp(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.XiaomiElectronicEDM -> {
                processXiaomiElectronicEDM(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.XiaomiPlayfulPop -> {
                processXiaomiPlayfulPop(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.XiaomiAcousticPluck -> {
                processXiaomiAcousticPluck(input, audio.sampleRate, rtpFloats, events, config)
            }
            PipelineType.XiaomiAdaptiveSymphony -> {
                processXiaomiAdaptiveSymphony(input, audio.sampleRate, rtpFloats, events, config)
            }
        }

        // Convert rtpFloats (-1.0f to 1.0f) to signed 8-bit PCM byte array (-128 to 127)
        // Hard-cap at ±121 to match POCO hardware peak and eliminate chassis buzzing
        val maxAmp = (121.0f / 127.0f)
        val rtpRawBytes = ByteArray(rtpFloats.size)
        for (i in rtpFloats.indices) {
            val clamped = rtpFloats[i].coerceIn(-maxAmp, maxAmp)
            val s8 = (clamped * 127.0f).roundToInt().coerceIn(-121, 121)
            rtpRawBytes[i] = s8.toByte()
        }

        // Downsampled envelope points for live UI visualizer (150 display bars)
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

    // ---------------------------------------------------------------------------------------------
    // Direct Form II Transposed Biquad IIR Filter
    // ---------------------------------------------------------------------------------------------
    private class BiquadFilter(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float
    ) {
        private var z1 = 0f
        private var z2 = 0f

        fun process(x: Float): Float {
            val out = b0 * x + z1
            z1 = b1 * x - a1 * out + z2
            z2 = b2 * x - a2 * out
            return out
        }

        companion object {
            fun createLowpass(fc: Float, sampleRate: Float, q: Float = 0.7071f): BiquadFilter {
                val w0 = (2.0 * PI * fc / sampleRate).toFloat()
                val cosW0 = cos(w0)
                val sinW0 = sin(w0)
                val alpha = sinW0 / (2f * q)
                val b0 = (1f - cosW0) / 2f
                val b1 = 1f - cosW0
                val b2 = (1f - cosW0) / 2f
                val a0 = 1f + alpha
                val a1 = -2f * cosW0
                val a2 = 1f - alpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun createBandpass(fc: Float, bwOctaves: Float, sampleRate: Float): BiquadFilter {
                val w0 = (2.0 * PI * fc / sampleRate).toFloat()
                val cosW0 = cos(w0)
                val sinW0 = sin(w0)
                val sinhVal = sinh(ln(2.0) / 2.0 * bwOctaves * w0 / sinW0).toFloat()
                val alpha = sinW0 * sinhVal
                val b0 = alpha
                val b1 = 0f
                val b2 = -alpha
                val a0 = 1f + alpha
                val a1 = -2f * cosW0
                val a2 = 1f - alpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Authentic Grain Stamping & Resonant Sustain Engine
    // ---------------------------------------------------------------------------------------------
    private fun stampGrain(
        output: FloatArray,
        startIndex: Int,
        grain: ByteArray,
        gain: Float = 1.0f,
        duckExisting: Boolean = true
    ) {
        val end = min(output.size, startIndex + grain.size)
        if (startIndex >= end) return

        // 50% dynamic transient ducking of any underlying bass sustain
        if (duckExisting) {
            for (i in startIndex until end) {
                output[i] *= 0.5f
            }
        }

        // Normalize grain relative to its own native peak so gain precisely sets the output peak
        var maxAbs = 0
        for (b in grain) {
            val a = abs(b.toInt())
            if (a > maxAbs) maxAbs = a
        }
        val normFactor = if (maxAbs > 0) (gain / maxAbs) else (gain / 127.0f)

        for (k in 0 until (end - startIndex)) {
            val s8 = grain[k].toInt()
            val normalized = s8 * normFactor
            output[startIndex + k] = (output[startIndex + k] + normalized).coerceIn(-1.0f, 1.0f)
        }
    }

    private fun stampResonantBass(
        output: FloatArray,
        startIndex: Int,
        durationMs: Int = 100,
        freqHz: Float = 171.4f,
        gain: Float = 1.0f
    ) {
        val numSamples = ((durationMs * OUTPUT_SAMPLE_RATE) / 1000).coerceAtMost(output.size - startIndex)
        if (numSamples <= 0) return

        // Authentic POCO Dual-Frequency Resonance Architecture:
        // 1. Attack & Hold: resonant frequency (freqHz, 160-171.4Hz) builds physical body mass displacement without buzz
        // 2. Terminal Strike: 233.0Hz high-acceleration tactile snap phase-locked to exactly 121 peak (targetPeak)
        val strikeFreqHz = 233.0f
        val attackSamples = ((2.0f * OUTPUT_SAMPLE_RATE / freqHz).toInt()).coerceAtMost(numSamples / 3)
        val strikeSamples = ((2.0f * OUTPUT_SAMPLE_RATE / strikeFreqHz).toInt()).coerceAtMost(numSamples / 3)

        val targetPeak = (121.0f / 127.0f)
        val attackGain = 0.85f * targetPeak * gain
        val holdGain = 0.22f * targetPeak * gain
        val strikeGain = targetPeak * gain

        for (s in 0 until numSamples) {
            val env = when {
                s < attackSamples -> {
                    val t = s.toFloat() / OUTPUT_SAMPLE_RATE
                    val sine = sin(2.0 * PI * freqHz * t).toFloat()
                    val e = (0.5f - 0.5f * cos(PI * s / attackSamples)).toFloat() * attackGain
                    sine * e
                }
                s >= numSamples - strikeSamples -> {
                    val rem = s - (numSamples - strikeSamples)
                    val e = (0.5f - 0.5f * cos(2.0 * PI * rem / strikeSamples)).toFloat() * strikeGain
                    // Phase-align 233Hz sine wave so it peaks (+1.0) precisely at the center of the Hanning strike window (tRel = 0)
                    val tRel = (rem - strikeSamples / 2.0f) / OUTPUT_SAMPLE_RATE
                    val sine = cos(2.0 * PI * strikeFreqHz * tRel).toFloat()
                    sine * e
                }
                else -> {
                    val t = s.toFloat() / OUTPUT_SAMPLE_RATE
                    val sine = sin(2.0 * PI * freqHz * t).toFloat()
                    sine * holdGain
                }
            }
            val sample = env.coerceIn(-targetPeak, targetPeak)
            output[startIndex + s] = (output[startIndex + s] + sample).coerceIn(-1.0f, 1.0f)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pipeline Implementations
    // ---------------------------------------------------------------------------------------------

    /**
     * Pipeline 1: Xiaomi Melodic Crisp (Carousel & Childhood Style)
     * Stamps the authentic 429-sample hardware grain (17.9ms, 90pk) on melodic note onsets.
     */
    private fun processXiaomiMelodicCrisp(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val hopSamples = (inRate * 0.005f).roundToInt().coerceAtLeast(1) // 5ms hop
        val frameSamples = (inRate * 0.010f).roundToInt()               // 10ms frame
        val bp = BiquadFilter.createBandpass(1400f, 1.2f, inRate.toFloat())
        val lp = BiquadFilter.createLowpass(180f, inRate.toFloat())

        val numFrames = (input.size - frameSamples) / hopSamples
        if (numFrames <= 0) return

        val bpEnergy = FloatArray(numFrames)
        val lpEnergy = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            val start = f * hopSamples
            var sumBp = 0f
            var sumLp = 0f
            for (j in 0 until frameSamples) {
                val s = input[start + j]
                val b = bp.process(s)
                val l = lp.process(s)
                sumBp += b * b
                sumLp += l * l
            }
            bpEnergy[f] = sqrt(sumBp / frameSamples)
            lpEnergy[f] = sqrt(sumLp / frameSamples)
        }

        val minIntervalMs = 115L
        var lastOnsetMs = -minIntervalMs
        var lastBassMs = -600L
        val win = 30 // 150ms moving window

        for (f in win until (numFrames - win)) {
            val currBp = bpEnergy[f]
            val currLp = lpEnergy[f]
            val currTimeMs = (f * 5L)

            // Local moving average
            var localSum = 0f
            for (k in (f - win) until (f + win)) {
                localSum += bpEnergy[k]
            }
            val localMean = localSum / (win * 2)

            val isLocalPeak = currBp >= bpEnergy[f - 1] &&
                    currBp >= bpEnergy[f + 1] &&
                    currBp >= bpEnergy[f - 2] &&
                    currBp >= bpEnergy[f + 2]

            val threshold = localMean * (1.18f / config.sensitivity.coerceIn(0.25f, 2.5f))

            if (isLocalPeak && currBp > threshold && currBp > 0.012f) {
                if (currTimeMs - lastOnsetMs >= minIntervalMs) {
                    val outIdx = ((currTimeMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()

                    // Distinguish true downbeats/phrase accents (>= 450ms apart) vs crisp melodic notes
                    val hasBassEnergy = (currLp > currBp * 1.25f) && (currLp > 0.024f)
                    val isPhraseAccent = (currTimeMs - lastOnsetMs >= 280L) && (currBp > threshold * 1.35f) && (currBp > 0.028f)
                    val isDownbeat = (hasBassEnergy || isPhraseAccent) && (currTimeMs - lastBassMs >= 450L)

                    lastOnsetMs = currTimeMs

                    if (isDownbeat) {
                        lastBassMs = currTimeMs
                        // POCO Tier 1: Resonant bass downbeat impact (100ms at 171.4Hz, POCO ASR envelope peaking at 121)
                        events.add(HapticEvent(timestampMs = currTimeMs, effectId = 5)) // EFFECT_HEAVY_CLICK
                        stampResonantBass(output, outIdx, durationMs = 100, freqHz = config.resonantFrequencyHz, gain = 1.0f)
                    } else {
                        // POCO Tier 2 / Stock Melodic: Crisp note snap (peak 85-108)
                        events.add(HapticEvent(timestampMs = currTimeMs, effectId = 0)) // EFFECT_CLICK
                        val gain = (currBp * 4.8f * config.sensitivity).coerceIn(0.70f, 0.85f)
                        stampGrain(output, outIdx, MELODIC_CLICK_429_GRAIN, gain = gain, duckExisting = true)
                    }
                }
            }
        }
    }

    /**
     * Pipeline 2: Xiaomi Electronic EDM (MiRemix & Club Style)
     * High-energy punch: 168ms 160Hz bass sustain drops + sharp transient snaps with ducking.
     */
    private fun processXiaomiElectronicEDM(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val hopSamples = (inRate * 0.005f).roundToInt().coerceAtLeast(1)
        val frameSamples = (inRate * 0.010f).roundToInt()
        val bp = BiquadFilter.createBandpass(1800f, 1.0f, inRate.toFloat())
        val lp = BiquadFilter.createLowpass(160f, inRate.toFloat())

        val numFrames = (input.size - frameSamples) / hopSamples
        if (numFrames <= 0) return

        val bpEnergy = FloatArray(numFrames)
        val lpEnergy = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            val start = f * hopSamples
            var sumBp = 0f
            var sumLp = 0f
            for (j in 0 until frameSamples) {
                val s = input[start + j]
                val b = bp.process(s)
                val l = lp.process(s)
                sumBp += b * b
                sumLp += l * l
            }
            bpEnergy[f] = sqrt(sumBp / frameSamples)
            lpEnergy[f] = sqrt(sumLp / frameSamples)
        }

        val minIntervalMs = 125L
        var lastOnsetMs = -minIntervalMs
        var lastBassMs = -500L
        val win = 25

        for (f in win until (numFrames - win)) {
            val currBp = bpEnergy[f]
            val currLp = lpEnergy[f]
            val currTimeMs = (f * 5L)

            val isBassPeak = currLp >= lpEnergy[f - 1] && currLp >= lpEnergy[f + 1] && currLp >= lpEnergy[f - 2]
            val isHighPeak = currBp >= bpEnergy[f - 1] && currBp >= bpEnergy[f + 1] && currBp >= bpEnergy[f - 2]

            var localSumLp = 0f
            var localSumBp = 0f
            for (k in (f - win) until (f + win)) {
                localSumLp += lpEnergy[k]
                localSumBp += bpEnergy[k]
            }
            val meanLp = localSumLp / (win * 2)
            val meanBp = localSumBp / (win * 2)

            val bassTrigger = isBassPeak && currLp > meanLp * (1.18f / config.sensitivity.coerceIn(0.25f, 2.5f)) && currLp > 0.016f && (currTimeMs - lastBassMs >= 350L)
            val highTrigger = isHighPeak && currBp > meanBp * (1.25f / config.sensitivity.coerceIn(0.25f, 2.5f)) && currBp > 0.018f

            if ((bassTrigger || highTrigger) && (currTimeMs - lastOnsetMs >= minIntervalMs)) {
                lastOnsetMs = currTimeMs
                val outIdx = ((currTimeMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()

                if (bassTrigger) {
                    lastBassMs = currTimeMs
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 5)) // EFFECT_HEAVY_CLICK
                    stampResonantBass(output, outIdx, durationMs = 110, freqHz = config.resonantFrequencyHz, gain = 1.0f)
                } else {
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 0)) // EFFECT_CLICK
                    stampGrain(output, outIdx, HEAVY_CLICK_GRAIN, gain = 0.90f, duckExisting = true)
                }
            }
        }
    }

    /**
     * Pipeline 3: Xiaomi Playful Pop (Candy & Bubble Style)
     * Utilizes Xiaomi's 681-sample Pop grain and Melodic click for bouncy water droplets and clicks.
     */
    private fun processXiaomiPlayfulPop(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val hopSamples = (inRate * 0.005f).roundToInt().coerceAtLeast(1)
        val frameSamples = (inRate * 0.010f).roundToInt()
        val bp = BiquadFilter.createBandpass(1200f, 1.4f, inRate.toFloat())

        val numFrames = (input.size - frameSamples) / hopSamples
        if (numFrames <= 0) return

        val energy = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * hopSamples
            var sum = 0f
            for (j in 0 until frameSamples) {
                val b = bp.process(input[start + j])
                sum += b * b
            }
            energy[f] = sqrt(sum / frameSamples)
        }

        val minIntervalMs = 110L
        var lastOnsetMs = -minIntervalMs
        val win = 25

        for (f in win until (numFrames - win)) {
            val curr = energy[f]
            val currTimeMs = (f * 5L)

            val isPeak = curr >= energy[f - 1] && curr >= energy[f + 1] && curr >= energy[f - 2] && curr >= energy[f + 2]
            var sum = 0f
            for (k in (f - win) until (f + win)) sum += energy[k]
            val mean = sum / (win * 2)

            val threshold = mean * (1.35f / config.sensitivity.coerceIn(0.4f, 2.5f))

            if (isPeak && curr > threshold && curr > 0.015f && (currTimeMs - lastOnsetMs >= minIntervalMs)) {
                lastOnsetMs = currTimeMs
                val outIdx = ((currTimeMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()

                // Alternate between authentic Pop and Melodic Click
                val usePop = (f % 2 == 0)
                if (usePop) {
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 4)) // EFFECT_POP
                    stampGrain(output, outIdx, POP_GRAIN, gain = 0.95f, duckExisting = true)
                } else {
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 0)) // EFFECT_CLICK
                    stampGrain(output, outIdx, MELODIC_CLICK_429_GRAIN, gain = 0.85f, duckExisting = true)
                }
            }
        }
    }

    /**
     * Pipeline 4: Xiaomi Acoustic Pluck (Guitar & Celesta Style)
     * Isolates delicate string plucks and acoustic transient attacks via 2200Hz high-band tracking.
     */
    private fun processXiaomiAcousticPluck(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val hopSamples = (inRate * 0.005f).roundToInt().coerceAtLeast(1)
        val frameSamples = (inRate * 0.010f).roundToInt()
        val bp = BiquadFilter.createBandpass(2200f, 1.0f, inRate.toFloat())

        val numFrames = (input.size - frameSamples) / hopSamples
        if (numFrames <= 0) return

        val energy = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * hopSamples
            var sum = 0f
            for (j in 0 until frameSamples) {
                val b = bp.process(input[start + j])
                sum += b * b
            }
            energy[f] = sqrt(sum / frameSamples)
        }

        val minIntervalMs = 110L
        var lastOnsetMs = -minIntervalMs
        val win = 25

        for (f in win until (numFrames - win)) {
            val curr = energy[f]
            val currTimeMs = (f * 5L)

            val isPeak = curr >= energy[f - 1] && curr >= energy[f + 1] && curr >= energy[f - 2]
            var sum = 0f
            for (k in (f - win) until (f + win)) sum += energy[k]
            val mean = sum / (win * 2)

            val threshold = mean * (1.3f / config.sensitivity.coerceIn(0.4f, 2.5f))

            if (isPeak && curr > threshold && curr > 0.014f && (currTimeMs - lastOnsetMs >= minIntervalMs)) {
                lastOnsetMs = currTimeMs
                val outIdx = ((currTimeMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()

                events.add(HapticEvent(timestampMs = currTimeMs, effectId = 0)) // EFFECT_CLICK
                val gain = (curr * 4.0f * config.sensitivity).coerceIn(0.55f, 0.95f)
                stampGrain(output, outIdx, CLICK_GRAIN, gain = gain, duckExisting = true)
            }
        }
    }

    /**
     * Pipeline 5: Xiaomi Adaptive Symphony (Full Dynamic Multiband Crossover)
     * Dynamic intelligent crossover assigning kick, snare, vocal consonants, and melody.
     */
    private fun processXiaomiAdaptiveSymphony(
        input: FloatArray,
        inRate: Int,
        output: FloatArray,
        events: MutableList<HapticEvent>,
        config: PipelineConfig
    ) {
        val hopSamples = (inRate * 0.005f).roundToInt().coerceAtLeast(1)
        val frameSamples = (inRate * 0.010f).roundToInt()
        val bp = BiquadFilter.createBandpass(1500f, 1.2f, inRate.toFloat())
        val lp = BiquadFilter.createLowpass(200f, inRate.toFloat())

        val numFrames = (input.size - frameSamples) / hopSamples
        if (numFrames <= 0) return

        val bpEnergy = FloatArray(numFrames)
        val lpEnergy = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            val start = f * hopSamples
            var sumBp = 0f
            var sumLp = 0f
            for (j in 0 until frameSamples) {
                val s = input[start + j]
                val b = bp.process(s)
                val l = lp.process(s)
                sumBp += b * b
                sumLp += l * l
            }
            bpEnergy[f] = sqrt(sumBp / frameSamples)
            lpEnergy[f] = sqrt(sumLp / frameSamples)
        }

        val minIntervalMs = 115L
        var lastOnsetMs = -minIntervalMs
        var lastBassMs = -500L
        val win = 25

        for (f in win until (numFrames - win)) {
            val currBp = bpEnergy[f]
            val currLp = lpEnergy[f]
            val totalEnergy = currBp + currLp
            val currTimeMs = (f * 5L)

            var sumTotal = 0f
            for (k in (f - win) until (f + win)) {
                sumTotal += (bpEnergy[k] + lpEnergy[k])
            }
            val meanTotal = sumTotal / (win * 2)

            val isPeak = totalEnergy >= (bpEnergy[f - 1] + lpEnergy[f - 1]) &&
                    totalEnergy >= (bpEnergy[f + 1] + lpEnergy[f + 1]) &&
                    totalEnergy >= (bpEnergy[f - 2] + lpEnergy[f - 2])

            val threshold = meanTotal * (1.35f / config.sensitivity.coerceIn(0.4f, 2.5f))

            if (isPeak && totalEnergy > threshold && totalEnergy > 0.02f && (currTimeMs - lastOnsetMs >= minIntervalMs)) {
                lastOnsetMs = currTimeMs
                val outIdx = ((currTimeMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()

                if ((currLp > currBp * 1.3f || currLp > 0.035f) && (currTimeMs - lastBassMs >= 450L)) {
                    lastBassMs = currTimeMs
                    // Deep low-end drop
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 5)) // EFFECT_HEAVY_CLICK
                    stampResonantBass(output, outIdx, durationMs = 100, freqHz = config.resonantFrequencyHz, gain = 1.0f)
                } else if (currBp > currLp * 1.5f) {
                    // Crisp treble click
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 0)) // EFFECT_CLICK
                    stampGrain(output, outIdx, MELODIC_CLICK_429_GRAIN, gain = 0.71f, duckExisting = true)
                } else {
                    // Balanced punch
                    events.add(HapticEvent(timestampMs = currTimeMs, effectId = 1)) // EFFECT_DOUBLE_CLICK
                    stampGrain(output, outIdx, DOUBLE_CLICK_GRAIN, gain = 0.75f, duckExisting = true)
                }
            }
        }
    }

    /**
     * Generates standard Android VibrationEffect with discrete pulses and dead silence between beats.
     */
    fun buildAndroidVibrationEffect(events: List<HapticEvent>, durationMs: Long): VibrationEffect {
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
            val isHeavy = (event.effectId == 5)
            val pulseMs = if (isHeavy) 26L else 16L
            timings.add(pulseMs)
            val amp = when (event.effectId) {
                5 -> 240
                3 -> 190
                4 -> 160
                2 -> 90
                else -> 160
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
