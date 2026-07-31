package dev.joseph.hyperbounce;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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

                        // Lock down the stock bar's background and native children so WhatsApp can't revert it
                        stockBar.getViewTreeObserver().addOnPreDrawListener(() -> {
                            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
                            if (menuContainer != null && menuContainer.getVisibility() != View.GONE) {
                                menuContainer.setVisibility(View.GONE);
                            }
                            stockBar.setBackgroundColor(Color.TRANSPARENT);
                            return true;
                        });
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });
    }

    private void executeUIHeist(ViewGroup stockBar) {
        try {
            if (stockBar.findViewWithTag("HyperGlassContainer") != null) return; 

            Context context = stockBar.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            
            // Allow our pill to stretch outside the bounds natively
            stockBar.setClipChildren(false);
            stockBar.setClipToPadding(false);
            ViewGroup parent = (ViewGroup) stockBar.getParent();
            if (parent != null) {
                parent.setClipChildren(false);
                parent.setClipToPadding(false);
            }

            // Extract Tabs
            List<ExtractedTab> extractedTabs = new ArrayList<>();
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer != null) {
                for (int i = 0; i < menuContainer.getChildCount(); i++) {
                    View stockTab = menuContainer.getChildAt(i);
                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);
                    if (nativeIcon == null || nativeText == null) continue;
                    
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    String label = nativeText.getText().toString();
                    extractedTabs.add(new ExtractedTab(label, clonedDrawable, i, stockTab));
                }
            }

            // --- MASTER CONTAINER ---
            FrameLayout container = new FrameLayout(context);
            container.setTag("HyperGlassContainer");
            container.setClipChildren(false);

            // --- 1. THE NATIVE GLASS BLUR LAYER ---
            View glassView = new View(context);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(1000f);
            
            boolean isDark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            // Highly transparent base to let the blur shine
            bg.setColor(Color.parseColor(isDark ? "#331F2C34" : "#33FFFFFF"));
            bg.setStroke(2, Color.parseColor(isDark ? "#20FFFFFF" : "#15000000"));
            glassView.setBackground(bg);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                glassView.setBackgroundBlurRadius(80); // NATIVE HARDWARE BLUR
            }

            // Fixed Premium Width (260dp)
            int barWidth = (int)(260 * density);
            int glassHeight = (int)(56 * density);
            
            FrameLayout.LayoutParams glassLp = new FrameLayout.LayoutParams(barWidth, glassHeight);
            glassLp.gravity = Gravity.CENTER;
            container.addView(glassView, glassLp);

            // --- 2. THE COMPOSE UI LAYER (Overflows the Glass) ---
            View composeFloatingBar = ComposeBridge.createFloatingBar(context, stockBar, extractedTabs, index -> {
                extractedTabs.get(index).getStockView().performClick();
                return Unit.INSTANCE;
            });

            // Compose view is 80dp tall so the jelly pill can stretch vertically outside the 56dp glass
            int composeHeight = (int)(80 * density);
            FrameLayout.LayoutParams composeLp = new FrameLayout.LayoutParams(barWidth, composeHeight);
            composeLp.gravity = Gravity.CENTER;
            container.addView(composeFloatingBar, composeLp);

            // --- 3. INJECT INTO WHATSAPP ---
            // Adding this inside WDSBottomBar means it inherently follows WhatsApp's visibility rules (e.g. Search screen)
            ViewGroup.LayoutParams containerLp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(100 * density)
            );
            stockBar.addView(container, containerLp);

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

    private TextView findTextView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) return (TextView) v;
            if (v instanceof ViewGroup) {
                TextView tv = findTextView((ViewGroup) v);
                if (tv != null) return tv;
            }
        }
        return null;
    }
}
