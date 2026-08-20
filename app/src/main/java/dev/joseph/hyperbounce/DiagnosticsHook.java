package dev.joseph.hyperbounce;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DiagnosticsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Trace";
    private static long lastLogTime = 0;
    private static boolean unlocked = false;

    // Writes directly to the Launcher's private storage (bypasses all Logcat filters)
    private void writeLog(String text) {
        Log.e(TAG, text);
        try {
            File f = new File("/data/data/com.miui.home/files/hyper_trace.txt");
            FileOutputStream fos = new FileOutputStream(f, true);
            PrintWriter pw = new PrintWriter(fos);
            pw.println(text);
            pw.flush();
            pw.close();
        } catch (Throwable t) {}
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        
        if (!param.getPackageName().equals("com.miui.home")) return;

        // Clear the old log file when the launcher starts
        try {
            new File("/data/data/com.miui.home/files/hyper_trace.txt").delete();
        } catch (Throwable t) {}

        writeLog("\n\n[*] === MIUI HOME HOOKED SUCCESSFULLY ===");

        // 1. Hook Launcher.onResume to know EXACTLY when the screen unlocks
        try {
            Class<?> launcherClass = Class.forName("com.miui.home.launcher.Launcher", false, param.getClassLoader());
            Method onResume = launcherClass.getDeclaredMethod("onResume");
            hook(onResume).intercept(chain -> {
                unlocked = true;
                writeLog("\n[>>>] SCREEN UNLOCKED (Launcher.onResume) [<<<]");
                return chain.proceed();
            });
        } catch (Throwable t) { 
            writeLog("[-] Failed to hook Launcher.onResume"); 
        }

        // 2. Catch the core Animation Engine
        try {
            Method startAnim = android.animation.ValueAnimator.class.getDeclaredMethod("start");
            hook(startAnim).intercept(chain -> {
                if (unlocked) {
                    long now = System.currentTimeMillis();
                    // Throttle to prevent lagging the UI
                    if (now - lastLogTime > 250) { 
                        String stack = Log.getStackTraceString(new Exception());
                        if (stack.contains("com.miui.home") && !stack.contains("com.miui.home.recents")) {
                            lastLogTime = now;
                            writeLog("\n[+] ANIMATION STARTED. Stack Trace:");
                            String[] lines = stack.split("\n");
                            for(int i = 1; i < Math.min(12, lines.length); i++) {
                                writeLog(lines[i]);
                            }
                        }
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {}
        
        // 3. Scan for specific MIUI Unlock triggers
        String[] targets = {
            "com.miui.home.launcher.anim.UnlockAnimationManager",
            "com.miui.home.launcher.anim.LaunchAndExitAnimator",
            "com.miui.home.recents.util.UnlockAnimationPlugin"
        };
        
        for (String cName : targets) {
            try {
                Class<?> clazz = Class.forName(cName, false, param.getClassLoader());
                for (Method m : clazz.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (Modifier.isAbstract(mod) || Modifier.isNative(mod) || (mod & 0x1000) != 0) continue;
                    if (m.getName().toLowerCase().contains("unlock")) {
                        hook(m).intercept(chain -> {
                            writeLog("\n[!] MIUI TRIGGERED: " + cName + "." + m.getName());
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {}
        }
    }
}
