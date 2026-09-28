package dev.joseph.hyperbounce.gesture

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pocket Touch Prevention engine specifically engineered for devices without a physical
 * hardware infrared proximity sensor (such as Xiaomi/Redmi/POCO devices using virtual
 * ultrasound proximity sensors that are powered off during deep sleep).
 *
 * Employs multi-layer touch-geometry, contact surface area, temporal, and spatial
 * heuristics to eliminate false triggers caused by fabric rubbing, thigh contact, or palm resting.
 */
object PocketTouchFilter {

    // Thresholds tuned for FocalTech digitizers (X: 0..19520, Y: 0..43392, Major: 0..255)
    private const val MAX_PERMISSIBLE_TOUCH_MAJOR = 75
    private const val MAX_AVERAGE_TOUCH_MAJOR = 65.0f

    private const val MIN_DURATION_MS = 180L
    private const val MAX_DURATION_MS = 2600L

    private const val MIN_BOUNDING_WIDTH = 1500.0f
    private const val MIN_BOUNDING_HEIGHT = 1800.0f
    private const val MIN_TOTAL_PATH_LENGTH = 3500.0f
    private const val MIN_SAMPLE_COUNT = 12

    private const val EDGE_MARGIN_X = 350.0f
    private const val EDGE_MARGIN_Y = 500.0f
    private const val MAX_SCREEN_X = 19520.0f
    private const val MAX_SCREEN_Y = 43392.0f

    /**
     * Evaluates a completed stroke candidate.
     *
     * @param xPoints X coordinates in raw digitizer units
     * @param yPoints Y coordinates in raw digitizer units
     * @param count Number of points recorded in this stroke
     * @param durationMs Milliseconds elapsed between ACTION_DOWN and ACTION_UP
     * @param maxTouchMajor The maximum ABS_MT_TOUCH_MAJOR encountered during the stroke
     * @param avgTouchMajor The average ABS_MT_TOUCH_MAJOR across all samples
     * @param maxActiveSlots The maximum concurrent multi-touch slots detected during the stroke
     * @return Result containing whether the stroke is valid and if not, the rejection reason
     */
    fun evaluateStroke(
        xPoints: FloatArray,
        yPoints: FloatArray,
        count: Int,
        durationMs: Long,
        maxTouchMajor: Int,
        avgTouchMajor: Float,
        maxActiveSlots: Int
    ): FilterResult {
        // 1. Multi-finger contact rejection (fabric or palm pressing multiple areas)
        if (maxActiveSlots > 0) {
            return FilterResult(false, "Rejected: Multi-touch contact detected (slots=$maxActiveSlots)")
        }

        // 2. Contact area rejection (palm, thigh, or broad fabric contact)
        if (maxTouchMajor > MAX_PERMISSIBLE_TOUCH_MAJOR) {
            return FilterResult(false, "Rejected: Contact area too large ($maxTouchMajor > $MAX_PERMISSIBLE_TOUCH_MAJOR)")
        }
        if (avgTouchMajor > MAX_AVERAGE_TOUCH_MAJOR) {
            return FilterResult(false, "Rejected: Average contact area too large ($avgTouchMajor > $MAX_AVERAGE_TOUCH_MAJOR)")
        }

        // 3. Duration gating (static tap vs deliberate drawing vs resting contact)
        if (durationMs < MIN_DURATION_MS) {
            return FilterResult(false, "Rejected: Duration too brief ($durationMs ms < $MIN_DURATION_MS ms)")
        }
        if (durationMs > MAX_DURATION_MS) {
            return FilterResult(false, "Rejected: Duration too long ($durationMs ms > $MAX_DURATION_MS ms)")
        }

        // 4. Sample density check
        if (count < MIN_SAMPLE_COUNT) {
            return FilterResult(false, "Rejected: Insufficient samples ($count < $MIN_SAMPLE_COUNT)")
        }

        // 5. Bounding box & path geometry
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var pathLength = 0.0f

        for (i in 0 until count) {
            val x = xPoints[i]
            val y = yPoints[i]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            if (i > 0) {
                pathLength += hypot(x - xPoints[i - 1], y - yPoints[i - 1])
            }
        }

        val boundingWidth = maxX - minX
        val boundingHeight = maxY - minY

        // Reject tiny micro-jitters in pocket
        if (boundingWidth < MIN_BOUNDING_WIDTH || boundingHeight < MIN_BOUNDING_HEIGHT) {
            return FilterResult(false, "Rejected: Bounding box too small ($boundingWidth x $boundingHeight)")
        }

        // Reject insufficient movement
        if (pathLength < MIN_TOTAL_PATH_LENGTH) {
            return FilterResult(false, "Rejected: Path length too short ($pathLength < $MIN_TOTAL_PATH_LENGTH)")
        }

        // 6. Bezel-to-bezel swipe rejection (phone sliding into/out of pocket)
        val startsAtEdge = (xPoints[0] < EDGE_MARGIN_X || xPoints[0] > (MAX_SCREEN_X - EDGE_MARGIN_X) ||
                yPoints[0] < EDGE_MARGIN_Y || yPoints[0] > (MAX_SCREEN_Y - EDGE_MARGIN_Y))
        val spansFullWidth = boundingWidth > (MAX_SCREEN_X * 0.90f)
        val spansFullHeight = boundingHeight > (MAX_SCREEN_Y * 0.90f)

        if (startsAtEdge && (spansFullWidth || spansFullHeight)) {
            return FilterResult(false, "Rejected: Edge-to-edge pocket sliding brush")
        }

        return FilterResult(true, "Accepted")
    }

    data class FilterResult(
        val accepted: Boolean,
        val reason: String
    )
}
