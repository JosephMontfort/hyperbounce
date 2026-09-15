package dev.joseph.hyperbounce.launcher

import io.github.libxposed.api.XposedModule

/**
 * Backward compatibility stub.
 * All optimizations are now integrated directly into the main [ScreenUnlockHook].
 */
object ScreenUnlockOptimizedHook {
    fun updateVelocity(velocity: Float) {
        ScreenUnlockHook.updateVelocity(velocity)
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        ScreenUnlockHook.init(module, classLoader)
    }
}
