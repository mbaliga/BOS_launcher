# Liftoff Launcher

> **Constellation** · `state: active` · public · [registry: `Personal-Tracker/CONSTELLATION.md`](https://github.com/mbaliga/Personal-Tracker/blob/main/CONSTELLATION.md)
> Sphere/coverflow Android launcher (Compose, no AOSP fork).

A landscape-first Android launcher. Since wave 16 the default landing is **Glance** (a near-empty home: clock and date,
the music playing, optional weather ambience, hotspots and a surface indicator) over a two-slot, signal-driven background;
the paged Pages home, the Recent apps and All apps landings and the original sphere/coverflow style are one setting away
(Ground Control > Home > Landing). Built from scratch in **Kotlin + Jetpack Compose** (no AOSP/Pixel fork), with AGSL materials
gated for capable devices. The product spec is `docs/Spec.md`; how far each requirement is lives in `docs/SPEC_STATUS.md`
(decisions in `docs/decisions/README.md`).

## Module layout

| Module | Responsibility |
|---|---|
| `:app` | Single-Activity Compose host, manifest (HOME + DEFAULT), set-as-default flow, `HomeScreen`, Ground Control settings, the recovery wiring |
| `:core-design` | Hyle/Aarso design system: color, type, shapes, themes, motion, the in-app translation tables (`L10n`, 8 languages) |
| `:core-data` | Preference stores, the state export/import codec, `AppRepository` over `LauncherApps`, crash-loop guard (`CrashLoopGuard`), one-time retired-key cleanup (`RetiredPrefKeys`) |
| `:core-catalog` | Home item catalog, drawer, widgets, badges, profiles, package reconcile |
| `:core-capabilities` | Capability report and OEM notes (CFG-08, NFR-21) |
| `:core-launch` | Launching apps, shortcuts and intents |
| `:core-input` | Shortcut registry, key routing, context menus, the puck, gestures |
| `:core-quality` | Budgets, contrast and accessibility helpers, and the NFR audit tests |
| `:core-signals` | The signal level that drives the background and the media/now-playing state |
| `:feature-sphere` | `SphereGeometry`, `SphereState`, `CoverflowLayer`, all-apps `Globe` (the Sphere style) |
| `:feature-grid` | The Pages home, Glance and the other landings, the two-slot `SignalBackground`, corners and palettes, Radar's screen (Flat, Filmstrip, Orbit), the XMB-style settings space |
| `:feature-search` | Radar's providers and wiring (apps, settings, files, shortcuts, calculator) over the shared engine, plus the always-on keyboard |
| `:feature-dock` | The dock, workspaces and the Desktop-class footprint |
| `:feature-desktop` | The desktop surface and its widgets |
| `:feature-launchpad` | Comms (the left card), the Recents panel (right card) and notification groups |
| `:search-engine` | Vendored `search-core` from Shared-Libraries-asoc: query language, facets, relative dates, natural-language interpretation, ranking; and the Radar provider model |
| `:crash-recovery` | Vendored `crash-recovery` 1.4.0 from Shared-Libraries-asoc: the crash splash and recovery screen (readable report, Copy, Save, Share, Continue, loop-gated Reset), plain android.widget; our Safe mode is its second layer |
| `:shaders` | AGSL capability gate (`RuntimeShader` is API 33+) with a fallback path |

Removed in wave 15 and later: the background gallery and its picture and wallpaper options, Radar's typed prefixes and `!bang`
syntax, the bottom search field, and the full-screen portrait Comms.

## What works now

- **Real home app:** manifest HOME + DEFAULT, `RoleManager.ROLE_HOME` request with a
  home-settings fallback, transparent wallpaper-backed window, home-key reset (`onNewIntent`).
- **Live inventory** from `LauncherApps` (+ `LauncherApps.Callback`, work-profile aware),
  filtered by a persisted hidden-apps set.
- **Coverflow equator** with the prototype's magnification-integral spacing via `graphicsLayer`;
  orientation-driven row split (Auto = 2 rows portrait / 1 landscape) with focus-preserving remap.
- **Unified navigation gestures** (one arbitrated handler): drag to scrub with snap, tap to
  focus/launch, **pinch-in → all-apps globe** (rotate + lens), **spread → settings** (or back
  home), **pull-down → hub**, long-press → app menu.
- **All-apps globe:** Fibonacci-sphere projection, yaw/pitch rotation, magnification lens.
- **Type-to-search** (portrait always-keyboard): live ranked results, tap or Enter to launch.
- **Pull-down Hub:** glass panel with a demo "Needs you" feed + dismissable chips (real
  NotificationListenerService feed lands at M3).
- **Live surface settings:** the prototype's sliders (perspective / spacing / turn / depth /
  min-scale / focus / arc / tilt) + row mode, tuning the equator in real time.
