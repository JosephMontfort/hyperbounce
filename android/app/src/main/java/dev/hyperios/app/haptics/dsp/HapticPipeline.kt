package dev.hyperios.app.haptics.dsp

import android.os.VibrationEffect

enum class PipelineType(
    val title: String,
    val subtitle: String,
    val description: String
) {
    XiaomiMelodicCrisp(
        title = "Melodic Crisp",
        subtitle = "Melodic Note Onsets",
        description = "Authentic hardware grain stamped precisely on melodic note onsets, paired with resonant bass downbeats."
    ),
    XiaomiElectronicEDM(
        title = "Electronic EDM",
        subtitle = "Dual-Band Crossover",
        description = "High-energy dual-band crossover. 172Hz resonant bass drops combined with sharp high-frequency transient snaps. Features dynamic transient ducking."
    ),
    XiaomiPlayfulPop(
        title = "Playful Pop",
        subtitle = "Candy & Bubble Dynamics",
        description = "Playful tactile pops and droplets utilizing crisp clicks. Bouncy, vibrant, and perfectly separated with 110ms spring recovery."
    ),
    XiaomiAcousticPluck(
        title = "Acoustic Pluck",
        subtitle = "Delicate String Transients",
        description = "Isolates delicate string plucks and acoustic attack transients via spectral flux. Triggers rapid hardware micro-clicks without motor dragging."
    ),
    XiaomiAdaptiveSymphony(
        title = "Adaptive Symphony",
        subtitle = "Full-Band Dynamic Crossover",
        description = "Adaptive multiband analyzer that dynamically assigns kick drums, snares, vocals, and synth lines to exact hardware primitive grains."
    );

    companion object {
        fun fromNameOrDefault(name: String): PipelineType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: when (name) {
                    "SharpTransient" -> XiaomiMelodicCrisp
                    "ResonantPunch" -> XiaomiElectronicEDM
                    "HarmonicBeatSnap" -> XiaomiAdaptiveSymphony
                    "GranularTexture" -> XiaomiAcousticPluck
                    "AdaptiveHybrid" -> XiaomiAdaptiveSymphony
                    else -> XiaomiMelodicCrisp
                }
        }
    }
}

data class HapticEvent(
    val timestampMs: Long,
    val effectId: Int = 0 // 0 = EFFECT_CLICK, 5 = EFFECT_HEAVY_CLICK, 2 = EFFECT_TICK, 3 = EFFECT_THUD
)

data class PipelineConfig(
    val resonantFrequencyHz: Float = 172.0f,
    val sensitivity: Float = 1.0f,
    val brakingStrength: Float = 0.85f,
    val maxDurationMs: Long = 0L // 0L means full unconstrained duration
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
