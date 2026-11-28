# Wave 25 actionIcons (owner item 6)

| ID | Status | Note |
|----|--------|------|
| PAL-02 | done in code | App rows' action list (Tab/Right) has a leading icon per entry; Robolectric screenshot read |
| PAL-03 | done in code | Nested shortcut pills show the shortcut's own icon (public LauncherApps API, off-main-thread, bounded cache), fall back to the app icon/glyph |
| PAL-06 | done in code | Web rows keep the engine glyph (Globe, Search, Play Store); unchanged by design |
| PAL-18 | done in code | Same icons in Orbit, Filmstrip and Flat (all draw through `HitFace`, `ShortcutPill`, `RadarActionList`); Flat suggestion rows have icons |
| EXT-* | done in code | Launcher commands, settings intents, calculator answers map to glyphs |

Verified: JVM/Robolectric tests in `ActionIconsW25Test` (exhaustive mapping, own icon drawn, no-icon fallback, throwing source, bounded cache) and screenshots in `feature-grid/build/screenshots/w25-actionIcons-*.png`, read.
Device-only (not verified): the real GPay "Scan and pay" icon through `getShortcutIconDrawable` (needs the default home role and `hasShortcutHostPermission`), RTL on a device, TalkBack.
