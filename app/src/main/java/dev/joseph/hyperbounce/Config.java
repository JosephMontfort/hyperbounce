package dev.joseph.hyperbounce;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;

public class Config {
    // Master Switches
    public boolean unlockEnabled = true;
    public boolean appAnimEnabled = true;
    public boolean ccAnimEnabled = true;
    public boolean ccLowEndAnim = false;
    public boolean ccJellyEnabled = true;
    public boolean ccModularEditEnabled = true;
    public boolean ccRestartOnEditExit = true;
    public boolean notifAnimEnabled = false;

    // Modular Component Customization Priorities
    public int ccPriorityCards = 20;
    public int ccPriorityMedia = 30;
    public int ccPriorityBrightness = 31;
    public int ccPriorityVolume = 32;
    public int ccPrioritySmartHub = 50;
    public int ccPriorityDeviceControls = 51;
    public int ccPriorityTiles = 100;

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
    public float minResponse = 0.45f;
    public float maxResponse = 0.75f;
    public float minDamping = 0.42f;
    public float maxDamping = 0.65f;

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

    // ==========================================
    // LOCKSCREEN CARD & PILL TUNING
    // ==========================================
    public float lockCardBorderWidth = 2.5f;
    public float lockCardBorderAlpha = 0.47f;
    public float lockCardRefractionHeight = 24.0f;
    public float lockCardRefractionAmount = 32.0f;
    public float lockCardDepthEffect = 0.12f;
    public float lockPillWidth = 156.0f;
    public float lockPillHeight = 6.0f;
    public float lockPillBottomMargin = 9.0f;
    public float lockLipHeight = 28.0f;

    // ==========================================
    // PIN PAD GEOMETRY & TYPOGRAPHY
    // ==========================================
    public float pinCircleDiameter = 82.0f;
    public float pinCircleFillAlpha = 0.20f;
    public float pinCircleStrokeWidth = 1.5f;
    public float pinCircleStrokeAlpha = 0.47f;
    public boolean pinShowKlondike = false;
    public float pinDigitTextSize = 36.0f;
    public float pinRow1Offset = -90.0f;
    public float pinRow2Offset = -60.0f;
    public float pinRow3Offset = -30.0f;
    public float pinRow0Offset = -115.0f;

    // ==========================================
    // PIN PAD INTERACTIVE SPRING SQUISH & GLOW PHYSICS
    // ==========================================
    public float pinTapSquishScale = 0.88f;
    public float pinReleasePlunge = 0.10f;
    public float pinJellyMaxStretch = 0.16f;
    public float pinJellyElasticLimit = 0.40f;
    public float pinSpringStiffness = 50.0f;
    public float pinSpringDamping = 5.5f;
    public float pinPressGlowAlpha = 0.35f;
    public float pinGlowRadius = 1.4f;

    // ==========================================
    // PIN PAD SEQUENTIAL ENTRY ANIMATION
    // ==========================================
    public float pinEntryTranslationY = 90.0f;
    public float pinEntryInitialScale = 0.50f;
    public float pinEntryDuration = 420.0f;
    public float pinRowDelayStep = 55.0f;
    public float pinColDelayStep = 25.0f;
    public float pinEntryOvershoot = 1.8f;

    // ==========================================
    // CONTROL CENTER PHYSICS & ENTRY ENGINE
    // ==========================================
    public boolean ccDisableAnim = false;
    public boolean ccCenterOrigin = true;
    public boolean ccIosVerticalDrop = false;
    public float ccEntryInitialScale = 0.50f;
    public float ccEntryTranslationY = 120.0f;
    public boolean ccWaterfallEnabled = true;
    public float ccWaterfallDelayStep = 0.04f;
    public float ccSpreadRatio = 0.02f;
    public float ccSpreadResponse = 0.85f;
    public float ccSpreadDamping = 0.85f;
    public float ccSpreadReleaseResponse = 0.70f;
    public float ccSpreadReleaseDamping = 0.90f;
    public float ccTileShowResponse = 0.85f;
    public float ccTileShowDamping = 0.85f;
    public float ccTileHideResponse = 0.80f;
    public float ccTileHideDamping = 0.90f;
    public float ccTileShrinkResponse = 0.85f;
    public float ccTileShrinkDamping = 0.85f;
    public float ccTileDelayScale = 60.0f;
    public float ccBlurResponse = 0.90f;
    public float ccBlurDamping = 0.88f;
    public float ccFrictionMultiplier = 0.40f;

