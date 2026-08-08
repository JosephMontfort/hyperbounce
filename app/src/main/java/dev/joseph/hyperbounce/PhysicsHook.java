package dev.joseph.hyperbounce;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.instagram.android";
    private static final float OVERLAY_LIFT_DP = 88f;

    private static final Set<String> HIDDEN_TAB_BAR_NAMES = new HashSet<>(Arrays.asList(
            "tab_bar",
            "tab_bar_shadow",
            "layout_bottom_searchbar"
    ));

    /**
     * Root containers / stubs that should sit above the floating nav bar.
     * Parents are lifted first; descendants are skipped if an ancestor already moved.
     */
    private static final Set<String> LIFTED_OVERLAY_NAMES = new HashSet<>(Arrays.asList(
            "clips_item_overlay_component",
            "clips_media_info_component",
            "clips_ufi_component",
            "clips_bottom_legibility_gradient_component",
            "clips_footer_mask_container",
            "clips_self_view_footer_navbar_container",
            "clips_follow_request_footer_stub",
            "clips_reply_bar_stub",
            "floating_send_stub",
            "interest_banner_send_stub",
            "interest_banner_reply_bar_stub",
            "basel_cta_stub",
            "attribution_bottom_bar_stub",
            "shop_lab_entry_point_stub",
            "clips_creator_pick_bar_stub",
            "clips_comment_composer_stub",
            "clips_voice_instructions_stub",
            "reel_question_response_privacy_nux_stub",
            "clips_bottom_space_filler_stub",
            "pog_root_view",
            "pog_avatar_view",
            "avatar_container",
            "heart_emitter_view"
    ));

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        log("==================================================");
        log("[INIT] HyperBounce: Floating Nav + Overlay Lift");
        log("==================================================");

        hookAttachmentPhase();
        hookMeasurePhase();
    }

    private void hookAttachmentPhase() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (view.getResources() == null || view.getId() == View.NO_ID) return;

                    String resName = safeResourceName(view);
                    if (resName == null) return;

                    try {
                        hideStockBottomBar(view, resName);
                        maybeInjectLiquidBar(view, resName);
                        maybeLiftOverlay(view, resName);
                    } catch (Throwable t) {
                        log("[WARN] attachment hook failed for " + resName + ": " + t.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            log("[ERROR] attach hook failed: " + t.getMessage());
        }
    }

    private void hookMeasurePhase() {
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "measureChildWithMargins",
                    View.class, int.class, int.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            View child = (View) param.args[0];
                            if (child.getResources() == null || child.getId() == View.NO_ID) return;

                            String resName = safeResourceName(child);
                            if (resName == null) return;

                            try {
                                // Keep the content surface flush to the bottom edge.
                                if ("layout_container_main".equals(resName)
                                        || "layout_container_main_wrapper".equals(resName)
                                        || "swipeable_tab_view_pager".equals(resName)) {
                                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                        if (mlp.bottomMargin > 0) {
                                            mlp.bottomMargin = 0;
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log("[WARN] measure hook failed for " + resName + ": " + t.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            log("[ERROR] measure hook failed: " + t.getMessage());
        }
    }

    private void hideStockBottomBar(View view, String resName) {
        if (!HIDDEN_TAB_BAR_NAMES.contains(resName)) return;

        view.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            view.setLayoutParams(lp);
        }
    }

    private void maybeInjectLiquidBar(View view, String resName) {
        if (!"layout_container_main_wrapper".equals(resName)) return;
        if (!(view.getContext() instanceof Activity)) return;

        Activity activity = (Activity) view.getContext();
        view.post(() -> {
            try {
                int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                ViewGroup stockBar = decorView.findViewById(stockBarId);
                LiquidGlassInjectorKt.injectLiquidGlass(activity, (ViewGroup) view, stockBar);
            } catch (Throwable t) {
                log("[ERROR] Liquid bar injection failed: " + t.getMessage());
            }
        });
    }

    private void maybeLiftOverlay(View view, String resName) {
        if (!LIFTED_OVERLAY_NAMES.contains(resName)) return;
        if (hasLiftedAncestor(view)) return;

        int liftPx = dpToPx(view, OVERLAY_LIFT_DP);
        Object tag = view.getTag(R.id.hyperbounce_overlay_lift_applied);
        int previousLiftPx = tag instanceof Integer ? (Integer) tag : 0;

        if (previousLiftPx == liftPx) return;

        // Preserve any existing translationY by applying only the delta between old and new lift.
        view.setTranslationY(view.getTranslationY() + (previousLiftPx - liftPx));
        view.setTag(R.id.hyperbounce_overlay_lift_applied, liftPx);
    }

    private boolean hasLiftedAncestor(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View ancestor = (View) parent;
            Object tag = ancestor.getTag(R.id.hyperbounce_overlay_lift_applied);
            if (tag instanceof Integer && ((Integer) tag) > 0) {
                return true;
            }
            parent = ancestor.getParent();
        }
        return false;
    }

    private int dpToPx(View view, float dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    private String safeResourceName(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable t) {
            return null;
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
