package com.advancedplayer.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.rememberScrollState as rememberScroll
import androidx.compose.foundation.verticalScroll
import com.advancedplayer.BuildConfig
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.DeckStyle
import com.advancedplayer.data.ScanState
import com.advancedplayer.data.ThemeMode
import com.advancedplayer.data.VisualizerStyle
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.AppSlider
import com.advancedplayer.ui.theme.AccentPresets
import com.advancedplayer.ui.theme.LocalPlayerColors
import com.advancedplayer.ui.theme.MonoFamily
import com.advancedplayer.ui.theme.contentOn
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsState().value ?: AppSettings()
    val colors = LocalPlayerColors.current
    val scan by vm.scanState.collectAsState()
    val (pickFolder, pickFiles) = rememberSourcePickers(vm)
    var colorPicker by remember { mutableStateOf(false) }
    var excludeDialog by remember { mutableStateOf(false) }
    var resetStats by remember { mutableStateOf(false) }
    fun set(block: suspend () -> Unit) = vm.launch { block() }

    ScreenScaffold(stringResource(R.string.settings), onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScroll()).padding(bottom = 40.dp)) {
            // Appearance
            Section(stringResource(R.string.settings_appearance))
            Label(stringResource(R.string.theme))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                val opts = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light)
                opts.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { set { vm.settingsRepo.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.accent, activeContentColor = colors.onAccent),
                    ) { Text(stringResource(label)) }
                }
            }
            Label(stringResource(R.string.accent_color))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentPresets.forEach { opt ->
                    val c = Color(opt.argb)
                    Swatch(c, selected = s.accent == opt.argb) { set { vm.settingsRepo.setAccent(opt.argb) } }
                }
                val custom = AccentPresets.none { it.argb == s.accent }
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(if (custom) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { colorPicker = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Colorize, stringResource(R.string.custom_color), tint = Color.White) }
            }
            SwitchRow(stringResource(R.string.album_colors), stringResource(R.string.album_colors_desc), s.albumColors) {
                set { vm.settingsRepo.setAlbumColors(it) }
            }
            Label(stringResource(R.string.deck_style))
            ChoiceChips(
                listOf(DeckStyle.COVER to R.string.deck_cover, DeckStyle.CIRCLE to R.string.deck_circle, DeckStyle.VINYL to R.string.deck_vinyl, DeckStyle.CASSETTE to R.string.deck_cassette),
                s.deckStyle,
            ) { set { vm.settingsRepo.setDeckStyle(it) } }
            SwitchRow(stringResource(R.string.crt_effect), stringResource(R.string.crt_effect_desc), s.crtEffect) {
                set { vm.settingsRepo.setCrtEffect(it) }
            }
            Label(stringResource(R.string.default_visualizer))
            ChoiceChips(visualizerOptions, s.visualizerStyle) { set { vm.settingsRepo.setVisualizerStyle(it) } }

            // Language
            Section(stringResource(R.string.language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').ifEmpty { "en" }
            ChoiceChips(
                listOf("en" to R.string.language_en, "cs" to R.string.language_cs, "sk" to R.string.language_sk),
                current,
            ) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) }

            // Library
            Section(stringResource(R.string.nav_library))
            SwitchRow(stringResource(R.string.scan_device_setting), stringResource(R.string.scan_device_desc), s.scanMediaStore) {
                set {
                    vm.settingsRepo.setScanMediaStore(it)
                    vm.library.rescan()
                }
            }
            SwitchRow(stringResource(R.string.include_videos), null, s.includeVideos) {
                set {
                    vm.settingsRepo.setIncludeVideos(it)
                    vm.library.rescan()
                }
            }
            var minDur by remember(s.minDurationSec) { mutableFloatStateOf(s.minDurationSec.toFloat()) }
            Label(stringResource(R.string.min_duration, minDur.roundToInt()))
            AppSlider(
                value = minDur, onValueChange = { minDur = it }, valueRange = 0f..120f, steps = 23,
                onValueChangeFinished = {
                    set {
                        vm.settingsRepo.setMinDuration(minDur.roundToInt())
                        vm.library.rescan()
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Label(stringResource(R.string.folders))
            if (s.folderUris.isEmpty()) {
                Text(stringResource(R.string.no_folders), color = colors.muted, modifier = Modifier.padding(horizontal = 20.dp))
            }
            s.folderUris.forEach { uri ->
                PathRow(Icons.Rounded.Folder, treeName(uri)) { set { vm.library.removeFolder(uri) } }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = pickFolder) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_add_folder))
                }
                TextButton(onClick = pickFiles) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pick_files))
                }
            }
            Label(stringResource(R.string.excluded_folders))
            s.excludedFolders.forEach { path ->
                PathRow(Icons.Rounded.FolderOff, path) {
                    set {
                        vm.settingsRepo.removeExcluded(path)
                        vm.library.rescan()
                    }
                }
            }
            TextButton(onClick = { excludeDialog = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.exclude_folder))
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = scan !is ScanState.Running) { vm.rescan() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = colors.accent)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.rescan), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    when (val st = scan) {
                        is ScanState.Running -> stringResource(R.string.scanning)
                        is ScanState.Done -> stringResource(R.string.scan_done, st.count)
                        else -> ""
                    },
                    color = colors.muted, style = MaterialTheme.typography.bodySmall,
                )
            }

            // Playback
            Section(stringResource(R.string.settings_playback))
            var cf by remember(s.crossfadeSec) { mutableFloatStateOf(s.crossfadeSec.toFloat()) }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.crossfade), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (cf.roundToInt() == 0) stringResource(R.string.crossfade_off) else stringResource(R.string.seconds_value, cf.roundToInt()),
                    fontFamily = MonoFamily, color = colors.accent,
                )
            }
            AppSlider(
                value = cf, onValueChange = { cf = it }, valueRange = 0f..12f, steps = 11,
                onValueChangeFinished = { set { vm.settingsRepo.setCrossfade(cf.roundToInt()) } },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SwitchRow(stringResource(R.string.remember_position), stringResource(R.string.remember_position_desc), s.rememberLongPositions) {
                set { vm.settingsRepo.setRememberPositions(it) }
            }
            SwitchRow(stringResource(R.string.pause_on_disconnect), null, s.pauseOnDisconnect) { set { vm.settingsRepo.setPauseOnDisconnect(it) } }
            SwitchRow(stringResource(R.string.resume_on_connect), null, s.resumeOnConnect) { set { vm.settingsRepo.setResumeOnConnect(it) } }

            // Gestures
            Section(stringResource(R.string.settings_gestures))
            SwitchRow(stringResource(R.string.swipe_gestures), null, s.swipeGestures) { set { vm.settingsRepo.setSwipeGestures(it) } }
            SwitchRow(stringResource(R.string.shake_to_skip), null, s.shakeToSkip) { set { vm.settingsRepo.setShakeToSkip(it) } }

            // Stats & about
            Section(stringResource(R.string.tool_stats))
            Label(stringResource(R.string.stats_period))
            ChoiceChips(listOf(7, 14, 30, 90).map { it to R.string.days_value }, s.statsDays, labelArg = true) {
                set { vm.settingsRepo.setStatsDays(it) }
            }
            TextButton(onClick = { resetStats = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.reset_stats), color = MaterialTheme.colorScheme.error)
            }
            Section(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                stringResource(R.string.about_text, BuildConfig.VERSION_NAME), color = colors.muted,
                fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    if (colorPicker) {
        ColorPickerDialog(Color(s.accent), onDismiss = { colorPicker = false }) { c ->
            colorPicker = false
            set { vm.settingsRepo.setAccent(c.toArgb().toLong() and 0xFFFFFFFFL) }
        }
    }
    if (excludeDialog) {
        val folders by remember { vm.library.folders() }.collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = { excludeDialog = false },
            title = { Text(stringResource(R.string.exclude_folder)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(folders.filter { it.folder.startsWith("/") }, key = { it.folder }) { f ->
                        Text(
                            f.folder, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                excludeDialog = false
                                set {
                                    vm.settingsRepo.addExcluded(f.folder)
                                    vm.library.rescan()
                                }
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { excludeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (resetStats) {
        AlertDialog(
            onDismissRequest = { resetStats = false },
            title = { Text(stringResource(R.string.reset_stats_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    resetStats = false
                    set { vm.library.resetStats() }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { resetStats = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

val visualizerOptions = listOf(
    VisualizerStyle.BARS to R.string.vis_bars,
    VisualizerStyle.CIRCLE to R.string.vis_circle,
    VisualizerStyle.WAVE to R.string.vis_wave,
    VisualizerStyle.MIRROR to R.string.vis_mirror,
    VisualizerStyle.PARTICLES to R.string.vis_particles,
    VisualizerStyle.VU to R.string.vis_vu,
)

private fun treeName(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(uri)).replace(':', '/')
}.getOrDefault(uri)

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelLarge, color = LocalPlayerColors.current.accent,
        fontFamily = MonoFamily, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalPlayerColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

@Composable
private fun <T> ChoiceChips(options: List<Pair<T, Int>>, selected: T, labelArg: Boolean = false, onSelect: (T) -> Unit) {
    val colors = LocalPlayerColors.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) colors.accent else colors.card)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(if (labelArg) stringResource(label, value as Any) else stringResource(label), color = if (sel) colors.onAccent else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PathRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalPlayerColors.current.muted)
        Spacer(Modifier.width(14.dp))
        Text(text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.delete)) }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(color)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = contentOn(color))
    }
}

@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(h, sat, v)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color)) },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "#%06X".format(color.toArgb() and 0xFFFFFF), color = contentOn(color), fontFamily = MonoFamily,
                    )
                }
                Spacer(Modifier.height(12.dp))
                GradientSlider(h / 360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { h = it * 360f }
                GradientSlider(sat, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v)))) { sat = it }
                GradientSlider(v, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, sat, 1f)))) { v = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GradientSlider(value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
        Slider(
            value = value, onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}
