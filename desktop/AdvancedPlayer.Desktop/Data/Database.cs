using AdvancedPlayer.Desktop.Models;
using Microsoft.Data.Sqlite;

namespace AdvancedPlayer.Desktop.Data;

public sealed record PlaylistInfo(long Id, string Name, int Count);

public sealed record StatsSummary(int Tracks, int Plays, long ListenedMs);

public sealed record TopTrack(Track Track, int Plays, long ListenedMs);

/// <summary>SQLite storage for the library, playlists, listening history and settings. Every call is serialized.</summary>
public sealed class Database : IDisposable
{
    private const string TrackColumns =
        "id, key, is_phone, phone_address, remote_id, title, artist, album, genre, year, track_no, duration_ms, " +
        "size_bytes, date_added, file_mtime, local_file, art_path, lyrics, mime_type, file_name, favorite, play_count, last_played";

    private readonly object _gate = new();
    private readonly SqliteConnection _conn;

    public Database(string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        _conn = new SqliteConnection($"Data Source={path}");
        _conn.Open();
        Run("PRAGMA foreign_keys = ON;");
        Run("PRAGMA journal_mode = WAL;");
        Run(Schema);
    }

    private const string Schema = """
        CREATE TABLE IF NOT EXISTS tracks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            key TEXT NOT NULL UNIQUE,
            is_phone INTEGER NOT NULL DEFAULT 0,
            phone_address TEXT NOT NULL DEFAULT '',
            remote_id INTEGER NOT NULL DEFAULT 0,
            title TEXT NOT NULL DEFAULT '',
            artist TEXT NOT NULL DEFAULT '',
            album TEXT NOT NULL DEFAULT '',
            genre TEXT NOT NULL DEFAULT '',
            year INTEGER NOT NULL DEFAULT 0,
            track_no INTEGER NOT NULL DEFAULT 0,
            duration_ms INTEGER NOT NULL DEFAULT 0,
            size_bytes INTEGER NOT NULL DEFAULT 0,
            date_added INTEGER NOT NULL DEFAULT 0,
            file_mtime INTEGER NOT NULL DEFAULT 0,
            local_file TEXT,
            art_path TEXT,
            lyrics TEXT,
            mime_type TEXT NOT NULL DEFAULT '',
            file_name TEXT NOT NULL DEFAULT '',
            favorite INTEGER NOT NULL DEFAULT 0,
            play_count INTEGER NOT NULL DEFAULT 0,
            last_played INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
        CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
        CREATE TABLE IF NOT EXISTS playlists (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS playlist_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
            track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
            position INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS play_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
            timestamp INTEGER NOT NULL,
            listened_ms INTEGER NOT NULL,
            counted INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_events_ts ON play_events(timestamp);
        CREATE INDEX IF NOT EXISTS idx_events_track ON play_events(track_id);
        CREATE TABLE IF NOT EXISTS folders (path TEXT PRIMARY KEY);
        CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """;

    // ---- tracks -----------------------------------------------------------------

    public List<Track> LoadTracks() =>
        Query($"SELECT {TrackColumns} FROM tracks ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, track_no, title COLLATE NOCASE", ReadTrack);

