package dev.joseph.hyperbounce;

import android.graphics.RectF;
import android.view.View;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class AppTransitionHook {

    public static void init(PhysicsHook module, ClassLoader classLoader, Config config) {
        // =========================================================================
        // 1. FRAME-BY-FRAME GEOMETRY ENGINE (RectFSpringAnim)
        // =========================================================================
        try {
            Class<?> rectFAnimClass = Class.forName("com.miui.home.recents.util.RectFSpringAnim", false, classLoader);
            Method updateRectMethod = rectFAnimClass.getDeclaredMethod("updateRect", 
                    RectF.class, float.class, float.class, float.class, float.class, float.class);

            module.hook(updateRectMethod).intercept(chain -> {
                Object result = chain.proceed();

                // STOCK BEHAVIOR PASS-THROUGH
                if (!config.appAnimEnabled || (config.appYArc == 0f && config.appBottomZWarp == 0f && config.appXSway == 0f)) {
                    return result;
                }

                try {
                    Object rawArgs = chain.getArgs();
                    Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                    RectF rectF = (RectF) args[0];
                    Object thisObj = chain.getThisObject();

                    if (rectF == null || thisObj == null) return result;

                    // OVERSHOOT FIX: Do not warp geometry on App Open
                    Method isOpenAnimMethod = rectFAnimClass.getMethod("isOpenAnim");
                    if ((boolean) isOpenAnimMethod.invoke(thisObj)) {
                        return result; 
                    }

                    Method isCancelMethod = rectFAnimClass.getMethod("isRequestCancel");
                    Method isEndMethod = rectFAnimClass.getMethod("isRequestEnd");
                    if ((boolean) isCancelMethod.invoke(thisObj) || (boolean) isEndMethod.invoke(thisObj)) {
                        return result;
                    }

                    Method getStartRectMethod = rectFAnimClass.getMethod("getStartRect");
                    Method getTargetRectMethod = rectFAnimClass.getMethod("getTargetRect");
                    RectF startRect = (RectF) getStartRectMethod.invoke(thisObj);
                    RectF targetRect = (RectF) getTargetRectMethod.invoke(thisObj);

                    if (startRect == null || targetRect == null) return result;

                    float startW = startRect.width();
                    float targetW = targetRect.width();
                    float totalDelta = Math.abs(targetW - startW);

                    // BUG FIX: Prevent shrinking to 0 by clamping minimum delta
                    if (totalDelta < 50.0f) return result;

                    float curW = rectF.width();
                    float progress = Math.abs(curW - startW) / totalDelta;
                    progress = Math.max(0.0f, Math.min(1.0f, progress));

                    float curve = Math.max(0.0f, (float) Math.sin(progress * Math.PI));
                    float smoothEnvelope = (float) Math.pow(curve, 1.0f / Math.max(0.1f, config.appInterruptSmooth));

                    float curL = rectF.left;
                    float curT = rectF.top;
                    float curR = rectF.right;
                    float curB = rectF.bottom;
                    float curH = curB - curT;

                    // 3D Bottom-Edge Compression (Clamped to prevent inversion)
                    float maxCompression = curH * 0.5f; 
                    float bottomCompression = Math.min(smoothEnvelope * (curH * config.appBottomZWarp), maxCompression);
                    float newBottom = curB - bottomCompression;

                    // Parabolic Vertical Y-Arc
                    float yLift = smoothEnvelope * config.appYArc;
                    float newTop = curT - yLift;
                    newBottom = newBottom - yLift;

                    float xSway = smoothEnvelope * config.appXSway;
                    rectF.set(curL + xSway, newTop, curR + xSway, newBottom);

                } catch (Throwable ignored) {}

                return result;
            });
        } catch (Throwable t) {}

        // =========================================================================
        // 2. MULTI-CHANNEL SPRING PHYSICS (SpringBundle)
        // =========================================================================
        try {
            Class<?> springBundleClass = Class.forName("com.miui.home.recents.anim.SpringBundle", false, classLoader);
            Method getSpringForceMethod = springBundleClass.getDeclaredMethod("getSpringForce");

            module.hook(getSpringForceMethod).intercept(chain -> {
                Object springForce = chain.proceed();
                if (springForce == null) return springForce;

                try {
                    Object thisObj = chain.getThisObject();
                    Method getKeyMethod = springBundleClass.getMethod("getKey");
                    String key = (String) getKeyMethod.invoke(thisObj);

                    if (key != null) {
                        Class<?> sfClass = springForce.getClass();
                        Method setDamping = sfClass.getMethod("setDampingRatio", float.class);
                        Method setStiffness = sfClass.getMethod("setStiffness", float.class);

                        boolean enabled = config.appAnimEnabled;
                        float damping = 0.8f;      // HyperOS Stock Damping
                        float stiffness = 200.0f;  // HyperOS Stock Stiffness

                        switch (key) {
                            case "centerX":
                            case "centerY":
                                damping = enabled ? config.appTravelDamping : 0.8f;
                                stiffness = enabled ? config.appTravelStiffness : 200.0f;
                                break;
                            case "Width":
                            case "Height":
                                damping = enabled ? config.appSizeDamping : 0.8f;
                                stiffness = enabled ? config.appSizeStiffness : 200.0f;
                                break;
                            case "Ratio":
                                damping = enabled ? config.appRatioDamping : 0.8f;
                                stiffness = enabled ? config.appRatioStiffness : 400.0f; // Stock is 400!
                                break;
                            case "Radius":
                            case "RadiusRatio":
                                damping = enabled ? config.appCornerDamping : 0.8f;
                                stiffness = enabled ? config.appCornerStiffness : 200.0f;
                                break;
                            case "Alpha":
                            case "SurfaceExtAlpha":
                            case "appLeashBlurRadius":
                                damping = enabled ? config.appAlphaDamping : 0.99f;
                                stiffness = enabled ? config.appAlphaStiffness : 400.0f; // Stock is 400!
                                break;
                        }

                        // Apply determined values (either tuned or forced back to stock)
                        setDamping.invoke(springForce, damping);
                        setStiffness.invoke(springForce, stiffness);
                    }
                } catch (Throwable ignored) {}

                return springForce;
            });
        } catch (Throwable t) {}

        // =========================================================================
        // 3. GHOST ICON 3D PITCH & ROTATION (FloatingIconView2)
        // =========================================================================
        try {
            Class<?> floatingIconClass = Class.forName("com.miui.home.recents.views.FloatingIconView2", false, classLoader);
            Method[] methods = floatingIconClass.getDeclaredMethods();

            for (Method m : methods) {
                if (m.getName().equals("update") && m.getParameterTypes().length >= 4) {
                    module.hook(m).intercept(chain -> {
                        Object result = chain.proceed();

                        try {
                            View view = (View) chain.getThisObject();
                            
                            // Revert to stock if disabled or set to 0
                            if (!config.appAnimEnabled || config.appIconPitchDeg == 0.0f) {
                                view.setRotationX(0.0f);
                                return result;
                            }

                            Field isOpeningField = floatingIconClass.getDeclaredField("mIsOpening");
                            isOpeningField.setAccessible(true);
                            if (isOpeningField.getBoolean(view)) {
                                view.setRotationX(0.0f);
                                return result;
                            }

                            Object rawArgs = chain.getArgs();
                            Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;

                            float progress = 0.0f;
                            if (args.length > 3 && args[3] instanceof Float) {
                                progress = (Float) args[3];
                            }

                            if (progress > 0.0f && progress < 1.0f) {
                                view.setCameraDistance(config.appIconCameraDist);
                                view.setPivotY(view.getHeight() * 0.95f); 
                                float tilt = (float) Math.sin(progress * Math.PI) * config.appIconPitchDeg;
                                view.setRotationX(tilt);
                            } else {
                                view.setRotationX(0.0f);
                            }
                        } catch (Throwable ignored) {}

                        return result;
                    });
                    break;
                }
            }
        } catch (Throwable t) {}
    }
}
