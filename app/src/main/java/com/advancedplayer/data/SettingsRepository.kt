package com.advancedplayer.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class DeckStyle { COVER, CIRCLE, CASSETTE, VINYL }

enum class VisualizerStyle { BARS, CIRCLE, WAVE, MIRROR, PARTICLES, VU }

enum class SongSort { TITLE, ARTIST, DATE_ADDED, DURATION, MOST_PLAYED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Long = DEFAULT_ACCENT,
    val albumColors: Boolean = true,
    val deckStyle: DeckStyle = DeckStyle.COVER,
    val crtEffect: Boolean = false,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.BARS,
    val scanMediaStore: Boolean = true,
    val includeVideos: Boolean = true,
    val minDurationSec: Int = 30,
    val folderUris: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
    val crossfadeSec: Int = 0,
    val rememberLongPositions: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val resumeOnConnect: Boolean = false,
    val swipeGestures: Boolean = true,
    val shakeToSkip: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqBands: List<Float> = List(EQ_BAND_COUNT) { 0f },
    val eqPreset: String = "flat",
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val loudness: Float = 0f,
    val reverb: Float = 0f,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val songSort: SongSort = SongSort.TITLE,
    val statsDays: Int = 30,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF1DB954
        const val EQ_BAND_COUNT = 10
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = longPreferencesKey("accent")
        val albumColors = booleanPreferencesKey("album_colors")
        val deck = stringPreferencesKey("deck")
        val crt = booleanPreferencesKey("crt")
        val vis = stringPreferencesKey("vis")
        val scanMs = booleanPreferencesKey("scan_ms")
        val videos = booleanPreferencesKey("videos")
        val minDur = intPreferencesKey("min_dur")
        val folders = stringSetPreferencesKey("folders")
        val excluded = stringSetPreferencesKey("excluded")
        val crossfade = intPreferencesKey("crossfade")
        val rememberPos = booleanPreferencesKey("remember_pos")
        val pauseDisc = booleanPreferencesKey("pause_disc")
        val resumeConn = booleanPreferencesKey("resume_conn")
        val swipe = booleanPreferencesKey("swipe")
        val shake = booleanPreferencesKey("shake")
        val eqOn = booleanPreferencesKey("eq_on")
        val eqBands = stringPreferencesKey("eq_bands")
        val eqPreset = stringPreferencesKey("eq_preset")
        val bass = floatPreferencesKey("bass")
        val virt = floatPreferencesKey("virt")
        val loud = floatPreferencesKey("loud")
        val reverb = floatPreferencesKey("reverb")
        val speed = floatPreferencesKey("speed")
        val pitch = floatPreferencesKey("pitch")
        val sort = stringPreferencesKey("sort")
        val statsDays = intPreferencesKey("stats_days")
        val onboarding = booleanPreferencesKey("onboarding")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = this[K.theme].toEnum(d.themeMode),
            accent = this[K.accent] ?: d.accent,
            albumColors = this[K.albumColors] ?: d.albumColors,
            deckStyle = this[K.deck].toEnum(d.deckStyle),
            crtEffect = this[K.crt] ?: d.crtEffect,
            visualizerStyle = this[K.vis].toEnum(d.visualizerStyle),
            scanMediaStore = this[K.scanMs] ?: d.scanMediaStore,
            includeVideos = this[K.videos] ?: d.includeVideos,
            minDurationSec = this[K.minDur] ?: d.minDurationSec,
            folderUris = this[K.folders] ?: d.folderUris,
            excludedFolders = this[K.excluded] ?: d.excludedFolders,
            crossfadeSec = this[K.crossfade] ?: d.crossfadeSec,
            rememberLongPositions = this[K.rememberPos] ?: d.rememberLongPositions,
            pauseOnDisconnect = this[K.pauseDisc] ?: d.pauseOnDisconnect,
            resumeOnConnect = this[K.resumeConn] ?: d.resumeOnConnect,
            swipeGestures = this[K.swipe] ?: d.swipeGestures,
            shakeToSkip = this[K.shake] ?: d.shakeToSkip,
            eqEnabled = this[K.eqOn] ?: d.eqEnabled,
            eqBands = this[K.eqBands]?.split(',')?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == AppSettings.EQ_BAND_COUNT } ?: d.eqBands,
            eqPreset = this[K.eqPreset] ?: d.eqPreset,
            bassBoost = this[K.bass] ?: d.bassBoost,
            virtualizer = this[K.virt] ?: d.virtualizer,
            loudness = this[K.loud] ?: d.loudness,
            reverb = this[K.reverb] ?: d.reverb,
            speed = this[K.speed] ?: d.speed,
            pitch = this[K.pitch] ?: d.pitch,
            songSort = this[K.sort].toEnum(d.songSort),
            statsDays = this[K.statsDays] ?: d.statsDays,
            onboardingDone = this[K.onboarding] ?: d.onboardingDone,
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setThemeMode(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setAccent(v: Long) = edit { it[K.accent] = v }
    suspend fun setAlbumColors(v: Boolean) = edit { it[K.albumColors] = v }
    suspend fun setDeckStyle(v: DeckStyle) = edit { it[K.deck] = v.name }
    suspend fun setCrtEffect(v: Boolean) = edit { it[K.crt] = v }
    suspend fun setVisualizerStyle(v: VisualizerStyle) = edit { it[K.vis] = v.name }
    suspend fun setScanMediaStore(v: Boolean) = edit { it[K.scanMs] = v }
    suspend fun setIncludeVideos(v: Boolean) = edit { it[K.videos] = v }
    suspend fun setMinDuration(v: Int) = edit { it[K.minDur] = v }
    suspend fun addFolder(uri: String) = edit { it[K.folders] = (it[K.folders] ?: emptySet()) + uri }
    suspend fun removeFolder(uri: String) = edit { it[K.folders] = (it[K.folders] ?: emptySet()) - uri }
    suspend fun addExcluded(path: String) = edit { it[K.excluded] = (it[K.excluded] ?: emptySet()) + path }
    suspend fun removeExcluded(path: String) = edit { it[K.excluded] = (it[K.excluded] ?: emptySet()) - path }
    suspend fun setCrossfade(v: Int) = edit { it[K.crossfade] = v }
    suspend fun setRememberPositions(v: Boolean) = edit { it[K.rememberPos] = v }
    suspend fun setPauseOnDisconnect(v: Boolean) = edit { it[K.pauseDisc] = v }
    suspend fun setResumeOnConnect(v: Boolean) = edit { it[K.resumeConn] = v }
    suspend fun setSwipeGestures(v: Boolean) = edit { it[K.swipe] = v }
    suspend fun setShakeToSkip(v: Boolean) = edit { it[K.shake] = v }
    suspend fun setEqEnabled(v: Boolean) = edit { it[K.eqOn] = v }
    suspend fun setEq(bands: List<Float>, preset: String) = edit {
        it[K.eqBands] = bands.joinToString(",")
        it[K.eqPreset] = preset
    }
    suspend fun setBassBoost(v: Float) = edit { it[K.bass] = v }
    suspend fun setVirtualizer(v: Float) = edit { it[K.virt] = v }
    suspend fun setLoudness(v: Float) = edit { it[K.loud] = v }
    suspend fun setReverb(v: Float) = edit { it[K.reverb] = v }
    suspend fun setSpeedPitch(speed: Float, pitch: Float) = edit {
        it[K.speed] = speed
        it[K.pitch] = pitch
    }
    suspend fun setSongSort(v: SongSort) = edit { it[K.sort] = v.name }
    suspend fun setStatsDays(v: Int) = edit { it[K.statsDays] = v }
    suspend fun setOnboardingDone() = edit { it[K.onboarding] = true }
}
