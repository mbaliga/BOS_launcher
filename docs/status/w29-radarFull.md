# Wave 29, stream radarFull (Radar: the rings and icons use the whole screen, every visible icon is tappable)

Owner (build 58, screen recording): "There is still some error with respect to the touch area that works. The rotating rings and icons are getting cropped in both
the portrait and landscape view." The wave 26 decision (circles, tiles may leave the screen) stands; a visible crop line inside the screen and dead touch areas do not.
Decision record: `docs/decisions/radarFull.md`.

Nothing here is device-confirmed. Verified: Robolectric Compose tests with real pointer injection through `SearchScreen`, and screenshots read by eye
(`feature-grid/build/screenshots/w29-radar-{portrait,landscape}-{empty,typed}.png`: no horizontal cut line, tiles run under the field and above and below the mic edge to edge).
Device-only: the real keyboard animation (the tests fake the IME inset), real touch feel.

| ID | Status | Note |
| --- | --- | --- |
| PAL-18 | built | The Orbit is a layer of its own over the whole display area (top edge to the keyboard top or bottom edge, edge to edge); no clipped region box; the field, buttons, mic, strip circles and wheel are overlays above it. |
| PAL-20 | built | Moving rings roam the whole layer; a tile partly under the field or the mic is drawn and takes taps on its visible part; still rings keep the old region fit. Empty-space taps keep the wave 25 behaviour. |
| NFR-13 | built | The groups read query line (0), results and the Orbit layer (1), mic (2) by traversal index, because the Orbit layer now overlaps the field and the mic geometrically. The ring tiles' semantics node now sits where the tile is drawn (the offset used to come after the semantics, so TalkBack's frame and bounds were at the layer's corner). |

## Cause found while testing

Two things made touches dead beyond the clipped box: Compose gives a touch to the topmost sibling that has a pointer-input node even if that node ignores it, so (1) the typed
strip's scrolling Row, padded inside its scroll node, took the touches in its padding (now the padding is outside the scroll node and the Row is as wide as its circles), and
(2) a child outside its parent's bounds gets no touch at all (the Orbit now fills the layer instead of being clipped in a padded box).

## Tests

New: `RadarFullW29UiTest` (PAL_18_w29_...): for 360x640, 411x891, 891x411 and 1280x800, keyboard closed and 40 percent open, query empty and typed "clau": the Orbit layer's bounds equal the display area
(top 0 to the keyboard top, left 0 to right); 40 grid points each hit a tile face, an overlay control or the documented empty-space action; every tile's visible face takes the tap (centre and four
offsets, also where it passes under the field, the mic and beside the wheel); tiles revolve through the bands under the field and above the mic; the Flat list fills the area; four screenshots.

Changed or removed (each listed with the reason):

| Test | Change |
| --- | --- |
| RadarBoundsW25UiTest `PAL_18_orbitTilesStayInsideTheDisplayArea_*`, `PAL_18_filmstrip*` | Kept: they describe the still rings (motion off) and the Filmstrip. They looked for the tag `orbitTile`, which did not exist, so they checked nothing; the tag exists now and they check. `fakeInsets` waits 1.5 s because the Orbit layer follows the region a few frames behind the keyboard. |
| RadarBoundsW25UiTest `PAL_18_movingRingsStayInsideTheDisplayAreaToo` | Removed: moving rings leave the screen by design (wave 26) and may pass under the field and the mic (wave 29); replaced by `PAL_18_w29_tilesRevolveThroughTheBandsUnderTheFieldAndAboveTheMic`. |
| RadarGlassW22Test `NFR_13_theQueryLineTheResultsAndTheMicAreSeparateTraversalGroupsReadTopToBottom` | Checks the groups' traversal indices (0, 1, 2) instead of their geometry, which now overlaps. |
| RadarAuditGlassW22UiTest `NFR_13_theTypedOrbitStripPillsAre48dp...` | "The mic is not inside a results group" is now a tree check (no big traversal-group ancestor) instead of a geometry check. |
| RadarShortcutFormW25UiTest `PAL_03_flatShortcutRowDoesNotOverlap_*` | A Flat row may pass under the mic (it is an overlay and the visible part stays tappable); the pair row x mic is skipped. |
