package com.novax.browser
import android.os.SystemClock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlin.concurrent.thread

class BackgroundPlayService : Service() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.novax.browser.PLAY_PAUSE"
        const val ACTION_NEXT = "com.novax.browser.NEXT"
        const val ACTION_PREVIOUS = "com.novax.browser.PREVIOUS"
        const val ACTION_CLOSE = "com.novax.browser.CLOSE_AUDIO"
        const val ACTION_OPEN_PINNED = "com.novax.browser.OPEN_PINNED"

        const val CHANNEL_ID = "NovaXBgHidden"
        const val NOTIFICATION_ID = 1
        const val EXTRA_TITLE = "novax_media_title"
        const val EXTRA_URL = "novax_media_url"
        const val EXTRA_PLAYING = "novax_media_playing"
        const val EXTRA_DURATION = "novax_media_duration"
        const val EXTRA_POSITION = "novax_media_position"
    }

    private var mediaSession: MediaSessionCompat? = null
    private var artworkBitmap: Bitmap? = null
    private var currentDuration: Long = -1L
    private var currentPosition: Long = 0L
    private var currentMediaUrl: String = ""
    private var currentTitle: String = "Infer Browser Audio"
    private var isCurrentlyPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Infer Browser Audio", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        mediaSession = MediaSessionCompat(this, "InferBrowser").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = sendActionToBrowser(ACTION_PLAY_PAUSE)
                override fun onPause() = sendActionToBrowser(ACTION_PLAY_PAUSE)
                override fun onSkipToNext() = sendActionToBrowser(ACTION_NEXT)
                override fun onSkipToPrevious() = sendActionToBrowser(ACTION_PREVIOUS)
                override fun onStop() {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                sendActionToBrowser(ACTION_CLOSE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                sendActionToBrowser(intent.action!!)
                return START_STICKY
            }
        }

        val newTitle = intent?.getStringExtra(EXTRA_TITLE)
        val newUrl = intent?.getStringExtra(EXTRA_URL)
        val prefs = getSharedPreferences("novax_audio_state", MODE_PRIVATE)

        // नया वीडियो आते ही पुराना थंबनेल तुरंत डिलीट करना
        if (newUrl != null && newUrl.isNotBlank() && newUrl != currentMediaUrl) {
            currentMediaUrl = newUrl
            artworkBitmap = null 
        }

        if (newTitle != null && newTitle.isNotBlank() && newTitle != "Infer Browser Audio") {
            currentTitle = newTitle
        }

        // स्ट्रिक्ट प्ले/पॉज़ स्टेट
        if (intent?.hasExtra(EXTRA_PLAYING) == true) {
            isCurrentlyPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
            prefs.edit().putBoolean("playing", isCurrentlyPlaying).apply()
        } else {
            isCurrentlyPlaying = prefs.getBoolean("playing", true)
        }

        if (intent?.hasExtra(EXTRA_DURATION) == true) currentDuration = intent.getLongExtra(EXTRA_DURATION, -1L)
        if (intent?.hasExtra(EXTRA_POSITION) == true) currentPosition = intent.getLongExtra(EXTRA_POSITION, 0L)

        updateNotification()

        // सुपर-फास्ट 0.1s थंबनेल और टाइटल अपडेट
        if (currentMediaUrl.isNotBlank()) {
            thread {
                val newArtwork = loadArtwork(currentMediaUrl)
                if (newArtwork != null) {
                    artworkBitmap = newArtwork
                } else {
                    artworkBitmap = null // पुराना थंबनेल अटका न रहे, तुरंत रीसेट हो
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isCurrentlyPlaying) "Pause" else "Play"

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Infer Browser")
                .apply { if (currentDuration > 0) putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration) }
                .apply { artworkBitmap?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(if (isCurrentlyPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, currentPosition, if (isCurrentlyPlaying) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )

        fun actionPending(action: String, reqCode: Int): PendingIntent {
            val i = Intent(this, BackgroundPlayService::class.java).apply { this.action = action }
            return PendingIntent.getService(this, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PINNED
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(this, 100, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("Infer Browser")
            .setLargeIcon(artworkBitmap)
            .setContentIntent(openPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isCurrentlyPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Prev", actionPending(ACTION_PREVIOUS, 1))
            .addAction(playPauseIcon, playPauseTitle, actionPending(ACTION_PLAY_PAUSE, 2))
            .addAction(android.R.drawable.ic_media_next, "Next", actionPending(ACTION_NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", actionPending(ACTION_CLOSE, 4))
            .setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(1, 2, 3))
            .build()

        startForeground(NOTIFICATION_ID, notification)
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun loadArtwork(url: String): android.graphics.Bitmap? {
        if (url.isBlank()) return null
        val videoId = Regex("(?:youtube\\.com/watch\\?[^#]*v=|youtu\\.be/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: return null
        return try {
            val original = java.net.URL("https://img.youtube.com/vi/$videoId/hqdefault.jpg").openConnection().apply {
                connectTimeout = 2500; readTimeout = 2500
            }.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
            
            // Apply Premium Dark Gradient at the bottom for Player Controls
            val result = android.graphics.Bitmap.createBitmap(original.width, original.height, original.config ?: android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(original, 0f, 0f, null)
            val paint = android.graphics.Paint()
            val shader = android.graphics.LinearGradient(
                0f, original.height * 0.5f, 0f, original.height.toFloat(),
                android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#CC000000"),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, original.height * 0.5f, original.width.toFloat(), original.height.toFloat(), paint)
            result
        } catch (_: Exception) { null }
    }

    private fun sendActionToBrowser(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
