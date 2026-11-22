# Wave 54: filesChips (HOM-15)

Nothing was run on a device. Verified by JVM unit tests and Robolectric UI tests with a fake file index, and by reading the generated pictures (`app/build/screenshots/files-w54-rest.png`, `-scrolled.png`, `-rtl.png`). The JVM software canvas ignores graphics layers, so the picture is laid over the page colour (the plain surface behind the row) to show what the offscreen mask leaves; the lift (scale, shadow) cannot be seen in any JVM picture and is checked by position and size in a test instead.

| ID | Status | Note |
|---|---|---|
| HOM-15 | built (device untested) | Source chips edge to edge under the full Comms width (first chip at the 18 dp inset at rest, Add tile pinned at the end, RTL mirrored); 28 dp alpha fade (offscreen layer, `DstIn`) at a scroll edge only when more is hidden; long press and drag reorder (lift 1.06 with shadow and haptic tick, neighbours slide, auto-scroll near an edge, drop commits and persists); TalkBack actions Move earlier and Move later; Alt+Left and Alt+Right handler. |

## Tests
- `FilesChipReorderW54Test` (pure): no drag keeps the place, centre crossing with variable chip widths both ways, clamping to the chip's own kind, RTL equals the logical math, carried-chip clamp, neighbour slide amounts, auto-scroll speed (zero in the middle, linear to the maximum, both edges, tiny viewport), move and order helpers, Alt+arrow mapping in LTR and RTL.
- `FilesChipsW54Test` (Robolectric, 22 tests): row bounds equal the page width and the first chip keeps the inset; Add tile stays put while scrolling and keeps the inset; the last chip rests beside the Add tile; RTL mirrors row and tile; fade state `none` when everything fits, `end` at rest with hidden chips, `both` mid-scroll, `start` at the end, back to `end`; long press and drag moves a collection (stored order in `home_state`, source unchanged), left drag, a folder chip (folder order stored, never in front of the collections), All cannot be lifted, tap still selects and a drag does not, a short swipe scrolls instead of dragging, holding at the edge scrolls the row, RTL drags both ways, lifted chip follows the finger (position, 1.06 size, neighbour offset), TalkBack actions offered only where possible and working, saved folder order restored with new folders after, the chooser lists folders in that order, two pictures.
- Helper `FilesChipTestUtil.revealChip`: scrolls a chip into the stretch the Add tile does not cover.

## Changed existing tests
- `FilesCollectionsW53UiTest.tap`: a chip that sits under the faded end or the Add tile is scrolled into view first (before, the Add tile sat beside the row so every chip was reachable). No assertion changed.

## Not matched, decisions to know
- A chip moves among its own kind (collections among collections, folders among folders), not across; the chooser's order stays one with the chips (see the decision note).
- Alt+Left and Alt+Right: the key mapping is unit tested and the handler is attached to each chip, but no Robolectric test drives it through real focus (the Compose focus request did not take in the test harness), so the focus path is device-only.
- Drag by long press cannot be tested for feel; the touch slop on the JVM made carried distances a little shorter than injected, so tests use margins.
- The chips under the end fade and the Add tile cannot be tapped until scrolled into the free stretch; selecting a source scrolls its chip into that stretch.

## Device-only checks
Drag feel (lift, slide, drop), the haptic tick, auto-scroll speed at the edges, fade look on the real renderer and on a light page, TalkBack custom actions menu and announcements, a hardware keyboard with Alt+arrows, RTL with a real language, one-handed reach to the Add tile, a long list of folder chips.
