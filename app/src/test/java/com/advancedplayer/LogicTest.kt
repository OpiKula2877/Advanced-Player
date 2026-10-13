package com.advancedplayer

import androidx.media3.common.C
import com.advancedplayer.data.Id3Lyrics
import com.advancedplayer.data.LrcParser
import com.advancedplayer.playback.SmartShuffleOrder
import com.advancedplayer.playback.dsp.Biquad
import com.advancedplayer.playback.dsp.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class LrcParserTest {
    @Test
    fun parsesTimestampsAndSorts() {
        val l = LrcParser.parse(
            """
            [ti:Song]
            [00:12.50]Second
            [00:01.00]First
            [01:02.345]Third
            """.trimIndent(),
        )!!
        assertTrue(l.synced)
        assertEquals(listOf(1000L, 12500L, 62345L), l.lines.map { it.timeMs })
        assertEquals("First", l.lines[0].text)
    }

    @Test
    fun repeatedStampsAndOffset() {
        val l = LrcParser.parse("[offset:500]\n[00:10.00][00:20.00]Chorus")!!
        assertEquals(listOf(9500L, 19500L), l.lines.map { it.timeMs })
        assertTrue(l.lines.all { it.text == "Chorus" })
    }

    @Test
    fun plainTextIsUnsynced() {
        val l = LrcParser.parse("Line one\nLine two")!!
        assertFalse(l.synced)
        assertEquals(2, l.lines.size)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(LrcParser.parse("   \n"))
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")!!.lines
        assertEquals(-1, LrcParser.indexAt(lines, 500))
        assertEquals(0, LrcParser.indexAt(lines, 1000))
        assertEquals(1, LrcParser.indexAt(lines, 8999))
        assertEquals(2, LrcParser.indexAt(lines, 100_000))
    }
}

class SmartShuffleOrderTest {
    private fun order(o: SmartShuffleOrder): List<Int> {
        val out = ArrayList<Int>()
        var i = o.firstIndex
        while (i != C.INDEX_UNSET) {
            out += i
            i = o.getNextIndex(i)
        }
        return out
    }

    @Test
    fun startingWithPutsCurrentFirstAndCoversAll() {
        val o = SmartShuffleOrder.startingWith(50, 17, Random(1))
        val seq = order(o)
        assertEquals(17, seq.first())
        assertEquals((0 until 50).toSet(), seq.toSet())
        assertEquals(50, seq.size)
    }

    @Test
    fun playNextInsertsRightAfterAnchor() {
        val o = SmartShuffleOrder.startingWith(10, 4, Random(2))
        // "Play next" while item 4 plays inserts two items at playlist index 5.
        val n = o.cloneAndInsert(5, 2) as SmartShuffleOrder
        val seq = order(n)
        val at = seq.indexOf(4)
        assertEquals(listOf(5, 6), seq.subList(at + 1, at + 3))
        assertEquals(12, seq.toSet().size)
    }

    @Test
    fun appendGoesToEnd() {
        val o = SmartShuffleOrder.startingWith(5, 0, Random(3))
        val seq = order(o.cloneAndInsert(5, 3) as SmartShuffleOrder)
        assertEquals(listOf(5, 6, 7), seq.takeLast(3))
    }

    @Test
    fun removeShiftsIndices() {
        val o = SmartShuffleOrder.startingWith(6, 2, Random(4))
        val seq = order(o.cloneAndRemove(0, 2) as SmartShuffleOrder)
        assertEquals((0 until 4).toSet(), seq.toSet())
        assertEquals(0, seq.first()) // former index 2 shifted down by two
    }
}

class Id3LyricsTest {
    private fun syncSafe(n: Int) = byteArrayOf(
        (n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte(),
    )

    @Test
    fun readsUsltFrameV24() {
        val text = "[00:01.00]Hello"
        val payload = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)
        val frame = "USLT".toByteArray() + syncSafe(payload.size) + byteArrayOf(0, 0) + payload
        val header = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size)
        val result = Id3Lyrics.read(ByteArrayInputStream(header + frame))
        assertEquals(text, result)
    }

    @Test
    fun ignoresNonId3() {
        assertNull(Id3Lyrics.read(ByteArrayInputStream(ByteArray(64))))
    }
}

class DspTest {
    @Test
    fun fftFindsSinePeak() {
        val n = 1024
        val re = FloatArray(n) { sin(2 * PI * 32 * it / n).toFloat() }
        val im = FloatArray(n)
        Fft.transform(re, im)
        val mags = (0 until n / 2).map { sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(32, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun peakingFilterBoostsCenterFrequency() {
        val fs = 44100
        val f = Biquad().apply { setPeaking(fs, 1000.0, 1.41, 12.0) }
        var peak = 0.0
        for (i in 0 until fs) {
            val y = f.process(sin(2 * PI * 1000 * i / fs), 0)
            if (i > fs / 2) peak = maxOf(peak, abs(y))
        }
        // +12 dB is roughly x4 amplitude.
        assertTrue("peak=$peak", peak in 3.7..4.3)
    }

    @Test
    fun zeroGainBypasses() {
        val f = Biquad().apply { setPeaking(44100, 1000.0, 1.41, 0.0) }
        assertTrue(f.bypass)
        assertNotNull(f)
    }
}
