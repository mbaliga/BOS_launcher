# Wave 59: glanceTop (CFG-75 to CFG-79)

Nothing was run on a device. Verified by JVM unit tests, Robolectric UI and pixel tests (window copies with PixelCopy), Robolectric app tests on the real MainActivity, the Studio node tests, and by reading the screenshots listed below. Decisions: `docs/decisions/glanceTop.md`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-75 | partial (device untested) | The music line's marquee fades its TEXT to transparent with an alpha mask (offscreen layer, `DstIn` gradient) at a clipped edge only; the `edge` colour (black on dark, pale grey on light) is gone. A top corner reserves room only when it draws something (on and assigned): `GlanceHotspots.drawnOnScreen`. Pixel tests over white and a patterned background, dark and light theme, fail with the old gradient (checked). Studio's drawing follows the rule with an SVG mask. Device-only: the look over a real live wallpaper. |
| CFG-76 | partial (device untested) | "Music line width" cards (Fit around corners, default; Full width, 12 dp margins) in Home > Glance under Show music, dimmed while Show music is off; `musicLineWidth` in launcher_prefs, exported, Home tab reset, Studio schema regenerated, Studio drawing honours it, the Preview's Glance topic draws the real chrome. A corner tap over the full-width line runs the corner (test). Device-only: touch feel at the corners. |
| CFG-77 | partial (device untested) | The Hotspots editor (live miniature with the real corner layer, style and glyphs; tap toggles, long press or Edit opens the corner editor, the selected corner pulses, compact row below) replaces the presets and switches in Settings (first block of Home > Corners), the Home sheet (a Hotspots tile opening it as a full page), the page editor's Settings tab and Studio. The stored set is kept as it is (migration test). The Preview's Corners topic rings the corners that are on and pulses the editor's corner. |
| CFG-78 | partial (device untested) | Each corner a switch "<corner> hotspot, <item>" with On/Off state, long-press action and custom action Edit; arrows, Enter and E; right to left keeps the physical corners and reads translated names. Device-only: TalkBack and a hardware keyboard. |
| CFG-79 | partial (device untested) | Landscape miniature in landscape (physical corners under each screen corner); with Separate corners for landscape a Portrait / Landscape control edits that orientation's set (`editCornerLandscape` in the home). Device-only: rotation on a phone. |

## Tests added
- core-data `GlanceTopW59Test`: drawn vs empty corners, every assignment kind, rotation; `musicLineWidth` default, persistence, garbage, settings file; removed-preset migration (stored sets load and save unchanged; the Calm start).
- feature-grid `GlanceTopRulesW59Test` (width rule with Full width, which edges fade, arrows, orientation and sets), `GlanceTopFadeW59PixelTest` (4 pixel tests), `GlanceTopChromeW59UiTest` (Full width line width and corner touch wins, Fit around drawn corners, nothing drawn = edge to edge), `HotspotsEditorW59UiTest` (11: tap toggles and persists, long press / Edit / custom action open the editor for that corner, pulse and reduced motion, semantics, keys, landscape, separate corners, no orientation control with one set, right to left, Quarter style).
- app `GlanceTopW59SettingsTest` (editor first in Corners and the old rows gone, Music line width cards persist and dim, in-Settings tap persists to launcher_prefs and Edit opens the corner editor), `GlanceTopW59SmokeTest` (real app: Home sheet tile opens the editor page, tap persists, long press opens the corner editor, the miniature adds no focus nodes; enabled-but-empty top corners leave the line full width; Full width under drawn corners), `HomeQuickSheetVisualTest.CFG_77_theHotspotsTileOpensTheEditorAndTheOldCardsAreGone`.
- Studio node tests `CFG-75, CFG-76: the music line ...` and `CFG-77: the hotspots field has no presets ...`.

