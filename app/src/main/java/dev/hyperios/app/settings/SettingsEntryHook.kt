package dev.hyperios.app.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hooks com.android.settings.MiuiSettings#updateHeaderList(List) to insert
 * a "HyperiOS" entry at the top position (right after "my_device"), matching
 * HyperCeiler and HyperLight's architecture.
 *
 * Intercepts Resources.getDrawable and HeaderAdapter.getView so the 28dp squircle
 * icon (Monet or Flat) renders cleanly without ever displaying stock About phone icon.
 */
object SettingsEntryHook {
    private const val TAG = "HyperBounce_SettingsEntry"
    private const val HEADER_ID = 666L
    // Use android.R.drawable.ic_menu_info_details (17301569) as placeholder framework ID
    // HyperLight uses ic_menu_help (17301568), so 17301569 avoids any ID clash.
    private const val SETTINGS_ENTRY_ICON_RES = android.R.drawable.ic_menu_info_details

    @Volatile
    private var cachedModuleContext: Context? = null
    private val isResolvingIcon = AtomicBoolean(false)

    fun init(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
        try {
            // 1. Hook Resources.getDrawable to dynamically intercept SETTINGS_ENTRY_ICON_RES
            hookResources(module, prefs)

            // 2. Try hooking ResourcesImpl.loadDrawable as deep framework fallback
            hookResourcesImpl(module, classLoader, prefs)

            // 3. Try hooking MiuiSettings$HeaderAdapter.getView for direct view binding
            hookHeaderAdapter(module, classLoader, prefs)

            // 4. Hook MiuiSettings.updateHeaderList to insert the HyperiOS header
            hookUpdateHeaderList(module, classLoader, prefs)

            Log.i(TAG, "SettingsEntryHook successfully initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SettingsEntryHook", t)
        }
    }

