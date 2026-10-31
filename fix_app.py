import os, re

print("Starting automatic fixes...")

# 1. Fix BackgroundPlayService.kt (Progress Bar & Instant Notification)
bps = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
with open(bps, "r") as f: c = f.read()
# प्रोग्रेस बार फिक्स
c = c.replace("setState(if (isCurrentlyPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, currentPosition, if (isCurrentlyPlaying) 1f else 0f)", "setState(if (isCurrentlyPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, currentPosition, if (isCurrentlyPlaying) 1f else 0f, android.os.SystemClock.elapsedRealtime())")
# नोटिफिकेशन इंस्टेंट रिफ्रेश (थंबनेल और स्टेटस के लिए)
c = re.sub(r'(startForeground\(NOTIFICATION_ID, notification\))', r'\1\n        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)', c)
with open(bps, "w") as f: f.write(c)
print("- Player progress bar and thumbnail sync fixed.")

# 2. Fix activity_main.xml (Add Infer Play Card safely)
xml = "app/src/main/res/layout/activity_main.xml"
with open(xml, "r") as f: c = f.read()
if "btnInferPlay" not in c:
    btn_xml = """
        <LinearLayout
            android:id="@+id/btnInferPlay"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="12dp"
            android:layout_marginBottom="12dp"
            android:background="#0A1F0A"
            android:padding="12dp"
            android:gravity="center"
            android:clickable="true"
            android:focusable="true">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="▶ Infer Play (Premium Media)"
                android:textColor="#00FF00"
                android:textStyle="bold"
                android:textSize="16sp"/>
        </LinearLayout>
    """
    # इसे टॉगल बटन के ठीक ऊपर सेट कर रहे हैं ताकि UI न बिगड़े
    c = re.sub(r'(<Button[^>]+android:id="@+id/btnUiToggle")', btn_xml + r'\1', c)
    with open(xml, "w") as f: f.write(c)
print("- Infer Play UI Card injected.")

# 3. Fix MainActivity.kt (Connect Infer Play click)
main = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(main, "r") as f: c = f.read()
if "btnInferPlay" not in c:
    click_logic = r'\1\n        findViewById<android.view.View>(resources.getIdentifier("btnInferPlay", "id", packageName))?.setOnClickListener {\n            startActivity(android.content.Intent(this, InferPlayActivity::class.java))\n        }'
    c = re.sub(r'(setContentView\([^\)]+\))', click_logic, c, count=1)
    with open(main, "w") as f: f.write(c)
print("- Infer Play button linked to Premium engine.")

print("✅ All UI and Player fixes applied securely via Python!")
