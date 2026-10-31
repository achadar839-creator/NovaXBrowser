import os
import re

xml_file = "app/src/main/res/layout/activity_main.xml"
kt_file = "app/src/main/java/com/novax/browser/MainActivity.kt"
manifest_file = "app/src/main/AndroidManifest.xml"
play_activity_file = "app/src/main/java/com/novax/browser/InferPlayActivity.kt"

# 1. XML से btnInferPlay वाला पूरा लेआउट हटाना
if os.path.exists(xml_file):
    with open(xml_file, "r", encoding="utf-8") as f:
        xml_data = f.read()
    
    xml_data = re.sub(r'<LinearLayout[^>]*android:id="@+id/btnInferPlay".*?</LinearLayout>', '', xml_data, flags=re.DOTALL)
    
    with open(xml_file, "w", encoding="utf-8") as f:
        f.write(xml_data)

# 2. MainActivity से प्लेयर खोलने वाला लॉजिक और Import हटाना
if os.path.exists(kt_file):
    with open(kt_file, "r", encoding="utf-8") as f:
        kt_data = f.read()
    
    # Import हटाना
    kt_data = re.sub(r'import com\.novax\.browser\.InferPlayActivity\n?', '', kt_data)
    
    # Click Listener हटाना
    pattern = r'findViewById<android\.view\.View>\(resources\.getIdentifier\("btnInferPlay", "id", packageName\)\)\?\.setOnClickListener\s*\{[^}]*\}'
    kt_data = re.sub(pattern, '', kt_data)
    
    with open(kt_file, "w", encoding="utf-8") as f:
        f.write(kt_data)

# 3. AndroidManifest से Activity हटाना
if os.path.exists(manifest_file):
    with open(manifest_file, "r", encoding="utf-8") as f:
        manifest_data = f.read()
    
    manifest_data = re.sub(r'<activity[^>]*android:name="\.InferPlayActivity"[^>]*>.*?</activity>', '', manifest_data, flags=re.DOTALL)
    manifest_data = re.sub(r'<activity[^>]*android:name="\.InferPlayActivity"[^>]*/>', '', manifest_data)
    
    with open(manifest_file, "w", encoding="utf-8") as f:
        f.write(manifest_data)

# 4. InferPlayActivity.kt फाइल को पूरी तरह डिलीट करना
if os.path.exists(play_activity_file):
    os.remove(play_activity_file)

print("✅ Success! Infer Play (Media Player) ऐप से पूरी तरह हटा दिया गया है।")
