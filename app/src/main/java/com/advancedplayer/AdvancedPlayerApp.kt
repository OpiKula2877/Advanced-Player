package com.advancedplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.advancedplayer.data.ArtFetcher
import com.advancedplayer.data.ArtKeyer
import com.advancedplayer.data.ArtRepository
import com.advancedplayer.data.LibraryRepository
import com.advancedplayer.data.SettingsRepository
import com.advancedplayer.data.db.AppDatabase
import com.advancedplayer.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(app)
    val settings = SettingsRepository(app)
    val art = ArtRepository(app, db)
    val library = LibraryRepository(app, db, settings, art)
    val player = PlayerConnection(app, library, settings)
}

class AdvancedPlayerApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ArtKeyer())
                add(ArtFetcher.Factory(container.art))
            }
            .crossfade(180)
            .build()
}
