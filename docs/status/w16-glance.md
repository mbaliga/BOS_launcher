# w16-glance: status

Stream: the Glance home (default landing, clock and date, now-playing with pop-in controls, weather ambience, hotspots 0 to 4, surface indicator) over the two-slot background of wave 15. Decisions: `docs/decisions/glance.md`. Nothing here was confirmed on a device; device rows are in `docs/device-checklist.md`, "Glance home (wave 16, HOM-23)".

| ID | status | note |
| --- | --- | --- |
| HOM-23 | done, device pending | `GlanceSurface` (feature-grid `GlanceHome.kt`), pure model in core-data `GlanceModel.kt`, weather ambience `GlanceWeatherLayer.kt`. Content presets and free toggles, tap-to-pop controls with the `ControlsPop` state machine, hotspots by physical corner, indicator, settings in Ground Control > Home and the quick sheet. Screenshots (Robolectric, not a device) in `feature-grid/build/screenshots/glance_portrait_dark.png`, `glance_landscape_dark.png`, `glance_portrait_light.png`, `glance_portrait_rtl.png`, `glance_portrait_controls.png`, `glance_portrait_font200.png`, `glance_portrait_rain.png`, `glance_portrait_nothing_playing.png`, `glance_portrait_no_hotspots.png`. |
| HOM-22 | done | Glance is the fourth landing (enum name `Minimal` kept, label "Glance") and the default; migration through `LandingMigration` and the `landingChosen` marker (a stored Selected apps cannot be told from the old default, so it moves to Glance; Recent apps and All apps are kept). Existing tests that encoded the old default were updated with a note (see below). |
| HOM-01 | done | Spec row updated: Selected apps is no longer the default landing; layouts untouched; the empty-home hint shows only on Selected apps. |
| INP-16 | done | A disabled hotspot has no dwell reveal and does nothing (`glanceOn` filter on `cornerLabel` and `onCornerActivate`). |
| INP-17 | done | Each enabled hotspot is a focus node (`minimal.corner.*`, same ids), a disabled one is not composed; the clock and the music line are focus nodes too. |
| STR-06 | done | The Glance music line and pop-in controls read the existing `MediaNow` source; hidden when nothing plays, layout stable. |
| NFR-05 | partly, device pending | One shared clock read in draw lambdas, precomputed shapes, no allocation per frame by construction, nothing animates when the screen is off or the surface is hidden. No profiling was done. |
| NFR-14 | done | Reduced motion: controls fade, the weather is one still frame, the marquee and the indicator fade are off or instant; the audit allow-list records the one `animate*AsState`. |
| NFR-15 | partly | The text sits on the existing scrim of the background (`HomeScrim`); the weather layer is low contrast and has its own colour/alpha test (`GlanceWeatherContrastTest`). Not sampled against every background and weather pair on screen. |

## Existing tests changed because they encoded the old default (wave 16)

- `AppSmokeHarness.configure` now starts the Selected apps landing (explicit choice); new `configureFresh` keeps the default. `EditCardAppTest.startFresh` and one `AppSmokeTest` fresh-home test do the same.
- `HomeLandingTest`, `W13AuditStateTest`: the default and the "older file without a landing" now read Glance.
- `HomeLandingUiTest`, `HomeLandingQuickSheetTest`, `AppSmokeTest`: the fourth landing is labelled Glance, its description changed, the hotspots are the factory corners, and the Glance quick sheet is longer (the "Edit home pages" entry is scrolled to).
- `W4MotionAuditTest`: the audit allow-list lists `GlanceHome.kt` with one gated fade.

## Review pass (adversarial finisher)

- Defect fixed: the Glance indicator's spoken description said "Recent apps are on the right edge" whenever the right side was not Off, including when it is the Dock rail. It now names each destination honestly (new string "Dock is on the right edge", 8 languages).
- Checked by reading: default and migration (`LandingMigration`, marker only written for a non-default or an earlier choice), HomeScreen wiring (`GlanceSurface` composed on the Glance landing, corner dwell label and activation filtered by enabled hotspots, one media source), no hidden APIs added, the recents file and its privacy class are untouched by this stream, weather layer draws from precomputed arrays and brushes, reduced motion gates, clock kept LTR in RTL while the indicator mirrors.
- Not verified here: any behaviour on a device, profiling (NFR-05), contrast over every background and weather pair (NFR-15).

## Residue

- Device checks (checklist rows above).
- The "Show corner dots" setting (`minimalHints`) is no longer drawn (the hotspots draw their action glyph); the pref is kept so exports round-trip.
- Hotspots with a palette run their first item; widgets and complications in a corner are not hotspots on Glance.
