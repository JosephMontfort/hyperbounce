package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages iOS iconography replacements for Control Center quick setting tiles
 * and media player controls when the "iOS iconography" toggle is enabled.
 */
object IosIconographyManager {
    private const val TAG = "HyperBounce_IosIcon"

    @Volatile
    private var cachedModuleContext: Context? = null

    private val specToDrawableName = mapOf(
        "wifi" to "ios_ic_qs_wifi",
        "cell" to "ios_ic_qs_cell",
        "bt" to "ios_ic_qs_bt",
        "bluetooth" to "ios_ic_qs_bt",
        "airplane" to "ios_ic_qs_airplane",
        "flashlight" to "ios_ic_qs_flashlight",
        "hotspot" to "ios_ic_qs_hotspot",
        "rotation" to "ios_ic_qs_rotation",
        "auto_rotate" to "ios_ic_qs_rotation",
        "mute" to "ios_ic_qs_mute",
        "silent" to "ios_ic_qs_mute",
        "quiet" to "ios_ic_qs_quiet",
        "quietmode" to "ios_ic_qs_quiet",
        "dnd" to "ios_ic_qs_quiet",
        "vibrate" to "ios_ic_qs_vibrate",
        "battery" to "ios_ic_qs_battery",
        "powersaver" to "ios_ic_qs_battery",
        "batterysaver" to "ios_ic_qs_battery",
        "location" to "ios_ic_qs_location",
        "gps" to "ios_ic_qs_location",
        "nfc" to "ios_ic_qs_nfc",
        "screenrecord" to "ios_ic_qs_screenrecord",
        "screen_recorder" to "ios_ic_qs_screenrecord",
        "screenshot" to "ios_ic_qs_screenshot",
        "dark" to "ios_ic_qs_dark",
        "night" to "ios_ic_qs_dark",
        "dark_mode" to "ios_ic_qs_dark",
        "paper" to "ios_ic_qs_paper",
        "papermode" to "ios_ic_qs_paper"
    )

    fun isSupportedSpec(spec: String?): Boolean {
        if (spec == null) return false
        return specToDrawableName.containsKey(spec.lowercase())
    }

