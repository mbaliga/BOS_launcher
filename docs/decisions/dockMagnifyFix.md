# Dock magnify: no clipping, ever; press-drag-release (wave 60)

Supersedes part of [dockMagnify.md](dockMagnify.md) (wave 56): that note's "a dock that already scrolls just spreads: the viewport clips it" and
"the part of a pushed-out icon beyond the rail bounds relies on the added slack/headroom for touches" no longer hold. The owner's screenshot
(`dockmagnify-crop-issue.jpg`) showed a six-item, non-scrolling dock with its leftmost and rightmost icons bleeding past the shelf once magnified —
proof that "the viewport clips it" was never actually true for the dock's own drawn background: a clip only discards a GPU layer's pixels at a hard
edge, it does not stop an icon rendering past the dock's own rounded pill, which is drawn in the same layer tree, not as a clip boundary around it.

## Root causes found

Two, both in the real composition tree, neither a flaw in the kernel maths itself:

1. **`DockMagnify.compute()` only enforced `[lo, hi]` containment `if (!overflow)`.** A row whose rest layout already exceeded its room (a
   scrolling dock, or — critically — a row that is momentarily measured as too small for its own content, see (2)) skipped the containment step
   entirely: the spread was then free to extend however far the maths said, unclamped. The owner's six-item dock is not a scrolling dock at all;
   it hit this path only because of (2).
2. **`MagnifyField.lo`/`hi` lagged the rail's real measured size by one or more frames.** They were set only from a `SideEffect` reading the
   `railSize` state var — and `railSize` itself is only ever updated by the rail's own `onGloballyPositioned` callback. When touching the dock
   first grows its own box (the padding that reserves slack and headroom, `DockRail`'s `padded`/`slackDp`/`headDp`), that growth is a new
   measured size reported through `onGloballyPositioned`, but nothing then guarantees `DockRail` recomposes again soon enough to re-run the
   `SideEffect` that copies that size into `field.lo`/`field.hi`. In the reproduction built for this wave, the rail's own box genuinely grew from
   600 px to 758 px (confirmed by instrumenting `railSize`), but `field.hi` stayed at 600 px for every subsequent frame of the touch — so
   `compute()` was handed a *smaller* room than the rail had actually grown to, and (1) above then let the spread run past even that stale,
   too-small bound with nothing reining it in. This is investigation (a) of the wave 60 brief, confirmed: the rail's own parent (`DockLayer`'s
   `modifier = { edge -> ... }`) does give the rail genuine room to grow (the 20 dp vs. 76 dp margins already existed for this reason), and the
   rail's own `Modifier.layout` passes that room through to `availMain` unmodified; the break was downstream, in how the rail's *own* grown size
   reached the kernel's `lo`/`hi`.

Investigation (b) (whether the background surface's `graphicsLayer` scale could disagree with the icon layer's geometry) turned up no separate
bug: both already read the same `MagnifyField.Result` fields (`baseStart`/`baseEnd`/`start`/`end`). Once (1) and (2) above are fixed, the two
layers agree by construction. The surface's growth math is now pulled out into a pure function, `DockMagnify.surfaceGrowth`, so this stays true
by sharing code, not by two independent formulas that happen to match today.

## The fix

- `compute()` now always tries to keep the spread inside `[lo, hi]`, regardless of `overflow`. It still prefers **contracting the spacing around
  the pointer** first (bisected exactly, not the old single-shot formula, so it finds the least compression that actually fits — unbounded below
  the old 0.72-of-pitch floor if that is still not enough), and only **reduces the growth itself** ("every icon fits, some grow less") when even
  full collapse (every centre landing on the pointer) at the current growth still will not fit. Both searches are bisections over a monotonic
  predicate, so they always converge on something inside the room — proved by `DockMagnifyFixW60Test`'s stress matrix (every N from 2 to 12,
  every strength, a pointer sweep across the row, and room swept from generous down to tighter than the resting row itself), not merely asserted.
- `DockRail`'s rail-bounds `onGloballyPositioned` callback now also sets `field.lo`/`field.hi` directly from its own `c.size`, unconditionally
  (not only for the Settings-preview `demo` case, as before) — the same callback that already updates `railSize`, so the kernel's room is always
  exactly what was just measured, with no extra round trip through recomposition that can lag behind a touch-driven relayout.
