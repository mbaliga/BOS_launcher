# Wave 28 usageTimeline: Usage merged into Timeline

Owner: combine Usage with Timeline as a small visualization above it, drawn with the ASOC viz toolkit (version 0.3.0). Decision: `docs/decisions/usageViz.md`.

| ID | Status | Note |
|---|---|---|
| STR-02 (hub views) | changed | Tabs are ALL UNREAD FILES TIMELINE; `HubView.Usage` removed; `HubView.fromId("usage")` and `forPanel("usage")` give Timeline; a stored default view `usage` is read as Timeline and rewritten; swipes and keys step over four tabs (verified: `UsageViewMigrationTest`). |
| HOM-17 (usage) | changed | The Usage panel body (`UsagePanel`, `sliceColor`) is deleted; the data source (`UsageStatsSource`) is reused through `UsageWeekSource`. The Usage home tile, its range setting and `PanelTab.Usage` stay (a home item that now opens Timeline). |
| Usage block | new | `UsageTimelineBlock` is the first item of the Timeline list: stat header, `steps` of 7 days, one `stack` of the selected day, legend with app icons; access-missing action; empty line; table view; accessible names (tests `UsageBlock*Test`, `UsageTimelineW28Test`). |
| ASOC viz port | new | `feature-launchpad/.../ui/viz` (VizTextures, VizSteps, VizStack, VizLegend, VizStat, VizTable, Viz) (test `VizTexturesTest`: eight distinct non-empty pixel patterns, cached tiles). |
| Data shaping | new | `UsageShaping`, `UsageFormat`, `UsageWeekRepo` (`UsageShapingTest`: 7 columns, top 4 + Other with ties, empty/NaN guards, stable textures, durations, caching). |
| L10n | new | `L10nW28usageTimeline.kt` (es, fr, de, pt, hi, zh, ja, ar). |

## Tests changed or removed

- `HubTest.HUB_10_theSwitcherIs48dpHighShowsTheFiveTabsAndSwitchesOnATap`: the Usage tab click and `panel.usage` assertion removed (the view no longer exists); asserts the Usage tab is absent.
- `HubTest.HUB_10_aHomePanelLinkLandsOnTheMatchingHubView`: `forPanel("usage")` now expects Timeline (was Usage).
- `HubTest.HUB_19_theRealPanelShowsTheHub...`: requests the view of an old `usage` link and expects `panel.timeline` (was `panel.usage`).
- `CommsDesignCases.STR_02_w27_tabsAreUppercaseAllUnreadFilesUsageTimeline...` renamed `STR_02_w28_tabsAreUppercaseAllUnreadFilesTimeline...`: four tabs, USAGE text and tag asserted absent.
- `A11ySweepTourTest.NFR_13_wave25Surfaces...`: the `hub.view.usage` click and sweep removed (Timeline sweep stays and now covers the block).
- `HomeLandingItemsW24Test.HOM_18_onGlanceTheFilesUsageAndTimelinePanels...`: asserts FILES and TIMELINE present and USAGE absent; the Usage tab click became a wait for the usage block (access missing or data) in the Timeline.
- `HomeInfoW3AuditTest.HOM_17_usageAccessIsAReadOnlySpecialAccess...`: the `ACTION_USAGE_ACCESS_SETTINGS` check moved from HomePanels.kt to `GrantSupport.kt` plus `UsageTimelineBlock.kt` (the usage block asks through it); the no-network check now covers the block and `UsageWeek.kt` too.
- No test was deleted without a replacement; the Files view tests are unchanged.

## Verified and not verified

- Verified under Robolectric: all of the above, screenshots dark and light, portrait and landscape, granted / selected day / access missing / empty (`feature-launchpad/build/screenshots/usage-*.png`, read and judged against the toolkit's `steps` and `stack` demos: rounded-top columns on one baseline, textured pill, keys in the same textures).
- Device only: real `UsageStatsManager` numbers (the aggregate for a single past day can be coarse on some vendors), TalkBack reading order and the "Show as table" action in the actions menu, the visual weight of the accent yellow on a real display.
- Known deviations from the toolkit (all in `usageViz.md`): the figure only over the selected column, the share labelled under stack parts instead of value and name (the legend carries name and duration), no hover tooltip.

## Full run

`./gradlew testDebugUnitTest assembleRelease --offline --continue -q --max-workers=2`: release assembled. Failures that remain and are not from this stream: `PackageRemovedW26SmokeTest` (known order-dependent flake) and `RepoHygieneAuditTest.CFG_01_everyTestNamedInTheCoverageTableExists` (docs/TEST_COVERAGE.md still names three PAL_20 radar tests that the base commit removed; run `tools/spec_coverage.py`).
