package com.advancedplayer.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.theme.MonoFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Continuously advancing angle (degrees) while [running]; holds still when paused. */
@Composable
private fun rememberSpin(running: Boolean, degreesPerSecond: Float): () -> Float {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, degreesPerSecond) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) angle.floatValue = (angle.floatValue + (t - last) / 1e9f * degreesPerSecond) % 360f
                last = t
            }
        }
    }
    return { angle.floatValue }
}

/**
 * Angle (degrees) to rotate the tonearm so its stylus sits at [radius] from the record
 * centre. All values are normalised to the deck size. Solved as the intersection of the
 * arm's reach circle and the groove circle, picking the solution nearest the rest pose.
 */
private fun tonearmAngle(pivot: Offset, restTip: Offset, center: Offset, radius: Float): Float {
    val arm = (restTip - pivot).getDistance()
    val dx = center.x - pivot.x
    val dy = center.y - pivot.y
    val d = sqrt(dx * dx + dy * dy)
    val a = (arm * arm - radius * radius + d * d) / (2 * d)
    val h = sqrt((arm * arm - a * a).coerceAtLeast(0f))
    val bx = pivot.x + a * dx / d
    val by = pivot.y + a * dy / d
    val candidates = listOf(
        Offset(bx + h * dy / d, by - h * dx / d),
        Offset(bx - h * dy / d, by + h * dx / d),
    )
    val rest = atan2(restTip.y - pivot.y, restTip.x - pivot.x)
    return candidates.map { c ->
        var diff = atan2(c.y - pivot.y, c.x - pivot.x) - rest
        while (diff > PI) diff -= (2 * PI).toFloat()
        while (diff < -PI) diff += (2 * PI).toFloat()
        diff
    }.minBy { abs(it) } * 180f / PI.toFloat()
}

private val ArmPivot = Offset(0.93f, 0.08f)
private val ArmRestTip = Offset(0.80f, 0.62f)
private const val RECORD_RADIUS = 0.46f

/**
 * The tonearm follows the song position: outer groove at the start, the label at the end
 * (like a real record). It stays put when paused and follows the seek bar while dragging.
 */
@Composable
fun VinylDeck(artModel: Any?, playing: Boolean, accent: Color, progress: () -> Float, modifier: Modifier = Modifier) {
    val spin = rememberSpin(playing, 200f) // 33 1/3 rpm
    val p by animateFloatAsState(progress().coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMediumLow), label = "arm")
    val armAngle = tonearmAngle(ArmPivot, ArmRestTip, Offset(0.5f, 0.5f), RECORD_RADIUS * (0.95f - p * 0.59f))
    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val d = maxWidth
        Box(
            Modifier.size(d * 0.92f).graphicsLayer { rotationZ = spin() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF0B0B0B), r)
                var gr = r * 0.97f
                while (gr > r * 0.42f) {
                    drawCircle(Color(0xFF1C1C1C), gr, style = Stroke(1.2f))
                    gr -= r * 0.022f
                }
                // Light sheen that rotates with the record.
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent)),
                    startAngle = -30f, sweepAngle = 60f, useCenter = true,
                )
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent)),
                    startAngle = 150f, sweepAngle = 60f, useCenter = true,
                )
                drawCircle(accent.copy(alpha = 0.8f), r * 0.40f, style = Stroke(3f))
            }
            Artwork(artModel, Modifier.size(d * 0.92f * 0.38f), shape = CircleShape, iconSize = 32.dp)
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF0B0B0B)))
        }
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * ArmPivot.x, size.height * ArmPivot.y)
            val tip = Offset(size.width * ArmRestTip.x, size.height * ArmRestTip.y)
            rotate(armAngle, pivot) {
                drawLine(Color(0xFFBDBDBD), pivot, tip, strokeWidth = size.width * 0.018f, cap = StrokeCap.Round)
                // Headshell along the arm direction so the stylus ends exactly at the tip.
                val dir = (tip - pivot) / (tip - pivot).getDistance()
                val head = tip - dir * (size.width * 0.07f)
                drawLine(Color(0xFF8A8A8A), head, tip, strokeWidth = size.width * 0.05f, cap = StrokeCap.Round)
                drawCircle(accent, size.width * 0.008f, tip)
                drawCircle(Color(0xFF9E9E9E), size.width * 0.045f, pivot)
                drawCircle(accent, size.width * 0.018f, pivot)
            }
        }
    }
}

/**
 * Cassette whose tape is inside the shell: reels are drawn first and the shell (with the
 * label) is drawn over them with only a small window cut out, so the tape shows through.
 */
