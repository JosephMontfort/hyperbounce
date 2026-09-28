package dev.joseph.hyperbounce.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, zero-allocation implementation of the $1 Unistroke Recognizer
 * (Wobbrock, Wilson, Li - ACM UIST) specialized for screen-off gesture detection.
 *
 * Pre-calibrated for single-stroke 'O', 'V', and 'E' shapes with constrained rotation
 * invariance ([-45°, +45°]) to maintain natural orientation.
 */
class UnistrokeDollarRecognizer {

    companion object {
        const val NUM_POINTS = 64
        private const val REFERENCE_SIZE = 250.0f
        private val HALF_DIAGONAL = 0.5f * sqrt(REFERENCE_SIZE * REFERENCE_SIZE + REFERENCE_SIZE * REFERENCE_SIZE) // ~176.7767
        private val PHI = 0.5f * (-1.0f + sqrt(5.0f)) // ~0.6180339887
        private val ANGLE_RANGE = (PI / 4.0).toFloat() // 45 degrees
        private val ANGLE_PRECISION = (2.0 * PI / 180.0).toFloat() // 2 degrees

        // Pre-computed normalized templates (each exactly NUM_POINTS)
        private val TEMPLATES = ArrayList<GestureTemplate>()

        init {
            loadTemplates()
        }

        private fun loadTemplates() {
            // 1. 'O' Circle templates with multiple starting phases (Top, Right, Bottom, Left)
            val phases = doubleArrayOf(0.0, PI / 2.0, PI, 3.0 * PI / 2.0)
            for (phase in phases) {
                // Clockwise
                val cwX = FloatArray(65)
                val cwY = FloatArray(65)
                for (i in 0..64) {
                    val theta = phase + (i * 2.0 * PI / 64.0)
                    cwX[i] = (sin(theta) * 100.0).toFloat()
                    cwY[i] = (-cos(theta) * 100.0).toFloat()
                }
                addTemplate("O", cwX, cwY, 65)

                // Counter-Clockwise
                val ccwX = FloatArray(65)
                val ccwY = FloatArray(65)
                for (i in 0..64) {
                    val theta = phase - (i * 2.0 * PI / 64.0)
                    ccwX[i] = (sin(theta) * 100.0).toFloat()
                    ccwY[i] = (-cos(theta) * 100.0).toFloat()
                }
                addTemplate("O", ccwX, ccwY, 65)
            }

            // 3. 'V' Single Apex Unistroke (down left-to-bottom, up bottom-to-right)
            val vX = FloatArray(65)
            val vY = FloatArray(65)
            for (i in 0..32) {
                val t = i / 32.0f
                vX[i] = -100.0f * (1.0f - t)
                vY[i] = -100.0f * (1.0f - t) + 100.0f * t
            }
            for (i in 33..64) {
                val t = (i - 32) / 32.0f
                vX[i] = 100.0f * t
                vY[i] = 100.0f * (1.0f - t) - 100.0f * t
            }
            addTemplate("V", vX, vY, 65)

            // 4. 'E' GraffiTi Style (Top right -> left -> mid right -> back left -> bot right)
            val eGraffitiX = floatArrayOf(
                100f, 50f, 0f, -50f, -100f,
                -100f, -100f, -100f, -100f,
                -50f, 0f, 40f, 0f, -50f, -100f,
                -100f, -100f, -100f,
                -50f, 0f, 50f, 100f
            )
            val eGraffitiY = floatArrayOf(
                -100f, -100f, -100f, -100f, -100f,
                -75f, -50f, -25f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f,
                25f, 50f, 100f,
                100f, 100f, 100f, 100f
            )
            addTemplate("E", eGraffitiX, eGraffitiY, eGraffitiX.size)

            // 5. 'E' Cursive Script Loop (top curve -> mid loop -> bottom curve)
            val eScriptX = floatArrayOf(
                60f, 0f, -60f, -90f, -60f, 0f, 40f, 0f, -50f, -90f, -70f, 0f, 70f
            )
            val eScriptY = floatArrayOf(
                -90f, -100f, -80f, -40f, -10f, 0f, 0f, 0f, 15f, 50f, 85f, 100f, 95f
            )
            addTemplate("E", eScriptX, eScriptY, eScriptX.size)
        }

        private fun addTemplate(name: String, rawX: FloatArray, rawY: FloatArray, count: Int) {
            val normX = FloatArray(NUM_POINTS)
            val normY = FloatArray(NUM_POINTS)
            normalizeRaw(rawX, rawY, count, normX, normY)
            TEMPLATES.add(GestureTemplate(name, normX, normY))
        }

        private fun normalizeRaw(
            inX: FloatArray,
            inY: FloatArray,
            count: Int,
            outX: FloatArray,
            outY: FloatArray
        ) {
            // Resample
            var totalLength = 0.0f
            for (i in 1 until count) {
                totalLength += hypot(inX[i] - inX[i - 1], inY[i] - inY[i - 1])
            }
            val interval = totalLength / (NUM_POINTS - 1)
            var accumDist = 0.0f

            outX[0] = inX[0]
            outY[0] = inY[0]
            var outIdx = 1

            var currentX = inX[0]
            var currentY = inY[0]

            var i = 1
            while (i < count && outIdx < NUM_POINTS) {
                val segDist = hypot(inX[i] - currentX, inY[i] - currentY)
                if (accumDist + segDist >= interval) {
                    val ratio = (interval - accumDist) / segDist
                    currentX += ratio * (inX[i] - currentX)
                    currentY += ratio * (inY[i] - currentY)
                    outX[outIdx] = currentX
                    outY[outIdx] = currentY
                    outIdx++
                    accumDist = 0.0f
                } else {
                    accumDist += segDist
                    currentX = inX[i]
                    currentY = inY[i]
                    i++
                }
            }
            while (outIdx < NUM_POINTS) {
                outX[outIdx] = inX[count - 1]
                outY[outIdx] = inY[count - 1]
                outIdx++
            }

            // Scale to 250 x 250
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for (j in 0 until NUM_POINTS) {
                if (outX[j] < minX) minX = outX[j]
                if (outX[j] > maxX) maxX = outX[j]
                if (outY[j] < minY) minY = outY[j]
                if (outY[j] > maxY) maxY = outY[j]
            }
            val width = max(maxX - minX, 1.0f)
            val height = max(maxY - minY, 1.0f)
            for (j in 0 until NUM_POINTS) {
                outX[j] = outX[j] * (REFERENCE_SIZE / width)
                outY[j] = outY[j] * (REFERENCE_SIZE / height)
            }

            // Translate to Origin (0, 0)
            var sumX = 0.0f
            var sumY = 0.0f
            for (j in 0 until NUM_POINTS) {
                sumX += outX[j]
                sumY += outY[j]
            }
            val centroidX = sumX / NUM_POINTS
            val centroidY = sumY / NUM_POINTS
            for (j in 0 until NUM_POINTS) {
                outX[j] -= centroidX
                outY[j] -= centroidY
            }
        }
    }

