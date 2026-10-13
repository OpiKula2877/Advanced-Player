package com.advancedplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY title COLLATE NOCASE")
    fun songsByTitle(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, trackNumber")
    fun songsByArtist(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY dateAdded DESC")
    fun songsByDate(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY durationMs DESC")
    fun songsByDuration(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY playCount DESC, title COLLATE NOCASE")
    fun songsByPlays(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 1 ORDER BY title COLLATE NOCASE")
    fun videos(): Flow<List<Track>>

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<Track>

    @Query("SELECT COUNT(*) FROM tracks")
    fun count(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observe(id: Long): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Track>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun byUri(uri: String): Track?

    @Query("SELECT * FROM tracks WHERE isVideo = 0 AND album = :album ORDER BY trackNumber, title COLLATE NOCASE")
    fun album(album: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 AND artist = :artist ORDER BY album COLLATE NOCASE, trackNumber")
    fun artist(artist: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE")
    fun folder(folder: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE favorite = 1 ORDER BY title COLLATE NOCASE")
    fun favorites(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 ORDER BY dateAdded DESC LIMIT 100")
    fun recentlyAdded(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun mostPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 50")
    fun recentlyPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isVideo = 0 AND lastPlayed < :cutoff ORDER BY playCount DESC, lastPlayed ASC LIMIT 100")
    fun forgotten(cutoff: Long): Flow<List<Track>>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' " +
            "OR album LIKE '%' || :q || '%' ORDER BY title COLLATE NOCASE LIMIT 200",
    )
    fun search(q: String): Flow<List<Track>>

    @Query(
        "SELECT album, MAX(artist) AS artist, COUNT(*) AS count, MIN(id) AS coverId FROM tracks " +
            "WHERE isVideo = 0 GROUP BY album ORDER BY album COLLATE NOCASE",
    )
    fun albums(): Flow<List<AlbumSummary>>

    @Query(
        "SELECT artist, COUNT(*) AS count, COUNT(DISTINCT album) AS albumCount, MIN(id) AS coverId FROM tracks " +
            "WHERE isVideo = 0 GROUP BY artist ORDER BY artist COLLATE NOCASE",
    )
    fun artists(): Flow<List<ArtistSummary>>

    @Query("SELECT folder, COUNT(*) AS count, MIN(id) AS coverId FROM tracks GROUP BY folder ORDER BY folder COLLATE NOCASE")
    fun folders(): Flow<List<FolderSummary>>

    @Insert
    suspend fun insertAll(tracks: List<Track>)

    @Update
    suspend fun update(track: Track)

    @Update
    suspend fun updateAll(tracks: List<Track>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("UPDATE tracks SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :now WHERE id = :id")
    suspend fun markPlayed(id: Long, now: Long)

    @Query("UPDATE tracks SET savedPositionMs = :pos WHERE id = :id")
    suspend fun savePosition(id: Long, pos: Long)

    @Query("UPDATE tracks SET playCount = 0, lastPlayed = 0")
    suspend fun resetPlayCounts()
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.customArtPath, COUNT(e.id) AS count, MIN(e.trackId) AS coverId " +
            "FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE",
    )
    fun summaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): Playlist?

    @Query(
        "SELECT t.* FROM tracks t JOIN playlist_entries e ON e.trackId = t.id " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position",
    )
    fun tracks(playlistId: Long): Flow<List<Track>>

    @Query(
        "SELECT t.* FROM tracks t JOIN playlist_entries e ON e.trackId = t.id " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position",
    )
    suspend fun tracksOnce(playlistId: Long): List<Track>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entries(playlistId: Long): List<PlaylistEntry>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntry>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Transaction
    suspend fun replaceEntries(playlistId: Long, trackIds: List<Long>) {
        clearEntries(playlistId)
        insertEntries(trackIds.mapIndexed { i, id -> PlaylistEntry(playlistId = playlistId, trackId = id, position = i) })
    }

    @Transaction
    suspend fun append(playlistId: Long, trackIds: List<Long>) {
        val start = maxPosition(playlistId) + 1
        insertEntries(trackIds.mapIndexed { i, id -> PlaylistEntry(playlistId = playlistId, trackId = id, position = start + i) })
    }
}

@Dao
interface StatsDao {
    @Insert
    suspend fun insert(event: PlayEvent)

    @Query(
        "SELECT COALESCE(SUM(counted), 0) AS plays, COALESCE(SUM(listenedMs), 0) AS listenedMs, " +
            "COUNT(DISTINCT trackId) AS uniqueTracks FROM play_events WHERE timestamp >= :since",
    )
    fun totals(since: Long): Flow<StatsTotals>

    @Query(
        "SELECT e.trackId AS trackId, t.title AS title, t.artist AS artist, SUM(e.counted) AS plays, SUM(e.listenedMs) AS listenedMs " +
            "FROM play_events e JOIN tracks t ON t.id = e.trackId WHERE e.timestamp >= :since " +
            "GROUP BY e.trackId ORDER BY plays DESC, listenedMs DESC LIMIT :limit",
    )
    fun topTracks(since: Long, limit: Int): Flow<List<TrackPlays>>

    @Query(
        "SELECT t.artist AS artist, SUM(e.counted) AS plays, SUM(e.listenedMs) AS listenedMs, MIN(t.id) AS coverId " +
            "FROM play_events e JOIN tracks t ON t.id = e.trackId WHERE e.timestamp >= :since " +
            "GROUP BY t.artist ORDER BY plays DESC, listenedMs DESC LIMIT :limit",
    )
    fun topArtists(since: Long, limit: Int): Flow<List<ArtistPlays>>

    @Query(
        "SELECT (timestamp + :tzOffset) / 86400000 AS day, SUM(listenedMs) AS listenedMs, SUM(counted) AS plays " +
            "FROM play_events WHERE timestamp >= :since GROUP BY day ORDER BY day",
    )
    fun daily(since: Long, tzOffset: Long): Flow<List<DayListening>>

    @Query("DELETE FROM play_events")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE trackId = :trackId ORDER BY positionMs")
    fun forTrack(trackId: Long): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)
}
