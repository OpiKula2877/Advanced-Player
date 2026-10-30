using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;

namespace AdvancedPlayer.Desktop.Phone;

/// <summary>A paired phone that offers the PC link service.</summary>
public sealed record PhoneDevice(string Id, string Name, string Address);

/// <summary>Finds phones that run the Advanced Player PC link and opens an RFCOMM (Bluetooth serial) connection to them.</summary>
public static class PhoneLink
{
    /// <summary>Bluetooth service id of the PC link. Must match PcLinkServer in the Android app.</summary>
    public static readonly Guid ServiceId = new("8f4c2b1e-6a3d-4e7b-9c15-2d9a7f0b6e31");

    /// <summary>Paired devices that advertise the PC link service. Empty when nothing is paired or the phone link is off.</summary>
    public static async Task<List<PhoneDevice>> FindDevicesAsync()
    {
        var selector = RfcommDeviceService.GetDeviceSelector(RfcommServiceId.FromUuid(ServiceId));
        DeviceInformationCollection devices = await DeviceInformation.FindAllAsync(selector);
        var result = new List<PhoneDevice>();
        foreach (DeviceInformation info in devices)
        {
            RfcommDeviceService? service = await RfcommDeviceService.FromIdAsync(info.Id);
            if (service == null) continue;
            result.Add(new PhoneDevice(info.Id, info.Name, service.ConnectionHostName.RawName));
        }

        return result;
    }

    public static async Task<PhoneSession> ConnectAsync(PhoneDevice device, CancellationToken ct = default)
    {
        RfcommDeviceService? service = await RfcommDeviceService.FromIdAsync(device.Id).AsTask(ct);
        if (service == null)
            throw new PhoneException("Windows cannot open this phone. Pair it in Windows Bluetooth settings first.");

        var socket = new StreamSocket();
        try
        {
            await socket.ConnectAsync(
                service.ConnectionHostName,
                service.ConnectionServiceName,
                SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication).AsTask(ct);
        }
        catch (Exception ex)
        {
            socket.Dispose();
            throw new PhoneException("Could not connect. Open Advanced Player on the phone and turn on PC link. (" + ex.Message + ")");
        }

        return new PhoneSession(socket.InputStream.AsStreamForRead(), socket.OutputStream.AsStreamForWrite(), socket);
    }
}
