using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Audio;

/// <summary>
/// Playback controller: ties the queue to the audio engine, opens files (downloading phone songs first),
/// skips broken files, and records how long each song was listened to.
/// </summary>
public sealed class Player
{
    private readonly AudioEngine _engine;
    private readonly Func<Track, Task<string>> _resolveFile;
    private readonly Action<Track, long> _logListen;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private Track? _current;
    private string? _currentPath;
    private long _listenedMs;
    private bool _advancing;

    public Player(AudioEngine engine, PlayQueue queue, Func<Track, Task<string>> resolveFile, Action<Track, long> logListen)
    {
        _engine = engine;
        Queue = queue;
        _resolveFile = resolveFile;
        _logListen = logListen;
    }

    /// <summary>Raised on the UI thread whenever the track, the playing state or the loading state changes.</summary>
    public event Action? Changed;

    public PlayQueue Queue { get; }

    public Track? Current => _current;

    public bool IsLoading { get; private set; }

    public string? LastError { get; private set; }

    public bool IsPlaying => _engine.IsPlaying;

    public TimeSpan Position => _engine.Position;

    public TimeSpan Duration => _engine.Duration;

    public AudioEngine Engine => _engine;

    /// <summary>Output device used for the next file; null means the Windows default device.</summary>
    public string? OutputDeviceId { get; set; }

    public float Volume
    {
        get => _engine.Volume;
        set => _engine.Volume = value;
    }

    /// <summary>Replaces the queue with <paramref name="tracks"/> and starts at <paramref name="index"/>. Null shuffle keeps the current mode.</summary>
    public Task PlayListAsync(IReadOnlyList<Track> tracks, int index, bool? shuffle = null)
    {
        Queue.Set(tracks, index, shuffle ?? Queue.Shuffle);
        return OpenAsync(TimeSpan.Zero);
    }

    public async Task PlayNextAsync(Track track)
    {
        Queue.PlayNext(track);
        if (!_engine.IsPlaying && _current == null) await OpenAsync(TimeSpan.Zero);
        else RaiseChanged();
    }

    public async Task EnqueueAsync(Track track)
    {
        Queue.Enqueue(track);
        if (_current == null) await OpenAsync(TimeSpan.Zero);
        else RaiseChanged();
    }

    public async Task TogglePlayPauseAsync()
    {
        if (Queue.Current == null) return;
        if (!_engine.IsOpen) await OpenAsync(TimeSpan.Zero);
        else if (_engine.Ended) await OpenAsync(TimeSpan.Zero);
        else if (_engine.IsPlaying) Pause();
        else _engine.Play();
        RaiseChanged();
    }

    public void Pause()
    {
        _engine.Pause();
        RaiseChanged();
    }

    public async Task NextAsync()
    {
        if (Queue.Next(userRequested: true) != null) await OpenAsync(TimeSpan.Zero);
    }

    public async Task PreviousAsync()
    {
        // Like most players: a press after a few seconds restarts the song instead of going back.
        if (Position > TimeSpan.FromSeconds(3))
        {
            Seek(TimeSpan.Zero);
            return;
        }

        if (Queue.Previous() != null) await OpenAsync(TimeSpan.Zero);
    }

    public void Seek(TimeSpan position)
    {
        if (!_engine.IsOpen) return;
        bool wasEnded = _engine.Ended;
        _engine.Seek(position);
        if (wasEnded) _engine.Play();
        RaiseChanged();
    }

    public void Stop()
    {
        FinishCurrent();
        _engine.Close();
        RaiseChanged();
    }

    public void SetShuffle(bool shuffle)
    {
        Queue.SetShuffle(shuffle);
        RaiseChanged();
    }

    /// <summary>Plays the upcoming song at <paramref name="upcomingIndex"/> right away.</summary>
    public Task JumpToUpcomingAsync(int upcomingIndex) =>
        Queue.JumpToUpcoming(upcomingIndex) ? OpenAsync(TimeSpan.Zero) : Task.CompletedTask;

    public void RemoveUpcoming(int upcomingIndex)
    {
        Queue.RemoveUpcoming(upcomingIndex);
        RaiseChanged();
    }

    public void ClearUpcoming()
    {
        Queue.ClearUpcoming();
        RaiseChanged();
    }

    /// <summary>Moves playback to another output device, keeping the position and the play/pause state.</summary>
    public async Task SetOutputDeviceAsync(string? deviceId)
    {
        OutputDeviceId = deviceId;
        if (_currentPath == null || !_engine.IsOpen) return;
        await _gate.WaitAsync();
        try
        {
            TimeSpan position = _engine.Position;
            bool playing = _engine.IsPlaying;
            _engine.Open(_currentPath, position, deviceId, playing);
        }
        finally
        {
            _gate.Release();
        }
    }

    /// <summary>Called by a UI timer, about four times a second. Counts listening time and moves on when a song ends.</summary>
    public void Tick(TimeSpan elapsed)
    {
        if (_current != null && _engine.IsPlaying) _listenedMs += (long)elapsed.TotalMilliseconds;
        if (_current != null && _engine.Ended && !_advancing) _ = AdvanceAfterEndAsync();
    }

    private async Task AdvanceAfterEndAsync()
    {
        _advancing = true;
        try
        {
            if (Queue.Next(userRequested: false) != null) await OpenAsync(TimeSpan.Zero);
            else Stop();
        }
        finally
        {
            _advancing = false;
        }
    }

    private async Task OpenAsync(TimeSpan start)
    {
        await _gate.WaitAsync();
        try
        {
            FinishCurrent();
            for (int attempt = 0; attempt <= Queue.Count; attempt++)
            {
                Track? track = Queue.Current;
                _current = track;
                if (track == null)
                {
                    _engine.Close();
                    break;
                }

                track.IsPlaying = true;
                IsLoading = true;
                RaiseChanged();
                try
                {
                    string path = await _resolveFile(track);
                    _engine.Open(path, attempt == 0 ? start : TimeSpan.Zero, OutputDeviceId, autoPlay: true);
                    _currentPath = path;
                    LastError = null;
                    break;
                }
                catch (Exception ex)
                {
                    // A file that cannot be decoded or downloaded is skipped instead of stopping the queue.
                    _engine.Close();
                    LastError = ex.Message;
                    FinishCurrent();
                    if (Queue.Next(userRequested: true) == null) break;
                }
                finally
                {
                    IsLoading = false;
                }
            }
        }
        finally
        {
            _gate.Release();
            RaiseChanged();
        }
    }

    private void FinishCurrent()
    {
        if (_current != null)
        {
            if (_listenedMs > 0) _logListen(_current, _listenedMs);
            _current.IsPlaying = false;
        }

        _listenedMs = 0;
    }

    private void RaiseChanged() => Changed?.Invoke();
}
