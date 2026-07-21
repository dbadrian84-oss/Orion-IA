package org.example.project.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

// Pseudo-Perlin noise using layered sine functions for Compose Multiplatform compatibility
private fun pseudoNoise(x: Float, time: Float, frequency: Float = 1f, phase: Float = 0f): Float {
    return (sin(x * frequency + time + phase) * 0.5f +
        sin(x * frequency * 2.3f + time * 1.5f + phase * 1.2f) * 0.3f +
        sin(x * frequency * 3.7f + time * 0.7f + phase * 0.8f) * 0.2f)
}

/**
 * Organic blob animation driven by [audioLevel]. At audioLevel=0f the blob is a calm, gently
 * pulsing circle. At audioLevel=1f the blob is maximally deformed and energetic.
 */
@Composable
fun BlobAnimator(
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    color: Color = Color(0xFF00E5FF),
    glowColor: Color = Color(0xFF005082),
) {
    // Internal time for animation
    var time by remember { mutableStateOf(0f) }

    // Animate time continuously
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos -> time = (nanos / 1_000_000_000f) }
        }
    }

    // Audio-meter style smoothing: fast attack (reacts instantly to peaks),
    // slower release (doesn't collapse between syllables). A single symmetric
    // spring can't do this — it re-targets on every new sample and never
    // catches up with fast, constantly-changing mic input.
    var smoothedAudioLevel by remember { mutableStateOf(0f) }

    LaunchedEffect(audioLevel) {
        val current = smoothedAudioLevel
        if (audioLevel > current) {
            // Attack: snap up almost instantly to rising peaks
            animate(
                initialValue = current,
                targetValue = audioLevel,
                animationSpec = tween(durationMillis = 40, easing = LinearEasing),
            ) { value, _ ->
                smoothedAudioLevel = value
            }
        } else {
            // Release: ease down slower so it doesn't look jittery between words
            animate(
                initialValue = current,
                targetValue = audioLevel,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) { value, _ ->
                smoothedAudioLevel = value
            }
        }
    }

    val animatedAudioLevel = smoothedAudioLevel

    // Gentle idle pulse
    val idlePulse by
        animateFloatAsState(
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse",
        )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseRadius = this.size.width / 2f * 0.75f

        // Deformation intensity: blend idle animation + audio response
        val deformAmount = baseRadius * (0.08f + animatedAudioLevel * 0.35f + (idlePulse * 0.02f))
        val numPoints = 8
        val angleStep = (2 * PI / numPoints).toFloat()

        // Generate blob control points with noise deformation
        val points =
            Array(numPoints) { i ->
                val angle = i * angleStep - PI.toFloat() / 2f
                val noiseVal =
                    pseudoNoise(
                        x = angle,
                        time = time * (0.6f + animatedAudioLevel * 1.5f),
                        frequency = 1.2f,
                        phase = i * 0.8f,
                    )
                val r = baseRadius + noiseVal * deformAmount
                Offset(cx + cos(angle) * r, cy + sin(angle) * r)
            }

        // Draw outer glow (multiple layers for bloom effect)
        repeat(5) { glowLayer ->
            val glowAlpha = 0.04f - glowLayer * 0.007f
            val glowExpand = (glowLayer + 1) * 6f
            drawPath(
                path = buildBlobPath(points, cx, cy, numPoints, glowExpand),
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                glowColor.copy(alpha = glowAlpha * (1f + animatedAudioLevel)),
                                Color.Transparent,
                            ),
                        center = Offset(cx, cy),
                        radius = baseRadius + deformAmount + glowExpand,
                    ),
            )
        }

        // Draw inner gradient fill
        drawPath(
            path = buildBlobPath(points, cx, cy, numPoints, 0f),
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            color.copy(alpha = 0.9f + animatedAudioLevel * 0.1f),
                            glowColor.copy(alpha = 0.7f),
                            Color(0xFF001428).copy(alpha = 0.9f),
                        ),
                    center = Offset(cx * 0.85f, cy * 0.75f),
                    radius = baseRadius * 1.3f,
                ),
        )

        // Draw crisp border stroke
        drawPath(
            path = buildBlobPath(points, cx, cy, numPoints, 0f),
            color = color.copy(alpha = 0.8f + animatedAudioLevel * 0.2f),
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5f + animatedAudioLevel * 3f
                ),
        )

        // Inner shimmer highlight
        drawPath(
            path = buildBlobPath(points, cx, cy, numPoints, -8f),
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.12f + animatedAudioLevel * 0.08f),
                            Color.Transparent,
                        ),
                    start = Offset(cx * 0.5f, cy * 0.3f),
                    end = Offset(cx * 1.2f, cy * 1.1f),
                ),
        )
    }
}

/**
 * Builds a closed smooth Path through the given control [points] using cubic Bézier curves.
 * [shrink] allows drawing inner/outer offset versions for glow effects.
 */
private fun buildBlobPath(
    points: Array<Offset>,
    cx: Float,
    cy: Float,
    numPoints: Int,
    shrink: Float,
): Path {
    val path = Path()
    val shrunkPoints =
        if (shrink == 0f) points
        else {
            Array(numPoints) { i ->
                val p = points[i]
                val dirX = p.x - cx
                val dirY = p.y - cy
                val len = sqrt(dirX * dirX + dirY * dirY)
                if (len == 0f) p else Offset(p.x + dirX / len * shrink, p.y + dirY / len * shrink)
            }
        }

    // Catmull-Rom to Bézier for smooth closed curve
    for (i in 0 until numPoints) {
        val p0 = shrunkPoints[(i - 1 + numPoints) % numPoints]
        val p1 = shrunkPoints[i]
        val p2 = shrunkPoints[(i + 1) % numPoints]
        val p3 = shrunkPoints[(i + 2) % numPoints]

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        if (i == 0) path.moveTo(p1.x, p1.y)
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.close()
    return path
}
