package com.advancedplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.artModel
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.FavoriteButton
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.theme.LocalPlayerColors

@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.playerState.collectAsState()
    val track by vm.currentTrack.collectAsState()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val t = track ?: return
    val position by vm.player.position.collectAsState(0L)
    val colors = LocalPlayerColors.current
    val artColor = rememberArtColor(vm, t, settings.albumColors)
    val bg = lerp(colors.card, artColor ?: colors.accent, if (colors.isDark) 0.35f else 0.18f)
    val progress = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onOpen),
    ) {
        Column {
            SwipeableDeck(settings.swipeGestures, onNext = { vm.player.next() }, onPrevious = { vm.player.previous() }) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(t.artModel(), Modifier.size(42.dp), shape = RoundedCornerShape(6.dp), isVideo = t.isVideo)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artistOrUnknown(t.artist), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FavoriteButton(t.favorite) { vm.toggleFavorite(t) }
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            val fg = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp)) {
                drawLine(fg.copy(alpha = 0.18f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
                drawLine(fg, Offset(0f, size.height / 2), Offset(size.width * progress, size.height / 2), size.height)
            }
        }
    }
}
