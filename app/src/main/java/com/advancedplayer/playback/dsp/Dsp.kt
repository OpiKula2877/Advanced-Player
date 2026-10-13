package com.advancedplayer.playback.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ-cookbook biquad in transposed direct form II, with per-channel state. */
class Biquad(maxChannels: Int = 8) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val z1 = DoubleArray(maxChannels)
    private val z2 = DoubleArray(maxChannels)
    var bypass = true
        private set

    fun setPeaking(fs: Int, f0: Double, q: Double, gainDb: Double) {
        if (gainDb == 0.0 || f0 >= fs / 2.0) {
            bypass = true
            return
        }
        bypass = false
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        val a0 = 1 + alpha / a
        b0 = (1 + alpha * a) / a0
        b1 = -2 * cw / a0
        b2 = (1 - alpha * a) / a0
        a1 = -2 * cw / a0
        a2 = (1 - alpha / a) / a0
    }

    fun setLowShelf(fs: Int, f0: Double, gainDb: Double) {
        if (gainDb == 0.0) {
            bypass = true
            return
        }
        bypass = false
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0) // shelf slope S = 1
        val sa = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cw + sa
        b0 = a * ((a + 1) - (a - 1) * cw + sa) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0
        b2 = a * ((a + 1) - (a - 1) * cw - sa) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cw) / a0
        a2 = ((a + 1) + (a - 1) * cw - sa) / a0
    }

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }
}

/** Small Schroeder/Freeverb-style reverb: 4 parallel damped combs into 2 allpasses. */
class SimpleReverb(sampleRate: Int) {
    private val scale = sampleRate / 44100.0
    private val combs = intArrayOf(1116, 1188, 1277, 1356).map { Comb((it * scale).toInt().coerceAtLeast(1)) }
    private val allpasses = intArrayOf(556, 441).map { Allpass((it * scale).toInt().coerceAtLeast(1)) }

    fun process(input: Double): Double {
        var out = 0.0
        for (c in combs) out += c.process(input)
        for (a in allpasses) out = a.process(out)
        return out * 0.25
    }

    fun reset() {
        combs.forEach { it.reset() }
        allpasses.forEach { it.reset() }
    }

    private class Comb(size: Int) {
        private val buf = DoubleArray(size)
        private var idx = 0
        private var store = 0.0
        fun process(x: Double): Double {
            val out = buf[idx]
            store = out * 0.8 + store * 0.2
            buf[idx] = x + store * 0.84
            if (++idx >= buf.size) idx = 0
            return out
        }
        fun reset() {
            buf.fill(0.0)
            store = 0.0
        }
    }

    private class Allpass(size: Int) {
        private val buf = DoubleArray(size)
        private var idx = 0
        fun process(x: Double): Double {
            val b = buf[idx]
            val out = -x + b
            buf[idx] = x + b * 0.5
            if (++idx >= buf.size) idx = 0
            return out
        }
        fun reset() = buf.fill(0.0)
    }
}

/** In-place iterative radix-2 FFT. */
object Fft {
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
