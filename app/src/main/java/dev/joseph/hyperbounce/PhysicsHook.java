package dev.joseph.hyperbounce;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
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

                                Context ctx = stockBar.getContext();
                                if (ctx != null) {
                                    int currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        lastUiMode = currentUiMode;
                                        if (customBar != null) injectedRootView.removeView(customBar);
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
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (uiMode == Configuration.UI_MODE_NIGHT_YES);

            ViewGroup menuContainer = (ViewGroup) stockBar.getChildAt(0);
            if (menuContainer == null) return;
            int tabCount = menuContainer.getChildCount();
            List<View> stockTabs = new ArrayList<>();
            for (int i = 0; i < tabCount; i++) {
                stockTabs.add(menuContainer.getChildAt(i));
            }

            KernelLiquidBar liquidBar = new KernelLiquidBar(context, stockTabs, isDarkMode);
            liquidBar.setTag("HyperFloatingBar");

            int displayWidth = context.getResources().getDisplayMetrics().widthPixels;
            int barWidth = (int) (displayWidth * 0.65f);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    barWidth,
                    (int) (64 * context.getResources().getDisplayMetrics().density) // Fixed container height
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = 100;

            rootView.addView(liquidBar, lp);

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] Heist Exception: " + e.getMessage());
        }
    }

    // ====================================================================================
    // KERNEL LIQUID NAV BAR ENGINE (EXACT KERNELSU PHYSICS & BOUNDARY BLEED)
    // ====================================================================================

    private class KernelLiquidBar extends FrameLayout {
        private List<View> stockTabs;
        private List<ImageView> customIcons = new ArrayList<>();
        private IndicatorView indicatorPill;
        
        private int currentActiveIndex = 0;
        private int tabCount;
        private float tabWidthPx = 0f;
        private float totalWidthPx = 0f;
        
        private boolean isDragging = false;
        private float initialTouchX, lastTouchX;
        private long lastTouchTime;
        private float currentVelocity = 0f;
        
        private ValueAnimator snapAnimator;
        private float currentIndicatorX = 0f;

        private String activeColor, inactiveColor;

        public KernelLiquidBar(Context context, List<View> stockTabs, boolean isDarkMode) {
            super(context);
            this.stockTabs = stockTabs;
            this.tabCount = stockTabs.size();
            
            // Critical: Allow indicator to bleed outside the nav bar vertically and horizontally
            setClipChildren(false);
            setClipToPadding(false);
            
            activeColor = isDarkMode ? "#FFFFFF" : "#111B21";
            inactiveColor = isDarkMode ? "#66888888" : "#665E6C75";
            String bgColor = isDarkMode ? "#B31F2C34" : "#B3FFFFFF"; 
            String strokeColor = isDarkMode ? "#30FFFFFF" : "#20000000";
            String pillColor = isDarkMode ? "#40FFFFFF" : "#20000000";

            // 1. Frosted Glass Background (RenderEffect Blur)
            View glassBackground = new View(context);
            GradientDrawable bgShape = new GradientDrawable();
            bgShape.setShape(GradientDrawable.RECTANGLE);
            bgShape.setCornerRadius(150f);
            bgShape.setColor(Color.parseColor(bgColor));
            bgShape.setStroke(3, Color.parseColor(strokeColor));
            glassBackground.setBackground(bgShape);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                glassBackground.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
            }
            
            // CRITICAL FIX: Force hardware to clip the blur bleed so it stays a pill shape
            glassBackground.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 150f);
                }
            });
            glassBackground.setClipToOutline(true);
            
            addView(glassBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. Unclipped Sliding Indicator
            indicatorPill = new IndicatorView(context, Color.parseColor(pillColor));
            addView(indicatorPill, new FrameLayout.LayoutParams(0, 0));

            // 3. Static Icon Content Layer (Text Stripped)
            LinearLayout contentLayer = new LinearLayout(context);
            contentLayer.setOrientation(LinearLayout.HORIZONTAL);
            contentLayer.setClipChildren(false);
            addView(contentLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            for (int i = 0; i < tabCount; i++) {
                View stockTab = stockTabs.get(i);
                if (stockTab.isSelected()) currentActiveIndex = i;

                ImageView nativeIcon = findImageView((ViewGroup) stockTab);

                LinearLayout customTab = new LinearLayout(context);
                customTab.setGravity(Gravity.CENTER);
                customTab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

                if (nativeIcon != null) {
                    ImageView customIcon = new ImageView(context);
                    Drawable cloned = nativeIcon.getDrawable().getConstantState().newDrawable().mutate();
                    customIcon.setImageDrawable(cloned);
                    customIcons.add(customIcon);
                    customTab.addView(customIcon, new LinearLayout.LayoutParams(76, 76)); // Larger icons to fill space
                }
                contentLayer.addView(customTab);
            }

            updateTabColors();

            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (getWidth() > 0) {
                        totalWidthPx = getWidth();
                        float density = getResources().getDisplayMetrics().density;
                        float paddingPx = 4 * density;
                        float contentWidthPx = totalWidthPx - (paddingPx * 2); 
                        tabWidthPx = contentWidthPx / tabCount;
                        
                        // Indicator pill is initially same height as bar
                        FrameLayout.LayoutParams pillLp = (FrameLayout.LayoutParams) indicatorPill.getLayoutParams();
                        pillLp.width = (int) tabWidthPx;
                        pillLp.height = getHeight() - (int)(paddingPx * 2);
                        pillLp.leftMargin = (int) paddingPx;
                        pillLp.topMargin = (int) paddingPx;
                        indicatorPill.setLayoutParams(pillLp);

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
                    
                    // Exact KernelSU Pressed Scale (78/56 = ~1.39f). Expands OUTSIDE the bar boundary.
                    indicatorPill.animate().scaleX(1.39f).scaleY(1.39f).setDuration(150).start();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = rawX - lastTouchX;
                    long dt = currentTime - lastTouchTime;
                    
                    if (!isDragging && Math.abs(rawX - initialTouchX) > 15) {
                        isDragging = true;
                    }

                    if (dt > 0) {
                        currentVelocity = (dx / dt) * 10f; // Velocity scaling
                    }

                    if (isDragging) {
                        // 1. Exact KernelSU Jelly Deformation Math based on 1.39f expanded state
                        float absV = Math.abs(currentVelocity) / 10f;
                        float scaleXDeform = 1f - Math.max(-0.2f, Math.min(0.2f, absV * 0.75f));
                        float scaleYDeform = 1f - Math.max(-0.2f, Math.min(0.2f, absV * 0.25f));
                        
                        indicatorPill.setScaleX(1.39f / scaleXDeform);
                        indicatorPill.setScaleY(1.39f * scaleYDeform);

                        // 2. Position Sync
                        currentIndicatorX += dx;
                        float maxIndexX = (tabCount - 1) * tabWidthPx;

                        // 3. Exact KernelSU Rubber-band Root Offset
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
                            float easeOut = 1f - (float)Math.pow(1f - Math.abs(fraction), 3);
                            float panelOffset = (4f * getResources().getDisplayMetrics().density) * sign * easeOut;
                            this.setTranslationX(panelOffset);
                        } else {
                            this.setTranslationX(0f);
                        }

                        indicatorPill.setTranslationX(currentIndicatorX);
                        indicatorPill.setTouchPoint(localX - currentIndicatorX, event.getY());

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

                    snapAnimator = ValueAnimator.ofFloat(currentIndicatorX, targetX);
                    snapAnimator.setDuration(300);
                    snapAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
                    snapAnimator.addUpdateListener(animation -> {
                        currentIndicatorX = (float) animation.getAnimatedValue();
                        indicatorPill.setTranslationX(currentIndicatorX);
                    });
                    
                    // Deflate back to standard size
                    indicatorPill.animate().scaleX(1f).scaleY(1f).setDuration(250).start();
                    this.animate().translationX(0f).setDuration(200).start();
                    
                    indicatorPill.fadePress();
                    snapAnimator.start();

                    if (targetIndex >= 0 && targetIndex < stockTabs.size()) {
                        stockTabs.get(targetIndex).performClick();
                    }
                    
                    isDragging = false;
                    return true;
            }
            return false;
        }

        private void updateTabColors() {
            for (int i = 0; i < customIcons.size(); i++) {
                String targetColor = (i == currentActiveIndex) ? activeColor : inactiveColor;
                customIcons.get(i).setColorFilter(Color.parseColor(targetColor));
            }
        }
    }

    // ====================================================================================
    // SLIDING PILL WITH KERNELSU AGSL SPECULAR SHADER (InteractiveHighlight)
    // ====================================================================================

    private class IndicatorView extends View {
        private Paint pillPaint;
        private Paint highlightPaint;
        private RectF rectF;
        private float touchX = 0f, touchY = 0f;
        private float pressProgress = 0f;
        private ValueAnimator fadeAnim;
        private RuntimeShader agslShader;

        @SuppressLint("NewApi")
        public IndicatorView(Context context, int color) {
            super(context);
            pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            pillPaint.setColor(color);
            highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                highlightPaint.setBlendMode(BlendMode.PLUS);
                // Exact KernelSU AGSL Shader for smoothstep glow tracking
                String shaderStr = "uniform float2 size; layout(color) uniform half4 color; uniform float radius; uniform float2 position; " +
                        "half4 main(float2 coord) { float dist = distance(coord, position); float intensity = smoothstep(radius, radius * 0.5, dist); return color * intensity; }";
                agslShader = new RuntimeShader(shaderStr);
            }
            rectF = new RectF();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rectF.set(0, 0, w, h);
            if (agslShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                agslShader.setFloatUniform("size", w, h);
                agslShader.setFloatUniform("radius", Math.min(w, h) * 1.2f);
            }
        }

        public void setTouchPoint(float x, float y) {
            touchX = x; touchY = y;
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

        @SuppressLint("NewApi")
        @Override
        protected void onDraw(Canvas canvas) {
            float radius = getHeight() / 2f;
            canvas.drawRoundRect(rectF, radius, radius, pillPaint);

            if (pressProgress > 0 && agslShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                agslShader.setFloatUniform("position", Math.max(0, Math.min(touchX, getWidth())), Math.max(0, Math.min(touchY, getHeight())));
                agslShader.setColorUniform("color", Color.argb((int)(30 * pressProgress), 255, 255, 255)); // 0.12f alpha scale
                highlightPaint.setShader(agslShader);
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
}
