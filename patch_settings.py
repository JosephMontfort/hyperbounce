import re

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "r") as f:
    content = f.read()

if "import org.json.JSONObject" not in content:
    content = content.replace("import android.content.SharedPreferences", "import android.content.SharedPreferences\nimport org.json.JSONObject")

if "val diagnostics: StateFlow<SystemUiDiagnosticsSettings>" not in content:
    new_state = """
    // Diagnostics flow
    private val _diagnostics = MutableStateFlow(loadDiagnostics())
    val diagnostics: StateFlow<SystemUiDiagnosticsSettings> = _diagnostics.asStateFlow()

    private fun loadDiagnostics(): SystemUiDiagnosticsSettings {
        val jsonStr = prefs.getString("diagnostic_config_json", "{}")
        val json = try { JSONObject(jsonStr) } catch(e: Exception) { JSONObject() }
        
        return SystemUiDiagnosticsSettings(
            ccLowEndAnim = json.optBoolean("ccLowEndAnim", false),
            ccAnimEnabled = json.optBoolean("ccAnimEnabled", true),
            ccModularEditEnabled = json.optBoolean("ccModularEditEnabled", true),
            ccJellyEnabled = json.optBoolean("ccJellyEnabled", false),

            lockCardBorderWidth = json.optDouble("lockCardBorderWidth", 1.0).toFloat(),
            lockCardBorderAlpha = json.optDouble("lockCardBorderAlpha", 0.1).toFloat(),
            lockCardRefractionHeight = json.optDouble("lockCardRefractionHeight", 10.0).toFloat(),
            lockCardRefractionAmount = json.optDouble("lockCardRefractionAmount", 16.0).toFloat(),
            lockCardDepthEffect = json.optDouble("lockCardDepthEffect", 0.5).toFloat(),
            lockPillWidth = json.optDouble("lockPillWidth", 40.0).toFloat(),
            lockPillHeight = json.optDouble("lockPillHeight", 2.0).toFloat(),
            lockPillBottomMargin = json.optDouble("lockPillBottomMargin", 9.0).toFloat(),
            lockLipHeight = json.optDouble("lockLipHeight", 10.0).toFloat(),

            pinDigitTextSize = json.optDouble("pinDigitTextSize", 28.0).toFloat(),
            pinRow1Offset = json.optDouble("pinRow1Offset", 0.0).toFloat(),
            pinRow2Offset = json.optDouble("pinRow2Offset", 0.0).toFloat(),
            pinRow3Offset = json.optDouble("pinRow3Offset", 0.0).toFloat(),
            pinRow0Offset = json.optDouble("pinRow0Offset", 0.0).toFloat(),
            pinTapSquishScale = json.optDouble("pinTapSquishScale", 0.85).toFloat(),
            pinReleasePlunge = json.optDouble("pinReleasePlunge", 0.95).toFloat(),
            pinShowKlondike = json.optBoolean("pinShowKlondike", true),
            pinEntryTranslationY = json.optDouble("pinEntryTranslationY", 100.0).toFloat(),
            pinEntryInitialScale = json.optDouble("pinEntryInitialScale", 0.8).toFloat(),
            pinEntryDuration = json.optDouble("pinEntryDuration", 500.0).toFloat(),
            pinRowDelayStep = json.optDouble("pinRowDelayStep", 50.0).toFloat(),
            pinColDelayStep = json.optDouble("pinColDelayStep", 20.0).toFloat(),
            pinEntryOvershoot = json.optDouble("pinEntryOvershoot", 1.1).toFloat()
        )
    }

    @JvmName("updateDiagnostics")
    fun updateDiagnostics(updater: (SystemUiDiagnosticsSettings) -> SystemUiDiagnosticsSettings) {
        val current = _diagnostics.value
        val updated = updater(current)
        if (current != updated) {
            val json = JSONObject()
            json.put("ccLowEndAnim", updated.ccLowEndAnim)
            json.put("ccAnimEnabled", updated.ccAnimEnabled)
            json.put("ccModularEditEnabled", updated.ccModularEditEnabled)
            json.put("ccJellyEnabled", updated.ccJellyEnabled)
            
            json.put("lockCardBorderWidth", updated.lockCardBorderWidth.toDouble())
            json.put("lockCardBorderAlpha", updated.lockCardBorderAlpha.toDouble())
            json.put("lockCardRefractionHeight", updated.lockCardRefractionHeight.toDouble())
            json.put("lockCardRefractionAmount", updated.lockCardRefractionAmount.toDouble())
            json.put("lockCardDepthEffect", updated.lockCardDepthEffect.toDouble())
            json.put("lockPillWidth", updated.lockPillWidth.toDouble())
            json.put("lockPillHeight", updated.lockPillHeight.toDouble())
            json.put("lockPillBottomMargin", updated.lockPillBottomMargin.toDouble())
            json.put("lockLipHeight", updated.lockLipHeight.toDouble())

            json.put("pinDigitTextSize", updated.pinDigitTextSize.toDouble())
            json.put("pinRow1Offset", updated.pinRow1Offset.toDouble())
            json.put("pinRow2Offset", updated.pinRow2Offset.toDouble())
            json.put("pinRow3Offset", updated.pinRow3Offset.toDouble())
            json.put("pinRow0Offset", updated.pinRow0Offset.toDouble())
            json.put("pinTapSquishScale", updated.pinTapSquishScale.toDouble())
            json.put("pinReleasePlunge", updated.pinReleasePlunge.toDouble())
            json.put("pinShowKlondike", updated.pinShowKlondike)
            json.put("pinEntryTranslationY", updated.pinEntryTranslationY.toDouble())
            json.put("pinEntryInitialScale", updated.pinEntryInitialScale.toDouble())
            json.put("pinEntryDuration", updated.pinEntryDuration.toDouble())
            json.put("pinRowDelayStep", updated.pinRowDelayStep.toDouble())
            json.put("pinColDelayStep", updated.pinColDelayStep.toDouble())
            json.put("pinEntryOvershoot", updated.pinEntryOvershoot.toDouble())
            
            prefs.edit().putString("diagnostic_config_json", json.toString()).apply()
            _diagnostics.value = updated
        }
    }
    """
    content = content.replace("private fun loadSettings(): AppSettings {", new_state + "\n    private fun loadSettings(): AppSettings {")

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "w") as f:
    f.write(content)

