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

- Round 2 (owner feedback): edge-to-edge pager, flush status strip, ring-wedge palettes, curved wheel
  labels, XMB settings with tap-to-configure, discreet default hint, light search overlay, spacing +
  pinch density, indicator effects, drawer space, theme / icon theming, UI languages, R8 release build.

## Needs on-device verification (Pages home)
- Gesture feel (drag-rearrange, pinch, palette slide-select, coverflow ↔ pager hand-off), motion-gesture
  thresholds on real sensors, widget bind/config flows, background motion service on Android 14/15,
  network-dot colours on cellular. JVM tests + Robolectric screenshots cover logic and rendering only.

## Owner-verified (on-device behaviour)
- The build env has no device — all runtime/gesture/render/launcher behaviour is **owner-verified on the phone**.
- Set-as-default (`ROLE_HOME`), coverflow scrub/launch gestures, globe, search, hub, and voice are owner-confirmed on-device.

## Round 4
- Bubbles (Apple-Watch-style fisheye field) as a home style and a drawer view (`BubbleField`).
- Backgrounds: Black / System / Solid / Gradient / Texture / Live (8 live scenes, incl. Ribbon and Bokeh with touch-scatter); `BackgroundLayer`.
- Settings option panes open full screen with tappable breadcrumbs (`DetailScreen`) and visual, real-component previews (`SettingsPreviews`).
- Search: contact rows with Call/SMS/Email/WhatsApp/Telegram/Signal, macOS-style refinement chips, web rows (DuckDuckGo default so !bangs work, Play Store), `READ_CONTACTS` requested on demand.
- Three-card onboarding.
- Not verified on a device. Headless screenshots don't apply per-bubble layer transforms, so the bubble field has only unit tests (math) and no picture. New strings are English-only until translated.

## Round 6
- Launchpad (notifications centre, BB10-Hub style) as the left page: one swipe right of page one, or the unread strip; can be Off or any app widget full-screen. Grouping (time/app/sender/conversation/flat) and rules per app / sender / conversation / text (hide, mute, pin, auto-clear).
- Search is now called Radar.
- Settings sit on the dimmed wallpaper (or pitch black).
- More wallpapers: 24 solids, 20 gradients, 15 textures, 13 live scenes, Horizon picture and your own photo.
- Grid: separate across/down spacing, margins, label size, up to 10 columns.
- Not verified on a device (swipe-to-open gesture, widget hosting on the left page, notification listener behaviour).

## Round 7
- New home styles: PSP (XMB cross media bar) and Nintendo DSi (3 across, continuous scroll; lengthwise in portrait, across in landscape).
- Pages style can scroll continuously (Settings → Home screen → Scrolling).
- Settings regrouped; clock on the corner icons' centre line; no phantom search suggestions.
- Not verified on a device: gestures/fling in XMB and continuous homes, edge fades.
