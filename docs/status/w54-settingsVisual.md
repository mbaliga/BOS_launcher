# Wave 54: settingsVisual (CFG-23)

Nothing was run on a device. Verified by JVM unit tests and Robolectric UI tests, and by reading screenshots (`app/build/screenshots/settings-w54-*.png`: Home screen dark, light and at font scale 2, Look, Corners, Radar, App drawer, Side panel, Language).

| ID | Status | Note |
| --- | --- | --- |
| CFG-23 | built (device untested) | Every settings pick of two to nine options is a row of drawn cards (`OneUiVisualChoice`, one shared `OneUiVisualCard`). |

## What changed per page
- Home screen: Order, Right side, Clock style, Clock format, Clock colour, Clock size, Hotspots, Style, Pages, Scroll direction, Default density (kept the live preview and steppers under the cards), Page turning x2 are cards. Dock style is still a page, but the page is three cards (the dock's own drawing). Page N: Layout.
- Look: Light/dark, Font (wide cards), Icon shape, Icon colour, Settings backdrop, Calm effect, Alert effect, Show.
- Corners: Corner style, Icon type. Radar: Layout, Background, Web engine (neutral magnifier and the name, no logos). App drawer: Wheel groups, Ring segments (6/7/8), Pages. Side panel: Left page, Comms theme, Default view (up to nine cards), Widgets position.
- Desktop and dock: Dock position (Bottom, Left, Right). Wallpaper pane: Wallpaper type and Radar intensity. Home panels: usage range.
- System: Language is a pane (ten rows, still a list) with a glyph in each language's own script, radio semantics and a check; it no longer closes on pick.
- The Home quick sheet cards and the Files sort/group cards now use the same card shell. Order, Pages and Hotspots drawings moved out of the sheet and serve both places.

## Stayed text, and why
Switches, sliders and steppers (not choices). Language (ten options: list with glyphs). Comms gestures action chips (long per-gesture lists), gradient angle and From/To stop, Lab signal force, the clock tile sheet and app group layout segmented controls (item editing, not Settings). The 28-action corner/gesture pickers already have a glyph per row.

## Tests
- New `SettingsArtW54Test` (every enum value and Ring segment has a drawing, draws pixels in dark and light ink, pictures differ per value; Web engine excepted), `OneUiVisualChoiceW54Test` (select, selected state, radio role, label is text and description, 48 dp, wrapping at 200 dp and at font scale 2, focusable and clickable), `SettingsVisualW54Test` (over four home styles: no Choice is a text page; every card group composed, labels, 48 dp, one picked, a tap sets the setting for all pref-backed choices; layout and ring recorders; Language pane), `SettingsVisualW54ShotsTest`.
- Changed: `CommsOptionsW29Test` (stepped slab test replaced), `WorkspacePagesW41Test` (position cards), SettingsRail/PagesW46/Screenshot/ThemeScreenshot tests (new component; the Xmb/Dsi/Continuous preview shot removed), `docs/Spec.md`, `SPEC_STATUS.md` (one CFG-23 row and Counts), `TEST_COVERAGE.md`.
- Deleted code: VisualChoice and PreviewCard rail, one-off previews (home style, page shape, paging, corner, theme mode, glyph, search layout, sample icon, MiniPhone, ThemePreviews.kt), LaunchpadThemePreview and its test, the dock row chooser, the Segmented presentation.
- Strings: `L10nW54settingsVisual.kt` (one string, 8 languages).
- Results: feature-grid (1615), feature-dock (222), core-design, core-quality (220) green; app: new and affected tests green; `DockDialogsW24Test` failed once under machine load and passed twice on rerun, and its failure polluted `DockWiringTest` in the same run (it passes alone). Full `assembleRelease` was not run.

## Device-only checks
TalkBack reading of the cards and Language rows, real key and controller Enter on a focused card (the Robolectric window cannot take focus), look of the drawings on real screens and the light theme, the search for a settings row landing on a card row, dock style and position cards on a device, language switch from the new pane.
