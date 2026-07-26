package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

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

    private static class ToggleState {
        float progress = 0.0f;
        float bloom = 0.0f;
        float squish = 0.0f;
        float touchX = -1.0f;
        boolean springPatched = false;
        long last = System.currentTimeMillis();
    }
    private static final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();

    private void hookUniversalToggle(String className, ClassLoader classLoader) {
        Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
        if (clazz == null) return;

        // 1. Hook Touch Events to manually track finger X-coordinates
        try {
            XposedHelpers.findAndHookMethod(clazz, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    MotionEvent event = (MotionEvent) param.args[0];
                    ToggleState s = stateMap.get(view);
                    if (s != null) {
                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                            s.touchX = event.getX();
                            view.postInvalidateOnAnimation();
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            s.touchX = -1.0f;
                            view.postInvalidateOnAnimation();
                        }
                    }
                }
            });
        } catch (Throwable t) {}

        // 2. Hook Draw
        try {
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    android.widget.CompoundButton view = (android.widget.CompoundButton) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];

                    int width = view.getWidth();
                    int height = view.getHeight();
                    if (width <= 0 || height <= 0) return null;

                    Object helper = null;
                    try { helper = XposedHelpers.getObjectField(view, "mHelper"); } catch (Throwable t) {}
                    Object dataSrc = (helper != null) ? helper : view;

                    try { XposedHelpers.setObjectField(dataSrc, "mSlideBar", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mBarOn", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mSliderOn", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mSliderOff", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mMaskCheckedSlideBar", null); } catch(Throwable t){}
                    try { XposedHelpers.setObjectField(dataSrc, "mMaskUnCheckedSlideBar", null); } catch(Throwable t){}

                    boolean tracking = false;
                    try { tracking = XposedHelpers.getBooleanField(dataSrc, "mTracking"); } catch(Throwable t){}

                    ToggleState s = stateMap.get(view);
                    if (s == null) {
                        s = new ToggleState();
                        s.progress = view.isChecked() ? 1.0f : 0.0f;
                        stateMap.put(view, s);
                    }

                    if (!s.springPatched) {
                        try {
                            Object moveAnim = XposedHelpers.getObjectField(dataSrc, "mSliderMoveAnim");
                            if (moveAnim != null) {
                                Object spring = XposedHelpers.callMethod(moveAnim, "getSpring");
                                if (spring != null) {
                                    XposedHelpers.callMethod(spring, "setStiffness", 450.0f);
                                    XposedHelpers.callMethod(spring, "setDampingRatio", 0.88f);
                                    s.springPatched = true;
                                }
                            }
                        } catch (Throwable t) {}
                    }

                    long now = System.currentTimeMillis();
                    float dt = Math.min((now - s.last) / 1000.0f, 0.05f);
                    s.last = now;

                    boolean isChecked = view.isChecked();
                    boolean isPressed = view.isPressed();
                    float trackHeight = height * 0.65f;
                    float padding = trackHeight * 0.07f;
                    float restThumbHeight = trackHeight - (padding * 2.0f);
                    float restThumbWidth = restThumbHeight * 1.35f;

                    // DYNAMIC DRAG CALCULATION (Bypasses Xiaomi Engine)
                    float targetProgress;
                    if (tracking && s.touchX != -1.0f) {
                        float minCenter = padding + (restThumbWidth / 2.0f);
                        float maxCenter = width - padding - (restThumbWidth / 2.0f);
                        float rawDrag = (s.touchX - minCenter) / (maxCenter - minCenter);
                        targetProgress = Math.max(0f, Math.min(1f, rawDrag));
                    } else {
                        targetProgress = isChecked ? 1.0f : 0.0f;
                    }

                    s.progress = Math.max(0f, Math.min(1f, s.progress + (targetProgress - s.progress) * dt * 25.0f));

                    float bloomTarget = (tracking || isPressed) ? 1.0f : 0.0f;
                    s.bloom = Math.max(0f, Math.min(1f, s.bloom + (bloomTarget - s.bloom) * dt * 14.0f));

                    boolean isOverDragging = tracking && s.touchX != -1.0f && (s.touchX < 0 || s.touchX > width);
                    float squishTarget = isOverDragging ? 1.0f : 0.0f;
                    s.squish = Math.max(0f, Math.min(1f, s.squish + (squishTarget - s.squish) * dt * 6.0f));

                    if (Math.abs(s.progress - targetProgress) > 0.002f || 
                        Math.abs(s.bloom - bloomTarget) > 0.005f || 
                        Math.abs(s.squish - squishTarget) > 0.005f) {
                        view.postInvalidateOnAnimation();
                    }

                    boolean isEnabled = view.isEnabled();
                    float trackTop = (height - trackHeight) / 2.0f;
                    float trackBottom = trackTop + trackHeight;
                    float trackRadius = trackHeight / 2.0f;

                    if (!isEnabled) {
                        bgPaint.setColor(isChecked ? TRACK_ON_DISABLED : TRACK_OFF_DISABLED);
                    } else {
                        bgPaint.setColor((s.progress > 0.5f || tracking) ? TRACK_ON : TRACK_OFF);
                    }
                    canvas.drawRoundRect(new RectF(0, trackTop, width, trackBottom), trackRadius, trackRadius, bgPaint);

                    float baseMaxThumbHeight = height * 0.82f;
                    float currentMaxThumbHeight = baseMaxThumbHeight - ((baseMaxThumbHeight - trackHeight) * s.squish);
                    float scaleRatio = currentMaxThumbHeight / restThumbHeight;
                    float currentMaxThumbWidth = restThumbWidth * scaleRatio;
                    currentMaxThumbWidth += (restThumbWidth * 0.15f) * s.squish;

                    float currentThumbHeight = restThumbHeight + (currentMaxThumbHeight - restThumbHeight) * s.bloom;
                    float currentThumbWidth = restThumbWidth + (currentMaxThumbWidth - restThumbWidth) * s.bloom;
                    float thumbRadius = currentThumbHeight / 2.0f;

                    float currentPaddingX = padding - (padding * 0.7f * s.bloom);
                    float minX = currentPaddingX + (currentThumbWidth / 2.0f);
                    float maxX = width - currentPaddingX - (currentThumbWidth / 2.0f);
                    
                    float cx = minX + s.progress * (maxX - minX);
                    float cy = height / 2.0f;

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
        hookUniversalToggle("miuix.slidingwidget.widget.SlidingButton", lpparam.classLoader);
        hookUniversalToggle("miuix.slidingwidget.widget.SlidingSwitchCompat", lpparam.classLoader);
        hookUniversalToggle("miuix.slidingwidget.widget.SlidingSwitch", lpparam.classLoader);
    }
}
