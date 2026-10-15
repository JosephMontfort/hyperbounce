package dev.hyperios.app.controlcenter.hos4

import android.content.Context
import android.util.Log
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Intelligent Dynamic Tile Detector for HyperOS 4.
 * Automatically discovers card-style tiles (2x1) from QSController,
 * resource arrays, and runtime QSRecord inspection rather than relying
 * on hardcoded lists.
 */
object ControlCenterTileDetectorHos4 {
    private const val TAG = "HyperBounce_TileDetector4"

    private val sInspecting = ThreadLocal.withInitial { false }

    // Set of known card-style (2x1) tile specs
    val sCardSpecs: MutableSet<String> = Collections.synchronizedSet(
        LinkedHashSet<String>().apply {
            add("wifi")
            add("cell")
            add("bt")
            add("quietmode")
        }
    )

    /**
     * Checks if the given tile spec represents a 2x1 card.
     */
    fun isCard(spec: String?): Boolean {
        if (spec.isNullOrEmpty()) return false
        val s = spec.lowercase()
        return sCardSpecs.contains(s)
    }

    /**
     * Returns the default grid span for a tile: (2, 1) for cards, (1, 1) for circular tiles.
     */
    fun getDefaultSpan(spec: String?): Pair<Int, Int> {
        return if (isCard(spec)) Pair(2, 1) else Pair(1, 1)
    }

    /**
     * Registers a new card specification dynamically.
     */
    fun registerCardSpec(spec: String?) {
        if (!spec.isNullOrEmpty()) {
            val s = spec.lowercase()
            if (sCardSpecs.add(s)) {
                Log.i(TAG, "Dynamically registered new card spec: $s")
            }
        }
    }

    /**
     * Extracts the spec string from a runtime QSRecord without invoking hooked methods.
     * GUARANTEED SAFE against recursion loops.
     */
    fun getRecordSpec(record: Any?): String? {
        if (record == null) return null
        return try {
            val specField = record.javaClass.getDeclaredField("spec")
            specField.isAccessible = true
            (specField.get(record) as? String)?.lowercase()
        } catch (_: Throwable) {
            try {
                val specMethod = record.javaClass.getMethod("getSpec")
                (specMethod.invoke(record) as? String)?.lowercase()
            } catch (_: Throwable) { null }
        }
    }

    /**
     * Inspects a runtime QSRecord and dynamically learns its card status.
     * NEVER calls getType() or getSpanSize() to avoid recursive interception cycles.
     */
    fun inspectRecord(record: Any?): String? {
        if (record == null) return null
        if (sInspecting.get()) return getRecordSpec(record)

        try {
            sInspecting.set(true)
            val s = getRecordSpec(record) ?: return null

            var isCardRecord = false

            // Check boolean isCard field / method
            try {
                val isCardF = record.javaClass.getDeclaredField("isCard")
                isCardF.isAccessible = true
                isCardRecord = isCardF.getBoolean(record)
            } catch (_: Throwable) {
                try {
                    val isCardM = record.javaClass.getMethod("isCard")
                    isCardRecord = java.lang.Boolean.TRUE == isCardM.invoke(record)
                } catch (_: Throwable) {}
            }

            if (isCardRecord) {
                registerCardSpec(s)
            }
            return s
        } catch (t: Throwable) {
            Log.w(TAG, "inspectRecord error", t)
        } finally {
            sInspecting.set(false)
        }
        return getRecordSpec(record)
    }

    /**
     * Discovers card specifications by querying the host QSController.
     */
    fun inspectQSController(qsController: Any?) {
        if (qsController == null) return
        try {
            // Card style tiles (wifi, cell, etc.)
            try {
                val getCardM = qsController.javaClass.getMethod("getCardStyleTileSpecs")
                val cards = getCardM.invoke(qsController) as? List<*>
                if (cards != null) {
                    for (c in cards) {
                        if (c is String) registerCardSpec(c)
                    }
                }
            } catch (_: Throwable) {}

            // Compact card style tiles (bt, quietmode, etc.)
            try {
                val getCompactM = qsController.javaClass.getMethod("getCompactCardStyleTileSpecs")
                val compactCards = getCompactM.invoke(qsController) as? List<*>
                if (compactCards != null) {
                    for (c in compactCards) {
                        if (c is String) registerCardSpec(c)
                    }
                }
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.w(TAG, "inspectQSController error", t)
        }
    }

    /**
     * Fallback discovery from plugin package resources.
     */
    fun inspectResources(context: Context) {
        try {
            val res = context.resources
            val pkg = context.packageName

            val cardArrId = res.getIdentifier("card_style_tiles_mobile", "array", pkg)
            if (cardArrId != 0) {
                val arr = res.getStringArray(cardArrId)
                for (s in arr) registerCardSpec(s)
            }

            val compactArrId = res.getIdentifier("compact_card_style_tiles", "array", pkg)
            if (compactArrId != 0) {
                val arr = res.getStringArray(compactArrId)
                for (s in arr) registerCardSpec(s)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "inspectResources error", t)
        }
    }
}
