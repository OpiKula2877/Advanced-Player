namespace AdvancedPlayer.Setup;

/// <summary>
/// Command-line options. They follow the usual Windows installer style:
/// /S (silent), /D=folder, /NODESKTOP, /NOSTARTMENU, /NOREGISTRY. The uninstaller takes --uninstall.
/// </summary>
internal sealed class InstallerOptions
{
    public bool Silent { get; private set; }

    public bool Uninstall { get; private set; }

    /// <summary>Second step of an uninstall: the copy of the uninstaller removes the folder given after --finish.</summary>
    public bool UninstallFinish { get; private set; }

    public string? TargetDirectory { get; private set; }

    public bool Desktop { get; private set; } = true;

    public bool StartMenu { get; private set; } = true;

    public bool Register { get; private set; } = true;

    public static InstallerOptions Parse(string[] args)
    {
        var options = new InstallerOptions();
        for (int i = 0; i < args.Length; i++)
        {
            string arg = args[i];
            if (arg.Equals("/S", StringComparison.OrdinalIgnoreCase)) options.Silent = true;
            else if (arg.Equals("--uninstall", StringComparison.OrdinalIgnoreCase) || arg.Equals("/U", StringComparison.OrdinalIgnoreCase)) options.Uninstall = true;
            else if (arg.Equals("--finish", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
            {
                options.UninstallFinish = true;
                options.TargetDirectory = args[++i];
            }
            else if (arg.StartsWith("/D=", StringComparison.OrdinalIgnoreCase)) options.TargetDirectory = arg[3..];
            else if (arg.Equals("/NODESKTOP", StringComparison.OrdinalIgnoreCase)) options.Desktop = false;
            else if (arg.Equals("/NOSTARTMENU", StringComparison.OrdinalIgnoreCase)) options.StartMenu = false;
            else if (arg.Equals("/NOREGISTRY", StringComparison.OrdinalIgnoreCase)) options.Register = false;
        }

        return options;
    }
}
