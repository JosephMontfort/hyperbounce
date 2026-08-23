package dev.joseph.hyperbounce;

import android.graphics.RectF;
import android.view.View;
import java.lang.reflect.Method;

public class AppTransitionHook {

    public static void init(PhysicsHook module, ClassLoader classLoader, Config config) {
        // =========================================================================
        // 1. FRAME-BY-FRAME GEOMETRY & TRAJECTORY ENGINE (RectFSpringAnim)
        // =========================================================================
        try {
            Class<?> rectFAnimClass = Class.forName("com.miui.home.recents.util.RectFSpringAnim", false, classLoader);
            Method updateRectMethod = rectFAnimClass.getDeclaredMethod("updateRect", 
                    RectF.class, float.class, float.class, float.class, float.class, float.class);

            module.hook(updateRectMethod).intercept(chain -> {
                // Execute native layout calculation first
                Object result = chain.proceed();

                if (!config.appAnimEnabled) {
                    return result;
                }

                try {
                    Object rawArgs = chain.getArgs();
                    Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                    RectF rectF = (RectF) args[0];
                    Object thisObj = chain.getThisObject();

                    if (rectF == null || thisObj == null) {
                        return result;
                    }

                    // Interrupt Safety Guard: If canceled or requesting end, do not warp
                    Method isCancelMethod = rectFAnimClass.getMethod("isRequestCancel");
                    Method isEndMethod = rectFAnimClass.getMethod("isRequestEnd");
                    if ((boolean) isCancelMethod.invoke(thisObj) || (boolean) isEndMethod.invoke(thisObj)) {
                        return result;
                    }

                    // Retrieve Start and Target Rectangles
                    Method getStartRectMethod = rectFAnimClass.getMethod("getStartRect");
                    Method getTargetRectMethod = rectFAnimClass.getMethod("getTargetRect");
                    RectF startRect = (RectF) getStartRectMethod.invoke(thisObj);
                    RectF targetRect = (RectF) getTargetRectMethod.invoke(thisObj);

                    if (startRect == null || targetRect == null) return result;

                    float startW = startRect.width();
                    float targetW = targetRect.width();
                    float totalDelta = Math.abs(targetW - startW);

                    // If totalDelta is negligible (interrupted state / micro-jump), skip warp
                    if (totalDelta < 10.0f) {
                        return result;
                    }

                    float curW = rectF.width();
                    float progress = Math.abs(curW - startW) / totalDelta;
                    progress = Math.max(0.0f, Math.min(1.0f, progress));

                    // Bell Curve Envelope: 0.0 at start -> 1.0 at midpoint -> 0.0 at landing
                    float curve = (float) Math.sin(progress * Math.PI);

                    // Smooth edge roll-off for interrupt stabilization
                    float smoothEnvelope = (float) Math.pow(curve, 1.0f / Math.max(0.1f, config.appInterruptSmooth));

                    float curL = rectF.left;
                    float curT = rectF.top;
                    float curR = rectF.right;
                    float curB = rectF.bottom;
                    float curH = curB - curT;

                    // A. 3D Bottom-Edge Z-Depth Compression
                    float bottomCompression = smoothEnvelope * (curH * config.appBottomZWarp);
                    float newBottom = curB - bottomCompression;

                    // B. Parabolic Vertical Y-Arc (Upward Float)
                    float yLift = smoothEnvelope * config.appYArc;
                    float newTop = curT - yLift;
                    newBottom = newBottom - yLift;

                    // C. Horizontal Sway Curve
                    float xSway = smoothEnvelope * config.appXSway;
                    float newLeft = curL + xSway;
                    float newRight = curR + xSway;

                    // Apply warped coordinates directly
                    rectF.set(newLeft, newTop, newRight, newBottom);

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

                if (!config.appAnimEnabled || springForce == null) {
                    return springForce;
                }

                try {
                    Object thisObj = chain.getThisObject();
                    Method getKeyMethod = springBundleClass.getMethod("getKey");
                    String key = (String) getKeyMethod.invoke(thisObj);

                    if (key != null) {
                        Class<?> sfClass = springForce.getClass();
                        Method setDamping = sfClass.getMethod("setDampingRatio", float.class);
                        Method setStiffness = sfClass.getMethod("setStiffness", float.class);
                        Method getStiffness = sfClass.getMethod("getStiffness");

                        float currentStiff = (float) getStiffness.invoke(springForce);

                        switch (key) {
                            case "centerX":
                            case "centerY":
                                setDamping.invoke(springForce, config.appTravelDamping);
                                if (currentStiff > 100.0f) setStiffness.invoke(springForce, config.appTravelStiffness);
                                break;
                            case "Width":
                            case "Height":
                            case "Ratio":
                                setDamping.invoke(springForce, config.appSizeDamping);
                                if (currentStiff > 100.0f) setStiffness.invoke(springForce, config.appSizeStiffness);
                                break;
                            case "Radius":
                            case "RadiusRatio":
                                setDamping.invoke(springForce, config.appCornerDamping);
                                if (currentStiff > 100.0f) setStiffness.invoke(springForce, config.appCornerStiffness);
                                break;
                            case "Alpha":
                            case "SurfaceExtAlpha":
                                setDamping.invoke(springForce, config.appAlphaDamping);
                                if (currentStiff > 100.0f) setStiffness.invoke(springForce, config.appAlphaStiffness);
                                break;
                        }
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

                        if (!config.appAnimEnabled) return result;

                        try {
                            View view = (View) chain.getThisObject();
                            Object rawArgs = chain.getArgs();
                            Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;

                            // Float progress parameter
                            float progress = 0.0f;
                            if (args.length > 3 && args[3] instanceof Float) {
                                progress = (Float) args[3];
                            }

                            if (progress > 0.0f && progress < 1.0f && config.appIconPitchDeg > 0.1f) {
                                view.setCameraDistance(config.appIconCameraDist);
                                view.setPivotY(view.getHeight() * 0.95f); // Pivot at bottom edge

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
