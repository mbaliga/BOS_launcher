# Sphere Launcher

A landscape-first Android launcher whose home surface is the surface of a sphere: apps live on
a coverflow "equator" with a continuous magnification lens at the front-center tangent point.
Built from scratch in **Kotlin + Jetpack Compose** (no AOSP/Pixel fork), with AGSL materials
gated for capable devices. See the build brief for the full vision (M0–M5).

This repository currently implements the **M0 scaffold + M1 spine** — the runnable, themed,
landscape coverflow you can set as the default launcher and launch apps from.

## Module layout

| Module | Responsibility |
|---|---|
| `:app` | Single-Activity Compose host, manifest (HOME + DEFAULT), set-as-default flow, `HomeScreen` |
| `:core-design` | Hyle/Aarso design system — color, type, shapes, `SphereTheme`, the 8 named motion primitives |
| `:core-data` | `AppRepository` over `LauncherApps` exposing a live `Flow<List<AppEntry>>` |
| `:feature-sphere` | `SphereGeometry` (ported `magAt`/`txAt`), `SphereState`, the `Coverflow` composable |
| `:shaders` | AGSL capability gate (`RuntimeShader` is API 33+) with a fallback path |

Planned (per the brief): `:feature-search`, `:feature-hub`, `:feature-settings`,
`:feature-widgets`, `:haptics`, `:assistant`.

## What works now (M0 + M1 spine)

- Becomes a real home app: manifest HOME + DEFAULT intent-filters, `RoleManager.ROLE_HOME`
  request with a home-settings fallback, transparent wallpaper-backed window.
- Live app inventory from `LauncherApps` (+ `LauncherApps.Callback` for install/remove/update,
  work-profile aware), launched on tap.
- Coverflow equator with the prototype's magnification-integral spacing rendered via
  `graphicsLayer`: drag to scrub with integer snap, tap a side tile to bring it to focus, tap
  the focused tile to launch.
- Orientation-driven row split (Auto = 2 rows portrait / 1 landscape) with focus-preserving
  remap.
- Hyle theme + motion vocabulary applied throughout.

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
