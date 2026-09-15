package dev.joseph.hyperbounce.lockscreen

import io.github.libxposed.api.XposedModule

/**
 * Backward compatibility stub.
 * All optimizations are now integrated directly into the main [LockScreenSheetHook].
 */
object LockScreenSheetOptimizedHook {
    val lockscreenFlingVelocity: Float
        get() = LockScreenSheetHook.lockscreenFlingVelocity

    fun init(module: XposedModule, classLoader: ClassLoader) {
        LockScreenSheetHook.init(module, classLoader)
    }
}