    // Reusable arrays to ensure ZERO heap allocation during live recognition
    private val resampleX = FloatArray(NUM_POINTS)
    private val resampleY = FloatArray(NUM_POINTS)
    private val rotatedX = FloatArray(NUM_POINTS)
    private val rotatedY = FloatArray(NUM_POINTS)

    data class RecognitionResult(
        val name: String,
        val confidence: Float
    )

    private class GestureTemplate(
        val name: String,
        val pointsX: FloatArray,
        val pointsY: FloatArray
    )

    /**
     * Recognizes the candidate stroke against pre-loaded templates.
     *
     * @param inX Raw X coordinates
     * @param inY Raw Y coordinates
     * @param count Number of recorded points
     * @return RecognitionResult with the best matching name and confidence score (0.0..1.0)
     */
    fun recognize(inX: FloatArray, inY: FloatArray, count: Int): RecognitionResult {
        if (count < 2) {
            return RecognitionResult("UNKNOWN", 0.0f)
        }

        // 1. Resample raw points into 64 equidistant points in resampleX/Y
        normalizeCandidate(inX, inY, count)

        // Topological check: Loop closure for 'O'
        val startEndDist = hypot(resampleX[0] - resampleX[NUM_POINTS - 1], resampleY[0] - resampleY[NUM_POINTS - 1])
        var bestMatch = "UNKNOWN"
        var bestScore = 0.0f

        for (template in TEMPLATES) {
            val minDistance = distanceAtBestAngle(template.pointsX, template.pointsY)
            val score = 1.0f - (minDistance / HALF_DIAGONAL)

            if (score > bestScore) {
                bestScore = score
                bestMatch = template.name
            }
        }

        return RecognitionResult(bestMatch, bestScore)
    }

