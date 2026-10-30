using System.Runtime.InteropServices;
using NAudio.CoreAudioApi;

namespace AdvancedPlayer.Desktop.Audio;

/// <summary>
/// Switches the Windows default playback device, so that every app (for example YouTube in Brave) plays through it,
/// and gives the original default back. Windows has no public API for this. The Sound settings page uses the
/// undocumented IPolicyConfig interface, and this class uses it in the same way.
/// </summary>
public static class SystemOutput
{
    private enum ERole
    {
        Console = 0,
        Multimedia = 1,
        Communications = 2,
    }

    private static readonly ERole[] Roles = { ERole.Console, ERole.Multimedia, ERole.Communications };

    /// <summary>The defaults from before the first switch. Null while no switch is active.</summary>
    private static Dictionary<ERole, string>? _originals;

    /// <summary>True when the Windows audio policy interface can be created on this PC. Nothing is changed.</summary>
    public static bool IsAvailable()
    {
        try
        {
            object client = new PolicyConfigClient();
            Marshal.ReleaseComObject(client);
            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Makes the device the default output for all apps. The defaults from before the first switch are remembered,
    /// so that <see cref="Restore"/> can put them back. Returns null on success, or the reason it failed.
    /// </summary>
    public static string? TrySwitch(string? deviceId)
    {
        if (string.IsNullOrEmpty(deviceId)) return "no device selected";

        try
        {
            if (!IsActiveRenderDevice(deviceId)) return "the device is not connected";

            _originals ??= CurrentDefaults();
            IPolicyConfig policy = (IPolicyConfig)new PolicyConfigClient();
            try
            {
                foreach (ERole role in Roles)
                {
                    int result = policy.SetDefaultEndpoint(deviceId, role);
                    if (result != 0) Marshal.ThrowExceptionForHR(result);
                }
            }
            finally
            {
                Marshal.ReleaseComObject(policy);
            }

            return null;
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    /// <summary>Gives back the defaults that were in place before the first switch. Does nothing when no switch was made.</summary>
    public static void Restore()
    {
        if (_originals == null) return;

        try
        {
            IPolicyConfig policy = (IPolicyConfig)new PolicyConfigClient();
            try
            {
                foreach (KeyValuePair<ERole, string> original in _originals)
                {
                    policy.SetDefaultEndpoint(original.Value, original.Key);
                }
            }
            finally
            {
                Marshal.ReleaseComObject(policy);
            }
        }
        catch (Exception)
        {
            // The original device may have been removed in the meantime. Windows then keeps its current default.
        }
        finally
        {
            _originals = null;
        }
    }

    private static Dictionary<ERole, string> CurrentDefaults()
    {
        var defaults = new Dictionary<ERole, string>();
        using var enumerator = new MMDeviceEnumerator();
        foreach (ERole role in Roles)
        {
            try
            {
                defaults[role] = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, (Role)(int)role).ID;
            }
            catch (Exception)
            {
                // This role has no default device, so there is nothing to give back for it.
            }
        }

        return defaults;
    }

    private static bool IsActiveRenderDevice(string deviceId)
    {
        using var enumerator = new MMDeviceEnumerator();
        return enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active).Any(d => d.ID == deviceId);
    }

    [ComImport, Guid("870AF99C-171D-4F9E-AF0D-E63DF40C2BC9")]
    private class PolicyConfigClient
    {
    }

    /// <summary>The layout of the IPolicyConfig interface as the Windows 10 and 11 Sound settings use it.</summary>
    [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("F8679F50-850A-41CF-9C72-430F290290C8")]
    private interface IPolicyConfig
    {
        [PreserveSig] int GetMixFormat(string deviceName, IntPtr format);
        [PreserveSig] int GetDeviceFormat(string deviceName, int useDefault, IntPtr format);
        [PreserveSig] int ResetDeviceFormat(string deviceName);
        [PreserveSig] int SetDeviceFormat(string deviceName, IntPtr endpointFormat, IntPtr mixFormat);
        [PreserveSig] int GetProcessingPeriod(string deviceName, int useDefault, IntPtr defaultPeriod, IntPtr minimumPeriod);
        [PreserveSig] int SetProcessingPeriod(string deviceName, IntPtr period);
        [PreserveSig] int GetShareMode(string deviceName, IntPtr mode);
        [PreserveSig] int SetShareMode(string deviceName, IntPtr mode);
        [PreserveSig] int GetPropertyValue(string deviceName, int fxStore, IntPtr key, IntPtr value);
        [PreserveSig] int SetPropertyValue(string deviceName, int fxStore, IntPtr key, IntPtr value);
        [PreserveSig] int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string deviceId, ERole role);
        [PreserveSig] int SetEndpointVisibility([MarshalAs(UnmanagedType.LPWStr)] string deviceId, int visible);
    }
}
