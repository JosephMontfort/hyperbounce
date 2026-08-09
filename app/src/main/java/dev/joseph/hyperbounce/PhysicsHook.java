package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    // CACHE: Directly tracks the active master wrappers. Kills 99.9% of the CPU workload.
    private static final WeakHashMap<ViewGroup, Boolean> sOverlayParents = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.contains("instagram")) return;

        log("==================================================");
        log("[INIT] HyperBounce: Zero-Lag Smart Shifter Active");
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

        // 2. GHOST TAB BAR, INJECT UI, & CACHE PARENTS
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());

                        // Cache the exact parent holding the overlays to bypass recursive scanning
                        if ("clips_item_overlay_component".equals(resName)) {
                            View parent = (View) view.getParent();
                            if (parent instanceof ViewGroup) {
                                sOverlayParents.put((ViewGroup) parent, true);
                            }
                        }

                        // Hide stock elements, but keep tab_bar's visibility state intact for context checking
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

                        // Trigger Injection & Context-Aware Shift
                        if ("layout_container_main_wrapper".equals(resName)) {
                            if (view.getContext() instanceof Activity) {
                                Activity activity = (Activity) view.getContext();
                                view.post(() -> {
                                    try {
                                        int stockBarId = activity.getResources().getIdentifier("tab_bar", "id", activity.getPackageName());
                                        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                        View stockBar = decorView.findViewById(stockBarId);
                                        
                                        LiquidGlassInjectorKt.injectLiquidGlass(activity, (ViewGroup) view, (ViewGroup) stockBar);

                                        // Lightweight listener targeting the cache
                                        decorView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                                            View injectedBar = view.findViewWithTag("LiquidGlassInjected");
                                            
                                            // STRICT CONTEXT CHECK: Are we explicitly in the dedicated Reels Tab?
                                            boolean isReelsTabActive = false;
                                            if (stockBar != null && stockBar.getVisibility() == View.VISIBLE) {
                                                int clipsTabId = activity.getResources().getIdentifier("clips_tab", "id", activity.getPackageName());
                                                View clipsTab = stockBar.findViewById(clipsTabId);
                                                if (clipsTab != null && (clipsTab.isSelected() || clipsTab.isActivated())) {
                                                    isReelsTabActive = true;
                                                }
                                            }

                                            // Hide the Liquid Bar entirely if opened from Home Feed or DMs
                                            if (injectedBar != null) {
                                                injectedBar.setVisibility(isReelsTabActive ? View.VISIBLE : View.GONE);
                                            }

                                            // Determine target shift (-200px if active, 0px if viewing via DM/Home)
                                            float shiftY = isReelsTabActive ? -200.0f : 0.0f; 

                                            // INSTANT O(1) SHIFT: Loop strictly through the cached parents
                                            for (ViewGroup overlayParent : sOverlayParents.keySet()) {
                                                if (overlayParent != null && overlayParent.isAttachedToWindow()) {
                                                    for (int c = 0; c < overlayParent.getChildCount(); c++) {
                                                        View sibling = overlayParent.getChildAt(c);
                                                        int sibId = sibling.getId();
                                                        String sibName = "";
                                                        if (sibId != View.NO_ID && sibling.getResources() != null) {
                                                            try { sibName = sibling.getResources().getResourceEntryName(sibId); } catch(Exception e){}
                                                        }

                                                        // Ignore standard backgrounds/wrappers
                                                        if ("clips_bottom_legibility_gradient_component".equals(sibName) ||
                                                            "clips_touchable_background".equals(sibName) ||
                                                            "clips_item_overlay_component".equals(sibName) ||
                                                            "clips_pause_and_mute_component".equals(sibName)) {
                                                            continue;
                                                        }

                                                        // Ignore the scrubber
                                                        if (containsScrubber(sibling, 0)) {
                                                            continue;
                                                        }

                                                        // Safely shift Captions, UFIs, Avatars, and Anonymous Buttons
                                                        if (sibling.getTranslationY() != shiftY) {
                                                            sibling.setTranslationY(shiftY);
                                                        }
                                                    }
                                                }
                                            }
                                        });
                                    } catch (Exception e) {}
                                });
                            }
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

    // Depth-limited scrubber check prevents recursion lag
    private boolean containsScrubber(View view, int depth) {
        if (view == null || depth > 4) return false; 
        int id = view.getId();
        if (id != View.NO_ID && view.getResources() != null) {
            try {
                if ("scrubber".equals(view.getResources().getResourceEntryName(id))) return true;
            } catch (Exception e) {}
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsScrubber(vg.getChildAt(i), depth + 1)) return true;
            }
        }
        return false;
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
