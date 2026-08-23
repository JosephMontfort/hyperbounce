package dev.joseph.hyperbounce;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;

public class Config {
    // Master Switches
    public boolean unlockEnabled = true;
    public boolean appAnimEnabled = true;

    // Unlock Configurations
    public int animMode = 1; 
    public int gridColumns = 4;
    public int gridRows = 7;
    public HashMap<String, Float> dyn = new HashMap<>();
    
    public float dockDamping = 0.70f;
    public float dockResponse = 0.40f;
    public float centerResponse = 0.48f;
    public float centerDamping = 0.58f;
    public float edgeResponseDelay = 0.22f;
    public float edgeDampingDrop = 0.10f;
    public float zDepthMultiplier = 1.6f;

    public float durationScale = 1.0f;
    public float velocityInfluence = 1.0f;
    public float cascadeDamping = 0.95f;
    public float kineticDockDamping = 0.85f;
    public float kineticDockResponse = 0.38f;
    public float kineticDockZDepth = 800.0f;
    public float maxVelocity = 16000.0f;

    // ==========================================
    // APP OPEN / CLOSE & RECENTS ENGINE CONFIG
    // All defaults below match HyperOS pure stock.
    // ==========================================
    public float appYArc = 0.0f;              
    public float appBottomZWarp = 0.0f;        
    public float appXSway = 0.0f;               
    public float appInterruptSmooth = 1.0f;    

    public float appTravelDamping = 0.80f;      
    public float appTravelStiffness = 200.0f;   
    
    public float appSizeDamping = 0.80f;        
    public float appSizeStiffness = 200.0f;     
    
    public float appRatioDamping = 0.80f;        
    public float appRatioStiffness = 400.0f;     

    public float appCornerDamping = 0.80f;      
    public float appCornerStiffness = 200.0f;   
    
    public float appAlphaDamping = 0.99f;       
    public float appAlphaStiffness = 400.0f;    
    
    public float appIconPitchDeg = 0.0f;       
    public float appIconCameraDist = 8000.0f;   

    public float getDyn(String key, float defVal) {
        return dyn.containsKey(key) ? dyn.get(key) : defVal;
    }

    public void loadFromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            unlockEnabled = json.optBoolean("unlockEnabled", unlockEnabled);
            appAnimEnabled = json.optBoolean("appAnimEnabled", appAnimEnabled);
            animMode = json.optInt("animMode", animMode);
            gridColumns = json.optInt("gridColumns", gridColumns);
            gridRows = json.optInt("gridRows", gridRows);
            
            dockDamping = (float) json.optDouble("dockDamping", dockDamping);
            dockResponse = (float) json.optDouble("dockResponse", dockResponse);
            centerResponse = (float) json.optDouble("centerResponse", centerResponse);
            centerDamping = (float) json.optDouble("centerDamping", centerDamping);
            edgeResponseDelay = (float) json.optDouble("edgeResponseDelay", edgeResponseDelay);
            edgeDampingDrop = (float) json.optDouble("edgeDampingDrop", edgeDampingDrop);
            zDepthMultiplier = (float) json.optDouble("zDepthMultiplier", zDepthMultiplier);

            durationScale = (float) json.optDouble("durationScale", durationScale);
            velocityInfluence = (float) json.optDouble("velocityInfluence", velocityInfluence);
            cascadeDamping = (float) json.optDouble("cascadeDamping", cascadeDamping);
            kineticDockDamping = (float) json.optDouble("kineticDockDamping", kineticDockDamping);
            kineticDockResponse = (float) json.optDouble("kineticDockResponse", kineticDockResponse);
            kineticDockZDepth = (float) json.optDouble("kineticDockZDepth", kineticDockZDepth);
            maxVelocity = (float) json.optDouble("maxVelocity", maxVelocity);

            appYArc = (float) json.optDouble("appYArc", appYArc);
            appBottomZWarp = (float) json.optDouble("appBottomZWarp", appBottomZWarp);
            appXSway = (float) json.optDouble("appXSway", appXSway);
            appInterruptSmooth = (float) json.optDouble("appInterruptSmooth", appInterruptSmooth);
            appTravelDamping = (float) json.optDouble("appTravelDamping", appTravelDamping);
            appTravelStiffness = (float) json.optDouble("appTravelStiffness", appTravelStiffness);
            appSizeDamping = (float) json.optDouble("appSizeDamping", appSizeDamping);
            appSizeStiffness = (float) json.optDouble("appSizeStiffness", appSizeStiffness);
            appRatioDamping = (float) json.optDouble("appRatioDamping", appRatioDamping);
            appRatioStiffness = (float) json.optDouble("appRatioStiffness", appRatioStiffness);
            appCornerDamping = (float) json.optDouble("appCornerDamping", appCornerDamping);
            appCornerStiffness = (float) json.optDouble("appCornerStiffness", appCornerStiffness);
            appAlphaDamping = (float) json.optDouble("appAlphaDamping", appAlphaDamping);
            appAlphaStiffness = (float) json.optDouble("appAlphaStiffness", appAlphaStiffness);
            appIconPitchDeg = (float) json.optDouble("appIconPitchDeg", appIconPitchDeg);
            appIconCameraDist = (float) json.optDouble("appIconCameraDist", appIconCameraDist);

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try { dyn.put(k, (float) json.getDouble(k)); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
