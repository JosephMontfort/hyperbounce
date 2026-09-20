package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import dev.joseph.hyperbounce.haptics.dsp.HapticEvent
import dev.joseph.hyperbounce.haptics.player.HapticIntensityMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ActiveRingtoneProfile(
    val title: String,
    val durationMs: Long,
    val intensityMode: HapticIntensityMode,
    val events: List<HapticEvent>
)

object HapticRingtoneStorage {

    private const val FILE_NAME = "active_ringtone_haptics.json"

    fun save(context: Context, profile: ActiveRingtoneProfile) {
        try {
            val json = JSONObject().apply {
                put("title", profile.title)
                put("durationMs", profile.durationMs)
                put("intensityMode", profile.intensityMode.name)

                val eventsArray = JSONArray()
                profile.events.forEach { event ->
                    val evObj = JSONObject().apply {
                        put("timestampMs", event.timestampMs)
                        put("effectId", event.effectId)
                    }
                    eventsArray.put(evObj)
                }
                put("events", eventsArray)
            }

            val file = File(context.filesDir, FILE_NAME)
            val jsonString = json.toString()
            file.writeText(jsonString)

            // Export to /data/system so Telecom process has instant uninhibited access
            try {
                val sysPath = "/data/system/hyperbounce_ringtone_haptics.json"
                val cmd = arrayOf(
                    "su", "-c",
                    "cat << 'EOF' > $sysPath\n$jsonString\nEOF\nchmod 644 $sysPath"
                )
                Runtime.getRuntime().exec(cmd).waitFor()
            } catch (ignored: Exception) {}
        } catch (ignored: Exception) {}
    }

    fun load(context: Context): ActiveRingtoneProfile? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val text = file.readText()
            val json = JSONObject(text)

            val title = json.optString("title", "HyperBounce Ringtone")
            val durationMs = json.optLong("durationMs", 30000L)
            val modeName = json.optString("intensityMode", HapticIntensityMode.Punchy.name)
            val intensityMode = try {
                HapticIntensityMode.valueOf(modeName)
            } catch (e: Exception) {
                HapticIntensityMode.Punchy
            }

            val eventsArray = json.optJSONArray("events") ?: JSONArray()
            val events = mutableListOf<HapticEvent>()
            for (i in 0 until eventsArray.length()) {
                val evObj = eventsArray.getJSONObject(i)
                events.add(
                    HapticEvent(
                        timestampMs = evObj.getLong("timestampMs"),
                        effectId = evObj.getInt("effectId")
                    )
                )
            }

            ActiveRingtoneProfile(
                title = title,
                durationMs = durationMs,
                intensityMode = intensityMode,
                events = events.sortedBy { it.timestampMs }
            )
        } catch (e: Exception) {
            null
        }
    }
}
