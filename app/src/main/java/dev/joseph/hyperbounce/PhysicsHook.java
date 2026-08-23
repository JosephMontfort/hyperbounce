package dev.joseph.hyperbounce;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class PhysicsHook extends XposedModule {
    private static final Config config = new Config();
    private static boolean receiverRegistered = false;

    private static float latestVelocity = 0.0f;
    private static long lastVelocityTime = 0;
    private static float shadeTouchDownY = 0.0f;
    private static long shadeTouchDownTime = 0;

    private static final ThreadLocal<Float> normalizedDistance = ThreadLocal.withInitial(() -> 0.5f);
    private static final ThreadLocal<Integer> currentRow = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> isOuterColumn = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isDockView = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isIndicator = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isAnimating = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> currentKineticFactor = ThreadLocal.withInitial(() -> 0.0f);
    private static final ThreadLocal<Float> activeResponse = ThreadLocal.withInitial(() -> 0.50f);
    private static final ThreadLocal<Float> activeDamping = ThreadLocal.withInitial(() -> 0.92f);

    private static Context getAppContext() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            return (Context) atClass.getDeclaredMethod("currentApplication").invoke(null);
        } catch (Throwable t) { return null; }
    }

    private static void syncMemory() {
        if (receiverRegistered) return;
        Context ctx = getAppContext();
        if (ctx == null) return;
        try {
            Uri uri = Uri.parse("content://dev.joseph.hyperbounce.provider");
            Bundle res = ctx.getContentResolver().call(uri, "getConfig", null, null);
            if (res != null) config.loadFromJson(res.getString("config_json"));
        } catch (Throwable t) {}
        try {
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
                        config.loadFromJson(intent.getStringExtra("config_json"));
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(receiver, filter);
            receiverRegistered = true;
        } catch (Throwable t) {}
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);

        if (param.getPackageName().equals("com.android.systemui")) {
            try {
                Class<?> shadeViewClass = Class.forName("com.android.systemui.shade.NotificationShadeWindowView", false, param.getClassLoader());
                Method dispatchTouch = shadeViewClass.getDeclaredMethod("dispatchTouchEvent", android.view.MotionEvent.class);
                hook(dispatchTouch).intercept(chain -> {
                    try {
                        Object rawArgs = chain.getArgs();
                        Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                        android.view.MotionEvent event = (android.view.MotionEvent) args[0];
                        
                        int action = event.getActionMasked();
                        if (action == android.view.MotionEvent.ACTION_DOWN) {
                            shadeTouchDownY = event.getRawY();
                            shadeTouchDownTime = System.currentTimeMillis();
                        } else if (action == android.view.MotionEvent.ACTION_UP) {
                            float dy = event.getRawY() - shadeTouchDownY;
                            long dt = System.currentTimeMillis() - shadeTouchDownTime;
                            if (dt > 10 && dy < -150) { 
                                float rawVelocity = dy / (dt / 1000.0f); 
                                if (rawVelocity < -1000.0f) {
                                    Context ctx = getAppContext();
                                    if (ctx != null) {
                                        Intent intent = new Intent("dev.joseph.hyperbounce.ACTION_VELOCITY_UPDATE");
                                        intent.putExtra("velocity", rawVelocity);
                                        intent.setPackage("com.miui.home");
                                        ctx.sendBroadcast(intent);
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignore) {}
                    return chain.proceed();
                });
            } catch (Throwable t) {}
            return;
        }

        if (param.getPackageName().equals("com.miui.home")) {
            syncMemory();

            AppTransitionHook.init(this, param.getClassLoader(), config);

            try {
                Class<?> userPresentClass = Class.forName("com.miui.home.launcher.compat.UserPresentAnimationCompatPhone", false, param.getClassLoader());

                Method showMethod = userPresentClass.getDeclaredMethod("showUserPresentAnimation", View.class, int.class, boolean.class);
                hook(showMethod).intercept(chain -> {
                    syncMemory();
                    
                    if (!config.unlockEnabled) return chain.proceed();

                    Object rawArgs = chain.getArgs();
                    Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                    View view = (View) args[0];

                    if (view != null) {
                        try {
                            int[] loc = new int[2];
                            view.getLocationOnScreen(loc);
                            float vx = loc[0] + (view.getWidth() / 2.0f);
                            float vy = loc[1] + (view.getHeight() / 2.0f);
                            float screenW = view.getContext().getResources().getDisplayMetrics().widthPixels;
                            float screenH = view.getContext().getResources().getDisplayMetrics().heightPixels;

                            int cols = Math.max(1, config.gridColumns);
                            int rows = Math.max(1, config.gridRows);

                            int cellX = -1;
                            int cellY = -1;
                            long container = 0;
                            Object tag = view.getTag();
                            if (tag != null) {
                                try { cellX = tag.getClass().getField("cellX").getInt(tag); } catch (Throwable t) {}
                                try { cellY = tag.getClass().getField("cellY").getInt(tag); } catch (Throwable t) {}
                                try { container = tag.getClass().getField("container").getLong(tag); } catch (Throwable t) {}
                            }

                            int colIndex;
                            if (cellX != -1) {
                                colIndex = Math.max(0, Math.min(cols - 1, cellX));
                            } else {
                                float cellWidth = screenW / (float) cols;
                                colIndex = Math.max(0, Math.min(cols - 1, (int) (vx / cellWidth)));
                            }
                            isOuterColumn.set(colIndex == 0 || colIndex == cols - 1);

                            int rowIndex = cellY != -1 ? cellY : (int) Math.floor((vy / screenH) * rows);
                            rowIndex = Math.max(0, Math.min(rows - 1, rowIndex));
                            
                            isIndicator.set((boolean) args[2]);
                            isDockView.set(container == -101L);
                            currentRow.set(rowIndex);

                            if (System.currentTimeMillis() - lastVelocityTime < 2500) {
                                currentKineticFactor.set(Math.min(1.0f, Math.abs(latestVelocity) / config.maxVelocity));
                            } else {
                                currentKineticFactor.set(0.0f);
                            }

                            if (config.animMode == 0) {
                                float centerX = screenW / 2.0f;
                                float centerY = screenH * 0.42f;
                                float dx = (vx - centerX) / (screenW / 2.0f);
                                float dy = (vy - centerY) / (screenH / 2.0f);
                                normalizedDistance.set((float) Math.sqrt(dx * dx + dy * dy));
                            }
                        } catch (Exception e) {}
                    }

                    isAnimating.set(true);
                    Object result = chain.proceed();
                    isAnimating.set(false);
                    return result;
                });

                Method getDR = userPresentClass.getDeclaredMethod("getDampingResponse");
                hook(getDR).intercept(chain -> {
                    if (!isAnimating.get() || !config.unlockEnabled) return chain.proceed();

                    if (config.animMode == 0) {
                        if (isDockView.get() || isIndicator.get()) return new float[]{config.dockDamping, config.dockResponse};
                        float dist = normalizedDistance.get();
                        float response = config.centerResponse + (dist * config.edgeResponseDelay);
                        float damping = config.centerDamping - (dist * config.edgeDampingDrop);
                        response = Math.max(Math.min(config.minResponse, config.maxResponse), Math.min(Math.max(config.minResponse, config.maxResponse), response));
                        damping = Math.max(Math.min(config.minDamping, config.maxDamping), Math.min(Math.max(config.minDamping, config.maxDamping), damping));
                        return new float[]{damping, response};
                    } else {
                        if (isDockView.get() || isIndicator.get()) {
                            activeResponse.set(config.kineticDockResponse * config.durationScale);
                            activeDamping.set(config.kineticDockDamping);
                            return new float[]{config.kineticDockDamping, config.kineticDockResponse * config.durationScale};
                        }

                        float kF = currentKineticFactor.get();
                        float vInf = config.velocityInfluence;
                        int safeRow = Math.max(0, Math.min(config.gridRows - 1, currentRow.get()));
                        boolean outer = isOuterColumn.get();
                        
                        float customBounce = outer ? config.getDyn("r" + safeRow + "_outer_bounce", 0.2f) : config.getDyn("r" + safeRow + "_center_bounce", 0.2f);
                        float customSettle = outer ? config.getDyn("r" + safeRow + "_outer_settle", 0.5f) : config.getDyn("r" + safeRow + "_center_settle", 0.5f);

                        float response = customSettle * config.durationScale;
                        response *= (1.0f - (0.30f * kF * vInf));

                        float bounceDrop = customBounce * kF * vInf;
                        float damping = config.cascadeDamping * (1.0f - bounceDrop);

                        response = Math.max(0.15f, Math.min(response, 1.5f));
                        damping = Math.max(0.15f, Math.min(damping, 1.0f));

                        activeResponse.set(response);
                        activeDamping.set(damping);
                        return new float[]{damping, response};
                    }
                });

                Class<?> springAnimClass = Class.forName("com.miui.home.common.animate.SpringAnimator", false, param.getClassLoader());
                Constructor<?> constructor = springAnimClass.getDeclaredConstructor(float.class, float.class, float.class, float.class);
                hook(constructor).intercept(chain -> {
                    if (isAnimating.get() && config.unlockEnabled) {
                        Object rawArgs = chain.getArgs();
                        Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : ((Object[]) rawArgs).clone();

                        if (config.animMode == 0) {
                            args[2] = ((float) args[2]) * config.zDepthMultiplier;
                        } else {
                            float startVal = (float) args[2];
                            float endVal = (float) args[3];
                            float kF = currentKineticFactor.get();
                            float vInf = config.velocityInfluence;
                            int safeRow = Math.max(0, Math.min(config.gridRows - 1, currentRow.get()));

                            if (startVal == 0.0f && endVal == 1.0f) { 
                                args[0] = 1.0f;
                                args[1] = 0.15f * config.durationScale; 
                            } else if (startVal > 0.5f && startVal < 1.0f && endVal == 1.0f) { 
                                args[2] = 0.85f - (0.20f * kF * vInf); 
                                args[0] = activeDamping.get();
                                args[1] = activeResponse.get();
                            } else if (endVal == 0.0f && startVal > 5.0f) { 
                                if (isDockView.get() || isIndicator.get()) {
                                    args[2] = config.kineticDockZDepth; 
                                } else {
                                    float baseZ = config.getDyn("r" + safeRow + "_z", 1000.0f);
                                    float zMult = 1.0f + (kF * vInf * 0.4f);
                                    // Row depth is user-controlled; do not let the original
                                    // constructor Z silently override a valid configured value.
                                    args[2] = Math.max(1.0f, baseZ * zMult);
                                }
                            }
                        }
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {}
        }
    }
}
