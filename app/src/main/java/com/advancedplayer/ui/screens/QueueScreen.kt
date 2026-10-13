package com.advancedplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.artModel
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.NowPlayingBars
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.theme.LocalPlayerColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class QueueEntry(val key: String, val trackId: Long, val playlistIndex: Int)

@Composable
fun QueueScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val colors = LocalPlayerColors.current
    val ids = state.queueIds
    val tracks by remember(ids.toSet()) { vm.library.observeTracks(ids.distinct()) }.collectAsState(emptyList())
    val byId = remember(tracks) { tracks.associateBy { it.id } }

    val entries = remember { mutableStateListOf<QueueEntry>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.queueIds, state.queueIndices) {
        if (dragging) return@LaunchedEffect
        val seen = HashMap<Long, Int>()
        entries.clear()
        ids.forEachIndexed { i, id ->
            val n = seen.merge(id, 1, Int::plus)!!
            entries += QueueEntry("${id}_$n", id, state.queueIndices[i])
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = state.queueIndices.indexOf(state.currentIndex)
        if (i > 1) listState.scrollToItem(i - 1)
    }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        entries.add(to.index, entries.removeAt(from.index))
        vm.player.moveQueueItem(from.index, to.index)
    }

    ScreenScaffold(
        title = stringResource(R.string.queue),
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.player.clearQueueExceptCurrent() }) {
                Icon(Icons.Rounded.ClearAll, stringResource(R.string.clear_queue))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding) {
            if (state.shuffle) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.queue_shuffle_hint), color = colors.muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            itemsIndexed(entries, key = { _, e -> e.key }) { _, e ->
                ReorderableItem(reorder, key = e.key, enabled = !state.shuffle) { isDragging ->
                    val t = byId[e.trackId]
                    val current = e.playlistIndex == state.currentIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isDragging) colors.cardHover else MaterialTheme.colorScheme.background)
                            .clickable { vm.player.seekToQueueItem(e.playlistIndex) }
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t?.artModel(), Modifier.size(44.dp), isVideo = t?.isVideo == true)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(
                                t?.title.orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (current) colors.accent else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                artistOrUnknown(t?.artist.orEmpty()), color = colors.muted, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (current) NowPlayingBars(state.isPlaying, colors.accent, Modifier.padding(horizontal = 8.dp))
                        if (!current) {
                            IconButton(onClick = { vm.player.removeQueueItem(e.playlistIndex) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = colors.muted)
                            }
                        }
                        if (!state.shuffle) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { dragging = true },
                                    onDragStopped = { dragging = false },
                                ),
                            ) { Icon(Icons.Rounded.DragHandle, null, tint = colors.muted) }
                        }
                    }
                }
            }
        }
    }
}
