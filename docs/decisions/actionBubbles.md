# App shortcuts as action bubbles, no "+N" (wave 49)

Owner (screenshot of the Orbit, query "gpa"): the GPay bubble showed its app shortcuts only as one grey "+4" circle. "The sub options for the actions suggestions show as a '+4'
format, I'd rather have the actions suggestions up front as action bubbles."

Cause: the Orbit tile gave the shortcut row about 48 dp, so `ShortcutForm.row` fitted zero circles and the "+N" took the only slot.

## Decision

Standing policy (newly specified behaviour keeps only the owner's option): the "+N" form is deleted with its code (`ShortcutForm.Row/row/smallRowWidth`, `ROW_SHOWN`,
`ShortcutCircleRow`), its string ("%s more shortcuts", file `L10nW25shortcutForm.kt`) and its tests. Every matched shortcut of an app is an **action bubble**
(`ActionBubble`, built by `ShortcutBubble`): the wave 25 circle (24 dp glyph or the shortcut's own icon in a 40 dp circle, 48 dp slot) with the shortcut's title under it
(10 sp, up to two lines, ellipsised), 64 dp wide, the whole bubble one touch target.

- **Orbit**: the bubbles are satellites of their app (`ShortcutSatellites`, pure, dp, deterministic). For each app, best match first, each shortcut takes the first spot found by
  trying distances from the app outward (66 dp, +6 dp steps) and at each distance angles from the ring's outward direction alternating to both sides (10 degree steps; the
  alternation order flips in RTL). A spot must be inside the visible area (12 dp from the sides, below the field, above the strip), clear of the app, of every other result's
  box, of the wheel's text and of the satellites already placed (4 dp air). If the neighbourhood is full the nearest free spot anywhere in the area is taken, and only when the
  area has no free spot at all the one that covers least, so no bubble is ever dropped. A thin line (alpha 0.4) ties each bubble to its app. The spots are computed at the rings'
  rest position and the bubbles keep their offset to the app while the rings turn.
- **Filmstrip**: a wrapping row (three per line, so up to two lines) under the app; the item is as wide as the row and `StripFit.of(height, rowH)` reserves its height.
- **Flat**: a wrapping row under the app's row, aligned with the name, outside the row's tap area.
- Kept: tap runs the shortcut, long press shows the title tooltip, TalkBack reads "App shortcut: %s", ids `nested.<appKey>.<id>`, the preview lists every shortcut,
  the keyboard action list after "Open".
- **Cap** `NestedShortcuts.MAX` 4 to 6: Android allows more than four shortcuts per app but real apps publish three to five; six bubbles are two lines of three or a fan around the
  app. Beyond six they stay ordinary Actions rows (none is lost).
- In the Orbit the bubble labels follow the font size only up to 1.3 (`ORBIT_BUBBLE_FONT_MAX`): there is no room around an app for more. The tooltip and TalkBack carry the full title.

## Limits (honest)

- Orbit with motion on: the rings revolve, the bubbles ride along with their app but are only placed clear of the other results at the rings' rest position, so a revolving tile
  may pass under a bubble. The no-overlap guarantee is the still layout (reduced motion or a pause), which is what the tests check.
- On a small phone (360 dp wide) at font 1.5 or larger with many results, the wheel text and the other rings leave no free room around the app; bubbles then go to the nearest free
  place in the area (a longer line to the app) or, if none, where they cover least. They stay inside the display and none is dropped, but a touch target may overlap another.
- Nothing here is confirmed on a device.
