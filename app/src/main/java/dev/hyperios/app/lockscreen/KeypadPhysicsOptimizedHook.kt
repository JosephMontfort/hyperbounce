package dev.hyperios.app.lockscreen

import io.github.libxposed.api.XposedModule

/**
 * Backward compatibility stub.
 * All optimizations are now integrated directly into the main [KeypadPhysicsHook].
 */
object KeypadPhysicsOptimizedHook {
    fun init(module: XposedModule, classLoader: ClassLoader) {
        KeypadPhysicsHook.init(module, classLoader)
    }
}
