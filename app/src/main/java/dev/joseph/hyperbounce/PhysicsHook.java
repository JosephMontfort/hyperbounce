package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

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
        log("[INIT] HyperBounce: Liquid Glass & Litho Spoofer");
        log("==================================================");

        // 1. DIMENSION SPOOFER: Force the video to the bottom & fix DM Layouts
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Resources res = (Resources) param.thisObject;
                    try {
                        String name = res.getResourceEntryName((Integer) param.args[0]);
                        // Telling Instagram the tab bar doesn't exist naturally stretches the 
                        // video to the bottom and restores the broken DM Notes layout.
                        if (name.equals("tab_bar_height")) {
                            param.setResult(0); 
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. GHOST THE TAB BAR & INJECT UI
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

                        if (resName.equals("layout_container_main_wrapper")) {
                            if (view.getContext() instanceof Activity) {
                                Activity activity = (Activity) view.getContext();
                                view.post(() -> {
                                    try {
                                        int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                                        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                        ViewGroup stockBar = decorView.findViewById(stockBarId);
                                        LiquidGlassInjectorKt.injectLiquidGlass(activity, (ViewGroup) view, stockBar);
                                        
                                        // 3. THE LITHO OVERLAY ASSASSIN
                                        // Continuously scans and pushes the Reels text/buttons up by 240px (~80dp)
                                        decorView.getViewTreeObserver().addOnGlobalLayoutListener(() -> shiftOverlays(decorView));
                                        
                                    } catch (Exception e) {}
                                });
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}
    }

    private void shiftOverlays(ViewGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            
            boolean isTarget = false;
            if (child.getId() != View.NO_ID && child.getResources() != null) {
                try {
                    String name = child.getResources().getResourceEntryName(child.getId());
                    if (name.equals("clips_media_info_component") || 
                        name.equals("clips_bottom_legibility_gradient_component") || 
                        name.equals("clips_item_overlay_component") ||
                        name.equals("clips_ufi_component") ||
                        name.equals("pog_root_view") ||
                        name.equals("pog_avatar_view") ||
                        name.equals("avatar_container")) {
                        isTarget = true;
                    }
                } catch (Exception e) {}
            }
            
            if (isTarget) {
                // Physically lifts the rendered component and its touch targets UP 
                if (child.getTranslationY() != -240f) {
                    child.setTranslationY(-240f);
                }
            } else if (child instanceof ViewGroup) {
                // Only recurse if we didn't translate this node (prevents double-shifting)
                shiftOverlays((ViewGroup) child);
            }
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