    fun ensureModuleContext(hostContext: Context?): Context? {
        cachedModuleContext?.let { return it }
        val ctx = hostContext ?: return null
        return try {
            val pkgName = try {
                ctx.packageManager.getApplicationInfo("dev.hyperios.app", 0)
                "dev.hyperios.app"
            } catch (_: Throwable) {
                "dev.joseph.hyperbounce"
            }
            val modCtx = ctx.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY)
            cachedModuleContext = modCtx
            modCtx
        } catch (t: Throwable) {
            Log.w(TAG, "Failed ensuring module context: ${t.message}")
            null
        }
    }

    fun getIosTileDrawable(ctx: Context, spec: String): Drawable? {
        val drawableName = specToDrawableName[spec.lowercase()] ?: return null
        val modCtx = ensureModuleContext(ctx) ?: return null
        return try {
            val res = modCtx.resources
            val resId = res.getIdentifier(drawableName, "drawable", modCtx.packageName)
            if (resId != 0) {
                res.getDrawable(resId, modCtx.theme)?.mutate()
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error loading iOS tile drawable for $spec: ${t.message}")
            null
        }
    }

    fun getIosMediaDrawable(ctx: Context, drawableName: String): Drawable? {
        val modCtx = ensureModuleContext(ctx) ?: return null
        return try {
            val res = modCtx.resources
            val resId = res.getIdentifier(drawableName, "drawable", modCtx.packageName)
            if (resId != 0) {
                res.getDrawable(resId, modCtx.theme)?.mutate()
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error loading iOS media drawable $drawableName: ${t.message}")
            null
        }
    }

    fun applyIosIconToTile(itemView: View, spec: String, stateObj: Any?) {
        if (!ControlCenterConfigHolder.config.isIosIconographyActive) return
        val lowerSpec = spec.lowercase()
        if (!isSupportedSpec(lowerSpec)) return

        try {
            val ctx = itemView.context
            val stateInt = if (stateObj != null) {
                try {
                    val f = stateObj.javaClass.getField("state")
                    f.getInt(stateObj)
                } catch (_: Throwable) {
                    try {
                        val m = stateObj.javaClass.getMethod("getState")
                        m.invoke(stateObj) as? Int ?: 1
                    } catch (_: Throwable) { 1 }
                }
            } else 1

            // Locate target ImageView inside itemView
            val iconView = try {
                itemView.javaClass.getMethod("getIcon").invoke(itemView) as? View
            } catch (_: Throwable) {
                try {
                    itemView.javaClass.getMethod("getIconView").invoke(itemView) as? View
                } catch (_: Throwable) { null }
            }

            val targetImageView: ImageView? = when {
                itemView is ImageView -> itemView
                iconView is ImageView -> iconView
                iconView != null -> {
                    val iconField = try {
                        val f = iconView.javaClass.getDeclaredField("icon")
                        f.isAccessible = true
                        f.get(iconView) as? ImageView
                    } catch (_: Throwable) {
                        try {
                            val f2 = iconView.javaClass.getDeclaredField("iconView")
                            f2.isAccessible = true
                            f2.get(iconView) as? ImageView
                        } catch (_: Throwable) { null }
                    }
                    iconField ?: run {
                        val iconResId = ctx.resources.getIdentifier("icon", "id", ctx.packageName)
                        if (iconResId != 0) iconView.findViewById<ImageView>(iconResId) else null
                    }
                }
                else -> {
                    val iconField = try {
                        val f = itemView.javaClass.getDeclaredField("icon")
                        f.isAccessible = true
                        f.get(itemView) as? ImageView
                    } catch (_: Throwable) {
                        try {
                            val f2 = itemView.javaClass.getDeclaredField("iconView")
                            f2.isAccessible = true
                            f2.get(itemView) as? ImageView
                        } catch (_: Throwable) { null }
                    }
                    iconField ?: run {
                        val iconResId = ctx.resources.getIdentifier("icon", "id", ctx.packageName)
                        if (iconResId != 0) itemView.findViewById<ImageView>(iconResId) else null
                    }
                }
            }

            if (targetImageView == null) return

            val iosDrawable = getIosTileDrawable(ctx, lowerSpec) ?: return

            // Tint according to QS Tile state
            // 2: ACTIVE (pure white on accent background)
            // 1: INACTIVE (semi-transparent white / 70% opacity)
            // 0: UNAVAILABLE (dimmed white / 30% opacity)
            val tintColor = when (stateInt) {
                2 -> Color.WHITE
                1 -> Color.argb(179, 255, 255, 255)
                else -> Color.argb(77, 255, 255, 255)
            }
            iosDrawable.setTint(tintColor)

            targetImageView.setImageDrawable(iosDrawable)
            (iosDrawable as? Animatable)?.start()
            (iosDrawable as? Animatable2)?.start()
        } catch (t: Throwable) {
            Log.e(TAG, "applyIosIconToTile error for $spec", t)
        }
    }

    fun applyIosMediaIcons(holder: Any, isPlaying: Boolean) {
        if (!ControlCenterConfigHolder.config.isIosIconographyActive) return
        try {
            val holderClass = holder.javaClass
            var binding: Any? = null
            try {
                val f = holderClass.getDeclaredField("binding")
                f.isAccessible = true
                binding = f.get(holder)
            } catch (_: Throwable) {
                try {
                    val m = holderClass.getMethod("getBinding")
                    binding = m.invoke(holder)
                } catch (_: Throwable) {}
            }
            if (binding == null) return

            fun getImg(name: String): ImageView? {
                return try {
                    val f = binding.javaClass.getField(name)
                    f.get(binding) as? ImageView
                } catch (_: Throwable) {
                    try {
                        val m = binding.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() })
                        m.invoke(binding) as? ImageView
                    } catch (_: Throwable) { null }
                }
            }

            val play = getImg("play")
            val prev = getImg("prev")
            val next = getImg("next")
            val deviceIcon = getImg("deviceIcon")
            val ctx = play?.context ?: prev?.context ?: return

            val playDrawableName = if (isPlaying) "ic_media_pause_animation" else "ic_media_play"
            val playDrawable = getIosMediaDrawable(ctx, playDrawableName)
            if (playDrawable != null && play != null) {
                play.setImageDrawable(playDrawable)
                (playDrawable as? Animatable)?.start()
                (playDrawable as? Animatable2)?.start()
            }

            val prevDrawable = getIosMediaDrawable(ctx, "ic_media_prev")
            if (prevDrawable != null && prev != null) {
                prev.setImageDrawable(prevDrawable)
            }

            val nextDrawable = getIosMediaDrawable(ctx, "ic_media_next")
            if (nextDrawable != null && next != null) {
                next.setImageDrawable(nextDrawable)
            }

            val deviceDrawable = getIosMediaDrawable(ctx, "ic_media_device_default")
            if (deviceDrawable != null && deviceIcon != null) {
                deviceIcon.setImageDrawable(deviceDrawable)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyIosMediaIcons error", t)
        }
    }
}
