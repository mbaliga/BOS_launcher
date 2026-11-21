# Wave 36 pageEditor: pages on the side, tabs with content, the swing

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the rendered screenshots (`feature-grid/build/screenshots/page_editor_*.png`: paginated portrait dark and light, continuous portrait dark and light, paginated and continuous landscape dark, paginated landscape light, RTL, font scale 2, the Glance focused, a mid-swing frame). Design in `docs/decisions/pageEditor.md`.

| ID | Status | Note |
|---|---|---|
| HOM-12 | built (device untested) | The edit card now shows a strip of page cards (side when paginated, top when continuous, side in landscape) with Glance first and an Add page card last, the live page shrunken in the focus slot, and tabs with their content on the other side. Tap on a card swings that page into the slot (translation, tilt, scale, about 350 ms; cross-fade with reduced motion). Pure maths `PageEditorGeometryTest`; UI `PageEditorUiTest` (both modes, portrait, landscape, RTL, order RTL, light, font scale 2, reduced motion, roles, 48 dp tabs); frame with the swing `EditCardStateW36Test`; real app `PageEditorAppTest` (strip and tabs, tap makes the page the one edited, Add page card, rail delete with confirm and Undo, Continuous, right-to-left order, reduced motion, Glance card and its settings). |
| HOM-01 | built (device untested) | The Add page card appends one empty page and focuses it (`HOM_12_pe45`); a full page still says "Page full" (drop and tap go through the same `AddCarouselModel.place`). |
| HOM-04 | built (device untested) | The add carousel's Apps, Widgets, Folders and Shortcuts tabs are now the tabs Apps, Widgets, Folders (with the shortcut, link, file and group entries) and Settings. Drag from the Apps grid onto any visible card places the app on that page (`HOM_12_pe26`, through the `AddCarouselDrag.onDrop` seam; the long-press gesture itself is device only). |
| HOM-23 | built (device untested) | With Glance on it is the first card, not deletable, nothing can be dropped on it; its Settings tab holds its content items and hotspots (`HOM_12_pe24b`, `HOM_12_pe50`). |

## Existing tests changed
- `TourFreshSmokeTest.HOM_12_tourEditMode...` (same reason: it selects page 2's card before the rail's Delete page), `EditCardAppTest.HOM_12_deletePageThenUndo...` and `...theDeleteNoticeNeverUndoes...`: the Delete page button now lives on the rail of the page being edited, so the test selects page 2 first (tap its card, tagged `pe.page.1`) and no longer scrolls to it. `EditCardHost`, `PageCarousel` and `AddCarousel` are unchanged and keep their own tests (`EditCardUiTest`), but the app no longer shows them.

## Not matched to the mock-up
No hamburger glyph; a 12 dp gap between cards; the card content is the real icon grid. The Continuous editor does not scroll the scroll surface to the tapped page (it keeps its own selection), see the decision doc.

## Device only
Swing feel and tilt, the long-press drag from the app grid onto a card (including while the strip is scrolled), gesture conflicts between the strip's scroll and the live page's own drag, TalkBack reading order (the home's own page dots carry the same "Page N of M" label as the cards), real widget picking.

## Test runs
Run: `:feature-grid` (all), `:core-design` (all, TranslationCoverage), `:core-quality` (all), `:app` (all, 360 tests; the only other failure was the known flaky `PackageRemovedW26SmokeTest`, green on rerun). Not run: the other modules, `assembleRelease` in this note (see the final report).
