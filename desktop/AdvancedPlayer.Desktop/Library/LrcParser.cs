using System.Text.RegularExpressions;

namespace AdvancedPlayer.Desktop.Library;

public sealed record LyricLine(long TimeMs, string Text);

/// <summary>Parsed lyrics. <see cref="Synced"/> is false for plain text, which has no timestamps.</summary>
public sealed record Lyrics(IReadOnlyList<LyricLine> Lines, bool Synced);

/// <summary>Port of LrcParser in data/Lyrics.kt: [mm:ss.xx] stamps, optional [offset:] tag, plain-text fallback.</summary>
public static class LrcParser
{
    private static readonly Regex StampRegex = new(@"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]", RegexOptions.Compiled);
    private static readonly Regex OffsetRegex = new(@"\[offset:\s*([+-]?\d+)\]", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    public static Lyrics? Parse(string text)
    {
        long offset = OffsetRegex.Match(text) is { Success: true } om && long.TryParse(om.Groups[1].Value, out long o) ? o : 0;
        var synced = new List<LyricLine>();
        var plain = new List<LyricLine>();

        foreach (string raw in text.Split('\n'))
        {
            string line = raw.TrimEnd('\r', ' ', '\t');
            MatchCollection stamps = StampRegex.Matches(line);
            if (stamps.Count == 0)
            {
                string content = line.Trim();
                if (content.Length > 0 && !content.StartsWith('[')) plain.Add(new LyricLine(0, content)); // skip [ar:] style tags
                continue;
            }

            Match last = stamps[^1];
            string lyric = line[(last.Index + last.Length)..].Trim();
            foreach (Match stamp in stamps)
            {
                long minutes = long.Parse(stamp.Groups[1].Value);
                long seconds = long.Parse(stamp.Groups[2].Value);
                string fraction = stamp.Groups[3].Value;
                long millis = fraction.Length switch
                {
                    0 => 0,
                    1 => long.Parse(fraction) * 100,
                    2 => long.Parse(fraction) * 10,
                    _ => long.Parse(fraction[..3]),
                };
                long time = Math.Max(0, minutes * 60_000 + seconds * 1000 + millis - offset);
                synced.Add(new LyricLine(time, lyric));
            }
        }

        if (synced.Count > 0) return new Lyrics(synced.OrderBy(l => l.TimeMs).ToList(), true);
        if (plain.Count > 0) return new Lyrics(plain, false);
        return null;
    }

    /// <summary>Index of the line to highlight at <paramref name="positionMs"/>, or -1 before the first line.</summary>
    public static int IndexAt(IReadOnlyList<LyricLine> lines, long positionMs)
    {
        int lo = 0, hi = lines.Count - 1, answer = -1;
        while (lo <= hi)
        {
            int mid = (lo + hi) >>> 1;
            if (lines[mid].TimeMs <= positionMs)
            {
                answer = mid;
                lo = mid + 1;
            }
            else
            {
                hi = mid - 1;
            }
        }

        return answer;
    }
}