    // ==========================================
    // NOTIFICATION CENTER SHADE PHYSICS
    // ==========================================
    public boolean notifDisableAnim = false;
    public boolean notifLinearDrop = true;
    public float notifInitialScale = 1.0f;
    public float notifStaggerStep = 0.06f;
    public float notifSpringResponse = 0.85f;
    public float notifSpringDamping = 0.85f;
    public float notifExpandResponse = 0.88f;
    public float notifExpandDamping = 0.88f;
    public float notifReleaseResponse = 0.80f;
    public float notifReleaseDamping = 0.90f;
    public float notifBlurResponse = 0.90f;
    public float notifBlurDamping = 0.88f;

    public float getDyn(String key, float defVal) {
        return dyn.containsKey(key) ? dyn.get(key) : defVal;
    }

    private void resetDefaults() {
        Config d = new Config();
        unlockEnabled = d.unlockEnabled;
        appAnimEnabled = d.appAnimEnabled;
        ccAnimEnabled = d.ccAnimEnabled;
        ccLowEndAnim = d.ccLowEndAnim;
        ccJellyEnabled = d.ccJellyEnabled;
        ccModularEditEnabled = d.ccModularEditEnabled;
        ccRestartOnEditExit = d.ccRestartOnEditExit;
        ccPriorityCards = d.ccPriorityCards;
        ccPriorityMedia = d.ccPriorityMedia;
        ccPriorityBrightness = d.ccPriorityBrightness;
        ccPriorityVolume = d.ccPriorityVolume;
        ccPrioritySmartHub = d.ccPrioritySmartHub;
        ccPriorityDeviceControls = d.ccPriorityDeviceControls;
        ccPriorityTiles = d.ccPriorityTiles;
        notifAnimEnabled = d.notifAnimEnabled;
        animMode = d.animMode;
        gridColumns = d.gridColumns;
        gridRows = d.gridRows;
        dockDamping = d.dockDamping;
        dockResponse = d.dockResponse;
        centerResponse = d.centerResponse;
        centerDamping = d.centerDamping;
        edgeResponseDelay = d.edgeResponseDelay;
        edgeDampingDrop = d.edgeDampingDrop;
        zDepthMultiplier = d.zDepthMultiplier;
        minResponse = d.minResponse;
        maxResponse = d.maxResponse;
        minDamping = d.minDamping;
        maxDamping = d.maxDamping;
        durationScale = d.durationScale;
        velocityInfluence = d.velocityInfluence;
        cascadeDamping = d.cascadeDamping;
        kineticDockDamping = d.kineticDockDamping;
        kineticDockResponse = d.kineticDockResponse;
        kineticDockZDepth = d.kineticDockZDepth;
        maxVelocity = d.maxVelocity;
        appYArc = d.appYArc;
        appBottomZWarp = d.appBottomZWarp;
        appXSway = d.appXSway;
        appInterruptSmooth = d.appInterruptSmooth;
        appTravelDamping = d.appTravelDamping;
        appTravelStiffness = d.appTravelStiffness;
        appSizeDamping = d.appSizeDamping;
        appSizeStiffness = d.appSizeStiffness;
        appRatioDamping = d.appRatioDamping;
        appRatioStiffness = d.appRatioStiffness;
        appCornerDamping = d.appCornerDamping;
        appCornerStiffness = d.appCornerStiffness;
        appAlphaDamping = d.appAlphaDamping;
        appAlphaStiffness = d.appAlphaStiffness;
        appIconPitchDeg = d.appIconPitchDeg;
        appIconCameraDist = d.appIconCameraDist;
        lockCardBorderWidth = d.lockCardBorderWidth;
        lockCardBorderAlpha = d.lockCardBorderAlpha;
        lockCardRefractionHeight = d.lockCardRefractionHeight;
        lockCardRefractionAmount = d.lockCardRefractionAmount;
        lockCardDepthEffect = d.lockCardDepthEffect;
        lockPillWidth = d.lockPillWidth;
        lockPillHeight = d.lockPillHeight;
        lockPillBottomMargin = d.lockPillBottomMargin;
        lockLipHeight = d.lockLipHeight;
        pinCircleDiameter = d.pinCircleDiameter;
        pinCircleFillAlpha = d.pinCircleFillAlpha;
        pinCircleStrokeWidth = d.pinCircleStrokeWidth;
        pinCircleStrokeAlpha = d.pinCircleStrokeAlpha;
        pinShowKlondike = d.pinShowKlondike;
        pinDigitTextSize = d.pinDigitTextSize;
        pinRow1Offset = d.pinRow1Offset;
        pinRow2Offset = d.pinRow2Offset;
        pinRow3Offset = d.pinRow3Offset;
        pinRow0Offset = d.pinRow0Offset;
        pinTapSquishScale = d.pinTapSquishScale;
        pinReleasePlunge = d.pinReleasePlunge;
        pinJellyMaxStretch = d.pinJellyMaxStretch;
        pinJellyElasticLimit = d.pinJellyElasticLimit;
        pinSpringStiffness = d.pinSpringStiffness;
        pinSpringDamping = d.pinSpringDamping;
        pinPressGlowAlpha = d.pinPressGlowAlpha;
        pinGlowRadius = d.pinGlowRadius;
        pinEntryTranslationY = d.pinEntryTranslationY;
        pinEntryInitialScale = d.pinEntryInitialScale;
        pinEntryDuration = d.pinEntryDuration;
        pinRowDelayStep = d.pinRowDelayStep;
        pinColDelayStep = d.pinColDelayStep;
        pinEntryOvershoot = d.pinEntryOvershoot;
        ccDisableAnim = d.ccDisableAnim;
        ccCenterOrigin = d.ccCenterOrigin;
        ccIosVerticalDrop = d.ccIosVerticalDrop;
        ccEntryInitialScale = d.ccEntryInitialScale;
        ccEntryTranslationY = d.ccEntryTranslationY;
        ccWaterfallEnabled = d.ccWaterfallEnabled;
        ccWaterfallDelayStep = d.ccWaterfallDelayStep;
        ccSpreadRatio = d.ccSpreadRatio;
        ccSpreadResponse = d.ccSpreadResponse;
        ccSpreadDamping = d.ccSpreadDamping;
        ccSpreadReleaseResponse = d.ccSpreadReleaseResponse;
        ccSpreadReleaseDamping = d.ccSpreadReleaseDamping;
        ccTileShowResponse = d.ccTileShowResponse;
        ccTileShowDamping = d.ccTileShowDamping;
        ccTileHideResponse = d.ccTileHideResponse;
        ccTileHideDamping = d.ccTileHideDamping;
        ccTileShrinkResponse = d.ccTileShrinkResponse;
        ccTileShrinkDamping = d.ccTileShrinkDamping;
        ccTileDelayScale = d.ccTileDelayScale;
        ccBlurResponse = d.ccBlurResponse;
        ccBlurDamping = d.ccBlurDamping;
        ccFrictionMultiplier = d.ccFrictionMultiplier;
        notifDisableAnim = d.notifDisableAnim;
        notifLinearDrop = d.notifLinearDrop;
        notifInitialScale = d.notifInitialScale;
        notifStaggerStep = d.notifStaggerStep;
        notifSpringResponse = d.notifSpringResponse;
        notifSpringDamping = d.notifSpringDamping;
        notifExpandResponse = d.notifExpandResponse;
        notifExpandDamping = d.notifExpandDamping;
        notifReleaseResponse = d.notifReleaseResponse;
        notifReleaseDamping = d.notifReleaseDamping;
        notifBlurResponse = d.notifBlurResponse;
        notifBlurDamping = d.notifBlurDamping;
        dyn.clear();
    }

