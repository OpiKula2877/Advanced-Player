package com.advancedplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.db.Track
import com.advancedplayer.playback.AbLoop
import com.advancedplayer.playback.PlayerBridge
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.AppSlider
import com.advancedplayer.ui.components.formatDuration
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SheetRow(icon: ImageVector, text: String, trailing: String? = null, highlighted: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPlayerColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlighted) colors.accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(20.dp))
        Text(
            text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = if (highlighted) colors.accent else MaterialTheme.colorScheme.onSurface,
        )
        if (trailing != null) Text(trailing, fontFamily = MonoFamily, color = colors.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val timer by PlayerBridge.sleepTimer.collectAsState()
    var remaining by remember { mutableLongStateOf(timer.remainingMs() ?: 0L) }
    LaunchedEffect(timer) {
        while (timer.endAtElapsed != null) {
            remaining = timer.remainingMs() ?: 0L
            delay(1000)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.tool_sleep_timer), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (timer.endAtElapsed != null) {
                Text(
                    stringResource(R.string.sleep_active, formatDuration(remaining)),
                    color = LocalPlayerColors.current.accent, fontFamily = MonoFamily,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                SheetRow(Icons.Rounded.Timer, stringResource(R.string.sleep_minutes, m)) {
                    PlayerBridge.startSleepTimer(m)
                    onDismiss()
                }
            }
            SheetRow(Icons.Rounded.Album, stringResource(R.string.sleep_end_of_track), highlighted = timer.endOfTrack) {
                PlayerBridge.sleepAtEndOfTrack()
                onDismiss()
            }
            if (timer.active) {
                HorizontalDivider()
                SheetRow(Icons.Rounded.TimerOff, stringResource(R.string.sleep_off)) {
                    PlayerBridge.cancelSleepTimer()
                    onDismiss()
                }
            }
        }
    }
}

private data class SpeedPreset(val label: Int, val speed: Float, val pitch: Float, val reverb: Float)

private val speedPresets = listOf(
    SpeedPreset(R.string.preset_normal, 1f, 1f, 0f),
    SpeedPreset(R.string.preset_nightcore, 1.25f, 1.25f, 0f),
    SpeedPreset(R.string.preset_slowed, 0.85f, 0.85f, 0.45f),
    SpeedPreset(R.string.preset_vaporwave, 0.8f, 0.8f, 0.25f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedPitchSheet(vm: MainViewModel, settings: AppSettings, onDismiss: () -> Unit) {
    val colors = LocalPlayerColors.current
    var speed by remember { mutableFloatStateOf(settings.speed) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var linked by remember { mutableStateOf(abs(settings.speed - settings.pitch) < 0.001f) }
    fun commit() = vm.launch { vm.settingsRepo.setSpeedPitch(speed, pitch) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.speed_pitch), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speedPresets.forEach { p ->
                    val selected = abs(speed - p.speed) < 0.001f && abs(pitch - p.pitch) < 0.001f
                    FilterChip(
                        selected = selected,
                        onClick = {
                            speed = p.speed
                            pitch = p.pitch
                            linked = p.speed == p.pitch
                            commit()
                            vm.launch { vm.settingsRepo.setReverb(p.reverb) }
                        },
                        label = { Text(stringResource(p.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LabeledValue(stringResource(R.string.speed), "%.2fx".format(speed))
            AppSlider(
                value = speed, valueRange = 0.5f..2f, steps = 29,
                onValueChange = {
                    speed = (it * 20).roundToInt() / 20f
                    if (linked) pitch = speed
                },
                onValueChangeFinished = { commit() },
            )
            LabeledValue(stringResource(R.string.pitch), "%.2fx".format(pitch))
            AppSlider(
                value = pitch, valueRange = 0.5f..2f, steps = 29,
                onValueChange = {
                    pitch = (it * 20).roundToInt() / 20f
                    if (linked) speed = pitch
                },
                onValueChangeFinished = { commit() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.link_speed_pitch), modifier = Modifier.weight(1f))
                Switch(
                    checked = linked, onCheckedChange = { linked = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                )
            }
            TextButton(onClick = {
                speed = 1f
                pitch = 1f
                linked = true
                commit()
                vm.launch { vm.settingsRepo.setReverb(0f) }
            }) { Text(stringResource(R.string.reset)) }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, fontFamily = MonoFamily, color = LocalPlayerColors.current.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    track: Track,
    positionMs: () -> Long,
    onDismiss: () -> Unit,
    onBookmarks: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEdit: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onCarMode: () -> Unit,
    onEqualizer: () -> Unit,
) {
    val ab = PlayerBridge.abLoop.collectAsState().value
    val abForTrack = ab.trackId == track.id
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            val (abLabel, abTrailing) = when {
                !abForTrack || ab.a == null -> stringResource(R.string.ab_set_a) to null
                ab.b == null -> stringResource(R.string.ab_set_b) to "A ${formatDuration(ab.a)}"
                else -> stringResource(R.string.ab_clear) to "${formatDuration(ab.a)} - ${formatDuration(ab.b)}"
            }
            SheetRow(Icons.Rounded.Repeat, "${stringResource(R.string.ab_loop)}: $abLabel", abTrailing, highlighted = abForTrack && ab.a != null) {
                val pos = positionMs()
                PlayerBridge.abLoop.value = when {
                    !abForTrack || ab.a == null -> AbLoop(track.id, pos, null)
                    ab.b == null -> if (pos > ab.a + 500) ab.copy(b = pos) else ab
                    else -> AbLoop()
                }
                if (PlayerBridge.abLoop.value.b != null || PlayerBridge.abLoop.value.a == null) onDismiss()
            }
            SheetRow(Icons.Rounded.Bookmarks, stringResource(R.string.bookmarks), onClick = onBookmarks)
            SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), onClick = onAddToPlaylist)
            SheetRow(Icons.Rounded.Equalizer, stringResource(R.string.tool_equalizer), onClick = onEqualizer)
            SheetRow(Icons.Rounded.DirectionsCar, stringResource(R.string.tool_car_mode), onClick = onCarMode)
            HorizontalDivider()
            if (!track.isVideo) {
                SheetRow(Icons.Rounded.Album, stringResource(R.string.go_to_album), onClick = onAlbum)
                SheetRow(Icons.Rounded.Person, stringResource(R.string.go_to_artist), onClick = onArtist)
            }
            SheetRow(Icons.Rounded.Edit, stringResource(R.string.edit_info), onClick = onEdit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(vm: MainViewModel, track: Track, positionMs: () -> Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val bookmarks by remember(track.id) { vm.library.bookmarks(track.id) }.collectAsState(emptyList())
    val colors = LocalPlayerColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetRow(Icons.Rounded.BookmarkAdd, stringResource(R.string.add_bookmark), formatDuration(positionMs())) {
                vm.launch { vm.library.addBookmark(track.id, positionMs()) }
            }
            HorizontalDivider()
            if (bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.no_bookmarks), color = colors.muted,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(bookmarks, key = { it.id }) { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onSeek(b.positionMs)
                            onDismiss()
                        }.padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Bookmark, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(20.dp))
                        Text(formatDuration(b.positionMs), fontFamily = MonoFamily, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.launch { vm.library.deleteBookmark(b) } }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = colors.muted)
                        }
                    }
                }
            }
        }
    }
}
