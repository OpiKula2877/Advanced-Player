using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Views;

public sealed record TopItem(int Rank, string Title, string Subtitle, int Plays);

/// <summary>Totals, the most played songs and a 14-day listening chart, all read from the listening history.</summary>
public partial class StatsView : UserControl, IRefreshable
{
    public StatsView(MainWindow host)
    {
        InitializeComponent();
        TitleText.Text = Loc.Get("nav_stats");
        ChartTitle.Text = Loc.Get("stats_last14");
        TopTitle.Text = Loc.Get("stats_top");
        Refresh();
    }

    public void Refresh()
    {
        Database db = App.Services.Db;
        StatsSummary summary = db.Summary();
        SongsValue.Text = summary.Tracks.ToString(CultureInfo.CurrentCulture);
        SongsLabel.Text = Loc.Get("stats_songs");
        PlaysValue.Text = summary.Plays.ToString(CultureInfo.CurrentCulture);
        PlaysLabel.Text = Loc.Get("stats_plays");
        TimeSpan listened = TimeSpan.FromMilliseconds(summary.ListenedMs);
        ListenedValue.Text = Loc.Get("hours_minutes", (int)listened.TotalHours, listened.Minutes);
        ListenedLabel.Text = Loc.Get("stats_listened");

        BuildChart(db.DailyListening(14));
        TopList.ItemsSource = db.TopTracks(10)
            .Select((top, index) => new TopItem(
                index + 1,
                top.Track.DisplayTitle,
                $"{top.Track.DisplayArtist} · {TimeFormat.Format(TimeSpan.FromMilliseconds(top.ListenedMs))}",
                top.Plays))
            .ToList();
    }

    private void BuildChart(IReadOnlyList<(DateOnly Day, long ListenedMs)> days)
    {
        Chart.Children.Clear();
        Chart.ColumnDefinitions.Clear();
        long max = Math.Max(1, days.Max(d => d.ListenedMs));

        for (int i = 0; i < days.Count; i++)
        {
            (DateOnly day, long ms) = days[i];
            Chart.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            var bar = new Rectangle
            {
                Fill = (Brush)FindResource("AccentBrush"),
                RadiusX = 3,
                RadiusY = 3,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(5, 0, 5, 20),
                Height = Math.Max(2, 135.0 * ms / max),
                ToolTip = $"{day.Day}.{day.Month}. · {TimeFormat.Format(TimeSpan.FromMilliseconds(ms))}",
            };
            Grid.SetColumn(bar, i);
            Chart.Children.Add(bar);

            var label = new TextBlock
            {
                Text = day.Day.ToString(CultureInfo.InvariantCulture),
                FontSize = 10,
                Foreground = (Brush)FindResource("MutedBrush"),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom,
            };
            Grid.SetColumn(label, i);
            Chart.Children.Add(label);
        }
    }
}
