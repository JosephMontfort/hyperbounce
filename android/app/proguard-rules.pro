# Keep line numbers and essential source debugging info for crash logging
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# LibXposed Framework Entry Points
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# Keep all XposedModule implementations and their public constructors/methods
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
    public void onLoadPackage(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
    public void onSystemServerStarting(io.github.libxposed.api.XposedModuleInterface$SystemServerStartingParam);
}

# Keep libxposed service and API classes
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# Keep our module hook entry classes and all their methods/fields
-keep class dev.hyperios.app.PhysicsHook { *; }
-keep class dev.hyperios.app.toggles.LiquidTogglesHook { *; }

# Keep all internal hook classes and reflection helpers intact so no logic is pruned
-keep class dev.hyperios.app.nav.** { *; }
-keep class dev.hyperios.app.toggles.** { *; }
-keep class dev.hyperios.app.launcher.** { *; }
-keep class dev.hyperios.app.lockscreen.** { *; }
-keep class dev.hyperios.app.notification.** { *; }
-keep class dev.hyperios.app.controlcenter.** { *; }
-keep class dev.hyperios.app.dialer.** { *; }
-keep class dev.hyperios.app.caller.** { *; }
-keep class dev.hyperios.app.haptics.** { *; }
-keep class dev.hyperios.app.settings.** { *; }
-keep class dev.hyperios.app.safemode.** { *; }
-keep class dev.hyperios.app.prefs.** { *; }

# Android Manifest components & ContentProvider
-keep class dev.hyperios.app.ConfigProvider { *; }
-keep class dev.hyperios.app.ui.MainActivity { *; }
-keep class dev.hyperios.app.safemode.CrashActivity { *; }
-keep class dev.hyperios.app.safemode.ExceptionCrashActivity { *; }

# Flutter Engine & Embedding
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**

# Jetpack Compose & Miuix Rules
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class top.yukonga.miuix.** { *; }
-dontwarn top.yukonga.miuix.**
-dontwarn com.kyant.backdrop.**
-dontwarn com.kyant.shapes.**
