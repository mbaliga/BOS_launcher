# Wave 55: calmStyles status

Nothing here is device-confirmed. JVM and Robolectric only. Screenshots read: `feature-grid/build/screenshots/calm55-*.png`.

| ID | Status | Note |
| --- | --- | --- |
| PAL-24 | built (device untested) | One Calm style: Off, Regular, Edge dots, Corner bloom, Bottom glow, Edge beam; all ambient styles drawn on top at most 0.35 alpha (pixel tests: centre 80 percent mean overlay below 0.04, nothing in the middle 60 percent, every severity for Signal and Battery); Alert effect, vignette, comic lines removed; slow motion; still frame under reduced motion and battery saver; default follows the wallpaper kind |
| HOM-23 | built (device untested) | Regular battery and signal indicators in `FixedHomeChrome` (every page), centred under the music line, clear of the top hotspots, right-to-left mirrored; "Also show regular battery and signal" |
| CFG-23 | built (device untested) | Calm style as drawn cards with an enum-coverage test, the Alert row is gone, the switch row only beside an ambient style |
| CFG-01 / CFG-03 | built | `calmStyle` / `calmRegular` exported by reflection; `indCalm`, `indAlert`, `indEffects`, `indInvert` migrated once and retired (after migration) |

## Tests
- core-data `CalmStylesW55Test` (replaces `IndicatorEffectsW35Test`; severity tests kept): style model, default per wallpaper kind, regular rule, wave 35 and older migrations, store round trip, retired keys, regular indicator content.
- feature-grid `CalmStylesW55UiTest` (pixel non-occlusion per style and severity, cap, intensity, still frames, Beam LTR and RTL and length, default draws nothing on non-Signal kinds), `RegularIndicatorsW55UiTest` (content, portrait, landscape, RTL, with and without top hotspots and music), `IndicatorMathTest` (rewritten), `SettingsArtW54Test` (Calm style cards).
- app `CalmStyleRowsW55Test` (replaces `IndicatorRowsW35Test`), `SettingsVisualW54Test` (row names).
- Deleted: `IndicatorAlertW35UiTest` (alert effects, Radar alert layer), the comic-lines and vignette parts of `IndicatorMathTest`.
- Strings: `L10nW55calmStyles.kt` (15 strings, 8 languages); the dead effect strings of waves 14 to 35 were removed from the language files.

## What the screenshots show (read)
- Edge dots, Signal lost over a striped four-colour picture: two thin rings of small red dots along the edges, nothing in the middle. Corner bloom, Bottom glow and Edge beam healthy: faint but visible accents at the corners, the bottom edge and the right edge.
- Regular, portrait: signal bars and a battery with a bolt and "87%" centred under the music line between the two top hotspots; lost signal shows a red slash over dim bars, a 12 percent battery shows a red fill.

## Device only
Real signal and battery readings, the look over live wallpapers, video and GIF, battery saver, TalkBack, real frame cost.
