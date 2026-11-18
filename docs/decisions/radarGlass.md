# Radar glass, mic at the bottom centre, X inside the field (wave 21; PAL-18, PAL-20, INP-12, NFR-13, NFR-15, NFR-05)

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
- The mic's listening state is not announced as a state description (needs a string in eight languages).

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
