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

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val qrPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrCameraLauncher.launch(null)
        } else {
            Toast.makeText(this, "QR स्कैन करने के लिए कैमरा अनुमति ज़रूरी है", Toast.LENGTH_LONG).show()
        }
    }

private val qrCameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            Toast.makeText(this, "कैमरा फोटो नहीं मिली", Toast.LENGTH_SHORT).show()
        } else {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { codes ->
                    val value = codes.firstOrNull()?.rawValue
                    if (value.isNullOrBlank()) {
                        Toast.makeText(this, "QR कोड नहीं मिला", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("QR Code मिला")
                            .setMessage(value)
                            .setNegativeButton("बंद करें", null)
                            .setPositiveButton("खोलें") { _, _ ->
                                val parsed = android.net.Uri.parse(value.trim())
                                val scheme = parsed.scheme?.lowercase()
                                if ((scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank()) {
                                    createNewTab(value.trim())
                                } else {
                                    Toast.makeText(this, "QR में सुरक्षित वेबसाइट लिंक नहीं है", Toast.LENGTH_LONG).show()
                                }
                            }
                            .show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "QR स्कैन नहीं हो पाया", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener { scanner.close() }
        }
    }

private val fileUploadPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (callback != null) {
            val uris = if (result.resultCode == RESULT_OK) {
                val data = result.data
                when {
                    data?.clipData != null -> {
                        val clip = data.clipData!!
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
            callback.onReceiveValue(uris)
        }
    }
    

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
    private var selectedSearchEngine = "Google"
    private var pageTts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var findDirectionForward = true
    private var websiteTextZoom = 100
    private var desktopModeEnabled = false
    private val browserPrefs by lazy { getSharedPreferences("novax_browser_prefs", MODE_PRIVATE) }
    private var strictHttpsWarning = true
    private val incognitoTabs = java.util.Collections.newSetFromMap(java.util.WeakHashMap<KeepAliveWebView, Boolean>())
    private var allowHttpOnce: String? = null
    private val trackerHosts = setOf("doubleclick.net", "google-analytics.com", "googletagmanager.com", "facebook.net", "hotjar.com", "clarity.ms")
    private var trackingProtectionEnabled = true
    private var pageTitle: String = "NovaX Browser"

    private val adultDomains = listOf("porn.com", "pornhub.com", "xvideos.com", "xnxx.com")
    private val defaultVipUrl = "https://www.youtube.com/@mrphengmaxx?sub_confirmation=1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        desktopModeEnabled = browserPrefs.getBoolean("desktop_mode", false)

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
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = true
        wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        

        wv.webChromeClient = object : WebChromeClient() {
            // novax_web_permission_guard
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        // Camera/microphone access is denied until explicit in-app permission approval is implemented.
                        request?.deny()
                    }

                    override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                this@MainActivity.fileUploadCallback = filePathCallback

                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    fileUploadPicker.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                    this@MainActivity.fileUploadCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "फ़ाइल चुनने का विकल्प नहीं खुला",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }

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

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = android.app.DownloadManager.Request(Uri.parse(url))
                val fileName = android.webkit.URLUtil.guessFileName(
                    url, contentDisposition, mimeType
                )
                request.setTitle(fileName)
                request.setDescription("NovaX Browser डाउनलोड")
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    request.addRequestHeader("Cookie", cookies)
                }
                request.addRequestHeader("User-Agent", userAgent)
                request.addRequestHeader("Referer", url)
                request.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, fileName
                )
                if (url.startsWith("https://", true)) {
                    request.addRequestHeader("User-Agent", userAgent)
                }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "डाउनलोड शुरू हुआ: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "डाउनलोड शुरू नहीं हुआ", Toast.LENGTH_LONG).show()
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // novax_external_scheme_handler
                    val target = request?.url?.toString().orEmpty()
                    val scheme = request?.url?.scheme?.lowercase().orEmpty()
                    if (scheme !in listOf("http", "https", "about", "javascript", "data", "blob")) {
                        try {
                            if (scheme == "intent") {
                                val intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
                                if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                                else intent.getStringExtra("browser_fallback_url")?.let { view?.loadUrl(it) }
                            } else if (scheme in listOf("tel", "mailto", "sms", "smsto", "geo")) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "इस लिंक को खोलने के लिए ऐप उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }

                val reqUrl = request?.url?.toString() ?: return false
                val host = request.url?.host?.lowercase() ?: ""
                if (trackingProtectionEnabled &&
                    (host == "doubleclick.net" || host.endsWith(".doubleclick.net") ||
                     host == "google-analytics.com" || host.endsWith(".google-analytics.com") ||
                     host == "googletagmanager.com" || host.endsWith(".googletagmanager.com"))) {
                    return true
                }
                val approvedHttpUrl = allowHttpOnce == reqUrl
                if (approvedHttpUrl) allowHttpOnce = null
                if (strictHttpsWarning && !approvedHttpUrl &&
                    request.url?.scheme.equals("http", true) &&
                    host.isNotBlank() && !host.endsWith(".localhost") && host != "localhost") {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("असुरक्षित HTTP वेबसाइट")
                        .setMessage("यह वेबसाइट HTTPS का उपयोग नहीं करती। क्या आप फिर भी खोलना चाहते हैं?\n\n$reqUrl")
                        .setNegativeButton("वापस जाएँ", null)
                        .setPositiveButton("फिर भी खोलें") { _, _ -> allowHttpOnce = reqUrl; view?.loadUrl(reqUrl) }
                        .show()
                    return true
                }

                
                // Adult Blocker & SafeSearch
                if (adultDomains.any { host == it || host.endsWith(".$it") }) {
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
                    // novax_tracker_filter
                    val requestUrl = request?.url
                    val requestHost = requestUrl?.host?.lowercase().orEmpty()
                    if (trackingProtectionEnabled && requestHost.isNotBlank() &&
                        trackerHosts.any { requestHost == it || requestHost.endsWith(".$it") }) {
                        return WebResourceResponse(
                            "text/plain", "UTF-8",
                            204, "No Content", emptyMap(),
                            java.io.ByteArrayInputStream(ByteArray(0))
                        )
                    }

                if (request?.url?.host?.contains("doubleclick.net") == true) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (tabs.indexOf(wv) == currentTabIndex) etUrl.setText(url)

                if (!url.isNullOrBlank() &&
                    (url.startsWith("https://") || url.startsWith("http://"))) {
                    val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
                    val history = prefs.getStringSet("browser_history", emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    val title = view?.title?.takeIf { it.isNotBlank() } ?: url
                    history.removeAll { it.substringAfter("\n", "") == url }
                    history.add("$title\n$url")
                    while (history.size > 200) history.remove(history.first())
                    prefs.edit().putStringSet("browser_history", history).apply()
                }
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
        if (index !in tabs.indices) return

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
        // novax_incognito_cleanup
        if (incognitoTabs.remove(tab)) {
            tab.stopLoading()
            tab.clearHistory()
            tab.clearCache(true)
            tab.clearFormData()
            tab.clearSslPreferences()
            // Shared WebView profile: avoid deleting normal tabs' global data.
            // Full isolation requires a separate WebView profile.
        }

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


    private fun showHistory() {
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val history = prefs.getStringSet("browser_history", emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        if (history.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("🕘 Browser History")
                .setMessage("अभी कोई वेबसाइट हिस्ट्री में नहीं है।")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val entries = history.toList().reversed()
        val labels = entries.map { it.substringBefore("\n") }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🕘 Browser History")
            .setItems(labels) { _, index ->
                val url = entries[index].substringAfter("\n", "")
                if (url.isNotBlank()) {
                    if (tabs.isEmpty() || currentTabIndex !in tabs.indices) {
                        createNewTab(url)
                    } else {
                        tabs[currentTabIndex].loadUrl(url)
                    }
                }
            }
            .setNeutralButton("हिस्ट्री साफ़ करें") { _, _ ->
                prefs.edit().remove("browser_history").apply()
                Toast.makeText(this, "History साफ़ कर दी गई", Toast.LENGTH_SHORT).show()
            }
            .show()
    }


    private fun openHomePage() {
        val homeUrl = getSharedPreferences(
            "novax_preferences", MODE_PRIVATE
        ).getString("custom_homepage", "https://www.google.com")
            ?: "https://www.google.com"

        val index = currentTabIndex
        if (index in tabs.indices) {
            tabs[index].loadUrl(homeUrl)
        }
    }


    private fun showBrowserSettings() {
        val options = arrayOf(
            "होमपेज बदलें",
            "ब्राउज़िंग हिस्ट्री साफ़ करें"
        )
        AlertDialog.Builder(this)
            .setTitle("NovaX Browser Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val input = EditText(this)
                        input.hint = "https://example.com"
                        input.setText(
                            getSharedPreferences(
                                "novax_preferences", MODE_PRIVATE
                            ).getString(
                                "custom_homepage",
                                "https://www.google.com"
                            )
                        )
                        AlertDialog.Builder(this)
                            .setTitle("Custom Homepage")
                            .setView(input)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ ->
                                var url = input.text.toString().trim()
                                if (url.isNotBlank()) {
                                    if (!url.startsWith("https://") &&
                                        !url.startsWith("http://")) {
                                        url = "https://$url"
                                    }
                                    getSharedPreferences(
                                        "novax_preferences", MODE_PRIVATE
                                    ).edit()
                                        .putString("custom_homepage", url)
                                        .apply()
                                    Toast.makeText(
                                        this, "होमपेज सेव हो गया",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }.show()
                    }
                    1 -> {
                        getSharedPreferences(
                            "novax_preferences", MODE_PRIVATE
                        ).edit().remove("browser_history").apply()
                        Toast.makeText(
                            this, "हिस्ट्री साफ़ कर दी गई",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.setNegativeButton("बंद करें", null).show()
    }


    private fun openIncognitoTab() {
        val privateUrl = "https://www.google.com/"
        createNewTab(privateUrl)
        val privateTab = tabs.getOrNull(currentTabIndex)
        if (privateTab != null) {
            incognitoTabs.add(privateTab)
            privateTab.clearHistory()
            privateTab.clearCache(true)
            privateTab.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            privateTab.settings.saveFormData = false
            privateTab.settings.setGeolocationEnabled(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(privateTab, false)
            Toast.makeText(this, "निजी टैब खुल गया। बंद करने पर उसका डेटा साफ़ होगा।", Toast.LENGTH_LONG).show()
        }
    }


    private fun showQuickLinks() {
        val names = arrayOf("Google", "YouTube", "Facebook", "Instagram")
        val urls = arrayOf(
            "https://www.google.com",
            "https://www.youtube.com",
            "https://www.facebook.com",
            "https://www.instagram.com"
        )
        AlertDialog.Builder(this)
            .setTitle("NovaX Quick Links")
            .setItems(names) { _, which ->
                val index = currentTabIndex
                if (index in tabs.indices) tabs[index].loadUrl(urls[which])
            }
            .setNegativeButton("बंद करें", null)
            .show()
    }

    private fun clearBrowserData() {
        AlertDialog.Builder(this)
            .setTitle("Browsing Data साफ़ करें?")
            .setMessage("कैश, cookies और वेबसाइट का स्थानीय डेटा साफ़ किया जाएगा।")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("साफ़ करें") { _, _ ->
                try {
                    tabs.forEach { it.clearCache(true) }
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    Toast.makeText(
                        this, "Browsing data साफ़ करने का अनुरोध भेजा गया",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "डेटा साफ़ नहीं हो सका", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun openDownloadsFolder() {
        try {
            val intent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Downloads ऐप उपलब्ध नहीं है", Toast.LENGTH_LONG).show()
        }
    }


    private fun toggleDesktopMode() {
        val wv = tabs.getOrNull(currentTabIndex)
        if (wv == null) {
            Toast.makeText(this, "पहले वेबसाइट खोलें", Toast.LENGTH_SHORT).show()
            return
        }
        desktopModeEnabled = !desktopModeEnabled
        browserPrefs.edit().putBoolean("desktop_mode", desktopModeEnabled).apply()
        wv.settings.useWideViewPort = desktopModeEnabled
        wv.settings.loadWithOverviewMode = desktopModeEnabled
        wv.settings.userAgentString = if (desktopModeEnabled) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(this)
        }
        Toast.makeText(
            this,
            if (desktopModeEnabled) "Desktop Mode चालू" else "Mobile Mode चालू",
            Toast.LENGTH_SHORT
        ).show()
        wv.reload()
    }


    private fun showWebsiteTools() {
        val wv = tabs.getOrNull(currentTabIndex)
        if (wv == null) {
            Toast.makeText(this, "कोई वेबसाइट खुली नहीं है", Toast.LENGTH_SHORT).show()
            return
        }
        val title = wv.title ?: "शीर्षक उपलब्ध नहीं"
        val url = wv.url ?: "URL उपलब्ध नहीं"
        val options = arrayOf(
            "वेबसाइट की जानकारी",
            "टेक्स्ट बड़ा करें",
            "टेक्स्ट छोटा करें",
            "पेज रीफ़्रेश करें"
        )
        AlertDialog.Builder(this)
            .setTitle("Website Tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this)
                        .setTitle("Website Info")
                        .setMessage("Title: $title\n\nURL: $url")
                        .setPositiveButton("ठीक है", null)
                        .show()
                    1 -> {
                        websiteTextZoom = (websiteTextZoom + 10).coerceAtMost(200)
                        wv.settings.textZoom = websiteTextZoom
                    }
                    2 -> {
                        websiteTextZoom = (websiteTextZoom - 10).coerceAtLeast(50)
                        wv.settings.textZoom = websiteTextZoom
                    }
                    3 -> wv.reload()
                }
            }
            .setNegativeButton("बंद करें", null)
            .show()
    }


    private fun saveBrowserSession() {
        val urls = tabs
            .filter { !incognitoTabs.contains(it) }
            .mapNotNull { tab ->
                val url = tab.url
                if (!url.isNullOrBlank() &&
                    (url.startsWith("https://") || url.startsWith("http://"))) url
                else null
            }
            .distinct()
            .takeLast(8)

        getSharedPreferences("novax_preferences", MODE_PRIVATE)
            .edit()
            .putStringSet("last_session_urls", urls.toSet())
            .apply()
    }


    override fun onPause() {
        saveBrowserSession()
        super.onPause()
    }


    private fun showSearchEngineSelector() {
        val engines = arrayOf("Google", "Bing", "DuckDuckGo")
        AlertDialog.Builder(this)
            .setTitle("Default Search Engine")
            .setSingleChoiceItems(engines, engines.indexOf(selectedSearchEngine).coerceAtLeast(0)) { dialog, which ->
                selectedSearchEngine = engines[which]
                getSharedPreferences("novax_preferences", MODE_PRIVATE)
                    .edit().putString("search_engine", selectedSearchEngine).apply()
                Toast.makeText(this, "Search: $selectedSearchEngine", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("बंद करें", null)
            .show()
    }

    private fun searchWithSelectedEngine(query: String) {
        val q = android.net.Uri.encode(query)
        val base = when (selectedSearchEngine) {
            "Bing" -> "https://www.bing.com/search?q="
            "DuckDuckGo" -> "https://duckduckgo.com/?q="
            else -> "https://www.google.com/search?q="
        }
        tabs.getOrNull(currentTabIndex)?.loadUrl(base + q)
    }

    private fun showReadingMode() {
        val wv = tabs.getOrNull(currentTabIndex) ?: return
        val script = """
            (function(){
              var old=document.getElementById('novax-reading-overlay');
              if(old){old.remove();return 'Reading mode बंद';}
              var text=(document.querySelector('article')||document.querySelector('main')||document.body).innerText;
              var box=document.createElement('div');
              box.id='novax-reading-overlay';
              box.style='position:fixed;inset:0;z-index:2147483647;overflow:auto;background:#fff;color:#171717;padding:24px;font:20px/1.7 sans-serif;white-space:pre-wrap';
              var close=document.createElement('button');
              close.textContent='✕ Close Reading Mode';
              close.style='position:sticky;top:0;padding:12px;background:#111;color:white;border:0;border-radius:8px';
              close.onclick=function(){box.remove()};
              box.appendChild(close);
              var content=document.createElement('div');
              content.textContent=text;
              content.style='max-width:760px;margin:20px auto';
              box.appendChild(content);
              document.body.appendChild(box);
              return 'Reading mode चालू';
            })();
        """.trimIndent()
        wv.evaluateJavascript(script) { result ->
            Toast.makeText(this, result?.replace("\"", "") ?: "Reading Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentPage() {
        val wv = tabs.getOrNull(currentTabIndex)
        val url = wv?.url
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "पहले वेबसाइट खोलें", Toast.LENGTH_SHORT).show()
            return
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${wv.title ?: "NovaX Browser"}\n$url")
        }
        startActivity(Intent.createChooser(share, "वेबसाइट शेयर करें"))
    }

private fun loadSearchEnginePreference() {
        selectedSearchEngine = getSharedPreferences("novax_preferences", MODE_PRIVATE).getString("search_engine", "Google") ?: "Google"
    }

    
    private fun findTextInPage() {
        val input = EditText(this).apply {
            hint = "वेबसाइट में शब्द खोजें"
            singleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Find in Page")
            .setView(input)
            .setPositiveButton("अगला") { _, _ ->
                val query = input.text.toString().trim()
                tabs.getOrNull(currentTabIndex)?.let { wv ->
                    if (query.isNotEmpty()) {
                        wv.findAllAsync(query)
                        wv.findNext(true)
                        findDirectionForward = true
                    }
                }
            }
            .setNeutralButton("पिछला") { _, _ ->
                val query = input.text.toString().trim()
                tabs.getOrNull(currentTabIndex)?.let { wv ->
                    if (query.isNotEmpty()) {
                        wv.findAllAsync(query)
                        wv.findNext(false)
                        findDirectionForward = false
                    }
                }
            }
            .setNegativeButton("बंद करें", null)
            .show()
    }


    private fun speakPageText() {
        val wv = tabs.getOrNull(currentTabIndex) ?: return
        if (pageTts == null) {
            pageTts = android.speech.tts.TextToSpeech(this) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) {
                    pageTts?.language = java.util.Locale.getDefault()
                    extractAndSpeakPageText()
                } else {
                    Toast.makeText(this, "Text-to-Speech उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            extractAndSpeakPageText()
        }
    }

    private fun extractAndSpeakPageText() {
        val wv = tabs.getOrNull(currentTabIndex) ?: return
        wv.evaluateJavascript(
            "(document.querySelector('article') || document.querySelector('main') || document.body).innerText",
        ) { raw ->
            if (!ttsReady || raw.isNullOrBlank() || raw == "null") {
                Toast.makeText(this, "पढ़ने के लिए टेक्स्ट नहीं मिला", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            val text = try {
                org.json.JSONTokener(raw).nextValue().toString()
            } catch (_: Exception) {
                raw
            }.take(3500)
            pageTts?.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "novax_page_read"
            )
        }
    }

    private fun showQrScannerNotice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            qrCameraLauncher.launch(null)
        } else {
            qrPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun restoreBrowserSession() {
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val saved = prefs.getStringSet("last_session_urls", emptySet())
            ?.toList() ?: emptyList()
        val urls = saved.filter {
            it.startsWith("https://") || it.startsWith("http://")
        }.take(8)

        if (urls.isEmpty()) {
            Toast.makeText(this, "पिछला सेशन उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("पिछला सेशन वापस खोलें?")
            .setMessage("${urls.size} वेबसाइटें मिलीं। क्या इन्हें नए टैब में खोलना है?")
            .setNegativeButton("अभी नहीं", null)
            .setPositiveButton("खोलें") { _, _ ->
                urls.forEach { url -> createNewTab(url) }
            }
            .show()
    }

override fun onDestroy() {
        pageTts?.stop()
        pageTts?.shutdown()
        pageTts = null
        super.onDestroy()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnRestoreSession).setOnClickListener { restoreBrowserSession() }
        findViewById<Button>(R.id.btnFindPage).setOnClickListener { findTextInPage() }
        findViewById<Button>(R.id.btnSpeakPage).setOnClickListener { speakPageText() }
        findViewById<Button>(R.id.btnQrScanner).setOnClickListener { showQrScannerNotice() }
        findViewById<Button>(R.id.btnReadingMode).setOnClickListener { showReadingMode() }
        findViewById<Button>(R.id.btnSharePage).setOnClickListener { shareCurrentPage() }
        findViewById<Button>(R.id.btnSearchEngine).setOnClickListener { showSearchEngineSelector() }
        loadSearchEnginePreference()
        findViewById<Button>(R.id.btnWebsiteTools).setOnClickListener { showWebsiteTools() }
        findViewById<Button>(R.id.btnDesktopMode).setOnClickListener { toggleDesktopMode() }
        findViewById<Button>(R.id.btnQuickLinks).setOnClickListener { showQuickLinks() }
        findViewById<Button>(R.id.btnClearData).setOnClickListener { clearBrowserData() }
        findViewById<Button>(R.id.btnDownloads).setOnClickListener { openDownloadsFolder() }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            val wv = tabs.getOrNull(currentTabIndex)
            if (wv != null) {
                if (progressBar.visibility == View.VISIBLE) wv.stopLoading()
                else wv.reload()
            }
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showBrowserSettings()
        }
        findViewById<Button>(R.id.btnIncognito).setOnClickListener {
            openIncognitoTab()
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener { showHistory() }
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
            if (u.startsWith("https://") || u.startsWith("http://")) {
                tabs.getOrNull(currentTabIndex)?.loadUrl(u)
            } else if (u.contains(".") && !u.contains(" ")) {
                tabs.getOrNull(currentTabIndex)?.loadUrl("https://$u")
            } else {
                searchWithSelectedEngine(u)
            }
        }
        findViewById<Button>(R.id.btnHome).setOnClickListener { openHomePage() }
        
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
                closeTab(musicTab)
            } else {
                stopService(Intent(this, BackgroundPlayService::class.java))
                updateUI()
            }
        }

        findViewById<Button>(R.id.btnWallpaper).setOnClickListener {
            showWallpaperMenu()
        }

        btnAdminPanel.setOnClickListener {
            val input = EditText(this).apply { setText(getSharedPreferences("prefs", 0).getString("vip", defaultVipUrl)) }
            AlertDialog.Builder(this).setTitle("⚙️ Admin: Set VIP Link").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val candidate = input.text.toString().trim()
                    val parsed = android.net.Uri.parse(candidate)
                    if ((parsed.scheme == "https" || parsed.scheme == "http") &&
                        !parsed.host.isNullOrBlank() && parsed.userInfo == null) {
                        getSharedPreferences("prefs", 0).edit().putString("vip", candidate).apply()
                    } else {
                        Toast.makeText(this, "मान्य HTTP/HTTPS लिंक दर्ज करें", Toast.LENGTH_SHORT).show()
                    }
                }
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
