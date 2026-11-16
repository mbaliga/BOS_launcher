# Wave 9: cardTune (STR-01, STR-07, STR-08, STR-10)

| ID | Status | Note |
| --- | --- | --- |
| STR-01 | built | Card at rest is about 0.93 scale with a 7 percent sliver at the edge (both orientations, mirrored in RTL), no vertical shift, 28 dp corners, a black scrim rising to 0.5 and a hairline lighter edge on the leading side, all from one progress value (`LaunchpadCardGeometry`, `homeCardLayer` read inside graphicsLayer and drawWithContent lambdas). Neighbour card removed (drawing, geometry, tests, docs). Panel is full width and static, content inset by the return zone and faded in over the last 55 percent. Drag travel equals the card's edge travel (1:1); settle is a 0.45 s decelerate tween (`LaunchpadRevealState.animateTo`). Return zone is the sliver widened to 48 dp. Tests `LaunchpadCardGeometryTest` (sliver, scrim, content fade, 1:1, return zone, RTL, reduced motion), `RevealMathTest` (strip and drag assertions updated with a note), `LaunchpadCardCases` (overlay at the edge). Spec row and `docs/decisions/cardTune.md` updated. Motion feel and frame rate unverified on a device (checklist rows 2, 4, 12 in `docs/device-checklist-launchpad.md`). |
| STR-07 | built | Screenshot matrix with the card regenerated, both orientations, light and dark, RTL and mid-drag peek: `feature-launchpad/build/screenshots/launchpad-card-<portrait|landscape>-<case>-<light|dark>.png` (for example `launchpad-card-portrait-grouped-light.png`, `launchpad-card-portrait-rtl-light.png`). |
| STR-08 | built | The sliver is still the focusable "Home card. Return to home" button (Enter, Esc, arrows unchanged); the four icon tiles are focusable with a 2 dp focus ring. Existing keyboard tests pass. |
| STR-10 | built | Four icon-only vector buttons in one row (`SystemStateRow.kt`: clock with real hands, battery with level and bolt, Wi-Fi, bars, wired, link or offline icon, speaker, vibrate or muted icon), content descriptions such as "Battery: 82%, charging", click opens the system panel, focus ring. Tests `STR_10_card_systemRowIsFourIconButtonsWithoutText`, `STR_10_card_iconTileOpensItsSystemPanelAndIsFocusable`, `STR_10_systemRowShowsStateWithoutClaimingToToggle` (updated from text to descriptions). No new strings, so no new L10n table. |

## Spec rows changed

STR-01 and STR-10 (wording), STR-07 (sliver). Decision note: `docs/decisions/cardTune.md`.

## Review (adversarial pass)

Checked the diff, portrait/landscape/RTL/peek screenshots (narrow slice, no neighbour, four icon tiles), geometry (card left edge = 0.93 of width at rest, drag 1:1), return zone (48 dp, content inset to match), tile semantics and focus order, and stale neighbour references (none left). No defects found; testDebugUnitTest and assembleRelease pass.
