using System.Windows;
using System.Windows.Controls;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>A small single-line input dialog in the app's dark style.</summary>
public static class Prompt
{
    public static string? Ask(Window? owner, string title, string initial)
    {
        var box = new TextBox { Text = initial, MinWidth = 300, Margin = new Thickness(0, 0, 0, 14) };
        var ok = new Button { Content = Loc.Get("ok"), IsDefault = true, Width = 90 };
        var cancel = new Button { Content = Loc.Get("cancel"), IsCancel = true, Width = 90, Margin = new Thickness(0, 0, 8, 0) };
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        buttons.Children.Add(cancel);
        buttons.Children.Add(ok);

        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(new TextBlock { Text = title, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 10) });
        panel.Children.Add(box);
        panel.Children.Add(buttons);

        var window = new Window
        {
            Title = title,
            Content = panel,
            SizeToContent = SizeToContent.WidthAndHeight,
            ResizeMode = ResizeMode.NoResize,
            ShowInTaskbar = false,
            Owner = owner,
            WindowStartupLocation = owner == null ? WindowStartupLocation.CenterScreen : WindowStartupLocation.CenterOwner,
        };
        ok.Click += (_, _) => window.DialogResult = true;
        window.Loaded += (_, _) =>
        {
            box.Focus();
            box.SelectAll();
        };

        return window.ShowDialog() == true ? box.Text.Trim() : null;
    }
}
