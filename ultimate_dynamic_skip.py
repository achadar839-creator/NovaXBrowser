import os
import re

# 1. कस्टम नोटिफिकेशन डिज़ाइन (XML) बनाएँ
xml_dir = "app/src/main/res/layout"
os.makedirs(xml_dir, exist_ok=True)
xml_file = os.path.join(xml_dir, "layout_custom_notification.xml")

xml_content = """<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/notifRoot"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:background="#1A1A1A"
    android:padding="8dp">

    <ImageView
        android:id="@+id/notifArt"
        android:layout_width="64dp"
        android:layout_height="64dp"
        android:layout_alignParentStart="true"
        android:layout_centerVertical="true"
        android:scaleType="centerCrop"
        android:src="@android:drawable/ic_media_play" />

    <TextView
        android:id="@+id/notifTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_toEndOf="@id/notifArt"
        android:layout_toStartOf="@id/controlsContainer"
        android:layout_centerVertical="true"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="8dp"
        android:ellipsize="marquee"
        android:singleLine="true"
        android:text="Pinned Web"
        android:textColor="#FFFFFF"
        android:textSize="14sp"
        android:textStyle="bold" />

    <LinearLayout
        android:id="@+id/controlsContainer"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_alignParentEnd="true"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <ImageButton
            android:id="@+id/notifPrev"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?android:attr/selectableItemBackground"
            android:src="@android:drawable/ic_media_previous"
            android:tint="#FFFFFF" />

        <ImageButton
            android:id="@+id/notifPlayPause"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:layout_marginStart="8dp"
            android:background="?android:attr/selectableItemBackground"
            android:src="@android:drawable/ic_media_play"
            android:tint="#FFFFFF" />

        <ImageButton
            android:id="@+id/notifNext"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:layout_marginStart="8dp"
            android:background="?android:attr/selectableItemBackground"
            android:src="@android:drawable/ic_media_next"
            android:tint="#FFFFFF" />

        <ImageButton
            android:id="@+id/notifClose"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:layout_marginStart="12dp"
            android:background="?android:attr/selectableItemBackground"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:tint="#FF5555" />
    </LinearLayout>
</RelativeLayout>"""

with open(xml_file, "w", encoding="utf-8") as f:
    f.write(xml_content)

# 2. BackgroundPlayService (थंबनेल + डायनामिक बटन के साथ)
srv_file = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
srv_code = '''package com.novax.browser

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
        val videoId = Regex("(?:youtube\\\\.com/watch\\\\?[^#]*v=|youtu\\\\.be/|youtube\\\\.com/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: return null
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
}'''

with open(srv_file, "w", encoding="utf-8") as f:
    f.write(srv_code)

# 3. MainActivity में सुरक्षित तरीके से इंजेक्शन (Idempotent)
main_file = "app/src/main/java/com/novax/browser/MainActivity.kt"
if os.path.exists(main_file):
    with open(main_file, "r", encoding="utf-8") as f:
        main_data = f.read()

    if "fun adStateChanged" not in main_data:
        js_interface = '''@android.webkit.JavascriptInterface
            fun adStateChanged(isAd: Boolean) {
                runOnUiThread {
                    try {
                        val i = android.content.Intent(this@MainActivity, BackgroundPlayService::class.java).apply {
                            putExtra("novax_is_ad_playing", isAd)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(i)
                        } else {
                            startService(i)
                        }
                    } catch(e: Exception){}
                }
            }
            @android.webkit.JavascriptInterface
            fun requestCustomization() {'''
        main_data = main_data.replace('@android.webkit.JavascriptInterface\n            fun requestCustomization() {', js_interface)

    if '"com.novax.browser.SKIP_AD"' not in main_data:
        main_data = main_data.replace('addAction(BackgroundPlayService.ACTION_OPEN_PINNED)', 'addAction(BackgroundPlayService.ACTION_OPEN_PINNED)\n            addAction("com.novax.browser.SKIP_AD")')

    if '"com.novax.browser.SKIP_AD" -> {' not in main_data:
        skip_logic = '''"com.novax.browser.SKIP_AD" -> {
                pinnedMusicTab?.evaluateJavascript("(function(){var s=document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');if(s){s.click();return 'skipped';}var o=document.querySelector('.ad-showing, .ytp-ad-player-overlay, ytm-promoted-video-renderer, ytm-companion-ad-renderer');if(o){var v=document.querySelector('video,audio');if(v&&v.duration){v.currentTime=v.duration-0.1;return 'ff';}}return 'no_ad';})()", null)
            }
            BackgroundPlayService.ACTION_NEXT, BackgroundPlayService.ACTION_PREVIOUS -> {'''
        main_data = main_data.replace('BackgroundPlayService.ACTION_NEXT, BackgroundPlayService.ACTION_PREVIOUS -> {', skip_logic)

    if 'novax_is_ad_playing' not in main_data and 'window.lastAdState' not in main_data:
        old_sync_regex = r'\(function\(\) \{\s*if\(window\.novaxSync\) return;.*?\n\s*\}\)\(\);'
        new_sync = '''(function() {
                        if(window.novaxSync) return;
                        window.novaxSync = true;
                        var lastUrl = location.href;
                        setInterval(function() {
                            if(location.href !== lastUrl) {
                                lastUrl = location.href;
                                document.title = document.title + ' 🎵';
                            }
                            try {
                                var isAd = !!document.querySelector('.ad-showing, .ytp-ad-player-overlay, .ytp-ad-skip-button, ytm-promoted-video-renderer, ytm-companion-ad-renderer');
                                if(window.lastAdState !== isAd) {
                                    window.lastAdState = isAd;
                                    if(window.NovaX && window.NovaX.adStateChanged) window.NovaX.adStateChanged(isAd);
                                }
                            } catch(e) {}
                        }, 500);
                    })();'''
        main_data = re.sub(old_sync_regex, new_sync, main_data, flags=re.DOTALL)

    with open(main_file, "w", encoding="utf-8") as f:
        f.write(main_data)

print("✅ Perfect Success! थंबनेल + डायनामिक बटन का सेटअप पूरा हो गया।")
