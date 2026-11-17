# Wave 21: Radar glass, mic at the bottom centre, X inside the field

Owner: "For the Radar, keep the mic icon for voice input in the centre at the bottom. Don't make the field and button that kind of grey. Make it glass. Put the X inside the field." (reading and decisions in docs/decisions/radarGlass.md)

| ID | status | note |
|---|---|---|
| PAL-18 | built | Glass field, scope pill, Close and mic (`Glass.kt`: `Modifier.glass`, `GlassButton`, `GlassField`); mic at the bottom centre in Orbit, Filmstrip and Flat, portrait and landscape, with its strip reserved; Clear X inside the field (only with text), Close outside while empty, never both; scope pill inside the field at the leading end; right-to-left mirrors. Tests `RadarGlassW21UiTest` (bounds, 56 dp, centred, no overlap with results, X inside the field, mirrors, faked keyboard and gesture-bar insets) and read screenshots `w21-*.png` (orbit portrait, typed orbit, flat typed, filmstrip, landscape, Beam and Spire backgrounds, RTL). Not seen on a device. No real backdrop blur (documented). |
| PAL-20 | built | Preview and pinch unchanged; the mic strip is outside the result area. Existing preview tests pass. |
| INP-12 | built | Field still at the top with focus on open; mic above the keyboard (`imePadding`), checked with faked IME insets only. Existing wave 18 tests that encoded the old row (Clear beside the field in Flat, 48 dp Clear/Close) were updated, see below. |
| NFR-13 | built | Every target of the two bars is 48 dp or more (Clear, Close, scope pill, mic 56 dp), labelled, focusable (`NFR_13_everyTargetOfTheTopAndBottomBarsIs48dpOrMore`). TalkBack not run. |
| NFR-15 | built | Glass text and placeholder measured over every valid background pair, both intensities (default pair), portrait and landscape, levels 0 and 4, held-out clock values: minimum 6.25:1 for text and 4.70:1 for the placeholder (`RadarGlassContrastW21Test`). The glass is darkened only where the measured background needs it (`GlassShade`, off the main thread, once per background and size). Default pair shades: top 0.37, bottom 0.40. |
| NFR-05 | not measured | The glass draws from brushes cached per size (`drawWithCache`), nothing allocated per frame, no `RenderEffect`; the shade measurement runs once off the main thread. No frame-time measurement was made; the device checklist row asks for one. |

## Existing tests

No existing test failed after the change (full `./gradlew testDebugUnitTest assembleRelease --offline`: 1650 tests, 0 failures; release APK built). The wave 18 and 20 tests read the field by its text action and the Clear/Close buttons by description, which still hold; `RadarHostileQueriesW20Test.NFR_13_theClearAndCloseButtonsOfTheQueryLineAre48dpTouchTargets` (Flat, typed: Clear is 48 dp) now measures the X inside the field and carries a note saying so. Nothing was weakened; the new tests add the positions the owner asked for.
