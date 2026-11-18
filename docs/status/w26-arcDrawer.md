# Wave 26 arcDrawer: looped sections, Compact/Expanded switcher, colour-picker drawer

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the rendered screenshots (`feature-grid/build/screenshots/w26-*.png`) against the two Concepts references.

| ID | Status | Note |
|---|---|---|
| INP-18 | built (device untested) | Left/Right and the scroll wheel now step round the looped sections (`PuckKeys.step`); a single section stays. Test `INP_18_left_right_step_round_the_looped_sections`; Robolectric `ARC_01_keyboard_left...`, `ARC_01_swiping_along_the_arc...`. |
| ARC-01 | built | Sections loop (pure `ArcMath.wrap/step/wedgeSections`, `ArcDrawerMathTest`), by drag, wheel, keys and TalkBack actions. |
| ARC-02 | built | Setting "Switcher: Compact or Expanded", default Expanded, persisted and exported with `launcher_prefs` (`SwitcherStyleTest`, `UpgradeSmokeTest` enum-garbage key list). Compact: icon only; Expanded: icon plus count, dark selected wedge with coloured edge arc, round hub. |
| ARC-03 | built | Expanded drawer is the colour-picker fan (concentric rings of rotated square cells, mode row, search glyph, per-ring drag with fling, tap, long press). Pure polar layout tests: no overlaps, mirrored corners, counts per ring, on screen in portrait and landscape. |

## Old tests changed
- `core-input` `InputStreamTest.INP_18_left_right_rotate_and_clamp` became `INP_18_left_right_step_round_the_looped_sections`: it asserted that Left on the first and Right on the last section do nothing; the owner replaced that with looping.
- `feature-grid` `WheelGeometryTest`: removed the tests of the old wheel's geometry (`quadrantStarts`, polar/screen round trips, spoke layout, rotation clamp, culling, tangent text, etc.) together with the helpers they covered, which only the old spoke wheel used. The remaining normalisation, angular velocity, letter bucket and grouping tests stay.
- `UpgradeSmokeTest`: `switcherStyle` added to the list of keys fed garbage enum values (must not throw).

## Device-only / not verified
Fling and touch ergonomics, TalkBack speech order, real hardware keyboard focus, cutout insets in the real window.
