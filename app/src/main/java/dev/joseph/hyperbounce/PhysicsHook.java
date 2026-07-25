package dev.joseph.hyperbounce;

import android.view.View;
import android.view.MotionEvent;
import android.view.animation.OvershootInterpolator;
import android.animation.ValueAnimator;

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {
    
    // Safe memory mapping for debouncing touches without breaking system tags
    private static final WeakHashMap<View, Long> debounceMap = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // ==========================================
        // 1. SYSTEMUI & PLUGIN HOOKS
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            
            // --- A. DYNAMIC ISLAND DIRECT TOUCH INTERCEPT ---
            try {
                XposedHelpers.findAndHookMethod(View.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            final View view = (View) param.thisObject;
                            // Using string comparison to completely bypass Plugin ClassLoader mismatches
                            String className = view.getClass().getName();
                            
                            if (className.contains("DynamicIslandContentView") || className.contains("PlayerIslandConstraintLayout")) {
                                
                                long now = System.currentTimeMillis();
                                long lastFlip = debounceMap.containsKey(view) ? debounceMap.get(view) : 0;
                                
                                if (now - lastFlip > 600) {
                                    debounceMap.put(view, now);
                                    
                                    // Set 3D camera depth
                                    view.setCameraDistance(10000 * view.getResources().getDisplayMetrics().density);
                                    
                                    // Force the 3D rotation frame-by-frame to bypass Folme's physics loop
                                    ValueAnimator flipAnim = ValueAnimator.ofFloat(0f, 360f);
                                    flipAnim.setDuration(450);
                                    flipAnim.setInterpolator(new OvershootInterpolator(1.2f));
                                    flipAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                        @Override
                                        public void onAnimationUpdate(ValueAnimator animation) {
                                            view.setRotationX((float) animation.getAnimatedValue());
                                        }
                                    });
                                    flipAnim.start();
                                }
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Touch Hook Error: " + t.getMessage());
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
