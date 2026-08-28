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

    // Tile Grouping Model & State
    public static class TileGroup {
        public String id;
        public List<String> specs = new ArrayList<>();
        public boolean isVertical = false;

        public TileGroup(String id, List<String> specs) {
            this(id, specs, false);
        }

        public TileGroup(String id, List<String> specs, boolean isVertical) {
            this.id = id;
            this.specs = specs != null ? specs : new ArrayList<>();
            this.isVertical = isVertical;
        }
    }

    public static final List<TileGroup> sTileGroups = new ArrayList<>();
    public static volatile boolean sIsGroupSelectMode = false;
    public static final Set<String> sSelectedTileSpecs = new java.util.LinkedHashSet<>();
    public static volatile boolean sIsDisassembleMode = false;
    public static final Set<String> sSelectedDisassembleGroups = new java.util.LinkedHashSet<>();
    public static volatile Object sQSListController = null;
    public static volatile Object sMainPanelAdapter = null;
    public static volatile List<Object> sAllQSRecords = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static volatile Object sFrameCallback = null;
    private static final ThreadLocal<Set<Object>> sBindingRecords = ThreadLocal.withInitial(HashSet::new);

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

                // Load Tile Groups (support v2 with isVertical and legacy v1)
                String groupsStrV2 = sPrefs.getString("tile_groups_v2", "");
                sTileGroups.clear();
                if (!TextUtils.isEmpty(groupsStrV2)) {
                    for (String gEntry : groupsStrV2.split(";")) {
                        String[] parts = gEntry.split(":");
                        if (parts.length >= 3) {
                            String gId = parts[0];
                            boolean isVert = "V".equalsIgnoreCase(parts[1]);
                            List<String> gSpecs = new ArrayList<>();
                            for (String sp : parts[2].split(",")) {
                                if (!sp.trim().isEmpty()) gSpecs.add(sp.trim());
                            }
                            if (gSpecs.size() == 4 || gSpecs.size() == 2) {
                                sTileGroups.add(new TileGroup(gId, gSpecs, isVert));
                            }
                        }
                    }
                } else {
                    String groupsStr = sPrefs.getString("tile_groups_v1", "");
                    if (!TextUtils.isEmpty(groupsStr)) {
                        for (String gEntry : groupsStr.split(";")) {
                            String[] parts = gEntry.split(":");
                            if (parts.length == 2) {
                                String gId = parts[0];
                                List<String> gSpecs = new ArrayList<>();
                                for (String sp : parts[1].split(",")) {
                                    if (!sp.trim().isEmpty()) gSpecs.add(sp.trim());
                                }
                                if (gSpecs.size() == 4 || gSpecs.size() == 2) {
                                    sTileGroups.add(new TileGroup(gId, gSpecs, false));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {}
    }

    public static synchronized void resetLayoutToStock(Context ctx) {
        if (ctx == null) return;
        try {
            if (sPrefs == null) {
                sPrefs = ctx.getSharedPreferences("hyperbounce_cc_layout", Context.MODE_PRIVATE);
            }
            if (sPrefs != null) {
                sPrefs.edit().clear().commit();
            }
            sTileGroups.clear();
            sExpandedTileSpecs.clear();
            sExpandedTileSpecs.add("wifi");
            sExpandedTileSpecs.add("cell");
            sComponentPriorities.clear();
            sComponentPriorities.put("CompactQS", 0);
            sComponentPriorities.put("CompactQSCard", 1);
            sComponentPriorities.put("QSCardsController", 2);
            sComponentPriorities.put("BigMediaController", 3);
            sComponentPriorities.put("BrightnessVolumeController", 4);
            sComponentPriorities.put("QSListController", 100);
            savePriorities(ctx);
            android.util.Log.i(TAG, "Control Center Layout successfully reset to stock!");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "resetLayoutToStock error", t);
        }
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

                // Save Tile Groups (v2 supports 4-tile and 2-tile groups with orientation)
                StringBuilder groupsSb = new StringBuilder();
                for (TileGroup g : sTileGroups) {
                    if (g.specs.size() == 4 || g.specs.size() == 2) {
                        if (groupsSb.length() > 0) groupsSb.append(";");
                        groupsSb.append(g.id).append(":").append(g.isVertical ? "V" : "H").append(":");
                        for (int i = 0; i < g.specs.size(); i++) {
                            if (i > 0) groupsSb.append(",");
                            groupsSb.append(g.specs.get(i));
                        }
                    }
                }
                editor.putString("tile_groups_v2", groupsSb.toString());

                editor.commit();
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

    // Custom Anti-Aliased Rotate Icon Badge Drawable for 2-Tile Groups
    private static class RotateIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RotateIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.0f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            bgPaint.setColor(Color.parseColor("#99000000"));
            bgPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float r = Math.min(bounds.width(), bounds.height()) / 2f;
            canvas.drawCircle(cx, cy, r, bgPaint);

            float arcR = r * 0.55f;
            android.graphics.RectF arcRect = new android.graphics.RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
            canvas.drawArc(arcRect, 30, 260, false, paint);

            float arrowX = cx + arcR * (float) Math.cos(Math.toRadians(290));
            float arrowY = cy + arcR * (float) Math.sin(Math.toRadians(290));
            canvas.drawLine(arrowX, arrowY, arrowX - 5f, arrowY - 5f, paint);
            canvas.drawLine(arrowX, arrowY, arrowX + 5f, arrowY - 5f, paint);
        }

        @Override
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override
        public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
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

    // Custom Anti-Aliased Selection Circle Badge Drawable (Hollow vs Checked)
    private static class SelectCircleDrawable extends Drawable {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean isChecked = false;

        public SelectCircleDrawable() {
            ringPaint.setColor(Color.parseColor("#E6FFFFFF"));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(3.0f);

            bgPaint.setColor(Color.parseColor("#0D84FF"));
            bgPaint.setStyle(Paint.Style.FILL);

            checkPaint.setColor(Color.WHITE);
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(3.5f);
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setChecked(boolean checked) {
            this.isChecked = checked;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            float cx = w / 2.0f;
            float cy = h / 2.0f;
            float radius = Math.min(w, h) / 2.0f - 2.0f;

            if (isChecked) {
                canvas.drawCircle(cx, cy, radius, bgPaint);
                // Draw checkmark
                Path checkPath = new Path();
                checkPath.moveTo(cx - radius * 0.45f, cy);
                checkPath.lineTo(cx - radius * 0.10f, cy + radius * 0.40f);
                checkPath.lineTo(cx + radius * 0.50f, cy - radius * 0.35f);
                canvas.drawPath(checkPath, checkPaint);
            } else {
                Paint darkBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                darkBg.setColor(Color.parseColor("#80000000"));
                darkBg.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius, darkBg);
                canvas.drawCircle(cx, cy, radius, ringPaint);
            }
        }

        @Override public void setAlpha(int alpha) { ringPaint.setAlpha(alpha); bgPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Custom Anti-Aliased 4-Squares Group Vector Drawable for Header Button
    private static class GroupGridDrawable extends Drawable {
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GroupGridDrawable(boolean active) {
            boxPaint.setColor(active ? Color.parseColor("#0D84FF") : Color.WHITE);
            boxPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            float pad = w * 0.22f;
            float size = (w - pad * 2.0f - w * 0.12f) / 2.0f;
            float gap = w * 0.12f;
            float corner = size * 0.28f;

            // Box 1 (top-left)
            canvas.drawRoundRect(pad, pad, pad + size, pad + size, corner, corner, boxPaint);
            // Box 2 (top-right)
            canvas.drawRoundRect(pad + size + gap, pad, pad + size + gap + size, pad + size, corner, corner, boxPaint);
            // Box 3 (bottom-left)
            canvas.drawRoundRect(pad, pad + size + gap, pad + size, pad + size + gap + size, corner, corner, boxPaint);
            // Box 4 (bottom-right)
            canvas.drawRoundRect(pad + size + gap, pad + size + gap, pad + size + gap + size, pad + size + gap + size, corner, corner, boxPaint);
        }

        @Override public void setAlpha(int alpha) { boxPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Custom Anti-Aliased Ungroup / Split Badge Drawable
    private static class UngroupBadgeDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public UngroupBadgeDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.0f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            bgPaint.setColor(Color.parseColor("#B3E53935")); // Subtle translucent red accent
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

            // Draw X mark
            canvas.drawLine(cx - off, cy - off, cx + off, cy + off, paint);
            canvas.drawLine(cx + off, cy - off, cx - off, cy + off, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Custom Anti-Aliased Disassemble / Ungroup Header Vector Drawable
    private static class UngroupHeaderDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public UngroupHeaderDrawable(boolean active) {
            paint.setColor(active ? Color.parseColor("#FF453A") : Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.0f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float off = w * 0.22f;
            float d = w * 0.08f;

            // 4 outward corners mark
            canvas.drawLine(cx - off, cy - d, cx - off, cy - off, paint);
            canvas.drawLine(cx - off, cy - off, cx - d, cy - off, paint);

            canvas.drawLine(cx + d, cy - off, cx + off, cy - off, paint);
            canvas.drawLine(cx + off, cy - off, cx + off, cy - d, paint);

            canvas.drawLine(cx - off, cy + d, cx - off, cy + off, paint);
            canvas.drawLine(cx - off, cy + off, cx - d, cy + off, paint);

            canvas.drawLine(cx + d, cy + off, cx + off, cy + off, paint);
            canvas.drawLine(cx + off, cy + off, cx + off, cy + d, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void refreshAdapter(View root, Context ctx) {
        try {
            View decor = root.getRootView();
            int rvId = ctx.getResources().getIdentifier("main_panel", "id", ctx.getPackageName());
            View rv = decor.findViewById(rvId);
            if (rv != null) {
                Method getAdapterMethod = rv.getClass().getMethod("getAdapter");
                Object adp = getAdapterMethod.invoke(rv);
                if (adp != null) {
                    Method notifyDataSetChangedMethod = adp.getClass().getMethod("notifyDataSetChanged");
                    notifyDataSetChangedMethod.invoke(adp);
                }
            }
        } catch (Throwable ignore) {}
    }

    private static void triggerTileToggle(Object tile, Object record, View childView, ClassLoader classLoader) {
        try {
            if (tile != null) {
                try {
                    Method m = tile.getClass().getMethod("click");
                    m.invoke(tile);
                } catch (NoSuchMethodException e) {
                    try {
                        Method m = tile.getClass().getMethod("click", View.class);
                        m.invoke(tile, childView);
                    } catch (Throwable ignore) {}
                }
            }
            if (record != null) {
                try {
                    Method getClickActionMethod = record.getClass().getMethod("getClickAction");
                    Object clickAction = getClickActionMethod.invoke(record);
                    if (clickAction != null) {
                        for (Method cm : clickAction.getClass().getMethods()) {
                            if (cm.getName().equals("invoke") && cm.getParameterTypes().length == 1) {
                                cm.invoke(clickAction, childView);
                                break;
                            } else if (cm.getName().equals("invoke") && cm.getParameterTypes().length == 0) {
                                cm.invoke(clickAction);
                                break;
                            }
                        }
                    }
                } catch (Throwable ignore) {}
            }
            if (childView != null && tile != null) {
                childView.postDelayed(() -> {
                    try {
                        Method getStateMethod = tile.getClass().getMethod("getState");
                        Object tileState = getStateMethod.invoke(tile);
                        boolean isConn = false;
                        try {
                            Method isConnMethod = tile.getClass().getMethod("isConnected");
                            isConn = (boolean) isConnMethod.invoke(tile);
                        } catch (Throwable ignore) {}
                        Method updateStateMethod = childView.getClass().getMethod("updateState", Class.forName("com.android.systemui.plugins.qs.QSTile$State", false, classLoader), boolean.class, boolean.class);
                        updateStateMethod.invoke(childView, tileState, isConn, true);
                    } catch (Throwable ignore) {}
                }, 120);
            }
        } catch (Throwable t) {
            android.util.Log.e(TAG, "triggerTileToggle error", t);
        }
    }

    private static void triggerTileSecondaryClick(Object tile, Object record, View childView) {
        try {
            if (tile != null) {
                try {
                    Method m = tile.getClass().getMethod("secondaryClick");
                    m.invoke(tile);
                    return;
                } catch (NoSuchMethodException e) {
                    try {
                        Method m = tile.getClass().getMethod("secondaryClick", View.class);
                        m.invoke(tile, childView);
                        return;
                    } catch (NoSuchMethodException e2) {
                        try {
                            Method m2 = tile.getClass().getMethod("longClick");
                            m2.invoke(tile);
                            return;
                        } catch (Throwable ignore) {}
                    }
                }
            }
            if (record != null) {
                try {
                    Method getLongClickActionMethod = record.getClass().getMethod("getLongClickAction");
                    Object longClickAction = getLongClickActionMethod.invoke(record);
                    if (longClickAction != null) {
                        for (Method lm : longClickAction.getClass().getMethods()) {
                            if (lm.getName().equals("invoke") && lm.getParameterTypes().length == 1) {
                                lm.invoke(longClickAction, childView);
                                return;
                            } else if (lm.getName().equals("invoke") && lm.getParameterTypes().length == 0) {
                                lm.invoke(longClickAction);
                                return;
                            }
                        }
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable t) {
            android.util.Log.e(TAG, "triggerTileSecondaryClick error", t);
        }
    }

    private static void hideAllRotateButtons(ViewGroup root) {
        if (root == null) return;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != null) {
                if (child.getId() == 0x7f099992) {
                    child.setVisibility(View.GONE);
                }
                if (child instanceof ViewGroup) {
                    hideAllRotateButtons((ViewGroup) child);
                }
            }
        }
    }

    private static void ensureGroupButtonInjected(Object controller, ClassLoader classLoader) {
        if (controller == null) return;
        try {
            Method getViewMethod = controller.getClass().getMethod("getView");
            View root = (View) getViewMethod.invoke(controller);
            if (root == null) return;

            Context ctx = root.getContext();
            float density = ctx.getResources().getDisplayMetrics().density;

            int headerId = ctx.getResources().getIdentifier("customize_header", "id", ctx.getPackageName());
            int completeBtnId = ctx.getResources().getIdentifier("complete_button", "id", ctx.getPackageName());
            int titleId = ctx.getResources().getIdentifier("customize_title", "id", ctx.getPackageName());

            ViewGroup customizeHeader = root.findViewById(headerId);
            View completeButton = root.findViewById(completeBtnId);
            View customizeTitle = root.findViewById(titleId);

            if (customizeHeader == null || completeButton == null) return;

            int groupTag = 0x7f099997;
            int disassembleTag = 0x7f099994;

            ImageView groupButton = customizeHeader.findViewById(groupTag);
            ImageView disassembleButton = customizeHeader.findViewById(disassembleTag);

            int btnSize = (completeButton.getLayoutParams() != null && completeButton.getLayoutParams().height > 0)
                ? completeButton.getLayoutParams().height : (int) (40 * density);
            if (btnSize <= 0) btnSize = (int) (40 * density);

            if (groupButton == null) {
                groupButton = new ImageView(ctx);
                groupButton.setId(groupTag);
                groupButton.setImageDrawable(new GroupGridDrawable(sIsGroupSelectMode));

                ViewGroup.LayoutParams clp = null;
                try {
                    Method genMethod = customizeHeader.getClass().getMethod("generateDefaultLayoutParams");
                    genMethod.setAccessible(true);
                    clp = (ViewGroup.LayoutParams) genMethod.invoke(customizeHeader);
                } catch (Throwable t) {
                    try {
                        Class<?> lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", false, classLoader);
                        clp = (ViewGroup.LayoutParams) lpClass.getConstructor(int.class, int.class).newInstance(btnSize, btnSize);
                    } catch (Throwable t2) {
                        clp = new ViewGroup.MarginLayoutParams(btnSize, btnSize);
                    }
                }

                if (clp != null) {
                    clp.width = btnSize;
                    clp.height = btnSize;
                    try {
                        clp.getClass().getField("endToStart").setInt(clp, completeButton.getId());
                        clp.getClass().getField("topToTop").setInt(clp, completeButton.getId());
                        clp.getClass().getField("bottomToBottom").setInt(clp, completeButton.getId());
                        clp.getClass().getField("rightMargin").setInt(clp, (int) (8 * density));
                    } catch (Throwable ignore) {}
                }

                try {
                    Class<?> miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader);
                    Method setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View.class, int.class);
                    setModeMethod.invoke(null, groupButton, 1);

                    Class<?> tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader);
                    Object tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null);
                    Method getBlendMethod = tokenClass.getMethod("getCC_EDIT_BUTTON_BLEND_COLORS");
                    Object blendColors = getBlendMethod.invoke(tokenInst);
                    Method setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View.class, blendColors.getClass());
                    setBlendMethod.invoke(null, groupButton, blendColors);

                } catch (Throwable ignore) {}

                final float gRadius = btnSize / 2f;
                groupButton.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View v, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), gRadius);
                    }
                });
                groupButton.setClipToOutline(true);

                try {
                    Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                    Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                    boolean bOpened = (boolean) getBlurM.invoke(null, ctx);
                    if (!bOpened) {
                        android.graphics.drawable.GradientDrawable gBg = new android.graphics.drawable.GradientDrawable();
                        gBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        gBg.setColor(Color.parseColor("#33808080"));
                        groupButton.setBackground(gBg);
                    }
                } catch (Throwable ignore) {}

                try {
                    Method attachTouchMethod = controller.getClass().getMethod("attachTouchTarget", View.class);
                    attachTouchMethod.invoke(controller, groupButton);
                } catch (Throwable ignore) {}

                customizeHeader.addView(groupButton, clp);
            }

            if (disassembleButton == null) {
                disassembleButton = new ImageView(ctx);
                disassembleButton.setId(disassembleTag);
                disassembleButton.setImageDrawable(new UngroupHeaderDrawable(sIsDisassembleMode));

                ViewGroup.LayoutParams dlp = null;
                try {
                    Method genMethod = customizeHeader.getClass().getMethod("generateDefaultLayoutParams");
                    genMethod.setAccessible(true);
                    dlp = (ViewGroup.LayoutParams) genMethod.invoke(customizeHeader);
                } catch (Throwable t) {
                    try {
                        Class<?> lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", false, classLoader);
                        dlp = (ViewGroup.LayoutParams) lpClass.getConstructor(int.class, int.class).newInstance(btnSize, btnSize);
                    } catch (Throwable t2) {
                        dlp = new ViewGroup.MarginLayoutParams(btnSize, btnSize);
                    }
                }

                if (dlp != null) {
                    dlp.width = btnSize;
                    dlp.height = btnSize;
                    try {
                        dlp.getClass().getField("endToStart").setInt(dlp, groupButton.getId());
                        dlp.getClass().getField("topToTop").setInt(dlp, completeButton.getId());
                        dlp.getClass().getField("bottomToBottom").setInt(dlp, completeButton.getId());
                        dlp.getClass().getField("rightMargin").setInt(dlp, (int) (8 * density));
                    } catch (Throwable ignore) {}
                }

                if (customizeTitle != null && customizeTitle.getLayoutParams() != null) {
                    try {
                        customizeTitle.getLayoutParams().getClass().getField("endToStart").setInt(customizeTitle.getLayoutParams(), disassembleButton.getId());
                    } catch (Throwable ignore) {}
                }

                try {
                    Class<?> miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader);
                    Method setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View.class, int.class);
                    setModeMethod.invoke(null, disassembleButton, 1);

                    Class<?> tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader);
                    Object tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null);
                    Method getBlendMethod = tokenClass.getMethod("getCC_EDIT_BUTTON_BLEND_COLORS");
                    Object blendColors = getBlendMethod.invoke(tokenInst);
                    Method setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View.class, blendColors.getClass());
                    setBlendMethod.invoke(null, disassembleButton, blendColors);

                } catch (Throwable ignore) {}

                final float dRadius = btnSize / 2f;
                disassembleButton.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View v, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dRadius);
                    }
                });
                disassembleButton.setClipToOutline(true);

                try {
                    Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                    Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                    boolean bOpened = (boolean) getBlurM.invoke(null, ctx);
                    if (!bOpened) {
                        android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
                        dBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        dBg.setColor(Color.parseColor("#33808080"));
                        disassembleButton.setBackground(dBg);
                    }
                } catch (Throwable ignore) {}

                try {
                    Method attachTouchMethod = controller.getClass().getMethod("attachTouchTarget", View.class);
                    attachTouchMethod.invoke(controller, disassembleButton);
                } catch (Throwable ignore) {}

                customizeHeader.addView(disassembleButton, dlp);
            }

            final ImageView finalGroupBtn = groupButton;
            final ImageView finalDisassembleBtn = disassembleButton;

            groupButton.setImageDrawable(new GroupGridDrawable(sIsGroupSelectMode));
            groupButton.setVisibility(View.VISIBLE);
            groupButton.bringToFront();

            disassembleButton.setImageDrawable(new UngroupHeaderDrawable(sIsDisassembleMode));
            disassembleButton.setVisibility(View.VISIBLE);
            disassembleButton.bringToFront();

            groupButton.setOnClickListener(v -> {
                try {
                    if (!sIsGroupSelectMode) {
                        sIsGroupSelectMode = true;
                        sIsDisassembleMode = false;
                        sSelectedDisassembleGroups.clear();
                        sSelectedTileSpecs.clear();
                        finalGroupBtn.setImageDrawable(new GroupGridDrawable(true));
                        if (finalDisassembleBtn != null) finalDisassembleBtn.setImageDrawable(new UngroupHeaderDrawable(false));
                    } else {
                        if (sSelectedTileSpecs.size() == 4 || sSelectedTileSpecs.size() == 2) {
                            String groupId = "group_" + System.currentTimeMillis();
                            for (String spec : sSelectedTileSpecs) {
                                sExpandedTileSpecs.remove(spec);
                            }
                            sTileGroups.add(new TileGroup(groupId, new ArrayList<>(sSelectedTileSpecs), false));
                            sIsGroupSelectMode = false;
                            sSelectedTileSpecs.clear();
                            finalGroupBtn.setImageDrawable(new GroupGridDrawable(false));
                            savePriorities(ctx);
                        } else {
                            sIsGroupSelectMode = false;
                            sSelectedTileSpecs.clear();
                            finalGroupBtn.setImageDrawable(new GroupGridDrawable(false));
                        }
                    }
                    refreshAdapter(root, ctx);
                } catch (Throwable t) {
                    tripSafeMode(ctx, t);
                }
            });

            disassembleButton.setOnClickListener(v -> {
                try {
                    if (!sIsDisassembleMode) {
                        sIsDisassembleMode = true;
                        sIsGroupSelectMode = false;
                        sSelectedTileSpecs.clear();
                        sSelectedDisassembleGroups.clear();
                        finalDisassembleBtn.setImageDrawable(new UngroupHeaderDrawable(true));
                        if (finalGroupBtn != null) finalGroupBtn.setImageDrawable(new GroupGridDrawable(false));
                    } else {
                        if (!sSelectedDisassembleGroups.isEmpty()) {
                            java.util.Iterator<TileGroup> it = sTileGroups.iterator();
                            while (it.hasNext()) {
                                TileGroup g = it.next();
                                if (sSelectedDisassembleGroups.contains(g.id)) {
                                    it.remove();
                                }
                            }
                            sSelectedDisassembleGroups.clear();
                            sIsDisassembleMode = false;
                            finalDisassembleBtn.setImageDrawable(new UngroupHeaderDrawable(false));
                            savePriorities(ctx);
                        } else {
                            sIsDisassembleMode = false;
                            finalDisassembleBtn.setImageDrawable(new UngroupHeaderDrawable(false));
                        }
                    }
                    refreshAdapter(root, ctx);
                } catch (Throwable t) {
                    tripSafeMode(ctx, t);
                }
            });
        } catch (Throwable t) {
            android.util.Log.e(TAG, "ensureGroupButtonInjected error", t);
        }
    }

    private static String getRecordSpec(Object r) {
        if (r == null) return null;
        try {
            Method m = r.getClass().getMethod("getSpec");
            return (String) m.invoke(r);
        } catch (Throwable ignore) {
            try {
                Field f = r.getClass().getDeclaredField("spec");
                f.setAccessible(true);
                return (String) f.get(r);
            } catch (Throwable ignore2) { return null; }
        }
    }

    public static TileGroup isGroupAnchor(String spec) {
        if (spec == null) return null;
        ensurePrioritiesLoaded();
        for (TileGroup g : sTileGroups) {
            if ((g.specs.size() == 4 || g.specs.size() == 2) && spec.equals(g.specs.get(0))) {
                return g;
            }
        }
        return null;
    }

    public static boolean isGroupChild(String spec) {
        if (spec == null) return false;
        ensurePrioritiesLoaded();
        for (TileGroup g : sTileGroups) {
            if (g.specs.size() == 4 || g.specs.size() == 2) {
                for (int i = 1; i < g.specs.size(); i++) {
                    if (spec.equals(g.specs.get(i))) return true;
                }
            }
        }
        return false;
    }

    private static void ensurePrioritiesLoaded() {
        if (sPrefs == null && sQSListController != null) {
            try {
                Field sysUIContextField = sQSListController.getClass().getDeclaredField("sysUIContext");
                sysUIContextField.setAccessible(true);
                Context ctx = (Context) sysUIContextField.get(sQSListController);
                if (ctx != null) loadSavedPriorities(ctx);
            } catch (Throwable ignore) {}
        }
    }

    public static TileGroup getTileGroupBySpec(String spec) {
        if (spec == null) return null;
        for (TileGroup g : sTileGroups) {
            if (g.specs.contains(spec)) return g;
        }
        return null;
    }

    private static void ungroupTileGroup(Context ctx, TileGroup group, View view) {
        if (group == null) return;
        if (sQSListController != null) {
            try {
                Method getAddedMethod = sQSListController.getClass().getMethod("getAddedTiles");
                List addedList = (List) getAddedMethod.invoke(sQSListController);
                if (addedList != null) {
                    for (Object r : addedList) {
                        if (r != null) {
                            try {
                                Field sF = r.getClass().getDeclaredField("spec");
                                sF.setAccessible(true);
                                String s = (String) sF.get(r);
                                if (group.specs.contains(s)) {
                                    try {
                                        Method remCb = r.getClass().getMethod("removeCallback");
                                        remCb.invoke(r);
                                    } catch (Throwable ignore) {}
                                    try {
                                        Method setLis = r.getClass().getMethod("setListening", boolean.class);
                                        setLis.invoke(r, false);
                                    } catch (Throwable ignore) {}
                                }
                            } catch (Throwable ignore) {}
                        }
                    }
                }
            } catch (Throwable ignore) {}
        }
        sTileGroups.remove(group);
        savePriorities(ctx);

        View parent = view;
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
                    Object adapter = getAdapterMethod.invoke(rv);
                    if (adapter != null) {
                        Method notifyChanged = adapter.getClass().getMethod("notifyDataSetChanged");
                        notifyChanged.invoke(adapter);
                    }
                } catch (Throwable ignore) {}
            });
        }
    }

    private static void applyGroupContainerStyle(View view, ClassLoader classLoader) {
        if (view == null) return;
        try {
            Context ctx = view.getContext();
            int radiusResId = ctx.getResources().getIdentifier("control_center_universal_corner_radius", "dimen", ctx.getPackageName());
            final float cornerRadius = radiusResId != 0 ? ctx.getResources().getDimension(radiusResId) : 20f * ctx.getResources().getDisplayMetrics().density;

            // Check if Background Blur / Advanced Textures is enabled in system
            boolean blurEnabled = false;
            try {
                Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                blurEnabled = (boolean) getBlurM.invoke(null, ctx);
            } catch (Throwable ignore) {}

            // Set _cornerRadius field on QSCardItemView
            try {
                Field crField = view.getClass().getDeclaredField("_cornerRadius");
                crField.setAccessible(true);
                crField.setFloat(view, cornerRadius);
            } catch (Throwable ignore) {}

            // Set OutlineProvider with universal corner radius
            view.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerRadius);
                }
            });
            view.setClipToOutline(true);
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipChildren(true);
            }
            view.invalidateOutline();

            if (blurEnabled) {
                // Advanced Textures ON: Native Hardware Blur & Blend Colors
                view.setBackground(null);
                try {
                    Class<?> miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader);
                    try {
                        Method setPassWindowBlur = miBlurClass.getMethod("setPassWindowBlurEnabledCompat", View.class, boolean.class);
                        setPassWindowBlur.invoke(null, view, true);
                    } catch (Throwable ignore) {}

                    Method setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View.class, int.class);
                    setModeMethod.invoke(null, view, 1);

                    Class<?> tokenClass = Class.forName("miui.systemui.util.MiuiColorBlendToken", false, classLoader);
                    Object tokenInst = tokenClass.getDeclaredField("INSTANCE").get(null);
                    Method getBlendMethod = tokenClass.getMethod("getCC_TILE_DEFAULT_BLEND_COLORS");
                    Object blendColors = getBlendMethod.invoke(tokenInst);

                    Method setBlendMethod = miBlurClass.getMethod("setMiBackgroundBlendColors", View.class, blendColors.getClass());
                    setBlendMethod.invoke(null, view, blendColors);
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "applyGroupContainerStyle MiBlur error", t);
                }
            } else {
                // Advanced Textures OFF: Clear Blur & Apply Stock Card Drawable
                try {
                    Class<?> miBlurClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader);
                    Method setModeMethod = miBlurClass.getMethod("setMiViewBlurModeCompat", View.class, int.class);
                    setModeMethod.invoke(null, view, 0);

                    Method clearBlendMethod = miBlurClass.getMethod("clearMiBackgroundBlendColorCompat", View.class);
                    clearBlendMethod.invoke(null, view);
                } catch (Throwable ignore) {}

                try {
                    Class<?> commonUtilsClass = Class.forName("miui.systemui.util.CommonUtils", false, classLoader);
                    Object commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null);
                    Method clearEffect = commonUtilsClass.getMethod("clearMiBlurBlendEffect", View.class);
                    clearEffect.invoke(commonUtils, view);
                } catch (Throwable ignore) {}

                // Load stock card background disabled drawable
                Drawable cardBg = null;
                int bgResId = ctx.getResources().getIdentifier("qs_card_background_disabled", "drawable", ctx.getPackageName());
                if (bgResId == 0) {
                    bgResId = ctx.getResources().getIdentifier("qs_background_disabled", "drawable", ctx.getPackageName());
                }
                if (bgResId != 0) {
                    try {
                        cardBg = ctx.getTheme().getDrawable(bgResId).mutate();
                    } catch (Throwable ignore) {}
                }
                if (cardBg != null) {
                    view.setBackground(cardBg);
                } else {
                    android.graphics.drawable.GradientDrawable fallbackBg = new android.graphics.drawable.GradientDrawable();
                    fallbackBg.setCornerRadius(cornerRadius);
                    fallbackBg.setColor(Color.parseColor("#26808080"));
                    view.setBackground(fallbackBg);
                }
            }
        } catch (Throwable t) {
            android.util.Log.e(TAG, "applyGroupContainerStyle error", t);
        }
    }

    private static Object createGrouped2TileViewHolder(ViewGroup parent, Object qsListCtrl, ClassLoader classLoader, boolean isVertical) {
        if (parent == null || qsListCtrl == null || classLoader == null) return null;
        try {
            Context ctx = parent.getContext();
            float density = ctx.getResources().getDisplayMetrics().density;

            Field sysUIContextField = qsListCtrl.getClass().getDeclaredField("sysUIContext");
            sysUIContextField.setAccessible(true);
            Context sysUIContext = (Context) sysUIContextField.get(qsListCtrl);

            // Inflate genuine QSCardItemView as root
            Class<?> cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader);
            Method inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class);
            Object binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false);

            Method getRootMethod = cardBindingClass.getMethod("getRoot");
            ViewGroup root = (ViewGroup) getRootMethod.invoke(binding);

            // Initialize QSCardItemView with QSCardItemIconView
            try {
                Class<?> cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader);
                Constructor<?> cardIconCtor = null;
                for (Constructor<?> c : cardIconViewClass.getDeclaredConstructors()) {
                    if (c.getParameterTypes().length == 5) {
                        cardIconCtor = c;
                        break;
                    }
                }
                if (cardIconCtor == null) {
                    cardIconCtor = cardIconViewClass.getDeclaredConstructors()[0];
                }
                cardIconCtor.setAccessible(true);
                Object cardIconView = (cardIconCtor.getParameterTypes().length == 5)
                    ? cardIconCtor.newInstance(root.getContext(), sysUIContext, null, 4, null)
                    : cardIconCtor.newInstance(root.getContext(), sysUIContext);

                Method initCardMethod = root.getClass().getMethod("init", cardIconViewClass);
                initCardMethod.invoke(root, cardIconView);
            } catch (Throwable t) {
                android.util.Log.e(TAG, "QSCardItemView init error", t);
            }

            root.removeAllViews();

            int resHeightId = ctx.getResources().getIdentifier("control_center_universal_1_row_with_margin_size", "dimen", ctx.getPackageName());
            int unitH = (resHeightId != 0) ? ctx.getResources().getDimensionPixelSize(resHeightId) : (int) (76 * density);
            int resMarginId = ctx.getResources().getIdentifier("control_center_universal_margin", "dimen", ctx.getPackageName());
            int gap = (resMarginId != 0) ? ctx.getResources().getDimensionPixelSize(resMarginId) : (int) (10 * density);
            int tallH = unitH * 2 + gap;

            int cardH = isVertical ? tallH : unitH;
            ViewGroup.LayoutParams rlp = root.getLayoutParams();
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rlp.height = cardH;
                root.setLayoutParams(rlp);
            } else {
                root.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH));
            }

            Object mainPanelController = null;
            try {
                Field mainPanelControllerField = qsListCtrl.getClass().getDeclaredField("mainPanelController");
                mainPanelControllerField.setAccessible(true);
                Object mainPanelCtrlLazy = mainPanelControllerField.get(qsListCtrl);
                Method getMethod = mainPanelCtrlLazy.getClass().getMethod("get");
                mainPanelController = getMethod.invoke(mainPanelCtrlLazy);
            } catch (Throwable ignore) {}

            boolean blurEnabled = false;
            try {
                Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                blurEnabled = (boolean) getBlurM.invoke(null, ctx);
            } catch (Throwable ignore) {}

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(ctx);
            wrapper.setId(0x7f099996);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            wrapper.setClipChildren(false);
            wrapper.setClipToPadding(false);

            android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
            container.setId(0x7f099995);
            container.setOrientation(isVertical ? android.widget.LinearLayout.VERTICAL : android.widget.LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            if (isVertical) {
                container.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            } else {
                int pad = (int) (4 * density);
                container.setPadding(pad, pad, pad, pad);
            }
            wrapper.addView(container, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Injected Rotate Button for 2-tile containers in Edit Mode
            ImageView rotateBtn = new ImageView(ctx);
            rotateBtn.setId(0x7f099992);
            rotateBtn.setImageDrawable(new RotateIconDrawable());
            int rSize = (int) (22 * density);
            android.widget.FrameLayout.LayoutParams rlpBtn = new android.widget.FrameLayout.LayoutParams(rSize, rSize);
            rlpBtn.gravity = Gravity.BOTTOM | Gravity.END;
            int rMargin = (int) (3 * density);
            rlpBtn.setMargins(0, 0, rMargin, rMargin);
            rotateBtn.setLayoutParams(rlpBtn);
            rotateBtn.setVisibility(View.GONE);
            wrapper.addView(rotateBtn);

            // Injected Disassemble Selection Indicator
            ImageView disassembleIndicator = new ImageView(ctx);
            disassembleIndicator.setId(0x7f099998);
            int dSize = (int) (26 * density);
            android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(dSize, dSize);
            dlp.gravity = Gravity.TOP | Gravity.START;
            int dMargin = (int) (6 * density);
            dlp.setMargins(dMargin, dMargin, 0, 0);
            disassembleIndicator.setLayoutParams(dlp);
            disassembleIndicator.setImageDrawable(new SelectCircleDrawable());
            disassembleIndicator.setVisibility(View.GONE);
            wrapper.addView(disassembleIndicator);

            root.addView(wrapper);

            // Inflate 2 circular child tiles
            int tileLayoutId = ctx.getResources().getIdentifier("qs_tile_item_view", "layout", ctx.getPackageName());
            Class<?> tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader);
            Constructor<?> iconCtor = null;
            for (Constructor<?> c : tileIconViewClass.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 5) {
                    iconCtor = c;
                    break;
                }
            }
            if (iconCtor == null) iconCtor = tileIconViewClass.getDeclaredConstructors()[0];
            iconCtor.setAccessible(true);

            container.setClickable(false);
            container.setFocusable(false);
            root.setClickable(false);
            root.setFocusable(false);

            Object[] subHolders = new Object[2];
            Class<?> subHolderClass = null;
            Constructor<?> subHolderCtor = null;
            try {
                subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader);
                for (Constructor<?> c : subHolderClass.getDeclaredConstructors()) {
                    if (c.getParameterTypes().length == 2) {
                        subHolderCtor = c;
                        break;
                    }
                }
                if (subHolderCtor == null && subHolderClass.getDeclaredConstructors().length > 0) {
                    subHolderCtor = subHolderClass.getDeclaredConstructors()[0];
                }
                if (subHolderCtor != null) subHolderCtor.setAccessible(true);
            } catch (Throwable ignore) {}

            for (int i = 0; i < 2; i++) {
                View childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, container, false);
                Object iconView = (iconCtor.getParameterTypes().length == 5)
                    ? iconCtor.newInstance(ctx, sysUIContext, false, 4, null)
                    : iconCtor.newInstance(ctx, sysUIContext, false);

                Method initMethod = childTile.getClass().getMethod("init", tileIconViewClass);
                initMethod.invoke(childTile, iconView);

                if (isVertical && iconView instanceof View) {
                    ((View) iconView).setScaleX(0.92f);
                    ((View) iconView).setScaleY(0.92f);
                }

                int iconFrameId = ctx.getResources().getIdentifier("icon_frame", "id", ctx.getPackageName());
                View iconFrame = childTile.findViewById(iconFrameId);
                if (iconFrame != null) {
                    ViewGroup.LayoutParams iflp = iconFrame.getLayoutParams();
                    if (iflp instanceof android.widget.FrameLayout.LayoutParams) {
                        android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) iflp;
                        flp.gravity = Gravity.CENTER;
                        flp.topMargin = 0;
                        flp.bottomMargin = 0;
                        iconFrame.setLayoutParams(flp);
                    }
                    if (blurEnabled) {
                        android.graphics.drawable.GradientDrawable discBg = new android.graphics.drawable.GradientDrawable();
                        discBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        discBg.setColor(Color.parseColor("#38FFFFFF"));
                        discBg.setStroke((int) Math.max(1, 1.5f * density), Color.parseColor("#80FFFFFF"));
                        iconFrame.setBackground(discBg);
                    } else {
                        iconFrame.setBackground(null);
                    }
                }

                if (mainPanelController != null) {
                    try {
                        Method setMainCtrlMethod = childTile.getClass().getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader));
                        setMainCtrlMethod.invoke(childTile, mainPanelController);
                    } catch (Throwable ignore) {}
                }

                if (subHolderCtor != null) {
                    try {
                        if (subHolderCtor.getParameterTypes().length == 2) {
                            subHolders[i] = subHolderCtor.newInstance(childTile, mainPanelController);
                        } else {
                            subHolders[i] = subHolderCtor.newInstance(childTile);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Failed creating QSItemViewHolder for subTile " + i, t);
                    }
                }

                if (subHolders[i] != null) {
                    Class<?> sc = subHolders[i].getClass();
                    while (sc != null) {
                        try {
                            Field af = sc.getDeclaredField("attached");
                            af.setAccessible(true);
                            af.setBoolean(subHolders[i], true);
                        } catch (Throwable ignore) {}
                        try {
                            Field mpaField = sc.getDeclaredField("mainPanelAdapter");
                            mpaField.setAccessible(true);
                            if (sMainPanelAdapter != null) {
                                mpaField.set(subHolders[i], sMainPanelAdapter);
                            }
                        } catch (Throwable ignore) {}
                        try {
                            Field fcField = sc.getDeclaredField("frameCallback");
                            fcField.setAccessible(true);
                            if (sFrameCallback != null) {
                                fcField.set(subHolders[i], sFrameCallback);
                            }
                        } catch (Throwable ignore) {}
                        sc = sc.getSuperclass();
                    }
                }

                android.widget.LinearLayout.LayoutParams tlp = isVertical
                    ? new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
                    : new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                tlp.gravity = Gravity.CENTER;
                container.addView(childTile, tlp);
            }

            root.setTag(0x7f099993, subHolders);

            applyGroupContainerStyle(root, classLoader);
            root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                applyGroupContainerStyle(v, classLoader);
            });

            Class<?> cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader);
            Class<?> qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader);
            Constructor<?> holderCtor = cardHolderClass.getConstructor(qsItemViewClass);
            return holderCtor.newInstance(root);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "createGrouped2TileViewHolder error", t);
            return null;
        }
    }

    private static Object createGroupedCardViewHolder(ViewGroup parent, Object qsListCtrl, ClassLoader classLoader) {
        if (parent == null || qsListCtrl == null || classLoader == null) return null;
        try {
            Context ctx = parent.getContext();
            float density = ctx.getResources().getDisplayMetrics().density;

            Field sysUIContextField = qsListCtrl.getClass().getDeclaredField("sysUIContext");
            sysUIContextField.setAccessible(true);
            Context sysUIContext = (Context) sysUIContextField.get(qsListCtrl);

            // Inflate genuine QSCardItemView as root
            Class<?> cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader);
            Method inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class);
            Object binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false);

            Method getRootMethod = cardBindingClass.getMethod("getRoot");
            ViewGroup root = (ViewGroup) getRootMethod.invoke(binding);

            // Initialize QSCardItemView with QSCardItemIconView so lateinit property icon is properly set!
            try {
                Class<?> cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader);
                Constructor<?> cardIconCtor = null;
                for (Constructor<?> c : cardIconViewClass.getDeclaredConstructors()) {
                    if (c.getParameterTypes().length == 5) {
                        cardIconCtor = c;
                        break;
                    }
                }
                if (cardIconCtor == null) {
                    cardIconCtor = cardIconViewClass.getDeclaredConstructors()[0];
                }
                cardIconCtor.setAccessible(true);
                Object cardIconView = (cardIconCtor.getParameterTypes().length == 5)
                    ? cardIconCtor.newInstance(root.getContext(), sysUIContext, null, 4, null)
                    : cardIconCtor.newInstance(root.getContext(), sysUIContext);

                Method initCardMethod = root.getClass().getMethod("init", cardIconViewClass);
                initCardMethod.invoke(root, cardIconView);
            } catch (Throwable t) {
                android.util.Log.e(TAG, "QSCardItemView init error", t);
            }

            // Remove default card children (title, subtext, chevron, switch)
            root.removeAllViews();

            int cardH = (int) (162 * density);
            ViewGroup.LayoutParams rlp = root.getLayoutParams();
            if (rlp != null) {
                rlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rlp.height = cardH;
                root.setLayoutParams(rlp);
            } else {
                root.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardH));
            }

            Object mainPanelController = null;
            try {
                Field mainPanelControllerField = qsListCtrl.getClass().getDeclaredField("mainPanelController");
                mainPanelControllerField.setAccessible(true);
                Object mainPanelCtrlLazy = mainPanelControllerField.get(qsListCtrl);
                Method getMethod = mainPanelCtrlLazy.getClass().getMethod("get");
                mainPanelController = getMethod.invoke(mainPanelCtrlLazy);
            } catch (Throwable ignore) {}

            // Check if Background Blur / Advanced Textures is enabled
            boolean blurEnabled = false;
            try {
                Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                blurEnabled = (boolean) getBlurM.invoke(null, ctx);
            } catch (Throwable ignore) {}

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(ctx);
            wrapper.setId(0x7f099996);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Add 2-Row Vertical Container inside wrapper
            android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
            container.setId(0x7f099995);
            container.setOrientation(android.widget.LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER);
            android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            int pad = (int) (6 * density);
            container.setPadding(pad, pad, pad, pad);
            wrapper.addView(container, clp);

            // Injected Disassemble Selection Indicator
            ImageView disassembleIndicator = new ImageView(ctx);
            disassembleIndicator.setId(0x7f099998);
            int dSize = (int) (26 * density);
            android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(dSize, dSize);
            dlp.gravity = Gravity.TOP | Gravity.START;
            int dMargin = (int) (6 * density);
            dlp.setMargins(dMargin, dMargin, 0, 0);
            disassembleIndicator.setLayoutParams(dlp);
            disassembleIndicator.setImageDrawable(new SelectCircleDrawable());
            disassembleIndicator.setVisibility(View.GONE);
            wrapper.addView(disassembleIndicator);

            root.addView(wrapper);

            android.widget.LinearLayout rowTop = new android.widget.LinearLayout(ctx);
            rowTop.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowTop.setGravity(Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams rlp1 = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            container.addView(rowTop, rlp1);

            android.widget.LinearLayout rowBottom = new android.widget.LinearLayout(ctx);
            rowBottom.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowBottom.setGravity(Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams rlp2 = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            container.addView(rowBottom, rlp2);

            // Inflate 4 circular child tiles
            int tileLayoutId = ctx.getResources().getIdentifier("qs_tile_item_view", "layout", ctx.getPackageName());
            Class<?> tileIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", false, classLoader);
            Constructor<?> iconCtor = null;
            for (Constructor<?> c : tileIconViewClass.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 5) {
                    iconCtor = c;
                    break;
                }
            }
            if (iconCtor == null) iconCtor = tileIconViewClass.getDeclaredConstructors()[0];
            iconCtor.setAccessible(true);

            container.setClickable(false);
            container.setFocusable(false);
            root.setClickable(false);
            root.setFocusable(false);

            Object[] subHolders = new Object[4];
            Class<?> subHolderClass = null;
            Constructor<?> subHolderCtor = null;
            try {
                subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader);
                for (Constructor<?> c : subHolderClass.getDeclaredConstructors()) {
                    if (c.getParameterTypes().length == 2) {
                        subHolderCtor = c;
                        break;
                    }
                }
                if (subHolderCtor == null && subHolderClass.getDeclaredConstructors().length > 0) {
                    subHolderCtor = subHolderClass.getDeclaredConstructors()[0];
                }
                if (subHolderCtor != null) subHolderCtor.setAccessible(true);
            } catch (Throwable ignore) {}

            for (int i = 0; i < 4; i++) {
                ViewGroup targetRow = (i < 2) ? rowTop : rowBottom;
                View childTile = LayoutInflater.from(ctx).inflate(tileLayoutId, targetRow, false);
                Object iconView = (iconCtor.getParameterTypes().length == 5)
                    ? iconCtor.newInstance(ctx, sysUIContext, false, 4, null)
                    : iconCtor.newInstance(ctx, sysUIContext, false);

                Method initMethod = childTile.getClass().getMethod("init", tileIconViewClass);
                initMethod.invoke(childTile, iconView);

                // Center icon_frame within childTile to ensure equal top/bottom and row spacing
                int iconFrameId = ctx.getResources().getIdentifier("icon_frame", "id", ctx.getPackageName());
                View iconFrame = childTile.findViewById(iconFrameId);
                if (iconFrame != null) {
                    ViewGroup.LayoutParams iflp = iconFrame.getLayoutParams();
                    if (iflp instanceof android.widget.FrameLayout.LayoutParams) {
                        android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) iflp;
                        flp.gravity = Gravity.CENTER;
                        flp.topMargin = 0;
                        flp.bottomMargin = 0;
                        iconFrame.setLayoutParams(flp);
                    }
                    if (blurEnabled) {
                        android.graphics.drawable.GradientDrawable discBg = new android.graphics.drawable.GradientDrawable();
                        discBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        discBg.setColor(Color.parseColor("#38FFFFFF")); // 22% white translucent plate
                        discBg.setStroke((int) Math.max(1, 1.5f * density), Color.parseColor("#80FFFFFF")); // 50% crisp white outline
                        iconFrame.setBackground(discBg);
                    } else {
                        iconFrame.setBackground(null);
                    }
                }

                if (mainPanelController != null) {
                    try {
                        Method setMainCtrlMethod = childTile.getClass().getMethod("setMainPanelController", Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController", false, classLoader));
                        setMainCtrlMethod.invoke(childTile, mainPanelController);
                    } catch (Throwable ignore) {}
                }

                if (subHolderCtor != null) {
                    try {
                        if (subHolderCtor.getParameterTypes().length == 2) {
                            subHolders[i] = subHolderCtor.newInstance(childTile, mainPanelController);
                        } else {
                            subHolders[i] = subHolderCtor.newInstance(childTile);
                        }
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Failed creating QSItemViewHolder for subTile " + i, t);
                    }
                }

                // Initialize mainPanelAdapter & frameCallback on subHolder to prevent lateinit crash
                if (subHolders[i] != null) {
                    Class<?> sc = subHolders[i].getClass();
                    while (sc != null) {
                        try {
                            Field af = sc.getDeclaredField("attached");
                            af.setAccessible(true);
                            af.setBoolean(subHolders[i], true);
                        } catch (Throwable ignore) {}
                        try {
                            Field mpaField = sc.getDeclaredField("mainPanelAdapter");
                            mpaField.setAccessible(true);
                            if (sMainPanelAdapter != null) {
                                mpaField.set(subHolders[i], sMainPanelAdapter);
                            }
                        } catch (Throwable ignore) {}
                        try {
                            Field fcField = sc.getDeclaredField("frameCallback");
                            fcField.setAccessible(true);
                            if (sFrameCallback != null) {
                                fcField.set(subHolders[i], sFrameCallback);
                            }
                        } catch (Throwable ignore) {}
                        sc = sc.getSuperclass();
                    }
                }

                android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                tlp.gravity = Gravity.CENTER;
                targetRow.addView(childTile, tlp);
            }

            root.setTag(0x7f099993, subHolders);

            // Apply Xiaomi Blur & Rounded Corners
            applyGroupContainerStyle(root, classLoader);
            root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                applyGroupContainerStyle(v, classLoader);
            });

            // Construct genuine QSCardViewHolder
            Class<?> cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader);
            Class<?> qsItemViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSItemView", false, classLoader);
            Constructor<?> holderCtor = cardHolderClass.getConstructor(qsItemViewClass);
            return holderCtor.newInstance(root);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "createGroupedCardViewHolder error", t);
        }
        return null;
    }

    private static View getViewHolderItemView(Object holder) {
        if (holder == null) return null;
        Class<?> clazz = holder.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField("itemView");
                f.setAccessible(true);
                return (View) f.get(holder);
            } catch (Throwable ignore) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static void updateChildHolderFrame(Object parentHolder, Object childHolder) {
        if (parentHolder == null || childHolder == null) return;
        try {
            Class<?> parentClass = parentHolder.getClass();
            Class<?> childClass = childHolder.getClass();

            try {
                Method getScale = parentClass.getMethod("getHolderScale");
                Method setScale = childClass.getMethod("setHolderScale", float.class);
                setScale.invoke(childHolder, getScale.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getAlpha = parentClass.getMethod("getHolderAlpha");
                Method setAlpha = childClass.getMethod("setHolderAlpha", float.class);
                setAlpha.invoke(childHolder, getAlpha.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getTransX = parentClass.getMethod("getHolderTransX");
                Method setTransX = childClass.getMethod("setHolderTransX", float.class);
                setTransX.invoke(childHolder, getTransX.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getTransY = parentClass.getMethod("getHolderTransY");
                Method setTransY = childClass.getMethod("setHolderTransY", float.class);
                setTransY.invoke(childHolder, getTransY.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getExpandAlpha = parentClass.getMethod("getExpandAlpha");
                Method setExpandAlpha = childClass.getMethod("setExpandAlpha", float.class);
                setExpandAlpha.invoke(childHolder, getExpandAlpha.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getExpandScaleRatio = parentClass.getMethod("getExpandScaleRatio");
                Method setExpandScaleRatio = childClass.getMethod("setExpandScaleRatio", float.class);
                setExpandScaleRatio.invoke(childHolder, getExpandScaleRatio.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getSpreadScale = parentClass.getMethod("getSpreadScale");
                Method setSpreadScale = childClass.getMethod("setSpreadScale", float.class);
                setSpreadScale.invoke(childHolder, getSpreadScale.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getShrinkX = parentClass.getMethod("getShrinkX");
                Method setShrinkX = childClass.getMethod("setShrinkX", float.class);
                setShrinkX.invoke(childHolder, getShrinkX.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getShrinkY = parentClass.getMethod("getShrinkY");
                Method setShrinkY = childClass.getMethod("setShrinkY", float.class);
                setShrinkY.invoke(childHolder, getShrinkY.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getCenterX = parentClass.getMethod("getCenterX$miui_controlcenter_release");
                Method setCenterX = childClass.getMethod("setCenterX$miui_controlcenter_release", float.class);
                setCenterX.invoke(childHolder, getCenterX.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getCenterY = parentClass.getMethod("getCenterY$miui_controlcenter_release");
                Method setCenterY = childClass.getMethod("setCenterY$miui_controlcenter_release", float.class);
                setCenterY.invoke(childHolder, getCenterY.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getShrinkCenterY = parentClass.getMethod("getShrinkCenterY$miui_controlcenter_release");
                Method setShrinkCenterY = childClass.getMethod("setShrinkCenterY$miui_controlcenter_release", float.class);
                setShrinkCenterY.invoke(childHolder, getShrinkCenterY.invoke(parentHolder));
            } catch (Throwable ignore) {}

            try {
                Method getShrinkExpandTransY = parentClass.getMethod("getShrinkExpandTransY");
                Method setShrinkExpandTransY = childClass.getMethod("setShrinkExpandTransY", float.class);
                setShrinkExpandTransY.invoke(childHolder, getShrinkExpandTransY.invoke(parentHolder));
            } catch (Throwable ignore) {}

            Method onFrame = childClass.getMethod("onFrameCallback");
            onFrame.invoke(childHolder);
        } catch (Throwable ignore) {}
    }

    private static View getChildTileAt(ViewGroup container, int index) {
        if (container == null) return null;
        if (container.getChildCount() > 0 && container.getChildAt(0) instanceof android.widget.LinearLayout && ((android.widget.LinearLayout) container.getChildAt(0)).getChildCount() > 0 && !(container.getChildAt(0).getClass().getName().contains("QSTileItemView"))) {
            int rowIndex = index / 2;
            int colIndex = index % 2;
            if (rowIndex < container.getChildCount()) {
                View row = container.getChildAt(rowIndex);
                if (row instanceof ViewGroup && colIndex < ((ViewGroup) row).getChildCount()) {
                    return ((ViewGroup) row).getChildAt(colIndex);
                }
            }
            return null;
        } else {
            if (index < container.getChildCount()) {
                return container.getChildAt(index);
            }
            return null;
        }
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

            // 7. Hook MainPanelItemViewHolder (Center-Bounce, Spring Easing & iOS 26 Jelly & Mode Transitions)
            try {
                Class<?> holderClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader);
                for (Method m : holderClass.getDeclaredMethods()) {
                    if (m.getName().equals("updateMode")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object holder = chain.getThisObject();
                                View itemView = getViewHolderItemView(holder);
                                if (itemView != null) {
                                    View rotateBtn = itemView.findViewById(0x7f099992);
                                    if (rotateBtn != null) {
                                        Object rawArgs = chain.getArgs();
                                        Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                        boolean isEdit = false;
                                        if (args != null) {
                                            for (Object arg : args) {
                                                if (arg != null && "EDIT".equals(arg.toString())) {
                                                    isEdit = true;
                                                    break;
                                                }
                                            }
                                        }
                                        rotateBtn.setVisibility(isEdit ? View.VISIBLE : View.GONE);
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                }
                Class<?> easeManagerClass = Class.forName("miuix.animation.utils.EaseManager", false, classLoader);
                Method getStyleMethod = easeManagerClass.getMethod("getStyle", int.class, float[].class);

                for (Method m : holderClass.getDeclaredMethods()) {
                    if (m.getName().equals("prepareLocation")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = null;
                            try {
                                result = chain.proceed();
                            } catch (Throwable t) {
                                return null;
                            }
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
                            Object result = null;
                            try {
                                result = chain.proceed();
                            } catch (Throwable ignore) {
                                return null;
                            }
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
                                sQSListController = chain.getThisObject();
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
                                            String sp = null;
                                            try {
                                                Method getSpecMethod = rec.getClass().getMethod("getSpec");
                                                sp = (String) getSpecMethod.invoke(rec);
                                            } catch (Throwable ignore) {
                                                try {
                                                    Field specF = rec.getClass().getDeclaredField("spec");
                                                    specF.setAccessible(true);
                                                    sp = (String) specF.get(rec);
                                                } catch (Throwable ignore2) {}
                                            }
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

                    if (m.getName().equals("getListItems")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            sQSListController = chain.getThisObject();
                            List originalList = (List) chain.proceed();
                            if (originalList != null) {
                                sAllQSRecords.clear();
                                sAllQSRecords.addAll(originalList);
                            }
                            if (originalList == null || sTileGroups.isEmpty()) return originalList;
                            try {
                                ArrayList filtered = new ArrayList();
                                for (Object item : originalList) {
                                    if (item != null) {
                                        String spec = null;
                                        try {
                                            Method getSpecMethod = item.getClass().getMethod("getSpec");
                                            spec = (String) getSpecMethod.invoke(item);
                                        } catch (Throwable ignore) {
                                            try {
                                                Field specField = item.getClass().getDeclaredField("spec");
                                                specField.setAccessible(true);
                                                spec = (String) specField.get(item);
                                            } catch (Throwable ignore2) {}
                                        }

                                        if (spec != null && isGroupChild(spec)) {
                                            continue;
                                        }
                                    }
                                    filtered.add(item);
                                }
                                return filtered;
                            } catch (Throwable t) {
                                android.util.Log.e(TAG, "QSListController getListItems filter error", t);
                            }
                            return originalList;
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
                                    if (args != null && args.length >= 2) {
                                        int viewType = ((Integer) args[1]);
                                        ViewGroup parent = (ViewGroup) args[0];
                                        Object qsListCtrl = chain.getThisObject();
                                        Context ctx = parent.getContext();
                                        float density = ctx.getResources().getDisplayMetrics().density;

                                        Field sysUIContextField = qsListCtrl.getClass().getDeclaredField("sysUIContext");
                                        sysUIContextField.setAccessible(true);
                                        Context sysUIContext = (Context) sysUIContextField.get(qsListCtrl);

                                        if (viewType == 2274) {
                                            Object holder = createGroupedCardViewHolder(parent, qsListCtrl, classLoader);
                                            if (holder != null) return holder;
                                        }
                                        if (viewType == 2275) {
                                            Object holder = createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, false);
                                            if (holder != null) return holder;
                                        }
                                        if (viewType == 2276) {
                                            Object holder = createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, true);
                                            if (holder != null) return holder;
                                        }

                                        if (viewType == 2273) {
                                            Class<?> cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader);
                                            Method inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class);
                                            Object binding = inflateMethod.invoke(null, LayoutInflater.from(parent.getContext()), parent, false);

                                            Method getRootMethod = cardBindingClass.getMethod("getRoot");
                                            View root = (View) getRootMethod.invoke(binding);

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
                                    }
                                } catch (Throwable t) {
                                    android.util.Log.e(TAG, "QSListController.createViewHolder inflation error", t);
                                }
                            }
                            return result;
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 10b. Hook MainPanelAdapter.onCreateViewHolder to catch 2274 and return GroupedCardViewHolder
            try {
                Class<?> mainAdapterClass = Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", false, classLoader);
                for (Method m : mainAdapterClass.getDeclaredMethods()) {
                    if (m.getName().equals("onCreateViewHolder") && m.getParameterTypes().length == 2 && m.getParameterTypes()[1] == int.class) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object rawArgs = chain.getArgs();
                            Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                            if (args != null && args.length >= 2) {
                                int vt = (Integer) args[1];
                                if (vt == 2274 || vt == 2275 || vt == 2276 || vt == 2273) {
                                    try {
                                        Object adapterObj = chain.getThisObject();
                                        sMainPanelAdapter = adapterObj;
                                        Object qsListCtrl = null;
                                        try {
                                            Field qsListField = mainAdapterClass.getDeclaredField("qsListController");
                                            qsListField.setAccessible(true);
                                            qsListCtrl = qsListField.get(adapterObj);
                                        } catch (Throwable ignore) {}
                                        if (qsListCtrl == null) qsListCtrl = sQSListController;

                                        ViewGroup parent = (ViewGroup) args[0];
                                        Context ctx = parent.getContext();
                                        Context sysUIContext = ctx;
                                        if (qsListCtrl != null) {
                                            try {
                                                Field sysUIContextField = qsListCtrl.getClass().getDeclaredField("sysUIContext");
                                                sysUIContextField.setAccessible(true);
                                                Context sc = (Context) sysUIContextField.get(qsListCtrl);
                                                if (sc != null) sysUIContext = sc;
                                            } catch (Throwable ignore) {}
                                        }

                                        if (vt == 2274) {
                                            Object holder = createGroupedCardViewHolder(parent, qsListCtrl, classLoader);
                                            if (holder != null) return holder;
                                        } else if (vt == 2275) {
                                            Object holder = createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, false);
                                            if (holder != null) return holder;
                                        } else if (vt == 2276) {
                                            Object holder = createGrouped2TileViewHolder(parent, qsListCtrl, classLoader, true);
                                            if (holder != null) return holder;
                                        } else if (vt == 2273) {
                                            Class<?> cardBindingClass = Class.forName("miui.systemui.controlcenter.databinding.QsCardItemViewBinding", false, classLoader);
                                            Method inflateMethod = cardBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class);
                                            Object binding = inflateMethod.invoke(null, LayoutInflater.from(ctx), parent, false);

                                            Method getRootMethod = cardBindingClass.getMethod("getRoot");
                                            View root = (View) getRootMethod.invoke(binding);

                                            Class<?> cardIconViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", false, classLoader);
                                            Constructor<?> iconCtor = null;
                                            for (Constructor<?> c : cardIconViewClass.getDeclaredConstructors()) {
                                                if (c.getParameterTypes().length == 5) {
                                                    iconCtor = c;
                                                    break;
                                                }
                                            }
                                            if (iconCtor == null && cardIconViewClass.getDeclaredConstructors().length > 0) {
                                                iconCtor = cardIconViewClass.getDeclaredConstructors()[0];
                                            }
                                            if (iconCtor != null) {
                                                iconCtor.setAccessible(true);
                                                Object iconView = (iconCtor.getParameterTypes().length == 5) 
                                                    ? iconCtor.newInstance(root.getContext(), sysUIContext, null, 4, null)
                                                    : iconCtor.newInstance(root.getContext(), sysUIContext);

                                                Method initMethod = root.getClass().getMethod("init", cardIconViewClass);
                                                initMethod.invoke(root, iconView);
                                            }

                                            Class<?> cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader);
                                            Constructor<?> holderCtor = cardHolderClass.getConstructor(Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader));
                                            return holderCtor.newInstance(root);
                                        }
                                    } catch (Throwable t) {
                                        android.util.Log.e(TAG, "MainPanelAdapter.onCreateViewHolder " + vt + " intercept error", t);
                                    }
                                }
                            }
                            return chain.proceed();
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
                                    try {
                                        android.content.IntentFilter resetFilter = new android.content.IntentFilter("dev.joseph.hyperbounce.RESET_CC_LAYOUT");
                                        ctx.registerReceiver(new android.content.BroadcastReceiver() {
                                            @Override
                                            public void onReceive(Context rCtx, android.content.Intent intent) {
                                                resetLayoutToStock(rCtx);
                                            }
                                        }, resetFilter, Context.RECEIVER_EXPORTED);
                                    } catch (Throwable ignore) {}



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
                                if (spec != null) {
                                    TileGroup g = isGroupAnchor(spec);
                                    if (g != null) {
                                        if (g.specs.size() == 4) {
                                            return 2274; // Grouped 2x2 Card Container!
                                        } else if (g.specs.size() == 2) {
                                            return g.isVertical ? 2276 : 2275; // 2-Tile Vertical (1x2) or Horizontal (2x1)
                                        }
                                    }
                                    if (sExpandedTileSpecs.contains(spec)) {
                                        return 2273; // Genuine 2273 Card View Type!
                                    }
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
                                if (spec != null) {
                                    TileGroup g = isGroupAnchor(spec);
                                    if (g != null) {
                                        if (g.specs.size() == 4) {
                                            return 2; // 2-Column Span for 2x2!
                                        } else if (g.specs.size() == 2) {
                                            return g.isVertical ? 1 : 2; // 1-Column for Vertical, 2-Column for Horizontal!
                                        }
                                    }
                                    if (sExpandedTileSpecs.contains(spec)) {
                                        return 2; // Genuine 2-Column Span!
                                    }
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

                    if (m.getName().equals("setListening") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object record = chain.getThisObject();
                                Field specField = qsRecordClass.getDeclaredField("spec");
                                specField.setAccessible(true);
                                String spec = (String) specField.get(record);
                                TileGroup group = isGroupAnchor(spec);
                                if (group != null) {
                                    Object rawArgs = chain.getArgs();
                                    Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                    boolean listening = args != null && args.length > 0 && (boolean) args[0];
                                    List addedList = null;
                                    if (sQSListController != null) {
                                        try {
                                            Method getAddedMethod = sQSListController.getClass().getMethod("getAddedTiles");
                                            addedList = (List) getAddedMethod.invoke(sQSListController);
                                        } catch (Throwable ignore) {}
                                    }
                                    if (addedList != null) {
                                        for (Object r : addedList) {
                                            if (r != null) {
                                                try {
                                                    Field sF = r.getClass().getDeclaredField("spec");
                                                    sF.setAccessible(true);
                                                    String s = (String) sF.get(r);
                                                    if (group.specs.contains(s)) {
                                                        try {
                                                            Method getTileM = r.getClass().getMethod("getTile");
                                                            Object tileObj = getTileM.invoke(r);
                                                            if (tileObj != null) {
                                                                Method setLisM = tileObj.getClass().getMethod("setListening", Object.class, boolean.class);
                                                                setLisM.invoke(tileObj, r, listening);
                                                                if (listening) {
                                                                    Method addCb = r.getClass().getMethod("addCallback");
                                                                    addCb.invoke(r);
                                                                    Method refM = tileObj.getClass().getMethod("refreshState");
                                                                    refM.invoke(tileObj);
                                                                }
                                                            }
                                                        } catch (Throwable ignore) {}
                                                    }
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
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

            // 14. Inject Dedicated Resize Handle Badge & Grouped Card Binding on QSRecord View Binding
            try {
                Class<?> qsRecordClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSRecord", false, classLoader);
                for (Method m : qsRecordClass.getDeclaredMethods()) {
                    if (m.getName().equals("onBindViewHolder")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object record = chain.getThisObject();
                            if (sBindingRecords.get().contains(record)) {
                                return chain.proceed();
                            }
                            Object result = chain.proceed();
                            try {
                                sBindingRecords.get().add(record);
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

                                    // Grouped Card Binding (4-Tile & 2-Tile)
                                    TileGroup group = isGroupAnchor(spec);
                                    if (group != null) {
                                        boolean missingChild = false;
                                        View wrapperView = itemView.findViewById(0x7f099996);
                                        if (wrapperView != null) {
                                            wrapperView.setTag(0x7f099991, isEdit);
                                        }
                                        View rotateBtn = itemView.findViewById(0x7f099992);
                                        if (rotateBtn != null) {
                                            if (isEdit) {
                                                rotateBtn.setVisibility(View.VISIBLE);
                                                rotateBtn.bringToFront();
                                                rotateBtn.setClickable(true);
                                                rotateBtn.setOnClickListener(v -> {
                                                    try {
                                                        group.isVertical = !group.isVertical;
                                                        savePriorities(ctx);
                                                        refreshAdapter(itemView.getRootView(), ctx);
                                                    } catch (Throwable t) {
                                                        android.util.Log.e(TAG, "Rotate group click error", t);
                                                    }
                                                });
                                            } else {
                                                rotateBtn.setVisibility(View.GONE);
                                            }
                                        }
                                        ViewGroup container = itemView.findViewById(0x7f099995);
                                        if (container != null) {
                                            try {
                                                Map<String, Object> recordMap = new HashMap<>();

                                                // Get the active QSListController directly associated with this record
                                                Object activeQsListCtrl = null;
                                                try {
                                                    Field qsControllerField = qsRecordClass.getDeclaredField("qsController");
                                                    qsControllerField.setAccessible(true);
                                                    Object qsCtrl = qsControllerField.get(record);
                                                    if (qsCtrl != null) {
                                                        Method getQsListCtrlMethod = qsCtrl.getClass().getMethod("getQsListController");
                                                        Object lazyQsList = getQsListCtrlMethod.invoke(qsCtrl);
                                                        Method getMethod = lazyQsList.getClass().getMethod("get");
                                                        activeQsListCtrl = getMethod.invoke(lazyQsList);
                                                    }
                                                } catch (Throwable ignore) {}
                                                if (activeQsListCtrl == null) activeQsListCtrl = sQSListController;

                                                if (activeQsListCtrl != null) {
                                                    try {
                                                        Field recsF = activeQsListCtrl.getClass().getDeclaredField("records");
                                                        recsF.setAccessible(true);
                                                        List recsList = (List) recsF.get(activeQsListCtrl);
                                                        if (recsList != null) {
                                                            for (Object r : recsList) {
                                                                String s = getRecordSpec(r);
                                                                if (s != null) recordMap.put(s, r);
                                                            }
                                                        }
                                                    } catch (Throwable ignore) {}
                                                    try {
                                                        Method getAddedMethod = activeQsListCtrl.getClass().getMethod("getAddedTiles");
                                                        List addedList = (List) getAddedMethod.invoke(activeQsListCtrl);
                                                        if (addedList != null) {
                                                            for (Object r : addedList) {
                                                                String s = getRecordSpec(r);
                                                                if (s != null && !recordMap.containsKey(s)) recordMap.put(s, r);
                                                            }
                                                        }
                                                    } catch (Throwable ignore) {}
                                                    try {
                                                        Method getRecordsM = activeQsListCtrl.getClass().getMethod("getRecords");
                                                        List allRecs = (List) getRecordsM.invoke(activeQsListCtrl);
                                                        if (allRecs != null) {
                                                            for (Object r : allRecs) {
                                                                String s = getRecordSpec(r);
                                                                if (s != null && !recordMap.containsKey(s)) recordMap.put(s, r);
                                                            }
                                                        }
                                                    } catch (Throwable ignore) {}
                                                }

                                                // Fallback to static cache
                                                if (sAllQSRecords != null && !sAllQSRecords.isEmpty()) {
                                                    for (Object r : sAllQSRecords) {
                                                        String s = getRecordSpec(r);
                                                        if (s != null && !recordMap.containsKey(s)) recordMap.put(s, r);
                                                    }
                                                }

                                                // Ensure grouped container permanently maintains dark translucent blur & 20dp smooth corners
                                                applyGroupContainerStyle(itemView, classLoader);

                                                Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                                if (subHolders == null) {
                                                    subHolders = new Object[4];
                                                    Class<?> subHolderClass = null;
                                                    Constructor<?> subHolderCtor = null;
                                                    try {
                                                        subHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder", false, classLoader);
                                                        for (Constructor<?> c : subHolderClass.getDeclaredConstructors()) {
                                                            if (c.getParameterTypes().length == 2) {
                                                                subHolderCtor = c;
                                                                break;
                                                            }
                                                        }
                                                        if (subHolderCtor == null && subHolderClass.getDeclaredConstructors().length > 0) {
                                                            subHolderCtor = subHolderClass.getDeclaredConstructors()[0];
                                                        }
                                                        if (subHolderCtor != null) subHolderCtor.setAccessible(true);
                                                    } catch (Throwable ignore) {}

                                                    Object mainPanelController = null;
                                                    try {
                                                        Field mainPanelControllerField = record.getClass().getDeclaredField("mainPanelController");
                                                        mainPanelControllerField.setAccessible(true);
                                                        Object mainPanelCtrlLazy = mainPanelControllerField.get(record);
                                                        Method getMethod = mainPanelCtrlLazy.getClass().getMethod("get");
                                                        mainPanelController = getMethod.invoke(mainPanelCtrlLazy);
                                                    } catch (Throwable ignore) {}

                                                    for (int i = 0; i < 4; i++) {
                                                        View childTile = getChildTileAt(container, i);
                                                        if (childTile != null && subHolderCtor != null) {
                                                            try {
                                                                if (subHolderCtor.getParameterTypes().length == 2) {
                                                                    subHolders[i] = subHolderCtor.newInstance(childTile, mainPanelController);
                                                                } else {
                                                                    subHolders[i] = subHolderCtor.newInstance(childTile);
                                                                }
                                                            } catch (Throwable ignore) {}
                                                        }
                                                    }
                                                    itemView.setTag(0x7f099993, subHolders);
                                                }

                                                for (int i = 0; i < 4 && i < group.specs.size(); i++) {
                                                    String childSpec = group.specs.get(i);
                                                    View childView = getChildTileAt(container, i);
                                                    Object childRecord = recordMap.get(childSpec);
                                                    Object subHolder = (subHolders != null && i < subHolders.length) ? subHolders[i] : null;

                                                    if (childRecord == null) {
                                                        missingChild = true;
                                                    }

                                                    if (childRecord != null && subHolder != null && childView != null) {
                                                        try {
                                                            // 1. Wire subHolder to childRecord
                                                            try {
                                                                for (Method shM : subHolder.getClass().getMethods()) {
                                                                    if (shM.getName().startsWith("setItem")) {
                                                                        shM.invoke(subHolder, childRecord);
                                                                        break;
                                                                    }
                                                                }
                                                            } catch (Throwable ignore) {}

                                                            // 2. Wire childRecord to subHolder
                                                            Method setHolderMethod = childRecord.getClass().getMethod("setHolder", Class.forName("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelItemViewHolder", false, classLoader));
                                                            setHolderMethod.invoke(childRecord, subHolder);

                                                            // 3. Delegate to native onBindViewHolder of QSRecord (attaches native click listener!)
                                                            Method onBindMethod = childRecord.getClass().getMethod("onBindViewHolder");
                                                            onBindMethod.invoke(childRecord);

                                                            // 4. Set attached flag on subHolder
                                                            Class<?> vhCls = subHolder.getClass();
                                                            while (vhCls != null) {
                                                                try {
                                                                    Field af = vhCls.getDeclaredField("attached");
                                                                    af.setAccessible(true);
                                                                    af.setBoolean(subHolder, true);
                                                                    break;
                                                                } catch (Throwable ignore) { vhCls = vhCls.getSuperclass(); }
                                                            }

                                                            // 5. Update mode
                                                            try {
                                                                Class<?> modeClass = Class.forName("miui.systemui.controlcenter.panel.main.MainPanelController$Mode", false, classLoader);
                                                                Object currentMode = isEdit ? Enum.valueOf((Class<Enum>) modeClass, "EDIT") : Enum.valueOf((Class<Enum>) modeClass, "NORMAL");
                                                                Method updateModeMethod = childRecord.getClass().getMethod("updateMode", modeClass, boolean.class);
                                                                updateModeMethod.invoke(childRecord, currentMode, false);
                                                            } catch (Throwable ignore) {}

                                                            // 6. Direct QSTile listening registration & refreshState
                                                            try {
                                                                Method addCb = childRecord.getClass().getMethod("addCallback");
                                                                addCb.invoke(childRecord);
                                                            } catch (Throwable ignore) {}
                                                            try {
                                                                Method getTileM = childRecord.getClass().getMethod("getTile");
                                                                Object tileObj = getTileM.invoke(childRecord);
                                                                if (tileObj != null) {
                                                                    Method setLisM = tileObj.getClass().getMethod("setListening", Object.class, boolean.class);
                                                                    setLisM.invoke(tileObj, childRecord, true);
                                                                    Method refM = tileObj.getClass().getMethod("refreshState");
                                                                    refM.invoke(tileObj);
                                                                }
                                                            } catch (Throwable ignore) {}

                                                            // In edit mode: child tiles are non-clickable so dragging pulls container
                                                            if (isEdit) {
                                                                childView.setClickable(false);
                                                                childView.setLongClickable(false);
                                                                childView.setFocusable(false);
                                                            } else {
                                                                childView.setClickable(true);
                                                                childView.setLongClickable(true);
                                                                childView.setFocusable(true);
                                                            }

                                                            // 7. Center icon_frame and apply blur disc highlight if Advanced Textures is on
                                                            try {
                                                                boolean blurEnabled = false;
                                                                try {
                                                                    Class<?> ccUtils = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader);
                                                                    Method getBlurM = ccUtils.getMethod("getBackgroundBlurOpenedInDefaultTheme", Context.class);
                                                                    blurEnabled = (boolean) getBlurM.invoke(null, ctx);
                                                                } catch (Throwable ignore) {}

                                                                int iconFrameId = ctx.getResources().getIdentifier("icon_frame", "id", ctx.getPackageName());
                                                                View iconFrame = childView.findViewById(iconFrameId);
                                                                if (iconFrame != null) {
                                                                    ViewGroup.LayoutParams iflp = iconFrame.getLayoutParams();
                                                                    if (iflp instanceof android.widget.FrameLayout.LayoutParams) {
                                                                        android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) iflp;
                                                                        flp.gravity = Gravity.CENTER;
                                                                        flp.topMargin = 0;
                                                                        flp.bottomMargin = 0;
                                                                        iconFrame.setLayoutParams(flp);
                                                                    }
                                                                    if (blurEnabled) {
                                                                        android.graphics.drawable.GradientDrawable discBg = new android.graphics.drawable.GradientDrawable();
                                                                        discBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                                                                        discBg.setColor(Color.parseColor("#38FFFFFF"));
                                                                        discBg.setStroke((int) Math.max(1, 1.5f * density), Color.parseColor("#80FFFFFF"));
                                                                        iconFrame.setBackground(discBg);
                                                                    } else {
                                                                        iconFrame.setBackground(null);
                                                                    }
                                                                }
                                                            } catch (Throwable ignore) {}

                                                            // 8. Forward updateBlendBlur to child holder
                                                            try {
                                                                Method ubb = subHolder.getClass().getMethod("updateBlendBlur");
                                                                ubb.invoke(subHolder);
                                                            } catch (Throwable ignore) {}
                                                        } catch (Throwable t) {
                                                            android.util.Log.e(TAG, "Grouped card sub-tile native binding error: " + childSpec, t);
                                                        }
                                                    }
                                                }
                                            } catch (Throwable t) {
                                                android.util.Log.e(TAG, "Grouped card sub-tiles binding error", t);
                                            }
                                        }

                                        // Disassemble Selection Indicator attached to top-left of all containers
                                        int selectTag = 0x7f099998;
                                        View rawBadge = itemView.findViewById(selectTag);
                                        ImageView indicatorBadge = (rawBadge instanceof ImageView) ? (ImageView) rawBadge : null;

                                        if (sIsDisassembleMode) {
                                            if (indicatorBadge != null) {
                                                SelectCircleDrawable scd = (indicatorBadge.getDrawable() instanceof SelectCircleDrawable)
                                                    ? (SelectCircleDrawable) indicatorBadge.getDrawable() : new SelectCircleDrawable();
                                                scd.setChecked(sSelectedDisassembleGroups.contains(group.id));
                                                indicatorBadge.setImageDrawable(scd);
                                                indicatorBadge.setVisibility(View.VISIBLE);
                                                indicatorBadge.bringToFront();
                                                indicatorBadge.setClickable(true);

                                                final ImageView finalBadge = indicatorBadge;
                                                View.OnClickListener toggleDisassembleSelect = v -> {
                                                    if (sSelectedDisassembleGroups.contains(group.id)) {
                                                        sSelectedDisassembleGroups.remove(group.id);
                                                    } else {
                                                        sSelectedDisassembleGroups.add(group.id);
                                                    }
                                                    SelectCircleDrawable curScd = (SelectCircleDrawable) finalBadge.getDrawable();
                                                    if (curScd != null) {
                                                        curScd.setChecked(sSelectedDisassembleGroups.contains(group.id));
                                                        finalBadge.invalidate();
                                                    }
                                                };
                                                indicatorBadge.setOnClickListener(toggleDisassembleSelect);
                                                itemView.setOnClickListener(toggleDisassembleSelect);
                                                itemView.setClickable(true);

                                                // Enable selection toggle on EVERY child tile and icon frame inside the container
                                                for (int ci = 0; ci < 4; ci++) {
                                                    View ct = getChildTileAt(container, ci);
                                                    if (ct != null) {
                                                        ct.setOnClickListener(toggleDisassembleSelect);
                                                        ct.setClickable(true);
                                                        ct.setLongClickable(false);
                                                        int ifId = ctx.getResources().getIdentifier("icon_frame", "id", ctx.getPackageName());
                                                        View ifView = ct.findViewById(ifId);
                                                        if (ifView != null) {
                                                            ifView.setOnClickListener(toggleDisassembleSelect);
                                                            ifView.setClickable(true);
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            if (indicatorBadge != null) {
                                                indicatorBadge.setVisibility(View.GONE);
                                                indicatorBadge.setOnClickListener(null);
                                                indicatorBadge.setClickable(false);
                                            }
                                            itemView.setOnClickListener(null);
                                            itemView.setClickable(false);
                                            itemView.setFocusable(false);

                                            // Clear icon_frame overlay clicks and configure edit/normal touch state
                                            for (int ci = 0; ci < 4; ci++) {
                                                View ct = getChildTileAt(container, ci);
                                                if (ct != null) {
                                                    int ifId = ctx.getResources().getIdentifier("icon_frame", "id", ctx.getPackageName());
                                                    View ifView = ct.findViewById(ifId);
                                                    if (ifView != null) {
                                                        ifView.setOnClickListener(null);
                                                        ifView.setClickable(false);
                                                    }
                                                    if (isEdit) {
                                                        ct.setClickable(false);
                                                        ct.setLongClickable(false);
                                                        ct.setFocusable(false);
                                                    } else {
                                                        ct.setClickable(true);
                                                        ct.setLongClickable(true);
                                                        ct.setFocusable(true);
                                                    }
                                                }
                                            }
                                        }

                                        // If any sub-tile record was not yet ready on early layout, schedule one-shot retry pass
                                        if (missingChild) {
                                            final Object finalRecord = record;
                                            itemView.postDelayed(() -> {
                                                try {
                                                    if (itemView.isAttachedToWindow()) {
                                                        Method onBindM = finalRecord.getClass().getMethod("onBindViewHolder");
                                                        onBindM.invoke(finalRecord);
                                                    }
                                                } catch (Throwable ignore) {}
                                            }, 100);
                                        }
                                    }

                                    int resizeTag = 0x7f099999;
                                    ImageView resizeHandle = itemView.findViewById(resizeTag);
                                    int selectTag = 0x7f099998;
                                    ImageView selectBadge = itemView.findViewById(selectTag);

                                    // Top-Left Selection Circle for Tile Grouping
                                    if (isEdit && added && spec != null && sIsGroupSelectMode && group == null) {
                                        final String finalSpec = spec;
                                        if (selectBadge == null && itemView instanceof ViewGroup) {
                                            selectBadge = new ImageView(ctx);
                                            selectBadge.setId(selectTag);
                                            SelectCircleDrawable scd = new SelectCircleDrawable();
                                            scd.setChecked(sSelectedTileSpecs.contains(finalSpec));
                                            selectBadge.setImageDrawable(scd);

                                            int badgeSize = (int) (22 * density);
                                            if (itemView instanceof FrameLayout) {
                                                FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
                                                slp.gravity = Gravity.TOP | Gravity.START;
                                                slp.leftMargin = (int) (8 * density);
                                                slp.topMargin = (int) (8 * density);
                                                ((ViewGroup) itemView).addView(selectBadge, slp);
                                            } else {
                                                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(badgeSize, badgeSize);
                                                ((ViewGroup) itemView).addView(selectBadge, lp);
                                            }
                                        }
                                        if (selectBadge != null) {
                                            SelectCircleDrawable scd = (SelectCircleDrawable) selectBadge.getDrawable();
                                            if (scd != null) scd.setChecked(sSelectedTileSpecs.contains(finalSpec));
                                            selectBadge.setVisibility(View.VISIBLE);
                                            selectBadge.bringToFront();

                                            final ImageView finalSelectBadge = selectBadge;
                                            selectBadge.setOnClickListener(v -> {
                                                try {
                                                    if (sSelectedTileSpecs.contains(finalSpec)) {
                                                        sSelectedTileSpecs.remove(finalSpec);
                                                    } else {
                                                        if (sSelectedTileSpecs.size() < 4) {
                                                            sSelectedTileSpecs.add(finalSpec);
                                                        }
                                                    }
                                                    SelectCircleDrawable curScd = (SelectCircleDrawable) finalSelectBadge.getDrawable();
                                                    if (curScd != null) curScd.setChecked(sSelectedTileSpecs.contains(finalSpec));
                                                } catch (Throwable ignore) {}
                                            });
                                        }
                                    } else if (selectBadge != null) {
                                        selectBadge.setVisibility(View.GONE);
                                    }

                                    // Resize Handle
                                    if (isEdit && added && spec != null && !sIsGroupSelectMode && group == null && !itemView.getClass().getName().contains("Wordless")) {
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
                                android.util.Log.e(TAG, "QSRecord onBindViewHolder binding error", t);
                            } finally {
                                sBindingRecords.get().remove(record);
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

            // 14c. Keep Grouped 2x2 Container in Dark Realtime Blur & 20dp Rounded Outlines (Never Solid White, Never Sharp)
            try {
                Class<?> cardViewClass = Class.forName("miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader);
                for (Method m : cardViewClass.getDeclaredMethods()) {
                    if (m.getName().equals("updateBlurBlendBackground") || m.getName().equals("updateDrawableBackground") || m.getName().equals("updateBackground") || m.getName().equals("updateClip")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            View itemView = (View) chain.getThisObject();
                            if (itemView != null && itemView.findViewById(0x7f099995) != null) {
                                applyGroupContainerStyle(itemView, classLoader);
                                return null;
                            }
                            return chain.proceed();
                        });
                    }
                    if (m.getName().equals("onTouchEvent") || m.getName().equals("onInterceptTouchEvent")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            View itemView = (View) chain.getThisObject();
                            if (itemView != null && itemView.findViewById(0x7f099995) != null) {
                                if (sIsDisassembleMode) {
                                    return chain.proceed();
                                }
                                return false;
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable ignore) {}

            // 14d. Forward QSCardViewHolder Animations, Attachments & Frame Callbacks to all Sub-Holders
            try {
                Class<?> cardHolderClass = Class.forName("miui.systemui.controlcenter.panel.main.qs.QSCardViewHolder", false, classLoader);
                for (Method m : cardHolderClass.getDeclaredMethods()) {
                    if (m.getName().equals("onFrameCallback")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object cardHolder = chain.getThisObject();
                                View itemView = getViewHolderItemView(cardHolder);
                                if (itemView != null) {
                                    Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                    if (subHolders != null) {
                                        for (Object subHolder : subHolders) {
                                            if (subHolder != null) {
                                                updateChildHolderFrame(cardHolder, subHolder);
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                    if (m.getName().equals("onModeChanged")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object cardHolder = chain.getThisObject();
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                View itemView = getViewHolderItemView(cardHolder);
                                if (itemView != null && args != null && args.length >= 2) {
                                    Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                    if (subHolders != null) {
                                        for (Object subHolder : subHolders) {
                                            if (subHolder != null) {
                                                try {
                                                    Method childOnMode = subHolder.getClass().getMethod("onModeChanged", args[0].getClass(), boolean.class);
                                                    childOnMode.invoke(subHolder, args[0], args[1]);
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                    if (m.getName().equals("onConfigurationChanged")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object cardHolder = chain.getThisObject();
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                View itemView = getViewHolderItemView(cardHolder);
                                if (itemView != null && args != null && args.length >= 1) {
                                    Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                    if (subHolders != null) {
                                        for (Object subHolder : subHolders) {
                                            if (subHolder != null) {
                                                try {
                                                    Method childOnConfig = subHolder.getClass().getMethod("onConfigurationChanged", int.class);
                                                    childOnConfig.invoke(subHolder, args[0]);
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                    if (m.getName().equals("onViewAttachedToWindow")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object cardHolder = chain.getThisObject();
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof List) ? ((List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                if (args != null && args.length >= 2) {
                                    sMainPanelAdapter = args[0];
                                    sFrameCallback = args[1];
                                }
                                View itemView = getViewHolderItemView(cardHolder);
                                if (itemView != null) {
                                    Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                    if (subHolders != null) {
                                        for (Object subHolder : subHolders) {
                                            if (subHolder != null) {
                                                Class<?> vhCls = subHolder.getClass();
                                                while (vhCls != null) {
                                                    try {
                                                        Field af = vhCls.getDeclaredField("attached");
                                                        af.setAccessible(true);
                                                        af.setBoolean(subHolder, true);
                                                        break;
                                                    } catch (Throwable ignore) { vhCls = vhCls.getSuperclass(); }
                                                }
                                                try {
                                                    if (args != null && args.length >= 2) {
                                                        Method childOnAttach = subHolder.getClass().getMethod("onViewAttachedToWindow", args[0].getClass(), args[1].getClass());
                                                        childOnAttach.invoke(subHolder, args[0], args[1]);
                                                    } else {
                                                        Method childOnAttach = subHolder.getClass().getMethod("onViewAttachedToWindow");
                                                        childOnAttach.invoke(subHolder);
                                                    }
                                                } catch (Throwable ignore) {}
                                                try {
                                                    for (Method im : subHolder.getClass().getMethods()) {
                                                        if (im.getName().startsWith("getItem") && im.getParameterTypes().length == 0) {
                                                            Object childRec = im.invoke(subHolder);
                                                            if (childRec != null) {
                                                                try {
                                                                    Method addCb = childRec.getClass().getMethod("addCallback");
                                                                    addCb.invoke(childRec);
                                                                } catch (Throwable ignore) {}
                                                                try {
                                                                    Method getTileM = childRec.getClass().getMethod("getTile");
                                                                    Object tileObj = getTileM.invoke(childRec);
                                                                    if (tileObj != null) {
                                                                        Method setLisM = tileObj.getClass().getMethod("setListening", Object.class, boolean.class);
                                                                        setLisM.invoke(tileObj, childRec, true);
                                                                        Method refM = tileObj.getClass().getMethod("refreshState");
                                                                        refM.invoke(tileObj);
                                                                    }
                                                                } catch (Throwable ignore) {}
                                                            }
                                                            break;
                                                        }
                                                    }
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }
                    if (m.getName().equals("onViewDetachedFromWindow")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object cardHolder = chain.getThisObject();
                                View itemView = getViewHolderItemView(cardHolder);
                                if (itemView != null) {
                                    Object[] subHolders = (Object[]) itemView.getTag(0x7f099993);
                                    if (subHolders != null) {
                                        for (Object subHolder : subHolders) {
                                            if (subHolder != null) {
                                                try {
                                                    Method childOnDetach = subHolder.getClass().getMethod("onViewDetachedFromWindow");
                                                    childOnDetach.invoke(subHolder);
                                                } catch (Throwable ignore) {}
                                                try {
                                                    for (Method im : subHolder.getClass().getMethods()) {
                                                        if (im.getName().startsWith("getItem") && im.getParameterTypes().length == 0) {
                                                            Object childRec = im.invoke(subHolder);
                                                            if (childRec != null) {
                                                                try {
                                                                    Method remCb = childRec.getClass().getMethod("removeCallback");
                                                                    remCb.invoke(childRec);
                                                                } catch (Throwable ignore) {}
                                                                try {
                                                                    Method setLis = childRec.getClass().getMethod("setListening", boolean.class);
                                                                    setLis.invoke(childRec, false);
                                                                } catch (Throwable ignore) {}
                                                            }
                                                            break;
                                                        }
                                                    }
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            return result;
                        });
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

            // 18. Inject "Group / Select" Button in CustomizeHeaderController across all lifecycle points
            try {
                Class<?> customizeHeaderClass = Class.forName("miui.systemui.controlcenter.panel.main.header.CustomizeHeaderController", false, classLoader);
                
                for (java.lang.reflect.Constructor<?> c : customizeHeaderClass.getDeclaredConstructors()) {
                    c.setAccessible(true);
                    module.hook(c).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            ensureGroupButtonInjected(chain.getThisObject(), classLoader);
                        } catch (Throwable ignore) {}
                        return result;
                    });
                }

                for (Method m : customizeHeaderClass.getDeclaredMethods()) {
                    String mName = m.getName();
                    if (mName.equals("onCreate") || mName.equals("transitionFrom") || mName.equals("updateResources") || mName.equals("updateBlendStyle")) {
                        m.setAccessible(true);
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                ensureGroupButtonInjected(chain.getThisObject(), classLoader);
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
