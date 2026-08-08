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

        log("==================================================");
        log("[INIT] HyperBounce: Opaque Bar Extermination");
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
                                    // Force heightMeasureSpec to 0px EXACTLY
                                    int zeroHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                                    param.args[1] = zeroHeightSpec;
                                }
                            } catch (Exception e) {}
                        }
                    }
                );
                log("[SUCCESS] Hooked TouchInterceptorLinearLayout.onMeasure -> 0px forced!");
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

                                // Set system navigation bar background to transparent
                                if (view.getContext() instanceof Activity) {
                                    Activity activity = (Activity) view.getContext();
                                    Window window = activity.getWindow();
                                    if (window != null) {
                                        window.setNavigationBarColor(Color.TRANSPARENT);
                                    }
                                }
                                log("[NUKE] Background cleared & height collapsed for: " + resName);
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
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
