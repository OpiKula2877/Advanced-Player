using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Views;

public enum GroupMode { Albums, Artists, Playlists }

/// <summary>One row of the album, artist or playlist list. PlaylistId is set for playlists only.</summary>
public sealed record GroupItem(string Title, string Subtitle, string? ArtPath, IReadOnlyList<Track> Tracks, long? PlaylistId);

/// <summary>Albums, artists or playlists. Opening a row shows its songs; playlists can be created, renamed and deleted.</summary>
public partial class GroupListView : UserControl, IRefreshable
{
    private readonly MainWindow _host;
    private readonly GroupMode _mode;

    public GroupListView(MainWindow host, GroupMode mode)
    {
        InitializeComponent();
        _host = host;
        _mode = mode;
        TitleText.Text = Loc.Get(mode switch
        {
            GroupMode.Albums => "nav_albums",
            GroupMode.Artists => "nav_artists",
            _ => "nav_playlists",
        });
        NewButton.Visibility = mode == GroupMode.Playlists ? Visibility.Visible : Visibility.Collapsed;
        Refresh();
    }

    public void Refresh()
    {
        List.ItemsSource = Build().ToList();
    }

    private IEnumerable<GroupItem> Build()
    {
        var library = App.Services.Library;
        switch (_mode)
        {
            case GroupMode.Albums:
                return library.Tracks
                    .GroupBy(t => t.Album.Trim(), StringComparer.CurrentCultureIgnoreCase)
                    .OrderBy(g => g.Key, StringComparer.CurrentCultureIgnoreCase)
                    .Select(g =>
                    {
                        var tracks = g.OrderBy(t => t.TrackNumber).ThenBy(t => t.DisplayTitle).ToList();
                        string title = g.Key.Length == 0 ? Loc.Get("unknown_album") : g.Key;
                        string subtitle = Loc.Get("albums_by", tracks[0].DisplayArtist, tracks.Count);
                        return new GroupItem(title, subtitle, tracks.FirstOrDefault(t => t.ArtPath != null)?.ArtPath, tracks, null);
                    })
                    .ToList();

            case GroupMode.Artists:
                return library.Tracks
                    .GroupBy(t => t.Artist.Trim(), StringComparer.CurrentCultureIgnoreCase)
                    .OrderBy(g => g.Key, StringComparer.CurrentCultureIgnoreCase)
                    .Select(g =>
                    {
                        var tracks = g.OrderBy(t => t.Album).ThenBy(t => t.TrackNumber).ToList();
                        string title = g.Key.Length == 0 ? Loc.Get("unknown_artist") : g.Key;
                        return new GroupItem(title, Loc.Get("tracks_count", tracks.Count), tracks.FirstOrDefault(t => t.ArtPath != null)?.ArtPath, tracks, null);
                    })
                    .ToList();

            default:
                return App.Services.Db.Playlists().Select(p =>
                {
                    var tracks = App.Services.Db.PlaylistTracks(p.Id);
                    return new GroupItem(p.Name, Loc.Get("tracks_count", tracks.Count), tracks.FirstOrDefault(t => t.ArtPath != null)?.ArtPath, tracks, p.Id);
                }).ToList();
        }
    }

    private void List_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (List.SelectedItem is not GroupItem group) return;
        List.SelectedItem = null;

        Func<IReadOnlyList<Track>>? source = group.PlaylistId is { } id ? () => App.Services.Db.PlaylistTracks(id) : null;
        _host.Navigate(new TrackListView(_host, group.Title, group.Tracks, group.PlaylistId, source));
    }

    private void New_Click(object sender, RoutedEventArgs e)
    {
        string? name = Prompt.Ask(Window.GetWindow(this), Loc.Get("playlist_name"), "");
        if (string.IsNullOrWhiteSpace(name)) return;
        App.Services.Db.CreatePlaylist(name.Trim());
        Refresh();
    }

    /// <summary>Right-click on a playlist offers rename and delete.</summary>
    private void List_PreviewMouseRightButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (_mode != GroupMode.Playlists) return;
        if (ItemsControl.ContainerFromElement(List, e.OriginalSource as DependencyObject) is not ListBoxItem { DataContext: GroupItem { PlaylistId: { } id } group }) return;

        var menu = new ContextMenu();
        var rename = new MenuItem { Header = Loc.Get("rename") };
        rename.Click += (_, _) => Rename(id, group.Title);
        var delete = new MenuItem { Header = Loc.Get("delete") };
        delete.Click += (_, _) => Delete(id, group.Title);
        menu.Items.Add(rename);
        menu.Items.Add(delete);
        List.ContextMenu = menu;
    }

    private void Rename(long id, string current)
    {
        string? name = Prompt.Ask(Window.GetWindow(this), Loc.Get("new_name"), current);
        if (string.IsNullOrWhiteSpace(name)) return;
        App.Services.Db.RenamePlaylist(id, name.Trim());
        Refresh();
    }

    private void Delete(long id, string name)
    {
        if (MessageBox.Show(Loc.Get("confirm_delete", name), Loc.Get("delete"), MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes)
            return;
        App.Services.Db.DeletePlaylist(id);
        Refresh();
    }
}
