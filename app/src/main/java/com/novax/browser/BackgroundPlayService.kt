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
                if (intent.action == ACTION_PLAY_PAUSE) {
                    isCurrentlyPlaying = !isCurrentlyPlaying
                    updateNotification()
                }
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

    private fun getDominantColor(bitmap: android.graphics.Bitmap?): Int {
        if (bitmap == null) return android.graphics.Color.parseColor("#1A1A1A")
        return try {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val color = scaled.getPixel(0, 0)
            scaled.recycle()
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[1] = hsv[1] * 0.8f 
            hsv[2] = hsv[2] * 0.35f 
            android.graphics.Color.HSVToColor(hsv)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#1A1A1A")
        }
    }

    private fun updateNotification() {
        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        mediaSession?.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, "Infer Premium")
                .apply { if (currentDuration > 0) putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration) }
                .apply { artworkBitmap?.let { putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )

        mediaSession?.setPlaybackState(
            android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setActions(android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(if (isCurrentlyPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED, currentPosition, if (isCurrentlyPlaying) 1f else 0f, android.os.SystemClock.elapsedRealtime())
                .build()
        )

        fun actionPending(action: String, reqCode: Int): android.app.PendingIntent {
            val i = android.content.Intent(this, BackgroundPlayService::class.java).apply { this.action = action }
            return android.app.PendingIntent.getService(this, reqCode, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        }

        val openIntent = android.content.Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PINNED
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = android.app.PendingIntent.getActivity(this, 100, openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val customView = android.widget.RemoteViews(packageName, resources.getIdentifier("layout_custom_notification", "layout", packageName))
        customView.setTextViewText(resources.getIdentifier("notifTitle", "id", packageName), currentTitle)
        customView.setImageViewResource(resources.getIdentifier("notifPlayPause", "id", packageName), playPauseIcon)
        
        if (artworkBitmap != null) {
            customView.setImageViewBitmap(resources.getIdentifier("notifArt", "id", packageName), artworkBitmap)
            val bgColor = getDominantColor(artworkBitmap)
            customView.setInt(resources.getIdentifier("notifRoot", "id", packageName), "setBackgroundColor", bgColor)
        } else {
            customView.setImageViewResource(resources.getIdentifier("notifArt", "id", packageName), android.R.drawable.ic_media_play)
            customView.setInt(resources.getIdentifier("notifRoot", "id", packageName), "setBackgroundColor", android.graphics.Color.parseColor("#1A1A1A"))
        }

        customView.setOnClickPendingIntent(resources.getIdentifier("notifPlayPause", "id", packageName), actionPending(ACTION_PLAY_PAUSE, 2))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifNext", "id", packageName), actionPending(ACTION_NEXT, 3))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifPrev", "id", packageName), actionPending(ACTION_PREVIOUS, 1))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifClose", "id", packageName), actionPending(ACTION_CLOSE, 4))

        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setContentIntent(openPending)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isCurrentlyPlaying)
            .setStyle(androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle().setMediaSession(mediaSession?.sessionToken))
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
