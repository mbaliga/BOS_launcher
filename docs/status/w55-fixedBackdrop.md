# W55 fixedBackdrop: the wallpaper does not move with the pager

Owner: "When I scroll between pages, the background also moves, it shouldn't." Nothing here was run on a device. Design: `docs/decisions/fixedBackdrop.md`.

Found: in `pages.mp4` (frames at 10 fps) the Signal beam, grid and glow slide with the page mid-swipe. In code the wallpaper was drawn inside each pager page (Glance `underlay`, plus `Background()` in every pages stop).

| ID | Status | Note |
|---|---|---|
| HOM-16 | built (device untested) | `FixedHomeBackdrop` draws every wallpaper kind and the calm/alert indicator effects once, below the pager, the edit page scale and the Comms/Recents card. Different Pages wallpaper cross-fades with the pager position (alpha in a graphicsLayer lambda). Tests: `FixedHomeBackdropW55Test` (feature-grid: layer bounds and pixel columns equal at rest and mid-swipe for Glance to page, page to page, Continuous inner scroll and RTL; cross-fade mix equal on every column, RTL too; Glance off shows Pages; alpha rule) and `FixedBackdropW55SmokeTest` (real home: same, plus cross-fade layer present mid-swipe). Screenshots read: `app/build/screenshots/w55_glance_rest.png`, `w55_mid_swipe.png`, `w55_page.png`, `w55_crossfade_mid.png` (wallpaper identical, clock and chrome move/stay as designed). |
| HOM-23 | built | The Glance page is transparent (`GlanceSurface(transparentBase)`); clock, gestures unchanged. |
| CFG-18 | built | The preview dock records the fixed wallpaper on its own layer (`captureWallpaper`). |

Changed tests: none needed changing (no test assumed the wallpaper inside a page).

Not matched / device only: swipe feel and GPU cost of two live wallpapers during a cross-fade; the Comms/Recents card keeps a still wallpaper copy as its face (moves with the card, the fixed backdrop does not); edit-mode margins now show over a full-size wallpaper (look on a device). Tablets use the same code path (no size-specific branch); not separately screenshotted.
