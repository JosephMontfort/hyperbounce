package dev.joseph.hyperbounce;

import android.util.Log;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.json.JSONObject;

public class PhysicsHook extends XposedModule {
    private static final String TAG = "HyperBounce-Physics";
    private static final ThreadLocal<Float> normalizedDistance = ThreadLocal.withInitial(() -> 0.5f);
    private static final ThreadLocal<Boolean> isDockView = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isAnimating = ThreadLocal.withInitial(() -> false);

    private static long configLastModified = 0;
    private static final Config config = new Config();

    private void loadConfig() {
        File file = new File("/data/data/dev.joseph.hyperbounce/files/config.json");
        if (file.exists() && file.lastModified() > configLastModified) {
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                String jsonStr = new String(data, "UTF-8");
                JSONObject json = new JSONObject(jsonStr);

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
                
                configLastModified = file.lastModified();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load config", e);
            }
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("com.miui.home")) return;

        try {
            Class<?> userPresentClass = Class.forName("com.miui.home.launcher.compat.UserPresentAnimationCompatPhone", false, param.getClassLoader());
            
            // Hook 1: Map the grid distances seamlessly without recursive calls
            Method showMethod = userPresentClass.getDeclaredMethod("showUserPresentAnimation", View.class, int.class, boolean.class);
            hook(showMethod).intercept(chain -> {
                View view = (View) chain.args[0];
                boolean arg2 = (boolean) chain.args[2];
                if (view != null) {
                    try {
                        int[] loc = new int[2];
                        view.getLocationOnScreen(loc);
                        float vx = loc[0] + (view.getWidth() / 2.0f);
                        float vy = loc[1] + (view.getHeight() / 2.0f);

                        android.util.DisplayMetrics metrics = view.getContext().getResources().getDisplayMetrics();
                        float screenW = metrics.widthPixels;
                        float screenH = metrics.heightPixels;

                        float centerX = screenW / 2.0f;
                        float centerY = screenH * 0.42f;

                        isDockView.set(vy > (screenH * 0.84f) || arg2);

                        float dx = (vx - centerX) / (screenW / 2.0f);
                        float dy = (vy - centerY) / (screenH / 2.0f);
                        normalizedDistance.set((float) Math.sqrt(dx * dx + dy * dy));
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

            // Hook 2: Dynamic Spring Override
            Method getDR = userPresentClass.getDeclaredMethod("getDampingResponse");
            hook(getDR).intercept(chain -> {
                if (!isAnimating.get()) return chain.proceed();

                loadConfig();
                if (isDockView.get()) {
                    return new float[]{config.dockDamping, config.dockResponse};
                }

                float dist = normalizedDistance.get();
                float response = config.centerResponse + (dist * config.edgeResponseDelay);
                float damping = config.centerDamping - (dist * config.edgeDampingDrop);

                response = Math.min(Math.max(response, config.minResponse), config.maxResponse);
                damping = Math.min(Math.max(damping, config.minDamping), config.maxDamping);

                return new float[]{damping, response};
            });

            // Hook 3: Z-Depth Injection
            Class<?> springAnimClass = Class.forName("com.miui.home.common.animate.SpringAnimator", false, param.getClassLoader());
            Constructor<?> constructor = springAnimClass.getDeclaredConstructor(float.class, float.class, float.class, float.class);
            hook(constructor).intercept(chain -> {
                if (isAnimating.get()) {
                    loadConfig();
                    float startVal = (float) chain.args[2];
                    chain.args[2] = startVal * config.zDepthMultiplier;
                }
                return chain.proceed();
            });

            Log.i(TAG, "[+] MIUI Home hooked successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "[-] Hook failed", t);
        }
    }
}
