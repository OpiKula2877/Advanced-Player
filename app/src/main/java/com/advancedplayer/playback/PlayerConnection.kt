package com.advancedplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.advancedplayer.data.LibraryRepository
import com.advancedplayer.data.SettingsRepository
import com.advancedplayer.data.db.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class PlayerUiState(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Track ids in play order (respects shuffle). */
    val queueIds: List<Long> = emptyList(),
    /** Playlist index of each entry in [queueIds]. */
    val queueIndices: List<Int> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val isVideo: Boolean = false,
) {
    val hasMedia get() = currentId != null
}

/** UI-side wrapper around a [MediaController] connected to [PlaybackService]. */
class PlayerConnection(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Emits the playback position while collected. */
    val position: Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0L)
            delay(100)
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val tl = c.currentTimeline
        val order = ArrayList<Int>(c.mediaItemCount)
        if (!tl.isEmpty) {
            var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && order.size < tl.windowCount) {
                order += i
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        val item = c.currentMediaItem
        _state.value = PlayerUiState(
            connected = true,
            currentId = item?.mediaId?.toLongOrNull(),
            title = c.mediaMetadata.title?.toString().orEmpty(),
            artist = c.mediaMetadata.artist?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queueIds = order.mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() },
            queueIndices = order,
            currentIndex = c.currentMediaItemIndex,
            isVideo = MediaItems.isVideo(item),
        )
    }

    // region actions

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val start = if (shuffle) Random.nextInt(tracks.size) else startIndex.coerceIn(0, tracks.size - 1)
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map(MediaItems::fromTrack), start, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playTracks(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map(MediaItems::fromTrack))
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playTracks(tracks)
        c.addMediaItems(tracks.map(MediaItems::fromTrack))
    }

    @OptIn(UnstableApi::class)
    fun togglePlay() {
        controller?.let { Util.handlePlayPauseButtonAction(it) }
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun seekToQueueItem(index: Int) = controller?.let {
        it.seekTo(index, 0)
        it.play()
    }

    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun moveQueueItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun removeQueueItem(index: Int) = controller?.removeMediaItem(index)

    fun clearQueueExceptCurrent() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (cur == C.INDEX_UNSET) return
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)
    }

    suspend fun setSpeedPitch(speed: Float, pitch: Float) = settings.setSpeedPitch(speed, pitch)

    suspend fun toggleCurrentFavorite() {
        state.value.currentId?.let { library.toggleFavorite(it) }
    }

    // endregion
}
