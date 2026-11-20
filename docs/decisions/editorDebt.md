# Decision: page editor debt (wave 37)

**Status: built (JVM and Robolectric tested; nothing is device verified).** Closes the gaps wave 36 listed in `pageEditor.md`. Affects HOM-12, HOM-01 in `docs/Spec.md`.

## What changed
1. **Continuous scroll-to-page.** Tapping a card in the Continuous editor selects the page (as before) and now also scrolls the scroll surface (`ColumnScrollHome`) so that page's first column is at the start edge (the left edge; the right edge with the right-to-left order). The first column of a page is the column of its first item on the packed surface (`PageEditorGeometry.pageStartColumns`); an empty page starts where the next page with items does, trailing empty pages at the last column; near the end the scroll clamps. The scroll is an instant `scrollTo` under the 350 ms swing. The other way round: while the user scrolls the surface (a drag or fling, not our own jump) the page whose column is at the start edge becomes the selected page (`pageInView`), so the strip, the rail and Add / drops follow. The selected page is kept when the surface is where it would put it (clamped pages at the end, pages sharing a column), so a tap never flips back. The selection (`contSel`) and the scroll request live in `EditCardState`, so the home passes them to `ColumnScrollHome`.
2. **The edit bar's Add page** (and the new menu's) goes through one path with the strip's "+" card: `HomePageOps.add` after the LAST page, focus the new page, swing from where the card is, in Paginated and in Continuous (the Continuous case no longer uses the pager index). The overlay registers it as `EditCardState.addPage()`. The old behaviour (after the current page) is gone, on the owner's rule that the page editor has one add path.
3. **Hamburger.** A 48 dp button at the top of the left column (above the tabs; in Continuous above the tabs row, under the strip) opens a menu with Done, Undo, Redo (disabled when there is nothing to do) and Add page. TalkBack: the button is "Page editor menu" (role Button), the items carry their own labels. The edit bar at the bottom is unchanged.
4. **Spacing.** The gap between strip cards is `PageEditorGeometry.GAP_DP = 6` (was 12); the strip keeps showing the neighbours.
5. **TalkBack duplicates.** While the editor is open the home's page indicator is cleared from the accessibility tree (`PageIndicator(hiddenFromAccessibility = true)`, `clearAndSetSemantics`); it stays drawn and tappable, so only the strip cards announce "Page N of M". It is announced again after Done.
6. **Per-page wallpaper: not done, on purpose.** The model has one wallpaper setting for all pages (`pagesSameAsGlance`); a per-page wallpaper needs a model, a store and a migration and is a separate owner decision. The Settings tab keeps the one switch.

## Where the code is
`PageEditorGeometry.kt` (the three pure functions and `GAP_DP`), `ColumnScrollHome.kt` (`PageScrollRequest`, the two effects), `PageEditorTabs.kt` (`PageEditorMenu`, `PageEditorMenuButton`), `PageEditorHost.kt` (the menu in the tabs column), `GridHome.kt` (`PageIndicator`), `EditCardWiring.kt`, two spots in `HomeScreen.kt`. Strings: `L10nW37editorDebt.kt` ("Page editor menu").

## Device only
The feel of the instant scroll under the swing, the drag-follow while a fling runs, TalkBack reading order and the menu popup placement, and the hamburger in landscape.
