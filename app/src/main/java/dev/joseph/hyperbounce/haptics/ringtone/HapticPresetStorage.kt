package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.net.Uri
import dev.joseph.hyperbounce.haptics.dsp.AudioDecoder
import dev.joseph.hyperbounce.haptics.dsp.HapticEvent
import dev.joseph.hyperbounce.haptics.dsp.HapticSynthesizer
import dev.joseph.hyperbounce.haptics.dsp.PipelineType
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic
import dev.joseph.hyperbounce.haptics.player.HapticIntensityMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SavedPipelineData(
    val pipelineType: PipelineType,
    val durationMs: Long,
    val peakCount: Int,
    val envelopePoints: FloatArray,
    val events: List<HapticEvent>
)

data class SavedHapticPreset(
    val id: String,
    val title: String,
    val audioFileName: String,
    val durationMs: Long,
    val trimStartMs: Int,
    val trimEndMs: Int,
    val selectedPipelineIndex: Int,
    val intensityMode: HapticIntensityMode,
    val createdAt: Long,
    val pipelines: List<SavedPipelineData>
)

data class LoadedPresetData(
    val audioUri: Uri,
    val decodedAudio: AudioDecoder.DecodedAudio,
    val synthesizedHaptics: List<SynthesizedHaptic>
)

object HapticPresetStorage {

    private fun getPresetsDir(context: Context): File {
        val dir = File(context.filesDir, "presets")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun savePreset(
        context: Context,
        title: String,
        audioFileName: String,
        decodedAudio: AudioDecoder.DecodedAudio,
        synthesizedHaptics: List<SynthesizedHaptic>,
        selectedPipelineIndex: Int,
        trimStartMs: Int,
        trimEndMs: Int,
        intensityMode: HapticIntensityMode
    ): SavedHapticPreset {
        val dir = getPresetsDir(context)
        val id = System.currentTimeMillis().toString()

        // 1. Save standard 16-bit PCM WAV to disk
        val wavBytes = RingtoneManagerHelper.cropAndEncodeWav(
            decodedAudio,
            0,
            decodedAudio.durationMs.toInt()
        )
        val wavFile = File(dir, "preset_${id}.wav")
        wavFile.writeBytes(wavBytes)

        // 2. Save raw 24kHz RTP binaries for each pipeline
        synthesizedHaptics.forEachIndexed { index, haptic ->
            val binFile = File(dir, "preset_${id}_pipe${index}.bin")
            binFile.writeBytes(haptic.rtpRawBytes)
        }

        // 3. Build JSON representation
        val pipelinesData = synthesizedHaptics.map { haptic ->
            SavedPipelineData(
                pipelineType = haptic.pipelineType,
                durationMs = haptic.durationMs,
                peakCount = haptic.peakCount,
                envelopePoints = haptic.envelopePoints,
                events = haptic.events
            )
        }

        val json = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("audioFileName", audioFileName)
            put("durationMs", decodedAudio.durationMs)
            put("trimStartMs", trimStartMs)
            put("trimEndMs", trimEndMs)
            put("selectedPipelineIndex", selectedPipelineIndex)
            put("intensityMode", intensityMode.name)
            put("createdAt", System.currentTimeMillis())

            val pipesArray = JSONArray()
            synthesizedHaptics.forEach { haptic ->
                val pObj = JSONObject().apply {
                    put("pipelineType", haptic.pipelineType.name)
                    put("durationMs", haptic.durationMs)
                    put("peakCount", haptic.peakCount)

                    val envArr = JSONArray()
                    haptic.envelopePoints.forEach { envArr.put(it.toDouble()) }
                    put("envelopePoints", envArr)

                    val evArr = JSONArray()
                    haptic.events.forEach { ev ->
                        val evObj = JSONObject().apply {
                            put("timestampMs", ev.timestampMs)
                            put("effectId", ev.effectId)
                        }
                        evArr.put(evObj)
                    }
                    put("events", evArr)
                }
                pipesArray.put(pObj)
            }
            put("pipelines", pipesArray)
        }

        val jsonFile = File(dir, "preset_${id}.json")
        jsonFile.writeText(json.toString())

        return SavedHapticPreset(
            id = id,
            title = title,
            audioFileName = audioFileName,
            durationMs = decodedAudio.durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            selectedPipelineIndex = selectedPipelineIndex,
            intensityMode = intensityMode,
            createdAt = System.currentTimeMillis(),
            pipelines = pipelinesData
        )
    }

