# Backgrounds: the two-slot, signal-driven model (HOM-16, PAL-24, wave 15)

## What changed

The background of the app is up to two distinct items from nine (five living wallpapers: Beam, Spire, Halo, Laser, Shaft; four effects: Glow lines, Breathing dots, Bottom glow, Radar sweep), at most one a living wallpaper, None valid in each slot; default Spire + Breathing dots. Every item is a `SignalView` and glows differently with the signal level; with no signal it dims and the comic lines appear. One composable, `SignalBackground`, serves home, Radar, Launchpad and edit backdrops and the Settings backdrop. Ground Control > Background has the two pickers with live thumbnails, a "Preview signal" control, and Intensity. What was removed, and how to restore it: `backgroundsReset.md`.

## Why

Owner request, quoted there: keep only the proposed options, remove the earlier ones; the references 27 to 34 define the look.

## What the old spec said

HOM-16 "Wallpaper integration": the built-in backgrounds stay, an added option draws over the system wallpaper, forwards scroll offsets and derives accents. The section 3 "Backgrounds" row listed 24 solids, 20 gradients, 15 textures, 13 live, the Horizon picture and an own photo. PAL-24 was Radar-only (Sweep, Signal view, Comic lines switches; Radar background pane). All three are rewritten.

## Decisions

1. **Model.** `BgItem` (ten entries with None), `BackgroundChoice(slot1, slot2)`; rules in one place (`valid`, `refusal`, `with`, `normalised`). The stored form is two ids (`bgSlot1`, `bgSlot2`); an unknown id falls back to that slot's default, a broken pair empties slot 2. `radarIntensity` is reused as the Intensity (a wave 14 value the owner may have set) and keeps its name; `radarSignalForce` stays as the Lab override.
2. **Levels.** `BackgroundCurves`: five numbers per parameter (levels 0 to 4), linear between, so a fractional eased level (time constant 330 ms, about 95 percent after 1 s, instant under reduced motion) slides every parameter. Level 0 is the dim resting state, never nothing (the owner's "dim resting hairline or ember"). A live source that has not spoken yet draws as level 3 without comic lines, so launch never flashes the no-signal look.
3. **Drawing.** Everything procedural in one Canvas, nothing bitmap but a 96 px film-grain tile (alpha 0 to 9 of 255 noise, one repeated shader, drawn once over the lit items). Shapes and shaders are built once per window size; a frame changes the canvas transform and paint alphas only. Soft bands (Beam, Shaft) are 36 to 40 abutting slices, each filled with a gaussian cross-section gradient (a stack of nested hard-edged shapes banded visibly in the first render and was replaced); Halo is a radial ring profile multiplied by a sweep ramp (`ComposeShader`, five levels precomputed, the frame fades between the two nearest). Cores use an additive blend on dark; on light everything is a wash at 30 percent without additive light.
4. **Orientation** (the picture is not rotated; the composition is chosen per orientation):
   - Beam (30): landscape enters from the right edge and runs left (as pictured); portrait enters from the bottom edge and runs up.
   - Spire (31): rises from the bottom centre in both; the cusp base sits at 64 percent of the height with the bloom below it.
   - Halo (32): a tilted ring (-22 degrees, 0.8 flattening), sized by the shorter side, centred; head at the lower right.
   - Laser (33): vertical beam on a floor line at 70 percent with the arc's circle sized by the shorter side; both orientations stay upright.
   - Shaft (34): landscape from the top edge near 34 percent to the lower right at 68 percent (about 60 degrees, as pictured); portrait is steeper, from 28 percent at the top to 80 percent at the bottom.
   - Bottom glow (27 to 29): a line at 93.5 percent of the height spanning the middle half of the width in both; bloom height from 5 percent (level 0) to 58 percent (level 4); level 1 is the quiet line of picture 28, level 4 the full bloom of picture 27; below the line the window drops back to near black.
5. **Reference comparison.** Beam, Spire, Halo, Laser and Bottom glow match the references in kind (colours, composition, the way the shape grows with the level); they are not pixel-compared and the faint vertical streaks of picture 31 and the dust specks of 33 are not drawn. Screenshots: `feature-grid/build/screenshots/bg-<item>-L{0,2,4}-{port,land}.png`.
6. **Surfaces.** `SignalBackground` replaces the home's `BackgroundLayer`, Radar's backdrop picture and `RadarBackgroundHost`, and the Settings backdrop. Radar keeps its own black base. Launchpad's and the edit card's backdrops are the home surface drawn small (they show the live home), so they need no call of their own.
7. **Legibility.** `BackgroundSamples` draws the real painters into a 48x96 bitmap at levels 4 and 2 and four clock values and samples a 5x5 grid; the NFR-15 plan sizes a scrim only where needed. A test runs every item and the default pair through it and requires AA.
8. **Settings.** One pane, Ground Control > Look > Background; the options carry a still thumbnail each (ten animated thumbnails would cost battery), the strip above them animates. A disabled option says why ("Already in the other slot", "Only one living wallpaper") and is announced as disabled with the reason.
9. **Strings.** `L10nW15backgrounds.kt`, eight languages. Old orphaned keys (for example "Radar background", "Choose a photo…") are left in the tables: they are harmless and removing them would only widen the merge with the parallel stream.

## Known limits

Nothing here is device-confirmed (device-checklist rows added). The sweep and ping and the beads' twinkle are not compared with any reference. Pixel-level fidelity to the five pictures is by eye. The light variants are quiet washes; the Glance stream may refine them when it builds the light surface.
