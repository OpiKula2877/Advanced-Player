namespace AdvancedPlayer.Desktop.Audio;

/// <summary>
/// RBJ-cookbook biquad in transposed direct form II, with per-channel state.
/// A port of playback/dsp/Dsp.kt from the Android app, so both EQs sound the same.
/// </summary>
public sealed class Biquad
{
    private double _b0 = 1, _b1, _b2, _a1, _a2;
    private readonly double[] _z1;
    private readonly double[] _z2;

    public Biquad(int channels)
    {
        _z1 = new double[channels];
        _z2 = new double[channels];
    }

    public bool Bypass { get; private set; } = true;

    public void SetPeaking(int sampleRate, double f0, double q, double gainDb)
    {
        if (gainDb == 0.0 || f0 >= sampleRate / 2.0)
        {
            Bypass = true;
            return;
        }

        Bypass = false;
        double a = Math.Pow(10.0, gainDb / 40);
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double alpha = Math.Sin(w0) / (2 * q);
        double cw = Math.Cos(w0);
        double a0 = 1 + alpha / a;
        _b0 = (1 + alpha * a) / a0;
        _b1 = -2 * cw / a0;
        _b2 = (1 - alpha * a) / a0;
        _a1 = -2 * cw / a0;
        _a2 = (1 - alpha / a) / a0;
    }

    public void SetLowShelf(int sampleRate, double f0, double gainDb)
    {
        if (gainDb == 0.0)
        {
            Bypass = true;
            return;
        }

        Bypass = false;
        double a = Math.Pow(10.0, gainDb / 40);
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double cw = Math.Cos(w0);
        double alpha = Math.Sin(w0) / 2 * Math.Sqrt(2.0); // shelf slope S = 1
        double sa = 2 * Math.Sqrt(a) * alpha;
        double a0 = (a + 1) + (a - 1) * cw + sa;
        _b0 = a * ((a + 1) - (a - 1) * cw + sa) / a0;
        _b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0;
        _b2 = a * ((a + 1) - (a - 1) * cw - sa) / a0;
        _a1 = -2 * ((a - 1) + (a + 1) * cw) / a0;
        _a2 = ((a + 1) + (a - 1) * cw - sa) / a0;
    }

    public double Process(double x, int ch)
    {
        double y = _b0 * x + _z1[ch];
        _z1[ch] = _b1 * x - _a1 * y + _z2[ch];
        _z2[ch] = _b2 * x - _a2 * y;
        return y;
    }

    public void Reset()
    {
        Array.Fill(_z1, 0.0);
        Array.Fill(_z2, 0.0);
    }
}
