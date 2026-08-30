package dev.joseph.hyperbounce

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
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
                    val intent = Intent("dev.joseph.hyperbounce.RESET_CC_LAYOUT")
                    ctx.sendBroadcast(intent)
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
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
