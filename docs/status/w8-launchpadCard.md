# Wave 8: launchpadCard (STR-01, STR-02, STR-07, STR-08, STR-09, HOM-09)

| ID | Status | Note |
| --- | --- | --- |
| STR-01 | built | Launchpad is the background of a shrunken home card in both orientations (right side, left in RTL). Pure model `LaunchpadCardGeometry` (feature-launchpad `LaunchpadCardGeometry.kt`), applied as one graphicsLayer `homeCardLayer` on the existing home in `HomeScreen.kt`; peek and spring-back kept (`RevealMath`, mirrored edge drag); tap, drag back, Back, Esc, handle and shortcut return. Reduced motion snaps the card and cross-fades the backdrop. Tests `LaunchpadCardGeometryTest` (JVM: pose, bounds, RTL, reduced motion, neighbour, edit-mode side), `RevealMathTest` (old portrait-takes-the-screen assertion rewritten), `LaunchpadCardPortraitTest` and `LaunchpadCardLandscapeTest` (Robolectric). Spec row and `docs/decisions/launchpadCard.md` updated. Frame rate on a device unverified. |
| STR-02 | built | Rows (grouping, actions, inline reply, dismiss) are unchanged; they now sit under the selected section title in the background column. Existing interaction, reply, touch and flow tests pass unchanged. |
| STR-07 | built | Screenshots with the card in place, both orientations: grouped, digest, empty, no grant in light and dark, a mid-drag peek (fraction 0.4) and a right-to-left case, written to `feature-launchpad/build/screenshots/launchpad-card-<orientation>-<case>-<light|dark>.png` (for example `launchpad-card-portrait-peek-light.png`). The older content-only matrix still runs. Device checklist rows added in `docs/device-checklist-launchpad.md`. |
| STR-08 | built | Arrows walk titles then rows of the selected section (existing `LaunchpadFocus`), Left or Right fold and unfold, Esc returns with focus restored; the card is a focusable button (Tab, Enter). Titles are tabs with a selected state, the card has a spoken label (new strings in `L10nW8launchpadCard.kt`, eight languages). Tests `STR_08_card_*`, `NFR_13_card_*`, existing `LaunchpadInteractionTest`. |
| STR-09 | built | The waiting indicator, handle and rail sit on the notifications edge, mirrored to the right in RTL (`HomeScreen.kt` one-line change). No new test beyond the existing `WaitingHeadlineTest`. |
| HOM-09 | built | Badges are not touched by this stream; the existing tests still pass. |

## Spec rows changed

STR-01 (card layout, both orientations, RTL, reduced motion), STR-02 (where rows appear), STR-07 (matrix with the card), STR-08 (titles, card reachable), STR-09 (edge in RTL). Decision note: `docs/decisions/launchpadCard.md`.

## Residue

| ID | Needs |
| --- | --- |
| STR-01 | Device steps (wave 8 section of `docs/device-checklist-launchpad.md`): 60 fps of the shrink, RTL drag, TalkBack, reduced motion. |
| STR-09 | Device check of the indicator and handle on the right edge in an RTL language. |
| HOM-09 | None from this stream. |
