package com.advancedplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.advancedplayer.data.db.AppDatabase
import com.advancedplayer.data.db.Track
import com.advancedplayer.data.db.TrackSource
import java.io.File

/**
 * Builds the library from three sources: the MediaStore (whole device), user-chosen
 * SAF folder trees and individually picked files. Rescans keep user edits, favorites
 * and play counts because rows are matched by uri.
 */
class MediaScanner(private val context: Context, private val db: AppDatabase) {

    private val resolver get() = context.contentResolver

    suspend fun scan(settings: AppSettings): Int {
        val minMs = settings.minDurationSec * 1000L
        val found = LinkedHashMap<String, Track>()

        if (settings.scanMediaStore && Permissions.hasMediaAccess(context)) {
            val fromStore = scanMediaStoreAudio(minMs, settings.excludedFolders) +
                if (settings.includeVideos) scanMediaStoreVideo(minMs, settings.excludedFolders) else emptyList()
            val completed = fromStore.mapNotNull(::completeUnindexed)
            completed.filter { it.durationMs >= minMs }.forEach { found[it.uri] = it }
        }
        val lyricsByStem = HashMap<String, String>()
        for (tree in settings.folderUris) {
            runCatching { scanTree(Uri.parse(tree), minMs, settings.includeVideos, lyricsByStem) }
                .getOrDefault(emptyList())
                .forEach { found[it.uri] = it }
        }

        val existing = db.tracks().all()
        val byUri = existing.associateBy { it.uri }
        val toInsert = ArrayList<Track>()
        val toUpdate = ArrayList<Track>()
        for (scanned in found.values) {
            val old = byUri[scanned.uri]
            val lyrics = scanned.filePath?.let { lyricsByStem[stem(it)] }
            if (old == null) {
                toInsert += scanned.copy(lyricsUri = lyrics)
            } else {
                val merged = if (old.edited) {
                    old.copy(durationMs = scanned.durationMs, folder = scanned.folder, filePath = scanned.filePath)
                } else {
                    scanned.copy(
                        id = old.id,
                        customArtPath = old.customArtPath,
                        lyricsUri = old.lyricsUri ?: lyrics,
                        favorite = old.favorite,
                        playCount = old.playCount,
                        lastPlayed = old.lastPlayed,
                        savedPositionMs = old.savedPositionMs,
                        dateAdded = old.dateAdded,
                    )
                }
                if (merged != old) toUpdate += merged
            }
        }
        // Files picked by hand stay until removed; scanned sources are pruned when gone.
        val stale = existing.filter { it.source != TrackSource.FILE && it.uri !in found }.map { it.id }

        db.withTransaction {
            if (stale.isNotEmpty()) stale.chunked(500).forEach { db.tracks().deleteIds(it) }
            if (toUpdate.isNotEmpty()) db.tracks().updateAll(toUpdate)
            if (toInsert.isNotEmpty()) db.tracks().insertAll(toInsert)
        }
        return found.size + existing.count { it.source == TrackSource.FILE }
    }

    /** Adds individually picked documents; the caller has already taken persistable permission. */
    suspend fun addFiles(uris: List<Uri>): Int {
        val now = System.currentTimeMillis()
        val tracks = uris.mapNotNull { uri ->
            if (db.tracks().byUri(uri.toString()) != null) return@mapNotNull null
            val name = displayName(uri) ?: uri.lastPathSegment ?: "Unknown"
            val mime = resolver.getType(uri) ?: ""
            extract(uri, name, mime, folder = "Picked files", source = TrackSource.FILE, dateAdded = now, size = 0L)
        }
        if (tracks.isNotEmpty()) db.tracks().insertAll(tracks)
        return tracks.size
    }

