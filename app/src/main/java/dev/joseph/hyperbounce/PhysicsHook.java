package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PhysicsHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.whatsapp")) {
            XposedBridge.log("[HyperBounce] Executing Crash-Free WhatsApp Liquid Glass Engine...");
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
        Class<?> wdsBottomBarClass = XposedHelpers.findClassIfExists("com.whatsapp.ui.wds.components.bottombar.WDSBottomBar", classLoader);
        if (wdsBottomBarClass == null) return;

        // 1. Initial Injection via Constructor listener (Guaranteed no crashes)
        XposedBridge.hookAllConstructors(wdsBottomBarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final ViewGroup realBar = (ViewGroup) param.thisObject;

                realBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        realBar.post(() -> injectGlassOverlay(realBar));
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {}
                });
            }
        });

        // 2. Safe Continuous Stripping on every UI frame draw
        XposedBridge.hookAllMethods(wdsBottomBarClass, "dispatchDraw", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ViewGroup realBar = (ViewGroup) param.thisObject;
                
                // Aggressively kill the Material 3 Green Blob
                try {
                    XposedHelpers.callMethod(realBar, "setItemActiveIndicatorEnabled", false);
                } catch (Throwable ignored) {}

                // Strip the black rectangular background from the bar and its children
                realBar.setBackground(null);
                realBar.setClipChildren(false);

                if (realBar.getChildCount() > 1) {
                    View menu = realBar.getChildAt(1); // Standard index for the tab container
                    if (menu != null) {
                        menu.setBackground(null);
                        if (menu instanceof ViewGroup) {
                            ViewGroup vgMenu = (ViewGroup) menu;
                            vgMenu.setClipChildren(false);
                            for (int i = 0; i < vgMenu.getChildCount(); i++) {
                                View tab = vgMenu.getChildAt(i);
                                tab.setBackground(null);
                            }
                        }
                    }
                }
            }
        });

        // 3. Force custom pill indicator to redraw when screen is touched
        XposedBridge.hookAllMethods(wdsBottomBarClass, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ViewGroup realBar = (ViewGroup) param.thisObject;
                View overlay = realBar.findViewWithTag("HyperGlassOverlay");
                if (overlay != null) {
                    overlay.postInvalidate(); 
                }
            }
        });
    }

    private void injectGlassOverlay(ViewGroup realBar) {
        try {
            if (realBar.findViewWithTag("HyperGlassOverlay") != null) return;

            // Build Glass Overlay
            View glassOverlay = new View(realBar.getContext()) {
                private final Paint glassBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RuntimeShader glassShader = new RuntimeShader(AGSL_WHATSAPP_GLASS);
                private final RenderEffect blurFx = RenderEffect.createBlurEffect(45.0f, 45.0f, Shader.TileMode.CLAMP);

                {
                    glassBgPaint.setColor(Color.parseColor("#30000000")); // Deep glass tint
                    indicatorPaint.setColor(Color.parseColor("#35FFFFFF")); // Custom iOS White Pill
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
                    
                    // 1. Draw Glass Base Pill
                    RectF rect = new RectF(0, 0, getWidth(), getHeight());
                    float radius = rect.height() / 2.0f;
                    canvas.drawRoundRect(rect, radius, radius, glassBgPaint);

                    // 2. Scan and Draw iOS Pill
                    try {
                        if (realBar.getChildCount() > 1) {
                            ViewGroup menuContainer = (ViewGroup) realBar.getChildAt(1); 
                            for (int i = 0; i < menuContainer.getChildCount(); i++) {
                                View tab = menuContainer.getChildAt(i);
                                
                                // Check if this tab is the active one
                                boolean isActive = tab.isSelected() || tab.isActivated();
                                try {
                                    if (!isActive) isActive = (boolean) XposedHelpers.callMethod(tab, "isChecked");
                                } catch (Throwable ignored) {}

                                if (isActive) {
                                    // Math to map the tab's coordinate inside the padded glass overlay
                                    float myLeft = this.getLeft();
                                    float myTop = this.getTop();
                                    
                                    float tabCx = menuContainer.getLeft() + tab.getLeft() + (tab.getWidth() / 2.0f);
                                    float tabCy = menuContainer.getTop() + tab.getTop() + (tab.getHeight() / 2.0f);
                                    
                                    float localCx = tabCx - myLeft;
                                    float localCy = tabCy - myTop;

                                    // Size of the iOS indicator pill
                                    float pillW = tab.getWidth() * 0.40f;
                                    float pillH = tab.getHeight() * 0.35f;
                                    
                                    RectF pillRect = new RectF(localCx - pillW, localCy - pillH, localCx + pillW, localCy + pillH);
                                    canvas.drawRoundRect(pillRect, pillH, pillH, indicatorPaint);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("HyperBounce", "Error drawing pill: ", e);
                    }
                }
            };

            glassOverlay.setTag("HyperGlassOverlay");
            glassOverlay.setClickable(false);
            glassOverlay.setFocusable(false);

            // Apply margins to the OVERLAY, not the real bar. This guarantees the floating visual without breaking WhatsApp layouts.
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            flp.setMargins(48, 12, 48, 36); // Left, Top, Right, Bottom

            // Inject at Index 0 (Rendered BEHIND the tab icons!)
            realBar.addView(glassOverlay, 0, flp);

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] FATAL ERROR during execution: " + Log.getStackTraceString(e));
        }
    }
}
