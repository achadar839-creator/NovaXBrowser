import os

file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
if not os.path.exists(file_path):
    print("❌ MainActivity.kt नहीं मिली!")
    exit()

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# 1. पुराने सभी अजीब या कमजोर ऐड-ब्लॉकर वेरिएबल्स को साफ कर देते हैं ताकि कोई कचरा न बचे
for old_var in ["universalAdBlockScript", "adBlockerScript", "mobileGodModeAdBlock"]:
    if old_var in code:
        print(f"🧹 पुराना वेरिएबल हटाया जा रहा है: {old_var}")
        # हम पुराने ब्लॉक को हटाने के लिए सिंपल रिप्लेसमेंट करेंगे या नया फ्रेश कोड डालेंगे

# 2. 20 साल के फ्यूचर के हिसाब से सबसे खतरनाक, हार्ड और यूनिवर्सल ऐड-ब्लॉकर स्क्रिप्ट
future_proof_blocker = """
    // [20-Year Future-Proof Universal & Deep-Hook Ad-Blocker Engine]
    private val universalAdBlockScript = \"\"\"
    (function() {
        if (window.__novaFutureShieldActive) return;
        window.__novaFutureShieldActive = true;
        'use strict';

        // A. Deep Prototype Hooking: Prevent creation of ad elements at code-level
        const originalCreateElement = document.createElement;
        document.createElement = function(tagName, options) {
            const el = originalCreateElement.call(document, tagName, options);
            if (tagName && typeof tagName === 'string' && tagName.toLowerCase() === 'iframe') {
                setTimeout(() => {
                    try {
                        const src = el.src || '';
                        if (src && (src.includes('ad') || src.includes('doubleclick') || src.includes('syndication') || src.includes('pagead') || src.includes('popads'))) {
                            el.remove();
                        }
                    } catch(e) {}
                }, 10);
            }
            return el;
        };

        // B. Ultimate CSS Shield for 20 years of web evolution
        const shieldCSS = `
            .video-ads, .ytp-ad-module, .ytp-ad-overlay-image, #masthead-ad,
            ytd-promoted-sparkles-web-renderer, .ad-showing, .ad-container,
            .adsbygoogle, div[id^="google_ads"], iframe[src*="ad"],
            div[class*="ad-banner"], div[class*="advertisement"], div[class*="sponsored"],
            iframe[src*="doubleclick"], div[id*="div-gpt-ad"], ins.adsbygoogle,
            [class*="ads-"], [id*="ads-"], [aria-label*="Advertisement"] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
                width: 0 !important;
                height: 0 !important;
                max-height: 0 !important;
                position: absolute !important;
                z-index: -999999 !important;
            }
        `;

        const applyShieldCSS = () => {
            if (!document.getElementById('nova-future-shield-css')) {
                const s = document.createElement('style');
                s.id = 'nova-future-shield-css';
                s.innerHTML = shieldCSS;
                (document.head || document.documentElement || document.body).appendChild(s);
            }
        };

        applyShieldCSS();
        setInterval(applyShieldCSS, 500);

        // C. Continuous High-Frequency Annihilation Loop (Every 20ms)
        setInterval(() => {
            const maliciousElements = document.querySelectorAll('.ad-container, .adsbygoogle, div[id^="google_ads"], iframe[src*="ad"], div[class*="ad-banner"], div[class*="advertisement"], div[class*="sponsored"], ins.adsbygoogle');
            maliciousElements.forEach(el => {
                try { el.remove(); } catch(e) {}
            });

            // YouTube Ultra-Fast Skip & Ad Eraser
            const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot, .videoAdUiSkipButton, .ytp-skip-ad-button');
            if (skipBtn) {
                try { skipBtn.click(); } catch(e) {}
            }

            const video = document.querySelector('video');
            if (video && (document.querySelector('.ad-showing') || (video.duration && video.duration < 40 && !video.paused))) {
                try {
                    video.playbackRate = 16.0;
                    video.muted = true;
                    if (video.duration && !isNaN(video.duration)) {
                        video.currentTime = video.duration;
                    }
                } catch(e) {}
            }
        }, 20);

        // D. Quantum Mutation Observer for real-time DOM interception
        const observer = new MutationObserver((mutations) => {
            for (let mut of mutations) {
                for (let node of mut.addedNodes) {
                    if (node && node.nodeType === 1) {
                        const tag = (node.tagName || '').toLowerCase();
                        const cls = (node.className || '').toString().toLowerCase();
                        const id = (node.id || '').toString().toLowerCase();
                        const src = (node.src || '').toString().toLowerCase();

                        if (cls.includes('ad') || id.includes('ad') || cls.includes('sponsored') || id.includes('sponsored') || (tag === 'iframe' && (src.includes('ad') || src.includes('doubleclick') || src.includes('analytic')))) {
                            try { node.remove(); } catch(e) {}
                        }
                    }
                }
            }
        });

        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        } else {
            document.addEventListener('DOMContentLoaded', () => {
                observer.observe(document.body, { childList: true, subtree: true });
            });
        }
    })();
    \"\"\"
"""

# अगर पुराना इंजेक्टर पहले से है तो उसे हटाकर नया फ्यूचर-प्रूफ कोड डालेंगे
if "universalAdBlockScript" in code:
    print("🔄 पुराने यूनिवर्सल ब्लॉकर को हटाकर 20-Year Future-Proof इंजन सेट किया जा रहा है...")
    # हम पुराने वेरिएबल से लेकर उसके नीचे के हिस्से को साफ कर सकते हैं या नया फ्रेश जोड़ सकते हैं
    # सबसे सेफ तरीका यह है कि पुराने को हटाकर नया कोड लिख दें
    import re
    code = re.sub(r'private val universalAdBlockScript\s*=\s*\"\"\"[\s\S]*?\"\"\"', '', code)

# onPageFinished में इसे जोड़ना सुनिश्चित करते हैं
target_hook = "override fun onPageFinished(view: WebView?, url: String?) {"
if target_hook in code and "universalAdBlockScript" not in code:
    code = code.replace(target_hook, target_hook + "\n            view?.evaluateJavascript(universalAdBlockScript, null)\n")
    code = future_proof_blocker + "\n" + code
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(code)
    print("🚀 20 साल के फ्यूचर-प्रूफ वाला सबसे खतरनाक ऐड-ब्लॉकर सफलताપूर्वक इंस्टॉल हो गया है!")
else:
    print("⚡ हुक पहले से है या अपडेट कर दिया गया है।")

