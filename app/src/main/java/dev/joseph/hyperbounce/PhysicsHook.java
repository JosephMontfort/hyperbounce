package dev.joseph.hyperbounce;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import dev.joseph.hyperbounce.ui.ComposeBridge;
import dev.joseph.hyperbounce.ui.ExtractedTab;
import kotlin.Unit;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        stockBar.post(() -> executeUIHeist(stockBar));
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });
    }

    private void executeUIHeist(ViewGroup stockBar) {
        try {
            ViewGroup rootView = (ViewGroup) stockBar.getRootView();
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; 

            Context context = stockBar.getContext();
            float density = context.getResources().getDisplayMetrics().density;

            // 1. Extract Tabs and STRIP Native Ripples (Fixes the ugly circular flash)
            List<ExtractedTab> extractedTabs = new ArrayList<>();
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            
            if (menuContainer != null) {
                for (int i = 0; i < menuContainer.getChildCount(); i++) {
                    View stockTab = menuContainer.getChildAt(i);
                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    if (nativeIcon == null) continue;
                    
                    // Kill the native Android ripple effect immediately
                    nativeIcon.setBackground(null);
                    stockTab.setBackground(null);
                    
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    extractedTabs.add(new ExtractedTab("Tab", clonedDrawable, i, stockTab));
                }
            }

            // 2. Build the Compose Bar (Injecting directly into RootView for true transparency)
            View composeFloatingBar = ComposeBridge.createFloatingBar(context, rootView, extractedTabs, index -> {
                extractedTabs.get(index).getStockView().performClick();
                return Unit.INSTANCE;
            });
            composeFloatingBar.setTag("HyperFloatingBar");

            // 3. Compact Premium Sizing (240dp max width, avoids Meta AI FAB)
            int barWidth = (int)(240 * density);
            int barHeight = (int)(80 * density); // Taller than the glass to allow vertical jelly overflow
            
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(barWidth, barHeight);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = (int)(30 * density); // Raised slightly above the bottom edge
            rootView.addView(composeFloatingBar, lp);

            // 4. The Visibility Sync & Stock Bar Nuke
            stockBar.getViewTreeObserver().addOnPreDrawListener(() -> {
                // Completely erase stock visuals without triggering layout passes
                stockBar.setBackground(null);
                stockBar.setElevation(0f);
                if (menuContainer != null && menuContainer.getVisibility() != View.INVISIBLE) {
                    menuContainer.setVisibility(View.INVISIBLE);
                }

                // Sync custom bar with stock bar (Fixes the Search Screen Bug)
                if (stockBar.isShown() && stockBar.getVisibility() == View.VISIBLE) {
                    if (composeFloatingBar.getVisibility() != View.VISIBLE) {
                        composeFloatingBar.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (composeFloatingBar.getVisibility() != View.GONE) {
                        composeFloatingBar.setVisibility(View.GONE);
                    }
                }
                return true;
            });

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.getMessage());
        }
    }

    private ImageView findImageView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof ImageView) return (ImageView) v;
            if (v instanceof ViewGroup) {
                ImageView iv = findImageView((ViewGroup) v);
                if (iv != null) return iv;
            }
        }
        return null;
    }
}
