package com.advancedplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advancedplayer.R
import com.advancedplayer.data.ArtModel
import com.advancedplayer.data.LibraryRepository
import com.advancedplayer.data.db.StatsTotals
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.Routes
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.EmptyState
import com.advancedplayer.ui.components.SectionHeader
import com.advancedplayer.ui.components.artistOrUnknown
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import java.util.Calendar

@Composable
private fun formatListening(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) {
        stringResource(R.string.minutes_short, minutes.toInt())
    } else {
        stringResource(R.string.hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

@Composable
fun StatsScreen(vm: MainViewModel, navigate: (String) -> Unit, onBack: () -> Unit) {
    val colors = LocalPlayerColors.current
    val totals by remember { vm.library.statsTotals(0) }.collectAsState(StatsTotals(0, 0, 0))
    val topTracks by remember { vm.library.topTracks(0, 10) }.collectAsState(emptyList())
    val topArtists by remember { vm.library.topArtists(0, 5) }.collectAsState(emptyList())
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val statsDays = vm.settings.collectAsState().value?.statsDays ?: 30
    val daily by remember(statsDays) { vm.library.daily(statsDays) }.collectAsState(emptyList())

    ScreenScaffold(stringResource(R.string.tool_stats), onBack) { padding ->
        if (totals.listenedMs == 0L) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.BarChart, stringResource(R.string.stats_empty))
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(stringResource(R.string.stats_total_time), formatListening(totals.listenedMs), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stats_total_plays), totals.plays.toString(), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stats_unique), totals.uniqueTracks.toString(), Modifier.weight(1f))
                }
            }
            item { DailyChart(daily, statsDays, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
            item {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(colors.accent, lerp(colors.accent, Color(0xFF4B2CA0), 0.7f))))
                        .clickable { navigate(Routes.WRAPPED) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.stats_your_year, year), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
            }
            item { SectionHeader(stringResource(R.string.stats_top_tracks)) }
            itemsIndexed(topTracks, key = { _, t -> t.trackId }) { i, t ->
                RankRow(i, ArtModel(t.trackId), t.title, artistOrUnknown(t.artist), pluralStringResource(R.plurals.plays_count, t.plays, t.plays))
            }
            item { SectionHeader(stringResource(R.string.stats_top_artists)) }
            itemsIndexed(topArtists, key = { _, a -> a.artist }) { i, a ->
                RankRow(i, ArtModel(a.coverId), artistOrUnknown(a.artist), formatListening(a.listenedMs), pluralStringResource(R.plurals.plays_count, a.plays, a.plays), circle = true)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    val colors = LocalPlayerColors.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(colors.card).padding(14.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = MonoFamily, color = colors.accent, maxLines = 2)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 2)
    }
}

@Composable
private fun RankRow(index: Int, art: Any, title: String, subtitle: String, trailing: String, circle: Boolean = false) {
    val colors = LocalPlayerColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(index + 1), fontFamily = MonoFamily, color = if (index < 3) colors.accent else colors.muted, modifier = Modifier.width(32.dp))
        Artwork(art, Modifier.size(48.dp), shape = if (circle) CircleShape else RoundedCornerShape(6.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(trailing, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** "Your year" story cards in the spirit of a yearly recap. */
@Composable
fun WrappedScreen(vm: MainViewModel, onBack: () -> Unit) {
    val colors = LocalPlayerColors.current
    val since = remember { LibraryRepository.startOfYear() }
    val totals by remember { vm.library.statsTotals(since) }.collectAsState(StatsTotals(0, 0, 0))
    val topTracks by remember { vm.library.topTracks(since, 5) }.collectAsState(emptyList())
    val topArtists by remember { vm.library.topArtists(since, 5) }.collectAsState(emptyList())
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val pager = rememberPagerState { 4 }
    val palette = listOf(
        listOf(colors.accent, Color(0xFF121212)),
        listOf(Color(0xFFE91E8C), Color(0xFF2A0F3D)),
        listOf(Color(0xFF2E77D0), Color(0xFF0B1A33)),
        listOf(Color(0xFFFF7A1A), Color(0xFF3A1600)),
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(palette[page])).statusBarsPadding().navigationBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> {
                        Text(stringResource(R.string.stats_your_year, year), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 48.sp)
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.wrapped_minutes), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
                        Text("${totals.listenedMs / 60_000}", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Black, fontFamily = MonoFamily)
                        Text(stringResource(R.string.wrapped_minutes_unit), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    1 -> {
                        Text(stringResource(R.string.wrapped_top_song), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        topTracks.firstOrNull()?.let { t ->
                            Artwork(ArtModel(t.trackId), Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(12.dp), iconSize = 72.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(t.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, maxLines = 2)
                            Text(artistOrUnknown(t.artist), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
                        }
                        topTracks.drop(1).forEachIndexed { i, t ->
                            Text("${i + 2}. ${t.title}", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    2 -> {
                        Text(stringResource(R.string.wrapped_top_artist), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        topArtists.firstOrNull()?.let { a ->
                            Artwork(ArtModel(a.coverId), Modifier.size(240.dp).align(Alignment.CenterHorizontally), shape = CircleShape, iconSize = 72.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(artistOrUnknown(a.artist), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Text(formatListening(a.listenedMs), color = Color.White.copy(alpha = 0.8f), fontFamily = MonoFamily, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.wrapped_songs), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
                        Text("${totals.uniqueTracks}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black, fontFamily = MonoFamily)
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(R.string.wrapped_total_plays), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleLarge)
                        Text("${totals.plays}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, fontFamily = MonoFamily)
                    }
                }
            }
        }
        // Story-style progress segments
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                )
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(top = 16.dp).align(Alignment.TopEnd)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
        }
    }
}
