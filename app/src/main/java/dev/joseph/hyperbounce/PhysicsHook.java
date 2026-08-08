package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

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
        log("[INIT] HyperBounce: Void Nuke & Aggressive Tracer");
        log("==================================================");

        // 1. DIMENSION SPOOFER: Force OS to tell Instagram the tab bar is 0px tall
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
                                
                                // Silently override the dimension to 0
                                param.setResult(0);
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] Dimension Spoofer failed: " + t.getMessage());
        }

        // 2. VIEW ATTACHMENT: Nuke visibility AND margins dynamically
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

                            // A. Obliterate the actual bar and shadow
                            if (resName.equals("tab_bar") || resName.equals("tab_bar_shadow") || resName.equals("layout_bottom_searchbar")) {
                                view.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp != null) {
                                    lp.height = 0;
                                    view.setLayoutParams(lp);
                                }
                                log("[NUKE] Obliterated view: " + resName);
                            }

                            // B. Strip the hardcoded bottom margins from the main feed containers
                            if (resName.equals("layout_container_main") || 
                                resName.equals("layout_container_main_wrapper") ||
                                resName.equals("layout_container_main_panel")) {
                                
                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                    if (mlp.bottomMargin > 0) {
                                        log("[MARGIN FIX] Stripped bottom margin (" + mlp.bottomMargin + "px) from: " + resName);
                                        mlp.bottomMargin = 0;
                                        view.setLayoutParams(mlp);
                                    }
                                }
                                
                                if (view.getPaddingBottom() > 0) {
                                    log("[PADDING FIX] Stripped bottom padding (" + view.getPaddingBottom() + "px) from: " + resName);
                                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), 0);
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] View Attachment hook failed: " + t.getMessage());
        }

        // 3. AGGRESSIVE LAYOUT TRACER: Tap the void to scan for rogue margins
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

                            // Only trace if touching the bottom 15% (The Void)
                            if (touchY > screenHeight * 0.85f) {
                                log("\n==================================================");
                                log("[VOID TOUCH TRACER] Hunting bounds at X:" + touchX + " Y:" + touchY);
                                log("==================================================");

                                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                if (decorView != null) {
                                    dumpLayoutBoundsAtPoint(decorView, touchX, touchY, 0);
                                }
                                log("==================================================\n");
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] Touch hook failed: " + t.getMessage());
        }
    }

    private void dumpLayoutBoundsAtPoint(View view, float x, float y, int depth) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        int vx = loc[0], vy = loc[1], vw = view.getWidth(), vh = view.getHeight();

        if (x >= vx && x <= vx + vw && y >= vy && y <= vy + vh) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");

            String resName = "NO_ID";
            try {
                if (view.getId() != View.NO_ID && view.getResources() != null) {
                    resName = view.getResources().getResourceEntryName(view.getId());
                }
            } catch (Exception e) {}

            int marginBottom = 0;
            if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                marginBottom = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
            }

            log(indent.toString() + "-> " + resName + " | class: " + view.getClass().getSimpleName() + " | height: " + vh + "px | marginBot: " + marginBottom + "px | padBot: " + view.getPaddingBottom() + "px");

            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpLayoutBoundsAtPoint(vg.getChildAt(i), x, y, depth + 1);
                }
            }
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
