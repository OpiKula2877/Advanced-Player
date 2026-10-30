using System.Globalization;
using System.Windows.Data;
using System.Windows.Media.Imaging;

namespace AdvancedPlayer.Desktop;

/// <summary>Turns a picture URL (for example a Spotify album image) into a small bitmap. Empty values give null.</summary>
public sealed class UrlArtConverter : IValueConverter
{
    public object? Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is not string url || url.Length == 0) return null;

        // The image downloads in the background, so the UI thread is not blocked.
        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.DecodePixelWidth = 96;
        bitmap.UriSource = new Uri(url, UriKind.Absolute);
        bitmap.EndInit();
        return bitmap;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
