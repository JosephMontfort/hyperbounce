package dev.joseph.hyperbounce;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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

        XposedBridge.log("[HyperBounce] [INIT] The Global Layout Enforcer Armed");

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    if (!activity.getClass().getName().contains("MainActivity") && !activity.getClass().getName().contains("MainTabActivity")) {
                        return;
                    }

                    Window window = activity.getWindow();
                    if (window != null) {
                        // 1. Edge-to-Edge Window Flags
                        window.setNavigationBarColor(Color.TRANSPARENT);
                        View decorView = window.getDecorView();
                        int flags = decorView.getSystemUiVisibility();
                        flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                        flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        decorView.setSystemUiVisibility(flags);

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }

                        // 2. The Unstoppable Global Layout Watchdog
                        // Tag checks ensure we only attach one listener per activity instance
                        if (decorView.getTag(activity.hashCode()) == null) {
                            decorView.setTag(activity.hashCode(), true);
                            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    enforceNoMarginsAndKillTabBar(activity, decorView);
                                }
                            });
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Log.e("HyperBounce", "[ERROR] Enforcer Hook failed: " + t.getMessage());
        }
    }

    private void enforceNoMarginsAndKillTabBar(Activity activity, View decorView) {
        // 1. EXTERMINATE THE TAB BAR DYNAMICALLY
        String[] barIds = {"tab_bar", "tab_bar_shadow", "layout_bottom_searchbar"};
        for (String barIdName : barIds) {
            int barResId = activity.getResources().getIdentifier(barIdName, "id", activity.getPackageName());
            if (barResId != 0) {
                View bar = decorView.findViewById(barResId);
                if (bar != null) {
                    if (bar.getVisibility() != View.GONE) {
                        bar.setVisibility(View.GONE);
                    }
                    ViewGroup.LayoutParams lp = bar.getLayoutParams();
                    if (lp != null && lp.height != 0) {
                        lp.height = 0;
                        bar.setLayoutParams(lp);
                    }
                }
            }
        }

        // 2. STRIP ROGUE MARGINS FROM ALL MAIN CONTAINERS
        String[] targetIds = {
            "layout_container_main",
            "layout_container_main_wrapper",
            "layout_container_main_panel",
            "swipe_navigation_container",
            "layout_container_center_right_coordinator_layout",
            "bottom_sheet_camera_container"
        };

        for (String idName : targetIds) {
            int resId = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            if (resId != 0) {
                View target = decorView.findViewById(resId);
                if (target != null) {
                    // Kill Margins
                    ViewGroup.LayoutParams lp = target.getLayoutParams();
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                        if (mlp.bottomMargin > 0) {
                            mlp.bottomMargin = 0;
                            target.setLayoutParams(mlp);
                        }
                    }

                    // Kill Paddings
                    if (target.getPaddingBottom() > 0) {
                        target.setPadding(target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(), 0);
                    }
                    
                    // Kill Translation Y Shifts
                    if (target.getTranslationY() < 0) {
                        target.setTranslationY(0);
                    }
                }
            }
        }
    }
}
