package dev.joseph.hyperbounce;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;

public class Config {
    public int animMode = 1; 
    public int gridColumns = 4;
    public int gridRows = 7;
    
    // Dynamic Storage for Row-by-Row / Center-vs-Outer parameters
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

            // Dynamically load all other keys (Z-Depths, Bounces, Settling times)
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try {
                    dyn.put(k, (float) json.getDouble(k));
                } catch (Exception e) {}
            }
        } catch (Exception e) {}
    }
}
