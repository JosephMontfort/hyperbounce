package dev.joseph.hyperbounce;

import android.view.View;
import android.view.MotionEvent;
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
        // 1. SYSTEMUI & PLUGIN HOOKS (ISLAND & QS)
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            
            // Cache the exact Island classes we found in static analysis for zero-lag pointer comparison
            final Class<?> clsIslandContent = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.window.content.DynamicIslandContentView", lpparam.classLoader);
            final Class<?> clsPlayerIsland = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout", lpparam.classLoader);

            // --- A. DYNAMIC ISLAND DIRECT TOUCH INTERCEPT ---
            try {
                XposedHelpers.findAndHookMethod(View.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        
                        // ONLY process on the exact moment the finger hits the glass (Action Down)
                        // This prevents string-checking 120 times a second while scrolling.
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            final View view = (View) param.thisObject;
                            Class<?> viewClass = view.getClass();
                            
                            // Check if the touched view is the Dynamic Island or Media Player
                            if (viewClass == clsIslandContent || viewClass == clsPlayerIsland) {
                                
                                // Prevent seizure-flips if you double-tap really fast (600ms debounce)
                                long now = System.currentTimeMillis();
                                Object tag = view.getTag(android.R.id.accessibilityActionScrollDown); // Random unused ID tag
                                long lastFlip = tag != null ? (long) tag : 0;
                                
                                if (now - lastFlip > 600) {
                                    view.setTag(android.R.id.accessibilityActionScrollDown, now);
                                    
                                    // Execute the 3D Barrel Roll + Expand
                                    view.animate().cancel();
                                    view.setCameraDistance(10000 * view.getResources().getDisplayMetrics().density);
                                    view.animate()
                                        .rotationXBy(360f)
                                        .scaleX(1.05f)
                                        .scaleY(1.05f)
                                        .setDuration(400)
                                        .setInterpolator(new OvershootInterpolator(1.2f))
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                                            }
                                        })
                                        .start();
                                }
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Touch Hook Error: " + t.getMessage());
            }

            // --- B. QS TILE FLIP ON LONG PRESS ---
            // QS Tiles typically allow performLongClick to fire, so we keep this one intact.
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
