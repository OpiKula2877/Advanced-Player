package com.advancedplayer.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.tanh

/** Live effect parameters shared between the settings UI and the audio thread. */
class FxParams {
    @Volatile var eqEnabled = false
    @Volatile var bands = FloatArray(BAND_COUNT)
    @Volatile var bassBoost = 0f
    @Volatile var reverb = 0f
    @Volatile var version = 0

    fun update(eqEnabled: Boolean, bands: List<Float>, bassBoost: Float, reverb: Float) {
        val arr = bands.toFloatArray()
        if (eqEnabled == this.eqEnabled && arr.contentEquals(this.bands) &&
            bassBoost == this.bassBoost && reverb == this.reverb
        ) return
        this.eqEnabled = eqEnabled
        this.bands = arr
        this.bassBoost = bassBoost
        this.reverb = reverb
        version++
    }

    companion object {
        const val BAND_COUNT = 10
        val FREQUENCIES = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        const val MAX_DB = 12f
    }
}

/**
 * 10-band graphic EQ + bass shelf + reverb on 16-bit PCM, and a tap that feeds the
 * visualizers. Runs before Sonic (speed/pitch) in the ExoPlayer audio chain.
 */
@UnstableApi
class AudioFxProcessor(private val params: FxParams, private val feedTap: Boolean) : BaseAudioProcessor() {
    private var sampleRate = 44100
    private var channels = 2
    private val eq = Array(FxParams.BAND_COUNT) { Biquad() }
    private val bass = Biquad()
    private var reverb: SimpleReverb? = null
    private var appliedVersion = -1
    private var tapL = FloatArray(4096)
    private var tapR = FloatArray(4096)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        reverb = SimpleReverb(sampleRate)
        appliedVersion = -1
        if (feedTap) AudioTap.sampleRate = sampleRate
        return inputAudioFormat
    }

    private fun refreshCoefficients() {
        val v = params.version
        if (v == appliedVersion) return
        appliedVersion = v
        val bands = params.bands
        for (i in eq.indices) {
            val gain = if (params.eqEnabled) bands.getOrElse(i) { 0f }.toDouble() else 0.0
            eq[i].setPeaking(sampleRate, FxParams.FREQUENCIES[i], 1.41, gain)
        }
        bass.setLowShelf(sampleRate, 110.0, params.bassBoost * 12.0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        refreshCoefficients()
        val out = replaceOutputBuffer(remaining)
        val frames = remaining / (2 * channels)
        if (feedTap && tapL.size < frames) {
            tapL = FloatArray(frames)
            tapR = FloatArray(frames)
        }
        val wet = params.reverb * 0.6
        val rv = reverb
        val anyEq = eq.any { !it.bypass }
        val active = anyEq || !bass.bypass || wet > 0.0

        for (f in 0 until frames) {
            var reverbOut = 0.0
            if (wet > 0.0 && rv != null) {
                // Reverb works on the mono input; read ahead without moving the buffer position.
                var sum = 0.0
                val base = inputBuffer.position()
                for (ch in 0 until channels) sum += inputBuffer.getShort(base + ch * 2) / 32768.0
                reverbOut = rv.process(sum / channels) * wet
            }
            for (ch in 0 until channels) {
                val raw = inputBuffer.getShort()
                var s: Double
                if (active) {
                    s = raw / 32768.0
                    if (anyEq) for (b in eq) if (!b.bypass) s = b.process(s, ch)
                    if (!bass.bypass) s = bass.process(s, ch)
                    s = s * (1 - wet * 0.35) + reverbOut
                    s = softClip(s)
                    out.putShort((s * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                } else {
                    s = raw / 32768.0
                    out.putShort(raw)
                }
                if (feedTap) {
                    if (ch == 0) tapL[f] = s.toFloat()
                    if (ch == 1 || channels == 1) tapR[f] = s.toFloat()
                }
            }
        }
        if (feedTap) AudioTap.write(tapL, tapR, frames)
        out.flip()
    }

    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= 0.85) return x
        val sign = if (x < 0) -1 else 1
        return sign * (0.85 + 0.15 * tanh((a - 0.85) / 0.15))
    }

    override fun onFlush() {
        eq.forEach { it.reset() }
        bass.reset()
        reverb?.reset()
    }

    override fun onReset() {
        onFlush()
        appliedVersion = -1
    }
}
