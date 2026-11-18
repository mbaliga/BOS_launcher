# Radar black gradients (wave 25, supersedes the glass below), nothing leaves the display, no dead touch (PAL-18, PAL-20, INP-12, NFR-13, NFR-15)

**Wave 25 owner request (authoritative, replaces the wave 21/22 glass):** "Radar looks weird - remove the glass effect buttons and keep only black gradients instead.
Don't let icons exit the display area. I should be able to see all recents at once, not have some outside the screen. There is still a dead touch area. The
landscape view cuts off above the keyboard."

Everything from "Radar glass, mic at the bottom centre" down is **history**: the glass look, its measured shade and the Spire lift are gone. What the owner
decided in waves 21 and 22 about *placement* stays: the mic is 56 dp at the bottom centre (inline in the field row when the free height is under 220 dp), the
Clear X is inside the field, Close is outside the field while the query is empty and not in Flat, every target is 48 dp, labelled, with a focus ring.

## 1. Black gradients instead of glass

`Glass.kt` is now `BlackSpec`, `Modifier.blackSurface(shape, pressed)`, `BlackButton` and `BlackField`. A surface is black at 96 percent at its top and 70 percent at
its bottom (one vertical gradient, built once per size by `drawWithCache`), solid black while pressed or listening; no blur, no translucent white fill or rim, no sheen,
no inner glow, no grey. The mic shows "listening" with a 2 dp violet ring on solid black. Text and icons are white. **Contrast holds by construction**: the least opaque
pixel is 70 percent black, so even over pure white it is a dark grey and white on it is above 4.5:1 (hint text at 80 percent white too);
`RadarBlackW25Test` computes it, so nothing is measured from the background any more.

Removed (code and what tested only it): `Modifier.glass`, `GlassLayers`, `GlassSpec` (fills, sheen, shade maths), `GlassButton`, `GlassField`, `GlassShade`/`GlassShades`/
`rememberGlassShades` (the measured shade), `BackgroundConfig.glass` with `RadarGeometry.glass`, the Spire's lift and tip floor (`GLASS_TOP_DP`) and `SignalBackground(glass =)`; the
Flat scope pill lost its nested glass fill (it keeps its 48 dp target). Tests removed: `RadarGlassContrastW21Test` (the measured-shade contrast over every background pair),
`RadarGlassFrameCostW22Test` (allocation of the glass draw path; its "no RenderEffect or blur anywhere in Radar" check lives on in `RadarBlackW25Test`). The behavioural
tests in `RadarGlassW21UiTest`, `RadarGlassW22Test` and `RadarAuditGlassW22UiTest` (focus, TalkBack, 48 dp, bounds, RTL) are kept.

## 2. Nothing leaves the display; all recents at once

`OrbitFit` (`RadarFit.kt`, pure) fits the rings to the Orbit's own region (below the field, above the mic bar and the keyboard). Each ring is an ellipse scaled to the
region (inner ellipses at 40, 60, 80, 100 percent) with up to 5, 9, 13, 17 tiles (the ring capacities are unchanged); a tile is placed only where its whole box (icon and
caption) lies inside the region, clear of the wheel and its "SEARCH" caption and of every other box. If the rings cannot hold every result at the current icon size, a tight
staggered fill of the same region is tried at that size (no guide lines are drawn then); if that cannot either, the icon shrinks 2 dp and the loop repeats, down to a **32 dp
floor**; only below that are the lowest-ranked results left out (the wheel keeps its room first: at most half the region plus 8 dp, so the rings keep the other half). Moving
rings no longer revolve through the screen (that carried icons off it): with motion on each tile drifts at most 3 dp around its place (reduced motion: still). A last clamp in
the tile's layout keeps its measured box inside the region whatever it holds (a caption, nested shortcut pills). The Filmstrip (`StripFit`) shrinks its tile (76 to 32 dp)
and drops the footnote, the caption and the nested pills before anything would hang below the free height; its row and the Flat list still scroll sideways and up
and down by design (their edge items are cut by the scroll, not by the layout).
Numbers (`RadarFitW25Test`, 44 recents wanted, font scale 1, keyboard closed): 411x891 and 1280x800 place all 44, 891x411 all 44, 360x640 38, 640x360 32, 320x480 20, 280x653 24;
with the keyboard 40 percent open 1280x800 still places 44, 891x411 40, 411x891 32, 640x360 26. When the region is too small to hold one tile row beside the wheel (320x480 with
the keyboard 60 percent open, big text) the Orbit places none and the wheel keeps the area; that is the honest limit, not hidden.

## 3. No dead touch

