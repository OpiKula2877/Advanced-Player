using AdvancedPlayer.Desktop.Models;

namespace AdvancedPlayer.Desktop.Audio;

public enum RepeatMode { Off, All, One }

/// <summary>
/// The list being played, with shuffle and repeat. Items are kept in their list order and
/// <see cref="_order"/> decides the order in which they are played.
/// </summary>
public sealed class PlayQueue
{
    private readonly List<Track> _items = new();
    private List<int> _order = new();
    private int _position = -1;
    private readonly Random _random = new();

    public bool Shuffle { get; private set; }

    public RepeatMode Repeat { get; set; } = RepeatMode.Off;

    public Track? Current => _position >= 0 && _position < _order.Count ? _items[_order[_position]] : null;

    public int Count => _items.Count;

    /// <summary>Tracks that play after the current one, in play order.</summary>
    public IEnumerable<Track> Upcoming =>
        _position < 0 ? Enumerable.Empty<Track>() : _order.Skip(_position + 1).Select(i => _items[i]);

    /// <summary>Replaces the queue with <paramref name="tracks"/> and starts at <paramref name="startIndex"/>.</summary>
    public void Set(IReadOnlyList<Track> tracks, int startIndex, bool shuffle)
    {
        _items.Clear();
        _items.AddRange(tracks);
        Shuffle = shuffle;
        if (_items.Count == 0)
        {
            _order = new List<int>();
            _position = -1;
            return;
        }

        _order = Enumerable.Range(0, _items.Count).ToList();
        if (shuffle)
        {
            Shuffled(startIndex);
            _position = 0;
        }
        else
        {
            _position = Math.Clamp(startIndex, 0, _items.Count - 1);
        }
    }

    /// <summary>Turns shuffle on or off and keeps the current track playing.</summary>
    public void SetShuffle(bool shuffle)
    {
        if (shuffle == Shuffle) return;
        Shuffle = shuffle;
        if (_items.Count == 0) return;
        int current = _position >= 0 ? _order[_position] : 0;
        if (shuffle)
        {
            Shuffled(current);
            _position = 0;
        }
        else
        {
            _order = Enumerable.Range(0, _items.Count).ToList();
            _position = current;
        }
    }

    /// <summary>Adds a track at the end of the queue.</summary>
    public void Enqueue(Track track)
    {
        _items.Add(track);
        _order.Add(_items.Count - 1);
    }

    /// <summary>Inserts a track right after the current one.</summary>
    public void PlayNext(Track track)
    {
        _items.Add(track);
        int insertAt = _position < 0 ? _order.Count : _position + 1;
        _order.Insert(insertAt, _items.Count - 1);
    }

    /// <summary>Moves to the next track. Natural track ends (<paramref name="userRequested"/> false) replay the track when repeat is One.</summary>
    public Track? Next(bool userRequested)
    {
        if (_order.Count == 0) return null;
        if (!userRequested && Repeat == RepeatMode.One) return Current;

        if (_position + 1 < _order.Count)
        {
            _position++;
            return Current;
        }

        if (Repeat == RepeatMode.All)
        {
            if (Shuffle) Shuffled(-1);
            _position = 0;
            return Current;
        }

        return null;
    }

    /// <summary>Moves to the previous track, or wraps to the last one when repeat is All.</summary>
    public Track? Previous()
    {
        if (_order.Count == 0) return null;
        if (_position > 0)
        {
            _position--;
        }
        else if (Repeat == RepeatMode.All)
        {
            _position = _order.Count - 1;
        }

        return Current;
    }

    /// <summary>Drops everything after the current song. The current song keeps playing.</summary>
    public void ClearUpcoming()
    {
        Track? current = Current;
        _items.Clear();
        if (current == null)
        {
            _order = new List<int>();
            _position = -1;
            return;
        }

        _items.Add(current);
        _order = new List<int> { 0 };
        _position = 0;
    }

    /// <summary>Removes the upcoming song at <paramref name="upcomingIndex"/> (an index into <see cref="Upcoming"/>).</summary>
    public void RemoveUpcoming(int upcomingIndex)
    {
        int orderIndex = _position + 1 + upcomingIndex;
        if (_position < 0 || orderIndex < 0 || orderIndex >= _order.Count) return;

        // Rebuild the list in play order so that the remaining songs keep their order.
        var playOrder = _order.Select(i => _items[i]).ToList();
        playOrder.RemoveAt(orderIndex);
        _items.Clear();
        _items.AddRange(playOrder);
        _order = Enumerable.Range(0, _items.Count).ToList();
    }

    /// <summary>Moves to the upcoming song at <paramref name="upcomingIndex"/>. Returns false when there is no such song.</summary>
    public bool JumpToUpcoming(int upcomingIndex)
    {
        int orderIndex = _position + 1 + upcomingIndex;
        if (_position < 0 || orderIndex < 0 || orderIndex >= _order.Count) return false;
        _position = orderIndex;
        return true;
    }

    /// <summary>Shuffles the play order. If <paramref name="first"/> is an item index, it is played first.</summary>
    private void Shuffled(int first)
    {
        var order = Enumerable.Range(0, _items.Count).OrderBy(_ => _random.Next()).ToList();
        if (first >= 0)
        {
            order.Remove(first);
            order.Insert(0, first);
        }

        _order = order;
    }
}
