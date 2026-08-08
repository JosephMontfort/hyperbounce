package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.contains("instagram")) return;

        log("==================================================");
        log("[INIT] HyperBounce: Void Nuke + Aggressive Background Tracer");
        log("==================================================");

        // 1. DIMENSION SPOOFER: Force OS resource dimension lookups to 0px
        try {
            XposedHelpers.findAndHookMethod(
                Resources.class,
                "getDimensionPixelSize",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Resources res = (Resources) param.thisObject;
                        int id = (Integer) param.args[0];
                        try {
                            String name = res.getResourceEntryName(id);
                            if (name.equals("tab_bar_height") || 
                                name.equals("tab_bar_separator_height") || 
                                name.contains("bottom_nav_height")) {
                                param.setResult(0);
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] Dimension Spoofer failed: " + t.getMessage());
        }

        // 2. MEASUREMENT INTERCEPTOR: Force TouchInterceptorLinearLayout height to EXACTLY 0 during layout pass
        try {
            Class<?> touchLayoutClass = XposedHelpers.findClassIfExists(
                "com.instagram.common.ui.widget.touchinterceptorlayout.TouchInterceptorLinearLayout",
                lpparam.classLoader
            );

            if (touchLayoutClass != null) {
                XposedHelpers.findAndHookMethod(
                    touchLayoutClass,
                    "onMeasure",
                    int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            View view = (View) param.thisObject;
                            if (view.getId() == View.NO_ID || view.getResources() == null) return;

                            try {
                                String resName = view.getResources().getResourceEntryName(view.getId());
                                if ("tab_bar".equals(resName)) {
                                    int zeroHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                                    param.args[1] = zeroHeightSpec;
                                }
                            } catch (Exception e) {}
                        }
                    }
                );
            }
        } catch (Throwable t) {
            log("[ERROR] Failed to hook onMeasure: " + t.getMessage());
        }

        // 3. ATTACHMENT HOOK: Clear backgrounds, collapse layout params, & set System Nav Bar transparent
        try {
            XposedHelpers.findAndHookMethod(
                View.class,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (view.getId() == View.NO_ID || view.getResources() == null) return;

                        try {
                            String resName = view.getResources().getResourceEntryName(view.getId());

                            // A. Strip background and collapse tab bar / shadow
                            if (resName.equals("tab_bar") || resName.equals("tab_bar_shadow") || resName.equals("layout_bottom_searchbar")) {
                                view.setVisibility(View.GONE);
                                view.setBackground(null);
                                view.setBackgroundColor(Color.TRANSPARENT);

                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp != null) {
                                    lp.height = 0;
                                    view.setLayoutParams(lp);
                                }

                                if (view.getContext() instanceof Activity) {
                                    Activity activity = (Activity) view.getContext();
                                    Window window = activity.getWindow();
                                    if (window != null) {
                                        window.setNavigationBarColor(Color.TRANSPARENT);
                                    }
                                }
                            }

                            // B. Strip bottom margins & paddings from main containers
                            if (resName.equals("layout_container_main") || 
                                resName.equals("layout_container_main_wrapper") ||
                                resName.equals("layout_container_main_panel")) {

                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                    if (mlp.bottomMargin > 0) {
                                        mlp.bottomMargin = 0;
                                        view.setLayoutParams(mlp);
                                    }
                                }
                                if (view.getPaddingBottom() > 0) {
                                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), 0);
                                }
                                view.setBackground(null);
                                view.setBackgroundColor(Color.TRANSPARENT);
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] View Attachment hook failed: " + t.getMessage());
        }

        // 4. AGGRESSIVE BACKGROUND TRACER: Tap the void to hunt down the opaque renderer
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "dispatchTouchEvent",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        Activity activity = (Activity) param.thisObject;

                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            float touchY = event.getRawY();
                            float touchX = event.getRawX();
                            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

                            // Target the bottom 15% where the void lives
                            if (touchY > screenHeight * 0.85f) {
                                log("\n==================================================");
                                log("[OPAQUE BAR HUNT] Touch Intercepted at X:" + touchX + " Y:" + touchY);
                                log("Activity: " + activity.getClass().getName());
                                log("==================================================");

                                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                if (decorView != null) {
                                    log("--- DEEP Z-AXIS BACKGROUND DUMP ---");
                                    dumpAggressiveBackgroundsAtPoint(decorView, touchX, touchY, 0);
                                }

                                log("\n--- VOID TOUCH STACKTRACE ---");
                                log(Log.getStackTraceString(new Throwable("OpaqueHuntStack")));
                                log("==================================================\n");
                            }
                        }
                    }
                }
            );
            log("[SUCCESS] Aggressive Background Tracer Armed");
        } catch (Throwable t) {
            log("[ERROR] Touch hook failed: " + t.getMessage());
        }
    }

    private void dumpAggressiveBackgroundsAtPoint(View view, float x, float y, int depth) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        int vx = loc[0], vy = loc[1], vw = view.getWidth(), vh = view.getHeight();

        // Check intersection
        if (x >= vx && x <= vx + vw && y >= vy && y <= vy + vh) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");

            String resName = "NO_ID";
            try {
                if (view.getId() != View.NO_ID && view.getResources() != null) {
                    resName = view.getResources().getResourceEntryName(view.getId());
                }
            } catch (Exception e) {}

            // Extract Layout Params
            int marginBot = 0;
            if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                marginBot = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
            }

            // Extract Background Info
            String bgData = "null";
            Drawable bg = view.getBackground();
            if (bg != null) {
                bgData = bg.getClass().getSimpleName();
                if (bg instanceof ColorDrawable) {
                    int color = ((ColorDrawable) bg).getColor();
                    bgData += String.format("(HEX: #%08X)", color);
                }
            }

            log(indent.toString() + "-> " + resName + " [" + view.getClass().getSimpleName() + "]");
            log(indent.toString() + "   Bounds: " + vw + "x" + vh + " | MarginBot: " + marginBot + " | PadBot: " + view.getPaddingBottom());
            log(indent.toString() + "   Background: " + bgData + " | Vis: " + view.getVisibility() + " | Alpha: " + view.getAlpha());

            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpAggressiveBackgroundsAtPoint(vg.getChildAt(i), x, y, depth + 1);
                }
            }
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
