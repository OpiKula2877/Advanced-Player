using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using AdvancedPlayer.Desktop.Audio;
using AdvancedPlayer.Desktop.Data;
using AdvancedPlayer.Desktop.Library;
using AdvancedPlayer.Desktop.Models;
using AdvancedPlayer.Desktop.Views;

namespace AdvancedPlayer.Desktop;

/// <summary>One lyric line in the list. IsCurrent highlights the line that is being sung.</summary>
public sealed class LyricItem : INotifyPropertyChanged
{
    private bool _isCurrent;

    public LyricItem(long timeMs, string text)
    {
        TimeMs = timeMs;
        Text = text;
    }

    public long TimeMs { get; }

    public string Text { get; }

    public bool IsCurrent
    {
        get => _isCurrent;
        set
        {
            if (_isCurrent == value) return;
            _isCurrent = value;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsCurrent)));
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
}

public sealed record StyleOption(CoverStyle Style, string Name);

public sealed record VisualOption(VisualMode Mode, string Name);

/// <summary>
/// Side panel: the artwork as a vinyl, a cassette or a cover, a visualizer in six modes, and synced lyrics.
/// The panel only exists while it is open, so it does its work only when the user can see it.
/// </summary>
public partial class NowPlayingPanel : UserControl
{
    private readonly ObservableCollection<LyricItem> _lyrics = new();
    private readonly List<StyleOption> _styles;
    private readonly List<VisualOption> _visuals;
    private Track? _track;
    private IReadOnlyList<LyricLine>? _syncedLines;
    private int _currentLine = -1;
    private bool _loading = true;

    public NowPlayingPanel()
    {
        InitializeComponent();
        _styles = new List<StyleOption>
        {
            new(CoverStyle.Vinyl, Loc.Get("style_vinyl")),
            new(CoverStyle.Cassette, Loc.Get("style_cassette")),
            new(CoverStyle.Cover, Loc.Get("style_cover")),
        };
        _visuals = new List<VisualOption>
        {
            new(VisualMode.Bars, Loc.Get("visual_bars")),
            new(VisualMode.Mirror, Loc.Get("visual_mirror")),
            new(VisualMode.Circle, Loc.Get("visual_circle")),
            new(VisualMode.Wave, Loc.Get("visual_wave")),
            new(VisualMode.Particles, Loc.Get("visual_particles")),
            new(VisualMode.Vu, Loc.Get("visual_vu")),
        };

        HeaderText.Text = Loc.Get("now_playing");
        StyleLabel.Text = Loc.Get("style");
        VisualLabel.Text = Loc.Get("visualizer");
        LyricsHeader.Text = Loc.Get("lyrics");
        LyricsList.ItemsSource = _lyrics;
        StyleBox.ItemsSource = _styles;
        VisualBox.ItemsSource = _visuals;

        AppSettings settings = App.Services.Settings;
        StyleOption style = _styles.FirstOrDefault(s => s.Style.ToString() == settings.CoverStyleName) ?? _styles[0];
        VisualOption visual = _visuals.FirstOrDefault(v => v.Mode.ToString() == settings.VisualModeName) ?? _visuals[0];
        StyleBox.SelectedItem = style;
        VisualBox.SelectedItem = visual;
        Cover.SetStyle(style.Style);
        Spectrum.SetMode(visual.Mode);
        _loading = false;
    }

    private void StyleBox_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || StyleBox.SelectedItem is not StyleOption option) return;
        Cover.SetStyle(option.Style);
        App.Services.Settings.CoverStyleName = option.Style.ToString();
    }

    private void VisualBox_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || VisualBox.SelectedItem is not VisualOption option) return;
        Spectrum.SetMode(option.Mode);
        App.Services.Settings.VisualModeName = option.Mode.ToString();
    }

    public void SetTrack(Track? track)
    {
        if (ReferenceEquals(track, _track)) return;
        _track = track;

        Cover.SetArt(ArtConverter.Load(track?.ArtPath));
        TitleText.Text = track?.DisplayTitle ?? Loc.Get("nothing_playing");
        ArtistText.Text = track == null ? "" : $"{track.DisplayArtist} · {track.DisplayAlbum}";

        _lyrics.Clear();
        _currentLine = -1;
        _syncedLines = null;
        if (track == null) return;

        Lyrics? parsed = track.Lyrics == null ? null : LrcParser.Parse(track.Lyrics);
        if (parsed == null)
        {
            _lyrics.Add(new LyricItem(0, Loc.Get("no_lyrics")));
            return;
        }

        foreach (LyricLine line in parsed.Lines) _lyrics.Add(new LyricItem(line.TimeMs, line.Text));
        if (parsed.Synced) _syncedLines = parsed.Lines;
    }

    /// <summary>Called by the main window about four times a second while the panel is open.</summary>
    public void OnTick(TimeSpan position, TimeSpan elapsed, SpectrumAnalyzer? spectrum, bool playing)
    {
        if (playing) Cover.Turn(elapsed.TotalSeconds * 40); // about one turn every nine seconds

        if (_syncedLines is { Count: > 0 } lines)
        {
            int index = LrcParser.IndexAt(lines, (long)position.TotalMilliseconds);
            if (index != _currentLine)
            {
                if (_currentLine >= 0 && _currentLine < _lyrics.Count) _lyrics[_currentLine].IsCurrent = false;
                _currentLine = index;
                if (index >= 0 && index < _lyrics.Count)
                {
                    _lyrics[index].IsCurrent = true;
                    LyricsList.ScrollIntoView(_lyrics[index]);
                }
            }
        }

        if (spectrum == null) return;
        spectrum.Update((float)elapsed.TotalSeconds);
        Spectrum.SetLevels(spectrum.Levels);
        Spectrum.Advance((float)elapsed.TotalSeconds);
        Spectrum.InvalidateVisual();
    }
}
