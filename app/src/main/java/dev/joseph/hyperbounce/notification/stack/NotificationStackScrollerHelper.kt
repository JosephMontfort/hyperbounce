package dev.joseph.hyperbounce.notification.stack

import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.ln

/**
 * iOS-Style Stacked Notifications Engine for HyperOS:
 * - Mathematical model reverse-engineered from HyperLight (n21.java).
 * - Evaluates directly in StackScrollAlgorithm.resetViewStates() with zero 120Hz CPU overhead.
 * - Non-linear logarithmic depth compression ensures cards gracefully stack at the bottom
 *   without endless scrolling overflow.
 * - Dynamic scale compression from 1.0 down to 0.8.
 * - Dynamic gap compression between 30dp down to 4dp.
 * - Negative Z-layering ensuring upper cards overlap lower cards.
 * - Dynamic scroll headroom expansion in NotificationStackScrollLayout.getScrollRange().
 */
object NotificationStackScrollerHelper {

    private const val TAG = "HyperBounce_StackScroller"

    private val LN_11 = ln(11.0).toFloat()
    private val LOG_LUT = FloatArray(129) { i ->
        val x = (i * 2.0f / 128.0f) * 10.0f + 1.0f
        (ln(x.toDouble()) / LN_11.toDouble()).toFloat()
    }

    @Volatile
    var extraStackScrollRange: Int = 0

    // Cached reflection fields and methods
    private var cachedHostViewField: Field? = null
    private var cachedIsOnKeyguardMethod: Method? = null
    private var cachedViewStateField: Field? = null
    private var cachedGetViewStateMethod: Method? = null
    private var cachedYTranslationField: Field? = null
    private var cachedScaleXField: Field? = null
    private var cachedScaleYField: Field? = null
    private var cachedZTranslationField: Field? = null
    private var cachedHeightField: Field? = null
    private var cachedGoneField: Field? = null
    private var cachedHiddenField: Field? = null

    /**
     * Evaluates logarithmic compression factor g(depth) via 129-entry LUT.
     */
    fun getLogCompression(depth: Float): Float {
        if (depth <= 0.0f) return 0.0f
        if (depth >= 2.0f) return (ln((depth * 10.0f + 1.0f).toDouble()) / LN_11.toDouble()).toFloat()
        val idxF = (depth * 128.0f) / 2.0f
        val idx = idxF.toInt()
        if (idx >= 128) return LOG_LUT[128]
        val frac = idxF - idx
        return LOG_LUT[idx] + frac * (LOG_LUT[idx + 1] - LOG_LUT[idx])
    }

