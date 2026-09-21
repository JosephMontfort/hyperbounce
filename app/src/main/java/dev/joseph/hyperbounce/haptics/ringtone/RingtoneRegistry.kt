package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Registry that manages custom ringtone allocations and maps audio paths/titles
 * to unique hardware Effect IDs (e.g. 901, 902...) for the Xiaomi LRA vibrator HAL.
 */
object RingtoneRegistry {

    private const val TAG = "RingtoneRegistry"
    private const val REGISTRY_FILE = "custom_ringtone_mapping.json"
    const val BASE_CUSTOM_EFFECT_ID = 901

    data class RingtoneMapping(
        val title: String,
        val filename: String,
        val effectId: Int,
        val addedAt: Long = System.currentTimeMillis()
    )

    private fun getFile(context: Context): File {
        return File(context.filesDir, REGISTRY_FILE)
    }

    /**
     * Loads all currently registered custom ringtones.
     */
    fun loadMappings(context: Context): List<RingtoneMapping> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("ringtones") ?: JSONArray()
            val list = ArrayList<RingtoneMapping>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RingtoneMapping(
                        title = obj.getString("title"),
                        filename = obj.getString("filename"),
                        effectId = obj.getInt("effectId"),
                        addedAt = obj.optLong("addedAt", 0L)
                    )
                )
            }
            list
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load ringtone mappings", t)
            emptyList()
        }
    }

    /**
     * Saves or replaces mappings in internal storage and mirrors to world-readable/shared location.
     */
    fun saveMappings(context: Context, mappings: List<RingtoneMapping>) {
        try {
            val arr = JSONArray()
            mappings.forEach { m ->
                arr.put(
                    JSONObject().apply {
                        put("title", m.title)
                        put("filename", m.filename)
                        put("effectId", m.effectId)
                        put("addedAt", m.addedAt)
                    }
                )
            }
            val root = JSONObject().apply {
                put("ringtones", arr)
                put("updatedAt", System.currentTimeMillis())
            }

            val file = getFile(context)
            file.writeText(root.toString())

            // Fallback mirror to /data/local/tmp for cross-process access if accessible
            try {
                val tmpFile = File("/data/local/tmp/hyperbounce_ringtone_mapping.json")
                if (tmpFile.exists() || tmpFile.createNewFile()) {
                    tmpFile.writeText(root.toString())
                    tmpFile.setReadable(true, false)
                }
            } catch (_: Throwable) {}

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save ringtone mappings", t)
        }
    }

    /**
     * Finds the next available custom Effect ID above existing stock and custom effects.
     */
    fun getNextAvailableEffectId(context: Context): Int {
        val currentMappings = loadMappings(context)
        val maxCustomId = currentMappings.maxOfOrNull { it.effectId } ?: (BASE_CUSTOM_EFFECT_ID - 1)

        // Also check /vendor/firmware if accessible
        var maxFirmwareId = 522
        try {
            val dir = File("/vendor/firmware")
            if (dir.exists() && dir.canRead()) {
                val files = dir.listFiles()
                files?.forEach { f ->
                    val name = f.name
                    if (name.endsWith("_P_RTP.bin") || name.endsWith("_RTP.bin")) {
                        val numPart = name.substringBefore('_').toIntOrNull()
                        if (numPart != null && numPart > maxFirmwareId) {
                            maxFirmwareId = numPart
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return maxOf(BASE_CUSTOM_EFFECT_ID, maxOf(maxCustomId, maxFirmwareId) + 1)
    }

    /**
     * Resolves an effect ID from a ringtone filename, URI, or audio path.
     */
    fun resolveEffectId(context: Context, query: String): Int? {
        val cleanQuery = query.substringAfterLast('/').substringBefore('?')
        val mappings = loadMappings(context)

        // 1. Direct filename match (e.g. "Atlantis.ogg")
        val matchByFile = mappings.firstOrNull { it.filename.equals(cleanQuery, ignoreCase = true) }
        if (matchByFile != null) return matchByFile.effectId

        // 2. Title match (without extension)
        val titleQuery = cleanQuery.substringBeforeLast('.')
        val matchByTitle = mappings.firstOrNull { it.title.equals(titleQuery, ignoreCase = true) }
        if (matchByTitle != null) return matchByTitle.effectId

        // 3. Fallback: inspect /vendor/firmware directly for `<ID>_${titleQuery}_P_RTP.bin`
        try {
            val dir = File("/vendor/firmware")
            if (dir.exists()) {
                val targetSuffix = "_${titleQuery}_P_RTP.bin"
                val match = dir.listFiles()?.firstOrNull { it.name.endsWith(targetSuffix, ignoreCase = true) }
                if (match != null) {
                    val id = match.name.substringBefore('_').toIntOrNull()
                    if (id != null) return id
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Resolves a file path on disk from a MediaStore content URI.
     */
    fun resolvePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null

        val proj = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE)
        return try {
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve path from URI: $uri", t)
            null
        }
    }
}
