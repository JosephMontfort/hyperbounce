package dev.joseph.hyperbounce.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.gesture.FocaltechNodeFinder
import dev.joseph.hyperbounce.gesture.ScreenOffGestureDaemonManager
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ScreenOffGestureScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gesturePrefs = repository.screenOffGesture

    var isRunning by remember { mutableStateOf(false) }
    var daemonPid by remember { mutableStateOf<Int?>(null) }
    var resolvedNode by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(true) }

    fun refreshStatus() {
        scope.launch(Dispatchers.IO) {
            val pid = ScreenOffGestureDaemonManager.getDaemonPid()
            val running = pid != null && pid > 0
            val node = FocaltechNodeFinder.findNode()
            withContext(Dispatchers.Main) {
                daemonPid = pid
                isRunning = running
                resolvedNode = node
                isChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    ScreenScaffold(
        title = "Screen-Off Gestures",
        subtitle = "LPWG hardware digitizer recognizer",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        // --- 1. Live Daemon Status Card ---
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) colors.accentGreen else colors.accentRed)
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicText(
                                text = if (isRunning) "DAEMON RUNNING (PID ${daemonPid ?: "?"})" else "DAEMON STOPPED",
                                style = HyperBounceType.headline.copy(
                                    color = if (isRunning) colors.accentGreen else colors.accentRed,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        BasicText(
                            text = "Refresh",
                            style = HyperBounceType.subhead.copy(
                                color = colors.accentBlue,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.clickable { refreshStatus() }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        text = "Hardware Node: $resolvedNode (focaltech_ts)",
                        style = HyperBounceType.caption.copy(
                            color = colors.secondaryLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = if (isRunning) {
                            "The root daemon is actively listening on the FocalTech digitizer stream with blocking POSIX I/O. Zero CPU wake-locks are held during sleep, allowing the SoC to remain in deep C-states."
                        } else {
                            "Screen-off gestures are currently dormant. Tap 'Start Daemon' to run the background root process."
                        },
                        style = HyperBounceType.subhead.copy(
                            color = colors.secondaryLabel,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isRunning) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accentGreen.copy(alpha = 0.85f))
                                    .clickable {
                                        ScreenOffGestureDaemonManager.startDaemon(
                                            context,
                                            gesturePrefs.confidenceThreshold
                                        ) { success ->
                                            scope.launch(Dispatchers.Main) {
                                                refreshStatus()
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Daemon started successfully" else "Failed to start daemon (Root required)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = "Start Daemon",
                                    style = HyperBounceType.headline.copy(color = Color.White, fontSize = 13.sp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accentRed.copy(alpha = 0.85f))
                                    .clickable {
                                        ScreenOffGestureDaemonManager.stopDaemon {
                                            scope.launch(Dispatchers.Main) {
                                                refreshStatus()
                                                Toast.makeText(context, "Daemon stopped", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = "Stop Daemon",
                                    style = HyperBounceType.headline.copy(color = Color.White, fontSize = 13.sp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accentBlue.copy(alpha = 0.85f))
                                    .clickable {
                                        ScreenOffGestureDaemonManager.restartDaemon(
                                            context,
                                            gesturePrefs.confidenceThreshold
                                        ) { success ->
                                            scope.launch(Dispatchers.Main) {
                                                refreshStatus()
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Daemon restarted" else "Failed to restart",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = "Restart",
                                    style = HyperBounceType.headline.copy(color = Color.White, fontSize = 13.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Pocket Prevention Architecture Info ---
        item {
            SectionHeader("Pocket Touch Prevention", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        text = "Hardware Proximity Bypass Architecture",
                        style = HyperBounceType.headline.copy(color = colors.label, fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = "Xiaomi and POCO devices use virtual ultrasound proximity software that powers off during deep sleep. HyperBounce replaces hardware sensor polling with a 5-layer touch-geometry filter:\n\n" +
                                "• Contact Area (touch_major > 75 rejected)\n" +
                                "• Multi-touch slot count (simultaneous fabric touches rejected)\n" +
                                "• Duration bounds (180ms - 2600ms required)\n" +
                                "• Spatial bounds (micro-jitters & bezel sweeps rejected)\n" +
                                "• $1 Unistroke math (>80% shape confidence required)",
                        style = HyperBounceType.subhead.copy(
                            color = colors.secondaryLabel,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        // --- 3. Gesture Actions & Calibration ---
        item {
            SectionHeader("Gesture Mappings", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Screen-Off Gestures",
                    subtitle = if (gesturePrefs.daemonEnabled) "Active when device screen is turned off" else "Disabled",
                    colors = colors,
                    iconTint = colors.accentGreen.copy(alpha = 0.85f),
                    backdrop = backdrop,
                    checked = gesturePrefs.daemonEnabled,
                    onCheckedChange = gesturePrefs::setDaemonEnabledPref,
                    icon = { PowerIcon(color = Color.White, size = 15.dp, strokeWidth = 1.8.dp) }
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Confidence Threshold",
                    subtitle = "Minimum mathematical $1 Unistroke match",
                    colors = colors,
                    backdrop = backdrop,
                    value = gesturePrefs.confidenceThreshold,
                    onValueChange = gesturePrefs::setConfidenceThresholdPref,
                    valueRange = 0.30f..0.90f,
                    defaultValue = 0.50f,
                    valueLabel = { "${(it * 100).roundToInt()}%" },
                    enabled = gesturePrefs.daemonEnabled
                )
            }
        }

        item {
            SectionHeader("Unistroke Actions", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText(
                                text = "Gesture 'O' (Circle Loop)",
                                style = HyperBounceType.headline.copy(color = colors.label, fontSize = 14.sp)
                            )
                            BasicText(
                                text = "Action: Wake Screen (KEYCODE_WAKEUP)",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel, fontSize = 12.sp)
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentBlue.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            BasicText(
                                text = "Wake Screen",
                                style = HyperBounceType.caption.copy(color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    RowDivider(colors.separator)
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText(
                                text = "Gesture 'V' (Single Apex)",
                                style = HyperBounceType.headline.copy(color = colors.label, fontSize = 14.sp)
                            )
                            BasicText(
                                text = "Action: Toggle Flashlight",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel, fontSize = 12.sp)
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            BasicText(
                                text = "Flashlight",
                                style = HyperBounceType.caption.copy(color = colors.accentOrange, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    RowDivider(colors.separator)
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            BasicText(
                                text = "Gesture 'E' (Cursive / GraffiTi)",
                                style = HyperBounceType.headline.copy(color = colors.label, fontSize = 14.sp)
                            )
                            BasicText(
                                text = "Action: Launch Camera",
                                style = HyperBounceType.caption.copy(color = colors.secondaryLabel, fontSize = 12.sp)
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentPurple.copy(alpha = 0.2f))
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            Runtime.getRuntime().exec(arrayOf("am", "broadcast", "-a", "dev.joseph.hyperbounce.GESTURE_DETECTED", "--es", "gesture", "E", "--ef", "confidence", "1.0"))
                                        } catch (_: Throwable) {}
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            BasicText(
                                text = "Test Camera",
                                style = HyperBounceType.caption.copy(color = colors.accentPurple, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            SectionHeader("Quick Hardware Test", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentOrange.copy(alpha = 0.2f))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        Runtime.getRuntime().exec(arrayOf("am", "broadcast", "-a", "dev.joseph.hyperbounce.GESTURE_DETECTED", "--es", "gesture", "V", "--ef", "confidence", "1.0"))
                                    } catch (_: Throwable) {}
                                }
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Test Torch (V)",
                            style = HyperBounceType.caption.copy(color = colors.accentOrange, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentBlue.copy(alpha = 0.2f))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        Runtime.getRuntime().exec(arrayOf("am", "broadcast", "-a", "dev.joseph.hyperbounce.GESTURE_DETECTED", "--es", "gesture", "O", "--ef", "confidence", "1.0"))
                                    } catch (_: Throwable) {}
                                }
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Test Wake (O)",
                            style = HyperBounceType.caption.copy(color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            SectionFooter(
                "Broadcast Intent: dev.joseph.hyperbounce.GESTURE_DETECTED (extras: gesture, confidence)",
                colors
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}
