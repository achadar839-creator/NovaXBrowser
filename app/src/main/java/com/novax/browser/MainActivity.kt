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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
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
    private var mediaActionReceiver: android.content.BroadcastReceiver? = null

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private var pendingMicPermissionRequest: PermissionRequest? = null

    private val micPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingMicPermissionRequest
        pendingMicPermissionRequest = null

        if (granted && request != null) {
            val audioResources = request.resources.filter {
                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
            }.toTypedArray()

            if (audioResources.isNotEmpty()) {
                request.grant(audioResources)
            } else {
                request.deny()
            }
        } else {
            request?.deny()
            Toast.makeText(
                this,
                "Microphone permission is required for voice input",
                Toast.LENGTH_LONG
            ).show()
        }
    }


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
                            .setNegativeButton("Close", null)
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
    private var strictHttpsWarning = true
    private val incognitoTabs = java.util.Collections.newSetFromMap(java.util.WeakHashMap<KeepAliveWebView, Boolean>())
    private var allowHttpOnce: String? = null
    private val trackerHosts = setOf("doubleclick.net", "google-analytics.com", "googletagmanager.com", "facebook.net", "hotjar.com", "clarity.ms")
    private var trackingProtectionEnabled = true
    private var pageTitle: String = "Infer Browser"

    private var rewardedAd: RewardedAd? = null
    private val rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
    private val premiumPrefs by lazy { getSharedPreferences("novax_premium", MODE_PRIVATE) }

    private fun isPremiumActive(): Boolean =
        premiumPrefs.getLong("premium_until", 0L) > System.currentTimeMillis()

    private fun loadRewardedAd() {
        if (isPremiumActive()) {
            rewardedAd = null
            return
        }
        RewardedAd.load(this, rewardedAdUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
            })
    }

    private fun showRewardedUnlock() {
        if (isPremiumActive()) {
            Toast.makeText(this, "👑 Premium is already active", Toast.LENGTH_SHORT).show()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            loadRewardedAd()
            Toast.makeText(this, "Ad loading—please tap again shortly", Toast.LENGTH_SHORT).show()
            return
        }

        rewardedAd = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (earned) {
                    val count = premiumPrefs.getInt("completed_rewards", 0) + 1
                    if (count >= 2) {
                        val premiumUntil = System.currentTimeMillis() + 6L * 60L * 60L * 1000L
                        premiumPrefs.edit()
                            .putLong("premium_until", premiumUntil)
                            .putBoolean("premium_unlocked", true)
                            .putInt("completed_rewards", 0)
                            .apply()
                        Toast.makeText(this@MainActivity,
                            "👑 Premium unlocked for 6 hours!", Toast.LENGTH_LONG).show()
                        updateUI()
                    } else {
                        premiumPrefs.edit().putInt("completed_rewards", count).apply()
                        Toast.makeText(this@MainActivity,
                            "1/2 ads completed. Watch one more!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity,
                        "Reward not earned. Please watch the ad fully.", Toast.LENGTH_SHORT).show()
                }
                updateUI()
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewardedAd()
                Toast.makeText(this@MainActivity,
                    "Ad couldn't be shown. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }

        ad.show(this) {
            earned = true
        }
    }


    private val adultDomains = listOf("porn.com", "pornhub.com", "xvideos.com", "xnxx.com")
    private val defaultVipUrl = "https://www.youtube.com/@mrphengmaxx?sub_confirmation=1"

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMediaAction(intent)
    }

    private fun handleMediaAction(intent: Intent) {

        when (intent.action) {
            BackgroundPlayService.ACTION_OPEN_PINNED -> {
                val pinned = pinnedMusicTab
                val index = tabs.indexOf(pinned)
                if (pinned != null && index >= 0) {
                    switchToTab(index)
                    pinned.requestFocus()
                } else {
                    Toast.makeText(this, "Pinned media tab is no longer available", Toast.LENGTH_SHORT).show()
                }
            }

            BackgroundPlayService.ACTION_NEXT, BackgroundPlayService.ACTION_PREVIOUS -> {
                val tab = pinnedMusicTab
                if (tab != null) {
                    val js = if (intent.action == BackgroundPlayService.ACTION_NEXT) {
                        """(function(){
                            var s = ['.ytp-next-button', '.ytm-custom-control[aria-label*="Next"]', '.ytm-custom-control[aria-label*="अगला"]', 'button[aria-label*="Next"]', 'button[title*="Next"]', 'a.ytm-autonav-bar-link', '[class*="next-button"]'];
                            for (var i=0; i<s.length; i++) { var b = document.querySelector(s[i]); if (b && !b.disabled) { b.click(); return 'clicked'; } }
                            var v = document.querySelector('video,audio'); if(v) { v.currentTime += 10; return 'skipped'; }
                            return 'unavailable';
                        })()"""
                    } else {
                        """(function(){
                            var s = ['.ytp-prev-button', '.ytm-custom-control[aria-label*="Previous"]', '.ytm-custom-control[aria-label*="पिछला"]', 'button[aria-label*="Previous"]', 'button[title*="Previous"]', '[class*="prev-button"]'];
                            for (var i=0; i<s.length; i++) { var b = document.querySelector(s[i]); if (b && !b.disabled) { b.click(); return 'clicked'; } }
                            var v = document.querySelector('video,audio'); if(v) { v.currentTime -= 10; return 'skipped'; }
                            return 'unavailable';
                        })()"""
                    }
                    tab.evaluateJavascript(js, null)
                } else {
                    Toast.makeText(this, "Pin a music tab first", Toast.LENGTH_SHORT).show()
                }
            }

            BackgroundPlayService.ACTION_PLAY_PAUSE -> {
                val tab = pinnedMusicTab
                if (tab == null) {
                    Toast.makeText(this, "Pin a music tab first", Toast.LENGTH_SHORT).show()
                } else {
                    val js = """(function(){
                        var media = Array.from(document.querySelectorAll('video,audio'));
                        var m = media.find(function(x){ return !x.paused && !x.ended; });
                        if (!m) {
                            m = media.find(function(x){ return x.readyState > 0 && !x.ended; });
                        }
                        if (!m) {
                            return JSON.stringify({
                                playing: false,
                                title: document.title,
                                url: location.href, duration: m.duration || 0, position: m.currentTime || 0,
                                found: false
                            });
                        }

                        if (m.paused || m.ended) {
                            try {
                                var promise = m.play();
                                if (promise && promise.catch) {
                                    promise.catch(function(){});
                                }
                            } catch(e) {}
                        } else {
                            m.pause();
                        }

                        return JSON.stringify({
                            playing: !m.paused && !m.ended,
                            title: document.title,
                            url: location.href, duration: m.duration || 0, position: m.currentTime || 0,
                            found: true
                        });
                    })()"""

                    tab.evaluateJavascript(js) { raw ->
                        try {
                            val decoded = org.json.JSONTokener(raw ?: "null").nextValue()
                            val json = if (decoded is String) {
                                org.json.JSONObject(decoded)
                            } else {
                                decoded as? org.json.JSONObject
                            }

                            if (json != null && json.optBoolean("found", false)) {
                                val update = Intent(this, BackgroundPlayService::class.java).apply {
                                    putExtra(
                                        BackgroundPlayService.EXTRA_TITLE,
                                        json.optString("title", tab.title ?: "Infer Browser Audio")
                                    )
                                    putExtra(
                                        BackgroundPlayService.EXTRA_URL,
                                        json.optString("url", tab.url.orEmpty())
                                    )
                                    putExtra(
                                        BackgroundPlayService.EXTRA_PLAYING,
                                        json.optBoolean("playing", false)
                                    )
                                    putExtra(
                                        "novax_media_duration",
                                        (json.optDouble("duration", 0.0) * 1000).toLong()
                                    )
                                    putExtra(
                                        "novax_media_position",
                                        (json.optDouble("position", 0.0) * 1000).toLong()
                                    )
                                }
                                startService(update)
                            }
                        } catch (_: Exception) {
                            // Ignore invalid JavaScript responses.
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = tabs.getOrNull(currentTabIndex)
                if (tab != null && tab.canGoBack()) {
                    tab.goBack()
                } else {
                    isEnabled = false
                    finish()
                }
            }
        })
        setContentView(R.layout.activity_main)

        mediaActionReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: Intent?
            ) {
                if (intent != null) handleMediaAction(intent)
            }
        }

        val mediaFilter = android.content.IntentFilter().apply {
            addAction(BackgroundPlayService.ACTION_PLAY_PAUSE)
            addAction(BackgroundPlayService.ACTION_NEXT)
            addAction(BackgroundPlayService.ACTION_PREVIOUS)
            addAction(BackgroundPlayService.ACTION_OPEN_PINNED)
        }

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            mediaActionReceiver,
            mediaFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val savedTitle = getSharedPreferences("novax_preferences", MODE_PRIVATE)
            .getString("browser_title", null)
        findViewById<android.widget.TextView>(R.id.tvBrand).text =
            if (savedTitle.isNullOrBlank() || savedTitle.contains("NovaX", ignoreCase = true))
                "⚡ Infer Browser"
            else savedTitle

        MobileAds.initialize(this) {}
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 410)
        }
        loadRewardedAd()
        val bannerAd = findViewById<AdView>(R.id.adView)
        bannerAd.visibility = if (isPremiumActive()) View.GONE else View.VISIBLE
        if (!isPremiumActive()) {
            bannerAd.loadAd(AdRequest.Builder().build())
        }

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
        openHomePage()
        setupButtons()
        findViewById<Button>(R.id.btnUiToggle).setOnClickListener {
            toggleBrowserBars()
        }
        toggleBrowserBars()
        applySavedTheme()
        restoreSavedWallpaper()
        updateUI()
    }

    private fun isAdmin(): Boolean {
        val email = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("email", "") ?: ""
        return email.equals("mutantsuperheros@gmail.com", true) || email.equals("achadar839@gmail.com", true)
    }

    private var browserBarsVisible = false
    private var fullDisplayMode = false

    private fun applyFullDisplayMode() {
        if (fullDisplayMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { c ->
                    c.hide(android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars())
                    c.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars())
                window.setDecorFitsSystemWindows(true)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullDisplayMode) applyFullDisplayMode()
    }

    private fun toggleBrowserBars() {
        browserBarsVisible = !browserBarsVisible
        fullDisplayMode = !browserBarsVisible
        val visibility = if (browserBarsVisible) View.VISIBLE else View.GONE

        findViewById<View>(R.id.topBar).visibility = visibility
        findViewById<View>(R.id.miniPlayerBar).visibility =
            if (browserBarsVisible && pinnedMusicTab != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.progressBar).visibility = visibility
        findViewById<View>(R.id.adView).visibility =
            if (browserBarsVisible && !isPremiumActive()) View.VISIBLE else View.GONE

        val root = findViewById<android.view.ViewGroup>(R.id.browserRoot)
        root?.let { container ->
            val tools = (0 until container.childCount)
                .map { container.getChildAt(it) }
                .firstOrNull { it is android.widget.HorizontalScrollView }
            tools?.visibility = visibility
            (container.findViewById<View>(R.id.btnBack)?.parent as? View)
                ?.visibility = visibility
        }

        findViewById<Button>(R.id.btnUiToggle).text =
            if (browserBarsVisible) "×" else "☰"
        applyFullDisplayMode()
    }

    private fun updateUI() {
        if (isAdmin()) {
            btnLogin.text = "👑 Admin"
            btnVipAd.text = "👑 Premium Active"
            btnAdminPanel.visibility = View.VISIBLE
        } else {
            btnLogin.text = "👤 Login"
            btnVipAd.text = if (isPremiumActive()) "👑 Premium Active" else "🎬 Premium (2 Ads)"
            btnAdminPanel.visibility = View.GONE
        }
        btnTabs.text = "[ ${tabs.size} ]"
        findViewById<View>(R.id.adView).visibility =
            if (isPremiumActive() || !browserBarsVisible) View.GONE else View.VISIBLE
        miniPlayerBar.visibility = if (browserBarsVisible && pinnedMusicTab != null) View.VISIBLE else View.GONE
    }

    private fun createNewTab(url: String) {
        val wv = KeepAliveWebView(this)
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = true
        wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        


        // novax_home_bridge
        wv.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun requestCustomization() {
                runOnUiThread {
                    if (wv.url != "https://novax.home/") {
                        Toast.makeText(
                            this@MainActivity,
                            "पहले Infer Browser Home खोलें",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }

                    if (isPremiumActive() || isAdmin()) {
                        wv.evaluateJavascript("openCustomizer()", null)
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("👑 Premium Feature")
                            .setMessage(
                                "Home Screen Customization के लिए Premium unlock करें या Admin account से login करें।"
                            )
                            .setPositiveButton("Watch Ads") { _, _ ->
                                showRewardedUnlock()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
        }, "NovaX")

        wv.webChromeClient = object : WebChromeClient() {
            // novax_web_permission_guard
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return

                runOnUiThread {
                    val audioResources = request.resources.filter {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }.toTypedArray()

                    if (audioResources.isEmpty()) {
                        request.deny()
                        return@runOnUiThread
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Microphone Permission")
                        .setMessage(
                            "Website ${request.origin} wants to use your microphone. Allow audio access?"
                        )
                        .setNegativeButton("Deny") { _, _ ->
                            request.deny()
                        }
                        .setPositiveButton("Allow") { _, _ ->
                            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                request.grant(audioResources)
                            } else {
                                pendingMicPermissionRequest = request
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .setOnCancelListener {
                            request.deny()
                        }
                        .show()
                }
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
                val downloadUri = Uri.parse(url)
                if (downloadUri.scheme !in listOf("http", "https")) {
                    Toast.makeText(this, "यह लिंक सीधे डाउनलोड नहीं हो सकता", Toast.LENGTH_LONG).show()
                    return@setDownloadListener
                }

                val fileName = android.webkit.URLUtil.guessFileName(
                    url, contentDisposition, mimeType
                ).replace(Regex("""[/\\:*?"<>|]"""), "_")
                    .take(180).ifBlank { "Infer_download" }

                val request = android.app.DownloadManager.Request(downloadUri)
                request.setTitle(fileName)
                request.setDescription("Infer Browser डाउनलोड")
                request.setMimeType(mimeType)

                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
                if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
                val pageUrl = wv.url
                if (!pageUrl.isNullOrBlank() && pageUrl.startsWith("http")) {
                    request.addRequestHeader("Referer", pageUrl)
                }

                request.setAllowedOverMetered(true)
                request.setAllowedOverRoaming(true)
                request.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, fileName
                )

                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val downloadId = manager.enqueue(request)
                Toast.makeText(this, "डाउनलोड शुरू: $fileName (ID $downloadId)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "डाउनलोड त्रुटि: ${e.message ?: "अज्ञात समस्या"}", Toast.LENGTH_LONG).show()
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
                val adHost = request?.url?.host?.lowercase().orEmpty()
                if (isPremiumActive() && (
                    adHost == "googlesyndication.com" ||
                    adHost.endsWith(".googlesyndication.com") ||
                    adHost == "googleadservices.com" ||
                    adHost.endsWith(".googleadservices.com") ||
                    adHost == "googletagservices.com" ||
                    adHost.endsWith(".googletagservices.com")
                )) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8",
                        204, "No Content", emptyMap(),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                // novax_transparent_home_wallpaper
                if (view != null && !url.isNullOrBlank() &&
                    (url.contains("google.com") || url == "about:blank")) {
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    view.evaluateJavascript(
                        "(function(){document.documentElement.style.setProperty('background','transparent','important');if(document.body)document.body.style.setProperty('background','transparent','important');})();",
                        null
                    )
                }
                if (tabs.indexOf(wv) == currentTabIndex) etUrl.setText(url)
                if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    val liquidJs = """(function() { if(document.getElementById('novax-float-dl')) return; var b = document.createElement('div'); b.id = 'novax-float-dl'; b.innerHTML = '📥 Download MP3 / MP4'; b.style.cssText = 'position:fixed; bottom:120px; right:15px; z-index:9999999; background:rgba(30,30,30,0.4); backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px); border:2px solid rgba(255,255,255,0.3); color:#ffffff; padding:12px 20px; border-radius:30px; font-weight:900; font-family:sans-serif; box-shadow:0 8px 32px rgba(0,0,0,0.4); cursor:pointer; text-shadow:0 1px 3px rgba(0,0,0,0.8); transition:transform 0.2s;'; setInterval(function(){ b.style.borderColor = 'hsl(' + (Date.now()/20 % 360) + ', 100%, 65%)'; b.style.boxShadow = '0 0 15px hsl(' + (Date.now()/20 % 360) + ', 100%, 55%, 0.6)'; }, 50); b.onmousedown = function(){ b.style.transform = 'scale(0.95)'; }; b.onmouseup = function(){ b.style.transform = 'scale(1)'; }; b.onclick = function(){ window.location.href = 'https://ssyoutube.com/en177/?url=' + encodeURIComponent(window.location.href); }; document.body.appendChild(b); })();"""
                    view?.evaluateJavascript(liquidJs, null)
                }
                if (isPremiumActive() && url?.contains("youtube.com") == true) {
                    view?.evaluateJavascript("setInterval(function(){ var s = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern'); if(s) { s.click(); } var v = document.querySelector('.ad-showing video') || document.querySelector('.html5-main-video'); if(v && document.querySelector('.ytp-ad-player-overlay')) { v.currentTime = v.duration; } }, 500);", null)
                }

                if (!url.isNullOrBlank() &&
                    (url.startsWith("https://") || url.startsWith("http://")) &&
                    !url.startsWith("https://novax.home/")) {
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
            .setTitle("Infer Browser Tabs (${tabs.size})")
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
                contentDescription = "Infer Browser photo wallpaper"
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
            .setTitle("Infer Browser Appearance")
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
            .setTitle("Infer Browser Appearance")
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
            .setTitle("🔖 Infer Browser Bookmarks")
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



    // novax_custom_home_loader
    private fun loadInferHome(webView: WebView) {
        try {
            val html = assets.open("novax_home.html")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            webView.loadDataWithBaseURL(
                "https://novax.home/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Infer Browser Home नहीं खुल पाया",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var nativeHomeView: android.widget.LinearLayout? = null
    private fun openHomePage() {
        val tab = tabs.getOrNull(currentTabIndex) ?: return
        val prefs = getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val homeUrl = prefs.getString("custom_homepage", "https://www.google.com") ?: "https://www.google.com"
        if (homeUrl.contains("google.com") || homeUrl.isEmpty()) {
            tab.visibility = android.view.View.GONE
            if (nativeHomeView == null) {
                nativeHomeView = android.widget.LinearLayout(this).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    addView(android.widget.TextView(this@MainActivity).apply {
                        tag = "home_title"
                        textSize = 42f
                        setTextColor(android.graphics.Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setShadowLayer(15f, 0f, 0f, android.graphics.Color.BLACK)
                        layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 60 }
                    })
                    addView(android.widget.TextView(this@MainActivity).apply {
                        text = "🔍 Search Google or type URL"
                        textSize = 16f
                        setTextColor(android.graphics.Color.WHITE)
                        setPadding(50, 35, 50, 35)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 60f
                            setColor(android.graphics.Color.parseColor("#25FFFFFF"))
                            setStroke(3, android.graphics.Color.parseColor("#66FFFFFF"))
                        }
                        setOnClickListener {
                            val et = findViewById<android.widget.EditText>(R.id.etUrl)
                            et.requestFocus()
                            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        textSize = 15f
                        setTextColor(android.graphics.Color.parseColor("#EEEEEE"))
                        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
                    })
                }
                findViewById<android.widget.FrameLayout>(R.id.webContainer).addView(nativeHomeView, 1)
            }
            nativeHomeView?.visibility = android.view.View.VISIBLE
            nativeHomeView?.findViewWithTag<android.widget.TextView>("home_title")?.text = prefs.getString("browser_title", "⚡ Infer Browser")
            etUrl.setText("")
            etUrl.hint = prefs.getString("search_hint", "Search or enter address")
        } else {
            nativeHomeView?.visibility = android.view.View.GONE
            tab.visibility = android.view.View.VISIBLE
            tab.loadUrl(homeUrl)
        }
    }


    private fun showBrowserSettings() {
        val options = arrayOf(
            "Change Homepage",
            "Clear Browsing History"
        )
        AlertDialog.Builder(this)
            .setTitle("Infer Browser Settings")
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
            }.setNegativeButton("Close", null).show()
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
            .setTitle("Infer Browser Quick Links")
            .setItems(names) { _, which ->
                val index = currentTabIndex
                if (index in tabs.indices) tabs[index].loadUrl(urls[which])
            }
            .setNegativeButton("Close", null)
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



    private fun openMediaDownloader(wv: WebView, selectAll: Boolean = false) {
        if (!isPremiumActive() && !isAdmin()) {
            showRewardedUnlock()
            return
        }

        val script = """
            (function() {
              const found = [];
              const videoExt = /\.(mp4|webm|mov|m4v|ogv)(?:$|[?#])/i;
              const audioExt = /\.(mp3|m4a|aac|ogg|wav|flac|opus)(?:$|[?#])/i;
              const imageExt = /\.(jpg|jpeg|png|webp|gif|avif|bmp)(?:$|[?#])/i;

              function add(raw, label, kind) {
                try {
                  if (!raw) return;
                  const u = new URL(raw, location.href);
                  if (!['http:', 'https:'].includes(u.protocol)) return;

                  let type = kind || '';
                  if (videoExt.test(u.href)) type = 'VIDEO';
                  else if (audioExt.test(u.href)) type = 'AUDIO';
                  else if (imageExt.test(u.href)) type = 'IMAGE';
                  else {
                    const mime = (label || '').toLowerCase();
                    if (/^video\//.test(mime)) type = 'VIDEO';
                    else if (/^audio\//.test(mime)) type = 'AUDIO';
                    else if (/^image\//.test(mime)) type = 'IMAGE';
                  }

                  if (!type || /\.(m3u8|mpd|ts)(?:$|[?#])/i.test(u.href)) return;
                  if (type === 'IMAGE' && (u.href.startsWith('data:') || u.href.startsWith('blob:'))) return;

                  if (!found.some(x => x.url === u.href)) {
                    found.push({
                      url: u.href,
                      label: String(label || u.pathname.split('/').pop() || type).slice(0, 100),
                      type: type
                    });
                  }
                } catch(e) {}
              }

              document.querySelectorAll('video, audio').forEach(e => {
                const kind = e.tagName.toLowerCase() === 'video' ? 'VIDEO' : 'AUDIO';
                add(e.currentSrc, e.getAttribute('title') || kind, kind);
                add(e.src, e.getAttribute('title') || kind, kind);
                if (kind === 'VIDEO') add(e.poster, e.getAttribute('title') || 'Video poster', 'IMAGE');
                e.querySelectorAll('source').forEach(x =>
                  add(x.src || x.getAttribute('src'), x.type || kind, kind)
                );
              });

              document.querySelectorAll('source').forEach(e => {
                const parent = e.closest('video, audio');
                const kind = parent && parent.tagName.toLowerCase() === 'video' ? 'VIDEO' :
                             parent ? 'AUDIO' : '';
                add(e.src || e.getAttribute('src'), e.type || kind, kind);
              });

              document.querySelectorAll('a[href]').forEach(e => {
                add(e.href, e.innerText.trim() || e.download || '', '');
              });

              document.querySelectorAll('img').forEach(e => {
                add(e.currentSrc || e.src, e.alt || 'Image', 'IMAGE');
                add(e.getAttribute('data-src'), e.alt || 'Image', 'IMAGE');
                add(e.getAttribute('data-original'), e.alt || 'Image', 'IMAGE');
                const set = e.getAttribute('srcset') || e.getAttribute('data-srcset') || '';
                set.split(',').forEach(part => {
                  const candidate = part.trim().split(/\s+/)[0];
                  add(candidate, e.alt || 'Image', 'IMAGE');
                });
              });

              document.querySelectorAll('[src]').forEach(e => {
                const tag = e.tagName.toLowerCase();
                if (tag === 'img' || tag === 'video' || tag === 'audio' || tag === 'source') return;
                add(e.getAttribute('src'), tag, '');
              });

              return JSON.stringify(found);
            })();
        """.trimIndent()

        wv.evaluateJavascript(script) { raw ->
            try {
                val decoded = org.json.JSONTokener(raw ?: "null").nextValue()
                val items = org.json.JSONArray(decoded as? String ?: "[]")

                if (items.length() == 0) {
                    Toast.makeText(
                        this,
                        "इस पेज पर सीधे डाउनलोड होने वाली मीडिया फाइल नहीं मिली",
                        Toast.LENGTH_LONG
                    ).show()
                    return@evaluateJavascript
                }

                val labels = Array(items.length()) { i ->
                    val item = items.getJSONObject(i)
                    "${item.optString("type")} — ${item.optString("label")}"
                }
                val selected = BooleanArray(items.length()) { selectAll }

                AlertDialog.Builder(this)
                    .setTitle("Infer Browser Media Downloader")
                    .setMultiChoiceItems(labels, selected) { _, which, checked ->
                        selected[which] = checked
                    }
                    .setNeutralButton("Select All") { _, _ ->
                        openMediaDownloader(wv, true)
                    }
                    .setPositiveButton("Download Selected") { _, _ ->
                        var count = 0
                        for (i in selected.indices) {
                            if (!selected[i]) continue
                            val item = items.getJSONObject(i)
                            val url = item.optString("url")
                            try {
                                val uri = Uri.parse(url)
                                val request = android.app.DownloadManager.Request(uri)
                                val cookies = CookieManager.getInstance().getCookie(url)
                                if (!cookies.isNullOrBlank()) {
                                    request.addRequestHeader("Cookie", cookies)
                                }

                                val userAgent = wv.settings.userAgentString
                                if (!userAgent.isNullOrBlank()) {
                                    request.addRequestHeader("User-Agent", userAgent)
                                }
                                request.addRequestHeader("Referer", wv.url ?: url)

                                val suggested = URLUtil.guessFileName(
                                    url, null, null
                                )
                                val name = suggested
                                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    .take(180)
                                    .ifBlank { "infer_media_${System.currentTimeMillis()}" }

                                request.setTitle(name)
                                request.setDescription("Infer Browser Media Downloader")
                                request.setNotificationVisibility(
                                    android.app.DownloadManager.Request
                                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                )
                                request.setDestinationInExternalPublicDir(
                                    android.os.Environment.DIRECTORY_DOWNLOADS,
                                    name
                                )

                                val manager = getSystemService(
                                    Context.DOWNLOAD_SERVICE
                                ) as android.app.DownloadManager
                                manager.enqueue(request)
                                count++
                            } catch (_: Exception) {
                            }
                        }
                        Toast.makeText(
                            this,
                            "Download Started: $count Files",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "मीडिया सूची पढ़ने में समस्या हुई",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
            "पेज रीफ़्रेश करें",
            "👑 Premium Media Downloader"
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
                    4 -> {
                        if (isPremiumActive() || isAdmin()) {
                            openMediaDownloader(wv)
                        } else {
                            showRewardedUnlock()
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
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
            .setNegativeButton("Close", null)
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
            putExtra(Intent.EXTRA_TEXT, "${wv.title ?: "Infer Browser"}\n$url")
        }
        startActivity(Intent.createChooser(share, "वेबसाइट शेयर करें"))
    }

private fun loadSearchEnginePreference() {
        selectedSearchEngine = getSharedPreferences("novax_preferences", MODE_PRIVATE).getString("search_engine", "Google") ?: "Google"
    }

    
    private fun findTextInPage() {
        val input = EditText(this).apply {
            hint = "वेबसाइट में शब्द खोजें"
            maxLines = 1
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
            .setNegativeButton("Close", null)
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
        mediaActionReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        mediaActionReceiver = null
        super.onDestroy()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnRestoreSession).setOnClickListener { restoreBrowserSession() }
        findViewById<Button>(R.id.btnFindPage).setOnClickListener { findTextInPage() }
        findViewById<Button>(R.id.btnQrScanner).setOnClickListener { showQrScannerNotice() }
        findViewById<Button>(R.id.btnReadingMode).setOnClickListener { showReadingMode() }
        findViewById<Button>(R.id.btnSharePage).setOnClickListener { shareCurrentPage() }
        findViewById<Button>(R.id.btnSearchEngine).setOnClickListener { showSearchEngineSelector() }
        loadSearchEnginePreference()
        findViewById<Button>(R.id.btnWebsiteTools).setOnClickListener { showWebsiteTools() }
        findViewById<Button>(R.id.btnDesktopMode).setOnClickListener { toggleDesktopMode() }
        findViewById<Button>(R.id.btnQuickLinks).setOnClickListener { showQuickLinks() }
        findViewById<Button>(R.id.btnClearData).setOnClickListener { clearBrowserData() }
        findViewById<Button>(R.id.btnDownloads).setOnClickListener {
            val tab = tabs.getOrNull(currentTabIndex)
            val currentUrl = tab?.url ?: ""
            if (currentUrl.contains("youtube.com/watch") || currentUrl.contains("youtu.be/") || currentUrl.contains("youtube.com/shorts/")) {
                Toast.makeText(this, "🚀 Fetching YouTube Premium Links...", Toast.LENGTH_SHORT).show()
                createNewTab("https://ssyoutube.com/en177/?url=" + java.net.URLEncoder.encode(currentUrl, "UTF-8"))
            } else {
                tab?.evaluateJavascript("(function(){ var v = document.querySelector('video'); if(v && v.src && !v.src.startsWith('blob:')) return v.src; var s = document.querySelector('video source'); if(s && s.src && !s.src.startsWith('blob:')) return s.src; var a = document.querySelector('audio'); if(a && a.src && !a.src.startsWith('blob:')) return a.src; return 'not_found'; })()") { result ->
                    val res = result?.replace("\"", "")
                    if (res != null && res != "not_found" && res != "null" && res.isNotBlank()) {
                        Toast.makeText(this, "📥 Media Found! Downloading...", Toast.LENGTH_SHORT).show()
                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(res))
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setTitle("Infer Browser Download")
                        getSystemService(android.app.DownloadManager::class.java).enqueue(request)
                    } else {
                        Toast.makeText(this, "❌ Media link nahi mila! (Protected/Blob Video)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btnDownloads).setOnLongClickListener { openDownloadsFolder(); true }

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
            nativeHomeView?.visibility = android.view.View.GONE
            tabs.getOrNull(currentTabIndex)?.visibility = android.view.View.VISIBLE
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
            if (!isPremiumActive() && !isAdmin()) {
                AlertDialog.Builder(this)
                    .setTitle("👑 Premium Feature")
                    .setMessage("Pin Audio is a Premium feature. Watch 2 rewarded ads completely to unlock Premium.")
                    .setPositiveButton("Watch Ads") { _, _ -> showRewardedUnlock() }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            val tab = tabs.getOrNull(currentTabIndex)
            if (tab == null) {
                Toast.makeText(this, "पहले कोई टैब खोलें", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pinnedMusicTab = tab
            tab.allowBackgroundPlay = true

            val title = tab.title?.takeIf { it.isNotBlank() } ?: "Infer Browser Audio"
            val url = tab.url.orEmpty()
            val serviceIntent = Intent(this, BackgroundPlayService::class.java).apply {
                putExtra(BackgroundPlayService.EXTRA_TITLE, title)
                putExtra(BackgroundPlayService.EXTRA_URL, url)
                putExtra(BackgroundPlayService.EXTRA_PLAYING, true)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            updateUI()
            Toast.makeText(this, "🎵 Audio pinned: $title", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnMiniPlayPause).setOnClickListener {
            val musicTab = pinnedMusicTab
            if (musicTab == null) {
                Toast.makeText(this, "पहले Pin Audio दबाएँ", Toast.LENGTH_SHORT).show()
            } else {
                musicTab.evaluateJavascript(
                    "(function(){var a=Array.from(document.querySelectorAll('audio,video'));var m=a.find(function(x){return !x.paused&&!x.ended;})||a.find(function(x){return x.readyState>0&&!x.ended;});if(!m)return 'no-media';if(m.paused){return m.play().then(function(){return 'playing';}).catch(function(){return 'blocked';});}m.pause();return 'paused';})()"
                ) { result ->
                    val msg = when {
                        result == null -> "मीडिया का जवाब नहीं मिला"
                        result.contains("playing") -> "▶️ चल रहा है"
                        result.contains("paused") -> "⏸️ रोक दिया"
                        result.contains("blocked") -> "वेबसाइट ने प्ले रोक दिया"
                        else -> "पहले वेबसाइट पर मीडिया चलाएँ"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnMiniPrevious).setOnClickListener {
            pinnedMusicTab?.evaluateJavascript(
                "(function(){var a=Array.from(document.querySelectorAll('audio,video'));var m=a.find(function(x){return !x.paused&&!x.ended;})||a.find(function(x){return x.readyState>0&&!x.ended;});if(m)m.currentTime=Math.max(0,m.currentTime-10);return 'ok';})()",
                null
            )
        }

        findViewById<Button>(R.id.btnMiniNext).setOnClickListener {
            pinnedMusicTab?.evaluateJavascript(
                "(function(){var a=Array.from(document.querySelectorAll('audio,video'));var m=a.find(function(x){return !x.paused&&!x.ended;})||a.find(function(x){return x.readyState>0&&!x.ended;});if(m)m.currentTime=Math.min(Number.isFinite(m.duration)?m.duration:m.currentTime+10,m.currentTime+10);return 'ok';})()",
                null
            )
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
            showAdminCustomization()
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
            if (isAdmin()) {
                Toast.makeText(this, "👑 Admin Premium Active", Toast.LENGTH_SHORT).show()
            } else {
                showRewardedUnlock()
            }
        }
    }


    private fun showAdminCustomization() {
        val options = arrayOf("Browser Title", "Search Bar Hint", "Homepage URL", "VIP Link")
        AlertDialog.Builder(this).setTitle("⚙️ Admin: Home Customization").setItems(options) { _, which -> editAdminSetting(which, options[which]) }.setNegativeButton("Close", null).show()
    }

    private fun editAdminSetting(which: Int, title: String) {
        val keys = arrayOf("browser_title", "search_hint", "custom_homepage", "vip")
        val defaults = arrayOf("⚡ Infer Browser", "Search or enter address", "https://www.google.com", defaultVipUrl)
        val prefs = if (which == 3) getSharedPreferences("prefs", 0) else getSharedPreferences("novax_preferences", MODE_PRIVATE)
        val input = EditText(this).apply { setText(prefs.getString(keys[which], defaults[which])) }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                var value = input.text.toString().trim()
                if (which == 2 && value.isNotBlank() && !value.startsWith("http")) value = "https://$value"
                prefs.edit().putString(keys[which], value).apply()
                if (which == 0) findViewById<android.widget.TextView>(R.id.tvBrand).text = value
                if (which == 0) nativeHomeView?.findViewWithTag<android.widget.TextView>("home_title")?.text = value
                if (which == 1) etUrl.hint = value
            }.show()
    }

}
