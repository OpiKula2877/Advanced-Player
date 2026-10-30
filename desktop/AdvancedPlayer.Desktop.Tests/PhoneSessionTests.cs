using System.Net;
using System.Net.Sockets;
using System.Text;
using AdvancedPlayer.Desktop.Phone;

namespace AdvancedPlayer.Desktop.Tests;

/// <summary>
/// Runs the PC side of the protocol against a small fake phone over loopback TCP. The fake phone answers
/// the same way as PcLinkServer.kt in the Android app.
/// </summary>
public class PhoneSessionTests
{
    private const string SongsJson =
        "[{\"id\":7,\"title\":\"Song\",\"artist\":\"Band\",\"album\":\"Album\",\"genre\":\"Rock\",\"year\":2001," +
        "\"trackNo\":3,\"durationMs\":215000,\"size\":5,\"name\":\"song.mp3\",\"mime\":\"audio/mpeg\",\"dateAdded\":1700000000000}," +
        "{\"id\":8,\"title\":\"Other\",\"artist\":\"\",\"album\":\"\",\"genre\":\"\",\"year\":0,\"trackNo\":0," +
        "\"durationMs\":1000,\"size\":0,\"name\":\"other.flac\",\"mime\":\"audio/flac\",\"dateAdded\":0}]";

    [Fact]
    public async Task ReadsSongsFilesArtAndLyricsFromThePhone()
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((IPEndPoint)listener.LocalEndpoint).Port;

        Task<TcpClient> accepted = listener.AcceptTcpClientAsync();
        using var pcSide = new TcpClient();
        await pcSide.ConnectAsync(IPAddress.Loopback, port);
        using TcpClient phoneSide = await accepted;

        Task fakePhone = RunFakePhoneAsync(phoneSide.GetStream());
        await using var session = new PhoneSession(pcSide.GetStream(), pcSide.GetStream());

        Assert.Equal(2, await session.HelloAsync());

        List<PhoneSong> songs = await session.ListAsync();
        Assert.Equal(2, songs.Count);
        Assert.Equal("Song", songs[0].Title);
        Assert.Equal(3, songs[0].TrackNo);
        Assert.Equal("other.flac", songs[1].Name);

        using var file = new MemoryStream();
        await session.DownloadAsync(7, file);
        Assert.Equal(new byte[] { 1, 2, 3, 4, 5 }, file.ToArray());

        Assert.Null(await session.ArtAsync(7));
        Assert.Equal("[00:01.00]hi", await session.LyricsAsync(7));

        await session.DisposeAsync();
        await fakePhone;
    }

    private static async Task RunFakePhoneAsync(Stream stream)
    {
        using var reader = new StreamReader(stream, Encoding.ASCII, detectEncodingFromByteOrderMarks: false, bufferSize: 1024, leaveOpen: true);
        while (true)
        {
            string? command = await reader.ReadLineAsync();
            if (command == null || command == "BYE") return;

            byte[] reply = command switch
            {
                "HELLO" => Encoding.ASCII.GetBytes("OK advancedplayer 1 2\n"),
                "LIST" => Block(Encoding.UTF8.GetBytes(SongsJson)),
                "GET 7" => Block(new byte[] { 1, 2, 3, 4, 5 }),
                "ART 7" => Encoding.ASCII.GetBytes("0\n"),
                "LRC 7" => Block(Encoding.UTF8.GetBytes("[00:01.00]hi")),
                _ => Encoding.ASCII.GetBytes("ERR unknown command\n"),
            };
            await stream.WriteAsync(reply);
            await stream.FlushAsync();
        }
    }

    /// <summary>A length line followed by the bytes, as the phone sends every block.</summary>
    private static byte[] Block(byte[] bytes) =>
        Encoding.ASCII.GetBytes($"{bytes.Length}\n").Concat(bytes).ToArray();
}
