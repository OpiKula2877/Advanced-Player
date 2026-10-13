package com.advancedplayer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    val media: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    val mediaAndNotifications: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) media + Manifest.permission.POST_NOTIFICATIONS else media

    fun hasMediaAccess(context: Context): Boolean {
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, audio) == PackageManager.PERMISSION_GRANTED
    }
}
