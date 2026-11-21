# Item action bar and item sheet (wave 45, editSheet)

Owner feedback: the home and page editing "is bonkers". Diagnosis item 9 of `docs/research/home-editing.md`: the clock tile editor was a full-screen dialog with one hint line, a loud "Remove from page" pill and a Cancel pill; its real options lived in another settings page.

## Decisions

1. **One selection seam.** `ItemSelectionSource` (key, bounds, clear) is the only thing the bar needs. The handles stream's selection holder already has those members and can implement it; until it lands the home feeds `HomeItemSelection` from the existing long-press events (`selectItem` in `HomeScreen.kt`). While editing, a long press on an app, folder, widget, shortcut, link or tile selects it instead of opening the old popup menu; outside edit mode the app menu is unchanged, and a long press on a tile starts editing and selects it.
2. **Action bar** (`ItemActionBar.kt`): 48 dp pill with icon and label buttons, Resize (widgets only: other items cannot change size, so no dead button), Remove, Settings. Placement is pure maths (`ActionBarMath.place`): below the item, else above, else clamped; the item's rect is grown by 30 dp (the handle reach) so the bar never sits on the handles; the dock and the top bar are excluded by shrinking the area. Remove calls the existing removal and shows a 6 s Undo snackbar that runs the edit session's undo. There is no Duplicate button: nothing in the app can duplicate an item.
3. **Item sheet** (`ItemSheet.kt`): `OneUiSheet` with a 20 percent scrim, grabber, heading, focus taken by the sheet, Esc closes. Rows are One UI rows; two or three short choices are a segmented control, never a popup. The row lists are pure (`ItemSheetModel.options`) and tested per item type.
4. **Clock tile options live in the sheet** and are three new preference keys in the existing launcher prefs file: `clockTileAnalog`, `clockTileFormat`, `clockTileDate`. Defaults (digital, system hours, date shown) are exactly the old look, so no migration is needed. The Ground Control Glance clock rows stay; the settings toggle "Clock tile on this page" stays because it adds or moves the tile, which the sheet cannot do.
5. **Omitted on purpose:** clock tile size 2x1 / 2x2 (the tile is a one-cell icon in the page model, there is no span for tiles, adding one is a layout-model change); folder "Change icon" and app "Rename label" (no model for either); Duplicate.
6. **Widget size presets** are 2x1, 2x2, 4x2 clipped to the page grid and applied through the existing resize path, which clamps to the grid; the provider's own min/max resize limits are the handles stream's work and are not applied here.
7. **Accessibility:** every bar button carries a description naming the item, the sheet has a heading, and the grid item nodes carry "Open item settings" in edit mode (`LocalItemSettingsOpener`).

## Merging with editHandles / editChrome

`ItemActionLayer(showOutline = false)` when the handles stream draws the outline. Setting its `EditSelectionState.fallbackBar = false` retires their minimal Remove button. The layer takes `reserveTop` / `reserveBottom` for the chrome stream's top bar and tray.

## Wave 47 (editIntegrate)
The seam is joined: `HomeItemSelection` is deleted, `ItemSelectionSource` is implemented by `EditSelectionState`, `showOutline` is false by default, the fallback Remove button is off and the bar's room comes from the measured chrome (top bar, hint, tray, dock) instead of the fixed 64 dp and dock + 72 dp. Resize no longer starts a mode: it focuses the handles of the selected widget. Size presets are clamped to the provider limits. See `editIntegrate.md`.
