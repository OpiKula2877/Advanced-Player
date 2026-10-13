package com.advancedplayer.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advancedplayer.data.VisualizerStyle
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.theme.MonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VuGreen = Color(0xFF3DDC84)
private val VuYellow = Color(0xFFF5C518)
private val VuRed = Color(0xFFFF4D4D)

@Composable
fun Visualizer(
    style: VisualizerStyle,
    handle: VisualizerHandle,
    color: Color,
    modifier: Modifier = Modifier,
    artModel: Any? = null,
) {
    when (style) {
        VisualizerStyle.CIRCLE -> CircleVisualizer(handle, color, artModel, modifier)
        VisualizerStyle.VU -> VuMeter(handle, color, modifier)
        else -> Canvas(modifier) {
            handle.tick.longValue // subscribe to frames
            val e = handle.engine
            when (style) {
                VisualizerStyle.BARS -> drawBars(e, color)
                VisualizerStyle.MIRROR -> drawMirror(e, color)
                VisualizerStyle.WAVE -> drawWave(e, color)
                VisualizerStyle.PARTICLES -> drawParticles(e, color)
                else -> Unit
            }
        }
    }
}

fun DrawScope.drawBars(e: VisualizerEngine, color: Color, peakColor: Color = Color.White) {
    val n = VisualizerEngine.BANDS
    val slot = size.width / n
    val barW = slot * 0.72f
    val radius = CornerRadius(barW / 2.5f)
    val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = 0f, endY = size.height)
    for (i in 0 until n) {
        val h = (e.bands[i] * size.height * 0.92f).coerceAtLeast(barW * 0.6f)
        val x = i * slot + (slot - barW) / 2
        drawRoundRect(brush, Offset(x, size.height - h), Size(barW, h), radius)
        val py = size.height - e.peaks[i] * size.height * 0.92f - 6f
        drawRect(peakColor.copy(alpha = 0.85f), Offset(x, py), Size(barW, 3f))
    }
}

fun DrawScope.drawMirror(e: VisualizerEngine, color: Color) {
    val n = VisualizerEngine.BANDS
    val half = n / 2
    val slot = size.width / n
    val barW = slot * 0.6f
    val mid = size.height / 2
    val brush = Brush.verticalGradient(
        listOf(color.copy(alpha = 0f), color, Color.White, color, color.copy(alpha = 0f)),
        startY = 0f, endY = size.height,
    )
    // Lows in the middle, highs toward the edges.
    for (i in 0 until n) {
        val band = if (i < half) (half - 1 - i) * 2 else (i - half) * 2
        val v = e.bands[band.coerceIn(0, n - 1)]
        val h = (v * size.height * 0.46f).coerceAtLeast(2f)
        val x = i * slot + (slot - barW) / 2
        drawRoundRect(brush, Offset(x, mid - h), Size(barW, h * 2), CornerRadius(barW / 2))
    }
}

fun DrawScope.drawWave(e: VisualizerEngine, color: Color) {
    val n = VisualizerEngine.WAVE
    val mid = size.height / 2
    val amp = size.height * 0.42f
    val path = Path()
    for (i in 0 until n) {
        val x = size.width * i / (n - 1)
        val y = mid - (e.wave[i] * 1.8f).coerceIn(-1f, 1f) * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawLine(color.copy(alpha = 0.15f), Offset(0f, mid), Offset(size.width, mid), 1.5f)
    drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = 18f, cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = 0.35f), style = Stroke(width = 8f, cap = StrokeCap.Round))
    drawPath(path, lerp(color, Color.White, 0.4f), style = Stroke(width = 3f, cap = StrokeCap.Round))
}