    public void loadFromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            unlockEnabled = json.optBoolean("unlockEnabled", unlockEnabled);
            appAnimEnabled = json.optBoolean("appAnimEnabled", appAnimEnabled);
            ccAnimEnabled = json.optBoolean("ccAnimEnabled", ccAnimEnabled);
            ccLowEndAnim = json.optBoolean("ccLowEndAnim", ccLowEndAnim);
            ccJellyEnabled = json.optBoolean("ccJellyEnabled", ccJellyEnabled);
            ccModularEditEnabled = json.optBoolean("ccModularEditEnabled", ccModularEditEnabled);
            ccRestartOnEditExit = json.optBoolean("ccRestartOnEditExit", ccRestartOnEditExit);
            ccPriorityCards = json.optInt("ccPriorityCards", ccPriorityCards);
            ccPriorityMedia = json.optInt("ccPriorityMedia", ccPriorityMedia);
            ccPriorityBrightness = json.optInt("ccPriorityBrightness", ccPriorityBrightness);
            ccPriorityVolume = json.optInt("ccPriorityVolume", ccPriorityVolume);
            ccPrioritySmartHub = json.optInt("ccPrioritySmartHub", ccPrioritySmartHub);
            ccPriorityDeviceControls = json.optInt("ccPriorityDeviceControls", ccPriorityDeviceControls);
            ccPriorityTiles = json.optInt("ccPriorityTiles", ccPriorityTiles);
            notifAnimEnabled = json.optBoolean("notifAnimEnabled", notifAnimEnabled);
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
            minResponse = (float) json.optDouble("minResponse", minResponse);
            maxResponse = (float) json.optDouble("maxResponse", maxResponse);
            minDamping = (float) json.optDouble("minDamping", minDamping);
            maxDamping = (float) json.optDouble("maxDamping", maxDamping);

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

