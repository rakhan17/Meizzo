package com.meizzo.client

import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import com.meizzo.glass.LiquidGlassBackdrop
import com.meizzo.glass.GlassDialogOverlay

@Composable
fun ClientGlassScreen(
    pairingCode: String,
    statusView: TextView,
    darkMode: Boolean,
    onCheckForUpdates: () -> Unit,
    dialog: ClientGlassDialogRequest?,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit
) {
    val foreground = if (darkMode) Color.White else Color(0xFF17171C)
    val secondary = if (darkMode) Color(0xFFB5B5C0) else Color(0xFF74747E)

    LiquidGlassBackdrop(darkMode) { backdrop ->
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                androidx.compose.foundation.text.BasicText(
                    "MEIZZO",
                    style = TextStyle(foreground, 13.sp, FontWeight.Bold, letterSpacing = 4.sp)
                )
                androidx.compose.foundation.text.BasicText(
                    "Your device.\nYour permission.",
                    style = TextStyle(foreground, 30.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, lineHeight = 36.sp)
                )
                Column(
                    Modifier.fillMaxWidth().clientGlassPanel(backdrop, darkMode, 32.dp).padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "YOUR PAIRING CODE",
                        style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 2.sp)
                    )
                    androidx.compose.foundation.text.BasicText(
                        pairingCode.chunked(3).joinToString(" "),
                        style = TextStyle(foreground, 42.sp, FontWeight.Medium, letterSpacing = 5.sp, fontFamily = FontFamily.Monospace)
                    )
                    androidx.compose.foundation.text.BasicText(
                        "Enter this code in MEIZZO Controller to request a connection.",
                        style = TextStyle(secondary, 14.sp, fontFamily = FontFamily.SansSerif, lineHeight = 20.sp)
                    )
                }
                Column(
                    Modifier.fillMaxWidth().clientGlassPanel(backdrop, darkMode, 24.dp).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "CONNECTION STATUS",
                        style = TextStyle(secondary, 11.sp, FontWeight.SemiBold, letterSpacing = 1.7.sp)
                    )
                    AndroidView(factory = { statusView }, modifier = Modifier.fillMaxWidth())
                }
                LiquidButton(
                    onClick = onCheckForUpdates,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = if (darkMode) Color(0x332D2D36) else Color(0x77FFFFFF)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        "Check for updates",
                        style = TextStyle(foreground, 14.sp, FontWeight.Medium)
                    )
                }
                androidx.compose.foundation.text.BasicText(
                    "PRIVATE BY DESIGN  ·  YOU CONTROL ACCESS",
                    style = TextStyle(secondary, 10.sp, FontWeight.Medium, letterSpacing = 1.1.sp)
                )
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
                    onDismiss = onDialogDismiss
                )
            }
        }
    }
}

data class ClientGlassDialogRequest(
    val id: String,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit
)

private fun Modifier.clientGlassPanel(backdrop: Backdrop, dark: Boolean, radius: Dp): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(radius) },
        effects = {
            vibrancy()
            blur(16.dp.toPx())
            lens(20.dp.toPx(), 30.dp.toPx())
        },
        highlight = { Highlight.Plain },
        onDrawSurface = {
            drawRect(if (dark) Color(0x6625252D) else Color(0x99FFFFFF))
        }
    )
