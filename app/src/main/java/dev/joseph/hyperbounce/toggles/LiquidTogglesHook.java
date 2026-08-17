package dev.joseph.hyperbounce.toggles;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public class LiquidTogglesHook extends XposedModule {

    private static final int TRACK_ON = Color.parseColor("#30D158");
    private static final int TRACK_OFF_DARK = Color.parseColor("#39393D");
    private static final int TRACK_OFF_LIGHT = Color.parseColor("#E9E9EA");

    private static final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint baseThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        bgPaint.setStyle(Paint.Style.FILL);
        activePaint.setStyle(Paint.Style.FILL);
        baseThumbPaint.setStyle(Paint.Style.FILL);
        baseThumbPaint.setColor(Color.WHITE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.parseColor("#30000000"));
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.parseColor("#15FFFFFF"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#75FFFFFF"));
        strokePaint.setStrokeWidth(3.0f);
        bmpPaint.setFilterBitmap(true);
    }

    private static final String[] TARGET_TOGGLE_CLASSES = {
            "miuix.slidingwidget.widget.SlidingButton",
            "miuix.slidingwidget.widget.SlidingSwitchCompat",
            "miuix.slidingwidget.widget.SlidingSwitch",
            "miui.slidingwidget.widget.SlidingButton",
            "miui.widget.SlidingButton",
            "com.miui.systemui.widget.SlidingButton",
            "miui.systemui.widget.SlidingButton",
            "fan.slidingwidget.widget.SlidingButton"
    };

    private static final String[] TARGET_SLIDER_CLASSES = {
            "miuix.androidbasewidget.widget.SeekBar",
            "fan.androidbase.widget.SeekBar"
    };

    private static final boolean USE_LIQUID_GLASS_TOGGLE = true;
    private static final boolean USE_LIQUID_GLASS_SLIDER = true;

    private final Set<Class<?>> hookedDynamicClasses =
            Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());
    private final WeakHashMap<Object, SliderState> sliderStateMap = new WeakHashMap<>();

    private interface HookAction {
        Object run(XposedInterface.Chain chain) throws Throwable;
    }

    private static class GlassState {
        Bitmap trackBitmap;
        Canvas trackCanvas;
        RenderNode blurNode;
        RenderNode glassNode;
        RuntimeShader shader;
        boolean shaderInitialized;
        float bloom;
        float bloomVelocity;
        float bubbleSquish;
        float bubbleVelocity;
        float lastX;
        float density = 1.0f;
    }

    private static class SliderState extends GlassState {
        float visualRatio = -1.0f;
        float touchX;
        boolean isTouching;
        boolean isDragging;
        long lastTime = System.currentTimeMillis();
    }

    private static int blendColors(int color1, int color2, float ratio) {
        float inverse = 1.0f - ratio;
        float a = Color.alpha(color1) * inverse + Color.alpha(color2) * ratio;
        float r = Color.red(color1) * inverse + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverse + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverse + Color.blue(color2) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private static void disableClipping(View view) {
        if (view == null) return;
        view.setLayerType(View.LAYER_TYPE_NONE, null);
        view.setClipToOutline(false);
        view.setClipBounds(null);

        ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof View) {
            View pView = (View) parent;
            String className = pView.getClass().getName();
            if (className.contains("ActionBar") || className.contains("RecyclerView") || className.contains("DecorView")) break;
            pView.setClipToOutline(false);
            if (pView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) pView;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
            }
            parent = pView.getParent();
            depth++;
            if (depth > 16) break;
        }
    }

    private static final String AGSL_SHADER_SLIDER =
            "uniform shader content;\n" +
            "uniform float thumbL;\n" +
            "uniform float thumbT;\n" +
            "uniform float thumbR;\n" +
            "uniform float thumbB;\n" +
            "uniform float refractionHeight;\n" +
            "half4 main(float2 fragCoord) {\n" +
            "    vec2 thumbSize = vec2(thumbR - thumbL, thumbB - thumbT);\n" +
            "    vec2 p = fragCoord - vec2(thumbL, thumbT);\n" +
            "    if (p.x < 0.0 || p.y < 0.0 || p.x > thumbSize.x || p.y > thumbSize.y) {\n" +
            "        return content.eval(fragCoord);\n" +
            "    }\n" +
            "    vec2 center = vec2(thumbL, thumbT) + thumbSize * 0.5;\n" +
            "    vec2 diff = fragCoord - center;\n" +
            "    vec2 distFromEdge = min(p, thumbSize - p);\n" +
            "    float edgeDist = min(distFromEdge.x, distFromEdge.y);\n" +
            "    float factor = smoothstep(0.0, refractionHeight, edgeDist);\n" +
            "    float dist = length(diff);\n" +
            "    vec2 dir = dist > 0.0 ? diff / dist : vec2(0.0);\n" +
            "    vec2 refractOffset = dir * (1.0 - factor) * 65.0;\n" +
            "    half4 color = content.eval(fragCoord - refractOffset);\n" +
            "    half4 basePixel = content.eval(fragCoord);\n" +
            "    color.rgb = mix(color.rgb, vec3(1.0), 0.12);\n" +
            "    color.rgb *= mix(0.70, 1.0, factor);\n" +
            "    float rimFactor = smoothstep(12.0, 0.0, edgeDist);\n" +
            "    vec3 glowColor = mix(vec3(1.0), basePixel.rgb, 0.85);\n" +
            "    color.rgb = mix(color.rgb, glowColor, rimFactor * 0.90);\n" +
            "    float rim = pow(1.0 - factor, 2.5);\n" +
            "    color.rgb += rim * 0.40;\n" +
            "    return color;\n" +
            "}";

    private void renderLiquidGlassThumb(Canvas canvas, GlassState s, int w, int h,
                                        float thumbL, float thumbT, float thumbR,
                                        float thumbB, float thumbRadius) {
        if (!canvas.isHardwareAccelerated()) return;
        RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);

        if (s.blurNode == null) s.blurNode = new RenderNode("BlurNode");
        if (s.glassNode == null) s.glassNode = new RenderNode("GlassNode");

        if (!s.shaderInitialized) {
            RenderEffect blurFx = RenderEffect.createBlurEffect(
                    55.0f, 55.0f, Shader.TileMode.CLAMP);
            s.blurNode.setRenderEffect(blurFx);
            try {
                s.shader = new RuntimeShader(AGSL_SHADER_SLIDER);
                RenderEffect shaderFx =
                        RenderEffect.createRuntimeShaderEffect(s.shader, "content");
                s.glassNode.setRenderEffect(shaderFx);
                s.shaderInitialized = true;
            } catch (Throwable ignored) {
            }
        }

        if (s.shaderInitialized) {
            s.shader.setFloatUniform("thumbL", thumbL);
            s.shader.setFloatUniform("thumbT", thumbT);
            s.shader.setFloatUniform("thumbR", thumbR);
            s.shader.setFloatUniform("thumbB", thumbB);
            s.shader.setFloatUniform("refractionHeight", 34.0f);
        }

        int exp = 100;
        s.blurNode.setPosition(0, 0, w + exp * 2, h + exp * 2);
        Canvas rcBlur = s.blurNode.beginRecording(w + exp * 2, h + exp * 2);
        rcBlur.translate(exp, exp);
        if (s.trackBitmap != null) rcBlur.drawBitmap(s.trackBitmap, 0.0f, 0.0f, bmpPaint);
        s.blurNode.endRecording();

        s.glassNode.setPosition(0, 0, w + exp * 2, h + exp * 2);
        Canvas rcGlass = s.glassNode.beginRecording(w + exp * 2, h + exp * 2);
        rcGlass.drawRenderNode(s.blurNode);
        s.glassNode.endRecording();

        Path clipPath = new Path();
        clipPath.addRoundRect(roundRectF, thumbRadius, thumbRadius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.translate(-exp, -exp);
        canvas.drawRenderNode(s.glassNode);
        canvas.restore();

        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, highlightPaint);
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = clazz.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void installHook(Executable executable, XposedInterface.Hooker hooker) {
        try {
            hook(executable).intercept(hooker);
        } catch (Throwable t) {
            log(4, "HyperBounce", "Failed to hook " + executable, t);
        }
    }

    private void hookMethod(Class<?> clazz, String name, Class<?>[] parameterTypes,
                            XposedInterface.Hooker hooker) {
        try {
            installHook(findMethod(clazz, name, parameterTypes), hooker);
        } catch (Throwable t) {
        }
    }

    private void hookAllConstructors(Class<?> clazz, XposedInterface.Hooker hooker) {
        try {
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                installHook(constructor, hooker);
            }
        } catch (Throwable t) {
            log(4, "HyperBounce", "Failed to hook constructors for " + clazz, t);
        }
    }

    private void applyDialerToggleEngine(ClassLoader classLoader) {
        try {
            Class<?> customPrefClass = findClassIfExists(
                    "com.android.phone.custompreferences.CustomCheckBoxPreference", classLoader);
            if (customPrefClass == null) return;

            Class<?> holderClass = findClassIfExists(
                    "androidx.preference.PreferenceViewHolder", classLoader);
            if (holderClass == null) return;

            hookMethod(customPrefClass, "onBindViewHolder",
                    new Class<?>[]{holderClass},
                    chain -> {
                        Object preference = chain.getThisObject();
                        Object holder = chain.getArg(0);
                        Object result = chain.proceed();

                        View itemView = null;
                        try {
                            itemView = (View) findField(holder.getClass(), "itemView").get(holder);
                        } catch (Throwable ignored) {
                        }
                        if (itemView == null) return result;

                        ViewGroup widgetFrame = itemView.findViewById(android.R.id.widget_frame);
                        if (widgetFrame == null) return result;

                        View oldToggle = itemView.findViewById(android.R.id.checkbox);
                        if (oldToggle != null) oldToggle.setVisibility(View.GONE);

                        View customToggle = widgetFrame.findViewWithTag("hyperbounce_liberated");
                        if (customToggle == null) {
                            android.content.Context context = itemView.getContext();
                            Class<?> sbClass = findClassIfExists(
                                    "miuix.slidingwidget.widget.SlidingButton", classLoader);
                            if (sbClass == null) return result;

                            Constructor<?> selected = null;
                            for (Constructor<?> c : sbClass.getDeclaredConstructors()) {
                                Class<?>[] p = c.getParameterTypes();
                                if (p.length == 1 && p[0].isAssignableFrom(context.getClass())) {
                                    selected = c;
                                    break;
                                }
                                if (p.length == 1 &&
                                        android.content.Context.class.isAssignableFrom(p[0])) {
                                    selected = c;
                                    break;
                                }
                            }
                            if (selected == null) return result;

                            selected.setAccessible(true);
                            customToggle = (View) selected.newInstance(context);
                            customToggle.setTag("hyperbounce_liberated");
                            customToggle.setClickable(true);
                            customToggle.setFocusable(true);
                            customToggle.setLongClickable(true);

                            final Object prefFinal = preference;
                            ((android.widget.CompoundButton) customToggle)
                                    .setOnCheckedChangeListener((buttonView, isChecked) -> {
                                        try {
                                            Method isCheckedMethod =
                                                    findMethod(prefFinal.getClass(), "isChecked");
                                            boolean currentPref =
                                                    (boolean) invoke(isCheckedMethod, prefFinal);
                                            if (currentPref != isChecked) return;

                                            Method callChange =
                                                    findMethod(prefFinal.getClass(), "callChangeListener",
                                                            boolean.class);
                                            boolean canChange =
                                                    (boolean) invoke(callChange, prefFinal, isChecked);
                                            if (canChange) {
                                                invoke(findMethod(prefFinal.getClass(), "setChecked",
                                                        boolean.class), prefFinal, isChecked);
                                            } else {
                                                buttonView.setChecked(!isChecked);
                                            }
                                        } catch (Throwable ignored) {
                                        }
                                    });

                            widgetFrame.addView(customToggle);
                        }

                        boolean isChecked = (boolean) invoke(
                                findMethod(preference.getClass(), "isChecked"), preference);
                        android.widget.CompoundButton btn =
                                (android.widget.CompoundButton) customToggle;
                        if (btn.isChecked() != isChecked) btn.setChecked(isChecked);
                        return result;
                    });
        } catch (Throwable ignored) {
        }
    }

    private void applyUnifiedToggleEngine(String className, ClassLoader classLoader) {
        Class<?> clazz = findClassIfExists(className, classLoader);
        if (clazz == null) return;
        synchronized (hookedDynamicClasses) {
            if (!hookedDynamicClasses.add(clazz)) return;
        }

        hookMethod(clazz, "jumpDrawablesToCurrentState", new Class<?>[]{},
                chain -> null);

        hookMethod(clazz, "setChecked", new Class<?>[]{boolean.class}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE) {
                LiquidToggleHost.syncChecked((View) chain.getThisObject());
            }
            return result;
        });

        hookMethod(clazz, "setVisibility", new Class<?>[]{int.class}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE) {
                int vis = (Integer) chain.getArg(0);
                LiquidToggleHost.syncVisibility((View) chain.getThisObject(), vis == View.VISIBLE);
            }
            return result;
        });

        hookMethod(clazz, "onDetachedFromWindow", new Class<?>[]{}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE) {
                LiquidToggleHost.remove((View) chain.getThisObject());
            }
            return result;
        });

        hookMethod(clazz, "onTouchEvent", new Class<?>[]{MotionEvent.class}, chain -> {
            if (USE_LIQUID_GLASS_TOGGLE) {
                View view = (View) chain.getThisObject();
                if (!view.isEnabled()) return false;
                MotionEvent event = (MotionEvent) chain.getArg(0);
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    ViewParent parent = view.getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                }
                LiquidToggleHost.forwardTouchEvent(view, event);
                return true;
            }
            return chain.proceed();
        });

        hookMethod(clazz, "onDraw", new Class<?>[]{android.graphics.Canvas.class}, chain -> {
            android.widget.CompoundButton view =
                    (android.widget.CompoundButton) chain.getThisObject();

            if (!view.isClickable()) view.setClickable(true);
            if (!view.isLongClickable()) view.setLongClickable(true);

            int width = view.getWidth(), height = view.getHeight();
            if (width <= 0 || height <= 0) return null;

            if (USE_LIQUID_GLASS_TOGGLE) {
                LiquidToggleHost.ensure(view);
                return null;
            }
            return chain.proceed();
        });
    }

    private void applyUnifiedSliderEngine(String className, ClassLoader classLoader) {
        Class<?> clazz = findClassIfExists(className, classLoader);
        if (clazz == null) return;
        synchronized (hookedDynamicClasses) {
            if (!hookedDynamicClasses.add(clazz)) return;
        }

        hookMethod(clazz, "setVisibility", new Class<?>[]{int.class}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_SLIDER) {
                int vis = (Integer) chain.getArg(0);
                dev.joseph.hyperbounce.toggles.LiquidSliderHost.syncVisibility((View) chain.getThisObject(), vis == View.VISIBLE);
            }
            return result;
        });

        hookMethod(clazz, "onDetachedFromWindow", new Class<?>[]{}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_SLIDER) {
                dev.joseph.hyperbounce.toggles.LiquidSliderHost.remove((View) chain.getThisObject());
            }
            return result;
        });

        hookMethod(clazz, "onTouchEvent", new Class<?>[]{MotionEvent.class}, chain -> {
            if (USE_LIQUID_GLASS_SLIDER) {
                View view = (View) chain.getThisObject();
                if (!view.isEnabled()) return false;
                MotionEvent event = (MotionEvent) chain.getArg(0);
                int action = event.getActionMasked();
                
                if (action == MotionEvent.ACTION_DOWN) {
                    ViewParent parent = view.getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    dev.joseph.hyperbounce.toggles.LiquidSliderHost.setTrackingTouch(view, true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    dev.joseph.hyperbounce.toggles.LiquidSliderHost.setTrackingTouch(view, false);
                }
                
                // DUAL-TOUCH ARCHITECTURE:
                // 1. Forward offset touch to Compose for liquid glass visual physics
                dev.joseph.hyperbounce.toggles.LiquidSliderHost.forwardTouchEvent(view, event);
                
                // 2. Feed original touch into Native Slider to process audio / exact system values!
                // Clamp Y to the exact center so the native SeekBar always tracks, even when touching the bloomed padding
                float originalY = event.getY();
                event.setLocation(event.getX(), view.getHeight() / 2.0f);
                Object result = chain.proceed();
                event.setLocation(event.getX(), originalY);
                
                return true;
            }

            // FALLBACK TO THE FULLY CUSTOM SLIDER ENGINE
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            MotionEvent event = (MotionEvent) chain.getArg(0);
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
                s.lastX = x;
                view.postInvalidateOnAnimation();
            } else if (action == MotionEvent.ACTION_MOVE) {
                s.touchX = x;
                s.isDragging = true;
                view.postInvalidateOnAnimation();
            } else if (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_CANCEL) {
                s.isTouching = false;
                s.isDragging = false;
                view.postInvalidateOnAnimation();
            }
            return result;
        });

        hookMethod(clazz, "onDraw", new Class<?>[]{android.graphics.Canvas.class}, chain -> {
            View view = (View) chain.getThisObject();
            Canvas canvas = (Canvas) chain.getArg(0);
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return null;

            if (USE_LIQUID_GLASS_SLIDER) {
                dev.joseph.hyperbounce.toggles.LiquidSliderHost.ensure(view);
                return null;
            }

            // FALLBACK TO THE FULLY CUSTOM SLIDER ENGINE

            try {
                Method getThumb = findMethod(view.getClass(), "getThumb");
                Object thumb = invoke(getThumb, view);
                if (thumb != null) {
                    Method setThumb = findMethod(view.getClass(), "setThumb",
                            android.graphics.drawable.Drawable.class);
                    invoke(setThumb, view, (Object) null);
                }
            } catch (Throwable ignored) {
            }

            SliderState s = sliderStateMap.get(view);
            if (s == null) {
                s = new SliderState();
                sliderStateMap.put(view, s);
            }

            int max = 100, min = 0, progress = 0;
            try { max = (Integer) invoke(findMethod(view.getClass(), "getMax"), view); } catch (Throwable ignored) {}
            try { min = (Integer) invoke(findMethod(view.getClass(), "getMin"), view); } catch (Throwable ignored) {}
            try { progress = (Integer) invoke(findMethod(view.getClass(), "getProgress"), view); } catch (Throwable ignored) {}

            float range = max - min;
            float discreteRatio = range > 0 ? (float) (progress - min) / range : 0f;
            discreteRatio = Math.max(0f, Math.min(1f, discreteRatio));
            if (s.visualRatio < 0.0f) s.visualRatio = discreteRatio;

            boolean isDarkMode =
                    (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;

            long now = System.currentTimeMillis();
            float dt = Math.min((now - s.lastTime) / 1000.0f, 0.05f);
            s.lastTime = now;

            float bloomTarget = (s.isTouching || s.isDragging) ? 1.0f : 0.0f;
            float bloomForce = (bloomTarget - s.bloom) * 250.0f -
                    (s.bloomVelocity * 16.0f);
            s.bloomVelocity += bloomForce * dt;
            s.bloom += s.bloomVelocity * dt;

            float dx = s.touchX - s.lastX;
            s.lastX = s.touchX;
            float velocity = Math.abs(dx) / Math.max(dt, 0.001f);
            float targetBubble = s.isDragging ? Math.min(velocity / 800.0f, 0.75f) : 0.0f;
            float springForce = (targetBubble - s.bubbleSquish) * 250.0f -
                    (s.bubbleVelocity * 12.0f);
            s.bubbleVelocity += springForce * dt;
            s.bubbleSquish += s.bubbleVelocity * dt;
            if (s.isTouching && !s.isDragging) s.bubbleVelocity += s.bloomVelocity * 0.20f;

            float currentThumbH = 46.0f + s.bloom;
            float currentThumbW = 82.0f + (8.0f * s.bloom);
            currentThumbW *= (1.0f + s.bubbleSquish * 0.35f);
            currentThumbH *= (1.0f - s.bubbleSquish * 0.15f);
            float thumbRadius = currentThumbH / 2.0f;

            float baseLeft = 4.0f, shrinkAmount = s.bloom * 16.0f;
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
            s.visualRatio += (targetRatio - s.visualRatio) * dt * 18.0f;

            if (Math.abs(s.bloom - bloomTarget) > 0.005f ||
                    Math.abs(s.bloomVelocity) > 0.01f ||
                    Math.abs(s.visualRatio - targetRatio) > 0.001f ||
                    Math.abs(s.bubbleVelocity) > 0.01f ||
                    s.bubbleSquish > 0.005f) {
                view.postInvalidateOnAnimation();
            }

            float trackHeight = 22.0f;
            float trackTop = (h - trackHeight) / 2.0f;
            float trackBottom = trackTop + trackHeight;
            float trackRadius = trackHeight / 2.0f;

            bgPaint.setColor(isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
            canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom,
                    trackRadius, trackRadius, bgPaint);

            float activeW = trackW * s.visualRatio;
            if (activeW > 0) {
                activePaint.setColor(TRACK_ON);
                canvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW,
                        trackBottom, trackRadius, trackRadius, activePaint);
            }

            if (s.trackBitmap == null ||
                    s.trackBitmap.getWidth() != w ||
                    s.trackBitmap.getHeight() != h) {
                s.trackBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                s.trackCanvas = new Canvas(s.trackBitmap);
            }
            s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
            s.trackCanvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom,
                    trackRadius, trackRadius, bgPaint);
            if (activeW > 0) {
                s.trackCanvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW,
                        trackBottom, trackRadius, trackRadius, activePaint);
            }

            float cx = minCx + travelW * s.visualRatio;
            float cy = h / 2.0f;
            float thumbL = cx - currentThumbW / 2.0f;
            float thumbR = cx + currentThumbW / 2.0f;
            float thumbT = cy - currentThumbH / 2.0f;
            float thumbB = cy + currentThumbH / 2.0f;

            int shadowAlpha = (int) (48 * (1.0f -
                    Math.max(0f, Math.min(1f, s.bloom))));
            if (shadowAlpha > 0) {
                shadowPaint.setAlpha(shadowAlpha);
                if (!isDarkMode) {
                    canvas.drawRoundRect(thumbL - 3.0f, thumbT - 3.0f,
                            thumbR + 3.0f, thumbB + 4.5f,
                            thumbRadius + 1.0f, thumbRadius + 1.0f, shadowPaint);
                } else {
                    canvas.drawRoundRect(thumbL, thumbT + 4.0f, thumbR,
                            thumbB + 4.0f, thumbRadius, thumbRadius, shadowPaint);
                }
            }

            renderLiquidGlassThumb(canvas, s, w, h,
                    thumbL, thumbT, thumbR, thumbB, thumbRadius);

            RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
            int glowStroke = blendColors(Color.parseColor("#75FFFFFF"),
                    activePaint.getColor(), 0.70f *
                            Math.max(0.3f, s.bloom));
            strokePaint.setColor(glowStroke);
            canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
            strokePaint.setColor(Color.parseColor("#75FFFFFF"));

            int whiteAlpha = (int) (255 * (1.0f -
                    Math.max(0f, Math.min(1f, s.bloom))));
            if (whiteAlpha > 0) {
                baseThumbPaint.setAlpha(whiteAlpha);
                canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, baseThumbPaint);
            }
            return null;
        });
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        ClassLoader classLoader = param.getClassLoader();
        if (classLoader == null) classLoader = param.getDefaultClassLoader();
        final ClassLoader packageClassLoader = classLoader;

        // The active liquid implementation handles target touches through each
        // widget's onTouchEvent hook. The old global dispatchTouchEvent hook is only
        // needed by the legacy non-liquid path; installing it globally otherwise adds
        // an interception to every View touch dispatch in the hooked process.
        if (!USE_LIQUID_GLASS_TOGGLE) {
            try {
                Method dispatch = View.class.getDeclaredMethod(
                        "dispatchTouchEvent", MotionEvent.class);
                dispatch.setAccessible(true);
                installHook(dispatch, chain -> {
                    View view = (View) chain.getThisObject();
                    String className = view.getClass().getName();
                    boolean isTarget = false;
                    for (String target : TARGET_TOGGLE_CLASSES) {
                        if (className.equals(target)) {
                            isTarget = true;
                            break;
                        }
                    }
                    if (isTarget) {
                        MotionEvent event = (MotionEvent) chain.getArg(0);
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            ViewParent parent = view.getParent();
                            if (parent != null)
                                parent.requestDisallowInterceptTouchEvent(true);
                        }
                        invoke(findMethod(view.getClass(), "onTouchEvent", MotionEvent.class),
                                view, event);
                        return true;
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
                log(4, "HyperBounce", "dispatchTouchEvent hook failed", t);
            }
        }

        for (String className : TARGET_TOGGLE_CLASSES)
            applyUnifiedToggleEngine(className, packageClassLoader);
        for (String className : TARGET_SLIDER_CLASSES)
            applyUnifiedSliderEngine(className, packageClassLoader);

        if (packageName.equals("com.android.phone") ||
                packageName.equals("com.android.contacts") ||
                packageName.equals("com.android.settings")) {
            applyDialerToggleEngine(packageClassLoader);
        }

        if (packageName.equals("com.android.systemui") ||
                packageName.equals("miui.systemui.plugin")) {
            try {
                Method loadClass = ClassLoader.class.getDeclaredMethod(
                        "loadClass", String.class, boolean.class);
                loadClass.setAccessible(true);
                installHook(loadClass, chain -> {
                    Object result = chain.proceed();
                    if (result instanceof Class<?>) {
                        Class<?> clazz = (Class<?>) result;
                        String name = clazz.getName();
                        if (name.equals("miui.systemui.controlcenter.widget.SlidingButton") ||
                                name.equals("com.miui.systemui.widget.SlidingButton")) {
                            applyUnifiedToggleEngine(name, clazz.getClassLoader());
                        }
                    }
                    return result;
                });
            } catch (Throwable t) {
                log(4, "HyperBounce", "ClassLoader hook failed", t);
            }
        }

        if (packageName.equals("miui.systemui.plugin") ||
                packageName.equals("com.android.systemui")) {
            dev.joseph.hyperbounce.toggles.LiquidKeypadHook.INSTANCE.apply(this, packageClassLoader);
            try {
                Class<?> delegateClass = findClassIfExists(
                        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate",
                        packageClassLoader);
                if (delegateClass != null) {
                    hookAllConstructors(delegateClass, chain -> {
                        Object result = chain.proceed();
                        try {
                            Class<?> folmeEaseClass = findClassIfExists(
                                    "miuix.animation.FolmeEase", packageClassLoader);
                            if (folmeEaseClass == null) return result;

                            Method spring = findMethod(
                                    folmeEaseClass, "spring", float.class, float.class);
                            Object iosSpring = invoke(spring, null, 0.65f, 0.35f);
                            Object fastSpring = invoke(spring, null, 0.70f, 0.25f);

                            for (Field field : delegateClass.getDeclaredFields()) {
                                field.setAccessible(true);
                                if (field.getType().getName().contains("EaseStyle")) {
                                    String name = field.getName();
                                    field.set(chain.getThisObject(),
                                            (name.contains("HIDDEN") ||
                                                    name.contains("APPEAR"))
                                                    ? fastSpring : iosSpring);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        return result;
                    });
                }
            } catch (Throwable t) {
                log(4, "HyperBounce", "Dynamic Island hook failed", t);
            }
        }
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(4, "HyperBounce", "Loaded on " +
                (param.isSystemServer() ? "system_server" : param.getProcessName()));
    }
}
