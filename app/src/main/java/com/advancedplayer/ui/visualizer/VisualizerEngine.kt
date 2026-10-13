package com.advancedplayer.ui.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.advancedplayer.playback.dsp.AudioTap
import com.advancedplayer.playback.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Turns the [AudioTap] stream into smoothed spectrum bands, a waveform, stereo levels
 * and a particle field. Updated once per frame by [rememberVisualizer].
 */
class VisualizerEngine {
    companion object {
        const val N = 2048
        const val BANDS = 64
        const val WAVE = 256
        private const val MIN_F = 32.0
        private const val MAX_F = 16000.0
    }

    private val mono = FloatArray(N)
    private val left = FloatArray(N)
    private val right = FloatArray(N)
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private var edges = IntArray(BANDS + 1)
    private var edgesRate = 0

    val bands = FloatArray(BANDS)
    val peaks = FloatArray(BANDS)
    val wave = FloatArray(WAVE)
    var bass = 0f
        private set
    var energy = 0f
        private set
    var levelL = 0f
        private set
    var levelR = 0f
        private set
    var peakL = 0f
        private set
    var peakR = 0f
        private set

    val particles = ParticleField()

    private fun ensureEdges(rate: Int) {
        if (rate == edgesRate) return
        edgesRate = rate
        val e = IntArray(BANDS + 1)
        for (b in 0..BANDS) {
            val f = MIN_F * (MAX_F / MIN_F).pow(b.toDouble() / BANDS)
            e[b] = (f * N / rate).toInt().coerceIn(1, N / 2 - 1)
        }
        for (b in 1..BANDS) if (e[b] <= e[b - 1]) e[b] = min(e[b - 1] + 1, N / 2)
        edges = e
    }

    fun update(dt: Float) {
        val ok = AudioTap.read(mono, left, right, N)
        if (!ok) {
            decay(dt)
            particles.update(dt, 0f, 0f)
            return
        }
        ensureEdges(AudioTap.sampleRate)
        for (i in 0 until N) {
            re[i] = mono[i] * window[i]
            im[i] = 0f
        }
        Fft.transform(re, im)

        val decayRate = 1.6f * dt
        for (b in 0 until BANDS) {
            var mag = 0f
            for (k in edges[b] until max(edges[b + 1], edges[b] + 1)) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                if (m > mag) mag = m
            }
            val amp = mag * 4f / N
            // Tilt so treble is visible next to the much louder bass.
            val tilt = 3.0 * (b.toDouble() / BANDS) * 4
            val db = 20 * log10(amp.toDouble().coerceAtLeast(1e-6)) + tilt
            val v = ((db + 62) / 56).toFloat().coerceIn(0f, 1f)
            bands[b] = if (v > bands[b]) bands[b] + (v - bands[b]) * 0.65f else max(v, bands[b] - decayRate)
            peaks[b] = if (bands[b] > peaks[b]) bands[b] else max(0f, peaks[b] - 0.45f * dt)
        }
        var bs = 0f
        for (b in 0 until 6) bs += bands[b]
        bass = bs / 6
        var en = 0f
        for (b in 0 until BANDS) en += bands[b]
        energy = en / BANDS

        val step = 512 / WAVE
        val start = N - 512
        for (i in 0 until WAVE) wave[i] = mono[start + i * step]

        var sl = 0f
        var sr = 0f
        for (i in N - 1024 until N) {
            sl += left[i] * left[i]
            sr += right[i] * right[i]
        }
        levelL = smoothLevel(levelL, rmsToLevel(sl / 1024), dt)
        levelR = smoothLevel(levelR, rmsToLevel(sr / 1024), dt)
        peakL = if (levelL > peakL) levelL else max(0f, peakL - 0.3f * dt)
        peakR = if (levelR > peakR) levelR else max(0f, peakR - 0.3f * dt)