            lockCardBorderWidth = (float) json.optDouble("lockCardBorderWidth", lockCardBorderWidth);
            lockCardBorderAlpha = (float) json.optDouble("lockCardBorderAlpha", lockCardBorderAlpha);
            lockCardRefractionHeight = (float) json.optDouble("lockCardRefractionHeight", lockCardRefractionHeight);
            lockCardRefractionAmount = (float) json.optDouble("lockCardRefractionAmount", lockCardRefractionAmount);
            lockCardDepthEffect = (float) json.optDouble("lockCardDepthEffect", lockCardDepthEffect);
            lockPillWidth = (float) json.optDouble("lockPillWidth", lockPillWidth);
            lockPillHeight = (float) json.optDouble("lockPillHeight", lockPillHeight);
            lockPillBottomMargin = (float) json.optDouble("lockPillBottomMargin", lockPillBottomMargin);
            lockLipHeight = (float) json.optDouble("lockLipHeight", lockLipHeight);
            pinCircleDiameter = (float) json.optDouble("pinCircleDiameter", pinCircleDiameter);
            pinCircleFillAlpha = (float) json.optDouble("pinCircleFillAlpha", pinCircleFillAlpha);
            pinCircleStrokeWidth = (float) json.optDouble("pinCircleStrokeWidth", pinCircleStrokeWidth);
            pinCircleStrokeAlpha = (float) json.optDouble("pinCircleStrokeAlpha", pinCircleStrokeAlpha);
            pinShowKlondike = json.optBoolean("pinShowKlondike", pinShowKlondike);
            pinDigitTextSize = (float) json.optDouble("pinDigitTextSize", pinDigitTextSize);
            pinRow1Offset = (float) json.optDouble("pinRow1Offset", pinRow1Offset);
            pinRow2Offset = (float) json.optDouble("pinRow2Offset", pinRow2Offset);
            pinRow3Offset = (float) json.optDouble("pinRow3Offset", pinRow3Offset);
            pinRow0Offset = (float) json.optDouble("pinRow0Offset", pinRow0Offset);
            pinTapSquishScale = (float) json.optDouble("pinTapSquishScale", pinTapSquishScale);
            pinReleasePlunge = (float) json.optDouble("pinReleasePlunge", pinReleasePlunge);
            pinJellyMaxStretch = (float) json.optDouble("pinJellyMaxStretch", pinJellyMaxStretch);
            pinJellyElasticLimit = (float) json.optDouble("pinJellyElasticLimit", pinJellyElasticLimit);
            pinSpringStiffness = (float) json.optDouble("pinSpringStiffness", pinSpringStiffness);
            pinSpringDamping = (float) json.optDouble("pinSpringDamping", pinSpringDamping);
            pinPressGlowAlpha = (float) json.optDouble("pinPressGlowAlpha", pinPressGlowAlpha);
            pinGlowRadius = (float) json.optDouble("pinGlowRadius", pinGlowRadius);
            pinEntryTranslationY = (float) json.optDouble("pinEntryTranslationY", pinEntryTranslationY);
            pinEntryInitialScale = (float) json.optDouble("pinEntryInitialScale", pinEntryInitialScale);
            pinEntryDuration = (float) json.optDouble("pinEntryDuration", pinEntryDuration);
            pinRowDelayStep = (float) json.optDouble("pinRowDelayStep", pinRowDelayStep);
            pinColDelayStep = (float) json.optDouble("pinColDelayStep", pinColDelayStep);
            pinEntryOvershoot = (float) json.optDouble("pinEntryOvershoot", pinEntryOvershoot);

