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
