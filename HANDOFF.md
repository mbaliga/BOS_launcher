# Sphere Launcher — Project Handoff

**Date:** June 24, 2026  
**Session:** https://claude.ai/code/session_01RGSNGtu69P7BGPWJ2H2eh3

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

## How to Pick Up

1. **Install & test:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. **Code review:** PR #1 is draft; review the diff at https://github.com/mbaliga/bos_launcher/pull/1
3. **Build changes:** Edit source under `app/src/main/kotlin/`, `feature-*/src/main/kotlin/`, rebuild with `./gradlew :app:assembleDebug`
4. **Next milestone:** Decide on `:feature-hub` (real NotificationListenerService feed), `:feature-widgets`, or performance tuning

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
