using System.Windows;
using System.Windows.Media;
using AdvancedPlayer.Desktop.Audio;

namespace AdvancedPlayer.Desktop.Views;

public enum CoverStyle { Vinyl, Cassette, Cover }

public enum VisualMode { Bars, Mirror, Circle, Wave, Particles, Vu }

/// <summary>Freezes WPF brushes and pens so that they can be shared between frames.</summary>
internal static class Frozen
{
    public static T Of<T>(T value) where T : Freezable
    {
        value.Freeze();
        return value;
    }
}

/// <summary>
/// The artwork drawn as a spinning vinyl record, a rotating cassette or a plain cover.
/// Everything is drawn in OnRender, so the panel only changes the angle and the artwork.
/// </summary>
public sealed class CoverVisual : FrameworkElement
{
    private static readonly Brush RecordBrush = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x16, 0x16, 0x18)));
    private static readonly Pen GroovePen = Frozen.Of(new Pen(Frozen.Of(new SolidColorBrush(Color.FromArgb(0x30, 0xFF, 0xFF, 0xFF))), 1));
    private static readonly Brush HoleBrush = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x0E, 0x0E, 0x10)));
    private static readonly Brush PlaceholderBrush = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x2A, 0x2A, 0x32)));
    private static readonly Brush CassetteBody = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x2B, 0x2B, 0x31)));
    private static readonly Brush CassetteLabel = Frozen.Of(new SolidColorBrush(Color.FromRgb(0xE9, 0xE4, 0xD8)));
    private static readonly Brush CassetteWindow = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x0A, 0x0A, 0x0C)));
    private static readonly Brush ReelBrush = Frozen.Of(new SolidColorBrush(Color.FromRgb(0xD8, 0xD8, 0xDE)));
    private static readonly Pen SpokePen = Frozen.Of(new Pen(Frozen.Of(new SolidColorBrush(Color.FromRgb(0x3A, 0x3A, 0x44))), 2.5));

    private ImageSource? _art;

    public CoverStyle Style { get; private set; } = CoverStyle.Vinyl;

    /// <summary>Rotation in degrees. The panel advances it while music plays.</summary>
    public double Angle { get; private set; }

    public void SetStyle(CoverStyle style)
    {
        Style = style;
        InvalidateVisual();
    }

    public void SetArt(ImageSource? art)
    {
        _art = art;
        InvalidateVisual();
    }

    public void Turn(double degrees)
    {
        Angle = (Angle + degrees) % 360;
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext dc)
    {
        double width = ActualWidth;
        double height = ActualHeight;
        if (width <= 0 || height <= 0) return;

        var center = new Point(width / 2, height / 2);
        switch (Style)
        {
            case CoverStyle.Vinyl:
                DrawVinyl(dc, center, Math.Min(width, height) / 2 - 4);
                break;
            case CoverStyle.Cassette:
                DrawCassette(dc, center, width, height);
                break;
            default:
                {
                    double side = Math.Min(width, height) - 8;
                    DrawCover(dc, new Rect(center.X - side / 2, center.Y - side / 2, side, side));
                    break;
                }
        }
    }

    private void DrawVinyl(DrawingContext dc, Point center, double radius)
    {
        dc.DrawEllipse(RecordBrush, null, center, radius, radius);
        for (double groove = radius * 0.45; groove < radius * 0.96; groove += 3.5)
            dc.DrawEllipse(null, GroovePen, center, groove, groove);

        // The label turns with the record; the clip keeps the artwork round.
        double labelRadius = radius * 0.38;
        dc.PushTransform(new RotateTransform(Angle, center.X, center.Y));
        dc.PushClip(new EllipseGeometry(center, labelRadius, labelRadius));
        if (_art != null)
            dc.DrawImage(_art, new Rect(center.X - labelRadius, center.Y - labelRadius, labelRadius * 2, labelRadius * 2));
        else
            dc.DrawEllipse(PlaceholderBrush, null, center, labelRadius, labelRadius);
        dc.Pop();
        dc.Pop();

        double hole = Math.Max(3, radius * 0.04);
        dc.DrawEllipse(HoleBrush, null, center, hole, hole);
    }

    private void DrawCassette(DrawingContext dc, Point center, double width, double height)
    {
        double bodyWidth = Math.Min(width - 8, (height - 8) * 1.6);
        double bodyHeight = bodyWidth * 0.62;
        var body = new Rect(center.X - bodyWidth / 2, center.Y - bodyHeight / 2, bodyWidth, bodyHeight);
        dc.DrawRoundedRectangle(CassetteBody, null, body, 10, 10);

        var label = new Rect(body.X + bodyWidth * 0.12, body.Y + bodyHeight * 0.08, bodyWidth * 0.76, bodyHeight * 0.36);
        dc.DrawRoundedRectangle(CassetteLabel, null, label, 4, 4);
        if (_art != null)
        {
            dc.PushClip(new RectangleGeometry(label, 4, 4));
            dc.DrawImage(_art, label);
            dc.Pop();
        }

        var window = new Rect(body.X + bodyWidth * 0.2, body.Y + bodyHeight * 0.5, bodyWidth * 0.6, bodyHeight * 0.4);
        dc.DrawRoundedRectangle(CassetteWindow, null, window, 8, 8);
        double reelRadius = bodyHeight * 0.15;
        DrawReel(dc, new Point(window.X + window.Width * 0.28, window.Y + window.Height * 0.5), reelRadius);
        DrawReel(dc, new Point(window.X + window.Width * 0.72, window.Y + window.Height * 0.5), reelRadius);
    }

    private void DrawReel(DrawingContext dc, Point center, double radius)
    {
        dc.DrawEllipse(ReelBrush, null, center, radius, radius);
        dc.PushTransform(new RotateTransform(Angle, center.X, center.Y));
        for (int spoke = 0; spoke < 3; spoke++)
        {
            double angle = spoke * 2 * Math.PI / 3;
            dc.DrawLine(SpokePen, center, new Point(center.X + Math.Cos(angle) * radius * 0.9, center.Y + Math.Sin(angle) * radius * 0.9));
        }

        dc.Pop();
    }

    private void DrawCover(DrawingContext dc, Rect area)
    {
        dc.PushClip(new RectangleGeometry(area, 10, 10));
        if (_art != null) dc.DrawImage(_art, area);
        else dc.DrawRectangle(PlaceholderBrush, null, area);
        dc.Pop();
    }
}

