package com.advancedplayer.playback.dsp

/**
 * Ring buffer of the most recent PCM written by the playback chain. The processor runs
 * ahead of the speaker by roughly the AudioTrack buffer, so readers look back by
 * [LATENCY_MS] to keep visuals in sync with what is audible.
 */
object AudioTap {
    private const val SIZE = 1 shl 16
    private const val MASK = SIZE - 1
    private const val LATENCY_MS = 260L

    private val left = FloatArray(SIZE)
    private val right = FloatArray(SIZE)
    private var writeIndex = 0L
    private var lastWriteNanos = 0L

    @Volatile var sampleRate = 44100
    @Volatile var speed = 1f

    private val lock = Any()

    fun beginBlock(): Long = synchronized(lock) { writeIndex }

    /** Called once per processed block with interleaved-derived channel samples. */
    fun write(l: FloatArray, r: FloatArray, count: Int) {
        synchronized(lock) {
            var w = writeIndex
            for (i in 0 until count) {
                val idx = (w and MASK.toLong()).toInt()
                left[idx] = l[i]
                right[idx] = r[i]
                w++
            }
            writeIndex = w
            lastWriteNanos = System.nanoTime()
        }
    }

    /**
     * Copies [n] mono samples ending at the currently audible position into [mono], and
     * the matching channels into [l]/[r]. Returns false while nothing is playing.
     */
    fun read(mono: FloatArray, l: FloatArray, r: FloatArray, n: Int): Boolean {
        synchronized(lock) {
            if (writeIndex == 0L) return false
            val sinceWriteMs = (System.nanoTime() - lastWriteNanos) / 1_000_000
            if (sinceWriteMs > LATENCY_MS + 400) return false
            val rate = sampleRate * speed
            val behind = ((LATENCY_MS - sinceWriteMs).coerceAtLeast(0) * rate / 1000).toLong()
            val end = (writeIndex - behind).coerceAtLeast(n.toLong())
            var src = end - n
            for (i in 0 until n) {
                val idx = (src and MASK.toLong()).toInt()
                l[i] = left[idx]
                r[i] = right[idx]
                mono[i] = (left[idx] + right[idx]) * 0.5f
                src++
            }
            return true
        }
    }

    fun clear() {
        synchronized(lock) {
            writeIndex = 0
            left.fill(0f)
            right.fill(0f)
        }
    }
}
