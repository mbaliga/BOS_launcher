# Wave 56: looksPresets status

Nothing here is device-confirmed. JVM and Robolectric only. Screenshots read: `feature-grid/build/screenshots/looks56-*.png`, `calib56-*.png`, `app/build/screenshots/settings-w56-*.png`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-26 | built (device untested) | Looks rail (Midnight, Paper, Terminal, Dusk, Sunrise, Mono) first on the Look page; drawn miniature per Look; tap applies through ThemeApplier with Undo; selection computed; picture and live wallpapers left alone; packs may carry calmStyle and iconPlate |
| CFG-27 | built (device untested) | Try it rows for Hot corner delay, Controls hide after and Motion sensitivity with measured readout, Set to and Reset to default; long press and double tap windows are not settings, so not done |

## Tests
- core-data `LookPresetsW56Test` (every Look applies, matches itself only, undo, wallpaper rules, pack fields), `CalibratorsW56Test` (pure measures).
- feature-grid `LooksRailW56Test` (cards, selection, pixels from the Look's wallpaper, all pictures differ, 48 dp), `CalibratorAreasW56Test` (hold, cancel, two taps, injected shake, reset, no sensor).
- app `LooksCalibratorsW56Test` (rail first on Look, apply, undo, wallpaper kept, Try it placement and writes, page screenshots).
- Strings: `L10nW56looksPresets.kt` (8 languages).

## Not matched
- Card art is drawn in code, not a capture of the real home (Clackpad used real crops).
- "Glass" Look renamed Dusk (glass look removed in wave 25).
- The rail sits on the Look page only, not as a first card row of a fresh Settings root.

## Device only
Accelerometer shake strengths, mouse hot-corner dwell, appearance over real wallpapers.
