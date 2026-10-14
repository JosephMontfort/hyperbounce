package dev.hyperios.app.launcher

import android.util.Log

/**
 * Native Hook Bridge for LibXposed API 102.
 * Synchronizes runtime Compose UI unlock configuration with the
 * native hooking layer (libhyperbounce_native.so) targeting
 * HyperOS 4 Rust physics engines.
 */
object NativeHookBridge {
    private const val TAG = "HyperBounce_NativeBridge"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("hyperbounce_native")
            isLoaded = true
            Log.d(TAG, "Loaded libhyperbounce_native.so successfully via JNI")
        } catch (_: Throwable) {
            // In Xposed injection, the library is loaded directly by the framework
            isLoaded = true
        }
    }

    fun syncConfig(enabled: Boolean, mode: Int, damping: Float, response: Float, velocity: Float) {
        if (!isLoaded) return
        try {
            updateUnlockConfig(enabled, mode, damping, response, velocity)
        } catch (t: Throwable) {
            Log.d(TAG, "Native hook bridge sync bypassed: ${t.message}")
        }
    }

    @JvmStatic
    private external fun updateUnlockConfig(
        enabled: Boolean,
        mode: Int,
        damping: Float,
        response: Float,
        velocity: Float
    )
}
