using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using AdvancedPlayer.Desktop.Audio;

namespace AdvancedPlayer.Desktop.Data;

/// <summary>Typed settings stored in the settings table. Missing or invalid values fall back to defaults.</summary>
public sealed class AppSettings
{
    private readonly Database _db;

    public AppSettings(Database db)
    {
        _db = db;
    }

    public float Volume
    {
        get => float.TryParse(_db.GetSetting("volume"), NumberStyles.Float, CultureInfo.InvariantCulture, out float v) ? Math.Clamp(v, 0f, 1f) : 0.8f;
        set => _db.SetSetting("volume", value.ToString(CultureInfo.InvariantCulture));
    }

    public bool Shuffle
    {
        get => _db.GetSetting("shuffle") == "1";
        set => _db.SetSetting("shuffle", value ? "1" : "0");
    }

    /// <summary>Output device id (WASAPI endpoint id). Null means the Windows default device.</summary>
    public string? OutputDeviceId
    {
        get => string.IsNullOrEmpty(_db.GetSetting("output_device")) ? null : _db.GetSetting("output_device");
        set => _db.SetSetting("output_device", value ?? "");
    }

    /// <summary>"en" or "cs".</summary>
    public string Language
    {
        get => _db.GetSetting("language") ?? (CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "cs" ? "cs" : "en");
        set => _db.SetSetting("language", value);
    }

    public EqSettings Equalizer
    {
        get
        {
            bool enabled = _db.GetSetting("eq_enabled") == "1";
            float bass = float.TryParse(_db.GetSetting("bass_boost"), NumberStyles.Float, CultureInfo.InvariantCulture, out float b) ? Math.Clamp(b, 0f, 1f) : 0f;
            var bands = new float[EqProcessor.BandCount];
            string? stored = _db.GetSetting("eq_bands");
            if (stored != null)
            {
                string[] parts = stored.Split(',');
                for (int i = 0; i < Math.Min(parts.Length, bands.Length); i++)
                {
                    if (float.TryParse(parts[i], NumberStyles.Float, CultureInfo.InvariantCulture, out float v)) bands[i] = Math.Clamp(v, -12f, 12f);
                }
            }

            return new EqSettings(enabled, bands, bass);
        }
        set
        {
            _db.SetSetting("eq_enabled", value.Enabled ? "1" : "0");
            _db.SetSetting("bass_boost", value.BassBoost.ToString(CultureInfo.InvariantCulture));
            _db.SetSetting("eq_bands", string.Join(",", value.BandsDb.Select(b => b.ToString(CultureInfo.InvariantCulture))));
        }
    }

    public string? EqPreset
    {
        get => _db.GetSetting("eq_preset");
        set => _db.SetSetting("eq_preset", value ?? "custom");
    }

    /// <summary>When on, the app switches the Windows default output to the chosen device while it runs.</summary>
    public bool SystemOutputEnabled
    {
        get => _db.GetSetting("system_output_on") == "1";
        set => _db.SetSetting("system_output_on", value ? "1" : "0");
    }

    /// <summary>The device id that the Windows default output is switched to.</summary>
    public string? SystemOutputDeviceId
    {
        get => string.IsNullOrEmpty(_db.GetSetting("system_output_device")) ? null : _db.GetSetting("system_output_device");
        set => _db.SetSetting("system_output_device", value ?? "");
    }

    /// <summary>Artwork style of the Now playing panel: Vinyl, Cassette or Cover.</summary>
    public string CoverStyleName
    {
        get => _db.GetSetting("now_style") ?? "Vinyl";
        set => _db.SetSetting("now_style", value);
    }

    /// <summary>Visualizer mode of the Now playing panel (the name of a VisualMode value).</summary>
    public string VisualModeName
    {
        get => _db.GetSetting("now_visual") ?? "Bars";
        set => _db.SetSetting("now_visual", value);
    }

    /// <summary>Spotify Client ID from developer.spotify.com. It is public, so it is stored as plain text.</summary>
    public string? SpotifyClientId
    {
        get => _db.GetSetting("spotify_client_id");
        set => _db.SetSetting("spotify_client_id", value ?? "");
    }

    /// <summary>Spotify refresh token, encrypted with Windows DPAPI for the current user. Null when signed out.</summary>
    public string? SpotifyRefreshToken
    {
        get
        {
            string? stored = _db.GetSetting("spotify_refresh");
            if (string.IsNullOrEmpty(stored)) return null;
            try
            {
                return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(stored), null, DataProtectionScope.CurrentUser));
            }
            catch (Exception ex) when (ex is CryptographicException or FormatException)
            {
                return null;
            }
        }
        set
        {
            string stored = value == null
                ? ""
                : Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser));
            _db.SetSetting("spotify_refresh", stored);
        }
    }

    /// <summary>Bluetooth address of the phone that was used last, empty when none.</summary>
    public string PhoneAddress
    {
        get => _db.GetSetting("phone_address") ?? "";
        set => _db.SetSetting("phone_address", value);
    }

    public string PhoneName
    {
        get => _db.GetSetting("phone_name") ?? "";
        set => _db.SetSetting("phone_name", value);
    }
}
