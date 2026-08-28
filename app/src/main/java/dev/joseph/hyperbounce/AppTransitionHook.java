package dev.joseph.hyperbounce;

import android.graphics.RectF;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import org.json.JSONObject;

public class AppTransitionHook {
    private static java.lang.reflect.Field sKeyField = null;
    private static java.lang.reflect.Field sImpField = null;
    private static java.lang.reflect.Field sDField = null;
    private static java.lang.reflect.Field sRField = null;

    public static void init(PhysicsHook module, ClassLoader classLoader, Config config) {
        if (classLoader == null) return;
        
        try {
            Class<?> companionClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider$Companion", false, classLoader);
            Class<?> animTypeEnum = Class.forName("com.miui.home.recents.util.RectFSpringAnim$AnimType", false, classLoader);
            Class<?> springParamClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider$SpringParam", false, classLoader);
            Constructor<?> springParamConst = springParamClass.getDeclaredConstructor(String.class, float.class, float.class, float.class);
            springParamConst.setAccessible(true);

            try {
                sKeyField = springParamClass.getDeclaredField("key");
                sKeyField.setAccessible(true);
                sImpField = springParamClass.getDeclaredField("impulse");
                sImpField.setAccessible(true);
                sDField = springParamClass.getDeclaredField("damping");
                sDField.setAccessible(true);
                sRField = springParamClass.getDeclaredField("response");
                sRField.setAccessible(true);
            } catch (Throwable ignore) {}
            
            Method generateMethod = companionClass.getDeclaredMethod("generateSpringBundlesByAnimType", animTypeEnum, RectF.class, RectF.class);
            
            module.hook(generateMethod).intercept(chain -> {
                PhysicsHook.syncMemory();
                Object[] result = (Object[]) chain.proceed();
                if (result == null || !config.appAnimEnabled) {
                    return result; // Stock behaviour by default
                }
                
                try {
                    float posDamping = (float) config.getDyn("appPosDamping", 0.80f);
                    float posResponse = (float) config.getDyn("appPosResponse", 0.40f);
                    
                    float scaleDamping = (float) config.getDyn("appScaleDamping", 0.80f);
                    float scaleResponse = (float) config.getDyn("appScaleResponse", 0.40f);
                    
                    float cornerDamping = (float) config.getDyn("appCornerDamping", 1.00f);
                    float cornerResponse = (float) config.getDyn("appCornerResponse", 0.35f);
                    
                    float alphaDamping = (float) config.getDyn("appAlphaDamping", 1.00f);
                    float alphaResponse = (float) config.getDyn("appAlphaResponse", 0.30f);
                    
                    for (int i = 0; i < result.length; i++) {
                        Object param = result[i];
                        if (param == null) continue;
                        
                        String key = (sKeyField != null) ? (String) sKeyField.get(param) : null;
                        float impulse = (sImpField != null) ? (float) sImpField.get(param) : 0.0f;
                        
                        float d = 0.9f;
                        float r = 0.3f;
                        
                        if ("centerX".equals(key) || "centerY".equals(key)) {
                            d = posDamping; r = posResponse;
                        } else if ("Width".equals(key) || "Height".equals(key) || "Ratio".equals(key)) {
                            d = scaleDamping; r = scaleResponse;
                        } else if ("RadiusRatio".equals(key) || "Radius".equals(key)) {
                            d = cornerDamping; r = cornerResponse;
                        } else if ("alpha".equals(key) || "SurfaceExtAlpha".equals(key) || "appLeashBlurRadius".equals(key)) {
                            d = alphaDamping; r = alphaResponse;
                        } else {
                            d = (sDField != null) ? (float) sDField.get(param) : 0.9f;
                            r = (sRField != null) ? (float) sRField.get(param) : 0.3f;
                        }
                        
                        result[i] = springParamConst.newInstance(key, d, r, impulse);
                    }
                } catch (Throwable t) {
                    // Ignore and fallback to stock
                }
                
                return result;
            });
            
        } catch (Throwable t) {
            // Ignore
        }
    }
}
