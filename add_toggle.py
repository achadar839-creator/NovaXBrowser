import os

main = "app/src/main/java/com/novax/browser/MainActivity.kt"
play = "app/src/main/java/com/novax/browser/InferPlayActivity.kt"

# 1. MainActivity.kt को अपडेट करें (SharedPreferences Sync के लिए)
with open(main, "r") as f:
    content = f.read()

if "ui_sync_injected" not in content:
    content = content.replace(
        "browserBarsVisible = !browserBarsVisible",
        "browserBarsVisible = !browserBarsVisible\n        getSharedPreferences(\"novax_preferences\", MODE_PRIVATE).edit().putBoolean(\"is_ui_hidden\", browserBarsVisible).apply()"
    )
    
    resume_code = """
    override fun onResume() {
        super.onResume()
        val hiddenInPrefs = getSharedPreferences("novax_preferences", MODE_PRIVATE).getBoolean("is_ui_hidden", false)
        if (hiddenInPrefs != browserBarsVisible) {
            toggleBrowserBars() // ui_sync_injected
        }
    }
    override fun onPause()"""
    content = content.replace("override fun onPause()", resume_code)
    
    with open(main, "w") as f:
        f.write(content)

# 2. InferPlayActivity.kt को नए बटन और फुलस्क्रीन लॉजिक के साथ बदलें
play_code = '''package com.novax.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class InferPlayActivity : AppCompatActivity() {
    private lateinit var mediaWebView: WebView
    private lateinit var btnToggle: Button
    private var isUiHidden = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("novax_preferences", Context.MODE_PRIVATE)
        isUiHidden = prefs.getBoolean("is_ui_hidden", false)

        val rootLayout = FrameLayout(this)
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        mediaWebView = WebView(this)
        mediaWebView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        btnToggle = Button(this).apply {
            text = if (isUiHidden) "×" else "☰"
            contentDescription = "Toggle UI"
            elevation = 16f
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#26FFFFFF"))
            setTextColor(android.graphics.Color.WHITE)
            
            val params = FrameLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                bottomMargin = (70 * resources.displayMetrics.density).toInt()
                marginEnd = (12 * resources.displayMetrics.density).toInt()
            }
            layoutParams = params

            setOnClickListener {
                isUiHidden = !isUiHidden
                text = if (isUiHidden) "×" else "☰"
                prefs.edit().putBoolean("is_ui_hidden", isUiHidden).apply()
                applyFullDisplayMode()
            }
        }

        rootLayout.addView(mediaWebView)
        rootLayout.addView(btnToggle)
        
        makeViewDraggable(btnToggle)

        setContentView(rootLayout)

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
                        "text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0))
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
        applyFullDisplayMode()
    }
    
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("novax_preferences", Context.MODE_PRIVATE)
        val currentState = prefs.getBoolean("is_ui_hidden", false)
        if (currentState != isUiHidden) {
            isUiHidden = currentState
            btnToggle.text = if (isUiHidden) "×" else "☰"
            applyFullDisplayMode()
        }
    }

    private fun applyFullDisplayMode() {
        if (isUiHidden) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { c ->
                    c.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    c.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                window.setDecorFitsSystemWindows(true)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullDisplayMode()
    }

    override fun onBackPressed() {
        if (mediaWebView.canGoBack()) mediaWebView.goBack() else super.onBackPressed()
    }
}
'''

with open(play, "w") as f:
    f.write(play_code)

print("✅ Success! Toggle Button (☰) added to Media Player and Sync setup is complete.")
