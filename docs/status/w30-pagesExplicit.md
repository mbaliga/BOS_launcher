# Wave 30 pagesExplicit: pages and icons are only what the user put there

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the rendered screenshots (`app/build/screenshots/w30-*.png`: Glance with house + 1 dot and house + 2 dots, the empty page with its hint, edit mode with page 1 holding one app and page 2 empty, page 1 after deleting page 2). The dialogs (delete confirmation, reset notice) are separate windows and are not in those captures: their text and buttons are asserted through the semantics tree.

Cause of the owner's 14 full pages (reproduced before the change, see decisions/pagesExplicit.md): `visualPages` appended every installed app to the last page for installs on the old "All apps" default, the first edit stored that arrangement, and delete moved a page's items to the previous page.

| ID | Status | Note |
|---|---|---|
| HOM-01 | built (device untested) | One empty page at a time: Add page adds exactly one empty page (`PagesExplicitW30Test.PGX_addPageCreatesExactlyOneEmptyPageAfterTheCurrentOne`; `PagesExplicitW30FlowTest.PGX_pagesAreAdded...`: Pages Off, turn the first page on, Add page, house + 2 dots). No app is ever placed for the user: new installs live in the drawer (`PGX_aPlacedItemStaysOnItsPageAndANewInstallIsNotPlaced`, the install in the flow test adds no page and no icon). The first-run seed of Settings, Phone and Messages is gone. |
| HOM-12 | built (device untested) | Delete page really removes the page and its items (nothing joins a neighbour, nothing refills), asks first when the page holds something ("N items will be removed from this page. They stay in the drawer."), the last page is emptied (Pages stays on), Undo brings the page back (`EditCardAppTest.HOM_12_deletePageThenUndo...`, `PGX_deletingAPageThatHoldsItemsAsks...`). A page emptied by moving its last item stays. A full page says "Page full" instead of spilling or opening a page (`PGX_fullCurrentPageRefusesTheItem...`, `PGX_aFullPageRefusesTheItemInsteadOfOpeningAPage`). |
| HOM-22 | built (device untested) | The page indicator shows the real page count (house + one dot per page; the trailing right-edge control is not a page dot). Migration of an auto-filled legacy home: `PagesExplicitW30Test.PGX_*` (14 auto-filled pages become one empty page with a backup; a hand-built 2 page home is kept; a recorded edit is never reset; a fresh install and an empty catalog decide nothing; the 4 consecutive full pages rule; restore within 30 days and not after) and the UI flow `PGX_anAutoFilledLegacyHomeIsReset...` (notice, Restore previous pages from Ground Control, backup file used up). |

