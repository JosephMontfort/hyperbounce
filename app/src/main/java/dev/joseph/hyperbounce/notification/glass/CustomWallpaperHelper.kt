package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Handles persistent storage, cross-process access, and memory caching for
 * custom notification center wallpaper backgrounds.
 */
object CustomWallpaperHelper {
    private const val TAG = "HyperBounce_CustomWp"
    const val FILENAME = "custom_notification_wallpaper.png"

    @Volatile
    private var cachedBitmap: Bitmap? = null

    /**
     * Obtains the custom wallpaper bitmap, cached in memory or loaded from filesDir/ConfigProvider.
     */
    fun getCustomWallpaperBitmap(context: Context): Bitmap? {
        val inMem = cachedBitmap
        if (inMem != null && !inMem.isRecycled) return inMem

        val appContext = context.applicationContext ?: context
        val isCompanionApp = appContext.packageName == "dev.joseph.hyperbounce"

        // If in companion app, read directly from local filesDir
        if (isCompanionApp) {
            try {
                val file = File(appContext.filesDir, FILENAME)
                if (file.exists() && file.length() > 0) {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (bmp != null) {
                        cachedBitmap = bmp
                        return bmp
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Local custom wallpaper read failed: ${t.message}")
            }
            return null
        }

        // SystemUI process: check if custom wallpaper is actually active
        if (!hasCustomWallpaper(appContext)) {
            clearCache(appContext)
            return null
        }

        // Fetch from ConfigProvider across processes (SystemUI access)
        try {
            val uri = Uri.parse("content://dev.joseph.hyperbounce.provider/$FILENAME")
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                if (bmp != null) {
                    cachedBitmap = bmp
                    return bmp
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "ConfigProvider custom wallpaper read failed: ${t.message}")
        }

        return null
    }

    fun hasCustomWallpaper(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (appContext.packageName == "dev.joseph.hyperbounce") {
            val file = File(appContext.filesDir, FILENAME)
            return file.exists() && file.length() > 0
        }
        try {
            val res = appContext.contentResolver.call(
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
                "hasCustomWallpaper", null, null
            )
            return res?.getBoolean("hasCustom", false) ?: false
        } catch (_: Throwable) {}
        val file = File(appContext.filesDir, FILENAME)
        return file.exists() && file.length() > 0
    }

    fun saveCustomWallpaper(context: Context, bitmap: Bitmap): Boolean {
        val appContext = context.applicationContext ?: context
        return try {
            val file = File(appContext.filesDir, FILENAME)
            val tmp = File(appContext.filesDir, "$FILENAME.tmp")
            FileOutputStream(tmp).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            tmp.renameTo(file)
            cachedBitmap = bitmap
            Log.d(TAG, "Custom wallpaper saved successfully (${bitmap.width}x${bitmap.height})")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save custom wallpaper", t)
            false
        }
    }

    fun clearCustomWallpaper(context: Context) {
        val appContext = context.applicationContext ?: context
        clearCache(appContext)
        try {
            val file = File(appContext.filesDir, FILENAME)
            if (file.exists()) file.delete()
            val tmp = File(appContext.filesDir, "$FILENAME.tmp")
            if (tmp.exists()) tmp.delete()
        } catch (_: Throwable) {}
        try {
            appContext.contentResolver.call(
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
                "clearCustomWallpaper", null, null
            )
        } catch (_: Throwable) {}
    }

    fun clearCache(context: Context? = null) {
        cachedBitmap = null
        if (context != null) {
            try {
                val appContext = context.applicationContext ?: context
                val file = File(appContext.filesDir, FILENAME)
                if (file.exists()) file.delete()
                val tmp = File(appContext.filesDir, "$FILENAME.tmp")
                if (tmp.exists()) tmp.delete()
            } catch (_: Throwable) {}
        }
    }

    fun invalidateCache() {
        cachedBitmap = null
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val appContext = context.applicationContext ?: context
        return try {
            var rotationDegrees = 0
            try {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Throwable) {}

            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val raw = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return null

            if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            } else {
                raw
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode bitmap from URI", t)
            null
        }
    }
}
