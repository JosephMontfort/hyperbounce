package dev.joseph.hyperbounce;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.contains("instagram")) return;

        log("[*] Target acquired: " + lpparam.packageName);

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
                            String entryName = view.getResources().getResourceEntryName(view.getId());

                            // 1. The Ghosting Maneuver: Banish the stock bar and its shadow
                            if (entryName.equals("tab_bar") || entryName.equals("tab_bar_shadow")) {
                                log("[+] Intercepted and Banished: " + entryName);
                                
                                view.setVisibility(View.GONE);
                                view.setTranslationY(5000f);
                                view.setAlpha(0f);
                                view.setClickable(false);
                                view.setEnabled(false);

                                // 2. The Heist: Inject the Liquid Glass UI over the void
                                if (entryName.equals("tab_bar") && view instanceof ViewGroup) {
                                    ViewGroup stockBar = (ViewGroup) view;
                                    if (stockBar.getContext() instanceof Activity) {
                                        Activity activity = (Activity) stockBar.getContext();
                                        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                                        
                                        // Post to ensure layout is ready
                                        stockBar.post(() -> executeUIHeist(activity, decorView, stockBar));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore resource lookup failures for obfuscated IDs
                        }
                    }
                }
            );
            log("[SUCCESS] Payload armed on View.onAttachedToWindow");
        } catch (Throwable t) {
            log("[ERROR] Failed to hook onAttachedToWindow: " + t.getMessage());
        }
    }

    private void executeUIHeist(Activity activity, ViewGroup rootGroup, ViewGroup stockBar) {
        try {
            // Prevent double injection
            if (rootGroup.findViewWithTag("HyperFloatingBar") != null) return;

            float density = activity.getResources().getDisplayMetrics().density;
            int displayWidth = activity.getResources().getDisplayMetrics().widthPixels;

            int barWidth = (int) (displayWidth * 0.68f);
            int barHeight = (int) (64 * density);
            int marginBot = (int) (28 * density);

            // Construct the Liquid Glass Pill
            FrameLayout customBar = new FrameLayout(activity);
            customBar.setTag("HyperFloatingBar");
            
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(barWidth, barHeight);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.bottomMargin = marginBot;
            customBar.setLayoutParams(params);

            boolean isDark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            customBar.setBackgroundColor(Color.parseColor(isDark ? "#6018222A" : "#60FFFFFF"));

            // Apply API 31+ Hardware Blur
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                customBar.setRenderEffect(RenderEffect.createBlurEffect(35f * density, 35f * density, Shader.TileMode.CLAMP));
            }

            // Clip to pill shape
            customBar.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
                }
            });
            customBar.setClipToOutline(true);

            // Temporarily mapping a tap on the new bar to trigger the 'Reels/Clips' tab on the hidden stock bar
            customBar.setOnClickListener(v -> {
                try {
                    int clipsTabId = activity.getResources().getIdentifier("clips_tab", "id", activity.getPackageName());
                    if (clipsTabId != 0) {
                        View clipsTab = stockBar.findViewById(clipsTabId);
                        if (clipsTab != null) clipsTab.performClick();
                    }
                } catch (Exception e) {
                    log("Failed to trigger stock tab click");
                }
            });

            rootGroup.setClipChildren(false);
            rootGroup.setClipToPadding(false);
            rootGroup.addView(customBar);

            log("[*] Custom iOS Liquid Glass Bar Injected Successfully!");
        } catch (Exception e) {
            log("Heist Exception: " + e.getMessage());
        }
    }

    private void log(String msg) {
        Log.e("HyperBounce-Tracer", msg);
        XposedBridge.log("[HyperBounce-Tracer] " + msg);
    }
}
