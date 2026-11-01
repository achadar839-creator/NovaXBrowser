import re

file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    data = f.read()

# Kotlin के ब्रैकेट {} को सही से मैच करने का फंक्शन (ताकि कोड क्रैश न हो)
def replace_block_exact(text, marker, replacement):
    idx = text.find(marker)
    if idx == -1: return text
    start_brace = text.find("{", idx)
    if start_brace == -1: return text
    brace_count = 1
    end_brace = start_brace + 1
    while brace_count > 0 and end_brace < len(text):
        if text[end_brace] == '{': brace_count += 1
        elif text[end_brace] == '}': brace_count -= 1
        end_brace += 1
    old_block = text[idx:end_brace]
    return text.replace(old_block, replacement)

# 1. नोटिफिकेशन से ACTION_CLOSE आने पर टैब डिलीट होने से बचाना
action_close_marker = "BackgroundPlayService.ACTION_CLOSE ->"
action_close_new = """BackgroundPlayService.ACTION_CLOSE -> {
                    // सिर्फ वीडियो/ऑडियो पॉज करें (टैब डिलीट न करें)
                    val webView = findViewById<android.widget.FrameLayout>(R.id.webContainer)?.getChildAt(0) as? android.webkit.WebView
                    webView?.evaluateJavascript("var m = document.querySelectorAll('video, audio'); m.forEach(function(v) { v.pause(); });", null)
                    
                    // मिनी-प्लेयर और प्रोग्रेस बार छुपा दें
                    findViewById<android.view.View>(R.id.miniPlayerBar)?.visibility = android.view.View.GONE
                    findViewById<android.view.View>(R.id.progressBar)?.visibility = android.view.View.GONE
                }"""
data = replace_block_exact(data, action_close_marker, action_close_new)

# 2. ऐप के अंदर मिनी-प्लेयर के क्लोज (✖) बटन को भी फिक्स करना
idx_btn = data.find("R.id.btnMiniClose")
if idx_btn != -1:
    idx_click = data.find("setOnClickListener", idx_btn)
    if idx_click != -1:
        start_brace = data.find("{", idx_click)
        if start_brace != -1:
            brace_count = 1
            end_brace = start_brace + 1
            while brace_count > 0 and end_brace < len(data):
                if data[end_brace] == '{': brace_count += 1
                elif data[end_brace] == '}': brace_count -= 1
                end_brace += 1
            old_block = data[idx_click:end_brace]
            
            new_block = """setOnClickListener {
            // नोटिफिकेशन सर्विस बंद करें
            val stopIntent = android.content.Intent(this@MainActivity, BackgroundPlayService::class.java).apply { action = BackgroundPlayService.ACTION_CLOSE }
            startService(stopIntent)
            
            // टैब बचाए रखें, सिर्फ प्लेयर छुपाएं और वीडियो पॉज़ करें
            val webView = findViewById<android.widget.FrameLayout>(R.id.webContainer)?.getChildAt(0) as? android.webkit.WebView
            webView?.evaluateJavascript("var m = document.querySelectorAll('video, audio'); m.forEach(function(v) { v.pause(); });", null)
            findViewById<android.view.View>(R.id.miniPlayerBar)?.visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.progressBar)?.visibility = android.view.View.GONE
        }"""
            data = data.replace(old_block, new_block)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(data)
print("✅ Tab Fix Applied: अब स्टॉप करने पर सिर्फ प्लेयर छुपेगा, आपका वेब-टैब बिल्कुल सुरक्षित रहेगा!")
