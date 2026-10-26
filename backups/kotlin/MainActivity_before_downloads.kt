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
    private val photoPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            getSharedPreferences("novax_preferences", MODE_PRIVATE)
                .edit()
                .putString("wallpaper_uri", uri.toString())
                .putString("wallpaper_type", "photo")
                .apply()
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            applyWallpaper(uri, "photo")
            Toast.makeText(this, "Photo wallpaper selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            getSharedPreferences("novax_preferences", MODE_PRIVATE)
                .edit()
                .putString("wallpaper_uri", uri.toString())
                .putString("wallpaper_type", "video")
                .apply()
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            applyWallpaper(uri, "video")
            Toast.makeText(this, "Video wallpaper selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val tabs = mutableListOf<KeepAliveWebView>()
    private var currentTabIndex = 0
    private var pinnedMusicTab: KeepAliveWebView? = null

    private lateinit var webContainer: FrameLayout
    private lateinit var etUrl: EditText
    private lateinit var btnVipAd: Button
    private lateinit var btnLogin: Button
    private lateinit var btnAdminPanel: Button
    private lateinit var btnTabs: Button
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var miniPlayerBar: LinearLayout
    private lateinit var progressBar: ProgressBar
    private var pageTitle: String = "NovaX Browser"

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
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        miniPlayerBar = findViewById(R.id.miniPlayerBar)
        progressBar = findViewById(R.id.progressBar)
        progressBar.progress = 0
        progressBar.visibility = View.GONE

        createNewTab("https://www.google.com")
        setupButtons()
        applySavedTheme()
        restoreSavedWallpaper()
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
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view === tabs.getOrNull(currentTabIndex)) {
                    progressBar.progress = newProgress
                    progressBar.visibility =
                        if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (view === tabs.getOrNull(currentTabIndex) && !title.isNullOrBlank()) {
                    pageTitle = title
                    this@MainActivity.title = title
                }
            }
        }

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
        val wallpaperIndex = (0 until webContainer.childCount)
            .firstOrNull { webContainer.getChildAt(it).tag == "novax_wallpaper" }
            ?: -1
        webContainer.addView(wv, wallpaperIndex + 1)
        switchToTab(tabs.size - 1)
        wv.loadUrl(url)
    }

    private fun switchToTab(index: Int) {
        currentTabIndex = index
        tabs.forEachIndexed { i, tab -> tab.visibility = if (i == index) View.VISIBLE else View.GONE }
        etUrl.setText(tabs[index].url)
        updateUI()
    }


    private fun showTabsOverview() {
        if (tabs.isEmpty()) {
            createNewTab("https://www.google.com")
            return
        }

        val scroll = ScrollView(this)
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        scroll.addView(rows)

        val dialog = AlertDialog.Builder(this)
            .setTitle("NovaX Tabs (${tabs.size})")
            .setView(scroll)
            .setPositiveButton("+ New Tab", null)
            .setNegativeButton("Close", null)
            .create()

        tabs.toList().forEachIndexed { index, tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val title = tab.title?.takeIf { it.isNotBlank() } ?: "New Tab"
            val url = tab.url ?: ""

            val info = TextView(this).apply {
                text = "${if (index == currentTabIndex) "✓ " else ""}${index + 1}. $title\n$url"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 13f
                setPadding(8, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val openButton = Button(this).apply {
                text = "Open"
                setOnClickListener {
                    val actualIndex = tabs.indexOf(tab)
                    if (actualIndex >= 0) switchToTab(actualIndex)
                    dialog.dismiss()
                }
            }

            val closeButton = Button(this).apply {
                text = "×"
                setOnClickListener {
                    closeTab(tab)
                    dialog.dismiss()
                    if (tabs.isNotEmpty()) showTabsOverview()
                }
            }

            row.addView(info)
            row.addView(openButton)
            row.addView(closeButton)
            rows.addView(row)

            val divider = View(this).apply {
                setBackgroundColor(0xFF444444.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
            }
            rows.addView(divider)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                createNewTab("https://www.google.com")
            }
        }
        dialog.show()
    }

    private fun closeTab(tab: KeepAliveWebView) {
        val index = tabs.indexOf(tab)
        if (index < 0) return

        if (pinnedMusicTab === tab) {
            pinnedMusicTab = null
            stopService(Intent(this, BackgroundPlayService::class.java))
        }

        tab.stopLoading()
        webContainer.removeView(tab)
        tabs.removeAt(index)
        tab.destroy()

        if (tabs.isEmpty()) {
            currentTabIndex = 0
            createNewTab("https://www.google.com")
            return
        }

        currentTabIndex = when {
            index < currentTabIndex -> currentTabIndex - 1
            index == currentTabIndex -> minOf(index, tabs.lastIndex)
            else -> currentTabIndex
        }

        switchToTab(currentTabIndex)
    }

    private fun applySavedTheme() {
        val selected = getSharedPreferences(
            "novax_preferences", MODE_PRIVATE
        ).getInt("selected_theme", 0)

        val colors = when (selected) {
            1 -> intArrayOf(0xFF071A12.toInt(), 0xFF0D2B1D.toInt(), 0xFF00E676.toInt())
            2 -> intArrayOf(0xFF160B24.toInt(), 0xFF26113D.toInt(), 0xFFBB86FC.toInt())
            else -> intArrayOf(0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF58A6FF.toInt())
        }

        window.decorView.setBackgroundColor(colors[0])
        webContainer.setBackgroundColor(colors[0])
        etUrl.setBackgroundColor(colors[1])
        etUrl.setTextColor(android.graphics.Color.WHITE)
    }

    private fun restoreSavedWallpaper() {
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val savedUri = prefs.getString("wallpaper_uri", null) ?: return
        val savedType = prefs.getString("wallpaper_type", "photo") ?: "photo"

        try {
            applyWallpaper(Uri.parse(savedUri), savedType)
        } catch (_: Exception) {
            Toast.makeText(this, "Saved wallpaper could not be loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyWallpaper(uri: Uri, type: String) {
        val oldViews = (0 until webContainer.childCount)
            .map { webContainer.getChildAt(it) }
            .filter { it.tag == "novax_wallpaper" }

        oldViews.forEach { webContainer.removeView(it) }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        if (type == "photo") {
            val image = ImageView(this).apply {
                tag = "novax_wallpaper"
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                contentDescription = "NovaX photo wallpaper"
            }
            webContainer.addView(image, 0, params)
        } else {
            val video = VideoView(this).apply {
                tag = "novax_wallpaper"
                setVideoURI(uri)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(
                        this@MainActivity,
                        "This video format may not be supported",
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }
            }
            webContainer.addView(video, 0, params)
        }

        Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show()
    }

    private fun showWallpaperMenu() {
        val options = arrayOf(
            "Choose Photo Wallpaper",
            "Choose Video Wallpaper",
            "Change Color Theme"
        )

        AlertDialog.Builder(this)
            .setTitle("NovaX Appearance")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> photoPicker.launch(arrayOf("image/*"))
                    1 -> videoPicker.launch(arrayOf("video/*"))
                    2 -> showThemeSwitcher()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeSwitcher() {
        val themes = arrayOf("Midnight Blue", "Emerald Green", "Royal Purple")
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val selected = prefs.getInt("selected_theme", 0).coerceIn(0, themes.lastIndex)

        AlertDialog.Builder(this)
            .setTitle("NovaX Appearance")
            .setSingleChoiceItems(themes, selected) { dialog, which ->
                prefs.edit().putInt("selected_theme", which).apply()
                val colors = when (which) {
                    1 -> intArrayOf(0xFF071A12.toInt(), 0xFF0D2B1D.toInt(), 0xFF00E676.toInt())
                    2 -> intArrayOf(0xFF160B24.toInt(), 0xFF26113D.toInt(), 0xFFBB86FC.toInt())
                    else -> intArrayOf(0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF58A6FF.toInt())
                }
                applySavedTheme()
                dialog.dismiss()
                Toast.makeText(this, "${themes[which]} selected", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showBookmarks() {
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val saved = prefs.getStringSet("bookmarks", emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        val options = arrayOf("वर्तमान वेबसाइट सेव करें", "सेव वेबसाइटें खोलें", "वेबसाइट हटाएँ")

        AlertDialog.Builder(this)
            .setTitle("🔖 NovaX Bookmarks")
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> {
                        if (tabs.isEmpty() || currentTabIndex !in tabs.indices) {
                            Toast.makeText(this, "पहले वेबसाइट खोलें", Toast.LENGTH_SHORT).show()
                        } else {
                            val wv = tabs[currentTabIndex]
                            val url = wv.url.orEmpty()
                            val title = wv.title?.takeIf { it.isNotBlank() } ?: url
                            if (url.isBlank()) {
                                Toast.makeText(this, "वेबसाइट उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
                            } else {
                                saved.add("$title\n$url")
                                prefs.edit().putStringSet("bookmarks", saved).apply()
                                Toast.makeText(this, "Bookmark सेव हो गया", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    1 -> {
                        if (saved.isEmpty()) {
                            Toast.makeText(this, "अभी कोई Bookmark सेव नहीं है", Toast.LENGTH_SHORT).show()
                        } else {
                            val entries = saved.toList()
                            AlertDialog.Builder(this)
                                .setTitle("सेव वेबसाइटें")
                                .setItems(entries.map { it.substringAfter("\n", it) }.toTypedArray()) { _, index ->
                                    val url = entries[index].substringAfter("\n", entries[index])
                                    if (tabs.isEmpty() || currentTabIndex !in tabs.indices) {
                                        createNewTab(url)
                                    } else {
                                        tabs[currentTabIndex].loadUrl(url)
                                    }
                                }
                                .show()
                        }
                    }
                    2 -> {
                        if (saved.isEmpty()) {
                            Toast.makeText(this, "हटाने के लिए Bookmark नहीं है", Toast.LENGTH_SHORT).show()
                        } else {
                            val entries = saved.toList()
                            AlertDialog.Builder(this)
                                .setTitle("Bookmark हटाएँ")
                                .setItems(entries.map { it.substringBefore("\n") }.toTypedArray()) { _, index ->
                                    saved.remove(entries[index])
                                    prefs.edit().putStringSet("bookmarks", saved).apply()
                                    Toast.makeText(this, "Bookmark हटा दिया गया", Toast.LENGTH_SHORT).show()
                                }
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBookmarks).setOnClickListener { showBookmarks() }

        btnBack.setOnClickListener {
            if (tabs.isNotEmpty() && currentTabIndex in tabs.indices &&
                tabs[currentTabIndex].canGoBack()) {
                tabs[currentTabIndex].goBack()
            }
        }

        btnForward.setOnClickListener {
            if (tabs.isNotEmpty() && currentTabIndex in tabs.indices &&
                tabs[currentTabIndex].canGoForward()) {
                tabs[currentTabIndex].goForward()
            }
        }


        findViewById<Button>(R.id.btnGo).setOnClickListener { 
            val u = etUrl.text.toString()
            tabs[currentTabIndex].loadUrl(if (u.contains(".")) "https://$u" else "https://www.google.com/search?q=$u&safe=active")
        }
        findViewById<Button>(R.id.btnHome).setOnClickListener { tabs[currentTabIndex].loadUrl("https://www.google.com") }
        
        btnTabs.setOnClickListener { showTabsOverview() }
        
        findViewById<Button>(R.id.btnPinAudio).setOnClickListener {
            pinnedMusicTab = tabs[currentTabIndex]
            pinnedMusicTab?.allowBackgroundPlay = true
            startService(Intent(this, BackgroundPlayService::class.java))
            createNewTab("https://www.google.com")
            Toast.makeText(this, "🎵 Audio Pinned & Background Play ON", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnMiniPlayPause).setOnClickListener {
            val musicTab = pinnedMusicTab
            if (musicTab == null) {
                Toast.makeText(this, "पहले Pin Audio दबाएँ", Toast.LENGTH_SHORT).show()
            } else {
                musicTab.evaluateJavascript(
                    "(function(){var media=document.querySelectorAll('audio,video');var playing=false;media.forEach(function(m){if(!m.paused){m.pause();}else{m.play().catch(function(){});playing=true;}});return playing;})()",
                    null
                )
                Toast.makeText(this, "Play/Pause अनुरोध भेजा गया", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnMiniClose).setOnClickListener {
            val musicTab = pinnedMusicTab
            if (musicTab != null) {
                val index = tabs.indexOf(musicTab)
                if (index >= 0) {
                    tabs.removeAt(index)
                    webContainer.removeView(musicTab)
                    if (tabs.isEmpty()) {
                        createNewTab("https://www.google.com")
                    } else {
                        if (currentTabIndex > index) currentTabIndex--
                        else if (currentTabIndex >= tabs.size) currentTabIndex = tabs.lastIndex
                        switchToTab(currentTabIndex)
                    }
                }
                musicTab.stopLoading()
                musicTab.destroy()
                pinnedMusicTab = null
            }
            stopService(Intent(this, BackgroundPlayService::class.java))
            updateUI()
        }
        
        findViewById<Button>(R.id.btnWallpaper).setOnClickListener {
            showWallpaperMenu()
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
