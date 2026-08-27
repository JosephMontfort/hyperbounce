package dev.joseph.hyperbounce;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ControlCenterHook {
    private static final String TAG = "Hyperbounce_CC";
    private static final Set<ClassLoader> hookedPluginLoaders = new HashSet<>();
    private static boolean sysuiHooked = false;
    private static boolean classLoaderHookInstalled = false;
    private static Object sShadeLowEndAnimFlow = null;

    // Real-time Velocity & Gesture State Tracking
    private static float sCurrentVelocityY = 0.0f;
    private static boolean sIsUserFlicking = false;
    private static long sLastTouchTime = 0;

    // Edit Mode Tracking
    private static volatile boolean sIsEditMode = false;

    // Dynamic Component Priority Map for Real-Time & Persistent Reordering
    private static final Map<String, Integer> sComponentPriorities = new HashMap<>();
    private static final Set<String> sExpandedTileSpecs = new HashSet<>();
    private static boolean sIsWifiFirst = true;
    private static SharedPreferences sPrefs = null;

    static {
        sComponentPriorities.put("QSCardsController", 20);
        sComponentPriorities.put("MediaPlayerController", 30);
        sComponentPriorities.put("BrightnessSliderController", 31);
        sComponentPriorities.put("VolumeSliderController", 32);
        sComponentPriorities.put("DeviceCenterEntryController", 50);
        sComponentPriorities.put("DeviceControlsEntryController", 51);
        sComponentPriorities.put("QSListController", 100);
    }

    private static synchronized void loadSavedPriorities(Context ctx) {
        if (ctx == null) return;
        try {
            if (sPrefs == null) {
                sPrefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE);
            }
            if (sPrefs != null) {
                for (String key : sComponentPriorities.keySet()) {
                    if ("QSListController".equals(key)) continue; // QSList is always anchored at 100
                    int saved = sPrefs.getInt("priority_" + key, -1);
                    if (saved != -1) {
                        sComponentPriorities.put(key, saved);
                    }
                }
                sIsWifiFirst = sPrefs.getBoolean("wifi_card_first", true);

                boolean initialized = sPrefs.getBoolean("has_initialized_tile_specs_v6", false);
                sExpandedTileSpecs.clear();
                if (!initialized) {
                    sExpandedTileSpecs.add("wifi");
                    sExpandedTileSpecs.add("cell");
                    sPrefs.edit().putBoolean("has_initialized_tile_specs_v6", true).putString("expanded_tile_specs", "wifi,cell").apply();
                } else {
                    String expandedStr = sPrefs.getString("expanded_tile_specs", "wifi,cell");
                    if (!TextUtils.isEmpty(expandedStr)) {
                        for (String spec : expandedStr.split(",")) {
                            if (!spec.trim().isEmpty()) {
                                sExpandedTileSpecs.add(spec.trim());
                            }
                        }
                    }
                    if (sExpandedTileSpecs.isEmpty()) {
                        sExpandedTileSpecs.add("wifi");
                        sExpandedTileSpecs.add("cell");
                    }
                }
            }
        } catch (Throwable ignore) {}
    }

    private static synchronized void savePriorities(Context ctx) {
        if (ctx == null && sPrefs == null) return;
        try {
            if (sPrefs == null && ctx != null) {
                sPrefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE);
            }
            if (sPrefs != null) {
                SharedPreferences.Editor editor = sPrefs.edit();
                for (Map.Entry<String, Integer> entry : sComponentPriorities.entrySet()) {
                    editor.putInt("priority_" + entry.getKey(), entry.getValue());
                }
                editor.putBoolean("wifi_card_first", sIsWifiFirst);

                StringBuilder sb = new StringBuilder();
                for (String spec : sExpandedTileSpecs) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(spec);
                }
                editor.putString("expanded_tile_specs", sb.toString());
                editor.apply();
            }
        } catch (Throwable ignore) {}
    }

    private static Boolean sCachedSafeMode = null;
    public static boolean isSafeMode(Context ctx) {
        if (sCachedSafeMode != null) return sCachedSafeMode;
        if (ctx == null) return false;
        try {
            Bundle res = ctx.getContentResolver().call(
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
                "isSafeMode", null, null
            );
            sCachedSafeMode = (res != null && res.getBoolean("safeMode", false));
            return sCachedSafeMode;
        } catch (Throwable t) {
            sCachedSafeMode = false;
            return false;
        }
    }

    public static void tripSafeMode(Context ctx, Throwable e) {
        android.util.Log.e(TAG, "CRITICAL ERROR IN CONTROLCENTER HOOK - TRIPPING SAFE MODE", e);
        sCachedSafeMode = true;
        if (ctx == null) return;
        try {
            ctx.getContentResolver().call(
                Uri.parse("content://dev.joseph.hyperbounce.provider"),
                "setSafeMode", "true", null
            );
        } catch (Throwable ignore) {}
    }

    // Custom Anti-Aliased Resize Handle Vector Badge Drawable
    private static class ResizeHandleDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ResizeHandleDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            bgPaint.setColor(Color.parseColor("#B3000000"));
            bgPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            canvas.drawCircle(w / 2.0f, h / 2.0f, Math.min(w, h) / 2.0f, bgPaint);

            float cx = w * 0.50f;
            float cy = h * 0.50f;
            float off = w * 0.22f;

            canvas.drawLine(cx - off, cy + off, cx + off, cy - off, paint);

            // Top-right arrow head
            Path p1 = new Path();
            p1.moveTo(cx + off - (w * 0.14f), cy - off);
            p1.lineTo(cx + off, cy - off);
            p1.lineTo(cx + off, cy - off + (h * 0.14f));
            canvas.drawPath(p1, paint);

            // Bottom-left arrow head
            Path p2 = new Path();
            p2.moveTo(cx - off + (w * 0.14f), cy + off);
            p2.lineTo(cx - off, cy + off);
            p2.lineTo(cx - off, cy + off - (h * 0.14f));
            canvas.drawPath(p2, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void toggleTileSize(Context ctx, String spec, View tileView) {
        if (spec == null || "wifi".equals(spec) || "cell".equals(spec)) return;
        if (sExpandedTileSpecs.contains(spec)) {
            sExpandedTileSpecs.remove(spec);
        } else {
            sExpandedTileSpecs.add(spec);
        }
        savePriorities(ctx);

        View parent = tileView;
        while (parent != null && !(parent.getClass().getName().contains("RecyclerView"))) {
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else break;
        }
        if (parent != null) {
            final View rv = parent;
            rv.post(() -> {
                try {
                    Method getAdapterMethod = rv.getClass().getMethod("getAdapter");
                    Object adp = getAdapterMethod.invoke(rv);
                    if (adp != null) {
                        Method notifyDataSetChangedMethod = adp.getClass().getMethod("notifyDataSetChanged");
                        notifyDataSetChangedMethod.invoke(adp);
                    }
                } catch (Throwable ignore) {}
            });
        }
    }

    public static synchronized void initPluginHooks(PhysicsHook module, ClassLoader classLoader, Config config) {
        if (classLoader == null || hookedPluginLoaders.contains(classLoader)) return;

        try {
            android.util.Log.i(TAG, "Initializing Control Center Plugin hooks on ClassLoader: " + classLoader);

            // 1. Hook Low-End Animation Switch in Control Center Plugin (Xiaomi Stock Engine)
            try {
                Class<?> companionClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController$Companion", false, classLoader);
                for (Method m : companionClass.getDeclaredMethods()) {
                    if (m.getName().equals("getLowEndAnim")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccLowEndAnim) return true;
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "getLowEndAnim hook error", t);
            }

            try {
                Class<?> deviceUtilsClass = Class.forName("miui.systemui.util.DeviceUtils", false, classLoader);
                for (Method m : deviceUtilsClass.getDeclaredMethods()) {
                    if (m.getName().equals("isLowEndDevice")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccLowEndAnim) return true;
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "isLowEndDevice hook error", t);
            }

            // 2. Hook Touch Tracking & Velocity in MainPanelTouchController
            try {
                Class<?> touchClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelTouchController", false, classLoader);
                for (Method m : touchClass.getDeclaredMethods()) {
                    if (m.getName().equals("handleMotionEvent")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] instanceof MotionEvent) {
                                    MotionEvent ev = (MotionEvent) args[0];
                                    int action = ev.getActionMasked();
                                    if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
                                        sIsUserFlicking = true;
                                        sLastTouchTime = System.currentTimeMillis();
                                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                        sLastTouchTime = System.currentTimeMillis();
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                    if (m.getName().equals("getVelocityY")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            float v = (float) chain.proceed();
                            sCurrentVelocityY = v;
                            return v;
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "touchController hook error", t);
            }

            // 3. Hook MainPanelModeController to track EDIT vs NORMAL mode state changes & persist on exit
            try {
                Class<?> modeControllerClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelModeController", false, classLoader);
                for (Method m : modeControllerClass.getDeclaredMethods()) {
                    if (m.getName().equals("set_mode")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] != null) {
                                    String modeName = args[0].toString();
                                    sIsEditMode = "EDIT".equals(modeName);
                                    if (!sIsEditMode) {
                                        try {
                                            Object modeCtrl = chain.getThisObject();
                                            Method getCtxMethod = modeCtrl.getClass().getMethod("getContext");
                                            Context ctx = (Context) getCtxMethod.invoke(modeCtrl);
                                            savePriorities(ctx);
                                        } catch (Throwable ignore) {}
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 4. Hook ControlCenterExpandController for Velocity and Hide Handlers
            try {
                Class<?> expandControllerClass = Class.forName("miui.systemui.controlcenter.windowview.ControlCenterExpandController", false, classLoader);
                for (Method m : expandControllerClass.getDeclaredMethods()) {
                    if (m.getName().equals("hidePanel")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (System.currentTimeMillis() - sLastTouchTime > 350) {
                                sIsUserFlicking = false;
                                sCurrentVelocityY = 0.0f;
                            }
                            return chain.proceed();
                        });
                    }
                    if (m.getName().equals("onExpandFinish")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] instanceof Float) {
                                    sCurrentVelocityY = ((Float) args[0]).floatValue();
                                    sLastTouchTime = System.currentTimeMillis();
                                    sIsUserFlicking = true;
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "expandController hook error", t);
            }

            // 5. Hook LowEndItemHolderAnimator for Eased-Out Overshoot Release Bounce
            try {
                Class<?> lowEndAnimatorClass = Class.forName("miui.systemui.controlcenter.panel.main.anim.LowEndItemHolderAnimator", false, classLoader);
                Class<?> easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader);
                Method getStyleMethod = easeManagerClass.getMethod("getStyle", int.class, float[].class);

                for (Method m : lowEndAnimatorClass.getDeclaredMethods()) {
                    if (m.getName().equals("expandedStretch")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length >= 4 && (Boolean) args[3]) {
                                    Object bounceEase = getStyleMethod.invoke(null, -2, new float[]{ 0.88f, 0.38f });
                                    Field releaseField = lowEndAnimatorClass.getDeclaredField("releaseTransYEase");
                                    releaseField.setAccessible(true);
                                    releaseField.set(null, bounceEase);
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "lowEndAnimator hook error", t);
            }

            // 6. Hook SpreadRowsAnimator for Eased-Out Overshoot Release Bounce
            try {
                Class<?> spreadAnimatorClass = Class.forName("miui.systemui.controlcenter.panel.main.anim.SpreadRowsAnimator", false, classLoader);
                Class<?> easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader);
                Method getStyleMethod = easeManagerClass.getMethod("getStyle", int.class, float[].class);

                for (Method m : spreadAnimatorClass.getDeclaredMethods()) {
                    if (m.getName().equals("changeExpand")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length >= 4 && (Boolean) args[3]) {
                                    Object bounceEase = getStyleMethod.invoke(null, -2, new float[]{ 0.88f, 0.38f });
                                    Field rowsField = spreadAnimatorClass.getDeclaredField("rows");
                                    rowsField.setAccessible(true);
                                    Object[] rows = (Object[]) rowsField.get(chain.getThisObject());
                                    if (rows != null) {
                                        for (Object row : rows) {
                                            if (row != null) {
                                                Field releaseEaseField = row.getClass().getDeclaredField("releaseEase");
                                                releaseEaseField.setAccessible(true);
                                                releaseEaseField.set(row, bounceEase);
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "spreadAnimator hook error", t);
            }

            // 7. Hook MainPanelItemViewHolder (Center-Bounce, Spring Easing & iOS 26 Jelly)
            try {
                Class<?> holderClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader);
                Class<?> easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader);
                Method getStyleMethod = easeManagerClass.getMethod("getStyle", int.class, float[].class);

                for (Method m : holderClass.getDeclaredMethods()) {
                    if (m.getName().equals("prepareLocation")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (config.ccLowEndAnim || !config.ccAnimEnabled) return result;

                            try {
                                Object holder = chain.getThisObject();
                                Context ctx = null;
                                try {
                                    Method getCtxMethod = holder.getClass().getMethod("getContext");
                                    ctx = (Context) getCtxMethod.invoke(holder);
                                } catch (Throwable ignore) {
                                    try {
                                        Field ivField = holder.getClass().getField("itemView");
                                        View iv = (View) ivField.get(holder);
                                        if (iv != null) ctx = iv.getContext();
                                    } catch (Throwable ignore2) {}
                                }
                                if (ctx == null) return result;

                                float screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                                float screenH = ctx.getResources().getDisplayMetrics().heightPixels;

                                Field shrinkCenterXField = holderClass.getDeclaredField("shrinkCenterX");
                                shrinkCenterXField.setAccessible(true);
                                shrinkCenterXField.setFloat(holder, screenW / 2.0f);

                                Field shrinkCenterYField = holderClass.getDeclaredField("shrinkCenterY");
                                shrinkCenterYField.setAccessible(true);
                                shrinkCenterYField.setFloat(holder, screenH / 2.0f);

                                Field delayPerField = holderClass.getDeclaredField("delayPer");
                                delayPerField.setAccessible(true);
                                delayPerField.setFloat(holder, 0.0f);

                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "prepareLocation hook error", t);
                            }
                            return result;
                        });
                    }

                    if (m.getName().equals("updateEases")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object holder = chain.getThisObject();

                                Field delayPerField = holderClass.getDeclaredField("delayPer");
                                delayPerField.setAccessible(true);
                                delayPerField.setFloat(holder, 0.0f);

                                Object bounceEase = getStyleMethod.invoke(null, -2, new float[]{ 0.88f, 0.38f });
                                Field releaseTransYEaseField = holderClass.getDeclaredField("releaseTransYEase");
                                releaseTransYEaseField.setAccessible(true);
                                releaseTransYEaseField.set(holder, bounceEase);

                                if (!config.ccLowEndAnim && config.ccAnimEnabled) {
                                    Field showEaseField = holderClass.getDeclaredField("showEase");
                                    showEaseField.setAccessible(true);
                                    Object showEase = showEaseField.get(holder);
                                    if (showEase != null) {
                                        Method setFactorsMethod = showEase.getClass().getMethod("setFactors", float.class, float.class);
                                        setFactorsMethod.setAccessible(true);
                                        setFactorsMethod.invoke(showEase, 0.88f, 0.82f);
                                    }

                                    Field hideEaseField = holderClass.getDeclaredField("hideEase");
                                    hideEaseField.setAccessible(true);
                                    Object hideEase = hideEaseField.get(holder);
                                    if (hideEase != null) {
                                        Method setFactorsMethod = hideEase.getClass().getMethod("setFactors", float.class, float.class);
                                        setFactorsMethod.setAccessible(true);
                                        setFactorsMethod.invoke(hideEase, 0.85f, 0.88f);
                                    }

                                    Field shrinkXEaseField = holderClass.getDeclaredField("shrinkXEase");
                                    shrinkXEaseField.setAccessible(true);
                                    Object shrinkXEase = shrinkXEaseField.get(holder);
                                    if (shrinkXEase != null) {
                                        Method setFactorsMethod = shrinkXEase.getClass().getMethod("setFactors", float.class, float.class);
                                        setFactorsMethod.setAccessible(true);
                                        setFactorsMethod.invoke(shrinkXEase, 0.88f, 0.82f);
                                    }

                                    Field shrinkYEaseField = holderClass.getDeclaredField("shrinkYEase");
                                    shrinkYEaseField.setAccessible(true);
                                    Object shrinkYEase = shrinkYEaseField.get(holder);
                                    if (shrinkYEase != null) {
                                        Method setFactorsMethod = shrinkYEase.getClass().getMethod("setFactors", float.class, float.class);
                                        setFactorsMethod.setAccessible(true);
                                        setFactorsMethod.invoke(shrinkYEase, 0.88f, 0.82f);
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }

                    // Hook getDraggable: Enable universal drag & reorder for ALL items in Mode.EDIT
                    if (m.getName().equals("getDraggable")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccModularEditEnabled) {
                                try {
                                    Object holder = chain.getThisObject();
                                    Field modeField = holderClass.getDeclaredField("mode");
                                    modeField.setAccessible(true);
                                    Object mode = modeField.get(holder);
                                    if (mode != null && mode.toString().equals("EDIT")) {
                                        return true;
                                    }
                                } catch (Throwable ignore) {}
                            }
                            return chain.proceed();
                        });
                    }

                    // Hook onFrameCallback: Velocity-Coupled, Height-Normalized, Eased-Out Jelly Physics
                    if (m.getName().equals("onFrameCallback")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (config.ccJellyEnabled) {
                                try {
                                    Object holder = chain.getThisObject();
                                    Field ivField = holderClass.getField("itemView");
                                    View itemView = (View) ivField.get(holder);
                                    if (itemView == null) return result;

                                    Field expandScaleRatioField = holderClass.getDeclaredField("expandScaleRatio");
                                    expandScaleRatioField.setAccessible(true);
                                    float progress = expandScaleRatioField.getFloat(holder);

                                    Field expandTransYField = holderClass.getDeclaredField("expandTransY");
                                    expandTransYField.setAccessible(true);
                                    float expandTransY = expandTransYField.getFloat(holder);

                                    Field spreadRowField = holderClass.getDeclaredField("spreadRow");
                                    spreadRowField.setAccessible(true);
                                    int row = spreadRowField.getInt(holder);

                                    int viewWidth = itemView.getWidth();
                                    int viewHeight = itemView.getHeight();
                                    if (viewWidth <= 0 || viewHeight <= 0) return result;

                                    boolean isRecentGesture = (System.currentTimeMillis() - sLastTouchTime) < 700;
                                    boolean isClosing = (progress < 0.99f && sCurrentVelocityY < -100.0f);

                                    if (!sIsUserFlicking && !isRecentGesture && Math.abs(expandTransY) < 1.0f) {
                                        itemView.setScaleX(1.0f);
                                        itemView.setScaleY(1.0f);
                                        return result;
                                    }

                                    float absVel = Math.abs(sCurrentVelocityY);
                                    float velFactor = (absVel > 0.1f) ? Math.max(0.35f, Math.min(1.20f, absVel / 3500.0f)) : 0.70f;

                                    float density = itemView.getContext().getResources().getDisplayMetrics().density;
                                    float standardCardHeight = 110.0f * density;
                                    float heightDampener = Math.max(0.42f, Math.min(1.0f, standardCardHeight / viewHeight));

                                    float progressStretchY = 0.0f;
                                    float progressSquishX = 0.0f;

                                    if (!isClosing && progress > 0.01f && progress < 0.99f && (sIsUserFlicking || isRecentGesture)) {
                                        float momentum = (float) Math.sin(progress * Math.PI);
                                        float rowStagger = 1.0f + (row * 0.040f);
                                        progressStretchY = momentum * 0.090f * velFactor * heightDampener * rowStagger;
                                        progressSquishX = progressStretchY * 0.38f;
                                    } else if (isClosing && (sIsUserFlicking || isRecentGesture)) {
                                        float momentum = (float) Math.sin(progress * Math.PI);
                                        progressStretchY = -momentum * 0.050f * velFactor * heightDampener;
                                        progressSquishX = -progressStretchY * 0.38f;
                                    }

                                    float strain = Math.max(-0.22f, Math.min(0.50f, expandTransY / 850.0f));
                                    float pullStretchY = strain * 0.32f * heightDampener;
                                    float pullSquishX = strain * 0.14f * heightDampener;

                                    float baseScale = Math.min(1.0f, Math.max(0.89f, 0.91f + (progress * 0.09f)));
                                    float totalScaleY = baseScale + progressStretchY + pullStretchY;
                                    float totalScaleX = baseScale - progressSquishX - pullSquishX;

                                    itemView.setPivotX(viewWidth / 2.0f);
                                    if (expandTransY > 3.0f || (!isClosing && progress > 0.05f && progress < 0.95f)) {
                                        itemView.setPivotY(viewHeight * 0.10f);
                                    } else if (expandTransY < -3.0f || isClosing) {
                                        itemView.setPivotY(viewHeight * 0.90f);
                                    } else {
                                        itemView.setPivotY(viewHeight / 2.0f);
                                    }

                                    itemView.setScaleX(Math.max(0.78f, Math.min(1.30f, totalScaleX)));
                                    itemView.setScaleY(Math.max(0.78f, Math.min(1.40f, totalScaleY)));

                                } catch (Throwable ignore) {}
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "MainPanelItemViewHolder hook error", t);
            }

            // 8. Hook Major Modules: Available in EDIT mode ONLY & Persistent Priority Lookup
            String[] majorModuleClassNames = new String[] {
                "miui.systemui.controlcenter.panel.main.qs.QSCardsController",
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerController",
                "miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController",
                "miui.systemui.controlcenter.panel.main.volume.VolumeSliderController"
            };

            for (String className : majorModuleClassNames) {
                try {
                    Class<?> targetClass = Class.forName(className, false, classLoader);
                    String simpleName = targetClass.getSimpleName();

                    for (Method m : targetClass.getDeclaredMethods()) {
                        if (m.getName().equals("available") && m.getParameterTypes().length == 1) {
                            m.setAccessible(true);
                            module.hook(m).intercept(chain -> {
                                boolean orig = (boolean) chain.proceed();
                                if (config.ccModularEditEnabled && sIsEditMode) {
                                    return true;
                                }
                                return orig;
                            });
                        }

                        // Hook getPriority() to inject dynamic, user-saved modular ordering
                        if (m.getName().equals("getPriority") && m.getParameterTypes().length == 0) {
                            m.setAccessible(true);
                            module.hook(m).intercept(chain -> {
                                if (config.ccModularEditEnabled && sComponentPriorities.containsKey(simpleName)) {
                                    return sComponentPriorities.get(simpleName);
                                }
                                return chain.proceed();
                            });
                        }
                    }
                } catch (Throwable ignore) {}
            }

            // Hook QSCardsController.getListItems() to safely return ordered cards without in-place list corruption
            try {
                Class<?> cardsClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardsController", false, classLoader);
                for (Method m : cardsClass.getDeclaredMethods()) {
                    if (m.getName().equals("getListItems")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            List list = (List) chain.proceed();
                            if (list != null && list.size() >= 2) {
                                List resultList = new ArrayList(list);
                                Object first = list.get(0);
                                Object second = list.get(1);
                                if (!sIsWifiFirst) {
                                    resultList.set(0, second);
                                    resultList.set(1, first);
                                } else {
                                    resultList.set(0, first);
                                    resultList.set(1, second);
                                }
                                return resultList;
                            }
                            return list;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // Hook Smart Home / Google Home: If disabled in Settings, NEVER show on Edit page or Control Center!
            String[] homeEntryClasses = new String[] {
                "miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryController",
                "miui.systemui.controlcenter.panel.main.devicecontrol.DeviceControlsEntryController"
            };
            for (String className : homeEntryClasses) {
                try {
                    Class<?> targetClass = Class.forName(className, false, classLoader);
                    String simpleName = targetClass.getSimpleName();
                    for (Method m : targetClass.getDeclaredMethods()) {
                        if (m.getName().equals("available") && m.getParameterTypes().length == 1) {
                            m.setAccessible(true);
                            module.hook(m).intercept(chain -> {
                                Object controller = chain.getThisObject();
                                boolean settingsAvail = false;
                                try {
                                    Field f = controller.getClass().getDeclaredField("availableOfSettings");
                                    f.setAccessible(true);
                                    settingsAvail = f.getBoolean(controller);
                                } catch (Throwable ignore) {}

                                // If disabled in Settings, NEVER show on Edit page or normal Control Center!
                                if (!settingsAvail) {
                                    return false;
                                }

                                try {
                                    if (controller.getClass().getName().contains("DeviceCenter")) {
                                        Field fMiLink = controller.getClass().getDeclaredField("miLinkController");
                                        fMiLink.setAccessible(true);
                                        Object miLink = fMiLink.get(controller);
                                        if (miLink != null) {
                                            Method mAvail = miLink.getClass().getMethod("getMiLinkAvailable");
                                            if (!((boolean) mAvail.invoke(miLink))) return false;
                                        }
                                    } else if (controller.getClass().getName().contains("DeviceControls")) {
                                        Field fInfo = controller.getClass().getDeclaredField("currentDCEntryInfo");
                                        fInfo.setAccessible(true);
                                        Object info = fInfo.get(controller);
                                        if (info != null) {
                                            Method mAvail = info.getClass().getMethod("getAvailable");
                                            if (!((boolean) mAvail.invoke(info))) return false;
                                        }
                                    }
                                } catch (Throwable ignore) {}

                                if (config.ccModularEditEnabled && sIsEditMode) {
                                    return true;
                                }

                                return chain.proceed();
                            });
                        }
                    }
                } catch (Throwable ignore) {}
            }

            // 9. Hook WordlessModeController: Move "Show icon labels" to TOPMOST (priority 5) & Compact Margins in EDIT mode
            try {
                Class<?> wordlessClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.WordlessModeController", false, classLoader);
                for (Method m : wordlessClass.getDeclaredMethods()) {
                    if (m.getName().equals("getPriority")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccModularEditEnabled) return 5;
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("onBindViewHolder")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (config.ccModularEditEnabled) {
                                try {
                                    Object wordlessCtrl = chain.getThisObject();
                                    Method getHolderMethod = wordlessClass.getMethod("getHolder");
                                    Object holder = getHolderMethod.invoke(wordlessCtrl);
                                    if (holder != null) {
                                        Field ivField = holder.getClass().getField("itemView");
                                        View itemView = (View) ivField.get(holder);
                                        if (itemView != null) {
                                            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                                mlp.topMargin = 0;
                                                itemView.setLayoutParams(mlp);
                                            }
                                        }
                                    }
                                } catch (Throwable ignore) {}
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 10. Hook QSListController.getPriority() & createViewHolder() to inflate genuine 2273 QSCardItemView
            try {
                Class<?> qsListClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSListController", false, classLoader);
                for (Method m : qsListClass.getDeclaredMethods()) {
                    if (m.getName().equals("getPriority")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            return 100; // QSList is always anchored at 100
                        });
                    }
                    if (m.getName().equals("distributeTiles")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object qsListCtrl = chain.getThisObject();
                                Field addedField = qsListClass.getDeclaredField("addedTiles");
                                addedField.setAccessible(true);
                                List added = (List) addedField.get(qsListCtrl);
                                if (added != null && added.size() > 1) {
                                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                                    java.util.Iterator it = added.iterator();
                                    while (it.hasNext()) {
                                        Object rec = it.next();
                                        if (rec != null) {
                                            Field specF = rec.getClass().getDeclaredField("spec");
                                            specF.setAccessible(true);
                                            String sp = (String) specF.get(rec);
                                            if (sp != null) {
                                                if (seen.contains(sp)) {
                                                    it.remove();
                                                } else {
                                                    seen.add(sp);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("createViewHolder")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (result == null) {
                                try {
                                    Object rawArgs = chain.getArgs();
                                    Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                    if (args != null && args.length >= 2 && ((Integer) args[1]) == 2273) {
                                        ViewGroup parent = (ViewGroup) args[0];
                                        Object qsListCtrl = chain.getThisObject();

                                        Class<?> cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader);
                                        Method inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class);
                                        Object binding = inflateMethod.invoke(null, LayoutInflater.from(parent.getContext()), parent, false);

                                        Method getRootMethod = cardBindingClass.getMethod("getRoot");
                                        View root = (View) getRootMethod.invoke(binding);

                                        Field sysUIContextField = qsListCtrl.getClass().getDeclaredField("sysUIContext");
                                        sysUIContextField.setAccessible(true);
                                        Context sysUIContext = (Context) sysUIContextField.get(qsListCtrl);

                                        Class<?> cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader);
                                        Constructor<?> iconCtor = null;
                                        for (Constructor<?> c : cardIconViewClass.getDeclaredConstructors()) {
                                            if (c.getParameterTypes().length == 5) {
                                                iconCtor = c;
                                                break;
                                            }
                                        }
                                        if (iconCtor == null) {
                                            iconCtor = cardIconViewClass.getDeclaredConstructors()[0];
                                        }
                                        iconCtor.setAccessible(true);
                                        Object iconView = (iconCtor.getParameterTypes().length == 5) 
                                            ? iconCtor.newInstance(root.getContext(), sysUIContext, null, 4, null)
                                            : iconCtor.newInstance(root.getContext(), sysUIContext);

                                        Method initMethod = root.getClass().getMethod("init", cardIconViewClass);
                                        initMethod.invoke(root, iconView);

                                        Class<?> cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader);
                                        Constructor<?> holderCtor = cardHolderClass.getConstructor(Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader));
                                        return holderCtor.newInstance(root);
                                    }
                                } catch (Throwable t) {
                                    android.util.Log.e(TAG, "QSListController.createViewHolder 2273 inflation error", t);
                                }
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 11. Hook MainPanelContentDistributor.distributePanels: Load saved priorities & sort rightPanelContent
            try {
                Class<?> distributorClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", false, classLoader);
                for (Method m : distributorClass.getDeclaredMethods()) {
                    if (m.getName().equals("distributePanels")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (config.ccModularEditEnabled) {
                                try {
                                    Object distributor = chain.getThisObject();

                                    Method getCtxMethod = distributorClass.getMethod("getContext");
                                    Context ctx = (Context) getCtxMethod.invoke(distributor);
                                    loadSavedPriorities(ctx);

                                    Field rightPanelContentField = distributorClass.getDeclaredField("rightPanelContent");
                                    rightPanelContentField.setAccessible(true);
                                    List rightList = (List) rightPanelContentField.get(distributor);

                                    if (sIsEditMode) {
                                        Method getChildControllersMethod = distributorClass.getDeclaredMethod("getChildControllers");
                                        getChildControllersMethod.setAccessible(true);
                                        List allChildren = (List) getChildControllersMethod.invoke(distributor);

                                        if (rightList != null && allChildren != null) {
                                            for (Object child : allChildren) {
                                                if (child != null) {
                                                    String cName = child.getClass().getName();
                                                    if (cName.contains("CompactQS") || cName.contains("CompactQSCard") || cName.contains("QSCardsController")) continue;
                                                    try {
                                                        Method availMethod = child.getClass().getMethod("available", boolean.class);
                                                        boolean avail = (boolean) availMethod.invoke(child, false);
                                                        if (avail && !rightList.contains(child)) {
                                                            rightList.add(child);
                                                        }
                                                    } catch (Throwable ignore) {}
                                                }
                                            }
                                        }
                                    }

                                    if (rightList != null && rightList.size() > 1) {
                                        Collections.sort(rightList, (o1, o2) -> {
                                            try {
                                                Method p1 = o1.getClass().getMethod("getPriority");
                                                Method p2 = o2.getClass().getMethod("getPriority");
                                                int pr1 = (int) p1.invoke(o1);
                                                int pr2 = (int) p2.invoke(o2);
                                                return Integer.compare(pr1, pr2);
                                            } catch (Throwable ignore) {
                                                return 0;
                                            }
                                        });
                                    }
                                } catch (Throwable t) {
                                    android.util.Log.e(TAG, "distributePanels hook error", t);
                                }
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 12. Hook QSRecord: Dynamic Representation between Type 8453 (Circle) & 2273 (Pill Card)
            try {
                Class<?> qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader);
                for (Method m : qsRecordClass.getDeclaredMethods()) {
                    if (m.getName().equals("getType")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);
                                if (spec != null && sExpandedTileSpecs.contains(spec)) {
                                    return 2273; // Genuine 2273 Card View Type!
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("getSpanSize")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);
                                if (spec != null && sExpandedTileSpecs.contains(spec)) {
                                    return 2; // Genuine 2-Column Span!
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("getAdded")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);
                                if ("wifi".equals(spec) || "cell".equals(spec)) {
                                    return true;
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("getRemovable")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);
                                if ("wifi".equals(spec) || "cell".equals(spec)) {
                                    return false;
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 13. Hook QSController.removeTile & addTile: Guard Wi-Fi/Cell cards, toggle size on circular tiles!
            try {
                Class<?> qsControllerClass = Class.forName("miui.systemui.controlcenter.qs.QSController", false, classLoader);
                for (Method m : qsControllerClass.getDeclaredMethods()) {
                    if (m.getName().equals("addTile")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] != null) {
                                    Object record = args[0];
                                    Field specField = record.getClass().getDeclaredField("spec");
                                    specField.setAccessible(true);
                                    String spec = (String) specField.get(record);
                                    if ("wifi".equals(spec) || "cell".equals(spec)) {
                                        return null; // NEVER duplicate Wi-Fi or Mobile Data into circular tiles!
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("removeTile")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] != null) {
                                    Object record = args[0];
                                    Field specField = record.getClass().getDeclaredField("spec");
                                    specField.setAccessible(true);
                                    String spec = (String) specField.get(record);

                                    if ("wifi".equals(spec) || "cell".equals(spec)) {
                                        return null; // Wi-Fi and Data cards are permanent top cards, strictly ignore removal/duplication!
                                    }

                                    Throwable t = new Throwable();
                                    for (StackTraceElement elem : t.getStackTrace()) {
                                        if (elem.getClassName().contains("clickAction") && !elem.getClassName().contains("markClickAction")) {
                                            Field ctxField = record.getClass().getDeclaredField("context");
                                            ctxField.setAccessible(true);
                                            Context ctx = (Context) ctxField.get(record);

                                            View tileView = null;
                                            try {
                                                Method getTileViewMethod = record.getClass().getMethod("getTileView");
                                                Object tileViewObj = getTileViewMethod.invoke(record);
                                                if (tileViewObj instanceof View) tileView = (View) tileViewObj;
                                            } catch (Throwable ignore) {}

                                            if (spec != null) {
                                                toggleTileSize(ctx, spec, tileView);
                                            }
                                            return null; // Suppress removeTile when tapping tile body, toggle size instead!
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "removeTile intercept error", t);
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 14. Inject Dedicated Resize Handle Badge on QSRecord View Binding
            try {
                Class<?> qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader);
                for (Method m : qsRecordClass.getDeclaredMethods()) {
                    if (m.getName().equals("onBindViewHolder")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);

                                Field addedField = qsRecordClass.getDeclaredField("added");
                                addedField.setAccessible(true);
                                boolean added = addedField.getBoolean(record);

                                Field modeField = qsRecordClass.getDeclaredField("mode");
                                modeField.setAccessible(true);
                                Object mode = modeField.get(record);
                                boolean isEdit = (mode != null && "EDIT".equals(mode.toString())) || sIsEditMode;

                                Method getTileViewMethod = qsRecordClass.getMethod("getTileView");
                                Object tileViewObj = getTileViewMethod.invoke(record);
                                if (tileViewObj instanceof View) {
                                    View itemView = (View) tileViewObj;
                                    Context ctx = itemView.getContext();
                                    float density = ctx.getResources().getDisplayMetrics().density;
                                    int resizeTag = 0x7f099999;
                                    ImageView resizeHandle = itemView.findViewById(resizeTag);

                                    if (isEdit && added && spec != null && !itemView.getClass().getName().contains("Wordless")) {
                                        final String finalSpec = spec;
                                        if (resizeHandle == null && itemView instanceof ViewGroup) {
                                            resizeHandle = new ImageView(ctx);
                                            resizeHandle.setId(resizeTag);
                                            resizeHandle.setImageDrawable(new ResizeHandleDrawable());

                                            int handleSize = (int) (22 * density);
                                            if (itemView instanceof FrameLayout) {
                                                FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(handleSize, handleSize);
                                                rlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                                rlp.leftMargin = (int) (18 * density);
                                                rlp.topMargin = (int) (34 * density);
                                                ((ViewGroup) itemView).addView(resizeHandle, rlp);
                                            } else {
                                                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(handleSize, handleSize);
                                                ((ViewGroup) itemView).addView(resizeHandle, lp);
                                            }
                                        }
                                        if (resizeHandle != null) {
                                            resizeHandle.setVisibility(View.VISIBLE);
                                            resizeHandle.bringToFront();
                                            resizeHandle.setOnClickListener(v -> toggleTileSize(ctx, finalSpec, itemView));
                                        }
                                    } else if (resizeHandle != null) {
                                        resizeHandle.setVisibility(View.GONE);
                                    }
                                }
                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "QSRecord onBindViewHolder resize handle error", t);
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 14b. Update Resize Handle Visibility on Mode Changed
            try {
                String[] viewClassNames = new String[] {
                    "miui.systemui.controlcenter.qs.tileview.QSTileItemView",
                    "miui.systemui.controlcenter.qs.tileview.QSCardItemView"
                };
                for (String vName : viewClassNames) {
                    Class<?> vClass = Class.forName(vName, false, classLoader);
                    for (Method m : vClass.getDeclaredMethods()) {
                        if (m.getName().equals("onModeChanged")) {
                            m.setAccessible(true);
                            module.hook(m).intercept(chain -> {
                                Object result = chain.proceed();
                                try {
                                    View itemView = (View) chain.getThisObject();
                                    Object rawArgs = chain.getArgs();
                                    Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                    boolean isEdit = args != null && args.length > 0 && args[0] != null && "EDIT".equals(args[0].toString());
                                    int resizeTag = 0x7f099999;
                                    ImageView resizeHandle = itemView.findViewById(resizeTag);
                                    if (resizeHandle != null) {
                                        resizeHandle.setVisibility(isEdit ? View.VISIBLE : View.GONE);
                                        if (isEdit) resizeHandle.bringToFront();
                                    }
                                } catch (Throwable ignore) {}
                                return result;
                            });
                        }
                    }
                }
            } catch (Throwable ignore) {}

            // 15. Disable Slider Touch / Seeking during EDIT mode
            try {
                Class<?> verticalSeekBarClass = Class.forName("miui.systemui.controlcenter.widget.VerticalSeekBar", false, classLoader);
                for (Method m : verticalSeekBarClass.getDeclaredMethods()) {
                    if (m.getName().equals("onTouchEvent")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (sIsEditMode) return false;
                            return chain.proceed();
                        });
                    }
                    if (m.getName().equals("dispatchTouchEvent")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (sIsEditMode) return false;
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 16. Attach SpannedGridLayoutManager for true 2D bin-packed Control Center grid
            try {
                Class<?> adapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader);
                for (Method m : adapterClass.getDeclaredMethods()) {
                    if (m.getName().equals("onAttachedToRecyclerView")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object adapter = chain.getThisObject();
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] instanceof androidx.recyclerview.widget.RecyclerView) {
                                    androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) args[0];
                                    SpannedGridLayoutManager customLm = new SpannedGridLayoutManager(rv.getContext(), 4);
                                    Field lmField = adapterClass.getDeclaredField("layoutManager");
                                    lmField.setAccessible(true);
                                    lmField.set(adapter, customLm);
                                    rv.setLayoutManager(customLm);
                                }
                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "Failed to attach SpannedGridLayoutManager", t);
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 17. Dynamic Cross-Component Rearranging with Zero Boundaries Everywhere
            try {
                Class<?> helperClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter$itemTouchHelper$2", false, classLoader);
                for (Method m : helperClass.getDeclaredMethods()) {
                    if (m.getName().equals("canDropOver")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length >= 3) {
                                    Object targetViewHolder = args[2];
                                    if (targetViewHolder != null) {
                                        Method getItemViewTypeMethod = targetViewHolder.getClass().getMethod("getItemViewType");
                                        int targetType = (int) getItemViewTypeMethod.invoke(targetViewHolder);
                                        if (targetType == 8398 || targetType == 3484337) {
                                            return false;
                                        }

                                        Method getOwnerMethod = targetViewHolder.getClass().getMethod("getOwner");
                                        Object targetOwner = getOwnerMethod.invoke(targetViewHolder);
                                        if (targetOwner != null && targetOwner.getClass().getName().contains("QSListController")) {
                                            Method getItemMethod = targetViewHolder.getClass().getMethod("getItem$miui_controlcenter_release");
                                            Object targetItem = getItemMethod.invoke(targetViewHolder);
                                            if (targetItem != null) {
                                                Field addedField = targetItem.getClass().getDeclaredField("added");
                                                addedField.setAccessible(true);
                                                boolean isTargetAdded = addedField.getBoolean(targetItem);
                                                if (!isTargetAdded) {
                                                    return false;
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return chain.proceed();
                        });
                    }

                    if (m.getName().equals("onMove")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length >= 3) {
                                    Object helperCallback = chain.getThisObject();
                                    Field this0Field = helperClass.getDeclaredField("this$0");
                                    this0Field.setAccessible(true);
                                    Object adapter = this0Field.get(helperCallback);

                                    Object sourceViewHolder = args[1];
                                    Object targetViewHolder = args[2];

                                    if (adapter != null && sourceViewHolder != null && targetViewHolder != null) {
                                        Method getItemViewTypeMethod = targetViewHolder.getClass().getMethod("getItemViewType");
                                        int targetType = (int) getItemViewTypeMethod.invoke(targetViewHolder);
                                        if (targetType == 8398 || targetType == 3484337) {
                                            return false;
                                        }

                                        Method getOwnerMethod = sourceViewHolder.getClass().getMethod("getOwner");
                                        Object sourceOwner = getOwnerMethod.invoke(sourceViewHolder);

                                        Method getTargetOwnerMethod = targetViewHolder.getClass().getMethod("getOwner");
                                        Object targetOwner = getTargetOwnerMethod.invoke(targetViewHolder);

                                        // Wi-Fi and Mobile Data card reordering within QSCardsController!
                                        if (sourceOwner != null && targetOwner != null && sourceOwner.getClass().getName().contains("QSCardsController") && sourceOwner.equals(targetOwner)) {
                                            sIsWifiFirst = !sIsWifiFirst;
                                            Field ctxField = adapter.getClass().getDeclaredField("context");
                                            ctxField.setAccessible(true);
                                            Context ctx = (Context) ctxField.get(adapter);
                                            savePriorities(ctx);

                                            Method notifyChangedMethod = adapter.getClass().getMethod("notifyChanged", boolean.class, boolean.class);
                                            notifyChangedMethod.invoke(adapter, true, true);
                                            return true;
                                        }

                                        // Circular QSList tiles reordering
                                        if (sourceOwner != null && targetOwner != null && sourceOwner.equals(targetOwner)) {
                                            Method getItemMethod = sourceViewHolder.getClass().getMethod("getItem$miui_controlcenter_release");
                                            Object sourceItem = getItemMethod.invoke(sourceViewHolder);

                                            Method getTargetItemMethod = targetViewHolder.getClass().getMethod("getItem$miui_controlcenter_release");
                                            Object targetItem = getTargetItemMethod.invoke(targetViewHolder);

                                            Method moveElementMethod = sourceOwner.getClass().getMethod("moveElement", getItemMethod.getReturnType(), getTargetItemMethod.getReturnType());
                                            return moveElementMethod.invoke(sourceOwner, sourceItem, targetItem);
                                        }

                                        // Cross-component movement anywhere across ANY modules!
                                        Field contentListField = adapter.getClass().getDeclaredField("contentList");
                                        contentListField.setAccessible(true);
                                        List contentList = (List) contentListField.get(adapter);

                                        if (contentList != null && sourceOwner != null && targetOwner != null && !sourceOwner.equals(targetOwner)) {
                                            int fromIndex = contentList.indexOf(sourceOwner);
                                            int toIndex = contentList.indexOf(targetOwner);
                                            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                                Object moved = contentList.remove(fromIndex);
                                                contentList.add(toIndex, moved);

                                                for (int i = 0; i < contentList.size(); i++) {
                                                    Object c = contentList.get(i);
                                                    String name = c.getClass().getSimpleName();
                                                    sComponentPriorities.put(name, 20 + i * 10);
                                                }

                                                Field ctxField = adapter.getClass().getDeclaredField("context");
                                                ctxField.setAccessible(true);
                                                Context ctx = (Context) ctxField.get(adapter);
                                                savePriorities(ctx);

                                                Method notifyChangedMethod = adapter.getClass().getMethod("notifyChanged", boolean.class, boolean.class);
                                                notifyChangedMethod.invoke(adapter, true, true);
                                                return true;
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "onMove dynamic rearrange hook error", t);
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 17. Hook BlurController for fluid window blur
            try {
                Class<?> blurClass = Class.forName("miui.systemui.controlcenter.windowview.BlurController", false, classLoader);
                for (Method m : blurClass.getDeclaredMethods()) {
                    if (m.getName().equals("onCreate")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            if (config.ccLowEndAnim || !config.ccAnimEnabled) return result;
                            try {
                                Object blurController = chain.getThisObject();
                                Field animConfigField = blurClass.getDeclaredField("animConfig");
                                animConfigField.setAccessible(true);
                                Object animConfig = animConfigField.get(blurController);
                                if (animConfig != null) {
                                    Class<?> easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader);
                                    Method getStyleMethod = easeManagerClass.getMethod("getStyle", int.class, float[].class);
                                    Object customEase = getStyleMethod.invoke(null, -2, new float[]{ 0.88f, 0.85f });
                                    Method setEaseMethod = animConfig.getClass().getMethod("setEase", easeManagerClass.getDeclaredClasses()[0]);
                                    setEaseMethod.setAccessible(true);
                                    setEaseMethod.invoke(animConfig, customEase);
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            hookedPluginLoaders.add(classLoader);
            android.util.Log.i(TAG, "Control Center Plugin hooks successfully attached!");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "initPluginHooks fatal error", t);
        }
    }

    public static void initSystemUIHooks(PhysicsHook module, ClassLoader classLoader, Config config) {
        if (classLoader == null || sysuiHooked) return;

        try {
            try {
                Class<?> factoryClass = Class.forName("com.android.systemui.shared.plugins.PluginInstance$PluginFactory", false, classLoader);
                for (Method m : factoryClass.getDeclaredMethods()) {
                    if (m.getName().equals("createPlugin")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object plugin = chain.proceed();
                            if (plugin != null) {
                                ClassLoader pluginLoader = plugin.getClass().getClassLoader();
                                if (pluginLoader != null) {
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        initPluginHooks(module, pluginLoader, config);
                                    });
                                }
                            }
                            return plugin;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            try {
                Class<?> cccClass = Class.forName("com.miui.systemui.controlcenter.container.ControlCenterContentController", false, classLoader);
                for (Method m : cccClass.getDeclaredMethods()) {
                    if (m.getName().equals("setPlugin") || m.getName().equals("onPluginLoaded")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length > 0 && args[0] != null) {
                                    ClassLoader pluginLoader = args[0].getClass().getClassLoader();
                                    if (pluginLoader != null) {
                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                            initPluginHooks(module, pluginLoader, config);
                                        });
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            try {
                Class<?> miuiConfigsClass = Class.forName("com.miui.utils.configs.MiuiConfigs", false, classLoader);
                for (Method m : miuiConfigsClass.getDeclaredMethods()) {
                    if (m.getName().equals("isLowEndDevice") || m.getName().equals("isLiteOrLowDevice")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccLowEndAnim) return true;
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            try {
                Class<?> shadeInteractorClass = Class.forName("com.miui.systemui.shade.MiuiShadeInteractor", false, classLoader);
                for (Constructor<?> c : shadeInteractorClass.getDeclaredConstructors()) {
                    c.setAccessible(true);
                    module.hook(c).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object interactor = chain.getThisObject();
                            Field lowEndAnimField = shadeInteractorClass.getDeclaredField("lowEndAnim");
                            lowEndAnimField.setAccessible(true);
                            sShadeLowEndAnimFlow = lowEndAnimField.get(interactor);
                            android.util.Log.i(TAG, "Captured MiuiShadeInteractor.lowEndAnim flow: " + sShadeLowEndAnimFlow);
                        } catch (Throwable ignore) {}
                        return result;
                    });
                }
            } catch (Throwable ignore) {}

            try {
                Class<?> readonlyStateFlowClass = Class.forName("kotlinx.coroutines.flow.ReadonlyStateFlow", false, classLoader);
                for (Method m : readonlyStateFlowClass.getDeclaredMethods()) {
                    if (m.getName().equals("getValue")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            if (config.ccLowEndAnim) {
                                Object target = chain.getThisObject();
                                if (target == sShadeLowEndAnimFlow || (sShadeLowEndAnimFlow == null && chain.proceed() instanceof Boolean)) {
                                    return Boolean.TRUE;
                                }
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            sysuiHooked = true;
            android.util.Log.i(TAG, "SystemUI hooks successfully initialized!");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "initSystemUIHooks fatal error", t);
        }
    }
}
