package dev.joseph.hyperbounce;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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

    private boolean isDarkMode = true;
    private int lastUiMode = -1;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.whatsapp")) return;

        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", lpparam.classLoader);
        if (wdsBottomBarClass == null) return;

        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup stockBar = (ViewGroup) param.thisObject;
                
                stockBar.setAlpha(0f);

                stockBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        stockBar.post(() -> executeUIHeist(stockBar));

                        stockBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                // 1. Sync Visibility with WhatsApp State Machine
                                ViewParent p = stockBar.getParent();
                                int parentVis = (p instanceof View) ? ((View)p).getVisibility() : -1;
                                float transY = stockBar.getTranslationY();

                                ViewGroup injectedRootView = (ViewGroup) stockBar.getRootView();
                                View customBar = injectedRootView.findViewWithTag("HyperFloatingBar");
                                
                                if (customBar != null) {
                                    boolean shouldHide = stockBar.getVisibility() == View.GONE || parentVis == View.GONE || transY > 50f;
                                    customBar.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
                                }

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
                                        if (customBar != null) {
                                            injectedRootView.removeView(customBar);
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
            
            // --- THEME & PALETTE ---
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            String bgColor = isDarkMode ? "#E61F2C34" : "#E6FFFFFF"; 
            String strokeColor = isDarkMode ? "#40FFFFFF" : "#30000000";

            // Extract Stock Tabs
            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer == null) return;
            int tabCount = menuContainer.getChildCount();
            List<View> stockTabs = new ArrayList<>();
            for (int i = 0; i < tabCount; i++) {
                stockTabs.add(menuContainer.getChildAt(i));
            }

            // Build the KernelLiquidBar
            KernelLiquidBar liquidBar = new KernelLiquidBar(context, stockTabs, isDarkMode);
            liquidBar.setTag("HyperFloatingBar");

            GradientDrawable pillShape = new GradientDrawable();
            pillShape.setShape(GradientDrawable.RECTANGLE);
            pillShape.setCornerRadius(100f);
            pillShape.setColor(Color.parseColor(bgColor));
            pillShape.setStroke(2, Color.parseColor(strokeColor));
            liquidBar.setBackground(pillShape);
            liquidBar.setElevation(80f);

            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            int barWidth = (int) (displayWidth * 0.70f);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    barWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 100;

            rootView.addView(liquidBar, lp);

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.getMessage());
        }
    }

    // ====================================================================================
    // KERNEL LIQUID NAV BAR ENGINE
    // ====================================================================================

    private class KernelLiquidBar extends FrameLayout {
        private List<View> stockTabs;
        private List<LinearLayout> customTabs = new ArrayList<>();
        private IndicatorView indicatorPill;
        
        private int currentActiveIndex = 0;
        private int tabCount;
        private float tabWidthPx = 0f;
        private float totalWidthPx = 0f;
        
        // Touch State
        private boolean isDragging = false;
        private float initialTouchX;
        private float lastTouchX;
        private long lastTouchTime;
        private float currentVelocity = 0f;
        
        private ValueAnimator snapAnimator;
        private float currentIndicatorX = 0f;

        private String activeTextColor;
        private String inactiveTextColor;

        public KernelLiquidBar(Context context, List<View> stockTabs, boolean isDarkMode) {
            super(context);
            this.stockTabs = stockTabs;
            this.tabCount = stockTabs.size();
            
            activeTextColor = isDarkMode ? "#FFFFFF" : "#111B21";
            inactiveTextColor = isDarkMode ? "#99888888" : "#885E6C75";
            String pillColor = isDarkMode ? "#40FFFFFF" : "#20000000";

            // 1. Setup Independent Sliding Indicator
            indicatorPill = new IndicatorView(context, Color.parseColor(pillColor));
            addView(indicatorPill, new FrameLayout.LayoutParams(0, 0));

            // 2. Setup Static Foreground Content Layer
            LinearLayout contentLayer = new LinearLayout(context);
            contentLayer.setOrientation(LinearLayout.HORIZONTAL);
            addView(contentLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < tabCount; i++) {
                View stockTab = stockTabs.get(i);
                
                if (stockTab.isSelected()) currentActiveIndex = i;

                ImageView nativeIcon = findImageView((ViewGroup) stockTab);
                TextView nativeText = findTextView((ViewGroup) stockTab);

                LinearLayout customTab = new LinearLayout(context);
                customTab.setOrientation(LinearLayout.VERTICAL);
                customTab.setGravity(Gravity.CENTER);
                customTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                customTab.setPadding(10, 24, 10, 24);

                if (nativeIcon != null) {
                    ImageView customIcon = new ImageView(context);
                    customIcon.setImageDrawable(nativeIcon.getDrawable().getConstantState().newDrawable().mutate());
                    customTab.addView(customIcon, new LinearLayout.LayoutParams(64, 64));
                }

                if (nativeText != null) {
                    TextView customText = new TextView(context);
                    customText.setText(nativeText.getText());
                    customText.setTextSize(10.5f);
                    customText.setSingleLine(true);
                    customText.setGravity(Gravity.CENTER);
                    customTab.addView(customText);
                }

                customTabs.add(customTab);
                contentLayer.addView(customTab);
            }

            updateTabColors();

            // Wait for layout measurement
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (getWidth() > 0) {
                        totalWidthPx = getWidth();
                        float density = getResources().getDisplayMetrics().density;
                        float contentWidthPx = totalWidthPx - (8 * density); // 4dp padding each side
                        tabWidthPx = contentWidthPx / tabCount;
                        
                        FrameLayout.LayoutParams pillLp = (FrameLayout.LayoutParams) indicatorPill.getLayoutParams();
                        pillLp.width = (int) tabWidthPx;
                        pillLp.height = getHeight() - (int)(8 * density);
                        pillLp.leftMargin = (int)(4 * density);
                        pillLp.topMargin = (int)(4 * density);
                        indicatorPill.setLayoutParams(pillLp);

                        // Initial snap
                        currentIndicatorX = currentActiveIndex * tabWidthPx;
                        indicatorPill.setTranslationX(currentIndicatorX);
                        
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float rawX = event.getRawX();
            float localX = event.getX();
            long currentTime = System.currentTimeMillis();

            int[] location = new int[2];
            getLocationOnScreen(location);
            float relativeX = rawX - location[0];

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (snapAnimator != null) snapAnimator.cancel();
                    isDragging = false;
                    initialTouchX = rawX;
                    lastTouchX = rawX;
                    lastTouchTime = currentTime;
                    currentVelocity = 0f;
                    
                    indicatorPill.setPressProgress(1f);
                    indicatorPill.setTouchPoint(localX - currentIndicatorX, event.getY());
                    
                    // Instantly scale up slightly on press
                    indicatorPill.animate().scaleX(1.05f).scaleY(0.95f).setDuration(150).start();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = rawX - lastTouchX;
                    long dt = currentTime - lastTouchTime;
                    
                    if (!isDragging && Math.abs(rawX - initialTouchX) > 15) {
                        isDragging = true;
                    }

                    if (dt > 0) {
                        currentVelocity = (dx / dt) * 10f; // Scale velocity for KernelSU math
                    }

                    if (isDragging) {
                        // 1. Jelly Deformation Math
                        float absV = Math.abs(currentVelocity);
                        float targetScaleX = 1f / (1f - Math.max(-0.2f, Math.min(0.2f, absV * 0.75f)));
                        float targetScaleY = 1f * (1f - Math.max(-0.2f, Math.min(0.2f, absV * 0.25f)));
                        indicatorPill.setScaleX(targetScaleX);
                        indicatorPill.setScaleY(targetScaleY);

                        // 2. Calculate New Pill Position
                        currentIndicatorX += dx;
                        float maxIndexX = (tabCount - 1) * tabWidthPx;

                        // 3. Rubber-band Effect on Root Container
                        float dragOffset = 0f;
                        if (currentIndicatorX < 0) {
                            dragOffset = currentIndicatorX;
                            currentIndicatorX = 0;
                        } else if (currentIndicatorX > maxIndexX) {
                            dragOffset = currentIndicatorX - maxIndexX;
                            currentIndicatorX = maxIndexX;
                        }
                        
                        if (dragOffset != 0) {
                            float fraction = Math.max(-1f, Math.min(1f, dragOffset / totalWidthPx));
                            float sign = Math.signum(fraction);
                            float t = Math.abs(fraction);
                            float easeOut = 1f - (float)Math.pow(1f - t, 3);
                            float dp4 = 4f * getResources().getDisplayMetrics().density;
                            float panelOffset = dp4 * sign * easeOut;
                            this.setTranslationX(panelOffset);
                        } else {
                            this.setTranslationX(0f);
                        }

                        indicatorPill.setTranslationX(currentIndicatorX);
                        indicatorPill.setTouchPoint(localX - currentIndicatorX, event.getY());

                        // 4. Live Highlight Sync
                        int hoverIdx = (int) Math.max(0, Math.min(tabCount - 1, (currentIndicatorX + (tabWidthPx/2)) / tabWidthPx));
                        if (hoverIdx != currentActiveIndex) {
                            currentActiveIndex = hoverIdx;
                            updateTabColors();
                        }
                    }

                    lastTouchX = rawX;
                    lastTouchTime = currentTime;
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    int targetIndex = isDragging ? 
                        (int) Math.max(0, Math.min(tabCount - 1, (currentIndicatorX + (tabWidthPx/2)) / tabWidthPx)) :
                        (int) Math.max(0, Math.min(tabCount - 1, relativeX / tabWidthPx));

                    currentActiveIndex = targetIndex;
                    updateTabColors();
                    
                    float targetX = targetIndex * tabWidthPx;

                    // Spring Snap Animation
                    snapAnimator = ValueAnimator.ofFloat(currentIndicatorX, targetX);
                    snapAnimator.setDuration(300);
                    snapAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
                    snapAnimator.addUpdateListener(animation -> {
                        currentIndicatorX = (float) animation.getAnimatedValue();
                        indicatorPill.setTranslationX(currentIndicatorX);
                    });
                    
                    // Reset Scales & Translation
                    indicatorPill.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
                    this.animate().translationX(0f).setDuration(200).start();
                    
                    // Fade out specular
                    indicatorPill.fadePress();
                    snapAnimator.start();

                    // Sync state with WhatsApp
                    if (targetIndex >= 0 && targetIndex < stockTabs.size()) {
                        stockTabs.get(targetIndex).performClick();
                    }
                    
                    isDragging = false;
                    return true;
            }
            return false;
        }

        private void updateTabColors() {
            for (int i = 0; i < customTabs.size(); i++) {
                LinearLayout tab = customTabs.get(i);
                ImageView icon = null;
                TextView text = null;
                
                for(int j=0; j<tab.getChildCount(); j++) {
                    View child = tab.getChildAt(j);
                    if (child instanceof ImageView) icon = (ImageView) child;
                    if (child instanceof TextView) text = (TextView) child;
                }

                String targetColor = (i == currentActiveIndex) ? activeTextColor : inactiveTextColor;
                
                if (icon != null) icon.setColorFilter(Color.parseColor(targetColor));
                if (text != null) text.setTextColor(Color.parseColor(targetColor));
            }
        }
    }

    // ====================================================================================
    // SLIDING PILL WITH SPECULAR HIGHLIGHT
    // ====================================================================================

    private class IndicatorView extends View {
        private Paint pillPaint;
        private Paint highlightPaint;
        private RectF rectF;
        private float touchX = 0f;
        private float touchY = 0f;
        private float pressProgress = 0f;
        private ValueAnimator fadeAnim;

        public IndicatorView(Context context, int color) {
            super(context);
            pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            pillPaint.setColor(color);
            
            highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                highlightPaint.setBlendMode(BlendMode.PLUS);
            }
            rectF = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rectF.set(0, 0, w, h);
        }

        public void setTouchPoint(float x, float y) {
            touchX = x;
            touchY = y;
            invalidate();
        }

        public void setPressProgress(float progress) {
            this.pressProgress = progress;
            if (fadeAnim != null) fadeAnim.cancel();
            invalidate();
        }

        public void fadePress() {
            fadeAnim = ValueAnimator.ofFloat(pressProgress, 0f);
            fadeAnim.setDuration(300);
            fadeAnim.addUpdateListener(anim -> {
                pressProgress = (float) anim.getAnimatedValue();
                invalidate();
            });
            fadeAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = getHeight() / 2f;
            
            // Draw Base Pill
            canvas.drawRoundRect(rectF, radius, radius, pillPaint);

            // Draw Specular Highlight (Tracks touch)
            if (pressProgress > 0) {
                RadialGradient rg = new RadialGradient(
                        touchX, touchY, radius * 2.5f,
                        Color.argb((int)(80 * pressProgress), 255, 255, 255),
                        Color.TRANSPARENT, 
                        Shader.TileMode.CLAMP
                );
                highlightPaint.setShader(rg);
                canvas.drawRoundRect(rectF, radius, radius, highlightPaint);
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
