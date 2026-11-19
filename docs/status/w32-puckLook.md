# Wave 32: puckLook status

Nothing here is device-confirmed. Robolectric screenshots (`feature-grid/build/screenshots/w32-puck-*.png`: dark and light, portrait and landscape, left and right corner, empty and app slot, pressed) were read and compared with the Concepts crop.

| ID | Status | Note |
| --- | --- | --- |
| CFG-15 | done in JVM and Robolectric | solid ring, hairline dividers, black or white glyphs, pressed, empty plus, unavailable, focus outline |
| INP-19 | partly | ring far deeper than 48 dp; wedge arc 30 dp at mid radius, 40 to 47 dp at the outer edge (geometry limit of four wedges in a quarter turn) |
| NFR-13 / NFR-14 | done | glyph contrast at least 4.5:1 (`PuckLookTest`), pressed fill instant under reduced motion |

## Matches the reference
White solid ring against a black hub with no gap, hairline dividers, black glyphs centred in each wedge, clean outer arc, labels outside. Light theme inverts it as specified.

## Does not match / device-only
- Ring is a quarter turn with flat ends on the quadrant edges and is much deeper than the reference (0.9 x hub radius); the hub circle protrudes past the flat edge.
- App icons are about 18 dp (60 percent of the wedge's smaller dimension).
- Keyboard focus outline is covered by code and a pixel check that nothing else draws; focusing a wedge was not driven in a test. No device run.

## Tests changed
`PuckSectionsGeometryTest`: the gap tests (1 dp gap, trimmed wedge angles) replaced by touching equal wedges; the radii-ordered test allows inner equal to the hub. `PuckSectionsUiTest`: screenshot names are now `w32-puck-*`. New: `PuckLookTest`, ring/dividers/glyph centre/app size geometry tests, dark and light pixel tests, pressed, reduced motion, empty plus.