    private fun getHostView(algorithm: Any): ViewGroup? {
        try {
            if (cachedHostViewField == null) {
                var curr: Class<*>? = algorithm.javaClass
                while (curr != null) {
                    try {
                        cachedHostViewField = curr.getDeclaredField("mHostView").apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            return cachedHostViewField?.get(algorithm) as? ViewGroup
        } catch (_: Throwable) {
            return null
        }
    }

    fun isOnKeyguard(ambientState: Any?): Boolean {
        if (ambientState == null) return false
        try {
            if (cachedIsOnKeyguardMethod == null) {
                cachedIsOnKeyguardMethod = ambientState.javaClass.getMethod("isOnKeyguard")
            }
            return cachedIsOnKeyguardMethod?.invoke(ambientState) as? Boolean ?: false
        } catch (_: Throwable) {
            return false
        }
    }

    private fun getViewState(child: View): Any? {
        try {
            if (cachedGetViewStateMethod == null) {
                try {
                    cachedGetViewStateMethod = child.javaClass.getMethod("getViewState")
                } catch (_: Throwable) {}
            }
            if (cachedGetViewStateMethod != null) {
                return cachedGetViewStateMethod?.invoke(child)
            }
            if (cachedViewStateField == null) {
                var curr: Class<*>? = child.javaClass
                while (curr != null) {
                    try {
                        cachedViewStateField = curr.getDeclaredField("mViewState").apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            return cachedViewStateField?.get(child)
        } catch (_: Throwable) {
            return null
        }
    }

    private fun getFloat(viewState: Any, fieldName: String, cachedRef: Field?): Pair<Float, Field?> {
        var f = cachedRef
        try {
            if (f == null) {
                var curr: Class<*>? = viewState.javaClass
                while (curr != null) {
                    try {
                        f = curr.getDeclaredField(fieldName).apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            val v = f?.getFloat(viewState) ?: 0.0f
            return Pair(v, f)
        } catch (_: Throwable) {
            return Pair(0.0f, f)
        }
    }

    private fun setFloat(viewState: Any, fieldName: String, cachedRef: Field?, value: Float): Field? {
        var f = cachedRef
        try {
            if (f == null) {
                var curr: Class<*>? = viewState.javaClass
                while (curr != null) {
                    try {
                        f = curr.getDeclaredField(fieldName).apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            f?.setFloat(viewState, value)
        } catch (_: Throwable) {}
        return f
    }

    private fun getInt(viewState: Any, fieldName: String, cachedRef: Field?): Pair<Int, Field?> {
        var f = cachedRef
        try {
            if (f == null) {
                var curr: Class<*>? = viewState.javaClass
                while (curr != null) {
                    try {
                        f = curr.getDeclaredField(fieldName).apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            val v = f?.getInt(viewState) ?: 0
            return Pair(v, f)
        } catch (_: Throwable) {
            return Pair(0, f)
        }
    }

    private fun getBoolean(viewState: Any, fieldName: String, cachedRef: Field?): Pair<Boolean, Field?> {
        var f = cachedRef
        try {
            if (f == null) {
                var curr: Class<*>? = viewState.javaClass
                while (curr != null) {
                    try {
                        f = curr.getDeclaredField(fieldName).apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            val v = f?.getBoolean(viewState) ?: false
            return Pair(v, f)
        } catch (_: Throwable) {
            return Pair(false, f)
        }
    }

    /**
     * Executes the stacked notification mathematical transformation after Stock StackScrollAlgorithm
     * has generated initial view states.
     */
    fun applyStackAlgorithm(algorithm: Any, ambientState: Any?) {
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.isSheetActive || !cfg.notifStackEnabled) {
            extraStackScrollRange = 0
            return
        }

        // Never stack notifications on the actual lockscreen!
        if (isOnKeyguard(ambientState)) {
            extraStackScrollRange = 0
            return
        }

        val nssl = getHostView(algorithm) ?: return
        val childCount = nssl.childCount
        if (childCount <= 0) {
            extraStackScrollRange = 0
            return
        }

        val density = nssl.resources.displayMetrics.density
        val maxLayoutHeight = if (nssl.height > 0) {
            nssl.height.toFloat()
        } else {
            Resources.getSystem().displayMetrics.heightPixels.toFloat()
        }

        // Authentic iOS stack configuration: start stack at 70% of panel height
        val stackStartRatio = 0.70f
        val stackStartY = maxLayoutHeight * stackStartRatio
        val stackRegionHeight = maxLayoutHeight * (1.0f - stackStartRatio)
        if (stackRegionHeight <= 0f) {
            extraStackScrollRange = 0
            return
        }

        val minGap = 4f * density
        val maxGap = 30f * density
        val gapRange = minGap - maxGap // negative delta for non-linear compression

        var prevBottom = -1.0f
        var currentZ = -1.0f
        var maxStackOverflow = 0.0f

        for (i in 0 until childCount) {
            val child = nssl.getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue

            val className = child.javaClass.simpleName
            if (className.contains("Shelf", ignoreCase = true) ||
                className.contains("Footer", ignoreCase = true) ||
                className.contains("Empty", ignoreCase = true) ||
                className.contains("SectionHeader", ignoreCase = true) ||
                className.contains("MediaHeader", ignoreCase = true)
            ) {
                continue
            }

            val vs = getViewState(child) ?: continue

            val (isGone, gRef) = getBoolean(vs, "gone", cachedGoneField)
            cachedGoneField = gRef
            if (isGone) continue

            val (isHidden, hRef) = getBoolean(vs, "hidden", cachedHiddenField)
            cachedHiddenField = hRef
            if (isHidden) continue

            val (rowY, yRef) = getFloat(vs, "mYTranslation", cachedYTranslationField)
            cachedYTranslationField = yRef

            val (rowH, heightRef) = getInt(vs, "height", cachedHeightField)
            cachedHeightField = heightRef
            val rowHeight = if (rowH > 0) rowH.toFloat() else child.height.toFloat()
            if (rowHeight <= 0f) continue

            // 1. Invert Z-order so upper cards overlap lower stacked cards authentically
            cachedZTranslationField = setFloat(vs, "mZTranslation", cachedZTranslationField, currentZ)
            currentZ -= 1.0f

            if (rowY < stackStartY) {
                // Card is above stack threshold: render at normal scale & stock spacing
                cachedScaleXField = setFloat(vs, "mScaleX", cachedScaleXField, 1.0f)
                cachedScaleYField = setFloat(vs, "mScaleY", cachedScaleYField, 1.0f)
                if (prevBottom < 0f || rowY >= prevBottom) {
                    prevBottom = rowY + rowHeight
                } else {
                    cachedYTranslationField = setFloat(vs, "mYTranslation", cachedYTranslationField, prevBottom)
                    prevBottom += rowHeight
                }
            } else {
                // Card enters the stack: apply non-linear logarithmic depth compression
                val depth = (rowY - stackStartY) / stackRegionHeight
                val gDepth = getLogCompression(depth)

                // Scale factor smoothly compresses from 1.0 down towards 0.80
                val scale = (1.0f - (depth * 0.20f)).coerceIn(0.80f, 1.0f)

                // Inter-card gap compresses between 30dp down towards 4dp
                val compressedGap = minGap.coerceAtLeast(gapRange * gDepth + maxGap)
                if (prevBottom < 0f) {
                    prevBottom = rowY + rowHeight
                }

                val targetBottom = prevBottom + compressedGap
                val scaledHeight = rowHeight * scale
                val newY = (((targetBottom - scaledHeight) - rowY) * gDepth) + rowY

                cachedYTranslationField = setFloat(vs, "mYTranslation", cachedYTranslationField, newY)

                child.pivotX = child.width / 2.0f
                cachedScaleXField = setFloat(vs, "mScaleX", cachedScaleXField, scale)
                cachedScaleYField = setFloat(vs, "mScaleY", cachedScaleYField, scale)

                prevBottom = newY + scaledHeight

                val overflow = rowY - stackStartY
                if (overflow > maxStackOverflow) {
                    maxStackOverflow = overflow
                }
            }
        }

        extraStackScrollRange = maxStackOverflow.toInt()
    }
}
