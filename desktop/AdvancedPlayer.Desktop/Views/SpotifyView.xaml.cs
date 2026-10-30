using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Spotify;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>
/// Spotify search and playback. Search and "Open in Spotify" work on any account.
/// Playing on a Spotify device through the API needs Premium; free accounts get a clear message instead.
/// </summary>
public partial class SpotifyView : UserControl, IRefreshable
{
    private SpotifyClient? _client;

    public SpotifyView(MainWindow host)
    {
        InitializeComponent();
        TitleText.Text = Loc.Get("nav_spotify");
        IntroText.Text = Loc.Get("spotify_intro");
        SaveClientButton.Content = Loc.Get("spotify_save");
        SignInButton.Content = Loc.Get("spotify_sign_in");
        SignOutButton.Content = Loc.Get("spotify_sign_out");
        SearchButton.Content = Loc.Get("spotify_search");
        RefreshDevicesButton.Content = Loc.Get("spotify_refresh_devices");
        OpenButton.Content = Loc.Get("spotify_open");
        PlayButton.Content = Loc.Get("spotify_play_device");
        PauseButton.Content = Loc.Get("pause");
        NextButton.Content = Loc.Get("next");
        PrevButton.Content = Loc.Get("previous");
        ClientIdBox.Text = App.Services.Settings.SpotifyClientId ?? "";
        UpdateSignInState();
    }

    public void Refresh() => UpdateSignInState();

    private void UpdateSignInState()
    {
        bool signedIn = App.Services.Settings.SpotifyRefreshToken != null;
        SignInButton.IsEnabled = !signedIn;
        SignOutButton.IsEnabled = signedIn;
        StatusText.Text = Loc.Get(signedIn ? "spotify_signed_in" : "spotify_signed_out");
    }

    private void SaveClient_Click(object sender, RoutedEventArgs e)
    {
        App.Services.Settings.SpotifyClientId = ClientIdBox.Text.Trim();
        StatusText.Text = Loc.Get("spotify_saved");
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        string clientId = ClientIdBox.Text.Trim();
        if (clientId.Length == 0) throw new SpotifyException("no_client", Loc.Get("spotify_client_missing"));

        App.Services.Settings.SpotifyClientId = clientId;
        StatusText.Text = Loc.Get("spotify_waiting");
        SpotifyTokens tokens = await SpotifyAuth.SignInAsync(clientId);
        SaveTokens(tokens);
        _client = new SpotifyClient(clientId, tokens, SaveTokens);
        UpdateSignInState();
        await LoadDevicesAsync();
    });

    private void SignOut_Click(object sender, RoutedEventArgs e)
    {
        _client = null;
        App.Services.Settings.SpotifyRefreshToken = null;
        UpdateSignInState();
    }

    private void Query_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) Search_Click(sender, e);
    }

    private async void Search_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        string query = QueryBox.Text.Trim();
        if (query.Length == 0) return;

        SpotifyClient client = await EnsureClientAsync();
        List<SpotifyTrack> tracks = await client.SearchTracksAsync(query);
        ResultList.ItemsSource = tracks;
        StatusText.Text = Loc.Get("tracks_count", tracks.Count);
    });

    private async void Devices_Click(object sender, RoutedEventArgs e) => await RunAsync(LoadDevicesAsync);

    private void Open_Click(object sender, RoutedEventArgs e)
    {
        if (ResultList.SelectedItem is not SpotifyTrack track)
        {
            StatusText.Text = Loc.Get("spotify_pick_track");
            return;
        }

        Process.Start(new ProcessStartInfo($"https://open.spotify.com/track/{track.Id}") { UseShellExecute = true });
    }

    private async void Play_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        if (ResultList.SelectedItem is not SpotifyTrack track)
        {
            StatusText.Text = Loc.Get("spotify_pick_track");
            return;
        }

        SpotifyClient client = await EnsureClientAsync();
        await client.PlayTrackAsync(track.Uri, (DeviceBox.SelectedItem as SpotifyDevice)?.Id);
        StatusText.Text = Loc.Get("spotify_playing", track.Title);
    });

    private void Result_MouseDoubleClick(object sender, MouseButtonEventArgs e) => Play_Click(sender, e);

    private async void Pause_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        SpotifyClient client = await EnsureClientAsync();
        await client.PauseAsync((DeviceBox.SelectedItem as SpotifyDevice)?.Id);
    });

    private async void Next_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        SpotifyClient client = await EnsureClientAsync();
        await client.NextAsync((DeviceBox.SelectedItem as SpotifyDevice)?.Id);
    });

    private async void Prev_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        SpotifyClient client = await EnsureClientAsync();
        await client.PreviousAsync((DeviceBox.SelectedItem as SpotifyDevice)?.Id);
    });

    private async Task LoadDevicesAsync()
    {
        SpotifyClient client = await EnsureClientAsync();
        List<SpotifyDevice> devices = await client.GetDevicesAsync();
        DeviceBox.ItemsSource = devices;
        DeviceBox.SelectedItem = devices.FirstOrDefault(d => d.IsActive) ?? devices.FirstOrDefault();
        StatusText.Text = devices.Count == 0 ? Loc.Get("spotify_no_device") : Loc.Get("spotify_devices_found", devices.Count);
    }

    /// <summary>Returns a signed-in client, refreshing the access token from the stored refresh token.</summary>
    private async Task<SpotifyClient> EnsureClientAsync()
    {
        if (_client != null) return _client;

        AppSettings settings = App.Services.Settings;
        string? clientId = settings.SpotifyClientId;
        string? refresh = settings.SpotifyRefreshToken;
        if (string.IsNullOrWhiteSpace(clientId)) throw new SpotifyException("no_client", Loc.Get("spotify_client_missing"));
        if (refresh == null) throw new SpotifyException("not_signed_in", Loc.Get("spotify_not_signed_in"));

        SpotifyTokens tokens = await SpotifyAuth.RefreshAsync(clientId, refresh);
        SaveTokens(tokens);
        _client = new SpotifyClient(clientId, tokens, SaveTokens);
        return _client;
    }

    private void SaveTokens(SpotifyTokens tokens) => App.Services.Settings.SpotifyRefreshToken = tokens.RefreshToken;

    /// <summary>Runs a Spotify action and shows its error in plain words instead of a stack trace.</summary>
    private async Task RunAsync(Func<Task> action)
    {
        try
        {
            await action();
        }
        catch (SpotifyException ex)
        {
            StatusText.Text = ex.Code switch
            {
                "premium_required" => Loc.Get("spotify_premium_required"),
                "no_device" => Loc.Get("spotify_no_device"),
                _ => ex.Message,
            };
        }
        catch (Exception ex)
        {
            StatusText.Text = ex.Message;
        }
    }
}
