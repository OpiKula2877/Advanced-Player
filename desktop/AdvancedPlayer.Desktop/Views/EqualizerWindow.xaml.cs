using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using AdvancedPlayer.Desktop.Audio;

namespace AdvancedPlayer.Desktop.Views;

public sealed class PresetItem
{
    public PresetItem(string id, string name, float[]? bands)
    {
        Id = id;
        Name = name;
        Bands = bands;
    }

    public string Id { get; }

    public string Name { get; }

    /// <summary>Band gains in dB. Null for "custom", which keeps whatever the sliders say.</summary>
    public float[]? Bands { get; }
}

/// <summary>10-band equalizer, bass boost and presets. Changes apply at once to playback and are saved.</summary>
public partial class EqualizerWindow : Window
{
    // Presets copied from EqualizerScreen.kt in the Android app.
    private static readonly (string Id, string NameKey, float[]? Bands)[] PresetDefinitions =
    {
        ("flat", "preset_flat", new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }),
        ("rock", "preset_rock", new float[] { 5, 4, 3, 1, -1, -1, 1, 3, 4, 5 }),
        ("pop", "preset_pop", new float[] { -1, 1, 3, 4, 3, 1, -1, -1, 1, 2 }),
        ("jazz", "preset_jazz", new float[] { 3, 2, 1, 2, -1, -1, 0, 1, 2, 3 }),
        ("classical", "preset_classical", new float[] { 4, 3, 2, 1, -1, -1, 0, 2, 3, 4 }),
        ("bass", "preset_bass", new float[] { 7, 6, 5, 3, 1, 0, 0, 0, 0, 0 }),
        ("vocal", "preset_vocal", new float[] { -2, -2, -1, 1, 3, 4, 4, 2, 0, -1 }),
        ("electronic", "preset_electronic", new float[] { 5, 4, 1, 0, -2, 1, 0, 2, 4, 5 }),
        ("custom", "preset_custom", null),
    };

    private readonly AppServices _services;
    private readonly Slider[] _sliders = new Slider[EqProcessor.BandCount];
    private readonly List<PresetItem> _presets;
    private bool _loading = true;

    public EqualizerWindow(AppServices services)
    {
        InitializeComponent();
        _services = services;

        for (int i = 0; i < _sliders.Length; i++)
        {
            BandGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            var slider = new Slider
            {
                Orientation = Orientation.Vertical,
                Minimum = -12,
                Maximum = 12,
                TickFrequency = 1,
                Height = 240,
                HorizontalAlignment = HorizontalAlignment.Center,
            };
            slider.ValueChanged += (_, _) =>
            {
                if (!_loading) OnBandChanged();
            };
            _sliders[i] = slider;

            var label = new TextBlock
            {
                Text = FrequencyLabel(EqProcessor.Frequencies[i]),
                HorizontalAlignment = HorizontalAlignment.Center,
                FontSize = 11,
                Foreground = (Brush)FindResource("MutedBrush"),
                Margin = new Thickness(0, 6, 0, 0),
            };
            var column = new StackPanel();
            column.Children.Add(slider);
            column.Children.Add(label);
            Grid.SetColumn(column, i);
            BandGrid.Children.Add(column);
        }

        EqSettings settings = services.Settings.Equalizer;
        EnabledBox.IsChecked = settings.Enabled;
        BassSlider.Value = settings.BassBoost * 100;
        for (int i = 0; i < _sliders.Length; i++) _sliders[i].Value = settings.BandsDb[i];
        UpdateBassText();

        _presets = PresetDefinitions.Select(p => new PresetItem(p.Id, Loc.Get(p.NameKey), p.Bands)).ToList();
        PresetBox.ItemsSource = _presets;
        PresetBox.SelectedItem = _presets.FirstOrDefault(p => p.Id == (services.Settings.EqPreset ?? "custom")) ?? _presets[^1];
        _loading = false;
    }

    private void Preset_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || PresetBox.SelectedItem is not PresetItem { Bands: { } bands }) return;
        _loading = true;
        for (int i = 0; i < _sliders.Length; i++) _sliders[i].Value = bands[i];
        _loading = false;
        Apply();
    }

    /// <summary>Moving a band by hand turns the preset into "custom".</summary>
    private void OnBandChanged()
    {
        _loading = true;
        PresetBox.SelectedItem = _presets[^1];
        _loading = false;
        Apply();
    }

    private void Enabled_Click(object sender, RoutedEventArgs e) => Apply();

    private void Bass_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_loading) return;
        UpdateBassText();
        Apply();
    }

    private void Reset_Click(object sender, RoutedEventArgs e)
    {
        _loading = true;
        foreach (Slider slider in _sliders) slider.Value = 0;
        BassSlider.Value = 0;
        PresetBox.SelectedItem = _presets[0];
        _loading = false;
        UpdateBassText();
        Apply();
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void Apply()
    {
        var bands = _sliders.Select(s => (float)s.Value).ToArray();
        var settings = new EqSettings(EnabledBox.IsChecked == true, bands, (float)(BassSlider.Value / 100.0));
        _services.Settings.Equalizer = settings;
        _services.Settings.EqPreset = (PresetBox.SelectedItem as PresetItem)?.Id ?? "custom";
        _services.Engine.SetEqualizer(settings);
    }

    private void UpdateBassText() => BassText.Text = $"{(int)BassSlider.Value}%";

    private static string FrequencyLabel(double hz) => hz >= 1000 ? $"{hz / 1000:0}k" : $"{hz:0}";
}
