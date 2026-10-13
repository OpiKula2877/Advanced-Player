package com.advancedplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.ArtModel
import com.advancedplayer.data.SmartList
import com.advancedplayer.data.SongSort
import com.advancedplayer.data.artModel
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.ChipButton
import com.advancedplayer.ui.components.formatDuration
import com.advancedplayer.ui.theme.MonoFamily
import com.advancedplayer.ui.components.EmptyState
import com.advancedplayer.ui.components.TrackRow
import com.advancedplayer.ui.components.albumOrUnknown
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.components.playlistArtModel
import com.advancedplayer.ui.theme.LocalPlayerColors
import kotlinx.coroutines.launch

private val tabs = listOf(R.string.tab_songs, R.string.tab_artists, R.string.tab_albums, R.string.tab_playlists, R.string.tab_videos, R.string.tab_folders)

@Composable
fun rememberSourcePickers(vm: MainViewModel): Pair<() -> Unit, () -> Unit> {
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    return { folder.launch(null) } to { files.launch(arrayOf("audio/*", "video/*")) }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, navigate: (String) -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    val colors = LocalPlayerColors.current
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val (pickFolder, pickFiles) = rememberSourcePickers(vm)
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (pager.currentPage == 0) {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(
                            SongSort.TITLE to R.string.sort_title,
                            SongSort.ARTIST to R.string.sort_artist,
                            SongSort.DATE_ADDED to R.string.sort_date_added,
                            SongSort.DURATION to R.string.sort_duration,
                            SongSort.MOST_PLAYED to R.string.sort_most_played,
                        ).forEach { (s, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(label),
                                        color = if (settings.songSort == s) colors.accent else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (settings.songSort == s) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    sortMenu = false
                                    vm.launch { vm.settingsRepo.setSongSort(s) }
                                },
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.library_sources)) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_scan_device)) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = { addMenu = false; vm.rescan() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_folder)) }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                        onClick = { addMenu = false; pickFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_pick_files)) }, leadingIcon = { Icon(Icons.Rounded.AudioFile, null) },
                        onClick = { addMenu = false; pickFiles() },
                    )
                }
            }
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {},
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(pager.currentPage, matchContentSize = true),
                    color = colors.accent, width = androidx.compose.ui.unit.Dp.Unspecified,
                )
            },
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(stringResource(label), fontWeight = FontWeight.Bold) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = colors.muted,
                )
            }
        }
        val pad = PaddingValues(bottom = bottomPadding + 8.dp)
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            when (page) {
                0 -> SongsTab(vm, settings.songSort, pad, pickFolder, pickFiles)
                1 -> ArtistsTab(vm, pad, navigate)
                2 -> AlbumsTab(vm, pad, navigate)
                3 -> PlaylistsTab(vm, pad, navigate)
                4 -> VideosTab(vm, pad, navigate)
                5 -> FoldersTab(vm, pad, navigate)
            }
        }
    }
}

@Composable
private fun SongsTab(
    vm: MainViewModel, sort: SongSort, pad: PaddingValues,
    pickFolder: () -> Unit, pickFiles: () -> Unit,
) {
    val songs by remember(sort) { vm.library.songs(sort) }.collectAsState(null)
    val state by vm.playerState.collectAsState()
    val list = songs ?: return
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, stringResource(R.string.empty_library_title), stringResource(R.string.empty_library_text)) {
            LibrarySourceButtons(vm, pickFolder, pickFiles)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        item(key = "shuffle") {
            Row(
                Modifier.fillMaxWidth().clickable { vm.shufflePlay(list) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = LocalPlayerColors.current.accent)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.shuffle_play), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.songs_count, list.size, list.size), color = LocalPlayerColors.current.muted)
            }
        }
        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, state.currentId == t.id, state.isPlaying, onClick = { vm.play(list, i) })
        }
    }
}

