package com.meizzo.controller

import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import com.meizzo.glass.LiquidGlassBackdrop
import com.meizzo.glass.GlassDialogOverlay
import kotlin.math.roundToInt

@Composable
private fun BrandHeader(foreground: Color) {
    BasicText(
        "MEIZZO",
        style = TextStyle(foreground, 24.sp, FontWeight.Bold, letterSpacing = 4.sp, fontFamily = FontFamily.SansSerif)
    )
}

@Composable
fun ControllerGlassScreen(
    darkMode: Boolean,
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    onPair: (String) -> Unit,
    connectionView: TextView,
    devices: List<ControllerDeviceUi>,
    onDeviceAction: (String, String) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    alertSettings: AutoAlertManager.Settings,
    onSaveAutoAlertSettings: (AutoAlertManager.Settings) -> Unit,
    updateStatus: String,
    onCheckForUpdates: () -> Unit,
    dialog: ControllerGlassDialogRequest?,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit
) {
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var batteryThreshold by rememberSaveable(alertSettings.batteryPercent) { mutableIntStateOf(alertSettings.batteryPercent) }
    var temperatureThreshold by rememberSaveable(alertSettings.temperatureC) { mutableFloatStateOf(alertSettings.temperatureC) }
    var offlineMinutes by rememberSaveable(alertSettings.offlineMinutes) { mutableIntStateOf(alertSettings.offlineMinutes) }
    var chargerUnplugged by rememberSaveable(alertSettings.chargerUnplugged) { mutableStateOf(alertSettings.chargerUnplugged) }
    val foreground = if (darkMode) Color.White else Color(0xFF17171C)
    val secondary = if (darkMode) Color(0xFFB5B5C0) else Color(0xFF74747E)

    LiquidGlassBackdrop(darkMode) { backdrop ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.Start) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 48.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (selectedTab()) {
                        0 -> {
                            BrandHeader(foreground)
                            BasicText("Connect a device", style = TextStyle(foreground, 32.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                            BasicText("Enter the six-digit code shown on your Client.", style = TextStyle(secondary, 15.sp, fontFamily = FontFamily.SansSerif, lineHeight = 21.sp))
                            Column(
                                Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 28.dp).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                BasicText("PAIRING CODE", style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 1.8.sp))
                                BasicTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                    singleLine = true,
                                    textStyle = TextStyle(foreground, 28.sp, FontWeight.Medium, letterSpacing = 8.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    visualTransformation = VisualTransformation { input ->
                                        val visible = input.text.padEnd(6, '0')
                                        TransformedText(
                                            AnnotatedString(visible),
                                            object : OffsetMapping {
                                                override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, 6)
                                                override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, input.text.length)
                                            }
                                        )
                                    },
                                    decorationBox = { inner ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            inner()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                                LiquidButton(
                                    onClick = { onPair(pairingCode) },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                    tint = Color(0xFF007AFF)
                                ) {
                                    BasicText("Connect device", style = TextStyle(Color.Wh                        1 -> {
                            val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId }
                            if (selectedDeviceId == null || selectedDevice == null) {
                                BrandHeader(foreground)
                                BasicText("Your devices", style = TextStyle(foreground, 32.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                                BasicText("Manage paired devices and start secure sessions.", style = TextStyle(secondary, 15.sp, fontFamily = FontFamily.SansSerif, lineHeight = 21.sp))
                                if (devices.isEmpty()) {
                                    Column(Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 24.dp).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BasicText("No paired devices", style = TextStyle(foreground, 18.sp, FontWeight.SemiBold))
                                        BasicText("Enter a Client's six-digit pairing code in the Link tab to get started.", style = TextStyle(secondary, 14.sp, lineHeight = 20.sp))
                                    }
                                }
                                devices.forEach { device ->
                                    Column(Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 22.dp).clickable { selectedDeviceId = device.id }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                BasicText(device.name, style = TextStyle(foreground, 19.sp, FontWeight.SemiBold))
                                                BasicText((if (device.online) "Online" else "Offline") + " · " + device.platform, style = TextStyle(secondary, 12.sp, FontWeight.Medium))
                                            }
                                            BasicText("›", style = TextStyle(secondary, 28.sp, FontWeight.Light))
                                        }
                                        if (device.id == devices.firstOrNull()?.id) AndroidView(factory = { connectionView }, modifier = Modifier.fillMaxWidth())
                                        BasicText(device.battery + " battery · " + device.network, style = TextStyle(secondary, 13.sp))
                                    }
                                }
                            } else {
                                LiquidButton(onClick = { selectedDeviceId = null }, backdrop = backdrop, modifier = Modifier.width(116.dp).height(40.dp), surfaceColor = if (darkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)) {
                                    BasicText("‹ Devices", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                                }
                                BrandHeader(foreground)
                                BasicText(selectedDevice.name, style = TextStyle(foreground, 30.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                                BasicText((if (selectedDevice.online) "Online" else "Offline") + " · " + selectedDevice.platform, style = TextStyle(secondary, 13.sp, FontWeight.Medium))
                                Column(Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BasicText(selectedDevice.battery + " battery · " + selectedDevice.network, style = TextStyle(secondary, 13.sp))
                                }
                                GlassActionButton("Rename device", backdrop, darkMode, Modifier.fillMaxWidth()) {
                                    onDeviceAction(selectedDevice.id, "rename")
                                }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassActionButton(
                                            if (selectedDevice.mediaKind == "screen") "Open screen" else "Screen",
                                            backdrop, darkMode, Modifier.weight(1f),
                                            selectedDevice.online && (!selectedDevice.mediaActive || selectedDevice.mediaKind == "screen")
                                        ) { onDeviceAction(selectedDevice.id, "screen") }
                                        GlassActionButton(
                                            if (selectedDevice.mediaKind == "camera") "Open camera" else "Camera",
                                            backdrop, darkMode, Modifier.weight(1f),
                                            selectedDevice.online && (!selectedDevice.mediaActive || selectedDevice.mediaKind == "camera")
                                        ) { onDeviceAction(selectedDevice.id, "camera") }
                                        GlassActionButton(
                                            if (selectedDevice.mediaKind == "files") "Open files" else "Files",
                                            backdrop, darkMode, Modifier.weight(1f),
                                            selectedDevice.online && (!selectedDevice.mediaActive || selectedDevice.mediaKind == "files")
                                        ) { onDeviceAction(selectedDevice.id, "files") }
                                    }
                                    GlassActionButton(
                                        if (selectedDevice.interactionActive) "End interaction access" else if (selectedDevice.interactionPending) "Waiting for approval" else "Request interaction access",
                                        backdrop,
                                        darkMode,
                                        Modifier.fillMaxWidth(),
                                        selectedDevice.online && !selectedDevice.interactionPending
                                    ) { onDeviceAction(selectedDevice.id, "interaction") }
                                    if (selectedDevice.interactionActive) {
                                        BasicText("REMOTE CONTROLS", style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 1.5.sp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Back", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "back") }
                                            GlassActionButton("Home", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "home") }
                                            GlassActionButton("Recents", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "recents") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Volume −", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "volume_down") }
                                            GlassActionButton("Mute", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "volume_mute") }
                                            GlassActionButton("Volume +", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "volume_up") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Torch on", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "flashlight_on") }
                                            GlassActionButton("Torch off", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "flashlight_off") }
                                            GlassActionButton(if (selectedDevice.alarmActive) "Alarm off" else "Alarm on", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "alarm_on") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Browse files", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "browse_files") }
                                            GlassActionButton("Read clipboard", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "clipboard_read") }
                                            GlassActionButton("Write clipboard", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "clipboard_write") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Emergency", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "emergency") }
                                            GlassActionButton("Send text", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "text") }
                                            GlassActionButton("My clipboard", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "controller_clipboard") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Notifications", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "notifications") }
                                            GlassActionButton("Quick settings", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "quick_settings") }
                                            GlassActionButton("Power menu", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "power_dialog") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Camera flip", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "camera_flip") }
                                            GlassActionButton("Lock screen", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "lock_screen") }
                                            if (selectedDevice.hasLocation) {
                                                GlassActionButton("Find device", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "location") }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Backspace", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "key_backspace") }
                                            GlassActionButton("Enter", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "key_enter") }
                                            GlassActionButton("Tab", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "key_tab") }
                                            GlassActionButton("Esc", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "key_escape") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GlassActionButton("Tap", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "tap") }
                                            GlassActionButton("Swipe", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "swipe") }
                                            GlassActionButton("New line", backdrop, darkMode, Modifier.weight(1f)) { onDeviceAction(selectedDevice.id, "new_line") }
                                        }
                                    }
                            }
                        }
                        else -> {
                            BrandHeader(foreground)
                            BasicText("Settings", style = TextStyle(foreground, 32.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                            Column(
                                Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 24.dp).padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        BasicText("Appearance", style = TextStyle(foreground, 17.sp, FontWeight.Medium))
                                        BasicText(if (darkMode) "Dark appearance" else "Light appearance", style = TextStyle(secondary, 13.sp))
                                    }
                                    LiquidToggle(selected = { darkMode }, onSelect = onDarkModeChanged, backdrop = backdrop)
                                }
                            }
                            BasicText("AUTO-ALERT THRESHOLDS", style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 1.7.sp))
                            BasicText("Low battery · $batteryThreshold%", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                            LiquidSlider(
                                value = { batteryThreshold.toFloat() },
                                onValueChange = { batteryThreshold = it.roundToInt().coerceIn(1, 100) },
                                valueRange = 1f..100f,
                                visibilityThreshold = 1f,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth()
                            )
                            BasicText("High temperature · ${"%.1f".format(temperatureThreshold)}°C", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                            LiquidSlider(
                                value = { temperatureThreshold },
                                onValueChange = { temperatureThreshold = it.coerceIn(25f, 80f) },
                                valueRange = 25f..80f,
                                visibilityThreshold = 0.5f,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth()
                            )
                            BasicText("Offline alert · $offlineMinutes minutes", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                            LiquidSlider(
                                value = { offlineMinutes.toFloat() },
                                onValueChange = { offlineMinutes = it.roundToInt().coerceIn(1, 1440) },
                                valueRange = 1f..1440f,
                                visibilityThreshold = 1f,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    BasicText("Charger disconnected", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                                    BasicText("Alert when external power is removed", style = TextStyle(secondary, 12.sp))
                                }
                                LiquidToggle(selected = { chargerUnplugged }, onSelect = { chargerUnplugged = it }, backdrop = backdrop)
                            }
                            LiquidButton(
                                onClick = {
                                    onSaveAutoAlertSettings(
                                        AutoAlertManager.Settings(batteryThreshold, temperatureThreshold, offlineMinutes, chargerUnplugged)
                                    )
                                },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                tint = Color(0xFF0A84FF)
                            ) {
                                BasicText("Save alert settings", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                            }
                            LiquidButton(
                                onClick = onCheckForUpdates,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                surfaceColor = if (darkMode) Color(0x332D2D36) else Color(0x77FFFFFF)
                            ) {
                                BasicText("Check for updates", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                            }
                            BasicText(updateStatus, style = TextStyle(secondary, 12.sp, lineHeight = 18.sp))
                            BasicText("MEIZZO Controller · Secure device management", style = TextStyle(secondary, 12.sp))
                        }
                    }
                }
                LiquidBottomTabs(
                    selectedTabIndex = selectedTab,
                    onTabSelected = onTabSelected,
                    backdrop = backdrop,
                    tabsCount = 3,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).navigationBarsPadding()
                ) {
                    listOf("Link" to "⌁", "Devices" to "▦", "Settings" to "⚙").forEachIndexed { index, item ->
                        LiquidBottomTab(onClick = { onTabSelected(index) }) {
                            BasicText(item.second, style = TextStyle(foreground, 19.sp, FontWeight.Medium))
                            BasicText(item.first, style = TextStyle(foreground, 10.sp, FontWeight.Medium))
                        }
                    }
                }
            }
            if (dialog != null) {
                GlassDialogOverlay(
                    backdrop = backdrop,
                    dark = darkMode,
                    title = dialog.title,
                    message = dialog.message,
                    confirmLabel = dialog.confirmLabel,
                    dismissLabel = dialog.dismissLabel,
                    onConfirm = onDialogConfirm,
                    onDismiss = onDialogDismiss,
                    inputValue = dialog.inputValue,
                    inputHint = dialog.inputHint,
                    onInputChange = dialog.onInputChanged
                )
            }
        }
    }
}

