# Wave 37 editorDebt: page editor debt

Nothing was run on a device. Design in `docs/decisions/editorDebt.md`.

| ID | Status | Note |
|---|---|---|
| HOM-12 item 1 | built (device untested) | Continuous: tapping a card scrolls the surface to the page's first column; user scrolling moves the strip selection. `PageEditorGeometryTest` ed02..ed04 (pure), `PageEditorAppTest` ed10, ed11 (real app: tap scrolls, scrolling by `ScrollBy` selects page 3 and back). |
| HOM-12 / HOM-01 item 2 | built (device untested) | The edit bar's Add page uses the strip's path (after the last page, focus the new one) in Paginated (ed12) and Continuous (ed13, selection is the editor's own, not the pager index). |
| HOM-12 item 3 | built (device untested) | 48 dp hamburger above the tabs with Done, Undo, Redo, Add page and TalkBack labels: `PageEditorUiTest` ed06, ed07; reaches the real actions in `PageEditorAppTest` ed14. New string "Page editor menu" in eight languages (`L10nW37editorDebt.kt`). |
| HOM-12 item 4 | built (device untested) | `GAP_DP` 12 to 6: `PageEditorGeometryTest` ed01, `PageEditorUiTest` ed05 (cards 6 dp apart, neighbours still peek). Existing geometry tests now take the constant. |
| HOM-12 item 5 | built (device untested) | The page dots are cleared from the accessibility tree while editing: `PageEditorAppTest` ed15 (one "Page 2 of 3" while editing, two again after Done; verified to fail without the change). |
| item 6 | not done, on purpose | Per-page wallpaper needs a model; the Settings tab keeps the same-as-Glance switch. |

## Existing tests changed
`PageEditorGeometryTest` helpers pass `PageEditorGeometry.GAP_DP` instead of a literal 12 dp. No test was deleted.

## Screenshots read
`feature-grid/build/screenshots/page_editor_paginated_portrait_dark.png`: the hamburger sits top left above the large "Apps" title, the tabs and app grid below, the rail beside the card, and the strip cards are close (6 dp) with the neighbours peeking above and below. `page_editor_continuous_portrait_dark.png`: the cards in a row across the top, the hamburger under the rail above the tabs row. In both the stand-in "live page" in the test host does not fill the focus slot (a test artefact of the host, not of the app). Not seen: the open menu popup (a separate window the screenshot does not capture) and any frame on a device.

## Device only
Feel of the scroll under the swing, drag-follow during a fling, TalkBack order and the menu popup, landscape.
