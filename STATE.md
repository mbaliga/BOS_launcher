# STATE — Sphere Launcher

Living handoff. See `README.md` for the module map and `HANDOFF.md`/`ARCHITECTURE.md` for detail.

## Current state
- M0–M4 done: daily-drivable landscape coverflow (set-as-default), all-apps globe, type-to-search,
  pull-down hub, live surface settings, app management, **notifications** feed, and **voice**.
- Version: `v0.1.0-m1`.
- M5 (desktop / DeX large-screen mode) is pending.
- **Pages home** (`:feature-grid`) is now the default style; see README "Pages home". Per-page layouts
  (grid density / circle / coverflow), physical-corner slots with radial palettes, complications and peek
  widgets, wheel drawer, touch + motion gestures, network-aware dot grid, XMB settings space with UI tones,
  new launcher icon. Persisted in SharedPreferences via `LauncherStores` (core-data).

## Next steps
- M5: desktop / DeX external-display layout + windowing.
- Graduate the hub / settings UI out of `:app/ui` into `:feature-hub` / `:feature-settings` modules.
- Resolve the tracked `core-design` Hyle-consumer debt (see registry) rather than ad-hoc patches.

## Needs on-device verification (Pages home)
- Gesture feel (drag-rearrange, pinch, palette slide-select, coverflow ↔ pager hand-off), motion-gesture
  thresholds on real sensors, widget bind/config flows, background motion service on Android 14/15,
  network-dot colours on cellular. JVM tests + Robolectric screenshots cover logic and rendering only.

## Owner-verified (on-device behaviour)
- The build env has no device — all runtime/gesture/render/launcher behaviour is **owner-verified on the phone**.
- Set-as-default (`ROLE_HOME`), coverflow scrub/launch gestures, globe, search, hub, and voice are owner-confirmed on-device.
