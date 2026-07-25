package dev.joseph.hyperbounce;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // ==========================================
        // 1. DYNAMIC ISLAND FLUIDITY REWRITE
        // Target both the Plugin and SystemUI ClassLoaders
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            
            // --- A. INJECT APPLE SPRING PHYSICS ---
            try {
                Class<?> folmeEaseClass = XposedHelpers.findClassIfExists("miuix.animation.FolmeEase", lpparam.classLoader);
                if (folmeEaseClass != null) {
                    XposedHelpers.findAndHookMethod(folmeEaseClass, "spring", float.class, float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // Override Xiaomi's stiff physics with iOS underdamped fluid dynamics.
                            // This globally affects Swipes, Clicks, and Multi-Island splits.
                            param.args[0] = 0.65f; // Damping (Lower = more bounciness, iOS is ~0.65)
                            param.args[1] = 0.35f; // Response Time (Lower = faster expansion)
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Physics Override Error: " + t.getMessage());
            }

            // --- B. FIX THE TRANSPARENT BOX GLITCH ---
            try {
                Class<?> bgViewClass = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.DynamicIslandBackgroundView", lpparam.classLoader);
                if (bgViewClass != null) {
                    XposedHelpers.findAndHookMethod(bgViewClass, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            View view = (View) param.thisObject;
                            float targetAlpha = (float) param.args[0];
                            
                            // If the system tries to make the box visible (Alpha > 0)
                            // BUT the height is 0 (uninflated) or massive (rendering glitch)
                            // We force the alpha to stay 0 until the layout bounds stabilize.
                            if (targetAlpha > 0.1f) {
                                int height = view.getHeight();
                                if (height == 0 || height > 500) {
                                    param.args[0] = 0f; // Suppress the glitch frame
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Alpha Glitch Fix Error: " + t.getMessage());
            }
        }

        // ==========================================
        // 2. LAUNCHER FLUIDITY (NO LAG INTERRUPTION)
        // ==========================================
        if (lpparam.packageName.equals("com.miui.home")) {
            try {
                Class<?> rectFSpringAnimClass = XposedHelpers.findClassIfExists("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader);
                if (rectFSpringAnimClass != null) {
                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setDamping", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = 0.45f; 
                        }
                    });
                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setStiffness", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            float currentStiffness = (float) param.args[0];
                            if (currentStiffness < 300.0f) {
                                param.args[0] = 350.0f;
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Launcher Hook Error: " + t.getMessage());
            }
        }
    }
}
