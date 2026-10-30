namespace AdvancedPlayer.Desktop.Views;

/// <summary>A page that re-reads its data when it becomes visible again (for example after Back).</summary>
public interface IRefreshable
{
    void Refresh();
}
