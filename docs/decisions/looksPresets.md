# Looks presets and calibrators (wave 56)

Owner: "Look at clackpad and emulate the settings (Clackpad config) from there for our settings." Source: docs/handoff/clackpad_vs_liftoff_settings.md items 1 and 9.

## Decisions
- A Look is a `ThemePack` (data), applied by the existing `ThemeApplier`. The pack model gained two optional fields, `calmStyle` and `iconPlate`, because a whole look needs them; the format version stays 1 and old files parse unchanged. `capture` (export) was left alone.
- Matching (Clackpad `QuickTheme.matches`) compares every field the Look sets with the stored value; the wallpaper counts only while the Glance wallpaper is a Solid or Gradient. Applying never replaces Signal, system or picture wallpapers.
- The rail is a rail row (title, cards inline, no page, no box); the result is a flat "Applied X" line plus the existing "Undo last theme change" text row, so no dialog.
- The brief's "Glass" Look became "Dusk": the glass look was removed in wave 25 and the name would mislead. Transparent dock is still covered.
- Reset to default look keeps the calm style and icon plate (they were never part of that default).
- Card art is drawn in code from the Look's values rather than captured from a screenshot: it stays correct when a Look is added and needs no bitmaps.
- Calibrators are measurements from event times (no clock to inject), written through the slider's own setter. Long press and double tap windows are not Liftoff settings, so none exists. The shake threshold function moved to `Calibrators.shakeThreshold` and the detector calls it, so the two cannot drift.
- The Try it rows reuse the rail-row presentation (`SettingItem.Choice` with a `preview`), so the shared settings machinery needed no new row kind.

## Device only
Real accelerometer shake values, mouse dwell for the hot corner, and the look of the cards over real wallpapers.
