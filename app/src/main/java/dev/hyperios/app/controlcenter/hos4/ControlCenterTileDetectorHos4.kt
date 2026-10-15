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
     * Inspects a runtime QSRecord and dynamically learns its card status.
     */
    fun inspectRecord(record: Any?): String? {
        if (record == null) return null
        try {
            var spec: String? = null
            try {
                val specField = record.javaClass.getDeclaredField("spec")
                specField.isAccessible = true
                spec = specField.get(record) as? String
            } catch (_: Throwable) {
                val specMethod = record.javaClass.getMethod("getSpec")
                spec = specMethod.invoke(record) as? String
            }

            if (spec != null) {
                val s = spec.lowercase()
                var isCardRecord = false

                // 1. Check isCard method / field
                try {
                    val isCardM = record.javaClass.getMethod("isCard")
                    isCardRecord = java.lang.Boolean.TRUE == isCardM.invoke(record)
                } catch (_: Throwable) {
                    try {
                        val isCardF = record.javaClass.getDeclaredField("isCard")
                        isCardF.isAccessible = true
                        isCardRecord = isCardF.getBoolean(record)
                    } catch (_: Throwable) {}
                }

                // 2. Check getType() == 2273 (card type)
                if (!isCardRecord) {
                    try {
                        val getTypeM = record.javaClass.getMethod("getType")
                        val vt = getTypeM.invoke(record) as? Int
                        if (vt == 2273) isCardRecord = true
                    } catch (_: Throwable) {}
                }

                // 3. Check getSpanSize() == 2
                if (!isCardRecord) {
                    try {
                        val getSpanM = record.javaClass.getMethod("getSpanSize")
                        val span = getSpanM.invoke(record) as? Int
                        if (span == 2) isCardRecord = true
                    } catch (_: Throwable) {}
                }

                if (isCardRecord) {
                    registerCardSpec(s)
                }
                return s
            }
        } catch (t: Throwable) {
            Log.w(TAG, "inspectRecord error", t)
        }
        return null
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
