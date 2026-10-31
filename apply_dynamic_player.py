import os

print("🚀 Upgrading BackgroundPlayService with 2030 Dynamic Player...")

bps_path = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
if os.path.exists(bps_path):
    with open(bps_path, "r", encoding="utf-8") as f:
        code = f.read()

    start_str = "private fun updateNotification()"
    end_str = "private fun loadArtwork"
    s_idx = code.find(start_str)
    e_idx = code.find(end_str)

    if s_idx != -1 and e_idx != -1:
        new_logic = """private fun getDominantColor(bitmap: android.graphics.Bitmap?): Int {
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

    """
        code = code[:s_idx] + new_logic + code[e_idx:]
        with open(bps_path, "w", encoding="utf-8") as f:
            f.write(code)
        print("✅ BackgroundPlayService successfully upgraded!")
    else:
        print("❌ Insertion error!")
else:
    print("❌ Service file not found!")
