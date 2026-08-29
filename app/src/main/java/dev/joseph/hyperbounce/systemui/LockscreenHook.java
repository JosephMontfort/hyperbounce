package dev.joseph.hyperbounce.systemui;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

public class LockscreenHook {
        // 1. AndroidLiquidGlass: 4-Sided True Refraction Shader for Lockscreen Card
        private static final String REFRACTION_SHADER_SRC =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
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
            "    return 1.0 - sqrt(max(0.0, 1.0 - x * x));\n" +
            "}\n" +
            "\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(centeredCoord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) {\n" +
            "        return content.eval(coord);\n" +
            "    }\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / max(0.001, refractionHeight)) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord + float2(0.0001)));\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    return content.eval(refractedCoord);\n" +
            "}\n";

        // 2. AndroidLiquidGlass: Physical Directional Fresnel Highlight Shader for 4-Sided Card Frame
        private static final String FRESNEL_FRAME_SHADER_SRC =
            "uniform float2 size;\n" +
            "uniform float4 cornerRadii;\n" +
            "layout(color) uniform half4 color;\n" +
            "uniform float angle;\n" +
            "uniform float falloff;\n" +
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
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = coord - halfSize;\n" +
            "    float radius = radiusAt(centeredCoord, cornerRadii);\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n" +
            "    float2 normal = float2(cos(angle), sin(angle));\n" +
            "    float d = dot(grad, normal);\n" +
            "    float intensity = pow(abs(d), falloff);\n" +
            "    return color * intensity;\n" +
            "}\n";

        // 3. AndroidLiquidGlass: Radial Specular Highlight Shader for PIN Buttons
        private static final String RADIAL_TOUCH_SHADER_SRC =
            "uniform float2 size;\n" +
            "layout(color) uniform half4 color;\n" +
            "uniform float radius;\n" +
            "uniform float2 position;\n" +
            "\n" +
            "half4 main(float2 coord) {\n" +
            "    float dist = distance(coord, position);\n" +
            "    float intensity = smoothstep(radius, radius * 0.40, dist);\n" +
            "    return color * intensity;\n" +
            "}\n";

        public static class CircleGlassDrawable extends android.graphics.drawable.Drawable {
            private final android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Paint strokePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Paint glowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final float density;
            public float highlightAlpha = 0f;
            public float touchX = -1f;
            public float touchY = -1f;
            private Object radialRuntimeShader = null;

