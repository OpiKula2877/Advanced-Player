package com.advancedplayer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.advancedplayer.R
import com.advancedplayer.data.ArtModel
import com.advancedplayer.data.artModel
import com.advancedplayer.data.db.Track
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import java.io.File

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun artistOrUnknown(artist: String) = artist.ifEmpty { stringResource(R.string.unknown_artist) }

@Composable
fun albumOrUnknown(album: String) = album.ifEmpty { stringResource(R.string.unknown_album) }

/** Artwork with a tinted placeholder underneath; [model] may be an [ArtModel] or a [File]. */
@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    isVideo: Boolean = false,
    iconSize: Dp = 22.dp,
) {
    val colors = LocalPlayerColors.current
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.cardHover, colors.accent.copy(alpha = 0.28f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(iconSize),
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun playlistArtModel(customArtPath: String?, coverId: Long?): Any? =
    customArtPath?.let { File(it) } ?: coverId?.let { ArtModel(it) }

/** Three animated bars shown next to the playing track (a small retro touch). */
@Composable
fun NowPlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(420, 300, 520).map { d ->
        transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "bar",
        )
    }
    Canvas(modifier.size(14.dp)) {
        val w = size.width / 5
        phases.forEachIndexed { i, p ->
            val h = size.height * (if (playing) p.value else 0.3f)
            drawRoundRect(
                color, topLeft = Offset(i * 2 * w, size.height - h), size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    extraMenu: List<MenuEntry> = emptyList(),
) {
    val colors = LocalPlayerColors.current
    val actions = LocalTrackActions.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) {
                    NowPlayingBars(isPlaying, colors.accent)
                } else {
                    Text(
                        "${index + 1}", style = MaterialTheme.typography.bodyMedium,
                        fontFamily = MonoFamily, color = colors.muted,
                    )
                }
            }
        } else {
            Artwork(track.artModel(), Modifier.size(48.dp), isVideo = track.isVideo)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) colors.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && index == null) {
                    NowPlayingBars(isPlaying, colors.accent, Modifier.padding(end = 6.dp))
                }
                Text(
                    "${artistOrUnknown(track.artist)} · ${formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            FavoriteButton(track.favorite) { actions.toggleFavorite(track) }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more), tint = colors.muted)
            }
            TrackMenu(track, expanded = menu, onDismiss = { menu = false }, extra = extraMenu)
        }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, onClick: () -> Unit) {
    val colors = LocalPlayerColors.current
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
            tint = if (favorite) colors.accent else colors.muted,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier, leading: @Composable () -> Unit = {}) {
    val colors = LocalPlayerColors.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle_play), tint = colors.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        ) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String? = null, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val colors = LocalPlayerColors.current
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(colors.card),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = colors.accent, modifier = Modifier.size(40.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        Spacer(Modifier.height(4.dp))
        actions()
    }
}

@Composable
fun ChipButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50)) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
