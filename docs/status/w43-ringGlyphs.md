# Wave 43: ringGlyphs status

Nothing here is device-confirmed. JVM and Robolectric only; screenshots `feature-grid/build/screenshots/w43-ring-*.png` (dark and light, portrait and landscape, left and right corner, at rest and turned one or two segments, one with labels) and `w43-action-glyphs-square.png`.

| ID | Status | Note |
| --- | --- | --- |
| INP-19 | done in JVM and Robolectric | ring glyphs keep one fixed square size (24 dp action, 36 dp app) at the segment's own centre; skipped, not shifted or shrunk, when the box is not wholly inside the window; label at a fixed offset, shown only when it fits |
| CFG-15 | unchanged | the "Show labels under glyphs" toggle keeps its meaning; only the label placement rule changed |
| NFR-13 | unchanged | tap area rule: glyph box visible OR 10 dp at mid radius |

## Tests
`PuckSectionsGeometryTest`: replaced the two wave 38 glyph-shifting tests (`...glyphIsCentredOnTheMiddleOfItsVisibleRun` became a note on the name pill's anchor, `...glyphIsAlwaysWholeInsideTheWindow...CarriesItOnThePartThatShows` deleted: it asserted the behaviour the owner removed); added INP_19_w43 tests for centre = mid point at every rotation, constant position/size across windows, no glyph box across the edge, rotation brings a hidden glyph into view, tap-target rule, label rule. `PuckSectionsUiTest`: helpers pass the glyph size; added pixel tests (cut glyph has no ink, turning brings it in; same ink size at every rotation; every action glyph square, inked and unclipped; source has no non-uniform scale; app icon square at 36 dp; label offset) and the w43 screenshots.

## Device-only
How it looks on the phone at the owner's density; the feel of glyphs leaving and entering at the edge while a finger turns the ring.
