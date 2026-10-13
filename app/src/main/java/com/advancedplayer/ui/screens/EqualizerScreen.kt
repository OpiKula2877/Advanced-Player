package com.advancedplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.playback.dsp.FxParams
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.AppSlider
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import kotlin.math.roundToInt

data class EqPreset(val id: String, val label: Int, val bands: List<Float>)

val EqPresets = listOf(
    EqPreset("flat", R.string.eq_preset_flat, List(10) { 0f }),
    EqPreset("rock", R.string.eq_preset_rock, listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
    EqPreset("pop", R.string.eq_preset_pop, listOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, -1f, 1f, 2f)),
    EqPreset("jazz", R.string.eq_preset_jazz, listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    EqPreset("classical", R.string.eq_preset_classical, listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    EqPreset("bass", R.string.eq_preset_bass, listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("vocal", R.string.eq_preset_vocal, listOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -1f)),
    EqPreset("electronic", R.string.eq_preset_electronic, listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f)),
)

private val freqLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@Composable
fun EqualizerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val colors = LocalPlayerColors.current
    val bands = remember { mutableStateListOf<Float>().apply { addAll(settings.eqBands) } }
    var preset by remember { mutableStateOf(settings.eqPreset) }
    LaunchedEffect(settings.eqBands) {
        if (bands.toList() != settings.eqBands) {
            bands.clear()
            bands.addAll(settings.eqBands)
        }
    }
    fun commitBands(p: String) {
        preset = p
        vm.launch { vm.settingsRepo.setEq(bands.toList(), p) }
    }

    ScreenScaffold(stringResource(R.string.tool_equalizer), onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.eq_enable), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.eqEnabled,
                    onCheckedChange = { vm.launch { vm.settingsRepo.setEqEnabled(it) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                )
            }
            Text(
                stringResource(R.string.eq_presets), style = MaterialTheme.typography.titleSmall, color = colors.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPresets.forEach { p ->
                    FilterChip(
                        selected = preset == p.id,
                        onClick = {
                            bands.clear()
                            bands.addAll(p.bands)
                            commitBands(p.id)
                            if (!settings.eqEnabled) vm.launch { vm.settingsRepo.setEqEnabled(true) }
                        },
                        label = { Text(stringResource(p.label)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent),
                    )
                }
                if (preset == "custom") {
                    FilterChip(
                        selected = true, onClick = {}, label = { Text(stringResource(R.string.eq_preset_custom)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            EqGraph(
                bands = bands,
                enabled = settings.eqEnabled,
                accent = colors.accent,
                muted = colors.muted,
                onChange = { i, v -> bands[i] = v },
                onCommit = { commitBands("custom") },
                modifier = Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 12.dp)
                    .alpha(if (settings.eqEnabled) 1f else 0.45f),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                freqLabels.forEach {
                    Text(
                        it, fontFamily = MonoFamily, fontSize = 11.sp, color = colors.muted,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Text(
                stringResource(R.string.effects), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 4.dp),
            )
            EffectSlider(stringResource(R.string.bass_boost), settings.bassBoost) { vm.launch { vm.settingsRepo.setBassBoost(it) } }
            EffectSlider(stringResource(R.string.virtualizer), settings.virtualizer) { vm.launch { vm.settingsRepo.setVirtualizer(it) } }
            EffectSlider(stringResource(R.string.loudness), settings.loudness) { vm.launch { vm.settingsRepo.setLoudness(it) } }
            EffectSlider(stringResource(R.string.reverb), settings.reverb) { vm.launch { vm.settingsRepo.setReverb(it) } }
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    val colors = LocalPlayerColors.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${(v * 100).roundToInt()}%", fontFamily = MonoFamily, color = colors.accent)
        }
        AppSlider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onCommit(v) })
    }
}

/**
 * Ten draggable band handles joined by a smooth response curve. Each column is its own
 * vertical slider; dragging anywhere in a column moves that band.
 */
@Composable
private fun EqGraph(
    bands: List<Float>,
    enabled: Boolean,
    accent: Color,
    muted: Color,
    onChange: (Int, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = FxParams.MAX_DB
    fun valueAt(y: Float, h: Float) = (((h / 2 - y) / (h / 2 * 0.88f)) * max).coerceIn(-max, max).let { (it * 2).roundToInt() / 2f }
    var active by remember { mutableStateOf(-1) }
    Canvas(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        active = (o.x / (size.width / bands.size)).toInt().coerceIn(0, bands.size - 1)
                        onChange(active, valueAt(o.y, size.height.toFloat()))
                    },
                    onDragEnd = { active = -1; onCommit() },
                    onDragCancel = { active = -1; onCommit() },
                ) { change, _ ->
                    if (active >= 0) onChange(active, valueAt(change.position.y, size.height.toFloat()))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    val i = (o.x / (size.width / bands.size)).toInt().coerceIn(0, bands.size - 1)
                    onChange(i, valueAt(o.y, size.height.toFloat()))
                    onCommit()
                }
            },
    ) {
        val n = bands.size
        val slot = size.width / n
        val mid = size.height / 2
        val range = mid * 0.88f
        // Grid
        listOf(-12f, -6f, 0f, 6f, 12f).forEach { db ->
            val y = mid - db / max * range
            drawLine(muted.copy(alpha = if (db == 0f) 0.45f else 0.15f), Offset(0f, y), Offset(size.width, y), 1.5f)
        }
        val points = bands.mapIndexed { i, v -> Offset(slot * i + slot / 2, mid - v / max * range) }
        // Tracks
        points.forEach { p ->
            drawRoundRect(muted.copy(alpha = 0.15f), Offset(p.x - 3f, mid - range), Size(6f, range * 2), CornerRadius(3f))
            val top = minOf(p.y, mid)
            drawRoundRect(accent.copy(alpha = 0.55f), Offset(p.x - 3f, top), Size(6f, kotlin.math.abs(p.y - mid)), CornerRadius(3f))
        }
        // Curve
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) {
                path.moveTo(0f, p.y)
                path.lineTo(p.x, p.y)
            } else {
                val prev = points[i - 1]
                val cx = (prev.x + p.x) / 2
                path.cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
            }
        }
        path.lineTo(size.width, points.last().y)
        drawPath(path, accent.copy(alpha = 0.25f), style = Stroke(10f, cap = StrokeCap.Round))
        drawPath(path, accent, style = Stroke(4f, cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            drawCircle(if (i == active) Color.White else accent, if (i == active) 16f else 12f, p)
            drawCircle(Color.Black.copy(alpha = 0.35f), 5f, p)
        }
    }
}
