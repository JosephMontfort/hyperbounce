package dev.joseph.hyperbounce.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.catalog.destinations.ExpandableGlassMenu
import com.kyant.backdrop.catalog.destinations.MenuAlignment
import com.kyant.backdrop.catalog.destinations.MenuAnimationPreset
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { HyperBounceApp() }
    }
}

private enum class Screen { HOME, APP, GLASS, MOTION }

@Composable
private fun HyperBounceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedApp by remember { mutableStateOf("Instagram") }
    var liquidEnabled by remember { mutableStateOf(true) }
    var masterValue by remember { mutableFloatStateOf(1f) }
    var appSettings by remember { mutableStateOf(AppSettings()) }
    var animationSpeed by remember { mutableFloatStateOf(1f) }
    var containerBulge by remember { mutableFloatStateOf(0.395f) }
    var contentBulge by remember { mutableFloatStateOf(0.15f) }
    var contentPopX by remember { mutableFloatStateOf(0.03f) }
    var contentPopY by remember { mutableFloatStateOf(0.01f) }
    var dragTension by remember { mutableFloatStateOf(0.05f) }
    var motionBlur by remember { mutableFloatStateOf(25f) }
    var arcOffset by remember { mutableFloatStateOf(75f) }
    var menuPreset by remember { mutableStateOf(MenuAnimationPreset.Bouncy) }
    var menuHaptics by remember { mutableStateOf(true) }

    fun currentAppSettings(): AppBackdropSettings = when (selectedApp) {
        "Instagram" -> appSettings.instagram
        "YouTube" -> appSettings.youtube
        else -> appSettings.whatsapp
    }

    fun updateCurrentApp(update: (AppBackdropSettings) -> AppBackdropSettings) {
        appSettings = when (selectedApp) {
            "Instagram" -> appSettings.copy(instagram = update(appSettings.instagram))
            "YouTube" -> appSettings.copy(youtube = update(appSettings.youtube))
            else -> appSettings.copy(whatsapp = update(appSettings.whatsapp))
        }
    }

    val backdrop = rememberAppBackdrop()
    val scrollState = rememberScrollState()
    val menuProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    Box(Modifier.fillMaxSize()) {
        AppBackdropBackground()
        AppContentBackdrop(
            backdrop = backdrop,
            liquidEnabled = liquidEnabled,
            modifier = Modifier.fillMaxSize()
        )

        when (screen) {
            Screen.HOME -> {
                HomeScreen(
                    backdrop = backdrop,
                    liquidEnabled = liquidEnabled,
                    onLiquidEnabledChange = { liquidEnabled = it },
                    masterValue = masterValue,
                    onMasterValueChange = { masterValue = it },
                    onApp = { selectedApp = it; screen = Screen.APP },
                    scrollState = scrollState,
                )
            }
            Screen.APP -> {
                AppScreen(
                    appName = selectedApp,
                    backdrop = backdrop,
                    liquidEnabled = liquidEnabled,
                    blur = currentAppSettings().blur,
                    onBlurChange = { v -> updateCurrentApp { it.copy(blur = v) } },
                    refractionHeight = currentAppSettings().refractionHeight,
                    onRefractionHeightChange = { v -> updateCurrentApp { it.copy(refractionHeight = v) } },
                    refractionAmount = currentAppSettings().refractionAmount,
                    onRefractionAmountChange = { v -> updateCurrentApp { it.copy(refractionAmount = v) } },
                    highlight = currentAppSettings().highlight,
                    onHighlightChange = { v -> updateCurrentApp { it.copy(highlight = v) } },
                    surfaceAlpha = currentAppSettings().surfaceAlpha,
                    onSurfaceAlphaChange = { v -> updateCurrentApp { it.copy(surfaceAlpha = v) } },
                    chromatic = currentAppSettings().chromaticAberration,
                    onChromaticChange = { v -> updateCurrentApp { it.copy(chromaticAberration = v) } },
                    onBack = { screen = Screen.HOME },
                    onMore = { screen = Screen.GLASS },
                    scrollState = scrollState,
                )
            }
            Screen.GLASS -> GlassScreen(
                backdrop = backdrop,
                liquidEnabled = liquidEnabled,
                blur = currentAppSettings().blur,
                onBlurChange = { v -> updateCurrentApp { it.copy(blur = v) } },
                refractionHeight = currentAppSettings().refractionHeight,
                onRefractionHeightChange = { v -> updateCurrentApp { it.copy(refractionHeight = v) } },
                refractionAmount = currentAppSettings().refractionAmount,
                onRefractionAmountChange = { v -> updateCurrentApp { it.copy(refractionAmount = v) } },
                highlight = currentAppSettings().highlight,
                onHighlightChange = { v -> updateCurrentApp { it.copy(highlight = v) } },
                surfaceAlpha = currentAppSettings().surfaceAlpha,
                onSurfaceAlphaChange = { v -> updateCurrentApp { it.copy(surfaceAlpha = v) } },
                chromatic = currentAppSettings().chromaticAberration,
                onChromaticChange = { v -> updateCurrentApp { it.copy(chromaticAberration = v) } },
                onBack = { screen = Screen.HOME },
                scrollState = scrollState,
            )
            Screen.MOTION -> MotionScreen(
                backdrop = backdrop,
                animationSpeed = animationSpeed,
                onAnimationSpeedChange = { animationSpeed = it },
                containerBulge = containerBulge,
                onContainerBulgeChange = { containerBulge = it },
                contentBulge = contentBulge,
                onContentBulgeChange = { contentBulge = it },
                contentPopX = contentPopX,
                onContentPopXChange = { contentPopX = it },
                contentPopY = contentPopY,
                onContentPopYChange = { contentPopY = it },
                dragTension = dragTension,
                onDragTensionChange = { dragTension = it },
                motionBlur = motionBlur,
                onMotionBlurChange = { motionBlur = it },
                arcOffset = arcOffset,
                onArcOffsetChange = { arcOffset = it },
                onBack = { screen = Screen.HOME },
                scrollState = scrollState,
            )
        }

        ExpandableGlassMenu(
            animatableProgress = menuProgress,
            animationPreset = menuPreset,
            animationSpeedMultiplier = animationSpeed,
            containerBulgeMultiplier = containerBulge,
            contentBulgeMultiplier = contentBulge,
            contentPopX = contentPopX,
            contentPopY = contentPopY,
            dragJellyTension = dragTension,
            motionBlurAmount = motionBlur,
            arcYOffsetDp = arcOffset,
            alignment = MenuAlignment.TopTrailing,
            backdrop = backdrop,
            isGlassEnabled = liquidEnabled,
            isHapticsEnabled = menuHaptics,
            cornerRadius = 30.dp,
            blurRadius = currentAppSettings().blur,
            refractionHeight = 16f,
            refractionAmount = 20f,
            chromaticAberration = currentAppSettings().chromaticAberration,
            horizontalOffset = 0f,
            verticalOffset = 30f,
            modifier = Modifier.statusBarsPadding().padding(top = 8.dp, end = 14.dp),
            label = { BasicText("⋯", style = TextStyle(Color.White, 24.sp, FontWeight.Bold)) },
        ) { touch, closeMenu, hovered, setHovered ->
            MorphMenuRow("Glass", "Global glass controls", touch, hovered == 0, { setHovered(0) }) {
                closeMenu(); screen = Screen.GLASS
            }
            MorphMenuRow("Motion", "Bouncy animation physics", touch, hovered == 1, { setHovered(1) }) {
                closeMenu(); screen = Screen.MOTION
            }
            MorphMenuRow("Haptics", if (menuHaptics) "On" else "Off", touch, hovered == 2, { setHovered(2) }) {
                menuHaptics = !menuHaptics
            }
        }
    }
}

