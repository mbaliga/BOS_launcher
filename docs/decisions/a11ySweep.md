# Whole-app TalkBack and semantics sweep, notch hit area (wave 23; NFR-13, NFR-14, HOM-23, STR-06, STR-01, STR-14)

## The notch hit area (HOM-23, STR-06)

The Glance media notch drew a 32 dp black tab under the cutout while its three buttons were 48 dp, so about 16 dp of the touch area was invisible and a tap
just under the black hit a button. **Decision: make the drawn notch as tall as the targets** (`GlanceNotch.BODY_HEIGHT_DP` 48, icon centred, `ICON_TOP_DP` 13)
rather than clip the buttons to 32 dp (48 dp is the requirement for the three transport buttons). The box, the black shape and the targets are now the same
height; a tap below the black falls through to the home card. The title line under the notch sits 16 dp lower. Tests: `HOM_23_theTouchAreaIsExactly...`
(`GlanceNotchUiTest`), `GlanceNotchGeometryTest`; screenshot `glance_notch_hit_area.png` read.

## The sweep (NFR-13, NFR-14)

`A11ySweepTourTest` (app module) walks the real `MainActivity` under Robolectric to Glance with the controls shown, Launchpad, the Recents panel, the drawer, Radar
(at rest and typed) and Ground Control's top level, in portrait and landscape, and `A11yAudit` reads the merged semantics tree (what TalkBack reads) of each:

- every node with a click action has a non-empty label, a role, and a touch area of at least 48 dp both ways (the hit-test area, see below);
- every switch, checkbox, radio and tab exposes its state; Ground Control and the Recents panel have a marked heading; Launchpad and Recents have a pane title.
- Exceptions are listed with a reason in `A11yExceptions`: text fields (no Compose role; a description would replace the typed text in what TalkBack reads).

What the first run found and was fixed: Ground Control's Search and Close icons were 40 dp pointer-only targets (no click action, no role); the "GROUND CONTROL"
title was not a heading; its category tabs and rows, the Launchpad menu and Home chip, the drawer's view switch (now radio buttons with their selected state),
sort chip, app tiles, and Radar's result rows had no role; the Radar "Package contains" row was 42 dp; Ground Control's choice stepper arrows had a wrong
(Image) role and English-only names (now translated); the Launchpad and the Recents panel did not announce themselves (now `paneTitle`); the page-indicator
dots on the Selected home had no click action (pointer only).
`A11yPolishW23AuditTest` (core-quality) is the source-side companion: no named control may listen to taps only through `pointerInput`.

Limits, stated so they are not mistaken for device results:
- **The 48 dp check uses the hit-test area.** Compose widens the hit area of a small clickable to 48 dp, so a 36 dp drawn chip passes; the sweep writes the
  controls whose drawn layout is under 48 dp to `app/build/a11y-small-layouts.txt` (drawer view chips and sort chip, the Radar field, the Glance title line,
  Ground Control's stacked stepper arrows). They are reachable but visually small and their widened areas can touch; that is an owner/device judgement.
- Reading order, how TalkBack actually speaks each state, focus after a pane opens, and switch access are device-only. The tests check properties of the tree.
- The empty Radar field is announced as an edit box without the word "Search" (a content description would hide the typed text from TalkBack); device check.
