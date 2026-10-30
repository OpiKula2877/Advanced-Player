using AdvancedPlayer.Desktop.Audio;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Library;
using AdvancedPlayer.Desktop.Models;
using AdvancedPlayer.Desktop.Phone;

namespace AdvancedPlayer.Desktop;

/// <summary>Creates and owns the long-lived objects: database, library, audio engine, player and phone link.</summary>
public sealed class AppServices : IDisposable
{
    public const long CountedListenMs = 5_000;

    public AppServices(string dataDirectory)
    {
        DataDirectory = dataDirectory;
        Directory.CreateDirectory(dataDirectory);
        Db = new Database(Path.Combine(dataDirectory, "library.db"));
        Settings = new AppSettings(Db);
        Library = new LibraryService(Db, dataDirectory);
        Library.Reload();
        Phone = new PhoneLibrary(Library);

        Engine = new AudioEngine();
        Engine.SetEqualizer(Settings.Equalizer);
        Engine.Volume = Settings.Volume;

        Player = new Player(Engine, new PlayQueue(), ResolveFileAsync, LogListen)
        {
            OutputDeviceId = Settings.OutputDeviceId,
        };
        Player.Volume = Settings.Volume;
    }

    public string DataDirectory { get; }

    public Database Db { get; }

    public AppSettings Settings { get; }

    public LibraryService Library { get; }

    public PhoneLibrary Phone { get; }

    public AudioEngine Engine { get; }

    public Player Player { get; }

    /// <summary>The open connection to the phone, or null when not connected.</summary>
    public PhoneSession? PhoneSession { get; set; }

    public PhoneDevice? PhoneDevice { get; set; }

    private async Task<string> ResolveFileAsync(Track track)
    {
        if (!track.IsPhone)
        {
            if (track.LocalFile == null || !File.Exists(track.LocalFile))
                throw new FileNotFoundException("The file is missing: " + track.DisplayTitle, track.LocalFile);
            return track.LocalFile;
        }

        PhoneSession session = PhoneSession ?? throw new PhoneException(Loc.Get("phone_not_connected"));
        string path = await Phone.EnsureFileAsync(track, session);
        await Phone.EnsureMetadataAsync(track, session);
        return path;
    }

    private void LogListen(Track track, long listenedMs)
    {
        bool counted = listenedMs >= CountedListenMs;
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        Db.LogPlay(track.Id, listenedMs, counted, now);
        if (counted)
        {
            track.PlayCount++;
            track.LastPlayed = now;
        }
    }

    public void Dispose()
    {
        Player.Stop();
        Engine.Dispose();
        PhoneSession?.DisposeAsync().AsTask().Wait(TimeSpan.FromSeconds(2));
        Db.Dispose();
    }
}
