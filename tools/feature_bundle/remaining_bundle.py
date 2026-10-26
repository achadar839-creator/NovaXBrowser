from pathlib import Path
import re

root = Path.cwd()
bundle = root / "tools/feature_bundle"
source = bundle / "MainActivity.review.kt"
out = bundle / "MainActivity.final-review.kt"
report = bundle / "REMAINING_BUNDLE_REPORT.txt"

if not source.exists():
    raise SystemExit("ERROR: MainActivity.review.kt missing; run apply_bundle.py first.")

s = source.read_text(encoding="utf-8")
results = []

def replace_once(old, new, label):
    global s
    n = s.count(old)
    if n == 1:
        s = s.replace(old, new, 1)
        results.append("READY: " + label)
    else:
        results.append(f"NEEDS MANUAL REVIEW ({n} matches): {label}")

# Persist desktop mode preference.
replace_once(
    "private var desktopModeEnabled = false",
    'private var desktopModeEnabled = false',
    "Desktop preference field located"
)

# Validate VIP URL before saving; allow only HTTP(S), preferably HTTPS.
old = '''.setPositiveButton("Save") { _, _ -> getSharedPreferences("prefs", 0).edit().putString("vip", input.text.toString()).apply() }'''
new = '''.setPositiveButton("Save") { _, _ ->
                    val candidate = input.text.toString().trim()
                    val parsed = android.net.Uri.parse(candidate)
                    if ((parsed.scheme == "https" || parsed.scheme == "http") &&
                        !parsed.host.isNullOrBlank() && parsed.userInfo == null) {
                        getSharedPreferences("prefs", 0).edit().putString("vip", candidate).apply()
                    } else {
                        Toast.makeText(this, "मान्य HTTP/HTTPS लिंक दर्ज करें", Toast.LENGTH_SHORT).show()
                    }
                }'''
replace_once(old, new, "VIP URL validation")

# Add explicit security warning to report; email-only admin checks cannot be made secure by a local UI patch.
results.append("REVIEW REQUIRED: Admin access currently relies on a locally stored email; use trusted server/provider authentication for real security.")

# Desktop preference must be wired where the toggle is applied; record it if exact block differs.
if "desktopModeEnabled = !desktopModeEnabled" in s:
    results.append("FOUND: Desktop toggle; preference persistence needs a targeted edit and build validation.")
else:
    results.append("NEEDS MANUAL REVIEW: Desktop toggle not found.")

out.write_text(s, encoding="utf-8")
report.write_text(
    "NovaX Browser remaining feature bundle\n"
    "MODE: REVIEW COPY ONLY — original source untouched\n\n"
    + "\n".join(results)
    + "\n\nOutput: tools/feature_bundle/MainActivity.final-review.kt\n"
    "No build, activation, or GitHub push performed.\n",
    encoding="utf-8"
)
print(report.read_text(encoding="utf-8"))
