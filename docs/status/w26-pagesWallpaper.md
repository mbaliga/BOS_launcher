# Wave 26 pagesWallpaper (owner clause 10)

| ID | Status | Note |
|----|--------|------|
| HOM-16 | done (Robolectric only) | Wallpaper per surface: Glance and Pages, switch "Pages use the same wallpaper as Glance" (default on); each solid, gradient or live. Tests: WallpaperModelTest, WallpaperPaneUiTest, WallpaperSurfaceRenderTest, WallpaperRowsW26Test |
| HOM-16 bug | fixed | Live wallpaper picker was only under Look > Theme; now in Home screen settings for Glance and Pages, test fails on the old layout |
| CFG-03 | done | Existing bgSlot1/2 become the Glance wallpaper, same-as-Glance on; no schema bump (additive keys in launcher_prefs); settings export/import round trip tested |
| PAL-24 | unchanged | two-slot signal machinery, SignalViews registry and the one clock reused for both surfaces |
| NFR-14 | ok | no new infinite animation; live preview still under reduced motion |

Old tests changed: none removed. Device-only: look and feel of the gradient/solid on a phone, crossfade between different wallpapers.

Old tests changed (wave 26): NfrWiringTest.NFR_15_homeDrawsTheScrimBehindTheTextLayer and RadarSignalLabWiringTest.PAL_24_theBackgroundPane... asserted the old source layout (scrim drawn in HomeScreen, one BackgroundPane call); they now assert the HomeSurfaceBackground seam and the Glance and Pages pane calls. The quick sheet Background button now opens Home screen (where the wallpaper rows are).
