package dev.joseph.hyperbounce;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.json.JSONObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class PhysicsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Physics";
    private static final ThreadLocal<Float> normalizedDistance = ThreadLocal.withInitial(() -> 0.5f);
    private static final ThreadLocal<Boolean> isDockView = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isAnimating = ThreadLocal.withInitial(() -> false);
    
    // ThreadLocals for Script Context
    private static final ThreadLocal<Float> currentVx = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Float> currentVy = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Float> currentScreenW = ThreadLocal.withInitial(() -> 1080.0f);
    private static final ThreadLocal<Float> currentScreenH = ThreadLocal.withInitial(() -> 2400.0f);

    private static long configLastModified = 0;
    private static final Config config = new Config();
    private static org.mozilla.javascript.Script compiledScript = null;

    private void loadConfig() {
        File file = new File("/data/local/tmp/hyperbounce.json");
        if (file.exists() && file.lastModified() > configLastModified) {
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                String jsonStr = new String(data, "UTF-8");
                JSONObject json = new JSONObject(jsonStr);

                config.animationDuration = (float) json.optDouble("animationDuration", 1.0);
                config.gridDeadzoneX = (float) json.optDouble("gridDeadzoneX", 0.125);
                config.dockDamping = (float) json.optDouble("dockDamping", 0.70);
                config.dockResponse = (float) json.optDouble("dockResponse", 0.40);
                config.centerResponse = (float) json.optDouble("centerResponse", 0.48);
                config.centerDamping = (float) json.optDouble("centerDamping", 0.52);
                config.edgeResponseDelay = (float) json.optDouble("edgeResponseDelay", 0.22);
                config.edgeDampingDrop = (float) json.optDouble("edgeDampingDrop", 0.10);
                config.minResponse = (float) json.optDouble("minResponse", 0.45);
                config.maxResponse = (float) json.optDouble("maxResponse", 0.75);
                config.minDamping = (float) json.optDouble("minDamping", 0.40);
                config.maxDamping = (float) json.optDouble("maxDamping", 0.55);
                config.zDepthMultiplier = (float) json.optDouble("zDepthMultiplier", 1.6);
                config.useCustomScript = json.optBoolean("useCustomScript", false);
                
                String newScript = json.optString("customScript", config.customScript);
                if (!newScript.equals(config.customScript) || compiledScript == null) {
                    config.customScript = newScript;
                    if (config.useCustomScript) {
                        try {
                            org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
                            rhino.setOptimizationLevel(-1); // Required for Android (no Dalvik generation)
                            compiledScript = rhino.compileString(config.customScript, "HyperScript", 1, null);
                        } finally {
                            org.mozilla.javascript.Context.exit();
                        }
                    }
                }
                
                configLastModified = file.lastModified();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load config/script", e);
            }
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("com.miui.home")) return;

        try {
            Class<?> userPresentClass = Class.forName("com.miui.home.launcher.compat.UserPresentAnimationCompatPhone", false, param.getClassLoader());
            
            Method showMethod = userPresentClass.getDeclaredMethod("showUserPresentAnimation", View.class, int.class, boolean.class);
            hook(showMethod).intercept(chain -> {
                Object rawArgs = chain.getArgs();
                Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                
                View view = (View) args[0];
                boolean arg2 = (boolean) args[2];
                if (view != null) {
                    try {
                        loadConfig();
                        
                        int[] loc = new int[2];
                        view.getLocationOnScreen(loc);
                        float vx = loc[0] + (view.getWidth() / 2.0f);
                        float vy = loc[1] + (view.getHeight() / 2.0f);

                        // Use RealMetrics for perfect physical pixel symmetry
                        WindowManager wm = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
                        DisplayMetrics metrics = new DisplayMetrics();
                        wm.getDefaultDisplay().getRealMetrics(metrics);
                        
                        float screenW = metrics.widthPixels;
                        float screenH = metrics.heightPixels;

                        currentVx.set(vx);
                        currentVy.set(vy);
                        currentScreenW.set(screenW);
                        currentScreenH.set(screenH);

                        float centerX = screenW / 2.0f;
                        float centerY = screenH * 0.45f;

                        isDockView.set(vy > (screenH * 0.84f) || arg2);

                        // Enforce PERFECT symmetry with Math.abs on both axes
                        float deadzonePx = screenW * config.gridDeadzoneX;
                        float absDx = Math.abs(vx - centerX);
                        float dx = Math.max(0.0f, absDx - deadzonePx);
                        float dy = Math.abs(vy - centerY);
                        
                        float maxDx = (screenW / 2.0f) - deadzonePx;
                        float maxDy = Math.max(centerY, screenH - centerY);
                        float maxDist = (float) Math.sqrt((maxDx * maxDx) + (maxDy * maxDy));
                        
                        float dist = (float) Math.sqrt((dx * dx) + (dy * dy));
                        normalizedDistance.set(Math.min(dist / maxDist, 1.0f));
                    } catch (Exception e) {
                        normalizedDistance.set(0.5f);
                        isDockView.set(false);
                    }
                }
                isAnimating.set(true);
                Object result = chain.proceed();
                isAnimating.set(false);
                return result;
            });

            Method getDR = userPresentClass.getDeclaredMethod("getDampingResponse");
            hook(getDR).intercept(chain -> {
                if (!isAnimating.get()) return chain.proceed();
                loadConfig();

                float damping = config.centerDamping;
                float response = config.centerResponse;

                if (config.useCustomScript && compiledScript != null) {
                    // DYNAMIC CODE EXECUTION via Rhino JS
                    try {
                        org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
                        rhino.setOptimizationLevel(-1);
                        Scriptable scope = rhino.initStandardObjects();
                        
                        ScriptableObject.putProperty(scope, "isDockView", isDockView.get());
                        ScriptableObject.putProperty(scope, "dist", normalizedDistance.get());
                        ScriptableObject.putProperty(scope, "vx", currentVx.get());
                        ScriptableObject.putProperty(scope, "vy", currentVy.get());
                        ScriptableObject.putProperty(scope, "screenW", currentScreenW.get());
                        ScriptableObject.putProperty(scope, "screenH", currentScreenH.get());

                        Object result = compiledScript.exec(rhino, scope);
                        if (result instanceof org.mozilla.javascript.NativeArray) {
                            org.mozilla.javascript.NativeArray arr = (org.mozilla.javascript.NativeArray) result;
                            damping = (float) ((Number) arr.get(0, scope)).doubleValue();
                            response = (float) ((Number) arr.get(1, scope)).doubleValue();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Custom Script crashed, falling back to sliders.", e);
                        config.useCustomScript = false; 
                    } finally {
                        org.mozilla.javascript.Context.exit();
                    }
                } else {
                    // SLIDER LOGIC
                    if (isDockView.get()) {
                        damping = config.dockDamping;
                        response = config.dockResponse;
                    } else {
                        float dist = normalizedDistance.get();
                        response = config.centerResponse + (dist * config.edgeResponseDelay);
                        damping = config.centerDamping - (dist * config.edgeDampingDrop);

                        response = Math.min(Math.max(response, config.minResponse), config.maxResponse);
                        damping = Math.min(Math.max(damping, config.minDamping), config.maxDamping);
                    }
                }

                // Apply Global Animation Duration Slider
                response *= config.animationDuration;

                return new float[]{damping, response};
            });

            Class<?> springAnimClass = Class.forName("com.miui.home.common.animate.SpringAnimator", false, param.getClassLoader());
            Constructor<?> constructor = springAnimClass.getDeclaredConstructor(float.class, float.class, float.class, float.class);
            hook(constructor).intercept(chain -> {
                if (isAnimating.get()) {
                    loadConfig();
                    Object rawArgs = chain.getArgs();
                    Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : ((Object[]) rawArgs).clone();
                    
                    float startVal = (float) args[2];
                    args[2] = startVal * config.zDepthMultiplier;
                    return chain.proceed(args);
                }
                return chain.proceed();
            });

            Log.i(TAG, "[+] MIUI Home hooked successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "[-] Hook failed", t);
        }
    }
}
