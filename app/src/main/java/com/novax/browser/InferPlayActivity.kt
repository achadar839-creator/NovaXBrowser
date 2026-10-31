package com.novax.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class InferPlayActivity : AppCompatActivity() {

    private lateinit var mediaWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaWebView = WebView(this)
        setContentView(mediaWebView)

        mediaWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        mediaWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val isAdTraffic = url.contains("/pagead/") ||
                                  url.contains("/api/stats/ads") ||
                                  url.contains("googleads") ||
                                  url.contains("doubleclick.net") ||
                                  url.contains("/youtubei/v1/player/ad_break") ||
                                  url.contains("&adformat=") ||
                                  url.contains("&ad_type=") ||
                                  url.contains("ad_flags=")

                if (isAdTraffic) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "No Content",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val cssShield = """
                    (function() {
                        const style = document.createElement('style');
                        style.innerHTML = `
                            ytm-promoted-sparkles-web-renderer,
                            ytd-promoted-video-renderer,
                            .ad-container,
                            .ytp-ad-overlay-container,
                            #player-ads,
                            ytm-companion-ad-renderer { display: none !important; }
                        `;
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(cssShield, null)
            }
        }

        mediaWebView.loadUrl("https://m.youtube.com")
    }

    override fun onBackPressed() {
        if (mediaWebView.canGoBack()) mediaWebView.goBack() else super.onBackPressed()
    }
}