/// <summary>The spectrum in one of six modes: bars, mirror, circle, wave, particles and a VU meter.</summary>
public sealed class SpectrumVisual : FrameworkElement
{
    private const int Bands = SpectrumAnalyzer.BandCount;
    private static readonly Brush AccentBrush = Frozen.Of(new SolidColorBrush(Color.FromRgb(0x5E, 0xEA, 0xD4)));
    private static readonly Brush FaintBrush = Frozen.Of(new SolidColorBrush(Color.FromArgb(0x40, 0x5E, 0xEA, 0xD4)));
    private static readonly Pen AccentPen = Frozen.Of(new Pen(AccentBrush, 2));
    private static readonly Pen WavePen = Frozen.Of(new Pen(AccentBrush, 2.5));

    private readonly Random _random = new();
    private readonly List<Particle> _particles = new();
    private float[] _levels = new float[Bands];
    private float _left;
    private float _right;
    private float _peakLeft;
    private float _peakRight;

    public VisualMode Mode { get; private set; } = VisualMode.Bars;

    public void SetMode(VisualMode mode)
    {
        Mode = mode;
        _particles.Clear();
        InvalidateVisual();
    }

    /// <summary>The band levels from <see cref="SpectrumAnalyzer.Levels"/>, which the analyzer updates in place.</summary>
    public void SetLevels(float[] levels) => _levels = levels;

    /// <summary>Moves particles and the VU needles forward by <paramref name="dt"/> seconds.</summary>
    public void Advance(float dt)
    {
        _left = Average(0, Bands / 2);
        _right = Average(Bands / 2, Bands);
        _peakLeft = Math.Max(_left, _peakLeft - 0.5f * dt);
        _peakRight = Math.Max(_right, _peakRight - 0.5f * dt);

        if (Mode != VisualMode.Particles || ActualWidth <= 0) return;

        double slot = ActualWidth / Bands;
        for (int b = 0; b < Bands && _particles.Count < 160; b++)
        {
            float level = _levels[b];
            if (level < 0.3f || _random.NextDouble() > level * 0.6) continue;
            _particles.Add(new Particle
            {
                X = (b + 0.5) * slot,
                Y = ActualHeight,
                Vx = (_random.NextDouble() - 0.5) * 60,
                Vy = -(140 + level * 300),
                Life = 1,
            });
        }

        foreach (Particle p in _particles)
        {
            p.X += p.Vx * dt;
            p.Y += p.Vy * dt;
            p.Vy += 180 * dt;
            p.Life -= dt * 0.9;
        }

        _particles.RemoveAll(p => p.Life <= 0 || p.Y > ActualHeight + 10);
    }

