using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;
using AdvancedPlayer.Desktop.Audio;
using AdvancedPlayer.Desktop.Data;

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
        AudioTitle.Text = Loc.Get("audio_title");
        AudioHint.Text = Loc.Get("audio_hint");
        AudioSettingsButton.Content = Loc.Get("audio_open");
        SystemOutputBox.Content = Loc.Get("system_output_enable");
        SystemOutputHint.Text = Loc.Get("system_output_hint");
        LoadSystemOutput();
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

    /// <summary>Opens Windows' per-app volume page, where each app (for example Brave) gets its own output device.</summary>
    private void AudioSettings_Click(object sender, RoutedEventArgs e)
    {
        Process.Start(new ProcessStartInfo("ms-settings:apps-volume") { UseShellExecute = true });
    }

    /// <summary>Fills the device list and shows the saved state of the switch.</summary>
    private void LoadSystemOutput()
    {
        AppSettings settings = App.Services.Settings;
        var devices = AudioEngine.OutputDevices().ToList();
        SystemOutputDeviceBox.ItemsSource = devices;
        SystemOutputDeviceBox.SelectedItem = devices.FirstOrDefault(d => d.Id == settings.SystemOutputDeviceId) ?? devices.FirstOrDefault();
        SystemOutputBox.IsChecked = settings.SystemOutputEnabled;
        if (settings.SystemOutputEnabled && devices.All(d => d.Id != settings.SystemOutputDeviceId))
        {
            SystemOutputStatus.Text = Loc.Get("system_output_missing");
        }
    }

    private void SystemOutput_Click(object sender, RoutedEventArgs e)
    {
        AppSettings settings = App.Services.Settings;
        if (SystemOutputBox.IsChecked == true)
        {
            settings.SystemOutputEnabled = true;
            if (SystemOutputDeviceBox.SelectedItem is OutputDevice device) settings.SystemOutputDeviceId = device.Id;
            ApplySystemOutput();
        }
        else
        {
            settings.SystemOutputEnabled = false;
            SystemOutput.Restore();
            SystemOutputStatus.Text = Loc.Get("system_output_restored");
        }
    }

    private void SystemOutputDevice_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || SystemOutputDeviceBox.SelectedItem is not OutputDevice device) return;

        App.Services.Settings.SystemOutputDeviceId = device.Id;
        if (App.Services.Settings.SystemOutputEnabled) ApplySystemOutput();
    }

    private void ApplySystemOutput()
    {
        string? error = SystemOutput.TrySwitch(App.Services.Settings.SystemOutputDeviceId);
        if (error == null)
        {
            SystemOutputStatus.Text = Loc.Get("system_output_switched", (SystemOutputDeviceBox.SelectedItem as OutputDevice)?.Name ?? "");
            return;
        }

        // A failed switch must not stay switched on, and the part that did switch is given back.
        SystemOutput.Restore();
        App.Services.Settings.SystemOutputEnabled = false;
        SystemOutputBox.IsChecked = false;
        SystemOutputStatus.Text = Loc.Get("system_output_failed", error);
    }

    private async void Scan_Click(object sender, RoutedEventArgs e) => await _host.ScanAsync();

    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || LanguageBox.SelectedItem is not LanguageOption option) return;
        App.Services.Settings.Language = option.Code;
        RestartHint.Text = Loc.Get("restart_hint");
    }
}
