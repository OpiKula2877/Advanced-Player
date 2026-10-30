using System.Text;
using System.Text.Json;

namespace AdvancedPlayer.Desktop.Phone;

public sealed class PhoneException(string message) : Exception(message);

/// <summary>A song the phone offers in its library (the LIST command).</summary>
public sealed record PhoneSong(
    long Id,
    string Title,
    string Artist,
    string Album,
    string Genre,
    int Year,
    int TrackNo,
    long DurationMs,
    long Size,
    string Name,
    string Mime,
    long DateAdded);

/// <summary>
/// Client side of the phone protocol (see desktop/README.md). Every request is one ASCII line, and every
/// answer is either a line or a decimal length followed by that many bytes. Works over any duplex stream.
/// </summary>
public sealed class PhoneSession : IAsyncDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    private readonly Stream _input;
    private readonly Stream _output;
    private readonly SemaphoreSlim _requests = new(1, 1);
    private readonly IDisposable? _owner;

    public PhoneSession(Stream input, Stream output, IDisposable? owner = null)
    {
        _input = new BufferedStream(input, 64 * 1024);
        _output = output;
        _owner = owner;
    }

    /// <summary>Checks the protocol version and returns how many songs the phone reports.</summary>
    public async Task<int> HelloAsync(CancellationToken ct = default)
    {
        string line = await RequestLineAsync("HELLO", ct);
        string[] parts = line.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length < 2 || parts[0] != "OK") throw new PhoneException("Unexpected answer from the phone: " + line);
        return parts.Length >= 4 && int.TryParse(parts[3], out int count) ? count : 0;
    }

    public async Task<List<PhoneSong>> ListAsync(CancellationToken ct = default)
    {
        await _requests.WaitAsync(ct);
        try
        {
            await SendAsync("LIST", ct);
            long length = await ReadLengthAsync(ct);
            byte[] json = new byte[length];
            await ReadExactlyAsync(json, ct);
            return JsonSerializer.Deserialize<List<PhoneSong>>(json, JsonOptions) ?? new List<PhoneSong>();
        }
        finally
        {
            _requests.Release();
        }
    }

    /// <summary>Streams the audio file of song <paramref name="id"/> into <paramref name="destination"/>.</summary>
    public async Task DownloadAsync(long id, Stream destination, IProgress<long>? progress = null, CancellationToken ct = default)
    {
        await _requests.WaitAsync(ct);
        try
        {
            await SendAsync($"GET {id}", ct);
            long length = await ReadLengthAsync(ct);
            if (length < 0) throw new PhoneException("The phone no longer has this song.");

            var buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0)
            {
                int wanted = (int)Math.Min(buffer.Length, remaining);
                int read = await _input.ReadAsync(buffer.AsMemory(0, wanted), ct);
                if (read == 0) throw new PhoneException("The connection to the phone was lost.");
                await destination.WriteAsync(buffer.AsMemory(0, read), ct);
                remaining -= read;
                progress?.Report(length - remaining);
            }
        }
        finally
        {
            _requests.Release();
        }
    }

    /// <summary>Cover art bytes (JPEG or PNG), or null when the song has none.</summary>
    public async Task<byte[]?> ArtAsync(long id, CancellationToken ct = default)
    {
        await _requests.WaitAsync(ct);
        try
        {
            await SendAsync($"ART {id}", ct);
            long length = await ReadLengthAsync(ct);
            if (length <= 0) return null;
            byte[] data = new byte[length];
            await ReadExactlyAsync(data, ct);
            return data;
        }
        finally
        {
            _requests.Release();
        }
    }

    /// <summary>Lyrics as LRC text, or null when the song has none.</summary>
    public async Task<string?> LyricsAsync(long id, CancellationToken ct = default)
    {
        await _requests.WaitAsync(ct);
        try
        {
            await SendAsync($"LRC {id}", ct);
            long length = await ReadLengthAsync(ct);
            if (length <= 0) return null;
            byte[] text = new byte[length];
            await ReadExactlyAsync(text, ct);
            return Encoding.UTF8.GetString(text);
        }
        finally
        {
            _requests.Release();
        }
    }

    public async ValueTask DisposeAsync()
    {
        try
        {
            if (await _requests.WaitAsync(TimeSpan.FromSeconds(2)))
            {
                try
                {
                    await SendAsync("BYE", CancellationToken.None);
                }
                finally
                {
                    _requests.Release();
                }
            }
        }
        catch (Exception)
        {
            // The phone already went away; nothing to say goodbye to.
        }

        _input.Dispose();
        _output.Dispose();
        _owner?.Dispose();
    }

    private async Task<string> RequestLineAsync(string command, CancellationToken ct)
    {
        await _requests.WaitAsync(ct);
        try
        {
            await SendAsync(command, ct);
            return await ReadLineAsync(ct);
        }
        finally
        {
            _requests.Release();
        }
    }

    private async Task SendAsync(string command, CancellationToken ct)
    {
        byte[] bytes = Encoding.ASCII.GetBytes(command + "\n");
        await _output.WriteAsync(bytes, ct);
        await _output.FlushAsync(ct);
    }

    private async Task<string> ReadLineAsync(CancellationToken ct)
    {
        var line = new List<byte>();
        var one = new byte[1];
        while (true)
        {
            int read = await _input.ReadAsync(one, ct);
            if (read == 0) throw new PhoneException("The connection to the phone was lost.");
            if (one[0] == '\n') break;
            line.Add(one[0]);
            if (line.Count > 4096) throw new PhoneException("The phone sent a line that is too long.");
        }

        string text = Encoding.UTF8.GetString(line.ToArray()).TrimEnd('\r');
        if (text.StartsWith("ERR", StringComparison.Ordinal))
            throw new PhoneException(text.Length > 4 ? text[4..] : "The phone reported an error.");
        return text;
    }

    private async Task<long> ReadLengthAsync(CancellationToken ct)
    {
        string line = await ReadLineAsync(ct);
        if (!long.TryParse(line, out long length) || length > int.MaxValue)
            throw new PhoneException("Bad length from the phone: " + line);
        return length;
    }

    private async Task ReadExactlyAsync(byte[] buffer, CancellationToken ct)
    {
        int offset = 0;
        while (offset < buffer.Length)
        {
            int read = await _input.ReadAsync(buffer.AsMemory(offset, buffer.Length - offset), ct);
            if (read == 0) throw new PhoneException("The connection to the phone was lost.");
            offset += read;
        }
    }
}