data class ControllerGlassDialogRequest(
    val id: String,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val inputValue: String? = null,
    val inputHint: String = "",
    val onInputChanged: (String) -> Unit = {}
)

data class ControllerDeviceUi(
    val id: String,
    val name: String,
    val online: Boolean,
    val platform: String,
    val battery: String,
    val network: String,
    val hasLocation: Boolean,
    val mediaActive: Boolean,
    val mediaKind: String?,
    val alarmActive: Boolean,
    val interactionActive: Boolean,
    val interactionPending: Boolean
)

@Composable
private fun GlassActionButton(
    label: String,
    backdrop: Backdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier,
        isInteractive = enabled,
        surfaceColor = if (enabled) {
            if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
        } else Color.Gray.copy(alpha = 0.08f)
    ) {
        BasicText(
            label,
            style = TextStyle(
                if (enabled) { if (dark) Color.White else Color(0xFF17171C) } else Color.Gray,
                12.sp,
                FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        )
    }
}

private fun Modifier.glassPanel(backdrop: Backdrop, dark: Boolean, radius: Dp): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(radius) },
        effects = {
            vibrancy()
            blur(14.dp.toPx())
        },
        highlight = { Highlight.Plain },
        onDrawSurface = {
            drawRect(if (dark) Color(0x6625252D) else Color(0x99FFFFFF))
        }
    )
