# Wave 30 drawerLoop: the app drawer sections loop

Verified by JVM and Robolectric tests and by reading the screenshots (`feature-grid/build/screenshots/w30-at-A-portrait-right|w30-at-Z-portrait-right|w30-at-hash-portrait-right|w30-at-hash-portrait-left|w30-at-A-landscape-right|w30-at-Z-landscape-right|w30-at-A-landscape-left|w30-category-at-first-portrait-right|w30-mid-fling-portrait-right.png`): at A the band reads Z # [A] B C, at Z it reads X Y [Z] # A B, at '#' Y Z [#] A B C, mirrored and landscape alike, and the category ring shows Other before Games. Nothing run on a device (fling feel and the real scroll wheel are device-only).

| ID | Status | Note |
|---|---|---|
| HOM-01 | done | Loop: periodic rotation, cyclic bands and spokes, shortest-path focus; `WheelLoopW30Test`, `WheelLoopW30UiTest` |
| INP-18 | done | Keyboard Left/Right wrap, Home/End, typed letter wraps, scroll notches wrap, TalkBack Next/Previous section; `InputStreamTest`, `WheelLoopW30UiTest` |
| CFG-15 | unchanged | The wheel grouping setting still picks letters, categories or Recent; categories loop, Recent (one group) stays |

Removed or changed tests
- `InputStreamTest.INP_18_left_right_rotate_and_clamp`: renamed `INP_18_left_right_rotate_and_wrap`; it asserted no movement at the ends, now Left on the first group goes to the last and Right on the last to the first (a single group stays).
- `WheelGeometryTest.rotationClampLetsFirstAndLastSpokeReachTheMiddle`: the initial rotation of a long ring is no longer 4 degrees past the leading edge; a looping ring starts with its first group centred (so '#' is visible before A). The 4 degree start is asserted on a ring too narrow to loop. The clamp assertions are kept (the small-ring path still clamps).

Adversarial review findings (all fixed; tests named `*_w30r_*`)
1. A ring only just wider than the drawn window (span between 150 degrees and 150 plus its widest group) drew one group, its label and its icons at both ends of the window at once. `WheelGeometry.loops` now needs the span to be at least the window plus the widest group; `HOM_01_w30r_no_group_is_ever_drawn_twice_at_once` (n = 2..60, rotation sweep). `HOM_01_loops_only_when_the_ring_is_wider_than_the_drawn_window` changed: ring(8) (160 degrees) no longer loops, ring(9) does.
2. The TalkBack section nodes asked for the sections with no margin, which made a ring of 90..150 degrees "loop" for the nodes only: ghost duplicate nodes where nothing is drawn. The loop decision no longer depends on the caller's margin; `HOM_01_w30r_a_ring_that_does_not_loop_has_no_copy_slots_for_the_section_nodes`, `HOM_01_w30r_a_small_ring_has_one_node_per_section`.
3. A second Right/Left press (key repeat, scroll notches, TalkBack) while the ring was still turning stepped from the group under the middle at that instant, so the press was swallowed. The drawer now steps from the group the spring is heading for (a quick reversal comes back); `INP_18_w30r_two_quick_Right_presses_move_two_sections_and_a_quick_reversal_comes_back`.
4. Reduced motion: the ring still used a spring and a fling. Now it snaps (also across the seam), a release settles on the nearest group; `HOM_01_w30r_reduced_motion_snaps_across_the_seam_without_a_spring`.
5. The section nodes' selected state was read in composition, recomposing the whole disc on every frame of a turn; it is now read in the semantics lambda (no test: a recomposition count is not observable here).
6. The section nodes had a selected state and a click but no role; they are buttons now: `INP_18_w30r_section_nodes_are_buttons`.

Known and left: 48 dp section nodes overlap their neighbours on the narrow band (the band arcs are about 25 dp), so explore-by-touch may land on a neighbour; swipe navigation is unaffected. A typed letter past the last A-Z label goes to the first group, not to '#' (the owner text only fixes the order of the ring).
