package com.meizzo.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

private val Ink = Color(0xFF111116)
private val MutedInk = Color(0xFF777780)
private val Paper = Color(0xFFF5F5F8)
private val DarkPaper = Color(0xFF101014)
private val Accent = Color(0xFF0A84FF)

@Composable
fun LiquidGlassBackdrop(
    dark: Boolean,
    content: @Composable BoxScope.(LayerBackdrop) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .background(
                Brush.linearGradient(
                    colors = if (dark) listOf(Color(0xFF08080C), Color(0xFF17171E), Color(0xFF09090D)) else listOf(Paper, Color(0xFFE9EBF2), Paper),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .drawBehind {
                drawCircle(
                    color = Accent.copy(alpha = if (dark) 0.13f else 0.10f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.92f, size.height * 0.12f)
                )
                drawCircle(
                    color = Color(0xFF8E8EFF).copy(alpha = if (dark) 0.08f else 0.07f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.02f, size.height * 0.72f)
                )
            }
    ) { content(backdrop) }
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
    onDismiss: () -> Unit
) {
    val text = if (dark) Color.White else Ink
    val secondary = if (dark) Color(0xFFB6B6C0) else MutedInk
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.48f else 0.24f)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(30.dp) },
                effects = {
                    vibrancy()
                    blur(18.dp.toPx())
                    lens(22.dp.toPx(), 34.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.22f)) },
                onDrawSurface = { drawRect(if (dark) Color(0xCC202027) else Color(0xCCFFFFFF)) }
            ).padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(title, style = TextStyle(text, 22.sp, FontWeight.SemiBold, fontFamily = FontFamily.SansSerif))
            BasicText(message, style = TextStyle(secondary, 15.sp, fontFamily = FontFamily.SansSerif, lineHeight = 21.sp))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassActionText(dismissLabel, dark, false, Modifier.weight(1f), onDismiss)
                GlassActionText(confirmLabel, dark, true, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun GlassActionText(label: String, dark: Boolean, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (primary) Color.White else if (dark) Color.White else Ink
    Box(
        modifier.clickable(onClick = onClick).height(48.dp).background(
            if (primary) Accent else if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
            RoundedCornerShape(24.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color, 15.sp, FontWeight.Medium, fontFamily = FontFamily.SansSerif))
    }
}
