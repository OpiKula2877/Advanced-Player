package com.advancedplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.SmartList
import com.advancedplayer.data.artModel
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.EmptyState
import com.advancedplayer.ui.components.MenuEntry
import com.advancedplayer.ui.components.PlayShuffleRow
import com.advancedplayer.ui.components.TrackRow
import com.advancedplayer.ui.components.albumOrUnknown
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.components.formatDuration
import com.advancedplayer.ui.components.playlistArtModel
import com.advancedplayer.ui.player.rememberArtColor
import com.advancedplayer.ui.theme.LocalPlayerColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

fun smartTitle(s: SmartList) = when (s) {
    SmartList.FAVORITES -> R.string.smart_favorites
    SmartList.RECENTLY_ADDED -> R.string.smart_recently_added
    SmartList.MOST_PLAYED -> R.string.smart_most_played
    SmartList.FORGOTTEN -> R.string.smart_forgotten
    SmartList.RECENTLY_PLAYED -> R.string.smart_recently_played
}

fun smartIcon(s: SmartList): ImageVector = when (s) {
    SmartList.FAVORITES -> Icons.Rounded.Star
    SmartList.RECENTLY_ADDED -> Icons.Rounded.NewReleases
    SmartList.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
    SmartList.FORGOTTEN -> Icons.Rounded.Diamond
    SmartList.RECENTLY_PLAYED -> Icons.Rounded.History
}

