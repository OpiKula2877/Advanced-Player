package com.advancedplayer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import com.advancedplayer.data.Permissions
import com.advancedplayer.ui.AppRoot
import com.advancedplayer.ui.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = Permissions.hasMediaAccess(this)
        if (hasPermission) vm.rescan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // English is the default regardless of the phone language; CS/SK are opt-in in settings.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        enableEdgeToEdge()
        container.player.connect()

        hasPermission = Permissions.hasMediaAccess(this)
        if (savedInstanceState == null) {
            if (hasPermission) vm.rescan() else permissionLauncher.launch(Permissions.mediaAndNotifications)
            handleViewIntent(intent)
        }
        setContent {
            AppRoot(vm, hasPermission) { permissionLauncher.launch(Permissions.mediaAndNotifications) }
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) container.player.disconnect()
        super.onDestroy()
    }

    /** "Open with" from a file manager: add the file to the library and play it. */
    private fun handleViewIntent(intent: Intent?) {
        val uri: Uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        vm.launch {
            vm.library.addFiles(listOf(uri))
            val track = vm.library.trackByUri(uri.toString()) ?: return@launch
            repeat(30) {
                if (container.player.controller != null) {
                    container.player.playTracks(listOf(track))
                    return@launch
                }
                delay(100)
            }
        }
    }

    private val container get() = (application as AdvancedPlayerApp).container
}