    fun listPresets(context: Context): List<SavedHapticPreset> {
        val dir = getPresetsDir(context)
        val files = dir.listFiles { f -> f.name.startsWith("preset_") && f.name.endsWith(".json") } ?: return emptyList()

        val results = mutableListOf<SavedHapticPreset>()
        for (file in files) {
            try {
                val json = JSONObject(file.readText())
                val id = json.getString("id")
                val title = json.optString("title", "Untitled Preset")
                val audioFileName = json.optString("audioFileName", "audio.wav")
                val durationMs = json.optLong("durationMs", 0L)
                val trimStartMs = json.optInt("trimStartMs", 0)
                val trimEndMs = json.optInt("trimEndMs", durationMs.toInt())
                val selectedPipelineIndex = json.optInt("selectedPipelineIndex", 0)
                val modeName = json.optString("intensityMode", HapticIntensityMode.Punchy.name)
                val intensityMode = try {
                    HapticIntensityMode.valueOf(modeName)
                } catch (e: Exception) {
                    HapticIntensityMode.Punchy
                }
                val createdAt = json.optLong("createdAt", file.lastModified())

                val pipesArray = json.optJSONArray("pipelines") ?: JSONArray()
                val pipelines = mutableListOf<SavedPipelineData>()
                for (i in 0 until pipesArray.length()) {
                    val pObj = pipesArray.getJSONObject(i)
                    val pType = PipelineType.fromNameOrDefault(pObj.getString("pipelineType"))
                    val pDur = pObj.optLong("durationMs", durationMs)
                    val peakCount = pObj.optInt("peakCount", 0)

                    val envArr = pObj.optJSONArray("envelopePoints") ?: JSONArray()
                    val envFloats = FloatArray(envArr.length())
                    for (j in 0 until envArr.length()) {
                        envFloats[j] = envArr.getDouble(j).toFloat()
                    }

                    val evArr = pObj.optJSONArray("events") ?: JSONArray()
                    val events = mutableListOf<HapticEvent>()
                    for (k in 0 until evArr.length()) {
                        val evObj = evArr.getJSONObject(k)
                        events.add(
                            HapticEvent(
                                timestampMs = evObj.getLong("timestampMs"),
                                effectId = evObj.getInt("effectId")
                            )
                        )
                    }

                    pipelines.add(
                        SavedPipelineData(
                            pipelineType = pType,
                            durationMs = pDur,
                            peakCount = peakCount,
                            envelopePoints = envFloats,
                            events = events
                        )
                    )
                }

                results.add(
                    SavedHapticPreset(
                        id = id,
                        title = title,
                        audioFileName = audioFileName,
                        durationMs = durationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        selectedPipelineIndex = selectedPipelineIndex,
                        intensityMode = intensityMode,
                        createdAt = createdAt,
                        pipelines = pipelines
                    )
                )
            } catch (ignored: Exception) {}
        }

        return results.sortedByDescending { it.createdAt }
    }

    fun loadPresetData(context: Context, preset: SavedHapticPreset): LoadedPresetData? {
        val dir = getPresetsDir(context)
        val wavFile = File(dir, "preset_${preset.id}.wav")
        if (!wavFile.exists()) return null

        return try {
            val decodedAudio = readWavToDecodedAudio(wavFile)
            val synthesizedList = preset.pipelines.mapIndexed { index, pipe ->
                val binFile = File(dir, "preset_${preset.id}_pipe${index}.bin")
                val rtpBytes = if (binFile.exists()) binFile.readBytes() else ByteArray(0)
                val vibrationEffect = HapticSynthesizer.buildAndroidVibrationEffect(pipe.events, pipe.durationMs)

                SynthesizedHaptic(
                    pipelineType = pipe.pipelineType,
                    rtpRawBytes = rtpBytes,
                    vibrationEffect = vibrationEffect,
                    envelopePoints = pipe.envelopePoints,
                    events = pipe.events,
                    durationMs = pipe.durationMs,
                    peakCount = pipe.peakCount,
                    sampleRateHz = 24000
                )
            }

            LoadedPresetData(
                audioUri = Uri.fromFile(wavFile),
                decodedAudio = decodedAudio,
                synthesizedHaptics = synthesizedList
            )
        } catch (e: Exception) {
            null
        }
    }

    fun deletePreset(context: Context, id: String) {
        val dir = getPresetsDir(context)
        File(dir, "preset_${id}.json").delete()
        File(dir, "preset_${id}.wav").delete()
        for (i in 0..10) {
            File(dir, "preset_${id}_pipe${i}.bin").delete()
        }
    }

    private fun readWavToDecodedAudio(wavFile: File): AudioDecoder.DecodedAudio {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) throw IllegalArgumentException("WAV file too short")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = buffer.getInt(24)
        val pcmByteLength = bytes.size - 44
        val numSamples = pcmByteLength / 2
        val samples = FloatArray(numSamples)
        buffer.position(44)
        for (i in 0 until numSamples) {
            val s16 = buffer.short
            samples[i] = (s16.toFloat() / 32767.0f).coerceIn(-1.0f, 1.0f)
        }
        val durationMs = if (sampleRate > 0) (numSamples.toLong() * 1000L) / sampleRate else 0L
        return AudioDecoder.DecodedAudio(samples, sampleRate, durationMs)
    }
}