            public CircleGlassDrawable(float density) {
                this.density = density;
                fillPaint.setStyle(android.graphics.Paint.Style.FILL);
                strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
                glowPaint.setStyle(android.graphics.Paint.Style.FILL);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        radialRuntimeShader = new android.graphics.RuntimeShader(RADIAL_TOUCH_SHADER_SRC);
                    } catch (Throwable ignore) {}
                }
            }

            @Override
            public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                
                float radius = (config.pinCircleDiameter / 2.0f) * density;
                if (radius <= 0f) radius = 41f * density;

                float baseFill = Math.max(0f, Math.min(1f, config.pinCircleFillAlpha));
                float boost = Math.max(0f, Math.min(1f, config.pinPressGlowAlpha)) * highlightAlpha;
                float totalFill = Math.min(1.0f, baseFill + boost);

                int fillA = (int) (totalFill * 255);
                fillPaint.setColor(0x00FFFFFF | (fillA << 24));
                
                // Fresnel specular rim lighting with 45-degree top-left light source
                int strokeA = (int) (Math.max(0f, Math.min(1f, config.pinCircleStrokeAlpha)) * 255);
                strokePaint.setStrokeWidth(Math.max(0.5f, config.pinCircleStrokeWidth) * density);
                android.graphics.SweepGradient sweepGrad = new android.graphics.SweepGradient(
                    cx, cy,
                    new int[]{ 
                        0x00FFFFFF | (strokeA << 24), 
                        0x00FFFFFF | (((int) (strokeA * 0.45f)) << 24), 
                        0x00FFFFFF | (((int) (strokeA * 0.90f)) << 24), 
                        0x00FFFFFF | (strokeA << 24) 
                    },
                    new float[]{ 0.0f, 0.45f, 0.75f, 1.0f }
                );
                strokePaint.setShader(sweepGrad);

                canvas.drawCircle(cx, cy, radius, fillPaint);
                canvas.drawCircle(cx, cy, radius, strokePaint);

                // Localized Radial Specular Highlight on touch contact
                if (highlightAlpha > 0.01f && config.pinPressGlowAlpha > 0.01f) {
                    float pX = touchX >= 0 ? touchX : cx;
                    float pY = touchY >= 0 ? touchY : cy;
                    int glowA = (int) (Math.max(0f, Math.min(1f, config.pinPressGlowAlpha * highlightAlpha)) * 255);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && radialRuntimeShader instanceof android.graphics.RuntimeShader) {
                        try {
                            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) radialRuntimeShader;
                            shader.setFloatUniform("size", bounds.width(), bounds.height());
                            shader.setColorUniform("color", 0x00FFFFFF | (glowA << 24));
                            shader.setFloatUniform("radius", radius * Math.max(0.5f, config.pinGlowRadius));
                            shader.setFloatUniform("position", pX, pY);
                            glowPaint.setShader(shader);
                            canvas.drawCircle(cx, cy, radius, glowPaint);
                        } catch (Throwable t) {
                            glowPaint.setShader(null);
                            glowPaint.setColor(0x00FFFFFF | (((int)(glowA * 0.65f)) << 24));
                            canvas.drawCircle(pX, pY, radius * 0.75f, glowPaint);
                        }
                    } else {
                        android.graphics.RadialGradient radGrad = new android.graphics.RadialGradient(
                            pX, pY, radius * Math.max(0.5f, config.pinGlowRadius * 0.8f),
                            0x00FFFFFF | (glowA << 24),
                            0x00FFFFFF,
                            android.graphics.Shader.TileMode.CLAMP
                        );
                        glowPaint.setShader(radGrad);
                        canvas.drawCircle(cx, cy, radius, glowPaint);
                    }
                }
            }

            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                fillPaint.setColorFilter(colorFilter);
                strokePaint.setColorFilter(colorFilter);
                glowPaint.setColorFilter(colorFilter);
            }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        }

        // 4. AndroidLiquidGlass: Specular Fresnel Frame for 4-Sided Lockscreen Card
        public static class LiquidGlassFrameDrawable extends android.graphics.drawable.Drawable {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.Paint strokePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final float density;
            private Object fresnelShader = null;

            public LiquidGlassFrameDrawable(float density) {
                this.density = density;
                paint.setStyle(android.graphics.Paint.Style.FILL);
                strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        fresnelShader = new android.graphics.RuntimeShader(FRESNEL_FRAME_SHADER_SRC);
                    } catch (Throwable ignore) {}
                }
            }

            @Override
            public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                if (bounds.width() <= 0 || bounds.height() <= 0) return;

                int strokeA = (int) (Math.max(0f, Math.min(1f, config.lockCardBorderAlpha)) * 255);
                float strokeW = Math.max(0.5f, config.lockCardBorderWidth) * density;
                float cornerR = 28f * density;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && fresnelShader instanceof android.graphics.RuntimeShader) {
                    try {
                        android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) fresnelShader;
                        shader.setFloatUniform("size", bounds.width(), bounds.height());
                        shader.setFloatUniform("cornerRadii", 0f, 0f, cornerR, cornerR);
                        shader.setColorUniform("color", 0x00FFFFFF | (strokeA << 24));
                        shader.setFloatUniform("angle", 45f * ((float) Math.PI / 180f));
                        shader.setFloatUniform("falloff", 1.2f);
                        strokePaint.setShader(shader);
                        strokePaint.setStrokeWidth(strokeW);
                        
                        android.graphics.RectF rf = new android.graphics.RectF(bounds);
                        canvas.drawRoundRect(rf, cornerR, cornerR, strokePaint);
                        return;
                    } catch (Throwable ignore) {}
                }

                strokePaint.setShader(null);
                strokePaint.setColor(0x00FFFFFF | (strokeA << 24));
                strokePaint.setStrokeWidth(strokeW);
                android.graphics.RectF rf = new android.graphics.RectF(bounds);
                canvas.drawRoundRect(rf, cornerR, cornerR, strokePaint);
            }

            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
                strokePaint.setColorFilter(colorFilter);
            }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        }

        public static class Spring {
            public float current = 1f;
            public float target = 1f;
            public float velocity = 0f;

            public Spring(float cur, float tgt) {
                this.current = cur;
                this.target = tgt;
            }

            public boolean update(float stiffness, float damping) {
                float displacement = current - target;
                float acceleration = (-stiffness * displacement) + (-damping * velocity);
                velocity += acceleration * 0.016f;
                current += velocity * 0.016f;
                return Math.abs(velocity) > 0.001f || Math.abs(displacement) > 0.001f;
            }

            public void snap(float value) {
                current = value;
                target = value;
                velocity = 0f;
            }
        }

        public static class KeyState {
            public final Spring scale = new Spring(1f, 1f);
            public final Spring highlight = new Spring(0f, 0f);
            public final Spring dragX = new Spring(0f, 0f);
            public final Spring dragY = new Spring(0f, 0f);
            public float touchDownX = 0f;
            public float touchDownY = 0f;
            public boolean isPressed = false;
            public boolean isRunning = false;
        }

        private static final java.util.WeakHashMap<View, KeyState> keyStateMap = new java.util.WeakHashMap<>();

        private static void startSpringAnimation(View view, KeyState state) {
            if (state.isRunning) return;
            state.isRunning = true;

            final Runnable springStep = new Runnable() {
                @Override
                public void run() {
                    float stiffness = Math.max(5f, config.pinSpringStiffness);
                    float damping = Math.max(0.5f, config.pinSpringDamping);

                    boolean activeScale = state.scale.update(stiffness, damping);
                    boolean activeHighlight = state.highlight.update(stiffness * 1.5f, damping * 1.2f);
                    boolean activeDragX = state.dragX.update(stiffness * 0.85f, damping * 0.90f);
                    boolean activeDragY = state.dragY.update(stiffness * 0.85f, damping * 0.90f);

                    // AndroidLiquidGlass: Hyperbolic tangent elastic limits for jelly drag
                    float maxOffset = Math.min(view.getWidth(), view.getHeight()) * Math.max(0.10f, config.pinJellyElasticLimit);
                    if (maxOffset <= 0f) maxOffset = 40f;
                    float initialDerivative = 0.25f;
                    float tx = (float) (maxOffset * Math.tanh(initialDerivative * state.dragX.current / maxOffset));
                    float ty = (float) (maxOffset * Math.tanh(initialDerivative * state.dragY.current / maxOffset));
                    view.setTranslationX(tx);
                    view.setTranslationY(ty);

                    // AndroidLiquidGlass: Anisotropic angle stretch & squash (atan2)
                    float dist = (float) Math.hypot(state.dragX.current, state.dragY.current);
                    float offsetAngle = (float) Math.atan2(state.dragY.current, state.dragX.current);
                    float maxDragScale = Math.max(0.0f, config.pinJellyMaxStretch);
                    float stretchMag = (float) Math.min(maxDragScale, (dist / maxOffset) * maxDragScale);

                    float sx = state.scale.current + (float) (stretchMag * Math.abs(Math.cos(offsetAngle)));
                    float sy = state.scale.current + (float) (stretchMag * Math.abs(Math.sin(offsetAngle)));
                    view.setScaleX(sx);
                    view.setScaleY(sy);

                    if (view.getBackground() instanceof CircleGlassDrawable) {
                        CircleGlassDrawable d = (CircleGlassDrawable) view.getBackground();
                        d.highlightAlpha = Math.max(0f, Math.min(1f, state.highlight.current));
                        view.invalidate();
                    }

                    if (activeScale || activeHighlight || activeDragX || activeDragY || state.isPressed) {
                        view.postOnAnimation(this);
                    } else {
                        view.setScaleX(state.scale.target);
                        view.setScaleY(state.scale.target);
                        view.setTranslationX(0f);
                        view.setTranslationY(0f);
                        if (view.getBackground() instanceof CircleGlassDrawable) {
                            CircleGlassDrawable d = (CircleGlassDrawable) view.getBackground();
                            d.highlightAlpha = state.highlight.target;
                            view.invalidate();
                        }
                        state.isRunning = false;
                    }
                }
            };
            view.postOnAnimation(springStep);
        }

    private static final Config config = new Config();
    private static boolean receiverRegistered = false;

    private static float latestVelocity = 0.0f;
    private static long lastVelocityTime = 0;
    private static float shadeTouchDownY = 0.0f;
    private static long shadeTouchDownTime = 0;

    
    

    public static void init(XposedModule module, ClassLoader classLoader, DiagnosticConfig config) {
            // 1. Hook KeyguardPanelViewController (HyperOS Lockscreen Controller)
            try {
                Class<?> kpvcClass = Class.forName("com.android.keyguard.panel.KeyguardPanelViewController", false, classLoader);
                for (Method m : kpvcClass.getDeclaredMethods()) {
                    // Hook updateKeyguardElementsExpansionInternal(float)
                    if (m.getName().startsWith("updateKeyguardElementsExpansionInternal") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == float.class) {
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                float f = ((Number) args[0]).floatValue();
                                Object kpvc = chain.getThisObject();
                                
                                java.lang.reflect.Field rootViewField = kpvc.getClass().getDeclaredField("keyguardRootView");
                                rootViewField.setAccessible(true);
                                View rootView = (View) rootViewField.get(kpvc);

                                
                                View notifView = null;
                                try {
                                    java.lang.reflect.Field nsslField = kpvc.getClass().getDeclaredField("notificationStackScrollLayout");
                                    nsslField.setAccessible(true);
                                    notifView = (View) nsslField.get(kpvc);
                                } catch (Throwable ignore) {}
                                if (notifView == null) {
                                    try {
                                        java.lang.reflect.Field ncpField = kpvc.getClass().getDeclaredField("notificationContainerParent");
                                        ncpField.setAccessible(true);
                                        notifView = (View) ncpField.get(kpvc);
                                    } catch (Throwable ignore) {}
                                }
                                
                                final View finalNotifView = notifView;
                                
                                if (rootView != null) {
                                    float fraction = Math.max(0.0f, Math.min(1.0f, f));
                                    float viewHeight = (float) rootView.getHeight();
                                    if (viewHeight == 0) viewHeight = 2400f;
                                    final float upwardOffset = (1.0f - fraction) * -viewHeight;
                                    
                                    Runnable updateUI = () -> {
                                        // 1. Move root lockscreen layer upward smoothly (120Hz native performance, zero lag)
                                        rootView.setTranslationY(upwardOffset);
                                        rootView.setScaleX(1.0f);
                                        rootView.setScaleY(1.0f);

                                        // AndroidLiquidGlass: 4-Sided True Refraction Shader on Lockscreen Card Sheet
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            try {
                                                float cardW = rootView.getWidth() > 0 ? (float) rootView.getWidth() : 1080f;
                                                float cardH = rootView.getHeight() > 0 ? (float) rootView.getHeight() : 2400f;
                                                float density = rootView.getContext().getResources().getDisplayMetrics().density;
                                                float cornerR = 28f * density;
                                                
                                                android.graphics.RuntimeShader refractionShader = new android.graphics.RuntimeShader(REFRACTION_SHADER_SRC);
                                                refractionShader.setFloatUniform("size", cardW, cardH);
                                                refractionShader.setFloatUniform("offset", 0f, 0f);
                                                refractionShader.setFloatUniform("cornerRadii", 0f, 0f, cornerR, cornerR);
                                                refractionShader.setFloatUniform("refractionHeight", Math.max(1f, config.lockCardRefractionHeight) * density);
                                                refractionShader.setFloatUniform("refractionAmount", Math.max(1f, config.lockCardRefractionAmount) * density);
                                                refractionShader.setFloatUniform("depthEffect", Math.max(0f, config.lockCardDepthEffect));
                                                
                                                android.graphics.RenderEffect shaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(refractionShader, "content");
                                                rootView.setRenderEffect(shaderEffect);
                                            } catch (Throwable ignore) {}
                                        }
                                        
                                        // 2. Move notification container upward in sync
                                        if (finalNotifView != null) {
                                            finalNotifView.setTranslationY(upwardOffset);
                                            finalNotifView.setScaleX(1.0f);
                                            finalNotifView.setScaleY(1.0f);
                                        }
                                        
                                        // 3. Inject Full Container Sheet Outline with Physical Fresnel Specular Highlights
                                        if (rootView instanceof ViewGroup) {
                                            ViewGroup rootGroup = (ViewGroup) rootView;
                                            View fullFrame = rootGroup.findViewWithTag("hyperbounce_full_sheet_frame");
                                            if (fullFrame == null) {
                                                Context context = rootGroup.getContext();
                                                fullFrame = new View(context);
                                                fullFrame.setId(View.generateViewId());
                                                fullFrame.setTag("hyperbounce_full_sheet_frame");
                                                
                                                float density = context.getResources().getDisplayMetrics().density;
                                                fullFrame.setBackground(new LiquidGlassFrameDrawable(density));
                                                
                                                boolean added = false;
                                                try {
                                                    Class<?> clpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", false, rootGroup.getContext().getClassLoader());
                                                    java.lang.reflect.Constructor<?> clpCons = clpClass.getConstructor(int.class, int.class);
                                                    ViewGroup.MarginLayoutParams clp = (ViewGroup.MarginLayoutParams) clpCons.newInstance(
                                                        ViewGroup.LayoutParams.MATCH_PARENT, 
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    );
                                                    clpClass.getField("topToTop").set(clp, 0);
                                                    clpClass.getField("bottomToBottom").set(clp, 0);
                                                    clpClass.getField("startToStart").set(clp, 0);
                                                    clpClass.getField("endToEnd").set(clp, 0);
                                                    rootGroup.addView(fullFrame, 0, clp);
                                                    added = true;
                                                } catch (Throwable ignore) {}
                                                
                                                if (!added) {
                                                    try {
                                                        android.widget.FrameLayout.LayoutParams flp = 
                                                            new android.widget.FrameLayout.LayoutParams(
                                                                ViewGroup.LayoutParams.MATCH_PARENT, 
                                                                ViewGroup.LayoutParams.MATCH_PARENT
                                                            );
                                                        rootGroup.addView(fullFrame, 0, flp);
                                                    } catch (Throwable ignore) {}
                                                }
                                            }
                                        }
                                        
                                        // 4. Inject Bottom Glass Lip & Dynamic Pill into KeyguardBottomAreaView
                                        try {
                                            java.lang.reflect.Field bottomAreaField = kpvc.getClass().getDeclaredField("keyguardBottomAreaInjector");
                                            bottomAreaField.setAccessible(true);
                                            Object bottomAreaInjector = bottomAreaField.get(kpvc);
                                            if (bottomAreaInjector != null) {
                                                java.lang.reflect.Field bViewField = bottomAreaInjector.getClass().getDeclaredField("mKeyguardBottomAreaView");
                                                bViewField.setAccessible(true);
                                                ViewGroup bottomAreaView = (ViewGroup) bViewField.get(bottomAreaInjector);
                                                if (bottomAreaView != null) {
                                                    Context context = bottomAreaView.getContext();
                                                    float density = context.getResources().getDisplayMetrics().density;
                                                    
                                                    // A. Bottom Specular Glass Lip
                                                    View glassRim = bottomAreaView.findViewWithTag("hyperbounce_glass_rim");
                                                    if (glassRim == null) {
                                                        glassRim = new View(context);
                                                        glassRim.setId(View.generateViewId());
                                                        glassRim.setTag("hyperbounce_glass_rim");
                                                        
                                                        int rimHeight = (int) (Math.max(10f, config.lockLipHeight) * density);
                                                        int cornerR = (int) (28 * density);
                                                        int strokeA = (int) (Math.max(0f, Math.min(1f, config.lockCardBorderAlpha)) * 255);
                                                        int strokeColor = 0x00FFFFFF | (strokeA << 24);
                                                        int strokeW = (int) (Math.max(0.5f, config.lockCardBorderWidth) * density);
                                                        
                                                        android.graphics.drawable.GradientDrawable rimDrawable = new android.graphics.drawable.GradientDrawable();
                                                        rimDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                                                        rimDrawable.setColors(new int[]{ 0x03FFFFFF, 0x25FFFFFF });
                                                        rimDrawable.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
                                                        rimDrawable.setCornerRadii(new float[]{ 0, 0, 0, 0, cornerR, cornerR, cornerR, cornerR });
                                                        rimDrawable.setStroke(strokeW, strokeColor);
                                                        
                                                        glassRim.setBackground(rimDrawable);
                                                        glassRim.setElevation(4 * density);
                                                        
                                                        android.widget.FrameLayout.LayoutParams rlp = 
                                                            new android.widget.FrameLayout.LayoutParams(
                                                                ViewGroup.LayoutParams.MATCH_PARENT, 
                                                                rimHeight, 
                                                                android.view.Gravity.BOTTOM
                                                            );
                                                        bottomAreaView.addView(glassRim, 0, rlp);
                                                    }
                                                    
                                                    // B. Dynamic iOS Home Indicator Bar
                                                    View pill = bottomAreaView.findViewWithTag("hyperbounce_home_pill");
                                                    if (pill == null) {
                                                        pill = new View(context);
                                                        pill.setId(View.generateViewId());
                                                        pill.setTag("hyperbounce_home_pill");
                                                        
                                                        int pillW = (int) (Math.max(40f, config.lockPillWidth) * density);
                                                        int pillH = (int) (Math.max(2f, config.lockPillHeight) * density);
                                                        int radius = pillH / 2;
                                                        int bMargin = (int) (config.lockPillBottomMargin * density);
                                                        
                                                        android.graphics.drawable.GradientDrawable pillDrawable = new android.graphics.drawable.GradientDrawable();
                                                        pillDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                                                        pillDrawable.setColor(0xF0FFFFFF);
                                                        pillDrawable.setCornerRadius(radius);
                                                        pill.setBackground(pillDrawable);
                                                        pill.setElevation(8 * density);
                                                        
                                                        android.widget.FrameLayout.LayoutParams flp = 
                                                            new android.widget.FrameLayout.LayoutParams(
                                                                pillW, 
                                                                pillH, 
                                                                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL
                                                            );
                                                        flp.bottomMargin = bMargin;
                                                        bottomAreaView.addView(pill, flp);
                                                    }
                                                }
                                            }
                                        } catch (Throwable t) {
                                            android.util.Log.e("Hyperbounce", "Rim/Pill injection error", t);
                                        }
                                    };
                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                        updateUI.run();
                                    } else {
                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(updateUI);
                                    }
                                }
                            } catch (Throwable t) {
                                android.util.Log.e("Hyperbounce", "KPVC expansion hook error", t);
                            }
                            return result;
                        });
                    }
                    
                    // Hook doScale / doScale$1 to completely disable shrinking towards center
                    if (m.getName().startsWith("doScale") && m.getParameterTypes().length == 2 && m.getParameterTypes()[0] == float.class && View.class.isAssignableFrom(m.getParameterTypes()[1])) {
                        module.hook(m).intercept(chain -> {
                            try {
                                Object rawArgs = chain.getArgs();
                                Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                                View v = (View) args[1];
                                if (v != null) {
                                    Runnable resetScale = () -> {
                                        v.setScaleX(1.0f);
                                        v.setScaleY(1.0f);
                                    };
                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                        resetScale.run();
                                    } else {
                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(resetScale);
                                    }
                                }
                            } catch (Throwable t) {}
                            return null;
                        });
                    }

                    // Hook startUnlockKeyguardDismissAnim for smooth unlock dismissal upward
                    if (m.getName().equals("startUnlockKeyguardDismissAnim")) {
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object kpvc = chain.getThisObject();
                                java.lang.reflect.Field rootViewField = kpvc.getClass().getDeclaredField("keyguardRootView");
                                rootViewField.setAccessible(true);
                                View rootView = (View) rootViewField.get(kpvc);
                                
                                View notifView = null;
                                try {
                                    java.lang.reflect.Field nsslField = kpvc.getClass().getDeclaredField("notificationStackScrollLayout");
                                    nsslField.setAccessible(true);
                                    notifView = (View) nsslField.get(kpvc);
                                } catch (Throwable ignore) {}
                                if (notifView == null) {
                                    try {
                                        java.lang.reflect.Field ncpField = kpvc.getClass().getDeclaredField("notificationContainerParent");
                                        ncpField.setAccessible(true);
                                        notifView = (View) ncpField.get(kpvc);
                                    } catch (Throwable ignore) {}
                                }
                                
                                final View finalNotifView = notifView;
                                
                                if (rootView != null) {
                                    Runnable animUI = () -> {
                                        float viewHeight = (float) rootView.getHeight();
                                        if (viewHeight == 0) viewHeight = 2400f;
                                        rootView.animate()
                                            .translationY(-viewHeight)
                                            .alpha(0.0f)
                                            .setDuration(220)
                                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                                            .start();
                                        if (finalNotifView != null) {
                                            finalNotifView.animate()
                                                .translationY(-viewHeight)
                                                .alpha(0.0f)
                                                .setDuration(220)
                                                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                                                .start();
                                        }
                                    };
                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                        animUI.run();
                                    } else {
                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(animUI);
                                    }
                                }
                            } catch (Throwable t) {}
                            return result;
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("Hyperbounce", "KPVC class hook error", t);
            }

            // 2. Disable clock scaling flag in KeyguardClockContainer
            try {
                Class<?> clockContainerClass = Class.forName("com.android.keyguard.clock.KeyguardClockContainer", false, classLoader);
                for (Method m : clockContainerClass.getDeclaredMethods()) {
                    if (m.getName().equals("updateMagazineView") || m.getName().equals("onFinishInflate")) {
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object container = chain.getThisObject();
                                java.lang.reflect.Field canScaleField = container.getClass().getDeclaredField("mClockCanScale");
                                canScaleField.setAccessible(true);
                                canScaleField.setBoolean(container, false);
                            } catch (Throwable t) {}
                            return result;
                        });
                    }
                }
            } catch (Throwable t) {}

            // 3. Instant Touch Squish on MiuiNumPadKey (Reacts Immediately on Hardware Touch Down/Up)
            try {
                Class<?> keyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader);
                for (Method m : keyClass.getDeclaredMethods()) {
                    if (m.getName().equals("onTouchEvent") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == android.view.MotionEvent.class) {
                        module.hook(m).intercept(chain -> {
                            View view = (View) chain.getThisObject();
                            Object rawArgs = chain.getArgs();
                            Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                            android.view.MotionEvent event = (android.view.MotionEvent) args[0];
                            
                            if (event != null) {
                                KeyState state = keyStateMap.get(view);
                                if (state == null) {
                                    state = new KeyState();
                                    keyStateMap.put(view, state);
                                }

                                int action = event.getActionMasked();
                                if (action == android.view.MotionEvent.ACTION_DOWN) {
                                    android.view.ViewParent p = view.getParent();
                                    while (p != null) {
                                        p.requestDisallowInterceptTouchEvent(true);
                                        p = p.getParent();
                                    }
                                    try {
                                        Method setAnimMethod = view.getClass().getMethod("setAnimationEnabled", boolean.class);
                                        setAnimMethod.setAccessible(true);
                                        setAnimMethod.invoke(view, false);
                                    } catch (Throwable ignore) {}

                                    state.touchDownX = event.getX();
                                    state.touchDownY = event.getY();
                                    state.isPressed = true;
                                    state.scale.target = (float) config.pinTapSquishScale;
                                    state.highlight.target = 1.0f;
                                    state.dragX.target = 0f;
                                    state.dragY.target = 0f;

                                    if (view.getBackground() instanceof CircleGlassDrawable) {
                                        CircleGlassDrawable d = (CircleGlassDrawable) view.getBackground();
                                        d.touchX = event.getX();
                                        d.touchY = event.getY();
                                    }
                                    startSpringAnimation(view, state);
                                } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                                    float dx = event.getX() - state.touchDownX;
                                    float dy = event.getY() - state.touchDownY;
                                    state.dragX.target = dx;
                                    state.dragY.target = dy;

                                    if (view.getBackground() instanceof CircleGlassDrawable) {
                                        CircleGlassDrawable d = (CircleGlassDrawable) view.getBackground();
                                        d.touchX = event.getX();
                                        d.touchY = event.getY();
                                    }
                                    startSpringAnimation(view, state);
                                } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                                    state.isPressed = false;
                                    if (action == android.view.MotionEvent.ACTION_UP) {
                                        state.scale.current = (float) (config.pinTapSquishScale - config.pinReleasePlunge);
                                    }
                                    state.scale.target = 1.0f;
                                    state.highlight.target = 0.0f;
                                    state.dragX.target = 0f;
                                    state.dragY.target = 0f;
                                    startSpringAnimation(view, state);
                                }
                            }
                            return chain.proceed();
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("Hyperbounce", "MiuiNumPadKey touch squish hook error", t);
            }

            // 4. Hook MiuiKeyguardPINView for Row Spacing Hijack, Dynamic Sized Circle Glass Backgrounds, Centered Numbers & Y-Axis Glide Entry
            try {
                Class<?> pinViewClass = Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader);

                final java.util.function.Consumer<View> styleKeyAsCircle = (keyView) -> {
                    if (keyView == null) return;
                    try {
                        Context ctx = keyView.getContext();
                        float density = ctx.getResources().getDisplayMetrics().density;
                        
                        try {
                            Method setAnimMethod = keyView.getClass().getMethod("setAnimationEnabled", boolean.class);
                            setAnimMethod.setAccessible(true);
                            setAnimMethod.invoke(keyView, false);
                        } catch (Throwable ignore) {}

                        keyView.setClipToOutline(false);
                        if (keyView.getParent() instanceof ViewGroup) ((ViewGroup) keyView.getParent()).setClipChildren(false);
                        if (keyView.getParent() != null && keyView.getParent().getParent() instanceof ViewGroup) {
                            ((ViewGroup) keyView.getParent().getParent()).setClipChildren(false);
                        }
                        // Set Perfect Circle Glass Background (Custom Dynamic Circle Drawable)
                        keyView.setBackground(new CircleGlassDrawable(density));
                        keyView.setElevation(6 * density);
                        
                        // Toggle klondike letters (ABC, DEF, etc.) based on config
                        try {
                            java.lang.reflect.Field klondikeField = keyView.getClass().getDeclaredField("mKlondikeText");
                            klondikeField.setAccessible(true);
                            android.widget.TextView klondikeText = (android.widget.TextView) klondikeField.get(keyView);
                            if (klondikeText != null) {
                                klondikeText.setVisibility(config.pinShowKlondike ? View.VISIBLE : View.GONE);
                                if (config.pinShowKlondike) {
                                    klondikeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
                                    klondikeText.setTextColor(0xCCFFFFFF);
                                }
                            }
                        } catch (Throwable ignore) {}
                        
                        // Center and style digit text (numbers)
                        try {
                            java.lang.reflect.Field digitTextField = keyView.getClass().getDeclaredField("mDigitText");
                            digitTextField.setAccessible(true);
                            android.widget.TextView digitText = (android.widget.TextView) digitTextField.get(keyView);
                            if (digitText != null) {
                                digitText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(18f, config.pinDigitTextSize));
                                digitText.setTextColor(0xFFFFFFFF);
                                digitText.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
                                digitText.setGravity(android.view.Gravity.CENTER);
                            }
                        } catch (Throwable ignore) {}
                    } catch (Throwable ignore) {}
                };

                final java.util.function.Consumer<View> applyPinLayoutAndStyles = (pinView) -> {
                    if (pinView == null) return;
                    try {
                        Context ctx = pinView.getContext();
                        float density = ctx.getResources().getDisplayMetrics().density;
                        
                        // 1. Apply row translation vertical spacing from config
                        int row1Id = ctx.getResources().getIdentifier("row1", "id", "com.android.systemui");
                        int row2Id = ctx.getResources().getIdentifier("row2", "id", "com.android.systemui");
                        int row3Id = ctx.getResources().getIdentifier("row3", "id", "com.android.systemui");
                        int row4Id = ctx.getResources().getIdentifier("row4", "id", "com.android.systemui");
                        int row0Id = ctx.getResources().getIdentifier("row0", "id", "com.android.systemui");
                        
                        if (row1Id != 0) { View r1 = pinView.findViewById(row1Id); if (r1 != null) r1.setTranslationY(config.pinRow1Offset * density); }
                        if (row2Id != 0) { View r2 = pinView.findViewById(row2Id); if (r2 != null) r2.setTranslationY(config.pinRow2Offset * density); }
                        if (row3Id != 0) { View r3 = pinView.findViewById(row3Id); if (r3 != null) r3.setTranslationY(config.pinRow3Offset * density); }
                        if (row4Id != 0) { View r4 = pinView.findViewById(row4Id); if (r4 != null) r4.setTranslationY(0f); }
                        if (row0Id != 0) { View r0 = pinView.findViewById(row0Id); if (r0 != null) r0.setTranslationY(config.pinRow0Offset * density); }
                        
                        // 2. Style numeric digit keys 1-9 and 0 with circular frosted glass
                        int[] numKeyIds = new int[]{
                            ctx.getResources().getIdentifier("key1", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key2", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key3", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key4", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key5", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key6", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key7", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key8", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key9", "id", "com.android.systemui"),
                            ctx.getResources().getIdentifier("key0", "id", "com.android.systemui")
                        };
                        for (int id : numKeyIds) {
                            if (id != 0) {
                                View k = pinView.findViewById(id);
                                if (k != null) styleKeyAsCircle.accept(k);
                            }
                        }
                        
                        // 3. Keep Delete, Cancel, and Emergency transparent
                        int delId = ctx.getResources().getIdentifier("delete_button", "id", "com.android.systemui");
                        if (delId != 0) { View del = pinView.findViewById(delId); if (del != null) del.setBackground(null); }
                        int cancelId = ctx.getResources().getIdentifier("cancel_button", "id", "com.android.systemui");
                        if (cancelId != 0) { View cancel = pinView.findViewById(cancelId); if (cancel != null) cancel.setBackground(null); }
                        int emerId = ctx.getResources().getIdentifier("emergency_call_button", "id", "com.android.systemui");
                        if (emerId != 0) { View emer = pinView.findViewById(emerId); if (emer != null) emer.setBackground(null); }
                    } catch (Throwable ignore) {}
                };

                for (Method m : pinViewClass.getDeclaredMethods()) {
                    if (m.getName().equals("onFinishInflate") || m.getName().equals("updatePositionForFod") || m.getName().equals("updateLayoutParams")) {
                        module.hook(m).intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                View pinView = (View) chain.getThisObject();
                                applyPinLayoutAndStyles.accept(pinView);
                            } catch (Throwable ignore) {}
                            return result;
                        });
                    }

                    // Hook startAppearAnimation for Y-Axis Glide & Staggered Sequential Entrance
                    if (m.getName().equals("startAppearAnimation")) {
                        module.hook(m).intercept(chain -> {
                            Object pinViewObj = chain.getThisObject();
                            View pinView = (View) pinViewObj;
                            Context ctx = pinView.getContext();
                            float density = ctx.getResources().getDisplayMetrics().density;
                            
                            try {
                                android.util.Pair[][] pairs = new android.util.Pair[4][3];
                                for (int r = 0; r < 4; r++) {
                                    for (int c = 0; c < 3; c++) {
                                        pairs[r][c] = new android.util.Pair(0, 0);
                                    }
                                }
                                java.lang.reflect.Field keysAnimField = pinViewObj.getClass().getField("keysAnimStartState");
                                keysAnimField.setAccessible(true);
                                keysAnimField.set(pinViewObj, pairs);
                                
                                java.lang.reflect.Field appearAnimField = pinViewObj.getClass().getField("mAppearAnimating");
                                appearAnimField.setAccessible(true);
                                appearAnimField.setBoolean(pinViewObj, true);
                                
                                java.lang.reflect.Field initAppearField = pinViewObj.getClass().getField("initAppearAnimed");
                                initAppearField.setAccessible(true);
                                initAppearField.setBoolean(pinViewObj, true);
                                
                                java.lang.reflect.Field canceledField = pinViewObj.getClass().getField("appearAnimCanceled");
                                canceledField.setAccessible(true);
                                canceledField.setBoolean(pinViewObj, false);
                            } catch (Throwable ignore) {}
                            
                            Runnable runCustomAppear = () -> {
                                try {
                                    pinView.setAlpha(1.0f);
                                    applyPinLayoutAndStyles.accept(pinView);
                                    
                                    // 1. PIN dots fade in smoothly
                                    int row0Id = ctx.getResources().getIdentifier("row0", "id", "com.android.systemui");
                                    if (row0Id != 0) {
                                        View row0 = pinView.findViewById(row0Id);
                                        if (row0 != null) {
                                            row0.setAlpha(0.0f);
                                            row0.setTranslationY((config.pinRow0Offset - 15f) * density);
                                            row0.animate()
                                                .alpha(1.0f)
                                                .translationY(config.pinRow0Offset * density)
                                                .setDuration(340)
                                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                                .start();
                                        }
                                    }
                                    
                                    // 2. Y-Axis Glide & Settling Matrix for Keys
                                    String[][] gridNames = new String[][]{
                                        { "key1", "key2", "key3" },
                                        { "key4", "key5", "key6" },
                                        { "key7", "key8", "key9" },
                                        { "delete_button", "key0", "cancel_button" }
                                    };
                                    
                                    float initialYDrop = config.pinEntryTranslationY * density;
                                    long animDuration = (long) Math.max(150f, config.pinEntryDuration);
                                    float overshootFactor = Math.max(0.0f, config.pinEntryOvershoot);
                                    android.view.animation.Interpolator iosGlideSpring = (overshootFactor > 0.01f)
                                        ? new android.view.animation.OvershootInterpolator(overshootFactor)
                                        : new android.view.animation.PathInterpolator(0.18f, 0.90f, 0.25f, 1.0f);
                                    
                                    for (int row = 0; row < gridNames.length; row++) {
                                        for (int col = 0; col < gridNames[row].length; col++) {
                                            String name = gridNames[row][col];
                                            int id = ctx.getResources().getIdentifier(name, "id", "com.android.systemui");
                                            if (id == 0) continue;
                                            View key = pinView.findViewById(id);
                                            if (key != null) {
                                                // Check for individual per-key delay override or use default stagger step
                                                float customDelay = -1f;
                                                long delay = (customDelay >= 0f) ? (long) customDelay : (long) ((row * config.pinRowDelayStep) + (col * config.pinColDelayStep));
                                                
                                                // Start position: deep below resting place (glide up along Y-axis into position)
                                                key.setAlpha(0.0f);
                                                key.setScaleX(config.pinEntryInitialScale);
                                                key.setScaleY(config.pinEntryInitialScale);
                                                key.setTranslationY(initialYDrop);
                                                
                                                key.animate()
                                                    .alpha(1.0f)
                                                    .scaleX(1.0f)
                                                    .scaleY(1.0f)
                                                    .translationY(0.0f)
                                                    .setDuration(animDuration)
                                                    .setStartDelay(delay)
                                                    .setInterpolator(iosGlideSpring)
                                                    .start();
                                            }
                                        }
                                    }
                                    
                                    int emerId = ctx.getResources().getIdentifier("emergency_call_button", "id", "com.android.systemui");
                                    if (emerId != 0) {
                                        View emer = pinView.findViewById(emerId);
                                        if (emer != null) {
                                            float customEmerDelay = -1f;
                                            long emerDelay = (customEmerDelay >= 0f) ? (long) customEmerDelay : (long) (config.pinRowDelayStep * 4);
                                            emer.setAlpha(0.0f);
                                            emer.setTranslationY(25f * density);
                                            emer.animate()
                                                .alpha(1.0f)
                                                .translationY(0.0f)
                                                .setDuration(animDuration)
                                                .setStartDelay(emerDelay)
                                                .setInterpolator(iosGlideSpring)
                                                .start();
                                        }
                                    }
                                } catch (Throwable t) {
                                    android.util.Log.e("Hyperbounce", "Custom appear animation error", t);
                                }
                            };
                            
                            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                runCustomAppear.run();
                            } else {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(runCustomAppear);
                            }
                            
                            return null;
                        });
                    }

                    // Hook startBackAnimation for clean crash-free dismiss on BACK press
                    if (m.getName().equals("startBackAnimation")) {
                        module.hook(m).intercept(chain -> {
                            Object pinViewObj = chain.getThisObject();
                            View pinView = (View) pinViewObj;
                            Object rawArgs = chain.getArgs();
                            Object[] args = (rawArgs instanceof java.util.List) ? ((java.util.List<?>) rawArgs).toArray() : (Object[]) rawArgs;
                            Runnable callback = (args.length > 0 && args[0] instanceof Runnable) ? (Runnable) args[0] : null;
                            
                            try {
                                pinView.animate()
                                    .alpha(0.0f)
                                    .setDuration(220)
                                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                                    .withEndAction(() -> {
                                        if (callback != null) callback.run();
                                    })
                                    .start();
                            } catch (Throwable t) {
                                if (callback != null) callback.run();
                            }
                            return null;
                        });
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("Hyperbounce", "PIN view hook error", t);
            }

    }
}
