package com.advancedplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.advancedplayer.data.db.AppDatabase
import com.advancedplayer.data.db.Bookmark
import com.advancedplayer.data.db.PlayEvent
import com.advancedplayer.data.db.Playlist
import com.advancedplayer.data.db.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class SmartList { FAVORITES, RECENTLY_ADDED, MOST_PLAYED, FORGOTTEN, RECENTLY_PLAYED }

sealed interface ScanState {
    data object Idle : ScanState
    data object Running : ScanState
    data class Done(val count: Int) : ScanState
}

class LibraryRepository(
    private val context: Context,
    val db: AppDatabase,
    private val settings: SettingsRepository,
    val art: ArtRepository,
) {
    private val scanner = MediaScanner(context, db)
    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private val tracks get() = db.tracks()
    private val playlists get() = db.playlists()

    suspend fun rescan() {
        if (scanMutex.isLocked) return
        scanMutex.withLock {
            _scanState.value = ScanState.Running
            val n = runCatching { withContext(Dispatchers.IO) { scanner.scan(settings.current()) } }
                .onFailure { android.util.Log.e("LibraryScan", "Scan failed", it) }
                .getOrDefault(0)
            android.util.Log.i("LibraryScan", "Scan finished: $n tracks")
            _scanState.value = ScanState.Done(n)
        }
    }

    suspend fun addFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.addFolder(uri.toString())
        rescan()
    }

    suspend fun removeFolder(uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.removeFolder(uri)
        rescan()
    }

    suspend fun addFiles(uris: List<Uri>): Int {
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return withContext(Dispatchers.IO) { scanner.addFiles(uris) }
    }

    fun songs(sort: SongSort): Flow<List<Track>> = when (sort) {
        SongSort.TITLE -> tracks.songsByTitle()
        SongSort.ARTIST -> tracks.songsByArtist()
        SongSort.DATE_ADDED -> tracks.songsByDate()
        SongSort.DURATION -> tracks.songsByDuration()
        SongSort.MOST_PLAYED -> tracks.songsByPlays()
    }

    fun videos() = tracks.videos()
    fun albums() = tracks.albums()
    fun artists() = tracks.artists()
    fun folders() = tracks.folders()
    fun trackCount() = tracks.count()
    fun playlistSummaries() = playlists.summaries()
    fun observeTrack(id: Long) = tracks.observe(id)
    fun observeTracks(ids: List<Long>): Flow<List<Track>> = if (ids.isEmpty()) flowOf(emptyList()) else tracks.observeByIds(ids)
    fun observePlaylist(id: Long) = playlists.observe(id)
    fun search(q: String): Flow<List<Track>> = if (q.isBlank()) flowOf(emptyList()) else tracks.search(q.trim())

    fun smart(list: SmartList): Flow<List<Track>> = when (list) {
        SmartList.FAVORITES -> tracks.favorites()
        SmartList.RECENTLY_ADDED -> tracks.recentlyAdded()
        SmartList.MOST_PLAYED -> tracks.mostPlayed()
        SmartList.RECENTLY_PLAYED -> tracks.recentlyPlayed()
        SmartList.FORGOTTEN -> tracks.forgotten(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    }

    fun albumTracks(album: String) = tracks.album(album)
    fun artistTracks(artist: String) = tracks.artist(artist)
    fun folderTracks(folder: String) = tracks.folder(folder)
    fun playlistTracks(id: Long) = playlists.tracks(id)

    suspend fun track(id: Long) = tracks.byId(id)
    suspend fun trackByUri(uri: String) = tracks.byUri(uri)
    suspend fun tracksByIds(ids: List<Long>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val map = ids.chunked(900).flatMap { tracks.byIds(it) }.associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }

    suspend fun setFavorite(id: Long, fav: Boolean) = tracks.setFavorite(id, fav)
    suspend fun toggleFavorite(id: Long): Boolean {
        val t = tracks.byId(id) ?: return false
        tracks.setFavorite(id, !t.favorite)
        return !t.favorite
    }

    suspend fun updateTrack(track: Track) = tracks.update(track)

    suspend fun setTrackArt(track: Track, source: Uri?) {
        val newPath = source?.let { art.importCustom(it, "t${track.id}") }
        art.deleteCustom(track.customArtPath)
        tracks.update(track.copy(customArtPath = newPath, edited = true))
    }

    suspend fun setLyricsFile(track: Track, uri: Uri?) {
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        tracks.update(track.copy(lyricsUri = uri?.toString()))
    }

    // Playlists

    suspend fun allPlaylists() = playlists.all()
    suspend fun createPlaylist(name: String, trackIds: List<Long> = emptyList()): Long {
        val id = playlists.insert(Playlist(name = name.trim()))
        if (trackIds.isNotEmpty()) playlists.append(id, trackIds)
        return id
    }
    suspend fun renamePlaylist(id: Long, name: String) {
        playlists.byId(id)?.let { playlists.update(it.copy(name = name.trim())) }
    }
    suspend fun deletePlaylist(id: Long) {
        playlists.byId(id)?.let {
            art.deleteCustom(it.customArtPath)
            playlists.delete(it)
        }
    }
    suspend fun setPlaylistArt(id: Long, source: Uri?) {
        val p = playlists.byId(id) ?: return
        val newPath = source?.let { art.importCustom(it, "p$id") }
        art.deleteCustom(p.customArtPath)
        playlists.update(p.copy(customArtPath = newPath))
    }
    suspend fun addToPlaylist(id: Long, trackIds: List<Long>) = playlists.append(id, trackIds)
    suspend fun setPlaylistOrder(id: Long, trackIds: List<Long>) = playlists.replaceEntries(id, trackIds)
    suspend fun removeFromPlaylist(id: Long, index: Int) {
        val ids = playlists.entries(id).map { it.trackId }.toMutableList()
        if (index in ids.indices) {
            ids.removeAt(index)
            playlists.replaceEntries(id, ids)
        }
    }

    // Stats & bookmarks

    suspend fun logPlay(trackId: Long, listenedMs: Long, counted: Boolean) {
        db.stats().insert(PlayEvent(trackId = trackId, timestamp = System.currentTimeMillis(), listenedMs = listenedMs, counted = counted))
        if (counted) tracks.markPlayed(trackId, System.currentTimeMillis())
    }
    suspend fun savePosition(trackId: Long, pos: Long) = tracks.savePosition(trackId, pos)
    fun statsTotals(since: Long) = db.stats().totals(since)
    fun daily(days: Int): Flow<List<com.advancedplayer.data.db.DayListening>> {
        val tz = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val offset = tz.getOffset(now).toLong()
        val today = (now + offset) / 86_400_000L
        val since = (today - days + 1) * 86_400_000L - offset
        return db.stats().daily(since, offset)
    }
    fun topTracks(since: Long, limit: Int) = db.stats().topTracks(since, limit)
    fun topArtists(since: Long, limit: Int) = db.stats().topArtists(since, limit)
    suspend fun resetStats() {
        db.stats().clear()
        tracks.resetPlayCounts()
    }

    fun bookmarks(trackId: Long) = db.bookmarks().forTrack(trackId)
    suspend fun addBookmark(trackId: Long, pos: Long) = db.bookmarks().insert(Bookmark(trackId = trackId, positionMs = pos))
    suspend fun deleteBookmark(b: Bookmark) = db.bookmarks().delete(b)

    companion object {
        fun startOfYear(): Long = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
