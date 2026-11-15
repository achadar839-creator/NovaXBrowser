import re
import os

file_path = "app/src/main/java/com/novax/browser/MainActivity.kt"

if not os.path.exists(file_path):
    print("❌ Error: MainActivity.kt nahi mili. Ensure aap NovaXBrowser folder ke andar hain.")
    exit(1)

with open(file_path, "r") as f:
    code = f.read()

# 1. onCreate() में setupPrivacyEasterEgg() जोड़ना
code = re.sub(
    r'(restoreSavedWallpaper\(\)\s+)updateUI\(\)', 
    r'\1setupPrivacyEasterEgg()\n        updateUI()', 
    code
)

# 2. btnGo (Search Bar) में //admin हैकर कमांड जोड़ना
btn_go_target = r'(val u = etUrl\.text\.toString\(\)\s+)(if \(u\.startsWith\("https://"\))'
btn_go_replacement = r'val u = etUrl.text.toString().trim()\n            if (u == "//admin" || u == "cmd:login") { showAdminLoginDialog(); etUrl.setText(""); return@setOnClickListener }\n            \2'
code = re.sub(btn_go_target, btn_go_replacement, code)

# 3. पुराने btnLogin को नए सीक्रेट डायलॉग से रिप्लेस करना
login_target = r'btnLogin\.setOnClickListener \{[\s\S]*?\}\.show\(\)\n\s*\}'
login_replacement = r'btnLogin.setOnClickListener { showAdminLoginDialog() }'
code = re.sub(login_target, login_replacement, code)

# 4. फाइल के आखिर में दोनों नए 'डॉक्टर डूम' फंक्शन्स इंजेक्ट करना
funcs = """
    // --- STEALTH UI: DOCTOR DOOM ---
    private var shieldTapCount = 0
    private var firstTapTime: Long = 0
    private val TAP_WINDOW: Long = 2500

    private fun setupPrivacyEasterEgg() {
        val privacyShield = findViewById<android.widget.ImageView>(R.id.privacyShield)
        privacyShield?.setOnClickListener {
            val currentTime = android.os.SystemClock.elapsedRealtime()
            if (currentTime - firstTapTime > TAP_WINDOW) shieldTapCount = 0
            if (shieldTapCount == 0) firstTapTime = currentTime
            shieldTapCount++
            if (shieldTapCount >= 5) {
                shieldTapCount = 0; firstTapTime = 0
                showAdminLoginDialog()
            }
        }
    }

    private fun showAdminLoginDialog() {
        val passwordInput = android.widget.EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter password"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("INFER // ADMIN").setMessage("Restricted access").setView(passwordInput)
            .setPositiveButton("AUTHENTICATE", null).setNegativeButton("CANCEL") { d, _ -> d.dismiss() }.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (passwordInput.text.toString().trim() == "mutant") {
                    dialog.dismiss()
                    getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE).edit().putString("email", "mutantsuperheros@gmail.com").apply()
                    updateUI()
                    android.widget.Toast.makeText(this, "Welcome Back, Boss.", android.widget.Toast.LENGTH_SHORT).show()
                } else passwordInput.error = "Access denied"
            }
        }
        dialog.show()
    }
"""
code = re.sub(r'(\n}\n*fun makeViewDraggable)', lambda m: "\n" + funcs + m.group(1), code)

with open(file_path, "w") as f:
    f.write(code)

print("✅ SUCCESS: Doctor Doom Stealth Login injected perfectly into MainActivity.kt!")
