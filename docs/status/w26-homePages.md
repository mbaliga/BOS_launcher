# Wave 26, stream homePages: status

Branch w26-homePages. Owner clauses 4 to 9 (home model); not the wallpaper (stream pagesWallpaper), not the arc drawer. Decisions: docs/decisions/homePages.md. Nothing here is device-confirmed.

| ID | Status | Note |
|---|---|---|
| HOM-01 | done (JVM and Robolectric) | Pages after Glance: Scroll (`ColumnScrollHome`, `ColumnFill` math) or Paginated (grid pages), chosen once in the add-page flow (`PagesModeDialog`), changeable in Ground Control, Home. "Add page" works from Glance (quick sheet `quick.addpage`, Ground Control row): `HomePagesFlowTest` starts on Glance, long-presses, taps Add page, chooses Paginated, lands on page 1, sees the Glance stop in the page dots, returns by the dot and goes forward by a swipe. Scroll fill math tested for 360x640, 411x891, 891x411, 1280x800, 280x653 (`ColumnFillTest`). |
| HOM-22 | done | The landing selector, `HomeLanding`, the cycle command (Ctrl+Shift+L, corner and gesture action), the Recent apps and All apps landings and their UI are deleted. `glanceEnabled` On/Off in Ground Control, Home and in the long-press quick sheet (tag `quick.glance`, "Calm home screen (Glance)"). Migration `HomeModelMigration` (JVM-tested and through the upgrade smoke tests): every old landing value reads as Glance on, pages untouched, old Continuous scroll becomes Scroll. |
| HOM-23 | done | Glance first stop of the deck (`glance.deckdots`, `pages.glancedot`); `HomeOrder` (Left to right default, Right to left): Launchpad edge, Recents edge, column and page order, scroll start, hotspot roles, swipe directions; independent of the language (`HomeOrderCombinationsTest`: 4 combinations of order x language). |
| STR-01, STR-14 | adjusted | The Launchpad edge now follows the home order, not the app language (an RTL language with Left to right order keeps Launchpad on the left). |
| CFG-19 | done | New strings in `L10nW26homePages.kt` (8 languages). |

## Verified
- `./gradlew testDebugUnitTest assembleRelease --offline --continue -q --max-workers=2` passed (exit 0) on the final tree. Screenshots READ (feature-grid, `docs/status/w26-homePages-screens/`): scroll portrait with 30 icons (411x891: 7 rows, fills columns top to bottom), scroll landscape 30 icons (891x411: 3 rows, 10 columns), scroll right to left (starts at the right edge, first column right-most), paginated 2 pages with the Glance stop, Glance off (no Glance stop), paginated right to left (first cell at the right, Glance stop at the right), Glance with deck dots in both orders.
- Whole-app screenshots are not produced: the compose window capture never settles in the full app under Robolectric (the ambience animates); the surfaces are shot standalone instead.

## Device only
Touch feel and inertia of the scroll, real window insets for the top and bottom bands, haptics, Launchpad card geometry on a real RTL layout, rotation with a live widget.

## Old tests updated or deleted (each one encoded the replaced landing model)
| Test | Action | Reason |
|---|---|---|
| core-data `HomeLandingTest` (settings and rules parts) | rewritten; recents and hotspot parts kept | default Glance, migration, rules per surface, retired cycle action |
| core-data `HomeChromeRulesTest`, `GlanceAuditW20Test`, `GlanceModelTest`, `W13AuditStateTest`, `HomePlacementLandingTest` | updated to `HomeShown`; landing migration and marker tests removed | `HomeLanding`, `LandingMigration` are gone |
| core-input `HomeLandingCommandTest` | deleted | the cycle command is gone |
| feature-grid `HomeLandingUiTest` | deleted | landing screens are gone |
| core-quality `HomeLandingW12AuditTest`, `W13CrossAuditTest` | updated | file names, new pref keys, no landing key written |
| feature-launchpad `W13LabelsTest` | updated | labels of Order and the pages mode instead of the landing |
| app `AppSmokeTest` (landing tests) | replaced by Glance on/off tests; cycle shortcut test now asserts it does nothing; "recent apps second line" test deleted | no landing, no Recent apps landing |
| app `AppSmokeHarness`, `EditCardAppTest`, `TourBase`, `TourBusySmokeTest`, `TourFreshSmokeTest` (landing tour step becomes a Glance on/off tour), `TourRecreateSmokeTest`, `ZeroSizeStartSmokeTest`, `A11ySweepTourTest`, `DockOptionalW24SmokeTest`, `DockDeskReviewW24RulesTest` | updated | harness starts with Glance off (the old Selected apps home); landing values replaced |
| app `HomeLandingItemsW24Test`, `GlanceSmokeTest`, `GlanceEdgesSmokeTest`, `UpgradeSmokeTest` | updated | surface instead of landing; RTL tests now set the Right to left order; old stored landing values still start and migrate |
| app `HomeLandingQuickSheetTest` | replaced by `HomeQuickSheetGlanceTest` | no HOME LANDING selector; Glance tile, mode, order, Add page |
| new | `HomeOrderTest`, `ColumnFillTest`, `ColumnScrollHomeUiTest`, `GlanceHotspotLayerUiTest`, `HomePagesW26ScreenshotTest`, `HomePagesFlowTest`, `HomeOrderCombinationsTest` | |
