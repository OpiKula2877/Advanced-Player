using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace AdvancedPlayer.Desktop.Views;

public sealed record LanguageOption(string Code, string Name);

/// <summary>Music folders, a rescan button and the interface language.</summary>
public partial class SettingsView : UserControl, IRefreshable
{
    private readonly MainWindow _host;
    private readonly List<LanguageOption> _languages = new() { new("en", "English"), new("cs", "Čeština") };
    private bool _loading;

    public SettingsView(MainWindow host)
    {
        InitializeComponent();
        _host = host;
        TitleText.Text = Loc.Get("nav_settings");
        FoldersTitle.Text = Loc.Get("folders");
        AddFolderButton.Content = Loc.Get("add_folder");
        RemoveFolderButton.Content = Loc.Get("remove");
        ScanButton.Content = Loc.Get("scan");
        LanguageTitle.Text = Loc.Get("language");
        LanguageBox.ItemsSource = _languages;
        Refresh();
    }

    public void Refresh()
    {
        AppServices services = App.Services;
        FolderList.ItemsSource = services.Db.Folders();
        _loading = true;
        LanguageBox.SelectedItem = _languages.FirstOrDefault(o => o.Code == services.Settings.Language);
        _loading = false;
        AboutText.Text = "Advanced Player for Windows · " + services.DataDirectory;
    }

    private async void AddFolder_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFolderDialog { Title = Loc.Get("choose_folder") };
        if (dialog.ShowDialog(Window.GetWindow(this)) != true) return;
        App.Services.Db.AddFolder(dialog.FolderName);
        Refresh();
        await _host.ScanAsync();
    }

    private async void RemoveFolder_Click(object sender, RoutedEventArgs e)
    {
        if (FolderList.SelectedItem is not string folder) return;
        App.Services.Db.RemoveFolder(folder);
        Refresh();
        // The scan drops songs that are no longer inside a music folder.
        await _host.ScanAsync();
    }

    private async void Scan_Click(object sender, RoutedEventArgs e) => await _host.ScanAsync();

    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || LanguageBox.SelectedItem is not LanguageOption option) return;
        App.Services.Settings.Language = option.Code;
        RestartHint.Text = Loc.Get("restart_hint");
    }
}
