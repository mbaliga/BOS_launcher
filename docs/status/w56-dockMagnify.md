# Wave 56: dockMagnify (Mac-style magnification, classic 3D shelf)

Requirement rows: DCK-04 (dock settings, style, sheet, Settings page); DCK-01 (Edit dock disables magnification). Tests are named `DCK_04_mag_*`, `DCK_04_style_*`, `DCK_04_sheet_05`. Decision note: `docs/decisions/dockMagnify.md`. `docs/SPEC_STATUS.md` not edited.

| ID | Status | Note |
| --- | --- | --- |
| DCK-04 magnify setting | built, not device-verified | Magnify icons (off), Subtle/Medium/Large cards, sheet and Settings page, live; two new DockSettings fields; export/import and old-file reads tested (`DockMagnifyStateW56Test`, `DockMagnifyW56Test` 08) |
| DCK-04 behaviour | built, not device-verified | kernel, spread, clamp, hit mapping, tooltip placement (pure tests, 3 to 12 icons, side docks, RTL); touch drag, release, tap, hover click, tooltip, editing, reduced motion, side docks, RTL, 10 icons without recomposition, focus keys, long press (`DockMagnifyUiW56Test`) |
| DCK-04 3D look | built, not device-verified | glass shelf, reflections, dots, bubble, light and dark; screenshots `feature-dock/build/screenshots/w56-*.png` read |

## Replaced tests
`DockStyleTokensW39Test` slab/contact-shadow tests and `DockStyleUiW39Test` 20/22/23 for 3D (they asserted the deleted rounded slab): replaced by shelf tests. `DCK_04_page_34` now lists seven fields.

## Found from screenshots / tests
Tooltip sat on the icon (wrong pivot fraction in 3D slots); stale member positions after the rail grows (fixed with a refresh on rail layout); row clipped at the ends (fit by contraction, then less growth); a shift moved the icon off the finger (removed); the ripple read as a dark box (off in 3D).

## Not matched / limits
Real icons get no gloss; the shelf is not a bitmap-quality render; a scrolling dock clips the spread at its viewport; the preview frame clips the bubble at its top; the part of a pushed-out icon beyond the rail bounds relies on the added slack/headroom for touches.

## Device-only
Touch feel, 60 fps with a real wallpaper, hover with a real mouse or trackpad, D-pad on a real pad, TalkBack.
