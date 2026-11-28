# Background reset: what was removed, why, how to get it back, and the new API (wave 15)

## Why

Owner request (wave 15): "We have till now added a lot of things and then whittled them down, I want to take a slightly different approach - for the things I am now specifying, only keep the options I am proposing, remove the others we had before." The proposed options are nine signal-driven items in two slots (HOM-16, PAL-24). Everything of the previous background gallery goes.

## Removed, exactly

Code (all in `feature-grid` unless noted):
- `Backgrounds.kt` (about 1,500 lines): `BackgroundPresets` (24 solid colours, 20 gradients, 15 textures, 13 live scenes plus the "breathing-dots" live scene, the Horizon picture), `BackgroundLayer`, `BackgroundMath`, `TextureTiles` and the texture renderer, `LiveBackground` and every live scene (aurora, starfield, waves, fireflies, nebula, rain, ribbon, bokeh, lava, snow, matrix, orbits, plasma), `liveSampleColors`, `horizonSampleColors`, `observeBackgroundTouch` and `TouchPoint` (the bokeh scene's touch input).
- `PictureBackground.kt` (`PictureSource.Horizon` and `PictureSource.File`, EXIF-aware decode) and the bundled resource `res/drawable-nodpi/search_backdrop.webp` (the procedural-looking Horizon picture, the Radar backdrop picture).
- `HomeWallpaper.kt`: `WallpaperScrollEffect`, `LocalWallpaperForward`, `rememberHomeAccent`, and the `wallpaperScroll` parameters of `ContinuousHome`, `XmbHome`, `LandingLook` (the system-wallpaper scroll-offset forwarding for every home style).
- `RadarBackgroundPane.kt` (the wave 14 Radar-only pane: Sweep, Signal view, Comic lines switches).
- `HomeLegibility.kt`'s `WallpaperSamples` (static colours of solids and gradients, photo sampling, system wallpaper colours); the file now samples the real painters.
- `core-catalog`: `WallpaperMath`, `WallpaperScroll`, `WallpaperBridge` (`setWallpaperOffsets`, `WallpaperColors` accent) and their tests.
- `core-data`: `BackgroundKind`, `SearchBackdropStyle`, and the prefs `backgroundKind`, `backgroundPreset`, `backgroundPicturePath`, `backgroundColorA`, `backgroundColorB`, `showWallpaper`, `searchBackdrop`, `searchBackdropStyle`, `radarSweep`, `radarSignalView`, `radarComicNoSignal`; the device-bound picture path (`DeviceBound.pictureOf`, the `bgPicture` key); the picture migration (`BackgroundPictures.migrate`) and the restore guard's `backgroundCleared`.
- `app`: the photo picker for the background, `SettingsCategories` Background pane with its kind and preset pickers and colour pickers, the Radar "Background" style row and "Radar background" pane, the `pickBackgroundPhoto` callback, the home accent from the system wallpaper.

Kept on purpose (not part of the gallery): the palette source "From my wallpaper" in Ground Control > Look (CFG-10, `WallpaperColorReader` in `core-design`, a theme feature that reads wallpaper colours and draws nothing), the `shaders` module (`ShaderCapability`, no background used it), `searchMotion` (Filmstrip and Orbit drift), the Settings backdrop choice (now "Background, dimmed" or "Pitch black").

Tests removed with their features: `BackgroundTest` (45 tests of presets, maths, texture and live scenes), `BackgroundGalleryTest`, `TextureStressTest`, `WallpaperScrollUiTest`, `WallpaperScrollTest`, `AdaptiveWidgetsTest.HOM_16_wallpaperOffsetsAndAccent`, `W4MotionUiTest` live-scene and Horizon sampling tests (replaced by tests of the new items), the solid and gradient sampling test in `ReducedMotionUiTest` (replaced by a None test). Tests that only touched the removed fields (`DeviceBoundTest`, `StateBackupExclusionTest`, `RehydrationTest`, `StateCodecTest`, `StateStoreTest`) lost those lines; the rest of each is unchanged.

Spec: HOM-16, PAL-24 and the "Backgrounds" and Radar rows of section 3 are rewritten (see below); PAL-18 and CFG-17 mention the change. SPEC_STATUS.md is not edited by this stream (rule of the wave); its rows for HOM-16 (was "partial: system-wallpaper background, scroll-offset forwarding, accent"), PAL-24 and NFR-15 (live scenes sampled) now describe removed behaviour and should read "removed by owner decision, replaced by the two-slot model" when the statuses are merged.

## Migration (no crash on old data)

- Stored old choice: the keys `bgKind`, `bgPreset`, `bgA`, `bgB`, `bgPicture`, `wallpaper`, `searchBackdrop`, `searchBdStyle`, `radarSweep`, `radarSignalView`, `radarComic` are no longer read. With no `bgSlot1` and `bgSlot2` stored, every install gets the default pair (Spire + Breathing dots). The first write deletes the retired keys (`RetiredBackgroundKeys`). `radarIntensity` and `radarSignalForce` live on (Intensity and the Lab override).
- Leftover pictures: `BackgroundPictures.purge` deletes `background_*.img` from `filesDir` and `noBackupFilesDir` once at start, off the main thread.
- Exports and imports: export schema 3, settings document 2. A new step 2 to 3 in `StateMigrations` drops the retired keys from an older state file; `SettingsCodec.decode` also ignores them (a standalone settings file, a build 31 prefs file) and does not re-emit them through the unknown-keys pass-through. Fixture `state_v2.json`, tests `BackgroundMigrationTest`, `RadarBackgroundPrefsTest`.

## How to restore

`git revert` the commit named "Background reset" of branch `w15-backgrounds` (it holds the removal and the new model together, so reverting it returns the whole gallery); the prefs keys then read again. Reverting only the removal is not supported: the new model replaced the fields.

## The API for surfaces (used by Glance home and any other surface)

```kotlin
// feature-grid, package com.bos.sphere.feature.grid
@Composable
fun SignalBackground(
    prefs: LauncherPrefs,          // reads backgroundSlot1, backgroundSlot2, radarIntensity, radarSignalForce
    modifier: Modifier = Modifier, // fills its box; no touch handling; no semantics except one polite live region
    dark: Boolean = true,          // false: the quiet light variant
    baseFill: Boolean = false,     // paint the black (or light grey) base too
    animate: Boolean = true,       // false: one static frame (thumbnails, Settings backdrop)
    announce: Boolean = true,      // false: do not announce the signal level to TalkBack (use for a second copy on screen)
)
```
Place it as the bottom layer of a `Box`, under the text. It follows the phone's signal by itself (collected only while composed and the activity is started), runs one shared clock only while resumed and focused (never under reduced motion), and costs nothing when both slots are None. Legibility: `rememberHomeScrim(BackgroundChoice(slot1, slot2), intensity, dark)` returns the NFR-15 plan and `HomeScrim(plan)` draws it; `BackgroundSamples.colors(...)` gives the sampled colours when a surface wants to measure its own text. Pure model: `BackgroundChoice` and `BgItem` in `core-data` (`with(slot, item)`, `refusal(slot, item)`, `items`, `wallpaper`). Per-level parameters: `BackgroundCurves` (one table per parameter, level 0..4 interpolated).

## Where each wave 14 piece went

The dot grid, the scope sweep and the glow lines are the items Breathing dots, Radar sweep and Glow lines; the comic lines are drawn at no signal over any choice; the Lab force-signal rows are unchanged; `RadarBackgroundHost` became `SignalBackground`, `RadarBackdropConfig` became `BackgroundConfig`, `RadarBackdropPainter` became `BackgroundPainter`.
