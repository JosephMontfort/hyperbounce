package dev.hyperios.app

import android.util.Log
import dev.hyperios.app.nav.InstagramHook
import dev.hyperios.app.nav.WhatsAppHook
import dev.hyperios.app.nav.YouTubeHook
import dev.hyperios.app.safemode.SafeModeHandler
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PhysicsHook : XposedModule() {

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val prefs = try {
            getRemotePreferences(dev.hyperios.app.prefs.PREFS_GROUP)
        } catch (_: Throwable) {
            null
        }
        val enabled = prefs?.getBoolean("hot_reload_enabled", false) ?: false
        if (!enabled) {
            log(android.util.Log.INFO, "HyperiOS", "Hot reload refused (disabled in settings)")
            return false
        }
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        super.onHotReloaded(param)
        log(android.util.Log.INFO, "HyperiOS", "Hot reloaded successfully")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        try {
            val prefs = try {
                getRemotePreferences(dev.hyperios.app.prefs.PREFS_GROUP)
            } catch (t: Throwable) {
                null
            }


            // Safe Mode Check (HyperCeiler Architecture)
            if (SafeModeHandler.isSafeMode(param.packageName, prefs)) {
                Log.w("HyperiOS", "[SAFE MODE ACTIVE] Skipping all hooks for ${param.packageName}")
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
                            dev.hyperios.app.lockscreen.LockScreenConfigHolder.updateFromPrefs(prefs)
                            dev.hyperios.app.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            dev.hyperios.app.notification.NotificationSheetConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { p, _ ->
                                val liveHotReload = p?.getBoolean("hot_reload_enabled", false) ?: false
                                if (!liveHotReload) return@registerOnSharedPreferenceChangeListener
                                dev.hyperios.app.lockscreen.LockScreenConfigHolder.updateFromPrefs(p)
                                dev.hyperios.app.controlcenter.ControlCenterConfigHolder.updateFromPrefs(p)
                                dev.hyperios.app.notification.NotificationSheetConfigHolder.updateFromPrefs(p)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperiOS", "Failed to bind remote preferences in SystemUI", t)
                        }
                    }
                    dev.hyperios.app.lockscreen.LockScreenSheetHook.init(this, param.classLoader)
                    dev.hyperios.app.lockscreen.KeypadPhysicsHook.init(this, param.classLoader)
                    dev.hyperios.app.controlcenter.ControlCenterHook.initSystemUIHooks(this, param.classLoader)
                    dev.hyperios.app.notification.NotificationSheetHook.init(this, param.classLoader)
                    dev.hyperios.app.notification.IosHunAnimationHook.init(this, param.classLoader)
                    if (dev.hyperios.app.common.DeviceUtils.isHyperOS4) {
                        dev.hyperios.app.launcher.ScreenUnlockHook.initSystemUI(this, param.classLoader)
                    }
                }

                param.packageName == "miui.systemui.plugin" -> {
                    if (prefs != null) {
                        try {
                            dev.hyperios.app.controlcenter.ControlCenterConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { p, _ ->
                                val liveHotReload = p?.getBoolean("hot_reload_enabled", false) ?: false
                                if (!liveHotReload) return@registerOnSharedPreferenceChangeListener
                                dev.hyperios.app.controlcenter.ControlCenterConfigHolder.updateFromPrefs(p)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperiOS", "Failed to bind remote preferences in Control Center Plugin", t)
                        }
                    }
                    dev.hyperios.app.controlcenter.ControlCenterHook.initPluginHooks(this, param.classLoader)
                }

                param.packageName == "com.miui.home" || param.packageName == "com.mi.android.globallauncher" -> {
                    if (prefs != null) {
                        try {
                            dev.hyperios.app.launcher.LauncherConfigHolder.update(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { p, _ ->
                                val liveHotReload = p?.getBoolean("hot_reload_enabled", false) ?: false
                                if (!liveHotReload) return@registerOnSharedPreferenceChangeListener
                                dev.hyperios.app.launcher.LauncherConfigHolder.update(p)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperiOS", "Failed to bind remote preferences in MIUI Home", t)
                        }
                    }
                    if (!dev.hyperios.app.common.DeviceUtils.isHyperOS4) {
                        dev.hyperios.app.launcher.ScreenUnlockHook.init(this, param.classLoader)
                        dev.hyperios.app.launcher.AppTransitionHook.init(this, param.classLoader)
                    } else {
                        Log.i("HyperiOS", "HyperOS 4 detected: Java launcher hooks bypassed (managed via SystemUI & native layer)")
                    }
                }

                param.packageName == "com.android.incallui" -> {
                    if (prefs != null) {
                        try {
                            dev.hyperios.app.caller.IosCallerConfigHolder.updateFromPrefs(prefs)
                            dev.hyperios.app.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { p, _ ->
                                val liveHotReload = p?.getBoolean("hot_reload_enabled", false) ?: false
                                if (!liveHotReload) return@registerOnSharedPreferenceChangeListener
                                dev.hyperios.app.caller.IosCallerConfigHolder.updateFromPrefs(p)
                                dev.hyperios.app.dialer.DialerRecentsConfigHolder.updateFromPrefs(p)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperiOS", "Failed to bind remote preferences in InCallUI", t)
                        }
                    }
                    dev.hyperios.app.caller.IosCallerScreenHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.contacts" -> {
                    if (prefs != null) {
                        try {
                            dev.hyperios.app.dialer.DialerRecentsConfigHolder.updateFromPrefs(prefs)
                            prefs.registerOnSharedPreferenceChangeListener { p, _ ->
                                val liveHotReload = p?.getBoolean("hot_reload_enabled", false) ?: false
                                if (!liveHotReload) return@registerOnSharedPreferenceChangeListener
                                dev.hyperios.app.dialer.DialerRecentsConfigHolder.updateFromPrefs(p)
                            }
                        } catch (t: Throwable) {
                            Log.e("HyperiOS", "Failed to bind remote preferences in Contacts", t)
                        }
                    }
                    dev.hyperios.app.dialer.DialerRecentsHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.settings" ||
                param.packageName == "com.xiaomi.misettings" ||
                param.packageName == "com.android.thememanager" -> {
                    if (param.packageName == "com.android.settings" || param.packageName == "com.xiaomi.misettings") {
                        if (prefs != null) {
                            try {
                                prefs.registerOnSharedPreferenceChangeListener { _, _ -> }
                            } catch (t: Throwable) {
                                Log.e("HyperiOS", "Failed to bind remote preferences in Settings", t)
                            }
                        }
                        dev.hyperios.app.settings.SettingsEntryHook.init(this, param.classLoader, prefs)
                    }
                    dev.hyperios.app.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
                }

                param.packageName == "com.android.server.telecom" ||
                param.packageName == "android" ||
                param.packageName == "system" ||
                param.packageName == "system_server" -> {
                    dev.hyperios.app.haptics.ringtone.TelecomRingtoneHapticHook.init(this, param.classLoader)
                    dev.hyperios.app.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
                    if (dev.hyperios.app.common.DeviceUtils.isHyperOS4) {
                        dev.hyperios.app.launcher.ScreenUnlockHook.initSystemServer(this, param.classLoader)
                    }
                }
            }


        } catch (t: Throwable) {
            Log.e("HyperiOS", "PhysicsHook failed for ${param.packageName}, leaving stock UI untouched", t)
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        try {
            Log.d("HyperiOS", "onSystemServerStarting: initializing CrashMonitor, Telecom, and Xiaomi ringtone hooks")
            dev.hyperios.app.safemode.CrashMonitor.init(this, param.classLoader)
            dev.hyperios.app.haptics.ringtone.TelecomRingtoneHapticHook.init(this, param.classLoader)
            dev.hyperios.app.haptics.ringtone.XiaomiRingtoneHapticHook.init(this, param.classLoader)
            if (dev.hyperios.app.common.DeviceUtils.isHyperOS4) {
                dev.hyperios.app.launcher.ScreenUnlockHook.initSystemServer(this, param.classLoader)
            }
        } catch (t: Throwable) {
            Log.e("HyperiOS", "Failed to init hooks in onSystemServerStarting", t)
        }
    }
}
