package com.advancedplayer.pclink

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.advancedplayer.AdvancedPlayerApp
import com.advancedplayer.R

/**
 * Keeps the PC link running while the setting is on. Android requires a visible notification for this,
 * so the link is never hidden. The service stops itself when Bluetooth permission or Bluetooth is missing.
 */
class PcLinkService : Service() {
    private var server: PcLinkServer? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.pc_link_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Checked before startForeground: Android 14 refuses a connectedDevice service without Bluetooth permission.
        if (!hasBluetoothPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        if (server == null) {
            val started = PcLinkServer(this, (application as AdvancedPlayerApp).container)
            if (started.start()) server = started else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pc_link_channel))
            .setContentText(getString(R.string.pc_link_notification))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "pc_link"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PcLinkService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PcLinkService::class.java))
        }
    }
}
