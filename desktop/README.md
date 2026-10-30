# Advanced Player for Windows

A Windows version of the Android app, written in C# (.NET 10, WPF). It plays the music on your PC,
and it can read the library from your phone over Bluetooth and search Spotify.

## Features

- Library from folders on this PC: MP3, M4A/AAC, FLAC, OGG, WAV and WMA, with tags and cover art
- Songs, albums, artists, playlists, favourites and recently played
- Play queue: add songs with the "Add to queue" button or the right-click menu, play a song early, remove songs, clear the queue
- Shuffle, repeat, seeking, volume and output device selection (Bluetooth speakers and headphones show up here once Windows is connected to them)
- Sleep timer (the Zzz button)
- 10-band equalizer with bass boost and the same presets as the Android app
- Now playing: vinyl, cassette or cover style, a visualizer in six modes (bars, mirror, circle, wave, particles, VU meter) and synced lyrics (`.lrc` files and embedded lyrics)
- Statistics: totals, most played songs and a 14-day chart
- Phone over Bluetooth: the phone's songs appear in your library (see below)
- Spotify: search and open songs in Spotify, and play on a Spotify device with Premium (see below)
- English and Czech. The language setting applies after a restart.

## Not ported yet

Video, car mode, the home screen widget, the shake gesture, crossfade and gapless playback, the speed,
pitch and reverb presets, the A-B loop, bookmarks, smart playlists and tag editing.

## Build and run

You need Windows 10 (build 19041) or newer and the .NET 10 SDK.

```bash
dotnet run --project desktop/AdvancedPlayer.Desktop
```

```bash
dotnet test desktop/AdvancedPlayer.Desktop.Tests
```

## Installer (setup.exe)

To build one `setup.exe` for other PCs, run this from the repository root:

```bash
powershell -ExecutionPolicy Bypass -File desktop\installer\build-installer.ps1
```

The result is `desktop\installer\dist\setup.exe`. The installer:

- installs for the current user into `%LOCALAPPDATA%\Programs\Advanced Player`, so no administrator rights are needed;
- adds a Start menu entry and, if you choose, a desktop shortcut;
- registers the program under Settings → Apps, so it can be uninstalled there;
- includes the .NET runtime, so .NET does not have to be installed on the PC.

Silent install: `setup.exe /S /D=C:\path\to\folder`. The options `/NODESKTOP`, `/NOSTARTMENU` and `/NOREGISTRY` skip the matching steps.
Uninstall: run `Uninstall.exe` in the install folder, or use Settings → Apps. The library and settings in `%LOCALAPPDATA%\AdvancedPlayer` are kept.

The installer is not code-signed, so Windows SmartScreen may show a warning the first time it runs. Signing needs a code-signing certificate.

## Library

Open **Settings → Music folders**, add a folder and press **Scan library**. Your Music folder is added on
the first start. Files that are deleted or moved out of the folders are removed on the next scan.

The data is kept in `%LOCALAPPDATA%\AdvancedPlayer`: `library.db`, the artwork cache, and the phone
songs that were downloaded (`phone`).

## Phone over Bluetooth

1. Pair the phone with Windows in **Settings → Bluetooth & devices**.
2. On the phone, open Advanced Player → **Settings → PC** and turn on **Share library with PC over Bluetooth**.
   The setting is off by default. A notification shows while the link runs, and the app asks for the
   Bluetooth permission the first time.
3. On the PC, open **Phone (Bluetooth)**, press **Find phones**, select the phone and press **Connect**.
   The phone's songs appear in the library. A song is downloaded the first time it is played.

Classic Bluetooth moves roughly 100–250 KB/s in practice, so the first download of a large FLAC file takes a while.

### How the link works

The phone runs a Bluetooth (RFCOMM) server with the service id `8f4c2b1e-6a3d-4e7b-9c15-2d9a7f0b6e31`.
Every request is one line of ASCII. A "length" answer is a decimal byte count and a newline, followed by
exactly that many bytes.

| Request | Answer |
|---|---|
| `HELLO` | `OK advancedplayer 1 <songs>` |
| `LIST` | length + JSON array of songs |
| `GET <id>` | length + the audio file, or `-1` |
| `ART <id>` | length + JPEG or PNG bytes, or `0` |
| `LRC <id>` | length + LRC text, or `0` |
| `BYE` | the connection closes |

The phone side is `app/src/main/java/com/advancedplayer/pclink/`. The PC side is `Phone/`.

## Spotify

Spotify does not give third-party apps the audio stream, because it is protected by DRM. So the
equalizer, bass boost, visualizer and digital effects **do not apply to Spotify songs**. What works:

- **Sign in** with a free developer app (PKCE, so there is no client secret). Create an app at
  <https://developer.spotify.com/dashboard>, add the redirect URI `http://127.0.0.1:8912/callback`,
  paste the Client ID into the Spotify tab and press **Sign in**.
- **Search** works with any Spotify account.
- **Open in Spotify** opens the song in the Spotify app or in the web player. This works without Premium.
- **Play on a device** needs Spotify Premium and an active Spotify device (the Spotify app on this PC or on
  your phone). Free accounts get a clear message instead.

The refresh token is stored encrypted for your Windows account (DPAPI). For an equalizer on Spotify,
use the equalizer inside the Spotify app if your version has one, or a system-wide equalizer such as
Equalizer APO.

## Status and limits

- The Bluetooth link and the Spotify sign-in are built and compile, and the phone protocol is covered by
  tests over a loopback connection. They have not been run against a real phone or a real Spotify account yet.
- Audio goes through WASAPI in shared mode. If a device refuses that format, playback falls back to WinMM.
- The Android app is untouched apart from the new PC link option and the version number.
