# Wave 45: pageEditorLayout (HOM-12, HOM-01)

Decision record: `docs/decisions/pageEditorLayout.md`. Research: `docs/research/home-editing.md` (stream D). Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| HOM-12 | partial | Page editor redone: portrait is a 56 dp top-bar slot, a horizontal strip of 64 x 112 dp page cards, the live card (never cropped, centred), the tab row and a fixed-height scrolling content panel; landscape and 600 dp wide windows keep a vertical strip at the end side; reorder by long press drag (ghost, insertion line, haptics), Shift+arrows, overflow menu and TalkBack actions; Glance first, New page last; floating rail, hamburger, Settings Delete page and cropping removed; Settings names the page and labels the wallpaper switch "All pages". Device-only: swing feel, drag and haptics, real keyboard focus, TalkBack, real insets. |
| HOM-01 | partial | Pages added from the New page card (one path, plus the edit bar until the chrome replaces it). |

## Built
- `PageEditorGeometry.kt`: new pure `layout(...)` (portrait stack, side mode for landscape and >= 600 dp, tab row scaling with font, content panel share and floor, live scale <= 0.88 and window-bound), `dropIndex`, `insertionGap`, `stepTarget`, `stepOf`, `offsetOf` from the live card's centre, swing with a start scale and one tilt axis.
- `PageEditorStrip.kt` (new): the strip, Glance / page / New page cards, overflow menu, long-press drag with ghost and insertion line, key navigation and Shift reorder, custom actions, live-region announcement.
- `PageEditorHost.kt`: `topBar` slot (integration point for w45-editChrome), tabs and content column, rail removed, layout from density, font scale and insets.
- `PageEditorTabs.kt`: text tabs with underline, page identity and "All pages" in Settings, no Delete page, hamburger removed.
- `EditCardWiring.kt`: `onReorder` via `HomePageOps.moveTo`, swing start scale, no menu.
- `L10nW45pageEditorLayout.kt` (es fr de pt hi zh ja ar), registered in `L10n.kt`.

## Tests
- JVM `PageEditorGeometryTest` (feature-grid): portrait order, live card inside the window for seven phone sizes x font 1/1.3/2 x bar 68/0, content panel share and floor, strip card size and pitch, mirrored side strip, landscape and tablet sizes, target transform, swing axis and start scale, drop hit test, `dropIndex`, `insertionGap`, `stepTarget`, `stepOf`, continuous scroll maths (unchanged).
- Robolectric `PageEditorUiTest` (feature-grid, 35): strip above and tabs below, live card whole on 320x568, 360x640, 411x891, font 1.3 and 2, tablet 800x1280, landscape; New page card with label; overflow menu entries and disabled ends; no rail, hamburger, Settings delete or "Add page" description; drag reorder with ghost and insertion line; a short drag does nothing; Shift+arrows, arrow focus and Enter; custom actions; Glance card (no actions); Glance Settings; drop seam; Continuous; RTL and the right-to-left order; landscape; light theme; reduced motion; a11y roles and descriptions; eight pages scrolled; top-bar slot. The harness sizes the layout to the Robolectric window (it is smaller than the configuration's screen because of the system bars).
- Robolectric in the real app `PageEditorAppTest` (19): selection, New page, delete through the overflow menu with confirm and Undo, Continuous row and scroll-to-page, RTL order, reduced motion, Glance, the edit bar's Add page, Move later through the menu, drag reorder in the store, removed items absent and the page identity. `EditCardAppTest`, `TourFreshSmokeTest`, `EditCardStateW36Test` updated for the new geometry.
- Old tests changed because the owner replaced the behaviour: `HOM_12_pe23` (rail) to the overflow menu, `ed05` (6 dp gap) removed (the gap is 8 dp and tested in geometry), `ed06` and `ed07` (hamburger) removed, `ed14` rewritten (hamburger gone), `pe01` to `pe05` geometry (side strip portrait) rewritten, `ed01` (gap) replaced by reorder maths.
- Run: `:feature-grid` PageEditorGeometryTest and PageEditorUiTest, `:app` PageEditorAppTest, EditCardAppTest, EditCardStateW36Test, TourFreshSmokeTest, `:core-design` (25), `:core-quality` (201). Full `:feature-grid` run (1350 tests): 6 failures were counted in the results of that run, all in PageEditorUiTest (the machine was shared; the file was overwritten before I read which ones); the class was rerun alone after the last change (a font-scale cap on the strip cards) and passes 35 of 35. Treat PageEditorUiTest as possibly timing sensitive under load until a second full run is done. Not run: `:app` full suite, `assembleRelease`, other modules (no other module changed).

## Screenshots read (feature-grid/build/screenshots/page_editor_*.png)
- `paginated_portrait_dark`: top: the test's TOPBAR text in the 56 dp slot; below, the strip: Glance (house on a navy card), pages 1 to 4 with small icon previews and a three-dot at the foot, page 2 larger with a 2 dp violet outline and centred, the dashed New page card clipped at the right edge; then the live card (a stand-in blue rounded box) centred, fully inside with clear margins; then the tabs (Apps bold white, the others dim; the underline is faint) and the app grid. The live card is about a third of the width.
- `strip_scrolled_8_pages`: page 7 centred and outlined, pages 5, 6 and 8 around it, a part of page 4 on the left, the New page card with plus and label on the right.
- `paginated_landscape_dark`: tabs and apps left, the live card (wide) in the middle, the vertical strip at the far right with page 2 outlined; page 1 and 3 clipped at the strip's ends. The label "Empty page" wraps in the narrow card.
- `reorder_drag`: page 2 dimmed at its place, a translucent ghost over pages 3 and 4, a thin violet insertion line after page 3.
- `settings_tab`: tabs with Settings bold, heading "Page 1", "Page 1 of 4", an "All pages" caption and the wallpaper switch with the hint "Applies to every page and the scroll, not only this one". `continuous_portrait_dark`: the same layout, page 3 (empty) focused. `font_scale_2` (before the strip-label cap): card numbers and "Empty page" overflow their small cards, the content panel clips the switch row (it scrolls), the Settings tab runs off the right edge (the row scrolls); the cap was added afterwards and that screenshot was not re-read.
- Not read: light variants, RTL, overflow menu, glance focus.

## Not matched / not done
See "Not matched / honest limits" in the decision record: the small live card, the 48 x 32 dp overflow target, no auto-scroll on drag, double "selected" announcement, bottom bar room still reserved until the chrome lands, and the top bar slot is empty until it does.
