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
