# w17-glanceEdges: status

Stream: Launchpad on the left and recents on the right, reachable from the Glance home. Owner bug report on build 48: "The glance needs to have launchpad on the left. Why is that missing?" Decisions and the root-cause report: `docs/decisions/glanceEdges.md`. Nothing here was confirmed on a device; device rows are in `docs/device-checklist.md`, "Glance edges (wave 17)".

Reproduction (injected touch swipes, `GlanceEdgesSmokeTest`): on build 48 the edge swipe, the shortcut and the corner action all worked from Glance; what was missing was every visible affordance (cause: `showsChrome(Minimal)` false, plus the decorative indicator glyph) and, on a real gesture-nav phone, the swipe zone sits inside the system back zone.

| ID | status | note |
| --- | --- | --- |
| HOM-23 | done, device pending | `GlanceEdgeHandle` (feature-grid `GlanceEdges.kt`), quiet `LaunchpadWaitingIndicator(quiet = true)`, indicator buttons in `GlanceHome.kt` (`glance.indicator.launchpad`, `glance.indicator.right.*`), Spec row updated. Screenshots (Robolectric): `feature-grid/build/screenshots/glance_handles_portrait_dark.png`, `glance_handles_landscape_dark.png`, `glance_handles_portrait_rtl.png`, `glance_handles_portrait_light.png`, `glance_handles_awake_again.png`. Read: portrait dark and RTL (handles at both physical edges, indicator mirrored; landscape also read). |
| STR-01 | done, device pending | Launchpad reachable from Glance by edge swipe (48 dp zone on Glance), handle, indicator glyph, Ctrl+N and the OpenLaunchpad action; Spec row updated; geometry untouched. |
| STR-09 | done | Quiet waiting dot on Glance (Priority accent kept), tests in `GlanceWaitingDotTest`; the pill stays on the other landings. |
| STR-14 | done, device pending | Quiet right handle and the right-hand dot button on Glance; none when Right side is Off or Dock rail. |
| HOM-22 | done | `HomeLandingRules.showsChrome` replaced by the explicit table `HomeChromeRules` (core-data `HomeChrome.kt`), `HomeChromeRulesTest` covers every landing x element. |
| INP-08 | done | The OpenLaunchpad corner action and the shortcut are covered on Glance in `GlanceEdgesSmokeTest`; the edge zone and the handle tap are the reliable path against the system back gesture (device row). |
| INP-16 | done | Hotspot corners on the left and right edges do not stop the edge swipe (tested from 3 and 97 percent height). |
| NFR-13 | done | Handles and indicator buttons are labelled buttons and focus nodes; 48 dp targets asserted. |
| NFR-20 | done, device pending | No `systemGestureExclusion` (audit green); the wider Glance zone is the only mitigation, recorded as a device row. |

## Existing tests changed because they encoded the old behaviour (wave 17)

- `HomeLandingTest.HOM_22_minimalDrawsNoChrome...`: the `showsChrome` assertions are gone with the flag (the table has its own test).
- `AppSmokeTest.HOM_22_glanceShowsOnlyTheClock...`: the focus graph on Glance now also holds the two indicator buttons.
- `GlanceHomeUiTest`: the one merged indicator description test is replaced by the button tests.

## Residue

- Real edge swipe on a gesture-navigation phone, the system back zone conflict, fade timing and look of the quiet handles and dot with real notifications: device rows only.
- No app-level (whole HomeScreen) screenshot: Robolectric's capture of the full app times out; the surface plus handles screenshots in feature-grid stand in.
