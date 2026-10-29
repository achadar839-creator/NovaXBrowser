import re

path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(path, "r", encoding="utf-8") as f:
    code = f.read()

new_payload = """view?.evaluateJavascript(\"\"\"
                    (function() {
                        if (window.novaxAdKiller) return;
                        window.novaxAdKiller = true;
                        
                        var s = document.createElement('style');
                        s.id = 'novax-premium-adblock';
                        s.innerHTML = '.ad-showing video { opacity: 0 !important; width: 0px !important; height: 0px !important; } .ytp-ad-overlay-container, ytd-ad-slot-renderer, ytp-ad-message-overlay, ytm-promoted-sparkles-web-renderer, .ytp-ad-persistent-progress-bar { display: none !important; }';
                        document.head.appendChild(s);
                        
                        function killAds() {
                            var v = document.querySelector('video');
                            /* 5-second unskippable ad bypass: Play at 16x speed and skip to end */
                            if (v && (document.querySelector('.ad-showing') || document.querySelector('.ytm-custom-ad'))) {
                                v.playbackRate = 16.0;
                                v.currentTime = v.duration || 9999;
                            }
                            /* Auto-click skip buttons */
                            var btn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-ad-skip-button, .ytp-ad-skip-button-slot');
                            if (btn) btn.click();
                        }
                        
                        var observer = new MutationObserver(killAds);
                        observer.observe(document.body, { childList: true, subtree: true });
                        setInterval(killAds, 500);
                    })();
\"\"\", null)"""

def replacer(match):
    # Only replace old adblock code, keep everything else safe
    if 'novax-premium-adblock' in match.group(0):
        return new_payload
    return match.group(0)

# Replace the old javascript payload with the new one
new_code = re.sub(r'view\?\.evaluateJavascript\(\"\"\"[\s\S]*?\"\"\"\s*,\s*null\)', replacer, code)

with open(path, "w", encoding="utf-8") as f:
    f.write(new_code)

print("\n✅ YouTube AdBlocker Successfully Upgraded to PRO (MutationObserver + 16x Speed Skip)!")
