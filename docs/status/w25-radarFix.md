# Wave 25, stream radarFix (owner item 5)

Owner text: "Radar looks weird - remove the glass effect buttons and keep only black gradients instead. Don't let icons exit the display area. I should be able to see
all recents at once, not have some outside the screen. There is still a dead touch area. The landscape view cuts off above the keyboard."

Nothing here is device-confirmed. Verified: JVM unit tests, Robolectric Compose tests (bounds, touch grids, screenshots read by eye). Device-only: how the black surfaces
look in daylight, real keyboards (the tests fake the IME inset), real touch behaviour. Checklist: `docs/device-checklist.md` "wave 25, radarFix".

| ID | Status | Note |
| --- | --- | --- |
| PAL-18 | built | Glass replaced by black gradients (`BlackSpec`, `blackSurface`, `BlackButton`, `BlackField` in `Glass.kt`); mic 56 dp bottom centre, X inside the field, Close outside when empty and not Flat, 48 dp targets, focus rings, labels all kept. Rings fitted to the region (`OrbitFit`); Filmstrip shrinks (`StripFit`); empty-space tap closes Radar or puts the keyboard away. Decision: `docs/decisions/radarGlass.md`. |
| PAL-20 | built | All recents at once: 44 on 411x891, 1280x800 and 891x411 (keyboard closed); fewer on small windows (the numbers are in the decision record); icons shrink to a 32 dp floor before any result is dropped. Orbit tiles no longer revolve through the screen (a 3 dp drift with motion on). Long-press and right-click unchanged. |
| INP-12 | built | The field takes a tap anywhere on it (padding too); the wheel's blank space puts the cursor in it; landscape with the keyboard open uses the free height above it (mic inline from wave 23). |
| NFR-13 | built | 48 dp targets, labels and focus rings kept; tile semantics now sit where the tile is drawn (they were at the origin of the Orbit box before the layout modifier order was fixed); no label drawn partly outside the region (bounds matrix). |
| NFR-15 | built | Contrast by construction: the least opaque black pixel is 70 percent, white text on it over pure white is above 4.5:1 (`RadarBlackW25Test`); the measured-shade machinery is deleted. |

## Removed (owner item 5) and why

- Code: `Modifier.glass`, `GlassLayers`, `GlassSpec`, `GlassButton`, `GlassField`, `GlassShade`, `GlassShades`, `rememberGlassShades`, `BackgroundConfig.glass`, `RadarGeometry.glass`, the Spire lift and `GLASS_TOP_DP`,
  `SignalBackground(glass =)`, the Flat scope pill's glass fill, the mic's violet glass tint (listening is now a violet ring on solid black), the Orbit's rotating rings and horizontal drag (they carried icons off screen).
- Tests deleted: `RadarGlassContrastW21Test` (3 tests: measured shade over every background pair), `RadarGlassFrameCostW22Test` (3 tests: glass draw allocation, `GlassLayers.draw` body, banned APIs; the banned-API check lives on in `RadarBlackW25Test`). Both only tested the glass look.
- Tests kept (behaviour): `RadarGlassW21UiTest`, `RadarGlassW22Test`, `RadarAuditGlassW22UiTest` (focus, TalkBack, 48 dp, mic and region bounds, RTL, Close/Clear rules). The Device checks item `GlassLook` kept its id and now reads "Radar black buttons in daylight, dark and light" (all eight languages).

## New tests

- `RadarFitW25Test` (pure): bounds matrix, 7 windows x font 1 and 2 x keyboard closed and 40 percent open x 4 counts, no overlaps, all 44 recents on large windows, ring capacities, landscape, `StripFit`.
- `RadarBoundsW25UiTest` (Robolectric, real `SearchScreen`): 56 Orbit bounds tests over the 7 windows (landscape also 60 percent keyboard), 28 Filmstrip tests, drifting tiles, all-44 and landscape-free-height tests, 8 pointer-injection grids over the screen (Orbit, Filmstrip, Flat; each point hits a control or result or closes Radar), the strip beside the mic, screenshots.
- `RadarBlackW25Test`: contrast by construction, black-only drawing, glass machinery gone, no blur/RenderEffect.

## Not done / limits

- Orbit places none when the region cannot hold one tile row beside the wheel (320x480 with the keyboard 60 percent open, or big text with the keyboard up); the wheel keeps the area.
- Filmstrip and Flat scroll by design; their edge items are cut by the scroll, only vertical bounds are guaranteed. A contact's expanded channel bar in a very short Filmstrip can still be taller than the caption row.
- Nested shortcut pills under an Orbit tile are clamped inside the region but may overlap a neighbour when typed results have shortcuts.
- Suggestion and action row icons: left to stream actionIcons (not touched).
