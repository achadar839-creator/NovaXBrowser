import re

path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(path, "r", encoding="utf-8") as f:
    code = f.read()

# 1000% God-Mode AdBlocker (Premium Only)
new_payload = """view?.evaluateJavascript(\"\"\"
                    (function() {
                        if (window.godModeAdBlock) return;
                        window.godModeAdBlock = true;
                        
                        /* Level 1: Hide all known YouTube Ad Containers on Mobile & Desktop */
                        var s = document.createElement('style');
                        s.innerHTML = 'ytm-ad-player-overlay-renderer, ytm-promoted-sparkles-web-renderer, .ytp-ad-overlay-container, ytm-companion-ad-renderer, .ytp-ad-message-overlay, ytm-promoted-video-renderer, ytd-ad-slot-renderer, .ytp-ad-player-overlay, .ad-showing { display: none !important; opacity: 0 !important; width: 0 !important; height: 0 !important; pointer-events: none !important; }';
                        document.head.appendChild(s);
                        
                        function annihilateAds() {
                            var v = document.querySelector('video');
                            var isAd = document.querySelector('ytm-ad-player-overlay-renderer, .ad-showing, .ytp-ad-player-overlay, .ytm-custom-ad');
                            
                            /* Level 2: Fast-forward & Mute Unskippable Video Ads */
                            if (v && isAd) {
                                v.playbackRate = 16.0;
                                v.muted = true;
                                if (v.duration && !isNaN(v.duration)) {
                                    v.currentTime = v.duration;
                                }
                            }
                            
                            /* Level 3: Auto-click any skip button instantly */
                            var btns = document.querySelectorAll('.ytm-ad-skip-button, .ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern');
                            btns.forEach(function(b) { b.click(); });
                        }
                        
                        /* Run check every 100 milliseconds (Insanely fast) */
                        setInterval(annihilateAds, 100);
                        
                        /* Run check whenever DOM changes */
                        var obs = new MutationObserver(annihilateAds);
                        if (document.body) {
                            obs.observe(document.body, { childList: true, subtree: true });
                        }
                    })();
\"\"\", null)"""

# This regex only replaces the previous ad-blocker JS, keeping the Kotlin premium check 100% safe
code = re.sub(r'view\?\.evaluateJavascript\(\"\"\"[\s\S]*?(novaxAdKiller|ultimateAdKiller)[\s\S]*?\"\"\"\s*,\s*null\)', new_payload, code)

with open(path, "w", encoding="utf-8") as f:
    f.write(code)

print("\n✅ 1000% God-Mode AdBlocker Installed Successfully!")
