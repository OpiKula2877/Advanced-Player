namespace AdvancedPlayer.Desktop.Audio;

/// <summary>
/// Turns the most recent output samples into smoothed spectrum bands for the visualizer.
/// Mirrors ui/visualizer/VisualizerEngine.kt: 2048-point FFT, 64 log-spaced bands from 32 Hz to 16 kHz.
/// </summary>
public sealed class SpectrumAnalyzer
{
    public const int FftSize = 2048;
    public const int BandCount = 64;
    private const double MinHz = 32.0;
    private const double MaxHz = 16000.0;

    private readonly float[] _ring = new float[FftSize * 2];
    private readonly object _lock = new();
    private readonly float[] _window = new float[FftSize];
    private readonly int[] _binEdges = new int[BandCount + 1];
    private readonly float[] _re = new float[FftSize];
    private readonly float[] _im = new float[FftSize];
    private int _write;

    public SpectrumAnalyzer(int sampleRate)
    {
        SampleRate = sampleRate;
        for (int i = 0; i < FftSize; i++)
            _window[i] = (float)(0.5 - 0.5 * Math.Cos(2 * Math.PI * i / (FftSize - 1)));

        double binHz = sampleRate / (double)FftSize;
        for (int b = 0; b <= BandCount; b++)
        {
            double f = MinHz * Math.Pow(MaxHz / MinHz, b / (double)BandCount);
            _binEdges[b] = Math.Clamp((int)Math.Round(f / binHz), 1, FftSize / 2);
        }
        for (int b = 1; b <= BandCount; b++)
            if (_binEdges[b] <= _binEdges[b - 1]) _binEdges[b] = Math.Min(_binEdges[b - 1] + 1, FftSize / 2);
    }

    public int SampleRate { get; }

    /// <summary>Smoothed band levels in 0..1. Only touched from the UI thread.</summary>
    public float[] Levels { get; } = new float[BandCount];

    /// <summary>Called from the audio thread with interleaved samples; keeps a mono ring buffer.</summary>
    public void Feed(ReadOnlySpan<float> samples, int channels)
    {
        lock (_lock)
        {
            for (int i = 0; i + channels <= samples.Length; i += channels)
            {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += samples[i + c];
                _ring[_write] = sum / channels;
                _write = (_write + 1) % _ring.Length;
            }
        }
    }

    /// <summary>Recomputes <see cref="Levels"/> from the latest samples. Call about 30 times per second.</summary>
    public void Update(float dt)
    {
        lock (_lock)
        {
            int start = (_write - FftSize + _ring.Length) % _ring.Length;
            for (int i = 0; i < FftSize; i++)
            {
                _re[i] = _ring[(start + i) % _ring.Length] * _window[i];
                _im[i] = 0f;
            }
        }

        Fft(_re, _im);

        for (int b = 0; b < BandCount; b++)
        {
            float sum = 0f;
            int n = 0;
            for (int k = _binEdges[b]; k < _binEdges[b + 1] && k < FftSize / 2; k++)
            {
                sum += MathF.Sqrt(_re[k] * _re[k] + _im[k] * _im[k]);
                n++;
            }

            float mag = n > 0 ? sum / n : 0f;
            double db = 20 * Math.Log10(mag / (FftSize / 4.0) + 1e-9);
            double target = (db + 60) / 60 + 0.15 * b / BandCount; // gentle tilt so highs stay visible
            float v = (float)Math.Clamp(target, 0.0, 1.0);

            float old = Levels[b];
            Levels[b] = v > old ? old + (v - old) * 0.65f : Math.Max(v, old - 1.6f * dt);
        }
    }

    /// <summary>In-place iterative radix-2 FFT (port of Fft.transform in Dsp.kt).</summary>
    private static void Fft(float[] re, float[] im)
    {
        int n = re.Length;
        for (int i = 1, j = 0; i < n; i++)
        {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j)
            {
                (re[i], re[j]) = (re[j], re[i]);
                (im[i], im[j]) = (im[j], im[i]);
            }
        }

        for (int len = 2; len <= n; len <<= 1)
        {
            double ang = -2 * Math.PI / len;
            float wr = (float)Math.Cos(ang);
            float wi = (float)Math.Sin(ang);
            for (int i = 0; i < n; i += len)
            {
                float cr = 1f, ci = 0f;
                for (int k = 0; k < len / 2; k++)
                {
                    int a = i + k;
                    int b = a + len / 2;
                    float xr = re[b] * cr - im[b] * ci;
                    float xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }
}
