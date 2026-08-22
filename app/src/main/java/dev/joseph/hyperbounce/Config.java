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
    
    public float cascadeDamping = 0.95f;
    public float cascadeBaseResponse = 0.55f; 
    public float cascadeRowStagger = 0.040f;
    
    public float kineticDockDamping = 0.85f;
    public float kineticDockResponse = 0.38f;
    public float kineticDockZDepth = 800.0f;
    
    public float maxVelocity = 16000.0f;

    // Row-by-Row Velocity Bounce Intensity
    public float r0Bounce = 0.45f;
    public float r1Bounce = 0.35f;
    public float r2Bounce = 0.25f;
    public float r3Bounce = 0.15f;
    public float r4Bounce = 0.10f;
    public float r5Bounce = 0.05f;
    public float r6Bounce = 0.05f;

    // Row-by-Row Z-Depth Spawn Distance
    public float r0Z = 2000.0f;
    public float r1Z = 1600.0f;
    public float r2Z = 1200.0f;
    public float r3Z = 900.0f;
    public float r4Z = 600.0f;
    public float r5Z = 450.0f;
    public float r6Z = 450.0f;

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
            
            cascadeDamping = (float) json.optDouble("cascadeDamping", cascadeDamping);
            cascadeBaseResponse = (float) json.optDouble("cascadeBaseResponse", cascadeBaseResponse);
            cascadeRowStagger = (float) json.optDouble("cascadeRowStagger", cascadeRowStagger);
            
            kineticDockDamping = (float) json.optDouble("kineticDockDamping", kineticDockDamping);
            kineticDockResponse = (float) json.optDouble("kineticDockResponse", kineticDockResponse);
            kineticDockZDepth = (float) json.optDouble("kineticDockZDepth", kineticDockZDepth);
            
            maxVelocity = (float) json.optDouble("maxVelocity", maxVelocity);

            r0Bounce = (float) json.optDouble("r0Bounce", r0Bounce);
            r1Bounce = (float) json.optDouble("r1Bounce", r1Bounce);
            r2Bounce = (float) json.optDouble("r2Bounce", r2Bounce);
            r3Bounce = (float) json.optDouble("r3Bounce", r3Bounce);
            r4Bounce = (float) json.optDouble("r4Bounce", r4Bounce);
            r5Bounce = (float) json.optDouble("r5Bounce", r5Bounce);
            r6Bounce = (float) json.optDouble("r6Bounce", r6Bounce);

            r0Z = (float) json.optDouble("r0Z", r0Z);
            r1Z = (float) json.optDouble("r1Z", r1Z);
            r2Z = (float) json.optDouble("r2Z", r2Z);
            r3Z = (float) json.optDouble("r3Z", r3Z);
            r4Z = (float) json.optDouble("r4Z", r4Z);
            r5Z = (float) json.optDouble("r5Z", r5Z);
            r6Z = (float) json.optDouble("r6Z", r6Z);
        } catch (Exception e) {}
    }
}
