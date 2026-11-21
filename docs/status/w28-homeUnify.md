# Wave 28, stream homeUnify: status

Branch w28-homeUnify (base c869e34). Corrects wave 26: the home is ONE pager with ONE page indicator and a three-value Pages setting. Decisions: docs/decisions/homeUnify.md (supersedes the deck part of homePages.md). Nothing here is device-confirmed.

| ID | Status | Note |
|---|---|---|
| HOM-01 | done (JVM and Robolectric) | Pages setting `HomePages` Off / Paginated / Continuous (Ground Control, Home screen row "Pages"; three chips `quick.pages.*` in the long-press quick sheet). Off: Glance only (Glance off: one editable first page). Paginated: grid pages are the stops after Glance. Continuous: `ColumnScrollHome` is the stop after Glance. "Add page" asks nothing, turns Paginated on; not offered in Continuous. Items are added only by long press, Edit home, add carousel (`HomeUnifyFlowTest`: Pages Off, quick sheet, Paginated, Edit home, add an app, add the Files tile through "Widgets, folders and icons", swipe back and forward, Pages Off keeps the items stored). |
| HOM-22 | done | `pagesModeChosen` and `PagesModeDialog` removed; `homeScroll`/`pagesModeChosen` are only migration input (`HomeModelMigration.pagesMode`, `LauncherPrefsStore.migratePages`, JVM-tested). "Edit home" with Pages Off turns Paginated on first with a one-line toast. |
| HOM-23 | done | One horizontal `HorizontalPager` (tag `home.pager`) in `HomeScreen`; Glance is stop 0; `landing` is derived. ONE `PageIndicator` drawn once above the pager (`page.indicator`): Comms glyph, Glance house stop, page dots, right-hand dots; asserted as exactly one node with unchanged bottom/height/centre between Glance and page 1, in Paginated and Continuous. `GlanceSurface` swipes use the paged surface so a sideways swipe is the pager's. Order Right to left mirrors pager, row and swipes (`HomeUnifyFlowTest`, `HomeUnifyScreenshotTest`). Home button: stop 0. TalkBack: "Page 2 of 4" counts Glance as stop 1. Reduced motion: indicator fade 0 ms, pager jumps. |
| STR-01, STR-14 | unchanged | Edge drags for Comms and Recents still win in their edge zone. |
| CFG-19 | done | `L10nW28homeUnify.kt` (8 languages). |

## Verified
- Screenshots READ (`feature-grid/build/screenshots/w28-*.png`, from `HomeUnifyScreenshotTest` composing the same pager, Glance, grid, scroll and indicator the app uses; the whole-app capture does not settle under Robolectric): Glance with the indicator, page 1 with the indicator (same row, same place), Continuous with 30 icons portrait (7 rows x 4 columns visible) and landscape, Continuous right to left (starts at the right, indicator mirrored), page 1 right to left, `w28-glance-indicator-ltr/rtl-order`.
- `./gradlew testDebugUnitTest assembleRelease --offline --continue -q --max-workers=2` on the final tree: every module green, `assembleRelease` built; the only red test was `PackageRemovedW26SmokeTest.UNI_05_...`, which is flaky on the BASE too (1 failure in 4 runs of it together with `EditCardAppTest` on c869e34; passes alone) and unrelated to the home.
- Found by the tests while building: a lazy pager page with several root nodes is not placed (the pages stop is now one Box), and a swipe back from the first grid page / start of the scroll does not hand over by nested scroll (see the decision doc).

## Device only
Touch feel of the swipe back to Glance (it runs on release through `deckSwipe`, not as a live drag) and of the nested grid pager inside the outer one, real AppWidget bind through the widget picker (the flow test places the Files panel tile), real insets under the indicator.