    /// <summary>Insert or refresh a track by its key. Favourites and play history are kept. Returns the row id.</summary>
    public long UpsertTrack(Track t)
    {
        lock (_gate)
        {
            Execute(
                """
                INSERT INTO tracks (key, is_phone, phone_address, remote_id, title, artist, album, genre, year, track_no,
                    duration_ms, size_bytes, date_added, file_mtime, local_file, art_path, lyrics, mime_type, file_name)
                VALUES (@key, @phone, @address, @remote, @title, @artist, @album, @genre, @year, @track,
                    @duration, @size, @added, @mtime, @local, @art, @lyrics, @mime, @fileName)
                ON CONFLICT(key) DO UPDATE SET
                    title = excluded.title, artist = excluded.artist, album = excluded.album, genre = excluded.genre,
                    year = excluded.year, track_no = excluded.track_no, duration_ms = excluded.duration_ms,
                    size_bytes = excluded.size_bytes, file_mtime = excluded.file_mtime, mime_type = excluded.mime_type,
                    file_name = excluded.file_name, phone_address = excluded.phone_address, remote_id = excluded.remote_id,
                    local_file = COALESCE(excluded.local_file, tracks.local_file),
                    art_path = COALESCE(excluded.art_path, tracks.art_path),
                    lyrics = COALESCE(excluded.lyrics, tracks.lyrics)
                """,
                ("@key", t.Key), ("@phone", t.IsPhone ? 1 : 0), ("@address", t.PhoneAddress), ("@remote", t.RemoteId),
                ("@title", t.Title), ("@artist", t.Artist), ("@album", t.Album), ("@genre", t.Genre), ("@year", t.Year),
                ("@track", t.TrackNumber), ("@duration", t.DurationMs), ("@size", t.SizeBytes), ("@added", t.DateAdded),
                ("@mtime", t.FileWriteTicks), ("@local", t.LocalFile), ("@art", t.ArtPath), ("@lyrics", t.Lyrics),
                ("@mime", t.MimeType), ("@fileName", t.FileName));
            return Scalar<long>("SELECT id FROM tracks WHERE key = @key", ("@key", t.Key));
        }
    }

    /// <summary>Local files already in the library: key → (id, size, last write ticks), used to skip unchanged files.</summary>
    public Dictionary<string, (long Id, long Size, long Ticks)> LocalIndex()
    {
        var map = new Dictionary<string, (long, long, long)>();
        foreach (var row in Query("SELECT key, id, size_bytes, file_mtime FROM tracks WHERE is_phone = 0",
                     r => (r.GetString(0), r.GetInt64(1), r.GetInt64(2), r.GetInt64(3))))
        {
            map[row.Item1] = (row.Item2, row.Item3, row.Item4);
        }

        return map;
    }

    public List<Track> PhoneTracks(string address) =>
        Query($"SELECT {TrackColumns} FROM tracks WHERE is_phone = 1 AND phone_address = @address", ReadTrack, ("@address", address));

    public void DeleteTracks(IEnumerable<long> ids)
    {
        lock (_gate)
        {
            using var tx = _conn.BeginTransaction();
            foreach (long id in ids)
            {
                using var cmd = Command("DELETE FROM tracks WHERE id = @id", ("@id", id));
                cmd.ExecuteNonQuery();
            }

            tx.Commit();
        }
    }

    public void SetFavorite(long trackId, bool favorite) =>
        Execute("UPDATE tracks SET favorite = @fav WHERE id = @id", ("@fav", favorite ? 1 : 0), ("@id", trackId));

    public void SetLocalFile(long trackId, string? localFile, string? artPath, string? lyrics) =>
        Execute(
            "UPDATE tracks SET local_file = COALESCE(@local, local_file), art_path = COALESCE(@art, art_path), lyrics = COALESCE(@lyrics, lyrics) WHERE id = @id",
            ("@local", localFile), ("@art", artPath), ("@lyrics", lyrics), ("@id", trackId));

    public List<Track> Favorites() =>
        Query($"SELECT {TrackColumns} FROM tracks WHERE favorite = 1 ORDER BY title COLLATE NOCASE", ReadTrack);

    public List<Track> RecentlyPlayed(int limit = 200) =>
        Query($"SELECT {TrackColumns} FROM tracks WHERE last_played > 0 ORDER BY last_played DESC LIMIT @limit", ReadTrack, ("@limit", limit));

    // ---- playlists --------------------------------------------------------------

    public List<PlaylistInfo> Playlists() =>
        Query(
            "SELECT p.id, p.name, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlist_id = p.id) FROM playlists p ORDER BY p.name COLLATE NOCASE",
            r => new PlaylistInfo(r.GetInt64(0), r.GetString(1), r.GetInt32(2)));

