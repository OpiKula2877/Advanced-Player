package com.advancedplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.advancedplayer.AdvancedPlayerApp
import com.advancedplayer.AppContainer
import com.advancedplayer.MainActivity
import com.advancedplayer.R
import com.advancedplayer.data.AppSettings
import com.advancedplayer.data.SmartList
import com.advancedplayer.data.SongSort
import com.advancedplayer.playback.dsp.AudioFxProcessor
import com.advancedplayer.playback.dsp.AudioTap
import com.advancedplayer.util.ShakeDetector
import com.advancedplayer.widget.PlayerWidget
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private lateinit var container: AppContainer
    private lateinit var player: ExoPlayer
    private lateinit var fadePlayer: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var queueStore: QueueStore
    private var settings = AppSettings()
    private var settingsLoaded = false

    private var loudness: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var shake: ShakeDetector? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private var ignoreDevicesUntil = 0L

    private val currentId = MutableStateFlow<Long?>(null)

    // Crossfade / sleep fade state
    private enum class Fade { IDLE, PREPARED, RUNNING }
    private var fadeState = Fade.IDLE
    private var fadeStart = 0L
    private var fadeLength = 0L
    private var mainGain = 1f
    private var sleepGain = 1f

    // Listening stats
    private var statId: Long? = null
    private var statListened = 0L
    private var statDuration = 0L

    private var saveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as AdvancedPlayerApp).container
        queueStore = QueueStore(this)
        player = buildPlayer(AudioFxProcessor(PlayerBridge.fx, feedTap = true), main = true)
        fadePlayer = buildPlayer(AudioFxProcessor(PlayerBridge.fx, feedTap = false), main = false)
        player.setShuffleOrder(SmartShuffleOrder(0))
        player.addListener(listener)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activityIntent)
            .build()

        attachEffects()
        registerDeviceCallback()
        scope.launch { container.settings.settings.collect { applySettings(it) } }
        scope.launch { restoreQueue() }
        scope.launch { ticker() }
        @kotlin.OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            currentId.flatMapLatest { id -> if (id == null) flowOf(null) else container.library.observeTrack(id) }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.favorite == b?.favorite }
                .collect { t -> updateFavoriteButton(t?.favorite == true) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            saveQueue()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        flushStats()
        PlayerWidget.publish(this, container, null, false)
        shake?.stop()
        deviceCallback?.let { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(it) }
        loudness?.release()
        virtualizer?.release()
        session?.release()
        session = null
        player.release()
        fadePlayer.release()
        AudioTap.clear()
        scope.cancel()
        super.onDestroy()
    }

    // region player setup

    private fun buildPlayer(processor: AudioFxProcessor, main: Boolean): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(processor))
                .build()
        }.setEnableDecoderFallback(true)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // Constant-bitrate seeking makes MP3s without a seek table jump instantly instead of
        // scanning the file from the start.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(attrs, main)
            .setHandleAudioBecomingNoisy(main)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    private fun attachEffects() {
        val sid = player.audioSessionId
        loudness = runCatching { LoudnessEnhancer(sid) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sid) }.getOrNull()
    }

    private fun applySettings(s: AppSettings) {
        val first = !settingsLoaded
        settingsLoaded = true
        settings = s
        PlayerBridge.fx.update(s.eqEnabled, s.eqBands, s.bassBoost, s.reverb)
        player.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
        loudness?.runCatching {
            setTargetGain((s.loudness * 1500).toInt())
            enabled = s.loudness > 0f
        }
        virtualizer?.runCatching {
            if (strengthSupported) setStrength((s.virtualizer * 1000).toInt().toShort())
            enabled = s.virtualizer > 0f
        }
        val params = PlaybackParameters(s.speed, s.pitch)
        if (player.playbackParameters != params) player.playbackParameters = params
        AudioTap.speed = s.speed
        if (s.shakeToSkip) {
            if (shake == null) shake = ShakeDetector(this) { if (player.mediaItemCount > 0) player.seekToNextMediaItem() }.also { it.start() }
        } else {
            shake?.stop()
            shake = null
        }
        if (first) Unit
    }

    // endregion

    // region queue persistence

    private suspend fun restoreQueue() {
        val saved = queueStore.load() ?: return
        val tracks = container.library.tracksByIds(saved.ids)
        if (tracks.isEmpty() || player.mediaItemCount > 0) return
        val currentId = saved.ids.getOrNull(saved.index)
        val index = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        player.setMediaItems(tracks.map(MediaItems::fromTrack), index, saved.positionMs)
        player.repeatMode = saved.repeatMode
        player.shuffleModeEnabled = saved.shuffle
        player.prepare()
    }

    private fun currentQueue(): SavedQueue? {
        if (player.mediaItemCount == 0) return null
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        return SavedQueue(ids, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    private fun saveQueue() {
        currentQueue()?.let { queueStore.save(it) }
    }

    private fun saveQueueSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            saveQueue()
        }
    }

    // endregion

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            // Fires for repeats and replays of the same track too, so each one is a new play.
            restartStats(id)
            currentId.value = id
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled) {
                reshuffleFromCurrent()
            }
            val ab = PlayerBridge.abLoop.value
            if (ab.trackId != null && ab.trackId != id) PlayerBridge.abLoop.value = AbLoop()
            if (id != null && settings.rememberLongPositions && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                scope.launch { restoreLongPosition(id) }
            }
            saveQueueSoon()
            publishWidget()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) reshuffleFromCurrent()
            saveQueueSoon()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = saveQueueSoon()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueueSoon()
            if (player.mediaItemCount == 0) currentId.value = null
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                saveLongPosition()
                saveQueue()
                if (fadeState == Fade.RUNNING && !player.playWhenReady) finishFade()
            }
            publishWidget()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                PlayerBridge.cancelSleepTimer()
                player.pauseAtEndOfMediaItems = false
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Skip unplayable files instead of stopping the whole queue.
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun reshuffleFromCurrent() {
        val count = player.mediaItemCount
        if (count == 0) return
        player.setShuffleOrder(SmartShuffleOrder.startingWith(count, player.currentMediaItemIndex))
    }

    private suspend fun restoreLongPosition(id: Long) {
        val t = container.library.track(id) ?: return
        if (t.durationMs < LONG_TRACK_MS || t.savedPositionMs < 5_000 || t.savedPositionMs > t.durationMs - 10_000) return
        if (player.currentMediaItem?.mediaId == id.toString() && player.currentPosition < 3_000) {
            player.seekTo(t.savedPositionMs)
        }
    }

    private fun saveLongPosition() {
        if (!settings.rememberLongPositions) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur < LONG_TRACK_MS) return
        val pos = player.currentPosition
        val save = if (pos > dur - 10_000) 0L else pos
        container.appScope.launch { container.library.savePosition(id, save) }
    }

    // region ticker: stats, A-B loop, crossfade, sleep timer

    private suspend fun ticker() {
        var last = SystemClock.elapsedRealtime()
        var n = 0
        while (scope.isActive) {
            delay(if (player.isPlaying || fadeState != Fade.IDLE) 100 else 500)
            val now = SystemClock.elapsedRealtime()
            val dt = now - last
            last = now

            val id = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (id != statId) restartStats(id)
            if (player.isPlaying) {
                statListened += dt
                if (player.duration != C.TIME_UNSET) statDuration = player.duration
            }

            checkAbLoop(id)
            checkSleepTimer()
            checkCrossfade(now)
            player.volume = (mainGain * sleepGain).coerceIn(0f, 1f)

            if (player.isPlaying && ++n % 50 == 0) {
                saveLongPosition()
                saveQueue()
            }
        }
    }

    /** Any listen of at least [MIN_LISTEN_MS] counts as one play; each replay counts again. */
    private fun flushStats() {
        val id = statId ?: return
        val listened = statListened
        statListened = 0
        if (listened < MIN_LISTEN_MS) return
        container.appScope.launch { container.library.logPlay(id, listened, counted = true) }
    }

    /** Starts a new listening session, e.g. on a track change or a repeat of the same track. */
    private fun restartStats(id: Long?) {
        flushStats()
        statId = id
        statListened = 0
        statDuration = 0
    }

    private fun checkAbLoop(id: Long?) {
        val ab = PlayerBridge.abLoop.value
        if (ab.active && ab.trackId == id && player.currentPosition >= ab.b!!) player.seekTo(ab.a!!)
    }

    private fun checkSleepTimer() {
        val st = PlayerBridge.sleepTimer.value
        if (player.pauseAtEndOfMediaItems != st.endOfTrack) player.pauseAtEndOfMediaItems = st.endOfTrack
        sleepGain = 1f
        if (st.endOfTrack && player.duration != C.TIME_UNSET) {
            val rem = player.duration - player.currentPosition
            if (rem < 8_000) sleepGain = (rem / 8_000f).coerceIn(0.05f, 1f)
        }
        val remaining = st.remainingMs() ?: return
        if (remaining <= 0) {
            player.pause()
            PlayerBridge.cancelSleepTimer()
            sleepGain = 1f
        } else if (remaining < 20_000) {
            sleepGain = remaining / 20_000f
        }
    }

    private fun checkCrossfade(now: Long) {
        when (fadeState) {
            Fade.RUNNING -> {
                val t = ((now - fadeStart).toFloat() / fadeLength).coerceIn(0f, 1f)
                mainGain = sin(t * PI / 2).toFloat()
                fadePlayer.volume = (cos(t * PI / 2).toFloat() * sleepGain).coerceIn(0f, 1f)
                if (t >= 1f) finishFade()
                return
            }
            else -> Unit
        }
        val cfMs = settings.crossfadeSec * 1000L
        val item = player.currentMediaItem
        val dur = player.duration
        val eligible = cfMs > 0 && player.isPlaying && item != null && !MediaItems.isVideo(item) &&
            !PlayerBridge.sleepTimer.value.endOfTrack && !PlayerBridge.abLoop.value.active &&
            player.repeatMode != Player.REPEAT_MODE_ONE && player.hasNextMediaItem() &&
            dur != C.TIME_UNSET && dur > cfMs * 3
        if (!eligible) {
            if (fadeState == Fade.PREPARED) resetFadePlayer()
            return
        }
        val pos = player.currentPosition
        val remaining = dur - pos
        if (fadeState == Fade.IDLE && remaining <= cfMs + 2_500 && remaining > cfMs) {
            // Pre-buffer the outgoing track so the hand-off is seamless.
            fadePlayer.setMediaItem(item!!)
            fadePlayer.playbackParameters = player.playbackParameters
            fadePlayer.prepare()
            fadePlayer.seekTo(pos + (remaining - cfMs))
            fadePlayer.volume = 0f
            fadeState = Fade.PREPARED
        } else if (fadeState == Fade.PREPARED && remaining <= cfMs) {
            if (fadePlayer.currentMediaItem?.mediaId != item!!.mediaId) {
                resetFadePlayer()
                return
            }
            fadePlayer.seekTo(player.currentPosition)
            fadePlayer.volume = sleepGain
            fadePlayer.play()
            fadeStart = now
            fadeLength = remaining.coerceAtLeast(500)
            fadeState = Fade.RUNNING
            mainGain = 0f
            player.volume = 0f
            player.seekToNextMediaItem()
        }
    }

    private fun finishFade() {
        resetFadePlayer()
        mainGain = 1f
    }

    private fun resetFadePlayer() {
        fadePlayer.stop()
        fadePlayer.clearMediaItems()
        fadeState = Fade.IDLE
    }

    // endregion

    // region headphones / widget / notification

    private fun registerDeviceCallback() {
        val am = getSystemService(AudioManager::class.java)
        ignoreDevicesUntil = SystemClock.elapsedRealtime() + 2_000
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (SystemClock.elapsedRealtime() < ignoreDevicesUntil || !settings.resumeOnConnect) return
                val headphones = addedDevices.any { it.isSink && it.type in HEADPHONE_TYPES }
                if (headphones && !player.isPlaying && player.mediaItemCount > 0) {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }
        }
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        deviceCallback = cb
    }

    private fun publishWidget() {
        PlayerWidget.publish(this, container, player.currentMediaItem, player.isPlaying || player.playWhenReady)
    }

    private fun updateFavoriteButton(favorite: Boolean) {
        val s = session ?: return
        if (player.mediaItemCount == 0) return
        val button = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(if (favorite) R.string.remove_favorite else R.string.add_favorite))
            .setCustomIconResId(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline)
            .setSessionCommand(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        s.setMediaButtonPreferences(ImmutableList.of(button))
    }

    // endregion

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_FAVORITE) {
                val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                    ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                return scope.future {
                    container.library.toggleFavorite(id)
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaItems.folder(ROOT, getString(R.string.app_name)), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val t = mediaId.toLongOrNull()?.let { container.library.track(it) }
            if (t != null) LibraryResult.ofItem(MediaItems.fromTrack(t), null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val ids = mediaItems.mapNotNull { it.mediaId.toLongOrNull() }
            val byId = container.library.tracksByIds(ids).associateBy { it.id }
            mediaItems.mapNotNull { item ->
                item.mediaId.toLongOrNull()?.let { byId[it] }?.let(MediaItems::fromTrack)
                    ?: item.takeIf { it.localConfiguration != null }
            }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val saved = queueStore.load()
            val tracks = saved?.let { container.library.tracksByIds(it.ids) }.orEmpty()
            if (saved == null || tracks.isEmpty()) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            } else {
                val idx = tracks.indexOfFirst { it.id == saved.ids.getOrNull(saved.index) }.coerceAtLeast(0)
                MediaSession.MediaItemsWithStartPosition(tracks.map(MediaItems::fromTrack), idx, saved.positionMs)
            }
        }
    }

    private suspend fun children(parentId: String): List<MediaItem> {
        val lib = container.library
        return when {
            parentId == ROOT -> listOf(
                MediaItems.folder(NODE_FAV, getString(R.string.smart_favorites), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                MediaItems.folder(NODE_RECENT, getString(R.string.smart_recently_played), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                MediaItems.folder(NODE_PLAYLISTS, getString(R.string.tab_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                MediaItems.folder(NODE_ALBUMS, getString(R.string.tab_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                MediaItems.folder(NODE_ARTISTS, getString(R.string.tab_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                MediaItems.folder(NODE_SONGS, getString(R.string.tab_songs), MediaMetadata.MEDIA_TYPE_PLAYLIST),
            )
            parentId == NODE_FAV -> lib.smart(SmartList.FAVORITES).first().map(MediaItems::fromTrack)
            parentId == NODE_RECENT -> lib.smart(SmartList.RECENTLY_PLAYED).first().map(MediaItems::fromTrack)
            parentId == NODE_SONGS -> lib.songs(SongSort.TITLE).first().map(MediaItems::fromTrack)
            parentId == NODE_PLAYLISTS -> lib.playlistSummaries().first().map {
                MediaItems.folder("pl:${it.id}", it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            parentId == NODE_ALBUMS -> lib.albums().first().map {
                MediaItems.folder("al:${it.album}", it.album.ifEmpty { getString(R.string.unknown_album) }, MediaMetadata.MEDIA_TYPE_ALBUM)
            }
            parentId == NODE_ARTISTS -> lib.artists().first().map {
                MediaItems.folder("ar:${it.artist}", it.artist.ifEmpty { getString(R.string.unknown_artist) }, MediaMetadata.MEDIA_TYPE_ARTIST)
            }
            parentId.startsWith("pl:") -> parentId.removePrefix("pl:").toLongOrNull()
                ?.let { lib.playlistTracks(it).first().map(MediaItems::fromTrack) }.orEmpty()
            parentId.startsWith("al:") -> lib.albumTracks(parentId.removePrefix("al:")).first().map(MediaItems::fromTrack)
            parentId.startsWith("ar:") -> lib.artistTracks(parentId.removePrefix("ar:")).first().map(MediaItems::fromTrack)
            else -> emptyList()
        }
    }

    companion object {
        const val CMD_FAVORITE = "com.advancedplayer.FAVORITE"
        const val LONG_TRACK_MS = 20 * 60 * 1000L
        const val MIN_LISTEN_MS = 5_000L
        private const val ROOT = "root"
        private const val NODE_FAV = "fav"
        private const val NODE_RECENT = "recent"
        private const val NODE_PLAYLISTS = "playlists"
        private const val NODE_ALBUMS = "albums"
        private const val NODE_ARTISTS = "artists"
        private const val NODE_SONGS = "songs"

        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            26, // TYPE_BLE_HEADSET (API 31)
        )
    }
}
