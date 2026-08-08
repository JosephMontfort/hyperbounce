package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Insets;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.contains("instagram")) return;

        log("[INIT] HyperBounce: Void Annihilation Mode Active");

        // 1. DIMENSION SPOOFER: Lie about Instagram's internal tab sizes
        try {
            XposedHelpers.findAndHookMethod(
                Resources.class, 
                "getDimensionPixelSize", 
                int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Resources res = (Resources) param.thisObject;
                        try {
                            String name = res.getResourceEntryName((Integer) param.args[0]);
                            if (name.equals("tab_bar_height") || name.equals("tab_bar_separator_height")) {
                                param.setResult(0);
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {}

        // 2. OS INSETS SPOOFER (The Void Killer): Force Android to report 0px for system bottom paddings
        try {
            // Legacy Insets Interception
            XposedHelpers.findAndHookMethod(
                WindowInsets.class, 
                "getSystemWindowInsetBottom", 
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return 0; 
                    }
                }
            );
        } catch (Throwable t) {}

        try {
            // API 30+ Modern Insets Interception
            XposedHelpers.findAndHookMethod(
                WindowInsets.class, 
                "getInsets", 
                int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int typeMask = (Integer) param.args[0];
                        // If Instagram asks for Navigation Bar or general System Bar insets
                        if ((typeMask & WindowInsets.Type.navigationBars()) != 0 || 
                            (typeMask & WindowInsets.Type.systemBars()) != 0) {
                            
                            Insets original = (Insets) param.getResult();
                            if (original != null && original.bottom > 0) {
                                // Strip the bottom inset completely to stretch the feed
                                param.setResult(Insets.of(original.left, original.top, original.right, 0));
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {}

        // 3. ATTACHMENT HOOK: Nuke Tab Bar & Force Container Margins to 0
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

                            // A. Obliterate the Stock Bar
                            if (resName.equals("tab_bar") || resName.equals("tab_bar_shadow") || resName.equals("layout_bottom_searchbar")) {
                                view.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp != null) {
                                    lp.height = 0;
                                    view.setLayoutParams(lp);
                                }
                            }

                            // B. Forcefully Stretch the Main Containers
                            if (resName.equals("layout_container_main") || 
                                resName.equals("layout_container_main_wrapper") ||
                                resName.equals("layout_container_main_panel") ||
                                resName.equals("swipe_navigation_container")) {

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
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {}

        // 4. ACTIVITY THEME HOOK: Make the system gesture bar fully transparent
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class, 
                "onResume", 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        if (activity.getClass().getName().contains("InstagramMainActivity")) {
                            Window window = activity.getWindow();
                            if (window != null) {
                                window.setNavigationBarColor(Color.TRANSPARENT);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {}
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
