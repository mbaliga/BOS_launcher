# Wave 58: defects of the code audit (stream defects, branch w58-defects)

Every item was checked against the code of this branch first; all were still present (none had been fixed by a later wave). Decision record:
`docs/decisions/defects58.md`. Requirements CFG-60 to CFG-64 (Spec.md, SPEC_STATUS.md). Nothing here is device-confirmed; all checks are JVM or
Robolectric tests.

| ID | Item | Still present? | What changed | Test (fails without the fix) |
|---|---|---|---|---|
| CFG-60 | 1. Backup gaps | yes: `launchpad_hub` excluded from backup and not in the export allow-list, `icon_colors` in NEVER_EXPORTED_STORES, `theming` neither exported nor in the allow-list (the old classification test only saw literal file names, so `theming`, `grant_history` and FILE constants slipped past) | `launchpad_hub`, `icon_colors`, `theming` exported (PrefFilesSource.ALLOWED) and backed up; Comms widget ids moved to device-local `comms_widgets` (one-time move); `DEVICE_KEYS` keeps a stray widget list out of export/import; `grant_history`, `theme_undo` device-local; reload of Comms options, icon colours, theming after an import | `BackupGapsW58Test` (every opened file incl. FILE constants classified; every exported file round-trips; device key never leaves or gets replaced), `BackupRoundTripW58Test` (real stores: Default view, Clear from the shade, Widgets switch and place, icon colour, palette source, density, text size, clock format survive export, fresh install, import; widget id stays behind; old widget list migrates once) |
| CFG-61 | 2. Clock format | yes: Settings cards filtered out System and showed the default as 24-hour; the clock tile said only "System" | `ClockFormat.System` = "System (12/24 by device)"; Settings cards, clock edit panel and clock tile list all three, System first; Studio `schema.json`/`schema.js` regenerated with `STUDIO_REGEN=1`, the Studio fixtures with `node web/liftoff-studio/tests/make-fixtures.js` (node tests 26/26) | `DefectsW58Test.CFG_61_*`, `DefectsW58AppTest.CFG_61_theClockFormatRowOffersSystemFirstAndShowsItAsTheDefault` |
| CFG-63 | 3. First run | yes: Pick a look only set `homeStyle` (invisible with Pages Off); no hint on Glance; DND actions opened the system page directly | Pick a look = Glance only (Pages Off) or a page style (Pages on, Paginated); Glance shows "Long press for options" once; DND: explanation first (`DndAccessExplainer`), the background service never opens the page (decision: ask, keep the bindings) | `DefectsW58Test.CFG_63_*` (applyLook, hint rule and flag), `DefectsW58AppTest.CFG_63_glanceShowsLongPressForOptionsOnceOnAFreshInstall` (real home), `CFG_63_aSilentActionWithoutAccessAsksFirstAndNeverOpensTheSystemPageOnItsOwn` (real ActionRunner) |
| CFG-62 | 4a. Web suggestions | yes | `disabledUnless(searchWeb, "Web results is off")` | `DefectsW58AppTest.CFG_62_webSuggestionsSaysWhyItDoesNothingWhileWebResultsIsOff` |
| CFG-62 | 4b. Radar layout re-pins | yes (both the row and its visual picker) | layout pick leaves `searchLayoutPinned` alone | `DefectsW58AppTest.CFG_62_pickingARadarLayoutLeavesKeepThisLayoutWithAKeyboardAlone` |
| CFG-62 | 4c. Pinch toggle loses bindings | yes | `PinchDensityToggle`; replaced bindings kept in the shortcuts file (`pinchSaved.*`) and restored | `DefectsW58Test.CFG_62_pinchDensity*`, `CFG_62_theRememberedPinchBindingsSurviveARestart`, `DefectsW58AppTest.CFG_62_turningPinchDensityOffPutsTheCustomPinchBindingsBack` |
| CFG-62 | 4d. Dead swipe-left default | yes | default unbound; `GestureDefaultsW58` keeps Radar once for an older install whose home lets the swipe fire, leaves the dead default otherwise, never touches an explicit choice | `DefectsW58Test.CFG_62_swipeLeftIsUnboundByDefault...` |
| CFG-62 | 4e. appsOnHome | yes (forced every start, no UI) | field, enum, store keys, reset entry and Studio field removed; `legacyAppsEverything()` feeds the pages migration, key dropped after it settles; dropped on import | `DefectsW58Test.CFG_62_appsOnHomeIsGone...` |
| CFG-62 | 4f. HomeOrder.corner() | yes (no caller) | decision: hotspots stay physical; function deleted, enum comment and `homePages.md`, `drawerRevert.md`, handoff fixed | `HomeOrderTest.HOM_23_theOrderMirrorsCommsAndRecentsButNotTheHotspots` (replaces `HOM_23_hotspotCornersSwapLeftAndRightInRightToLeftOnly`, which tested the unused function) |
| CFG-62 | 4g. Grid + Continuous axis | yes | surface is sideways in both orientations | `DefectsW58Test.CFG_62_gridContinuousIsASidewaysSurfaceInBothOrientations` |
| CFG-62 | 4h. Theme undo memory-only | yes | `ThemeUndo` (own file `ThemeUndo.kt`, so the theme pack sources stay storage-free) kept in `theme_undo` | `DefectsW58Test.CFG_62_theThemeUndoSurvivesProcessDeath` |
| CFG-62 | 4i. Profile without left widget | yes | `ProfileLeftWidget.apply`: only the id is cleared | `DefectsW58Test.CFG_62_aProfileWithoutALeftWidgetKeepsTheGlobalLeftPage` |
| CFG-64 | 5. Permissions | yes | removed health.READ_* (5), ACTIVITY_RECOGNITION, READ_MEDIA_* (3), the Health Connect `<queries>` entry, rationale filter and dependency, dead `Health.kt`, `StepsSource.kt`, `HealthSourcePref`; media checks in FileIndexer, FileSearch, CapabilityRegistry follow | `PermissionConsumersW58AuditTest` (every declared permission of every manifest has a named consumer; the removed ones stay gone) |

