package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

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
        log("[INIT] HyperBounce: The Snitch & Void Raycaster");
        log("==================================================");

        // 1. Keep the Dimension Spoofer (This halved the height earlier)
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Resources res = (Resources) param.thisObject;
                    try {
                        String name = res.getResourceEntryName((Integer) param.args[0]);
                        if (name.equals("tab_bar_height") || name.equals("tab_bar_separator_height") || name.contains("bottom_nav_height")) {
                            param.setResult(0);
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. Keep the Insets Assassin (This killed the OS protections)
        try {
            XposedHelpers.findAndHookMethod(View.class, "dispatchApplyWindowInsets", WindowInsets.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;
                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        if (isMainContainer(resName)) {
                            if (android.os.Build.VERSION.SDK_INT >= 30) param.setResult(WindowInsets.CONSUMED);
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 3. THE LAYOUT SNITCH: Catch whatever is shrinking the feed
        XC_MethodHook snitchHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if (view.getId() == View.NO_ID || view.getResources() == null) return;
                try {
                    String resName = view.getResources().getResourceEntryName(view.getId());
                    if (isMainContainer(resName)) {
                        String methodName = param.method.getName();
                        
                        // Check if the layout modification actually shrinks the bottom
                        boolean isSuspicious = false;
                        String details = "";

                        if (methodName.equals("setPadding") || methodName.equals("setPaddingRelative")) {
                            int padBottom = (Integer) param.args[3];
                            if (padBottom > 0) {
                                isSuspicious = true;
                                details = "PadBot: " + padBottom;
                            }
                        } else if (methodName.equals("setLayoutParams")) {
                            ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[0];
                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                int marginBot = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
                                if (marginBot > 0) {
                                    isSuspicious = true;
                                    details = "MarginBot: " + marginBot;
                                }
                            }
                        } else if (methodName.equals("setTranslationY")) {
                            float transY = (Float) param.args[0];
                            if (transY < 0) {
                                isSuspicious = true;
                                details = "TransY: " + transY;
                            }
                        }

                        if (isSuspicious) {
                            log("\n[SNITCH] Rogue Layout Modification Detected!");
                            log("Target: " + resName);
                            log("Action: " + methodName + " -> " + details);
                            log("Stacktrace:\n" + Log.getStackTraceString(new Throwable("LayoutSnitchTrace")));
                        }
                    }
                } catch (Exception e) {}
            }
        };

        try {
            XposedHelpers.findAndHookMethod(View.class, "setPadding", int.class, int.class, int.class, int.class, snitchHook);
            XposedHelpers.findAndHookMethod(View.class, "setPaddingRelative", int.class, int.class, int.class, int.class, snitchHook);
            XposedHelpers.findAndHookMethod(View.class, "setLayoutParams", ViewGroup.LayoutParams.class, snitchHook);
            XposedHelpers.findAndHookMethod(View.class, "setTranslationY", float.class, snitchHook);
        } catch (Throwable t) {}

        // 4. THE VOID RAYCASTER: Tap the reduced bar to scan it
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

                        // Target the bottom 12% for the reduced height void
                        if (touchY > screenHeight * 0.88f) {
                            log("\n==================================================");
                            log("[VOID RAYCASTER] Intercepted at X:" + touchX + " Y:" + touchY);
                            log("==================================================");

                            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                            if (decorView != null) {
                                dumpRaycast(decorView, touchX, touchY, 0);
                            }
                            log("==================================================\n");
                        }
                    }
                }
            });
        } catch (Throwable t) {}
    }

    private boolean isMainContainer(String resName) {
        return resName.equals("layout_container_main") || 
               resName.equals("layout_container_main_wrapper") ||
               resName.equals("layout_container_main_panel") ||
               resName.equals("swipe_navigation_container") ||
               resName.equals("layout_container_center_right_coordinator_layout") ||
               resName.equals("bottom_sheet_camera_container");
    }

    private void dumpRaycast(View view, float x, float y, int depth) {
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

            int marginBot = 0;
            if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                marginBot = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin;
            }

            log(indent.toString() + "-> " + resName + " [" + view.getClass().getSimpleName() + "]");
            log(indent.toString() + "   Bounds: " + vw + "x" + vh + " | MarB: " + marginBot + " | PadB: " + view.getPaddingBottom() + " | TransY: " + view.getTranslationY());

            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpRaycast(vg.getChildAt(i), x, y, depth + 1);
                }
            }
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
