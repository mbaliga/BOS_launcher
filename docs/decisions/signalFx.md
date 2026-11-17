# Radar scope, signal glow lines, comic no-signal lines and the dot grid (wave 14)

## What changed

- **Radar background.** Behind Orbit, Filmstrip and Flat, Radar now draws the owner's breathing dot grid, a quiet scope (rings, crosshair, centre dot, a turning wedge, a periodic ping), the Glow lines signal view and, at no signal, comic focus lines. New files: `RadarBackground.kt`, `RadarBackdropMath.kt`, `SignalViews.kt`, `GlowLinesMath.kt`, `GlowLinesSvg.kt`, `ComicLines.kt`, `RadarBackgroundPane.kt`, `RadarSignal.kt` (core-signals). Edits to shared files are one-liners: `SearchOverlay.kt` (the host call), `SearchChrome.kt` (the old green sweep is no longer drawn), `SettingsCategories.kt` and `Lab.kt` (a pane row and the Lab rows), `Backgrounds.kt` (the Breathing dots scene), `LauncherPrefs` (five new fields, persisted in `launcher_prefs`, which is exported), `L10n.kt` (two expressions).
- **Ground Control > Radar > Radar background**: Sweep, Signal view (Off, Glow lines), Comic lines for no signal, Intensity (Subtle, Normal), and a live preview strip that can show any signal state. **Lab** gets one row per forced state (No signal, Weak, Fair, Good, Excellent, Live).
- **Breathing dots** is a built-in live home background (the default background is unchanged); `BreathingDotGrid` is a reusable composable.
- **Spec**: new requirement PAL-24 (Radar background and signal view); the Radar summary row and CFG-17 mention it.

## Why

Owner request (wave 14): the radar-scope recording (sweep and ping), the glow-lines recording and SVG (signal strength as glow pulses), "comic lines only to show no signal, a reverse kind of calm computing indicator", and the dot grid SVG with "the dots should breathe". The owner will supply more ways to view signal later, hence the `SignalView` registry.

## What the old spec and code said

- The Radar search setting "Background" had a Radar sweep style with a green sweep, rings and blips (`RadarSweep`). That style now means plain black behind the new scope; the green drawing is no longer used (the function stays in `SearchChrome.kt` for now). The existing label text "A picture, plain black, or a radar scope with a turning sweep" still reads true.
- PAL-18 to PAL-23 are unchanged. Nothing in the spec described a background layer before PAL-24.
- Tests that encoded the old live-background list (`BackgroundTest`, `BackgroundGalleryTest`: 13 live scenes, the order of the last five) were updated to 14 scenes with "breathing-dots" last.

## Known limits

- Nothing here is confirmed on a device. The device-checklist rows cover real signal changes, airplane mode, 10 minutes of battery and frame rate.
- Cellular strength uses the dBm of the network capabilities; a finer bar count needs `READ_PHONE_STATE` and is an open question (docs/decisions/radarSignal.md).
- The light variant exists and is tested, but Radar's own base is black in every theme today.
