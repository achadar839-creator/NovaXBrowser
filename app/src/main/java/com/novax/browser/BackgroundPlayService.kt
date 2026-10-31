package com.novax.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class BackgroundPlayService : Service() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.novax.browser.PLAY_PAUSE"
        const val ACTION_NEXT = "com.novax.browser.NEXT"
        const val ACTION_PREVIOUS = "com.novax.browser.PREVIOUS"
        const val ACTION_CLOSE = "com.novax.browser.CLOSE_AUDIO"
        const val ACTION_OPEN_PINNED = "com.novax.browser.OPEN_PINNED"
        const val ACTION_SKIP_AD = "com.novax.browser.SKIP_AD"

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
    private var currentTitle: String = "Infer Browser Pinned Web"
    private var isCurrentlyPlaying: Boolean = false
    private var isAdPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Infer Browser Pinned Web", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        mediaSession = MediaSessionCompat(this, "InferBrowser").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = handleAction(ACTION_PLAY_PAUSE)
                override fun onPause() = handleAction(ACTION_PLAY_PAUSE)
                override fun onSkipToNext() = handleAction(if(isAdPlaying) ACTION_SKIP_AD else ACTION_NEXT)
                override fun onSkipToPrevious() = handleAction(ACTION_PREVIOUS)
                override fun onStop() = handleAction(ACTION_CLOSE)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null && action.startsWith("com.novax.browser.")) {
            handleAction(action)
            if (action == ACTION_CLOSE) return START_NOT_STICKY
            return START_STICKY
        }

        // Ad State Check
        if (intent?.hasExtra("novax_is_ad_playing") == true) {
            isAdPlaying = intent.getBooleanExtra("novax_is_ad_playing", false)
            updateNotification()
            return START_STICKY
        }

        val newTitle = intent?.getStringExtra(EXTRA_TITLE)
        val newUrl = intent?.getStringExtra(EXTRA_URL)
        val prefs = getSharedPreferences("novax_audio_state", MODE_PRIVATE)

        if (newUrl != null && newUrl.isNotBlank() && newUrl != currentMediaUrl) {
            currentMediaUrl = newUrl
            artworkBitmap = null
        }
        if (newTitle != null && newTitle.isNotBlank()) currentTitle = newTitle

        if (intent?.hasExtra(EXTRA_PLAYING) == true) {
            isCurrentlyPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
            prefs.edit().putBoolean("playing", isCurrentlyPlaying).apply()
        } else {
            isCurrentlyPlaying = prefs.getBoolean("playing", true)
        }

        if (intent?.hasExtra(EXTRA_DURATION) == true) currentDuration = intent.getLongExtra(EXTRA_DURATION, -1L)
        if (intent?.hasExtra(EXTRA_POSITION) == true) currentPosition = intent.getLongExtra(EXTRA_POSITION, 0L)

        updateNotification()

        if (currentMediaUrl.isNotBlank() && artworkBitmap == null) {
            thread {
                val newArtwork = loadArtwork(currentMediaUrl)
                if (newArtwork != null) {
                    artworkBitmap = newArtwork
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            ACTION_CLOSE -> {
                sendBroadcast(Intent(ACTION_CLOSE).setPackage(packageName))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY_PAUSE -> {
                isCurrentlyPlaying = !isCurrentlyPlaying
                sendBroadcast(Intent(ACTION_PLAY_PAUSE).setPackage(packageName))
                updateNotification()
            }
            ACTION_NEXT, ACTION_PREVIOUS, ACTION_SKIP_AD -> {
                sendBroadcast(Intent(action).setPackage(packageName))
            }
        }
    }

    private fun getDominantColor(bitmap: Bitmap?): Int {
        if (bitmap == null) return android.graphics.Color.parseColor("#1A1A1A")
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val color = scaled.getPixel(0, 0)
            scaled.recycle()
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[1] = hsv[1] * 0.8f; hsv[2] = hsv[2] * 0.35f
            android.graphics.Color.HSVToColor(hsv)
        } catch (e: Exception) { android.graphics.Color.parseColor("#1A1A1A") }
    }

    private fun updateNotification() {
        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val nextIcon = if (isAdPlaying) android.R.drawable.ic_media_ff else android.R.drawable.ic_media_next
        val nextAction = if (isAdPlaying) ACTION_SKIP_AD else ACTION_NEXT

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, if(isAdPlaying) "⚠️ Advertisement" else "Infer Pinned Web")
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

        val openPending = PendingIntent.getActivity(this, 100, Intent(this, MainActivity::class.java).apply { action = ACTION_OPEN_PINNED; addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val customView = RemoteViews(packageName, resources.getIdentifier("layout_custom_notification", "layout", packageName))
        customView.setTextViewText(resources.getIdentifier("notifTitle", "id", packageName), if(isAdPlaying) "⚠️ Skip Ad Ready" else currentTitle)
        customView.setImageViewResource(resources.getIdentifier("notifPlayPause", "id", packageName), playPauseIcon)
        customView.setImageViewResource(resources.getIdentifier("notifNext", "id", packageName), nextIcon)

        if (artworkBitmap != null && !isAdPlaying) {
            customView.setImageViewBitmap(resources.getIdentifier("notifArt", "id", packageName), artworkBitmap)
            customView.setInt(resources.getIdentifier("notifRoot", "id", packageName), "setBackgroundColor", getDominantColor(artworkBitmap))
        } else {
            customView.setImageViewResource(resources.getIdentifier("notifArt", "id", packageName), android.R.drawable.ic_media_play)
            customView.setInt(resources.getIdentifier("notifRoot", "id", packageName), "setBackgroundColor", android.graphics.Color.parseColor("#1A1A1A"))
        }

        customView.setOnClickPendingIntent(resources.getIdentifier("notifPlayPause", "id", packageName), actionPending(ACTION_PLAY_PAUSE, 2))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifNext", "id", packageName), actionPending(nextAction, 3))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifPrev", "id", packageName), actionPending(ACTION_PREVIOUS, 1))
        customView.setOnClickPendingIntent(resources.getIdentifier("notifClose", "id", packageName), actionPending(ACTION_CLOSE, 4))

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setContentIntent(openPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isCurrentlyPlaying)
            .setStyle(androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle().setMediaSession(mediaSession?.sessionToken))
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun loadArtwork(url: String): Bitmap? {
        if (url.isBlank()) return null
        val videoId = Regex("(?:youtube\\.com/watch\\?[^#]*v=|youtu\\.be/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: return null
        return try {
            val original = java.net.URL("https://img.youtube.com/vi/$videoId/hqdefault.jpg").openConnection().apply {
                connectTimeout = 2500; readTimeout = 2500
            }.getInputStream().use { BitmapFactory.decodeStream(it) } ?: return null

            val result = Bitmap.createBitmap(original.width, original.height, original.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(original, 0f, 0f, null)
            val paint = android.graphics.Paint()
            paint.shader = android.graphics.LinearGradient(0f, original.height * 0.5f, 0f, original.height.toFloat(), android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#CC000000"), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, original.height * 0.5f, original.width.toFloat(), original.height.toFloat(), paint)
            result
        } catch (_: Exception) { null }
    }

    override fun onDestroy() { mediaSession?.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}