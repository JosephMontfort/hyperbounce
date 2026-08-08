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
        log("[INIT] HyperBounce Aggressive Tracer Active in: " + lpparam.packageName);
        log("==================================================");

        // 1. Hook Activity.dispatchTouchEvent for aggressive touch tracing
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
                            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                            float touchY = event.getRawY();
                            float touchX = event.getRawX();

                            // Target the bottom 20% of the screen (Navigation Area)
                            if (touchY > screenHeight * 0.80f) {
                                log("\n==================================================");
                                log("[TOUCH IN NAV BAR ZONE] RawX: " + touchX + ", RawY: " + touchY + " / ScreenH: " + screenHeight);
                                log("Activity: " + activity.getClass().getName());
                                log("==================================================");

                                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                if (decorView != null) {
                                    log("--- DUMPING VIEW INTERSECTIONS AT TOUCH POINT ---");
                                    dumpViewsAtPoint(decorView, touchX, touchY, 0);
                                }

                                log("\n--- TOUCH EVENT STACKTRACE ---");
                                log(Log.getStackTraceString(new Throwable("NavTouchStack")));
                                log("==================================================\n");
                            }
                        }
                    }
                }
            );
            log("[SUCCESS] Hooked Activity.dispatchTouchEvent");
        } catch (Throwable t) {
            log("[ERROR] Failed to hook dispatchTouchEvent: " + t.getMessage());
        }

        // 2. Hook View.onAttachedToWindow to catch inflating components
        try {
            XposedHelpers.findAndHookMethod(
                View.class,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        String resName = safeGetResourceName(view);
                        String className = view.getClass().getName();

                        if (resName.toLowerCase().contains("tab") ||
                            resName.toLowerCase().contains("nav") ||
                            resName.toLowerCase().contains("bottom") ||
                            className.toLowerCase().contains("touchinterceptor") ||
                            className.toLowerCase().contains("litho") ||
                            className.toLowerCase().contains("componenthost")
                        ) {
                            log("\n[VIEW ATTACHED] Class: " + className + " | ID: " + resName + " | Vis: " + view.getVisibility() + " | Size: " + view.getWidth() + "x" + view.getHeight());
                            String parentName = (view.getParent() != null) ? view.getParent().getClass().getName() : "null";
                            log("Parent: " + parentName);
                            // We comment out the stacktrace here temporarily so it doesn't flood logcat completely on app boot
                            // log("Stacktrace:\n" + Log.getStackTraceString(new Throwable("ViewAttachStack")));
                        }
                    }
                }
            );
            log("[SUCCESS] Hooked View.onAttachedToWindow");
        } catch (Throwable t) {
            log("[ERROR] Failed to hook View.onAttachedToWindow: " + t.getMessage());
        }
    }

    private void dumpViewsAtPoint(View view, float x, float y, int depth) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int vx = location[0];
        int vy = location[1];
        int vw = view.getWidth();
        int vh = view.getHeight();

        if (x >= vx && x <= vx + vw && y >= vy && y <= vy + vh) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            
            String resName = safeGetResourceName(view);
            String visStr = "UNKNOWN";
            if (view.getVisibility() == View.VISIBLE) visStr = "VISIBLE";
            else if (view.getVisibility() == View.INVISIBLE) visStr = "INVISIBLE";
            else if (view.getVisibility() == View.GONE) visStr = "GONE";

            log(indent.toString() + "-> [DEPTH " + depth + "] " + view.getClass().getName() + " | ID: " + resName + " | Rect: (" + vx + ", " + vy + ", " + (vx + vw) + ", " + (vy + vh) + ") | Vis: " + visStr + " | Alpha: " + view.getAlpha() + " | TransY: " + view.getTranslationY());

            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpViewsAtPoint(vg.getChildAt(i), x, y, depth + 1);
                }
            }
        }
    }

    private String safeGetResourceName(View view) {
        if (view.getId() == View.NO_ID || view.getResources() == null) return "NO_ID";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Exception e) {
            return "0x" + Integer.toHexString(view.getId());
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
