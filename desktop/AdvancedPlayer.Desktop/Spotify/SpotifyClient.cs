using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Spotify;

public sealed record SpotifyTrack(string Id, string Uri, string Title, string Artist, string Album, long DurationMs, string? ArtUrl)
{
    public string DurationText => TimeFormat.Format(TimeSpan.FromMilliseconds(DurationMs));
}

public sealed record SpotifyDevice(string Id, string Name, string Type, bool IsActive);

/// <summary>
/// Calls to the Spotify Web API. Search works on any account. Playing on a Spotify device needs Premium,
/// and the API answers with an error for free accounts.
/// </summary>
public sealed class SpotifyClient
{
    private static readonly HttpClient Http = new() { BaseAddress = new Uri("https://api.spotify.com/v1/") };

    private readonly string _clientId;
    private readonly Action<SpotifyTokens> _onTokensChanged;
    private SpotifyTokens _tokens;

    public SpotifyClient(string clientId, SpotifyTokens tokens, Action<SpotifyTokens> onTokensChanged)
    {
        _clientId = clientId;
        _tokens = tokens;
        _onTokensChanged = onTokensChanged;
    }

    public async Task<List<SpotifyTrack>> SearchTracksAsync(string query, CancellationToken ct = default)
    {
        string json = await SendAsync(HttpMethod.Get, $"search?type=track&limit=20&q={Uri.EscapeDataString(query)}", null, ct);
        using JsonDocument doc = JsonDocument.Parse(json);
        var tracks = new List<SpotifyTrack>();
        foreach (JsonElement item in doc.RootElement.GetProperty("tracks").GetProperty("items").EnumerateArray())
        {
            JsonElement album = item.GetProperty("album");
            tracks.Add(new SpotifyTrack(
                item.GetProperty("id").GetString() ?? "",
                item.GetProperty("uri").GetString() ?? "",
                item.GetProperty("name").GetString() ?? "",
                string.Join(", ", item.GetProperty("artists").EnumerateArray().Select(a => a.GetProperty("name").GetString())),
                album.GetProperty("name").GetString() ?? "",
                item.GetProperty("duration_ms").GetInt64(),
                ArtUrl(album)));
        }

        return tracks;
    }

    public async Task<List<SpotifyDevice>> GetDevicesAsync(CancellationToken ct = default)
    {
        string json = await SendAsync(HttpMethod.Get, "me/player/devices", null, ct);
        using JsonDocument doc = JsonDocument.Parse(json);
        var devices = new List<SpotifyDevice>();
        if (!doc.RootElement.TryGetProperty("devices", out JsonElement list)) return devices;

        foreach (JsonElement d in list.EnumerateArray())
        {
            devices.Add(new SpotifyDevice(
                d.GetProperty("id").GetString() ?? "",
                d.GetProperty("name").GetString() ?? "",
                d.GetProperty("type").GetString() ?? "",
                d.GetProperty("is_active").GetBoolean()));
        }

        // Some devices are hidden from the API and have no id; they cannot be used.
        return devices.Where(d => d.Id.Length > 0).ToList();
    }

    public Task PlayTrackAsync(string trackUri, string? deviceId, CancellationToken ct = default)
    {
        string body = JsonSerializer.Serialize(new { uris = new[] { trackUri } });
        return SendAsync(HttpMethod.Put, "me/player/play" + DeviceQuery(deviceId), body, ct);
    }

    public Task PauseAsync(string? deviceId, CancellationToken ct = default) =>
        SendAsync(HttpMethod.Put, "me/player/pause" + DeviceQuery(deviceId), null, ct);

    public Task NextAsync(string? deviceId, CancellationToken ct = default) =>
        SendAsync(HttpMethod.Post, "me/player/next" + DeviceQuery(deviceId), null, ct);

    public Task PreviousAsync(string? deviceId, CancellationToken ct = default) =>
        SendAsync(HttpMethod.Post, "me/player/previous" + DeviceQuery(deviceId), null, ct);

    private static string DeviceQuery(string? deviceId) =>
        string.IsNullOrEmpty(deviceId) ? "" : "?device_id=" + Uri.EscapeDataString(deviceId);

    /// <summary>Picks the album image closest to 300 px wide. Returns null when there is none.</summary>
    private static string? ArtUrl(JsonElement album)
    {
        if (!album.TryGetProperty("images", out JsonElement images)) return null;
        string? best = null;
        int bestDistance = int.MaxValue;
        foreach (JsonElement image in images.EnumerateArray())
        {
            int width = image.TryGetProperty("width", out JsonElement w) && w.ValueKind == JsonValueKind.Number ? w.GetInt32() : 300;
            int distance = Math.Abs(width - 300);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = image.GetProperty("url").GetString();
            }
        }

        return best;
    }

    private async Task<string> SendAsync(HttpMethod method, string path, string? jsonBody, CancellationToken ct, bool retried = false)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _tokens.AccessToken);
        if (jsonBody != null) request.Content = new StringContent(jsonBody, Encoding.UTF8, "application/json");

        using HttpResponseMessage response = await Http.SendAsync(request, ct);
        string text = await response.Content.ReadAsStringAsync(ct);

        if (response.StatusCode == HttpStatusCode.Unauthorized && !retried)
        {
            _tokens = await SpotifyAuth.RefreshAsync(_clientId, _tokens.RefreshToken, ct);
            _onTokensChanged(_tokens);
            return await SendAsync(method, path, jsonBody, ct, retried: true);
        }

        if (!response.IsSuccessStatusCode) throw ToException(response.StatusCode, text);
        return text;
    }

    private static SpotifyException ToException(HttpStatusCode status, string text)
    {
        string message = text;
        try
        {
            using JsonDocument doc = JsonDocument.Parse(text);
            if (doc.RootElement.TryGetProperty("error", out JsonElement error) && error.ValueKind == JsonValueKind.Object
                && error.TryGetProperty("message", out JsonElement m))
            {
                message = m.GetString() ?? text;
            }
        }
        catch (JsonException)
        {
            // Not JSON: keep the raw text as the message.
        }

        string code = status switch
        {
            HttpStatusCode.Forbidden when message.Contains("premium", StringComparison.OrdinalIgnoreCase) => "premium_required",
            HttpStatusCode.Forbidden => "premium_required",
            HttpStatusCode.NotFound => "no_device",
            _ => status.ToString(),
        };
        return new SpotifyException(code, message);
    }
}
