# Wave 20: Radar Orbit stays on screen while typing

| ID | status | note |
|---|---|---|
| PAL-18 | built | Typing keeps the Orbit (`RadarLayoutPolicy.showsFlatList`); results re-lay on the rings by rank, best match innermost; tiers named Best match/Close/Related/Other for TalkBack; web hand-off rows and a Filenames chip on a compact `ResultStrip` under the rings (rings centred above it); hardware keyboard/pointer still gets Flat unless pinned (reading in docs/decisions/orbitTyping.md). Verified by tests (`PAL_18_*`, `PAL_12_*` ring tests) and by reading screenshots w20-typed-orbit-portrait-dark and landscape-dark (rings centred below the query line, strip at the bottom, nothing pushed off). Not seen on a device. |
| PAL-07 | built | Keyboard walk follows the typed ring order then the strip rows (`navTiles`); `PAL_07_typedOrbitRingOrderIsTheTileOrderTheKeysWalk`. No hardware keyboard run. |
| PAL-12 | built | `RadarRings.stableOrder` keeps place across keystrokes (tests `PAL_12_resultsKeepTheirPlaceFromOneKeystrokeToTheNext`, best-match-first, overflow); `StableOrder.merge` for streamed hits unchanged. Streaming ring shifts not pixel-tested. |
| PAL-20 | built | Long-press preview on a typed Orbit tile (`PAL_20_orbitTileLongPressStillOpensThePreviewWhileTyping`); strip pills use the same tile actions. |
| PAL-09 | built | Learning untouched; typed order uses the engine/Ranker blend (not blended twice). `PaletteSession` is dead code used only by its own tests; left in place. |
