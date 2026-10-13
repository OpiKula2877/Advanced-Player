package com.advancedplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.ScanState
import com.advancedplayer.data.SmartList
import com.advancedplayer.data.artModel
import com.advancedplayer.data.db.PlaylistSummary
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.EmptyState
import com.advancedplayer.ui.components.SectionHeader
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.components.playlistArtModel
import com.advancedplayer.ui.player.SleepTimerSheet
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoLabel
import java.util.Calendar

@Composable
private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return stringResource(
        when (h) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    navigate: (String) -> Unit,
    bottomPadding: Dp,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val colors = LocalPlayerColors.current
    val count by vm.trackCount.collectAsState()
    val scan by vm.scanState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val recent by remember { vm.library.smart(SmartList.RECENTLY_PLAYED) }.collectAsState(emptyList())
    val added by remember { vm.library.smart(SmartList.RECENTLY_ADDED) }.collectAsState(emptyList())
    val (pickFolder, pickFiles) = rememberSourcePickers(vm)
    var timerSheet by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name).uppercase(), style = MonoLabel, color = colors.accent)
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { navigate(Routes.STATS) }) { Icon(Icons.Rounded.BarChart, stringResource(R.string.tool_stats)) }
                IconButton(onClick = { navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            }
        }
        if (scan is ScanState.Running) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.scanning), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                }
            }
        }
        if (!hasPermission) {
            item {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.card).padding(20.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.permission_text), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    ) { Text(stringResource(R.string.action_grant_access)) }
                }
            }
        }
        if (count == 0 && scan !is ScanState.Running) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, stringResource(R.string.empty_library_title), stringResource(R.string.empty_library_text)) {
                    LibrarySourceButtons(vm, pickFolder, pickFiles)
                }
            }
        }

        // Quick-access grid, Spotify style
        item {
            val tiles: List<Any> = SmartList.entries.filter { it != SmartList.RECENTLY_PLAYED } + playlists.take(4)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile ->
                            Box(Modifier.weight(1f)) {
                                when (tile) {
                                    is SmartList -> QuickTile(
                                        stringResource(smartTitle(tile)),
                                        { SmartTile(tile, Modifier.size(56.dp), 24.dp) },
                                    ) { navigate(Routes.list(Routes.KIND_SMART, tile.name)) }
                                    is PlaylistSummary -> QuickTile(
                                        tile.name,
                                        { Artwork(playlistArtModel(tile.customArtPath, tile.coverId), Modifier.size(56.dp), shape = RoundedCornerShape(0.dp)) },
                                    ) { navigate(Routes.list(Routes.KIND_PLAYLIST, tile.id.toString())) }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.smart_recently_played)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(recent.take(20), key = { _, t -> t.id }) { i, t ->
                        CoverCard(t.title, artistOrUnknown(t.artist), { Artwork(t.artModel(), it, isVideo = t.isVideo, iconSize = 40.dp) }) {
                            vm.play(recent, i)
                            if (t.isVideo) navigate(Routes.VIDEO)
                        }
                    }
                }
            }
        }
        if (added.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.smart_recently_added)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(added.take(20), key = { _, t -> t.id }) { i, t ->
                        CoverCard(t.title, artistOrUnknown(t.artist), { Artwork(t.artModel(), it, iconSize = 40.dp) }) { vm.play(added, i) }
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_your_playlists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        CoverCard(p.name, pluralStringResource(R.plurals.songs_count, p.count, p.count), {
                            Artwork(playlistArtModel(p.customArtPath, p.coverId), it, iconSize = 40.dp)
                        }) { navigate(Routes.list(Routes.KIND_PLAYLIST, p.id.toString())) }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.section_tools)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ToolCard(Icons.Rounded.GraphicEq, stringResource(R.string.tool_visualizer)) { navigate(Routes.VISUALIZER) } }
                item { ToolCard(Icons.Rounded.Equalizer, stringResource(R.string.tool_equalizer)) { navigate(Routes.EQ) } }
                item { ToolCard(Icons.Rounded.Timer, stringResource(R.string.tool_sleep_timer)) { timerSheet = true } }
                item { ToolCard(Icons.Rounded.DirectionsCar, stringResource(R.string.tool_car_mode)) { navigate(Routes.CAR) } }
                item { ToolCard(Icons.Rounded.BarChart, stringResource(R.string.tool_stats)) { navigate(Routes.STATS) } }
            }
        }
    }
    if (timerSheet) SleepTimerSheet { timerSheet = false }
}

@Composable
private fun QuickTile(title: String, art: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = LocalPlayerColors.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(colors.card).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun CoverCard(title: String, subtitle: String, art: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        art(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalPlayerColors.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalPlayerColors.current
    Column(
        Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).background(colors.card).clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
}