    protected override void OnRender(DrawingContext dc)
    {
        double width = ActualWidth;
        double height = ActualHeight;
        if (width <= 0 || height <= 0) return;

        switch (Mode)
        {
            case VisualMode.Mirror:
                DrawBars(dc, width, height, mirror: true);
                break;
            case VisualMode.Circle:
                DrawCircle(dc, width, height);
                break;
            case VisualMode.Wave:
                DrawWave(dc, width, height);
                break;
            case VisualMode.Particles:
                DrawParticles(dc);
                break;
            case VisualMode.Vu:
                DrawVu(dc, width, height);
                break;
            default:
                DrawBars(dc, width, height, mirror: false);
                break;
        }
    }

    private float Average(int from, int to)
    {
        float sum = 0;
        for (int i = from; i < to; i++) sum += _levels[i];
        return sum / (to - from);
    }

    private void DrawBars(DrawingContext dc, double width, double height, bool mirror)
    {
        double slot = width / Bands;
        double barWidth = slot * 0.7;
        for (int b = 0; b < Bands; b++)
        {
            double barHeight = Math.Max(2, _levels[b] * (mirror ? height / 2 : height));
            double x = b * slot + (slot - barWidth) / 2;
            var rect = mirror
                ? new Rect(x, height / 2 - barHeight / 2, barWidth, barHeight)
                : new Rect(x, height - barHeight, barWidth, barHeight);
            dc.DrawRoundedRectangle(AccentBrush, null, rect, 1.5, 1.5);
        }
    }

    private void DrawCircle(DrawingContext dc, double width, double height)
    {
        var center = new Point(width / 2, height / 2);
        double inner = Math.Min(width, height) * 0.2;
        double outer = Math.Min(width, height) * 0.3;
        for (int b = 0; b < Bands; b++)
        {
            double angle = -Math.PI / 2 + b * 2 * Math.PI / Bands;
            double length = Math.Max(2, _levels[b] * outer);
            var from = new Point(center.X + Math.Cos(angle) * inner, center.Y + Math.Sin(angle) * inner);
            var to = new Point(center.X + Math.Cos(angle) * (inner + length), center.Y + Math.Sin(angle) * (inner + length));
            dc.DrawLine(AccentPen, from, to);
        }
    }

    private void DrawWave(DrawingContext dc, double width, double height)
    {
        var figure = new PathFigure { StartPoint = new Point(0, height / 2) };
        for (int b = 0; b < Bands; b++)
        {
            double x = (b + 0.5) * width / Bands;
            double y = height / 2 - _levels[b] * height * 0.45;
            figure.Segments.Add(new LineSegment(new Point(x, y), true));
        }

        figure.Segments.Add(new LineSegment(new Point(width, height / 2), true));
        dc.DrawGeometry(null, WavePen, new PathGeometry(new[] { figure }));
    }

    private void DrawParticles(DrawingContext dc)
    {
        foreach (Particle p in _particles)
        {
            byte alpha = (byte)Math.Clamp(p.Life * 255, 0, 255);
            dc.DrawEllipse(new SolidColorBrush(Color.FromArgb(alpha, 0x5E, 0xEA, 0xD4)), null, new Point(p.X, p.Y), 3, 3);
        }
    }

    private void DrawVu(DrawingContext dc, double width, double height)
    {
        DrawMeter(dc, new Rect(width * 0.18, 0, width * 0.26, height), _left, _peakLeft);
        DrawMeter(dc, new Rect(width * 0.56, 0, width * 0.26, height), _right, _peakRight);
    }

    private static void DrawMeter(DrawingContext dc, Rect area, float level, float peak)
    {
        dc.DrawRoundedRectangle(FaintBrush, null, area, 6, 6);
        double fill = Math.Clamp(level, 0f, 1f) * area.Height;
        dc.DrawRoundedRectangle(AccentBrush, null, new Rect(area.X, area.Bottom - fill, area.Width, fill), 6, 6);
        double peakY = area.Bottom - Math.Clamp(peak, 0f, 1f) * area.Height;
        dc.DrawLine(AccentPen, new Point(area.X, peakY), new Point(area.Right, peakY));
    }

    private sealed class Particle
    {
        public double X;
        public double Y;
        public double Vx;
        public double Vy;
        public double Life;
    }
}
