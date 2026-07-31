package dev.joseph.hyperbounce;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
            XposedBridge.log("[HyperBounce] Executing RAM Patch Glass Engine...");
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

        // 1. Inject Glass Overlay once when the view attaches
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

        // 2. RAM Patching: Silently execute background drawables during draw phase (Crash-Free)
        XposedBridge.hookAllMethods(wdsBottomBarClass, "dispatchDraw", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ViewGroup realBar = (ViewGroup) param.thisObject;
                
                // --- TARGET 1: Kill the WDSBottomBar Background ---
                realBar.setBackgroundColor(Color.TRANSPARENT);

                // --- TARGET 2: Kill the Parent Black Containers (Based on Recon Log) ---
                ViewParent parent = realBar.getParent();
                int depth = 0;
                while (parent instanceof View && depth < 7) {
                    View pView = (View) parent;
                    Drawable bg = pView.getBackground();
                    if (bg instanceof ColorDrawable) {
                        int color = ((ColorDrawable) bg).getColor();
                        // If it's WhatsApp's #FF0A1014 dark theme color, nuke it
                        if (color == Color.parseColor("#FF0A1014") || color == Color.parseColor("#0A1014")) {
                            pView.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                    parent = parent.getParent();
                    depth++;
                }

                // --- TARGET 3: Kill the Green Material Blob ---
                if (realBar.getChildCount() > 1) {
                    View menuView = realBar.getChildAt(1); // Index 1 is the tab menu
                    if (menuView instanceof ViewGroup && !"HyperGlassOverlay".equals(menuView.getTag())) {
                        ViewGroup menu = (ViewGroup) menuView;
                        for (int i = 0; i < menu.getChildCount(); i++) {
                            View tabView = menu.getChildAt(i);
                            if (tabView instanceof ViewGroup) {
                                ViewGroup tab = (ViewGroup) tabView;
                                for (int j = 0; j < tab.getChildCount(); j++) {
                                    View tabChild = tab.getChildAt(j);
                                    if (tabChild instanceof FrameLayout) {
                                        FrameLayout fl = (FrameLayout) tabChild;
                                        for (int k = 0; k < fl.getChildCount(); k++) {
                                            View flChild = fl.getChildAt(k);
                                            // The blob is the only raw android.view.View in this layout
                                            if (flChild.getClass() == View.class) {
                                                flChild.setAlpha(0.0f); // GPU-level transparent (NO LAYOUT PASS TRIGGERED)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Update the iOS pill position
                View overlay = realBar.findViewWithTag("HyperGlassOverlay");
                if (overlay != null) overlay.postInvalidate();
            }
        });
    }

    private void injectGlassOverlay(ViewGroup realBar) {
        try {
            if (realBar.findViewWithTag("HyperGlassOverlay") != null) return;

            View glassOverlay = new View(realBar.getContext()) {
                private final Paint glassBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RuntimeShader glassShader = new RuntimeShader(AGSL_WHATSAPP_GLASS);
                private final RenderEffect blurFx = RenderEffect.createBlurEffect(45.0f, 45.0f, Shader.TileMode.CLAMP);

                {
                    glassBgPaint.setColor(Color.parseColor("#30000000")); // Deep glass tint
                    indicatorPaint.setColor(Color.parseColor("#35FFFFFF")); // iOS White Pill
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
                    
                    // Draw Floating Glass Pill
                    RectF rect = new RectF(0, 0, getWidth(), getHeight());
                    float radius = rect.height() / 2.0f;
                    canvas.drawRoundRect(rect, radius, radius, glassBgPaint);

                    // Scan and Draw iOS Pill
                    try {
                        for (int i = 0; i < realBar.getChildCount(); i++) {
                            View child = realBar.getChildAt(i);
                            if (child instanceof ViewGroup && !"HyperGlassOverlay".equals(child.getTag())) {
                                ViewGroup menuContainer = (ViewGroup) child;
                                for (int j = 0; j < menuContainer.getChildCount(); j++) {
                                    View tab = menuContainer.getChildAt(j);
                                    
                                    boolean isActive = tab.isSelected() || tab.isActivated();
                                    try {
                                        if (!isActive) isActive = (boolean) XposedHelpers.callMethod(tab, "isChecked");
                                    } catch (Throwable ignored) {}

                                    if (isActive) {
                                        float myLeft = this.getLeft();
                                        float myTop = this.getTop();
                                        float tabCx = menuContainer.getLeft() + tab.getLeft() + (tab.getWidth() / 2.0f);
                                        float tabCy = menuContainer.getTop() + tab.getTop() + (tab.getHeight() / 2.0f);
                                        
                                        float localCx = tabCx - myLeft;
                                        float localCy = tabCy - myTop;

                                        // Size of the iOS indicator
                                        float pillW = tab.getWidth() * 0.40f;
                                        float pillH = tab.getHeight() * 0.35f;
                                        
                                        RectF pillRect = new RectF(localCx - pillW, localCy - pillH, localCx + pillW, localCy + pillH);
                                        canvas.drawRoundRect(pillRect, pillH, pillH, indicatorPaint);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("HyperBounce", "Indicator logic failed: ", e);
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
            flp.setMargins(48, 12, 48, 36);

            realBar.addView(glassOverlay, 0, flp);

        } catch (Exception e) {
            XposedBridge.log("[HyperBounce] FATAL ERROR during execution: " + Log.getStackTraceString(e));
        }
    }
}
