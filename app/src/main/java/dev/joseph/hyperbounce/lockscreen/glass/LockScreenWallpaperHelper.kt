package dev.joseph.hyperbounce.lockscreen.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log

/**
 * Robust wallpaper provider for HyperOS Lock Screen.
 * Retrieves the live lockscreen wallpaper with all active HyperOS lockscreen effects
 * (glass effect, blur effect, depth effect, color ribbons, etc.) directly via
 * MiuiKeyguardWallPaperManager and MiuiWallpaperManager AIDL service.
 */
object LockScreenWallpaperHelper {
    private const val TAG = "HyperBounce_WpHelper"
    @Volatile private var cachedDrawable: Drawable? = null
    @Volatile private var lastFetchTime = 0L
    private const val CACHE_TTL_MS = 3000L

    fun getLiveLockWallpaper(context: Context): Drawable? {
        val now = SystemClock.uptimeMillis()
        val cached = cachedDrawable
        if (cached != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cached
        }

        var result: Drawable? = null

        // 1. Try HyperOS MiuiKeyguardWallPaperManager (renders live preview with active lockscreen effects)
        try {
            val ifaceClass = Class.forName("com.miui.systemui.interfacesmanager.InterfacesImplManager", false, context.classLoader)
            val mgrClass = Class.forName("com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager", false, context.classLoader)
            val getImplMethod = ifaceClass.getMethod("getImpl", Class::class.java)
            val mgr = getImplMethod.invoke(null, mgrClass)
            if (mgr != null) {
                try {
                    val previewMethod = mgrClass.getMethod("getKeyguardWallPaperPreview", Context::class.java)
                    val d = previewMethod.invoke(mgr, context) as? Drawable
                    if (d != null) {
                        result = d
                    }
                } catch (_: Throwable) {}

                if (result == null) {
                    try {
                        val blurField = mgrClass.getField("mKeyguardWallpaperBlurPreview")
                        val d = blurField.get(mgr) as? Drawable
                        if (d != null) {
                            result = d
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "MiuiKeyguardWallPaperManager lookup: ${t.message}")
        }

        // 2. Try HyperOS MiuiWallpaperManager.getFitScreenWallpaperPreview(2, context)
        // Flag 2 = FLAG_LOCK. This queries IMiuiWallpaperManagerService.getMiuiWallpaperPreview(2)
        // returning the exact wallpaper WITH active lockscreen effects (glass, blur, depth cutout)
        if (result == null) {
            try {
                val ifaceClass = Class.forName("com.miui.systemui.interfacesmanager.InterfacesImplManager", false, context.classLoader)
                val miuiWpClass = Class.forName("com.miui.miwallpaper.MiuiWallpaperManager", false, context.classLoader)
                val getImplMethod = ifaceClass.getMethod("getImpl", Class::class.java)
                val miuiWp = getImplMethod.invoke(null, miuiWpClass)
                if (miuiWp != null) {
                    val method = miuiWpClass.getMethod("getFitScreenWallpaperPreview", Int::class.javaPrimitiveType, Context::class.java)
                    val bmp = method.invoke(miuiWp, 2, context) as? Bitmap
                    if (bmp != null) {
                        result = BitmapDrawable(context.resources, bmp)
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "MiuiWallpaperManager lookup: ${t.message}")
            }
        }

        // 3. Fallback to Android WallpaperManager (static lock/system wallpaper)
        if (result == null) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val d = wm.drawable ?: wm.peekDrawable()
                if (d != null) {
                    result = d
                }
            } catch (t: Throwable) {
                Log.w(TAG, "WallpaperManager fallback failed", t)
            }
        }

        if (result != null) {
            cachedDrawable = result
            lastFetchTime = now
        }
        return result ?: cachedDrawable
    }

    fun invalidateCache() {
        cachedDrawable = null
        lastFetchTime = 0L
    }
}