Found and fixed: (a) nothing took a tap on empty space (between tiles, the strip beside the mic, the blank part of a list), (b) the wheel's blank space between its labels
swallowed taps, (c) the field's padding around its text line did nothing. Now: the Radar root takes any tap no child took: with nothing typed it **closes Radar**, with a
query it puts the soft keyboard away (the documented empty-space action); a tap on the wheel's blank space or anywhere on the field puts the cursor in the field; only the mic
itself (56 dp) is active in the bottom bar. `RadarBoundsW25UiTest` injects taps on a grid across the screen (Orbit, Filmstrip, Flat; portrait and landscape; keyboard
open and closed) and asserts every point hits a control, a result or the empty-space action. Orbit tiles are also placed outermost in their modifier chain now, so
their semantics and touch area are where they are drawn.

## 4. Landscape

With the keyboard open in landscape the region is the whole free height above the keyboard (the mic goes inline in the field row from wave 23), the wheel takes at most
half of it and the rings are fitted to the rest: no label is cut and no blank band is left (`PAL_18_landscapeUsesTheWholeFreeHeightAboveTheKeyboard`).

## To undo

The glass is in git history (wave 22). Restoring it means `Glass.kt` from that commit and the `shades` plumbing in `SearchOverlay`; the Spire lift is `BackgroundConfig.glass`.

---

# (History) Radar glass, mic at the bottom centre, X inside the field (wave 21; PAL-18, PAL-20, INP-12, NFR-13, NFR-15, NFR-05)

Owner request (authoritative): "For the Radar, keep the mic icon for voice input in the centre at the bottom. Don't make the field and
button that kind of grey. Make it glass. Put the X inside the field."

The owner's screenshot (build 49, Radar over Spire) showed a flat grey-blue field and a flat grey mic circle, the clear X as a separate
button beside the mic, and the mic at the bottom right. Since then (wave 18) the field moved to the top; the mic had stayed in the
field row, next to Close/Clear.

## What changed

1. **Mic: bottom centre.** `PushToTalkButton` left the field row. `SearchScreen` draws it in a 72 dp strip (`RadarMicBarHeight`: the 56 dp
   button plus 8 dp above and below) centred at the bottom of the safe, IME-padded area, so it rides above the software keyboard and the
   gesture bar (the existing `safeDrawing` + `imePadding`) in all three layouts and both orientations; landscape keeps it centred
   horizontally. The results area reserves that strip (`padding(bottom = RadarMicBarHeight)` and `clipToBounds`), so the rings, the
   wheel, the strip of web rows, the Filmstrip and the Flat list end above it and never run under it. When there is no on-device
   recogniser (INP-13: the button is not drawn at all) no space is reserved. Behaviour is unchanged: press and hold to talk, release to
   search, a tap or Enter/Space for TalkBack and keyboard users, the same permission flow (CFG-07). Labelled "Speak to search", focusable.
   The keyboard-selected result's action list moved up by the strip so it cannot sit on the mic.
2. **Glass.** `Glass.kt` (feature-grid) is one reusable look, see below. The field, the scope pill, Close, Clear and the mic use it.
3. **X inside the field.** The field is a `GlassField`: [scope pill (Flat only)] [text] [Clear X]. Clear is a 48 dp target with a 20 dp
   glyph at the trailing end, shown only while the field has text, labelled "Clear", focusable and operable from a keyboard. There is no
   Clear button beside the field any more.
   - **Close and Clear are never both visible.** With text: Clear (inside). With no text: Close, a 48 dp glass circle outside the field at
     its trailing end, in Orbit and Filmstrip. **Wave 22 decision: Flat has no Close.** Wave 21 added one to Flat as well; the owner asked
     only for the X inside the field, and nothing requires a visible Close in Flat: Flat is the layout hardware keyboard and pointer users get
     (PAL-18), where Esc leaves (PAL-07); Back and the back gesture leave for everyone else; NFR-13 asks for labelled 48 dp targets on the
     controls that exist, not for a second way out. So in Flat the field takes the whole width and the only X is the Clear inside it
     (with text). `PAL_18_flatHasNoCloseBesideTheFieldOnlyTheXInsideItWhenThereIsText`; Orbit and Filmstrip keep Close
     (`PAL_18_orbitAndFilmstripStillHaveCloseWhileTheQueryIsEmpty`). If a visible exit is wanted in Flat, restore `!flatMode` in `SearchScreen`.
   - The scope menu was outside the field (Flat only; Orbit and Filmstrip use the wheel as the scope). It is now **inside the field at its
     leading end** as a quiet glass pill (48 dp tall touch target).
   - Right-to-left mirrors the whole row (leading/trailing, no left/right literals); tested.

## The reusable glass (`Glass.kt`)

```kotlin
Modifier.glass(shape = RoundedCornerShape(50), tint = Color.White, pressed = false, dark = true, strength = 1f, shade = 0f)
GlassButton(onClick, description, size = 48.dp, shape = CircleShape, filled = true, shade = 0f) { icon }   // labelled, focusable, 48 dp
GlassField(shade = 0f) { leading; text; trailing }                                                        // fully rounded Row
```

