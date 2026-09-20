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
        description = "High-frequency onset detection + active 180° braking pulse. Eliminates motor ringing for an ultra-crisp Apple-style glass click."
    ),
    ResonantPunch(
        title = "Resonant Punch",
        subtitle = "Deep Bass & Kick Impact",
        description = "2nd-order bandpass centered around LRA resonance (~160Hz) with logarithmic envelope compression. Ideal for drum kicks and heavy alerts."
    ),
    HarmonicBeatSnap(
        title = "Harmonic Beat-Snap",
        subtitle = "Rhythmic Pitch Tracking",
        description = "Sub-band energy tracking with heterodyne frequency shift to 160Hz. Perfectly follows musical rhythms, ringtones, and melodic chimes."
    ),
    GranularTexture(
        title = "Granular Texture",
        subtitle = "Scrubbing, Spinning & Friction",
        description = "Zero-crossing rate tracking with micro-staccato pulse bursts. Creates physical tactile friction for scrolling, slider scrubbers, and rotary dials."
    ),
    AdaptiveHybrid(
        title = "Adaptive Hybrid",
        subtitle = "Full Dynamic Spectrum",
        description = "Dual-band spectral crossover splitting transient attack and low-end body with Dynamic Range Compression (DRC). Rich cinematic feel."
    )
}

data class PipelineConfig(
    val resonantFrequencyHz: Float = 160.0f,
    val sensitivity: Float = 1.0f,
    val brakingStrength: Float = 0.85f,
    val maxDurationMs: Long = 10000L
)

data class SynthesizedHaptic(
    val pipelineType: PipelineType,
    val rtpRawBytes: ByteArray,        // Raw 24kHz signed 8-bit PCM (int8_t) ready to save as .bin
    val vibrationEffect: VibrationEffect, // Standard Android VibrationEffect for immediate UI preview
    val envelopePoints: FloatArray,   // Downsampled 0.0f - 1.0f amplitude envelope for live UI visualizer
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
