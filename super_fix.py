import os

print("Analyzing and applying safe fixes...")

# 1. BackgroundPlayService.kt: Instant UI update for Play/Pause
bps_path = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
if os.path.exists(bps_path):
    with open(bps_path, "r", encoding="utf-8") as f:
        bps_code = f.read()

    target_action = "sendActionToBrowser(intent.action!!)"
    if "isCurrentlyPlaying = !isCurrentlyPlaying" not in bps_code and target_action in bps_code:
        replacement_action = target_action + """
                if (intent.action == ACTION_PLAY_PAUSE) {
                    isCurrentlyPlaying = !isCurrentlyPlaying
                    updateNotification()
                }"""
        bps_code = bps_code.replace(target_action, replacement_action)
        with open(bps_path, "w", encoding="utf-8") as f:
            f.write(bps_code)
        print("✅ BackgroundPlayService: Instant Play/Pause sync applied.")
    else:
        print("ℹ️ BackgroundPlayService: Play/Pause sync already present.")

# 2. activity_main.xml: Safely inject Infer Play card
xml_path = "app/src/main/res/layout/activity_main.xml"
if os.path.exists(xml_path):
    with open(xml_path, "r", encoding="utf-8") as f:
        xml_code = f.read()

    if "btnInferPlay" not in xml_code:
        idx = xml_code.find('"@+id/btnUiToggle"')
        if idx != -1:
            start = xml_code.rfind('<Button', 0, idx)
            if start != -1:
                btn_code = '\n    <LinearLayout android:id="@+id/btnInferPlay" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginHorizontal="12dp" android:layout_marginBottom="80dp" android:background="#0A1F0A" android:padding="12dp" android:gravity="center" android:clickable="true" android:focusable="true"><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="▶ Infer Play (Premium Media)" android:textColor="#00FF00" android:textStyle="bold" android:textSize="16sp"/></LinearLayout>\n'
                xml_code = xml_code[:start] + btn_code + xml_code[start:]
                with open(xml_path, "w", encoding="utf-8") as f:
                    f.write(xml_code)
                print("✅ XML Layout: Infer Play Card safely injected.")

# 3. MainActivity.kt: Connect Infer Play Button
main_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
if os.path.exists(main_path):
    with open(main_path, "r", encoding="utf-8") as f:
        main_code = f.read()

    if "btnInferPlay" not in main_code:
        target_view = "setContentView"
        idx = main_code.find(target_view)
        if idx != -1:
            eol = main_code.find('\n', idx)
            if eol != -1:
                logic_code = '\n        findViewById<android.view.View>(resources.getIdentifier("btnInferPlay", "id", packageName))?.setOnClickListener { startActivity(android.content.Intent(this@MainActivity, InferPlayActivity::class.java)) }'
                main_code = main_code[:eol] + logic_code + main_code[eol:]
                with open(main_path, "w", encoding="utf-8") as f:
                    f.write(main_code)
                print("✅ MainActivity: Infer Play button click listener connected.")

print("🎯 All precise fixes completed!")
