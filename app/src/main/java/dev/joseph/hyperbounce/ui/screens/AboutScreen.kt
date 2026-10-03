package dev.joseph.hyperbounce.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.components.ChevronIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun AboutScreen(
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        ScreenScaffold(
            title = "About",
            colors = colors,
            backdrop = backdrop,
            onBack = onBack
        ) { contentBackdrop ->
            item {
                AboutHeader(colors)
            }
            item {
                DeveloperSection(colors, contentBackdrop)
            }
            item {
                LibrariesSection(colors, contentBackdrop)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(56.dp))
            BasicText(
                "About",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = HyperBounceType.largeTitle.copy(color = colors.label)
            )
            Spacer(Modifier.height(8.dp))
            AboutHeader(colors)
            DeveloperSection(colors, backdrop)
            LibrariesSection(colors, backdrop)
            Spacer(Modifier.height(120.dp)) // Bottom tab clearance
        }
    }
}

@Composable
private fun AboutHeader(colors: HyperBounceColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val shape = RoundedCornerShape(22.dp)
        Image(
            painter = painterResource(R.drawable.ic_app_icon),
            contentDescription = "HyperiOS Icon",
            modifier = Modifier
                .size(92.dp)
                .shadow(16.dp, shape = shape, clip = false)
                .clip(shape)
        )
        Spacer(Modifier.height(14.dp))
        BasicText(
            "HyperiOS",
            style = HyperBounceType.title1.copy(
                color = colors.label,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            "HyperiOS brings iOS 26 inspired aesthetics to HyperOS users.",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = HyperBounceType.subhead.copy(
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            "Version 1.0.0",
            style = HyperBounceType.caption.copy(color = colors.tertiaryLabel)
        )
    }
}

@Composable
private fun DeveloperSection(colors: HyperBounceColors, backdrop: Backdrop) {
    val uriHandler = LocalUriHandler.current
    SectionHeader("Developer", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0.0f, label = "devPress")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.label.copy(alpha = bgAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        try {
                            uriHandler.openUri("https://t.me/montfort_1607")
                        } catch (_: Exception) {}
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accentBlue),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    "M",
                    style = HyperBounceType.headline.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Column(Modifier.weight(1f)) {
                BasicText(
                    "Montfort",
                    style = HyperBounceType.headline.copy(
                        color = colors.label,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    "@montfort_1607",
                    style = HyperBounceType.subhead.copy(
                        color = colors.accentBlue
                    )
                )
            }

            ChevronIcon(
                color = colors.tertiaryLabel,
                size = 12.dp,
                strokeWidth = 2.dp,
                pointingLeft = false
            )
        }
    }
}

private data class LibraryItem(
    val name: String,
    val description: String,
    val url: String
)

@Composable
private fun LibrariesSection(colors: HyperBounceColors, backdrop: Backdrop) {
    val libraries = remember {
        listOf(
            LibraryItem(
                name = "AndroidLiquidGlass (Backdrop)",
                description = "Real-time optics, blur, refraction, vibrancy and liquid dynamics engine by Kyant0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass"
            ),
            LibraryItem(
                name = "Shapes",
                description = "Modern smooth corners, capsules and squircle shapes for Compose by Kyant0",
                url = "https://github.com/Kyant0/shapes"
            ),
            LibraryItem(
                name = "LibXposed",
                description = "Modern, type-safe API 102 ART hooking framework and IPC service bridge interface",
                url = "https://github.com/libxposed/api"
            ),
            LibraryItem(
                name = "Jetpack Compose & Multiplatform",
                description = "Declarative reactive UI framework by AndroidX and JetBrains Compose Multiplatform",
                url = "https://github.com/JetBrains/compose-multiplatform"
            ),
            LibraryItem(
                name = "Miuix Iconography",
                description = "Native HyperOS system iconography and design language components",
                url = "https://github.com/MiCode/Miuix"
            )
        )
    }

    SectionHeader("Open Source Libraries", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        libraries.forEachIndexed { index, lib ->
            if (index > 0) {
                RowDivider(colors.separator, insetStart = 16.dp)
            }
            LibraryCardRow(
                name = lib.name,
                description = lib.description,
                url = lib.url,
                colors = colors
            )
        }
    }
}

@Composable
private fun LibraryCardRow(
    name: String,
    description: String,
    url: String,
    colors: HyperBounceColors
) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0.0f, label = "libPress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.label.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {}
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                name,
                style = HyperBounceType.headline.copy(
                    color = colors.label,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                description,
                style = HyperBounceType.footnote.copy(
                    color = colors.secondaryLabel,
                    lineHeight = 16.sp
                )
            )
        }
        ChevronIcon(
            color = colors.tertiaryLabel,
            size = 12.dp,
            strokeWidth = 2.dp,
            pointingLeft = false
        )
    }
}
