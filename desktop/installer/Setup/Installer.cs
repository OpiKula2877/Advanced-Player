using System.IO.Compression;
using System.Reflection;
using Microsoft.Win32;

namespace AdvancedPlayer.Setup;

/// <summary>
/// Copies the program into place, creates the shortcuts and registers the uninstaller for the current user.
/// No administrator rights are needed, because the program lives in the user's own folder.
/// </summary>
internal static class Installer
{
    public const string AppName = "Advanced Player";
    public const string AppExe = "AdvancedPlayer.exe";
    public const string AppVersion = "1.3.0";
    public const string Publisher = "OpiKula";
    public const string UninstallExe = "Uninstall.exe";

    private const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\AdvancedPlayer";
    private const string PayloadName = "payload.zip";

    public static string DefaultDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", AppName);

    public static void Install(string targetDirectory, bool desktop, bool startMenu, bool register, IProgress<int>? progress = null)
    {
        string target = Path.GetFullPath(targetDirectory);

        // Refuse folders with other files in them, so that a wrong folder choice never deletes anything.
        bool hasOtherFiles = Directory.Exists(target)
            && Directory.EnumerateFileSystemEntries(target).Any()
            && !File.Exists(Path.Combine(target, AppExe));
        if (hasOtherFiles)
        {
            throw new IOException("The folder is not empty and does not contain Advanced Player. Choose another folder.");
        }

        string zip = Path.Combine(Path.GetTempPath(), $"advancedplayer-{Guid.NewGuid():N}.zip");
        try
        {
            using (Stream payload = OpenPayload())
            using (FileStream file = File.Create(zip))
            {
                payload.CopyTo(file);
            }

            progress?.Report(10);

            // An earlier version is replaced. The library and the settings are kept in %LOCALAPPDATA%\AdvancedPlayer.
            if (Directory.Exists(target)) ClearDirectory(target);
            Directory.CreateDirectory(target);
            ZipFile.ExtractToDirectory(zip, target, overwriteFiles: true);
            progress?.Report(80);
        }
        finally
        {
            if (File.Exists(zip)) File.Delete(zip);
        }

        string appPath = Path.Combine(target, AppExe);
        string uninstallPath = Path.Combine(target, UninstallExe);
        File.Copy(Environment.ProcessPath!, uninstallPath, overwrite: true);

        if (startMenu) CreateShortcut(StartMenuShortcut, appPath, target);
        if (desktop) CreateShortcut(DesktopShortcut, appPath, target);
        if (register) RegisterUninstaller(target, uninstallPath);
        progress?.Report(100);
    }

    /// <summary>Removes the shortcuts and the uninstall entry. The program folder itself is removed by the uninstaller.</summary>
    public static void RemoveShortcutsAndRegistration()
    {
        DeleteIfExists(StartMenuShortcut);
        DeleteIfExists(DesktopShortcut);
        Registry.CurrentUser.DeleteSubKeyTree(UninstallKey, throwOnMissingSubKey: false);
    }

    public static long DirectorySize(string directory) =>
        Directory.EnumerateFiles(directory, "*", SearchOption.AllDirectories).Sum(f => new FileInfo(f).Length);

    private static string StartMenuShortcut =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), AppName + ".lnk");

    private static string DesktopShortcut =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), AppName + ".lnk");

    private static Stream OpenPayload() =>
        Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadName)
        ?? throw new InvalidOperationException("This setup file has no program inside. Build it with desktop\\installer\\build-installer.ps1.");

    private static void ClearDirectory(string directory)
    {
        foreach (string file in Directory.GetFiles(directory)) File.Delete(file);
        foreach (string subdirectory in Directory.GetDirectories(directory)) Directory.Delete(subdirectory, recursive: true);
    }

    private static void CreateShortcut(string shortcutPath, string targetPath, string workingDirectory)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(shortcutPath)!);
        Type shellType = Type.GetTypeFromProgID("WScript.Shell") ?? throw new InvalidOperationException("WScript.Shell is not available.");
        dynamic shell = Activator.CreateInstance(shellType)!;
        dynamic link = shell.CreateShortcut(shortcutPath);
        link.TargetPath = targetPath;
        link.WorkingDirectory = workingDirectory;
        link.IconLocation = targetPath + ",0";
        link.Description = AppName;
        link.Save();
    }

    private static void RegisterUninstaller(string target, string uninstallPath)
    {
        using RegistryKey key = Registry.CurrentUser.CreateSubKey(UninstallKey);
        key.SetValue("DisplayName", AppName);
        key.SetValue("DisplayVersion", AppVersion);
        key.SetValue("Publisher", Publisher);
        key.SetValue("InstallLocation", target);
        key.SetValue("DisplayIcon", Path.Combine(target, AppExe));
        key.SetValue("UninstallString", $"\"{uninstallPath}\" --uninstall");
        key.SetValue("NoModify", 1, RegistryValueKind.DWord);
        key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
        key.SetValue("EstimatedSize", (int)(DirectorySize(target) / 1024), RegistryValueKind.DWord);
    }

    private static void DeleteIfExists(string path)
    {
        if (File.Exists(path)) File.Delete(path);
    }
}