fun DrawScope.drawParticles(e: VisualizerEngine, color: Color) {
    val p = e.particles
    val c = center
    val scale = min(size.width, size.height) * 0.75f
    // A complementary-ish second tone so the field is not one flat colour.
    val second = lerp(color, Color(0xFF7DF9FF), 0.55f)

    // Shockwave rings fired on bass kicks.
    for (r in 0 until ParticleField.RINGS) {
        val life = p.ringLife[r]
        if (life <= 0f) continue
        drawCircle(
            lerp(second, color, life).copy(alpha = (life * 0.7f).coerceIn(0f, 1f)),
            p.ringRadius[r] * scale, c, style = Stroke(width = 2f + life * 10f),
        )
    }

    // Streaks: each particle is drawn as a short line along its motion (warp effect).
    for (i in p.life.indices) {
        val life = p.life[i]
        if (life <= 0f) continue
        val pos = Offset(c.x + p.x[i] * scale, c.y + p.y[i] * scale)
        val tail = Offset(pos.x - p.vx[i] * scale * 0.12f, pos.y - p.vy[i] * scale * 0.12f)
        val tint = lerp(lerp(color, second, p.tone[i]), Color.White, (1f - life) * 0.5f)
        drawLine(tint.copy(alpha = (life * 0.85f).coerceIn(0f, 1f)), tail, pos, strokeWidth = p.size[i] * density * 0.6f, cap = StrokeCap.Round)
    }

    // Core: rotating dashed ring that breathes with the bass instead of a flat glow.
    val coreR = scale * (0.07f + e.bass * 0.06f)
    rotate(p.spin * 57.3f, c) {
        val dashes = 12
        for (k in 0 until dashes) {
            drawArc(
                color.copy(alpha = 0.55f + e.bass * 0.45f),
                startAngle = k * 360f / dashes, sweepAngle = 360f / dashes * 0.55f, useCenter = false,
                topLeft = Offset(c.x - coreR, c.y - coreR), size = Size(coreR * 2, coreR * 2),
                style = Stroke(width = 3f + e.bass * 5f, cap = StrokeCap.Round),
            )
        }
    }
    drawCircle(second.copy(alpha = 0.9f), coreR * 0.25f + e.energy * 6f, c)
}

fun DrawScope.drawCircleSpectrum(e: VisualizerEngine, color: Color, radius: Float, maxLen: Float) {
    val n = VisualizerEngine.BANDS
    val spokes = n * 2
    val c = center
    val stroke = (2 * PI.toFloat() * radius / spokes) * 0.55f
    for (s in 0 until spokes) {
        // Mirror the spectrum so the ring is symmetric.
        val band = if (s < n) s else spokes - 1 - s
        val v = e.bands[band]
        val angle = (s.toFloat() / spokes) * 2f * PI.toFloat() - PI.toFloat() / 2
        val len = 4f + v * maxLen
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            lerp(color, Color.White, v * 0.5f),
            Offset(c.x + dx * radius, c.y + dy * radius),
            Offset(c.x + dx * (radius + len), c.y + dy * (radius + len)),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
    drawCircle(color.copy(alpha = 0.25f + e.bass * 0.5f), radius - 4f, c, style = Stroke(3f + e.bass * 6f))
}

@Composable
fun CircleVisualizer(handle: VisualizerHandle, color: Color, artModel: Any?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val minDim = min(maxWidth.value, maxHeight.value)
        val artSize = (minDim * 0.52f).dp
        Canvas(Modifier.fillMaxSize()) {
            handle.tick.longValue
            val r = min(size.width, size.height) * 0.27f
            drawCircleSpectrum(handle.engine, color, r + 6f, min(size.width, size.height) * 0.2f)
        }
        Box(
            Modifier.size(artSize).graphicsLayer {
                handle.tick.longValue
                val s = 1f + handle.engine.bass * 0.07f
                scaleX = s
                scaleY = s
                rotationZ = 0f
            },
        ) {
            Artwork(artModel, Modifier.fillMaxSize(), shape = CircleShape, iconSize = 48.dp)
        }
    }
}

/** Retro stereo LED meter. */
@Composable
fun VuMeter(handle: VisualizerHandle, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        listOf("L", "R").forEachIndexed { ch, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color, fontFamily = MonoFamily, fontSize = 18.sp, modifier = Modifier.width(24.dp))
                Canvas(Modifier.weight(1f).height(36.dp)) {
                    handle.tick.longValue
                    val e = handle.engine
                    val level = if (ch == 0) e.levelL else e.levelR
                    val peak = if (ch == 0) e.peakL else e.peakR
                    drawLeds(level, peak)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp)) {
            listOf("-40", "-20", "-10", "-6", "-3", "0").forEach {
                Text(it, color = color.copy(alpha = 0.7f), fontFamily = MonoFamily, fontSize = 11.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun DrawScope.drawLeds(level: Float, peak: Float) {
    val segs = 30
    val gap = 4f
    val w = (size.width - gap * (segs - 1)) / segs
    val lit = (level * segs).toInt()
    val peakSeg = (peak * segs).toInt().coerceAtMost(segs - 1)
    for (i in 0 until segs) {
        val f = i.toFloat() / segs
        val base = when {
            f > 0.86f -> VuRed
            f > 0.68f -> VuYellow
            else -> VuGreen
        }
        val on = i < lit || i == peakSeg && peak > 0.02f
        drawRoundRect(
            if (on) base else base.copy(alpha = 0.12f),
            Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(3f),
        )
    }
}
