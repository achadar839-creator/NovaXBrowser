path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(path, "r", encoding="utf-8") as f:
    code = f.read()

adblock_code = """
    private fun applyPremiumAdBlocker(view: WebView?) {
        if (isPremiumActive()) {
            view?.evaluateJavascript(\"\"\"
                (function() {
                    if (!document.getElementById('novax-premium-adblock')) {
                        var s = document.createElement('style');
                        s.id = 'novax-premium-adblock';
                        s.innerHTML = '.video-ads, .ytp-ad-module, .ytp-ad-overlay-image, #masthead-ad, ytd-promoted-sparkles-web-renderer, .ad-showing { display: none !important; }';
                        document.head.appendChild(s);
                    }
                    setInterval(() => {
                        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot');
                        if (skipBtn) {
                            skipBtn.click();
                        }
                        const video = document.querySelector('video');
                        if (video && document.querySelector('.ad-showing')) {
                            video.playbackRate = 16.0;
                            video.muted = true;
                        }
                    }, 100);
                })();
            \"\"\".trimIndent(), null)
        }
    }
"""

if "applyPremiumAdBlocker" not in code:
    code = code.rstrip()[:-1] + "\n" + adblock_code + "\n}\n"
    with open(path, "w", encoding="utf-8") as f:
        f.write(code)
    print("SUCCESS: Super Fast 0.1s AdBlocker injected into core!")
else:
    print("AdBlocker already exists in core.")
