package dev.joseph.hyperbounce;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    private List<LinearLayout> customTabs = new ArrayList<>();
    private int currentActiveIndex = 0;
    private boolean isDarkMode = true;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        // 1. The Ultimate Nuke: Intercept Layout Calculations
        XposedBridge.hookAllMethods(wdsBottomBarClass, "onMeasure", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View stockBar = (View) param.thisObject;
                
                // Force the stock bar height to 0
                ViewGroup.LayoutParams lp = stockBar.getLayoutParams();
                if (lp != null && lp.height != 0) {
                    lp.height = 0;
                    stockBar.setLayoutParams(lp);
                }
                stockBar.setVisibility(View.GONE);

                // Force the parent container height to 0
                View parent = (View) stockBar.getParent();
                if (parent != null) {
                    ViewGroup.LayoutParams parentLp = parent.getLayoutParams();
                    if (parentLp != null && parentLp.height != 0) {
                        parentLp.height = 0;
                        parent.setLayoutParams(parentLp);
                    }
                    parent.setVisibility(View.GONE);
                }
            }
        });

        // 2. UI Heist Trigger
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
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; // Prevent duplicates

            Context context = stockBar.getContext();
            customTabs.clear();

            // --- THEME DETECTION ---
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            String bgColor = isDarkMode ? "#1F2C34" : "#FFFFFF";
            String strokeColor = isDarkMode ? "#374248" : "#E0E0E0";

            // Build the Custom Pill
            LinearLayout customBar = new LinearLayout(context);
            customBar.setTag("HyperFloatingBar");
            customBar.setOrientation(LinearLayout.HORIZONTAL);
            customBar.setGravity(Gravity.CENTER);
            customBar.setClickable(true);
            customBar.setFocusable(true);

            GradientDrawable pillShape = new GradientDrawable();
            pillShape.setShape(GradientDrawable.RECTANGLE);
            pillShape.setCornerRadius(100f);
            pillShape.setColor(Color.parseColor(bgColor));
            pillShape.setStroke(2, Color.parseColor(strokeColor));
            customBar.setBackground(pillShape);
            customBar.setElevation(80f);

            // Extract Native Icons & Inject
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer != null) {
                int tabCount = menuContainer.getChildCount();
                
                for (int i = 0; i < tabCount; i++) {
                    final int tabIndex = i;
                    View stockTab = menuContainer.getChildAt(i);
                    
                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);

                    if (nativeIcon == null || nativeText == null) continue;

                    LinearLayout customTab = new LinearLayout(context);
                    customTab.setOrientation(LinearLayout.VERTICAL);
                    customTab.setGravity(Gravity.CENTER);
                    customTab.setPadding(35, 20, 35, 20);

                    ImageView customIcon = new ImageView(context);
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(clonedDrawable);
                    
                    LinearLayout iconContainer = new LinearLayout(context);
                    iconContainer.setGravity(Gravity.CENTER);
                    iconContainer.setPadding(20, 10, 20, 10);
                    iconContainer.addView(customIcon, new LinearLayout.LayoutParams(65, 65));

                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(11f);
                    customText.setPadding(0, 8, 0, 0);
                    customText.setGravity(Gravity.CENTER);

                    customTab.addView(iconContainer);
                    customTab.addView(customText);

                    customTab.setOnClickListener(v -> {
                        stockTab.performClick();
                        currentActiveIndex = tabIndex;
                        updateTabStates();
                    });

                    customTabs.add(customTab);
                    customBar.addView(customTab);
                }
            }

            // Inject into Window
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 70;

            rootView.addView(customBar, lp);
            updateTabStates();

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Failed: " + e.getMessage());
        }
    }

    private void updateTabStates() {
        // Theme-aware indicator colors
        String activeHighlightColor = isDarkMode ? "#2A3942" : "#F0F2F5";
        String activeTextColor = isDarkMode ? "#FFFFFF" : "#111B21";
        String inactiveTextColor = isDarkMode ? "#888888" : "#5E6C75";

        for (int i = 0; i < customTabs.size(); i++) {
            LinearLayout tab = customTabs.get(i);
            LinearLayout iconContainer = (LinearLayout) tab.getChildAt(0);
            ImageView icon = (ImageView) iconContainer.getChildAt(0);
            TextView text = (TextView) tab.getChildAt(1);

            if (i == currentActiveIndex) {
                icon.setColorFilter(Color.parseColor(activeTextColor));
                text.setTextColor(Color.parseColor(activeTextColor));
                
                GradientDrawable activeHighlight = new GradientDrawable();
                activeHighlight.setShape(GradientDrawable.RECTANGLE);
                activeHighlight.setCornerRadius(40f);
                activeHighlight.setColor(Color.parseColor(activeHighlightColor));
                iconContainer.setBackground(activeHighlight);
            } else {
                icon.setColorFilter(Color.parseColor(inactiveTextColor));
                text.setTextColor(Color.parseColor(inactiveTextColor));
                iconContainer.setBackground(null);
            }
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
