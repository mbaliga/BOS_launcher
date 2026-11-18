# commsLayout (wave 28)

> Wave 29 (commsRead): the pinch no longer sets the Comms size; it filters by read state, see [commsRead.md](commsRead.md).

Owner: "Please observe the spacing and alignment of these mockups and make sure you follow it exactly - it looks rather clunky now. User should be able to configure the scale of the content here from 75% to 125% in detents of 5%. 100% is default."

## What was wrong
Wave 27 used 28/40 sp text, 56 dp icons and 80 dp rows. The mock-ups (`docs/design/comms/comms-mock-screen.png`, `comms-mock-menu.png`, 904 x 2000 px screenshots of a 1080 x 2400 phone, so 1 dp = 2.2 px at 411 dp) are far more compact.

## Measured table (PIL, px in the mock-up; dp = px / 2.2)
| Element | Mock-up (px) | dp at scale 1 |
|---|---|---|
| Content left margin / right edge | 70 / 802 (card sliver starts 843) | 32 / 18 left of the sliver |
| Menu glyph ink | x 70-97, y 80-108 | 12.8 square, 1.8 dp bars |
| Title "Comms" | ink left 129, cap 77-104 | 17 sp, text starts 57.7 dp |
| Clock "23:02" | ink left 73, digits 183-219 | 23 sp light, row 48 dp |
| Status glyph centres x | 595.5, 656.5, 711, 777.5 (y 198 sun, 201 others) | inks 20.4 / 10.5x16.4 / 16.4 / 20.5x16.4, 11.7 gaps |
| Tabs | ink left 70, 174, 358, 489, 644; cap 286-312 | 17 sp, 35 dp row, minimum slots 46.4 / 83.4 / 60 / 70.9 dp |
| Tabs hairline | y 337-338, x 70-800 | 1 dp |
| Rows | icon tops 374, 529, 684, 838 (pitch 154.5) | 70 dp row (69 + 1 hairline), icon 38 dp, 16 dp from the row top |
| Row text | text left 190; title cap 387-413; sub cap 439-456; time right 800, 435-452 | title 17 sp baseline 33.8, sub 11 sp baseline 53.3, time 11 sp baseline 51.2, 16 dp after the icon |
| Row hairlines | y 492, 647, 801 | inset 32 / 18 |
| Menu panel | 528 px wide, padding 72, end 485 | 240 dp (58.4 percent), 32 / 19.5 |
| GROUP BY / SHOW | cap 73-91 / 369-387, left 72 | 12 sp capitals, baselines 41.4 dp and 32.3 dp below the grid |
| Grid | dividers x 208, 347, y 223; cells 71-485 x 132-315 | 3 x 2, cells 41.4 dp, glyph 23 dp, 1 dp hairlines |
| Show rows | centres 450, 529, 607, 686, 765 | 36 dp pitch, icon 16.5 dp, 16.5 gap, name 17 sp, count right at 485 |
| Screen behind the menu | shifted right 551 px, not dimmed | panel + 10.5 dp, scrim alpha 0 (still a tap target) |

Measuring revealed the owner's reading was a little high: title cap 27 px is 17 sp (not 19-20), tab text natural width (no extra letter spacing), second line and time 11 sp.

## Design
- `CommsStyle` holds every dimension in dp/sp at scale 1.0. `CommsScaleHost` gives its content a `Density` of system density x factor, so every `.dp` and `.sp` in the Comms surfaces (screen, history rows, hub notices, menu panel, delete confirmation, empty states) is multiplied by the factor; the sheets (sound, groups) are not scaled. The host also scales the view configuration's minimum touch target back, so Compose's 48 dp touch-target expansion is 48 dp of real screen at every size; explicit minimums (menu button, status glyph height, stepper keys, chips) use `commsTarget()`.
- Layout is by baseline (`paddingFromBaseline`) so text lands where the mock-up has it whatever the font. The app's own font has other glyph widths than the mock-up's, so text is matched by left edge and baseline; tab labels have minimum slot widths from the mock-up so their left edges match.
- Touch targets under the mock-up's own pitch (tabs 35 dp, grid cells 41 dp, Show rows 36 dp, status glyphs 33 dp wide) are widened to 48 dp by the minimum touch target, not by the layout.
- Right margin: 18 dp from the card's sliver; the right edge never moves under the card, long titles ellipsize (8 dp short of the margin).

## Comms size
- 75..125 in 5s (11 detents), default 100; `CommsScale` (pure stepping, clamp, snap, parse of garbage), `CommsScaleSettings` (pref file `comms_scale`, key `pct`, `TolerantPrefs`; exported with launcher settings through `PrefFilesSource.ALLOWED`; classified in the W13 audit and the `StateSchema` notes; additive, no schema bump).
- Controls: Ground Control, Comms category, a stepper row (minus, "100%", plus; Left/Right keys; progress semantics); Options row of the Comms menu (same control, 48 dp keys); pinch on the list (live, the start size times the finger spread snapped to a detent, a dead zone of 8 percent before it counts; Ctrl+Plus and Ctrl+Minus step one detent).
- Removed: the wave 25 row density (Compact, Comfortable, Expanded) and its pref key (nothing migrated; an old `density` value in `launchpad_hub` is ignored). The pinch that changed grouping granularity after the density saturated depended on density, so it is dropped (grouping is chosen in the menu grid). The two pinch bindings of the Comms gesture settings (show unread / show all) are no longer offered because a pinch sets the size now; the stored values stay unused.

## Tests
`CommsLayoutGeometryTest` (renders the real composables at w411dp-h909dp-352dpi and compares ink against the table within 2 px; text 3 px for rows 2+ because the mock-up's rows are 155 px apart and its text 156), `CommsScaleCases` (pitch at 75/100/125, pinch, targets at 75, persistence, stepper semantics, right edge), `CommsScaleTest` (JVM stepping), `MockDiff` + `tools/comms_diff.py` (mock | shot | overlay). Screenshots: `build/screenshots/comms-geo-*-diff.png`, `comms-portrait|landscape-scale-75|100|125*.png`.
