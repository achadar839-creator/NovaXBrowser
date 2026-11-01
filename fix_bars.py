import re
file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"
try:
    with open(file_path, "r", encoding="utf-8") as f:
        data = f.read()

    fix_code = """
        // TOP BLACK BAR & BOTTOM WHITE BAR FIX
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
                // ऊपर की काली पट्टी और नीचे की सफेद पट्टी को गायब (Transparent) करना
                statusBarColor = android.graphics.Color.TRANSPARENT
                navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }

        // टॉप बार को थोड़ा नीचे खिसकाना ताकि वह बैटरी/नेटवर्क आइकन के पीछे न छुपे
        findViewById<android.view.View>(R.id.topBar)?.let { topBar ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
                val statusHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                view.setPadding(view.paddingLeft, statusHeight + 12, view.paddingRight, view.paddingBottom)
                insets
            }
        }

        // नीचे वाले सारे बटनों को नेविगेशन जेस्चर के ठीक ऊपर सेट करना ताकि वो आधे न दिखें
        findViewById<android.view.View>(R.id.browserRoot)?.let { root ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val navHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navHeight)
                insets
            }
        }
"""

    if "TOP BLACK BAR & BOTTOM WHITE BAR FIX" not in data:
        data = re.sub(r"(setContentView\(.*?\))", r"\1\n" + fix_code, data)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(data)
        print("✅ Success: Top Black Bar & Bottom White Bar Fixed!")
    else:
        print("⚠️ Fix is already applied.")
except Exception as e:
    print("Error:", e)
