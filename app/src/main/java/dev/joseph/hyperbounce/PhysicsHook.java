package dev.joseph.hyperbounce;

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
    // SHARED PAINTS & COLORS
    // ==========================================
    private static final int TRACK_ON             = Color.parseColor("#30D158");
    private static final int TRACK_OFF_DARK       = Color.parseColor("#39393D");
    private static final int TRACK_OFF_LIGHT      = Color.parseColor("#E9E9EA");
    private static final int TRACK_ON_DISABLED    = Color.parseColor("#1C4D25");
    private static final int TRACK_OFF_DISABLED   = Color.parseColor("#2C2C2E");

    private static final Paint bgPaint            = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint activePaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint baseThumbPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint highlightPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint bmpPaint           = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        bgPaint.setStyle(Paint.Style.FILL);
        activePaint.setStyle(Paint.Style.FILL);
        
        baseThumbPaint.setStyle(Paint.Style.FILL);
        baseThumbPaint.setColor(Color.parseColor("#FFFFFF"));

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

    private static final HashSet<String> hookedDynamicClasses = new HashSet<>();

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
        view.setClipToOutline(false);
        ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) parent;
            vg.setClipChildren(false);
            vg.setClipToPadding(false);
            parent = vg.getParent();
        }
    }

    // ==========================================
    // STATES & HARDWARE PIPELINE
    // ==========================================
    private static class GlassState {
        Bitmap trackBitmap;
        Canvas trackCanvas;
        RenderNode blurNode;
        RenderNode glassNode;
        RuntimeShader shader;
        boolean shaderInitialized = false;
        
        float bloom = 0.0f;
        float bloomVelocity = 0.0f; // Spring velocity for expansion
        float bubbleSquish = 0.0f;
        float bubbleVelocity = 0.0f; // Spring velocity for drag wobble
        float lastX = 0f;
    }

    private static class ToggleState extends GlassState {
        float progress = 0.0f;
        float colorProgress = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
        float startX = 0f;
        float currentX = 0f;
        long lastTime = System.currentTimeMillis();
    }

    private static class SliderState extends GlassState {
        float visualRatio = -1.0f;
        float touchX = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
        long lastTime = System.currentTimeMillis();
    }

    private static final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();
    private static final WeakHashMap<Object, SliderState> sliderStateMap = new WeakHashMap<>();

    // Unified Master Shader (Pure Edge Refraction + Color Bleed)
    private static final String AGSL_SHADER_UNIFIED = 
        "uniform shader content;\n" +
        "uniform float thumbL;\n" +
        "uniform float thumbT;\n" +
        "uniform float thumbR;\n" +
        "uniform float thumbB;\n" +
        "uniform float refractionAmount;\n" +
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
        "    \n" +
        "    float dist = length(diff);\n" +
        "    vec2 dir = dist > 0.0 ? diff / dist : vec2(0.0);\n" +
        "    vec2 refractOffset = dir * (1.0 - factor) * refractionAmount;\n" +
        "    \n" +
        "    half4 color = content.eval(fragCoord - refractOffset);\n" +
        "    half4 basePixel = content.eval(fragCoord);\n" +
        "    \n" +
        "    color.rgb = mix(color.rgb, vec3(1.0), 0.12);\n" + // Subtly frosted core
        "    color.rgb *= mix(0.70, 1.0, factor);\n" + // Darken bevel edge
        "    \n" +
        "    // Aggressively bleed the underlying container color into the glass rim\n" +
        "    color.rgb += basePixel.rgb * (1.0 - factor) * 0.60;\n" + 
        "    \n" +
        "    float rim = pow(1.0 - factor, 2.5);\n" +
        "    color.rgb += rim * 0.50;\n" + // Sharp bright highlight
        "    return color;\n" +
        "}";

    private void renderLiquidGlassThumb(Canvas canvas, GlassState s, int w, int h, float thumbL, float thumbT, float thumbR, float thumbB, float thumbRadius) {
        if (!canvas.isHardwareAccelerated()) return;
        RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
        
        if (s.blurNode == null) s.blurNode = new RenderNode("BlurNode");
        if (s.glassNode == null) s.glassNode = new RenderNode("GlassNode");

        if (!s.shaderInitialized) {
            RenderEffect blurFx = RenderEffect.createBlurEffect(55.0f, 55.0f, Shader.TileMode.CLAMP);
            s.blurNode.setRenderEffect(blurFx);

            try {
                s.shader = new RuntimeShader(AGSL_SHADER_UNIFIED);
                RenderEffect shaderFx = RenderEffect.createRuntimeShaderEffect(s.shader, "content");
                s.glassNode.setRenderEffect(shaderFx);
                s.shaderInitialized = true;
            } catch (Throwable t) {}
        }

        if (s.shaderInitialized) {
            s.shader.setFloatUniform("thumbL", thumbL);
            s.shader.setFloatUniform("thumbT", thumbT);
            s.shader.setFloatUniform("thumbR", thumbR);
            s.shader.setFloatUniform("thumbB", thumbB);
            s.shader.setFloatUniform("refractionAmount", 65.0f);
            s.shader.setFloatUniform("refractionHeight", 34.0f);
        }

        s.blurNode.setPosition(0, 0, w, h);
        Canvas rcBlur = s.blurNode.beginRecording(w, h);
        rcBlur.drawBitmap(s.trackBitmap, 0.0f, 0.0f, bmpPaint);
        s.blurNode.endRecording();

        s.glassNode.setPosition(0, 0, w, h);
        Canvas rcGlass = s.glassNode.beginRecording(w, h);
        rcGlass.drawRenderNode(s.blurNode);
        s.glassNode.endRecording();

        Path clipPath = new Path();
        clipPath.addRoundRect(roundRectF, thumbRadius, thumbRadius, Path.Direction.CW);
        
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRenderNode(s.glassNode);
        canvas.restore();
        
        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, highlightPaint);
        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
    }

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
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable { return null; }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "setChecked", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    ToggleState s = stateMap.get(view);
                    if (s != null) {
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
                    ToggleState s = stateMap.get(view);
                    if (s != null) {
                        s.isTouching = (boolean) param.args[0];
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
                        s.colorProgress = s.progress;
                        stateMap.put(view, s);
                    }
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        disableClipping(view); // Hierarchy un-clipping on touch!
                        s.isTouching = true; s.isDragging = false; 
                        s.startX = event.getX(); s.currentX = event.getX(); s.lastX = event.getX();
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        s.currentX = event.getX();
                        if (Math.abs(s.currentX - s.startX) > 4.0f) s.isDragging = true;
                        view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        s.isTouching = false; s.isDragging = false;
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

                    int width = view.getWidth();
                    int height = view.getHeight();
                    if (width <= 0 || height <= 0) return null;

                    try { if (view.getButtonDrawable() != null) view.setButtonDrawable(null); } catch (Throwable t) {}

                    ToggleState s = stateMap.get(view);
                    if (s == null) {
                        s = new ToggleState();
                        s.progress = view.isChecked() ? 1.0f : 0.0f;
                        s.colorProgress = s.progress;
                        stateMap.put(view, s);
                    }

                    boolean isChecked = view.isChecked();
                    boolean isEnabled = view.isEnabled();
                    boolean isDarkMode = (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;

                    long now = System.currentTimeMillis();
                    float dt = Math.min((now - s.lastTime) / 1000.0f, 0.05f);
                    s.lastTime = now;

                    float drawW = width, drawH = height, offsetX = 0f, offsetY = 0f;
                    if (width > height * 2.5f && width > 200f) {
                        drawH = Math.min(height * 0.55f, 110f);
                        drawW = drawH * 1.75f;
                        offsetX = width - drawW - (height * 0.15f);
                        offsetY = (height - drawH) / 2.0f;
                    }

                    float trackHeight = drawH * 0.72f, padding = trackHeight * 0.07f;
                    float restThumbHeight = trackHeight - (padding * 2.0f);
                    float restThumbWidth = restThumbHeight * 1.35f;

                    float targetProgress = isChecked ? 1.0f : 0.0f;
                    if (s.isDragging) {
                        float minCenter = offsetX + padding + (restThumbWidth / 2.0f);
                        float maxCenter = offsetX + drawW - padding - (restThumbWidth / 2.0f);
                        float rawDrag = (s.currentX - minCenter) / (maxCenter - minCenter);
                        targetProgress = Math.max(0f, Math.min(1f, rawDrag));
                    }
                    s.progress += (targetProgress - s.progress) * dt * 18.0f; 

                    float targetColorRatio = (s.progress >= 0.5f) ? 1.0f : 0.0f;
                    s.colorProgress += (targetColorRatio - s.colorProgress) * dt * 16.0f;
                    s.colorProgress = Math.max(0.0f, Math.min(1.0f, s.colorProgress));

                    // Damped Harmonic Oscillator for Bloom (Pop & Release Spring)
                    float bloomTarget = (s.isTouching || s.isDragging) ? 1.0f : 0.0f;
                    float bloomForce = (bloomTarget - s.bloom) * 200.0f - (s.bloomVelocity * 14.0f);
                    s.bloomVelocity += bloomForce * dt;
                    s.bloom += s.bloomVelocity * dt;

                    // Damped Harmonic Oscillator for Drag Bubble Wobble
                    float dx = s.currentX - s.lastX;
                    s.lastX = s.currentX;
                    float velocity = Math.abs(dx) / Math.max(dt, 0.001f);
                    float targetBubble = s.isDragging ? Math.min(velocity / 1200.0f, 0.50f) : 0.0f;
                    float springForce = (targetBubble - s.bubbleSquish) * 180.0f - (s.bubbleVelocity * 10.0f);
                    s.bubbleVelocity += springForce * dt;
                    s.bubbleSquish += s.bubbleVelocity * dt;

                    if (Math.abs(s.progress - targetProgress) > 0.002f || 
                        Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.bloomVelocity) > 0.01f ||
                        Math.abs(s.colorProgress - targetColorRatio) > 0.005f || 
                        Math.abs(s.bubbleVelocity) > 0.01f || s.bubbleSquish > 0.005f) {
                        view.postInvalidateOnAnimation();
                    }

                    float trackTop = offsetY + (drawH - trackHeight) / 2.0f;
                    float trackBottom = trackTop + trackHeight;
                    float trackRadius = trackHeight / 2.0f;

                    // Expanded Bounds: Vertical is capped just beneath drawH to prevent clipping, Horizontal stretches wide.
                    float maxExpandedH = drawH * 0.92f; 
                    float maxExpandedW = restThumbWidth * 1.60f; 

                    float currentThumbHeight = restThumbHeight + (maxExpandedH - restThumbHeight) * s.bloom;
                    float currentThumbWidth = restThumbWidth + (maxExpandedW - restThumbWidth) * s.bloom;

                    // Dynamic symmetric wobble based on velocity
                    currentThumbWidth *= (1.0f + (s.bubbleSquish * 0.45f));
                    currentThumbHeight *= (1.0f - (s.bubbleSquish * 0.25f));
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

                    // Determine Toggle Color
                    int targetOffColor = !isEnabled ? TRACK_OFF_DISABLED : (isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
                    int targetOnColor = !isEnabled ? TRACK_ON_DISABLED : TRACK_ON;
                    bgPaint.setColor(blendColors(targetOffColor, targetOnColor, s.colorProgress));

                    // 1. Draw Normal Track to Main Canvas
                    canvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

                    // 2. Offscreen Buffer: Manually draw a SHRUNKEN track to simulate perfect physical magnification
                    if (s.trackBitmap == null || s.trackBitmap.getWidth() != width || s.trackBitmap.getHeight() != height) {
                        s.trackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        s.trackCanvas = new Canvas(s.trackBitmap);
                    }
                    s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    
                    float shrinkY = 6.0f * s.bloom; // Precisely squeeze the track when active
                    s.trackCanvas.drawRoundRect(offsetX, trackTop + shrinkY, offsetX + drawW, trackBottom - shrinkY, Math.max(0, trackRadius - shrinkY), Math.max(0, trackRadius - shrinkY), bgPaint);

                    int shadowAlpha = (int)(48 * (1.0f - s.bloom));
                    if (shadowAlpha > 0 && isEnabled) {
                        shadowPaint.setAlpha(shadowAlpha);
                        canvas.drawRoundRect(thumbLeft - 1.0f, thumbTop + 2.0f, thumbRight + 1.0f, thumbBottom + 3.0f, thumbRadius, thumbRadius, shadowPaint);
                    }

                    // 3. Render Glass (refracting the physically shrunken track)
                    renderLiquidGlassThumb(canvas, s, width, height, thumbLeft, thumbTop, thumbRight, thumbBottom, thumbRadius);

                    // White Thumb Crossfade
                    int whiteAlpha = (int)(255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (whiteAlpha > 0) {
                        baseThumbPaint.setAlpha(isEnabled ? whiteAlpha : (int)(whiteAlpha * 0.5f));
                        canvas.drawRoundRect(new RectF(thumbLeft, thumbTop, thumbRight, thumbBottom), thumbRadius, thumbRadius, baseThumbPaint);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

    // ==========================================
    // SLIDER ENGINE
    // ==========================================
    private void applyUnifiedSliderEngine(String className, ClassLoader classLoader) {
        if (hookedDynamicClasses.contains(className)) return;
        Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
        if (clazz == null) return;
        hookedDynamicClasses.add(className);

        try {
            XposedHelpers.findAndHookMethod(clazz, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    MotionEvent event = (MotionEvent) param.args[0];
                    SliderState s = sliderStateMap.get(view);
                    if (s == null) { 
                        s = new SliderState(); 
                        s.lastTime = System.currentTimeMillis();
                        sliderStateMap.put(view, s); 
                    }
                    int action = event.getActionMasked();
                    float x = event.getX();
                    if (action == MotionEvent.ACTION_DOWN) {
                        disableClipping(view);
                        s.isTouching = true; s.isDragging = false; s.touchX = x; s.lastX = x; view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        s.touchX = x; s.isDragging = true; view.postInvalidateOnAnimation();
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        s.isTouching = false; s.isDragging = false; view.postInvalidateOnAnimation();
                    }
                }
            });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Canvas canvas = (Canvas) param.args[0];
                    int w = view.getWidth(), h = view.getHeight();
                    if (w <= 0 || h <= 0) return null;

                    try {
                        Object thumb = XposedHelpers.callMethod(view, "getThumb");
                        if (thumb != null) XposedHelpers.callMethod(view, "setThumb", new Class[]{android.graphics.drawable.Drawable.class}, (Object) null);
                    } catch (Throwable e) {}

                    SliderState s = sliderStateMap.get(view);
                    if (s == null) { 
                        s = new SliderState(); 
                        s.lastTime = System.currentTimeMillis();
                        sliderStateMap.put(view, s); 
                    }

                    int max = 100, min = 0, progress = 0;
                    try { max = (int) XposedHelpers.callMethod(view, "getMax"); } catch (Throwable e) {}
                    try { min = (int) XposedHelpers.callMethod(view, "getMin"); } catch (Throwable e) {}
                    try { progress = (int) XposedHelpers.callMethod(view, "getProgress"); } catch (Throwable e) {}

                    float range = max - min;
                    float discreteRatio = (range > 0) ? (float)(progress - min) / range : 0f;
                    discreteRatio = Math.max(0f, Math.min(1f, discreteRatio));
                    if (s.visualRatio < 0.0f) s.visualRatio = discreteRatio;

                    boolean isDarkMode = (view.getContext().getResources().getConfiguration().uiMode & 0x30) == 0x20;

                    long now = System.currentTimeMillis();
                    float dt = Math.min((now - s.lastTime) / 1000.0f, 0.05f);
                    s.lastTime = now;

                    // Slider Spring Physics (Pop Expansion)
                    float bloomTarget = (s.isTouching || s.isDragging) ? 1.0f : 0.0f;
                    float bloomForce = (bloomTarget - s.bloom) * 200.0f - (s.bloomVelocity * 14.0f);
                    s.bloomVelocity += bloomForce * dt;
                    s.bloom += s.bloomVelocity * dt;

                    // Slider Spring Physics (Velocity Wobble)
                    float dx = s.touchX - s.lastX;
                    s.lastX = s.touchX;
                    float velocity = Math.abs(dx) / Math.max(dt, 0.001f);
                    float targetBubble = s.isDragging ? Math.min(velocity / 500.0f, 0.6f) : 0.0f;
                    float springForce = (targetBubble - s.bubbleSquish) * 180.0f - (s.bubbleVelocity * 6.0f); // Lower damping = wilder wobble
                    s.bubbleVelocity += springForce * dt;
                    s.bubbleSquish += s.bubbleVelocity * dt;

                    // Reduced horizontal expansion, minimal vertical expansion
                    float currentThumbH = 46.0f + (1.0f * s.bloom);
                    float currentThumbW = 82.0f + (16.0f * s.bloom); 
                    
                    // Apply symmetric droplet morph
                    currentThumbW *= (1.0f + (s.bubbleSquish * 0.35f));
                    currentThumbH *= (1.0f - (s.bubbleSquish * 0.15f));
                    float thumbRadius = currentThumbH / 2.0f;

                    float baseLeft = 4.0f, shrinkAmount = s.bloom * 16.0f; 
                    float trackLeft = baseLeft + shrinkAmount, trackRight = (w - baseLeft) - shrinkAmount;
                    float trackW = trackRight - trackLeft;

                    float minCx = baseLeft + (currentThumbW / 2.0f), maxCx = (w - baseLeft) - (currentThumbW / 2.0f);
                    float travelW = maxCx - minCx;

                    float targetRatio = discreteRatio;
                    if (s.isDragging && travelW > 0) {
                        float rawDragRatio = (s.touchX - minCx) / travelW;
                        targetRatio = Math.max(0.0f, Math.min(1.0f, rawDragRatio));
                    }
                    s.visualRatio += (targetRatio - s.visualRatio) * dt * 18.0f;

                    if (Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.bloomVelocity) > 0.01f ||
                        Math.abs(s.visualRatio - targetRatio) > 0.001f ||
                        Math.abs(s.bubbleVelocity) > 0.01f || s.bubbleSquish > 0.005f) {
                        view.postInvalidateOnAnimation();
                    }

                    float trackHeight = 22.0f; 
                    float trackTop = (h - trackHeight) / 2.0f;
                    float trackBottom = trackTop + trackHeight;
                    float trackRadius = trackHeight / 2.0f;

                    // Main Canvas Track
                    bgPaint.setColor(isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
                    canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, trackRadius, trackRadius, bgPaint);

                    float activeW = trackW * s.visualRatio;
                    if (activeW > 0) {
                        activePaint.setColor(TRACK_ON);
                        canvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW, trackBottom, trackRadius, trackRadius, activePaint);
                    }

                    // Offscreen Track for Glass (No shrink for sliders)
                    if (s.trackBitmap == null || s.trackBitmap.getWidth() != w || s.trackBitmap.getHeight() != h) {
                        s.trackBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        s.trackCanvas = new Canvas(s.trackBitmap);
                    }
                    s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    s.trackCanvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, trackRadius, trackRadius, bgPaint);
                    if (activeW > 0) s.trackCanvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW, trackBottom, trackRadius, trackRadius, activePaint);

                    float cx = minCx + travelW * s.visualRatio;
                    float cy = h / 2.0f;
                    float thumbL = cx - (currentThumbW / 2.0f);
                    float thumbR = cx + (currentThumbW / 2.0f);
                    float thumbT = cy - (currentThumbH / 2.0f);
                    float thumbB = cy + (currentThumbH / 2.0f);

                    int shadowAlpha = (int)(48 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (shadowAlpha > 0) {
                        shadowPaint.setAlpha(shadowAlpha);
                        if (!isDarkMode) canvas.drawRoundRect(thumbL - 3.0f, thumbT - 3.0f, thumbR + 3.0f, thumbB + 4.5f, thumbRadius + 1.0f, thumbRadius + 1.0f, shadowPaint);
                        else canvas.drawRoundRect(thumbL, thumbT + 4.0f, thumbR, thumbB + 4.0f, thumbRadius, thumbRadius, shadowPaint);
                    }

                    renderLiquidGlassThumb(canvas, s, w, h, thumbL, thumbT, thumbR, thumbB, thumbRadius);

                    int whiteAlpha = (int)(255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (whiteAlpha > 0) {
                        baseThumbPaint.setAlpha(whiteAlpha);
                        canvas.drawRoundRect(new RectF(thumbL, thumbT, thumbR, thumbB), thumbRadius, thumbRadius, baseThumbPaint);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
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
                            if (className.equals(target)) { isTarget = true; break; }
                        }
                        if (isTarget) {
                            MotionEvent event = (MotionEvent) param.args[0];
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                android.view.ViewParent parent = view.getParent();
                                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                            }
                            XposedHelpers.callMethod(view, "onTouchEvent", event);
                            return true; 
                        }
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }
                });
            }
        } catch (Throwable t) {}

        for (String className : TARGET_TOGGLE_CLASSES) applyUnifiedToggleEngine(className, lpparam.classLoader);
        for (String className : TARGET_SLIDER_CLASSES) applyUnifiedSliderEngine(className, lpparam.classLoader);

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
                                    if (name.contains("HIDDEN") || name.contains("APPEAR")) field.set(param.thisObject, fastSpring);
                                    else field.set(param.thisObject, iosSpring); 
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) {}
        }
    }
}
