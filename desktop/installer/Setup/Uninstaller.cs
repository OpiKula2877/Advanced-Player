using System.Diagnostics;
using System.Windows.Forms;

namespace AdvancedPlayer.Setup;

/// <summary>
/// Removes the program. The uninstaller cannot delete the folder it runs from, so it starts a copy of itself
/// from the temp folder. That copy removes the program folder and then deletes itself.
/// </summary>
internal static class Uninstaller
{
    public static int Run(InstallerOptions options)
    {
        if (options.UninstallFinish)
        {
            Finish(options.TargetDirectory ?? Installer.DefaultDirectory);
            return 0;
        }

        string here = Path.GetDirectoryName(Environment.ProcessPath) ?? Installer.DefaultDirectory;
        string target = Path.GetFullPath(options.TargetDirectory ?? here);

        if (!options.Silent)
        {
            DialogResult answer = MessageBox.Show(
                $"Remove {Installer.AppName} from this PC?\n\nYour library and settings in %LOCALAPPDATA%\\AdvancedPlayer are kept.",
                Installer.AppName,
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);
            if (answer != DialogResult.Yes) return 0;
        }

        string copy = Path.Combine(Path.GetTempPath(), $"advancedplayer-uninstall-{Guid.NewGuid():N}.exe");
        File.Copy(Environment.ProcessPath!, copy);
        Process.Start(new ProcessStartInfo(copy)
        {
            ArgumentList = { "--finish", target },
            UseShellExecute = false,
        });
        return 0;
    }

    private static void Finish(string target)
    {
        Installer.RemoveShortcutsAndRegistration();

        // The original uninstaller may still hold a file for a moment, so retry for up to 15 seconds.
        for (int attempt = 0; attempt < 30; attempt++)
        {
            try
            {
                if (Directory.Exists(target)) Directory.Delete(target, recursive: true);
                break;
            }
            catch (IOException)
            {
                Thread.Sleep(500);
            }
            catch (UnauthorizedAccessException)
            {
                Thread.Sleep(500);
            }
        }

        // Delete this temp copy after it has exited. The Arguments string is used as written, so the quotes survive.
        Process.Start(new ProcessStartInfo("cmd.exe")
        {
            Arguments = $"/c ping 127.0.0.1 -n 3 > nul & del /f /q \"{Environment.ProcessPath}\"",
            CreateNoWindow = true,
            UseShellExecute = false,
        });
    }
}
