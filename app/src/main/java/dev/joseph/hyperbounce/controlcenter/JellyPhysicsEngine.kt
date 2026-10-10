package dev.joseph.hyperbounce.controlcenter

import android.view.View

/**
 * High-performance iOS Jelly Physics Engine for HyperOS Control Center (v2.0).
 *
 * Fully integrated with HyperOS Folme physics:
 * 1. Folme's internal spring drives the physical overshoot translation with sub-critical damping (0.55, 0.30s).
 * 2. Spatial Lattice Dynamics: inter-row spacing expands elastically during pull and contracts during overshoot rebound.
 * 3. Poisson Volume Conservation: proportional aspect scale (scaleY vs scaleX) locked to spatial strain.
 * 4. Zero CPU Overhead: 0 reflection in frame callbacks, 0 background threads/runnables, early-exit when at rest.
 */
object JellyPhysicsEngine {

    private const val POISSON_RATIO = 0.32f
    private const val STRAIN_SCALE_FACTOR = 0.35f
    private const val ROW_LATTICE_STEP = 0.055f

    fun onFrame(
        targetView: View,
        holder: Any?,
        progress: Float,
        expandTransY: Float,
        row: Int,
        centerX: Float
    ) {
        if (!ControlCenterConfigHolder.config.isJellyActive) {
            if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                targetView.scaleX = 1.0f
                targetView.scaleY = 1.0f
                targetView.pivotX = targetView.width * 0.5f
                targetView.pivotY = targetView.height * 0.5f
            }
            return
        }

        // Fast-path: When at rest, immediately return with zero overhead
        if (Math.abs(expandTransY) < 0.2f && progress >= 0.999f) {
            if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                targetView.scaleX = 1.0f
                targetView.scaleY = 1.0f
                targetView.pivotX = targetView.width * 0.5f
                targetView.pivotY = targetView.height * 0.5f
            }
            return
        }

        val w = targetView.width
        val h = targetView.height
        if (w <= 0 || h <= 0) return

        val screenW = targetView.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val normX = (centerX / screenW).coerceIn(0.0f, 1.0f)
        // Subtle diagonal wavefront from top-right corner
        val waveFactor = 1.0f - (normX * 0.12f)

        // 1. Pulldown entry stretch momentum (when progress is between 0.01 and 0.99)
        var enterStrain = 0.0f
        if (progress in 0.01f..0.985f) {
            val momentum = (progress * (1.0f - progress) * 4.0f).coerceAtLeast(0.0f)
            enterStrain = momentum * 0.08f * waveFactor
        }

        // 2. Overscroll / rebound strain from expandTransY
        // When pulled down (transY > 0), strain is positive (elongation)
        // When rebounded upward (transY < 0), strain is negative (squash compression)
        val overscrollStrain = (expandTransY / 1200.0f).coerceIn(-0.06f, 0.22f) * waveFactor

        val totalStrain = enterStrain + overscrollStrain

        // 3. Spatial Lattice Gradient (Inter-row spacing expansion)
        if (Math.abs(expandTransY) >= 0.2f && row > 0) {
            val latticeOffset = expandTransY * (row * ROW_LATTICE_STEP)
            targetView.translationY += latticeOffset
        }

        // 4. Aspect Ratio Scale with Poisson conservation and fixed top anchor
        val sY = 1.0f + (totalStrain * STRAIN_SCALE_FACTOR)
        val sX = 1.0f - (totalStrain * STRAIN_SCALE_FACTOR * POISSON_RATIO)

        targetView.pivotX = w * 0.5f
        targetView.pivotY = 0.0f // Fixed top anchor prevents micro-jitter
        targetView.scaleX = sX
        targetView.scaleY = sY
    }
}