- **App management:** long-press → App info / Hide-Unhide / Uninstall.
- **Hyle identity:** theme, the named motion curves, dot-grid substrate, ambient chrome
  (clock / weather / battery / now-playing / settings gear).
- **Liftoff Studio (`web/liftoff-studio/`, wave 56):** a static browser page that edits a Liftoff configuration off the device with a live drawing of the home (Glance, pages, Radar, the drawer disc, Comms, Recents) beside the six settings tabs, keeps named profiles in the browser, and reads and writes the files the app's Backup uses. No build step, no external request; not yet published (see `web/liftoff-studio/README.md`).

## Pages home (default style)

The home that ships by default is a paged, rotatable surface (the Sphere coverflow remains as a style
in settings):

- **Pages** — each page is its own **grid** (with its own density, or the home default), a **circle**
  (centre + rings of 6/12/18) or a **coverflow** strip. Long-press an app and drag to rearrange (edge-dwell
  flips pages; drop past the last page to make a new one). Page-turning direction is chosen per
  orientation (sideways / up-down); the indicator follows the axis.
- **Clock** — top (default), bottom, hidden, a page tile, or a corner complication.
- **Corners** — four slots pinned to the device's **physical** corners as it rotates (or fixed to the
  screen). Each holds an action, an app, a **palette** (a folder that fans out radially from the corner:
  tap then tap, or press-slide-release), a **complication** / cluster (battery, date, time, alarm,
  ring mode, torch, storage, notifications), or a **widget** that peeks from the corner and pops out on
  touch. Glyph or quarter-circle Bubble style. Long-press a corner to change it.
- **All apps** — opens as a **wheel** around the corner you opened it from (groups as spokes: A–Z,
  category or recent; drag to turn), or as a plain list; Search lands in the list with the keyboard up.
- **Gestures** — pinch in/out, swipes across the paging axis, double-tap; **motion**: shake, flick twice,
  flip twice, face-down → any action (ringer vibrate/silent/cycle, DND, flashlight, apps…), optionally
  from a background foreground-service.
- **Network dots** — the substrate dot grid breathes green (good signal) → red (poor), solid grey in
  airplane mode, dark when offline.
- **Status strip** — a 3DS-style bar (top or bottom) with left / centre / right zones: the clock (size,
  12/24-hour, day, date, optional weather) plus small readouts — battery, signal, steps, next alarm, ring
  mode, storage, memory, notifications.
- **Spacing and density** — icon size and spacing are separate; a bound gesture (e.g. pinch) steps the
  grid denser or sparser and pulls icons forward to fill the gaps.
- **Indicator** — the background reads out any measurement (signal, battery, storage, memory,
  notifications, volume, time of day) through combinable effects: breathing dots, dot bloom from the
  centre or edges, vignette, comic-book lines, snake outline, edge meter; colours are configurable.
- **Apps and drawer** — keep all apps on the pages or only chosen ones, one page or many; the drawer is
  its own space (grid / list / details; A–Z, type, recent, most used; edge scrub rail).
- **Theme and language** — dark / AMOLED / light, accent colour picker, icon shape / tint / plate and
  per-app icon colour; the launcher's own UI is translated (es, fr, de, pt, hi, zh, ja, ar).
- **Search** — the shared engine: plain words match name and package as you type; facets `type:games`,
  `used:yesterday`, `launches:>5`, `is:work|recent|frequent|never`, `pkg:`; quotes, `OR`, `-not`; and
  natural language when a plain query finds nothing ("games I used yesterday"). An always-on keyboard
  mode makes the home screen a type-to-find phone.
- **Settings space** — long-press anywhere empty: a full-screen cross-media-bar (categories across,
  items down, option pane at the side), with Orrery's console tones (`UiSound`, ported from
  `orrery-kit/sfx.js`).

## Building

Requires the Android SDK (platform 35, build-tools 35.0.0). Point Gradle at it via
`local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then:

```
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest   # JVM tests incl. Robolectric screenshot smoke tests
```

`ScreenshotTest` (in `:feature-grid`) renders the main surfaces to `feature-grid/build/screenshots/`.

Notes:
- `minSdk 31`, `targetSdk 35`. AGSL effects are gated to API 33+ (`ShaderCapability`) with a
  flat fallback for reach.
- The prototype `spherelauncher13.html` remains the visual source of truth for look/feel.

## Do not touch

- The **AGSL shader path is capability-gated to API 33+** — don't ungate it (flat fallback below 33).
- **Play-Integrity posture: no root / no Shizuku dependency** — keep it.
- `core-design` reimplements a Hyle-like palette — a tracked Hyle-consumer debt (see registry); do not "fix" it ad hoc.

## Licence

Licensed under the [Apache License 2.0](LICENSE) (SPDX: `Apache-2.0`). Copyright 2026 Madhav Baliga.
