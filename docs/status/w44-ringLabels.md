# Wave 44: ringLabels (curved labels on the wheel's band, the Category tap)

| ID | Status | Note |
| --- | --- | --- |
| INP-18 Category tap | built, not device-verified | Root cause: the ring's touch overlay (a 2 x ring-radius square) was composed above the group labels' click boxes; on the 45 degree diagonal it covers the Category label, and Compose gives a touch only to the topmost sibling that contains it. Reproduced by position (Category 0 of 5 radii; A-Z and Recent worked), fixed by one sharing touch layer above the ring with curved slots. `RingLabelsW44UiTest.INP_18_w44_tapEveryGroupLabel*` (bottom corners in portrait and landscape, top corners), `..._categoryHitsAcrossItsWholeCurvedSlotButNotInTheShortcutRing`, `..._groupLabelNodesFollowTheCurvedSlotsAndStillClick`. |
| INP-18 label slots | built | At least 48 dp along the arc where the neighbours allow, never overlapping, radial depth ring edge to hub edge (`RingLabelGeometryW44Test`). TalkBack nodes are the non-overlapping boxes on the slot centres (axis-aligned, not curved: not matched). Keyboard untouched. |
| INP-18 text on the arc | built, not device-verified | `RingLabels.kt` (`ArcLabel`, `RingLabelGeometry`): group names and band section labels set with `drawTextOnPath`; never inverted (every half degree, both corners mirrored), flipped run on the lower half, curved underline, RTL strings, rotation, cached paths and paints, no per-frame allocation (source check). |
| Screenshots | read | `feature-grid/build/screenshots/w44-disc-*.png` (dark and light, bottom-left and bottom-right, each group selected, a rotated ring, landscape, top-left). In them: "Category" and "Recent" visibly follow the circle, the selected one is bold with a curved violet underline, single band letters are rotated tangent; the mirrored corner reads upright (bottom to top at the left edge); light theme unselected names are thin grey on the light ring (as before). The curve is gentle: it is a 126 dp circle. |

## Old tests changed
None deleted. `WheelDiscTouchAreaW29Test` (chips inside the window and not overlapping) passes with the new node boxes.

## Tests run
New: `RingLabelGeometryW44Test` (10, pure JVM), `RingLabelsW44UiTest` (18, Robolectric). feature-grid whole module (1364 tests: 9 failures before the touch-node fix, 5 of them `WheelDiscTouchAreaW29Test` overlap, fixed and rerun green together with the wheel and ring tests; 4 are `GlanceHomeUiTest` and `HomePagesW26ScreenshotTest` indicator tests of the page indicator change on main, not this wave), core-quality whole (green). Not run: app module, `assembleRelease`.

## Device-only
Fonts, Arabic and Hebrew shaping on the real font, how the bend reads at arm's length, the tap feel of the diagonal slot, TalkBack exploration.
