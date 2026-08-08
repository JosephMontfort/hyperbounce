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
        log("[INIT] HyperBounce: Targeted Hollow Out Protocol");
        log("==================================================");

        // 1. GHOST THE TAB BAR
        // This ensures the buttons and icons are physically removed from the screen
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
                            log("[GHOST] Exterminated: " + resName);
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {
            log("[ERROR] Ghost hook failed: " + t.getMessage());
        }

        // 2. THE 96px MARGIN ASSASSIN
        // Targets the exact rogue layout modification caught by our Snitch
        try {
            XposedHelpers.findAndHookMethod(View.class, "setLayoutParams", ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view.getId() == View.NO_ID || view.getResources() == null) return;

                    try {
                        String resName = view.getResources().getResourceEntryName(view.getId());
                        
                        // We ONLY target the main wrappers, ignoring bottom_sheet_camera_container to prevent freezes
                        if (resName.equals("layout_container_main") || resName.equals("layout_container_main_wrapper")) {
                            ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[0];
                            
                            if (lp instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                                
                                // Instagram sets this to ~95px or 96px. 
                                // We strip it if it's large, but ignore small margins to keep the UI stable.
                                if (mlp.bottomMargin > 50) {
                                    log("[ASSASSIN] Intercepted MarginBot: " + mlp.bottomMargin + " on " + resName + " -> Forced to 0");
                                    mlp.bottomMargin = 0;
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {
            log("[ERROR] Margin Assassin failed: " + t.getMessage());
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
