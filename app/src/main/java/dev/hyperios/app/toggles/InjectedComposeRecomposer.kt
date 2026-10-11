package dev.hyperios.app.toggles

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shared Compose scheduler for all injected toggle/slider overlays in one host process.
 *
 * Previously every injected widget created its own Recomposer + coroutine scope. That
 * multiplies scheduling, snapshot, and applyChanges overhead as the number of widgets
 * grows, especially while a scrolling container is moving several overlays at once.
 *
 * We keep the existing isolated MotionDurationScale behavior (important for HyperOS /
 * SystemUI plugin contexts), but share the Recomposer so all injected compositions use
 * one scheduler per process.
 */
internal object InjectedComposeRecomposer {
    private val lock = Any()

    @Volatile
    private var instance: Recomposer? = null

    @Volatile
    private var ownerScope: CoroutineScope? = null

    private val motionScale = object : MotionDurationScale {
        override val scaleFactor: Float = 1f
    }

    fun get(): Recomposer {
        instance?.let { return it }

        synchronized(lock) {
            instance?.let { return it }

            val recomposer = Recomposer(AndroidUiDispatcher.Main + motionScale)
            val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

            ownerScope = scope
            instance = recomposer

            scope.launch {
                try {
                    recomposer.runRecomposeAndApplyChanges()
                } catch (_: Throwable) {
                    // Process teardown / host shutdown can terminate the recomposer.
                }
            }

            return recomposer
        }
    }
}
