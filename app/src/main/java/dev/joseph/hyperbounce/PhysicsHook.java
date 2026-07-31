package dev.joseph.hyperbounce;

import android.content.Context;
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
    private int currentActiveIndex = 0; // Default to Chats

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
            // 1. Space Reclamation (Nuke the stock wrapper)
            ViewParent parent = stockBar.getParent();
            if (parent instanceof View) {
                ((View) parent).setVisibility(View.GONE);
            }

            ViewGroup rootView = (ViewGroup) stockBar.getRootView();
            if (rootView.findViewWithTag("HyperFloatingBar") != null) return; // Prevent duplicates

            Context context = stockBar.getContext();
            customTabs.clear(); // Reset on rebuild

            // 2. Build the KernelSU-style Floating Pill
            LinearLayout customBar = new LinearLayout(context);
            customBar.setTag("HyperFloatingBar");
            customBar.setOrientation(LinearLayout.HORIZONTAL);
            customBar.setGravity(Gravity.CENTER);
            
            // Block stray touches
            customBar.setClickable(true);
            customBar.setFocusable(true);

            // Dark slate pill background
            GradientDrawable pillShape = new GradientDrawable();
            pillShape.setShape(GradientDrawable.RECTANGLE);
            pillShape.setCornerRadius(100f); // Perfect rounded pill
            pillShape.setColor(Color.parseColor("#1F2C34"));
            pillShape.setStroke(2, Color.parseColor("#374248")); // Subtle border
            customBar.setBackground(pillShape);
            customBar.setElevation(80f);

            // 3. Extract and Inject (The Ram Heist)
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer != null) {
                int tabCount = menuContainer.getChildCount();
                
                for (int i = 0; i < tabCount; i++) {
                    final int tabIndex = i;
                    View stockTab = menuContainer.getChildAt(i);
                    
                    // Steal the native views
                    ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                    TextView nativeText = findTextView((ViewGroup) stockTab);

                    if (nativeIcon == null || nativeText == null) continue;

                    // Create Custom Vertical Tab (Icon stacked above Text)
                    LinearLayout customTab = new LinearLayout(context);
                    customTab.setOrientation(LinearLayout.VERTICAL);
                    customTab.setGravity(Gravity.CENTER);
                    customTab.setPadding(35, 20, 35, 20);

                    // Replicate the Icon
                    ImageView customIcon = new ImageView(context);
                    // Clone the drawable so it doesn't break WhatsApp's rendering
                    Drawable clonedDrawable = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(clonedDrawable);
                    
                    // Style the Icon Container (For the active indicator highlight)
                    LinearLayout iconContainer = new LinearLayout(context);
                    iconContainer.setGravity(Gravity.CENTER);
                    iconContainer.setPadding(20, 10, 20, 10);
                    iconContainer.addView(customIcon, new LinearLayout.LayoutParams(65, 65)); // Icon sizing

                    // Replicate the Text
                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(11f); // Smaller text, iOS style
                    customText.setPadding(0, 8, 0, 0);
                    customText.setGravity(Gravity.CENTER);

                    // Assemble the Tab
                    customTab.addView(iconContainer);
                    customTab.addView(customText);

                    // The Puppeteer Listener
                    customTab.setOnClickListener(v -> {
                        // Switch Fragments
                        stockTab.performClick();
                        // Update our UI states
                        currentActiveIndex = tabIndex;
                        updateTabStates();
                    });

                    customTabs.add(customTab);
                    customBar.addView(customTab);
                }
            }

            // 4. Inject into the Master Window
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 70; // Float height

            rootView.addView(customBar, lp);
            
            // Initialize the starting UI state
            updateTabStates();

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Failed: " + e.getMessage());
        }
    }

    // --- State Manager (The Page Indicator) ---
    private void updateTabStates() {
        for (int i = 0; i < customTabs.size(); i++) {
            LinearLayout tab = customTabs.get(i);
            LinearLayout iconContainer = (LinearLayout) tab.getChildAt(0);
            ImageView icon = (ImageView) iconContainer.getChildAt(0);
            TextView text = (TextView) tab.getChildAt(1);

            if (i == currentActiveIndex) {
                // ACTIVE STATE: Bright White + Subtle Pill Highlight
                icon.setColorFilter(Color.parseColor("#FFFFFF"));
                text.setTextColor(Color.parseColor("#FFFFFF"));
                
                GradientDrawable activeHighlight = new GradientDrawable();
                activeHighlight.setShape(GradientDrawable.RECTANGLE);
                activeHighlight.setCornerRadius(40f);
                activeHighlight.setColor(Color.parseColor("#2A3942")); // Lighter highlight bubble
                iconContainer.setBackground(activeHighlight);
            } else {
                // INACTIVE STATE: Dim Grey + No Highlight
                icon.setColorFilter(Color.parseColor("#888888"));
                text.setTextColor(Color.parseColor("#888888"));
                iconContainer.setBackground(null);
            }
        }
    }

    // --- Recursive View Stealers ---
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
