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
import android.widget.ImageView;
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
        
        view.setLayerType(View.LAYER_TYPE_NONE, null);
        view.setClipToOutline(false);
        view.setClipBounds(null);
        
        boolean needsElevation = false;
        ToggleState ts = stateMap.get(view);
        if (ts != null && ts.bloom > 0.01f) needsElevation = true;
        SliderState ss = sliderStateMap.get(view);
        if (ss != null && ss.bloom > 0.01f) needsElevation = true;

        float z = needsElevation ? 100f : 0f;
        view.setTranslationZ(z);

        ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof View) {
            View pView = (View) parent;
            pView.setClipToOutline(false);
            
            if (pView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) pView;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                if (depth < 3) vg.setTranslationZ(z);
            }
            parent = pView.getParent();
            depth++;
        }
    }

    private static class GlassState {
        Bitmap trackBitmap;
        Canvas trackCanvas;
        RenderNode blurNode;
        RenderNode glassNode;
        RuntimeShader shader;
        boolean shaderInitialized = false;
        
        float bloom = 0.0f;
        float bloomVelocity = 0.0f; 
        float bubbleSquish = 0.0f;
        float bubbleVelocity = 0.0f; 
        float lastX = 0f;
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
        float touchX = 0.0f;
        boolean isTouching = false;
        boolean isDragging = false;
        long lastTime = System.currentTimeMillis();
    }

    private static final WeakHashMap<Object, ToggleState> stateMap = new WeakHashMap<>();
    private static final WeakHashMap<Object, SliderState> sliderStateMap = new WeakHashMap<>();

    private static final String AGSL_SHADER_TOGGLE = 
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
        "    float pinchX = 1.65; float pinchY = 1.95;\n" +
        "    vec2 lensWarp = center + vec2(diff.x * pinchX, diff.y * pinchY);\n" +
        "    vec2 distFromEdge = min(p, thumbSize - p);\n" +
        "    float edgeDist = min(distFromEdge.x, distFromEdge.y);\n" +
        "    float rimFactor = smoothstep(14.0, 0.0, edgeDist);\n" +
        "    half4 color = content.eval(lensWarp);\n" +
        "    half4 basePixel = content.eval(fragCoord);\n" +
        "    color.rgb = mix(color.rgb, vec3(1.0), 0.15);\n" +
        "    vec3 glowColor = mix(vec3(1.0), basePixel.rgb, 0.85);\n" +
        "    color.rgb = mix(color.rgb, glowColor, rimFactor * 0.90);\n" +
        "    float specular = pow(rimFactor, 3.0);\n" +
        "    color.rgb += specular * 0.45;\n" +
        "    return color;\n" +
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

    private void renderLiquidGlassThumb(Canvas canvas, GlassState s, int w, int h, float thumbL, float thumbT, float thumbR, float thumbB, float thumbRadius, boolean isToggle) {
        if (!canvas.isHardwareAccelerated()) return;
        RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
        
        if (s.blurNode == null) s.blurNode = new RenderNode("BlurNode");
        if (s.glassNode == null) s.glassNode = new RenderNode("GlassNode");

        if (!s.shaderInitialized) {
            float blurRadius = isToggle ? 8.0f : 55.0f;
            RenderEffect blurFx = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
            s.blurNode.setRenderEffect(blurFx);

            try {
                String shaderCode = isToggle ? AGSL_SHADER_TOGGLE : AGSL_SHADER_SLIDER;
                s.shader = new RuntimeShader(shaderCode);
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
            s.shader.setFloatUniform("refractionHeight", 34.0f);
        }

        int exp = 100;
        s.blurNode.setPosition(0, 0, w + exp * 2, h + exp * 2);
        Canvas rcBlur = s.blurNode.beginRecording(w + exp * 2, h + exp * 2);
        rcBlur.translate(exp, exp);
        rcBlur.drawBitmap(s.trackBitmap, 0.0f, 0.0f, bmpPaint);
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

    private void renderDialerToggle(ImageView view, Canvas canvas) {
        int width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) return;

        disableClipping(view);

        ToggleState s = stateMap.get(view);
        if (s == null) {
            s = new ToggleState();
            stateMap.put(view, s);
        }

        boolean isChecked = false;
        int[] states = view.getDrawableState();
        if (states != null) {
            for (int st : states) {
                if (st == 16842912 || st == 16843518 || st == 16842913) {
                    isChecked = true; break;
                }
            }
        }

        boolean isTouching = false;
        ViewParent p = view.getParent();
        while (p instanceof View) {
            View pv = (View) p;
            if (pv.isPressed()) {
                isTouching = true;
                break;
            }
            if (pv.getClass().getSimpleName().equals("HyperCellLayout")) break;
            p = pv.getParent();
        }

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
        s.progress += (targetProgress - s.progress) * dt * 18.0f; 

        float targetColorRatio = (s.progress >= 0.5f) ? 1.0f : 0.0f;
        s.colorProgress += (targetColorRatio - s.colorProgress) * dt * 16.0f;
        s.colorProgress = Math.max(0.0f, Math.min(1.0f, s.colorProgress));

        float bloomTarget = isTouching ? 1.0f : 0.0f;
        float bloomForce = (bloomTarget - s.bloom) * 250.0f - (s.bloomVelocity * 16.0f);
        s.bloomVelocity += bloomForce * dt;
        s.bloom += s.bloomVelocity * dt;

        if (Math.abs(s.progress - targetProgress) > 0.002f || 
            Math.abs(s.bloom - bloomTarget) > 0.005f || Math.abs(s.bloomVelocity) > 0.01f ||
            Math.abs(s.colorProgress - targetColorRatio) > 0.005f) {
            view.postInvalidateOnAnimation();
        }

        float trackTop = offsetY + (drawH - trackHeight) / 2.0f;
        float trackBottom = trackTop + trackHeight;
        float trackRadius = trackHeight / 2.0f;

        float maxExpandedW = restThumbWidth * 1.35f; 
        float currentThumbHeight = restThumbHeight + ((drawH * 0.88f) - restThumbHeight) * s.bloom; 
        float currentThumbWidth = restThumbWidth + (maxExpandedW - restThumbWidth) * s.bloom;
        float thumbRadius = currentThumbHeight / 2.0f;

        float baseMinX = offsetX + (restThumbWidth / 2.0f) + 2.0f;
        float baseMaxX = offsetX + drawW - (restThumbWidth / 2.0f) - 2.0f;

        float targetMinX = offsetX;
        float targetMaxX = offsetX + drawW;

        float minX = baseMinX + (targetMinX - baseMinX) * s.bloom;
        float maxX = baseMaxX + (targetMaxX - baseMaxX) * s.bloom;

        float cx = minX + s.progress * (maxX - minX);
        float cy = offsetY + (drawH / 2.0f);

        float thumbL = cx - (currentThumbWidth / 2.0f);
        float thumbR = cx + (currentThumbWidth / 2.0f);
        float thumbT = cy - (currentThumbHeight / 2.0f);
        float thumbB = cy + (currentThumbHeight / 2.0f);

        int targetOffColor = !isEnabled ? TRACK_OFF_DISABLED : (isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
        int targetOnColor = !isEnabled ? TRACK_ON_DISABLED : TRACK_ON;
        bgPaint.setColor(blendColors(targetOffColor, targetOnColor, s.colorProgress));

        canvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

        if (s.trackBitmap == null || s.trackBitmap.getWidth() != width || s.trackBitmap.getHeight() != height) {
            s.trackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            s.trackCanvas = new Canvas(s.trackBitmap);
        }
        s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        s.trackCanvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

        int shadowAlpha = (int)(48 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
        if (shadowAlpha > 0 && isEnabled) {
            shadowPaint.setAlpha(shadowAlpha);
            canvas.drawRoundRect(thumbL - 1.0f, thumbT + 2.0f, thumbR + 1.0f, thumbB + 3.0f, thumbRadius, thumbRadius, shadowPaint);
        }

        renderLiquidGlassThumb(canvas, s, width, height, thumbL, thumbT, thumbR, thumbB, thumbRadius, true);

        RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
        int glowStroke = blendColors(Color.parseColor("#75FFFFFF"), bgPaint.getColor(), 0.70f * Math.max(0.3f, s.bloom));
        strokePaint.setColor(glowStroke);
        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
        strokePaint.setColor(Color.parseColor("#75FFFFFF"));

        int whiteAlpha = (int)(255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
        if (whiteAlpha > 0) {
            baseThumbPaint.setAlpha(isEnabled ? whiteAlpha : (int)(whiteAlpha * 0.5f));
            canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, baseThumbPaint);
        }
    }

    private void applyDialerToggleEngine(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(ImageView.class, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ImageView view = (ImageView) param.thisObject;
                    int id = view.getId();
                    if (id == View.NO_ID) return;
                    try {
                        String resName = view.getResources().getResourceEntryName(id);
                        if ("secondary_action_iv".equals(resName)) {
                            String ctxName = view.getContext().getClass().getName();
                            if (!ctxName.contains("Setting") && !ctxName.contains("Preference") && !ctxName.contains("CallFeatures")) {
                                return; 
                            }
                            param.setResult(null);
                            Canvas canvas = (Canvas) param.args[0];
                            renderDialerToggle(view, canvas);
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable t) {}
    }

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
                    if (s != null) { view.postInvalidateOnAnimation(); }
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
                    disableClipping(view); 
                    
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
                    
                    // ZONE 1 (THE TOGGLE): Forcefully squash native framework limits so the toggle handles its own swipes!
                    if (!view.isClickable()) view.setClickable(true);
                    if (!view.isLongClickable()) view.setLongClickable(true);
                    
                    Canvas canvas = (Canvas) param.args[0];
                    int width = view.getWidth(), height = view.getHeight();
                    if (width <= 0 || height <= 0) return null;

                    disableClipping(view);
                    try { if (view.getButtonDrawable() != null) view.setButtonDrawable(null); } catch (Throwable t) {}

                    ToggleState s = stateMap.get(view);
                    if (s == null) {
                        s = new ToggleState();
                        s.progress = view.isChecked() ? 1.0f : 0.0f;
                        s.colorProgress = s.progress;
                        stateMap.put(view, s);
                    }

                    // ZONE 2 (THE TEXT ROW): Inject a custom spy listener onto the parent row
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
                            rowView.setOnTouchListener(new View.OnTouchListener() {
                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    int action = event.getActionMasked();
                                    boolean isDown = (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE);
                                    if (finalS.isParentPressed != isDown) {
                                        finalS.isParentPressed = isDown;
                                        finalView.postInvalidateOnAnimation();
                                    }
                                    // VERY IMPORTANT: Return false so the row STILL processes native clicks/ripples!
                                    return false;
                                }
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

                    // Bloom target combines direct touches AND our new row spy listener
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

                    float maxExpandedW = restThumbWidth * 1.35f; 
                    float currentThumbHeight = restThumbHeight + ((drawH * 0.88f) - restThumbHeight) * s.bloom; 
                    float currentThumbWidth = restThumbWidth + (maxExpandedW - restThumbWidth) * s.bloom;
                    float thumbRadius = currentThumbHeight / 2.0f;

                    float baseMinX = offsetX + (restThumbWidth / 2.0f) + 2.0f;
                    float baseMaxX = offsetX + drawW - (restThumbWidth / 2.0f) - 2.0f;

                    float targetMinX = offsetX;
                    float targetMaxX = offsetX + drawW;

                    float minX = baseMinX + (targetMinX - baseMinX) * s.bloom;
                    float maxX = baseMaxX + (targetMaxX - baseMaxX) * s.bloom;

                    float cx = minX + s.progress * (maxX - minX);
                    float cy = offsetY + (drawH / 2.0f);

                    float thumbL = cx - (currentThumbWidth / 2.0f);
                    float thumbR = cx + (currentThumbWidth / 2.0f);
                    float thumbT = cy - (currentThumbHeight / 2.0f);
                    float thumbB = cy + (currentThumbHeight / 2.0f);

                    int targetOffColor = !isEnabled ? TRACK_OFF_DISABLED : (isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
                    int targetOnColor = !isEnabled ? TRACK_ON_DISABLED : TRACK_ON;
                    bgPaint.setColor(blendColors(targetOffColor, targetOnColor, s.colorProgress));

                    canvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

                    if (s.trackBitmap == null || s.trackBitmap.getWidth() != width || s.trackBitmap.getHeight() != height) {
                        s.trackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        s.trackCanvas = new Canvas(s.trackBitmap);
                    }
                    
                    s.trackCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    s.trackCanvas.drawRoundRect(offsetX, trackTop, offsetX + drawW, trackBottom, trackRadius, trackRadius, bgPaint);

                    int shadowAlpha = (int)(48 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (shadowAlpha > 0 && isEnabled) {
                        shadowPaint.setAlpha(shadowAlpha);
                        canvas.drawRoundRect(thumbL - 1.0f, thumbT + 2.0f, thumbR + 1.0f, thumbB + 3.0f, thumbRadius, thumbRadius, shadowPaint);
                    }

                    renderLiquidGlassThumb(canvas, s, width, height, thumbL, thumbT, thumbR, thumbB, thumbRadius, true);

                    RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
                    int glowStroke = blendColors(Color.parseColor("#75FFFFFF"), bgPaint.getColor(), 0.70f * Math.max(0.3f, s.bloom));
                    strokePaint.setColor(glowStroke);
                    canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
                    strokePaint.setColor(Color.parseColor("#75FFFFFF"));

                    int whiteAlpha = (int)(255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (whiteAlpha > 0) {
                        baseThumbPaint.setAlpha(isEnabled ? whiteAlpha : (int)(whiteAlpha * 0.5f));
                        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, baseThumbPaint);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {}
    }

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

                    disableClipping(view);
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

                    float bloomTarget = (s.isTouching || s.isDragging) ? 1.0f : 0.0f;
                    float bloomForce = (bloomTarget - s.bloom) * 250.0f - (s.bloomVelocity * 16.0f);
                    s.bloomVelocity += bloomForce * dt;
                    s.bloom += s.bloomVelocity * dt;

                    float dx = s.touchX - s.lastX;
                    s.lastX = s.touchX;
                    float velocity = Math.abs(dx) / Math.max(dt, 0.001f);
                    float targetBubble = s.isDragging ? Math.min(velocity / 800.0f, 0.75f) : 0.0f;
                    float springForce = (targetBubble - s.bubbleSquish) * 250.0f - (s.bubbleVelocity * 12.0f); 
                    s.bubbleVelocity += springForce * dt;
                    s.bubbleSquish += s.bubbleVelocity * dt;

                    if (s.isTouching && !s.isDragging) s.bubbleVelocity += s.bloomVelocity * 0.20f;

                    float currentThumbH = 46.0f + (1.0f * s.bloom);
                    float currentThumbW = 82.0f + (8.0f * s.bloom); 
                    
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

                    bgPaint.setColor(isDarkMode ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
                    canvas.drawRoundRect(trackLeft, trackTop, trackRight, trackBottom, trackRadius, trackRadius, bgPaint);

                    float activeW = trackW * s.visualRatio;
                    if (activeW > 0) {
                        activePaint.setColor(TRACK_ON);
                        canvas.drawRoundRect(trackLeft, trackTop, trackLeft + activeW, trackBottom, trackRadius, trackRadius, activePaint);
                    }

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

                    renderLiquidGlassThumb(canvas, s, w, h, thumbL, thumbT, thumbR, thumbB, thumbRadius, false);

                    RectF roundRectF = new RectF(thumbL, thumbT, thumbR, thumbB);
                    int glowStroke = blendColors(Color.parseColor("#75FFFFFF"), activePaint.getColor(), 0.70f * Math.max(0.3f, s.bloom));
                    strokePaint.setColor(glowStroke);
                    canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, strokePaint);
                    strokePaint.setColor(Color.parseColor("#75FFFFFF"));

                    int whiteAlpha = (int)(255 * (1.0f - Math.max(0f, Math.min(1f, s.bloom))));
                    if (whiteAlpha > 0) {
                        baseThumbPaint.setAlpha(whiteAlpha);
                        canvas.drawRoundRect(roundRectF, thumbRadius, thumbRadius, baseThumbPaint);
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

        if (lpparam.packageName.equals("com.android.phone") || lpparam.packageName.equals("com.android.contacts") || lpparam.packageName.equals("com.android.settings")) {
            applyDialerToggleEngine(lpparam.classLoader);
        }

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
