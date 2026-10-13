package com.advancedplayer.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.palette.graphics.Palette
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.DeckStyle
import com.advancedplayer.data.VisualizerStyle
import com.advancedplayer.data.artModel
import com.advancedplayer.data.db.Track
import com.advancedplayer.playback.PlayerBridge
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.AddToPlaylistDialog
import com.advancedplayer.ui.components.AppSlider
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.FavoriteButton
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.components.albumOrUnknown
import com.advancedplayer.ui.components.formatDuration
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import com.advancedplayer.ui.theme.MonoLabel
import com.advancedplayer.ui.theme.contentOn
import com.advancedplayer.ui.theme.usableAccent
import com.advancedplayer.ui.visualizer.Visualizer
import com.advancedplayer.ui.visualizer.rememberVisualizer
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class PlayerSheet { TIMER, SPEED, MORE, BOOKMARKS, PLAYLIST }

/** Vibrant colour from the current artwork, or null when disabled / unavailable. */
@Composable
fun rememberArtColor(vm: MainViewModel, track: Track?, enabled: Boolean): Color? {
    val c by produceState<Color?>(null, track?.id, track?.customArtPath, enabled) {
        value = if (!enabled || track == null) {
            null
        } else {
            vm.library.art.bitmap(track.id, 160)?.let { bmp ->
                val p = Palette.from(bmp).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb?.let { Color(it) }
            }
        }
    }
    return c
}

@Composable
fun NowPlayingScreen(vm: MainViewModel, navigate: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val trackOrNull by vm.currentTrack.collectAsState()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val position by vm.player.position.collectAsState(0L)
    val ab by PlayerBridge.abLoop.collectAsState()
    val timer by PlayerBridge.sleepTimer.collectAsState()
    val colors = LocalPlayerColors.current
    val track = trackOrNull
    if (track == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val artColor = rememberArtColor(vm, track, settings.albumColors)
    val accentTarget = artColor?.let { usableAccent(it, colors.isDark) } ?: colors.accent
    val accent by animateColorAsState(accentTarget, tween(600), label = "accent")
    val tint by animateColorAsState(artColor ?: colors.accent, tween(600), label = "tint")
    val onAccent = contentOn(accent)

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    // Seek-bar drag position, shared with the vinyl tonearm and cassette reels.
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    val progress: () -> Float = {
        seekPreview ?: if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = if (colors.isDark) 0.55f else 0.35f),
                    0.65f to tint.copy(alpha = 0.08f),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.back), Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.now_playing).uppercase(), style = MonoLabel, color = colors.muted)
                    Text(
                        if (track.isVideo) track.folder.substringAfterLast('/') else albumOrUnknown(track.album),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { sheet = PlayerSheet.MORE }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            }

            // Deck / lyrics
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(showLyrics, label = "deck") { lyrics ->
                    if (lyrics) {
                        LyricsView(vm, track, position, accent, onSeek = { vm.player.seekTo(it) }, modifier = Modifier.fillMaxSize())
                    } else {
                        SwipeableDeck(
                            enabled = settings.swipeGestures,
                            onNext = { vm.player.next() },
                            onPrevious = { vm.player.previous() },
                        ) {
                            Deck(vm, track, settings, state.isPlaying, accent, progress) {
                                navigate(Routes.VIDEO)
                            }
                        }
                    }
                }
            }

            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        artistOrUnknown(track.artist), style = MaterialTheme.typography.bodyLarge, color = colors.muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = !track.isVideo) {
                            navigate(Routes.list(Routes.KIND_ARTIST, track.artist))
                        },
                    )
                }
                FavoriteButton(track.favorite, size = 30.dp) { vm.toggleFavorite(track) }
            }

            Spacer(Modifier.height(8.dp))
            SeekBar(
                position = position, duration = state.durationMs, accent = accent,
                abA = ab.a.takeIf { ab.trackId == track.id }, abB = ab.b.takeIf { ab.trackId == track.id },
                onSeek = { vm.player.seekTo(it) },
                onPreview = { seekPreview = it },
            )

            // Transport controls
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), state.shuffle, accent) { vm.player.toggleShuffle() }
                IconButton(onClick = { vm.player.previous() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = { vm.player.togglePlay() },
                    modifier = Modifier.size(76.dp).shadow(16.dp, CircleShape, ambientColor = accent, spotColor = accent),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = onAccent),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (state.isPlaying) R.string.pause else R.string.play), Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { vm.player.next() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(40.dp))
                }
                ToggleIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    stringResource(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ALL -> R.string.repeat_all
                            Player.REPEAT_MODE_ONE -> R.string.repeat_one
                            else -> R.string.repeat_off
                        },
                    ),
                    state.repeatMode != Player.REPEAT_MODE_OFF, accent,
                ) { vm.player.cycleRepeat() }
            }

            // Secondary actions
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ToggleIcon(Icons.Rounded.Lyrics, stringResource(R.string.lyrics), showLyrics, accent) { showLyrics = !showLyrics }
                ToggleIcon(Icons.Rounded.Speed, stringResource(R.string.speed_pitch), abs(settings.speed - 1f) > 0.001f || abs(settings.pitch - 1f) > 0.001f, accent) {
                    sheet = PlayerSheet.SPEED
                }
                ToggleIcon(Icons.Rounded.Timer, stringResource(R.string.tool_sleep_timer), timer.active, accent) { sheet = PlayerSheet.TIMER }
                ToggleIcon(Icons.Rounded.GraphicEq, stringResource(R.string.tool_visualizer), false, accent) { navigate(Routes.VISUALIZER) }
                ToggleIcon(Icons.Rounded.Equalizer, stringResource(R.string.tool_equalizer), settings.eqEnabled, accent) { navigate(Routes.EQ) }
                ToggleIcon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue), false, accent) { navigate(Routes.QUEUE) }
            }
        }
        if (settings.crtEffect) CrtOverlay()
    }

    val positionProvider = { vm.player.controller?.currentPosition ?: 0L }
    when (sheet) {
        PlayerSheet.TIMER -> SleepTimerSheet { sheet = null }
        PlayerSheet.SPEED -> SpeedPitchSheet(vm, settings) { sheet = null }
        PlayerSheet.BOOKMARKS -> BookmarksSheet(vm, track, positionProvider, { vm.player.seekTo(it) }) { sheet = null }
        PlayerSheet.PLAYLIST -> AddToPlaylistDialog(vm, listOf(track)) { sheet = null }
        PlayerSheet.MORE -> MoreSheet(
            track, positionProvider,
            onDismiss = { sheet = null },
            onBookmarks = { sheet = PlayerSheet.BOOKMARKS },
            onAddToPlaylist = { sheet = PlayerSheet.PLAYLIST },
            onEdit = { sheet = null; navigate(Routes.edit(track.id)) },
            onAlbum = { sheet = null; navigate(Routes.list(Routes.KIND_ALBUM, track.album)) },
            onArtist = { sheet = null; navigate(Routes.list(Routes.KIND_ARTIST, track.artist)) },
            onCarMode = { sheet = null; navigate(Routes.CAR) },
            onEqualizer = { sheet = null; navigate(Routes.EQ) },
        )
        null -> Unit
    }
}

