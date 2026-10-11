package dev.joseph.hyperbounce.safemode

import android.app.ApplicationErrorReport
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.VersionedPackage
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Monitors crashes directly inside system_server, replicating HyperCeiler's CrashMonitor.
 * Hooks AppErrors, PackageWatchdog/RescueParty, and BackgroundActivityStartController.
 */
object CrashMonitor {
    private const val TAG = "HyperBounce_CrashMonitor"
    private const val CRASH_TIME_WINDOW = 60000L
    private const val TRIGGER_INTERVAL = 10240L
    private const val MODULE_PACKAGE = "dev.hyperios.app"

    fun init(module: XposedModule, classLoader: ClassLoader) {
        try {
            Log.i(TAG, "Initializing system_server CrashMonitor hooks...")
            hookBackgroundActivityStart(module, classLoader)
            hookAppErrors(module, classLoader)
            hookRescueParty(module, classLoader)
            Log.i(TAG, "CrashMonitor successfully initialized in system_server")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing CrashMonitor in system_server", t)
        }
    }

    private fun isModulePackage(pkg: String?): Boolean {
        return pkg == MODULE_PACKAGE || pkg == "dev.joseph.hyperbounce"
    }

    private fun handleCrashLogic(
        context: Context,
        packageName: String,
        time: Long,
        crashInfo: ApplicationErrorReport.CrashInfo?,
        shortMsg: String?,
        stackTrace: String?
    ) {
        if (!CrashScope.isScopeApp(packageName)) return

        try {
            val resolver = context.contentResolver
            val raw = Settings.System.getString(resolver, CrashScope.CRASH_RECORD_KEY)
            val records = DataCrashRecord.parseList(raw)

            var shouldTrigger = false
            var matchIndex = -1

            // 1. Evict records older than sliding window (60s)
            val iterator = records.iterator()
            while (iterator.hasNext()) {
                val r = iterator.next()
                if (time - r.time > CRASH_TIME_WINDOW) {
                    iterator.remove()
                }
            }

            // 2. Check for matching package
            for (i in records.indices) {
                val r = records[i]
                if (r.pkg == packageName) {
                    matchIndex = i
                    if (r.count >= 2) {
                        shouldTrigger = true
                    } else {
                        records[i] = r.copy(count = r.count + 1, time = time)
                    }
                    break
                }
            }

            if (matchIndex == -1) {
                records.add(DataCrashRecord(packageName, time, 1))
            }

            if (shouldTrigger) {
                Log.e(TAG, "[CRASH LIMIT HIT] Package $packageName crashed 2 times within 60s! Tripping Safe Mode.")
                records.removeIf { it.pkg == packageName }
                Settings.System.putString(resolver, CrashScope.CRASH_RECORD_KEY, DataCrashRecord.listToJsonString(records))
                SafeModeHandler.onCrashDetected(context, packageName, crashInfo, shortMsg, stackTrace)
            } else {
                Settings.System.putString(resolver, CrashScope.CRASH_RECORD_KEY, DataCrashRecord.listToJsonString(records))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing crash logic for $packageName", t)
        }
    }

    private fun hookAppErrors(module: XposedModule, classLoader: ClassLoader) {
        try {
            val appErrorsClass = Class.forName("com.android.server.am.AppErrors", false, classLoader)
            for (m in appErrorsClass.declaredMethods) {
                if (m.name == "handleAppCrashInActivityController" && m.returnType == Boolean::class.javaPrimitiveType) {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val thisObj = chain.thisObject ?: return result
                                val contextField = try {
                                    thisObj.javaClass.getDeclaredField("mContext").apply { isAccessible = true }
                                } catch (_: Throwable) { null }
                                val context = (contextField?.get(thisObj) as? Context) ?: return result

                                val r = chain.args.getOrNull(0) ?: return result
                                val infoField = try {
                                    r.javaClass.getField("info")
                                } catch (_: Throwable) {
                                    r.javaClass.getDeclaredField("info").apply { isAccessible = true }
                                }
                                val appInfo = infoField.get(r) as? ApplicationInfo
                                val packageName = appInfo?.packageName ?: return result

                                val crashInfo = chain.args.getOrNull(1) as? ApplicationErrorReport.CrashInfo
                                val shortMsg = chain.args.getOrNull(3) as? String
                                val stackTrace = chain.args.getOrNull(4) as? String
                                val time = (chain.args.getOrNull(5) as? Long) ?: System.currentTimeMillis()

                                Log.d(TAG, "Captured crash in AppErrors: $packageName, msg: $shortMsg")
                                handleCrashLogic(context, packageName, time, crashInfo, shortMsg, stackTrace)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error handling crash interception in AppErrors", t)
                            }
                            return result
                        }
                    })
                    Log.i(TAG, "Hooked AppErrors#handleAppCrashInActivityController")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking com.android.server.am.AppErrors", t)
        }
    }

    private fun hookRescueParty(module: XposedModule, classLoader: ClassLoader) {
        val watchdogClasses = listOf(
            "com.android.server.PackageWatchdogImpl",
            "com.android.server.PackageWatchdog"
        )
        for (className in watchdogClasses) {
            try {
                val watchdogClass = Class.forName(className, false, classLoader)
                for (m in watchdogClass.declaredMethods) {
                    if (m.name == "setCrashApplicationLevel" && m.parameterTypes.size == 3) {
                        m.isAccessible = true
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                try {
                                    val level = chain.args.getOrNull(0) as? Int ?: 0
                                    val versionedPkg = chain.args.getOrNull(1) as? VersionedPackage
                                    val context = chain.args.getOrNull(2) as? Context
                                    val packageName = versionedPkg?.packageName

                                    if (packageName != null && CrashScope.isScopeApp(packageName) && context != null) {
                                        Log.w(TAG, "RescueParty triggered for $packageName (level $level). Neutralizing RescueParty...")
                                        if (SafeModeHandler.onCrashDetected(context, packageName, null, "RescueParty Triggered", "System detected frequent crashes")) {
                                            resetSystemRescueLevel(context, packageName)
                                            return true // Prevent RescueParty escalation/recovery reboot
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error handling RescueParty setCrashApplicationLevel hook", t)
                                }
                                return chain.proceed()
                            }
                        })
                        Log.i(TAG, "Hooked $className#setCrashApplicationLevel")
                    } else if (m.name.startsWith("doRescuePartyPlusStep")) {
                        m.isAccessible = true
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                try {
                                    val versionedPkg = chain.args.getOrNull(1) as? VersionedPackage
                                    val packageName = versionedPkg?.packageName
                                    if (packageName != null && CrashScope.isScopeApp(packageName)) {
                                        Log.w(TAG, "Suppressed RescuePartyPlus step for $packageName")
                                        return true
                                    }
                                } catch (_: Throwable) {}
                                return chain.proceed()
                            }
                        })
                        Log.i(TAG, "Hooked $className#${m.name}")
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun resetSystemRescueLevel(context: Context, packageName: String) {
        try {
            val resolver = context.contentResolver
            if (packageName == "com.android.systemui" || packageName == "miui.systemui.plugin") {
                Settings.Global.putInt(resolver, "sys.rescueparty.systemui.level", 0)
                Settings.Global.putInt(resolver, "sys.anr.rescue.systemui.level", 0)
            } else if (packageName == "com.miui.home" || packageName == "com.mi.android.globallauncher") {
                Settings.Global.putInt(resolver, "sys.rescueparty.home.level", 0)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed resetting system rescue level for $packageName", t)
        }
    }

    private fun hookBackgroundActivityStart(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook BackgroundActivityStartController
        try {
            val bascClass = Class.forName("com.android.server.wm.BackgroundActivityStartController", false, classLoader)
            val balVerdictClass = try {
                Class.forName("com.android.server.wm.BackgroundActivityStartController\$BalVerdict", false, classLoader)
            } catch (_: Throwable) { null }

            val allowByDefaultVerdict = resolveAllowByDefaultVerdict(balVerdictClass)

            for (m in bascClass.declaredMethods) {
                if (m.name == "abortLaunch") {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val state = chain.args.getOrNull(0)
                            if (state != null && shouldAllowBackgroundStart(state)) {
                                return allowByDefaultVerdict ?: chain.proceed()
                            }
                            return chain.proceed()
                        }
                    })
                    Log.i(TAG, "Hooked BackgroundActivityStartController#abortLaunch")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BackgroundActivityStartController not hookable: ${t.message}")
        }

        // 2. Hook ActivityStarterStub (MIUI specific)
        try {
            val stubClass = Class.forName("com.android.server.wm.ActivityStarterStub", false, classLoader)
            for (m in stubClass.declaredMethods) {
                if (m.name == "isAllowedStartActivity" && m.parameterTypes.size == 3) {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val pkg = chain.args.getOrNull(2) as? String
                            if (isModulePackage(pkg)) {
                                return true
                            }
                            return chain.proceed()
                        }
                    })
                    Log.i(TAG, "Hooked ActivityStarterStub#isAllowedStartActivity")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ActivityStarterStub not found: ${t.message}")
        }
    }

    private fun resolveAllowByDefaultVerdict(balVerdictClass: Class<*>?): Any? {
        if (balVerdictClass == null) return null
        return try {
            val allowField = try {
                balVerdictClass.getField("ALLOW_BY_DEFAULT")
            } catch (_: Throwable) {
                balVerdictClass.getField("BAL_ALLOW_DEFAULT")
            }
            allowField.get(null)
        } catch (_: Throwable) {
            try {
                for (ctor in balVerdictClass.declaredConstructors) {
                    ctor.isAccessible = true
                    val params = ctor.parameterTypes
                    if (params.size == 3 && params[0] == Int::class.javaPrimitiveType && params[1] == Boolean::class.javaPrimitiveType) {
                        return ctor.newInstance(1, false, "Default")
                    } else if (params.size == 2 && params[0] == Int::class.javaPrimitiveType) {
                        return ctor.newInstance(1, "Default")
                    }
                }
                null
            } catch (_: Throwable) {
                null
            }
        }
    }

    @Volatile private var callingPackageField: java.lang.reflect.Field? = null
    @Volatile private var realCallingPackageField: java.lang.reflect.Field? = null

    private fun shouldAllowBackgroundStart(state: Any): Boolean {
        return try {
            var f1 = callingPackageField
            if (f1 == null) {
                f1 = state.javaClass.getDeclaredField("mCallingPackage").apply { isAccessible = true }
                callingPackageField = f1
            }
            var f2 = realCallingPackageField
            if (f2 == null) {
                f2 = state.javaClass.getDeclaredField("mRealCallingPackage").apply { isAccessible = true }
                realCallingPackageField = f2
            }

            val calling = f1?.get(state) as? String
            val realCalling = f2?.get(state) as? String
            isModulePackage(calling) || isModulePackage(realCalling)
        } catch (_: Throwable) {
            false
        }
    }
}
