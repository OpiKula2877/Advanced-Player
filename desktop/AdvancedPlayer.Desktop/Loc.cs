using System.ComponentModel;

namespace AdvancedPlayer.Desktop;

/// <summary>
/// English and Czech UI strings. XAML binds to <see cref="Instance"/> with <c>{Binding [key], Source={x:Static local:Loc.Instance}}</c>.
/// </summary>
public sealed class Loc : INotifyPropertyChanged
{
    public static readonly Loc Instance = new();

    private static readonly Dictionary<string, (string En, string Cs)> Table = new()
    {
        ["app_title"] = ("Advanced Player", "Advanced Player"),
        ["nav_songs"] = ("Songs", "Skladby"),
        ["nav_albums"] = ("Albums", "Alba"),
        ["nav_artists"] = ("Artists", "Interpreti"),
        ["nav_playlists"] = ("Playlists", "Playlisty"),
        ["nav_favorites"] = ("Favourites", "Oblíbené"),
        ["nav_recent"] = ("Recently played", "Naposledy hrané"),
        ["nav_phone"] = ("Phone (Bluetooth)", "Telefon (Bluetooth)"),
        ["nav_stats"] = ("Statistics", "Statistiky"),
        ["nav_settings"] = ("Settings", "Nastavení"),
        ["search"] = ("Search", "Hledat"),
        ["play_all"] = ("Play all", "Přehrát vše"),
        ["shuffle_all"] = ("Shuffle", "Náhodně"),
        ["back"] = ("← Back", "← Zpět"),
        ["new_playlist"] = ("New playlist", "Nový playlist"),
        ["rename"] = ("Rename", "Přejmenovat"),
        ["delete"] = ("Delete", "Smazat"),
        ["remove_from_playlist"] = ("Remove from playlist", "Odebrat z playlistu"),
        ["play_next"] = ("Play next", "Přehrát jako další"),
        ["add_to_queue"] = ("Add to queue", "Přidat do fronty"),
        ["add_to_playlist"] = ("Add to playlist", "Přidat do playlistu"),
        ["toggle_favorite"] = ("Favourite / unfavourite", "Oblíbené / odebrat"),
        ["col_title"] = ("Title", "Název"),
        ["col_album"] = ("Album", "Album"),
        ["col_length"] = ("Length", "Délka"),
        ["tracks_count"] = ("{0} tracks", "{0} skladeb"),
        ["empty_list"] = ("Nothing here yet.", "Zatím tu nic není."),
        ["empty_library_hint"] = ("Add a music folder in Settings and scan it.", "Přidej složku s hudbou v Nastavení a prohledej ji."),
        ["scan"] = ("Scan library", "Prohledat knihovnu"),
        ["scanning"] = ("Scanning…", "Prohledávám…"),
        ["scan_done"] = ("Library updated: {0} files changed", "Knihovna aktualizována: změněno souborů {0}"),
        ["folders"] = ("Music folders", "Složky s hudbou"),
        ["add_folder"] = ("Add folder", "Přidat složku"),
        ["remove"] = ("Remove", "Odebrat"),
        ["language"] = ("Language", "Jazyk"),
        ["restart_hint"] = ("Restart the app to apply the language.", "Jazyk se použije po restartu aplikace."),
        ["equalizer"] = ("Equalizer", "Ekvalizér"),
        ["eq_enabled"] = ("Equalizer on", "Ekvalizér zapnutý"),
        ["eq_preset"] = ("Preset", "Předvolba"),
        ["preset_flat"] = ("Flat", "Vyrovnaný"),
        ["preset_rock"] = ("Rock", "Rock"),
        ["preset_pop"] = ("Pop", "Pop"),
        ["preset_jazz"] = ("Jazz", "Jazz"),
        ["preset_classical"] = ("Classical", "Klasika"),
        ["preset_bass"] = ("Bass", "Basy"),
        ["preset_vocal"] = ("Vocal", "Vokály"),
        ["preset_electronic"] = ("Electronic", "Elektronika"),
        ["preset_custom"] = ("Custom", "Vlastní"),
        ["bass_boost"] = ("Bass boost", "Zesílení basů"),
        ["reset"] = ("Reset", "Vynulovat"),
        ["output"] = ("Output", "Výstup"),
        ["output_default"] = ("Default device", "Výchozí zařízení"),
        ["sleep_timer"] = ("Sleep timer", "Časovač spánku"),
        ["sleep_off"] = ("Sleep timer off", "Časovač vypnut"),
        ["sleep_set"] = ("Playback stops in {0} min", "Přehrávání se zastaví za {0} min"),
        ["sleep_stopped"] = ("Sleep timer paused playback", "Časovač spánku pozastavil přehrávání"),
        ["now_playing"] = ("Now playing", "Právě hraje"),
        ["lyrics"] = ("Lyrics", "Text"),
        ["visualizer"] = ("Visualizer", "Vizualizér"),
        ["up_next"] = ("Up next", "Další ve frontě"),
        ["nav_queue"] = ("Queue", "Fronta"),
        ["clear_queue"] = ("Clear queue", "Vyčistit frontu"),
        ["remove_from_queue"] = ("Remove from queue", "Odebrat z fronty"),
        ["queue_empty"] = ("The queue is empty. Right-click a song and choose Add to queue.", "Fronta je prázdná. Pravým klikem na skladbu zvol Přidat do fronty."),
        ["add_to_queue_button"] = ("Add to queue", "Přidat do fronty"),
        ["pick_song"] = ("Add song…", "Přidat písničku…"),
        ["pick_songs_first"] = ("Select songs first.", "Nejdřív vyber skladby."),
        ["added_to_queue"] = ("Added to queue: {0}", "Přidáno do fronty: {0}"),
        ["close"] = ("Close", "Zavřít"),
        ["style"] = ("Style", "Styl"),
        ["style_vinyl"] = ("Vinyl", "Vinyl"),
        ["style_cassette"] = ("Cassette", "Kazeta"),
        ["style_cover"] = ("Cover", "Obal"),
        ["visual_bars"] = ("Bars", "Sloupce"),
        ["visual_mirror"] = ("Mirror", "Zrcadlo"),
        ["visual_circle"] = ("Circle", "Kruh"),
        ["visual_wave"] = ("Wave", "Vlna"),
        ["visual_particles"] = ("Particles", "Částice"),
        ["visual_vu"] = ("VU meter", "VU metr"),
        ["system_output_enable"] = ("Switch the Windows output while this app runs", "Přepnout výstup Windows, dokud tahle appka běží"),
        ["system_output_hint"] = ("The chosen device becomes the default output for all apps, including YouTube in Brave. The original output comes back when you close the app. If the app crashes, switch the output back in the Windows sound settings.", "Vybrané zařízení bude výchozí výstup pro všechny aplikace, včetně YouTube v Brave. Po zavření appky se vrátí původní výstup. Když appka spadne, přepni výstup zpět v nastavení zvuku ve Windows."),
        ["system_output_switched"] = ("Output switched to {0}", "Výstup přepnut na {0}"),
        ["system_output_restored"] = ("Original output restored", "Původní výstup obnoven"),
        ["system_output_failed"] = ("Could not switch the output: {0}", "Výstup se nepodařilo přepnout: {0}"),
        ["system_output_missing"] = ("The chosen device is not connected. Turn the switch off and on again after you connect it.", "Vybrané zařízení není připojené. Až ho připojíš, přepínač vypni a zapni znovu."),
        ["audio_title"] = ("Audio output", "Výstup zvuku"),
        ["audio_hint"] = ("Other apps, such as YouTube in Brave, keep their own output. Windows sets it per app: in the volume mixer, choose the Bluetooth speakers for Brave.", "Ostatní aplikace, třeba YouTube v Brave, mají vlastní výstup. Windows ho nastavuje pro každou aplikaci zvlášť: v mixéru hlasitosti vyber pro Brave Bluetooth reproduktory."),
        ["audio_open"] = ("Open app volume settings", "Otevřít nastavení hlasitosti aplikací"),
        ["nav_spotify"] = ("Spotify", "Spotify"),
        ["spotify_intro"] = (
            "1. Make a free app at developer.spotify.com/dashboard and copy its Client ID.\n2. In the app settings add the redirect URI http://127.0.0.1:8912/callback.\n3. Paste the Client ID here and press Sign in. Search works with any account. Playing on your Spotify device needs Premium.",
            "1. Vytvoř si zdarma aplikaci na developer.spotify.com/dashboard a zkopíruj její Client ID.\n2. V nastavení aplikace přidej redirect URI http://127.0.0.1:8912/callback.\n3. Client ID vlož sem a klikni na Přihlásit. Vyhledávání funguje u každého účtu, přehrávání na tvém Spotify zařízení potřebuje Premium."),
        ["spotify_save"] = ("Save Client ID", "Uložit Client ID"),
        ["spotify_sign_in"] = ("Sign in", "Přihlásit"),
        ["spotify_sign_out"] = ("Sign out", "Odhlásit"),
        ["spotify_search"] = ("Search", "Hledat"),
        ["spotify_refresh_devices"] = ("Refresh devices", "Obnovit zařízení"),
        ["spotify_open"] = ("Open in Spotify", "Otevřít ve Spotify"),
        ["spotify_play_device"] = ("Play on device", "Přehrát na zařízení"),
        ["spotify_signed_in"] = ("Signed in to Spotify", "Přihlášen do Spotify"),
        ["spotify_signed_out"] = ("Not signed in", "Nepřihlášen"),
        ["spotify_saved"] = ("Client ID saved", "Client ID uloženo"),
        ["spotify_waiting"] = ("Finish signing in in your browser…", "Dokonči přihlášení v prohlížeči…"),
        ["spotify_client_missing"] = ("Paste your Client ID first.", "Nejdřív vlož Client ID."),
        ["spotify_not_signed_in"] = ("Sign in to Spotify first.", "Nejdřív se přihlas do Spotify."),
        ["spotify_pick_track"] = ("Pick a song from the results first.", "Nejdřív vyber skladbu z výsledků."),
        ["spotify_premium_required"] = ("Playing on a device needs Spotify Premium. Use Open in Spotify instead.", "Přehrávání na zařízení vyžaduje Spotify Premium. Použij Otevřít ve Spotify."),
        ["spotify_no_device"] = ("No active Spotify device. Open Spotify on this PC or on your phone, then press Refresh devices.", "Není aktivní žádné Spotify zařízení. Otevři Spotify na PC nebo v telefonu a klikni na Obnovit zařízení."),
        ["spotify_devices_found"] = ("Devices found: {0}", "Nalezeno zařízení: {0}"),
        ["spotify_playing"] = ("Playing {0}", "Hraje {0}"),
        ["spotify_device"] = ("Device", "Zařízení"),
        ["no_lyrics"] = ("No lyrics for this song.", "K této skladbě nejsou texty."),
        ["nothing_playing"] = ("Nothing playing", "Nic nehraje"),
        ["loading"] = ("Loading…", "Načítám…"),
        ["play_error"] = ("Could not play: {0}", "Nelze přehrát: {0}"),
        ["phone_title"] = ("Phone over Bluetooth", "Telefon přes Bluetooth"),
        ["phone_intro"] = (
            "1. Pair the phone in Windows Bluetooth settings.\n2. In the Android app open Settings and turn on PC link.\n3. Here press Find phones, select the phone and press Connect.",
            "1. Paruj telefon v nastavení Bluetooth ve Windows.\n2. V Android aplikaci otevři Nastavení a zapni PC link.\n3. Tady klikni na Najít telefony, vyber telefon a klikni na Připojit."),
        ["phone_find"] = ("Find phones", "Najít telefony"),
        ["phone_connect"] = ("Connect", "Připojit"),
        ["phone_disconnect"] = ("Disconnect", "Odpojit"),
        ["phone_sync"] = ("Sync library", "Synchronizovat knihovnu"),
        ["phone_none"] = ("No phone found. Check the pairing and that PC link is on.", "Telefon nenalezen. Zkontroluj párování a že je zapnutý PC link."),
        ["phone_connected"] = ("Connected to {0}, {1} songs", "Připojeno k {0}, skladeb {1}"),
        ["phone_idle"] = ("Not connected", "Nepřipojeno"),
        ["phone_not_connected"] = ("Connect to the phone first.", "Nejdřív se připoj k telefonu."),
        ["phone_downloading"] = ("Downloading {0}…", "Stahuji {0}…"),
        ["phone_busy"] = ("Working…", "Pracuji…"),
        ["stats_plays"] = ("Plays", "Přehrání"),
        ["stats_listened"] = ("Listened", "Poslechnuto"),
        ["stats_songs"] = ("Songs", "Skladeb"),
        ["stats_top"] = ("Most played", "Nejčastěji hrané"),
        ["stats_last14"] = ("Last 14 days", "Posledních 14 dní"),
        ["hours_minutes"] = ("{0} h {1} min", "{0} h {1} min"),
        ["playlist_name"] = ("Playlist name", "Název playlistu"),
        ["new_name"] = ("New name", "Nový název"),
        ["confirm_delete"] = ("Delete \"{0}\"?", "Smazat \"{0}\"?"),
        ["choose_folder"] = ("Choose a music folder", "Vyber složku s hudbou"),
        ["ok"] = ("OK", "OK"),
        ["cancel"] = ("Cancel", "Zrušit"),
        ["repeat_off"] = ("Repeat off", "Opakování vypnuto"),
        ["repeat_all"] = ("Repeat all", "Opakovat vše"),
        ["repeat_one"] = ("Repeat one", "Opakovat jednu"),
        ["previous"] = ("Previous", "Předchozí"),
        ["next"] = ("Next", "Další"),
        ["play"] = ("Play", "Přehrát"),
        ["pause"] = ("Pause", "Pozastavit"),
        ["shuffle"] = ("Shuffle", "Náhodně"),
        ["volume"] = ("Volume", "Hlasitost"),
        ["show_panel"] = ("Now playing panel", "Panel Právě hraje"),
        ["albums_by"] = ("{0} · {1} tracks", "{0} · {1} skladeb"),
        ["unknown_album"] = ("Unknown album", "Neznámé album"),
        ["unknown_artist"] = ("Unknown artist", "Neznámý interpret"),
        ["in_library"] = ("In library", "V knihovně"),
        ["phone_songs_hint"] = ("Songs from the phone are marked with 📱 and download the first time you play them.",
            "Skladby z telefonu mají značku 📱 a stáhnou se při prvním přehrání."),
    };

    private Loc()
    {
    }

    /// <summary>Set once at startup from the saved language.</summary>
    public static bool Czech { get; set; }

    public string this[string key] => Get(key);

    /// <summary>Returns the string for the current language, with {0}, {1}… replaced by <paramref name="args"/>.</summary>
    public static string Get(string key, params object[] args)
    {
        if (!Table.TryGetValue(key, out var pair)) return key;
        string text = Czech ? pair.Cs : pair.En;
        return args.Length == 0 ? text : string.Format(System.Globalization.CultureInfo.CurrentCulture, text, args);
    }

    /// <summary>Tells XAML bindings to re-read every string (only needed if the language changes at runtime).</summary>
    public void Refresh() => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(string.Empty));

    public event PropertyChangedEventHandler? PropertyChanged;
}
