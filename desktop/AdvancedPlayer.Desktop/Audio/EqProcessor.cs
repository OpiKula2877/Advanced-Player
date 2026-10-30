using System.Threading;
using NAudio.Wave;

namespace AdvancedPlayer.Desktop.Audio;

/// <summary>Live equalizer state. Bands are in dB (-12..+12); bass boost is 0..1 and drives a 110 Hz shelf.</summary>
public sealed record EqSettings(bool Enabled, float[] BandsDb, float BassBoost)
{
    public static EqSettings Flat => new(false, new float[EqProcessor.BandCount], 0f);
}

/// <summary>
/// 10-band graphic EQ + bass shelf + volume + soft clip, applied to float samples at the output rate.
/// Port of playback/dsp/AudioFxProcessor.kt (the reverb is not ported yet).
/// </summary>
public sealed class EqProcessor : ISampleProvider
{
    public const int BandCount = 10;
    public static readonly double[] Frequencies = { 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 };
    private const double Q = 1.41;
    private const double BassFrequency = 110.0;

    private readonly ISampleProvider _source;
    private readonly SpectrumAnalyzer? _spectrum;
    private readonly int _sampleRate;
    private readonly int _channels;
    private readonly Biquad[] _bands;
    private readonly Biquad _bass;
    private volatile EqSettings _settings;
    private int _version;
    private int _appliedVersion = -1;
    private volatile float _volume = 1f;
    private volatile bool _sourceEnded;

    public EqProcessor(ISampleProvider source, SpectrumAnalyzer? spectrum, EqSettings settings)
    {
        _source = source;
        _spectrum = spectrum;
        _sampleRate = source.WaveFormat.SampleRate;
        _channels = source.WaveFormat.Channels;
        WaveFormat = WaveFormat.CreateIeeeFloatWaveFormat(_sampleRate, _channels);
        _bands = new Biquad[BandCount];
        for (int i = 0; i < BandCount; i++) _bands[i] = new Biquad(_channels);
        _bass = new Biquad(_channels);
        _settings = settings;
    }

    public WaveFormat WaveFormat { get; }

    /// <summary>Linear output volume, 0..1, applied before the soft clip.</summary>
    public float Volume
    {
        get => _volume;
        set => _volume = Math.Clamp(value, 0f, 1f);
    }

    /// <summary>True once the decoder has run out of samples.</summary>
    public bool SourceEnded => _sourceEnded;

    /// <summary>Forgets a previous end of stream, for example after a seek back into the song.</summary>
    public void ClearEnded() => _sourceEnded = false;

    /// <summary>Called from the UI thread. The audio thread picks the new coefficients up on its next block.</summary>
    public void Update(EqSettings settings)
    {
        _settings = settings;
        Interlocked.Increment(ref _version);
    }

    public int Read(Span<float> buffer)
    {
        int read = _source.Read(buffer);
        read -= read % _channels;
        if (read <= 0)
        {
            _sourceEnded = true;
            return 0;
        }

        ApplyIfChanged();

        bool eqActive = false;
        foreach (var band in _bands)
        {
            if (!band.Bypass)
            {
                eqActive = true;
                break;
            }
        }

        bool bassActive = !_bass.Bypass;
        bool active = eqActive || bassActive;
        float volume = _volume;
        int frames = read / _channels;

        for (int f = 0; f < frames; f++)
        {
            for (int ch = 0; ch < _channels; ch++)
            {
                int i = f * _channels + ch;
                double s = buffer[i];
                if (active)
                {
                    if (eqActive)
                    {
                        foreach (var band in _bands)
                            if (!band.Bypass) s = band.Process(s, ch);
                    }

                    if (bassActive) s = _bass.Process(s, ch);
                }

                s *= volume;
                if (active) s = SoftClip(s);
                buffer[i] = (float)s;
            }
        }

        _spectrum?.Feed(buffer[..read], _channels);
        return read;
    }

    private void ApplyIfChanged()
    {
        int version = Volatile.Read(ref _version);
        if (version == _appliedVersion) return;
        _appliedVersion = version;

        var s = _settings;
        for (int i = 0; i < BandCount; i++)
        {
            double gain = s.Enabled ? s.BandsDb[i] : 0.0;
            _bands[i].SetPeaking(_sampleRate, Frequencies[i], Q, gain);
        }

        _bass.SetLowShelf(_sampleRate, BassFrequency, s.BassBoost * 12.0);
    }

    private static double SoftClip(double x)
    {
        double a = Math.Abs(x);
        if (a <= 0.85) return x;
        double sign = x < 0 ? -1 : 1;
        return sign * (0.85 + 0.15 * Math.Tanh((a - 0.85) / 0.15));
    }
}