    public long CreatePlaylist(string name)
    {
        Execute("INSERT INTO playlists (name, created_at) VALUES (@name, @now)", ("@name", name), ("@now", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
        return Scalar<long>("SELECT last_insert_rowid()");
    }

    public void RenamePlaylist(long id, string name) =>
        Execute("UPDATE playlists SET name = @name WHERE id = @id", ("@name", name), ("@id", id));

    public void DeletePlaylist(long id) =>
        Execute("DELETE FROM playlists WHERE id = @id", ("@id", id));

    public List<Track> PlaylistTracks(long playlistId)
    {
        string columns = string.Join(", ", TrackColumns.Split(", ").Select(c => "t." + c));
        return Query(
            $"SELECT {columns} FROM playlist_entries e JOIN tracks t ON t.id = e.track_id WHERE e.playlist_id = @id ORDER BY e.position",
            ReadTrack, ("@id", playlistId));
    }

    public void AddToPlaylist(long playlistId, IEnumerable<long> trackIds)
    {
        lock (_gate)
        {
            using var tx = _conn.BeginTransaction();
            foreach (long trackId in trackIds)
            {
                using var cmd = Command(
                    """
                    INSERT INTO playlist_entries (playlist_id, track_id, position)
                    VALUES (@playlist, @track, (SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entries WHERE playlist_id = @playlist))
                    """,
                    ("@playlist", playlistId), ("@track", trackId));
                cmd.ExecuteNonQuery();
            }

            tx.Commit();
        }
    }

    public void RemoveFromPlaylist(long playlistId, int index)
    {
        lock (_gate)
        {
            using var tx = _conn.BeginTransaction();
            using (var del = Command("DELETE FROM playlist_entries WHERE playlist_id = @p AND position = @pos", ("@p", playlistId), ("@pos", index)))
                del.ExecuteNonQuery();
            using (var shift = Command("UPDATE playlist_entries SET position = position - 1 WHERE playlist_id = @p AND position > @pos", ("@p", playlistId), ("@pos", index)))
                shift.ExecuteNonQuery();
            tx.Commit();
        }
    }

    // ---- listening history ------------------------------------------------------

    /// <summary>Stores one listen. Only counted listens (5 s or more) add to the play count and "recently played".</summary>
    public void LogPlay(long trackId, long listenedMs, bool counted, long nowMs)
    {
        lock (_gate)
        {
            using var tx = _conn.BeginTransaction();
            using (var ins = Command(
                       "INSERT INTO play_events (track_id, timestamp, listened_ms, counted) VALUES (@t, @now, @ms, @c)",
                       ("@t", trackId), ("@now", nowMs), ("@ms", listenedMs), ("@c", counted ? 1 : 0)))
                ins.ExecuteNonQuery();
            if (counted)
            {
                using var upd = Command(
                    "UPDATE tracks SET play_count = play_count + 1, last_played = @now WHERE id = @t",
                    ("@t", trackId), ("@now", nowMs));
                upd.ExecuteNonQuery();
            }

            tx.Commit();
        }
    }

    public StatsSummary Summary()
    {
        lock (_gate)
        {
            using var cmd = Command(
                "SELECT (SELECT COUNT(*) FROM tracks), COALESCE(SUM(counted), 0), COALESCE(SUM(listened_ms), 0) FROM play_events");
            using var r = cmd.ExecuteReader();
            r.Read();
            return new StatsSummary(r.GetInt32(0), r.GetInt32(1), r.GetInt64(2));
        }
    }

    public List<TopTrack> TopTracks(int limit)
    {
        string columns = string.Join(", ", TrackColumns.Split(", ").Select(c => "t." + c));
        return Query(
            $"""
            SELECT {columns}, (SELECT COALESCE(SUM(e.listened_ms), 0) FROM play_events e WHERE e.track_id = t.id)
            FROM tracks t WHERE t.play_count > 0 ORDER BY t.play_count DESC, t.title COLLATE NOCASE LIMIT @limit
            """,
            r => new TopTrack(ReadTrack(r), r.GetInt32(21), r.GetInt64(23)),
            ("@limit", limit));
    }

    /// <summary>Listened milliseconds per local day for the last <paramref name="days"/> days (oldest first).</summary>
    public List<(DateOnly Day, long ListenedMs)> DailyListening(int days)
    {
        long offset = (long)TimeZoneInfo.Local.GetUtcOffset(DateTime.UtcNow).TotalMilliseconds;
        long since = DateTimeOffset.UtcNow.AddDays(-days).ToUnixTimeMilliseconds();
        var perDay = Query(
            "SELECT (timestamp + @offset) / 86400000 AS day, SUM(listened_ms) FROM play_events WHERE timestamp >= @since GROUP BY day",
            r => (Day: r.GetInt64(0), Ms: r.GetInt64(1)),
            ("@offset", offset), ("@since", since));

        var today = DateOnly.FromDateTime(DateTime.Now);
        var result = new List<(DateOnly, long)>();
        for (int i = days - 1; i >= 0; i--)
        {
            var day = today.AddDays(-i);
            long index = (long)(day.ToDateTime(TimeOnly.MinValue) - DateTime.UnixEpoch).TotalDays;
            long ms = perDay.FirstOrDefault(d => d.Day == index).Ms;
            result.Add((day, ms));
        }

        return result;
    }

    // ---- folders and settings ---------------------------------------------------

    public List<string> Folders() => Query("SELECT path FROM folders ORDER BY path", r => r.GetString(0));

    public void AddFolder(string path) => Execute("INSERT OR IGNORE INTO folders (path) VALUES (@p)", ("@p", path));

    public void RemoveFolder(string path) => Execute("DELETE FROM folders WHERE path = @p", ("@p", path));

    public string? GetSetting(string key) =>
        Query("SELECT value FROM settings WHERE key = @k", r => r.GetString(0), ("@k", key)).FirstOrDefault();

    public void SetSetting(string key, string value) =>
        Execute(
            "INSERT INTO settings (key, value) VALUES (@k, @v) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            ("@k", key), ("@v", value));

    // ---- helpers ----------------------------------------------------------------

    private static Track ReadTrack(SqliteDataReader r) => new()
    {
        Id = r.GetInt64(0),
        Key = r.GetString(1),
        IsPhone = r.GetInt64(2) != 0,
        PhoneAddress = r.GetString(3),
        RemoteId = r.GetInt64(4),
        Title = r.GetString(5),
        Artist = r.GetString(6),
        Album = r.GetString(7),
        Genre = r.GetString(8),
        Year = r.GetInt32(9),
        TrackNumber = r.GetInt32(10),
        DurationMs = r.GetInt64(11),
        SizeBytes = r.GetInt64(12),
        DateAdded = r.GetInt64(13),
        FileWriteTicks = r.GetInt64(14),
        LocalFile = r.IsDBNull(15) ? null : r.GetString(15),
        ArtPath = r.IsDBNull(16) ? null : r.GetString(16),
        Lyrics = r.IsDBNull(17) ? null : r.GetString(17),
        MimeType = r.GetString(18),
        FileName = r.GetString(19),
        Favorite = r.GetInt64(20) != 0,
        PlayCount = r.GetInt32(21),
        LastPlayed = r.GetInt64(22),
    };

    private SqliteCommand Command(string sql, params (string Name, object? Value)[] args)
    {
        var cmd = _conn.CreateCommand();
        cmd.CommandText = sql;
        foreach (var (name, value) in args) cmd.Parameters.AddWithValue(name, value ?? DBNull.Value);
        return cmd;
    }

    private void Run(string sql)
    {
        lock (_gate)
        {
            using var cmd = Command(sql);
            cmd.ExecuteNonQuery();
        }
    }

    private void Execute(string sql, params (string Name, object? Value)[] args)
    {
        lock (_gate)
        {
            using var cmd = Command(sql, args);
            cmd.ExecuteNonQuery();
        }
    }

    private T Scalar<T>(string sql, params (string Name, object? Value)[] args)
    {
        lock (_gate)
        {
            using var cmd = Command(sql, args);
            return (T)Convert.ChangeType(cmd.ExecuteScalar()!, typeof(T));
        }
    }

    private List<T> Query<T>(string sql, Func<SqliteDataReader, T> map, params (string Name, object? Value)[] args)
    {
        lock (_gate)
        {
            using var cmd = Command(sql, args);
            using var reader = cmd.ExecuteReader();
            var list = new List<T>();
            while (reader.Read()) list.Add(map(reader));
            return list;
        }
    }

    public void Dispose()
    {
        lock (_gate) _conn.Dispose();
    }
}
