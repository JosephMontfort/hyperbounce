package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

    private static final String TAG = "[HyperBounce-Diag] ";

    private static final int TRACK_OFF = Color.parseColor("#3A3A3C");
    private static final int TRACK_ON = Color.parseColor("#30D158");
    private static final int THUMB_WHITE = Color.parseColor("#FFFFFF");
    private static final int SHADOW = Color.parseColor("#25000000");
    private static final int RIM_COLOR = Color.parseColor("#12000000");

    private static final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        shadowPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.5f);
        rimPaint.setColor(RIM_COLOR);
    }

    private static class ToggleState {
        float bloom = 0.0f;
        float squish = 0.0f;
        long last = System.currentTimeMillis();
    }
    private static final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        
        // LOG EVERY PACKAGE LOAD
        XposedBridge.log(TAG + "Loaded in package: " + lpparam.packageName);

        // ==========================================
        // 1. SYSTEM-WIDE iOS TOGGLES (DIAGNOSTIC HOOKS)
        // ==========================================
        
        // Check 1: SlidingButton
        Class<?> slidingBtnClass = XposedHelpers.findClassIfExists("miuix.slidingwidget.widget.SlidingButton", lpparam.classLoader);
        if (slidingBtnClass != null) {
            XposedBridge.log(TAG + "--> FOUND SlidingButton in: " + lpparam.packageName);
            try {
                XposedHelpers.findAndHookMethod(slidingBtnClass, "onDraw", Canvas.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        android.widget.CompoundButton view = (android.widget.CompoundButton) param.thisObject;
                        view.setButtonDrawable((android.graphics.drawable.Drawable) null);
                        view.setBackground(null);
                        view.setAlpha(view.isEnabled() ? 1.0f : 0.4f);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Failed SlidingButton hook in " + lpparam.packageName + ": " + t.getMessage());
            }
        } else {
            XposedBridge.log(TAG + "x-- SlidingButton NOT found in: " + lpparam.packageName);
        }

        // Check 2: SlidingSwitchCompat
        Class<?> slidingSwitchClass = XposedHelpers.findClassIfExists("miuix.slidingwidget.widget.SlidingSwitchCompat", lpparam.classLoader);
        if (slidingSwitchClass != null) {
            XposedBridge.log(TAG + "--> FOUND SlidingSwitchCompat in: " + lpparam.packageName);
        }

        // Check 3: SlidingButtonHelper
        Class<?> helperClass = XposedHelpers.findClassIfExists("miuix.slidingwidget.widget.SlidingButtonHelper", lpparam.classLoader);
        if (helperClass != null) {
            XposedBridge.log(TAG + "===> SUCCESS: FOUND SlidingButtonHelper in: " + lpparam.packageName);
            
            try {
                XposedHelpers.findAndHookMethod(helperClass, "initAnim", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object moveAnim = XposedHelpers.getObjectField(param.thisObject, "mSliderMoveAnim");
                        if (moveAnim != null) {
                            Object spring = XposedHelpers.callMethod(moveAnim, "getSpring");
                            if (spring != null) {
                                XposedHelpers.callMethod(spring, "setStiffness", 450.0f);
                                XposedHelpers.callMethod(spring, "setDampingRatio", 0.88f);
                            }
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(helperClass, "onDraw", Canvas.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Object obj = param.thisObject;
                        Canvas canvas = (Canvas) param.args[0];

                        XposedHelpers.setObjectField(obj, "mSlideBar", null);
                        XposedHelpers.setObjectField(obj, "mBarOn", null);
                        XposedHelpers.setObjectField(obj, "mSliderOn", null);
                        XposedHelpers.setObjectField(obj, "mSliderOff", null);
                        XposedHelpers.setObjectField(obj, "mMaskCheckedSlideBar", null);
                        XposedHelpers.setObjectField(obj, "mMaskUnCheckedSlideBar", null);

                        int width = XposedHelpers.getIntField(obj, "mWidth");
                        int height = XposedHelpers.getIntField(obj, "mHeight");
                        if (width <= 0 || height <= 0) return null;

                        int offset = XposedHelpers.getIntField(obj, "mSliderOffset");
                        boolean tracking = XposedHelpers.getBooleanField(obj, "mTracking");
                        View view = (View) XposedHelpers.getObjectField(obj, "mView");

                        int posStart = XposedHelpers.getIntField(obj, "mSliderPositionStart");
                        int posEnd = XposedHelpers.getIntField(obj, "mSliderPositionEnd");

                        float maxTravel = (posEnd - posStart) > 0 ? (posEnd - posStart) : (width - height);
                        float rawProgress = maxTravel > 0 ? (float) (offset - posStart) / maxTravel : 0f;
                        float progress = Math.max(0f, Math.min(1f, rawProgress));

                        ToggleState s = stateMap.get(obj);
                        if (s == null) {
                            s = new ToggleState();
                            stateMap.put(obj, s);
                        }

                        long now = System.currentTimeMillis();
                        float dt = Math.min((now - s.last) / 1000.0f, 0.05f);
                        s.last = now;

                        float bloomTarget = tracking ? 1.0f : 0.0f;
                        s.bloom = Math.max(0f, Math.min(1f, s.bloom + (bloomTarget - s.bloom) * dt * 14.0f));

                        boolean isOverDragging = tracking && (rawProgress < -0.02f || rawProgress > 1.02f);
                        float squishTarget = isOverDragging ? 1.0f : 0.0f;
                        s.squish = Math.max(0f, Math.min(1f, s.squish + (squishTarget - s.squish) * dt * 6.0f));

                        if ((Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.squish - squishTarget) > 0.005f) && view != null) {
                            view.postInvalidateOnAnimation();
                        }

                        float trackHeight = height * 0.65f;
                        float trackTop = (height - trackHeight) / 2.0f;
                        float trackBottom = trackTop + trackHeight;
                        float trackRadius = trackHeight / 2.0f;

                        bgPaint.setColor((progress > 0.5f || tracking) ? TRACK_ON : TRACK_OFF);
                        canvas.drawRoundRect(new RectF(0, trackTop, width, trackBottom), trackRadius, trackRadius, bgPaint);

                        float padding = trackHeight * 0.07f;
                        float restThumbHeight = trackHeight - (padding * 2.0f);
                        float restThumbWidth = restThumbHeight * 1.35f;

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
                        
                        float cx = minX + progress * (maxX - minX);
                        float cy = height / 2.0f;

                        float thumbLeft = cx - (currentThumbWidth / 2.0f);
                        float thumbRight = cx + (currentThumbWidth / 2.0f);
                        float thumbTop = cy - (currentThumbHeight / 2.0f);
                        float thumbBottom = cy + (currentThumbHeight / 2.0f);

                        RectF thumbRect = new RectF(thumbLeft, thumbTop, thumbRight, thumbBottom);

                        shadowPaint.setColor(SHADOW);
                        float shadowOffset = 2.0f + (2.0f * s.bloom);
                        canvas.drawRoundRect(new RectF(thumbLeft, thumbTop + shadowOffset, thumbRight, thumbBottom + shadowOffset), thumbRadius, thumbRadius, shadowPaint);

                        thumbPaint.setColor(THUMB_WHITE);
                        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);
                        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, rimPaint);

                        return null; 
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Error hooking SlidingButtonHelper in " + lpparam.packageName + ": " + t.getMessage());
            }
        } else {
            XposedBridge.log(TAG + "x-- SlidingButtonHelper NOT found in: " + lpparam.packageName);
        }

        // ==========================================
        // 2. DYNAMIC ISLAND (PLUGIN & SYSTEMUI)
        // ==========================================
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
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Physics Override Error: " + t.getMessage());
            }

            try {
                Class<?> bgViewClass = XposedHelpers.findClassIfExists("miui.systemui.dynamicisland.DynamicIslandBackgroundView", lpparam.classLoader);
                if (bgViewClass != null) {
                    XposedHelpers.findAndHookMethod(bgViewClass, "layout", int.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int top = (int) param.args[1];
                            int bottom = (int) param.args[3];
                            if ((bottom - top) > 600) {
                                param.args[3] = top + 600;
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(bgViewClass, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            float targetAlpha = (float) param.args[0];
                            if (targetAlpha > 0.05f && targetAlpha < 1.0f) {
                                param.args[0] = 1.0f;
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Layout Glitch Fix Error: " + t.getMessage());
            }
        }

        // ==========================================
        // 3. LAUNCHER FLUIDITY
        // ==========================================
        if (lpparam.packageName.equals("com.miui.home")) {
            try {
                Class<?> rectFSpringAnimClass = XposedHelpers.findClassIfExists("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader);
                if (rectFSpringAnimClass != null) {
                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setDamping", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = 0.45f; 
                        }
                    });
                    XposedHelpers.findAndHookMethod(rectFSpringAnimClass, "setStiffness", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            float currentStiffness = (float) param.args[0];
                            if (currentStiffness < 300.0f) {
                                param.args[0] = 350.0f;
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Launcher Hook Error: " + t.getMessage());
            }
        }
    }
}