@Composable
private fun Deck(
    vm: MainViewModel,
    track: Track,
    settings: AppSettings,
    playing: Boolean,
    accent: Color,
    progress: () -> Float,
    onOpenVideo: () -> Unit,
) {
    val colors = LocalPlayerColors.current
    if (track.isVideo) {
        val controller = vm.player.controller
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                .clickable(onClick = onOpenVideo),
        ) {
            if (controller != null) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = false; player = controller } },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }
    when (settings.deckStyle) {
        DeckStyle.COVER -> {
            val scale by androidx.compose.animation.core.animateFloatAsState(if (playing) 1f else 0.88f, tween(400), label = "cover")
            Artwork(
                track.artModel(),
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(24.dp, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp), iconSize = 96.dp,
            )
        }
        DeckStyle.CIRCLE -> {
            val handle = rememberVisualizer()
            Visualizer(VisualizerStyle.CIRCLE, handle, accent, Modifier.fillMaxWidth().aspectRatio(1f), artModel = track.artModel())
        }
        DeckStyle.VINYL -> VinylDeck(track.artModel(), playing, accent, progress, Modifier.fillMaxWidth())
        DeckStyle.CASSETTE -> CassetteDeck(
            track.title, artistOrUnknown(track.artist), playing, progress, accent, colors.isDark, Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SwipeableDeck(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / 900f).coerceIn(0f, 0.6f)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = offset.value
                        scope.launch {
                            when {
                                v < -160 -> onNext()
                                v > 160 -> onPrevious()
                            }
                            offset.animateTo(0f, tween(250))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.BottomCenter) {
        IconButton(onClick = onClick) {
            Icon(icon, description, tint = if (active) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        }
        if (active) Box(Modifier.padding(bottom = 2.dp).size(4.dp).clip(CircleShape).background(accent))
    }
}

/**
 * Seek bar that never snaps back: after release it keeps showing the target until the
 * player reports a position near it, so the thumb does not jump to the old spot.
 */
@Composable
fun SeekBar(
    position: Long,
    duration: Long,
    accent: Color,
    abA: Long?,
    abB: Long?,
    onSeek: (Long) -> Unit,
    onPreview: (Float?) -> Unit = {},
) {
    val colors = LocalPlayerColors.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pending) {
        if (pending != null) {
            kotlinx.coroutines.delay(1500)
            pending = null
        }
    }
    val target = pending
    if (target != null && kotlin.math.abs(position - target) < 1200) {
        SideEffect { pending = null }
    }
    val shown = when {
        dragging != null -> (dragging!! * duration).toLong()
        target != null -> target
        else -> position
    }
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        Box(contentAlignment = Alignment.Center) {
            AppSlider(
                value = fraction,
                onValueChange = {
                    dragging = it
                    onPreview(it)
                },
                onValueChangeFinished = {
                    dragging?.let {
                        val ms = (it * duration).toLong()
                        pending = ms
                        onSeek(ms)
                    }
                    dragging = null
                    onPreview(null)
                },
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )
            if (duration > 0 && abA != null) {
                Canvas(Modifier.matchParentSizeCompat()) {
                    val pad = 14.dp.toPx()
                    val w = size.width - pad * 2
                    val y = size.height / 2
                    val ax = pad + w * (abA.toFloat() / duration)
                    val bx = abB?.let { pad + w * (it.toFloat() / duration) }
                    if (bx != null) drawLine(accent.copy(alpha = 0.5f), Offset(ax, y - 14f), Offset(bx, y - 14f), 4f)
                    drawLine(accent, Offset(ax, y - 24f), Offset(ax, y - 6f), 4f)
                    if (bx != null) drawLine(accent, Offset(bx, y - 24f), Offset(bx, y - 6f), 4f)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(formatDuration(shown), fontFamily = MonoFamily, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), fontFamily = MonoFamily, style = MaterialTheme.typography.labelMedium, color = colors.muted)
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(48.dp))
