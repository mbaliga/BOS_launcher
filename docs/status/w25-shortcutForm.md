# Wave 25, stream shortcutForm (owner feedback on build 53)

Owner text: "Some shortcuts/quicklinks appear as circles floating around the radar circles, others as pills below the icon. Looks weird and feels clunky."

Nothing here is device-confirmed. Verified: JVM unit tests and Robolectric Compose tests (bounds, overlap, screenshots read by eye). Device-only: the look on a real
phone, the long-press tooltip window, TalkBack reading of the circles.

| ID | Status | Note |
| --- | --- | --- |
| PAL-03 | built | An app's shortcuts are one row of up to three 40 dp circles (48 dp slots) plus a "+N" circle under the tile in Orbit, Filmstrip and Flat; the row is part of the tile layout (Orbit boxes and the Filmstrip item reserve it). Pills are deleted. |
| PAL-18 | built | Actions that are not tied to an app, the web hand-offs and Filenames are circles on the typed Orbit's strip, never tiles on the rings (`isStripTile`). Flat keeps list rows for them. |
| PAL-20 | built | Long press on a circle shows its label as a tooltip; "+N" opens the preview, which lists every shortcut with its label. |
| NFR-13 | built | 48 dp touch slots without overlap, content description = label, TalkBack long-click action, the "SEARCH" caption's tap area no longer lies under results. |

## Tests

- `RadarShortcutFormW25UiTest` (real `SearchScreen`, query "fylz", app with four shortcuts, Deep results and settings-page actions): no two interactive nodes overlap in Orbit and
  Flat at 360x640, 411x891, 891x411 and font 1.0 and 2.0 (the category wheel's labels are excluded, they have their own tests); the same for the Filmstrip tile at three windows;
  no action is an `orbitTile`, all sit on the strip as 48 dp slots below the rings; the row maths; tap runs a shortcut, "+N" opens the preview with all; label as description and tooltip.
- Updated: `RadarNestingTest` (Orbit with four shortcuts: two circles and "+2"), `ActionIconsW25Test` (icon checks use the circle).
- Screenshots (read): `feature-grid/build/screenshots/w25-shortcutForm-orbit-fylz.png`, `-orbit-fylz-landscape.png`, `-flat-fylz.png`.

## Not done / limits

- Flat's Actions and Web sections are list rows, not strip circles (a list is its form of every result); say so if one form there is wanted too.
- With the wheel on "Actions" or "The web" the typed Orbit's rings are empty and the strip (scrolls sideways) holds the results.
- The strip circles show no text; the label is the tooltip and the description.