Found on the way (fixed): `SettingsCodec.encode`, `ProfileSettings.readPrefs/writePrefs` relied on `LauncherPrefsStore.update` always writing, which held
only because the store's `appsOnHome` default differed from the data class; with the field gone a default settings value would have exported as nothing.
They now call `LauncherPrefsStore.writeAll` (caught by `HomePagesImportW28Test`). The SPEC_STATUS Counts line was already stale at the base (175 vs 185 rows);
corrected together with the five new rows.

Old tests changed or removed, with the reason:
- `HomeOrderTest.HOM_23_hotspotCornersSwapLeftAndRightInRightToLeftOnly`: replaced (the function it tested is deleted by decision).
- `HealthFormatTest`, `StepMathTest` (core-data): deleted with the dead `Health.kt` / `StepsSource.kt` they tested.
- `EditCardPagesTest.HOM_01_freshInstallSeedsOneEmptyPageAndNoApps`: the `FreshHome.prefs(...).appsOnHome` line removed (field gone).
- `EditCardAppTest`, `AppSmokeTest`, `RecentsPanelSmokeTest`, `TourRecreateSmokeTest`, `HomeUnifyReview2FlowTest`, `PagesExplicitW30FlowTest`: the `appsOnHome = ...` arguments removed (field gone; the legacy migration test still gets "All apps" from the existing-install fallback).
- `StudioSchemaW56Test`: the `appsOnHome` field row removed from the editorial table; schema regenerated.
- `DeviceKitAuditTest.NFR_20_noPermissionWasAddedByTheDeviceKit`: expected set without the removed permissions.
- `W13CrossAuditTest.NFR_18_everyPreferenceFileTheSourcesOpenIsClassified`, `NotificationHistoryTest.HUB_04_*`: classification follows the new files.

Not done / limits: system backup and device-transfer restores were not run (Robolectric has no backup agent); the DND explanation and the onboarding cards
have no screenshot test; motion sensors and the real Do Not Disturb page are device-only. The Studio's hand-written `cards.js` still has a drawing for
`appsOnHome` (harmless, no schema field uses it; left to the Studio stream).