        particles.update(dt, bass, energy)
    }

    private fun rmsToLevel(meanSquare: Float): Float {
        val db = 10 * log10(meanSquare.toDouble().coerceAtLeast(1e-9))
        return ((db + 42) / 42).toFloat().coerceIn(0f, 1f)
    }

    private fun smoothLevel(old: Float, new: Float, dt: Float) =
        if (new > old) old + (new - old) * 0.5f else max(new, old - 1.2f * dt)

    private fun decay(dt: Float) {
        val d = 1.6f * dt
        for (b in 0 until BANDS) {
            bands[b] = max(0f, bands[b] - d)
            peaks[b] = max(0f, peaks[b] - 0.45f * dt)
        }
        for (i in 0 until WAVE) wave[i] *= 0.85f
        bass = max(0f, bass - d)
        energy = max(0f, energy - d)
        levelL = max(0f, levelL - d)
        levelR = max(0f, levelR - d)
        peakL = max(0f, peakL - 0.3f * dt)
        peakR = max(0f, peakR - 0.3f * dt)
    }
}

/**
 * Particles spiral out of the centre like a galaxy arm; bass kicks fire bursts and
 * expanding shockwave rings. Coordinates are normalised (-1..1).
 */
class ParticleField(private val capacity: Int = 320) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val life = FloatArray(capacity)
    val size = FloatArray(capacity)
    /** 0..1 position along the colour gradient for each particle. */
    val tone = FloatArray(capacity)

    val ringRadius = FloatArray(RINGS)
    val ringLife = FloatArray(RINGS)
    var spin = 0f
        private set

    private var spawnAcc = 0f
    private var prevBass = 0f
    private var ringCooldown = 0f

    fun update(dt: Float, bass: Float, energy: Float) {
        val kick = (bass - prevBass).coerceAtLeast(0f)
        prevBass = bass
        spin += dt * (0.25f + energy * 1.4f)
        val speedBoost = 1f + bass * 2.2f
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            // Slight swirl: rotate velocity a little every frame.
            val turn = dt * 0.9f
            val nvx = vx[i] * cos(turn) - vy[i] * sin(turn)
            val nvy = vx[i] * sin(turn) + vy[i] * cos(turn)
            vx[i] = nvx
            vy[i] = nvy
            x[i] += vx[i] * dt * speedBoost
            y[i] += vy[i] * dt * speedBoost
            life[i] -= dt * 0.42f
        }

        ringCooldown -= dt
        if (kick > 0.06f && bass > 0.35f && ringCooldown <= 0f) {
            val slot = (0 until RINGS).minBy { ringLife[it] }
            ringRadius[slot] = 0.12f
            ringLife[slot] = 1f
            ringCooldown = 0.18f
        }
        for (r in 0 until RINGS) {
            if (ringLife[r] <= 0f) continue
            ringRadius[r] += dt * (0.9f + bass)
            ringLife[r] -= dt * 1.1f
        }

        spawnAcc += dt * (10f + energy * 110f) + kick * 90f
        var toSpawn = spawnAcc.toInt()
        spawnAcc -= toSpawn
        var i = 0
        while (toSpawn > 0 && i < capacity) {
            if (life[i] <= 0f) {
                // Spawn along three rotating arms for a spiral look.
                val arm = (Random.nextInt(3) * 2f * PI.toFloat() / 3f)
                val a = spin + arm + (Random.nextFloat() - 0.5f) * 0.9f
                val sp = 0.10f + Random.nextFloat() * 0.32f + kick * 1.8f
                x[i] = cos(a) * 0.10f
                y[i] = sin(a) * 0.10f
                vx[i] = cos(a) * sp
                vy[i] = sin(a) * sp
                life[i] = 0.65f + Random.nextFloat() * 0.35f
                size[i] = 1.2f + Random.nextFloat() * 2.8f + bass * 3f
                tone[i] = Random.nextFloat()
                toSpawn--
            }
            i++
        }
    }

    companion object {
        const val RINGS = 6
    }
}

class VisualizerHandle(val engine: VisualizerEngine, val tick: MutableLongState)

/** Runs the engine every frame while in composition. Read [VisualizerHandle.tick] in draw lambdas. */
@Composable
fun rememberVisualizer(): VisualizerHandle {
    val handle = remember { VisualizerHandle(VisualizerEngine(), mutableLongStateOf(0L)) }
    LaunchedEffect(handle) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = t
                handle.engine.update(dt)
                handle.tick.longValue = t
            }
        }
    }
    return handle
}
