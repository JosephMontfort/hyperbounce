package dev.joseph.hyperbounce;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
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

        XposedBridge.log("[HyperBounce] [INIT] Absolute Layout Enforcer Armed");

        // 1. THE INSETS ASSASSIN: Blind Instagram to the system navigation bar
        try {
            XposedHelpers.findAndHookMethod(View.class, "dispatchApplyWindowInsets", WindowInsets.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;
                    
                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        if (resName.equals("layout_container_center_right_coordinator_layout") || 
                            resName.equals("swipe_navigation_container") || 
                            resName.equals("layout_container_main_panel")) {
                            
                            // Tell the layout engine the insets have already been consumed
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                param.setResult(WindowInsets.CONSUMED);
                            } else {
                                WindowInsets insets = (WindowInsets) param.args[0];
                                param.setResult(insets.consumeSystemWindowInsets());
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. TRUE EDGE-TO-EDGE + THE PREDRAW WATCHDOG
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    if (!activity.getClass().getName().contains("MainActivity") && !activity.getClass().getName().contains("MainTabActivity")) return;

                    Window window = activity.getWindow();
                    if (window != null) {
                        // Strip Android OS window protections
                        window.setNavigationBarColor(Color.TRANSPARENT);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }

                        final View decorView = window.getDecorView();
                        int flags = decorView.getSystemUiVisibility();
                        flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                        flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        decorView.setSystemUiVisibility(flags);

                        // Attach the enforcer. It runs every single time the UI tries to update.
                        if (decorView.getTag(activity.hashCode()) == null) {
                            decorView.setTag(activity.hashCode(), true);

                            decorView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                @Override
                                public boolean onPreDraw() {
                                    enforceLayout(activity, decorView);
                                    return true; // Allow the draw pass to continue
                                }
                            });
                        }
                    }
                }
            });
        } catch (Throwable t) {}
    }

    private void enforceLayout(Activity activity, View decorView) {
        // A. EXTERMINATE THE TAB BAR TARGETS
        String[] barIds = {"tab_bar", "tab_bar_shadow", "layout_bottom_searchbar", "ls_vertical_nav_bar_stub"};
        for (String barId : barIds) {
            int resId = activity.getResources().getIdentifier(barId, "id", activity.getPackageName());
            if (resId != 0) {
                View bar = decorView.findViewById(resId);
                if (bar != null) {
                    if (bar.getVisibility() != View.GONE) bar.setVisibility(View.GONE);
                    ViewGroup.LayoutParams lp = bar.getLayoutParams();
                    if (lp != null && lp.height != 0) {
                        lp.height = 0;
                        bar.setLayoutParams(lp);
                    }
                }
            }
        }

        // B. BRUTE-FORCE STRETCH THE MAIN CONTAINERS
        String[] containerIds = {
            "layout_container_main",
            "layout_container_main_wrapper",
            "layout_container_main_panel",
            "swipe_navigation_container",
            "layout_container_center_right_coordinator_layout",
            "bottom_sheet_camera_container"
        };

        for (String cId : containerIds) {
            int resId = activity.getResources().getIdentifier(cId, "id", activity.getPackageName());
            if (resId != 0) {
                View container = decorView.findViewById(resId);
                if (container != null) {
                    boolean needsLayoutUpdate = false;
                    ViewGroup.LayoutParams lp = container.getLayoutParams();
                    
                    // Nuke any bottom margins dynamically generated by Coordinator/Constraint layouts
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                        if (mlp.bottomMargin != 0) {
                            mlp.bottomMargin = 0;
                            needsLayoutUpdate = true;
                        }
                    }
                    
                    // Force the height to stretch
                    if (lp != null && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        needsLayoutUpdate = true;
                    }

                    if (needsLayoutUpdate) {
                        container.setLayoutParams(lp);
                    }

                    // Nuke rogue padding
                    if (container.getPaddingBottom() != 0) {
                        container.setPadding(container.getPaddingLeft(), container.getPaddingTop(), container.getPaddingRight(), 0);
                    }
                }
            }
        }
    }
}
