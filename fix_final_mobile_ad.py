import re

path = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(path, "r", encoding="utf-8") as f:
    code = f.read()

# 1000% Bulletproof Mobile YouTube Ad Killer (Strictly Mobile m.youtube.com elements)
new_payload = """view?.evaluateJavascript(\"\"\"
                    (function() {
                        if (window.mobileGodModeAdBlock) return;
                        window.mobileGodModeAdBlock = true;
                        
                        /* Ultimate CSS Annihilation for Mobile YouTube (m.youtube.com) */
                        var style = document.createElement('style');
                        style.innerHTML = `
                            ytm-promoted-sparkles-web-renderer, 
                            ytm-ad-player-overlay-renderer, 
                            .ytp-ad-overlay-container, 
                            ytm-companion-ad-renderer, 
                            .ytp-ad-message-overlay, 
                            ytm-promoted-video-renderer, 
                            .sparkles-light-cta,
                            div[class*="ad-container"],
                            div[class*="promoted-"],
                            ytd-display-ad-renderer {
                                display: none !important;
                                opacity: 0 !important;
                                width: 0 !important;
                                height: 0 !important;
                                pointer-events: none !important;
                                visibility: hidden !important;
                            }
                        `;
                        document.head.appendChild(style);
                        
                        function killMobileAds() {
                            var v = document.querySelector('video');
                            
                            /* Check for mobile video ad overlays */
                            var mobileAdOverlay = document.querySelector('ytm-ad-player-overlay-renderer, .ad-showing, .ytp-ad-player-overlay');
                            
                            if (v && mobileAdOverlay) {
                                v.playbackRate = 16.0;
                                v.muted = true;
                                if (v.duration && !isNaN(v.duration)) {
                                    v.currentTime = v.duration;
                                }
                            }
                            
                            /* Auto-click mobile skip buttons instantly */
                            var skipBtns = document.querySelectorAll('.ytm-ad-skip-button, .ytp-ad-skip-button, .ytp-skip-ad-button');
                            skipBtns.forEach(function(btn) {
                                btn.click();
                            });
                            
                            /* Remove injected feed ads dynamically on mobile scroll */
                            var feedAds = document.querySelectorAll('ytm-promoted-sparkles-web-renderer, div[class*="promoted-video"]');
                            feedAds.forEach(function(ad) {
                                ad.remove();
                            });
                        }
                        
                        /* Blazing fast execution every 100ms */
                        setInterval(killMobileAds, 100);
                        
                        /* MutationObserver to catch dynamically injected mobile ads */
                        var observer = new MutationObserver(killMobileAds);
                        if (document.body) {
                            observer.observe(document.body, { childList: true, subtree: true });
                        }
                    })();
\"\"\", null)"""

# Replace the previous ad-blocker payload inside the Kotlin file safely
code = re.sub(r'view\?\.evaluateJavascript\(\"\"\"[\s\S]*?AdBlock[\s\S]*?\"\"\"\s*,\s*null\)', new_payload, code)

with open(path, "w", encoding="utf-8") as f:
    f.write(code)

print("\n✅ Final Mobile-Only YouTube Ad Killer Applied!")
