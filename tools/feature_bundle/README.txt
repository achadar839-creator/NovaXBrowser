NovaX Browser - Remaining Feature Bundle

Current mode: PREPARE ONLY
Existing browser files must remain intact.

Existing features detected:
- Multi-tab browser
- Bookmarks and browsing history
- Downloads
- Incognito tab
- Search engine selector
- SafeSearch URL rewrite
- Tracker blocking
- Adult-domain blocking
- HTTP warning
- Desktop mode
- Reading mode
- Find in page
- Read aloud
- QR scanner
- Photo/video wallpaper
- Themes
- Session save/restore
- File chooser
- Background audio service
- Mini player controls
- Login/Admin/VIP UI

Remaining work to prepare and audit:
1. Improve adult-domain matching.
2. Improve SafeSearch query handling.
3. Consolidate tracker filtering.
4. Prevent incognito cleanup from clearing normal-tab site data.
5. Fix external intent fallback navigation.
6. Fix HTTP confirmation bypass behavior.
7. Persist Desktop Mode preference.
8. Harden mini-player tab lifecycle.
9. Validate VIP URLs and document authentication limitations.
10. Add a feature registry for staged activation.
11. Verify every XML button has a handler.
12. Run a full compile check before enabling features.

Safety:
- Do not replace MainActivity.kt wholesale.
- Do not delete existing browser features.
- Do not enable staged features until the code audit passes.
- Real Admin authentication requires a trusted authentication provider/backend.
