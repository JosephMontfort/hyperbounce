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

    private static final String[] TARGET_TOGGLE_CLASSES = {
        "miuix.slidingwidget.widget.SlidingButton",
        "miuix.slidingwidget.widget.SlidingSwitchCompat",
        "miuix.slidingwidget.widget.SlidingSwitch",
        "miui.slidingwidget.widget.SlidingButton",
        "miui.widget.SlidingButton",
        "com.miui.systemui.widget.SlidingButton",
        "miui.systemui.widget.SlidingButton"
    };

    private static final HashSet<String> hookedDynamicClasses = new HashSet<>();

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

    private void applyUnifiedToggleEngine(String className, ClassLoader classLoader) {
        if (hookedDynamicClasses.contains(className)) return;
        
        Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
        if (clazz == null) return;
        
        hookedDynamicClasses.add(className);

        // A. Prevent Dialer/Preference Snapping
        try {
            XposedHelpers.findAndHookMethod(clazz, "jumpDrawablesToCurrentState", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null; // Kill native snap to allow smooth animation
                }
            });
        } catch (Throwable t) {}

        // B. Programmatic Tap Trigger (Handles text row clicks perfectly)
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

        // C. Track Touch & Press (Handles text area holds)
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

        // D. PHYSICS STATE TRACKING
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

        // E. Unified Render Engine
        try {
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    android.widget.CompoundButton view = (android.widget.CompoundButton) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];

                    float width = view.getWidth();
                    float height = view.getHeight();
                    if (width <= 0 || height <= 0) return null;

                    // Erase native Android mButtonDrawable (Kills blue stock toggle)
                    try {
                        if (view.getButtonDrawable() != null) {
                            view.setButtonDrawable(null);
                        }
                    } catch (Throwable t) {}

                    // Erase MIUI custom slide assets
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

                    // --- SMART BOUNDS LOGIC (Handles Full Row Detail Panels) ---
                    float drawW = width;
                    float drawH = height;
                    float offsetX = 0f;
                    float offsetY = 0f;

                    if (width > height * 2.5f && width > 200f) {
                        drawH = Math.min(height * 0.55f, 110f);
                        drawW = drawH * 1.75f;
                        offsetX = width - drawW - (height * 0.15f); // Anchor right
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

                    if (!isEnabled) {
                        bgPaint.setColor(isChecked ? TRACK_ON_DISABLED : TRACK_OFF_DISABLED);
                    } else {
                        bgPaint.setColor((s.progress > 0.5f || s.isDragging) ? TRACK_ON : TRACK_OFF);
                    }
                    canvas.drawRoundRect(new RectF(offsetX, trackTop, offsetX + drawW, trackBottom), trackRadius, trackRadius, bgPaint);

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

                    RectF thumbRect = new RectF(thumbLeft, thumbTop, thumbRight, thumbBottom);

                    if (isEnabled) {
                        shadowPaint.setColor(SHADOW);
                        float shadowOffset = 2.0f + (2.0f * s.bloom);
                        canvas.drawRoundRect(new RectF(thumbLeft, thumbTop + shadowOffset, thumbRight, thumbBottom + shadowOffset), thumbRadius, thumbRadius, shadowPaint);
                    }

                    thumbPaint.setColor(isEnabled ? THUMB_WHITE : THUMB_DISABLED);
                    canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);

                    if (isEnabled) {
                        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, rimPaint);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        
        // ==========================================
        // GLOBAL VIEW DISPATCHER (Frida Live Test 4 Port)
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

        // Apply visual engine
        for (String className : TARGET_TOGGLE_CLASSES) {
            applyUnifiedToggleEngine(className, lpparam.classLoader);
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
