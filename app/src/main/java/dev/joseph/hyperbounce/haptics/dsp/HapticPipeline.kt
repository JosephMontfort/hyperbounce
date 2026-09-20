package dev.joseph.hyperbounce.haptics.dsp

import android.os.VibrationEffect

enum class PipelineType(
    val title: String,
    val subtitle: String,
    val description: String
) {
    SharpTransient(
        title = "Sharp Transient",
        subtitle = "Glass Click & Micro-Impact",
        description = "High-frequency spectral flux onset detection mapped to crisp hardware clicks (Effect 0). Eliminates motor ringing for an ultra-crisp Apple-style glass click."
    ),
    ResonantPunch(
        title = "Resonant Punch",
        subtitle = "Deep Bass & Kick Impact",
        description = "2nd-order bandpass isolating sub-bass drops and kick transients, mapped to hardware Heavy Clicks (Effect 5) and Thuds (Effect 3)."
    ),
    HarmonicBeatSnap(
        title = "Harmonic Beat-Snap",
        subtitle = "Rhythmic Pitch Tracking",
        description = "Multi-band musical cadence tracking. Downbeats trigger Heavy Clicks while rhythmic off-beats trigger subtle micro-ticks."
    ),
    GranularTexture(
        title = "Granular Texture",
        subtitle = "Scrubbing, Spinning & Friction",
        description = "Zero-crossing rate and micro-rhythm tracking mapped to rapid tactile micro-ticks (Effect 2) with zero motor drag."
    ),
    AdaptiveHybrid(
        title = "Adaptive Hybrid",
        subtitle = "Full Dynamic Spectrum",
        description = "Dual-band crossover splitting transient attacks and bass body. Automatically alternates between heavy bass impacts and glass-like snare clicks."
    )
}

data class HapticEvent(
    val timestampMs: Long,
    val effectId: Int = 0 // 0 = EFFECT_CLICK, 5 = EFFECT_HEAVY_CLICK, 2 = EFFECT_TICK, 3 = EFFECT_THUD
)

data class PipelineConfig(
    val resonantFrequencyHz: Float = 160.0f,
    val sensitivity: Float = 1.0f,
    val brakingStrength: Float = 0.85f,
    val maxDurationMs: Long = 30000L // 30 seconds default
)

data class SynthesizedHaptic(
    val pipelineType: PipelineType,
    val rtpRawBytes: ByteArray,        // Raw 24kHz signed 8-bit PCM (int8_t) ready to save as .bin
    val vibrationEffect: VibrationEffect, // Standard Android VibrationEffect for immediate UI preview
    val envelopePoints: FloatArray,   // Downsampled 0.0f - 1.0f amplitude envelope for live UI visualizer
    val events: List<HapticEvent>,    // Exact beat timestamps and hardware effect IDs
    val durationMs: Long,
    val peakCount: Int,
    val sampleRateHz: Int = 24000
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesizedHaptic) return false
        return pipelineType == other.pipelineType &&
                durationMs == other.durationMs &&
                peakCount == other.peakCount &&
                sampleRateHz == other.sampleRateHz &&
                rtpRawBytes.contentEquals(other.rtpRawBytes)
    }

    override fun hashCode(): Int {
        var result = pipelineType.hashCode()
        result = 31 * result + rtpRawBytes.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + peakCount
        result = 31 * result + sampleRateHz
        return result
    }
}
