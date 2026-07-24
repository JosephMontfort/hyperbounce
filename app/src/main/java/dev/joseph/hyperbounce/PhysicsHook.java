package dev.joseph.hyperbounce;

import android.view.View;
import android.view.animation.OvershootInterpolator;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // ==========================================
        // 1. CONTROL CENTER TILE FLIP & EXPAND
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            try {
                XposedHelpers.findAndHookMethod(View.class, "performLongClick", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        final View view = (View) param.thisObject;
                        String viewName = view.getClass().getSimpleName();

                        // Target only Control Center / QS Tiles
                        if (viewName.contains("TileView") || viewName.contains("QS") || viewName.contains("ToggleSlider")) {
                            
                            // Cancel stock Folme animations instantly
                            view.animate().cancel();
                            view.setCameraDistance(10000 * view.getResources().getDisplayMetrics().density); // Add 3D depth

                            // Phase 1: Deep squish and start of the 3D flip (Velocity catch)
                            view.animate()
                                .scaleX(0.85f)
                                .scaleY(0.85f)
                                .rotationYBy(90f) // Flip halfway
                                .setDuration(120)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Phase 2: iOS-style hyper-fluid expand and finish flip
                                        view.animate()
                                            .scaleX(1.05f)
                                            .scaleY(1.05f)
                                            .rotationYBy(90f) // Complete the flip
                                            .setDuration(350)
                                            .setInterpolator(new OvershootInterpolator(1.6f))
                                            .withEndAction(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // Settle back to normal size seamlessly
                                                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                                                }
                                            })
                                            .start();
                                    }
                                })
                                .start();
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce QS Tile Hook Error: " + t.getMessage());
            }
        }

        // ==========================================
        // 2. LAUNCHER FLUIDITY (NO LAG INTERRUPTION)
        // ==========================================
        if (lpparam.packageName.equals("com.miui.home")) {
            try {
                // Hook the exact RectFSpringAnim class found in our static analysis
                Class<?> rectFSpringAnimClass = XposedHelpers.findClassIfExists("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader);
                
                if (rectFSpringAnimClass != null) {
                    // Force all app closing animations to use underdamped iOS spring curves
                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setDamping", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 0.45f creates that beautiful liquid overshoot when an app hits its icon bounds
                            param.args[0] = 0.45f; 
                        }
                    });

                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setStiffness", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // Keep the velocity high so there is zero lag during gesture interruption
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
