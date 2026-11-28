# Wave 30: puckSections status

Nothing here is device-confirmed; the Robolectric screenshots (build/screenshots of feature-grid, `w30-sections-*.png`) were read and compared with the owner's Concepts screenshot.

| ID | Status | Note |
| --- | --- | --- |
| CFG-15 | done in JVM and Robolectric | four slots (`PuckSlot`), defaults, migration of the wave 29 ring, editor in App drawer > Drawer shortcuts, availability, state file |
| HOM-01 | done in Robolectric | the disc opens with four wedge sections and a Back hub (`DrawerSectionsW30Test`) |
| INP-18 | done | Back first in the traversal order, digits 1 to 4 run a section, Esc / system Back close |
| INP-19 | partly | Back and ring depth are 48 dp; across the arc a wedge is 25 to 38 dp (four wedges in a quarter turn), see puckSections.md |
| NFR-13 / NFR-14 | done | labels, state description for unavailable sections, no infinite animation, works with animations off |

## Not done / does not match
- Wedge width across the arc is under 48 dp on phones (see the decision); the whole wedge is the target.
- Radar scope shortcuts do not exist as actions, so only Radar itself is offered.
- No on-device run; the long-press name is a transient pill, not a system tooltip.
- `PuckLayout` ring slots and capacity (core-input) are dead code kept to avoid a merge conflict with the drawerLoop stream.

## Tests changed
- `PuckActionsTest` (core-data): rewritten for slots, migration, availability (the old list, cap, move and remove tests encoded the multi-select ring).
- `RehydrationTest`: the puck store round trip uses `setAll` / `slots`.
- `StateScreensTest` CFG_15: the ceiling-equals-layout test (nine ring actions) replaced by four sections equal four wedges; the glyph test stays.
- `WheelDiscTouchAreaW29Test`: ring action overlap checks replaced by four sections inside the window, Back at least 48 dp and clear of the chips and app icons.
- `WheelDiscW29ScreenshotTest`, `DrawerRevertW29Test`, `InputTouchPathsUiTest`: "Search apps" hub button replaced by Back (the centre closes the drawer, no search glyph); the ring action test sets slot 0.

## New tests
`PuckSectionsGeometryTest` (wedges, gaps, mirror, order, hit, bounds), `PuckSectionsUiTest` (labels, tap, gap tap, app slot, long press name, empty slot, unavailable, Back, traversal order, mirror, RTL, reduced motion, 48 dp, five screenshots plus a pressed one), `DrawerSectionsW30Test` (home wiring, Radar section, app section, empty long press opens the editor, migration on start), `PuckActionsPaneW30Test` (the editor), `PuckActionsTest`.
