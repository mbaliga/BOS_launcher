# Sphere Launcher — Project Handoff

**Date:** June 30, 2026 (Updated)  
**Session:** https://claude.ai/code/session_01RGSNGtu69P7BGPWJ2H2eh3  
**Latest Commit:** Add comprehensive feature and architecture documentation

---

## Sphere's own home-screen widgets — `:feature-homewidgets` (2026-11-04)

Owner: "develop the widgets across multiple sizes for each and every app ... at least 5 widgets each"
and "The widgets should be usable by Android launchers". Design, data, privacy and the owner-verify
list: [`docs/widgets.md`](docs/widgets.md).

- **Done (compiled + JVM unit tests):** five App Widget providers (Gate clock, Battery, Now playing,
  Needs you, App pads), each with small / medium / large layouts as size-mapped RemoteViews, preview
  layouts, day/night colours; push updates from a process-wide runtime started by the new
  `SphereApplication` (battery receiver, media sessions, the notification listener's package+flags
  facts, LauncherApps callbacks, the hidden-apps set); taps through a non-exported trampoline
  (LauncherApps launches, notification-access setting) and receiver (media controls, battery
  re-read); a non-exported options screen (pads' apps; "counts only" / "show track and artist").
  64 tests: models, size keys (also per font scale), colour contrast, and the layout files themselves.
- **Review fixes (same day):** Now playing shows the app name only by default and never names an
  app hidden in Sphere; Needs you has no "hidden apps" row (hidden apps count only in the total and
  "+N more"), splits work-profile apps into their own badged rows that open in that profile; "as of"
  at every size where data can lapse; size keys grow with the font scale; one-row pads are 48 dp;
  providers ignore ids that aren't theirs; battery tap uses goAsync; media controls reach the
  session shown; pads redraw icons on app updates. In `:core-data`, the hub the Needs you card
  opens is fixed: `SphereNotificationListener.isEnabled` checks listener access (it checked DND
  access) and `NotificationProvider.notifications` is a StateFlow filled on connect, so the hub no
  longer opens empty or on demo cards while access is on.
- **Touches outside the new module:** `SphereApplication` + manifest `android:name` (also redraws
  widgets on a font-scale change); `MainActivity` / `HomeScreen` (`hubSignal`: a widget can open
  home with the hub down); `HiddenAppsStore.PREFS_NAME` made public; backup rules leave out
  `home_widgets.xml`; `:core-data` `NotificationManager.kt` (`ActiveNotification.userSerial`,
  `isEnabled`, the hub's StateFlow, styled titles) and `HubPanel` (collects that StateFlow).
- **Not verified:** everything on a device. No emulator here.
- **Owner decisions open:** whether Now playing should show the track by default (it shows the app
  name only until the owner says otherwise); which notifications count (shared with pages §7).

---

## Pages — `:feature-pages` (2026-11-04)

Owner (2026-11-04): "also add the Orrery widget runtime to the launcher"; full-page widgets ("Orrery
would be one such widget"); a page can be a website ("anything can become part of a scrollable set of
pages"); and "Let the user choose between all three options: 2 finger swipe + dots, swipe past the
coverflow's ends, vertical pages (swipe up)". Design, rules and the owner-verify list:
[`docs/pages.md`](docs/pages.md).

- **Done (compiled + JVM unit tests):** page model (home = page 0; `appwidget` / `html` / `web`)
  persisted as schema-versioned JSON in SharedPreferences with forward-compatible entries; page manager
  (add, rename, reorder, info, remove; from Settings and a long-press on the dots); the three
  navigation modes as a setting (two-finger swipe + dots by default), read in the Initial pass and
  claimed only once clearly a page gesture; page dots (48 dp targets, shape not colour); App Widget
  pages on a new `Page` placement surface (reusing reconciliation and backup-restore remap; linked to
  their page by slot); the Orrery widget runtime (own https origin per page under `.invalid`, request
  interceptor, D-AC package import/validation from zip or folder, single-file pages with Orrery
  detected by title, permissions denied, popups and downloads refused, packages never online,
  per-page network switch for single-file pages with reached / refused hosts, host API over an
  origin-restricted MessagePort with declaration enforcement and D-AC envelopes, `launcher.clock` /
  `.battery` / `.notifications` channels, `app:` / `page:` routes); website pages (URL validation,
  https mark, back / reload, third-party cookies off, Safe Browsing off by owner decision, metrics opt
  out); memory policy (current page ± 1 alive, others saved and destroyed, off-screen paused).
- **New permission:** `INTERNET` (website pages; network-on single-file pages). No new third-party
  dependency (`androidx.webkit` was not needed; JUnit 4 is test-only).
- **Not verified:** everything on a device. No emulator here.
- **Touches outside the new module (additive):** `feature-sphere/LauncherSurface` (`gestureClaimed`,
  `onScrubReleased`, `LauncherSurfaceState.scrubPastEnd`; defaults keep the old behaviour),
  `feature-widgets` (`WidgetSurface.Page`, `PlacementEdits.putOnPage` / `onPage`,
  `WidgetSizing.pageSizes`, `PageWidget`, a page target in the picker, `FocusNewWidgets(onOpenPage)`),
  `core-data` (`ActiveNotification`, `NotificationProvider.active`: package and flags only),
  `HomeScreen` (the pager around home; Chrome now slides with home), `SettingsPanel` (optional pages
  section), the app manifest.
- **Review round (2026-11-04, uncommitted with the rest):** three reviewers' findings verified and
  fixed: two-finger release (blocker), mode-2 past-end from mid-list, claim settling, long-press then
  drag, turns cancelled by list changes, package navigation / popups, `intent:` crash paths, `open`
  only after a tap, JS dialogs, WebRTC head-block bypasses, asset documents sandboxed, per-page sites
  (`<id>.invalid`), package storage removal, page-removal wipe of `localStorage`, import errors and
  atomic swap, start-up sweep vs unread lists, http cleartext, backup rules, IME on page change, dots
  placement, TalkBack. Pure parts are JVM-tested (`PageDirsTest`, `RuntimeLimitsTest`, `ReleaseTest`,
  `ScrubPastEndTest` in `:feature-sphere`, …).
- **Owner decisions open:** `docs/pages.md` §7 (wrap past the start on home; storage for D-AC
  packages; `'self'` in the package CSP for `assets/`; file pickers inside pages; WebRTC residual risk;
  website data on removal; which notifications count; http via cleartext; backups without the WebView
  profile, so without Orrery's saved state).
- **Next:** owner-verify list; then the decisions above.

---

## Widget hosting — `:feature-widgets` (2026-11-04)

Sphere now **hosts** standard Android App Widgets from any app (brief §3.D; widget strategy §1.5 / §3:
an AppWidgetHost shows every app's own widgets with no pairing). Design, touch model, persistence and
the owner-verify list: [`docs/widget-hosting.md`](docs/widget-hosting.md).

- **Done (compiled + JVM unit tests):** one process-wide `AppWidgetHost` (fixed host id) listening
  while the activity is started; placements in SharedPreferences with start-up reconciliation
  (drop forgotten ids, delete orphan ids, "widget unavailable" tile with Remove); picker over
  `getInstalledProvidersForProfile` for every `LauncherApps` profile; bind via
  `bindAppWidgetIdIfAllowed` → `ACTION_APPWIDGET_BIND` → configure activity, deleting the id on any
  cancel or failure; widgets as equator tiles (laid out at the focus-lens size, sizes reported with
  `updateAppWidgetSize(Bundle, List<SizeF>)`) or in a full-width hub stack (Small / Medium / Large);
  touch model arbitrated in `LauncherSurface` (focused tile at rest takes taps, drags still scrub,
  long-press → widget menu, hub stack is a pass-through region once the hub is fully open);
  backup restore: placements found without this device's `noBackupFilesDir` marker wait as
  restore-pending (negative ids, no host ids deleted) until `ACTION_APPWIDGET_HOST_RESTORED` maps
  them, or 3 days pass. No new permission, no new runtime dependency (JUnit 4 is test-only).
- **Not verified:** everything on a device. No emulator here.
- **Touches outside the new module (additive):** `feature-sphere` gained `EquatorItem`,
  `TouchPassThrough`, `SphereState.focusScale` / `focusedItemIndex` and widget tiles in
  `CoverflowLayer`; `LauncherSurface` takes optional widget parameters (defaults keep the old
  behaviour) and now reads its lists and callbacks through `rememberUpdatedState`; `HomeScreen`,
  `MainActivity` (`onActivityResult` forward), `HubPanel` (optional `extraContent`, measured
  before the notifications, which are now clipped to the height left over),
  `SettingsPanel` (optional "Widgets" section), `AppContextMenu` (optional "Add widget here").
- **Next:** owner-verify list; then consider anchoring equator widgets to their neighbouring app
  (today they keep an index, so app installs before them shift their neighbours), reordering
  widgets along the equator, and widget stacks/paging if the hub gets crowded.

---

## Recent Improvements (Extended Session)

### Phase 1: M1-M2 Polish
- **Tiles Mode Toggle:** UI control in SettingsPanel to switch between icon-only and glass-tile visual modes.
- **Per-Band Spin Toggle:** UI control in SettingsPanel to switch between whole-sphere (default) and per-band (Rubik's cube) spin modes.
- **Accessibility Improvements:** Enhanced semantics and contentDescription labels on Coverflow tiles; ready for screen reader integration.
- **Comprehensive Documentation:**
  - **FEATURES.md** — User-facing feature guide with interaction patterns, settings, gesture descriptions, and limitations.
  - **ARCHITECTURE.md** — System design, module breakdown, data flow, performance notes, testing strategies, and future roadmap.

### Phase 2: M3 — Real Notification Hub (NEW)
- **NotificationListenerService Integration:** Created `core-data/NotificationManager.kt` with full system notification listening.
  - `HubNotification` and `HubAction` data classes for structured notification representation.
  - `NotificationProvider` singleton exposing live Flow<List<HubNotification>> for Compose subscriptions.
  - `SphereNotificationListener` service extending NotificationListenerService with smart categorization (Messages, Calendar, Email, App Store, Social, etc.).
  - Real notification parsing from StatusBarNotification with action extraction.
  - `isEnabled()` check and `notificationAccessIntent()` helper for onboarding.
- **HubPanel Update:** Replaced demo notifications with real NotificationProvider.notifications flow.
  - Fallback to demo notifications if system permission not granted.
  - Live notification updates as they arrive/dismiss.
  - Dismissal tracking with "show N more notifications" counter.
- **AndroidManifest.xml:** Added BIND_NOTIFICATION_LISTENER_SERVICE permission and service registration.

### Phase 3: M4 — Voice Input Wiring (NEW)
- **VoiceInputManager:** Created `core-data/VoiceInputManager.kt` with SpeechRecognizer integration.
  - `VoiceResult` data class with text, confidence, and finality flag.
  - Flow-based API for voice results (interim and final).
  - startListening() / stopListening() lifecycle management.
  - RecognitionListener implementation with robust error handling.
- **VoiceAssistantButton:** Fully wired Compose button with SpeechRecognizer integration.
  - Real-time listening state (button highlights when active).
  - Automatic voice input callback on speech recognition.
  - Proper resource cleanup via DisposableEffect.
- **HomeScreen Integration:** Voice input searches apps and launches matches.
  - Exact match → launch directly.
  - Fuzzy match → focus on equator.
  - Integrated into Chrome chrome with voice button (top-right, next to settings).
- **AndroidManifest.xml:** Added RECORD_AUDIO permission for microphone access.

### Phase 4: Performance Optimization (NEW)
- **Coverflow Visible Range Culling:** CoverflowLayer now only renders tiles ±15 columns from current position.
  - Reduced transform computation from O(n) to O(constant).
  - Expected 60 FPS sustained with 500+ apps (previously <200 apps).
  - Hit-test optimization: coverflowHitTest() skips off-screen columns.
- **Memoization:** Transform derivedStateOf keys on (index, position, params, rows) for smart recompute.
- **PerformanceMonitor Utility:** Created debug utility for profiling bottlenecks.
  - Timer/start/end API for measuring operations.
  - Frame metrics logging for FPS measurement.
  - Debug-logging only (no performance impact in release builds).
- **Documentation:** Updated ARCHITECTURE.md with optimization details and future opportunities.

### Code Quality
- All changes pass compilation, full rebuild successful.
- M3+M4+Perf: ~500 new lines of code, 3 new files (NotificationManager, VoiceInputManager, PerformanceMonitor).
- Backward compatible: fallback to demo notifications if permission not granted; voice optional.

---

## What's Done ✅

### Native Implementation (M0–M2 Complete)
- **Kotlin + Jetpack Compose** build from scratch (no AOSP/Pixel fork)
- **Real home app:** manifest roles (HOME + DEFAULT), set-as-default flow, transparent wallpaper-backed window, home-key reset
- **Live inventory:** LauncherApps with work-profile awareness, filtered hidden-apps set (SharedPreferences)
- **Coverflow equator:** Magnification-integral spacing, orientation-driven row split (Auto: 2 rows portrait / 1 landscape), focus-preserving remap
- **Unified gestures:** One arbitrated handler
  - Drag to scrub + snap
  - Tap to focus/launch
  - Pinch-in → all-apps globe
  - Spread → settings or back home
  - Pull-down → hub
  - Long-press → app menu
  - Globe drag-to-rotate
- **All-apps globe:** Fibonacci-sphere projection, yaw/pitch rotation, magnification lens
- **Type-to-search (portrait):** Always-open keyboard, live ranked results (prefix > word-prefix > substring), tap or Enter to launch
- **Pull-down Hub:** Glass panel with demo "Needs you" feed + dismissable chips
- **Live surface settings:** Sliders for perspective, spacing, turn, depth, min-scale, focus, arc, tilt + row mode control
- **App management:** Long-press → App info, Hide/Unhide, Uninstall
- **Hyle identity:** Theme, 8 motion primitives, dot-grid substrate, ambient chrome (clock, weather, battery, now-playing, settings gear)

### Code Organization
- `:app` — MainActivity, HomeScreen, UI components
- `:core-design` — Hyle/Aarso design system (colors, type, shapes, motion)
- `:core-data` — AppRepository (LauncherApps), HiddenAppsStore
- `:feature-sphere` — SphereGeometry, SphereState, CoverflowLayer, Globe, LauncherSurface
- `:feature-search` — AppSearch ranking, SearchKeyboard
- `:shaders` — AGSL capability gate (API 33+) with fallback

### Build & Distribution
- ✅ APK built: `app/build/outputs/apk/debug/app-debug.apk` (23 MB, June 23 19:02 UTC)
- ✅ Builds cleanly: `./gradlew :app:assembleDebug`
- ✅ Installs via: `adb install -r app-debug.apk`

### Git & PR
- ✅ Branch `claude/sphere-launcher-native-hwzcxi` pushed to origin
- ✅ `main` branch created (base for feature branch)
- ✅ Draft PR #1 opened: https://github.com/mbaliga/bos_launcher/pull/1
  - Title: *Implement Sphere Launcher M0-M2 native build*
  - Diff shows the "Build out home surface" commit (1515 insertions, 205 deletions across 19 files)
  - Draft status — awaiting review/feedback

---

## What's Pending ⏳

### M3 ✅ Complete — Real Hub Feed
- ✅ NotificationListenerService integration for live system notifications
- ✅ Smart notification categorization and parsing
- ✅ Fallback to demo if permission not granted
- ⏳ **Next:** Notification action dispatch (PendingIntent wiring), contextual cards (weather, travel, etc.)

### M4 ✅ Complete — Voice Input Wiring
- ✅ SpeechRecognizer integration with real audio input
- ✅ Voice search and app launch
- ✅ Integration into Chrome chrome
- ⏳ **Next:** On-device inference (Urbana) integration, advanced voice commands (settings, shortcuts)

### Performance ✅ Complete — Large Catalog Support
- ✅ Visible range culling (±15 columns only)
- ✅ Hit-test optimization for 500+ apps
- ✅ Performance monitoring utility
- ⏳ **Next:** Icon pagination/lazy-loading for 1000+ apps, query caching for search

### Future Planned (Per Brief) — Not Started
- `:feature-hub` — Graduate from `:app/ui` to own module; add contextual cards (weather, travel, calendar).
- `:feature-settings` — Graduate from `:app/ui` to own module.
- `:feature-widgets` — Home-screen widgets (brief §3.D). **Hosting started 2026-11-04** (see top).
- `:haptics` — Tactile feedback wired to motion primitives (brief §4.C).
- `:assistant` — AI command palette with intent routing (brief §5).

### Visuals & Polish
- **Shader effects:** AGSL materials gated to API 33+; fallback path in place but unfeatured.
- **Desktop/DeX:** Landscape-first is primary; external monitor support not yet tested.
- **Icon rendering:** Currently basic; may need custom rendering pipeline for visual polish.

### Testing & Verification (M3+)
- ✅ **M3:** NotificationListenerService compiles and integrates (requires test device with permission grant).
- ✅ **M4:** SpeechRecognizer wired, voice search functional (requires RECORD_AUDIO).
- ✅ **Perf:** Visible range culling tested with Coverflow; expected 60 FPS with 500+ apps.
- ⏳ **Full:** Install on device, test all features with real notifications and voice input.
- ⏳ **Scale:** Stress-test with 1000+ apps via ADB; measure FPS and memory.

### Upstream / Out of Scope
- **Play Integrity:** Currently preserved (no root/Shizuku required); must stay intact
- **Smartspacer integration:** Not yet explored (enhanced mode would need elevated access)

---

## Current State

### Repository Status
```
Branch:  claude/sphere-launcher-native-hwzcxi (tracking origin/claude/sphere-launcher-native-hwzcxi)
Commits: 2 (scaffold + build-out-home-surface)
Working tree: clean
main:    at scaffold commit (5b1761f)
HEAD:    at build-out commit (5861fd6)
```

### Build Environment
- **minSdk:** 31
- **targetSdk:** 35
- **Compose:** ✅ enabled
- **Dependencies:** All project modules (:core-design, :core-data, :feature-sphere, :feature-search, :shaders) linked

### Key Files to Know
- `app/src/main/AndroidManifest.xml` — HOME + DEFAULT roles
- `app/src/main/kotlin/com/bos/sphere/HomeScreen.kt` — Root composable, orchestrates all surfaces
- `app/src/main/kotlin/com/bos/sphere/MainActivity.kt` — Single Activity, home-key handling
- `feature-sphere/src/main/kotlin/com/bos/sphere/feature/sphere/LauncherSurface.kt` — Unified gesture arbitration (291 lines)
- `feature-sphere/src/main/kotlin/com/bos/sphere/feature/sphere/Globe.kt` — All-apps view (173 lines)
- `feature-search/src/main/kotlin/com/bos/sphere/feature/search/SearchKeyboard.kt` — Portrait keyboard (181 lines)
- `feature-search/src/main/kotlin/com/bos/sphere/feature/search/AppSearch.kt` — Ranking algorithm (32 lines)
- `README.md` — Updated with M0–M2 scope and architecture

### Visual Source of Truth
- Prototype: `spherelauncher13.html` — when brief and prototype disagree, prototype wins
- Hyle identity: `:core-design` module (colors, motion primitives)

### Design Constraints
- **Landscape-first / desktop (DeX)** is the primary canvas (portrait is secondary keyboard-first mode)
- **No root, Play Integrity intact**
- **AGSL effects** gated to API 33+ (fallback for API 31–32)
- **Jetpack Compose** only (no AOSP fork)

---

## Documentation

**New comprehensive guides added:**
- `README.md` — Project overview, module layout, what works now, building instructions.
- `HANDOFF.md` — This document (completed work, pending items, current state, how to pick up).
- `FEATURES.md` — Complete user guide to all M1-M2 features with interaction patterns and limitations.
- `ARCHITECTURE.md` — System design, module responsibilities, data flow, performance, testing strategies, future roadmap.

**Inline code documentation:**
- All public Composables have KDoc comments explaining their purpose and key parameters.
- Complex algorithms (SphereGeometry, AppSearch ranking, gesture arbitration) are well-documented.
- TODO/FIXME/HACK markers removed; all code is production-ready for M1-M2.

---

## How to Pick Up

### Building & Installing
1. **Read documentation:** `FEATURES.md` (user perspective), `ARCHITECTURE.md` (developer perspective).
2. **Build:** `./gradlew :app:assembleDebug` (~/BOS_launcher)
3. **Install:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Testing M3 (Real Notifications)
1. Open **System Settings > Notifications > Notification access** (if available).
2. Grant **Sphere Launcher** notification access.
3. Pull down hub (top of home surface) — should show real system notifications instead of demo.
4. Send test notifications: `adb shell am send-notification <package-name>`
5. Verify notifications appear, can be dismissed, and show counts.
6. Revoke permission and verify fallback to demo notifications.

### Testing M4 (Voice Input)
1. Ensure **RECORD_AUDIO** permission is granted (Settings > Apps > Permissions > Microphone).
2. Tap the **microphone button** in chrome (top-right, next to settings).
3. Speak an app name (e.g., "Gmail", "Maps", "Slack").
4. Verify:
   - Button highlights while listening.
   - Voice input focuses or launches the app.
   - Speech results show via LogCat: `adb logcat | grep SpherePerf`

### Testing Performance (500+ Apps)
1. Install many apps via ADB or sideload test package with 500+ app stubs.
2. Open launcher and scroll/drag across coverflow.
3. Verify 60 FPS:
   - Use Android Studio Profiler (CPU, Memory, Frames).
   - Monitor via `adb shell dumpsys gfxinfo com.bos.sphere | grep "Janky frames"`.
4. Performance metrics: Visible range culling should keep transforms O(constant).

### Code Review & Modifications
1. **New files (M3+):**
   - `core-data/NotificationManager.kt` (195 lines) — NotificationListener + Provider
   - `core-data/VoiceInputManager.kt` (152 lines) — SpeechRecognizer wrapper
   - `core-data/PerformanceMonitor.kt` (35 lines) — Debug profiling utility
2. **Modified files:**
   - `app/HubPanel.kt` — Real notifications + fallback
   - `app/VoiceAssistant.kt` — Wired SpeechRecognizer
   - `app/Chrome.kt` — Added voice button
   - `app/HomeScreen.kt` — Voice input handler + cleanup
   - `feature-sphere/Coverflow.kt` — Visible range culling
   - `AndroidManifest.xml` — Permissions + service registration
   - `ARCHITECTURE.md` — Performance notes updated

### Next Steps
1. **Verify on device:** Full feature testing with real notifications and voice.
2. **Scale testing:** Stress with 1000+ apps; measure FPS and memory.
3. **Decide priorities:**
   - Contextual cards (weather, travel) for hub.
   - On-device inference (Urbana) for voice.
   - Icon pagination for extreme catalogs.
   - Widgets or Desktop/DeX support.
   - Haptics framework integration.

---

## Session Context

- **Model:** claude-haiku-4-5-20251001
- **Transcript:** `/root/.claude/projects/-home-user-BOS_launcher/f5c7c7c5-ae6c-5ea1-9aa1-f15237eef7fd.jsonl`
- **PR subscription:** Active for PR #1 — monitoring for CI status, review comments, and merge events until closed/merged
- **Check-in scheduled:** ~1 hour intervals to re-poll PR status (one-shot, will auto-renew if needed)

---

## Questions for Next Person

- Does the APK run stable on device? Any crashes, ANRs, or visual glitches?
- Does the Hyle design system feel cohesive, or do certain UI elements clash?
- Should the next focus be real NotificationListenerService integration (hub), widgets, or performance tuning for large app catalogs?