## Old tests updated or deleted
| Test | Action | Reason |
|---|---|---|
| app `HomePagesFlowTest` | deleted, replaced by `HomeUnifyFlowTest` | encoded the separate Pages surface, the deck dots and the Scroll or Paginated dialog |
| feature-grid `HomePagesW26ScreenshotTest` | deck-dot tests replaced by tests of the one indicator on Glance; Pages screenshots host the one `PageIndicator` | `DeckDots`, `pageStops` and GridHome's `onGlance` are gone |
| core-data `HomeLandingTest` | the Scroll/Paginated choice test replaced by three-value / migration tests; export test uses `homePages` | `HomeScroll`, `pagesModeChosen` gone |
| core-data `HomeOrderTest` | deck forward/backward test and the old stops test replaced | `HomeDeck` is now outer-pager math |
| core-data `HomeModelTest`, `StateStoreTest` | `homeScroll` to `homePages` | field renamed |
| app `HomeQuickSheetGlanceTest` | Scroll/Paginated chip test replaced by the three Pages chips; "Edit home pages" is "Edit home" | wave 28 quick sheet |
| app `HomeLandingItemsW24Test` | quick sheet button text "Edit home" | label |
| app `AppSmokeTest`, `GlanceEdgesSmokeTest`, `TourBase` | Glance detection by tag `glance.surface` (in the window) instead of `glance.deckdots` | one pager may keep a stop composed off screen |
| app `TourPanesSmokeTest`, `WallpaperRowsW26Test` | `HomePages` instead of `HomeScroll` | renamed |
| core-quality `W4MotionAuditTest` | animate*AsState allow-list: GridHome 4, GlanceHome 1 | the indicator fade moved to `PageIndicator` (0 ms when reduced) |
| core-quality `HomeLandingW12AuditTest` | key `homePages` instead of `pagesModeChosen` | pref key |
| feature-launchpad `W13LabelsTest` | `HomePages` labels | renamed |
| new | `HomeUnifyFlowTest`, `HomeUnifyRowsTest`, `HomeUnifyScreenshotTest`, HomeDeck and migration tests in core-data | |
| docs/TEST_COVERAGE.md | regenerated with tools/spec_coverage.py | RepoHygieneAuditTest was already red on the base (three removed PAL_20 tests still listed) |

## Navigation and indicator review (first adversarial pass)
Found and fixed, each with a test that failed first (`HomeUnifyReviewFlowTest`, `HomeOrderTest`, `BottomStackTest`):
| Issue | Fix |
|---|---|
| On Glance with the Glance indicator preference off and Pages on, the one indicator row was drawn but the dock reserved nothing for it (rail on the indicator) | `HomeDeck.indicatorShown` is the single answer for the row and the bottom stack |
| The page-dot clearance (46 dp) was smaller than the real one-row height (52 dp with the Comms glyph), so the dock gap fell under 8 dp on pages | `PAGE_DOTS_TOP_DP` 52 and always cleared, also with a bottom clock |
| Page dots, house dot and Home page glyph were 14 to 32 dp targets | every stop is a 48 dp high square, 48 dp wide (shrinks to 24 dp only when many pages share the row) |
| The swipe back from page 1 ran in edit mode, so dragging an icon toward the start edge left edit mode for Glance | `HomeDeck.backSwipeToGlance`: never while editing or a card is open |
| With Glance off a swipe anywhere on page 1 opened Comms | Comms opens from its edge only; the hand-over exists only to reach Glance |
| `rightEdgeSwipe` (dock rail on the right side) did not consume the gesture, so one right-edge swipe opened the dock AND paged the home from Glance | the gesture is consumed once it triggers |
Not changed (device only): the swipe back to Glance runs on release, not as a live drag.

