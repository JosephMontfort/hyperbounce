package dev.joseph.hyperbounce;

import android.util.Log;
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
        log("[INIT] HyperBounce: The ViewPager2 Assassin");
        log("==================================================");

        // 1. GHOST THE TAB BAR
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

        // 2. THE 95px MARGIN ASSASSIN (Intercept setLayoutParams)
        try {
            XposedHelpers.findAndHookMethod(View.class, "setLayoutParams", ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        
                        // We caught the ViewPager2! Strip the margin from it and its sibling.
                        if (resName.equals("layout_container_main") || 
                            resName.equals("layout_container_main_wrapper") || 
                            resName.equals("swipeable_tab_view_pager")) {
                            
                            ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[0];
                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                if (mlp.bottomMargin > 50) {
                                    mlp.bottomMargin = 0;
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 3. THE 95px MARGIN ASSASSIN (Intercept measureChildWithMargins)
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
                                    if (mlp.bottomMargin > 50) {
                                        mlp.bottomMargin = 0; // Strip it from memory before parent calculates
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {
            log("[ERROR] Margin Assassin failed: " + t.getMessage());
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
