# Launchpad card tuning (wave 9)

## What changed

- STR-01: the home card no longer rests as a two-thirds card. At rest it nearly leaves the screen: scale about 0.93, near edge at 93 percent of the width, so a thin sliver (about 7 percent) of its inner edge with the 28 dp rounded corners stays at the right edge (left in right-to-left), in both orientations. The vertical shift of the old card was removed (top and bottom are inset equally).
- The neighbouring page card under the home card was removed completely: drawing (`launchpadNeighbourCard`, now `launchpadBackdrop`, gradient only), geometry (`neighbourBounds`, `backgroundColumnPx`), tests and docs.
- The notifications panel is full width and static. Only its content is inset from the sliver side (by the return zone, at least 48 dp) and it fades in over the last 55 percent of the motion (`contentAlpha`); with reduced motion the whole backdrop cross-fades instead.
- Motion follows the owner's screen recording of another app: one progress value drives scale, translation, corner rounding, a black scrim on the card (0 to 0.5) and a hairline lighter edge on the leading side. The card's edge follows the finger 1:1 (`dragTravelFraction` equals the near edge, 0.93, close to the 0.94 asked for). Settling for taps, handle, shortcut and a released drag is a 0.45 s decelerate tween (was a low-bounce spring). Thresholds (0.35 open, 0.65 close, fling) are unchanged.
- The tap-to-return hit area is the visible sliver, widened to a 48 dp touch target when thinner (`returnZonePx`); it is still the focusable button reachable by keyboard and also takes the close drag.
- STR-10: the four system tiles are icon-only vector buttons in one row with state-carrying content descriptions and a focus ring. `SystemStateLogic` and the system panel intents are unchanged. No new strings: labels and values reuse existing translations.
- The edit-mode card (`EditCardGeometry`, feature-grid) is untouched.

## Why

Owner feedback on build 41: "Don't let the home peek so much when in the launchpad. The top 4 options should be icons, not text. The bottom blue thing should not show. Only the red you show as home. The screen zooms out and shifts." plus a screen recording of the reference interaction (card slides sideways in about 0.5 s, shrinks, rounds, dims, leaves a sliver).

## What the old spec said

STR-01 (wave 8): the home becomes a card about two thirds size pushed right, the freed background column houses the notifications and a neighbouring card peeks in below. STR-10: text tiles (value and caption), wrapped in rows of two when narrow. Tests that encoded this were rewritten, not removed: `LaunchpadCardGeometryTest` (two-thirds card, neighbour, background column), `RevealMathTest.STR_01_bothOrientationsKeepTheHomeAsACard` (strip and drag ranges), `LaunchpadCardCases` (overlay position), `LaunchpadScreenshotCases.STR_10_*` (text assertions).

## Known limits

- The reference frame rate and feel are only checked on a device (device checklist rows 2, 4 and 12).
- The panel is static, so a left swipe inside it closes only through its own gesture and the sliver; the old panel slide-in used when no card is present is unchanged.
