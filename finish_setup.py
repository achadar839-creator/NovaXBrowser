import re

# 1. Fix XML (Namespaces, Backgrounds, and Dummy Types to prevent ClassCastException)
xml_file = "app/src/main/res/layout/activity_main.xml"
with open(xml_file, 'r') as f:
    xml = f.read()

xml = xml.replace('xmlns:ads=', 'xmlns:app="http://schemas.android.com/apk/res-auto"\n    xmlns:ads=')
xml = xml.replace('@drawable/search_bg', '#0A0A0A') # Fix missing drawable
xml = xml.replace('<Button android:id="@+id/tvBrand" />', '<TextView android:id="@+id/tvBrand" android:layout_width="0dp" android:layout_height="0dp" />')
xml = xml.replace('<Button android:id="@+id/adCardContainer" />', '<LinearLayout android:id="@+id/adCardContainer" android:layout_width="0dp" android:layout_height="0dp" />')
xml = xml.replace('android:visibility="gone">', 'android:visibility="gone">\n        <LinearLayout android:id="@+id/topBar" android:layout_width="0dp" android:layout_height="0dp" />')

with open(xml_file, 'w') as f:
    f.write(xml)

# 2. Add FAB BottomSheet Logic to Kotlin
kt_file = "app/src/main/java/com/novax/browser/MainActivity.kt"
with open(kt_file, 'r') as f:
    kt = f.read()

fab_logic = """
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.superActionButton)?.setOnClickListener {
            val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val menuView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(60, 60, 60, 60)
                setBackgroundColor(android.graphics.Color.parseColor("#080B09"))
                
                addView(android.widget.TextView(this@MainActivity).apply {
                    text = "INFER SYSTEM"
                    setTextColor(android.graphics.Color.parseColor("#39FF14"))
                    textSize = 12f
                    setPadding(0, 0, 0, 40)
                })
                
                val options = listOf(
                    "◷   History" to { showHistory(); bottomSheet.dismiss() },
                    "☆   Bookmarks" to { showBookmarks(); bottomSheet.dismiss() },
                    "↓   Downloads" to { openDownloadsFolder(); bottomSheet.dismiss() },
                    "◐   Theme" to { showWallpaperMenu(); bottomSheet.dismiss() }
                )
                
                options.forEach { (title, action) ->
                    addView(android.widget.TextView(this@MainActivity).apply {
                        text = title
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 16f
                        setPadding(0, 30, 0, 30)
                        setOnClickListener { action() }
                    })
                }
            }
            bottomSheet.setContentView(menuView)
            bottomSheet.show()
        }
"""

kt = re.sub(r'(setupPrivacyEasterEgg\(\)\n)', r'\1' + fab_logic + '\n', kt)

with open(kt_file, 'w') as f:
    f.write(kt)

print("✅ SUCCESS: Super Action Button aur XML crash fixes lag gaye hain!")
