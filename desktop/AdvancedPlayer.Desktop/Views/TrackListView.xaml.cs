using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Models;

// Aliased because System.Windows.Controls.Primitives also has a type called Track.
using ButtonBase = System.Windows.Controls.Primitives.ButtonBase;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>A list of songs with search, playback, favourites and a context menu (add to playlist, remove from playlist).</summary>
public partial class TrackListView : UserControl, IRefreshable
{
    private readonly MainWindow _host;
    private readonly ObservableCollection<Track> _visible = new();
    private readonly Func<IReadOnlyList<Track>>? _source;
    private readonly long? _playlistId;
    private IReadOnlyList<Track> _all;

    public TrackListView(MainWindow host, string title, IReadOnlyList<Track> tracks, long? playlistId = null, Func<IReadOnlyList<Track>>? source = null)
    {
        InitializeComponent();
        _host = host;
        _all = tracks;
        _playlistId = playlistId;
        _source = source;
        TitleText.Text = title;
        AddToQueueButton.Content = Loc.Get("add_to_queue_button");
        List.ItemsSource = _visible;
        List.AddHandler(ButtonBase.ClickEvent, new RoutedEventHandler(OnRowButtonClick));
        BackButton.Visibility = host.HasHistory ? Visibility.Visible : Visibility.Collapsed;
        ApplyFilter();
    }

    public void Refresh()
    {
        if (_source != null) _all = _source();
        BackButton.Visibility = _host.HasHistory ? Visibility.Visible : Visibility.Collapsed;
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        string query = SearchBox.Text.Trim();
        _visible.Clear();
        foreach (Track track in _all)
        {
            if (query.Length == 0 || Matches(track, query)) _visible.Add(track);
        }

        CountText.Text = Loc.Get("tracks_count", _visible.Count);
    }

    private static bool Matches(Track track, string query) =>
        track.Title.Contains(query, StringComparison.CurrentCultureIgnoreCase)
        || track.Artist.Contains(query, StringComparison.CurrentCultureIgnoreCase)
        || track.Album.Contains(query, StringComparison.CurrentCultureIgnoreCase)
        || track.FileName.Contains(query, StringComparison.CurrentCultureIgnoreCase);

    private void Search_TextChanged(object sender, TextChangedEventArgs e) => ApplyFilter();

    private void Back_Click(object sender, RoutedEventArgs e) => _host.Back();

    private Task PlayAt(int index, bool? shuffle) =>
        _host.Try(() => App.Services.Player.PlayListAsync(_visible.ToList(), index, shuffle));

    private async void AddToQueue_Click(object sender, RoutedEventArgs e)
    {
        var selected = List.SelectedItems.Cast<Track>().ToList();
        if (selected.Count == 0)
        {
            _host.SetStatus(Loc.Get("pick_songs_first"));
            return;
        }

        foreach (Track track in selected) await App.Services.Player.EnqueueAsync(track);
        _host.SetStatus(Loc.Get("added_to_queue", selected.Count));
    }

    private async void PlayAll_Click(object sender, RoutedEventArgs e) => await PlayAt(0, false);

    private async void ShuffleAll_Click(object sender, RoutedEventArgs e) => await PlayAt(0, true);

    private async void List_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (List.SelectedIndex >= 0) await PlayAt(List.SelectedIndex, null);
    }

    /// <summary>Right-click selects the row under the pointer, then the menu is built for the selection.</summary>
    private void List_PreviewMouseRightButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (ItemsControl.ContainerFromElement(List, e.OriginalSource as DependencyObject) is not ListBoxItem item) return;
        if (!item.IsSelected)
        {
            List.SelectedItems.Clear();
            item.IsSelected = true;
        }

        List.ContextMenu = BuildMenu();
    }

    /// <summary>The heart button on each row (Tag = "fav") toggles the favourite flag.</summary>
    private void OnRowButtonClick(object sender, RoutedEventArgs e)
    {
        if (e.OriginalSource is not Button { Tag: "fav" } button || button.DataContext is not Track track) return;
        bool value = !track.Favorite;
        App.Services.Db.SetFavorite(track.Id, value);
        track.Favorite = value;
        e.Handled = true;
    }

    private ContextMenu BuildMenu()
    {
        var selected = List.SelectedItems.Cast<Track>().ToList();
        var menu = new ContextMenu();
        if (selected.Count == 0) return menu;

        AppServices services = App.Services;
        menu.Items.Add(LocItem("play", () => _ = PlayAt(_visible.IndexOf(selected[0]), null)));
        menu.Items.Add(LocItem("play_next", () => _ = _host.Try(async () =>
        {
            foreach (Track track in selected.AsEnumerable().Reverse()) await services.Player.PlayNextAsync(track);
        })));
        menu.Items.Add(LocItem("add_to_queue", () => _ = _host.Try(async () =>
        {
            foreach (Track track in selected) await services.Player.EnqueueAsync(track);
        })));

        var addTo = new MenuItem { Header = Loc.Get("add_to_playlist") };
        foreach (PlaylistInfo playlist in services.Db.Playlists())
        {
            addTo.Items.Add(Item(playlist.Name, () =>
            {
                services.Db.AddToPlaylist(playlist.Id, selected.Select(t => t.Id));
                _host.SetStatus(Loc.Get("add_to_playlist") + ": " + playlist.Name);
            }));
        }

        if (addTo.Items.Count == 0) addTo.IsEnabled = false;
        menu.Items.Add(addTo);

        bool favorite = !selected[0].Favorite;
        menu.Items.Add(LocItem("toggle_favorite", () =>
        {
            foreach (Track track in selected)
            {
                services.Db.SetFavorite(track.Id, favorite);
                track.Favorite = favorite;
            }
        }));

        // Positions in a playlist only match the list when no search filter is active.
        if (_playlistId is { } playlistId && _visible.Count == _all.Count)
        {
            menu.Items.Add(new Separator());
            menu.Items.Add(LocItem("remove_from_playlist", () =>
            {
                var positions = selected.Select(t => _visible.IndexOf(t)).OrderByDescending(i => i).ToList();
                foreach (int position in positions) services.Db.RemoveFromPlaylist(playlistId, position);
                Refresh();
            }));
        }

        return menu;
    }

    private static MenuItem LocItem(string key, Action action) => Item(Loc.Get(key), action);

    private static MenuItem Item(string header, Action action)
    {
        var item = new MenuItem { Header = header };
        item.Click += (_, _) => action();
        return item;
    }
}
