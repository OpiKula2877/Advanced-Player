package com.advancedplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.advancedplayer.R
import com.advancedplayer.data.artModel
import com.advancedplayer.ui.MainViewModel
import com.advancedplayer.ui.components.Artwork
import com.advancedplayer.ui.components.ChipButton
import com.advancedplayer.ui.theme.LocalPlayerColors

@Composable
fun EditTrackScreen(vm: MainViewModel, trackId: Long, onBack: () -> Unit) {
    val track by remember(trackId) { vm.library.observeTrack(trackId) }.collectAsState(null)
    val colors = LocalPlayerColors.current
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(track) {
        val t = track ?: return@LaunchedEffect
        if (!loaded) {
            title = t.title
            artist = t.artist
            album = t.album
            genre = t.genre
            year = if (t.year > 0) t.year.toString() else ""
            loaded = true
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val t = track
        if (uri != null && t != null) vm.launch { vm.library.setTrackArt(t, uri) }
    }
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = track
        if (uri != null && t != null) vm.launch { vm.library.setLyricsFile(t, uri) }
    }

    ScreenScaffold(stringResource(R.string.edit_info), onBack) { padding ->
        val t = track ?: return@ScreenScaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(t.artModel(), Modifier.size(200.dp), shape = RoundedCornerShape(10.dp), isVideo = t.isVideo, iconSize = 56.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(stringResource(R.string.change_image), Icons.Rounded.Image) {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (t.customArtPath != null) {
                    ChipButton(stringResource(R.string.remove_image), Icons.Rounded.HideImage) {
                        vm.launch { vm.library.setTrackArt(t, null) }
                    }
                }
            }
            Field(stringResource(R.string.sort_title), title) { title = it }
            Field(stringResource(R.string.sort_artist), artist) { artist = it }
            Field(stringResource(R.string.field_album), album) { album = it }
            Field(stringResource(R.string.field_genre), genre) { genre = it }
            OutlinedTextField(
                year, { year = it.filter(Char::isDigit).take(4) }, singleLine = true,
                label = { Text(stringResource(R.string.field_year)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(stringResource(R.string.attach_lyrics), Icons.Rounded.Lyrics) { lrcPicker.launch(arrayOf("*/*")) }
                if (t.lyricsUri != null) {
                    ChipButton(stringResource(R.string.remove_lyrics)) { vm.launch { vm.library.setLyricsFile(t, null) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.launch {
                        vm.library.updateTrack(
                            t.copy(
                                title = title.trim().ifEmpty { t.title },
                                artist = artist.trim(),
                                album = album.trim(),
                                genre = genre.trim(),
                                year = year.toIntOrNull() ?: 0,
                                edited = true,
                            ),
                        )
                    }
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, singleLine = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
