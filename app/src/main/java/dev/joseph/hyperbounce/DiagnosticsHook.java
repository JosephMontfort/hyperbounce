package dev.joseph.hyperbounce;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DiagnosticsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Trace";
    private static long lastLogTime = 0;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        
        if (!param.getPackageName().equals("com.miui.home")) return;

        log(Log.ERROR, TAG, "[*] Injecting ULTIMATE MIUI Home Animator Trap...");

        // 1. Hook the core OS Animator to catch EVERY animation the launcher plays
        try {
            Method animStart = android.animation.Animator.class.getDeclaredMethod("start");
            hook(animStart).intercept(chain -> {
                long now = System.currentTimeMillis();
                
                // Rate-limit the stack traces slightly so we don't crash the Android logger
                if (now - lastLogTime > 50) {
                    String stack = Log.getStackTraceString(new Exception());
                    
                    // Only log if the animation was triggered by MIUI Home
                    if (stack.contains("com.miui.home")) {
                        lastLogTime = now;
                        log(Log.ERROR, TAG, "\n[+] ANIMATOR STARTED! Trace:");
                        
                        String[] lines = stack.split("\n");
                        StringBuilder sb = new StringBuilder();
                        // Print the top 15 lines of the stack trace to track down the exact MIUI trigger class
                        for(int i = 1; i < Math.min(15, lines.length); i++) {
                            sb.append(lines[i]).append("\n");
                        }
                        log(Log.ERROR, TAG, sb.toString());
                    }
                }
                return chain.proceed();
            });
            log(Log.ERROR, TAG, "[+] Hooked android.animation.Animator.start()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "[-] Failed to hook Animator", t);
        }

        // 2. Hook Launcher lifecycle to set visual markers when you unlock
        try {
            Class<?> launcherClass = Class.forName("com.miui.home.launcher.Launcher", false, param.getClassLoader());
            String[] lifecycles = {"onResume", "onStart", "onPause"};
            
            for (String methodName : lifecycles) {
                try {
                    Method m = launcherClass.getDeclaredMethod(methodName);
                    hook(m).intercept(chain -> {
                        log(Log.ERROR, TAG, "\n========================================");
                        log(Log.ERROR, TAG, "[>>>] LAUNCHER LIFECYCLE: " + methodName);
                        log(Log.ERROR, TAG, "========================================\n");
                        return chain.proceed();
                    });
                } catch (Throwable t) {}
            }
            log(Log.ERROR, TAG, "[+] Hooked Launcher Lifecycle markers");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "[-] Failed to hook Launcher lifecycle");
        }

        // 3. Broad tracking of standard Workspace methods (Names only, no stacks to avoid lag)
        try {
            Class<?> workspaceClass = Class.forName("com.miui.home.launcher.Workspace", false, param.getClassLoader());
            for (Method m : workspaceClass.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (Modifier.isNative(mod) || Modifier.isAbstract(mod)) continue;
                String name = m.getName();
                if (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) continue;
                
                try {
                    hook(m).intercept(chain -> {
                        log(Log.ERROR, TAG, "[CALL] Workspace." + name);
                        return chain.proceed();
                    });
                } catch (Throwable t) {}
            }
            log(Log.ERROR, TAG, "[+] Hooked Workspace methods");
        } catch (Throwable t) {}
    }
}
