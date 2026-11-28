# w30 radarDim status (nothing here is device-confirmed)

| ID | status | note |
|---|---|---|
| PAL-24 | built | Radar background setting (Dimmed wallpaper default, True black); dim maths `RadarDim`, layer `RadarBackgroundLayer`. Tests: `RadarDimMathW30Test` (JVM), `RadarBackgroundW30Test` (default, persistence, fallback), `RadarDimW30UiTest` (default, no background node and black pixels in True black, every layout and orientation, every item x level label contrast, quarter-brightness bound), `RadarDimWiringW30Test` (settings row) |
| PAL-18 | built | wheel labels measured readable over every wallpaper item at signal levels 0 to 4 |
| NFR-14 | built | fade-in on open uses the motion prefs (none under reduced motion); True black stops the signal glow work |

Measured: worst selected-label contrast about 16:1, worst faded-neighbour (white 35 percent) 3.01:1, brightest background pixel behind the wheel labels 0.0018 luminance. Screenshots: `feature-grid/build/screenshots/radardim-*.png`, read and compared with the owner's screenshot (labels clear, faint Spire).

Device-only: how the dimmed Spire looks on a real panel, and a real keyboard/IME overlap.

Tests that assumed the full-strength wallpaper in Radar: none needed changing so far (see the final run note below if a failure was fixed).