            ccDisableAnim = json.optBoolean("ccDisableAnim", ccDisableAnim);
            ccCenterOrigin = json.optBoolean("ccCenterOrigin", ccCenterOrigin);
            ccIosVerticalDrop = json.optBoolean("ccIosVerticalDrop", ccIosVerticalDrop);
            ccEntryInitialScale = (float) json.optDouble("ccEntryInitialScale", ccEntryInitialScale);
            ccEntryTranslationY = (float) json.optDouble("ccEntryTranslationY", ccEntryTranslationY);
            ccWaterfallEnabled = json.optBoolean("ccWaterfallEnabled", ccWaterfallEnabled);
            ccWaterfallDelayStep = (float) json.optDouble("ccWaterfallDelayStep", ccWaterfallDelayStep);
            ccSpreadRatio = (float) json.optDouble("ccSpreadRatio", ccSpreadRatio);
            ccSpreadResponse = (float) json.optDouble("ccSpreadResponse", ccSpreadResponse);
            ccSpreadDamping = (float) json.optDouble("ccSpreadDamping", ccSpreadDamping);
            ccSpreadReleaseResponse = (float) json.optDouble("ccSpreadReleaseResponse", ccSpreadReleaseResponse);
            ccSpreadReleaseDamping = (float) json.optDouble("ccSpreadReleaseDamping", ccSpreadReleaseDamping);
            ccTileShowResponse = (float) json.optDouble("ccTileShowResponse", ccTileShowResponse);
            ccTileShowDamping = (float) json.optDouble("ccTileShowDamping", ccTileShowDamping);
            ccTileHideResponse = (float) json.optDouble("ccTileHideResponse", ccTileHideResponse);
            ccTileHideDamping = (float) json.optDouble("ccTileHideDamping", ccTileHideDamping);
            ccTileShrinkResponse = (float) json.optDouble("ccTileShrinkResponse", ccTileShrinkResponse);
            ccTileShrinkDamping = (float) json.optDouble("ccTileShrinkDamping", ccTileShrinkDamping);
            ccTileDelayScale = (float) json.optDouble("ccTileDelayScale", ccTileDelayScale);
            ccBlurResponse = (float) json.optDouble("ccBlurResponse", ccBlurResponse);
            ccBlurDamping = (float) json.optDouble("ccBlurDamping", ccBlurDamping);
            ccFrictionMultiplier = (float) json.optDouble("ccFrictionMultiplier", ccFrictionMultiplier);

            notifDisableAnim = json.optBoolean("notifDisableAnim", notifDisableAnim);
            notifLinearDrop = json.optBoolean("notifLinearDrop", notifLinearDrop);
            notifInitialScale = (float) json.optDouble("notifInitialScale", notifInitialScale);
            notifStaggerStep = (float) json.optDouble("notifStaggerStep", notifStaggerStep);
            notifSpringResponse = (float) json.optDouble("notifSpringResponse", notifSpringResponse);
            notifSpringDamping = (float) json.optDouble("notifSpringDamping", notifSpringDamping);
            notifExpandResponse = (float) json.optDouble("notifExpandResponse", notifExpandResponse);
            notifExpandDamping = (float) json.optDouble("notifExpandDamping", notifExpandDamping);
            notifReleaseResponse = (float) json.optDouble("notifReleaseResponse", notifReleaseResponse);
            notifReleaseDamping = (float) json.optDouble("notifReleaseDamping", notifReleaseDamping);
            notifBlurResponse = (float) json.optDouble("notifBlurResponse", notifBlurResponse);
            notifBlurDamping = (float) json.optDouble("notifBlurDamping", notifBlurDamping);

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                try { dyn.put(k, (float) json.getDouble(k)); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
