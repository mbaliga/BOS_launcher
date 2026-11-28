# dockMagnifyBunch (wave 61): "the icons currently bunch into the corners"

Source: the owner's screen recording of the dock during the new press-drag-release magnify gesture (wave 60): the icons visibly bunch
together near one edge or the other, overlapping, leaving most of the dock's own shelf empty.

## Investigation

Three real bugs were found while chasing this, each confirmed independently rather than assumed:

1. **`MIN_PITCH_FRACTION` was documented but never enforced.** `DockMagnify.compute()`'s own doc comment (from the wave 60 fix) said the
   containment bisection "never contracts closer than `MIN_PITCH_FRACTION` of the resting pitch" — but the actual bisection ran `lam` all
   the way down to `0` (every centre exactly on the pointer) whenever the row didn't fit at full growth, with no floor at all. Confirmed by
   reading the code directly: the constant was declared, referenced only in a comment, and never read anywhere in `compute()`.
2. **The slack/head padding reservation lagged the actual touch by a frame or more.** `DockRail.kt` read whether the dock was "engaged"
   (and therefore owed extra room for the spread) through `var engaged by remember {...}` updated via
   `LaunchedEffect(field) { snapshotFlow { field.engaged }.collect { engaged = it } }` — a coroutine hop. `DockMagnify.compute()`'s own
   `lo`/`hi` (the room it is allowed to use) is driven by the SAME padding, so on the first frame(s) of a touch, `compute()` could still see
   the narrower, unpadded room even though the touch had already started, pushing it toward the worst-case contraction unnecessarily.
3. **A pointer already outside the dock's own room could still reach `compute()`.** `trackMagnify` wrote the raw touch position into
   `field.pointer` *before* checking whether that position had left the dock's own bounds (`field.insideBounds`) — so the one event that
   trips the cancel check was *also* the one event whose raw coordinate got recorded, for at least the one frame before `pointerDown`
   turned false and the spring-back animation began. `DockMagnify.compute()` is only ever proven correct for a pointer inside
   `[lo, hi]` (`DockMagnifyFixW60Test`'s own documented precondition) — feeding it one outside that range is exactly the "every icon
   collapses onto the pointer" shape the recording shows, and reproducing a deliberately out-of-range pointer directly against
   `DockMagnify.compute()` (not the UI, the pure function) reproduced it with nothing else involved.

A fourth, narrower case was found and reproduced directly against a tightly-packed dock whose pointer reaches the exact edge of its own
room (`lo` or `hi`): the touched icon's own full-grown half-width alone (independent of any contraction) can exceed the room on that
side, which no amount of spacing contraction can fix. This is the one case `DockMagnifyFixW60Test` already documents as out of its own
scope ("a pointer closer to the edge than its own icon's half-width ... a different, un-fixable problem") and genuinely cannot be fixed
without either shrinking the touched icon's own growth there or widening the reserved slack further — left as-is, see "Not done" below.

## Decision: three targeted fixes, in priority order that favours keeping full growth

1. `DockMagnify.compute()`'s containment now tries, in order: (a) full growth with spacing no tighter than `MIN_PITCH_FRACTION` of the
   resting pitch (the common case, unaffected by this change), (b) full growth with spacing squeezed further than that floor if needed
   (important for a dock that already scrolls at rest, where the floor alone often cannot make the touched icon fit at all — tested by
   the existing `DockMagnifyUiW56Test` 10-icon case, which this fix was checked against directly after an earlier version of it broke
   that test by preferring the floor over growth), and only as the last resort (c) reducing growth itself, exactly as wave 60 already did.
2. `DockRail.kt` reads `field.engaged` directly (a plain property read in composition) instead of through the `LaunchedEffect`/
   `snapshotFlow` collector, so the slack/head padding — and therefore `compute()`'s own room — updates in the same frame a touch starts,
   not a frame or more later.
3. `trackMagnify` clamps the touch position to `[field.lo, field.hi]` before ever assigning it to `field.pointer`, so `compute()` never
   sees a pointer outside the room it was told to work within, even for the one frame before a cancel takes effect.

## Verification

- `DockMagnifyFixW60Test` (existing): still green — the no-clipping stress test and the owner's original six-icon scenario both still
  hold with all three fixes applied.
- `DockMagnifyUiW56Test` (existing): still green, including the 10-icon overflowing-dock case that an earlier (floor-first) version of
  fix 1 broke — caught by running the full suite, not assumed.
- `DockMagnifyDragRepro61Test` (new): a continuous press-drag along a dock with real slack at rest (matching the owner's own resting
  screenshot, not already scrolling) never puts two icons' bounds on top of each other at any point along the drag. Before these fixes,
  an early attempt at this same test (addressing `rule.onRoot()` instead of the originally-pressed node, which turned out not to deliver
  the drag at all — caught by a separate pixel-level screenshot check showing no visible change across the whole gesture, so that
  version would have passed vacuously) was discarded once that was understood; the kept version uses the same node-continuation pattern
  `DockGestureW60Test` already relies on.
- A direct, UI-free probe against `DockMagnify.compute()` itself (not shipped, used only to confirm the root cause) showed the pure
  function already degrades gracefully (shrinks `k` toward 0 rather than collapsing) even with zero slack and the pointer clamped to the
  exact edge — confirming the bug was in the Compose-side wiring (fixes 2 and 3), not in the geometry math itself (fix 1 is a genuine
  improvement to the math's own documented contract, but was not by itself sufficient to explain the recording).

## Not done

- The exact-edge case (fourth finding above) is unfixed: a pointer that reaches `lo`/`hi` precisely, on a dock whose touched icon's own
  full-grown half-width alone exceeds the remaining room there, still falls back to reduced growth. This matches the wave 60 fix's own
  documented scope boundary, not a new gap.
- No device confirmation: everything here is verified via Robolectric/Compose UI tests and a pure-math probe, not a real touch on a real
  phone. The owner's own retest of the next build is the actual confirmation this is fixed.
- The owner's screen recording also showed a system toast ("Claude, used this session") appearing near where the icons bunched,
  suggesting the finger may have drifted toward a screen corner during the drag (matching the reproduced scenario: a drag that reaches
  the dock's own edge) — this is a plausible read of the recording, not a confirmed one.