    private fun hookResources(module: XposedModule, prefs: SharedPreferences?) {
        // Hook Resources.getDrawable(int id, Resources.Theme theme)
        try {
            val getDrawableThemeMethod = Resources::class.java.getDeclaredMethod(
                "getDrawable",
                Int::class.javaPrimitiveType,
                Resources.Theme::class.java
            )
            getDrawableThemeMethod.isAccessible = true
            module.hook(getDrawableThemeMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val id = chain.args[0] as? Int ?: return chain.proceed()
                    if (id == SETTINGS_ENTRY_ICON_RES) {
                        val theme = chain.args[1] as? Resources.Theme
                        val hostRes = chain.thisObject as? Resources
                        val custom = getCustomIconDrawable(hostRes, theme, prefs)
                        if (custom != null) {
                            return custom
                        }
                    }
                    return chain.proceed()
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking Resources.getDrawable(int, Theme): ${t.message}")
        }

        // Hook Resources.getDrawable(int id)
        try {
            val getDrawableMethod = Resources::class.java.getDeclaredMethod(
                "getDrawable",
                Int::class.javaPrimitiveType
            )
            getDrawableMethod.isAccessible = true
            module.hook(getDrawableMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val id = chain.args[0] as? Int ?: return chain.proceed()
                    if (id == SETTINGS_ENTRY_ICON_RES) {
                        val hostRes = chain.thisObject as? Resources
                        val custom = getCustomIconDrawable(hostRes, null, prefs)
                        if (custom != null) {
                            return custom
                        }
                    }
                    return chain.proceed()
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking Resources.getDrawable(int): ${t.message}")
        }
    }

    private fun hookResourcesImpl(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
        try {
            val implClass = Class.forName("android.content.res.ResourcesImpl", false, classLoader)
            for (m in implClass.declaredMethods) {
                if (m.name == "loadDrawable" && m.parameterTypes.size >= 4) {
                    m.isAccessible = true
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val id = chain.args.getOrNull(2) as? Int ?: return chain.proceed()
                            if (id == SETTINGS_ENTRY_ICON_RES) {
                                val wrapper = chain.args.firstOrNull() as? Resources
                                val theme = chain.args.lastOrNull() as? Resources.Theme
                                val custom = getCustomIconDrawable(wrapper, theme, prefs)
                                if (custom != null) {
                                    return custom
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking ResourcesImpl.loadDrawable: ${t.message}")
        }
    }

    private fun hookHeaderAdapter(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
        try {
            val adapterClasses = listOf(
                "com.android.settings.MiuiSettings\$HeaderAdapter",
                "com.android.settingslib.miuisettings.preference.PreferenceActivity\$HeaderAdapter"
            )
            for (className in adapterClasses) {
                val cls = try {
                    Class.forName(className, false, classLoader)
                } catch (_: Throwable) {
                    continue
                }
                for (m in cls.declaredMethods) {
                    if (m.name == "getView" && m.parameterTypes.size == 3) {
                        m.isAccessible = true
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val view = chain.proceed() as? View ?: return chain.proceed()
                                try {
                                    val position = chain.args[0] as? Int ?: return view
                                    val adapter = chain.thisObject ?: return view
                                    val getItemMethod = adapter.javaClass.getMethod("getItem", Int::class.javaPrimitiveType)
                                    val item = getItemMethod.invoke(adapter, position) ?: return view
                                    val idField = item.javaClass.getField("id")
                                    if (idField.getLong(item) == HEADER_ID) {
                                        val iconView = view.findViewById<ImageView>(android.R.id.icon)
                                            ?: view.findViewById<ImageView>(view.resources.getIdentifier("icon", "id", "android"))
                                            ?: view.findViewById<ImageView>(view.resources.getIdentifier("icon", "id", view.context.packageName))
                                        if (iconView != null) {
                                            val custom = getCustomIconDrawable(view.resources, view.context.theme, prefs, view.context)
                                            if (custom != null) {
                                                iconView.setImageDrawable(custom)
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {}
                                return view
                            }
                        })
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed hooking HeaderAdapter: ${t.message}")
        }
    }

    private fun hookUpdateHeaderList(module: XposedModule, classLoader: ClassLoader, prefs: SharedPreferences?) {
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

                    // Cache module context from host activity
                    ensureModuleContext(context)

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

                    // Set Icon Resource ID to our interceptable framework ID
                    try {
                        headerClass.getField("iconRes").setInt(header, SETTINGS_ENTRY_ICON_RES)
                    } catch (_: Throwable) {}

                    // Also set 'icon' Drawable field if available on Header
                    val customDrawable = getCustomIconDrawable(context.resources, activity.theme, prefs, context)
                    if (customDrawable != null) {
                        try {
                            headerClass.getField("icon").set(header, customDrawable)
                        } catch (_: Throwable) {}
                    }

                    // Set Intent
                    try {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

                    // Insert right after "my_device" (About phone)
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
    }

    private fun ensureModuleContext(hostContext: Context?): Context? {
        cachedModuleContext?.let { return it }
        try {
            val ctx = hostContext ?: run {
                val actThreadClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = actThreadClass.getMethod("currentApplication")
                currentAppMethod.invoke(null) as? Context
            }
            if (ctx != null) {
                val pkgName = try {
                    ctx.packageManager.getApplicationInfo("dev.hyperios.app", 0)
                    "dev.hyperios.app"
                } catch (_: Throwable) {
                    "dev.hyperios.app"
                }
                val modCtx = ctx.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY)
                cachedModuleContext = modCtx
                return modCtx
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed ensuring module context: ${t.message}")
        }
        return null
    }

    private fun getCustomIconDrawable(
        hostResources: Resources?,
        theme: Resources.Theme?,
        prefs: SharedPreferences?,
        hostContext: Context? = null
    ): Drawable? {
        if (!isResolvingIcon.compareAndSet(false, true)) {
            return null
        }
        try {
            val iconStyleStr = prefs?.getString("app_icon_style", "Monet") ?: "Monet"
            val isFlat = iconStyleStr.equals("Flat", ignoreCase = true)
            val iconDrawableName = if (isFlat) "ic_settings_entry_flat" else "ic_settings_entry_monet"

            val modCtx = ensureModuleContext(hostContext)
            if (modCtx != null) {
                val res = modCtx.resources
                val resId = res.getIdentifier(iconDrawableName, "drawable", modCtx.packageName)
                if (resId != 0) {
                    val d = try {
                        res.getDrawable(resId, modCtx.theme)
                    } catch (_: Throwable) {
                        try {
                            res.getDrawable(resId, theme)
                        } catch (_: Throwable) {
                            res.getDrawable(resId, null)
                        }
                    }
                    if (d != null) {
                        return d.constantState?.newDrawable() ?: d
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed resolving custom icon drawable", t)
        } finally {
            isResolvingIcon.set(false)
        }
        return null
    }
}
