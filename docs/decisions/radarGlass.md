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
     its trailing end. Decision: Close now shows with an empty query in **all three layouts** (Flat used to show a Clear X there, which
     had nothing to clear; a Close is the useful button on an empty Flat). The brief asked for it in Orbit and Filmstrip only; Flat gets it
     too so no layout has an unexplained gap.
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

White text on 8 to 16 percent white over a wallpaper is fine on dark pixels and fails where the background is bright (Beam's white
tip rises from the bottom centre, exactly where the mic sits; some effects reach the top bar). Instead of tinting everything darker,
the glass is darkened only as far as needed, from measured pixels:

- `GlassShade.compute` paints the background in use (the real painters, as `BackgroundSamples` does) into a small bitmap at levels 4, 2
  and 0 and 7 clock values, reads every pixel in the two places the glass sits (the top bar; the bottom centre), and `shadeFor` finds the
  least black laid under the glass (0 to 0.92) that keeps white text and the 80 percent white placeholder at 4.7:1 or better over the
  strongest white the glass can add on a content pixel (`GlassSpec.worstWhiteAlpha`: brightest corner, pressed, the little sheen that
  reaches the text, and the scope pill on top of the field).
- `rememberGlassShades` runs it off the main thread when the background, the intensity or the window size changes (once, not per frame);
  `SearchScreen` hands the top and bottom shade to the field, Close and the mic. Until it lands the glass is clear.
- With the default pair (Spire + Breathing dots) the shades are 0.37 (top) and 0.40 (bottom): the blue spire's base sits under the mic.
  Pure black, dark effects and most pairs need none or little.
- The placeholder is 80 percent white (`0xCC`), up from 60 percent, so it also reaches AA.
- Test: `RadarGlassContrastW21Test` renders every valid background pair (nine items and None), both intensities for the default pair, in
  portrait and landscape, at levels 0 and 4 and clock values the sampler did not use, and asserts text and placeholder contrast over the
  strongest glass in the field, Close and mic zones (measured minimum 6.25 for text, 4.70 for the placeholder).

Other Radar text (results, headers) is unchanged and unaffected by the glass.

## Not done / limits

- No backdrop blur (above).
- The scope popup list (Flat) is still plain text over the background, as before; it is not part of the top or bottom bar.
- Keyboard-open placement is tested with faked IME and navigation-bar insets; not seen on a device.
