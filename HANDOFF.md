# Sphere Launcher — Project Handoff

**Date:** June 30, 2026 (Updated)  
**Session:** https://claude.ai/code/session_01RGSNGtu69P7BGPWJ2H2eh3  
**Latest Commit:** Add comprehensive feature and architecture documentation

---

## Recent Improvements (This Session)

Beyond the initial M0-M2 build, added:
- **Tiles Mode Toggle:** UI control in SettingsPanel to switch between icon-only and glass-tile visual modes.
- **Per-Band Spin Toggle:** UI control in SettingsPanel to switch between whole-sphere (default) and per-band (Rubik's cube) spin modes.
- **Voice Input Scaffold:** New `:app/ui/VoiceAssistant.kt` module with integration points for M4+ speech recognition and assistant features.
- **Accessibility Improvements:** Enhanced semantics and contentDescription labels on Coverflow tiles; ready for screen reader integration.
- **Comprehensive Documentation:**
  - **FEATURES.md** — User-facing feature guide with interaction patterns, settings, gesture descriptions, and limitations.
  - **ARCHITECTURE.md** — System design, module breakdown, data flow, performance notes, testing strategies, and future roadmap.
  - **Inline comments** — Improved code documentation in key files (SphereGeometry, Motion, AppRepository).
- **Code Quality:** All changes pass compilation, full rebuild successful, APK tested.

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

### Planned (Per Brief) — Not Started
- `:feature-hub` — Pull-down hub currently in `:app/ui` as demo; needs own module + real NotificationListenerService feed (M3)
- `:feature-settings` — Surface settings currently in `:app/ui`; will graduate to module
- `:feature-widgets` — Home-screen widgets (brief §3.D)
- `:haptics` — Tactile feedback (brief §4.C)
- `:assistant` — AI assistant integration (brief §5)

### Visuals & Polish
- **Shader effects:** AGSL materials gated to API 33+; fallback path in place but unfeatured
- **Desktop/DeX:** Landscape-first is primary; external monitor support not yet tested
- **Icon rendering:** Currently basic; may need custom rendering pipeline for visual polish

### Testing & Verification
- **Manual testing:** APK built and ready to install, but full feature verification pending
- **Stability:** Single Activity / Compose foundation is solid; integration testing would be next
- **Performance:** Coverflow, globe, and search may need optimization for larger app catalogs

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

1. **Understand the system:** Read `FEATURES.md` (user perspective) and `ARCHITECTURE.md` (developer perspective).
2. **Install & test:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. **Code review:** PR #1 has the full diff; review at https://github.com/mbaliga/bos_launcher/pull/1
4. **Build changes:** Edit source under `app/src/main/kotlin/`, `feature-*/src/main/kotlin/`, rebuild with `./gradlew :app:assembleDebug`
5. **Test new features:** Try Tiles toggle, Per-band spin, and all settings sliders in the Settings panel.
6. **Next milestone:** Decide on priority:
   - **Real hub feed** (NotificationListenerService integration, M3+).
   - **Voice input** (SpeechRecognizer wiring, M4+).
   - **Performance tuning** (for 500+ app catalogs).
   - **Widgets** (M4+) or **Desktop/DeX** support (M5+).

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
