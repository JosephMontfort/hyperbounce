package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.whatsapp")) {
            XposedBridge.log("[HyperBounce] Executing isolated WhatsApp Liquid Glass Engine...");
            hookWhatsApp(lpparam.classLoader);
        }
    }

    private static final String AGSL_WHATSAPP_GLASS = 
        "uniform shader child;\n" +
        "uniform float2 halfView;\n" +
        "uniform float2 halfViewFloor;\n" +
        "uniform float4 cornerRadii;\n" +
        "uniform float strokeWidth;\n" +
        "uniform float innerBlurRadius;\n" +
        "uniform float innerBlurRadiusSq;\n" +
        "uniform float highlightAlpha;\n" +
        "layout(color) uniform half4 strokeColor;\n" +
        "uniform float strokeAlphaMul;\n" +
        "uniform float3 lightDir1;\n" +
        "layout(color) uniform half4 lightColor1;\n" +
        "uniform float lightIntensity1;\n" +
        "uniform float3 lightDir2;\n" +
        "layout(color) uniform half4 lightColor2;\n" +
        "uniform float lightIntensity2;\n" +
        "float pickRadius(float2 fragCoord, float4 radii) {\n" +
        "    float2 up = fragCoord.y < halfView.y ? radii.xy : radii.zw;\n" +
        "    return fragCoord.x < halfView.x ? up.x : up.y;\n" +
        "}\n" +
        "float roundedBoxSDF(float2 pos, float2 halfSize, float radius) {\n" +
        "    radius = min(radius, min(halfSize.x, halfSize.y));\n" +
        "    float2 d = pos - halfSize + radius;\n" +
        "    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;\n" +
        "}\n" +
        "float3 getNormal(float2 fragCoord, float sdf, float R) {\n" +
        "    float2 xy = fragCoord - halfViewFloor;\n" +
        "    float2 xy_a = abs(xy);\n" +
        "    float t = smoothstep(-innerBlurRadius, 0.0, sdf);\n" +
        "    float z = sqrt(max(innerBlurRadiusSq - t * t, 0.0));\n" +
        "    float3 coord = float3(xy_a, -z);\n" +
        "    float2 corner = halfView - R;\n" +
        "    corner.x = min(corner.x, xy_a.x);\n" +
        "    corner.y = min(corner.y, xy_a.y);\n" +
        "    float2 dir = normalize(coord.xy - corner.xy);\n" +
        "    corner += dir * (R - innerBlurRadius);\n" +
        "    if (any(lessThan(xy_a, corner))) {\n" +
        "        return float3(0.0, 0.0, -1.0);\n" +
        "    }\n" +
        "    float2 signal = sign(xy);\n" +
        "    float3 n = normalize(coord - float3(corner, 0.0));\n" +
        "    n.xy *= signal;\n" +
        "    return n;\n" +
        "}\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 xy = abs(fragCoord - halfView);\n" +
        "    float originRadius = pickRadius(fragCoord, cornerRadii);\n" +
        "    float R = max(originRadius, innerBlurRadius);\n" +
        "    if (all(lessThan(xy, halfView - R))) {\n" +
        "        return child.eval(fragCoord);\n" +
        "    }\n" +
        "    float sdf = roundedBoxSDF(xy, halfView, originRadius);\n" +
        "    half outMask = half(smoothstep(0.0, -1.0, sdf));\n" +
        "    float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);\n" +
        "    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);\n" +
        "    float3 n = getNormal(fragCoord, sdf, R);\n" +
        "    float l1 = dot(n.xy, lightDir1.xy);\n" +
        "    rgb += half(l1 * l1 * lightIntensity1) * lightColor1.rgb;\n" +
        "    float l2 = dot(n.xy, lightDir2.xy);\n" +
        "    rgb += half(l2 * l2 * lightIntensity2) * lightColor2.rgb;\n" +
        "    half4 bg = child.eval(fragCoord);\n" +
        "    return half4(bg.rgb + (rgb * half(highlightAlpha)), bg.a) * outMask;\n" +
        "}";

    private void hookWhatsApp(ClassLoader classLoader) {
        XposedBridge.log("[HyperBounce] Starting WDSBottomBar tracking...");
        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", classLoader);
        if (wdsBottomBarClass == null) {
            XposedBridge.log("[HyperBounce] ERROR: WDSBottomBar not found.");
            return;
        }

        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup realBar = (ViewGroup) param.thisObject;
                XposedBridge.log("[HyperBounce] WDSBottomBar created.");

                realBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        realBar.post(() -> executeRipAndFloat(realBar));
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });
    }

    private void executeRipAndFloat(ViewGroup realBar) {
        try {
            if (realBar.findViewWithTag("HyperGlassOverlay") != null) {
                return; // Already initialized
            }

            XposedBridge.log("[HyperBounce] [STEP 1] Starting Rip & Float sequence...");
            ViewGroup parent = (ViewGroup) realBar.getParent();
            
            if (parent != null && parent.getClass().getName().contains("LinearLayout")) {
                XposedBridge.log("[HyperBounce] [STEP 2] Ripping WDSBottomBar out of LinearLayout!");
                int index = parent.indexOfChild(realBar);
                parent.removeView(realBar);

                // Add an invisible spacer so the chat list doesn't get covered by our floating bar
                View spacer = new View(realBar.getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200));
                parent.addView(spacer, index);

                XposedBridge.log("[HyperBounce] [STEP 3] Attaching to DecorView root...");
                ViewGroup root = (ViewGroup) realBar.getRootView();
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 180
                );
                flp.gravity = android.view.Gravity.BOTTOM;
                flp.setMargins(48, 0, 48, 60); // Side and bottom margins to make it float
                root.addView(realBar, flp);
            }

            XposedBridge.log("[HyperBounce] [STEP 4] Nuking Stock Indicator via Reflection...");
            try {
                XposedHelpers.callMethod(realBar, "setItemActiveIndicatorEnabled", false);
                XposedBridge.log("[HyperBounce] SUCCESS: setItemActiveIndicatorEnabled(false) called.");
            } catch (Throwable t) {
                XposedBridge.log("[HyperBounce] Reflection failed (Material 3 method missing): " + t.getMessage());
            }

            try {
                XposedHelpers.callMethod(realBar, "setItemBackground", (Object) null);
                XposedBridge.log("[HyperBounce] SUCCESS: setItemBackground(null) called.");
            } catch (Throwable t) {}

            XposedBridge.log("[HyperBounce] [STEP 5] Building Glass Overlay & Custom Indicator...");
            View glassOverlay = new View(realBar.getContext()) {
                private final Paint glassBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RuntimeShader glassShader = new RuntimeShader(AGSL_WHATSAPP_GLASS);
                private final RenderEffect blurFx = RenderEffect.createBlurEffect(45.0f, 45.0f, Shader.TileMode.CLAMP);

                {
                    glassBgPaint.setColor(Color.parseColor("#30000000")); // Base tint
                    indicatorPaint.setColor(Color.parseColor("#25FFFFFF")); // Custom iOS Pill Color
                }

                @Override
                protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                    super.onSizeChanged(w, h, oldw, oldh);
                    if (w <= 0 || h <= 0) return;

                    float halfW = w / 2.0f;
                    float halfH = h / 2.0f;

                    glassShader.setFloatUniform("halfView", halfW, halfH);
                    glassShader.setFloatUniform("halfViewFloor", (float) Math.floor(halfW), (float) Math.floor(halfH));
                    glassShader.setFloatUniform("cornerRadii", halfH, halfH, halfH, halfH);
                    glassShader.setFloatUniform("innerBlurRadius", 28.0f);
                    glassShader.setFloatUniform("innerBlurRadiusSq", 28.0f * 28.0f);
                    glassShader.setFloatUniform("strokeWidth", 2.0f);
                    glassShader.setFloatUniform("strokeAlphaMul", 1.2f);
                    glassShader.setFloatUniform("highlightAlpha", 0.80f);
                    glassShader.setColorUniform("strokeColor", Color.parseColor("#60FFFFFF"));
                    
                    glassShader.setFloatUniform("lightDir1", -0.707f, -0.707f, 0.5f);
                    glassShader.setColorUniform("lightColor1", Color.WHITE);
                    glassShader.setFloatUniform("lightIntensity1", 1.1f);
                    glassShader.setFloatUniform("lightDir2", 0.707f, 0.707f, 0.2f);
                    glassShader.setColorUniform("lightColor2", Color.parseColor("#90FFFFFF"));
                    glassShader.setFloatUniform("lightIntensity2", 0.5f);

                    RenderEffect glassFx = RenderEffect.createRuntimeShaderEffect(glassShader, "child");
                    this.setRenderEffect(RenderEffect.createChainEffect(glassFx, blurFx));
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    
                    // Draw Glass Base
                    RectF rect = new RectF(0, 0, getWidth(), getHeight());
                    float radius = rect.height() / 2.0f;
                    canvas.drawRoundRect(rect, radius, radius, glassBgPaint);

                    // Dynamically scan for active tab and draw custom iOS Pill
                    try {
                        if (realBar.getChildCount() > 1) {
                            ViewGroup menuContainer = (ViewGroup) realBar.getChildAt(1); // Overlay is 0, Menu is 1
                            for (int i = 0; i < menuContainer.getChildCount(); i++) {
                                View tab = menuContainer.getChildAt(i);
                                
                                // FORCE strip stock backgrounds
                                if (tab.getBackground() != null) tab.setBackground(null);
                                
                                if (tab.isSelected()) {
                                    // Calculate exact coordinates of the selected tab
                                    float left = tab.getLeft() + menuContainer.getLeft();
                                    float right = tab.getRight() + menuContainer.getLeft();
                                    float top = tab.getTop() + menuContainer.getTop();
                                    float bottom = tab.getBottom() + menuContainer.getTop();

                                    // Shrink it down to a neat pill shape inside the glass
                                    RectF pillRect = new RectF(left + 16, top + 16, right - 16, bottom - 16);
                                    canvas.drawRoundRect(pillRect, 40f, 40f, indicatorPaint);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("HyperBounce", "Error drawing custom indicator: ", e);
                    }
                }
            };

            glassOverlay.setTag("HyperGlassOverlay");
            glassOverlay.setClickable(false);
            glassOverlay.setFocusable(false);

            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            realBar.addView(glassOverlay, 0, flp);
            XposedBridge.log("[HyperBounce] [STEP 6] Glass Overlay injected at index 0.");

            // 7. Aggressive Continuous Background Stripping
            // This ensures the black box NEVER comes back when WhatsApp redraws
            realBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    // Force the overlay to redraw our custom indicator when tabs are clicked
                    glassOverlay.invalidate();

                    // Strip WDSBottomBar background
                    if (realBar.getBackground() != null) realBar.setBackground(null);

                    // Strip any lingering parent backgrounds
                    ViewParent p = realBar.getParent();
                    while (p instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) p;
                        if (vg.getBackground() != null) vg.setBackground(null);
                        vg.setClipChildren(false);
                        p = vg.getParent();
                    }
                    return true;
                }
            });
            XposedBridge.log("[HyperBounce] [STEP 7] Continuous Background Stripper enabled.");

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] FATAL ERROR during execution:");
            XposedBridge.log(Log.getStackTraceString(e));
        }
    }
}