`glass()` paints behind the element with `drawWithCache` (brushes are built once per size, nothing is allocated while drawing):
- a translucent white fill, brighter toward the top-left: 16 percent at the top-left to 8 percent at the bottom-right;
- a soft inner shadow (black, 10 percent) over the lower 45 percent;
- a sheen: white 14 percent at the top fading to nothing by the end of the top third;
- a 1 dp gradient hairline, white 55 percent at the top-left to 10 percent at the bottom-right;
- pressed: the fill is 7 points brighter; there is no ripple (`clickable(indication = null)`), so no colour clash;
- `tint` colours the glass (the mic goes violet while listening); `strength` quietens glass nested in glass (the scope pill, 0.45);
- `dark = false` is the light-surface glass: a milkier fill (60 to 38 percent white), a dark hairline and dark content colour
  (`GlassSpec.content(false)`). **Radar itself is black in every theme** (wave 15), so it always passes `dark = true`; the light variant is
  for the other surfaces the owner may move this to. Light and dark themes therefore render the same Radar (tested at both).
- Reduced motion: nothing here animates, so reduced motion changes nothing.
- No `RenderEffect`, no blurred copy of the background. A real backdrop blur needs one downsampled blurred copy of the whole background
  layer shared by every glass element, redrawn on each background change; the signal background animates every frame, so that copy would
  be stale or per frame. The frosted look therefore comes from the translucency, the gradient, the sheen and the hairline over the live
  background showing through. If a blur is wanted later it belongs behind `glass()` as an optional layer fed by one shared bitmap.

To use it elsewhere: wrap content in `GlassField`/`GlassButton` or add `Modifier.glass(shape)` before `clip`/`clickable`; draw text and
icons in `GlassSpec.content(dark)` and hints in `GlassSpec.hint(dark)`; if the surface behind can be bright, get `shade` as below.

## Legibility (NFR-15)

White text on 8 to 16 percent white over a wallpaper is fine on dark pixels and fails where the background is bright. Wave 21 darkened the
glass with a black shade under it, measured from pixels; with the default pair (Spire + Breathing dots) that was 0.37 (top) and 0.40
(bottom), and the glass looked smoky. Wave 22 finds the better answer in three steps and keeps the shade only where a pair still needs it.

1. **Move the bright part away from the glass (Radar only).** `BackgroundConfig.glass` (Radar passes `glass = true`; Home and every other
   surface are unchanged). The Spire's base and bloom sit 10 percent of the height higher, and its tip keeps its place but stops 84 dp below
   the top, so the tip no longer enters the field at level 4 and the bloom no longer sits under the mic. (Measured: the 0.37 came from the tip
   in the top bar at level 4 and from the comic-line grey at level 0; the 0.40 from the bloom under the mic.)
2. **Judge each piece of glass by what it carries.** The field carries text (4.5:1, placeholder at 80 percent white also 4.5:1); the scope pill's
   label is glass on glass (4.5:1, nested, pressable); Close and the mic carry only an icon (3:1, the non-text criterion). Wave 21
   measured all of them as pressed, nested text, which over-darkened the icon-only buttons and the field (the field is not pressable and
   holds no nested glass over its text). `GlassShade.Carries` = `Text` / `Label` / `Icon`; `GlassShades(top, bottom, close)`.
3. **A shade only where still needed.** `GlassShade.compute` renders the background (the real painters, with `glass = true`) at levels 4, 2
   and 0 and seven clock values, reads the pixels in the field, the pill, the Close circle and the mic zones, and `shadeFor` finds the least
   black that keeps each criterion (target plus 0.2). `rememberGlassShades` runs it off the main thread when the background, intensity or
   window size changes.

Result: the default pair needs **0.00** (field, Close and mic), over the 0.2 goal; the glass is clear. Over all valid pairs, both
orientations, levels 0 and 4, the measured minimum is 4.71 for field text, 4.70 for the placeholder and 3.23 for icons (`RadarGlassContrastW21Test`,
864 frames). 80 background and size cases still need some shade (at most 0.58: bright wallpapers such as Beam, Shaft and Laser reach the
field, Close or the mic, and only those keep a shade; the tests print which). A per-region luminance map (shading only the few cells a
bright tip covers) was considered and not built: for the default pair geometry removed the need, and the other pairs' bright areas cover
the whole zone, not a few cells.

Other Radar text (results, headers) is unchanged and unaffected by the glass.

## Layout: the mic strip, the wheel, the rings (wave 22)

