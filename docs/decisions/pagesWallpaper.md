# Wallpaper per surface: Glance and Pages (HOM-16, wave 26)

## What changed

Owner text (clause 10): "The pages or scroll can have the same or different wallpaper as the glance screen. Wallpaper can be solid colour, gradient or live wallpaper. Live wallpaper selection is not showing in the landing currently."

- Two wallpapers: **Glance wallpaper** and **Pages wallpaper**, with the switch **Pages use the same wallpaper as Glance** (default on). Pages means the paginated pages and the scroll alike.
- A wallpaper is exactly one of **Solid colour** (the shared colour picker: saturation/brightness square, hue bar, hex entry), **Gradient** (two stops, linear, angle in 45 degree steps, preview) or **Live wallpaper** (the wave 15 two-slot signal model: one living wallpaper plus an effect, or two different effects; the signal glow behaviours are untouched). Nothing else is a wallpaper.
- The settings live in Ground Control > Home screen: rows "Glance wallpaper", the switch, and "Pages wallpaper" (listed only while the switch is off; while it is on, the Pages pane is not reachable because there is nothing to choose).

## The bug ("live wallpaper selection is not showing")

The only picker was a "Background" pane under Look > Theme, a nested row far from where the home (landing) is configured, and the Glance settings block had no wallpaper entry. Nothing hid it by a condition (it was listed for every landing, signal and page state), so the fault was placement: the owner looked where the home is configured and the live wallpapers were not offered there. The rows now sit in Home screen, one per surface, and the old Look row is removed (one place). `WallpaperRowsW26Test` opens the Home screen category in every state (Glance or Recents landing, signal live or none, switch on or off, 0/1/3 pages) and asserts the rows, opens each pane and selects a live wallpaper, and fails against the old layout. Each live option carries a still thumbnail (no per-frame work) and the strip animates only while motion is allowed (a static frame under reduced motion).

## Model

`Wallpaper(kind, solid, gradient, live)` in core-data `Wallpaper.kt`; `WallpaperSurface { Glance, Pages }`. All three kinds' settings are kept so switching the kind loses nothing. Stored in `launcher_prefs` (so export, import and backup already cover it): the Glance live choice keeps the wave 15 keys `bgSlot1`/`bgSlot2`; new keys `wpGlanceKind/Solid/From/To/Angle`, `wpPagesSame`, `wpPagesKind/Solid/From/To/Angle`, `wpPagesSlot1/2`. Unknown kinds fall back to Live, an unknown slot id to its default, colours are forced opaque, angles normalised, a rule-breaking live pair empties its second slot.

**Migration:** an existing install has only `bgSlot1`/`bgSlot2`; with no new keys it reads as Glance = Live with those slots and `wpPagesSame` = true. So nothing changes visibly, no migration step is needed in the state schema (no version bump: the new keys are additive and an older export simply lacks them). Retired wave 15 keys stay retired.

## Drawing

One seam, `HomeSurfaceBackground(surface, prefs, ...)` (feature-grid `WallpaperSurfaces.kt`); the parameter type is `WallpaperSurface` because core-data already has a `HomeSurface` data class (the scroll/paging description). It picks the surface's wallpaper, draws solid/gradient as one `drawRect` (brush built once per size in `drawWithCache`) or the live one through `SignalBackground(choice = ...)`, plus the NFR-15 scrim (flat wallpapers: sampled colours into `HomeLegibility.plan`; live: the existing painter sampling). It crossfades on a key of (owner surface, kind, live choice): while the pages share the Glance wallpaper the owner is Glance on both surfaces, so the key, and therefore the instance and its GPU layer, do not change on a surface change. Editing a colour never changes the key, so dragging the picker does not crossfade. One background draws at a time; the signal two-slot machinery, `SignalViews` registry and the single clock are unchanged. No new infinite animation (the clock still reads the motion prefs).

HomeScreen: `Background(baseFill, surface = Pages)`; the Glance underlay passes `Glance`. The pages stream calls `HomeSurfaceBackground(WallpaperSurface.Pages, prefs)` from the paged and scroll homes (today the non-Glance landings call it through `Background()`).

## Not done / limits

Not device-confirmed (Robolectric only). Gradient angle is in 45 degree steps (chips), not a free slider. Light theme: a user solid/gradient is drawn as chosen; the scrim keeps the text legible.

## Superseded in part by wave 55 (fixedBackdrop)

"Drawing" above (the Pages stream calls `HomeSurfaceBackground(Pages)` from the paged and scroll homes; the Glance underlay passes Glance) is replaced: both are drawn by one fixed layer below the pager, `FixedHomeBackdrop`. Same wallpaper on both surfaces: one instance, no crossfade. Different: the Pages wallpaper fades in over the Glance one with the pager position instead of sliding with the page. See `docs/decisions/fixedBackdrop.md`.
