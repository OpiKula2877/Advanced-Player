using System.Windows;
using System.Windows.Controls;
using AdvancedPlayer.Desktop.Phone;

namespace AdvancedPlayer.Desktop.Views;

/// <summary>Finds the phone over Bluetooth, connects to it and syncs its songs into the library.</summary>
public partial class PhoneView : UserControl, IRefreshable
{
    private readonly MainWindow _host;

    public PhoneView(MainWindow host)
    {
        InitializeComponent();
        _host = host;
        TitleText.Text = Loc.Get("phone_title");
        IntroText.Text = Loc.Get("phone_intro");
        FindButton.Content = Loc.Get("phone_find");
        SyncButton.Content = Loc.Get("phone_sync");
        Refresh();
    }

    public void Refresh() => UpdateState();

    private void UpdateState()
    {
        AppServices services = App.Services;
        bool connected = services.PhoneSession != null;
        ConnectButton.Content = Loc.Get(connected ? "phone_disconnect" : "phone_connect");
        SyncButton.IsEnabled = connected;
        StatusText.Text = connected
            ? Loc.Get("phone_connected", services.PhoneDevice?.Name ?? services.Settings.PhoneName, services.Library.Tracks.Count(t => t.IsPhone))
            : Loc.Get("phone_idle");
    }

    private async void Find_Click(object sender, RoutedEventArgs e) => await _host.Try(async () =>
    {
        StatusText.Text = Loc.Get("phone_busy");
        List<PhoneDevice> devices = await PhoneLink.FindDevicesAsync();
        DeviceList.ItemsSource = devices;
        if (devices.Count == 0)
        {
            StatusText.Text = Loc.Get("phone_none");
            return;
        }

        DeviceList.SelectedIndex = 0;
        UpdateState();
    });

    private async void Connect_Click(object sender, RoutedEventArgs e) => await _host.Try(async () =>
    {
        AppServices services = App.Services;
        if (services.PhoneSession != null)
        {
            await DisconnectAsync();
            UpdateState();
            return;
        }

        if (DeviceList.SelectedItem is not PhoneDevice device)
        {
            StatusText.Text = Loc.Get("phone_none");
            return;
        }

        StatusText.Text = Loc.Get("phone_busy");
        PhoneSession session = await PhoneLink.ConnectAsync(device);
        try
        {
            await session.HelloAsync();
            await services.Phone.SyncAsync(session, device.Address);
        }
        catch (Exception)
        {
            await session.DisposeAsync();
            throw;
        }

        services.Settings.PhoneAddress = device.Address;
        services.Settings.PhoneName = device.Name;
        services.PhoneDevice = device;
        services.PhoneSession = session;
        _host.RefreshCurrent();
        UpdateState();
    });

    private async void Sync_Click(object sender, RoutedEventArgs e) => await _host.Try(async () =>
    {
        AppServices services = App.Services;
        if (services.PhoneSession is not { } session || services.PhoneDevice is not { } device) return;
        StatusText.Text = Loc.Get("phone_busy");
        await services.Phone.SyncAsync(session, device.Address);
        _host.RefreshCurrent();
        UpdateState();
    });

    private static async Task DisconnectAsync()
    {
        AppServices services = App.Services;
        PhoneSession? session = services.PhoneSession;
        services.PhoneSession = null;
        if (session != null) await session.DisposeAsync();
    }
}
