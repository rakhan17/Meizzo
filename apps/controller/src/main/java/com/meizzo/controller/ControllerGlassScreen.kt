package com.meizzo.controller

import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import com.meizzo.glass.LiquidGlassBackdrop

@Composable
fun ControllerGlassScreen(
    darkMode: Boolean,
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    onPair: (String) -> Unit,
    connectionView: TextView,
    managementView: LinearLayout,
    onDarkModeChanged: (Boolean) -> Unit,
    onAutoAlertSettings: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    var pairingCode by rememberSaveable { mutableStateOf("") }
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
                            BasicText("MEIZZO", style = TextStyle(foreground, 13.sp, FontWeight.Bold, letterSpacing = 3.sp))
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
                                    textStyle = TextStyle(foreground, 28.sp, FontWeight.Medium, letterSpacing = 8.sp, fontFamily = FontFamily.Monospace),
                                    decorationBox = { inner ->
                                        Box {
                                            if (pairingCode.isEmpty()) BasicText("000000", style = TextStyle(secondary.copy(alpha = 0.45f), 28.sp, letterSpacing = 8.sp, fontFamily = FontFamily.Monospace))
                                            inner()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                                LiquidButton(
                                    onClick = { onPair(pairingCode) },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                    tint = Color(0xFF0A84FF)
                                ) {
                                    BasicText("Connect device", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                                }
                            }
                        }
                        1 -> {
                            BasicText("MEIZZO", style = TextStyle(foreground, 13.sp, FontWeight.Bold, letterSpacing = 3.sp))
                            BasicText("Your devices", style = TextStyle(foreground, 32.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                            BasicText("Manage paired devices and start secure sessions.", style = TextStyle(secondary, 15.sp, fontFamily = FontFamily.SansSerif, lineHeight = 21.sp))
                            Column(
                                Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 22.dp).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BasicText("CONNECTION", style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 1.6.sp))
                                AndroidView(factory = { connectionView }, modifier = Modifier.fillMaxWidth())
                            }
                            AndroidView(factory = { managementView }, modifier = Modifier.fillMaxWidth().glassPanel(backdrop, darkMode, 24.dp).padding(14.dp))
                        }
                        else -> {
                            BasicText("MEIZZO", style = TextStyle(foreground, 13.sp, FontWeight.Bold, letterSpacing = 3.sp))
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
                            LiquidButton(
                                onClick = onAutoAlertSettings,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                surfaceColor = if (darkMode) Color(0x332D2D36) else Color(0x77FFFFFF)
                            ) {
                                BasicText("Auto-Alert settings", style = TextStyle(foreground, 14.sp, FontWeight.Medium))
                            }
                            LiquidButton(
                                onClick = onCheckForUpdates,
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                tint = Color(0xFF0A84FF)
                            ) {
                                BasicText("Check for updates", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                            }
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
        }
    }
}

private fun Modifier.glassPanel(backdrop: Backdrop, dark: Boolean, radius: Dp): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(radius) },
        effects = {
            vibrancy()
            blur(14.dp.toPx())
            lens(18.dp.toPx(), 28.dp.toPx())
        },
        highlight = { Highlight.Plain },
        onDrawSurface = {
            drawRect(if (dark) Color(0x6625252D) else Color(0x99FFFFFF))
        }
    )
