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
        log("[INIT] HyperBounce: Litho Overlay Assassin");
        log("==================================================");

        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());

                        // 1. Ghost the Tab Bar
                        if (resName.equals("tab_bar") || resName.equals("tab_bar_shadow") || resName.equals("layout_bottom_searchbar")) {
                            view.setVisibility(View.GONE);
                            ViewGroup.LayoutParams lp = view.getLayoutParams();
                            if (lp != null) {
                                lp.height = 0;
                                view.setLayoutParams(lp);
                            }
                        }

                        // 2. Trigger Liquid Glass Injection
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

                        // 3. THE LITHO OVERLAY ASSASSIN
                        // Target ONLY the specific overlay elements to push them UP independently of the video
                        String[] pushUpTargets = {
                            "clips_media_info_component",
                            "clips_bottom_legibility_gradient_component",
                            "clips_ufi_component",
                            "clips_item_overlay_component",
                            "pog_root_view",
                            "pog_avatar_view",
                            "avatar_container",
                            "reel_viewer_front_avatar",
                            "reel_viewer_back_avatar",
                            "pog_like_view_stub",
                            "comment_facepile_stub",
                            "comment_facepile_gif_stub",
                            "face_swarm_stub",
                            "floating_send_stub",
                            "interest_banner_send_stub",
                            "interest_banner_reply_bar_stub",
                            "clips_follow_request_footer_stub",
                            "basel_cta_stub",
                            "attribution_bottom_bar_stub",
                            "shop_lab_entry_point_stub",
                            "clips_footer_mask_container"
                        };

                        for (String target : pushUpTargets) {
                            if (resName.equals(target)) {
                                // Shift the pixels and the touch target UP by 180px
                                view.setTranslationY(-180f);
                                
                                // Force it to stay up even if Litho dynamically recalculates the UI
                                view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                                    if (v.getTranslationY() != -180f) {
                                        v.setTranslationY(-180f);
                                    }
                                });
                                break;
                            }
                        }

                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}

        // 4. THE MARGIN ASSASSIN (Keep video stretched to the bottom)
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
                                    if (mlp.bottomMargin > 50) mlp.bottomMargin = 0;
                                }
                            }
                        } catch (Exception e) {}
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
