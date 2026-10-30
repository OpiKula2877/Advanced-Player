using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using AdvancedPlayer.Desktop.Audio;
using AdvancedPlayer.Desktop.Models;
using AdvancedPlayer.Desktop.Views;

namespace AdvancedPlayer.Desktop;

public partial class MainWindow : Window
{
    private readonly AppServices _services = App.Services;
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromMilliseconds(250) };
    private readonly List<UIElement> _history = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private TimeSpan _lastClock;
    private bool _seeking;
    private bool _scanning;
    private DateTime? _sleepAt;
    private Track? _shownTrack;

    public MainWindow()
    {
        InitializeComponent();
        Current = this;
        _timer.Tick += OnTick;
        _services.Player.Changed += UpdateBar;
        Closed += (_, _) => _timer.Stop();
    }

    public static MainWindow? Current { get; private set; }

    /// <summary>True when Back would return to an earlier page.</summary>
    public bool HasHistory => _history.Count > 0;

    private async void Window_Loaded(object sender, RoutedEventArgs e)
    {
        VolumeSlider.Value = _services.Settings.Volume;
        _services.Player.Queue.SetShuffle(_services.Settings.Shuffle);
        LoadOutputDevices();
        NavList.SelectedIndex = 0;
        _timer.Start();
        UpdateBar();

        if (_services.Db.Folders().Count == 0)
        {
            string music = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
            if (Directory.Exists(music)) _services.Db.AddFolder(music);
        }

        await ScanAsync();
    }

    // ---- navigation -------------------------------------------------------------

    private void NavList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (NavList.SelectedItem is not ListBoxItem { Tag: string key }) return;
        _history.Clear();
        ContentHost.Child = CreateSection(key);
    }

    private UIElement CreateSection(string key) => key switch
    {
        "albums" => new GroupListView(this, GroupMode.Albums),
        "artists" => new GroupListView(this, GroupMode.Artists),
        "playlists" => new GroupListView(this, GroupMode.Playlists),
        "favorites" => new TrackListView(this, Loc.Get("nav_favorites"), _services.Db.Favorites()),
        "queue" => new QueueView(this),
        "spotify" => new SpotifyView(this),
        "recent" => new TrackListView(this, Loc.Get("nav_recent"), _services.Db.RecentlyPlayed()),
        "phone" => new PhoneView(this),
        "stats" => new StatsView(this),
        "settings" => new SettingsView(this),
        _ => new TrackListView(this, Loc.Get("nav_songs"), _services.Library.Tracks.OrderBy(t => t.DisplayTitle, StringComparer.CurrentCultureIgnoreCase).ToList()),
    };

    /// <summary>Opens a page; <see cref="Back"/> returns to the page that was showing before.</summary>
    public void Navigate(UIElement page)
    {
        if (ContentHost.Child != null) _history.Add(ContentHost.Child);
        ContentHost.Child = page;
    }

    public void Back()
    {
        if (_history.Count == 0) return;
        UIElement previous = _history[^1];
        _history.RemoveAt(_history.Count - 1);
        ContentHost.Child = previous;
        if (previous is IRefreshable refreshable) refreshable.Refresh();
    }

    public void RefreshCurrent()
    {
        if (ContentHost.Child is IRefreshable refreshable) refreshable.Refresh();
    }

    // ---- status and library -----------------------------------------------------

    public void SetStatus(string text) => StatusText.Text = text;

    /// <summary>Scans the music folders in the background and reloads the library when done.</summary>
    public async Task ScanAsync()
    {
        if (_scanning) return;
        _scanning = true;
        try
        {
            SetStatus(Loc.Get("scanning"));
            var progress = new Progress<string>(name => SetStatus(Loc.Get("scanning") + " " + name));
            int changed = await _services.Library.ScanAsync(progress);
            _services.Library.Reload();
            SetStatus(Loc.Get("scan_done", changed));
            RefreshCurrent();
        }
        catch (Exception ex)
        {
            SetStatus(ex.Message);
        }
        finally
        {
            _scanning = false;
        }
    }

    /// <summary>Runs a playback action and shows any error in the status line instead of crashing.</summary>
    public async Task Try(Func<Task> action)
    {
        try
        {
            await action();
        }
        catch (Exception ex)
        {
            SetStatus(Loc.Get("play_error", ex.Message));
        }
    }

    // ---- player bar -------------------------------------------------------------

    private void UpdateBar()
    {
        Player player = _services.Player;
        Track? track = player.Current;
        if (!ReferenceEquals(track, _shownTrack))
        {
            _shownTrack = track;
            BarArt.Source = track == null ? null : new ArtConverter().Convert(track.ArtPath, typeof(ImageSource), null!, System.Globalization.CultureInfo.InvariantCulture) as ImageSource;
            NowPanel.SetTrack(track);
        }

        BarTitle.Text = track?.DisplayTitle ?? Loc.Get("nothing_playing");
        BarArtist.Text = track == null ? "" : player.IsLoading ? Loc.Get("loading") : track.DisplayArtist;
        PlayButton.Content = player.IsPlaying ? "" : "";

        ShuffleButton.Foreground = player.Queue.Shuffle ? (Brush)FindResource("AccentBrush") : (Brush)FindResource("MutedBrush");
        RepeatButton.Content = player.Queue.Repeat == RepeatMode.One ? "" : "";
        RepeatButton.Foreground = player.Queue.Repeat == RepeatMode.Off ? (Brush)FindResource("MutedBrush") : (Brush)FindResource("AccentBrush");

        if (player.LastError != null && !player.IsLoading) SetStatus(Loc.Get("play_error", player.LastError));
    }

    private void OnTick(object? sender, EventArgs e)
    {
        Player player = _services.Player;
        TimeSpan now = _clock.Elapsed;
        TimeSpan elapsed = now - _lastClock;
        _lastClock = now;
        player.Tick(elapsed);

        TimeSpan position = player.Position;
        TimeSpan length = player.Duration;
        if (!_seeking)
        {
            Progress.Maximum = Math.Max(1, length.TotalSeconds);
            Progress.Value = Math.Min(position.TotalSeconds, Progress.Maximum);
            PosText.Text = TimeFormat.Format(position);
            LenText.Text = TimeFormat.Format(length);
        }

        if (_sleepAt is { } stopAt && DateTime.Now >= stopAt)
        {
            _sleepAt = null;
            player.Pause();
            SetStatus(Loc.Get("sleep_stopped"));
        }

        if (NowPanel.Visibility == Visibility.Visible)
            NowPanel.OnTick(position, elapsed, _services.Engine.Spectrum, player.IsPlaying);
    }

    private void LoadOutputDevices()
    {
        var items = new List<OutputDevice> { new("", Loc.Get("output_default")) };
        items.AddRange(AudioEngine.OutputDevices());
        OutputBox.ItemsSource = items;
        string saved = _services.Settings.OutputDeviceId ?? "";
        OutputBox.SelectedItem = items.FirstOrDefault(i => i.Id == saved) ?? items[0];
    }

    private async void PlayPause_Click(object sender, RoutedEventArgs e) => await Try(() => _services.Player.TogglePlayPauseAsync());

    private async void Prev_Click(object sender, RoutedEventArgs e) => await Try(() => _services.Player.PreviousAsync());

    private async void Next_Click(object sender, RoutedEventArgs e) => await Try(() => _services.Player.NextAsync());

    private void Shuffle_Click(object sender, RoutedEventArgs e)
    {
        bool shuffle = !_services.Player.Queue.Shuffle;
        _services.Player.SetShuffle(shuffle);
        _services.Settings.Shuffle = shuffle;
        UpdateBar();
    }

    private void Repeat_Click(object sender, RoutedEventArgs e)
    {
        var queue = _services.Player.Queue;
        queue.Repeat = queue.Repeat switch
        {
            RepeatMode.Off => RepeatMode.All,
            RepeatMode.All => RepeatMode.One,
            _ => RepeatMode.Off,
        };
        UpdateBar();
    }

    private void Progress_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e) => _seeking = true;

    private void Progress_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        _seeking = false;
        _services.Player.Seek(TimeSpan.FromSeconds(Progress.Value));
    }

    private void Volume_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (!IsLoaded) return;
        _services.Player.Volume = (float)e.NewValue;
        _services.Settings.Volume = (float)e.NewValue;
    }

    private async void Output_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || OutputBox.SelectedItem is not OutputDevice device) return;
        string? id = string.IsNullOrEmpty(device.Id) ? null : device.Id;
        _services.Settings.OutputDeviceId = id;
        await Try(() => _services.Player.SetOutputDeviceAsync(id));
    }

    private void Eq_Click(object sender, RoutedEventArgs e)
    {
        new EqualizerWindow(_services) { Owner = this }.ShowDialog();
    }

    private void Sleep_Click(object sender, RoutedEventArgs e)
    {
        var menu = new ContextMenu { PlacementTarget = SleepButton, Placement = System.Windows.Controls.Primitives.PlacementMode.Top };
        foreach (int minutes in new[] { 15, 30, 45, 60 })
        {
            var item = new MenuItem { Header = $"{minutes} min" };
            item.Click += (_, _) =>
            {
                _sleepAt = DateTime.Now.AddMinutes(minutes);
                SetStatus(Loc.Get("sleep_set", minutes));
            };
            menu.Items.Add(item);
        }

        var off = new MenuItem { Header = Loc.Get("sleep_off") };
        off.Click += (_, _) =>
        {
            _sleepAt = null;
            SetStatus(Loc.Get("sleep_off"));
        };
        menu.Items.Add(off);
        menu.IsOpen = true;
    }

    private void Panel_Click(object sender, RoutedEventArgs e)
    {
        bool show = NowPanel.Visibility != Visibility.Visible;
        NowPanel.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
        NowColumn.Width = show ? new GridLength(340) : new GridLength(0);
        if (show) NowPanel.SetTrack(_services.Player.Current);
    }
}
