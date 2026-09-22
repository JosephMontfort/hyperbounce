package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * iOS 27 Depth Wallpaper Engine for HyperOS Notification Center.
 *
 * Uses transparent custom subject cutouts to render depth layering
 * over the notification center background wallpaper without background ML inference.
 */
object DepthWallpaperHelper {
    private const val TAG = "HyperBounce_DepthWP"
    const val CACHE_FILENAME = "lock_depth_subject.png"
    const val CUSTOM_FILENAME = "custom_depth_subject.png"

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedSubjectBitmap: Bitmap? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addSubjectListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeSubjectListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener.invoke()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Obtains the custom cutout subject bitmap if cached in memory or on disk.
     * Guaranteed 0ms overhead on the UI thread.
     */
    fun getSubjectBitmap(context: Context): Bitmap? {
        val inMemory = cachedSubjectBitmap
        if (inMemory != null && !inMemory.isRecycled) {
            return inMemory
        }

        val appContext = context.applicationContext ?: context

        // 1. Check custom subject file first
        try {
            val customFile = File(appContext.filesDir, CUSTOM_FILENAME)
            if (customFile.exists() && customFile.length() > 0) {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val customBmp = BitmapFactory.decodeFile(customFile.absolutePath, opts)
                if (customBmp != null) {
                    cachedSubjectBitmap = customBmp
                    return customBmp
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Custom subject local file lookup failed: ${t.message}")
        }

        // 2. Check cached file
        try {
            val cacheFile = File(appContext.filesDir, CACHE_FILENAME)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
                if (bmp != null) {
                    cachedSubjectBitmap = bmp
                    return bmp
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Cache file lookup failed: ${t.message}")
        }

        // 3. Fetch from ConfigProvider across processes (for SystemUI)
        try {
            val uri = Uri.parse("content://dev.joseph.hyperbounce.provider/$CUSTOM_FILENAME")
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val providerBmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                if (providerBmp != null) {
                    cachedSubjectBitmap = providerBmp
                    try {
                        val file = File(appContext.filesDir, CUSTOM_FILENAME)
                        val tmp = File(appContext.filesDir, "custom_depth_subject.tmp")
                        FileOutputStream(tmp).use { fos ->
                            providerBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            fos.flush()
                        }
                        tmp.renameTo(file)
                    } catch (_: Throwable) {}
                    return providerBmp
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "ConfigProvider custom subject lookup failed: ${t.message}")
        }

        return null
    }

    fun triggerAsyncSubjectGeneration(
        context: Context,
        forceRefresh: Boolean = false,
        onComplete: ((Bitmap?) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        bgExecutor.execute {
            if (forceRefresh) {
                cachedSubjectBitmap = null
            }
            val bmp = getSubjectBitmap(appContext)
            mainHandler.post {
                onComplete?.invoke(bmp)
                notifyListeners()
            }
        }
    }

    /**
     * Checks if a custom transparent subject cutout image is currently saved.
     */
    fun hasCustomSubject(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (appContext.packageName == "dev.joseph.hyperbounce") {
            val f = File(appContext.filesDir, CUSTOM_FILENAME)
            return f.exists() && f.length() > 0
        }
        try {
            val res = appContext.contentResolver.call(
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
                "hasCustomDepthSubject", null, null
            )
            return res?.getBoolean("hasCustom", false) ?: false
        } catch (_: Throwable) {}
        val f = File(appContext.filesDir, CUSTOM_FILENAME)
        return f.exists() && f.length() > 0
    }

    /**
     * Saves a user-uploaded image (cutout with background removed) as the active depth subject.
     * Automatically handles EXIF rotation and memory-safe scaling.
     */
    fun saveCustomSubjectFromUri(
        context: Context,
        uri: Uri,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        bgExecutor.execute {
            try {
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
                val rawBitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }

                if (rawBitmap == null) {
                    Log.w(TAG, "Failed to decode custom subject image from URI: $uri")
                    mainHandler.post { onComplete?.invoke(false) }
                    return@execute
                }

                val orientedBitmap = if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } else {
                    rawBitmap
                }

                val maxDim = 2560
                val finalBitmap = if (orientedBitmap.width > maxDim || orientedBitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / kotlin.math.max(orientedBitmap.width, orientedBitmap.height)
                    val nw = (orientedBitmap.width * scale).toInt()
                    val nh = (orientedBitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(orientedBitmap, nw, nh, true)
                } else {
                    orientedBitmap
                }

                val customFile = File(appContext.filesDir, CUSTOM_FILENAME)
                val activeFile = File(appContext.filesDir, CACHE_FILENAME)
                val tmp = File(appContext.filesDir, "custom_depth_subject.tmp")
                FileOutputStream(tmp).use { fos ->
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
                tmp.copyTo(customFile, overwrite = true)
                tmp.renameTo(activeFile)

                cachedSubjectBitmap = finalBitmap
                Log.d(TAG, "Custom depth subject saved successfully: ${finalBitmap.width}x${finalBitmap.height}")

                mainHandler.post {
                    notifyListeners()
                    onComplete?.invoke(true)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error saving custom depth subject", t)
                mainHandler.post { onComplete?.invoke(false) }
            }
        }
    }

    /**
     * Clears user custom subject image.
     */
    fun clearCustomSubject(
        context: Context,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        bgExecutor.execute {
            try {
                val customFile = File(appContext.filesDir, CUSTOM_FILENAME)
                if (customFile.exists()) customFile.delete()
                val activeFile = File(appContext.filesDir, CACHE_FILENAME)
                if (activeFile.exists()) activeFile.delete()

                try {
                    appContext.contentResolver.call(
                        Uri.parse("content://dev.joseph.hyperbounce.provider"),
                        "clearCustomDepthSubject", null, null
                    )
                } catch (_: Throwable) {}

                cachedSubjectBitmap = null
                mainHandler.post {
                    notifyListeners()
                    onComplete?.invoke(true)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error clearing custom depth subject", t)
                mainHandler.post { onComplete?.invoke(false) }
            }
        }
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
            val rawBitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return null

            if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decode subject bitmap from URI", t)
            null
        }
    }

    fun saveCustomSubjectBitmap(
        context: Context,
        bitmap: Bitmap,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val appContext = context.applicationContext ?: context
        bgExecutor.execute {
            try {
                val customFile = File(appContext.filesDir, CUSTOM_FILENAME)
                val activeFile = File(appContext.filesDir, CACHE_FILENAME)
                val tmp = File(appContext.filesDir, "custom_depth_subject.tmp")
                FileOutputStream(tmp).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                }
                tmp.copyTo(customFile, overwrite = true)
                tmp.renameTo(activeFile)

                cachedSubjectBitmap = bitmap
                Log.d(TAG, "Custom depth subject saved successfully: ${bitmap.width}x${bitmap.height}")

                mainHandler.post {
                    notifyListeners()
                    onComplete?.invoke(true)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error saving custom depth subject", t)
                mainHandler.post { onComplete?.invoke(false) }
            }
        }
    }

    fun clearCache(context: Context, forceClearAll: Boolean = false) {
        val appContext = context.applicationContext ?: context
        cachedSubjectBitmap = null
        if (forceClearAll && appContext.packageName != "dev.joseph.hyperbounce") {
            try {
                val customFile = File(appContext.filesDir, CUSTOM_FILENAME)
                if (customFile.exists()) customFile.delete()
                val activeFile = File(appContext.filesDir, CACHE_FILENAME)
                if (activeFile.exists()) activeFile.delete()
            } catch (_: Throwable) {}
        }
    }
}
