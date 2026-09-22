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
    private const val SHARED_TMP_REGISTRY = "/data/local/tmp/hyperbounce_ringtone_mapping.json"
    private const val SETTINGS_KEY = "hyperbounce_ringtone_mappings"
    const val BASE_CUSTOM_EFFECT_ID = 901

    data class RingtoneMapping(
        val title: String,
        val filename: String,
        val effectId: Int,
        val addedAt: Long = System.currentTimeMillis()
    )

    fun getGlobalContext(): Context? = try {
        val at = Class.forName("android.app.ActivityThread")
        at.getMethod("currentApplication").invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }

    private fun getFile(context: Context?): File? {
        val ctx = context ?: getGlobalContext() ?: return null
        return try {
            File(ctx.filesDir, REGISTRY_FILE)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Loads all currently registered custom ringtones.
     * Checks Settings.System, context.filesDir, shared tmp mirror, and parses /vendor/firmware directly.
     */
    fun loadMappings(context: Context?): List<RingtoneMapping> {
        val list = ArrayList<RingtoneMapping>()
        val ctx = context ?: getGlobalContext()

        // 1. Try Settings.System (accessible cross-process by any app/service)
        if (ctx != null) {
            try {
                val jsonText = android.provider.Settings.System.getString(ctx.contentResolver, SETTINGS_KEY)
                if (!jsonText.isNullOrBlank()) {
                    parseJsonIntoList(jsonText, list)
                    if (list.isNotEmpty()) return list
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Settings.System read fallback: ${t.message}")
            }
        }

        // 2. Try context private file
        val file = getFile(ctx)
        if (file != null && file.exists()) {
            try {
                parseJsonIntoList(file.readText(), list)
                if (list.isNotEmpty()) return list
            } catch (t: Throwable) {
                Log.w(TAG, "Failed reading app private mapping: ${t.message}")
            }
        }

        // 3. Try shared /data/local/tmp mirror
        try {
            val tmpFile = File(SHARED_TMP_REGISTRY)
            if (tmpFile.exists() && tmpFile.canRead()) {
                parseJsonIntoList(tmpFile.readText(), list)
                if (list.isNotEmpty()) return list
            }
        } catch (_: Throwable) {}

        // 4. Fallback: inspect /vendor/firmware directly if accessible
        try {
            val dir = File("/vendor/firmware")
            if (dir.exists() && dir.canRead()) {
                dir.listFiles()?.forEach { f ->
                    val name = f.name
                    if (name.endsWith("_P_RTP.bin", ignoreCase = true) || name.endsWith("_RTP.bin", ignoreCase = true)) {
                        val idStr = name.substringBefore('_', "")
                        val id = idStr.toIntOrNull()
                        if (id != null && id >= 900) {
                            val namePart = name.substringAfter('_')
                                .substringBeforeLast("_P_RTP.bin")
                                .substringBeforeLast("_RTP.bin")
                            list.add(
                                RingtoneMapping(
                                    title = namePart,
                                    filename = "${namePart}.ogg",
                                    effectId = id
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return list
    }

    private fun parseJsonIntoList(jsonText: String, outList: MutableList<RingtoneMapping>) {
        val json = JSONObject(jsonText)
        val arr = json.optJSONArray("ringtones") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val mapping = RingtoneMapping(
                title = obj.getString("title"),
                filename = obj.getString("filename"),
                effectId = obj.getInt("effectId"),
                addedAt = obj.optLong("addedAt", 0L)
            )
            if (outList.none { it.effectId == mapping.effectId }) {
                outList.add(mapping)
            }
        }
    }

    /**
     * Saves or replaces mappings in internal storage, Settings.System, and mirrors to world-readable shared location.
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
            val jsonString = root.toString()

            val file = getFile(context)
            file?.writeText(jsonString)

            // Persist to Settings.System for cross-process accessibility
            try {
                android.provider.Settings.System.putString(context.contentResolver, SETTINGS_KEY, jsonString)
            } catch (t: Throwable) {
                // If permission denied in unprivileged app, push via root
                try {
                    val escaped = jsonString.replace("'", "'\\''")
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $SETTINGS_KEY '$escaped'"))
                } catch (_: Throwable) {}
            }

            // Mirror to /data/local/tmp for cross-process access across system services
            try {
                val tmpFile = File(SHARED_TMP_REGISTRY)
                tmpFile.writeText(jsonString)
                tmpFile.setReadable(true, false)
            } catch (_: Throwable) {}

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save ringtone mappings", t)
        }
    }

    /**
     * Finds the next available custom Effect ID above existing stock and custom effects.
     */
    fun getNextAvailableEffectId(context: Context?): Int {
        val currentMappings = loadMappings(context)
        val maxCustomId = currentMappings.maxOfOrNull { it.effectId } ?: (BASE_CUSTOM_EFFECT_ID - 1)

        var maxFirmwareId = 522
        try {
            val dir = File("/vendor/firmware")
            if (dir.exists() && dir.canRead()) {
                dir.listFiles()?.forEach { f ->
                    val name = f.name
                    if (name.endsWith("_P_RTP.bin", ignoreCase = true) || name.endsWith("_RTP.bin", ignoreCase = true)) {
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
     * Resolves an effect ID from a ringtone filename, URI, title, or query string.
     * Resilient against process boundaries and path prefixes.
     */
    fun resolveEffectId(context: Context?, query: String): Int? {
        val ctx = context ?: getGlobalContext()

        // If query is a content:// URI string, resolve its media path or title first
        if (query.startsWith("content://")) {
            val parsedUri = try { Uri.parse(query) } catch (_: Throwable) { null }
            if (parsedUri != null) {
                val resolvedPath = resolvePathFromUri(ctx, parsedUri)
                if (!resolvedPath.isNullOrBlank() && resolvedPath != query) {
                    val idFromPath = resolveEffectId(ctx, resolvedPath)
                    if (idFromPath != null) return idFromPath
                }
            }
        }

        val cleanQuery = query.substringAfterLast('/').substringBefore('?')
        val queryStem = cleanQuery.substringBeforeLast('.')
        val alphaQuery = queryStem.filter { it.isLetterOrDigit() }.lowercase(java.util.Locale.ROOT)

        // 0. Direct numeric check
        cleanQuery.toIntOrNull()?.let { if (it >= 900) return it }

        val mappings = loadMappings(ctx)

        // 1. Check mapped filename
        val matchByFile = mappings.firstOrNull { it.filename.equals(cleanQuery, ignoreCase = true) }
        if (matchByFile != null) return matchByFile.effectId

        // 2. Check mapped title
        val matchByTitle = mappings.firstOrNull {
            it.title.equals(queryStem, ignoreCase = true) ||
            it.title.filter { c -> c.isLetterOrDigit() }.equals(alphaQuery, ignoreCase = true)
        }
        if (matchByTitle != null) return matchByTitle.effectId

        // 3. Fallback: inspect /vendor/firmware directly
        return resolveEffectIdFromFirmware(cleanQuery)
    }

    /**
     * Inspects /vendor/firmware directly for custom RTP binaries matching the query.
     * Works in any process (ThemeManager, system_server, Telecom) without requiring app storage.
     */
    fun resolveEffectIdFromFirmware(query: String): Int? {
        val cleanQuery = query.substringAfterLast('/').substringBefore('?')
        val queryStem = cleanQuery.substringBeforeLast('.')
        val alphaQuery = queryStem.filter { it.isLetterOrDigit() }.lowercase(java.util.Locale.ROOT)
        if (alphaQuery.isBlank()) return null

        try {
            val dir = File("/vendor/firmware")
            if (!dir.exists() || !dir.canRead()) return null
            val files = dir.listFiles() ?: return null

            for (f in files) {
                val name = f.name
                if (!name.endsWith("_RTP.bin", ignoreCase = true)) continue
                val idStr = name.substringBefore('_', "")
                val id = idStr.toIntOrNull() ?: continue
                if (id < 900 && id != 154 && id != 99) continue

                val namePart = name.substringAfter('_')
                    .substringBeforeLast("_P_RTP.bin")
                    .substringBeforeLast("_RTP.bin")
                val alphaPart = namePart.filter { it.isLetterOrDigit() }.lowercase(java.util.Locale.ROOT)

                if (namePart.equals(queryStem, ignoreCase = true) ||
                    alphaPart == alphaQuery ||
                    (alphaQuery.length >= 4 && alphaPart.contains(alphaQuery)) ||
                    (alphaPart.length >= 4 && alphaQuery.contains(alphaPart))
                ) {
                    Log.i(TAG, "resolveEffectIdFromFirmware: matched [$name] -> Effect ID #$id for query [$query]")
                    return id
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resolve effect ID from /vendor/firmware", t)
        }

        return null
    }

    /**
     * Resolves a file path or title on disk from a MediaStore content URI.
     * Queries DATA, DISPLAY_NAME, and TITLE to guarantee extraction even with scoped storage.
     */
    fun resolvePathFromUri(context: Context?, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return uri.toString()

        val ctx = context ?: getGlobalContext() ?: return uri.toString()
        val proj = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE
        )
        return try {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    if (!data.isNullOrBlank()) return@use data

                    val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    if (!name.isNullOrBlank()) return@use name

                    val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    if (titleIdx >= 0) cursor.getString(titleIdx) else null
                } else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve path from URI: $uri", t)
            uri.lastPathSegment
        }
    }
}
