package com.meizzo.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.RoundedRectangle

private val Ink = Color(0xFF111116)
private val MutedInk = Color(0xFF777780)
private val Accent = Color(0xFF0A84FF)

val LocalLiquidGlassDarkMode = staticCompositionLocalOf { true }

@Composable
fun LiquidGlassBackdrop(
    dark: Boolean,
    content: @Composable BoxScope.(LayerBackdrop) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalLiquidGlassDarkMode provides dark) {
    // Record only the background as the backdrop source. Glass surfaces must
    // be siblings of this source, never descendants of the layer they sample.
    // Capturing the glass surfaces inside the source creates a RenderNode
    // feedback loop that can crash Android's RenderThread (SIGSEGV).
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(if (dark) Color(0xFF000000) else Color(0xFFF2F2F7))
        )
        content(backdrop)
    }
    }
}

@Composable
fun GlassDialogOverlay(
    backdrop: Backdrop,
    dark: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    inputValue: String? = null,
    inputHint: String = "",
    onInputChange: (String) -> Unit = {}
) {
    val text = if (dark) Color.White else Ink
    val secondary = if (dark) Color(0xFFB6B6C0) else MutedInk
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.48f else 0.24f)).padding(24.dp).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(36.dp) },
                effects = {
                    colorControls(
                        brightness = if (dark) 0f else 0.2f,
                        saturation = 1.5f
                    )
                    vibrancy()
                    blur(if (dark) 8.dp.toPx() else 16.dp.toPx())
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.22f)) },
                onDrawSurface = { drawRect(if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.60f)) }
            ).padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(title, style = TextStyle(text, 22.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
            BasicText(message, style = TextStyle(secondary, 15.sp, fontFamily = FontFamily.SansSerif, lineHeight = 21.sp))
            if (inputValue != null) {
                BasicTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(text, 15.sp, fontFamily = FontFamily.SansSerif),
                    modifier = Modifier.fillMaxWidth().background(
                        if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        RoundedRectangle(16.dp)
                    ).padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (inputValue.isBlank()) {
                                BasicText(inputHint, style = TextStyle(secondary.copy(alpha = 0.65f), 14.sp))
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f),
                    surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                ) {
                    BasicText(dismissLabel, style = TextStyle(text, 14.sp, FontWeight.Medium, fontFamily = FontFamily.SansSerif))
                }
                LiquidButton(
                    onClick = onConfirm,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f),
                    tint = Accent
                ) {
                    BasicText(confirmLabel, style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
                }
            }
        }
    }
}

