package dev.joseph.hyperbounce.controlcenter

import android.os.SystemClock
import android.view.View
import java.util.WeakHashMap

/**
 * High-performance Authentic iOS Jelly Physics Engine for HyperOS Control Center (v2.2).
 *
 * Implements:
 * 1. Heavily-damped Quasi-Critical Harmonic Oscillator (zeta = 0.75, omega_n = 12.0 rad/s):
 *    Produces exactly ONE smooth recoil and single subtle compression squash overshoot (-18%),
 *    then smoothly settles to 1.0 in 280ms. ZERO second-bounce, ZERO shivering, ZERO trembling.
 * 2. Subtle, Refined Stretch Strain:
 *    MAX_OVERPULL_STRAIN = 0.065f (max 6.5% stretch, true to iOS).
 *    MAX_PULLDOWN_STRAIN = 0.025f (max 2.5% soft elasticity during pulldown).
 * 3. Normal Pulldown Glides Cleanly:
 *    Natural sine momentum curve that smoothly vanishes to 0 as progress reaches 1.0;
 *    ZERO spring oscillation or shivering when panel lands.
 * 4. True Finger Down Gating:
 *    Uses sIsUserFlicking to cleanly separate active dragging from gesture release,
 *    preventing release flutter or re-trigger loops.
 * 5. Permanent Center Pivot (0.5w, 0.5h):
 *    Permanently locked to view center; zero coordinate jitter.
 * 6. Non-symmetrical Right-to-Left (RTL) wavefront:
 *    Subtle 20ms diagonal delay gradient across tile columns for organic liquid wave feel.
 */
object JellyPhysicsEngine {

    private const val POISSON_RATIO = 0.35f
    private const val MAX_OVERPULL_STRAIN = 0.065f // Max 6.5% stretch: subtle, refined, true to iOS
    private const val MAX_PULLDOWN_STRAIN = 0.025f // Max 2.5% during normal pulldown entry

    // Damped harmonic oscillator constants (zeta = 0.75, omega_n = 12.0 rad/s)
    private const val SPRING_OMEGA = 12.0f
    private const val DAMPING_RATIO = 0.75f
    private const val GAMMA = DAMPING_RATIO * SPRING_OMEGA // 9.0 s^-1
    private val OMEGA_D = (SPRING_OMEGA * Math.sqrt((1.0 - DAMPING_RATIO * DAMPING_RATIO).toDouble())).toFloat() // ~ 7.937 rad/s
    private val RATIO = GAMMA / OMEGA_D // ~ 1.134
    private const val MAX_SETTLE_TIME_SEC = 0.28f // 280 ms single-rebound settle

    class ReboundState(val view: View) : Runnable {
        var isRebounding: Boolean = false
        var isPulling: Boolean = false
        var peakStrain: Float = 0.0f
        var initialStrain: Float = 0.0f
        var releaseTimeMs: Long = 0L
        var rtlDelayMs: Long = 0L

        fun triggerRebound(strain: Float, delayMs: Long) {
            isPulling = false
            isRebounding = true
            initialStrain = strain
            releaseTimeMs = SystemClock.uptimeMillis()
            rtlDelayMs = delayMs
            view.removeCallbacks(this)
            view.postOnAnimation(this)
        }

        fun tick(now: Long = SystemClock.uptimeMillis()) {
            if (!isRebounding) return
            if (!view.isAttachedToWindow) {
                stopAndReset()
                return
            }

            val elapsedSec = (now - releaseTimeMs - rtlDelayMs) / 1000.0f

            if (elapsedSec < 0.0f) {
                // Wave hasn't reached this column yet; hold current initial strain
                applyScale(view, initialStrain)
                return
            }

            if (elapsedSec >= MAX_SETTLE_TIME_SEC) {
                stopAndReset()
                return
            }

            // iOS second-order damped harmonic oscillator:
            // S(t) = S0 * e^(-gamma*t) * [cos(omega_d*t) - (gamma/omega_d)*sin(omega_d*t)]
            val decay = Math.exp((-GAMMA * elapsedSec).toDouble()).toFloat()
            val osc = Math.cos((OMEGA_D * elapsedSec).toDouble()).toFloat() -
                    RATIO * Math.sin((OMEGA_D * elapsedSec).toDouble()).toFloat()
            val currentStrain = initialStrain * decay * osc

            applyScale(view, currentStrain)
        }

        override fun run() {
            if (!isRebounding) return
            tick(SystemClock.uptimeMillis())
            if (isRebounding) {
                view.postOnAnimation(this)
            }
        }