## Tests that encoded auto-fill or spill (changed or replaced)
- `HomeModelTest`: "unseeded layout flows all apps across pages" and "visual pages ... append new apps" now assert that nothing is appended (only `strays`); "move into a full page cascades" became "a drop on a full page is refused"; "overflow page inherits the spilling page's layout" now goes through a density change (`setLayout`); "moving past the end ... only the emptied source page is dropped" now keeps the emptied page; "add prefers the given page and falls back to the last" now never makes a page; "page insert, delete" asserts delete removes the items.
- `EditCardPagesTest`, `EditCardAuditW13Test`: delete no longer merges into the previous page (replaced by `PGX_deleteRemovesThePageAndItsItems...`; the overflowed-page tests now expect the whole stored page and its keys in `taken`); `HOM_04_fullPageSpillsToTheNextWithRoomThenGrowsAPage` replaced by the page-full test; `place` returns null on a full page.
- `HomeInfoW3Test`, `HomeRestW5Test`, `HomeWiringTest`, `HomeBehaviourTest`: the cross size-class tests use `HomeResolve.visual` (the user's own folders and widgets are carried, apps are not); one expectation no longer includes an app that is on no page.
- `HomeEditModeTest`, `InputTouchPathsUiTest`, `AppSmokeTest`: fixtures place their apps explicitly instead of relying on auto-fill.
- `EditCardAppTest.HOM_01_aReturningUserWithoutALayoutStillSeesTheirAppsFlow` replaced by `PGX_aReturningUserWithoutALayoutGetsNoAppsFlowedOntoThePages`; the delete tests answer the new confirmation; `TourFreshSmokeTest.HOM_12_tourEditMode...` answers it too.

## Device only / not done
- Touch feel, real widget picking (the flow test stands a tile in for the widget), the dialogs' look, the real upgrade path from the owner's 14 page install (the heuristics are fixture tested, the owner's actual pref and layout files were not available).
- `PackageRemovedW26SmokeTest` is the known order-dependent flake.

## Adversarial review findings (second pass)
Each has a test that failed before the fix (core-catalog `EditGraveyardTest.PGX_review_*`, core-data `PagesExplicitW30Test.PGX_review_*`).

| # | Finding | Fix | Test |
|---|---|---|---|
| R1 | The auto-fill heuristic judged "full" by TODAY's capacity: a layout written at a smaller density (or judged in landscape) was never "full", so the owner's 14 pages could be missed and kept for good (the version marker is set on Keep). | A page counts as full when it holds as many as the fullest stored page (when that is at most today's capacity). | `PGX_review_aDensityChangeSinceTheAutoFillDoesNotHideTheLegacyLayout` |
| R2 | The inverse: a tiny hand-built home in catalog order (7 apps over two pages) matched "apps only, in automatic order, full pages" and was reset. | Rule 1 needs at least 12 apps and pages of at least 4. | `PGX_review_aTinyHandBuiltHomeInCatalogOrderIsNotMistakenForTheAutoFill` |
| R3 | `LegacyHomeBackup.save` swallowed every error, so with a full disk the migration wiped the layout with no backup (data loss). | `save` returns whether the file reads back; on failure the layout stays and the migration stays pending. | `PGX_review_aBackupThatCannotBeWrittenNeverWipesTheLayout` |
| R4 | The `resetNoticePending` flag lives in the backed-up layout prefs but the backup file is excluded from backup and transfer (and expires after 30 days), so a restored phone showed "Restore previous pages" with nothing to restore. | `noticeStillValid` drops the notice when the backup is gone; HomeScreen uses it. | `PGX_review_theResetNoticeIsDroppedWhenItsBackupIsGone` |
| R5 | Restore (notice or Settings row) silently replaced pages the user had built since the reset. | `restoreReplacesEdits` plus a confirmation ("Your current pages will be replaced by the previous ones."), new string in all 8 languages. | `PGX_review_restoreAsksBeforeReplacingPagesTheUserHasBuiltSinceTheReset` |
| R6 | Dropping an app on the home (or placing it from Glance) with every page full wrote nothing visible, cleared its "removed" mark and gave no feedback. | `HomeLayoutStore.addToHome` returns false and writes nothing; HomeScreen shows "Page full". | `PGX_review_aFullHomeRefusesAnAppDroppedOnItAndWritesNothing` |
| R7 | An item moved from the dock or taken out of a folder onto a full page (and a folder created from items on no page) was removed from its dock slot or folder, then could not be placed: it vanished (a widget or link was re-added as an extra overflow page by the strays rule). | `HomeEdit.move`, `takeOut` and `createFolder` refuse when the target page has no room; nothing changes. | `PGX_review_aDockItemDroppedOnAFullPage...`, `..._aFolderItemTakenOut...`, `..._aFolderMadeFromItemsOff...` |

Found, not fixed (device or larger scope):
- The migration judges the ACTIVE window size class only; another class's stored copy that is auto-filled stays if the active class is kept (a reset clears every class).
- The first non-empty app list decides the migration once; a partial list (work profile not loaded yet) could make a legacy layout look hand-built (legacy "Everything" mode is then switched off anyway, so nothing re-fills).
- The Settings Delete page row moves the settings page selection before the confirmation answer.
- Removing a widget from a stack onto a full page is still unguarded (WidgetStacks.removeMember).
