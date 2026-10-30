using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace AdvancedPlayer.Desktop;

/// <summary>Turns a cached artwork file path into a small decoded bitmap. Null or missing files give null (a placeholder is shown instead).</summary>
public sealed class ArtConverter : IValueConverter
{
    private static readonly Dictionary<string, ImageSource> Cache = new(StringComparer.OrdinalIgnoreCase);

    public object? Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is not string path || !File.Exists(path)) return null;
        if (Cache.TryGetValue(path, out var cached)) return cached;

        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.DecodePixelWidth = 160;
        bitmap.UriSource = new Uri(path, UriKind.Absolute);
        bitmap.EndInit();
        bitmap.Freeze();
        Cache[path] = bitmap;
        return bitmap;
    }

    /// <summary>Artwork for a cached file path, or null when there is none.</summary>
    public static ImageSource? Load(string? path) =>
        path == null ? null : new ArtConverter().Convert(path, typeof(ImageSource), null!, CultureInfo.InvariantCulture) as ImageSource;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