    private fun normalizeCandidate(inX: FloatArray, inY: FloatArray, count: Int) {
        // Step 1: Resample
        var totalLength = 0.0f
        for (i in 1 until count) {
            totalLength += hypot(inX[i] - inX[i - 1], inY[i] - inY[i - 1])
        }
        val interval = totalLength / (NUM_POINTS - 1)
        var accumDist = 0.0f

        resampleX[0] = inX[0]
        resampleY[0] = inY[0]
        var outIdx = 1

        var currentX = inX[0]
        var currentY = inY[0]

        var i = 1
        while (i < count && outIdx < NUM_POINTS) {
            val segDist = hypot(inX[i] - currentX, inY[i] - currentY)
            if (accumDist + segDist >= interval) {
                val ratio = (interval - accumDist) / segDist
                currentX += ratio * (inX[i] - currentX)
                currentY += ratio * (inY[i] - currentY)
                resampleX[outIdx] = currentX
                resampleY[outIdx] = currentY
                outIdx++
                accumDist = 0.0f
            } else {
                accumDist += segDist
                currentX = inX[i]
                currentY = inY[i]
                i++
            }
        }
        while (outIdx < NUM_POINTS) {
            resampleX[outIdx] = inX[count - 1]
            resampleY[outIdx] = inY[count - 1]
            outIdx++
        }

        // Step 2: Scale to 250 x 250
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (j in 0 until NUM_POINTS) {
            if (resampleX[j] < minX) minX = resampleX[j]
            if (resampleX[j] > maxX) maxX = resampleX[j]
            if (resampleY[j] < minY) minY = resampleY[j]
            if (resampleY[j] > maxY) maxY = resampleY[j]
        }
        val width = max(maxX - minX, 1.0f)
        val height = max(maxY - minY, 1.0f)
        for (j in 0 until NUM_POINTS) {
            resampleX[j] = resampleX[j] * (REFERENCE_SIZE / width)
            resampleY[j] = resampleY[j] * (REFERENCE_SIZE / height)
        }

        // Step 3: Translate to Origin (0, 0)
        var sumX = 0.0f
        var sumY = 0.0f
        for (j in 0 until NUM_POINTS) {
            sumX += resampleX[j]
            sumY += resampleY[j]
        }
        val centroidX = sumX / NUM_POINTS
        val centroidY = sumY / NUM_POINTS
        for (j in 0 until NUM_POINTS) {
            resampleX[j] -= centroidX
            resampleY[j] -= centroidY
        }
    }

    private fun distanceAtBestAngle(templateX: FloatArray, templateY: FloatArray): Float {
        var a = -ANGLE_RANGE
        var b = ANGLE_RANGE
        var x1 = PHI * a + (1.0f - PHI) * b
        var f1 = distanceAtAngle(x1, templateX, templateY)
        var x2 = (1.0f - PHI) * a + PHI * b
        var f2 = distanceAtAngle(x2, templateX, templateY)

        while (abs(b - a) > ANGLE_PRECISION) {
            if (f1 < f2) {
                b = x2
                x2 = x1
                f2 = f1
                x1 = PHI * a + (1.0f - PHI) * b
                f1 = distanceAtAngle(x1, templateX, templateY)
            } else {
                a = x1
                x1 = x2
                f1 = f2
                x2 = (1.0f - PHI) * a + PHI * b
                f2 = distanceAtAngle(x2, templateX, templateY)
            }
        }
        return minOf(f1, f2)
    }

    private fun distanceAtAngle(theta: Float, templateX: FloatArray, templateY: FloatArray): Float {
        val cosT = cos(theta)
        val sinT = sin(theta)

        var totalDist = 0.0f
        for (i in 0 until NUM_POINTS) {
            val rx = resampleX[i] * cosT - resampleY[i] * sinT
            val ry = resampleX[i] * sinT + resampleY[i] * cosT
            totalDist += hypot(rx - templateX[i], ry - templateY[i])
        }
        return totalDist / NUM_POINTS
    }
}
