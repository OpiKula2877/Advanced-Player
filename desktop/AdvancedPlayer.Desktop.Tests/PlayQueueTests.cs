using AdvancedPlayer.Desktop.Audio;
using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Tests;

public class PlayQueueTests
{
    private static List<Track> Tracks(params string[] titles) =>
        titles.Select(t => new Track { Title = t, Key = t }).ToList();

    [Fact]
    public void NextAndPreviousFollowTheListOrder()
    {
        var queue = new PlayQueue();
        queue.Set(Tracks("a", "b", "c"), 0, shuffle: false);

        Assert.Equal("a", queue.Current!.Title);
        Assert.Equal("b", queue.Next(userRequested: true)!.Title);
        Assert.Equal("c", queue.Next(userRequested: true)!.Title);
        Assert.Null(queue.Next(userRequested: true));
        Assert.Equal("b", queue.Previous()!.Title);
    }

    [Fact]
    public void RepeatAllWrapsToTheStart()
    {
        var queue = new PlayQueue { Repeat = RepeatMode.All };
        queue.Set(Tracks("a", "b"), 1, shuffle: false);

        Assert.Equal("b", queue.Current!.Title);
        Assert.Equal("a", queue.Next(userRequested: true)!.Title);
    }

    [Fact]
    public void RepeatOneReplaysOnNaturalEndButAUserPressMovesOn()
    {
        var queue = new PlayQueue { Repeat = RepeatMode.One };
        queue.Set(Tracks("a", "b"), 0, shuffle: false);

        Assert.Equal("a", queue.Next(userRequested: false)!.Title);
        Assert.Equal("b", queue.Next(userRequested: true)!.Title);
    }

    [Fact]
    public void EnqueueAndPlayNextPutSongsInTheRightPlaces()
    {
        var queue = new PlayQueue();
        queue.Set(Tracks("a", "b"), 0, shuffle: false);
        queue.Enqueue(new Track { Title = "end" });
        queue.PlayNext(new Track { Title = "soon" });

        Assert.Equal(new[] { "soon", "b", "end" }, queue.Upcoming.Select(t => t.Title));
    }

    [Fact]
    public void ClearUpcomingKeepsOnlyTheCurrentSong()
    {
        var queue = new PlayQueue();
        queue.Set(Tracks("a", "b", "c"), 1, shuffle: false);

        queue.ClearUpcoming();

        Assert.Equal("b", queue.Current!.Title);
        Assert.Empty(queue.Upcoming);
    }

    [Fact]
    public void RemoveAndJumpWorkOnUpcomingSongs()
    {
        var queue = new PlayQueue();
        queue.Set(Tracks("a", "b", "c", "d"), 0, shuffle: false);

        queue.RemoveUpcoming(0);
        Assert.Equal(new[] { "c", "d" }, queue.Upcoming.Select(t => t.Title));

        Assert.True(queue.JumpToUpcoming(1));
        Assert.Equal("d", queue.Current!.Title);
    }

    [Fact]
    public void ShuffleStartsWithTheChosenSongAndKeepsEverySongOnce()
    {
        var queue = new PlayQueue();
        queue.Set(Tracks("a", "b", "c", "d", "e"), 2, shuffle: true);

        Assert.Equal("c", queue.Current!.Title);
        var titles = new List<string> { queue.Current!.Title };
        titles.AddRange(queue.Upcoming.Select(t => t.Title));
        Assert.Equal(new[] { "a", "b", "c", "d", "e" }, titles.OrderBy(t => t));
    }
}
