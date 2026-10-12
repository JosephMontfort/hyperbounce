package dev.hyperios.app.toggles;

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

import dev.hyperios.app.prefs.HyperBouncePrefs;

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
    private static final int TRACK_ON_DISABLED = Color.parseColor("#1C4D25");
    private static final int TRACK_OFF_DISABLED = Color.parseColor("#2C2C2E");
    private static int toggleAccentColor = TRACK_ON;
    private static int sliderAccentColor = Color.parseColor("#007AFF");

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
            "miui.systemui.widget.SlidingButton"
    };

    private static final String[] TARGET_SLIDER_CLASSES = {
            "miuix.androidbasewidget.widget.SeekBar"
    };

    // Not `final`: set once per process from HyperBouncePrefs at the top of
    // onPackageReady(), before either flag is read anywhere else in this file. The
    // `true` defaults here only matter if that read somehow never runs.
    private static boolean USE_LIQUID_GLASS_TOGGLE = true;
    private static boolean USE_LIQUID_GLASS_SLIDER = true;

    private final Set<Class<?>> hookedDynamicClasses =
            Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());
    private final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();
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

    private static class ToggleState extends GlassState {
        float progress = 0.0f;
        float colorProgress = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
        boolean isParentPressed = false;
        boolean initializedObserver = false;
        float startX = 0f;
        float currentX = 0f;
        long lastTime = System.currentTimeMillis();
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
            if (className.contains("ActionBar") ||
                    className.contains("RecyclerView") ||
                    pView instanceof android.widget.AbsListView ||
                    pView instanceof android.widget.ScrollView ||
                    className.contains("DecorView")) break;
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

    private static final String AGSL_SHADER_TOGGLE =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "uniform float chromaticAberration;\n" +
            "\n" +
            "float radiusAt(float2 coord, float4 radii) {\n" +
            "    if (coord.x >= 0.0) {\n" +
            "        if (coord.y <= 0.0) return radii.y;\n" +
            "        else return radii.z;\n" +
            "    } else {\n" +
            "        if (coord.y <= 0.0) return radii.x;\n" +
            "        else return radii.w;\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
            "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
            "    return outside + inside;\n" +
            "}\n" +
            "\n" +
            "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
            "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
            "    } else {\n" +
            "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n" +
            "\n" +
            "half4 sampleDispersion(float2 coord, float2 dispersedCoord) {\n" +
            "    half4 color = half4(0.0);\n" +
            "    half4 red = content.eval(coord + dispersedCoord);\n" +
            "    color.r += red.r / 3.5; color.a += red.a / 7.0;\n" +
            "    half4 orange = content.eval(coord + dispersedCoord * (2.0 / 3.0));\n" +
            "    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;\n" +
            "    half4 yellow = content.eval(coord + dispersedCoord * (1.0 / 3.0));\n" +
            "    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;\n" +
            "    half4 green = content.eval(coord);\n" +
            "    color.g += green.g / 3.5; color.a += green.a / 7.0;\n" +
            "    half4 cyan = content.eval(coord - dispersedCoord * (1.0 / 3.0));\n" +
            "    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;\n" +
            "    half4 blue = content.eval(coord - dispersedCoord * (2.0 / 3.0));\n" +
            "    color.b += blue.b / 3.0; color.a += blue.a / 7.0;\n" +
            "    half4 purple = content.eval(coord - dispersedCoord);\n" +
            "    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "half4 main(float2 coord) {\n" +
            "    if (refractionHeight <= 0.001 || refractionAmount <= 0.001) return content.eval(coord);\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) return content.eval(coord);\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001));\n" +
            "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
            "    return sampleDispersion(refractedCoord, dispersedCoord);\n" +
            "}";

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

    private void renderLiquidGlassToggleThumb(Canvas canvas, ToggleState s, int w, int h,
                                              float thumbL, float thumbT, float thumbR, float thumbB,
                                              float thumbRadius) {
        if (!canvas.isHardwareAccelerated()) return;

        if (s.blurNode == null) s.blurNode = new RenderNode("HyperBounceToggleBlur");
        if (s.glassNode == null) s.glassNode = new RenderNode("HyperBounceToggleGlass");

        final float density = Math.max(1.0f, s.density);
        final float bloom = Math.max(0f, Math.min(1f, s.bloom));
        final float refractionHeight = 5.0f * density * bloom;
        final float refractionAmount = 10.0f * density * bloom;
        final float blurRadius = 8.0f * density * (1.0f - bloom);

        try {
            s.blurNode.setRenderEffect(RenderEffect.createBlurEffect(
                    Math.max(0.001f, blurRadius), Math.max(0.001f, blurRadius), Shader.TileMode.CLAMP));

            if (!s.shaderInitialized) {
                s.shader = new RuntimeShader(AGSL_SHADER_TOGGLE);
                s.glassNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(s.shader, "content"));
                s.shaderInitialized = true;
            }

            float sx = thumbR - thumbL;
            float sy = thumbB - thumbT;
            float radius = Math.min(thumbRadius, Math.min(sx, sy) * 0.5f);
            s.shader.setFloatUniform("size", sx, sy);
            s.shader.setFloatUniform("offset", -thumbL, -thumbT);
            s.shader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
            s.shader.setFloatUniform("refractionHeight", refractionHeight);
            s.shader.setFloatUniform("refractionAmount", refractionAmount);
            s.shader.setFloatUniform("depthEffect", 0.0f);
            s.shader.setFloatUniform("chromaticAberration", 1.0f);

            float backdropScaleX = lerpFloatToggle(1.0f, 0.75f, bloom);
            float backdropScaleY = lerpFloatToggle(1.0f, 0.75f, bloom);
            float centerX = (thumbL + thumbR) * 0.5f;
            float centerY = (thumbT + thumbB) * 0.5f;

            s.blurNode.setPosition(0, 0, w, h);
            Canvas rcBlur = s.blurNode.beginRecording(w, h);

            rcBlur.save();
            float backdropPadding = 10.0f * density;
            rcBlur.clipRect(
                    thumbL - backdropPadding, thumbT - backdropPadding,
                    thumbR + backdropPadding, thumbB + backdropPadding);

            rcBlur.translate(centerX, centerY);
            rcBlur.scale(backdropScaleX, backdropScaleY);
            rcBlur.translate(-centerX, -centerY);
            if (s.trackBitmap != null) rcBlur.drawBitmap(s.trackBitmap, 0.0f, 0.0f, bmpPaint);
            rcBlur.restore();

            s.blurNode.endRecording();

            s.glassNode.setPosition(0, 0, w, h);
            Canvas rcGlass = s.glassNode.beginRecording(w, h);
            rcGlass.drawRenderNode(s.blurNode);
            s.glassNode.endRecording();

            Path clipPath = new Path();
            clipPath.addRoundRect(new RectF(thumbL, thumbT, thumbR, thumbB),
                    radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRenderNode(s.glassNode);
            canvas.restore();

            float bubbleAmount = Math.max(0.0f, Math.min(1.0f, s.bubbleSquish / 0.70f));

            int ambientAlpha = (int) (38f * bloom + 18f * bubbleAmount);
            if (ambientAlpha > 0) {
                highlightPaint.setAlpha(Math.min(72, ambientAlpha));
                canvas.drawRoundRect(new RectF(
                                thumbL + 0.7f * density,
                                thumbT + 0.7f * density,
                                thumbR - 0.7f * density,
                                thumbB - 0.7f * density),
                        Math.max(0.0f, radius - 0.7f * density),
                        Math.max(0.0f, radius - 0.7f * density),
                        highlightPaint);
                highlightPaint.setAlpha(255);
            }

            if (bloom > 0.01f) {
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(Math.max(0.75f, 1.2f * density * bloom));
                strokePaint.setColor(Color.argb(
                        (int) (42f * bloom),
                        255, 255, 255));
                canvas.drawRoundRect(new RectF(
                                thumbL + 0.9f * density,
                                thumbT + 0.9f * density,
                                thumbR - 0.9f * density,
                                thumbB - 0.9f * density),
                        Math.max(0.0f, radius - 0.9f * density),
                        Math.max(0.0f, radius - 0.9f * density),
                        strokePaint);
                strokePaint.setStrokeWidth(1.0f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float lerpFloatToggle(float a, float b, float t) {
        return a + (b - a) * t;
    }

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

        int exp = 100;
        if (s.shaderInitialized) {
            s.shader.setFloatUniform("thumbL", thumbL + exp);
            s.shader.setFloatUniform("thumbT", thumbT + exp);
            s.shader.setFloatUniform("thumbR", thumbR + exp);
            s.shader.setFloatUniform("thumbB", thumbB + exp);
            s.shader.setFloatUniform("refractionHeight", 34.0f);
        }

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
            log(4, "HyperiOS", "Failed to hook " + executable, t);
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
            log(4, "HyperiOS", "Failed to hook constructors for " + clazz, t);
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
                chain -> {
                    if (!clazz.isInstance(chain.getThisObject())) return chain.proceed();
                    return null;
                });

        hookAllConstructors(clazz, chain -> {
            Object result = chain.proceed();
            Object thisObj = chain.getThisObject();
            if (!clazz.isInstance(thisObj)) return result;
            View view = (View) thisObj;
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.post(() -> {
                        if (USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(v)) {
                            LiquidToggleHost.ensure(v);
                        }
                    });
                }
                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(v)) {
                        LiquidToggleHost.remove(v);
                    }
                }
            });
            view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l > 0 && b - t > 0 && USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(v)) {
                    LiquidToggleHost.ensure(v);
                }
            });
            return result;
        });

        hookMethod(clazz, "setChecked", new Class<?>[]{boolean.class}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(chain.getThisObject())) {
                LiquidToggleHost.syncChecked((View) chain.getThisObject());
            }
            return result;
        });

        hookMethod(clazz, "setVisibility", new Class<?>[]{int.class}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(chain.getThisObject())) {
                int vis = (Integer) chain.getArg(0);
                LiquidToggleHost.syncVisibility((View) chain.getThisObject(), vis == View.VISIBLE);
            }
            return result;
        });

        hookMethod(clazz, "onDetachedFromWindow", new Class<?>[]{}, chain -> {
            Object result = chain.proceed();
            if (USE_LIQUID_GLASS_TOGGLE && clazz.isInstance(chain.getThisObject())) {
                LiquidToggleHost.remove((View) chain.getThisObject());
            }
            return result;
        });

        hookMethod(clazz, "onTouchEvent", new Class<?>[]{MotionEvent.class}, chain -> {
            if (!clazz.isInstance(chain.getThisObject())) return chain.proceed();
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

            View view = (View) chain.getThisObject();
            if (!view.isEnabled()) return false;
            disableClipping(view);

            MotionEvent event = (MotionEvent) chain.getArg(0);
            ToggleState s = stateMap.get(view);
            if (s == null) {
                s = new ToggleState();
                s.progress = ((android.widget.CompoundButton) view).isChecked() ? 1.0f : 0.0f;
                s.colorProgress = s.progress;
                stateMap.put(view, s);
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                s.isTouching = true;
                s.isDragging = false;
                s.startX = event.getX();
                s.currentX = event.getX();
                s.lastX = event.getX();
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
            return chain.proceed();
        });

        hookMethod(clazz, "onDraw", new Class<?>[]{android.graphics.Canvas.class}, chain -> {
            if (!clazz.isInstance(chain.getThisObject())) return chain.proceed();
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

            Canvas canvas = (Canvas) chain.getArg(0);
            disableClipping(view);
            try {
                if (view.getButtonDrawable() != null) view.setButtonDrawable(null);
            } catch (Throwable ignored) {
            }

            ToggleState s = stateMap.get(view);
            if (s == null) {
                s = new ToggleState();
                s.progress = view.isChecked() ? 1.0f : 0.0f;
                s.colorProgress = s.progress;
                stateMap.put(view, s);
            }

            if (!s.initializedObserver) {
                ViewParent p = view.getParent();
                View rowView = null;
                while (p instanceof View) {
                    View pv = (View) p;
                    String name = pv.getClass().getSimpleName();
                    if (name.equals("RecyclerView") || name.contains("ListView")) break;
                    rowView = pv;
                    p = pv.getParent();
                }
                if (rowView != null) {
                    final android.widget.CompoundButton finalView = view;
                    final ToggleState finalS = s;
                    rowView.setOnTouchListener((v, event) -> {
                        int action = event.getActionMasked();
                        boolean isDown = (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE);
                        if (finalS.isParentPressed != isDown) {
                            finalS.isParentPressed = isDown;
                            finalView.postInvalidateOnAnimation();
                        }
                        return false;
                    });
                }
                s.initializedObserver = true;
            }

            boolean isChecked = view.isChecked();
            boolean isEnabled = view.isEnabled();
            boolean isDarkMode = (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;

            long now = System.currentTimeMillis();
            float dt = Math.min((now - s.lastTime) / 1000.0f, 0.05f);
            s.lastTime = now;

            final float toggleScale = 0.95f;
            float density = view.getResources().getDisplayMetrics().density;
            s.density = density;
            float drawW = 64.0f * toggleScale * density;
            float drawH = 28.0f * toggleScale * density;
            float offsetX, offsetY;
            if (width > height * 2.5f && width > 200f) {
                offsetX = width - drawW - (height * 0.15f);
                offsetY = (height - drawH) / 2.0f;
            } else {
                offsetX = (width - drawW) / 2.0f;
                offsetY = (height - drawH) / 2.0f;
            }

            float trackHeight = drawH;
            float restThumbHeight = 24.0f * toggleScale * density;
            float restThumbWidth = 40.0f * toggleScale * density;

            float targetProgress = isChecked ? 1.0f : 0.0f;
            if (s.isDragging) {
                float minCenter = offsetX + (restThumbWidth / 2.0f);
                float maxCenter = offsetX + drawW - (restThumbWidth / 2.0f);
                float rawDrag = (s.currentX - minCenter) / (maxCenter - minCenter);
                targetProgress = Math.max(0f, Math.min(1f, rawDrag));
            }
            s.progress += (targetProgress - s.progress) * dt * 18.0f;

            float targetColorRatio = (s.progress >= 0.5f) ? 1.0f : 0.0f;
            s.colorProgress += (targetColorRatio - s.colorProgress) * dt * 16.0f;
            s.colorProgress = Math.max(0.0f, Math.min(1.0f, s.colorProgress));

            float bloomTarget = (s.isTouching || s.isDragging || s.isParentPressed) ? 1.0f : 0.0f;
            float bloomForce = (bloomTarget - s.bloom) * 250.0f - (s.bloomVelocity * 16.0f);
            s.bloomVelocity += bloomForce * dt;
            s.bloom += s.bloomVelocity * dt;

            float dx = s.currentX - s.lastX;
            s.lastX = s.currentX;
            float velocity = Math.abs(dx) / Math.max(dt, 0.001f);
            float targetBubble = s.isDragging ? Math.min(velocity / 800.0f, 0.70f) : 0.0f;
            float springForce = (targetBubble - s.bubbleSquish) * 250.0f - (s.bubbleVelocity * 12.0f);
            s.bubbleVelocity += springForce * dt;
            s.bubbleSquish += s.bubbleVelocity * dt;

            if (s.isTouching && !s.isDragging) s.bubbleVelocity += s.bloomVelocity * 0.15f;

            if (Math.abs(s.progress - targetProgress) > 0.002f ||
                    Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.bloomVelocity) > 0.01f ||
                    Math.abs(s.colorProgress - targetColorRatio) > 0.005f ||
                    Math.abs(s.bubbleVelocity) > 0.01f || s.bubbleSquish > 0.005f) {
                view.postInvalidateOnAnimation();
            }

            float trackTop = offsetY + (drawH - trackHeight) / 2.0f;
            float trackBottom = trackTop + trackHeight;
            float trackRadius = trackHeight / 2.0f;

            float maxExpandedW = 60.0f * toggleScale * density;
            float maxExpandedH = 36.0f * toggleScale * density;
            float bubbleAmount = Math.max(0.0f, Math.min(1.0f, s.bubbleSquish / 0.70f));
            float bubbleScaleX = 1.0f + (0.11f * bubbleAmount);
            float bubbleScaleY = 1.0f - (0.055f * bubbleAmount);
            float currentThumbHeight = (restThumbHeight + (maxExpandedH - restThumbHeight) * s.bloom) * bubbleScaleY;
            float currentThumbWidth = (restThumbWidth + (maxExpandedW - restThumbWidth) * s.bloom) * bubbleScaleX;
            float thumbRadius = currentThumbHeight / 2.0f;

            float thumbPadding = 2.0f * toggleScale * density;
            float baseMinX = offsetX + thumbPadding + (restThumbWidth / 2.0f);
            float baseMaxX = offsetX + drawW - thumbPadding - (restThumbWidth / 2.0f);
            float minX = baseMinX;
            float maxX = baseMaxX;

            float cx = minX + s.progress * (maxX - minX);
            float cy = offsetY + (drawH / 2.0f);

            float thumbL = cx - (currentThumbWidth / 2.0f);
            float thumbR = cx + (currentThumbWidth / 2.0f);
            float thumbT = cy - (currentThumbHeight / 2.0f);
            float thumbB = cy + (currentThumbHeight / 2.0f);

            int targetOffColor = !isEnabled ? TRACK_OFF_DISABLED : (isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
            int targetOnColor = !isEnabled ? blendColors(Color.BLACK, toggleAccentColor, 0.4f) : toggleAccentColor;
            bgPaint.setColor(blendColors(targetOffColor, targetOnColor, s.colorProgress));

            canvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

            if (s.trackBitmap == null || s.trackBitmap.getWidth() != width || s.trackBitmap.getHeight() != height) {
                s.trackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                s.trackCanvas = new Canvas(s.trackBitmap);
            }

            s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
            s.trackCanvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

            int shadowAlpha = (int) (48 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
            if (shadowAlpha > 0 && isEnabled) {
                shadowPaint.setAlpha(shadowAlpha);
                canvas.drawRoundRect(thumbL - 1.0f, thumbT + 2.0f, thumbR + 1.0f, thumbB + 3.0f, thumbRadius, thumbRadius, shadowPaint);
            }

            renderLiquidGlassToggleThumb(canvas, s, width, height, thumbL, thumbT, thumbR, thumbB, thumbRadius);

            RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
            int glowStroke = blendColors(Color.parseColor("#75FFFFFF"), bgPaint.getColor(), 0.70f * Math.max(0.3f, s.bloom));
            strokePaint.setColor(glowStroke);
            canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
            strokePaint.setColor(Color.parseColor("#75FFFFFF"));

            int whiteAlpha = (int) (255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
            if (whiteAlpha > 0) {
                baseThumbPaint.setAlpha(isEnabled ? whiteAlpha : (int) (whiteAlpha * 0.5f));
                canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, baseThumbPaint);
            }

            return null;
        });
    }

    private void applyUnifiedSliderEngine(String className, ClassLoader classLoader) {
        Class<?> clazz = findClassIfExists(className, classLoader);
        if (clazz == null) return;
        synchronized (hookedDynamicClasses) {
            if (!hookedDynamicClasses.add(clazz)) return;
        }

        hookAllConstructors(clazz, chain -> {
            Object result = chain.proceed();
            Object thisObj = chain.getThisObject();
            if (!clazz.isInstance(thisObj)) return result;
            View view = (View) thisObj;
            if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return result;
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(v)) return;
                    v.post(() -> {
                        if (clazz.isInstance(v)) {
                            if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(v)) return;
                            disableClipping(v);
                            if (USE_LIQUID_GLASS_SLIDER) {
                                dev.hyperios.app.toggles.LiquidSliderHost.ensure(v);
                            }
                        }
                    });
                }
                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(v)) return;
                    if (USE_LIQUID_GLASS_SLIDER && clazz.isInstance(v)) {
                        dev.hyperios.app.toggles.LiquidSliderHost.remove(v);
                    }
                }
            });
            view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(v)) return;
                if (r - l > 0 && b - t > 0 && clazz.isInstance(v)) {
                    disableClipping(v);
                    if (USE_LIQUID_GLASS_SLIDER) {
                        dev.hyperios.app.toggles.LiquidSliderHost.ensure(v);
                    }
                }
            });
            return result;
        });

        hookMethod(clazz, "setProgress", new Class<?>[]{int.class}, chain -> {
            Object result = chain.proceed();
            if (clazz.isInstance(chain.getThisObject())) {
                View view = (View) chain.getThisObject();
                if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return result;
                if (USE_LIQUID_GLASS_SLIDER) {
                    int progress = (Integer) chain.getArg(0);
                    dev.hyperios.app.toggles.LiquidSliderHost.syncProgress(view, progress);
                }
            }
            return result;
        });

        hookMethod(clazz, "setProgress", new Class<?>[]{int.class, boolean.class}, chain -> {
            Object result = chain.proceed();
            if (clazz.isInstance(chain.getThisObject())) {
                View view = (View) chain.getThisObject();
                if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return result;
                if (USE_LIQUID_GLASS_SLIDER) {
                    int progress = (Integer) chain.getArg(0);
                    dev.hyperios.app.toggles.LiquidSliderHost.syncProgress(view, progress);
                }
            }
            return result;
        });

        hookMethod(clazz, "setVisibility", new Class<?>[]{int.class}, chain -> {
            Object result = chain.proceed();
            if (clazz.isInstance(chain.getThisObject())) {
                View view = (View) chain.getThisObject();
                if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return result;
                if (USE_LIQUID_GLASS_SLIDER) {
                    int vis = (Integer) chain.getArg(0);
                    dev.hyperios.app.toggles.LiquidSliderHost.syncVisibility(view, vis == View.VISIBLE);
                }
            }
            return result;
        });

        hookMethod(clazz, "onDetachedFromWindow", new Class<?>[]{}, chain -> {
            Object result = chain.proceed();
            if (clazz.isInstance(chain.getThisObject())) {
                View view = (View) chain.getThisObject();
                if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return result;
                if (USE_LIQUID_GLASS_SLIDER) {
                    dev.hyperios.app.toggles.LiquidSliderHost.remove(view);
                }
            }
            return result;
        });

        hookMethod(clazz, "onTouchEvent", new Class<?>[]{MotionEvent.class}, chain -> {
            if (!clazz.isInstance(chain.getThisObject())) return chain.proceed();
            View view = (View) chain.getThisObject();
            if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return chain.proceed();
            if (USE_LIQUID_GLASS_SLIDER) {
                if (!view.isEnabled()) return false;
                MotionEvent event = (MotionEvent) chain.getArg(0);
                int action = event.getActionMasked();
                
                if (action == MotionEvent.ACTION_DOWN) {
                    ViewParent parent = view.getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    dev.hyperios.app.toggles.LiquidSliderHost.setTrackingTouch(view, true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    dev.hyperios.app.toggles.LiquidSliderHost.setTrackingTouch(view, false);
                }
                
                // DUAL-TOUCH ARCHITECTURE:
                // 1. Forward offset touch to Compose for liquid glass visual physics
                dev.hyperios.app.toggles.LiquidSliderHost.forwardTouchEvent(view, event);
                
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
            if (!view.isEnabled()) return false;
            disableClipping(view);
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
            if (!clazz.isInstance(chain.getThisObject())) return chain.proceed();
            View view = (View) chain.getThisObject();
            if (dev.hyperios.app.toggles.LiquidSliderHost.isExempt(view)) return chain.proceed();
            Canvas canvas = (Canvas) chain.getArg(0);
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return null;

            if (USE_LIQUID_GLASS_SLIDER) {
                dev.hyperios.app.toggles.LiquidSliderHost.ensure(view);
                return null;
            }

            // FALLBACK TO THE FULLY CUSTOM SLIDER ENGINE
            disableClipping(view);

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

            float density = view.getResources().getDisplayMetrics().density;
            s.density = density;
            float restThumbW = 40.0f * density;
            float restThumbH = 24.0f * density;
            float maxExpandedW = 60.0f * density;
            float maxExpandedH = 36.0f * density;

            float bubbleAmount = Math.max(0.0f, Math.min(1.0f, s.bubbleSquish / 0.75f));
            float bubbleScaleX = 1.0f + (0.12f * bubbleAmount);
            float bubbleScaleY = 1.0f - (0.06f * bubbleAmount);

            float currentThumbH = (restThumbH + (maxExpandedH - restThumbH) * s.bloom) * bubbleScaleY;
            float currentThumbW = (restThumbW + (maxExpandedW - restThumbW) * s.bloom) * bubbleScaleX;
            float thumbRadius = currentThumbH / 2.0f;

            float inset = 16.0f * density;
            float shrinkAmount = s.bloom * 2.0f * density;
            float trackLeft = inset + shrinkAmount;
            float trackRight = (w - inset) - shrinkAmount;
            float trackW = trackRight - trackLeft;

            float minCx = inset + (restThumbW / 2.0f);
            float maxCx = (w - inset) - (restThumbW / 2.0f);
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

            float trackHeight = 6.0f * density;
            float trackTop = (h - trackHeight) / 2.0f;
            float trackBottom = trackTop + trackHeight;
            float trackRadius = trackHeight / 2.0f;

            bgPaint.setColor(isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
            canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom,
                    trackRadius, trackRadius, bgPaint);

            float activeW = trackW * s.visualRatio;
            if (activeW > 0) {
                activePaint.setColor(sliderAccentColor);
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
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        try {
            android.content.SharedPreferences prefs = getRemotePreferences(dev.hyperios.app.prefs.HyperBouncePrefsKt.PREFS_GROUP);
            boolean enabled = prefs != null && prefs.getBoolean("hot_reload_enabled", false);
            if (!enabled) {
                log(4, "HyperiOS", "Hot reload refused in LiquidTogglesHook (disabled in settings)");
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        super.onHotReloaded(param);
        log(4, "HyperiOS", "Hot reloaded successfully in LiquidTogglesHook");
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        ClassLoader classLoader = param.getClassLoader();
        if (classLoader == null) classLoader = param.getDefaultClassLoader();
        final ClassLoader packageClassLoader = classLoader;

        if (dev.hyperios.app.safemode.SafeModeHandler.INSTANCE.isSafeMode(packageName, null)) {
            log(4, "HyperiOS", "[SAFE MODE ACTIVE] Skipping toggles/sliders for " + packageName);
            return;
        }

        HyperBouncePrefs hyperBouncePrefs = null;
        boolean togglesSlidersHookEnabled = true;
        boolean toggleEnabled = true;
        boolean sliderEnabled = true;
        boolean miuixAppsHookingEnabled = false;
        try {
            hyperBouncePrefs = new HyperBouncePrefs(this);
            if (!hyperBouncePrefs.getModuleEnabled()) {
                log(4, "HyperiOS", "disabled globally, skipping toggles/sliders/keypad injection");
                return;
            }
            togglesSlidersHookEnabled = hyperBouncePrefs.getTogglesSlidersHookEnabled();
            if (!togglesSlidersHookEnabled) {
                log(4, "HyperiOS", "toggles/sliders hook disabled via master switch, skipping");
                return;
            }

            toggleEnabled = hyperBouncePrefs.getToggleEnabled();
            sliderEnabled = hyperBouncePrefs.getSliderEnabled();
            USE_LIQUID_GLASS_TOGGLE = hyperBouncePrefs.getToggleEngineIsLiquidGlass();
            USE_LIQUID_GLASS_SLIDER = hyperBouncePrefs.getSliderEngineIsLiquidGlass();

            if (toggleEnabled) {
                dev.hyperios.app.toggles.LiquidToggleHost.setHyperBouncePrefs(hyperBouncePrefs);
                toggleAccentColor = (int) hyperBouncePrefs.toggleAccentColor();
            }
            if (sliderEnabled) {
                dev.hyperios.app.toggles.LiquidSliderHost.setHyperBouncePrefs(hyperBouncePrefs);
                sliderAccentColor = (int) hyperBouncePrefs.sliderAccentColor();
            }
            miuixAppsHookingEnabled = hyperBouncePrefs.getMiuixAppsHookingEnabled();
        } catch (Throwable t) {
            // This hook runs inside system-critical processes (android, systemui,
            // settings) — an uncaught exception here is a much worse outcome than falling
            // back to the pre-Phase-2 hardcoded defaults (both flags already default to
            // `true` above), so this must never propagate. hyperBouncePrefs may be left
            // null; LiquidKeypadHook below checks for that.
            log(5, "HyperiOS", "settings read failed, continuing with defaults: " + t);
        }

        // The active liquid implementation handles target touches through each
        // widget's onTouchEvent hook. The old global dispatchTouchEvent hook is only
        // needed by the legacy non-liquid path; installing it globally otherwise adds
        // an interception to every View touch dispatch in the hooked process.
        if (toggleEnabled && !USE_LIQUID_GLASS_TOGGLE) {
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
                log(4, "HyperiOS", "dispatchTouchEvent hook failed", t);
            }
        }

        if (toggleEnabled) {
            for (String className : TARGET_TOGGLE_CLASSES)
                applyUnifiedToggleEngine(className, packageClassLoader);
        }
        if (sliderEnabled) {
            for (String className : TARGET_SLIDER_CLASSES)
                applyUnifiedSliderEngine(className, packageClassLoader);
        }

        if (miuixAppsHookingEnabled) {
            if (toggleEnabled) {
                applyUnifiedToggleEngine("fan.slidingwidget.widget.SlidingButton", packageClassLoader);
            }
            if (sliderEnabled) {
                applyUnifiedSliderEngine("fan.androidbase.widget.SeekBar", packageClassLoader);
            }

            if (!packageName.equals("com.android.systemui") &&
                    !packageName.equals("miui.systemui.plugin")) {
                if (toggleEnabled || sliderEnabled) {
                    try {
                        Method loadClass = ClassLoader.class.getDeclaredMethod(
                                "loadClass", String.class, boolean.class);
                        loadClass.setAccessible(true);
                        final boolean finalToggleEnabled = toggleEnabled;
                        final boolean finalSliderEnabled = sliderEnabled;
                        installHook(loadClass, chain -> {
                            String name = (String) chain.getArg(0);
                            if (name == null) return chain.proceed();
                            boolean isToggle = finalToggleEnabled && (name.endsWith("SlidingButton") ||
                                     name.endsWith("SlidingSwitch") ||
                                     name.endsWith("SlidingSwitchCompat"));
                            boolean isSlider = finalSliderEnabled && name.endsWith("SeekBar");
                            if (!isToggle && !isSlider) {
                                return chain.proceed();
                            }

                            if (name.startsWith("android.") || name.startsWith("androidx.") ||
                                    name.startsWith("java.") || name.startsWith("kotlin.")) {
                                return chain.proceed();
                            }

                            Object result = chain.proceed();
                            if (result instanceof Class<?>) {
                                Class<?> clazz = (Class<?>) result;
                                if (isToggle && android.widget.CompoundButton.class.isAssignableFrom(clazz)) {
                                    applyUnifiedToggleEngine(name, clazz.getClassLoader());
                                } else if (isSlider && android.widget.ProgressBar.class.isAssignableFrom(clazz)) {
                                    applyUnifiedSliderEngine(name, clazz.getClassLoader());
                                }
                            }
                            return result;
                        });
                    } catch (Throwable t) {
                        log(4, "HyperiOS", "Dynamic ClassLoader hook failed for " + packageName, t);
                    }
                }
            }
        }

        if (toggleEnabled && (packageName.equals("com.android.phone") ||
                packageName.equals("com.android.contacts") ||
                packageName.equals("com.android.settings"))) {
            applyDialerToggleEngine(packageClassLoader);
        }

        if (toggleEnabled && (packageName.equals("com.android.systemui") ||
                packageName.equals("miui.systemui.plugin"))) {
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
                log(4, "HyperiOS", "ClassLoader hook failed", t);
            }
        }

    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(4, "HyperiOS", "Loaded on " +
                (param.isSystemServer() ? "system_server" : param.getProcessName()));
    }
}