@Composable
fun CassetteDeck(
    title: String,
    artist: String,
    playing: Boolean,
    progress: () -> Float,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val spin = rememberSpin(playing, 150f)
    val body = if (dark) Color(0xFF242424) else Color(0xFF3A3A3A)
    val label = if (dark) Color(0xFFEDE6D6) else Color(0xFFF4EEDF)
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1.58f)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val window = Rect(Offset(cw * 0.27f, ch * 0.49f), Size(cw * 0.46f, ch * 0.17f))
            val windowRadius = CornerRadius(ch * 0.07f)
            val lc = Offset(cw * 0.335f, ch * 0.575f)
            val rc = Offset(cw * 0.665f, ch * 0.575f)

            // 1) Inside of the cassette: dark cavity, tape packs and spinning hubs.
            drawRoundRect(Color(0xFF0E0E0E), size = size, cornerRadius = CornerRadius(cw * 0.04f))
            val p = progress().coerceIn(0f, 1f)
            val hub = ch * 0.055f
            val maxTape = ch * 0.20f
            val leftR = hub + (maxTape - hub) * sqrt(1f - p)
            val rightR = hub + (maxTape - hub) * sqrt(p)
            val tape = Color(0xFF3B2A1F)
            drawCircle(tape, leftR, lc)
            drawCircle(tape, rightR, rc)
            listOf(lc, rc).forEach { c ->
                drawCircle(Color(0xFFE8E8E8), hub, c)
                rotate(spin(), c) {
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3
                        drawLine(
                            Color(0xFF555555),
                            c + Offset(cos(a) * hub * 0.35f, sin(a) * hub * 0.35f),
                            c + Offset(cos(a) * hub * 0.85f, sin(a) * hub * 0.85f),
                            strokeWidth = hub * 0.18f, cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // 2) Shell and label over the reels, each with the window cut out.
            val hole = Path().apply { addRoundRect(RoundRect(window, windowRadius)) }
            val outer = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cw * 0.04f))) }
            drawPath(Path().apply { op(outer, hole, PathOperation.Difference) }, body)
            val labelRect = Rect(Offset(cw * 0.07f, ch * 0.08f), Size(cw * 0.86f, ch * 0.64f))
            val labelOuter = Path().apply { addRoundRect(RoundRect(labelRect, CornerRadius(cw * 0.02f))) }
            drawPath(Path().apply { op(labelOuter, hole, PathOperation.Difference) }, label)
            drawRect(accent, Offset(labelRect.left, labelRect.top + labelRect.height * 0.40f), Size(labelRect.width, labelRect.height * 0.07f))
            drawRect(accent.copy(alpha = 0.55f), Offset(labelRect.left, labelRect.top + labelRect.height * 0.49f), Size(labelRect.width, labelRect.height * 0.035f))
            // Window frame and a faint glass reflection.
            drawRoundRect(Color(0xFF1A1A1A), window.topLeft, window.size, windowRadius, style = Stroke(ch * 0.018f))
            drawRoundRect(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), window.topLeft, window.bottomRight),
                window.topLeft, window.size, windowRadius,
            )

            // Screws
            listOf(Offset(0.04f, 0.06f), Offset(0.96f, 0.06f), Offset(0.04f, 0.94f), Offset(0.96f, 0.94f)).forEach {
                val pos = Offset(it.x * cw, it.y * ch)
                drawCircle(Color(0xFF6B6B6B), cw * 0.014f, pos)
                drawLine(Color(0xFF3B3B3B), pos - Offset(cw * 0.008f, 0f), pos + Offset(cw * 0.008f, 0f), 2f)
            }
            // Bottom trapezoid with capstan holes
            val bt = ch * 0.77f
            drawRoundRect(if (dark) Color(0xFF2E2E2E) else Color(0xFF4A4A4A), Offset(cw * 0.18f, bt), Size(cw * 0.64f, ch * 0.19f), CornerRadius(cw * 0.02f))
            for (k in 0 until 4) drawCircle(Color(0xFF0E0E0E), ch * 0.025f, Offset(cw * (0.3f + k * 0.13f), bt + ch * 0.1f))
        }
        Column(Modifier.padding(start = w * 0.1f, top = w * 0.07f, end = w * 0.14f)) {
            Text(
                title.uppercase(), color = Color(0xFF1A1A1A), fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist, color = Color(0xFF4A4A4A), fontFamily = MonoFamily, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "A", color = Color(0xFF1A1A1A), fontFamily = MonoFamily, fontWeight = FontWeight.Black, fontSize = 18.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = -w * 0.1f, y = w * 0.07f),
        )
    }
}

private val grainBitmap: ImageBitmap by lazy {
    val size = 96
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    for (y in 0 until size) for (x in 0 until size) {
        val v = rnd.nextInt(256)
        bmp.setPixel(x, y, android.graphics.Color.argb(rnd.nextInt(18, 46), v, v, v))
    }
    bmp.asImageBitmap()
}

/** Scanlines, film grain and a soft vignette over the player. */
@Composable
fun CrtOverlay(modifier: Modifier = Modifier) {
    val jitter = rememberSpin(true, 997f)
    val brush = remember { ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated)) }
    Canvas(modifier.fillMaxSize()) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val j = jitter()
        translate(left = (j * 7f) % 96f - 96f, top = (j * 13f) % 96f - 96f) {
            drawRect(brush, Offset.Zero, Size(size.width + 192f, size.height + 192f))
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = center, radius = size.maxDimension * 0.75f,
            ),
        )
    }
}
