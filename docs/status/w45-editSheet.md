# Wave 45: editSheet (HOM-07, HOM-12)

| ID | Status | Note |
| --- | --- | --- |
| HOM-12 | built | Item action bar (`ItemActionBar.kt`) and item sheet (`ItemSheet.kt`) replace `TileMenuDialog` and the "Remove from page" pill. Selection on the existing long-press events through `ItemSelectionSource`. Remove with a 6 s Undo (edit-session undo). Tests: `ItemSheetModelTest` (placement maths, option lists), `ItemEditUiTest` (bar, 48 dp buttons, remove and undo, sheet per item type, clock tile changes, Esc, labels, screenshots), `GlanceModelTest` (clock tile prefs). |
| HOM-07 | built | Widget sheet: Reconfigure (when the provider has a configure activity) and size presets through the existing resize path. Provider min/max limits are not applied (handles stream). |

Clock tile options built: Style (Digital / Analog), Hours (system / 12 / 24, digital only), Show date. Omitted: size 2x1 / 2x2 (no span model for tiles), Duplicate, folder change icon, app rename label.

Not verified: nothing here ran on a device. Long-press selection, the bar position against real handles and dock, the sheet over the shrunken home card, and TalkBack output are untested outside Robolectric. In the screenshots the bottom sheet window is cropped by the test capture. The handles stream's selection holder was not available; the integration is the `ItemSelectionSource` interface.

Old tests changed: none needed (no test referenced the removed dialog).
