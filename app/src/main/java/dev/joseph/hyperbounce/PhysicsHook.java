package dev.joseph.hyperbounce;

import android.app.Activity;
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
        log("[INIT] HyperBounce: Liquid Glass & Text Raycaster");
        log("==================================================");

        // 1. GHOST THE TAB BAR & INJECT UI
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        if (resName.equals("tab_bar") || resName.equals("tab_bar_shadow") || resName.equals("layout_bottom_searchbar")) {
                            view.setVisibility(View.GONE);
                            ViewGroup.LayoutParams lp = view.getLayoutParams();
                            if (lp != null) {
                                lp.height = 0;
                                view.setLayoutParams(lp);
                            }
                        }

                        // Trigger Injection
                        if (resName.equals("layout_container_main_wrapper")) {
                            if (view.getContext() instanceof Activity) {
                                Activity activity = (Activity) view.getContext();
                                view.post(() -> {
                                    try {
                                        int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                                        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                        ViewGroup stockBar = decorView.findViewById(stockBarId);
                                        LiquidGlassInjectorKt.injectLiquidGlass(activity, (ViewGroup) view, stockBar);
                                    } catch (Exception e) {}
                                });
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. THE MARGIN ASSASSIN (Keep video stretched to the bottom)
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "measureChildWithMargins", 
                View.class, int.class, int.class, int.class, int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View child = (View) param.args[0];
                        if (child.getId() == View.NO_ID || child.getResources() == null) return;

                        try {
                            String resName = child.getResources().getResourceEntryName(child.getId());
                            if (resName.equals("layout_container_main") || 
                                resName.equals("layout_container_main_wrapper") || 
                                resName.equals("swipeable_tab_view_pager")) {
                                
                                ViewGroup.LayoutParams lp = child.getLayoutParams();
                                if (lp instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                    if (mlp.bottomMargin > 50) mlp.bottomMargin = 0;
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {}

        // 3. THE TEXT RAYCASTER (Find the Follow/Description tags)
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    MotionEvent event = (MotionEvent) param.args[0];
                    Activity activity = (Activity) param.thisObject;

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        float touchY = event.getRawY();
                        float touchX = event.getRawX();
                        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

                        // Target the area just above the nav bar (70% to 90% of screen height) where text lives
                        if (touchY > screenHeight * 0.70f && touchY < screenHeight * 0.95f) {
                            log("\n=== TEXT RAYCASTER: Tapped at X:" + touchX + " Y:" + touchY + " ===");
                            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                            dumpViewsAt(decorView, touchX, touchY, 0);
                            log("=========================================\n");
                        }
                    }
                }
            });
        } catch (Throwable t) {}
    }

    private void dumpViewsAt(View view, float x, float y, int depth) {
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

            log(indent.toString() + "-> " + resName + " [" + view.getClass().getSimpleName() + "] Y:" + vy);

            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpViewsAt(vg.getChildAt(i), x, y, depth + 1);
                }
            }
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
