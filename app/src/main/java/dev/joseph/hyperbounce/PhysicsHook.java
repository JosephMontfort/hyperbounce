package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
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
        log("[INIT] HyperBounce: Ultimate Smart Shifter (Lag-Free)");
        log("==================================================");

        // 1. DIMENSION SPOOFER: Force video to bottom & fix DM Layouts
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Resources res = (Resources) param.thisObject;
                    try {
                        String name = res.getResourceEntryName((Integer) param.args[0]);
                        if ("tab_bar_height".equals(name)) {
                            param.setResult(0); 
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. GHOST TAB BAR, INJECT UI, & SHIFT OVERLAYS
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());

                        // Hide stock elements, but keep tab_bar's visibility state intact for tracking
                        if ("tab_bar".equals(resName) || "tab_bar_shadow".equals(resName) || "layout_bottom_searchbar".equals(resName)) {
                            if ("tab_bar".equals(resName)) {
                                view.setAlpha(0f);
                                view.setTranslationY(9999f);
                            } else {
                                view.setVisibility(View.GONE);
                            }
                            ViewGroup.LayoutParams lp = view.getLayoutParams();
                            if (lp != null) {
                                lp.height = 0;
                                view.setLayoutParams(lp);
                            }
                        }

                        // Trigger Injection & Lightweight Visibility Tracker
                        if ("layout_container_main_wrapper".equals(resName)) {
                            if (view.getContext() instanceof Activity) {
                                Activity activity = (Activity) view.getContext();
                                view.post(() -> {
                                    try {
                                        int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                                        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                        View stockBar = decorView.findViewById(stockBarId);
                                        
                                        LiquidGlassInjectorKt.injectLiquidGlass(activity, (ViewGroup) view, (ViewGroup) stockBar);

                                        // ULTRA-LIGHTWEIGHT Listener: Only toggles visibility. No recursion!
                                        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                                            View injectedBar = view.findViewWithTag("LiquidGlassInjected");
                                            boolean isTabBarActive = (stockBar != null && stockBar.getVisibility() == View.VISIBLE);
                                            if (injectedBar != null) {
                                                injectedBar.setVisibility(isTabBarActive ? View.VISIBLE : View.GONE);
                                            }
                                        });
                                    } catch (Exception e) {}
                                });
                            }
                        }

                        // Localized Overlay Shifter: NO MORE GLOBAL RECURSION (Kills the lag)
                        if ("clips_item_overlay_component".equals(resName)) {
                            view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                                ViewGroup parent = (ViewGroup) v.getParent();
                                if (parent == null) return;
                                
                                Activity activity = (Activity) v.getContext();
                                int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                                View stockBar = activity.getWindow().getDecorView().findViewById(stockBarId);
                                
                                // Check if we are in the main tabs. If not (DMs/Home), shift resets to 0.
                                boolean isTabBarActive = (stockBar != null && stockBar.getVisibility() == View.VISIBLE);
                                float shiftY = isTabBarActive ? -200.0f : 0.0f; // Increased height to 200
                                
                                // Loop ONLY through the ~10 siblings. Extremely fast.
                                for (int c = 0; c < parent.getChildCount(); c++) {
                                    View sibling = parent.getChildAt(c);
                                    int sibId = sibling.getId();
                                    String sibName = "";
                                    if (sibId != View.NO_ID && sibling.getResources() != null) {
                                        try { sibName = sibling.getResources().getResourceEntryName(sibId); } catch(Exception e){}
                                    }

                                    // Ignore background, gradient, master wrappers, and mute components
                                    if ("clips_bottom_legibility_gradient_component".equals(sibName) ||
                                        "clips_touchable_background".equals(sibName) ||
                                        "clips_item_overlay_component".equals(sibName) ||
                                        "clips_pause_and_mute_component".equals(sibName)) {
                                        continue;
                                    }

                                    // Ignore the scrubber
                                    if (containsScrubber(sibling)) {
                                        continue;
                                    }

                                    if (sibling.getTranslationY() != shiftY) {
                                        sibling.setTranslationY(shiftY);
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 3. THE MARGIN ASSASSIN (Keep video stretched to the bottom)
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
                            if ("layout_container_main".equals(resName) || 
                                "layout_container_main_wrapper".equals(resName) || 
                                "swipeable_tab_view_pager".equals(resName)) {
                                
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
    }

    private boolean containsScrubber(View view) {
        if (view == null) return false;
        int id = view.getId();
        if (id != View.NO_ID && view.getResources() != null) {
            try {
                if ("scrubber".equals(view.getResources().getResourceEntryName(id))) return true;
            } catch (Exception e) {}
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsScrubber(vg.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
