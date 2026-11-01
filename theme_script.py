import re

# 1. HTML फाइल में रंग बदलने वाला जावास्क्रिप्ट फंक्शन जोड़ना
html_path = "app/src/main/assets/home.html"
try:
    with open(html_path, "r", encoding="utf-8") as f:
        html = f.read()
    if "setDynamicTheme" not in html:
        html = html.replace("function updateClock() {", "function setDynamicTheme(c) { document.documentElement.style.setProperty('--cyan', c); }\nfunction updateClock() {")
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html)
except Exception as e:
    print("HTML Update Error:", e)

# 2. MainActivity.kt में ऑटोमैटिक कलर एक्सट्रैक्टर जोड़ना
kt_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(kt_path, "r", encoding="utf-8") as f:
    data = f.read()

theme_code = """
    private var lastThemeBitmap: android.graphics.Bitmap? = null
    private fun applyDynamicTheme(bg: android.graphics.drawable.Drawable?) {
        val bitmap = when (bg) {
            is android.graphics.drawable.BitmapDrawable -> bg.bitmap
            else -> return
        }
        if (bitmap == lastThemeBitmap) return
        lastThemeBitmap = bitmap

        Thread {
            try {
                var r = 0L; var g = 0L; var b = 0L; var count = 0
                val step = Math.max(1, bitmap.width / 20)
                for (x in 0 until bitmap.width step step) {
                    for (y in 0 until bitmap.height step step) {
                        val pixel = bitmap.getPixel(x, y)
                        r += android.graphics.Color.red(pixel)
                        g += android.graphics.Color.green(pixel)
                        b += android.graphics.Color.blue(pixel)
                        count++
                    }
                }
                if (count == 0) return@Thread
                val avgColor = android.graphics.Color.rgb((r/count).toInt(), (g/count).toInt(), (b/count).toInt())
                
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(avgColor, hsv)
                hsv[1] = Math.max(0.5f, hsv[1])
                hsv[2] = Math.max(0.8f, hsv[2])
                val themeColor = android.graphics.Color.HSVToColor(hsv)
                val hexColor = String.format("#%06X", (0xFFFFFF and themeColor))

                runOnUiThread {
                    try {
                        findViewById<android.widget.TextView>(R.id.tvBrand)?.setTextColor(themeColor)
                        findViewById<android.widget.Button>(R.id.btnBack)?.setTextColor(themeColor)
                        findViewById<android.widget.Button>(R.id.btnForward)?.setTextColor(themeColor)
                        findViewById<android.widget.Button>(R.id.btnRefresh)?.setTextColor(themeColor)
                        findViewById<android.widget.Button>(R.id.btnTabs)?.setTextColor(themeColor)

                        val js = "javascript:if(typeof setDynamicTheme === 'function') { setDynamicTheme('" + hexColor + "'); }"
                        tabs.forEach { it.evaluateJavascript(js, null) }
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
        }.start()
    }
"""

if "applyDynamicTheme(" not in data:
    data = data[:data.rfind("}")] + theme_code + "\n}"

watcher_code = """
        // DYNAMIC THEME WATCHER
        findViewById<android.view.View>(R.id.browserRoot)?.let { root ->
            root.viewTreeObserver.addOnGlobalLayoutListener {
                applyDynamicTheme(root.background)
            }
        }
"""

if "DYNAMIC THEME WATCHER" not in data:
    data = re.sub(r"(setContentView\(.*?\))", r"\1\n" + watcher_code, data)
    with open(kt_path, "w", encoding="utf-8") as f:
        f.write(data)

print("✅ Success: Material You (Dynamic Theming) ऐप में जुड़ गया!")
