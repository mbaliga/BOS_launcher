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

## Not done
- The flare:corner ratio was **not** recalibrated to the reference's measured ~1:1 split (the current code is closer to 2:1,
  28:14). The pixel trace's exact dp mapping from a cropped, unknown-DPI reference image is too uncertain to retune with
  confidence; the change made here (eliminating the straight-run seam) is the part the measured data supports without
  reservation - a single smooth curve with no jump, on any device.
- No device screenshot of either shape: this is traced against the reference image and verified geometrically
  (`EdgeBulgeShapeW60Test.kt`), not confirmed by eye on a phone.
- Whether `WaitingIndicatorView` is in fact the UI element the owner meant by "the thing that hosts notifications" is a reasoned
  call, not a certainty: it is the left-edge, flush-to-x=0 indicator whose own docstring already says it "hugs the physical left
  edge", which best matches the reference image's positioning, but no second screenshot confirms it.
