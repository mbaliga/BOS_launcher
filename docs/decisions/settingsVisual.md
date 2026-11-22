# Settings as drawn cards (wave 54, CFG-23)

Owner: "Love what you've done with the sort and group, make sure to use such visual options across the board in all settings."

## Decisions
- One card: `OneUiVisualCard` (feature-grid), 80 dp wide, 64 dp picture, 12 sp label, accent ring and check when picked, radio (or checkbox) semantics, focus ring, FlowRow groups. The Home quick sheet `OptionCard` and the Files `ChoiceCard` now delegate to it (dark ink forced there), so the three sheets cannot drift. Font cards use the wide variant (148 dp).
- Rule: a Choice of 2 to 9 options is a row of cards (`OneUiModel.VISUAL_MAX`, `offersCards`); more is a list page. A test over the real builder fails when a Choice is left as a text page. The row keeps its title, hint and value text (search and summaries unchanged); it is still the `Rail` presentation, so the row rendering files were not touched.
- Pictures are drawn code, one `ArtSet` per setting over every enum value (`SettingsArtHome`, `SettingsArtLook`, `SettingsArtApp`, `DockEdgeArt`); the shared Order, Pages and Hotspots drawings moved out of the Home sheet and serve both. A new enum value breaks the exhaustive `when` and the coverage test.
- Web engines: one neutral magnifier and the name. No logos.
- Language stays a list (ten rows) but is a pane with a glyph in each language's own script; it no longer closes on pick.
- Dock style stays a page (its value lives in the workspace store, which the settings builder cannot reach) but the page is cards. Dock position became cards too.
- Deleted: the sideways `VisualChoice` rail and `PreviewCard`, the one-off preview composables (home style, page shape, paging, corner style, theme mode, glyph, search layout, themed sample icon, MiniPhone, Xmb/Dsi/Continuous previews), `LaunchpadThemePreview` with its test, the dock's row-style chooser and the `Segmented` presentation.
- Stayed as text/other: Comms gestures (long per-gesture action lists), gradient angle and From/To stop, signal force (Lab), the clock tile sheet and group layout segmented controls (item editing, not Settings).
- Also cards: Wallpaper type (solid, gradient, signal, system, picture), Radar intensity, Home panels usage range.
