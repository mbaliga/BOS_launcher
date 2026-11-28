# Wave 49, stream actionBubbles (owner feedback on the Orbit "+4")

Owner: "The sub options for the actions suggestions show as a '+4' format, I'd rather have the actions suggestions up front as action bubbles." Nothing here is
device-confirmed. Verified: JVM and Robolectric tests (bounds, overlap, screenshots read by eye). Device-only: the look and the long-press tooltip on a real phone, TalkBack
reading, the rings moving with bubbles attached (motion on).

| ID | Status | Note |
| --- | --- | --- |
| PAL-03 | built | Every matched shortcut of an app (cap raised from 4 to 6) is an action bubble up front, in Orbit (satellites on a tested spot search, thin tie line), Filmstrip and Flat (wrapping row). The "+N" form, its code, its string and its tests are deleted. |
| PAL-07 | built (unchanged) | Keyboard action list after "Open" and ids `nested.<appKey>.<id>` are kept. |
| PAL-18 | built (unchanged) | All three layouts show the bubbles; non-app actions stay on the strip. |
| PAL-20 | built | Long press on a bubble shows its title; the preview still lists all shortcuts. |
| NFR-13 | built | 64 x 72 dp bubble (48 dp slot + label), content description "App shortcut: <title>", traversal index right after its app. |

## Tests

- New `RadarSatellitesTest` (pure geometry): all bubbles placed inside the area, clear of app/others/each other, >= 48 dp, deterministic, edge, landscape, font 1 to 2, RTL, crowded fallback, `placeAll`.
- `RadarShortcutFormW25UiTest` (real `SearchScreen`) rewritten for bubbles: Orbit at 891x411 (font 1, 2), 411x891 (1, 1.5, 2), 360x640 (1), RTL; Flat at three windows and two font scales; Filmstrip at three windows: four bubbles, 64 dp wide, label height, no two interactive nodes overlap, none is a "+N" tag; tap runs; preview lists all; description text; the 360x640 font 1.5 and 2 corner: all four appear inside the display.
- `RadarNestingTest`: cap test uses `MAX + 3`; new test that six shortcuts all nest.
- Deleted: `PAL_03_aRowShowsThreeCirclesAndAPlusN...` (the +N row maths) and the "+N opens the preview" part of the tap test.
- Screenshots (read): `feature-grid/build/screenshots/w49-actionBubbles-orbit-fylz-portrait.png`, `-orbit-fylz-landscape.png`, `-orbit-fylz-rtl.png`, `-orbit-fylz-360-font2.png`, `-flat-fylz.png`.

## Not matched / limits

- See [decisions/actionBubbles.md](../decisions/actionBubbles.md): with motion on a revolving tile may pass under a bubble; at 360 dp wide and font 1.5+ with a full ring there can be no free room and a bubble may overlap another target.
- The spec row text is changed in `docs/Spec.md` (PAL-03); `docs/SPEC_STATUS.md` was not edited.