- A declared, honest limit: the pointer itself must be able to hold its own fully-grown icon without crossing `lo`/`hi` — i.e. no closer to either
  edge than that icon's resting half-width. The real rail guarantees this by construction (the slack it reserves while touched,
  `DockMagnify.slackDp`, is sized from the strength precisely so the touch point is never that close to the edge); a room literally narrower than
  one icon, or a touch point placed closer to the edge than its own icon's half-width, is a different, unfixable problem (the touch point itself
  is off the usable strip), not the clipping bug the owner reported, and is out of scope for this invariant.

## Press-drag-release (replacing the per-item clickable while magnify is on)

The owner: *"finger on the icons strip... either I can have my finger exit the interactive area without lifting it, which cancels the command, or
if I lift the finger with an app in focus, then that app should open."* Implemented as one extension to the existing `trackMagnify` pointer-input
loop (not a second, independent `awaitPointerEventScope`), sharing its state through three new `MagnifyField` fields (`gestureActive`,
`insideBounds`, `onGestureRelease`) that `DockRail` refreshes every recomposition via the same `SideEffect` that already refreshes the field's
other per-frame inputs — a sibling detector racing the same event stream non-destructively (as `trackMagnify` already does with the rail's
long-press and drag detectors) would have needed to duplicate the slop/hold bookkeeping and risked disagreeing with it about what counts as a
drag; extending the one loop keeps a single source of truth and needed no change to its existing, heavily-tested pointer-reading contract (the
public surface `DockRail` and `DockHost` see is unchanged).

- **Interactive bounds**: the rail's own measured bounds (`[0, railSize]` on each axis, in the same viewport-pixel frame `MagnifyField.pointer`
  already uses), widened by an 18 dp forgiving margin on every side. The margin exists so the ordinary jitter of a real finger — a few px past
  the rail's own hit-tested edge, well under a full slot — is not punished; a drag that goes meaningfully away from the dock (up off a bottom
  dock, say) still cancels promptly. Chosen as a fixed constant rather than, say, a fraction of a slot: it reads more predictably and the
  existing slack/headroom already scale with strength, so the cancel margin did not need to.
- Cancellation is immediate and sticky for that one physical press: the moment the finger (or a mouse button held down) leaves the bounds, the
  tracker forces `pointerDown`/`magnifyTouch` off right there (so the spread's target drops and it springs back at once, not waiting for a
  release that may never come back inside), and ignores every further move of that same press — even one that re-enters the bounds — until it
  actually lifts. That matches a dock-style drag-to-launch: once cancelled, it stays cancelled for that press.
- On an uncancelled release, `DockRail` hit-tests the **release point against the current (possibly magnified) geometry**
  (`MagnifyField.Result.indexAt`), not the point where the press started — the whole point of press-anywhere-drag-release-to-a-different-icon.
  For a quick plain tap (no slop exceeded), the geometry at release is still essentially at rest (the 120 ms/slop gate that already protects a
  plain tap from ever seeing the magnify effect is unchanged), so the release point naturally resolves to the pressed icon, matching today's
  behaviour with no special-casing.
- Scope: this replaces the DockSlot's own `clickable`/`combinedClickable` **launch** behaviour only, and only for pinned/recent icon slots, only
  while `magnifyOn` (the setting is on and the dock is not being edited). The long-press (menu) and keyboard/D-pad/Enter paths are untouched; the
  Home glyph and the usage-enable button keep their own existing gestures (workspace-switch drag, direct tap) — they are distinct controls with
  their own already-tested behaviour, not "icons" in the sense the owner's text describes, so folding them into this gesture was out of scope.
  The long-press-opens-menu race (a stationary press that both exceeds the gesture's own slop/hold timer, engaging magnify, and then crosses the
  separate long-press timeout) is resolved by having the release handler check the menu's own open/closed state before launching anything — a
  menu already open from that same press suppresses the launch.
- Mouse and stylus: the same loop's existing non-touch branch (hover engages immediately, as it always has) now also tracks a
  press/drag/release through that branch's own `Press`/`Release` event types, so the model is identical for a mouse as for a finger.

## Not matched / device-only

- Touch feel, a real mouse or trackpad drag, and a hardware stylus are not run here — only Robolectric-synthesised pointer event sequences.
  `DockGestureW60Test` covers touch and (separately) mouse event sequences, but neither exercises real input hardware or timing.
- The cancel margin (18 dp) is a design choice, not something a device check has validated against how a real finger actually drifts off a dock.
- Screenshots (`DockMagnifyFixShotW60Test`, `feature-dock/build/screenshots/w60-*.png`) are read and judged here against the bug screenshot; they
  are not a device capture.
