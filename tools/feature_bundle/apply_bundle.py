from pathlib import Path
from datetime import datetime
import shutil

root = Path.cwd()
main = root / "app/src/main/java/com/novax/browser/MainActivity.kt"
bundle = root / "tools/feature_bundle"
backup_dir = bundle / "backups"
backup_dir.mkdir(parents=True, exist_ok=True)

if not main.exists():
    raise SystemExit("ERROR: MainActivity.kt nahi mili; koi change nahi kiya.")

src = main.read_text(encoding="utf-8")
original = src
changes = []

def replace_once(old, new, label):
    global src
    count = src.count(old)
    if count == 1:
        src = src.replace(old, new, 1)
        changes.append("READY: " + label)
    elif count == 0:
        changes.append("SKIPPED (pattern not found): " + label)
    else:
        changes.append(f"SKIPPED (pattern appears {count} times): {label}")

# 1. Fix intent fallback navigation.
replace_once(
    'else intent.getStringExtra("browser_fallback_url")?.let { loadUrl(it) }',
    'else intent.getStringExtra("browser_fallback_url")?.let { view?.loadUrl(it) }',
    "External intent fallback"
)

# 2. Do not clear shared profile data when closing an incognito tab.
replace_once(
    """            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()""",
    """            // Shared WebView profile: avoid deleting normal tabs' global data.
            // Full isolation requires a separate WebView profile.""",
    "Incognito global-data cleanup safety"
)

# 3. Avoid adult-blocker false positives in arbitrary URL text.
replace_once(
    'if (adultDomains.any { host.contains(it) } || reqUrl.contains("porn", true)) {',
    'if (adultDomains.any { host == it || host.endsWith(".$it") }) {',
    "Adult domain hostname matching"
)

# 4. Allow only the exact HTTP URL approved once.
replace_once(
    """                if (strictHttpsWarning && request.url?.scheme.equals("http", true) &&
                    host.isNotBlank() && !host.endsWith(".localhost") && host != "localhost") {""",
    """                val approvedHttpUrl = allowHttpOnce == reqUrl
                if (approvedHttpUrl) allowHttpOnce = null
                if (strictHttpsWarning && !approvedHttpUrl &&
                    request.url?.scheme.equals("http", true) &&
                    host.isNotBlank() && !host.endsWith(".localhost") && host != "localhost") {""",
    "HTTP one-time bypass check"
)

replace_once(
    '.setPositiveButton("फिर भी खोलें") { _, _ -> view?.loadUrl(reqUrl) }',
    '.setPositiveButton("फिर भी खोलें") { _, _ -> allowHttpOnce = reqUrl; view?.loadUrl(reqUrl) }',
    "HTTP approval action"
)

# Save a review copy and a change report first.
review_file = bundle / "MainActivity.review.kt"
report = bundle / "PATCH_RESULT.txt"
review_file.write_text(src, encoding="utf-8")

report.write_text(
    "NovaX Browser combined patch preparation\n"
    "Mode: REVIEW COPY ONLY — original MainActivity.kt not overwritten\n"
    f"Prepared: {datetime.now().isoformat(timespec='seconds')}\n\n"
    + "\n".join(changes)
    + "\n\nReview copy: tools/feature_bundle/MainActivity.review.kt\n"
    "This script does not activate staged registry flags, build an APK, or push to GitHub.\n",
    encoding="utf-8"
)

print(report.read_text(encoding="utf-8"))
print("Original MainActivity.kt unchanged:", main.read_text(encoding="utf-8") == original)
print("Review copy created:", review_file.exists())
