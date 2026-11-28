# edgeBulgeShape (wave 60): tracing the owner's reference image exactly

Source: the owner's own words, with a reference image attached — *"Observe the shape of the bulged things that host the
notifications and the music player - please trace the shape and make sure it's exactly right."* The image was a flat dark-red
background with two lighter-red silhouettes: one flush with the top edge (the Glance music-controls notch), one flush with the
left edge (the Launchpad "waiting" indicator).

## What the image showed

Pixel-traced (BFS connected-component bounding boxes, then a per-column/per-row depth profile for each silhouette): both shapes
rose from their flush edge in a single smooth hump to a flat plateau, with **no jump anywhere along the curve**. Both profiles
were numerically close at matching relative offsets once rotated 90 degrees against each other, strongly suggesting one shared
construction, not two independently-drawn shapes.

## What the code actually drew

`GlanceNotch`/`NotchShape` (feature-grid) already builds the top notch from exactly this kind of construction - a concave
"shoulder" cubic that leaves the flush edge tangentially and arrives vertically at a straight side, then a convex corner cubic -
but the live draw call (`GlanceHome.kt`) passed a **fixed** `SHOULDER_DP = 26f` as the shoulder's height, regardless of the
notch's actual total height `h = cutoutTop + 48dp` (device-dependent, commonly much taller than `26 + CORNER_DP` once a real
status-bar/camera cutout is added in). Whenever `h > SHOULDER_DP + CORNER_DP`, the shoulder curve's end point (y = 26) and the
corner curve's start point (y = h - 14) disagreed, and the path connected them with a **straight vertical line** - a visible
kink the reference image's single smooth hump does not have. (`EdgeBulgeShapeW60Test.kt`,
`W60_theOldFixedShoulderWouldHaveLeftAVisibleSeamOnARealCutout`/`...LandsExactlyWhereTheCornersStraightRunWouldStart`, pins the
size of that jump on a realistic 80dp-tall notch: 40dp, nowhere close to a rounding error.)

The left-edge "waiting" indicator (`WaitingIndicator.kt`'s `WaitingIndicatorView`) did not attempt the flare-into-edge
construction at all: it used a plain `RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)`, a basic two-corner rounded rect with
no melt into the edge.

## Decision: one shared shape, fix the seam, reuse it on the left edge

1. **`NotchShape.path(w, h, f, c, hs)`'s `hs` parameter now defaults to `h - c`** instead of the caller choosing an independent
   value. The shoulder curve then always lands exactly where the corner's straight run starts, so there is never a seam, for any
   `h`. `GlanceHome.kt`'s draw call (and its hairline-fade-end calculation, which depended on the same shoulder height) now use
   this default instead of `GlanceNotch.SHOULDER_DP`; the constant is kept, documented as legacy, only because the existing
   `GlanceNotchShapeW25Test` still exercises `shoulderAt`/`shoulderPoints` directly with an explicit shoulder height.
2. **The shape itself moved from feature-grid to `core-design`** (`EdgeBulgeShape.kt`: `NotchShape` + the new `EdgeBulgeShape`),
   the one module both feature-grid and feature-launchpad already depend on - rather than adding a new feature-grid dependency
   to feature-launchpad for one shape class. `NotchShape.edgePath(l, d, f, c, hs)` is `path`'s body with every `(x, y)` swapped
   (a literal transpose, not an independently re-derived formula), so the vertical-edge twin can never drift from the top notch's
   curve by construction - matching the reference image's own near-identical profiles.
3. **`WaitingIndicatorView`'s non-quiet background is now `EdgeBulgeShape(flare = 14f, corner = 8f)`** instead of the plain
   rounded rect, melting into the left edge the same way the notch melts into the top edge. The flare/corner are scaled down from
   the notch's own 28/14 dp (tuned for a ~48-88 dp tall bar) to suit this badge's much shorter height (`heightIn(min = 36.dp)`);
   the start padding was widened from 4dp to 10dp so the count text clears the deeper flare near the badge's own top/bottom ends.

Not changed: the quiet (dot-only) form of the waiting indicator, the corner "peek widget" (`WidgetHosting.kt`, corner-anchored,
not a mid-edge bulge, and a weaker match for the reference's flush-at-x=0 criterion), and the music-reliability and Settings-flake
work landing in the same wave (separate agents, separate files).

## Not done (as of the pixel-traced version above)
- The flare:corner ratio was **not** recalibrated to the reference's measured ~1:1 split (the current code is closer to 2:1,
  28:14). The pixel trace's exact dp mapping from a cropped, unknown-DPI reference image is too uncertain to retune with
  confidence; the change made here (eliminating the straight-run seam) is the part the measured data supports without
  reservation - a single smooth curve with no jump, on any device.
- No device screenshot of either shape: this is traced against the reference image and verified geometrically
  (`EdgeBulgeShapeW60Test.kt`), not confirmed by eye on a phone.
- Whether `WaitingIndicatorView` is in fact the UI element the owner meant by "the thing that hosts notifications" is a reasoned
  call, not a certainty: it is the left-edge, flush-to-x=0 indicator whose own docstring already says it "hugs the physical left
  edge", which best matches the reference image's positioning, but no second screenshot confirms it.

## Revision: the owner supplied the actual design source (`Music Controls.svg` / `Notification bar.svg`)

The owner followed up with a `Bulge_thingies.zip` containing the two components' real exported vector paths - the ground truth
the pixel trace above was only approximating. Parsing both confirmed the earlier guess architecturally (`Notification bar.svg` is
`Music Controls.svg`'s exact path with every `(x, y)` control point swapped, matching `path`/`edgePath`'s transpose relationship
byte-for-byte at matching offsets) but revealed the **curve itself** was still wrong in detail: the real curve is not a 2-piece
"shoulder, then a separate circular corner" (what `path()` built, even after the `hs = h - c` fix) - it is **one continuous
8-segment cubic spline** that leaves the flush edge with a horizontal tangent and arrives at the flat body with a horizontal
tangent too, with no intermediate vertical-tangent waypoint.

**Decision: replace the shoulder+corner construction with the traced spline, normalized and scaled to fit the existing reach/depth
budget.** `NotchShape`'s `shoulderPoints`/`shoulderAt`/the old `path()` are gone; a private `FLARE_SPLINE` (the 8 segments'
control points, normalized to a 0..1 reach/depth unit square) and a `flareSpline()` builder replace them, used by both `path()`
and `edgePath()` (still an exact transpose of each other - `flareSpline`'s `reachX/reachY/depthX/depthY` pair just swaps which
real axis plays which role). Verified by reconstructing the exact reference curve in Python from the parsed SVG and rendering it
pixel-for-pixel against the original `Music Controls.svg` (Playwright screenshot, overlaid) before porting it to Kotlin - the two
are visually indistinguishable at the reference's own 366x67 scale.

