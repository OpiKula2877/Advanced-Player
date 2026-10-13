package com.advancedplayer.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.LrcParser
import com.advancedplayer.data.Lyrics
import com.advancedplayer.data.LyricsLoader
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.ChipButton
import com.advancedplayer.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LyricsView(
    vm: MainViewModel,
    track: Track,
    positionMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lyrics: Lyrics? by produceState<Lyrics?>(null, track.id, track.lyricsUri) {
        value = withContext(Dispatchers.IO) { runCatching { LyricsLoader.load(context, track) }.getOrNull() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.launch { vm.library.setLyricsFile(track, uri) }
    }
    val l = lyrics
    if (l == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(Icons.Rounded.Lyrics, stringResource(R.string.no_lyrics)) {
                ChipButton(stringResource(R.string.attach_lyrics)) { picker.launch(arrayOf("*/*")) }
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val current = if (l.synced) LrcParser.indexAt(l.lines, positionMs) else -1
    var userScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) { userScrolling = listState.isScrollInProgress }
    LaunchedEffect(current) {
        if (current >= 0 && !userScrolling) listState.animateScrollToItem(current, scrollOffset = -220)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(l.lines) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                when {
                    !l.synced -> MaterialTheme.colorScheme.onSurface
                    active -> accent
                    i < current -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                },
                label = "lyric",
            )
            Text(
                line.text.ifBlank { "..." },
                style = if (l.synced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                color = color,
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = l.synced) { onSeek(line.timeMs) }
                    .padding(horizontal = 4.dp),
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ChipButton(stringResource(R.string.attach_lyrics)) { picker.launch(arrayOf("*/*")) }
            }
        }
    }
}
