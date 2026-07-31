package dev.joseph.hyperbounce;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
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
    private List<View> stockTabs = new ArrayList<>();
    private int currentActiveIndex = 0;
    private boolean isDarkMode = true;
    private float swipeStartX = 0f;
    private static final float SWIPE_THRESHOLD = 80f; // Distance threshold in px for pull gesture

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        // 1. CLASS-LEVEL NUKE: Intercept setVisibility directly to eliminate startup flash
        XposedBridge.hookAllMethods(wdsBottomBarClass, "setVisibility", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = View.GONE; // Always force GONE before drawing
            }
        });

        // 2. CONSTRUCTOR HOOK: Synchronous pre-render execution
        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;
                stockBar.setVisibility(View.GONE);

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        // Immediately hide parent wrapper without delay
                        ViewParent parent = stockBar.getParent();
                        if (parent instanceof View) {
                            ((View) parent).setVisibility(View.GONE);
                        }

                        // Execute UI Heist synchronously (NO post() delay)
                        executeUIHeist(stockBar);

                        // Backup PreDraw Sentinel
                        stockBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                if (stockBar.getVisibility() != View.GONE) {
                                    stockBar.setVisibility(View.GONE);
                                }
                                ViewParent p = stockBar.getParent();
                                if (p instanceof View && ((View) p).getVisibility() != View.GONE) {
                                    ((View) p).setVisibility(View.GONE);
                                }
                                return true;
                            }
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
            ViewGroup rootView = (ViewGroup) stockBar.getRootView();
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; // Prevent duplicate injection

            Context context = stockBar.getContext();
            customTabs.clear();
            stockTabs.clear();

            // --- THEME DETECTION & TRANSLUCENT PALETTE ---
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            // Translucent Backgrounds (75% Alpha)
            String bgColor = isDarkMode ? "#C01F2C34" : "#C0FFFFFF";
            String strokeColor = isDarkMode ? "#40FFFFFF" : "#30000000";

            // Build the Translucent Custom Pill
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

            // Extract Native Icons & Setup Custom Tabs
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer != null) {
                int tabCount = menuContainer.getChildCount();

                for (int i = 0; i < tabCount; i++) {
                    final int tabIndex = i;
                    View stockTab = menuContainer.getChildAt(i);
                    stockTabs.add(stockTab);

                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);

                    if (nativeIcon == null || nativeText == null) continue;

                    // Custom Tab Layout (Indicator wraps BOTH Icon and Text)
                    LinearLayout customTab = new LinearLayout(context);
                    customTab.setOrientation(LinearLayout.VERTICAL);
                    customTab.setGravity(Gravity.CENTER);
                    customTab.setPadding(35, 18, 35, 18);

                    ImageView customIcon = new ImageView(context);
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(clonedDrawable);

                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(11f);
                    customText.setPadding(0, 6, 0, 0);
                    customText.setGravity(Gravity.CENTER);

                    customTab.addView(customIcon, new LinearLayout.LayoutParams(60, 60));
                    customTab.addView(customText);

                    // --- TAP & HORIZONTAL PULL GESTURE LISTENER ---
                    customTab.setOnTouchListener((v, event) -> {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                swipeStartX = event.getRawX();
                                break;
                            case MotionEvent.ACTION_MOVE:
                                float deltaX = event.getRawX() - swipeStartX;
                                if (deltaX < -SWIPE_THRESHOLD) { // Pulled Left -> Next Tab
                                    if (currentActiveIndex < customTabs.size() - 1) {
                                        switchToTab(currentActiveIndex + 1);
                                        swipeStartX = event.getRawX();
                                    }
                                    return true;
                                } else if (deltaX > SWIPE_THRESHOLD) { // Pulled Right -> Previous Tab
                                    if (currentActiveIndex > 0) {
                                        switchToTab(currentActiveIndex - 1);
                                        swipeStartX = event.getRawX();
                                    }
                                    return true;
                                }
                                break;
                        }
                        return false; // Allow standard click if not swiped
                    });

                    customTab.setOnClickListener(v -> switchToTab(tabIndex));

                    customTabs.add(customTab);
                    customBar.addView(customTab);
                }
            }

            // Inject into Root Window
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 70;

            rootView.addView(customBar, lp);
            updateTabStates();

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] UI Heist Exception: " + e.getMessage());
        }
    }

    private void switchToTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < stockTabs.size()) {
            stockTabs.get(tabIndex).performClick();
            currentActiveIndex = tabIndex;
            updateTabStates();
        }
    }

    // --- STATE MANAGER (Indicator Wraps Icon + Text) ---
    private void updateTabStates() {
        String activeHighlightColor = isDarkMode ? "#C02A3942" : "#C0E0E0E0";
        String activeTextColor = isDarkMode ? "#FFFFFF" : "#111B21";
        String inactiveTextColor = isDarkMode ? "#99888888" : "#885E6C75";

        for (int i = 0; i < customTabs.size(); i++) {
            LinearLayout tab = customTabs.get(i);
            ImageView icon = (ImageView) tab.getChildAt(0);
            TextView text = (TextView) tab.getChildAt(1);

            if (i == currentActiveIndex) {
                icon.setColorFilter(Color.parseColor(activeTextColor));
                text.setTextColor(Color.parseColor(activeTextColor));

                // Active Pill Highlight wrapping the entire tab (Icon + Text)
                GradientDrawable activeHighlight = new GradientDrawable();
                activeHighlight.setShape(GradientDrawable.RECTANGLE);
                activeHighlight.setCornerRadius(50f);
                activeHighlight.setColor(Color.parseColor(activeHighlightColor));
                tab.setBackground(activeHighlight);
            } else {
                icon.setColorFilter(Color.parseColor(inactiveTextColor));
                text.setTextColor(Color.parseColor(inactiveTextColor));
                tab.setBackground(null);
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
