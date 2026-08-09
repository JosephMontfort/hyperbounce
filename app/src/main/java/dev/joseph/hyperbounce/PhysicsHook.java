package dev.joseph.hyperbounce;

import android.app.Activity;
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
        log("[INIT] HyperBounce: Master UI Injector");
        log("==================================================");

        // 1. GHOST THE TAB BAR & INJECT LIQUID UI
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
                                    } catch (Exception e) {}
                                });
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 2. ISOLATED MARGIN ASSASSIN (Fixes Reels Void & Protects DMs)
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
                            
                            // Target ONLY the Reels containers. 
                            // This stretches the video to the bottom but leaves the DM screen entirely alone!
                            if (resName.equals("clips_viewer_container") || resName.equals("root_clips_layout")) {
                                ViewGroup.LayoutParams lp = child.getLayoutParams();
                                if (lp instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                    if (mlp.bottomMargin > 0) mlp.bottomMargin = 0;
                                }
                            }
                        } catch (Exception e) {}
                    }
                }
            );
        } catch (Throwable t) {}

        // 3. THE LITHO & COMPOSE PIXEL SHIFTER
        try {
            XposedHelpers.findAndHookMethod(View.class, "onLayout", boolean.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    float targetShift = -220f; // Sits perfectly right above the Liquid Nav Bar
                    
                    // A. Shift the Legacy Litho Components (Captions, Author, Follow Button, Like/Comment rail)
                    if (view.getId() != View.NO_ID && view.getResources() != null) {
                        try {
                            String resName = view.getResources().getResourceEntryName(view.getId());
                            if (resName.equals("clips_media_info_component") || 
                                resName.equals("clips_ufi_component") || 
                                resName.equals("clips_bottom_legibility_gradient_component")) {
                                
                                if (view.getTranslationY() != targetShift) view.setTranslationY(targetShift);
                            }
                        } catch (Exception e) {}
                    }

                    // B. Shift the Modern Compose Components (Progress Bar, Floating Avatars, Interactions)
                    String className = view.getClass().getSimpleName();
                    if (className.equals("ComposeView")) {
                        View parent = (View) view.getParent();
                        if (parent != null && parent.getClass().getSimpleName().equals("RoundedCornerFrameLayout")) {
                            if (view.getTranslationY() != targetShift) view.setTranslationY(targetShift);
                        }
                    }
                }
            });
        } catch (Throwable t) {}
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
