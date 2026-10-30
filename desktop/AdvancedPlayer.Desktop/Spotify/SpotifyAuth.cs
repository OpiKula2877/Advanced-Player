using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace AdvancedPlayer.Desktop.Spotify;

/// <summary>Tokens from Spotify. The access token lasts about an hour; the refresh token is stored encrypted.</summary>
public sealed record SpotifyTokens(string AccessToken, string RefreshToken, DateTime ExpiresAtUtc);

/// <summary>An error from Spotify. <see cref="Code"/> is a short name such as "premium_required".</summary>
public sealed class SpotifyException : Exception
{
    public SpotifyException(string code, string message) : base(message)
    {
        Code = code;
    }

    public string Code { get; }
}

/// <summary>
/// Sign-in with the Authorization Code flow + PKCE, so only the public Client ID is needed and no secret is stored.
/// The browser returns to a small listener on 127.0.0.1 that is open only while signing in.
/// </summary>
public static class SpotifyAuth
{
    public const int CallbackPort = 8912;
    public const string RedirectUri = "http://127.0.0.1:8912/callback";
    public const string Scopes = "user-read-playback-state user-modify-playback-state user-read-currently-playing";

    private static readonly HttpClient Http = new();

    public static async Task<SpotifyTokens> SignInAsync(string clientId, CancellationToken ct = default)
    {
        string verifier = CreateVerifier();
        string challenge = CreateChallenge(verifier);
        string state = Convert.ToHexString(RandomNumberGenerator.GetBytes(12));
        string url = "https://accounts.spotify.com/authorize?" + BuildQuery(
            ("client_id", clientId),
            ("response_type", "code"),
            ("redirect_uri", RedirectUri),
            ("code_challenge_method", "S256"),
            ("code_challenge", challenge),
            ("state", state),
            ("scope", Scopes));

        var listener = new TcpListener(IPAddress.Loopback, CallbackPort);
        listener.Start();
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            string code = await WaitForCodeAsync(listener, state, ct);
            return await TokenRequestAsync(
                ct,
                ("client_id", clientId),
                ("grant_type", "authorization_code"),
                ("code", code),
                ("redirect_uri", RedirectUri),
                ("code_verifier", verifier));
        }
        finally
        {
            listener.Stop();
        }
    }

    public static async Task<SpotifyTokens> RefreshAsync(string clientId, string refreshToken, CancellationToken ct = default)
    {
        SpotifyTokens fresh = await TokenRequestAsync(
            ct,
            ("client_id", clientId),
            ("grant_type", "refresh_token"),
            ("refresh_token", refreshToken));

        // Spotify may rotate the refresh token; keep the old one when no new one arrives.
        return fresh.RefreshToken.Length == 0 ? fresh with { RefreshToken = refreshToken } : fresh;
    }

    private static async Task<string> WaitForCodeAsync(TcpListener listener, string expectedState, CancellationToken ct)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(TimeSpan.FromMinutes(3));

        using TcpClient client = await listener.AcceptTcpClientAsync(timeout.Token);
        using NetworkStream stream = client.GetStream();
        using var reader = new StreamReader(stream, Encoding.ASCII, detectEncodingFromByteOrderMarks: false, bufferSize: 1024, leaveOpen: true);

        // The first line looks like: GET /callback?code=...&state=... HTTP/1.1
        string requestLine = await reader.ReadLineAsync(timeout.Token) ?? "";
        string target = requestLine.Split(' ') is [_, var path, ..] ? path : "";
        Dictionary<string, string> query = ParseQuery(target);

        bool ok = query.TryGetValue("state", out string? state) && state == expectedState && query.ContainsKey("code");
        string message = ok ? "Přihlášení v pořádku. Tuhle kartu můžeš zavřít." : "Přihlášení se nepovedlo. Zkus to znovu v aplikaci.";
        byte[] body = Encoding.UTF8.GetBytes(
            "<!doctype html><html><head><meta charset=\"utf-8\"></head>" +
            "<body style=\"font-family:Segoe UI;background:#121214;color:#EDEDF0;padding:40px\"><h2>" + message + "</h2></body></html>");
        byte[] header = Encoding.ASCII.GetBytes(
            $"HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
        await stream.WriteAsync(header, timeout.Token);
        await stream.WriteAsync(body, timeout.Token);

        if (query.ContainsKey("error")) throw new SpotifyException("access_denied", "Přihlášení bylo zrušeno.");
        if (!ok) throw new SpotifyException("bad_state", "Spotify vrátil neplatnou odpověď.");
        return query["code"];
    }

    private static async Task<SpotifyTokens> TokenRequestAsync(CancellationToken ct, params (string Key, string Value)[] fields)
    {
        using var form = new FormUrlEncodedContent(fields.Select(f => new KeyValuePair<string, string>(f.Key, f.Value)));
        using HttpResponseMessage response = await Http.PostAsync("https://accounts.spotify.com/api/token", form, ct);
        string json = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode)
            throw new SpotifyException("token", "Spotify odmítl přihlášení (HTTP " + (int)response.StatusCode + ").");

        using JsonDocument doc = JsonDocument.Parse(json);
        JsonElement root = doc.RootElement;
        string access = root.GetProperty("access_token").GetString()
            ?? throw new SpotifyException("token", "Spotify nevrátil přístupový token.");
        int expiresIn = root.TryGetProperty("expires_in", out JsonElement expires) ? expires.GetInt32() : 3600;
        string refresh = root.TryGetProperty("refresh_token", out JsonElement refreshElement) ? refreshElement.GetString() ?? "" : "";
        return new SpotifyTokens(access, refresh, DateTime.UtcNow.AddSeconds(expiresIn - 60));
    }

    private static Dictionary<string, string> ParseQuery(string target)
    {
        var result = new Dictionary<string, string>(StringComparer.Ordinal);
        int question = target.IndexOf('?');
        if (question < 0) return result;

        foreach (string pair in target[(question + 1)..].Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            int equals = pair.IndexOf('=');
            string key = Uri.UnescapeDataString(equals < 0 ? pair : pair[..equals]);
            string value = equals < 0 ? "" : Uri.UnescapeDataString(pair[(equals + 1)..].Replace('+', ' '));
            result[key] = value;
        }

        return result;
    }

    private static string CreateVerifier() => Base64Url(RandomNumberGenerator.GetBytes(64));

    private static string CreateChallenge(string verifier) => Base64Url(SHA256.HashData(Encoding.ASCII.GetBytes(verifier)));

    private static string Base64Url(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    private static string BuildQuery(params (string Key, string Value)[] pairs) =>
        string.Join("&", pairs.Select(p => $"{Uri.EscapeDataString(p.Key)}={Uri.EscapeDataString(p.Value)}"));
}
