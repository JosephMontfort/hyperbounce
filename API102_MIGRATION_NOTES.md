# HyperBounce — libxposed API 102 port

## Scope
Ported the complete `hyperbounce-liquid-toggles` source from the legacy XposedBridge-style API to the supplied libxposed API 102.0.0 artifacts.

## Legacy → modern migration
- `IXposedHookLoadPackage` / `handleLoadPackage()` → `XposedModule.onPackageReady()`.
- `LoadPackageParam.classLoader` → `PackageReadyParam.getClassLoader()`.
- `XposedHelpers.findClassIfExists()` → class-loader-aware `Class.forName(..., false, loader)` helper.
- `XposedHelpers.findAndHookMethod()` → reflection to resolve the exact `Method`, then `XposedInterface.hook(executable).intercept(...)`.
- `XC_MethodHook` before/after callbacks → interceptor-chain `Hooker` implementations using `chain.proceed()`.
- `XC_MethodReplacement` → interceptor that returns the replacement value without `chain.proceed()`.
- `XposedBridge.hookAllConstructors()` → iterate `getDeclaredConstructors()` and hook each constructor.
- `XposedBridge.invokeOriginalMethod()` → `chain.proceed()`, or direct reflective invocation only where the legacy replacement explicitly invoked another method.
- `XposedHelpers.callMethod/newInstance/getObjectField/callStaticMethod()` → local reflection helpers with class-loader-aware lookup.
- `assets/xposed_init` → `src/main/resources/META-INF/xposed/java_init.list`.
- legacy manifest Xposed metadata → modern `android:label`/`android:description` plus `scope.list` and `module.prop`.

## API 102 metadata
- `minApiVersion=101`
- `targetApiVersion=102`
- `staticScope=true`

## Scope preserved
- `com.android.systemui`
- `miui.systemui.plugin`
- `com.android.settings`
- `com.android.phone`
- `com.android.contacts`

## Important system-app detail
Target-dependent AndroidX/vendor classes are resolved through the target process' class loader. In particular, `androidx.preference.PreferenceViewHolder` is not resolved through the module's own class loader.

## Build verification
A full Gradle build was attempted. The project wrapper requires Gradle 9.6.0, but this execution environment cannot reach `services.gradle.org`, so dependency/toolchain resolution could not complete here. The source tree was still statically checked for legacy Xposed API references and modern API metadata/entrypoint correctness.

## Included API artifact
The supplied `api-102.0.0.aar` is included under `app/libs/` and referenced with `compileOnly(files("libs/api-102.0.0.aar"))`.

The supplied service/interface/lint artifacts were inspected; this module does not need the service/interface artifacts for its current behavior because it uses the core API only.
