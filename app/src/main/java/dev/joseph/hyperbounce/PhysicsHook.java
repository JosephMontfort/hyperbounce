package dev.joseph.hyperbounce;

import android.view.View;
import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // ==========================================
        // 1. DYNAMIC ISLAND (PLUGIN & SYSTEMUI)
        // ==========================================
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            
            // --- A. INJECT APPLE SPRING PHYSICS VIA REFLECTION ---
            try {
                Class<?> delegateClass = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate", lpparam.classLoader);
                if (delegateClass != null) {
                    XposedBridge.hookAllConstructors(delegateClass, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // The system just built the Island and cached its stiff physics.
                            // We are going to rip them out and replace them.
                            Class<?> folmeEaseClass = XposedHelpers.findClass("miuix.animation.FolmeEase", lpparam.classLoader);
                            
                            // Create the iOS Spring (0.65 = Bounciness/Damping, 0.35 = Speed)
                            Object iosSpring = XposedHelpers.callStaticMethod(folmeEaseClass, "spring", 0.65f, 0.35f);
                            // A slightly faster spring for closing the island
                            Object fastSpring = XposedHelpers.callStaticMethod(folmeEaseClass, "spring", 0.70f, 0.25f);

                            // Loop through every single variable in the Animation Delegate
                            for (Field field : delegateClass.getDeclaredFields()) {
                                field.setAccessible(true);
                                
                                // If the variable is an EaseStyle (Xiaomi's physics object)
                                if (field.getType().getName().contains("EaseStyle")) {
                                    String name = field.getName();
                                    
                                    // Overwrite the variable with our custom Apple physics
                                    if (name.contains("HIDDEN") || name.contains("APPEAR")) {
                                        field.set(param.thisObject, fastSpring);
                                    } else {
                                        field.set(param.thisObject, iosSpring); 
                                    }
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Physics Override Error: " + t.getMessage());
            }

            // --- B. FIX THE BOTTOM SCREEN GLITCH (B: 1800) ---
            try {
                Class<?> bgViewClass = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.DynamicIslandBackgroundView", lpparam.classLoader);
                if (bgViewClass != null) {
                    XposedHelpers.findAndHookMethod(bgViewClass, "layout", int.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int top = (int) param.args[1];
                            int bottom = (int) param.args[3];
                            
                            // If the system panics and tries to stretch the island to the bottom of the screen
                            // We violently clamp the max height to 600px to kill the flash frame.
                            if ((bottom - top) > 600) {
                                param.args[3] = top + 600;
                            }
                        }
                    });

                    // --- C. FIX THE TRANSPARENT BOX GLITCH ---
                    XposedHelpers.findAndHookMethod(bgViewClass, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            float targetAlpha = (float) param.args[0];
                            // iOS island is a solid black pill. It never fades in transparently.
                            // If it's trying to be semi-transparent, we snap it to solid black instantly.
                            if (targetAlpha > 0.05f && targetAlpha < 1.0f) {
                                param.args[0] = 1.0f;
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("HyperBounce Layout Glitch Fix Error: " + t.getMessage());
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
