using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using AdvancedPlayer.Desktop.Models;

// Aliased because System.Windows.Controls.Primitives also has a type called Track.
using ButtonBase = System.Windows.Controls.Primitives.ButtonBase;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>What is playing and what comes next. Songs can be played early or removed, and the whole queue can be cleared.</summary>
public partial class QueueView : UserControl, IRefreshable
{
    private readonly MainWindow _host;
    private readonly ObservableCollection<Track> _items = new();

    public QueueView(MainWindow host)
    {
        InitializeComponent();
        _host = host;
        TitleText.Text = Loc.Get("nav_queue");
        AddSongButton.Content = Loc.Get("pick_song");
        ClearButton.Content = Loc.Get("clear_queue");
        List.ItemsSource = _items;
        List.AddHandler(ButtonBase.ClickEvent, new RoutedEventHandler(OnRowButtonClick));
        Loaded += (_, _) => App.Services.Player.Changed += Refresh;
        Unloaded += (_, _) => App.Services.Player.Changed -= Refresh;
        Refresh();
    }

    public void Refresh()
    {
        var player = App.Services.Player;
        Track? current = player.Current;
        CurrentText.Text = current == null
            ? Loc.Get("nothing_playing")
            : $"{Loc.Get("now_playing")}: {current.DisplayTitle} · {current.DisplayArtist}";

        _items.Clear();
        foreach (Track track in player.Queue.Upcoming) _items.Add(track);
        CountText.Text = Loc.Get("tracks_count", _items.Count);
        if (_items.Count == 0) CountText.Text += "  ·  " + Loc.Get("queue_empty");
    }

    private async void List_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (List.SelectedIndex < 0) return;
        int index = List.SelectedIndex;
        await _host.Try(() => App.Services.Player.JumpToUpcomingAsync(index));
    }

    private void AddSong_Click(object sender, RoutedEventArgs e)
    {
        new SongPicker { Owner = Window.GetWindow(this) }.ShowDialog();
        Refresh();
    }

    private void Clear_Click(object sender, RoutedEventArgs e)
    {
        App.Services.Player.ClearUpcoming();
        Refresh();
    }

    private void List_PreviewMouseRightButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (ItemsControl.ContainerFromElement(List, e.OriginalSource as DependencyObject) is not ListBoxItem item) return;
        if (!item.IsSelected)
        {
            List.SelectedItems.Clear();
            item.IsSelected = true;
        }

        // Remove from the end first so that the remaining indexes stay valid.
        var positions = List.SelectedItems.Cast<Track>()
            .Select(t => _items.IndexOf(t))
            .Where(i => i >= 0)
            .OrderByDescending(i => i)
            .ToList();

        var menu = new ContextMenu();
        var remove = new MenuItem { Header = Loc.Get("remove_from_queue") };
        remove.Click += (_, _) =>
        {
            foreach (int position in positions) App.Services.Player.RemoveUpcoming(position);
            Refresh();
        };
        menu.Items.Add(remove);
        List.ContextMenu = menu;
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
}