    private fun scanMediaStoreAudio(minMs: Long, excluded: Set<String>): List<Track> {
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
        )
        // Freshly copied files may not be fully indexed yet (NULL flags/duration), so only
        // exclude what is positively a system sound or known to be too short.
        val selection = "COALESCE(${MediaStore.Audio.Media.IS_RINGTONE}, 0) = 0 " +
            "AND COALESCE(${MediaStore.Audio.Media.IS_NOTIFICATION}, 0) = 0 " +
            "AND COALESCE(${MediaStore.Audio.Media.IS_ALARM}, 0) = 0 " +
            "AND (${MediaStore.Audio.Media.DURATION} IS NULL OR ${MediaStore.Audio.Media.DURATION} >= ?)"
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(minMs.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.long(MediaStore.Audio.Media._ID)
                val path = c.str(MediaStore.Audio.Media.DATA)
                val folder = path?.substringBeforeLast('/', "") ?: ""
                if (excluded.any { folder.startsWith(it) }) continue
                val artist = c.str(MediaStore.Audio.Media.ARTIST).cleanTag()
                val album = c.str(MediaStore.Audio.Media.ALBUM).cleanTag()
                val title = c.str(MediaStore.Audio.Media.TITLE)?.takeIf { it.isNotBlank() }
                    ?: path?.substringAfterLast('/')?.let(::stem) ?: "Unknown"
                val track = c.int(MediaStore.Audio.Media.TRACK)
                out += Track(
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    year = c.int(MediaStore.Audio.Media.YEAR),
                    trackNumber = if (track > 1000) track % 1000 else track,
                    durationMs = c.long(MediaStore.Audio.Media.DURATION),
                    dateAdded = c.long(MediaStore.Audio.Media.DATE_ADDED) * 1000,
                    folder = folder,
                    filePath = path,
                    source = TrackSource.MEDIA_STORE,
                    mimeType = c.str(MediaStore.Audio.Media.MIME_TYPE) ?: "",
                    sizeBytes = c.long(MediaStore.Audio.Media.SIZE),
                )
            }
        }
        return out
    }

    private fun scanMediaStoreVideo(minMs: Long, excluded: Set<String>): List<Track> {
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
        )
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            "(${MediaStore.Video.Media.DURATION} IS NULL OR ${MediaStore.Video.Media.DURATION} >= ?)", arrayOf(minMs.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.long(MediaStore.Video.Media._ID)
                val path = c.str(MediaStore.Video.Media.DATA)
                val folder = path?.substringBeforeLast('/', "") ?: ""
                if (excluded.any { folder.startsWith(it) }) continue
                out += Track(
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = c.str(MediaStore.Video.Media.TITLE)?.takeIf { it.isNotBlank() }
                        ?: path?.substringAfterLast('/')?.let(::stem) ?: "Video",
                    artist = c.str(MediaStore.Video.Media.ARTIST).cleanTag(),
                    album = c.str(MediaStore.Video.Media.ALBUM).cleanTag().takeIf { it != folder.substringAfterLast('/') } ?: "",
                    durationMs = c.long(MediaStore.Video.Media.DURATION),
                    dateAdded = c.long(MediaStore.Video.Media.DATE_ADDED) * 1000,
                    folder = folder,
                    filePath = path,
                    isVideo = true,
                    source = TrackSource.MEDIA_STORE,
                    mimeType = c.str(MediaStore.Video.Media.MIME_TYPE) ?: "",
                    sizeBytes = c.long(MediaStore.Video.Media.SIZE),
                )
            }
        }
        return out
    }

    /** Walks a SAF tree with direct DocumentsContract queries (much faster than DocumentFile). */
    private fun scanTree(
        treeUri: Uri,
        minMs: Long,
        includeVideos: Boolean,
        lyrics: MutableMap<String, String>,
    ): List<Track> {
        val out = ArrayList<Track>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = rootId.substringAfterLast(':').ifEmpty { rootId }
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add(rootId to rootName)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (stack.isNotEmpty()) {
            val (docId, folderName) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> stack.add(id to "$folderName/$name")
                        name.endsWith(".lrc", ignoreCase = true) -> lyrics["$folderName/${stem(name)}"] = docUri.toString()
                        mime.startsWith("audio/") || (includeVideos && mime.startsWith("video/")) -> {
                            val t = extract(docUri, name, mime, folderName, TrackSource.FOLDER, c.getLong(3), c.getLong(4))
                            if (t != null && t.durationMs >= minMs) out += t
                        }
                    }
                }
            }
        }
        return out
    }

    private fun extract(
        uri: Uri,
        fileName: String,
        mime: String,
        folder: String,
        source: Int,
        dateAdded: Long,
        size: Long,
    ): Track? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val trackNo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            Track(
                uri = uri.toString(),
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: stem(fileName),
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).cleanTag(),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).cleanTag(),
                genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).cleanTag(),
                year = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                trackNumber = trackNo,
                durationMs = duration,
                dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                folder = folder,
                filePath = if (source == TrackSource.FOLDER) "$folder/$fileName" else null,
                isVideo = hasVideo || mime.startsWith("video/"),
                source = source,
                mimeType = mime,
                sizeBytes = size,
            )
        } catch (e: Exception) {
            android.util.Log.w("LibraryScan", "Cannot read $uri", e)
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** MediaStore rows that are not indexed yet have no duration/tags; read them from the file. */
    private fun completeUnindexed(t: Track): Track? {
        if (t.durationMs > 0) return t
        val name = t.filePath?.substringAfterLast('/') ?: t.title
        val x = extract(Uri.parse(t.uri), name, t.mimeType, t.folder, t.source, t.dateAdded, t.sizeBytes) ?: return null
        return t.copy(
            durationMs = x.durationMs,
            title = x.title.ifBlank { t.title },
            artist = t.artist.ifEmpty { x.artist },
            album = t.album.ifEmpty { x.album },
            genre = x.genre,
            year = if (t.year > 0) t.year else x.year,
        )
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun Cursor.str(col: String): String? = getString(getColumnIndexOrThrow(col))
    private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))

    companion object {
        fun stem(name: String): String = name.substringBeforeLast('.', name)

        /** MediaStore reports missing tags as "<unknown>"; we store them as empty strings. */
        fun String?.cleanTag(): String = this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" } ?: ""

        fun lrcSibling(track: Track): File? {
            val path = track.filePath ?: return null
            val f = File(path.substringBeforeLast('.') + ".lrc")
            return f.takeIf { runCatching { it.canRead() }.getOrDefault(false) }
        }
    }
}
