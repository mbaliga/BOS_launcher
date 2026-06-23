# Sphere Launcher

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

## Building

Requires the Android SDK (platform 35, build-tools 35.0.0). Point Gradle at it via
`local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then:

```
./gradlew :app:assembleDebug
```

Notes:
- `minSdk 31`, `targetSdk 35`. AGSL effects are gated to API 33+ (`ShaderCapability`) with a
  flat fallback for reach.
- The prototype `spherelauncher13.html` remains the visual source of truth for look/feel.
