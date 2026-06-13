package com.yaokongqi.remote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yaokongqi.remote.MainActivity
import com.yaokongqi.remote.R

class RemoteService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pcName = intent?.getStringExtra(EXTRA_PC_NAME) ?: ""
        startForeground(NOTIFICATION_ID, buildNotification(pcName))
        return START_STICKY
    }

    private fun buildNotification(pcName: String): Notification {
        val channelId = "yaokongqi_connection"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_connected, pcName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_PC_NAME = "pc_name"
        private const val NOTIFICATION_ID = 1
    }
}