## Old tests changed or deleted (behaviour replaced on purpose)
- `GlanceModelTest.HOM_23_hotspotPresetsAreFourTwoOneNone` (core-data): deleted with the presets; replaced by `HOM_23_allFourHotspotsAreOnByDefault`.
- `GlanceAuditW20Test.HOM_23_everyHotspotPresetDrawsTheRightCountInEveryRotation` (core-data): renamed `HOM_23_everyHotspotSetDrawsTheRightCountInEveryRotation`, plain sets instead of presets.
- `StudioSchemaW56Test` (core-data): `musicLineWidth` field; `glanceHotspots` and the `corners.*` fields now name the row "Corners/Hotspots"; the layout check looks for the editor in Corners instead of "Top left hotspot" in Glance.
- `SettingsArtW54Test` (feature-grid): the Hotspots art set is replaced by the Music line width art set.
- `GlanceHomeUiTest.HOM_23_aDisabledHotspotDrawsNothingAndTheCountFollowsThePreset` (feature-grid): plain sets instead of presets.
- `PageEditorUiTest.HOM_12_pe24b_...` (feature-grid): asserts the hotspot switches are gone from the Glance settings tab.
- `GlanceSmokeTest.HOM_23_theHotspotPresetsShowFourTwoOneOrNone` (app): renamed `HOM_23_theHotspotSetsShowTheirCount`, plain sets.
- `TourPanesSmokeTest.HOM_23_theHotspotsRowChangesTheGlanceHotspots` (app): replaced by `CFG_77_theHotspotsRowIsTheEditorAndReadsHowManyAreOn`.
- `HomeQuickSheetVisualTest` (app): `HOM_21_w51_everyHotspotsCardSetsItsCorners` deleted (cards gone; see the new CFG_77 test); the label/role test reads an Order card; the 48 dp test checks the Hotspots tile.
- `SettingsOneUiW40Test.CFG_21_aSearchResultOpensTheRealRowInItsTab` (app): searches "music line" (the "<corner> hotspot" rows are gone).
- `SettingsVisualW54Test.CFG_23_everySmallChoiceTheInventoryListsIsThere` (app): "Music line width" instead of "Hotspots" among the card choices.
- `GlanceFixedW38SmokeTest.GFX_38_theHomeSettingsListShowMusicAndNoGlanceContent` (app): Hotspots is no longer a Glance row; Music line width is.
- `PageEditorAppTest.HOM_12_pe50_...` (app): the Glance settings tab shows Show music (no Hotspots heading).
- `SettingsTabsW57ModelTest.CFG_40_theRowCountsPerTab` (app): Home has 80 rows (was 87: the Hotspots choice, four hotspot switches and four corner rows out; Music line width and the editor in).
- `SettingsVisualW54Test.choices` (app): the Hotspots editor row is skipped by the card-choice checks (a rail row of its own, like Looks and Try it; covered by `HotspotsEditorW59UiTest`).
- `HomeQuickSheetGlanceTest` (app): `HOM_23_theQuickSheetHasHotspotsChipsButNoGlanceContentChips` became `HOM_23_theQuickSheetHasAHotspotsTileButNoGlanceContentChipsAndNoPresets`; `GFX_38_theHotspotsChipsStayWithGlanceOff...` became `GFX_38_theHotspotsTileStaysWithGlanceOff...`.
- `web/liftoff-studio/tests/studio.test.js` line 330: a syntax error from the wave 58 merge (a comment inside the call) that kept the whole node suite from running; fixed.

## Screenshots read
- `feature-grid/build/screenshots/w59_line_{white,pattern}_{dark,light}.png`: both ends of the moving title fade into the wallpaper; no block at either end. The dark theme's light ink over plain white reads weakly (the ink, not the fade; left as it is, see the decision note).
- `feature-grid/build/screenshots/w59_chrome_fullwidth.png`: the line edge to edge under the Camera and Music corner glyphs, its ends fading under them.
- `feature-grid/build/screenshots/w59_editor_{portrait_all_on,portrait_top_right_off,quarter_style,rtl,landscape,separate_landscape}.png`: the miniature with the real glyphs; the off corner dimmed with a slashed ring; the pulse ring on the selected glyph; Quarter discs; Arabic labels with the corners still physical; the landscape miniature and the Portrait / Landscape control. Fixed after reading: the ring and the off mark were centred on the 56 dp touch square rather than on the glyph and were cut by the miniature's rounded edge (now centred on the glyph, kept inside).
- `app/build/screenshots/w59_settings_corners.png`: the editor as the first block of Home > Corners.

## Not done
- No device run (look over a live wallpaper, TalkBack, hardware keyboard, rotation, touch arbitration).
- The page editor's Settings tab has no link to the editor.
- The on/off set stays one set of physical corners for both orientations (separate corners for landscape separates the assignments only).
