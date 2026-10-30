using System.Windows;
using AdvancedPlayer.Desktop.Audio;

namespace AdvancedPlayer.Desktop;

public partial class App : Application
{
    public static AppServices Services { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        string dataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "AdvancedPlayer");
        Services = new AppServices(dataDirectory);
        Loc.Czech = Services.Settings.Language == "cs";

        // The Windows output is switched at startup when the user turned that on. It is given back on exit.
        if (Services.Settings.SystemOutputEnabled)
        {
            SystemOutput.TrySwitch(Services.Settings.SystemOutputDeviceId);
        }

        DispatcherUnhandledException += (_, args) =>
        {
            MessageBox.Show(args.Exception.Message, Loc.Get("app_title"), MessageBoxButton.OK, MessageBoxImage.Error);
            args.Handled = true;
        };

        base.OnStartup(e);
        new MainWindow().Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        SystemOutput.Restore();
        Services?.Dispose();
        base.OnExit(e);
    }
}
