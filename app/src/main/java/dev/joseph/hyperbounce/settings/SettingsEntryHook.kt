package dev.joseph.hyperbounce.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Hooks com.android.settings.MiuiSettings#updateHeaderList(List) to insert
 * a "HyperiOS" entry at the top position (right after "my_device"), matching
 * HyperCeiler's architecture.
 */
object SettingsEntryHook {
    private const val TAG = "HyperBounce_SettingsEntry"
    private const val HEADER_ID = 666L
    @Volatile
    private var isLoaderAdded = false

    fun init(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
        try {
            val miuiSettingsClass = Class.forName("com.android.settings.MiuiSettings", false, classLoader)
            val headerClass = Class.forName("com.android.settingslib.miuisettings.preference.PreferenceActivity\$Header", false, classLoader)

            var targetMethod: Method? = null
            for (m in miuiSettingsClass.declaredMethods) {
                if (m.name == "updateHeaderList" && m.parameterTypes.size == 1 && List::class.java.isAssignableFrom(m.parameterTypes[0])) {
                    targetMethod = m
                    break
                }
            }

            if (targetMethod == null) {
                Log.w(TAG, "updateHeaderList method not found in MiuiSettings")
                return
            }

            targetMethod.isAccessible = true

            module.hook(targetMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val isEnabled = prefs?.getBoolean("settings_app_entry_enabled", false) ?: false
                        if (!isEnabled) return result

                        val activity = chain.thisObject as? Activity ?: return result
                        val context = activity.baseContext ?: activity
                        @Suppress("UNCHECKED_CAST")
                        val list = chain.args[0] as? MutableList<Any?> ?: return result

                        // Check if header with ID 666 is already added
                        val alreadyAdded = list.any { item ->
                            if (item == null) false
                            else {
                                try {
                                    val idField = item.javaClass.getField("id")
                                    idField.getLong(item) == HEADER_ID
                                } catch (_: Throwable) {
                                    false
                                }
                            }
                        }
                        if (alreadyAdded) return result

                        // Create Header instance
                        val header = headerClass.getDeclaredConstructor().newInstance()

                        // Set ID
                        try {
                            headerClass.getField("id").setLong(header, HEADER_ID)
                        } catch (_: Throwable) {}

                        // Set Title
                        try {
                            headerClass.getField("title").set(header, "HyperiOS")
                        } catch (_: Throwable) {}

                        val iconStyleStr = prefs?.getString("app_icon_style", "Monet") ?: "Monet"
                        val isFlat = iconStyleStr.equals("Flat", ignoreCase = true)

                        // Inject module resources into Settings so it can resolve our app icon and drawables
                        var appIconDrawable: android.graphics.drawable.Drawable? = null
                        var iconRes = 0
                        try {
                            val pkgName = "dev.hyperios.app"
                            val appInfo = try {
                                context.packageManager.getApplicationInfo(pkgName, 0)
                            } catch (_: Throwable) {
                                context.packageManager.getApplicationInfo("dev.joseph.hyperbounce", 0)
                            }

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !isLoaderAdded) {
                                try {
                                    val apkFile = java.io.File(appInfo.sourceDir)
                                    val pfd = android.os.ParcelFileDescriptor.open(apkFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                    val provider = android.content.res.loader.ResourcesProvider.loadFromApk(pfd)
                                    val loader = android.content.res.loader.ResourcesLoader()
                                    loader.addProvider(provider)
                                    context.resources.addLoaders(loader)
                                    activity.resources.addLoaders(loader)
                                    pfd.close()
                                    isLoaderAdded = true
                                } catch (e: Throwable) {
                                    Log.w(TAG, "Failed to add ResourcesLoader: ${e.message}")
                                }
                            }

                            val iconDrawableName = if (isFlat) "ic_settings_entry_flat" else "ic_settings_entry_monet"

                            // 1. Try to load the 28dp squircle drawable directly from module package context
                            try {
                                val moduleContext = context.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY)
                                val modResId = moduleContext.resources.getIdentifier(iconDrawableName, "drawable", pkgName)
                                if (modResId != 0) {
                                    appIconDrawable = moduleContext.getDrawable(modResId)
                                }
                            } catch (_: Throwable) {}

                            // 2. Resolve resource ID in injected resources
                            iconRes = context.resources.getIdentifier(iconDrawableName, "drawable", pkgName)
                            if (iconRes == 0) {
                                iconRes = activity.resources.getIdentifier(iconDrawableName, "drawable", pkgName)
                            }

                            if (appIconDrawable == null && iconRes != 0) {
                                try {
                                    appIconDrawable = context.resources.getDrawable(iconRes, context.theme)
                                } catch (_: Throwable) {}
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed resolving module info: ${t.message}")
                        }

                        // Set Icon
                        try {
                            if (iconRes != 0) {
                                headerClass.getField("iconRes").setInt(header, iconRes)
                            } else {
                                val fallbackRes = context.resources.getIdentifier("ic_my_device", "drawable", context.packageName)
                                headerClass.getField("iconRes").setInt(header, fallbackRes)
                            }
                        } catch (_: Throwable) {}

                        // Also set 'icon' Drawable field if available on Header
                        if (appIconDrawable != null) {
                            try {
                                headerClass.getField("icon").set(header, appIconDrawable)
                            } catch (_: Throwable) {}
                        }

                        // Set Intent
                        try {
                            val intent = Intent().apply {
                                putExtra("isDisplayHomeAsUpEnabled", true)
                                setClassName("dev.hyperios.app", "dev.hyperios.app.ui.MainActivity")
                            }
                            headerClass.getField("intent").set(header, intent)
                        } catch (_: Throwable) {}

                        // Set Extras with header_user
                        try {
                            val bundle = Bundle()
                            bundle.putParcelableArrayList("header_user", arrayListOf(Process.myUserHandle()))
                            headerClass.getField("extras").set(header, bundle)
                        } catch (_: Throwable) {}

                        // Insert at Top position (right after my_device)
                        val myDeviceId = try {
                            context.resources.getIdentifier("my_device", "id", context.packageName)
                        } catch (_: Throwable) { 0 }

                        var insertIndex = -1
                        if (myDeviceId != 0) {
                            for (i in list.indices) {
                                val item = list[i] ?: continue
                                try {
                                    val id = item.javaClass.getField("id").getLong(item)
                                    if (id == myDeviceId.toLong()) {
                                        insertIndex = i + 1
                                        break
                                    }
                                } catch (_: Throwable) {}
                            }
                        }

                        if (insertIndex in 0..list.size) {
                            list.add(insertIndex, header)
                        } else if (list.isNotEmpty()) {
                            list.add(1.coerceAtMost(list.size), header)
                        } else {
                            list.add(header)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error inserting HyperiOS header in Settings", t)
                    }
                    return result
                }
            })

            Log.i(TAG, "SettingsEntryHook successfully initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SettingsEntryHook", t)
        }
    }
}
