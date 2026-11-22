# Fixed home backdrop (wave 55, stream w55-fixedBackdrop)

Owner feedback (verbatim): "When I scroll between pages, the background also moves, it shouldn't."

## What was wrong (found in the screen recording and in the code)

Frames of `pages.mp4` taken at 10 per second show the Signal wallpaper (beam, dotted grid, glow) sliding with the page during a Glance to page 1 swipe: in the frames between the settled ones the beam is cut off at the left edge together with the clock while the next page's beam enters from the right. In the code, `HomeScreen` drew the wallpaper INSIDE each pager page: the Glance page passed it as `GlanceSurface(underlay = ...)` (and `GlanceSurface` painted its own opaque base), and every pages stop called `Background()` as its first child (paged grid, continuous scroll, PSP, DSi, List). A pager translates its pages, so each page carried its own copy of the wallpaper. There was no parallax code; the whole cause was placement.

## What changed

- `FixedHomeBackdrop` (feature-grid) draws the wallpaper (through the unchanged `HomeSurfaceBackground` seam, so Solid, Gradient, Signal, System live, Picture/GIF/video) and, as its overlay, the indicator (calm/alert) effects ONCE. `HomeScreen` places it as the first child of the home box, BELOW the pager, the edit-mode page scale (`editCardLayer`) and the Comms/Recents card (`homeCardLayer`), so none of those transforms touch it. The per-page `Background()` calls and the Glance underlay are gone; the Glance page is transparent (`GlanceSurface(transparentBase = true)`).
- Same wallpaper on both surfaces (the default): one instance, nothing changes on a swipe. Different Pages wallpaper: the Glance wallpaper is the base and the Pages wallpaper fades in over it with the pager position, `pagesAlpha = (currentPage + currentPageOffsetFraction)` clamped to 0..1 (1 when Glance is off), applied in a `graphicsLayer` lambda; the mount/unmount of each layer is `derivedStateOf` on a threshold, so only the settled end states recompose and a swipe does not recompose the backdrop. At rest only one wallpaper is composed (one live wallpaper draws at a time, as before); both exist only during the swipe. The alpha follows the logical page index, so RTL order (`reverseLayout`) and Continuous (one pager stop) behave the same.
- Wallpaper dim for Radar and Comms is unchanged (Radar draws its own through `RadarDim`).
- System live wallpaper: the layer draws nothing and the Glance page no longer paints a base, so the window's wallpaper shows as before.
- Preview dock: it records the home card; the fixed wallpaper is now recorded on its own layer (`captureWallpaper`) and drawn first, so the preview still shows the wallpaper.

## The one exception, on purpose

While Comms or Recents is open or dragging, the home is a shrunken CARD over an opaque panel. The card keeps a still copy of the wallpaper as its face (`animate = false`, no scrim animation), because otherwise the card would be transparent over the panel. That copy is part of the card and moves with it; the fixed backdrop itself never moves. The panel covers the backdrop; it may dim it, never moves it.

## Edit mode

The 0.88 page scale applies to the page content only; the wallpaper stays full size behind it. Where the scaled page leaves a margin, `EditChromeBackdrop` (unchanged) paints its gradient and scrim over the wallpaper as before.

## Not matched / device only

- Robolectric proves layout and drawn pixels (bounds of the layer and pixel columns at rest versus mid-swipe), not the swipe feel, frame pacing or GPU cost of two live wallpapers during a cross-fade.
- During a cross-fade between two different Signal wallpapers both draw for the duration of the swipe.
- The edit-mode margin now sits over a full-size wallpaper rather than over a scaled copy; look on a device.
