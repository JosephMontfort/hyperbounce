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
    
    // Theme State Tracking
    private boolean isDarkMode = true;
    private int lastUiMode = -1;

    // Advanced Drag Gesture State
    private boolean isDragging = false;
    private int initialTouchIndex = -1;
    private float touchStartX = 0f;
    private float touchStartY = 0f;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        // ONLY hooking constructor. Zero risky class-level hooks.
        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;
                
                // Dim it instantly so there's no visual flash on cold start
                stockBar.setAlpha(0f);

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        
                        // We MUST use post() so WhatsApp has a few milliseconds to load the stock icons before we steal them!
                        stockBar.post(() -> executeUIHeist(stockBar));

                        // The Bulletproof Native Sentinel (Handles Theme Changes & Force-Hiding)
                        stockBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                // 1. Sync Visibility & Heavy Logging
                                ViewParent p = stockBar.getParent();
                                int parentVis = (p instanceof View) ? ((View)p).getVisibility() : -1;
                                float transY = stockBar.getTranslationY();
                                
                                XposedBridge.log("[HyperBounce] PreDraw | Stock Vis: " + stockBar.getVisibility() + " | Parent Vis: " + parentVis + " | TransY: " + transY);

                                ViewGroup injectedRootView = (ViewGroup) stockBar.getRootView();
                                View customBar = injectedRootView.findViewWithTag("HyperFloatingBar");
                                
                                if (customBar != null) {
                                    // WhatsApp sets stockBar or parent to GONE on search/camera screens
                                    boolean shouldHide = stockBar.getVisibility() == View.GONE || parentVis == View.GONE || transY > 50f;
                                    customBar.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
                                }

                                // Non-destructive Nuke: Zero out height & alpha instead of forcing GONE
                                // This allows WhatsApp's native state machine to keep updating visibilities
                                stockBar.setAlpha(0f);
                                if (stockBar.getLayoutParams() != null && stockBar.getLayoutParams().height != 0) {
                                    stockBar.getLayoutParams().height = 0;
                                    stockBar.requestLayout();
                                }

                                // 2. Dynamic Theme Change Detection
                                Context ctx = stockBar.getContext();
                                if (ctx != null) {
                                    int currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        lastUiMode = currentUiMode;
                                        
                                        // Theme changed! Purge the old bar and rebuild seamlessly
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
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; // Prevent duplicates

            Context context = stockBar.getContext();
            customTabs.clear();
            stockTabs.clear();

            // --- THEME DETECTION & TRANSLUCENT PALETTE ---
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            // Translucent Backgrounds (75% Alpha -> #C0)
            String bgColor = isDarkMode ? "#C01F2C34" : "#C0FFFFFF";
            String strokeColor = isDarkMode ? "#40FFFFFF" : "#30000000";

            // --- WIDER BAR CONTAINER (92% Screen Width) ---
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
                    
                    // DISABLE touch on individual tabs so the Master customBar handles the drag gestures natively
                    customTab.setClickable(false);
                    customTab.setFocusable(false);
                    
                    LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                            0, 
                            ViewGroup.LayoutParams.WRAP_CONTENT, 
                            1.0f 
                    );
                    customTab.setLayoutParams(tabLp);
                    customTab.setPadding(10, 18, 10, 18);

                    ImageView customIcon = new ImageView(context);
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(clonedDrawable);

                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(10.5f);
                    customText.setSingleLine(true);
                    customText.setGravity(Gravity.CENTER);

                    customTab.addView(customIcon, new LinearLayout.LayoutParams(72, 72));
                    // Text stripped for premium look

                    customTabs.add(customTab);
                    customBar.addView(customTab);
                }
            }

            // --- ADVANCED SLIDE & TAP GESTURE ENGINE ---
            customBar.setOnTouchListener((v, event) -> {
                float rawX = event.getRawX();
                float rawY = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = rawX;
                        touchStartY = rawY;
                        isDragging = false;
                        initialTouchIndex = calculateTabIndexFromX(rawX, customBar);
                        return true; // Consume touch to allow MOVE tracking

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(rawX - touchStartX);
                        
                        // If they move their finger horizontally more than 20 pixels, engage Drag Mode
                        if (!isDragging && dx > 20) {
                            isDragging = true;
                        }

                        // Live-update the highlight bubble under their finger
                        if (isDragging) {
                            int hoverIdx = calculateTabIndexFromX(rawX, customBar);
                            if (hoverIdx != -1 && hoverIdx != currentActiveIndex) {
                                currentActiveIndex = hoverIdx;
                                updateTabStates(); // Visually move the bubble instantly
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            // Released after a swipe
                            int releaseIdx = calculateTabIndexFromX(rawX, customBar);
                            if (releaseIdx != -1) {
                                switchToTab(releaseIdx); // Native fragment switch
                            }
                        } else {
                            // Standard Tap
                            if (initialTouchIndex != -1) {
                                switchToTab(initialTouchIndex);
                            }
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            });

            // Layout Parameters (92% Width & Lifted High)
            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            int barWidth = (int) (displayWidth * 0.70f); // Reduced to clear Meta AI button

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    barWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 130; // Lifted safely above navigation pill

            rootView.addView(customBar, lp);
            
            // Re-sync active tab from WhatsApp's actual UI state, just in case
            syncActiveTabWithStock();
            updateTabStates();

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.getMessage());
        }
    }

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
    
    private void syncActiveTabWithStock() {
        for (int i = 0; i < stockTabs.size(); i++) {
            if (stockTabs.get(i).isSelected()) {
                currentActiveIndex = i;
                break;
            }
        }
    }

    private void updateTabStates() {
        String activeHighlightColor = isDarkMode ? "#C02A3942" : "#C0E0E0E0";
        String activeTextColor = isDarkMode ? "#FFFFFF" : "#111B21";
        String inactiveTextColor = isDarkMode ? "#99888888" : "#885E6C75";

        for (int i = 0; i < customTabs.size(); i++) {
            LinearLayout tab = customTabs.get(i);
            ImageView icon = (ImageView) tab.getChildAt(0);
            TextView text = tab.getChildCount() > 1 ? (TextView) tab.getChildAt(1) : null;

            if (i == currentActiveIndex) {
                icon.setColorFilter(Color.parseColor(activeTextColor));
                if (text != null) text.setTextColor(Color.parseColor(activeTextColor));

                // Indicator bubble wraps the icon + text
                GradientDrawable activeHighlight = new GradientDrawable();
                activeHighlight.setShape(GradientDrawable.RECTANGLE);
                activeHighlight.setCornerRadius(50f);
                activeHighlight.setColor(Color.parseColor(activeHighlightColor));
                tab.setBackground(activeHighlight);
            } else {
                icon.setColorFilter(Color.parseColor(inactiveTextColor));
                if (text != null) text.setTextColor(Color.parseColor(inactiveTextColor));
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
