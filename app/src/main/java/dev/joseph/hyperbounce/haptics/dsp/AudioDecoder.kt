package dev.joseph.hyperbounce.haptics.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes any audio file (WAV, MP3, OGG, M4A, FLAC) from a content Uri or File
 * into normalized mono float PCM samples (-1.0f to 1.0f) using Android's MediaCodec.
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 5000L

    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecodedAudio) return false
            return sampleRate == other.sampleRate &&
                    durationMs == other.durationMs &&
                    samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    suspend fun decode(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data source for URI: $uri", e)
            throw e
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = trackFormat
                break
            }
        }

        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in selected file")
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val decodedSamples = ArrayList<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize, presentationTimeUs, 0
                            )
                            extractor.advance()
                            if (durationUs > 0) {
                                val progress = (presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.95f)
                                onProgress(progress * 0.4f) // First 40% is decoding
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        // Decode 16-bit PCM to normalized float mono
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val shortArray = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(shortArray)

                        if (channelCount == 1) {
                            for (s in shortArray) {
                                decodedSamples.add(s / 32768.0f)
                            }
                        } else {
                            // Downmix stereo / multi-channel to mono
                            var i = 0
                            while (i < shortArray.size) {
                                var sum = 0f
                                for (c in 0 until channelCount) {
                                    if (i + c < shortArray.size) {
                                        sum += shortArray[i + c]
                                    }
                                }
                                decodedSamples.add((sum / channelCount) / 32768.0f)
                                i += channelCount
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val floatSamples = decodedSamples.toFloatArray()
        val durationMs = if (sampleRate > 0) {
            (floatSamples.size.toLong() * 1000L) / sampleRate
        } else 0L

        onProgress(0.45f)
        DecodedAudio(
            samples = floatSamples,
            sampleRate = sampleRate,
            durationMs = durationMs
        )
    }
}
