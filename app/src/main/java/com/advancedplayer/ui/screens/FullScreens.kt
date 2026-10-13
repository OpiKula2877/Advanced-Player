package com.advancedplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.artModel
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.FavoriteButton
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.player.rememberArtColor
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoLabel
import com.advancedplayer.ui.theme.contentOn
import com.advancedplayer.ui.theme.usableAccent
import com.advancedplayer.ui.visualizer.Visualizer
import com.advancedplayer.ui.visualizer.rememberVisualizer
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Lets a screen rotate freely (or forces an orientation) and restores the app default on exit. */
@Composable
fun ScreenOrientation(orientation: Int) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(orientation) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose { activity.requestedOrientation = previous }
    }
}

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun Immersive() {
    val activity = LocalContext.current.findActivity() ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
fun VisualizerScreen(vm: MainViewModel, onBack: () -> Unit) {
    ScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    KeepScreenOn()
    Immersive()
    val settings = vm.settings.collectAsState().value ?: AppSettings()
    val track by vm.currentTrack.collectAsState()
    val state by vm.playerState.collectAsState()
    val colors = LocalPlayerColors.current
    val artColor = rememberArtColor(vm, track, settings.albumColors)
    val color = artColor?.let { usableAccent(it, true) } ?: colors.accent
    val styles = visualizerOptions
    val pager = rememberPagerState(initialPage = styles.indexOfFirst { it.first == settings.visualizerStyle }.coerceAtLeast(0)) { styles.size }
    val handle = rememberVisualizer()
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, touch) {
        if (controls) {
            delay(3500)
            controls = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF050505))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
                touch++
            },
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val style = styles[page].first
            Visualizer(
                style, handle, color,
                Modifier.fillMaxSize().padding(if (style == com.advancedplayer.data.VisualizerStyle.BARS) 0.dp else 24.dp),
                artModel = track?.artModel(),
            )
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                    Column {
                        Text(stringResource(styles[pager.currentPage].second).uppercase(), style = MonoLabel, color = color)
                        Text(stringResource(R.string.visualizer_hint), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(track?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artistOrUnknown(track?.artist.orEmpty()), color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                    IconButton(onClick = { vm.player.previous(); touch++ }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), tint = Color.White) }
                    IconButton(onClick = { vm.player.togglePlay(); touch++ }) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { vm.player.next(); touch++ }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun CarModeScreen(vm: MainViewModel, onBack: () -> Unit) {
    KeepScreenOn()
    val track by vm.currentTrack.collectAsState()
    val state by vm.playerState.collectAsState()
    val position by vm.player.position.collectAsState(0L)
    val colors = LocalPlayerColors.current
    val accent = colors.accent
    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tool_car_mode).uppercase(), style = MonoLabel, color = accent, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.car_exit), color = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            track?.title ?: stringResource(R.string.nothing_playing), color = Color.White, fontSize = 40.sp,
            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp,
        )
        Text(artistOrUnknown(track?.artist.orEmpty()), color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        val progress = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).background(accent))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            BigButton(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Color(0xFF1E1E1E), Color.White) { vm.player.previous() }
            BigButton(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.isPlaying) R.string.pause else R.string.play), accent, contentOn(accent), 132,
            ) { vm.player.togglePlay() }
            BigButton(Icons.Rounded.SkipNext, stringResource(R.string.next), Color(0xFF1E1E1E), Color.White) { vm.player.next() }
        }
        Spacer(Modifier.height(24.dp))
        track?.let { t ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FavoriteButton(t.favorite, size = 48.dp) { vm.toggleFavorite(t) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BigButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: Color, fg: Color, size: Int = 104, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, modifier = Modifier.size(size.dp), shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = fg),
    ) { Icon(icon, desc, Modifier.size((size * 0.5f).dp)) }
}

@Composable
fun VideoScreen(vm: MainViewModel, onBack: () -> Unit) {
    ScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    KeepScreenOn()
    Immersive()
    val controller = vm.player.controller
    val track by vm.currentTrack.collectAsState()
    var overlay by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (controller != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller
                        setShowNextButton(true)
                        setShowPreviousButton(true)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> overlay = v == android.view.View.VISIBLE })
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    vm.player.controller?.pause()
                    onBack()
                }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Text(
                    track?.title.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_only), color = Color.White)
                }
            }
        }
    }
}
