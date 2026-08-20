package dev.joseph.hyperbounce;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class DiagnosticsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Trace";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        
        if (!param.getPackageName().equals("com.miui.home")) return;

        Log.e(TAG, "[*] Injecting MIUI Home Unlock Tracer...");

        String[][] targets = {
            {"com.miui.home.launcher.anim.UnlockAnimationManager", "startUnlockAnimation"},
            {"com.miui.home.launcher.anim.UnlockAnimationManager", "playUnlockAnimation"},
            {"com.miui.home.launcher.anim.LaunchAndExitAnimator", "startUnlockAnimation"},
            {"com.miui.home.recents.util.UnlockAnimationPlugin", "startUnlockAnimation"},
            {"com.miui.home.launcher.Workspace", "onUnlockAnimationStart"},
            {"com.miui.home.launcher.Workspace", "startUnlockAnimation"}
        };

        for (String[] target : targets) {
            try {
                Class<?> clazz = Class.forName(target[0], false, param.getClassLoader());
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(target[1])) {
                        try {
                            hook(m).intercept(chain -> {
                                Log.e(TAG, "\n[+] TRIGGERED: " + target[0] + "." + target[1]);
                                String stack = Log.getStackTraceString(new Exception("UnlockTrace"));
                                String[] lines = stack.split("\n");
                                StringBuilder sb = new StringBuilder();
                                // Print the first 8 lines of the stack trace to see the caller
                                for(int i = 0; i < Math.min(8, lines.length); i++) {
                                    sb.append(lines[i]).append("\n");
                                }
                                Log.e(TAG, sb.toString());
                                return chain.proceed();
                            });
                            Log.e(TAG, "[+] Successfully hooked: " + target[1] + " in " + target[0]);
                        } catch (Throwable t) {
                            Log.e(TAG, "[-] Failed to hook " + target[1], t);
                        }
                    }
                }
            } catch (Throwable t) {
                // Silently skip if class doesn't exist on this MIUI version
            }
        }
    }
}
