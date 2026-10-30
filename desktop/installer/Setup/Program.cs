using System.Windows.Forms;

namespace AdvancedPlayer.Setup;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        InstallerOptions options = InstallerOptions.Parse(args);
        try
        {
            if (options.Uninstall || options.UninstallFinish)
            {
                return Uninstaller.Run(options);
            }

            if (options.Silent)
            {
                Installer.Install(options.TargetDirectory ?? Installer.DefaultDirectory, options.Desktop, options.StartMenu, options.Register);
                return 0;
            }

            Application.SetHighDpiMode(HighDpiMode.SystemAware);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
            return 0;
        }
        catch (Exception ex)
        {
            // A silent run has no window, so the reason is written to a log file in the temp folder.
            File.WriteAllText(Path.Combine(Path.GetTempPath(), "advancedplayer-setup.log"), ex.ToString());
            return 1;
        }
    }
}
