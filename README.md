# Sphere Launcher

> **Constellation** · `state: active` · public · [registry: `Personal-Tracker/CONSTELLATION.md`](https://github.com/mbaliga/Personal-Tracker/blob/main/CONSTELLATION.md)
> Sphere/coverflow Android launcher (Compose, no AOSP fork).

A landscape-first Android launcher whose home surface is the surface of a sphere: apps live on
a coverflow "equator" with a continuous magnification lens at the front-center tangent point.
Built from scratch in **Kotlin + Jetpack Compose** (no AOSP/Pixel fork), with AGSL materials
gated for capable devices. See the build brief for the full vision (M0–M5).

This repository implements the **M0 scaffold through most of M1–M2** — a daily-drivable landscape
coverflow you can set as default, plus the all-apps globe, type-to-search, a pull-down hub, live
surface settings, and app management.

## Module layout

| Module | Responsibility |
|---|---|
| `:app` | Single-Activity Compose host, manifest (HOME + DEFAULT), set-as-default flow, `HomeScreen` |
| `:core-design` | Hyle/Aarso design system — color, type, shapes, `SphereTheme`, the 8 named motion primitives |
| `:core-data` | `AppRepository` over `LauncherApps` exposing a live `Flow<List<AppEntry>>` |
| `:feature-sphere` | `SphereGeometry` (ported `magAt`/`txAt`), `SphereState`, `CoverflowLayer`, all-apps `Globe`, and `LauncherSurface` (unified gestures) |
| `:feature-search` | Apps-only search ranking + the portrait always-keyboard |
| `:feature-grid` | The **Pages** home (default): paged grid / circle / coverflow pages, corners + radial palettes, complications, peek widgets, wheel + list drawers, network dot grid, the XMB-style settings space and its synthesized UI sounds |
| `:shaders` | AGSL capability gate (`RuntimeShader` is API 33+) with a fallback path |

Planned (per the brief): `:feature-hub`, `:feature-settings`, `:feature-widgets`, `:haptics`,
`:assistant`. (The hub / settings UI currently lives in `:app/ui` and will graduate to modules.)

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
- **Hyle identity:** theme, the 8 motion primitives, dot-grid substrate, ambient chrome
  (clock / weather / battery / now-playing / settings gear).

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
