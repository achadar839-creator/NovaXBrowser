import re

kt_file = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(kt_file, "r") as f:
    code = f.read()

# 1. VIP button dabane par Video Ad ki jagah tumhari website khulwayein
target1 = "val ad = rewardedAd"
replacement1 = """android.widget.Toast.makeText(this, "Redirecting to Universal Media Extractor...", android.widget.Toast.LENGTH_LONG).show()
        createNewTab("https://universal-media-extractor-8pux.onrender.com")
        return
        val ad = rewardedAd"""
code = code.replace(target1, replacement1)

# 2. Browser ko secret link (infer://unlock-vip) pehchanna sikhayein
target2 = "override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {"
replacement2 = """override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqStr = request?.url?.toString() ?: ""
                if (reqStr.startsWith("infer://unlock-vip")) {
                    val premiumUntil = System.currentTimeMillis() + 2L * 60L * 60L * 1000L
                    premiumPrefs.edit().putLong("premium_until", premiumUntil).putBoolean("premium_unlocked", true).apply()
                    android.widget.Toast.makeText(this@MainActivity, "👑 Premium Unlocked for 2 Hours!", android.widget.Toast.LENGTH_LONG).show()
                    updateUI()
                    if (view is KeepAliveWebView) closeTab(view)
                    return true
                }"""
code = code.replace(target2, replacement2)

with open(kt_file, "w") as f:
    f.write(code)

print("✅ SUCCESS: Universal Media Extractor is now connected to Infer VIP System!")
