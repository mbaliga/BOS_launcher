# Wave 23 a11yPolish: TalkBack states, compact landscape Radar, notch hit area, accessibility sweep

Nothing here was seen or heard on a device. Tests are Robolectric and pure JVM: no real IME, cutout, gesture bar or TalkBack. Screenshots read:
`feature-grid/build/screenshots/w23-orbit-small-landscape-keyboard.png`, `w23-flat-small-landscape-keyboard-font2.png`,
`w23-filmstrip-small-landscape-keyboard.png`, `glance_notch_hit_area.png`. Decisions: docs/decisions/radarGlass.md (wave 23 section), docs/decisions/a11ySweep.md.

## What changed

1. **Radar mic state.** The mic exposes a state description (Ready, Listening, "Unavailable, microphone permission is off") and a polite live region; eight languages in `L10nW23a11yPolish.kt`. Tests `NFR_13_theMicSaysUnavailableUntilTheMicrophonePermissionIsGranted`, `PAL_18_inlineMicStillHasItsStateAndTheResultsGetTheFreedHeight`, `NFR_13_eachVoiceStateHasItsOwnWordsAndAllEightLanguagesTranslateThem`.
2. **Adaptive mic.** Under 220 dp of free height under the field (landscape, keyboard up) the 72 dp strip is not reserved and the mic is a 48 dp glass circle in the field row; otherwise unchanged. Bounds matrix in `RadarGlassW22Test` (Orbit, Orbit typed, Filmstrip, Flat; small and large landscape, portrait; font 1.0 and 2.0; keyboard faked closed and open).
3. **Notch hit area.** The drawn notch is now 48 dp tall (it was 32 dp with 48 dp targets), so the touch area is what is drawn; a tap below it reaches the home card.
4. **Sweep.** `A11ySweepTourTest` + `A11yAudit` + `A11yExceptions` (app), `A11yPolishW23AuditTest` (core-quality). Found and fixed: Ground Control search and close icons (40 dp, pointer-only), no heading on Ground Control, tabs and rows without roles, Launchpad menu and Home chip, drawer view switch / sort / tiles, Radar rows, a 42 dp Radar row, mis-roled and untranslated Ground Control stepper arrows, the clock taps (pointer-only, three sites), the page-indicator dots (pointer-only), no pane title on Launchpad and Recents, and the Glance "Previous" label that had no translation (the translation test had passed by accident).

## Per requirement

| ID | status | note |
|---|---|---|
| NFR-13 | partial | The sweep now covers Glance (controls shown), Launchpad, Recents, drawer, Radar (rest and typed), Ground Control top level, portrait and landscape, and passes: labels, roles, hit areas, states, a heading in Recents and Ground Control, pane titles on Launchpad and Recents. Honest limits: the 48 dp check is the hit-test area, which Compose widens to 48 dp for small clickables, so drawn controls under 48 dp remain (drawer view chips 40 x 36 dp, sort chip, the Radar field, Ground Control's stacked stepper arrows 38 x 24 dp, the Glance title line); the list is written to `app/build/a11y-small-layouts.txt`. The empty Radar and drawer text fields announce as an edit box without "Search" (a description would hide the typed text). Reading order, how TalkBack speaks the states, focus after a pane opens: device only (see docs/device-checklist.md). Not swept: the other Ground Control panes and sheets, the edit card, onboarding, the dock. |
| NFR-14 | partial | Nothing here animates (the mic state is not an animation); the live region is polite. The sweep does not test motion preferences. Device run with animations removed stays open. |
| PAL-18 | built | Adaptive mic position tested over the matrix; screenshots read (mic beside the field, results keep the height). Wave 21 and 22 decisions unchanged elsewhere. Not seen on a device. |
| PAL-20 | built | Unchanged. Result rows got a button role and a 48 dp minimum height (Flat); long-press and right-click preview paths are untouched. |
| INP-12 | partial | Unchanged gap: the real soft keyboard, its height and the insets are device-only (Robolectric fakes the IME inset). The adaptive mic is driven by the measured free height, so it follows a real keyboard, but that was not seen. |
| HOM-23 | built | Notch drawn as tall as its targets; test and screenshot. The Glance control labels (Previous, Next) are now translated. The Glance clock tap and page-indicator taps have click actions. |
| STR-06 | built | Same notch change covers the media buttons (48 dp, inside the drawn notch, labels unchanged). Not run with a real media session or a device cutout. |
| STR-01 | built | Launchpad announces itself with a pane title; its menu and Home chip have button roles and 48 dp. Swept in both orientations. Device: TalkBack announcement and focus after open. |
| STR-14 | built | Recents panel has a pane title, a marked heading and labelled 48 dp rows and Clear all (swept). Device: TalkBack announcement. |

## Residue (device or owner)

- TalkBack on a device: the mic state wording and timing, the pane title announcements, reading order of Radar, Launchpad, Recents, Ground Control; the empty search field's announcement.
- A real keyboard in landscape on a small phone: whether 220 dp is the right switch point (owner/device tuning; the constant is `RadarMicPlacement.INLINE_BELOW`).
- Drawn controls under 48 dp (list above): owner decision whether to enlarge them visually; their hit area is already 48 dp.
- The other Ground Control panes, the edit card and onboarding were not swept.
