package com.novax.browser

import android.webkit.WebResourceRequest

object InferAdBlocker {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adservice.google.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "popads.net",
        "popcash.net",
        "exoclick.com",
        "taboola.com",
        "outbrain.com",
        "adsterra.com",
        "propellerads.com",
        "zemanta.com",
        "criteo.com",
        "openx.net",
        "rubiconproject.com",
        "pubmatic.com",
        "smartadserver.com"
    )

    fun shouldBlock(request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val host = uri.host?.lowercase() ?: return false

        // केवल HTTP/HTTPS requests पर लागू
        if (uri.scheme != "http" && uri.scheme != "https") {
            return false
        }

        // विज्ञापन डोमेन और उनके subdomains ब्लॉक करें
        return blockedHosts.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    val webScript = """
    (function() {
        if (window.__inferAdBlockerActive) return;
        window.__inferAdBlockerActive = true;

        const css = `
            .ad-container,
            .ads-container,
            .ad-banner,
            .advertisement,
            .advertising,
            .sponsored-content,
            .sponsored-post,
            [data-ad],
            [data-ad-slot],
            [data-ad-client],
            iframe[src*="doubleclick"],
            iframe[src*="googlesyndication"],
            iframe[src*="adservice"],
            iframe[src*="popads"],
            ins.adsbygoogle {
                display: none !important;
                visibility: hidden !important;
                pointer-events: none !important;
                max-height: 0 !important;
                overflow: hidden !important;
            }
        `;

        function installCSS() {
            if (document.getElementById("infer-adblock-style")) return;
            const style = document.createElement("style");
            style.id = "infer-adblock-style";
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);
        }

        function removeKnownAds() {
            const selectors = [
                ".ad-container",
                ".ads-container",
                ".ad-banner",
                ".advertisement",
                ".sponsored-content",
                "[data-ad-slot]",
                "[data-ad-client]",
                "ins.adsbygoogle"
            ];

            document.querySelectorAll(selectors.join(",")).forEach(el => {
                el.remove();
            });
        }

        function handleYouTubeSkip() {
            const skipButtons = [
                ".ytp-ad-skip-button",
                ".ytp-ad-skip-button-modern",
                "button.ytp-skip-ad-button"
            ];

            skipButtons.forEach(selector => {
                document.querySelectorAll(selector).forEach(button => {
                    if (button.offsetParent !== null) button.click();
                });
            });
        }

        installCSS();
        removeKnownAds();

        const observer = new MutationObserver(() => {
            installCSS();
            removeKnownAds();
            handleYouTubeSkip();
        });

        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });

        // YouTube skip button उपलब्ध होने पर ही क्लिक होगा।
        setInterval(handleYouTubeSkip, 1000);

    })();
    """.trimIndent()
}
