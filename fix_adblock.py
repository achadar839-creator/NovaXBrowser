import os

target_files = ["main.py", "browser.py", "app.py", "inject.py"]
found_file = None
for f in target_files:
    if os.path.exists(f):
        found_file = f
        break

if found_file:
    print(f"📁 मिल गई ब्राउज़र फाइल: {found_file}")
    with open(found_file, "r", encoding="utf-8") as file:
        content = file.read()

    ad_block_js = """
// Auto-injected Ad Blocker for Infer Browser
(function() {
    const observer = new MutationObserver((mutations, obs) => {
        const adElements = document.querySelectorAll(".ad-banner, .advertisement, iframe[src*=\\"ad\\"], div[id*=\\"google_ads\\"]");
        if (adElements.length > 0) {
            adElements.forEach((el, index) => {
                if (index > 0 || adElements.length >= 2) {
                    el.remove();
                }
            });
        }
    });
    observer.observe(document, { childList: true, subtree: true });
})();
"""

    if "MutationObserver" not in content:
        with open(found_file, "a", encoding="utf-8") as file:
            file.write("\n# --- Injected Ad Blocker Fix ---\n" + ad_block_js + "\n")
        print("✅ ऐड-ब्लॉकिंग स्क्रिप्ट 'Infer Browser' में सफलतापूर्वक इंजेक्ट हो गई!")
    else:
        print("⚡ ऐड-ब्लॉकर पहले से मौजूद है फाइल में।")
else:
    print("❌ फोल्डर में कोई पाइथन ब्राउज़र फाइल नहीं मिली। यहाँ की फाइलें चेक करते हैं:")
    print(os.listdir("."))
