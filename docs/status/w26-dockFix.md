# Wave 26 dockFix status

Branch w26-dockFix. Owner feedback on build 54. Robolectric/JVM verified only; nothing is device-confirmed.

| ID | Status | Note |
| --- | --- | --- |
| DCK-01 (icons-only dock) | changed | Home glyph tap now goes home (was: opened the dock panel). Panel only from the long-press menu, Ground Control, menu key. |
| DCK-09 (taps never open settings) | built (Robolectric) | `DockFixW26Test`: glyph tap = home, background tap = nothing, icon tap = launch, menu entries open the panel. |
| DCK-10 (hide an empty dock) | built (Robolectric) | No pins and no recents: no rail, no Home glyph, no reserved space; returns with the first item. `DockVisibility`, `DockPresence`, `DockFixW26Test`, `DockStackW26SmokeTest`. First pin: app menu "Add to dock", Radar, Ground Control panel "Add apps to the dock" (new). |
| HOM-19 (bottom stack) | fixed (Robolectric) | Dock above the indicator (Glance or page dots), at least 8 dp clear, at five sizes in portrait and landscape; the dock reserve no longer lifts the page dots (GridHome gets `reserve*`). |
| HOM-23 (Glance indicator) | not changed | Indicator drawing untouched; its size is mirrored by `GLANCE_INDICATOR_TOP_DP`. |

Updated tests: `W4RadarInputDockTest.INP_07_theWorkspaceChipSwitchesWorkspaceBySwipeAndGoesHomeOnTap` (asserted tap opens the panel).
No other test encoded tap-opens-panel. Screenshots read: `app/build/screenshots/w26-stack-*.png` (portrait Glance and pages, landscape Glance, 280x653, empty dock).

Known / not done
- Landscape Glance with the dock rail forced on a short window (891x411): the rail sits over the date line of the Glance centre (centre content does not yet give up the rail's room; GlanceHome.kt is another stream's file).
- Hotspot and gesture-area bounds are asserted in the model test (corner squares), not in the Robolectric bounds test.
- Device-only: feel of the long press, the popup window (not in screenshots), real recents arriving after launch.
