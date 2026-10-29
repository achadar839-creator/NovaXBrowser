import os

file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
if not os.path.exists(file_path):
    print("❌ MainActivity.kt नहीं मिली!")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# यूनिवर्सल ऐड-ब्लॉकर स्क्रिप्ट जो हर वेबपेज पर ऑटो-इंजेक्ट होगी
universal_blocker_snippet = """
    private val universalAdBlockScript = \"\"\"
    (function() {
        if (window.hasUniversalAdBlocker) return;
        window.hasUniversalAdBlocker = true;
        'use strict';
        const adCSS = `
            .video-ads, .ytp-ad-module, .ytp-ad-overlay-image, #masthead-ad,
            ytd-promoted-sparkles-web-renderer, .ad-showing, .ad-container,
            .adsbygoogle, div[id^="google_ads"], iframe[src*="ad"],
            div[class*="ad-banner"], div[class*="advertisement"], div[class*="sponsored"],
            iframe[src*="doubleclick"], div[id*="div-gpt-ad"] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
                width: 0 !important;
                height: 0 !important;
                max-height: 0 !important;
            }
        `;
        const injectStyle = () => {
            if (!document.getElementById('novax-universal-ad-css')) {
                const style = document.createElement('style');
                style.id = 'novax-universal-ad-css';
                style.innerHTML = adCSS;
                (document.head || document.documentElement).appendChild(style);
            }
        };
        injectStyle();
        setInterval(injectStyle, 1000);

        setInterval(() => {
            const ads = document.querySelectorAll('.ad-container, .adsbygoogle, div[id^="google_ads"], iframe[src*="ad"], div[class*="ad-banner"], div[class*="advertisement"], div[class*="sponsored"]');
            ads.forEach(el => el.remove());

            const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot, .videoAdUiSkipButton');
            if (skipBtn) skipBtn.click();

            const video = document.querySelector('video');
            if (video && (document.querySelector('.ad-showing') || video.duration < 30)) {
                video.playbackRate = 16.0;
                video.muted = true;
                try { if (video.duration && !isNaN(video.duration)) { video.currentTime = video.duration; } } catch(e) {}
            }
        }, 50);

        const observer = new MutationObserver((mutations) => {
            for (let mutation of mutations) {
                for (let node of mutation.addedNodes) {
                    if (node && node.nodeType === 1) {
                        const tag = node.tagName ? node.tagName.toLowerCase() : '';
                        const cls = node.className ? node.className.toString().toLowerCase() : '';
                        const id = node.id ? node.id.toString().toLowerCase() : '';
                        const src = node.src ? node.src.toLowerCase() : '';
                        if (cls.includes('ad') || id.includes('ad') || (tag === 'iframe' && (src.includes('ad') || src.includes('doubleclick') || src.includes('syndication')))) {
                            node.remove();
                        }
                    }
                }
            }
        });
        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        }
    })();
    \"\"\"
"""

if "universalAdBlockScript" not in code:
    target = "override fun onPageFinished(view: WebView?, url: String?) {"
    if target in code:
        code = code.replace(target, target + "\n            view?.evaluateJavascript(universalAdBlockScript, null)\n")
        code = universal_blocker_snippet + "\n" + code
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(code)
        print("✅ यूनिवर्सल परमानेंट ऐड-ब्लॉकर सफलतापूर्वक MainActivity.kt में जुड़ गया है!")
    else:
        print("❌ onPageFinished हुक नहीं मिला फाइल में।")
else:
    print("⚡ यूनिवर्सल ऐड-ब्लॉकर पहले से मौजूद है।")
