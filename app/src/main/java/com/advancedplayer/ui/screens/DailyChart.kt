package com.advancedplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advancedplayer.R
import com.advancedplayer.data.db.DayListening
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max

/** Minutes listened per local day for the last [days] days, oldest first; missing days are 0. */
fun fillDays(rows: List<DayListening>, days: Int): List<Pair<Long, Long>> {
    val tz = TimeZone.getDefault()
    val now = System.currentTimeMillis()
    val today = (now + tz.getOffset(now)) / 86_400_000L
    val byDay = rows.associate { it.day to it.listenedMs }
    return (today - days + 1..today).map { d -> d to (byDay[d] ?: 0L) / 60_000L }
}

/**
 * Single-series bar chart of daily listening minutes. One colour (the accent), thin bars
 * with a 2px gap and rounded tops anchored on the baseline, a recessive grid, and a
 * tap/drag readout instead of numbers on every bar.
 */
@Composable
fun DailyChart(rows: List<DayListening>, days: Int, modifier: Modifier = Modifier) {
    val colors = LocalPlayerColors.current
    val data = remember(rows, days) { fillDays(rows, days) }
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val total = data.sumOf { it.second }
    val avg = if (data.isNotEmpty()) total / data.size else 0
    val maxMin = max(1L, data.maxOfOrNull { it.second } ?: 1L)
    // Round the axis top to a friendly step so grid labels are clean.
    // Smallest friendly step that keeps the axis to at most four gridlines above zero.
    val step = listOf(1L, 2L, 5L, 10L, 15L, 30L, 60L, 120L, 240L).firstOrNull { maxMin / it.toDouble() <= 4 } ?: 480L
    val axisTop = (ceil(maxMin / step.toDouble()) * step).toLong().coerceAtLeast(step)
    val ticks = (axisTop / step).toInt()
    val measurer = rememberTextMeasurer()
    val muted = colors.muted
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    fun dayLabel(day: Long): String {
        val tz = TimeZone.getDefault()
        val utc = day * 86_400_000L
        return dateFormat.format(Date(utc - tz.getOffset(utc)))
    }
    val minLabel = stringResource(R.string.minutes_short, 0).replace("0", "").trim()

    Column(modifier.clip(RoundedCornerShape(16.dp)).background(colors.card).padding(16.dp)) {
        Text(stringResource(R.string.stats_daily), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.stats_last_days, days), style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                val sel = selected?.let { data.getOrNull(it) }
                Text(
                    if (sel != null) stringResource(R.string.minutes_short, sel.second.toInt())
                    else stringResource(R.string.minutes_short, total.toInt()),
                    style = MaterialTheme.typography.headlineSmall, fontFamily = MonoFamily,
                )
                Text(
                    if (sel != null) dayLabel(sel.first) else stringResource(R.string.stats_last_days, days),
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
            }
            Column {
                Text(stringResource(R.string.minutes_short, avg.toInt()), style = MaterialTheme.typography.titleMedium, fontFamily = MonoFamily)
                Text(stringResource(R.string.stats_avg_day), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        val labelStyle = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, color = muted)
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .pointerInput(data) {
                    detectTapGestures { o ->
                        val i = ((o.x - 36.dp.toPx()) / ((size.width - 36.dp.toPx()) / data.size)).toInt()
                        selected = if (i in data.indices && selected != i) i else null
                    }
                }
                .pointerInput(data) {
                    detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
                        val i = ((change.position.x - 36.dp.toPx()) / ((size.width - 36.dp.toPx()) / data.size)).toInt()
                        if (i in data.indices) selected = i
                    }
                },
        ) {
            val left = 36.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val plotW = size.width - left
            val plotH = bottom - 4.dp.toPx()
            // Recessive grid with three minute labels.
            for (k in 0..ticks) {
                val v = step * k
                val y = bottom - plotH * (v.toFloat() / axisTop)
                drawLine(grid, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
                val t = measurer.measure("$v $minLabel", labelStyle)
                drawText(t, topLeft = Offset(0f, y - t.size.height / 2))
            }
            val slot = plotW / data.size
            val gap = 2.dp.toPx()
            val barW = (slot - gap).coerceAtLeast(1f)
            val radius = minOf(4.dp.toPx(), barW / 2)
            data.forEachIndexed { i, (_, minutes) ->
                if (minutes <= 0) return@forEachIndexed
                val h = (plotH * (minutes.toFloat() / axisTop)).coerceAtLeast(2.dp.toPx())
                val x = left + i * slot + gap / 2
                val dim = selected != null && selected != i
                // Rounded data end at the top only; the baseline end stays square.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = bottom - h, right = x + barW, bottom = bottom,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path, colors.accent.copy(alpha = if (dim) 0.35f else 1f))
            }
            drawLine(muted.copy(alpha = 0.4f), Offset(left, bottom), Offset(size.width, bottom), 1.dp.toPx())
            // First, middle and last date under the axis.
            listOf(0, data.size / 2, data.size - 1).distinct().forEach { i ->
                val t = measurer.measure(dayLabel(data[i].first), labelStyle)
                val cx = left + i * slot + slot / 2
                val x = (cx - t.size.width / 2).coerceIn(left, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, bottom + 4.dp.toPx()))
            }
            selected?.let { i ->
                val cx = left + i * slot + slot / 2
                drawLine(muted.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, bottom), 1.dp.toPx())
            }
        }
        if (total == 0L) {
            Text(stringResource(R.string.stats_no_listening), color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
