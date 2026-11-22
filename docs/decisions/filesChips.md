# Files source chips: edge to edge, fade, reorder (wave 54, HOM-15)

Owner request: "The pills are getting cut off sharply here, please make it a [fade] gradient and make it edge to edge. We also need to be able to reorder these, just long press and drag is fine."

## Edge to edge
The 18 dp side margin of the Comms page used to clip the chip row. `HomePanelsBody` no longer pads the Files section; `FilesSection` takes an `inset` (18 dp from the page) and applies it to everything except the chip row. The row spans the full width; its content starts with `inset` padding (the first chip sits at the normal inset at rest) and ends with room for the Add tile. The Add tile is laid over the end side of the row with the same inset (a pill, not clipped) and mirrors in a right-to-left layout.

## Fade
`horizontalEdgeFade` (FilesChipBar.kt) puts the row on an offscreen layer (`CompositingStrategy.Offscreen`) and draws an alpha mask with `BlendMode.DstIn`: 28 dp at the start edge only once the row has scrolled, 28 dp at the end edge only while more is hidden, nothing at an edge with nothing behind it. At the end the fade finishes where the Add tile begins (the "reserve": tile width, inset and gap), so chips scroll under the tile as faded shapes, never hard cut. The state (`none`, `start`, `end`, `both`) is also exposed as a semantics property for tests (not announced). The modifier is generic; the chip row is the only horizontally scrolling chip row in the Files area (the chooser and editor pages are vertical).

## Reorder
- Long press a collection chip or a folder chip: it lifts (scale 1.06, 10 dp shadow, a haptic tick `TextHandleMove`), drag horizontally, neighbours slide aside live (120 ms), the row scrolls when the finger is within 56 dp of an edge (up to 14 dp per frame, linear), drop anywhere commits. A plain swipe still scrolls and a tap still selects (a tap right after a drag is ignored for 350 ms).
- All (first) and the Add tile are fixed. A chip moves inside its own kind: collections among collections, folders among folders (the block order All, collections, folders is kept; this keeps the chooser's list and Move up and Move down in step with the same stored order). The dragged chip is held inside its block.
- The target place is the number of other chips whose centre lies before the dragged chip's centre, so chips of different widths are handled correctly. All arithmetic is in reading-direction coordinates, so right-to-left is the same code with a sign. The math is in `FilesChipReorder` (pure, unit tested).
- Persistence: collections keep their order in `panel.files.collections` (`shown`, as before); the folder order is the new `panel.files.folders.order` (folder URIs, newline separated) in `home_state`, read through TolerantPrefs. `home_state` is already exported with a backup and classified, so no new classification entry. A folder not named in the order follows the named ones; a name with no grant is ignored. The chooser lists folders in the same order.
- Alternatives: TalkBack custom actions "Move earlier" and "Move later" on each movable chip (offered only where the move is possible), and Alt+Left and Alt+Right with the chip focused (the arrows follow the screen, so in right-to-left Alt+Left moves a chip later). The chooser's Move up and Move down from wave 53 stay.
- No cancel by dragging out: drop anywhere commits.

## Rejected
- Interleaving folders among collections: it needs one combined order that the chooser's collection order would then contradict.
- A drag handle: the owner asked for a plain long press.

## Undo
Revert the wave; `panel.files.folders.order` is then an unused key (add it to `RetiredPrefKeys`).