        fun stopAndReset() {
            isRebounding = false
            isPulling = false
            peakStrain = 0.0f
            initialStrain = 0.0f
            view.removeCallbacks(this)
            val w = view.width
            val h = view.height
            if (w > 0 && h > 0) {
                view.pivotX = w * 0.5f
                view.pivotY = h * 0.5f
            }
            view.scaleX = 1.0f
            view.scaleY = 1.0f
        }
    }

    private val viewStates = WeakHashMap<View, ReboundState>()

    private fun getState(view: View): ReboundState {
        return viewStates.getOrPut(view) { ReboundState(view) }
    }

    private fun applyScale(view: View, strainY: Float) {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return

        val sY = 1.0f + strainY
        val sX = 1.0f - (strainY * POISSON_RATIO)

        // Permanent center pivot completely eliminates micro-jitter and shivering
        view.pivotX = w * 0.5f
        view.pivotY = h * 0.5f
        view.scaleX = sX.coerceIn(0.92f, 1.10f)
        view.scaleY = sY.coerceIn(0.92f, 1.10f)
    }

    /**
     * Called on each frame from MainPanelItemViewHolder.onFrameCallback and MultipleAnimViewHolder.onFrameCallback
     */
    fun onFrame(
        targetView: View,
        holder: Any?,
        progress: Float,
        expandTransY: Float,
        row: Int,
        centerX: Float
    ) {
        if (!ControlCenterConfigHolder.config.isJellyActive) {
            val state = viewStates[targetView]
            if (state != null && (state.isRebounding || state.isPulling)) {
                state.stopAndReset()
            } else if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                targetView.scaleX = 1.0f
                targetView.scaleY = 1.0f
            }
            return
        }

        val w = targetView.width
        val h = targetView.height
        if (w <= 0 || h <= 0) return

        val state = getState(targetView)
        val now = SystemClock.uptimeMillis()

        // Normalized horizontal coordinate from right to left (0 = rightmost, 1 = leftmost)
        val screenW = targetView.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val normX = (centerX / screenW).coerceIn(0.0f, 1.0f)
        val rtlFactor = (1.0f - normX).coerceIn(0.0f, 1.0f)

        val isFingerDown = ControlCenterConfigHolder.sIsUserFlicking
        val isClosing = progress < 0.98f && ControlCenterConfigHolder.sCurrentVelocityY < -100.0f

        // Swipe up dismissal: immediately reset cleanly
        if (isClosing) {
            state.stopAndReset()
            return
        }

        // Row lattice factor: lower rows stretch slightly more (e.g. 1.0 -> 1.14)
        val rowLattice = 1.0f + (row * 0.035f)

        // -------------------------------------------------------------
        // 1. OVERSCROLL PULL & REBOUND (CC already open, user overpulling)
        // -------------------------------------------------------------
        if (isFingerDown && expandTransY > 1.0f) {
            // User is actively touching the screen and pulling down in overscroll!
            state.isRebounding = false
            state.isPulling = true
            targetView.removeCallbacks(state)

            val effectiveTransY = (expandTransY - rtlFactor * 12.0f).coerceAtLeast(0.0f)
            val strain = (effectiveTransY / 1200.0f).coerceIn(0.0f, MAX_OVERPULL_STRAIN) * rowLattice
            state.peakStrain = Math.max(state.peakStrain, strain)

            applyScale(targetView, strain)
            return
        }

        // Finger released from overpull! Trigger rebound ONCE!
        if (state.isPulling && !isFingerDown) {
            val peak = state.peakStrain
            state.isPulling = false
            state.peakStrain = 0.0f
            if (peak > 0.008f) {
                val delayMs = (rtlFactor * 20.0f).toLong()
                state.triggerRebound(peak, delayMs)
                state.tick(now)
                return
            } else {
                state.stopAndReset()
                return
            }
        }

        // While actively rebounding: tick the single-rebound spring
        if (state.isRebounding) {
            state.tick(now)
            return
        }

        // -------------------------------------------------------------
        // 2. NORMAL PULLDOWN ENTRY (Opening CC from top)
        // -------------------------------------------------------------
        if (isFingerDown && progress in 0.05f..0.98f) {
            // Subtle, elegant entry elasticity that naturally decays to 0 at progress = 1.0
            val momentum = Math.sin(progress * Math.PI).toFloat()
            val strain = momentum * MAX_PULLDOWN_STRAIN * rowLattice * (1.0f - rtlFactor * 0.10f)
            applyScale(targetView, strain)
            return
        }

        // -------------------------------------------------------------
        // 3. RESTING STATE: cleanly ensure neutral 1.0 scale
        // -------------------------------------------------------------
        if (!isFingerDown && Math.abs(expandTransY) < 1.0f) {
            if (targetView.scaleX != 1.0f || targetView.scaleY != 1.0f) {
                state.stopAndReset()
            }
        }
    }
}
