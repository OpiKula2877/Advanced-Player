package com.advancedplayer.pclink

import com.advancedplayer.data.Lyrics
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * The wire format of the PC link, shared by the phone and the PC app.
 * Every request is one ASCII line ending in '\n'. Every answer is either a line, or a decimal
 * length line followed by exactly that many bytes (see [writeBlock]).
 */
object PcLinkProtocol {
    /** Bluetooth service id of the PC link. The PC app connects to this id. */
    val SERVICE_UUID: UUID = UUID.fromString("8f4c2b1e-6a3d-4e7b-9c15-2d9a7f0b6e31")

    const val VERSION = 1

    private const val MAX_LINE = 4096

    /** Reads one line without the newline. Returns null at the end of the stream or when the line is too long. */
    fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (line.size() == 0) null else String(line.toByteArray(), Charsets.UTF_8)
            if (b == '\n'.code) return String(line.toByteArray(), Charsets.UTF_8).trimEnd('\r')
            if (line.size() >= MAX_LINE) return null
            line.write(b)
        }
    }

    /** Writes "length\n" followed by the bytes. */
    fun writeBlock(output: OutputStream, bytes: ByteArray) {
        output.write("${bytes.size}\n".toByteArray(Charsets.US_ASCII))
        output.write(bytes)
    }

    /** Turns parsed lyrics back into LRC text: [mm:ss.xx] stamps for synced lyrics, plain lines otherwise. */
    fun toLrc(lyrics: Lyrics): String = lyrics.lines.joinToString("\n") { line ->
        if (lyrics.synced) "[${formatStamp(line.timeMs)}]${line.text}" else line.text
    }

    private fun formatStamp(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        val hundredths = (ms % 1_000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}
