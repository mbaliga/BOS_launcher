# Wave 33: drawerDebt status

Nothing here is device-confirmed. JVM and Robolectric only; Robolectric screenshots (`feature-grid/build/screenshots/w32-puck-*.png`, dark and light, portrait and landscape, left and right corner) were read.

| ID | Status | Note |
| --- | --- | --- |
| INP-18 | done in JVM | a typed letter past the last letter goes to '#' (else the first group); Next past Z is '#', then A |
| NFR-13 | done in JVM and Robolectric | section nodes are their own band slice (48 dp deep, arc width inside the sector), rotated along the band; rectangles proven disjoint at every rotation on six window sizes; a one-spoke slice is under 48 dp wide on windows narrower than 360 dp (geometry limit) |
| HOM-01 | done in JVM | spokes of a group are icon x 1.42 + 1 dp apart; no two icons overlap at any rotation (0.5 degree steps), six window sizes both orientations; about 15 percent fewer groups visible at once at 45 dp icons |
| INP-19 | done | `PuckLayout` ring slots, capacity and their tests removed; hub and label radii remain |
| CFG-15 | partly | hub 44 dp, ring continues past the quadrant edges and is clipped only by the screen; depth is 75 dp, not the asked 56 (see below) |

## Dead code removal
Removed from core-input: `PuckSlot` (ring slot), `PuckLayoutResult.slots/capacity/searchSizeDp/actionSizeDp`, `PuckLayout.clearance/insideWindow`, `GAP`, `MIN_SEARCH`, `MIN_ACTION`, `MAX_ACTION_COUNT`. Tests removed from `InputStreamTest`: `INP_19_no_overlap_at_any_window_size_from_compact_up`, `INP_19_nothing_is_clipped_by_the_window`, `INP_19_ring_stays_inside_the_hub_and_clear_of_the_group_labels`, `INP_19_phone_keeps_the_five_default_actions_and_small_windows_cap_the_count` (each tested the removed slots); `INP_19_sizes_derive_from_the_window` kept on the remaining radii. A search of the repo finds no other reference. `docs/SPEC_STATUS.md` rows INP-19 and CFG-15 still describe the old ring (that file is not edited by this wave; `docs/status/input.md` and `state.md` are updated).

## Fan geometry versus the owner's reference
Matches: the ring now wraps the 44 dp hub with no gap and carries on round it past the quadrant's two edges, so the hub no longer sticks out of a flat end; the outer edge is a clean circular arc, cut only by the screen edge; white ring, black hub, hairline dividers, one black glyph per wedge (inverted in light).
Does not match: the reference ring is about 1 x the hub radius thick; ours is 75 dp deep against the 44 dp hub (1.7 x). The brief allowed 56 dp only if the wedges keep 44 dp across the arc at the outer edge; at 56 dp they would be 39 dp (a 44 dp arc needs an outer radius of 112 dp), so the outer edge stays (119 dp, 46.7 dp across) and only the hub grew. The dividers stop at the quadrant edges, so the ring parts that continue round the hub have no dividers and no tap (inert); they are plain ring colour. App icons in a wedge stay about 19 dp.

## Tests changed
- `WheelLoopW30UiTest`: section angles are signed (-180..180) instead of 0..360, because the wider spokes put Z just outside the quadrant (a small negative angle, read as 355 before); the 48 dp node test now asserts the node's upright bounding box (the node is turned along the band).
- `PuckSectionsUiTest`: `PuckLayout.compute` has one argument.
New: `WheelDebtW33Test`, tests in `InputStreamTest`, `PuckSectionsGeometryTest`, `PuckSectionsUiTest`.

## Device-only
TalkBack touch exploration landing on the rotated nodes (Robolectric checks bounds and click, not the real explore-by-touch hit test); the look of the ring against a real wallpaper; the feel of the wider spoke spacing.
