# Radar uses the whole screen: no crop line, no dead touch (wave 29, supersedes the region rules of radarGlass.md and shortcutForm.md)

**Owner (build 58, with a screen recording):** "There is still some error with respect to the touch area that works. The rotating rings and icons are getting cropped in
both the portrait and landscape view." The wave 26 decision stands (circular rings, tiles revolve and may leave the screen, "it's fine"). What is not accepted is a
visible crop LINE inside the screen and dead touch areas beside it.

## Cause

The Orbit lived in a box padded to the region between the query bar and the mic bar (or the typed strip) and `clipToBounds`-ed there. A tile crossing the region's top or
bottom was cut by a straight line, and Compose does not deliver a touch to a child outside its parent's bounds, so the part of a tile beyond that line was dead too.
The wide containers beside the controls (the mic bar, the strip's scrolling row) were also full width.

## Rules now

1. **The Orbit is a layer of its own over the whole display area**: edge to edge horizontally, from the top edge (under the status bar and cutout) to the top of the
   keyboard (`imePadding`) or the bottom edge when it is down (`RADAR_ORBIT_TAG`, `SearchOverlay.kt`). The rings stay centred where they were (the middle of the category
   wheel): the region under the field is still measured (`onGloballyPositioned`, `OrbitGeo`) and the Orbit takes its centre and size from it; nothing moves.
   The only clip is that layer's own bounds, the true screen or keyboard edge.
2. **Everything else is an overlay above it** (composition order): the field with Clear/Close, the mic, the typed-query strip circles, the category wheel and "SEARCH".
   Each blocks only its own bounds. The mic bar Box, the query bar Row and the strip's scrolling row have no pointer handling of their own; the strip Row is as wide as its
   circles (it scrolls when they do not fit) instead of `fillMaxWidth`.
3. **Empty space** keeps the wave 25 behaviour: a tap on it closes Radar when the query is empty, and puts the keyboard away when something is typed.
4. **Moving rings** revolve over the whole layer; a tile partly under the field or the mic is drawn and takes taps on its visible part. **Still rings** (reduced motion /
   the motion setting off) are fitted to the old region (below the field, above the mic or the typed strip, clear of the wheel text), so nothing is hidden by an overlay there.
5. **Filmstrip and Flat**: the results area has no clipped box. The Flat list fills the area and scrolls under the field and above the mic with its own
   `contentPadding` (field height on top, mic strip at the bottom); the Filmstrip's tiles are fitted to its free height (`StripFit`) and it no longer clips.

Test seam: the rings' tiles carry `RADAR_ORBIT_TILE_TAG` (the wave 25 tests looked for this tag, which did not exist, so they checked nothing; they check now).

Not device-verified: all of this is Robolectric (pointer injection through the real `SearchScreen`) and read screenshots; the keyboard animation is faked with insets.