@Composable
private fun HomeScreen(
    backdrop: Backdrop,
    liquidEnabled: Boolean,
    onLiquidEnabledChange: (Boolean) -> Unit,
    masterValue: Float,
    onMasterValueChange: (Float) -> Unit,
    onApp: (String) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(38.dp))
        BasicText("HyperBounce", style = TextStyle(Color.White, 34.sp, FontWeight.Bold))
        BasicText("Liquid Glass control center", style = TextStyle(Color.White.copy(.62f), 16.sp))
        GlassCard(backdrop) {
            SettingRow(
                title = "Liquid Effects",
                subtitle = if (liquidEnabled) "Live refraction, vibrancy and highlights" else "Translucent mode",
                value = liquidEnabled,
                onValueChange = onLiquidEnabledChange,
                backdrop = backdrop,
            )
        }
        GlassCard(backdrop) {
            BasicText("Master Glass Intensity", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))
            LiquidSlider(
                value = { masterValue },
                onValueChange = onMasterValueChange,
                valueRange = 0f..1f,
                visibilityThreshold = .001f,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            BasicText("${(masterValue * 100).toInt()}%", style = TextStyle(Color.White.copy(.55f), 12.sp))
        }
        BasicText("APPS", style = TextStyle(Color.White.copy(.5f), 12.sp, FontWeight.Bold))
        AppCard("Instagram", "Floating navigation + backdrop", backdrop, onClick = { onApp("Instagram") })
        AppCard("YouTube", "Floating navigation + backdrop", backdrop, onClick = { onApp("YouTube") })
        AppCard("WhatsApp", "Floating navigation + backdrop", backdrop, onClick = { onApp("WhatsApp") })
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AppScreen(
    appName: String, backdrop: Backdrop, liquidEnabled: Boolean,
    blur: Float, onBlurChange: (Float) -> Unit,
    refractionHeight: Float, onRefractionHeightChange: (Float) -> Unit,
    refractionAmount: Float, onRefractionAmountChange: (Float) -> Unit,
    highlight: Float, onHighlightChange: (Float) -> Unit,
    surfaceAlpha: Float, onSurfaceAlphaChange: (Float) -> Unit,
    chromatic: Boolean, onChromaticChange: (Boolean) -> Unit,
    onBack: () -> Unit, onMore: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FloatingBackButton(backdrop, onBack, Modifier.padding(start = 16.dp, top = 12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText(appName, style = TextStyle(Color.White, 30.sp, FontWeight.Bold))
            BasicText("Per-app controls. Changes here are intentionally isolated from the other apps.", style = TextStyle(Color.White.copy(.58f), 14.sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlStylePicker("Toggle", backdrop, Modifier.weight(1f))
                ControlStylePicker("Slider", backdrop, Modifier.weight(1f))
            }
            GlassCard(backdrop) {
                SettingRow("Liquid effects", if (liquidEnabled) "Enabled globally" else "Translucent globally", liquidEnabled, {}, backdrop, enabled = false)
                Spacer(Modifier.height(18.dp))
                SliderSetting("Blur radius", blur, 0f..32f, onBlurChange, backdrop)
                SliderSetting("Refraction height", refractionHeight, 0f..48f, onRefractionHeightChange, backdrop)
                SliderSetting("Refraction amount", refractionAmount, 0f..64f, onRefractionAmountChange, backdrop)
                SliderSetting("Highlight", highlight, 0f..1f, onHighlightChange, backdrop)
                SliderSetting("Surface alpha", surfaceAlpha, 0f..1f, onSurfaceAlphaChange, backdrop)
                BooleanSetting("Chromatic aberration", chromatic, onChromaticChange, backdrop)
            }
            AppCard("Toggle style", "Phase 1 preview • backend selection comes in phase 2", backdrop, onClick = {})
            AppCard("Slider style", "Phase 1 preview • backend selection comes in phase 2", backdrop, onClick = {})
            AppCard("Advanced effects", "Open shared glass controls", backdrop, onClick = onMore)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun GlassScreen(
    backdrop: Backdrop, liquidEnabled: Boolean,
    blur: Float, onBlurChange: (Float) -> Unit,
    refractionHeight: Float, onRefractionHeightChange: (Float) -> Unit,
    refractionAmount: Float, onRefractionAmountChange: (Float) -> Unit,
    highlight: Float, onHighlightChange: (Float) -> Unit,
    surfaceAlpha: Float, onSurfaceAlphaChange: (Float) -> Unit,
    chromatic: Boolean, onChromaticChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FloatingBackButton(backdrop, onBack, Modifier.padding(start = 16.dp, top = 12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText("Glass", style = TextStyle(Color.White, 30.sp, FontWeight.Bold))
            BasicText("These are the visual properties exposed by Kyant's backdrop engine and used throughout the control UI.", style = TextStyle(Color.White.copy(.58f), 14.sp))
            GlassCard(backdrop) {
                BooleanSetting("Master liquid effects", liquidEnabled, {}, backdrop, enabled = false)
                Spacer(Modifier.height(8.dp))
                SliderSetting("Blur radius", blur, 0f..32f, onBlurChange, backdrop)
                SliderSetting("Refraction height", refractionHeight, 0f..48f, onRefractionHeightChange, backdrop)
                SliderSetting("Refraction amount", refractionAmount, 0f..64f, onRefractionAmountChange, backdrop)
                SliderSetting("Highlight", highlight, 0f..1f, onHighlightChange, backdrop)
                SliderSetting("Surface alpha", surfaceAlpha, 0f..1f, onSurfaceAlphaChange, backdrop)
                BooleanSetting("Chromatic aberration", chromatic, onChromaticChange, backdrop)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MotionScreen(
    backdrop: Backdrop,
    animationSpeed: Float, onAnimationSpeedChange: (Float) -> Unit,
    containerBulge: Float, onContainerBulgeChange: (Float) -> Unit,
    contentBulge: Float, onContentBulgeChange: (Float) -> Unit,
    contentPopX: Float, onContentPopXChange: (Float) -> Unit,
    contentPopY: Float, onContentPopYChange: (Float) -> Unit,
    dragTension: Float, onDragTensionChange: (Float) -> Unit,
    motionBlur: Float, onMotionBlurChange: (Float) -> Unit,
    arcOffset: Float, onArcOffsetChange: (Float) -> Unit,
    onBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FloatingBackButton(backdrop, onBack, Modifier.padding(start = 16.dp, top = 12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText("Motion", style = TextStyle(Color.White, 30.sp, FontWeight.Bold))
            BasicText("The exact tuning knobs from the library's bouncy morph dropdown implementation.", style = TextStyle(Color.White.copy(.58f), 14.sp))
            GlassCard(backdrop) {
                SliderSetting("Animation speed", animationSpeed, .1f..4f, onAnimationSpeedChange, backdrop)
                SliderSetting("Container bulge", containerBulge, 0f..1f, onContainerBulgeChange, backdrop)
                SliderSetting("Inner content bulge", contentBulge, 0f..1f, onContentBulgeChange, backdrop)
                SliderSetting("Content pop X", contentPopX, 0f..0.2f, onContentPopXChange, backdrop)
                SliderSetting("Content pop Y", contentPopY, 0f..0.2f, onContentPopYChange, backdrop)
                SliderSetting("Drag jelly tension", dragTension, .01f..0.2f, onDragTensionChange, backdrop)
                SliderSetting("Motion blur peak", motionBlur, 0f..80f, onMotionBlurChange, backdrop)
                SliderSetting("Arc Y limit", arcOffset, 0f..250f, onArcOffsetChange, backdrop)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AppCard(name: String, subtitle: String, backdrop: Backdrop, onClick: () -> Unit) {
    GlassCard(backdrop, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.12f)), contentAlignment = Alignment.Center) {
                BasicText(name.first().toString(), style = TextStyle(Color.White, 18.sp, FontWeight.Bold))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(name, style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
                BasicText(subtitle, style = TextStyle(Color.White.copy(.55f), 12.sp))
            }
            BasicText("›", style = TextStyle(Color.White.copy(.5f), 28.sp))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: Boolean, onValueChange: (Boolean) -> Unit, backdrop: Backdrop, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(title, style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
            BasicText(subtitle, style = TextStyle(Color.White.copy(.55f), 12.sp))
        }
        LiquidToggle(selected = { value }, onSelect = onValueChange, backdrop = backdrop, modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun BooleanSetting(title: String, value: Boolean, onValueChange: (Boolean) -> Unit, backdrop: Backdrop, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, modifier = Modifier.weight(1f), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
        LiquidToggle(selected = { value }, onSelect = onValueChange, backdrop = backdrop, modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun SliderSetting(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, backdrop: Backdrop) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(title, style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
            BasicText(formatNumber(value), style = TextStyle(Color.White.copy(.50f), 12.sp))
        }
        LiquidSlider(value = { value }, onValueChange = onValueChange, valueRange = range, visibilityThreshold = .001f, backdrop = backdrop)
    }
}

@Composable
private fun ControlStylePicker(label: String, backdrop: Backdrop, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf("Liquid") }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    Box(modifier.height(58.dp)) {
        ExpandableGlassMenu(
            animatableProgress = progress,
            animationPreset = MenuAnimationPreset.Bouncy,
            animationSpeedMultiplier = 1f,
            containerBulgeMultiplier = .395f,
            contentBulgeMultiplier = .15f,
            contentPopX = .03f,
            contentPopY = .01f,
            dragJellyTension = .05f,
            motionBlurAmount = 25f,
            arcYOffsetDp = 75f,
            alignment = MenuAlignment.TopLeading,
            backdrop = backdrop,
            isGlassEnabled = true,
            isHapticsEnabled = true,
            cornerRadius = 26.dp,
            blurRadius = 10f,
            refractionHeight = 16f,
            refractionAmount = 20f,
            chromaticAberration = false,
            horizontalOffset = 0f,
            verticalOffset = 30f,
            modifier = Modifier.fillMaxSize(),
            labelSize = androidx.compose.ui.geometry.Size(110f, 50f),
            label = { BasicText("$label  ·  $selected", style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold)) },
        ) { _, close, hovered, setHovered ->
            MorphMenuRow("Liquid", "Kyant library implementation", androidx.compose.ui.geometry.Offset.Unspecified, hovered == 0, { setHovered(0) }) { selected = "Liquid"; close() }
            MorphMenuRow("Classic", "Alternate implementation", androidx.compose.ui.geometry.Offset.Unspecified, hovered == 1, { setHovered(1) }) { selected = "Classic"; close() }
        }
    }
}

private fun formatNumber(value: Float): String = if (kotlin.math.abs(value) >= 1f) String.format("%.1f", value) else String.format("%.3f", value)

@Composable
private fun GlassCard(backdrop: Backdrop, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
    Box(
        modifier = modifier.pointerInput(onClick) {
            if (onClick != null) detectTapGestures(onTap = { onClick() })
        }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(18f.dp.toPx(), 18f.dp.toPx(), depthEffect = true)
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
            )
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun FloatingBackButton(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(52.dp),
        content = { BasicText("‹", style = TextStyle(Color.White, 32.sp, FontWeight.Light)) }
    )
}

@Composable
private fun MorphMenuRow(title: String, subtitle: String, globalTouch: androidx.compose.ui.geometry.Offset, hovered: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val alpha = if (hovered) .14f else .05f
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha)).pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(title, style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
        BasicText(subtitle, style = TextStyle(Color.White.copy(.60f), 11.sp))
    }
}

@Composable
private fun rememberAppBackdrop(): Backdrop = rememberCanvasBackdrop {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF07111F), Color(0xFF23123A), Color(0xFF071C28)),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            tileMode = TileMode.Clamp,
        )
    )
}

@Composable
private fun AppBackdropBackground() {
    Canvas(Modifier.fillMaxSize()) { drawBackground() }
}

private fun DrawScope.drawBackground() {
    drawCircle(Color(0xFF2F8CFF).copy(.18f), radius = size.minDimension * .55f, center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .20f))
    drawCircle(Color(0xFF9A4DFF).copy(.16f), radius = size.minDimension * .50f, center = androidx.compose.ui.geometry.Offset(size.width * .86f, size.height * .42f))
}

@Composable
private fun AppContentBackdrop(backdrop: Backdrop, liquidEnabled: Boolean, modifier: Modifier) {
    // The glass UI itself is driven by the library components; this layer exists only to
    // provide a stable backdrop source behind those components.
    Box(modifier)
}
