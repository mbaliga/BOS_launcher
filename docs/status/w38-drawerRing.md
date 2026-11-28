# Wave 38: drawerRing status

Nothing here is device-confirmed. JVM and Robolectric only; screenshots `feature-grid/build/screenshots/w38-ring-*.png` (dark and light, portrait and landscape, left and right corner, 6, 7 and 8 segments, turned, labels on, empty and app slot) were read.

| ID | Status | Note |
| --- | --- | --- |
| CFG-15 | done in JVM and Robolectric | 6 to 8 segment rotatable ring, 8 stored slots, Ring segments / Drawer shortcuts / Show labels in Ground Control > App drawer, migration of the four-slot store |
| INP-19 | done in JVM | hub 59 dp (within 4 dp of the edge), ring 60 dp deep, whole segments at least 44 dp across the arc (46 dp at the hub edge, 8 segments); cut segments less |
| INP-18 | done in Robolectric | Shift+Left/Right (and plain Left/Right with ring focus) and TalkBack actions turn the ring; plain keys still turn the groups |
| NFR-13 | partly | whole segments meet 44 dp; edge-cut ones do not; hub far above 48 dp |
| NFR-14 | done | reduced motion: snap, no inertia |
| HOM-01 | changed | the app drawer is always the disc; the flat drawer and its settings are removed (see decision) |

## Tests
New: `PuckSectionsGeometryTest` (rewritten), `PuckSectionsUiTest` (rewritten), `PuckActionsTest` (rewritten), `DrawerRingW38Test`, ring-key tests in `InputStreamTest`. Updated: `PuckActionsPaneW30Test`, `DrawerSectionsW30Test`, `RehydrationTest`, `UpgradeSmokeTest`. Removed with the flat drawer: `DrawerOrderingTest`, `DrawerDetailsW26Test`, three drawer tests in `InputTouchPathsUiTest`/`InputW3UiTest`/`ScreenshotTest`; `INP_01_drawer_rows...` and `INP_07_the_close_button_and_back_dismiss_the_drawer` and `INP_07_the_drawer_style_decides...` went with it.

## Not done
No Wi-Fi sheet, sound sheet or recents-specific extra actions beyond the existing `CornerActionId` list. Per-segment rows live in the Drawer shortcuts pane (one tap in), not inline in the category list.

## Device-only
Drag feel and fling, outer-wheel hand-over, TalkBack explore-by-touch, real wallpaper look.