## Settings, migration, edit flow, persistence and tests (second adversarial pass)
Each issue below was not in the earlier reports; each has a test that failed first (or an audit) and is fixed. `HomeUnifyReview2FlowTest`, `HomePagesImportW28Test`, `HomeUnifyW28AuditTest`, `HomeUnifyRowsTest`.
| # | Issue | Fix |
|---|---|---|
| 1 | Importing a settings file from before wave 28 (no `homePages`) set Pages to Off whatever the file's layout held, so a home with pages arrived with every page hidden; the install's own migration never ran because the import wrote `homePages` | `SettingsCodec.decode(settings, layoutHasPages)` applies `HomeModelMigration.pagesMode` against the layout of the same file; the retired keys are read, not kept as unknown fields (`HomePagesImportW28Test`) |
| 2 | An empty Continuous scroll showed nothing: the "Long press to add apps" hint was excluded for Continuous, so a user who picked Continuous saw a blank screen with no way to start | the hint shows on the empty scroll too and opens edit mode (`HOM_01_w28_anEmptyContinuousScroll...`: tap hint, edit mode, add an app) |
| 3 | "Add page" (quick sheet on Glance, Ground Control) silently switched Pages from Off to Paginated, a second way to turn pages on besides the setting and without the one-line explanation | offered only in Paginated; the Pages setting and "Edit home" (with its notice) are the only ways to turn pages on (`HOM_22_w28_addPageIsOfferedOnlyWhenPagesIsPaginated...`, `HomeUnifyRowsTest`) |
| 4 | Ground Control still said "Edit home pages" while the quick sheet and the Spec say "Edit home" | renamed (the label already has 8 translations); hint of "Add page" shortened and translated |
| 5 | 54 lines (6 strings x 9 tables) of the removed Scroll or Paginated dialog and "Pages after Glance" row stayed in `L10nW26homePages`, plus an unused "Pages start empty" string with 8 translations in the wave 28 table | removed; `HomeUnifyW28AuditTest` requires every wave 28 string to be used by code and translated in 8 languages and no retired key or dialog outside the migration code |
| 6 | The Spec.md HOM-22 row still described the wave 26 deck: the add-page flow asking once Scroll or Paginated, "Add page is reachable from Glance" | rewritten for the one pager, the three-value Pages setting, one indicator, migration and Add page only in Paginated; audit test on the wording |
| 7 | `HomeUnifyRowsTest` kept `mutableStateOf` in a plain test class and wrote it outside a Compose host (rule: no snapshot writes outside composition) | plain field; audit test |
| 8 | `HomeDeck.visiblePages` was tested but never used: `HomeScreen` repeated the rule inline | `HomeScreen` calls it |
| 9 | Stale comments naming "Edit home pages", Scroll or Paginated question | updated (`HomeShown.kt`, `SettingsCategories.kt`, `HomeQuickSheet.kt`) |
Verified and fine: the three-value row exists once in Ground Control (Home screen) and as three quick-sheet chips, nowhere else; no `pagesModeChosen`/`HomeScroll`/`PagesModeDialog` use outside migration; migration of old Continuous, Paged chosen, unchosen with and without pages, old landing values, Off then On restoring items, an existing store never losing pages (`HomeLandingTest`, `HomeOrderTest`); `homePages` is exported in the settings document and `homeScroll`/`pagesModeChosen` are deleted on the next save; Edit home from Glance with Pages Off turns Paginated on and enters edit mode (new flow test); no new infinite animation (indicator fade is 0 ms under reduced motion).
Known and left: the notice toast also appears when an item is placed from Glance while Pages is Off (its text says to pick apps; harmless); the swipe back to Glance runs on release (device only).
Changed tests: `HomeQuickSheetGlanceTest` Add page test now starts in Paginated and the Continuous test also covers Off wording; `HomeUnifyRowsTest` Add page test replaced by the Paginated-only test; `HomeUnifyFlowTest` Ground Control Add page test starts in Paginated.
Test runs: TourFreshSmokeTest, TourBusySmokeTest, TourRecreateSmokeTest, TourSizesW23SmokeTest, HomeUnify*, AppSmokeTest, A11ySweepTourTest and UpgradeSmokeTest run twice each: green except the new Review2 test before its fix (the intended red). Full `./gradlew testDebugUnitTest assembleRelease --offline --continue -q --max-workers=2`: assembleRelease built; red were `PackageRemovedW26SmokeTest` (known flake, passes alone), `BackupPaneUiTest.CFG_04` (a wrong-thread view call, flaky, passes alone, unrelated to the home) and `HomeQuickSheetGlanceTest` (the old Add page expectation, fixed here).