The reference's own absolute proportions (reach ≈ 2.15x depth) are **not** used directly: at that ratio, `FLARE_DP`'s existing
28dp (and even a much larger value) would eat far more than half the notch's own `MIN_WIDTH_DP` body, breaking the existing
"three 48dp targets fit the body" contract. The curve's *character* (continuous, no straight run, horizontal at both ends) is
preserved exactly; its reach is still bounded by `FLARE_DP + CORNER_DP` (42dp, unchanged) and its depth is always the real `h` -
a deliberate choice to keep the existing structural/layout guarantees rather than the reference's literal aspect ratio.

**New bug found and fixed while verifying at production sizes:** the Launchpad waiting indicator's badge can be as short as its
own `heightIn(min = 36.dp)`. With `reach = flare + corner` uneclamped, a badge shorter than `2 * reach` makes the two mirrored
curves cross into a pointed, arrow-like shape instead of a bulge (caught by rendering the real sizes with Playwright, not by the
unit tests alone, which only checked bounds). `path()`/`edgePath()` now clamp `reach` to at most half the available width/length.
The badge's own `flare`/`corner` were also tuned down from 14/8 to **10/6dp** so the common case stays clear of the clamp
entirely and reads less "pinched" even at the 36dp minimum (still a compressed, pointier bulge than at a taller badge height -
an inherent trade-off of a wide, gentle reference curve squeezed into a short badge, not a bug).

Verified: `EdgeBulgeShapeW60Test.kt` (the no-crossing clamp, the transpose relationship, reach/depth reaching exactly `h`/`w`) and
a new `EdgeBulgeShapeShotW60Test.kt` (real Compose+Robolectric `PixelCopy` screenshots of both shapes at realistic sizes,
`build/screenshots/w60_shape_*.png`, read and judged - the top notch and the taller waiting-indicator badge both read as clean,
seamless bulges matching the reference image's silhouettes; the 36dp-minimum badge is visibly more compressed/pointed, the
honest trade-off above).

### Not done (this revision)
- The reference's literal reach:depth aspect ratio is still not reproduced (see above - a deliberate, not an oversight).
- No device screenshot; verification is Robolectric `PixelCopy` screenshots and the Playwright-rendered SVG comparison, not a
  real phone.
- The 36dp-minimum badge still looks more pointed than the taller states; a further redesign (e.g. a higher minimum height, or a
  badge-specific curve) was not attempted.
