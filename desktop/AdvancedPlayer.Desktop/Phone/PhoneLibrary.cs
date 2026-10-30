using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Library;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Phone;

/// <summary>Keeps the phone's songs in the library: the song list is synced, files, art and lyrics are fetched when needed.</summary>
public sealed class PhoneLibrary
{
    private readonly LibraryService _library;
    private readonly Database _db;

    public PhoneLibrary(LibraryService library)
    {
        _library = library;
        _db = library.Db;
    }

    /// <summary>Stores the phone's song list. Songs the phone no longer offers are removed from the library.</summary>
    public async Task<int> SyncAsync(PhoneSession session, string address, CancellationToken ct = default)
    {
        List<PhoneSong> songs = await session.ListAsync(ct);
        var offered = new HashSet<long>();
        foreach (PhoneSong song in songs)
        {
            offered.Add(song.Id);
            _db.UpsertTrack(new Track
            {
                Key = KeyFor(address, song.Id),
                IsPhone = true,
                PhoneAddress = address,
                RemoteId = song.Id,
                Title = song.Title,
                Artist = song.Artist,
                Album = song.Album,
                Genre = song.Genre,
                Year = song.Year,
                TrackNumber = song.TrackNo,
                DurationMs = song.DurationMs,
                SizeBytes = song.Size,
                DateAdded = song.DateAdded,
                FileName = song.Name,
                MimeType = song.Mime,
            });
        }

        var removed = _db.PhoneTracks(address).Where(t => !offered.Contains(t.RemoteId)).Select(t => t.Id).ToList();
        if (removed.Count > 0) _db.DeleteTracks(removed);
        _library.Reload();
        return songs.Count;
    }

    /// <summary>Returns a local file for the song, downloading it over Bluetooth the first time.</summary>
    public async Task<string> EnsureFileAsync(Track track, PhoneSession session, CancellationToken ct = default)
    {
        if (track.LocalFile is { } existing && File.Exists(existing)) return existing;

        string directory = Path.Combine(_library.PhoneCacheDirectory, SafeName(track.PhoneAddress));
        Directory.CreateDirectory(directory);
        string extension = Path.GetExtension(track.FileName);
        if (string.IsNullOrEmpty(extension)) extension = ".mp3";

        string path = Path.Combine(directory, track.RemoteId + extension);
        string partial = path + ".part";
        await using (var file = File.Create(partial))
        {
            await session.DownloadAsync(track.RemoteId, file, null, ct);
        }

        File.Move(partial, path, overwrite: true);
        _db.SetLocalFile(track.Id, path, null, null);
        track.LocalFile = path;
        return path;
    }

    /// <summary>Fetches cover art and lyrics for a phone song once; later calls do nothing.</summary>
    public async Task EnsureMetadataAsync(Track track, PhoneSession session, CancellationToken ct = default)
    {
        string? artPath = null;
        string? lyrics = null;

        if (track.ArtPath == null)
        {
            byte[]? art = await session.ArtAsync(track.RemoteId, ct);
            if (art != null) artPath = _library.SaveArtBytes(art, null);
        }

        if (track.Lyrics == null)
        {
            lyrics = await session.LyricsAsync(track.RemoteId, ct);
        }

        if (artPath == null && lyrics == null) return;
        _db.SetLocalFile(track.Id, null, artPath, lyrics);
        track.ArtPath ??= artPath;
        track.Lyrics ??= lyrics;
    }

    public static string KeyFor(string address, long songId) => $"phone:{address}:{songId}";

    private static string SafeName(string value) =>
        string.Concat(value.Select(c => char.IsLetterOrDigit(c) ? c : '_'));
}
