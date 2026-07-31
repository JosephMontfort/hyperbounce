package dev.joseph.hyperbounce;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    private boolean hasLogged = false;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.whatsapp")) {
            XposedBridge.log("[HyperBounce-Recon] Injecting Reconnaissance Hook into WhatsApp...");
            hookWhatsAppForRecon(lpparam.classLoader);
        }
    }

    private void hookWhatsAppForRecon(ClassLoader classLoader) {
        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", classLoader);
        if (wdsBottomBarClass == null) {
            XposedBridge.log("[HyperBounce-Recon] ERROR: WDSBottomBar class not found!");
            return;
        }

        XposedBridge.hookAllMethods(wdsBottomBarClass, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                MotionEvent event = (MotionEvent) param.args[0];
                
                // Only run the dump once per app launch on the first tap to prevent log flooding
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !hasLogged) {
                    hasLogged = true;
                    View bar = (View) param.thisObject;
                    executeDeepRecon(bar);
                }
            }
        });
    }

    private void executeDeepRecon(View targetBar) {
        XposedBridge.log("\n========================================================");
        XposedBridge.log("[HyperBounce-Recon] INITIATING DEEP VIEW HIERARCHY DUMP");
        XposedBridge.log("========================================================\n");

        try {
            // 1. Trace upwards to find the black background
            XposedBridge.log("--- TRACING PARENTS UPWARDS ---");
            ViewParent parent = targetBar.getParent();
            int depth = 1;
            while (parent instanceof View) {
                logViewDetails((View) parent, "PARENT [-" + depth + "]");
                parent = parent.getParent();
                depth++;
                if (depth > 6) break; // Don't need to go all the way to the screen root
            }

            // 2. Dump the target bar itself
            XposedBridge.log("\n--- TARGET VIEW ---");
            logViewDetails(targetBar, "WDSBottomBar");

            // 3. Trace downwards to find the inner containers and indicators
            XposedBridge.log("\n--- TRACING CHILDREN DOWNWARDS ---");
            if (targetBar instanceof ViewGroup) {
                dumpChildrenRecursively((ViewGroup) targetBar, 1, "");
            }

            // 4. Dump Class Methods to find the Green Indicator / Background setters
            XposedBridge.log("\n--- REFLECTION: SUSPICIOUS METHODS IN WDSBottomBar ---");
            Method[] methods = targetBar.getClass().getMethods();
            for (Method m : methods) {
                String name = m.getName().toLowerCase();
                // Filter only for methods related to backgrounds, indicators, or colors
                if (name.contains("indicator") || name.contains("background") || name.contains("color") || name.contains("tint")) {
                    XposedBridge.log("Method Found: " + m.getName() + " | Params: " + m.getParameterTypes().length);
                }
            }

            XposedBridge.log("\n========================================================");
            XposedBridge.log("[HyperBounce-Recon] DEEP DUMP COMPLETE.");
            XposedBridge.log("========================================================\n");

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce-Recon] ERROR during dump: " + Log.getStackTraceString(e));
        }
    }

    private void dumpChildrenRecursively(ViewGroup vg, int depth, String prefix) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            logViewDetails(child, prefix + "CHILD [+" + depth + "." + i + "]");
            if (child instanceof ViewGroup) {
                dumpChildrenRecursively((ViewGroup) child, depth + 1, prefix + "  ");
            }
        }
    }

    private void logViewDetails(View v, String label) {
        String className = v.getClass().getName();
        int width = v.getWidth();
        int height = v.getHeight();
        
        // Extract layout parameters
        String lpInfo = "null";
        if (v.getLayoutParams() != null) {
            lpInfo = v.getLayoutParams().getClass().getSimpleName();
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                lpInfo += String.format(" (Margins: L%d T%d R%d B%d)", mlp.leftMargin, mlp.topMargin, mlp.rightMargin, mlp.bottomMargin);
            }
        }

        // Extract background details and exact color hex codes
        String bgInfo = "null";
        Drawable bg = v.getBackground();
        if (bg != null) {
            bgInfo = bg.getClass().getSimpleName();
            if (bg instanceof ColorDrawable) {
                bgInfo += String.format(" (Hex: #%08X)", ((ColorDrawable) bg).getColor());
            } else {
                bgInfo += " (Complex Drawable - Might be MaterialShapeDrawable)";
            }
        }

        XposedBridge.log(String.format("%s: %s [%dx%d]", label, className, width, height));
        XposedBridge.log(String.format("   -> Background: %s", bgInfo));
        XposedBridge.log(String.format("   -> LayoutParams: %s", lpInfo));
    }
}
