using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using AdvancedPlayer.Desktop.Models;

// Aliased because System.Windows.Controls.Primitives also has a type called Track.
using ButtonBase = System.Windows.Controls.Primitives.ButtonBase;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>Search the library and add the chosen songs to the end of the queue. Several songs can be picked at once.</summary>
public partial class SongPicker : Window
{
    private readonly List<Track> _all;
    private readonly ObservableCollection<Track> _shown = new();

    public SongPicker()
    {
        InitializeComponent();
        AddButton.Content = Loc.Get("add_to_queue_button");
        CloseButton.Content = Loc.Get("close");
        _all = App.Services.Library.Tracks.OrderBy(t => t.DisplayTitle, StringComparer.CurrentCultureIgnoreCase).ToList();
        ResultList.ItemsSource = _shown;
        ResultList.AddHandler(ButtonBase.ClickEvent, new RoutedEventHandler(OnRowButtonClick));
        Filter();
        Loaded += (_, _) => SearchBox.Focus();
    }

    private void Filter()
    {
        string query = SearchBox.Text.Trim();
        _shown.Clear();
        foreach (Track track in _all)
        {
            if (query.Length == 0 || Matches(track, query)) _shown.Add(track);
        }

        StatusText.Text = Loc.Get("tracks_count", _shown.Count);
    }

    private static bool Matches(Track track, string query) =>
        track.Title.Contains(query, StringComparison.CurrentCultureIgnoreCase)
        || track.Artist.Contains(query, StringComparison.CurrentCultureIgnoreCase)
        || track.Album.Contains(query, StringComparison.CurrentCultureIgnoreCase);

    private void Search_TextChanged(object sender, TextChangedEventArgs e) => Filter();

    private async void Add_Click(object sender, RoutedEventArgs e)
    {
        var chosen = ResultList.SelectedItems.Cast<Track>().ToList();
        if (chosen.Count == 0)
        {
            StatusText.Text = Loc.Get("pick_songs_first");
            return;
        }

        foreach (Track track in chosen) await App.Services.Player.EnqueueAsync(track);
        Close();
    }

    private void Result_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (ResultList.SelectedItem is Track track) _ = App.Services.Player.EnqueueAsync(track);
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

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
