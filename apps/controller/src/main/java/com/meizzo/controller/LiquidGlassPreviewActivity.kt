package com.meizzo.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

class LiquidGlassPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { LiquidGlassPreview() }
    }
}

@Composable
private fun LiquidGlassPreview() {
    val backdrop = rememberLayerBackdrop()
    val ink = Color(0xFF101014)
    val muted = Color(0xFF777780)
    val accent = Color(0xFFB8B8C2)

    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF34343B), Color(0xFF111116), Color(0xFF292932))
                        )
                    )
            ) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 20.dp).size(190.dp).clip(CircleShape).background(Color(0xFF777783).copy(alpha = 0.42f)))
                Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 120.dp).size(230.dp).clip(CircleShape).background(Color(0xFF555560).copy(alpha = 0.38f)))
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(36.dp))
                Text("MEIZZO", color = Color.White, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                Text("Your devices.\nOne place.", color = Color.White, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
                Text("A calmer way to stay connected.", color = Color(0xFFB8B8C2), fontSize = 14.sp)
                GlassPanel(backdrop) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF8FD4A5)))
                        Column(Modifier.weight(1f)) {
                            Text("CONNECTION", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("All systems operational", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("•••", color = muted, fontSize = 18.sp)
                    }
                }
                Text("MY DEVICES", color = Color(0xFFD1D1D8), fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                GlassPanel(backdrop) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8E8EC)), contentAlignment = Alignment.Center) {
                            Text("M", color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("My Android", color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Connected · Last seen now", color = muted, fontSize = 12.sp)
                        }
                        Text("›", color = muted, fontSize = 26.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = { blur(18.dp.toPx()); lens(14.dp.toPx(), 24.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.20f)) }
                        )
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⌂  Home", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Devices", color = Color(0xFFD5D5DC), fontSize = 13.sp)
                    Text("Settings", color = Color(0xFFD5D5DC), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(backdrop: com.kyant.backdrop.Backdrop, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = { blur(20.dp.toPx()); lens(16.dp.toPx(), 28.dp.toPx()) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.58f)) }
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}
