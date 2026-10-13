package com.advancedplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.theme.LocalPlayerColors

data class MenuEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** App-wide actions for a track; provided once at the root so every list can use them. */
class TrackActions(
    val vm: MainViewModel,
    private val navigate: (String) -> Unit,
    private val pickPlaylist: (List<Track>) -> Unit,
) {
    fun toggleFavorite(t: Track) = vm.toggleFavorite(t)
    fun playNext(t: List<Track>) = vm.player.playNext(t)
    fun addToQueue(t: List<Track>) = vm.player.addToQueue(t)
    fun addToPlaylist(t: List<Track>) = pickPlaylist(t)
    fun edit(t: Track) = navigate(Routes.edit(t.id))
    fun album(t: Track) = navigate(Routes.list(Routes.KIND_ALBUM, t.album))
    fun artist(t: Track) = navigate(Routes.list(Routes.KIND_ARTIST, t.artist))
}

val LocalTrackActions = staticCompositionLocalOf<TrackActions> { error("TrackActions not provided") }

@Composable
fun TrackMenu(track: Track, expanded: Boolean, onDismiss: () -> Unit, extra: List<MenuEntry> = emptyList()) {
    val a = LocalTrackActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun item(label: Int, icon: ImageVector, action: () -> Unit) = DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { Icon(icon, null) },
            onClick = {
                onDismiss()
                action()
            },
        )
        item(R.string.play_next, Icons.Rounded.SkipNext) { a.playNext(listOf(track)) }
        item(R.string.add_to_queue, Icons.AutoMirrored.Rounded.QueueMusic) { a.addToQueue(listOf(track)) }
        item(R.string.add_to_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd) { a.addToPlaylist(listOf(track)) }
        item(
            if (track.favorite) R.string.remove_favorite else R.string.add_favorite,
            if (track.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
        ) { a.toggleFavorite(track) }
        HorizontalDivider()
        if (!track.isVideo) {
            item(R.string.go_to_album, Icons.Rounded.Album) { a.album(track) }
            item(R.string.go_to_artist, Icons.Rounded.Person) { a.artist(track) }
        }
        item(R.string.edit_info, Icons.Rounded.Edit) { a.edit(track) }
        extra.forEach { e ->
            DropdownMenuItem(
                text = { Text(e.label) },
                leadingIcon = { Icon(e.icon, null) },
                onClick = {
                    onDismiss()
                    e.onClick()
                },
            )
        }
    }
}

@Composable
fun AddToPlaylistDialog(vm: MainViewModel, tracks: List<Track>, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    val colors = LocalPlayerColors.current
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val addedMsg = stringResource(R.string.added_to_playlist)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.playlist_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = colors.accent)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_playlist), style = MaterialTheme.typography.titleMedium)
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        vm.addToPlaylist(p.id, p.name, tracks, addedMsg)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(playlistArtModel(p.customArtPath, p.coverId), Modifier.size(44.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    vm.createPlaylist(name, tracks)
                    vm.toast(addedMsg.format(name))
                    onDismiss()
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
