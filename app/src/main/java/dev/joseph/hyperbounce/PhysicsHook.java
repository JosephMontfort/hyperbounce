package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;
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

        log("[INIT] HyperBounce: Absolute Edge-to-Edge Override");

        // 1. DIMENSION SPOOFER
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

        // 2. THE PADDING INTERCEPTOR (The Silver Bullet)
        // This stops Instagram's OnApplyWindowInsetsListener from dynamically adding bottom padding
        XC_MethodHook paddingHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if (view.getId() == View.NO_ID || view.getResources() == null) return;
                try {
                    String resName = view.getResources().getResourceEntryName(view.getId());
                    if (isMainContainer(resName)) {
                        int bottomPadding = (Integer) param.args[3];
                        if (bottomPadding > 0) {
                            param.args[3] = 0; // Force bottom argument to 0 before execution
                        }
                    }
                } catch (Exception e) {}
            }
        };

        try {
            XposedHelpers.findAndHookMethod(View.class, "setPadding", int.class, int.class, int.class, int.class, paddingHook);
            XposedHelpers.findAndHookMethod(View.class, "setPaddingRelative", int.class, int.class, int.class, int.class, paddingHook);
        } catch (Throwable t) {
            log("[ERROR] Padding interceptor failed: " + t.getMessage());
        }

        // 3. THE MARGIN INTERCEPTOR
        // Stops Instagram from dynamically altering LayoutParams to push the feed up
        try {
            XposedHelpers.findAndHookMethod(View.class, "setLayoutParams", ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;
                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        if (isMainContainer(resName)) {
                            ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[0];
                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                if (mlp.bottomMargin > 0) {
                                    mlp.bottomMargin = 0; // Strip bottom margin dynamically
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 4. ATTACHMENT HOOK (Tab Bar Annihilation)
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
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 5. TRUE EDGE-TO-EDGE ENFORCER
        // Forces the Activity window to draw underneath the system navigation pill area
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    if (activity.getClass().getName().contains("MainActivity") || activity.getClass().getName().contains("MainTabActivity")) {
                        Window window = activity.getWindow();
                        if (window != null) {
                            window.setNavigationBarColor(Color.TRANSPARENT);
                            
                            View decorView = window.getDecorView();
                            int flags = decorView.getSystemUiVisibility();
                            // Tell Android we want to draw in the navigation bar space
                            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                            decorView.setSystemUiVisibility(flags);

                            // Modern API 30+ Edge-to-Edge
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                window.setDecorFitsSystemWindows(false);
                            }
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

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
