using System.Security.Cryptography;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Library;

/// <summary>
/// Owns the in-memory track list. Local files are scanned from the chosen folders and tags are read with TagLib#.
/// Track objects are reused across reloads so that the player, the queue and the lists keep pointing at the same instances.
/// </summary>
public sealed class LibraryService
{
    public static readonly string[] AudioExtensions = { ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".oga", ".wav", ".wma" };
    private static readonly string[] CoverNames = { "cover", "folder", "front" };
    private static readonly string[] CoverExtensions = { ".jpg", ".jpeg", ".png" };

    private readonly Dictionary<string, Track> _byKey = new(StringComparer.Ordinal);
    private List<Track> _tracks = new();

    public LibraryService(Database db, string dataDirectory)
    {
        Db = db;
        ArtDirectory = Path.Combine(dataDirectory, "art");
        PhoneCacheDirectory = Path.Combine(dataDirectory, "phone");
        Directory.CreateDirectory(ArtDirectory);
        Directory.CreateDirectory(PhoneCacheDirectory);
    }

    public Database Db { get; }

    public string ArtDirectory { get; }

    public string PhoneCacheDirectory { get; }

    /// <summary>Every track in the library: local files and songs from the phone.</summary>
    public IReadOnlyList<Track> Tracks => _tracks;

    /// <summary>Re-reads the database. Existing Track objects are updated in place.</summary>
    public void Reload()
    {
        var rows = Db.LoadTracks();
        var seen = new HashSet<string>(StringComparer.Ordinal);
        var list = new List<Track>(rows.Count);
        foreach (Track row in rows)
        {
            Track current;
            if (_byKey.TryGetValue(row.Key, out var existing))
            {
                existing.CopyFrom(row);
                current = existing;
            }
            else
            {
                _byKey[row.Key] = row;
                current = row;
            }

            seen.Add(row.Key);
            list.Add(current);
        }

        foreach (string key in _byKey.Keys.Where(k => !seen.Contains(k)).ToList()) _byKey.Remove(key);
        _tracks = list;
    }

    /// <summary>Adds new and changed files from the library folders and removes files that no longer exist.</summary>
    public async Task<int> ScanAsync(IProgress<string>? progress = null)
    {
        return await Task.Run(() =>
        {
            var folders = Db.Folders();
            var index = Db.LocalIndex();
            int changed = 0;

            foreach (string folder in folders)
            {
                if (!Directory.Exists(folder)) continue;
                foreach (string file in EnumerateAudio(folder))
                {
                    var info = new FileInfo(file);
                    if (index.TryGetValue(file, out var known) && known.Size == info.Length && known.Ticks == info.LastWriteTimeUtc.Ticks)
                        continue;

                    progress?.Report(info.Name);
                    Db.UpsertTrack(ReadLocalTrack(file, info));
                    changed++;
                }
            }

            var gone = index
                .Where(kv => !File.Exists(kv.Key) || !folders.Any(f => kv.Key.StartsWith(f, StringComparison.OrdinalIgnoreCase)))
                .Select(kv => kv.Value.Id)
                .ToList();
            if (gone.Count > 0) Db.DeleteTracks(gone);

            return changed;
        });
    }

    private static IEnumerable<string> EnumerateAudio(string folder)
    {
        var options = new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true };
        return Directory.EnumerateFiles(folder, "*", options)
            .Where(f => AudioExtensions.Contains(Path.GetExtension(f), StringComparer.OrdinalIgnoreCase));
    }

    private Track ReadLocalTrack(string path, FileInfo info)
    {
        var track = new Track
        {
            Key = path,
            LocalFile = path,
            FileName = Path.GetFileNameWithoutExtension(path),
            SizeBytes = info.Length,
            FileWriteTicks = info.LastWriteTimeUtc.Ticks,
            DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            MimeType = MimeTypeFor(path),
        };

        try
        {
            using var file = TagLib.File.Create(path);
            var tag = file.Tag;
            track.Title = tag.Title ?? "";
            track.Artist = FirstNonEmpty(tag.JoinedPerformers, tag.JoinedAlbumArtists);
            track.Album = tag.Album ?? "";
            track.Genre = tag.JoinedGenres ?? "";
            track.Year = (int)tag.Year;
            track.TrackNumber = (int)tag.Track;
            track.DurationMs = (long)(file.Properties?.Duration.TotalMilliseconds ?? 0);
            track.Lyrics = ReadSidecarLyrics(path) ?? NullIfBlank(tag.Lyrics);
            track.ArtPath = SaveArt(tag.Pictures) ?? FolderCover(Path.GetDirectoryName(path));
        }
        catch (Exception)
        {
            // Unreadable tags: the song stays in the library under its file name.
        }

        return track;
    }

    private string? SaveArt(TagLib.IPicture[] pictures)
    {
        var picture = pictures.FirstOrDefault(p => p.Type == TagLib.PictureType.FrontCover) ?? pictures.FirstOrDefault();
        if (picture == null) return null;
        return SaveArtBytes(picture.Data.Data, picture.MimeType);
    }

    /// <summary>Stores image bytes in the art cache under a hash of their content and returns the file path.</summary>
    public string SaveArtBytes(byte[] data, string? mimeType)
    {
        string hash = Convert.ToHexString(SHA256.HashData(data))[..32];
        string extension = (mimeType ?? "").Contains("png", StringComparison.OrdinalIgnoreCase) || IsPng(data) ? ".png" : ".jpg";
        string path = Path.Combine(ArtDirectory, hash + extension);
        if (!File.Exists(path)) File.WriteAllBytes(path, data);
        return path;
    }

    private static string? FolderCover(string? directory)
    {
        if (directory == null) return null;
        foreach (string name in CoverNames)
        foreach (string extension in CoverExtensions)
        {
            string candidate = Path.Combine(directory, name + extension);
            if (File.Exists(candidate)) return candidate;
        }

        return null;
    }

    private static string? ReadSidecarLyrics(string path)
    {
        string lrc = Path.ChangeExtension(path, ".lrc");
        return File.Exists(lrc) ? NullIfBlank(File.ReadAllText(lrc)) : null;
    }

    private static string MimeTypeFor(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".mp3" => "audio/mpeg",
        ".m4a" or ".aac" => "audio/mp4",
        ".flac" => "audio/flac",
        ".ogg" or ".oga" => "audio/ogg",
        ".wav" => "audio/wav",
        ".wma" => "audio/x-ms-wma",
        _ => "application/octet-stream",
    };

    private static bool IsPng(byte[] data) =>
        data.Length > 4 && data[0] == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';

    private static string FirstNonEmpty(params string?[] values) =>
        values.FirstOrDefault(v => !string.IsNullOrWhiteSpace(v))?.Trim() ?? "";

    private static string? NullIfBlank(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value;
}
