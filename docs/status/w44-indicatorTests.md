# W44 indicator: no dot for the right panel

Owner bug: "A dot is showing where there is no page". The page indicator drew a trailing dot for the right panel (Recents or the dock rail), which read as a page that does not exist.

| ID | Status | Note |
|---|---|---|
| HOM-23 | built | `PageIndicator` has no `right`/`onRight`; `GlanceSurface` has no `rightDestinations`/`onOpenRight`. The row is the Comms glyph, the Glance home glyph, the page pills. Not device-verified. |
| STR-14 | built | The right panel still opens by edge swipe, handle, Ctrl+R and the "Open recents" action; the dock rail by its edge reveal (`RightSideDestinations` untouched). |

Tests rewritten: GlanceHomeUiTest (two dot tests become `HOM_23_theIndicatorHasTheCommsButtonAndTheHomeGlyphAndNoDotForTheRightPanel` and `HOM_23_withCommsOffTheIndicatorIsOnlyTheHomeGlyph`), HomePagesW26ScreenshotTest (LTR/RTL row order now Comms vs house, plus no dot), GlanceEdgesSmokeTest (right-dot opens recents becomes no dot and the swipe still opens it; two STR-14 names), DockStackW26SmokeTest (indicator bounds use the Comms glyph), and call-site signature fixes in GlanceHomeForTest, GlanceNotchUiTest, GlanceTitleW25UiTest, GlanceClockW41Test, HomeUnifyScreenshotTest, FixedHomeChromeW38Test.
