package dev.joseph.hyperbounce;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("miui.systemui.plugin") && 
            !lpparam.packageName.equals("com.miui.home")) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                "miui.animation.physics.SpringForce",
                lpparam.classLoader,
                "setDampingRatio",
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        float currentDamping = (float) param.args[0];
                        // If HyperOS tries to make it rigid (overdamped > 0.8), force it to bounce (0.45)
                        if (currentDamping > 0.6f) {
                            param.args[0] = 0.45f;
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "miui.animation.physics.SpringForce",
                lpparam.classLoader,
                "setStiffness",
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Increase tension slightly so the bounce returns snappier
                        param.args[0] = 320.0f;
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("HyperBounce Error: " + t.getMessage());
        }
    }
}
