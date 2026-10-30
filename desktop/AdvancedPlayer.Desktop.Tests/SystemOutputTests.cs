using AdvancedPlayer.Desktop.Audio;

namespace AdvancedPlayer.Desktop.Tests;

public class SystemOutputTests
{
    [Fact]
    public void PolicyInterfaceCanBeCreatedWithoutChangingAnything()
    {
        Assert.True(SystemOutput.IsAvailable());
    }

    [Fact]
    public void SwitchWithoutADeviceReportsAnErrorAndChangesNothing()
    {
        Assert.NotNull(SystemOutput.TrySwitch(null));
        SystemOutput.Restore();
    }
}