@Composable
fun LibrarySourceButtons(vm: MainViewModel, pickFolder: () -> Unit, pickFiles: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipButton(stringResource(R.string.action_scan_device), Icons.Rounded.Refresh) { vm.rescan() }
        ChipButton(stringResource(R.string.action_add_folder), Icons.Rounded.CreateNewFolder, onClick = pickFolder)
        ChipButton(stringResource(R.string.action_pick_files), Icons.Rounded.AudioFile, onClick = pickFiles)
    }
}

@Composable
private fun ArtistsTab(vm: MainViewModel, pad: PaddingValues, navigate: (String) -> Unit) {
    val artists by remember { vm.library.artists() }.collectAsState(emptyList())
    val colors = LocalPlayerColors.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        items(artists, key = { it.artist }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { navigate(Routes.list(Routes.KIND_ARTIST, a.artist)) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(ArtModel(a.coverId), Modifier.size(56.dp), shape = CircleShape)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(artistOrUnknown(a.artist), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.songs_count, a.count, a.count), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(vm: MainViewModel, pad: PaddingValues, navigate: (String) -> Unit) {
    val albums by remember { vm.library.albums() }.collectAsState(emptyList())
    val colors = LocalPlayerColors.current
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.album }) { a ->
            Column(Modifier.clickable { navigate(Routes.list(Routes.KIND_ALBUM, a.album)) }) {
                Artwork(ArtModel(a.coverId), Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(8.dp), iconSize = 40.dp)
                Spacer(Modifier.size(8.dp))
                Text(albumOrUnknown(a.album), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artistOrUnknown(a.artist), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: MainViewModel, pad: PaddingValues, navigate: (String) -> Unit) {
    val playlists by vm.playlists.collectAsState()
    val colors = LocalPlayerColors.current
    var creating by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        item(key = "new") {
            Row(
                Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.new_playlist), style = MaterialTheme.typography.titleMedium)
            }
        }
        items(SmartList.entries.toList(), key = { "s_$it" }) { s ->
            ListEntry(
                title = stringResource(smartTitle(s)),
                subtitle = stringResource(R.string.smart_playlists),
                art = { SmartTile(s, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))) },
                onClick = { navigate(Routes.list(Routes.KIND_SMART, s.name)) },
            )
        }
        items(playlists, key = { "p_${it.id}" }) { p ->
            ListEntry(
                title = p.name,
                subtitle = pluralStringResource(R.plurals.songs_count, p.count, p.count),
                art = { Artwork(playlistArtModel(p.customArtPath, p.coverId), Modifier.size(56.dp)) },
                onClick = { navigate(Routes.list(Routes.KIND_PLAYLIST, p.id.toString())) },
            )
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.playlist_name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.createPlaylist(name)
                    creating = false
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ListEntry(title: String, subtitle: String, art: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = LocalPlayerColors.current.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun VideosTab(vm: MainViewModel, pad: PaddingValues, navigate: (String) -> Unit) {
    val videos by remember { vm.library.videos() }.collectAsState(emptyList())
    val state by vm.playerState.collectAsState()
    val colors = LocalPlayerColors.current
    if (videos.isEmpty()) {
        EmptyState(Icons.Rounded.LibraryMusic, stringResource(R.string.no_results))
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(170.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(videos, key = { it.id }) { v: Track ->
            Column(Modifier.clickable {
                vm.play(videos, videos.indexOf(v))
                navigate(Routes.VIDEO)
            }) {
                Box {
                    Artwork(
                        v.artModel(),
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(8.dp), isVideo = true,
                    )
                    Text(
                        formatDuration(v.durationMs),
                        style = MaterialTheme.typography.labelSmall, fontFamily = MonoFamily,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (state.currentId == v.id) colors.accent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FoldersTab(vm: MainViewModel, pad: PaddingValues, navigate: (String) -> Unit) {
    val folders by remember { vm.library.folders() }.collectAsState(emptyList())
    val colors = LocalPlayerColors.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        items(folders, key = { it.folder }) { f ->
            ListEntry(
                title = f.folder.substringAfterLast('/').ifEmpty { f.folder },
                subtitle = f.folder,
                art = {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    }
                },
                onClick = { navigate(Routes.list(Routes.KIND_FOLDER, f.folder)) },
            )
        }
    }
}
