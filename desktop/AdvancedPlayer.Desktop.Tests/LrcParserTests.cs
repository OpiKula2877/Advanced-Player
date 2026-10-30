using AdvancedPlayer.Desktop.Library;

namespace AdvancedPlayer.Desktop.Tests;

public class LrcParserTests
{
    [Fact]
    public void SyncedLinesAreSortedAndTheOffsetIsApplied()
    {
        Lyrics? lyrics = LrcParser.Parse("[offset:+500]\n[00:10.00]second\n[00:05.00]first\n");

        Assert.NotNull(lyrics);
        Assert.True(lyrics!.Synced);
        Assert.Equal(new[] { "first", "second" }, lyrics.Lines.Select(l => l.Text));
        Assert.Equal(new long[] { 4500, 9500 }, lyrics.Lines.Select(l => l.TimeMs));
    }

    [Fact]
    public void PlainTextHasNoTimes()
    {
        Lyrics? lyrics = LrcParser.Parse("line one\n\nline two");

        Assert.NotNull(lyrics);
        Assert.False(lyrics!.Synced);
        Assert.Equal(new[] { "line one", "line two" }, lyrics.Lines.Select(l => l.Text));
    }

    [Fact]
    public void IndexAtFindsTheLineBeingSung()
    {
        var lines = new List<LyricLine> { new(1000, "a"), new(2000, "b"), new(3000, "c") };

        Assert.Equal(-1, LrcParser.IndexAt(lines, 500));
        Assert.Equal(0, LrcParser.IndexAt(lines, 1000));
        Assert.Equal(1, LrcParser.IndexAt(lines, 2500));
        Assert.Equal(2, LrcParser.IndexAt(lines, 99_999));
    }
}
