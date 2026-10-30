using System.IO;
using NAudio.CoreAudioApi;
using NAudio.Vorbis;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

// WasapiOut is marked obsolete in NAudio 3 in favour of WasapiPlayer; it is still the simplest shared-mode output.
#pragma warning disable CS0618

namespace AdvancedPlayer.Desktop.Audio;

public sealed record OutputDevice(string Id, string Name);

/// <summary>
/// Decodes one file at a time and plays it through WASAPI (falling back to WinMM).
/// Chain: decoder → resampler → channel mapping → EQ/volume/visualizer tap → output device.
/// </summary>
public sealed class AudioEngine : IDisposable
{
    private WaveStream? _decoder;
    private EqProcessor? _eq;
    private WasapiOut? _wasapi;
    private WaveOut? _winmm;
    private SpectrumAnalyzer? _spectrum;
    private EqSettings _eqSettings = EqSettings.Flat;
    private float _volume = 1f;

    /// <summary>Visualizer source for the file that is open now; null when nothing is open.</summary>
    public SpectrumAnalyzer? Spectrum => _spectrum;

    public bool IsOpen => _decoder != null;

    public bool IsPlaying => PlaybackState == PlaybackState.Playing;

    /// <summary>True once the decoder has delivered its last sample.</summary>
    public bool Ended => _eq?.SourceEnded ?? false;

    public TimeSpan Position
    {
        get
        {
            try
            {
                return _decoder?.CurrentTime ?? TimeSpan.Zero;
            }
            catch (Exception)
            {
                return TimeSpan.Zero;
            }
        }
    }

    public TimeSpan Duration => _decoder?.TotalTime ?? TimeSpan.Zero;

    public float Volume
    {
        get => _volume;
        set
        {
            _volume = Math.Clamp(value, 0f, 1f);
            if (_eq != null) _eq.Volume = _volume;
        }
    }

    private PlaybackState PlaybackState =>
        _wasapi?.PlaybackState ?? _winmm?.PlaybackState ?? PlaybackState.Stopped;

    public static IReadOnlyList<OutputDevice> OutputDevices()
    {
        using var enumerator = new MMDeviceEnumerator();
        return enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active)
            .Select(d => new OutputDevice(d.ID, d.FriendlyName))
            .ToList();
    }

    public void SetEqualizer(EqSettings settings)
    {
        _eqSettings = settings;
        _eq?.Update(settings);
    }

    /// <summary>Opens <paramref name="path"/> at <paramref name="start"/> and starts playing when <paramref name="autoPlay"/> is set.</summary>
    public void Open(string path, TimeSpan start, string? deviceId, bool autoPlay)
    {
        Close();

        var decoder = OpenDecoder(path);
        try
        {
            ISampleProvider samples = decoder is ISampleProvider direct ? direct : decoder.ToSampleProvider();
            using var enumerator = new MMDeviceEnumerator();
            MMDevice device = ResolveDevice(enumerator, deviceId);
            WaveFormat mix = device.AudioClient.MixFormat;

            ISampleProvider chain = samples;
            if (chain.WaveFormat.SampleRate != mix.SampleRate)
                chain = new WdlResamplingSampleProvider(chain, mix.SampleRate);
            if (chain.WaveFormat.Channels != mix.Channels)
                chain = new ChannelAdapter(chain, mix.Channels);

            if (_spectrum == null || _spectrum.SampleRate != mix.SampleRate)
                _spectrum = new SpectrumAnalyzer(mix.SampleRate);

            _eq = new EqProcessor(chain, _spectrum, _eqSettings) { Volume = _volume };
            _decoder = decoder;
            if (start > TimeSpan.Zero) decoder.CurrentTime = Clamp(start, decoder.TotalTime);

            StartOutput(device, _eq);
            if (autoPlay) Play();
        }
        catch
        {
            Close();
            throw;
        }
    }

    public void Play()
    {
        if (_wasapi != null && _wasapi.PlaybackState != PlaybackState.Playing) _wasapi.Play();
        if (_winmm != null && _winmm.PlaybackState != PlaybackState.Playing) _winmm.Play();
    }

    public void Pause()
    {
        _wasapi?.Pause();
        _winmm?.Pause();
    }

    public void Seek(TimeSpan position)
    {
        if (_decoder == null) return;
        _decoder.CurrentTime = Clamp(position, _decoder.TotalTime);
        _eq?.ClearEnded();
    }

    public void Close()
    {
        try { _wasapi?.Stop(); } catch (Exception) { /* device already gone */ }
        try { _winmm?.Stop(); } catch (Exception) { /* device already gone */ }
        _wasapi?.Dispose();
        _winmm?.Dispose();
        _wasapi = null;
        _winmm = null;
        _decoder?.Dispose();
        _decoder = null;
        _eq = null;
    }

    public void Dispose() => Close();

    private void StartOutput(MMDevice device, EqProcessor eq)
    {
        try
        {
            _wasapi = new WasapiOut(device, AudioClientShareMode.Shared, true, 150);
            _wasapi.Init(eq.ToWaveProvider());
        }
        catch (Exception)
        {
            // Some devices refuse the shared-mode float format; WinMM converts it for us.
            _wasapi?.Dispose();
            _wasapi = null;
            _winmm = new WaveOut();
            _winmm.Init(new SampleToWaveProvider16(eq));
        }
    }

    private static MMDevice ResolveDevice(MMDeviceEnumerator enumerator, string? deviceId)
    {
        if (!string.IsNullOrEmpty(deviceId))
        {
            try
            {
                return enumerator.GetDevice(deviceId);
            }
            catch (Exception)
            {
                // The saved device is gone (unplugged or disconnected): use the system default.
            }
        }

        return enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
    }

    private static WaveStream OpenDecoder(string path) =>
        Path.GetExtension(path).ToLowerInvariant() switch
        {
            ".ogg" or ".oga" => new VorbisWaveReader(path),
            _ => new MediaFoundationReader(path),
        };

    private static TimeSpan Clamp(TimeSpan value, TimeSpan total)
    {
        if (value < TimeSpan.Zero) return TimeSpan.Zero;
        return value > total ? total : value;
    }

    /// <summary>Maps any channel layout onto the device's channel count (mono is copied, extra channels are dropped or silenced).</summary>
    private sealed class ChannelAdapter : ISampleProvider
    {
        private readonly ISampleProvider _source;
        private readonly int _outChannels;
        private float[] _scratch = Array.Empty<float>();

        public ChannelAdapter(ISampleProvider source, int outChannels)
        {
            _source = source;
            _outChannels = outChannels;
            WaveFormat = WaveFormat.CreateIeeeFloatWaveFormat(source.WaveFormat.SampleRate, outChannels);
        }

        public WaveFormat WaveFormat { get; }

        public int Read(Span<float> buffer)
        {
            int inChannels = _source.WaveFormat.Channels;
            int frames = buffer.Length / _outChannels;
            int needed = frames * inChannels;
            if (_scratch.Length < needed) _scratch = new float[needed];

            int read = _source.Read(_scratch.AsSpan(0, needed));
            int framesRead = read / inChannels;
            for (int f = 0; f < framesRead; f++)
            {
                for (int c = 0; c < _outChannels; c++)
                {
                    float value;
                    if (c < inChannels) value = _scratch[f * inChannels + c];
                    else value = inChannels == 1 ? _scratch[f] : 0f;
                    buffer[f * _outChannels + c] = value;
                }
            }

            return framesRead * _outChannels;
        }
    }
}
