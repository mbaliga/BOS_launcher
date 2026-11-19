# Radar dims its wallpaper or goes true black (wave 30; PAL-24, PAL-18, NFR-14)

**Owner (build 59, with an annotated screenshot of Radar over a bright Spire, query "jdk", Files scope):** "The wallpapers must dim or use a true black for the radar background, otherwise radar becomes a poor experience." The bloom of the live wallpaper shone right behind the category wheel; APPS and CONTACTS were barely legible and the glow washed the lower half.

**Supersedes** the parts of radarGlass.md, radarFull.md, backgrounds*.md and pagesWallpaper.md that say Radar shows the live wallpaper (Glance's) at full strength. Radar still uses the Glance's two background items, but through its own layer and its own setting.

## 1. The setting

`LauncherPrefs.radarBackground` (`RadarBackground`, exactly two values): **Dimmed wallpaper** (default) and **True black**. Ground Control > Radar > "Radar background" (a choice row). Stored as the additive string key `radarBackground` in `launcher_prefs` (already classified and exported with the other Radar prefs; an unknown value reads as Dimmed). It applies to Orbit, Filmstrip and Flat, portrait and landscape, empty and typed query.

## 2. Dimmed

`RadarBackgroundLayer` (feature-grid `RadarDim.kt`): the wallpaper (`SignalBackground`, still animating at the dimmed level), then a black layer of 75 percent (`RadarDim.SCRIM`), so no pixel is brighter than a quarter of what the wallpaper made it, then a second black cap of 92 percent (`WHEEL_CAP`) over an ellipse around the wheel row (the "SEARCH" word and the wheel, at most 190 dp either side of the centre line; flat out to 80 percent of its radius, so every corner of the label boxes is under the full cap) because the Spire tip and bloom sit there. Both are solid black with alpha (the cap is one radial brush built once per size by `drawWithCache`): no blur, no per-frame allocation. The layer fades in over 240 ms when Radar opens; under reduced motion it is simply there.

Why a cap and not only a bigger scrim: with the 75 percent scrim alone a pure white bloom is a 25 percent grey, and the faded neighbour labels (white at 35 percent) would only reach 2.4:1 over it. The cap keeps the worst possible pixel behind the labels at 2 percent grey.

**Measured** (`RadarDimW30UiTest`, Robolectric native graphics, portrait 320x414 dp-ish default and landscape 891x411): each of the nine background items alone (Beam, Spire, Halo, Laser, Shaft, Glow lines, Breathing dots, Bottom glow, Radar sweep) at signal levels 0 to 4 is drawn through the real layer, and every pixel in the "SEARCH" and wheel boxes is checked: white at 0.9 alpha (selected) at least 4.5:1 (measured worst case about 16:1), white at 0.35 alpha (faded neighbour, the lowest alpha in use) at least 3:1 (worst case 3.01, which is the limit of 35 percent white on pure black itself), brightest pixel luminance at most 0.0075 (measured 0.0018 at most). No item needed its own, higher scrim. The whole-screen maximum is asserted to be at most 25 percent of full brightness. The files `build/screenshots/radardim-measured-*.txt` list every number.

## 3. True black

Nothing is drawn: no `SignalBackground` is composed, so the glow, its clock and its signal collection do not run (and no signal live region is announced). The screen's own base is `#000000`; the rings, guides, tiles and labels stay.

## 4. Tests that changed

See `docs/status/w30-radarDim.md`.
