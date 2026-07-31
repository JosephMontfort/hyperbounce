package dev.joseph.hyperbounce;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    
    // Theme State Tracking
    private boolean isDarkMode = true;
    private int lastUiMode = -1;

    // Gesture & Long-press state tracking
    private boolean isDragging = false;
    private int initialTouchIndex = -1;
    private float touchStartX = 0f;
    private float touchStartY = 0f;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable = null;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;
                
                // Immediately crush visibility on creation
                stockBar.setVisibility(View.GONE);

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        ViewParent parent = stockBar.getParent();
                        if (parent instanceof View) {
                            ((View) parent).setVisibility(View.GONE);
                        }

                        // Build the initial UI
                        executeUIHeist(stockBar);

                        // The Bulletproof Native Sentinel
                        stockBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                // 1. Enforce Stock Bar Nuke
                                if (stockBar.getVisibility() != View.GONE) {
                                    stockBar.setVisibility(View.GONE);
                                }
                                ViewParent p = stockBar.getParent();
                                if (p instanceof View && ((View) p).getVisibility() != View.GONE) {
                                    ((View) p).setVisibility(View.GONE);
                                }

                                // 2. Dynamic Theme Change Detection (Safe Method)
                                Context ctx = stockBar.getContext();
                                if (ctx != null) {
                                    int currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        lastUiMode = currentUiMode;
                                        
                                        // Theme changed! Rebuild the custom bar.
                                        ViewGroup rootView = (ViewGroup) stockBar.getRootView();
                                        View existingBar = rootView.findViewWithTag("HyperFloatingBar");
                                        if (existingBar != null) {
                                            rootView.removeView(existingBar);
                                        }
                                        executeUIHeist(stockBar);
                                    } else if (lastUiMode == -1) {
                                        lastUiMode = currentUiMode;
                                    }
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
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return;

            Context context = stockBar.getContext();
            customTabs.clear();
            stockTabs.clear();

            // --- THEME DETECTION & TRANSLUCENT PALETTE ---
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            // Translucent Backgrounds (75% Alpha -> #C0)
            String bgColor = isDarkMode ? "#C01F2C34" : "#C0FFFFFF";
            String strokeColor = isDarkMode ? "#40FFFFFF" : "#30000000";

            // --- WIDER BAR CONTAINER (88% Screen Width) ---
            final LinearLayout customBar = new LinearLayout(context);
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

            // Extract Stock Tabs & Build Custom Weighted Tabs
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer != null) {
                int tabCount = menuContainer.getChildCount();

                for (int i = 0; i < tabCount; i++) {
                    View stockTab = menuContainer.getChildAt(i);
                    stockTabs.add(stockTab);

                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);

                    if (nativeIcon == null || nativeText == null) continue;

                    // Tab Layout with Equal Weighting (1.0f ensures text never clips)
                    LinearLayout customTab = new LinearLayout(context);
                    customTab.setOrientation(LinearLayout.VERTICAL);
                    customTab.setGravity(Gravity.CENTER);
                    
                    LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                            0, 
                            ViewGroup.LayoutParams.WRAP_CONTENT, 
                            1.0f 
                    );
                    customTab.setLayoutParams(tabLp);
                    customTab.setPadding(10, 16, 10, 16);

                    ImageView customIcon = new ImageView(context);
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(clonedDrawable);

                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(10.5f);
                    customText.setSingleLine(true);
                    customText.setGravity(Gravity.CENTER);

                    customTab.addView(customIcon, new LinearLayout.LayoutParams(56, 56));
                    customTab.addView(customText);

                    customTabs.add(customTab);
                    customBar.addView(customTab);
                }
            }

            // --- ADVANCED LONG PRESS & SLIDE GESTURE ENGINE ---
            customBar.setOnTouchListener((v, event) -> {
                float rawX = event.getRawX();
                float rawY = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = rawX;
                        touchStartY = rawY;
                        isDragging = false;
                        initialTouchIndex = calculateTabIndexFromX(rawX, customBar);

                        if (longPressRunnable != null) longPressHandler.removeCallbacks(longPressRunnable);
                        longPressRunnable = () -> {
                            isDragging = true; // Long press triggered
                            if (initialTouchIndex != -1) {
                                currentActiveIndex = initialTouchIndex;
                                updateTabStates();
                            }
                        };
                        longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                        return true; // Consume touch to allow MOVE tracking

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(rawX - touchStartX);
                        float dy = Math.abs(rawY - touchStartY);

                        // If user starts sliding before long-press timeout, cancel long-press and assume dragging
                        if (dx > 25 || dy > 25) {
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                                longPressRunnable = null;
                            }
                            if (!isDragging && dx > dy) { // Horizontal swipe bias
                                isDragging = true;
                            }
                        }

                        // Live-update the highlight bubble under their finger
                        if (isDragging) {
                            int hoverIdx = calculateTabIndexFromX(rawX, customBar);
                            if (hoverIdx != -1 && hoverIdx != currentActiveIndex) {
                                currentActiveIndex = hoverIdx;
                                updateTabStates();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (longPressRunnable != null) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            longPressRunnable = null;
                        }
                        
                        int releaseIdx = calculateTabIndexFromX(rawX, customBar);
                        if (releaseIdx != -1) {
                            switchToTab(releaseIdx);
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            });

            // Layout Parameters (88% Width & Lifted High)
            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            int barWidth = (int) (displayWidth * 0.88f);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    barWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 110; // Lifted high above the bottom edge

            rootView.addView(customBar, lp);
            updateTabStates();

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] UI Heist Exception: " + e.getMessage());
        }
    }

    // Calculates which tab the user's finger is currently hovering over
    private int calculateTabIndexFromX(float rawX, LinearLayout customBar) {
        int[] location = new int[2];
        customBar.getLocationOnScreen(location);
        float barLeft = location[0];
        float barWidth = customBar.getWidth();
        if (barWidth <= 0) return -1;

        float relativeX = rawX - barLeft;
        if (relativeX < 0 || relativeX > barWidth) return -1;

        int count = customTabs.size();
        if (count == 0) return -1;

        int idx = (int) ((relativeX / barWidth) * count);
        return Math.max(0, Math.min(count - 1, idx));
    }

    private void switchToTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < stockTabs.size()) {
            stockTabs.get(tabIndex).performClick();
            currentActiveIndex = tabIndex;
            updateTabStates();
        }
    }

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

                // Indicator bubble wraps the icon + text
                GradientDrawable activeHighlight = new GradientDrawable();
                activeHighlight.setShape(GradientDrawable.RECTANGLE);
                activeHighlight.setCornerRadius(50f);
                activeHighlight.setColor(Color.parseColor(activeHighlightColor));
                tab.setBackground(activeHighlight);
            } else {
                icon.setColorFilter(Color.parseColor(inactiveTextColor));
                text.setTextColor(Color.parseColor(inactiveTextColor));
                tab.setBackground(null); // Remove highlight
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
