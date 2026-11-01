import re

file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    data = f.read()

# MainActivity में जहाँ ब्रॉडकास्ट रिसीवर ACTION_PLAY_PAUSE सुन रहा है, वहीं ACTION_NEXT का कोड जोड़ना
target = """                BackgroundPlayService.ACTION_PLAY_PAUSE -> {
                    findViewById<android.widget.Button>(R.id.btnMiniPlayPause).performClick()
                }"""

new_code = target + """
                BackgroundPlayService.ACTION_NEXT -> {
                    // नोटिफिकेशन का Next बटन दबाने पर मिनी प्लेयर का Next बटन भी ट्रिगर होगा
                    findViewById<android.widget.Button>(R.id.btnMiniNext).performClick()
                }"""

if "BackgroundPlayService.ACTION_NEXT ->" not in data:
    data = data.replace(target, new_code)
    
    # IntentFilter में ACTION_NEXT जोड़ना ताकि MainActivity इसे सुन सके
    filter_target = """        filter.addAction(BackgroundPlayService.ACTION_PLAY_PAUSE)
        filter.addAction(BackgroundPlayService.ACTION_CLOSE)"""
        
    new_filter = filter_target + """
        filter.addAction(BackgroundPlayService.ACTION_NEXT)"""
        
    data = data.replace(filter_target, new_filter)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(data)
    print("✅ Success: Notification 'Next' button is now linked to the Mini-Player 'Next' button!")
else:
    print("⚠️ Next button is already linked in MainActivity.")
