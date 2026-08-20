package dev.joseph.hyperbounce;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DiagnosticsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Trace";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        
        if (!param.getPackageName().equals("com.miui.home")) return;

        // Using LibXposed's native logger to bypass MIUI log suppression
        log(Log.ERROR, TAG, "[*] Injecting MIUI Home Unlock Tracer...");

        String[] targetClasses = {
            "com.miui.home.launcher.anim.UnlockAnimationManager",
            "com.miui.home.launcher.anim.LaunchAndExitAnimator",
            "com.miui.home.recents.util.UnlockAnimationPlugin",
            "com.miui.home.launcher.Workspace"
        };

        for (String className : targetClasses) {
            try {
                Class<?> clazz = Class.forName(className, false, param.getClassLoader());
                for (Method m : clazz.getDeclaredMethods()) {
                    int modifiers = m.getModifiers();
                    // Skip abstract, native, or synthetic methods to prevent ART Thread crashes
                    if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers) || (modifiers & 0x1000) != 0) {
                        continue;
                    }

                    String name = m.getName();
                    // Hook ANY method containing "unlock" (ignoring generic getters/setters)
                    if (name.toLowerCase().contains("unlock") && !name.startsWith("get") && !name.startsWith("set")) {
                        try {
                            hook(m).intercept(chain -> {
                                log(Log.ERROR, TAG, "\n[+] TRIGGERED: " + className + "." + name);
                                String stack = Log.getStackTraceString(new Exception("UnlockTrace"));
                                String[] lines = stack.split("\n");
                                StringBuilder sb = new StringBuilder();
                                // Print the first 6 lines of the stack trace to see the caller
                                for(int i = 0; i < Math.min(6, lines.length); i++) {
                                    sb.append(lines[i]).append("\n");
                                }
                                log(Log.ERROR, TAG, sb.toString());
                                return chain.proceed();
                            });
                            log(Log.ERROR, TAG, "[+] Hooked: " + name + " in " + className);
                        } catch (Throwable t) {
                            // Silently skip if method mapping fails
                        }
                    }
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "[-] Class not found on this firmware: " + className);
            }
        }
    }
}
