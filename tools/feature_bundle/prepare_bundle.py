from pathlib import Path

root = Path.cwd()
bundle = root / "tools/feature_bundle"
main = root / "app/src/main/java/com/novax/browser/MainActivity.kt"
xml = root / "app/src/main/res/layout/activity_main.xml"

if not main.exists() or not xml.exists():
    raise SystemExit("ERROR: MainActivity.kt ya activity_main.xml nahi mili. Koi change nahi kiya.")

src = main.read_text(encoding="utf-8")
layout = xml.read_text(encoding="utf-8")

checks = {
    "multi_tab": "createNewTab" in src,
    "incognito": "openIncognitoTab" in src and "incognitoTabs" in src,
    "external_intent": "Intent.URI_INTENT_SCHEME" in src,
    "http_warning": "strictHttpsWarning" in src,
    "safe_search": "safe=active" in src,
    "tracker_blocking": "trackingProtectionEnabled" in src,
    "adult_blocking": "adultDomains" in src,
    "desktop_mode": "desktopModeEnabled" in src,
    "audio_pin": "pinnedMusicTab" in src,
    "vip_admin": "btnVipAd" in src and "btnAdminPanel" in src,
    "bookmarks_history": "btnBookmarks" in src and "btnHistory" in src,
    "downloads": "btnDownloads" in src,
    "qr_scanner": "btnQrScanner" in src,
    "reading_mode": "btnReadingMode" in src,
    "find_in_page": "btnFindPage" in src,
    "read_aloud": "btnSpeakPage" in src,
    "wallpaper": "btnWallpaper" in src,
    "session_restore": "btnRestoreSession" in src,
}

# Create a staging registry only; these flags are documentation for future wiring.
registry = root / "app/src/main/java/com/novax/browser/FeatureRegistry.kt"
if not registry.exists():
    registry.write_text("""package com.novax.browser

/**
 * NovaX Browser staged feature registry.
 * PREPARE ONLY: these flags are not wired into MainActivity yet.
 * Do not treat a listed flag as an active feature.
 */
object FeatureRegistry {
    const val PREPARE_ONLY = true
    const val ADULT_FILTER_V2 = false
    const val SAFE_SEARCH_V2 = false
    const val TRACKER_FILTER_V2 = false
    const val ISOLATED_INCOGNITO_PROFILE = false
    const val HTTP_BYPASS_FIX = false
    const val DESKTOP_MODE_PERSISTENCE = false
    const val MINI_PLAYER_LIFECYCLE_FIX = false
    const val VIP_URL_VALIDATION = false
    const val SECURE_ADMIN_AUTH = false
}
""", encoding="utf-8")

report = bundle / "AUDIT_REPORT.txt"
lines = [
    "NovaX Browser - Feature Bundle Inventory",
    "Mode: PREPARE ONLY",
    "No MainActivity.kt or layout XML changes were made by this script.",
    "",
    "Detected feature markers:"
]
lines.extend(f"{name}: {'FOUND' if found else 'NOT CONFIRMED'}" for name, found in checks.items())
lines += [
    "",
    "Important limitations:",
    "- Marker detection is not a functional test.",
    "- Android compilation has not been run.",
    "- Incognito may still share WebView cookies/storage with normal tabs.",
    "- Email-only Admin login is not secure authentication.",
    "- FeatureRegistry flags are not connected to app controls.",
]
report.write_text("\n".join(lines) + "\n", encoding="utf-8")

print("PREPARE BUNDLE: READY")
print("Registry:", registry.relative_to(root))
print("Report:", report.relative_to(root))
print("Confirmed markers:", sum(checks.values()), "/", len(checks))
print("MainActivity.kt and activity_main.xml were not modified.")
print("Next: inspect the report, then prepare one guarded patch script.")
