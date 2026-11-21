# Wave 48: editGaps (HOM-07, HOM-12)

The gaps listed as "not matched" by wave 47 (`docs/status/w47-editIntegrate.md`). Decision record: `docs/decisions/editGaps.md`. Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| HOM-07 | built, feel unverified | Size presets of the widget sheet follow "widgets block": `PresetFit` (core-catalog) refuses a preset that would cover another item (reject buzz + "Can't resize: another item is in the way", nothing pushed), the provider clamp of wave 47 stays; `HomeItemsHost.resizeTo` no longer uses the reflow path. Tests: `PresetFitW48Test`, `ItemEditUiTest` w48 (refused / current size never asked), app `EditIntegrate*` e1, e2. |
| HOM-12 | built, feel unverified | (1) Cancel restores widget sizes, folder contents, item definitions and the clock tile settings, frees widgets added during the session, and these count for the Discard confirm: `EditExtrasRestoreW48Test`, app e3 to e6. (2) Pages Continuous: the bar is above the item, else below the cell under it, never over either: `ItemSheetModelTest` w48 (sweep over every row), `ItemEditUiTest` w48 (first row, middle, last row, old placement still covers), app `EditIntegrateContinuousW47AppTest.f1` on the real surface. (3) Hint below the top bar, music strip and top hotspots; tray above the bottom hotspots when it would overlap: `EditChromeGeometryTest` w48 (seven windows, three requested tray offsets, idempotent, landscape unchanged), `HomeEditChromeUiTest` w48 (node bounds against the hotspot squares in portrait, landscape, 320x568). (4) Page editor on windows under 640 dp high: live card at least 0.45 on 320x568: `PageEditorGeometryTest` w48. (5) Undo/Redo badge and spoken counts; light-theme grid dots: `HomeEditChromeUiTest`, `EditChromeGeometryTest`. Row extended in Spec (one row). |

## Result numbers
- 320x568 with 24 dp system bars: the live card is 0.45 of the window (0.45 is the wish, the panel is 76 dp there; was about 0.31 with bars). Strip cards are 0.6 of default there.
- 411x891: unchanged (0.45, panel about a fifth).

## Changed tests (the behaviour they encoded was replaced)
`HomeEditChromeUiTest` and `HomeEditScreenshotTest`: the Undo/Redo content description is "Undo, 3 available" (was "Undo (3)"). `PageEditorGeometryTest` pe01d and pe01c: the small-window card scale floor is 0.6 (was 0.8) and the content panel floor on windows under 640 dp is 72 dp (was 80).

## Screenshot read
`feature-grid/build/screenshots/w48-1-portrait-hint-tray.png` (Robolectric, 411x891 dp at 2x, stand-in page): the hint sits under the top bar below the page's top corner band, the tray (Apps, Widgets, Folders, + Page) is raised clear of the page's bottom corners; the stand-in page does not draw the hotspots themselves, so the clearance is asserted by the bounds tests, not seen.

## Not matched
See "Not matched / honest limits" in the decision record (the next cell is estimated; the preset check uses the TilePacker order on the scroll surface; Undo/Redo do not restore extras step by step; the 72 dp panel on small windows).

## Device only
Touch feel of the bar over a scrolling surface, the lowered hint and raised tray on a real window with real insets and cutouts, haptics (the preset buzz), TalkBack wording of the counts, the light-theme dots on a real light wallpaper.
