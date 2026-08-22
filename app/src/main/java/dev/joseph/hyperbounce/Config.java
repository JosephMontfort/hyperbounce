package dev.joseph.hyperbounce;

import org.json.JSONObject;

public class Config {
    public int animMode = 1; 
    
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

    // Mode 1: Kinetic Y-Axis Cascade
    public float durationScale = 1.0f;
    public float velocityInfluence = 1.0f;
    public float velocityBounceIntensity = 0.40f;
    public float velocityDepthRows = 4.0f; // NEW: Controls how many rows get the bounce
    
    public float cascadeDamping = 0.95f;
    public float cascadeBaseResponse = 0.55f; 
    public float cascadeRowStagger = 0.040f;
    
    public float zDepthPunch = 1.5f;
    public float zDepthGradient = 1.2f;
    
    public float kineticDockDamping = 0.85f;
    public float kineticDockResponse = 0.38f;
    public float kineticDockZDepth = 0.8f;
    
    public float maxVelocity = 16000.0f;

    public void loadFromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            animMode = json.optInt("animMode", animMode);
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
            velocityBounceIntensity = (float) json.optDouble("velocityBounceIntensity", velocityBounceIntensity);
            velocityDepthRows = (float) json.optDouble("velocityDepthRows", velocityDepthRows);
            
            cascadeDamping = (float) json.optDouble("cascadeDamping", cascadeDamping);
            cascadeBaseResponse = (float) json.optDouble("cascadeBaseResponse", cascadeBaseResponse);
            cascadeRowStagger = (float) json.optDouble("cascadeRowStagger", cascadeRowStagger);
            
            zDepthPunch = (float) json.optDouble("zDepthPunch", zDepthPunch);
            zDepthGradient = (float) json.optDouble("zDepthGradient", zDepthGradient);
            
            kineticDockDamping = (float) json.optDouble("kineticDockDamping", kineticDockDamping);
            kineticDockResponse = (float) json.optDouble("kineticDockResponse", kineticDockResponse);
            kineticDockZDepth = (float) json.optDouble("kineticDockZDepth", kineticDockZDepth);
            
            maxVelocity = (float) json.optDouble("maxVelocity", maxVelocity);
        } catch (Exception e) {}
    }
}
