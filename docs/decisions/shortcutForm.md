# Radar shortcuts, quicklinks and actions: one form (wave 25)

> **Wave 49 (actionBubbles): the "row of three circles plus a +N circle" for an app's shortcuts is gone; see [actionBubbles.md](actionBubbles.md).** The circle itself, the strip for non-app actions and the rest stand.

> **Wave 29 (radarFull): the region rules (the strip and the rings living in a clipped results region) are superseded by [radarFull.md](radarFull.md).** The Orbit is a full-screen layer, nothing is clipped inside the screen, every visible part of a tile is tappable. The rest of this record stands.


Owner (build 53, query "fylz" in Orbit): "Some shortcuts/quicklinks appear as circles floating around the radar circles, others as pills below the icon. Looks weird and
feels clunky." The screenshot showed two or three pill chips stacked and overlapping under the app tile, "Deep results" as a stand-alone tile on the rings and the web
searches as pills along the bottom.

## Decision: circles, one row, one size

- A result is either an **app tile** (icon circle and name) or an **action**. Everything that is a shortcut, quicklink or action has one form, `ActionCircle`: a 24 dp
  glyph (the shortcut's own icon, else the built-in glyph of its `ActionKind`, else the app icon; see actionIcons.md) in a 40 dp circle, inside a 48 dp touch slot. The
  row pitch is the slot, so circles are 8 dp apart and no two touch targets overlap. The label is the content description ("App shortcut: Tools") and, on a long
  press or the TalkBack long-click action, a tooltip above the circle.
- **Shortcuts of an app** are one row of at most three circles plus a "+N" circle (description "N more shortcuts") that opens the existing preview, which lists every
  shortcut with its label. The row is part of the tile's layout: Orbit boxes reserve its width (up to three slots) and 48 dp height (`OrbitFit.fit(rowW, rowH)`, so the
  placement can not overlap any neighbour), the Filmstrip item is as wide as the row and as high as tile + row (`StripFit.of(h, withRow)`, the row is dropped when the room
  is short instead of overlapping), the Flat list puts the row under the app's row, outside its tap area.
- **Actions that are not tied to an app** (an inline answer, "Deep results", "Open settings page", launcher commands, settings, a shortcut whose app did not match) and
  the web hand-offs ("Search DuckDuckGo for ...", the page of a URL, the Play Store lookup) and Filenames are **never tiles on the rings**. In the typed Orbit they are
  circles of the same size on the strip along the bottom of the results region (`ResultStrip`, one scrolling row, 48 dp). `isStripTile` is the one rule; the rings hold
  apps, people, files and the permission asks.
- Flat is a list: its Actions and Web sections stay list rows (leading icon, label), as every other result is a row there. The Filmstrip is the idle layout (typing
  turns it into the list), so it only meets shortcuts in tests and when a category with them is chosen.

## Why circles and not pills

A pill needs its text visible; three pills do not fit under a 56 to 90 dp Orbit tile, so they were stacked and clamped, which is the overlap the owner saw. A circle has a
fixed footprint (48 dp), so the tile can reserve it and no layout has to guess. The cost is that the label is not always visible: long-press tooltip, content description
and the preview (labelled rows) carry it. The strip's web searches are now an engine glyph; the label ("Search DuckDuckGo for ...") is the tooltip. If the owner prefers
visible text on the strip, `ResultStrip` is the one place to change; the app rows would stay circles.

## Side effect

`QueryDisplay` ("SEARCH" beside the wheel) had a tap area as wide and as tall as half the screen, lying under the results. It is now only the word and the visible
height of the wheel, so no result lies under it. Taps beside it are empty space (the existing empty-space action).

## Undo

`ShortcutCircleRow`, `ActionCircle`, `ShortcutPreviewList` (RadarNesting.kt) replace `ShortcutPill`, `NestedShortcutPills`, `NestedShortcutRow`, which are deleted. The ring/strip
split is `SearchData.ringTiles` / `stripTiles` / `isStripTile`.
