package dev.joseph.hyperbounce;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;

public class Config {
    public int animMode = 1; 
    public int gridColumns = 4;
    public int gridRows = 7;
    
    // Dynamic Storage for Unlock Cascade
    public HashMap<String, Float> dyn = new HashMap<>();
    
    // Mode 0: Classic Radial Dispersion
    public float dockDamping = 0.70f;
    public float dockResponse = 0.40f;
    public float centerResponse = 0.48f;
    public float centerDamping = 0.58f;
    public float edgeResponseDelay = 0.22f;
    public float edgeDampingDrop = 0.10f;
    public float minResponse = 0.45f;
    public float maxResponse = 0.75f;
    public float minDamping = 0.42f;
    public float maxDamping = 0.65f;
    public float zDepthMultiplier = 1.6f;

    // Mode 1: Kinetic Y-Axis Cascade Globals
    public float durationScale = 1.0f;
    public float velocityInfluence = 1.0f;
    public float cascadeDamping = 0.95f;
    public float kineticDockDamping = 0.85f;
    public float kineticDockResponse = 0.38f;
    public float kineticDockZDepth = 800.0f;
    public float maxVelocity = 16000.0f;

    // ==========================================
    // APP OPEN / CLOSE & RECENTS ENGINE CONFIG
    // ==========================================
    public boolean appAnimEnabled = true;
    public float appYArc = 120.0f;              // Vertical parabolic lift (px)
    public float appBottomZWarp = 0.28f;        // 3D bottom card compression (0.0 to 0.8)
    public float appXSway = 0.0f;               // Lateral curve swing (px)
    public float appInterruptSmooth = 0.85f;    // Gesture reversal & fast-flick stabilizer

    public float appTravelDamping = 0.60f;      // Spatial trajectory damping
    public float appTravelStiffness = 140.0f;   // Spatial trajectory velocity/stiffness
    public float appSizeDamping = 0.58f;        // Window width/height rubber-band bounce
    public float appSizeStiffness = 150.0f;     // Window expansion rate
    public float appCornerDamping = 0.75f;      // Squircle to screen bezel morph damping
    public float appCornerStiffness = 200.0f;   // Corner morph response
    public float appAlphaDamping = 0.92f;       // Surface crossfade damping
    public float appAlphaStiffness = 300.0f;    // Surface crossfade velocity
    public float appIconPitchDeg = 18.0f;       // 3D X-axis tilt angle (degrees)
    public float appIconCameraDist = 8000.0f;   // 3D perspective field depth

    public float getDyn(String key, float defVal) {
        return dyn.containsKey(key) ? dyn.get(key) : defVal;
    }

    public void loadFromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            animMode = json.optInt("animMode", animMode);
            gridColumns = json.optInt("gridColumns", gridColumns);
            gridRows = json.optInt("gridRows", gridRows);
            
            dockDamping = (float) json.optDouble("dockDamping", dockDamping);
            dockResponse = (float) json.optDouble("dockResponse", dockResponse);
            centerResponse = (float) json.optDouble("centerResponse", centerResponse);
            centerDamping = (float) json.optDouble("centerDamping", centerDamping);
            edgeResponseDelay = (float) json.optDouble("edgeResponseDelay", edgeResponseDelay);
            edgeDampingDrop = (float) json.optDouble("edgeDampingDrop", edgeDampingDrop);
            minResponse = (float) json.optDouble("minResponse", minResponse);
            maxResponse = (float) json.optDouble("maxResponse", maxResponse);
            minDamping = (float) json.optDouble("minDamping", minDamping);
            maxDamping = (float) json.optDouble("maxDamping", maxDamping);
            zDepthMultiplier = (float) json.optDouble("zDepthMultiplier", zDepthMultiplier);

            durationScale = (float) json.optDouble("durationScale", durationScale);
            velocityInfluence = (float) json.optDouble("velocityInfluence", velocityInfluence);
            cascadeDamping = (float) json.optDouble("cascadeDamping", cascadeDamping);
            kineticDockDamping = (float) json.optDouble("kineticDockDamping", kineticDockDamping);
            kineticDockResponse = (float) json.optDouble("kineticDockResponse", kineticDockResponse);
            kineticDockZDepth = (float) json.optDouble("kineticDockZDepth", kineticDockZDepth);
            maxVelocity = (float) json.optDouble("maxVelocity", maxVelocity);

            // App Open/Close & Recents
            appAnimEnabled = json.optBoolean("appAnimEnabled", appAnimEnabled);
            appYArc = (float) json.optDouble("appYArc", appYArc);
            appBottomZWarp = (float) json.optDouble("appBottomZWarp", appBottomZWarp);
            appXSway = (float) json.optDouble("appXSway", appXSway);
            appInterruptSmooth = (float) json.optDouble("appInterruptSmooth", appInterruptSmooth);

            appTravelDamping = (float) json.optDouble("appTravelDamping", appTravelDamping);
            appTravelStiffness = (float) json.optDouble("appTravelStiffness", appTravelStiffness);
            appSizeDamping = (float) json.optDouble("appSizeDamping", appSizeDamping);
            appSizeStiffness = (float) json.optDouble("appSizeStiffness", appSizeStiffness);
            appCornerDamping = (float) json.optDouble("appCornerDamping", appCornerDamping);
            appCornerStiffness = (float) json.optDouble("appCornerStiffness", appCornerStiffness);
            appAlphaDamping = (float) json.optDouble("appAlphaDamping", appAlphaDamping);
            appAlphaStiffness = (float) json.optDouble("appAlphaStiffness", appAlphaStiffness);
            appIconPitchDeg = (float) json.optDouble("appIconPitchDeg", appIconPitchDeg);
            appIconCameraDist = (float) json.optDouble("appIconCameraDist", appIconCameraDist);

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try {
                    dyn.put(k, (float) json.getDouble(k));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
