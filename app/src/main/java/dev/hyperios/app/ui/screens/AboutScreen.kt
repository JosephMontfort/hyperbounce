package dev.hyperios.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.R
import dev.hyperios.app.safemode.SystemPropertiesHelper
import dev.hyperios.app.ui.components.ChevronIcon
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import dev.hyperios.app.ui.state.SettingsRepository
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    repository: SettingsRepository,
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
                SystemInfoSection(colors, contentBackdrop)
            }
            item {
                OpenSourceLicensesSection(colors, contentBackdrop)
            }
            item {
                DonationSection(colors, contentBackdrop)
            }
            item {
                Spacer(Modifier.height(36.dp))
            }
        }
    } else {
        RootScaffold(
            title = "About",
            colors = colors,
            backdrop = backdrop,
            repository = repository,
            showTopAction = false
        ) {
            AboutHeader(colors)
            DeveloperSection(colors, backdrop)
            SystemInfoSection(colors, backdrop)
            OpenSourceLicensesSection(colors, backdrop)
            DonationSection(colors, backdrop)
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

@Composable
private fun SystemInfoSection(colors: HyperBounceColors, backdrop: Backdrop) {
    val deviceModel = remember {
        val marketName = SystemPropertiesHelper.get("ro.product.marketname", "").trim()
        if (marketName.isNotEmpty()) {
            marketName
        } else {
            val model = SystemPropertiesHelper.get("ro.product.model", "").trim()
            if (model.isNotEmpty()) model else Build.MODEL
        }
    }

    val hyperOsVersion = remember {
        val miOs = SystemPropertiesHelper.get("ro.mi.os.version.name", "").trim()
        if (miOs.isNotEmpty()) {
            miOs
        } else {
            val miui = SystemPropertiesHelper.get("ro.miui.ui.version.name", "").trim()
            if (miui.isNotEmpty()) miui else "HyperOS"
        }
    }

    val androidVersion = remember {
        "Android ${Build.VERSION.RELEASE}"
    }

    SectionHeader("System Information", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        SystemInfoRow(label = "Device Model", value = deviceModel, colors = colors)
        RowDivider(colors.separator, insetStart = 16.dp)
        SystemInfoRow(label = "HyperOS Version", value = hyperOsVersion, colors = colors)
        RowDivider(colors.separator, insetStart = 16.dp)
        SystemInfoRow(label = "Android Version", value = androidVersion, colors = colors)
    }
}

@Composable
private fun SystemInfoRow(
    label: String,
    value: String,
    colors: HyperBounceColors
) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        BasicComponent(
            title = label,
            endActions = {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = label,
                style = HyperBounceType.headline.copy(
                    color = colors.label,
                    fontWeight = FontWeight.Normal
                )
            )
            BasicText(
                text = value,
                style = HyperBounceType.subhead.copy(
                    color = colors.secondaryLabel,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }
}

private data class LicenseItem(
    val name: String,
    val developer: String,
    val license: String,
    val url: String
)

@Composable
private fun OpenSourceLicensesSection(colors: HyperBounceColors, backdrop: Backdrop) {
    val licenses = remember {
        listOf(
            LicenseItem(
                name = "LibXposed API",
                developer = "JingMatrix",
                license = "Apache-2.0",
                url = "https://github.com/JingMatrix/libxposed-api"
            ),
            LicenseItem(
                name = "Android Liquid Glass",
                developer = "Kyant0",
                license = "Apache-2.0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass"
            ),
            LicenseItem(
                name = "Liquid Glass Android",
                developer = "QWEA0",
                license = "MIT",
                url = "https://github.com/QWEA0/Liquid-Glass-Android"
            ),
            LicenseItem(
                name = "miuix-kotlin-multiplatform",
                developer = "YuKongA",
                license = "Apache-2.0",
                url = "https://github.com/YuKongA/miuix-kotlin-multiplatform"
            ),
            LicenseItem(
                name = "Compose Multiplatform",
                developer = "JetBrains",
                license = "Apache-2.0",
                url = "https://github.com/JetBrains/compose-multiplatform"
            )
        )
    }

    SectionHeader("Open Source Licenses", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        licenses.forEachIndexed { index, item ->
            if (index > 0) {
                RowDivider(colors.separator, insetStart = 16.dp)
            }
            LicenseCardRow(item = item, colors = colors)
        }
    }
}

@Composable
private fun LicenseCardRow(
    item: LicenseItem,
    colors: HyperBounceColors
) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0.0f, label = "licensePress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.label.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    try {
                        uriHandler.openUri(item.url)
                    } catch (_: Exception) {}
                }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                item.name,
                style = HyperBounceType.body.copy(
                    color = colors.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                "${item.developer} • ${item.license}",
                style = HyperBounceType.footnote.copy(
                    color = colors.secondaryLabel,
                    fontSize = 13.sp
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

@Composable
private fun DonationSection(colors: HyperBounceColors, backdrop: Backdrop) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    SectionHeader("Support", colors)
    GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
        DonationButtonRow(
            label = "Donate",
            colors = colors,
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://donatr.ee/montfort_1607")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {}
                coroutineScope.launch {
                    delay(3000)
                    Toast.makeText(context, "Thanks for your support ❤️", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun DonationButtonRow(
    label: String,
    colors: HyperBounceColors,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0.0f, label = "donatePress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.label.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            label,
            style = HyperBounceType.body.copy(
                color = colors.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp
            )
        )
        ChevronIcon(
            color = colors.tertiaryLabel,
            size = 12.dp,
            strokeWidth = 2.dp,
            pointingLeft = false
        )
    }
}
