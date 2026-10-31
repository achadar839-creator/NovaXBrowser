import os

xml_file = "app/src/main/res/layout/activity_main.xml"
kt_file = "app/src/main/java/com/novax/browser/MainActivity.kt"

# 1. XML में बटन का नाम बदलें
if os.path.exists(xml_file):
    with open(xml_file, "r", encoding="utf-8") as f:
        xml_data = f.read()
    
    xml_data = xml_data.replace("📌 Pin Audio", "📌 Pin Web")
    
    with open(xml_file, "w", encoding="utf-8") as f:
        f.write(xml_data)

# 2. Kotlin कोड में टोस्ट मैसेज और लॉजिक बदलें
if os.path.exists(kt_file):
    with open(kt_file, "r", encoding="utf-8") as f:
        kt_data = f.read()
    
    kt_data = kt_data.replace("📌 Pin Audio", "📌 Pin Web")
    kt_data = kt_data.replace("🎵 Audio pinned:", "📌 Website pinned:")
    kt_data = kt_data.replace("Infer Browser Audio", "Infer Browser Pinned Web")
    kt_data = kt_data.replace("पहले Pin Audio दबाएँ", "पहले Pin Web दबाएँ")
    kt_data = kt_data.replace("Pin Audio is a Premium feature", "Pin Web is a Premium feature")
    
    with open(kt_file, "w", encoding="utf-8") as f:
        f.write(kt_data)

print("✅ Success! 'Pin Audio' को बदलकर 'Pin Web' कर दिया गया है।")
