package com.advancedplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.advancedplayer.AdvancedPlayerApp
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.ScanState
import com.advancedplayer.data.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AdvancedPlayerApp).container
    val library = container.library
    val settingsRepo = container.settings
    val player = container.player

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playerState = player.state

    val currentTrack: StateFlow<Track?> = player.state
        .map { it.currentId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else library.observeTrack(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val scanState: StateFlow<ScanState> = library.scanState
    val trackCount = library.trackCount().stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    val playlists = library.playlistSummaries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun toast(text: String) {
        _messages.tryEmit(text)
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(block = block)
    }

    fun rescan() = launch { library.rescan() }
    fun addFolder(uri: Uri) = launch { library.addFolder(uri) }
    fun addFiles(uris: List<Uri>) = launch { library.addFiles(uris) }

    fun play(tracks: List<Track>, index: Int = 0) = player.playTracks(tracks, index, shuffle = false)
    fun shufflePlay(tracks: List<Track>) = player.playTracks(tracks, shuffle = true)

    fun toggleFavorite(track: Track) = launch { library.setFavorite(track.id, !track.favorite) }

    fun addToPlaylist(playlistId: Long, name: String, tracks: List<Track>, message: String) = launch {
        library.addToPlaylist(playlistId, tracks.map { it.id })
        toast(message.format(name))
    }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) = launch {
        if (name.isNotBlank()) library.createPlaylist(name, tracks.map { it.id })
    }
}
