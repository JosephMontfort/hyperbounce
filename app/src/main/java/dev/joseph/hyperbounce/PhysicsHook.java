package dev.joseph.hyperbounce;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class PhysicsHook extends XposedModule {
    private static final String TAG = "HyperBounce";
    
    // In-memory Shared Config
    private static final Config config = new Config();
    private static boolean receiverRegistered = false;

    // In-memory Velocity Telemetry
    private static float latestVelocity = 0.0f;
    private static long lastVelocityTime = 0;

    // Per-Frame Thread Locals
    private static final ThreadLocal<Float> normalizedDistance = ThreadLocal.withInitial(() -> 0.5f);
    private static final ThreadLocal<Integer> currentRow = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> isDockView = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isAnimating = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> currentKineticFactor = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Float> currentCascadeResponse = ThreadLocal.withInitial(() -> 0.50f);
    private static final ThreadLocal<Float> currentCascadeDamping = ThreadLocal.withInitial(() -> 0.92f);

    private static Context getAppContext() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAppMethod = atClass.getDeclaredMethod("currentApplication");
            return (Context) currentAppMethod.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setupLauncherIpc() {
        if (receiverRegistered) return;
        Context ctx = getAppContext();
        if (ctx == null) return;

        try {
            // 1. Initial In-Memory Query from Provider
            Uri uri = Uri.parse("content://dev.joseph.hyperbounce.provider");
            Bundle res = ctx.getContentResolver().call(uri, "getConfig", null, null);
            if (res != null) {
                String json = res.getString("config_json");
                if (json != null) config.loadFromJson(json);
            }
        } catch (Throwable t) {}

        try {
            // 2. Register Dynamic In-Memory Broadcast Receiver
            IntentFilter filter = new IntentFilter();
            filter.addAction("dev.joseph.hyperbounce.ACTION_VELOCITY_UPDATE");
            filter.addAction("dev.joseph.hyperbounce.ACTION_CONFIG_CHANGED");

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("dev.joseph.hyperbounce.ACTION_VELOCITY_UPDATE".equals(intent.getAction())) {
                        latestVelocity = intent.getFloatExtra("velocity", 0.0f);
                        lastVelocityTime = System.currentTimeMillis();
                    } else if ("dev.joseph.hyperbounce.ACTION_CONFIG_CHANGED".equals(intent.getAction())) {
                        String json = intent.getStringExtra("config_json");
                        if (json != null) config.loadFromJson(json);
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, filter);
            }
            receiverRegistered = true;
        } catch (Throwable t) {}
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);

        // ==========================================
        // TARGET 1: SYSTEMUI (Lock Screen Velocity Transmitter)
        // ==========================================
        if (param.getPackageName().equals("com.android.systemui")) {
            try {
                Class<?> vtClass = Class.forName("android.view.VelocityTracker", false, param.getClassLoader());
                for (Method m : vtClass.getDeclaredMethods()) {
                    if (m.getName().equals("getYVelocity")) {
                        hook(m).intercept(chain -> {
                            Float vel = (Float) chain.proceed();
                            if (vel != null && vel < -1000.0f) {
                                Context ctx = getAppContext();
                                if (ctx != null) {
                                    Intent intent = new Intent("dev.joseph.hyperbounce.ACTION_VELOCITY_UPDATE");
                                    intent.putExtra("velocity", (float) vel);
                                    intent.setPackage("com.miui.home");
                                    ctx.sendBroadcast(intent);
                                }
                            }
                            return vel;
                        });
                    }
                }
                Log.i(TAG, "[+] SystemUI Velocity Tracker hooked successfully.");
            } catch (Throwable t) {
                Log.e(TAG, "[-] Failed to hook SystemUI", t);
            }
            return;
        }

        // ==========================================
        // TARGET 2: MIUI HOME (Physics Engine & Receiver)
        // ==========================================
        if (param.getPackageName().equals("com.miui.home")) {
            try {
                Class<?> userPresentClass = Class.forName("com.miui.home.launcher.compat.UserPresentAnimationCompatPhone", false, param.getClassLoader());

                Method showMethod = userPresentClass.getDeclaredMethod("showUserPresentAnimation", View.class, int.class, boolean.class);
                hook(showMethod).intercept(chain -> {
                    setupLauncherIpc();

                    Object rawArgs = chain.getArgs();
                    Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;

                    View view = (View) args[0];
                    boolean arg2 = (boolean) args[2];

                    if (view != null) {
                        try {
                            int[] loc = new int[2];
                            view.getLocationOnScreen(loc);
                            float vx = loc[0] + (view.getWidth() / 2.0f);
                            float vy = loc[1] + (view.getHeight() / 2.0f);

                            DisplayMetrics metrics = view.getContext().getResources().getDisplayMetrics();
                            float screenW = metrics.widthPixels;
                            float screenH = metrics.heightPixels;

                            isDockView.set((vy > (screenH * 0.84f)) || arg2);

                            // Compute Kinetic Factor
                            if (System.currentTimeMillis() - lastVelocityTime < 2500) {
                                currentKineticFactor.set(Math.min(1.0f, Math.abs(latestVelocity) / config.maxVelocity));
                            } else {
                                currentKineticFactor.set(0.0f);
                            }

                            if (config.animMode == 0) {
                                // MODE 0: ORIGINAL RADIAL DISPERSION (Original favorite math)
                                float centerX = screenW / 2.0f;
                                float centerY = screenH * 0.42f;
                                float dx = (vx - centerX) / (screenW / 2.0f);
                                float dy = (vy - centerY) / (screenH / 2.0f);
                                normalizedDistance.set((float) Math.sqrt(dx * dx + dy * dy));
                            } else {
                                // MODE 1: KINETIC Y-AXIS CASCADE
                                int cellY = -1;
                                Object tag = view.getTag();
                                if (tag != null) {
                                    try {
                                        cellY = tag.getClass().getField("cellY").getInt(tag);
                                    } catch (Throwable t) {}
                                }
                                if (cellY == -1) {
                                    cellY = (int) Math.floor((loc[1] / screenH) * 6.0f);
                                }
                                currentRow.set(cellY);
                            }
                        } catch (Exception e) {
                            normalizedDistance.set(0.5f);
                            currentRow.set(0);
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

                    if (config.animMode == 0) {
                        // MODE 0: RADIAL SPRINGS
                        if (isDockView.get()) {
                            return new float[]{config.dockDamping, config.dockResponse};
                        }
                        float dist = normalizedDistance.get();
                        float response = config.centerResponse + (dist * config.edgeResponseDelay);
                        float damping = config.centerDamping - (dist * config.edgeDampingDrop);

                        response = Math.min(Math.max(response, config.minResponse), config.maxResponse);
                        damping = Math.min(Math.max(damping, config.minDamping), config.maxDamping);
                        return new float[]{damping, response};
                    } else {
                        // MODE 1: KINETIC CASCADE
                        float kF = currentKineticFactor.get();
                        float baseDamping = config.cascadeDamping - (0.12f * kF);
                        float responseOffset = 0.15f * kF;

                        float damping = baseDamping;
                        float response;

                        if (isDockView.get()) {
                            response = 0.35f - responseOffset;
                        } else {
                            int row = currentRow.get();
                            response = config.cascadeBaseResponse + 0.10f - (row * config.cascadeRowStagger) - responseOffset;
                            response = Math.max(0.20f, Math.min(response, 0.70f));
                        }

                        currentCascadeResponse.set(response);
                        currentCascadeDamping.set(damping);
                        return new float[]{damping, response};
                    }
                });

                Class<?> springAnimClass = Class.forName("com.miui.home.common.animate.SpringAnimator", false, param.getClassLoader());
                Constructor<?> constructor = springAnimClass.getDeclaredConstructor(float.class, float.class, float.class, float.class);
                hook(constructor).intercept(chain -> {
                    if (isAnimating.get()) {
                        Object rawArgs = chain.getArgs();
                        Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : ((Object[]) rawArgs).clone();

                        if (config.animMode == 0) {
                            // MODE 0: PUNCH IN
                            float startVal = (float) args[2];
                            args[2] = startVal * config.zDepthMultiplier;
                        } else {
                            // MODE 1: MICRO ANIMATIONS
                            float startVal = (float) args[2];
                            float endVal = (float) args[3];
                            float kF = currentKineticFactor.get();

                            if (startVal == 0.0f && endVal == 1.0f) {
                                args[0] = 1.0f;
                                args[1] = 0.15f; // Fast Alpha fade
                            } else if (startVal > 0.5f && startVal < 1.0f && endVal == 1.0f) {
                                args[2] = 0.85f - (0.20f * kF); // Dynamic Scale Pop
                                args[0] = currentCascadeDamping.get();
                                args[1] = currentCascadeResponse.get();
                            } else if (endVal == 0.0f && startVal > 5.0f) {
                                float zPunch = 1.2f + (0.6f * kF);
                                args[2] = startVal * zPunch; // Dynamic Z-Punch
                            }
                        }
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });

                Log.i(TAG, "[+] MIUI Home hooked successfully.");
            } catch (Throwable t) {
                Log.e(TAG, "[-] MIUI Home hook failed", t);
            }
        }
    }
}
