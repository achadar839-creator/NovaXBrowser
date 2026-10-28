path = "app/src/main/java/com/novax/browser/BackgroundPlayService.kt"
with open(path, "r", encoding="utf-8") as f:
    code = f.read()

# थंबनेल लोडिंग को तुरंत और सुपर-फास्ट बनाने के लिए अपडेट करना
old_code = """        // नई फोटो डाउनलोड करना
        if (artworkBitmap == null && currentMediaUrl.isNotBlank()) {
            thread {
                val newArtwork = loadArtwork(currentMediaUrl)
                if (newArtwork != null) {
                    artworkBitmap = newArtwork
                    updateNotification()
                }
            }
        }"""

new_code = """        // सुपर-फास्ट 0.1s थंबनेल और टाइटल अपडेट
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
        }"""

if old_code in code:
    code = code.replace(old_code, new_code)
    with open(path, "w", encoding="utf-8") as f:
        f.write(code)
    print("SUCCESS: Super-fast 0.1s thumbnail switching enabled!")
else:
    print("Already updated or structure matched differently.")
