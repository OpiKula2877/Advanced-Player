using System.Diagnostics;
using System.Windows.Forms;

namespace AdvancedPlayer.Setup;

/// <summary>The installer window: choose the folder and the shortcuts, then install.</summary>
internal sealed class SetupForm : Form
{
    private readonly TextBox _folder = new();
    private readonly Button _browse = new();
    private readonly CheckBox _desktop = new();
    private readonly CheckBox _startMenu = new();
    private readonly ProgressBar _progress = new();
    private readonly Label _status = new();
    private readonly Button _install = new();
    private readonly Button _cancel = new();

    public SetupForm()
    {
        Text = $"{Installer.AppName} {Installer.AppVersion} setup";
        ClientSize = new Size(520, 300);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 9f);
        if (!string.IsNullOrEmpty(Environment.ProcessPath))
        {
            Icon? icon = Icon.ExtractAssociatedIcon(Environment.ProcessPath);
            if (icon != null) Icon = icon;
        }

        var intro = new Label
        {
            Text = $"This installs {Installer.AppName} for your Windows account. No administrator rights are needed.",
        };
        intro.SetBounds(20, 16, 480, 40);

        _folder.Text = Installer.DefaultDirectory;
        _folder.SetBounds(20, 70, 380, 24);

        _browse.Text = "Browse...";
        _browse.SetBounds(410, 68, 90, 28);
        _browse.Click += (_, _) => ChooseFolder();

        _desktop.Text = "Create a desktop shortcut";
        _desktop.Checked = true;
        _desktop.SetBounds(20, 110, 300, 24);

        _startMenu.Text = "Add to the Start menu";
        _startMenu.Checked = true;
        _startMenu.SetBounds(20, 138, 300, 24);

        _progress.SetBounds(20, 180, 480, 22);
        _status.SetBounds(20, 208, 480, 22);

        _install.Text = "Install";
        _install.SetBounds(300, 245, 90, 30);
        _install.Click += async (_, _) => await InstallAsync();

        _cancel.Text = "Cancel";
        _cancel.SetBounds(410, 245, 90, 30);
        _cancel.Click += (_, _) => Close();

        AcceptButton = _install;
        CancelButton = _cancel;
        Controls.AddRange(new Control[] { intro, _folder, _browse, _desktop, _startMenu, _progress, _status, _install, _cancel });
    }

    private void ChooseFolder()
    {
        using var dialog = new FolderBrowserDialog
        {
            Description = "Choose a folder for Advanced Player. The program gets its own subfolder in it.",
            UseDescriptionForTitle = true,
        };
        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            _folder.Text = Path.Combine(dialog.SelectedPath, Installer.AppName);
        }
    }

    private async Task InstallAsync()
    {
        string target = _folder.Text.Trim();
        if (target.Length == 0) return;

        SetBusy(true);
        var progress = new Progress<int>(value => _progress.Value = Math.Clamp(value, 0, 100));
        try
        {
            await Task.Run(() => Installer.Install(target, _desktop.Checked, _startMenu.Checked, register: true, progress));
            _status.Text = "Installed.";
        }
        catch (Exception ex)
        {
            SetBusy(false);
            _status.Text = "Installation failed.";
            MessageBox.Show(this, ex.Message, Installer.AppName, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }

        bool start = MessageBox.Show(this, $"{Installer.AppName} is installed. Start it now?", Installer.AppName,
            MessageBoxButtons.YesNo, MessageBoxIcon.Information) == DialogResult.Yes;
        if (start)
        {
            Process.Start(new ProcessStartInfo(Path.Combine(target, Installer.AppExe)) { UseShellExecute = true });
        }

        Close();
    }

    private void SetBusy(bool busy)
    {
        _folder.Enabled = !busy;
        _browse.Enabled = !busy;
        _desktop.Enabled = !busy;
        _startMenu.Enabled = !busy;
        _install.Enabled = !busy;
    }
}
