package com.advancedplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.ArtModel
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.EmptyState
import com.advancedplayer.ui.components.SectionHeader
import com.advancedplayer.ui.components.TrackRow
import com.advancedplayer.ui.components.albumOrUnknown
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.theme.LocalPlayerColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, navigate: (String) -> Unit, bottomPadding: Dp) {
    val colors = LocalPlayerColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val queryFlow = remember { MutableStateFlow("") }
    LaunchedEffect(query) { queryFlow.value = query }
    val results by remember { queryFlow.debounce(150).flatMapLatest { vm.library.search(it) } }.collectAsState(emptyList())
    val state by vm.playerState.collectAsState()
    val focus = remember { FocusRequester() }

    val artists = remember(results) { results.filter { !it.isVideo }.groupBy { it.artist }.entries.take(6) }
    val albums = remember(results) { results.filter { !it.isVideo && it.album.isNotEmpty() }.groupBy { it.album }.entries.take(6) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.nav_search), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            },
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.card, unfocusedContainerColor = colors.card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        if (query.isNotBlank() && results.isEmpty()) {
            EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.no_results))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 8.dp)) {
            if (artists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.tab_artists)) }
                items(artists, key = { "ar_${it.key}" }) { (artist, list) ->
                    ListEntry(
                        artistOrUnknown(artist), stringResource(R.string.sort_artist),
                        art = { Artwork(ArtModel(list.first().id), Modifier.size(52.dp), shape = CircleShape) },
                    ) { navigate(Routes.list(Routes.KIND_ARTIST, artist)) }
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.tab_albums)) }
                items(albums, key = { "al_${it.key}" }) { (album, list) ->
                    ListEntry(
                        albumOrUnknown(album), artistOrUnknown(list.first().artist),
                        art = { Artwork(ArtModel(list.first().id), Modifier.size(52.dp)) },
                    ) { navigate(Routes.list(Routes.KIND_ALBUM, album)) }
                }
            }
            if (results.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.tab_songs)) }
                itemsIndexed(results, key = { _, t -> "t_${t.id}" }) { i, t ->
                    TrackRow(t, state.currentId == t.id, state.isPlaying, onClick = {
                        vm.play(results, i)
                        if (t.isVideo) navigate(Routes.VIDEO)
                    })
                }
            }
        }
    }
}
