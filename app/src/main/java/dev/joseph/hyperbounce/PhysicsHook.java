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

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.whatsapp")) {
            XposedBridge.log("[HyperBounce] Executing isolated WhatsApp hook initialization...");
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
        XposedBridge.log("[HyperBounce] Starting WDSBottomBar lookup...");
        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", classLoader);
        if (wdsBottomBarClass == null) {
            XposedBridge.log("[HyperBounce] ERROR: WDSBottomBar class not found!");
            return;
        }
        XposedBridge.log("[HyperBounce] WDSBottomBar class found successfully!");

        try {
            XposedHelpers.findAndHookMethod(wdsBottomBarClass, "setBackgroundDrawable", android.graphics.drawable.Drawable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null); 
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[HyperBounce] Error hooking setBackgroundDrawable: " + Log.getStackTraceString(t));
        }

        try {
            XposedHelpers.findAndHookMethod(wdsBottomBarClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[HyperBounce] onAttachedToWindow triggered.");
                    try {
                        ViewGroup realBar = (ViewGroup) param.thisObject;

                        realBar.post(() -> {
                            try {
                                if (realBar.findViewWithTag("HyperGlassOverlay") != null) {
                                    XposedBridge.log("[HyperBounce] Overlay already exists. Skipping.");
                                    return;
                                }

                                XposedBridge.log("[HyperBounce] Applying margins...");
                                ViewGroup.LayoutParams barLp = realBar.getLayoutParams();
                                if (barLp instanceof ViewGroup.MarginLayoutParams) {
                                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) barLp;
                                    mlp.setMargins(48, 0, 48, 40); 
                                    realBar.setLayoutParams(mlp);
                                }

                                XposedBridge.log("[HyperBounce] Nuking backgrounds...");
                                realBar.setBackground(null);
                                for (int i = 0; i < realBar.getChildCount(); i++) {
                                    View child = realBar.getChildAt(i);
                                    child.setBackground(null);
                                    if (child instanceof ViewGroup) {
                                        ViewGroup childVg = (ViewGroup) child;
                                        childVg.setClipChildren(false);
                                        for (int j = 0; j < childVg.getChildCount(); j++) {
                                            childVg.getChildAt(j).setBackground(null);
                                        }
                                    }
                                }

                                ViewParent parent = realBar.getParent();
                                while (parent instanceof ViewGroup) {
                                    ViewGroup vg = (ViewGroup) parent;
                                    vg.setClipChildren(false);
                                    vg.setClipToPadding(false);
                                    vg.setBackground(null);
                                    parent = vg.getParent();
                                }

                                XposedBridge.log("[HyperBounce] Creating Glass Overlay...");
                                View glassOverlay = new View(realBar.getContext()) {
                                    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    private final RuntimeShader glassShader = new RuntimeShader(AGSL_WHATSAPP_GLASS);
                                    private final RenderEffect blurFx = RenderEffect.createBlurEffect(45.0f, 45.0f, Shader.TileMode.CLAMP);

                                    {
                                        paint.setColor(Color.parseColor("#30000000"));
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
                                        RectF rect = new RectF(0, 0, getWidth(), getHeight());
                                        float radius = rect.height() / 2.0f;
                                        canvas.drawRoundRect(rect, radius, radius, paint);
                                    }
                                };
                                glassOverlay.setTag("HyperGlassOverlay");
                                glassOverlay.setClickable(false);
                                glassOverlay.setFocusable(false);
                                glassOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

                                android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, 
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                );

                                XposedBridge.log("[HyperBounce] Injecting overlay...");
                                realBar.addView(glassOverlay, 0, flp);
                                XposedBridge.log("[HyperBounce] Injection complete!");

                            } catch (Exception e) {
                                XposedBridge.log("[HyperBounce] Runnable Error: " + Log.getStackTraceString(e));
                            }
                        });
                    } catch (Exception e) {
                        XposedBridge.log("[HyperBounce] onAttachedToWindow Error: " + Log.getStackTraceString(e));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[HyperBounce] Hook Error: " + Log.getStackTraceString(t));
        }
    }
}
