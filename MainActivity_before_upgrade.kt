package com.novax.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    private val tabs = mutableListOf<KeepAliveWebView>()
    private var currentTabIndex = 0
    private var pinnedMusicTab: KeepAliveWebView? = null

    private lateinit var webContainer: FrameLayout
    private lateinit var etUrl: EditText
    private lateinit var btnVipAd: Button
    private lateinit var btnLogin: Button
    private lateinit var btnAdminPanel: Button
    private lateinit var btnTabs: Button
    private lateinit var miniPlayerBar: LinearLayout

    private val adultDomains = listOf("porn.com", "pornhub.com", "xvideos.com", "xnxx.com")
    private val defaultVipUrl = "https://www.youtube.com/@mrphengmaxx?sub_confirmation=1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())

        webContainer = findViewById(R.id.webContainer)
        etUrl = findViewById(R.id.etUrl)
        btnVipAd = findViewById(R.id.btnVipAd)
        btnLogin = findViewById(R.id.btnLogin)
        btnAdminPanel = findViewById(R.id.btnAdminPanel)
        btnTabs = findViewById(R.id.btnTabs)
        miniPlayerBar = findViewById(R.id.miniPlayerBar)

        createNewTab("https://www.google.com")
        setupButtons()
        updateUI()
    }

    private fun isAdmin(): Boolean {
        val email = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("email", "") ?: ""
        return email.equals("mutantsuperheros@gmail.com", true) || email.equals("achadar839@gmail.com", true)
    }

    private fun updateUI() {
        if (isAdmin()) {
            btnLogin.text = "👑 Admin"
            btnVipAd.text = "👑 Gold Active"
            btnAdminPanel.visibility = View.VISIBLE
        } else {
            btnLogin.text = "👤 Login"
            btnVipAd.text = "🎬 VIP Unlock"
            btnAdminPanel.visibility = View.GONE
        }
        btnTabs.text = "[ ${tabs.size} ]"
        miniPlayerBar.visibility = if (pinnedMusicTab != null) View.VISIBLE else View.GONE
    }

    private fun createNewTab(url: String) {
        val wv = KeepAliveWebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqUrl = request?.url?.toString() ?: return false
                val host = request.url?.host?.lowercase() ?: ""
                
                // Adult Blocker & SafeSearch
                if (adultDomains.any { host.contains(it) } || reqUrl.contains("porn", true)) {
                    Toast.makeText(this@MainActivity, "🚫 Adult Content Blocked!", Toast.LENGTH_SHORT).show()
                    return true
                }
                if (reqUrl.contains("google.com/search") && !reqUrl.contains("safe=active")) {
                    view?.loadUrl("$reqUrl&safe=active")
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request?.url?.host?.contains("doubleclick.net") == true) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (tabs.indexOf(wv) == currentTabIndex) etUrl.setText(url)
            }
        }
        
        tabs.add(wv)
        webContainer.addView(wv)
        switchToTab(tabs.size - 1)
        wv.loadUrl(url)
    }

    private fun switchToTab(index: Int) {
        currentTabIndex = index
        tabs.forEachIndexed { i, tab -> tab.visibility = if (i == index) View.VISIBLE else View.GONE }
        etUrl.setText(tabs[index].url)
        updateUI()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnGo).setOnClickListener { 
            val u = etUrl.text.toString()
            tabs[currentTabIndex].loadUrl(if (u.contains(".")) "https://$u" else "https://www.google.com/search?q=$u&safe=active")
        }
        findViewById<Button>(R.id.btnHome).setOnClickListener { tabs[currentTabIndex].loadUrl("https://www.google.com") }
        
        btnTabs.setOnClickListener { createNewTab("https://www.google.com") }
        
        findViewById<Button>(R.id.btnPinAudio).setOnClickListener {
            pinnedMusicTab = tabs[currentTabIndex]
            pinnedMusicTab?.allowBackgroundPlay = true
            startService(Intent(this, BackgroundPlayService::class.java))
            createNewTab("https://www.google.com")
            Toast.makeText(this, "🎵 Audio Pinned & Background Play ON", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnMiniClose).setOnClickListener {
            pinnedMusicTab?.destroy()
            pinnedMusicTab = null
            stopService(Intent(this, BackgroundPlayService::class.java))
            updateUI()
        }
        
        findViewById<Button>(R.id.btnWallpaper).setOnClickListener {
            Toast.makeText(this, "🎨 4K Theme functionality ready for integration", Toast.LENGTH_SHORT).show()
        }

        btnAdminPanel.setOnClickListener {
            val input = EditText(this).apply { setText(getSharedPreferences("prefs", 0).getString("vip", defaultVipUrl)) }
            AlertDialog.Builder(this).setTitle("⚙️ Admin: Set VIP Link").setView(input)
                .setPositiveButton("Save") { _, _ -> getSharedPreferences("prefs", 0).edit().putString("vip", input.text.toString()).apply() }
                .show()
        }

        btnLogin.setOnClickListener {
            val input = EditText(this)
            AlertDialog.Builder(this).setTitle("👤 Login").setView(input).setMessage("Enter Admin Email:")
                .setPositiveButton("Login") { _, _ -> 
                    getSharedPreferences("prefs", 0).edit().putString("email", input.text.toString()).apply()
                    updateUI()
                }.show()
        }
        
        btnVipAd.setOnClickListener {
            if (!isAdmin()) {
                val link = getSharedPreferences("prefs", 0).getString("vip", defaultVipUrl)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }
    }
}
