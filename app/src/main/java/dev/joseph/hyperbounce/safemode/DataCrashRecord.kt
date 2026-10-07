package dev.joseph.hyperbounce.safemode

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single package crash count record stored in Settings.System.
 * Replicates HyperCeiler's DataCrashRecord structure for sliding window crash tracking.
 */
data class DataCrashRecord(
    val pkg: String,
    val time: Long,
    val count: Int
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("p", pkg)
        json.put("t", time)
        json.put("c", count)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): DataCrashRecord? {
            return try {
                val pkg = json.getString("p")
                val time = json.optLong("t", -1L)
                val count = json.optInt("c", 0)
                DataCrashRecord(pkg, time, count)
            } catch (_: Throwable) {
                null
            }
        }

        fun listToJsonString(list: List<DataCrashRecord>): String {
            val array = JSONArray()
            for (item in list) {
                array.put(item.toJson())
            }
            return array.toString()
        }

        fun parseList(raw: String?): MutableList<DataCrashRecord> {
            val list = mutableListOf<DataCrashRecord>()
            if (raw.isNullOrBlank()) return list
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val record = fromJson(obj)
                    if (record != null) {
                        list.add(record)
                    }
                }
            } catch (_: Throwable) {}
            return list
        }
    }
}
