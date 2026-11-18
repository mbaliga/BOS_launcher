# Wave 27 commsDesign

Decision doc: docs/decisions/commsDesign.md. Screenshots: feature-launchpad/build/screenshots/comms-*.png (portrait and landscape, dark and light, 0, 1, 4 and 30 notifications, the Unread tab, the menu, right to left), read and compared with the owner's mock-ups.

| ID | Status | Note |
|---|---|---|
| STR-01 (Comms menu panel) | done (JVM), not device-verified | 58 percent panel, icons-only 3 x 2 GROUP BY grid with hairlines, Show rows (All Comms first, real coloured 24 dp app icons, counts right-aligned), Options rows (density, default view, Read history, mark visible as read, delete history with confirm, Groups and rules, Clear all). Tests STR_01_w27_*. |
| STR-02 (Comms screen) | done (JVM), not device-verified | Header with menu glyph, title, clock, four status glyphs; uppercase tabs; 56 dp icon rows, inset hairlines, no stripes, no bands in the default view; history rows in the same style. Tests STR_02_w27_*. |
| STR-10 (status glyphs) | done (JVM) | Brightness, battery, signal, Wi-Fi as plain glyphs (new `SystemTileKind.Brightness`, display settings); clock tap exposes `onOpenSound`. |
| Light palette | derived | Shared light tokens with the same layout; see the decision doc. |

## Removed on purpose (the mock-ups do not show them)
- Floating compose button and its Radar hook (`onCompose` parameter of `LaunchpadPanel` and `LaunchpadContent`, the `onCompose = {...}` lines in `app/.../HomeScreen.kt`), with its tests (STR_02_compose_*, STR_01_w26_composeButtonSitsAtTheCardEdge).
- The four square system buttons (time, battery, connectivity, sound) row, the jump strip of names (`SectionStrip`), the 4 dp stripe, the `...` options button and inline options panel, the hub quick-filter chip row, the "Home" button, the panel's own title and close button.

## Updated tests (they encoded the old layout)
- HubTest: HUB_10 (tabs: five, no Read tab), HUB_11 (default view through the state), HUB_12 (Read selected through the state), HUB_13 filters (now state driven, chip row gone), HUB_14 pinch ladder (grouping starts Flat), HUB_26 (grouping set through the state, Flat has no headers), HUB_15 two-finger swipe (Read is skipped), HUB_15 swipe-down with filter, HUB_13 empty states, HUB_20 (options part moved), HUB_18 screenshot (no options panel). Deleted HUB_14_densityAndGroupingHaveButtonEquivalentsInOptions, HUB_16_markVisibleReadAndDeleteHistory..., HUB_16_aHalfMadeDeleteConfirmation...: replaced by STR_01_w27_densityAndGroupingOfAHistoryViewAreInTheMenu and STR_01_w27_markVisibleReadAndDeleteHistoryAreMenuRows... (Comms menu rows).
- LaunchpadHubW25Cases: panel width (58 percent), close button test (now Escape only), menu options test (new rows, count), compose tests (replaced by "no floating compose button"), row geometry (56 dp icon, 20 dp margins, no stripe), switcher slot position (under the clock row).
- LaunchpadEdgeW26Cases: no `onCompose`, 20 dp time inset, last glyph instead of the sound tile, compose test deleted.
- LaunchpadMenuMotionTest: panel width rule (58 percent, at most 380 dp).
- LaunchpadRoomyCases: row 78+ dp with a 56 dp icon and 20 dp padding; jump strip test replaced by "there is no jump strip".
- LaunchpadGroupsCases STR_13_column_aWorkSectionShowsOnePersonFromEveryApp: scrolls the list instead of the strip.
- app: A11ySweepTourTest (pane title Comms, Comms menu, menu rows instead of chips and inline options), HomeLandingItemsW24Test HOM_18 (uppercase tab labels), TourBusySmokeTest STR_02_busyTour... (no group strip). core-quality: A11yPolishW23AuditTest (pane title Comms), W13CrossAuditTest NFR_13_theNewControls... (the +N more line keeps its 48 dp target; the strip is gone).
- LaunchpadCardCases STR_10_card_systemRow... (same name, now the four status glyphs), LaunchpadScreenshotCases STR_10 (descriptions), LaunchpadTokensTest CFG_10 (dark palette is the Comms mock-up palette).

## Known limits
- History rows cannot tell a message from other notifications (the stored sender falls back to the title), so they always read text first, then who; live rows use the message flag (message text then sender, otherwise title then text).
- Signal glyph is bright unless offline and Wi-Fi glyph unless connected by Wi-Fi: the platform snapshot holds one active network, not both radios.
- Not device-verified: real launcher icons of installed apps (Robolectric seeds the icon cache), system settings targets.

## Build
Full command run once: `testDebugUnitTest assembleRelease` -> release APK assembled; 950 JVM tests in the full run, two failures not caused by this stream: `PackageRemovedW26SmokeTest` fails only under load (passes alone with these changes, a known unstable dock assertion from the wave 26 audit) and `feature-grid` `InputTouchPathsUiTest` INP_07 (names a test missing from W4RadarInputDockTest; no feature-grid file is touched here).