Each piece has its own region. The query line is the top 64 dp; the **mic strip** is the bottom 72 dp (reserved, centred); the **results area**
is what is left between them, clipped to its own bounds. Inside the results area:
- The **category wheel** is sized to the room: `WheelSpec.fit` gives 1, 3 or 5 rows (odd, the selected one in the middle) of `WheelSpec.rowFor(fontScale)`
  (34 dp, or 1.5 lines of the label at large text, so labels never overlap at font scale 2.0). Its labels fade to nothing half a row
  outside its own box (`wheelLabelAlpha`), so no glyph is cut at an edge. Wave 21 always drew five 34 dp rows plus faint extras, which in landscape ran
  into the strip and, in the Filmstrip, into the tiles.
- The **Filmstrip** keeps its 182 dp under the wheel when there is room, and gives way (down to nothing) before the wheel loses its one row,
  clipped to its own band.
- The **Orbit** rings are centred on the wheel; tiles stay clear of the wheel text, whose columns now grow with the font scale.
- Tests: `RadarGlassW22Test` runs 4 window sizes (360x640, 411x891, 640x360, 891x411 dp) x 4 layouts x font scale 1.0 and 2.0, each with the
  keyboard closed and then faked open (40 percent of the window), and checks the mic's bounds against the wheel box, its labels, every
  visible result, and the Filmstrip band against the wheel. Screenshots `w22-*.png` were read.
- Rejected: moving the mic into the "free gap" at the bottom centre of the wheel. The wheel is centred, the free gap depends on the rows and
  the font, and a reserved strip is the only layout where the bounds can be asserted.

## Frame cost (wave 22, NFR-05)

`Modifier.glass` builds `GlassLayers` once per size and look in `drawWithCache`; `GlassLayers.draw` only issues five draw calls with stored brushes.
`RadarGlassFrameCostW22Test` draws every kind of glass piece (field, shaded Close, listening pressed mic in RTL, nested pill, light glass)
2000 times and measures the thread's allocated bytes (352 over 2000 frames, 136 of them the test harness itself), checks the source of
`draw` for brush, list or Path construction, and scans the glass, the Radar screen and the painters for `RenderEffect`, `blur`, `BlurMaskFilter`
and the like (none).

## Not done / limits

- No backdrop blur (above).
- The scope popup list (Flat) is still plain text over the background, as before; it is not part of the top or bottom bar.
- Keyboard-open placement is tested with faked IME and navigation-bar insets; not seen on a device. In landscape with the keyboard up the results area is a
  few dozen dp: the wheel shows one row and the Filmstrip is clipped or gone; the owner may want the mic hidden then.
- The mic's listening state is not announced as a state description (needs a string in eight languages). Done in wave 23, below.

## Wave 23: the mic strip is adaptive; the mic announces its state (PAL-18, INP-12, NFR-13)

Owner/device report: landscape with the keyboard up, the reserved 72 dp mic strip took too much of the free height and clipped the wheel.

- **Rule.** `RadarMicPlacement.inline(freeHeight)`: the free height is the height of the safe, IME-padded area minus the 64 dp query line. At 220 dp or more
  the mic stays in its bottom strip, 56 dp, centred (nothing changed). Below 220 dp the strip is not reserved at all and the mic rides in the field row,
  beside the field before Close (or alone in Flat), 48 dp instead of 56 dp, still a glass circle with the same hold-to-talk behaviour, focus ring and
  label. The results area gets the whole height under the field. The keyboard-selected result's action list follows the same number.
- **Why beside and not inside the field.** The Clear X is already inside the field's trailing end; a second target there would put two 48 dp circles in a
  field that is 216 dp wide on a 640 dp window and make taps ambiguous. Beside it, the row is field, mic, Close (Orbit and Filmstrip, empty query).
- **Where it changes.** 640 x 360 dp landscape with a keyboard 40 percent high (free about 152 dp): inline. 891 x 411 with a 45 percent keyboard (free about
  163 dp): inline. 640 x 360 without a keyboard (about 296 dp), every portrait case, a keyboard in portrait: strip, as before. Tested in
  `RadarGlassW22Test` (`PAL_18_landscape...`, `PAL_18_portrait...`: Orbit at rest and typed, Filmstrip, Flat; font 1.0 and 2.0; keyboard closed and
  faked open); the region checks of wave 22 now accept the inline position. Screenshots `feature-grid/build/screenshots/w23-*.png` were read.
- **State, not shouting.** The mic exposes `stateDescription` Ready, Listening, or "Unavailable, microphone permission is off" (eight languages,
  `L10nW23a11yPolish.kt`) and a polite live region, so TalkBack says the new state after what it is already saying. The name stays "Speak to search".
  The permission is read when the control appears, when listening changes and when the permission dialog answers. "Unavailable" means the permission is
  off (a press still asks for it, CFG-07); a device with no on-device recogniser still draws no mic at all (INP-13).
- Not run on a device: the real keyboard height and insets (Robolectric fakes them), how TalkBack speaks the state, and whether a state change is read
  too early or too late.