/** Square tile with a gradient and icon, used for smart playlists. */
@Composable
fun SmartTile(s: SmartList, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 28.dp) {
    val colors = LocalPlayerColors.current
    val second = when (s) {
        SmartList.FAVORITES -> Color(0xFF4B2CA0)
        SmartList.RECENTLY_ADDED -> Color(0xFF0E7C86)
        SmartList.MOST_PLAYED -> Color(0xFFB23A48)
        SmartList.FORGOTTEN -> Color(0xFF7A5C1E)
        SmartList.RECENTLY_PLAYED -> Color(0xFF2A4D8F)
    }
    Box(
        modifier.background(Brush.linearGradient(listOf(colors.accent, second))),
        contentAlignment = Alignment.Center,
    ) { Icon(smartIcon(s), null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
fun TrackListScreen(vm: MainViewModel, kind: String, key: String, navigate: (String) -> Unit, onBack: () -> Unit) {
    val colors = LocalPlayerColors.current
    val state by vm.playerState.collectAsState()
    val playlistId = if (kind == Routes.KIND_PLAYLIST) key.toLongOrNull() else null
    val smart = if (kind == Routes.KIND_SMART) runCatching { SmartList.valueOf(key) }.getOrNull() else null
    val flow: Flow<List<Track>> = remember(kind, key) {
        when (kind) {
            Routes.KIND_ALBUM -> vm.library.albumTracks(key)
            Routes.KIND_ARTIST -> vm.library.artistTracks(key)
            Routes.KIND_FOLDER -> vm.library.folderTracks(key)
            Routes.KIND_PLAYLIST -> playlistId?.let { vm.library.playlistTracks(it) } ?: flowOf(emptyList())
            else -> smart?.let { vm.library.smart(it) } ?: flowOf(emptyList())
        }
    }
    val tracks by flow.collectAsState(emptyList())
    val playlist by remember(playlistId) { playlistId?.let { vm.library.observePlaylist(it) } ?: flowOf(null) }.collectAsState(null)
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val artColor = rememberArtColor(vm, tracks.firstOrNull(), settings.albumColors)

    val title = when (kind) {
        Routes.KIND_ALBUM -> albumOrUnknown(key)
        Routes.KIND_ARTIST -> artistOrUnknown(key)
        Routes.KIND_FOLDER -> key.substringAfterLast('/').ifEmpty { key }
        Routes.KIND_PLAYLIST -> playlist?.name.orEmpty()
        else -> smart?.let { stringResource(smartTitle(it)) }.orEmpty()
    }
    val totalMs = tracks.sumOf { it.durationMs }
    val subtitle = buildString {
        if (kind == Routes.KIND_ALBUM) tracks.firstOrNull()?.artist?.takeIf { it.isNotEmpty() }?.let { append(it).append(" · ") }
    } + pluralStringResource(R.plurals.songs_count, tracks.size, tracks.size) + " · " + formatDuration(totalMs)

    // Local mirror for drag-reordering playlists.
    val ordered = remember { mutableStateListOf<Track>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(tracks) {
        if (!dragging) {
            ordered.clear()
            ordered.addAll(tracks)
        }
    }
    val listState = rememberLazyListState()
    val headerCount = 2
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - headerCount
        val t = to.index - headerCount
        if (f in ordered.indices && t in ordered.indices) ordered.add(t, ordered.removeAt(f))
    }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && playlistId != null) vm.launch { vm.library.setPlaylistArt(playlistId, uri) }
    }

    fun playAt(i: Int) {
        val list = ordered.toList()
        vm.play(list, i)
        if (list.getOrNull(i)?.isVideo == true) navigate(Routes.VIDEO)
    }

    ScreenScaffold(
        title = "",
        onBack = onBack,
        actions = {
            if (playlistId != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_image)) }, leadingIcon = { Icon(Icons.Rounded.Image, null) },
                            onClick = {
                                menu = false
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        if (playlist?.customArtPath != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_image)) }, leadingIcon = { Icon(Icons.Rounded.HideImage, null) },
                                onClick = { menu = false; vm.launch { vm.library.setPlaylistArt(playlistId, null) } },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf((artColor ?: colors.accent).copy(alpha = 0.35f), Color.Transparent)))
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val artMod = Modifier.size(220.dp)
                    when {
                        smart != null -> SmartTile(smart, artMod, 72.dp)
                        playlistId != null -> Artwork(playlistArtModel(playlist?.customArtPath, tracks.firstOrNull()?.id), artMod, iconSize = 64.dp)
                        kind == Routes.KIND_ARTIST -> Artwork(tracks.firstOrNull()?.artModel(), artMod, shape = CircleShape, iconSize = 64.dp)
                        else -> Artwork(tracks.firstOrNull()?.artModel(), artMod, shape = RoundedCornerShape(8.dp), iconSize = 64.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            }
            item(key = "actions") {
                PlayShuffleRow(
                    onPlay = { if (ordered.isNotEmpty()) playAt(0) },
                    onShuffle = { vm.shufflePlay(ordered.toList()) },
                )
            }
            if (ordered.isEmpty()) {
                item(key = "empty") {
                    EmptyState(smart?.let(::smartIcon) ?: Icons.Rounded.Star, stringResource(R.string.no_results))
                }
            }
            itemsIndexed(ordered, key = { i, t -> "${t.id}_$i" }) { i, t ->
                if (playlistId != null) {
                    ReorderableItem(reorder, key = "${t.id}_$i") { isDragging ->
                        TrackRow(
                            t, isCurrent = state.currentId == t.id, isPlaying = state.isPlaying,
                            onClick = { playAt(i) },
                            modifier = if (isDragging) Modifier.background(colors.cardHover) else Modifier,
                            trailing = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { dragging = true },
                                        onDragStopped = {
                                            dragging = false
                                            vm.launch { vm.library.setPlaylistOrder(playlistId, ordered.map { it.id }) }
                                        },
                                    ),
                                ) { Icon(Icons.Rounded.DragHandle, null, tint = colors.muted) }
                            },
                            extraMenu = listOf(
                                MenuEntry(stringResource(R.string.remove_from_playlist), Icons.Rounded.RemoveCircleOutline) {
                                    vm.launch { vm.library.removeFromPlaylist(playlistId, i) }
                                },
                            ),
                        )
                    }
                } else {
                    TrackRow(
                        t, isCurrent = state.currentId == t.id, isPlaying = state.isPlaying,
                        onClick = { playAt(i) },
                        index = if (kind == Routes.KIND_ALBUM) i else null,
                    )
                }
            }
        }
    }

    if (renaming && playlistId != null) {
        var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.launch { vm.library.renamePlaylist(playlistId, name) }
                    renaming = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting && playlistId != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_playlist_confirm, playlist?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.launch { vm.library.deletePlaylist(playlistId) }
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
