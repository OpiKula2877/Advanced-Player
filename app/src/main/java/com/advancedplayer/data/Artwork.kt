package com.advancedplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.advancedplayer.data.db.AppDatabase
import com.advancedplayer.data.db.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.FileOutputStream

/** Coil model for a track's artwork. [stamp] changes when the user picks a custom image. */
data class ArtModel(val trackId: Long, val stamp: String = "")

fun Track.artModel() = ArtModel(id, customArtPath ?: "")

class NoArtException : Exception("No artwork")

/**
 * Extracts embedded artwork (or a video frame) once and keeps it in a disk cache, so
 * scrolling long lists does not re-open every media file.
 */
class ArtRepository(private val context: Context, private val db: AppDatabase) {
    private val cacheDir = File(context.cacheDir, "art").apply { mkdirs() }
    private val customDir = File(context.filesDir, "custom_art").apply { mkdirs() }
    private val locks = Array(16) { Mutex() }

    suspend fun artFile(trackId: Long): File? = withContext(Dispatchers.IO) {
        val track = db.tracks().byId(trackId) ?: return@withContext null
        track.customArtPath?.let { p -> File(p).takeIf { it.exists() }?.let { return@withContext it } }
        val file = File(cacheDir, "$trackId.jpg")
        val none = File(cacheDir, "$trackId.none")
        locks[(trackId % locks.size).toInt()].withLock {
            if (file.exists() && file.length() > 0) return@withLock file
            if (none.exists()) return@withLock null
            val bmp = extract(track)
            if (bmp == null) {
                none.createNewFile()
                null
            } else {
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                file
            }
        }
    }

    suspend fun bitmap(trackId: Long, maxSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = artFile(trackId) ?: return@withContext null
        decodeSampled(f.readBytes(), maxSize)
    }

    /** Copies a picked image into app storage and returns the new path. */
    suspend fun importCustom(source: Uri, prefix: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return@runCatching null
            val bmp = decodeSampled(bytes, 1200) ?: return@runCatching null
            val out = File(customDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            out.absolutePath
        }.getOrNull()
    }

    fun deleteCustom(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    private fun extract(track: Track): Bitmap? {
        val uri = Uri.parse(track.uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.embeddedPicture?.let { bytes -> decodeSampled(bytes, 1024)?.let { return it } }
            if (track.isVideo) {
                r.getFrameAtTime(track.durationMs * 1000 / 3, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { return scaleDown(it, 1024) }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { r.release() }
        }
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == "media") {
            runCatching { return context.contentResolver.loadThumbnail(uri, Size(512, 512), null) }
        }
        return null
    }

    private fun decodeSampled(bytes: ByteArray, max: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= max) return b
        val f = max.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }
}

class ArtFetcher(private val data: ArtModel, private val repo: ArtRepository) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = repo.artFile(data.trackId) ?: throw NoArtException()
        return SourceResult(ImageSource(file.toOkioPath()), mimeType = "image/jpeg", dataSource = DataSource.DISK)
    }

    class Factory(private val repo: ArtRepository) : Fetcher.Factory<ArtModel> {
        override fun create(data: ArtModel, options: Options, imageLoader: ImageLoader): Fetcher = ArtFetcher(data, repo)
    }
}

class ArtKeyer : Keyer<ArtModel> {
    override fun key(data: ArtModel, options: Options): String = "art:${data.trackId}:${data.stamp}"
}
