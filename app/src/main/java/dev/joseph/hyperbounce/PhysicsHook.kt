package dev.joseph.hyperbounce

import android.util.Log
import dev.joseph.hyperbounce.nav.InstagramHook
import dev.joseph.hyperbounce.nav.WhatsAppHook
import dev.joseph.hyperbounce.nav.YouTubeHook
import dev.joseph.hyperbounce.safemode.SafeModeHandler
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook : XposedModule() {

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val prefs = try {
            getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
        } catch (_: Throwable) {
            null
        }
        val enabled = prefs?.getBoolean("hot_reload_enabled", false) ?: false
        if (!enabled) {
            log(android.util.Log.INFO, "HyperBounce", "Hot reload refused (disabled in settings)")
            return false
        }
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        super.onHotReloaded(param)
        log(android.util.Log.INFO, "HyperBounce", "Hot reloaded successfully")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        try {
            val prefs = try {
                getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
            } catch (t: Throwable) {
                null
            }

            val isHotReload = prefs?.getBoolean("hot_reload_enabled", false) ?: false

            // Safe Mode Check (HyperCeiler Architecture)
            if (SafeModeHandler.isSafeMode(param.packageName, prefs)) {
                Log.w("HyperBounce", "[SAFE MODE ACTIVE] Skipping all hooks for ${param.packageName}")
                return
            }

            when {
                param.packageName.contains("instagram") ->
                    InstagramHook(this, param).onPackageReady()

                param.packageName == "com.whatsapp" ->
                    WhatsAppHook(this, param).onPackageReady()

                param.packageName == "com.google.android.youtube" ->
                    YouTubeHook(this, param).onPackageReady()

                param.packageName == "com.android.systemui" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                            dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder.updateFromPrefs(prefs)
                            if (isHotReload) {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                    dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                                    dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                                    dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder.updateFromPrefs(prefs)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in SystemUI", t)
                        }
                    }
                    dev.joseph.hyperbounce.lockscreen.LockScreenSheetHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.lockscreen.KeypadPhysicsHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.controlcenter.ControlCenterHook.initSystemUIHooks(this, param.classLoader)
                    dev.joseph.hyperbounce.notification.NotificationSheetHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.notification.IosHunAnimationHook.init(this, param.classLoader)
                }

                param.packageName == "miui.systemui.plugin" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            if (isHotReload) {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                    dev.joseph.hyperbounce.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in Control Center Plugin", t)
                        }
                    }
                    dev.joseph.hyperbounce.controlcenter.ControlCenterHook.initPluginHooks(this, param.classLoader)
                }

                param.packageName == "com.miui.home" || param.packageName == "com.mi.android.globallauncher" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                            if (isHotReload) {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                    dev.joseph.hyperbounce.launcher.LauncherConfigHolder.update(prefs)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in MIUI Home", t)
                        }
                    }
                    dev.joseph.hyperbounce.launcher.ScreenUnlockHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.launcher.AppTransitionHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.incallui" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.caller.IosCallerConfigHolder.updateFromPrefs(prefs)
                            dev.joseph.hyperbounce.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                            if (isHotReload) {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                    dev.joseph.hyperbounce.caller.IosCallerConfigHolder.updateFromPrefs(prefs)
                                    dev.joseph.hyperbounce.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in InCallUI", t)
                        }
                    }
                    dev.joseph.hyperbounce.caller.IosCallerScreenHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.contacts" -> {
                    if (prefs != null) {
                        try {
                            dev.joseph.hyperbounce.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                            if (isHotReload) {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                                    dev.joseph.hyperbounce.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperBounce", "Failed to bind remote preferences in Contacts", t)
                        }
                    }
                    dev.joseph.hyperbounce.dialer.DialerRecentsHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.settings" ||
                param.packageName == "com.xiaomi.misettings" ||
                param.packageName == "com.android.thememanager" -> {
                    if (param.packageName == "com.android.settings" || param.packageName == "com.xiaomi.misettings") {
                        dev.joseph.hyperbounce.settings.SettingsEntryHook.init(this, param.classLoader, prefs)
                    }
                    dev.joseph.hyperbounce.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.server.telecom" ||
                param.packageName == "android" ||
                param.packageName == "system" -> {
                    dev.joseph.hyperbounce.haptics.ringtone.TelecomRingtoneHapticHook.init(this, param.classLoader)
                    dev.joseph.hyperbounce.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
                }
            }

            // Real-Time Audio-to-Haptic Engine (Games, Media, AudioTrack)
            if (prefs != null) {
                try {
                    dev.joseph.hyperbounce.haptics.hook.AudioHapticConfigHolder.updateFromPrefs(prefs)
                    if (isHotReload) {
                        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                            dev.joseph.hyperbounce.haptics.hook.AudioHapticConfigHolder.updateFromPrefs(prefs)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("HyperBounce", "Failed to bind audio haptic prefs for ${param.packageName}", t)
                }
            }
            if (param.packageName != "com.android.systemui" &&
                param.packageName != "android" &&
                param.packageName != "dev.joseph.hyperbounce") {
                dev.joseph.hyperbounce.haptics.hook.RealtimeAudioHapticHook.init(this, param.classLoader)
            }
        } catch (t: Throwable) {
            Log.e("HyperBounce", "PhysicsHook failed for ${param.packageName}, leaving stock UI untouched", t)
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        try {
            Log.d("HyperBounce", "onSystemServerStarting: initializing CrashMonitor, Telecom, and Xiaomi ringtone hooks")
            dev.joseph.hyperbounce.safemode.CrashMonitor.init(this, param.classLoader)
            dev.joseph.hyperbounce.haptics.ringtone.TelecomRingtoneHapticHook.init(this, param.classLoader)
            dev.joseph.hyperbounce.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
        } catch (t: Throwable) {
            Log.e("HyperBounce", "Failed to init hooks in onSystemServerStarting", t)
        }
    }
}
