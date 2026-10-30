using System.Windows;

namespace AdvancedPlayer.Desktop;

public partial class App : Application
{
    public static AppServices Services { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        string dataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "AdvancedPlayer");
        Services = new AppServices(dataDirectory);
        Loc.Czech = Services.Settings.Language == "cs";

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
        Services?.Dispose();
        base.OnExit(e);
    }
}
