package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.util.WeakHashMap;
import java.util.HashSet;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    // ==========================================
    // TOGGLE PAINTS & COLORS
    // ==========================================
    private static final int TRACK_OFF   = Color.parseColor("#3A3A3C");
    private static final int TRACK_ON    = Color.parseColor("#30D158");
    private static final int THUMB_WHITE = Color.parseColor("#FFFFFF");
    private static final int SHADOW      = Color.parseColor("#25000000");
    private static final int RIM_COLOR   = Color.parseColor("#12000000");

    private static final int TRACK_OFF_DISABLED = Color.parseColor("#2C2C2E");
    private static final int TRACK_ON_DISABLED  = Color.parseColor("#1C4D25");
    private static final int THUMB_DISABLED     = Color.parseColor("#787880");

    private static final Paint bgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint thumbPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rimPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        shadowPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.5f);
        rimPaint.setColor(RIM_COLOR);
    }

    // ==========================================
    // SLIDER PAINTS & COLORS
    // ==========================================
    private static final int SLIDER_TRACK_ON = Color.parseColor("#30D158");
    private static final int SLIDER_TRACK_OFF_DARK = Color.parseColor("#39393D");
    private static final int SLIDER_TRACK_OFF_LIGHT = Color.parseColor("#E9E9EA");
    private static final int SLIDER_THUMB_WHITE = Color.parseColor("#FFFFFF");
    private static final int SLIDER_SHADOW_COLOR = Color.parseColor("#25000000");

    private static final Paint sliderBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sliderActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sliderThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sliderShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        sliderBgPaint.setStyle(Paint.Style.FILL);
        sliderActivePaint.setStyle(Paint.Style.FILL);
        sliderThumbPaint.setStyle(Paint.Style.FILL);
        sliderShadowPaint.setStyle(Paint.Style.FILL);
    }

    private static final String[] TARGET_TOGGLE_CLASSES = {
        "miuix.slidingwidget.widget.SlidingButton",
        "miuix.slidingwidget.widget.SlidingSwitchCompat",
        "miuix.slidingwidget.widget.SlidingSwitch",
        "miui.slidingwidget.widget.SlidingButton",
        "miui.widget.SlidingButton",
        "com.miui.systemui.widget.SlidingButton",
        "miui.systemui.widget.SlidingButton"
    };

    private static final String[] TARGET_SLIDER_CLASSES = {
        "miuix.androidbasewidget.widget.SeekBar"
    };

    private static final HashSet<String> hookedDynamicClasses = new HashSet<>();

    // ==========================================
    // STATES
    // ==========================================
    private static class ToggleState {
        float progress = 0.0f;
        float bloom = 0.0f;
        float squish = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
        float startX = 0f;
        float currentX = 0f;
        long lastTime = System.currentTimeMillis();
    }
    private static final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();

    private static class SliderState {
        float bloom = 0.0f;
        float visualRatio = -1.0f;
        float touchX = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
    }
    private static final WeakHashMap<Object, SliderState> sliderStateMap = new WeakHashMap<>();

    // ==========================================
    // TOGGLE ENGINE
    // ==========================================
    private void applyUnifiedToggleEngine(String className, ClassLoader classLoader) {
        if (hookedDynamicClasses.contains(className)) return;
        
        Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
        if (clazz == null) return;
        
        hookedDynamicClasses.add(className);

        try {
            XposedHelpers.findAndHookMethod(clazz, "jumpDrawablesToCurrentState", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null;
                }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "setChecked", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    ToggleState s = stateMap.get(view);
                    if (s != null) {
                        s.bloom = 1.0f; 
                        view.postInvalidateOnAnimation();
                    }
                }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "setPressed", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    boolean pressed = (boolean) param.args[0];
                    ToggleState s = stateMap.get(view);
                    if (s != null) {
                        s.isTouching = pressed;
                        view.postInvalidateOnAnimation();
                    }
                }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (!view.isEnabled()) return;
                    
                    MotionEvent event = (MotionEvent) param.args[0];
                    ToggleState s = stateMap.get(view);
                    if (s == null) {
                        s = new ToggleState();
                        s.progress = ((android.widget.CompoundButton) view).isChecked() ? 1.0f : 0.0f;
                        stateMap.put(view, s);
                    }
                    
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        s.isTouching = true;
                        s.isDragging = false;
                        s.startX = event.getX();
                        s.currentX = event.getX();
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        s.currentX = event.getX();
                        if (Math.abs(s.currentX - s.startX) > 4.0f) s.isDragging = true;
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        s.isTouching = false;
                        s.isDragging = false;
                        view.postInvalidateOnAnimation();
                    }
                }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    android.widget.CompoundButton view = (android.widget.CompoundButton) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];

                    float width = view.getWidth();
                    float height = view.getHeight();
                    if (width <= 0 || height <= 0) return null;

                    try {
                        if (view.getButtonDrawable() != null) {
                            view.setButtonDrawable(null);
                        }
                    } catch (Throwable t) {}

                    Object helper = null;
                    try { helper = XposedHelpers.getObjectField(view, "mHelper"); } catch (Throwable t) {}
                    Object dataSrc = (helper != null) ? helper : view;
                    try { XposedHelpers.setObjectField(dataSrc, "mSlideBar", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mBarOn", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mSliderOn", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mSliderOff", null); } catch(Throwable t){}

                    ToggleState s = stateMap.get(view);
                    if (s == null) {
                        s = new ToggleState();
                        s.progress = view.isChecked() ? 1.0f : 0.0f;
                        stateMap.put(view, s);
                    }

                    long now = System.currentTimeMillis();
                    float dt = Math.min((now - s.lastTime) / 1000.0f, 0.05f);
                    s.lastTime = now;

                    boolean isChecked = view.isChecked();
                    boolean isPressed = view.isPressed();
                    boolean isEnabled = view.isEnabled();

                    float drawW = width;
                    float drawH = height;
                    float offsetX = 0f;
                    float offsetY = 0f;

                    if (width > height * 2.5f && width > 200f) {
                        drawH = Math.min(height * 0.55f, 110f);
                        drawW = drawH * 1.75f;
                        offsetX = width - drawW - (height * 0.15f);
                        offsetY = (height - drawH) / 2.0f;
                    }

                    float trackHeight = drawH * 0.65f;
                    float padding = trackHeight * 0.07f;
                    float restThumbHeight = trackHeight - (padding * 2.0f);
                    float restThumbWidth = restThumbHeight * 1.35f;

                    float targetProgress;
                    if (s.isDragging) {
                        float minCenter = offsetX + padding + (restThumbWidth / 2.0f);
                        float maxCenter = offsetX + drawW - padding - (restThumbWidth / 2.0f);
                        float rawDrag = (s.currentX - minCenter) / (maxCenter - minCenter);
                        targetProgress = Math.max(0f, Math.min(1f, rawDrag));
                    } else {
                        targetProgress = isChecked ? 1.0f : 0.0f;
                    }

                    s.progress += (targetProgress - s.progress) * dt * 25.0f;
                    s.progress = Math.max(0f, Math.min(1f, s.progress));

                    float bloomTarget = (s.isTouching || isPressed) ? 1.0f : 0.0f;
                    s.bloom += (bloomTarget - s.bloom) * dt * 16.0f;
                    s.bloom = Math.max(0f, Math.min(1f, s.bloom));

                    boolean isOverDragging = s.isDragging && (s.currentX < 0 || s.currentX > width);
                    float squishTarget = isOverDragging ? 1.0f : 0.0f;
                    s.squish += (squishTarget - s.squish) * dt * 8.0f;
                    s.squish = Math.max(0f, Math.min(1f, s.squish));

                    if (Math.abs(s.progress - targetProgress) > 0.002f || 
                        Math.abs(s.bloom - bloomTarget) > 0.005f || 
                        Math.abs(s.squish - squishTarget) > 0.005f) {
                        view.postInvalidateOnAnimation();
                    }

                    float trackTop = offsetY + (drawH - trackHeight) / 2.0f;
                    float trackBottom = trackTop + trackHeight;
                    float trackRadius = trackHeight / 2.0f;

                    boolean isDarkMode = (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;
                    if (!isEnabled) {
                        bgPaint.setColor(isChecked ? TRACK_ON_DISABLED : (isDarkMode ? TRACK_OFF_DISABLED : Color.parseColor("#c6c6c6")));
                    } else {
                        bgPaint.setColor((s.progress > 0.5f || s.isDragging) ? TRACK_ON : (isDarkMode ? TRACK_OFF : Color.parseColor("#c6c6c6")));
                    }
                    canvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

                    float baseMaxThumbHeight = drawH * 0.82f;
                    float currentMaxThumbHeight = baseMaxThumbHeight - ((baseMaxThumbHeight - trackHeight) * s.squish);
                    float scaleRatio = currentMaxThumbHeight / restThumbHeight;
                    float currentMaxThumbWidth = restThumbWidth * scaleRatio;
                    currentMaxThumbWidth += (restThumbWidth * 0.15f) * s.squish;

                    float currentThumbHeight = restThumbHeight + (currentMaxThumbHeight - restThumbHeight) * s.bloom;
                    float currentThumbWidth = restThumbWidth + (currentMaxThumbWidth - restThumbWidth) * s.bloom;
                    float thumbRadius = currentThumbHeight / 2.0f;

                    float currentPaddingX = padding - (padding * 0.7f * s.bloom);
                    float minX = offsetX + currentPaddingX + (currentThumbWidth / 2.0f);
                    float maxX = offsetX + drawW - currentPaddingX - (currentThumbWidth / 2.0f);

                    float cx = minX + s.progress * (maxX - minX);
                    float cy = offsetY + (drawH / 2.0f);

                    float thumbLeft = cx - (currentThumbWidth / 2.0f);
                    float thumbRight = cx + (currentThumbWidth / 2.0f);
                    float thumbTop = cy - (currentThumbHeight / 2.0f);
                    float thumbBottom = cy + (currentThumbHeight / 2.0f);

                    if (isEnabled) {
                        shadowPaint.setColor(SHADOW);
                        float shadowOffset = 2.0f + (2.0f * s.bloom);
                        canvas.drawRoundRect(thumbLeft, thumbTop + shadowOffset, thumbRight, thumbBottom + shadowOffset, thumbRadius, thumbRadius, shadowPaint);
                    }

                    thumbPaint.setColor(isEnabled ? THUMB_WHITE : THUMB_DISABLED);
                    canvas.drawRoundRect(thumbLeft, thumbTop, thumbRight, thumbBottom, thumbRadius, thumbRadius, thumbPaint);

                    if (isEnabled) {
                        canvas.drawRoundRect(thumbLeft, thumbTop, thumbRight, thumbBottom, thumbRadius, thumbRadius, rimPaint);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

    // ==========================================
    // SLIDER ENGINE (iOS 26 Style - V9)
    // ==========================================
    private void applyUnifiedSliderEngine(String className, ClassLoader classLoader) {
        if (hookedDynamicClasses.contains(className)) return;
        
        Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
        if (clazz == null) return;
        
        hookedDynamicClasses.add(className);

        // Track touch events for slider kinematics
        try {
            XposedHelpers.findAndHookMethod(clazz, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    MotionEvent event = (MotionEvent) param.args[0];

                    SliderState s = sliderStateMap.get(view);
                    if (s == null) {
                        s = new SliderState();
                        sliderStateMap.put(view, s);
                    }

                    int action = event.getActionMasked();
                    float x = event.getX();

                    if (action == MotionEvent.ACTION_DOWN) {
                        s.isTouching = true;
                        s.isDragging = false;
                        s.touchX = x;
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        s.touchX = x;
                        s.isDragging = true;
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        s.isTouching = false;
                        s.isDragging = false;
                        view.postInvalidateOnAnimation();
                    }
                }
            });
        } catch (Throwable t) {}

        // Custom iOS 26 Rendering
        try {
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];

                    float w = view.getWidth();
                    float h = view.getHeight();
                    if (w <= 0 || h <= 0) return null;

                    // Erase stock assets natively
                    try {
                        Object thumb = XposedHelpers.callMethod(view, "getThumb");
                        if (thumb != null) {
                            XposedHelpers.callMethod(view, "setThumb", new Class[]{android.graphics.drawable.Drawable.class}, (Object) null);
                        }
                    } catch (Throwable e) {}

                    SliderState s = sliderStateMap.get(view);
                    if (s == null) {
                        s = new SliderState();
                        sliderStateMap.put(view, s);
                    }

                    // 1. PROGRESS KINEMATICS
                    int max = 100;
                    try { max = (int) XposedHelpers.callMethod(view, "getMax"); } catch (Throwable e) {}
                    int min = 0;
                    try { min = (int) XposedHelpers.callMethod(view, "getMin"); } catch (Throwable e) {}
                    int progress = 0;
                    try { progress = (int) XposedHelpers.callMethod(view, "getProgress"); } catch (Throwable e) {}

                    float range = max - min;
                    float discreteRatio = (range > 0) ? (float)(progress - min) / range : 0f;
                    discreteRatio = Math.max(0f, Math.min(1f, discreteRatio));

                    if (s.visualRatio < 0.0f) {
                        s.visualRatio = discreteRatio;
                    }

                    boolean isDarkMode = (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;

                    // 2. BLOOM & MASSIVE PILL THUMB DIMENSIONS
                    float bloomTarget = (s.isTouching || s.isDragging) ? 1.0f : 0.0f;
                    s.bloom += (bloomTarget - s.bloom) * 0.22f;

                    float restThumbH = 46.0f; 
                    float restThumbW = 82.0f; 

                    float currentThumbH = restThumbH + (8.0f * s.bloom);
                    float currentThumbW = restThumbW + (40.0f * s.bloom); 
                    float thumbRadius = currentThumbH / 2.0f;

                    // 3. SYMMETRIC TRACK DIMENSIONS & BOTH-SIDE BOUNDARY CONSTRAINTS
                    float baseLeft = 4.0f; 
                    float shrinkAmount = s.bloom * 24.0f; 
                    
                    float trackLeft = baseLeft + shrinkAmount;
                    float trackRight = (w - baseLeft) - shrinkAmount;
                    float trackW = trackRight - trackLeft;

                    float minCx = baseLeft + (currentThumbW / 2.0f);
                    float maxCx = (w - baseLeft) - (currentThumbW / 2.0f);
                    float travelW = maxCx - minCx;

                    float targetRatio = discreteRatio;
                    if (s.isDragging && travelW > 0) {
                        float rawDragRatio = (s.touchX - minCx) / travelW;
                        targetRatio = Math.max(0.0f, Math.min(1.0f, rawDragRatio));
                    }

                    s.visualRatio += (targetRatio - s.visualRatio) * 0.28f;

                    if (Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.visualRatio - targetRatio) > 0.001f) {
                        view.postInvalidateOnAnimation();
                    }

                    float trackHeight = 22.0f; 
                    float trackTop = (h - trackHeight) / 2.0f;
                    float trackBottom = trackTop + trackHeight;
                    float trackRadius = trackHeight / 2.0f;

                    // Inactive Inner Track (Symmetrically shrunk)
                    sliderBgPaint.setColor(isDarkMode ? SLIDER_TRACK_OFF_DARK : SLIDER_TRACK_OFF_LIGHT);
                    canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, trackRadius, trackRadius, sliderBgPaint);

                    // Active Green Inner Track
                    float activeW = trackW * s.visualRatio;
                    if (activeW > 0) {
                        sliderActivePaint.setColor(SLIDER_TRACK_ON);
                        canvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW, trackBottom, trackRadius, trackRadius, sliderActivePaint);
                    }

                    // 4. POSITION & RENDER THUMB WITH 70% OPACITY GLASS
                    float cx = minCx + travelW * s.visualRatio;
                    float cy = h / 2.0f;

                    float thumbL = cx - (currentThumbW / 2.0f);
                    float thumbR = cx + (currentThumbW / 2.0f);
                    float thumbT = cy - (currentThumbH / 2.0f);
                    float thumbB = cy + (currentThumbH / 2.0f);

                    // Shadow & Ambient Border
                    sliderShadowPaint.setColor(SLIDER_SHADOW_COLOR);
                    if (isDarkMode) {
                        canvas.drawRoundRect(thumbL, thumbT + 3.0f, thumbR, thumbB + 3.0f, thumbRadius, thumbRadius, sliderShadowPaint);
                    } else {
                        // Ambient all-sides shadow to make white thumb pop in light mode
                        canvas.drawRoundRect(thumbL - 1.5f, thumbT - 1.5f, thumbR + 1.5f, thumbB + 2.5f, thumbRadius, thumbRadius, sliderShadowPaint);
                    }

                    // Solid White transitioning to 70% Opacity Glass on touch/hold
                    sliderThumbPaint.setColor(SLIDER_THUMB_WHITE);
                    int targetAlpha = 255 - (int)(77 * s.bloom); // 255 down to 178 (70%)
                    sliderThumbPaint.setAlpha(targetAlpha);

                    canvas.drawRoundRect(thumbL, thumbT, thumbR, thumbB, thumbRadius, thumbRadius, sliderThumbPaint);

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        
        // ==========================================
        // GLOBAL VIEW DISPATCHER (Toggle Hijacking)
        // ==========================================
        try {
            Class<?> viewClass = XposedHelpers.findClassIfExists("android.view.View", lpparam.classLoader);
            if (viewClass != null) {
                XposedHelpers.findAndHookMethod(viewClass, "dispatchTouchEvent", MotionEvent.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        String className = view.getClass().getName();
                        
                        boolean isTarget = false;
                        for (String target : TARGET_TOGGLE_CLASSES) {
                            if (className.equals(target)) {
                                isTarget = true;
                                break;
                            }
                        }

                        if (isTarget) {
                            MotionEvent event = (MotionEvent) param.args[0];
                            int action = event.getActionMasked();
                            
                            if (action == MotionEvent.ACTION_DOWN) {
                                android.view.ViewParent parent = view.getParent();
                                if (parent != null) {
                                    parent.requestDisallowInterceptTouchEvent(true);
                                }
                            }
                            
                            XposedHelpers.callMethod(view, "onTouchEvent", event);
                            return true; // Aggressively steal the gesture!
                        }

                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                });
            }
        } catch (Throwable t) {}

        // Apply Toggle visual engine
        for (String className : TARGET_TOGGLE_CLASSES) {
            applyUnifiedToggleEngine(className, lpparam.classLoader);
        }

        // Apply Slider visual engine
        for (String className : TARGET_SLIDER_CLASSES) {
            applyUnifiedSliderEngine(className, lpparam.classLoader);
        }

        // ==========================================
        // DYNAMIC CLASSLOADER CATCHER (SystemUI Plugins)
        // ==========================================
        if (lpparam.packageName.equals("com.android.systemui") || lpparam.packageName.equals("miui.systemui.plugin")) {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.hasThrowable()) return;
                    Class<?> clazz = (Class<?>) param.getResult();
                    if (clazz != null) {
                        String name = clazz.getName();
                        if (name.equals("miui.systemui.controlcenter.widget.SlidingButton") ||
                            name.equals("com.miui.systemui.widget.SlidingButton")) {
                            applyUnifiedToggleEngine(name, clazz.getClassLoader());
                        }
                    }
                }
            });
        }

        // Dynamic Island animations...
        if (lpparam.packageName.equals("miui.systemui.plugin") || lpparam.packageName.equals("com.android.systemui")) {
            try {
                Class<?> delegateClass = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate", lpparam.classLoader);
                if (delegateClass != null) {
                    XposedBridge.hookAllConstructors(delegateClass, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Class<?> folmeEaseClass = XposedHelpers.findClass("miuix.animation.FolmeEase", lpparam.classLoader);
                            Object iosSpring = XposedHelpers.callStaticMethod(folmeEaseClass, "spring", 0.65f, 0.35f);
                            Object fastSpring = XposedHelpers.callStaticMethod(folmeEaseClass, "spring", 0.70f, 0.25f);
                            for (Field field : delegateClass.getDeclaredFields()) {
                                field.setAccessible(true);
                                if (field.getType().getName().contains("EaseStyle")) {
                                    String name = field.getName();
                                    if (name.contains("HIDDEN") || name.contains("APPEAR")) {
                                        field.set(param.thisObject, fastSpring);
                                    } else {
                                        field.set(param.thisObject, iosSpring); 
                                    }
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {}
        }
    }
}
