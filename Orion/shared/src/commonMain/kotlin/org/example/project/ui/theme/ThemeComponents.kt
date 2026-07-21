package org.example.project.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FuturisticBackground(content: @Composable BoxScope.() -> Unit) {
    val radialGradient = Brush.radialGradient(
        colors = listOf(
            DeepBlue.copy(alpha = 0.2f),
            DeepBlack
        ),
        radius = 1500f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .background(radialGradient),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF141414).copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = NeonBlue.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            ),
        content = content
    )
}
