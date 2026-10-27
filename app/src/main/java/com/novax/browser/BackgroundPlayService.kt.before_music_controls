package com.novax.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class BackgroundPlayService : Service() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.novax.browser.PLAY_PAUSE"
        const val ACTION_CLOSE = "com.novax.browser.CLOSE_AUDIO"
        const val ACTION_OPEN_PINNED = "com.novax.browser.OPEN_PINNED"
        const val CHANNEL_ID = "NovaXBg"
        const val NOTIFICATION_ID = 1
        const val EXTRA_TITLE = "novax_media_title"
        const val EXTRA_URL = "novax_media_url"
        const val EXTRA_PLAYING = "novax_media_playing"
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NovaX Live Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NovaX Browser background audio controls"
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PLAY_PAUSE -> {
                sendActionToBrowser(ACTION_PLAY_PAUSE)
            }

            ACTION_OPEN_PINNED -> {
                sendActionToBrowser(ACTION_OPEN_PINNED)
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "NovaX Live Audio"

        val prefs = getSharedPreferences("novax_audio_state", MODE_PRIVATE)
        val playing = if (intent?.hasExtra(EXTRA_PLAYING) == true) {
            intent.getBooleanExtra(EXTRA_PLAYING, false).also { state ->
                prefs.edit().putBoolean("playing", state).apply()
            }
        } else {
            prefs.getBoolean("playing", true)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PINNED
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val openPending = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, BackgroundPlayService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }

        val togglePending = PendingIntent.getService(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(this, BackgroundPlayService::class.java).apply {
            action = ACTION_CLOSE
        }

        val closePending = PendingIntent.getService(
            this,
            102,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (playing) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val playPauseLabel = if (playing) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("NOVAX  •  LIVE AUDIO")
            .setSubText("NOVAX  •  BACKGROUND PLAYER")
            .setColor(android.graphics.Color.rgb(76, 141, 255))
            .setColorized(true)
            .setContentIntent(openPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .addAction(playPauseIcon, playPauseLabel, togglePending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                closePending
            )
            .setStyle(
                MediaStyle().setShowActionsInCompactView(0, 1)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun sendActionToBrowser(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Keep the foreground service alive if the browser cannot be brought forward.
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
