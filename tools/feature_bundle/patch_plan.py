from pathlib import Path

root = Path.cwd()
main = root / "app/src/main/java/com/novax/browser/MainActivity.kt"
out = root / "tools/feature_bundle/PATCH_PLAN.txt"

src = main.read_text(encoding="utf-8")
checks = {
    "Intent fallback uses incorrect loadUrl call": 'else intent.getStringExtra("browser_fallback_url")?.let { loadUrl(it) }' in src,
    "Incognito cleanup globally clears cookies": "removeAllCookies(null)" in src,
    "Incognito cleanup globally clears web storage": "WebStorage.getInstance().deleteAllData()" in src,
    "Adult filtering uses broad URL matching": 'reqUrl.contains("porn", true)' in src,
    "HTTP one-time bypass variable exists": "allowHttpOnce" in src,
    "Desktop preference storage needs review": "desktopModeEnabled = false" in src,
    "VIP URL handling needs review": "btnVipAd" in src and "btnAdminPanel" in src,
    "Mini-player lifecycle needs review": "btnMiniClose" in src and "pinnedMusicTab" in src,
}

lines = [
    "NovaX Browser - Guarded Patch Plan",
    "MODE: REVIEW ONLY — NO APP SOURCE CHANGES",
    "",
]
lines += [f"[{'CHECK' if value else 'REVIEW'}] {name}" for name, value in checks.items()]
lines += [
    "",
    "Planned corrections:",
    "1. Correct external intent fallback navigation.",
    "2. Stop incognito-tab closure from clearing all normal-tab cookies/storage.",
    "3. Match blocked adult domains by hostname boundaries, not arbitrary URL text.",
    "4. Correctly consume the one-time HTTP confirmation bypass.",
    "5. Persist desktop-mode preference safely.",
    "6. Guard mini-player tab lifecycle.",
    "7. Validate VIP destinations and document that email-only login is not secure.",
    "8. Review XML click handlers and Android build configuration.",
    "",
    "No feature has been activated or modified by this plan.",
]
out.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("Patch plan created:", out.relative_to(root))
print("Review markers:", sum(checks.values()), "/", len(checks))
print(out.read_text(encoding="utf-8"))
