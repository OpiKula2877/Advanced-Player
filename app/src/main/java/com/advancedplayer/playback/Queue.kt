package com.advancedplayer.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import com.advancedplayer.data.db.Track
import java.io.File
import kotlin.random.Random

object MediaItems {
    const val EXTRA_VIDEO = "ap_video"

    fun fromTrack(t: Track): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(t.title)
            .setArtist(t.artist.ifEmpty { null })
            .setAlbumTitle(t.album.ifEmpty { null })
            .setGenre(t.genre.ifEmpty { null })
            .setDurationMs(t.durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (t.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(Bundle().apply { putBoolean(EXTRA_VIDEO, t.isVideo) })
        t.customArtPath?.let { meta.setArtworkUri(Uri.fromFile(File(it))) }
        return MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(t.uri)
            .setMediaMetadata(meta.build())
            .build()
    }

    fun folder(id: String, title: String, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build(),
            )
            .build()

    fun isVideo(item: MediaItem?): Boolean = item?.mediaMetadata?.extras?.getBoolean(EXTRA_VIDEO) == true
}

/**
 * Shuffle order that (1) can start from the current item so enabling shuffle mid-song
 * still plays everything, and (2) puts "Play next" insertions right after the item
 * they were inserted behind instead of at a random spot.
 */
@UnstableApi
class SmartShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random,
) : ShuffleOrder {
    private val positions = IntArray(shuffled.size).also { for (i in shuffled.indices) it[shuffled[i]] = i }

    constructor(length: Int, random: Random = Random.Default) : this(randomOrder(length, random), random)

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val p = positions[index] + 1
        return if (p < shuffled.size) shuffled[p] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val p = positions[index] - 1
        return if (p >= 0) shuffled[p] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled.last()

    override fun getFirstIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (shuffled.isEmpty()) return SmartShuffleOrder(insertionCount, random)
        val shiftedOld = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }
        val inserted = List(insertionCount) { insertionIndex + it }
        val result = ArrayList<Int>(shuffled.size + insertionCount)
        when {
            insertionIndex >= shuffled.size -> {
                result.addAll(shiftedOld)
                result.addAll(inserted)
            }
            insertionIndex == 0 -> {
                result.addAll(inserted)
                result.addAll(shiftedOld)
            }
            else -> {
                val anchor = insertionIndex - 1
                for (v in shiftedOld) {
                    result.add(v)
                    if (v == anchor) result.addAll(inserted)
                }
            }
        }
        return SmartShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val result = shuffled.filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - count else it }
        return SmartShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = SmartShuffleOrder(IntArray(0), random)

    companion object {
        private fun randomOrder(length: Int, random: Random): IntArray =
            IntArray(length) { it }.also { it.shuffle(random) }

        fun startingWith(length: Int, first: Int, random: Random = Random.Default): SmartShuffleOrder {
            if (length == 0 || first !in 0 until length) return SmartShuffleOrder(length, random)
            val rest = (0 until length).filter { it != first }.shuffled(random)
            return SmartShuffleOrder((listOf(first) + rest).toIntArray(), random)
        }
    }
}

data class SavedQueue(
    val ids: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    fun save(q: SavedQueue) = prefs.edit {
        putString("ids", q.ids.joinToString(","))
        putInt("index", q.index)
        putLong("pos", q.positionMs)
        putBoolean("shuffle", q.shuffle)
        putInt("repeat", q.repeatMode)
    }

    fun load(): SavedQueue? {
        val ids = prefs.getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return null
        return SavedQueue(
            ids = ids,
            index = prefs.getInt("index", 0),
            positionMs = prefs.getLong("pos", 0),
            shuffle = prefs.getBoolean("shuffle", false),
            repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF),
        )
    }
}
