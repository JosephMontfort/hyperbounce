package dev.joseph.hyperbounce.ui.search

import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.state.NavApp

data class SearchItem(
    val id: String,
    val title: String,
    val category: String, // Breadcrumb path shown in search results
    val destination: Destination,
    val highlightKey: String = title,
    val scrollIndex: Int = 0,
    val keywords: List<String> = emptyList()
)

object SearchIndex {
    val allItems: List<SearchItem> = listOf(
        // === Toggles & Sliders ===
        SearchItem(
            id = "toggle_hook_master",
            title = "Enable Hook",
            category = "Toggles and Sliders",
            destination = Destination.Engine,
            highlightKey = "Enable Hook",
            scrollIndex = 0,
            keywords = listOf("toggle", "slider", "master", "hook", "enable")
        ),
        SearchItem(
            id = "toggle_engine",
            title = "Toggles",
            category = "Toggles and Sliders",
            destination = Destination.Engine,
            highlightKey = "Toggles",
            scrollIndex = 1,
            keywords = listOf("toggle", "switch", "liquid glass", "engine")
        ),
        SearchItem(
            id = "slider_engine",
            title = "Sliders",
            category = "Toggles and Sliders",
            destination = Destination.Engine,
            highlightKey = "Sliders",
            scrollIndex = 1,
            keywords = listOf("slider", "liquid glass", "engine", "volume", "brightness")
        ),
        SearchItem(
            id = "toggle_accent_color",
            title = "Toggle Switch Accent",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Toggle Switch Accent",
            scrollIndex = 0,
            keywords = listOf("color", "accent", "preset", "green", "blue", "custom")
        ),
        SearchItem(
            id = "toggle_blur",
            title = "Blur",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Blur",
            scrollIndex = 1,
            keywords = listOf("blur", "gaussian", "glass", "optics")
        ),
        SearchItem(
            id = "toggle_lens_size",
            title = "Lens Size",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Lens Size",
            scrollIndex = 1,
            keywords = listOf("curvature", "refraction", "lens")
        ),
        SearchItem(
            id = "toggle_lens_depth",
            title = "Lens Depth",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Lens Depth",
            scrollIndex = 1,
            keywords = listOf("extrusion", "depth", "lens")
        ),
        SearchItem(
            id = "toggle_highlight",
            title = "Highlight Strength",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Highlight Strength",
            scrollIndex = 1,
            keywords = listOf("specular", "fresnel", "rim", "highlight")
        ),
        SearchItem(
            id = "toggle_shadow",
            title = "Shadow Radius",
            category = "Toggles and Sliders > Appearance",
            destination = Destination.ToggleAppearance,
            highlightKey = "Shadow Radius",
            scrollIndex = 1,
            keywords = listOf("shadow", "drop shadow", "diffusion")
        ),
        SearchItem(
            id = "toggle_drag_sensitivity",
            title = "Drag Sensitivity",
            category = "Toggles and Sliders > Drag Physics",
            destination = Destination.TogglePhysics,
            highlightKey = "Drag Sensitivity",
            scrollIndex = 0,
            keywords = listOf("drag", "touch", "tracking", "sensitivity")
        ),
        SearchItem(
            id = "toggle_spring_bounce",
            title = "Spring Bounciness",
            category = "Toggles and Sliders > Drag Physics",
            destination = Destination.TogglePhysics,
            highlightKey = "Spring Bounciness",
            scrollIndex = 0,
            keywords = listOf("damping", "overshoot", "bounce", "spring")
        ),
        SearchItem(
            id = "toggle_spring_stiffness",
            title = "Spring Stiffness",
            category = "Toggles and Sliders > Drag Physics",
            destination = Destination.TogglePhysics,
            highlightKey = "Spring Stiffness",
            scrollIndex = 0,
            keywords = listOf("stiffness", "frequency", "snap", "speed")
        ),
        SearchItem(
            id = "slider_blur",
            title = "Blur",
            category = "Toggles and Sliders > Slider Appearance",
            destination = Destination.SliderAppearance,
            highlightKey = "Blur",
            scrollIndex = 0,
            keywords = listOf("blur", "gaussian", "glass", "slider")
        ),
        SearchItem(
            id = "slider_lens_size",
            title = "Lens Size",
            category = "Toggles and Sliders > Slider Appearance",
            destination = Destination.SliderAppearance,
            highlightKey = "Lens Size",
            scrollIndex = 0,
            keywords = listOf("lens", "size", "slider")
        ),
        SearchItem(
            id = "slider_lens_depth",
            title = "Lens Depth",
            category = "Toggles and Sliders > Slider Appearance",
            destination = Destination.SliderAppearance,
            highlightKey = "Lens Depth",
            scrollIndex = 0,
            keywords = listOf("lens", "depth", "slider")
        ),
        SearchItem(
            id = "slider_highlight_strength",
            title = "Highlight Strength",
            category = "Toggles and Sliders > Slider Appearance",
            destination = Destination.SliderAppearance,
            highlightKey = "Highlight Strength",
            scrollIndex = 0,
            keywords = listOf("highlight", "fresnel", "slider")
        ),
        SearchItem(
            id = "slider_shadow_radius",
            title = "Shadow Radius",
            category = "Toggles and Sliders > Slider Appearance",
            destination = Destination.SliderAppearance,
            highlightKey = "Shadow Radius",
            scrollIndex = 0,
            keywords = listOf("shadow", "radius", "slider")
        ),
        SearchItem(
            id = "slider_drag_sensitivity",
            title = "Drag Sensitivity",
            category = "Toggles and Sliders > Slider Physics",
            destination = Destination.SliderPhysics,
            highlightKey = "Drag Sensitivity",
            scrollIndex = 0,
            keywords = listOf("drag", "slider", "tracking")
        ),
        SearchItem(
            id = "slider_spring_bounciness",
            title = "Spring Bounciness",
            category = "Toggles and Sliders > Slider Physics",
            destination = Destination.SliderPhysics,
            highlightKey = "Spring Bounciness",
            scrollIndex = 0,
            keywords = listOf("bounce", "damping", "slider")
        ),
        SearchItem(
            id = "slider_spring_stiffness",
            title = "Spring Stiffness",
            category = "Toggles and Sliders > Slider Physics",
            destination = Destination.SliderPhysics,
            highlightKey = "Spring Stiffness",
            scrollIndex = 0,
            keywords = listOf("stiffness", "snap", "slider")
        ),

        // === Lockscreen Stuffs ===
        SearchItem(
            id = "lock_pill_master",
            title = "Enable Home Indicator Pill",
            category = "LockScreen Stuffs > Lock Screen Sheet > Home Indicator Pill",
            destination = Destination.LockScreenSheetHomeIndicator,
            highlightKey = "Enable Home Indicator Pill",
            scrollIndex = 0,
            keywords = listOf("lockscreen", "pill", "home indicator", "bar", "master", "enable")
        ),
        SearchItem(
            id = "lockscreen_bottom_refraction",
            title = "Bottom Edge Refraction",
            category = "LockScreen Stuffs > Lock Screen Sheet > Liquid Glass Tuning",
            destination = Destination.LockScreenSheetGlassTuning,
            highlightKey = "Bottom Edge Refraction",
            scrollIndex = 0,
            keywords = listOf("refraction", "bottom", "rim", "edge", "lens")
        ),
        SearchItem(
            id = "lockscreen_dispersion",
            title = "Chromatic Dispersion",
            category = "LockScreen Stuffs > Lock Screen Sheet > Liquid Glass Tuning",
            destination = Destination.LockScreenSheetGlassTuning,
            highlightKey = "Chromatic Dispersion",
            scrollIndex = 0,
            keywords = listOf("chromatic", "dispersion", "rgb", "prism", "spectral")
        ),
        SearchItem(
            id = "lockscreen_caustic_softness",
            title = "Caustic Rim Softness",
            category = "LockScreen Stuffs > Lock Screen Sheet > Liquid Glass Tuning",
            destination = Destination.LockScreenSheetGlassTuning,
            highlightKey = "Caustic Rim Softness",
            scrollIndex = 0,
            keywords = listOf("caustic", "rim", "softness", "bevel")
        ),
        SearchItem(
            id = "lockscreen_saturation",
            title = "Vibrancy Saturation Boost",
            category = "LockScreen Stuffs > Lock Screen Sheet > Liquid Glass Tuning",
            destination = Destination.LockScreenSheetGlassTuning,
            highlightKey = "Vibrancy Saturation Boost",
            scrollIndex = 0,
            keywords = listOf("saturation", "vibrancy", "color")
        ),
        SearchItem(
            id = "lockscreen_frosted_blur",
            title = "Frosted Glass Blur",
            category = "LockScreen Stuffs > Lock Screen Sheet > Liquid Glass Tuning",
            destination = Destination.LockScreenSheetGlassTuning,
            highlightKey = "Frosted Glass Blur",
            scrollIndex = 0,
            keywords = listOf("blur", "frosted", "glass", "crystal")
        ),
        SearchItem(
            id = "keypad_glass_blur",
            title = "Glass Blur Radius",
            category = "LockScreen Stuffs > Pinpad > Glass Tuning",
            destination = Destination.KeypadGlassTuning,
            highlightKey = "Glass Blur Radius",
            scrollIndex = 0,
            keywords = listOf("pinpad", "keypad", "blur", "glass")
        ),
        SearchItem(
            id = "keypad_lens_size",
            title = "Lens Size",
            category = "LockScreen Stuffs > Pinpad > Glass Tuning",
            destination = Destination.KeypadGlassTuning,
            highlightKey = "Lens Size",
            scrollIndex = 0,
            keywords = listOf("pinpad", "lens", "curvature")
        ),
        SearchItem(
            id = "keypad_lens_depth",
            title = "Lens Depth",
            category = "LockScreen Stuffs > Pinpad > Glass Tuning",
            destination = Destination.KeypadGlassTuning,
            highlightKey = "Lens Depth",
            scrollIndex = 0,
            keywords = listOf("pinpad", "lens", "depth")
        ),
        SearchItem(
            id = "keypad_highlight",
            title = "Highlight Strength",
            category = "LockScreen Stuffs > Pinpad > Glass Tuning",
            destination = Destination.KeypadGlassTuning,
            highlightKey = "Highlight Strength",
            scrollIndex = 0,
            keywords = listOf("pinpad", "highlight", "fresnel")
        ),
        SearchItem(
            id = "keypad_shadow",
            title = "Shadow Radius",
            category = "LockScreen Stuffs > Pinpad > Glass Tuning",
            destination = Destination.KeypadGlassTuning,
            highlightKey = "Shadow Radius",
            scrollIndex = 0,
            keywords = listOf("pinpad", "shadow")
        ),
        SearchItem(
            id = "keypad_btn_size",
            title = "Button Size",
            category = "LockScreen Stuffs > Pinpad > Buttons Layout",
            destination = Destination.KeypadButtonsLayout,
            highlightKey = "Button Size",
            scrollIndex = 0,
            keywords = listOf("pinpad", "size", "button", "dimension")
        ),
        SearchItem(
            id = "keypad_btn_spacing",
            title = "Button Spacing",
            category = "LockScreen Stuffs > Pinpad > Buttons Layout",
            destination = Destination.KeypadButtonsLayout,
            highlightKey = "Button Spacing",
            scrollIndex = 0,
            keywords = listOf("pinpad", "spacing", "gap", "margin")
        ),
        SearchItem(
            id = "keypad_corner_radius",
            title = "Corner Radius",
            category = "LockScreen Stuffs > Pinpad > Buttons Layout",
            destination = Destination.KeypadButtonsLayout,
            highlightKey = "Corner Radius",
            scrollIndex = 0,
            keywords = listOf("pinpad", "corner", "roundness", "radius")
        ),
        SearchItem(
            id = "keypad_pad_margin",
            title = "Pad Margin Bottom",
            category = "LockScreen Stuffs > Pinpad > Pad Layout",
            destination = Destination.KeypadPadLayout,
            highlightKey = "Pad Margin Bottom",
            scrollIndex = 0,
            keywords = listOf("pinpad", "bottom", "margin", "position")
        ),
        SearchItem(
            id = "keypad_num_delay",
            title = "Number Press Delay",
            category = "LockScreen Stuffs > Pinpad > Delays",
            destination = Destination.KeypadDelays,
            highlightKey = "Number Press Delay",
            scrollIndex = 0,
            keywords = listOf("pinpad", "delay", "number", "press")
        ),
        SearchItem(
            id = "keypad_error_shake",
            title = "Error Shake Duration",
            category = "LockScreen Stuffs > Pinpad > Delays",
            destination = Destination.KeypadDelays,
            highlightKey = "Error Shake Duration",
            scrollIndex = 1,
            keywords = listOf("pinpad", "error", "shake", "duration")
        ),
        SearchItem(
            id = "keypad_backspace_delay",
            title = "Backspace Delay",
            category = "LockScreen Stuffs > Pinpad > Delays",
            destination = Destination.KeypadDelays,
            highlightKey = "Backspace Delay",
            scrollIndex = 2,
            keywords = listOf("pinpad", "backspace", "delete", "delay")
        ),
        SearchItem(
            id = "keypad_bloom_radius",
            title = "Bloom Radius",
            category = "LockScreen Stuffs > Pinpad > Bloom Physics",
            destination = Destination.KeypadIosBloomPhysics,
            highlightKey = "Bloom Radius",
            scrollIndex = 0,
            keywords = listOf("pinpad", "bloom", "glow", "radius", "ios")
        ),
        SearchItem(
            id = "keypad_bloom_intensity",
            title = "Bloom Intensity",
            category = "LockScreen Stuffs > Pinpad > Bloom Physics",
            destination = Destination.KeypadIosBloomPhysics,
            highlightKey = "Bloom Intensity",
            scrollIndex = 0,
            keywords = listOf("pinpad", "bloom", "glow", "intensity", "brightness")
        ),
        SearchItem(
            id = "keypad_bloom_decay",
            title = "Bloom Decay Duration",
            category = "LockScreen Stuffs > Pinpad > Bloom Physics",
            destination = Destination.KeypadIosBloomPhysics,
            highlightKey = "Bloom Decay Duration",
            scrollIndex = 0,
            keywords = listOf("pinpad", "bloom", "decay", "fade")
        ),

        // === System Launcher Stuffs ===
        SearchItem(
            id = "trans_master",
            title = "Enable App Transitions Engine",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Enable App Transitions Engine",
            scrollIndex = 0,
            keywords = listOf("launcher", "transitions", "app launch", "close")
        ),
        SearchItem(
            id = "trans_speed",
            title = "Animation Speed",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Animation Speed",
            scrollIndex = 1,
            keywords = listOf("speed", "velocity", "duration", "fast")
        ),
        SearchItem(
            id = "trans_open_damping",
            title = "Opening Spring Damping",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Opening Spring Damping",
            scrollIndex = 1,
            keywords = listOf("open", "spring", "damping", "elasticity")
        ),
        SearchItem(
            id = "trans_close_damping",
            title = "Closing Spring Damping",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Closing Spring Damping",
            scrollIndex = 1,
            keywords = listOf("close", "spring", "damping", "bounce")
        ),
        SearchItem(
            id = "trans_folder",
            title = "1:1 Folder Icon Trajectories",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "1:1 Folder Icon Trajectories",
            scrollIndex = 2,
            keywords = listOf("folder", "icon", "trajectory", "animation")
        ),
        SearchItem(
            id = "trans_recents_center",
            title = "Center Active App in Recents",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Center Active App in Recents",
            scrollIndex = 2,
            keywords = listOf("recents", "overview", "center", "active app")
        ),
        SearchItem(
            id = "trans_zero_friction",
            title = "Zero Friction Drag Resistance",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Zero Friction Drag Resistance",
            scrollIndex = 2,
            keywords = listOf("friction", "rubber-band", "drag", "swipe up")
        ),
        SearchItem(
            id = "trans_refined_haptics",
            title = "Refined Tactile Haptics",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Refined Tactile Haptics",
            scrollIndex = 3,
            keywords = listOf("haptic", "vibration", "tactile", "back gesture")
        ),
        SearchItem(
            id = "trans_sliding_cards_haptics",
            title = "Haptics for Sliding Cards",
            category = "System Launcher Stuffs > App Transitions",
            destination = Destination.AppTransitions,
            highlightKey = "Haptics for Sliding Cards",
            scrollIndex = 3,
            keywords = listOf("haptic", "recents", "sliding cards", "tick")
        ),
        SearchItem(
            id = "unlock_master",
            title = "Enable Unlock Kinetics",
            category = "System Launcher Stuffs > Screen Unlock",
            destination = Destination.ScreenUnlock,
            highlightKey = "Enable Unlock Kinetics",
            scrollIndex = 0,
            keywords = listOf("unlock", "kinetics", "launcher", "spring")
        ),
        SearchItem(
            id = "unlock_grid_cols",
            title = "Grid Columns",
            category = "System Launcher Stuffs > Screen Unlock",
            destination = Destination.ScreenUnlock,
            highlightKey = "Grid Columns",
            scrollIndex = 1,
            keywords = listOf("grid", "columns", "icons", "desktop")
        ),
        SearchItem(
            id = "unlock_grid_rows",
            title = "Grid Rows",
            category = "System Launcher Stuffs > Screen Unlock",
            destination = Destination.ScreenUnlock,
            highlightKey = "Grid Rows",
            scrollIndex = 1,
            keywords = listOf("grid", "rows", "icons", "desktop")
        ),

        // === Notification Centre ===
        SearchItem(
            id = "nc_sheet_master",
            title = "Notification Center iOS Sheet",
            category = "Notification Centre",
            destination = Destination.NotificationCenterSheet,
            highlightKey = "Notification Center iOS Sheet",
            scrollIndex = 0,
            keywords = listOf("notification", "sheet", "shade", "ios", "container")
        ),
        SearchItem(
            id = "nc_lock_wallpaper",
            title = "Lock Screen Wallpaper Background",
            category = "Notification Centre",
            destination = Destination.NotificationCenterSheet,
            highlightKey = "Lock Screen Wallpaper Background",
            scrollIndex = 1,
            keywords = listOf("wallpaper", "lockscreen", "background", "shade")
        ),
        SearchItem(
            id = "nc_1to1_tracking",
            title = "1:1 Finger Tracking",
            category = "Notification Centre",
            destination = Destination.NotificationCenterSheet,
            highlightKey = "1:1 Finger Tracking",
            scrollIndex = 1,
            keywords = listOf("tracking", "finger", "gesture", "pulldown", "threshold")
        ),
        SearchItem(
            id = "nc_homescreen_only",
            title = "Homescreen Only",
            category = "Notification Centre",
            destination = Destination.NotificationCenterSheet,
            highlightKey = "Homescreen Only",
            scrollIndex = 1,
            keywords = listOf("homescreen", "transparent", "shade")
        ),
        SearchItem(
            id = "nc_custom_wallpaper",
            title = "Custom Background Wallpaper",
            category = "Notification Centre",
            destination = Destination.NotificationCenterSheet,
            highlightKey = "Custom Background Wallpaper",
            scrollIndex = 1,
            keywords = listOf("wallpaper", "custom", "image", "crop")
        ),

        // === Control Centre ===
        SearchItem(
            id = "cc_edit_unlock",
            title = "Unlock Control Center Edit Page",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Unlock Control Center Edit Page",
            scrollIndex = 0,
            keywords = listOf("control center", "edit", "tiles", "resize", "modular")
        ),
        SearchItem(
            id = "cc_landscape_rows",
            title = "Landscape Right Panel Rows",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Landscape Right Panel Rows",
            scrollIndex = 0,
            keywords = listOf("landscape", "rows", "control center", "divider")
        ),
        SearchItem(
            id = "cc_fluid_resize",
            title = "Enhanced Tile Resize Handling",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Enhanced Tile Resize Handling",
            scrollIndex = 0,
            keywords = listOf("tile", "resize", "drag", "expansion")
        ),
        SearchItem(
            id = "cc_anim_engine",
            title = "Animation Engine",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Animation Engine",
            scrollIndex = 0,
            keywords = listOf("animation", "high-end", "low-end", "engine")
        ),
        SearchItem(
            id = "cc_center_bounce",
            title = "Center Bounce Animation",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Center Bounce Animation",
            scrollIndex = 0,
            keywords = listOf("center", "bounce", "spring", "harmonic")
        ),
        SearchItem(
            id = "cc_jelly",
            title = "Jelly Physics Engine",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Jelly Physics Engine",
            scrollIndex = 0,
            keywords = listOf("jelly", "squish", "stretch", "physics", "poisson")
        ),
        SearchItem(
            id = "cc_flashlight_unlock",
            title = "Unlock Flashlight Brightness Control",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Unlock Flashlight Brightness Control",
            scrollIndex = 0,
            keywords = listOf("flashlight", "torch", "brightness", "level")
        ),
        SearchItem(
            id = "cc_flashlight_max",
            title = "Maximum Brightness Limit",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Maximum Brightness Limit",
            scrollIndex = 0,
            keywords = listOf("flashlight", "brightness", "cap", "limit")
        ),
        SearchItem(
            id = "cc_reset_stock",
            title = "Reset Layout to Stock",
            category = "Control Centre",
            destination = Destination.ControlCenter,
            highlightKey = "Reset Layout to Stock",
            scrollIndex = 1,
            keywords = listOf("reset", "layout", "stock", "default", "restore")
        ),

        // === Floating Navigation Bar ===
        SearchItem(
            id = "nav_instagram",
            title = "Instagram Navigation Bar",
            category = "Floating Navigation Bar > Instagram",
            destination = Destination.AppDetail(NavApp.Instagram),
            highlightKey = "Instagram Navigation Bar",
            scrollIndex = 0,
            keywords = listOf("instagram", "navbar", "floating", "bottom bar")
        ),
        SearchItem(
            id = "nav_whatsapp",
            title = "WhatsApp Navigation Bar",
            category = "Floating Navigation Bar > WhatsApp",
            destination = Destination.AppDetail(NavApp.WhatsApp),
            highlightKey = "WhatsApp Navigation Bar",
            scrollIndex = 0,
            keywords = listOf("whatsapp", "navbar", "floating", "bottom bar")
        ),
        SearchItem(
            id = "nav_youtube",
            title = "YouTube Navigation Bar",
            category = "Floating Navigation Bar > YouTube",
            destination = Destination.AppDetail(NavApp.YouTube),
            highlightKey = "YouTube Navigation Bar",
            scrollIndex = 0,
            keywords = listOf("youtube", "navbar", "floating", "bottom bar")
        ),

        // === Dialer and Caller Screen ===
        SearchItem(
            id = "caller_master",
            title = "Enable iOS Caller Screen",
            category = "Dialer and Caller Screen",
            destination = Destination.CallerScreen,
            highlightKey = "Enable iOS Caller Screen",
            scrollIndex = 0,
            keywords = listOf("caller", "incoming call", "full screen", "ios", "dual slider")
        ),
        SearchItem(
            id = "caller_button_size",
            title = "Button & Track Size",
            category = "Dialer and Caller Screen",
            destination = Destination.CallerScreen,
            highlightKey = "Button & Track Size",
            scrollIndex = 1,
            keywords = listOf("caller", "button", "size", "track")
        ),
        SearchItem(
            id = "caller_offset",
            title = "Vertical Position Offset",
            category = "Dialer and Caller Screen",
            destination = Destination.CallerScreen,
            highlightKey = "Vertical Position Offset",
            scrollIndex = 1,
            keywords = listOf("caller", "vertical", "offset", "height", "position")
        ),
        SearchItem(
            id = "dialer_swipe",
            title = "Swipe Gestures in Dialer",
            category = "Dialer and Caller Screen",
            destination = Destination.CallerScreen,
            highlightKey = "Swipe Gestures in Dialer",
            scrollIndex = 2,
            keywords = listOf("dialer", "swipe", "gesture", "call", "delete", "recents")
        ),

        // === Haptic Studio ===
        SearchItem(
            id = "haptic_studio_main",
            title = "Haptic Studio Waveform",
            category = "Haptic Studio",
            destination = Destination.HapticStudio,
            highlightKey = "Haptic Studio",
            scrollIndex = 0,
            keywords = listOf("haptic", "studio", "waveform", "music", "vibration", "ringtone")
        ),
        SearchItem(
            id = "haptic_build_pack",
            title = "Build Magisk Ringtone Module",
            category = "Haptic Studio",
            destination = Destination.HapticStudio,
            highlightKey = "Build Pack",
            scrollIndex = 0,
            keywords = listOf("build pack", "magisk", "apatch", "module", "ringtone", "export")
        ),
        SearchItem(
            id = "haptic_trim",
            title = "Trim Audio Range",
            category = "Haptic Studio",
            destination = Destination.HapticStudio,
            highlightKey = "Trim",
            scrollIndex = 0,
            keywords = listOf("trim", "audio", "range", "start", "end", "duration")
        ),

        // === Settings & Safe Mode ===
        SearchItem(
            id = "safe_mode",
            title = "Safe Mode & Crash Guard",
            category = "Settings > Safe Mode",
            destination = Destination.SafeMode,
            highlightKey = "Safe Mode",
            scrollIndex = 0,
            keywords = listOf("safe mode", "crash", "guard", "recovery", "logs")
        ),
        SearchItem(
            id = "about_page",
            title = "About & Open Source Licenses",
            category = "About",
            destination = Destination.About,
            highlightKey = "About",
            scrollIndex = 0,
            keywords = listOf("about", "licenses", "github", "donate", "developer")
        )
    )

    fun search(query: String): List<SearchItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val tokens = q.split("\\s+".toRegex()).filter { it.isNotBlank() }

        return allItems.filter { item ->
            val matchTitle = tokens.all { token -> item.title.lowercase().contains(token) }
            val matchCategory = tokens.all { token -> item.category.lowercase().contains(token) }
            val matchKeywords = item.keywords.any { kw -> tokens.any { token -> kw.contains(token) } }
            matchTitle || matchCategory || matchKeywords
        }
    }
}
