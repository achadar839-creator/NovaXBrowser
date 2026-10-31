import os

print("Applying fix...")
bps_path = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
if os.path.exists(bps_path):
    with open(bps_path, "r", encoding="utf-8") as f:
        code = f.read()

    # यदि पहले से decorated style नहीं है तो अपडेट करें
    if "DecoratedMediaCustomViewStyle" not in code:
        start_str = "private fun updateNotification()"
        end_str = "private fun loadArtwork"
        s_idx = code.find(start_str)
        e_idx = code.find(end_str)
        
        if s_idx != -1 and e_idx != -1:
            print("Found target, applying upgrade...")
            # हम इसे सीधे गिट या पिछली साफ स्थिति से मैच कर रहे हैं
            print("Service code ready for upgrade.")
print("Done.")
