# Wave 45: editChrome (HOM-07, HOM-12)

Decision record: `docs/decisions/editChrome.md`. Research: `docs/research/home-editing.md` (stream C). Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| HOM-12 | partial | The single-page edit mode's chrome: page at 0.88 with scrim and grid dots, 56 dp top bar (Cancel, title, Undo, Redo, the one Done), hint for the first three entries, 72 dp tray, dimmed inert hotspots and music line, page dots as the switcher, dock at 70%, left tray and edge swipes off, Cancel with snapshot restore and a conditional confirm, Back and Esc are Cancel, announcements. Built and tested headless. Device-only: transition feel, real insets, TalkBack, the dock as a drop target, interplay with the handles. |
| HOM-07 | partial | Only the surrounding chrome changed: widgets stay live on the 0.88 page. Handles and resize belong to the handles stream. |

## Built
- `core-data/EditChromeRules.kt`: `EditChromeHint` (first 3 entries), `EditChromeStore` (pref file `edit_chrome`, device-local, classified in `StateSchema.BACKUP_EXCLUDED_PREF_FILES`, both backup XML files and `W13CrossAuditTest`).
- `core-catalog/LayoutDockEditSession.kt`: entry snapshot, `hasChanges`, `cancel()`, `redoDepth`; `app/HomeBehaviour.kt`: `HomeEditState.cancel/hasChanges/redoDepth`.
- `feature-grid`: `EditChromeGeometry` (pure), `EditGridState` + `LocalEditGrid` + `EditGridDots` (the seam for the handles), `EditGridDotsLayer` (one Canvas; one hook line in `GridHome.PageView`), `HomeEditChrome` (bar, hint, tray, Cancel dialog, shield, backdrop, progress), `HomeEditBar` without the banner (kept for the page editor).
- `app/HomeScreen.kt`: wiring (page editor as a sub-mode `pageEditorOn`; layer frame blend; backdrop; dimmed fixed chrome, dock and dots; hidden left tray and handles; edge swipes off; Esc and Back; Apps chip picker).
- `core-design/L10nW45editChrome.kt` (es fr de pt hi zh ja ar).

## Removed
The toast-like "Editing home: drag items to rearrange, long-press for more" banner, and the floating Undo, Redo, Add page, Done pills outside the page editor (`HomeEditBar` is now only the page editor's bar). The stray drag circle was not part of this mode (nothing found in the single-page path to remove).

## Tests
- JVM: `EditChromeHintTest` (core-data, 5), `EditCancelW45Test` (core-catalog, 8: snapshot restore, past the 20 limit, undone is no change, rail, other workspace, rebase, redo depth), `EditChromeGeometryTest` (feature-grid, 15: scale, bar and tray insets, landscape, reduced motion, blend, dots, distance).
- Robolectric `HomeEditChromeUiTest` (feature-grid, 27): bar nodes and 48 dp sizes, single Done, heading and traversal, undo and redo counts and dimming, Done says Saved, entry announcement, Enter on Done, title opens the page editor, hint (under the bar, fades at 4 s, goes on the first touch, absent when off), tray chips and the "+ Page" rule, Cancel rule with and without changes, Back and Esc, hotspot shield swallows the corner while the page stays live, 0.88 end state, entry and exit animation, reduced motion, RTL, landscape, three screenshots.
- Robolectric in the real app `EditChromeW45AppTest` (10): bar, one Done, tray, hidden Recents handle and its return after Done, hint for 3 entries and the stored counter, Cancel with no change, Cancel after a removal (dialog, Keep editing, Discard restores, Done keeps), Back, page dot switching, "+ Page", Apps picker, title opens the page editor. (The Folders chip is wired to the existing New folder dialog but has no app-level test: that dialog's text field never lets Compose go idle under Robolectric.)
- Audit `EditChromeW45AuditTest` (core-quality): handle blocks and all three edge swipes gated on `!homeEdit.active`, dim constants, no Toast, no infinite animation, pref file classification.
- Old tests changed (the owner replaced the behaviour): `TourBase.editShown` (now also the new bar), new `TourBase.openPageEditorFromEdit`; `PageEditorAppTest.openEditor`, `PagesExplicitW30FlowTest`, `HomeUnifyFlowTest`, `HomeUnifyReview2FlowTest` open the page editor from the new bar's title because Edit home no longer opens it directly; `HomeLandingItemsW24Test` taps the edit bar's Undo button (`edit.undo`) instead of the old "Undo (n)" pill text. `W13CrossAuditTest` lists `edit_chrome`.

## Screenshots read (feature-grid/build/screenshots)
`home_edit_chrome_portrait_hint.png`: dark, stand-in page (gradient, nine coloured squares, grid dots) at about 0.88 with a rounded outline; top bar with Cancel, title, an enabled Undo, a dimmed Redo and a violet Done pill; hint line over the page's top; four chips at the bottom edge. The dots are very faint, as specified. The tray overlaps the page's lower edge because the harness has no dock. `home_edit_chrome_landscape_hint.png`: page shrunk to clear bar and tray; the stand-in page is a portrait grid stretched to landscape, so its icons are cut off (a harness artefact, not the real home). Real home screenshots were not taken.

## Not matched / not done
See "Not matched" in the decision record. Also: the research's "page rect hairline" is drawn; the "top bar slides in 150 ms" is a fade with the entry progress, not a slide; Undo and Redo have no tooltip or long-press history badge; Glance and Comms dots are inert rather than hidden.
