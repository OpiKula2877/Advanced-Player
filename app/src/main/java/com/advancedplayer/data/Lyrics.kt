package com.advancedplayer.data

import android.content.Context
import android.net.Uri
import com.advancedplayer.data.db.Track
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

object LrcParser {
    private val tag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Lyrics? {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val stamps = tag.findAll(line).toList()
            if (stamps.isEmpty()) {
                if (line.isNotBlank() && !line.trimStart().startsWith("[")) plain += line.trim()
                continue
            }
            val content = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                synced += LyricLine((min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0), content)
            }
        }
        return when {
            synced.isNotEmpty() -> Lyrics(synced.sortedBy { it.timeMs }, synced = true)
            plain.isNotEmpty() -> Lyrics(plain.map { LyricLine(0, it) }, synced = false)
            else -> null
        }
    }

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

object LyricsLoader {
    fun load(context: Context, track: Track): Lyrics? {
        track.lyricsUri?.let { uri ->
            readText(context, Uri.parse(uri))?.let { LrcParser.parse(it) }?.let { return it }
        }
        MediaScanner.lrcSibling(track)?.let { f ->
            runCatching { f.readText() }.getOrNull()?.let { LrcParser.parse(it) }?.let { return it }
        }
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(track.uri))?.use { Id3Lyrics.read(it) }
        }.getOrNull()?.let { LrcParser.parse(it) }
    }

    private fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeText() }
    }.getOrNull()

    private fun ByteArray.decodeText(): String = when {
        size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte() ->
            String(this, 3, size - 3, Charsets.UTF_8)
        size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte() -> String(this, Charsets.UTF_16LE)
        size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte() -> String(this, Charsets.UTF_16BE)
        else -> String(this, Charsets.UTF_8)
    }
}

/** Minimal ID3v2.3/2.4 reader that extracts the first USLT (unsynchronised lyrics) frame. */
object Id3Lyrics {
    private const val MAX_TAG = 24 * 1024 * 1024

    fun read(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.readNBytesCompat(header) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val major = header[3].toInt()
        if (major != 3 && major != 4) return null
        val size = syncSafe(header, 6)
        if (size <= 0 || size > MAX_TAG) return null
        val tag = ByteArray(size)
        val read = input.readNBytesCompat(tag)
        var pos = 0
        if (header[5].toInt() and 0x40 != 0 && read > 4) {
            // Skip extended header.
            pos = if (major == 4) syncSafe(tag, 0) else (int32(tag, 0) + 4)
        }
        while (pos + 10 <= read) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = if (major == 4) syncSafe(tag, pos + 4) else int32(tag, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > read) break
            if (id == "USLT") return decodeUslt(tag, pos + 10, frameSize)
            pos += 10 + frameSize
        }
        return null
    }

    private fun decodeUslt(b: ByteArray, start: Int, len: Int): String? {
        if (len < 5) return null
        val enc = b[start].toInt()
        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        var p = start + 4 // encoding byte + 3-byte language
        val end = start + len
        val wide = enc == 1 || enc == 2
        // Skip content descriptor.
        while (p < end) {
            if (wide) {
                if (p + 1 < end && b[p].toInt() == 0 && b[p + 1].toInt() == 0) {
                    p += 2
                    break
                }
                p += 2
            } else {
                if (b[p].toInt() == 0) {
                    p++
                    break
                }
                p++
            }
        }
        if (p >= end) return null
        return String(b, p, end - p, charset).trim('\u0000', ' ', '\n', '\r').takeIf { it.isNotBlank() }
    }

    private fun syncSafe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
