# Wave 22: Glance player controls notch

Owner bug: the pop-in music controls were a boxed floating pill with the title and filled glyphs, not the reference notch. Robolectric tests only; nothing was run on a device.

| ID | status | note |
|---|---|---|
| HOM-23 | done, device pending | `ControlsNotch` hangs from y = 0 (flared concave top, rounded bottom, black), three outline glyphs (`OutlineMediaGlyphs.kt`) in 48 dp labelled targets, title line stays below it. Tests `GlanceNotchUiTest`, `GlanceNotchGeometryTest`, `GlanceHomeUiTest` updated. Screenshots `feature-grid/build/screenshots/glance_notch_*.png`. |
| STR-06 | done, device pending | Previous, Play/Pause, Next still call the same `MediaNow` functions; the play/pause glyph and label follow the playing state; hidden when nothing plays. |
| NFR-13 | done, device pending | Targets 48 dp, labelled Previous, Play, Pause, Next; focus nodes `glance.prev`, `glance.playpause`, `glance.next` with the focus ring. |
| NFR-14 | done, device pending | Spring drop from above the edge, 180 ms retract; reduced motion is a fade; timer (4 s, restart on use, pause while pressed) and tap-outside unchanged. |
