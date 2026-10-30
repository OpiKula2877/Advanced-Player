using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace AdvancedPlayer.Desktop.Models;

/// <summary>One playable song, either a file on this PC or a song that the phone shares over Bluetooth.</summary>
public sealed class Track : INotifyPropertyChanged
{
    private bool _favorite;
    private bool _isPlaying;

    public long Id { get; set; }

    /// <summary>Unique key: the absolute path for local files, or "phone:&lt;address&gt;:&lt;remoteId&gt;" for phone songs.</summary>
    public string Key { get; set; } = "";

    public bool IsPhone { get; set; }

    /// <summary>Bluetooth address of the phone that shares this song (phone songs only).</summary>
    public string PhoneAddress { get; set; } = "";

    /// <summary>The song's id on the phone (phone songs only).</summary>
    public long RemoteId { get; set; }

    public string Title { get; set; } = "";
    public string Artist { get; set; } = "";
    public string Album { get; set; } = "";
    public string Genre { get; set; } = "";
    public int Year { get; set; }
    public int TrackNumber { get; set; }
    public long DurationMs { get; set; }
    public long SizeBytes { get; set; }
    public long DateAdded { get; set; }
    public long FileWriteTicks { get; set; }
    public string FileName { get; set; } = "";
    public string MimeType { get; set; } = "";

    /// <summary>The file to play: the original for local songs, a cached copy for phone songs (null until downloaded).</summary>
    public string? LocalFile { get; set; }

    public string? ArtPath { get; set; }
    public string? Lyrics { get; set; }

    public bool Favorite
    {
        get => _favorite;
        set => SetField(ref _favorite, value);
    }

    public int PlayCount { get; set; }
    public long LastPlayed { get; set; }

    /// <summary>True while this song is the one playing (drives the highlight in lists).</summary>
    public bool IsPlaying
    {
        get => _isPlaying;
        set => SetField(ref _isPlaying, value);
    }

    /// <summary>Copies the stored fields of <paramref name="s"/>. Used when the library is reloaded, so the instance stays the same.</summary>
    public void CopyFrom(Track s)
    {
        Id = s.Id;
        Key = s.Key;
        IsPhone = s.IsPhone;
        PhoneAddress = s.PhoneAddress;
        RemoteId = s.RemoteId;
        Title = s.Title;
        Artist = s.Artist;
        Album = s.Album;
        Genre = s.Genre;
        Year = s.Year;
        TrackNumber = s.TrackNumber;
        DurationMs = s.DurationMs;
        SizeBytes = s.SizeBytes;
        DateAdded = s.DateAdded;
        FileWriteTicks = s.FileWriteTicks;
        FileName = s.FileName;
        MimeType = s.MimeType;
        LocalFile = s.LocalFile;
        ArtPath = s.ArtPath;
        Lyrics = s.Lyrics;
        PlayCount = s.PlayCount;
        LastPlayed = s.LastPlayed;
        Favorite = s.Favorite;
    }

    public string DisplayTitle => string.IsNullOrWhiteSpace(Title) ? FileName : Title;
    public string DisplayArtist => string.IsNullOrWhiteSpace(Artist) ? "—" : Artist;
    public string DisplayAlbum => string.IsNullOrWhiteSpace(Album) ? "—" : Album;
    public string DurationText => TimeFormat.Format(TimeSpan.FromMilliseconds(DurationMs));

    public event PropertyChangedEventHandler? PropertyChanged;

    private void SetField<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }
}

public static class TimeFormat
{
    /// <summary>m:ss, or h:mm:ss for songs longer than an hour.</summary>
    public static string Format(TimeSpan t)
    {
        if (t < TimeSpan.Zero) t = TimeSpan.Zero;
        return t.TotalHours >= 1
            ? $"{(int)t.TotalHours}:{t.Minutes:00}:{t.Seconds:00}"
            : $"{t.Minutes}:{t.Seconds:00}";
    }
}
