package dev.joseph.hyperbounce.haptics.ringtone

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import dev.joseph.hyperbounce.haptics.dsp.AudioDecoder
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

object RingtoneManagerHelper {

    private const val TAG = "RingtoneManagerHelper"

    /**
     * Losslessly crops mono FloatArray samples and encodes into a clean standard 16-bit PCM WAV.
     */
    fun cropAndEncodeWav(
        audio: AudioDecoder.DecodedAudio,
        startMs: Int,
        endMs: Int
    ): ByteArray {
        val sr = audio.sampleRate
        val startSample = ((startMs.toLong() * sr) / 1000L).toInt().coerceIn(0, audio.samples.size)
        val endSample = ((endMs.toLong() * sr) / 1000L).toInt().coerceIn(startSample, audio.samples.size)
        val numSamples = endSample - startSample

        val pcmBytes = ByteArray(numSamples * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in startSample until endSample) {
            val sample = audio.samples[i].coerceIn(-1.0f, 1.0f)
            val s16 = (sample * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(s16)
        }

        return createWavContainer(pcmBytes, sr, channels = 1)
    }

    /**
     * Slices the SynthesizedHaptic so its beat events and RTP waveform align exactly with the cropped audio.
     */
    fun cropHaptic(
        haptic: SynthesizedHaptic,
        startMs: Int,
        endMs: Int
    ): SynthesizedHaptic {
        val sMs = startMs.toLong()
        val eMs = endMs.toLong()
        val newDurationMs = (eMs - sMs).coerceAtLeast(0L)

        // Offset events so timestamps start at 0
        val croppedEvents = haptic.events
            .filter { it.timestampMs in sMs..eMs }
            .map { it.copy(timestampMs = it.timestampMs - sMs) }
            .sortedBy { it.timestampMs }

        // Slice 24kHz raw RTP bytes
        val startRtp = ((sMs * 24000L) / 1000L).toInt().coerceIn(0, haptic.rtpRawBytes.size)
        val endRtp = ((eMs * 24000L) / 1000L).toInt().coerceIn(startRtp, haptic.rtpRawBytes.size)
        val croppedRtp = haptic.rtpRawBytes.copyOfRange(startRtp, endRtp)

        // Resample 150 visual envelope display bars
        val numDisplayPoints = 150
        val croppedEnvelope = FloatArray(numDisplayPoints)
        if (croppedRtp.isNotEmpty()) {
            val step = croppedRtp.size.toFloat() / numDisplayPoints
            for (i in 0 until numDisplayPoints) {
                val idx = (i * step).toInt().coerceIn(0, croppedRtp.lastIndex)
                croppedEnvelope[i] = abs(croppedRtp[idx].toFloat()) / 128.0f
            }
        }

        return SynthesizedHaptic(
            pipelineType = haptic.pipelineType,
            rtpRawBytes = croppedRtp,
            vibrationEffect = haptic.vibrationEffect,
            envelopePoints = croppedEnvelope,
            events = croppedEvents,
            durationMs = newDurationMs,
            peakCount = croppedEvents.size
        )
    }

    /**
     * Installs the WAV bytes into MediaStore Ringtones directory.
     */
    fun installRingtoneAudio(
        context: Context,
        wavBytes: ByteArray,
        title: String
    ): Uri? {
        val cleanTitle = "HyperBounce_" + title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val fileName = "$cleanTitle.wav"

        return try {
            val resolver = context.contentResolver
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            // Remove existing entry with same title to prevent duplicate ringtones
            try {
                resolver.delete(
                    collectionUri,
                    "${MediaStore.Audio.Media.TITLE} = ?",
                    arrayOf(cleanTitle)
                )
            } catch (ignored: Exception) {}

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, cleanTitle)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                put(MediaStore.Audio.Media.IS_ALARM, 0)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES + "/HyperBounce")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collectionUri, values) ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                os.write(wavBytes)
                os.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finalizeValues, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error installing ringtone via MediaStore, falling back to direct file", e)
            installRingtoneDirectFile(context, wavBytes, cleanTitle)
        }
    }

    private fun installRingtoneDirectFile(context: Context, wavBytes: ByteArray, cleanTitle: String): Uri? {
        return try {
            val ringtonesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES), "HyperBounce")
            if (!ringtonesDir.exists()) ringtonesDir.mkdirs()
            val file = File(ringtonesDir, "$cleanTitle.wav")
            FileOutputStream(file).use { it.write(wavBytes) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed fallback direct file write", e)
            null
        }
    }

    /**
     * Sets the URI as the default phone ringtone and syncs HyperOS dual-SIM settings.
     */
    fun setAsDefaultRingtone(context: Context, ringtoneUri: Uri): Boolean {
        var success = false
        try {
            if (Settings.System.canWrite(context)) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    ringtoneUri
                )
                success = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard RingtoneManager set failed, attempting root sync: ${e.message}")
        }

        // On Xiaomi / HyperOS, sync dual-SIM ringtone slots via root shell for guaranteed application
        try {
            val uriStr = ringtoneUri.toString()
            val cmd = arrayOf(
                "su", "-c",
                "settings put system ringtone '$uriStr' && " +
                "settings put system ringtone_sound_slot_1 '$uriStr' && " +
                "settings put system ringtone_sound_slot_2 '$uriStr' && " +
                "settings put system ringtone_set 1"
            )
            val proc = Runtime.getRuntime().exec(cmd)
            proc.waitFor()
            success = proc.exitValue() == 0 || success
        } catch (ignored: Exception) {}

        return success
    }

    private fun createWavContainer(pcm16Bytes: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val totalAudioLen = pcm16Bytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val output = ByteArrayOutputStream(44 + pcm16Bytes.size)
        output.write(header)
        output.write(pcm16Bytes)
        return output.toByteArray()
    }
}
