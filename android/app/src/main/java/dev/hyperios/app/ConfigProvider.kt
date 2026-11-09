package dev.hyperios.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ConfigProvider : ContentProvider() {
    companion object {
        private var lastStartupTime: Long = 0
        private var crashLoopCounter: Int = 0
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        when (method) {
            "saveCCLayout" -> {
                if (!arg.isNullOrBlank()) {
                    try {
                        val file = File(ctx.filesDir, "cc_layout.json")
                        val tmp = File(ctx.filesDir, "cc_layout.tmp")
                        val fos = FileOutputStream(tmp)
                        fos.write(arg.toByteArray(Charsets.UTF_8))
                        fos.flush()
                        fos.close()
                        tmp.renameTo(file)
                    } catch (t: Throwable) {}
                }
                return Bundle()
            }
            "getCCLayout" -> {
                val b = Bundle()
                try {
                    val file = File(ctx.filesDir, "cc_layout.json")
                    if (file.exists()) {
                        val fis = FileInputStream(file)
                        val data = ByteArray(file.length().toInt())
                        fis.read(data)
                        fis.close()
                        b.putString("cc_layout_json", String(data, Charsets.UTF_8))
                    }
                } catch (t: Throwable) {}
                return b
            }
            "systemUiStartup" -> {
                val b = Bundle()
                val now = System.currentTimeMillis()
                if (now - lastStartupTime < 5000) {
                    crashLoopCounter++
                    if (crashLoopCounter >= 5) {
                        try {
                            val f = File(ctx.filesDir, "safemode.flag")
                            if (!f.exists()) f.createNewFile()
                        } catch (t: Throwable) {}
                    }
                } else {
                    crashLoopCounter = 1
                }
                lastStartupTime = now

                val f = File(ctx.filesDir, "safemode.flag")
                b.putBoolean("safeMode", f.exists())
                return b
            }
            "systemUiHealthy" -> {
                crashLoopCounter = 0
                return Bundle()
            }
            "isSafeMode" -> {
                val b = Bundle()
                val f = File(ctx.filesDir, "safemode.flag")
                b.putBoolean("safeMode", f.exists())
                return b
            }
            "resetCCLayout" -> {
                try {
                    val file = File(ctx.filesDir, "cc_layout.json")
                    if (file.exists()) file.delete()
                    val p = ctx.getSharedPreferences("hyperbounce_settings", Context.MODE_PRIVATE)
                    p.edit().putBoolean("cc_pending_reset_layout", false).apply()
                    val intent = Intent("dev.hyperios.app.RESET_CC_LAYOUT")
                    ctx.sendBroadcast(intent)
                } catch (t: Throwable) {}
                return Bundle()
            }
            "clearPendingResetLayout" -> {
                try {
                    val p = ctx.getSharedPreferences("hyperbounce_settings", Context.MODE_PRIVATE)
                    p.edit().putBoolean("cc_pending_reset_layout", false).apply()
                } catch (t: Throwable) {}
                return Bundle()
            }
            "setSafeMode" -> {
                try {
                    val f = File(ctx.filesDir, "safemode.flag")
                    if ("true" == arg) {
                        if (!f.exists()) f.createNewFile()
                    } else {
                        if (f.exists()) f.delete()
                    }
                } catch (t: Throwable) {}
                return Bundle()
            }
            "hasCustomDepthSubject" -> {
                val b = Bundle()
                val f = File(ctx.filesDir, "custom_depth_subject.png")
                b.putBoolean("hasCustom", f.exists() && f.length() > 0)
                return b
            }
            "clearCustomDepthSubject" -> {
                try {
                    val customFile = File(ctx.filesDir, "custom_depth_subject.png")
                    if (customFile.exists()) customFile.delete()
                    val segFile = File(ctx.filesDir, "segmented_depth_subject.png")
                    val activeFile = File(ctx.filesDir, "lock_depth_subject.png")
                    if (segFile.exists() && segFile.length() > 0) {
                        segFile.copyTo(activeFile, overwrite = true)
                    } else {
                        if (activeFile.exists()) activeFile.delete()
                    }
                } catch (_: Throwable) {}
                return Bundle()
            }
            "hasCustomWallpaper" -> {
                val b = Bundle()
                val f = File(ctx.filesDir, "custom_notification_wallpaper.png")
                b.putBoolean("hasCustom", f.exists() && f.length() > 0)
                return b
            }
            "clearCustomWallpaper" -> {
                try {
                    val customWp = File(ctx.filesDir, "custom_notification_wallpaper.png")
                    if (customWp.exists()) customWp.delete()
                } catch (_: Throwable) {}
                return Bundle()
            }
        }
        return null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val segment = uri.lastPathSegment ?: return null
        val allowedFiles = setOf(
            "lock_depth_subject.png",
            "custom_depth_subject.png",
            "segmented_depth_subject.png",
            "custom_notification_wallpaper.png",
            "incall_stock_background.png",
            "cc_layout.json"
        )
        if (segment in allowedFiles) {
            var file = File(ctx.filesDir, segment)
            if (mode.contains("w")) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
            }
            if (segment == "incall_stock_background.png") {
                try {
                    val sysBlur = File("/data/system/users/0/blurwallpaper")
                    if (sysBlur.exists() && sysBlur.length() > 50_000L) {
                        if (!file.exists() || file.length() != sysBlur.length() || file.lastModified() < sysBlur.lastModified()) {
                            sysBlur.copyTo(file, overwrite = true)
                            file.setLastModified(sysBlur.lastModified())
                        }
                    }
                } catch (_: Throwable) {}
            }
            if ((!file.exists() || file.length() == 0L) && segment == "lock_depth_subject.png") {
                val customFile = File(ctx.filesDir, "custom_depth_subject.png")
                if (customFile.exists() && customFile.length() > 0L) {
                    try {
                        customFile.copyTo(file, overwrite = true)
                    } catch (_: Throwable) {}
                    if (!file.exists() || file.length() == 0L) {
                        file = customFile
                    }
                } else {
                    val segFile = File(ctx.filesDir, "segmented_depth_subject.png")
                    if (segFile.exists() && segFile.length() > 0L) {
                        try {
                            segFile.copyTo(file, overwrite = true)
                        } catch (_: Throwable) {}
                        if (!file.exists() || file.length() == 0L) {
                            file = segFile
                        }
                    }
                }
            }
            if (file.exists() && file.length() > 0) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
