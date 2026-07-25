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
        // 1. SYSTEMUI & PLUGIN HOOKS
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            
            // --- A. DYNAMIC ISLAND 3D FLIP (HIJACKING FOLME ANIMATION ENGINE) ---
            try {
                // Hooking into Miui's core Folme state engine execution
                Class<?> animStateClass = XposedHelpers.findClassIfExists("miuix.animation.controller.StateFolme", lpparam.classLoader);
                
                if (animStateClass != null) {
                    XposedHelpers.findAndHookMethod(animStateClass, "to", Object[].class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // The Folme object holds a reference to the View being animated
                            Object targetObject = XposedHelpers.callMethod(param.thisObject, "getTarget");
                            
                            if (targetObject != null) {
                                Object viewObj = XposedHelpers.callMethod(targetObject, "getTargetObject");
                                
                                if (viewObj instanceof View) {
                                    final View view = (View) viewObj;
                                    String viewName = view.getClass().getSimpleName();

                                    // If Folme is animating the Dynamic Island
                                    if (viewName.contains("DynamicIslandContentView") || viewName.contains("PlayerIslandConstraintLayout")) {
                                        
                                        // Debounce check to prevent multiple rapid flips
                                        long now = System.currentTimeMillis();
                                        Object tag = view.getTag(android.R.id.accessibilityActionScrollDown);
                                        long lastFlip = tag != null ? (long) tag : 0;
                                        
                                        if (now - lastFlip > 600) {
                                            view.setTag(android.R.id.accessibilityActionScrollDown, now);
                                            
                                            // Run our 3D math on top of Folme's transition
                                            view.setCameraDistance(10000 * view.getResources().getDisplayMetrics().density);
                                            view.animate()
                                                .rotationXBy(360f)
                                                .scaleX(1.03f).scaleY(1.03f)
                                                .setDuration(400)
                                                .setInterpolator(new OvershootInterpolator(1.2f))
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                                                    }
                                                }).start();
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Folme Island Hook Error: " + t.getMessage());
            }

            // --- B. QS TILE FLIP ON LONG PRESS ---
            try {
                XposedHelpers.findAndHookMethod(View.class, "performLongClick", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        final View view = (View) param.thisObject;
                        String viewName = view.getClass().getSimpleName();

                        if (viewName.contains("TileView") || viewName.contains("QS") || viewName.contains("ToggleSlider")) {
                            view.animate().cancel();
                            view.setCameraDistance(10000 * view.getResources().getDisplayMetrics().density);
                            view.animate()
                                .scaleX(0.85f).scaleY(0.85f).rotationYBy(90f)
                                .setDuration(120)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        view.animate()
                                            .scaleX(1.05f).scaleY(1.05f).rotationYBy(90f)
                                            .setDuration(350)
                                            .setInterpolator(new OvershootInterpolator(1.6f))
                                            .withEndAction(new Runnable() {
                                                @Override
                                                public void run() {
                                                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                                                }
                                            }).start();
                                    }
                                }).start();
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce QS Hook Error: " + t.getMessage());
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
