package com.advancedplayer.pclink

import com.advancedplayer.data.LyricLine
import com.advancedplayer.data.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PcLinkProtocolTest {
    @Test
    fun readLineStripsTheLineEndings() {
        val input = ByteArrayInputStream("HELLO\r\nLIST\n".toByteArray())
        assertEquals("HELLO", PcLinkProtocol.readLine(input))
        assertEquals("LIST", PcLinkProtocol.readLine(input))
        assertNull(PcLinkProtocol.readLine(input))
    }

    @Test
    fun writeBlockPrefixesTheLength() {
        val out = ByteArrayOutputStream()
        PcLinkProtocol.writeBlock(out, "abc".toByteArray())
        assertEquals("3\nabc", String(out.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun syncedLyricsAreWrittenWithTimeStamps() {
        val lyrics = Lyrics(listOf(LyricLine(1_500, "first"), LyricLine(65_120, "second")), synced = true)
        assertEquals("[00:01.50]first\n[01:05.12]second", PcLinkProtocol.toLrc(lyrics))
    }

    @Test
    fun plainLyricsKeepTheirLines() {
        val lyrics = Lyrics(listOf(LyricLine(0, "a"), LyricLine(0, "b")), synced = false)
        assertEquals("a\nb", PcLinkProtocol.toLrc(lyrics))
    }
}
