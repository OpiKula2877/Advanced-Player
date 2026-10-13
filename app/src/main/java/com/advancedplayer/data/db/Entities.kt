package com.advancedplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TrackSource {
    const val MEDIA_STORE = 0
    const val FOLDER = 1
    const val FILE = 2
}

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uri"], unique = true), Index("artist"), Index("album"), Index("folder")],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long,
    val dateAdded: Long,
    val folder: String,
    val filePath: String? = null,
    val isVideo: Boolean = false,
    val source: Int,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val customArtPath: String? = null,
    val lyricsUri: String? = null,
    val edited: Boolean = false,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val savedPositionMs: Long = 0,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val customArtPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(
    tableName = "play_events",
    foreignKeys = [ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId"), Index("timestamp")],
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestamp: Long,
    val listenedMs: Long,
    val counted: Boolean,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AlbumSummary(
    val album: String,
    val artist: String,
    val count: Int,
    @ColumnInfo(name = "coverId") val coverId: Long,
)

data class ArtistSummary(
    val artist: String,
    val count: Int,
    val albumCount: Int,
    @ColumnInfo(name = "coverId") val coverId: Long,
)

data class FolderSummary(
    val folder: String,
    val count: Int,
    @ColumnInfo(name = "coverId") val coverId: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val customArtPath: String?,
    val count: Int,
    val coverId: Long?,
)

data class TrackPlays(
    val trackId: Long,
    val title: String,
    val artist: String,
    val plays: Int,
    val listenedMs: Long,
)

data class ArtistPlays(
    val artist: String,
    val plays: Int,
    val listenedMs: Long,
    val coverId: Long,
)

data class DayListening(
    /** Days since the epoch in local time. */
    val day: Long,
    val listenedMs: Long,
    val plays: Int,
)

data class StatsTotals(
    val plays: Int,
    val listenedMs: Long,
    val uniqueTracks: Int,
)
