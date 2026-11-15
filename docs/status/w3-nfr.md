# Wave 3: NFR stream

Nothing here is device-verified. Every NFR with a budget stays partial until the number is written into `docs/device-checklist.md` section 13. `tools/device_nfr.sh` now runs the whole device sequence as one command (perf.sh, measure_pss.sh, the macrobenchmark through `-Pliftoff.benchmark=true`, optional soak). The macrobenchmark module is included only with that Gradle property, because `androidx.benchmark` is not in the offline cache; the `androidx.baselineprofile` plugin was removed from it (the generator uses the plain `BaselineProfileRule`) so the module has one fewer thing to resolve. That module was never compiled here.

| ID | Status | Note |
| --- | --- | --- |
| NFR-01 | partial | Added: opt-in `:benchmark` include and `tools/device_nfr.sh` (one command), checklist row with pass condition and where to record. Test `NFR_01_NFR_05_macrobenchmarkIsOneGradleFlag...`. Residue: run `tools/device_nfr.sh` on the mid-range device; warm p95 must be at most 150 ms. |
| NFR-02 | partial | Same tooling. Residue: cold start p95 at most 700 ms, from the same run. |
| NFR-03 | partial | Trace names unchanged and pinned. Residue: `PaletteBenchmarks` on a device; p95 at most 100 ms. |
| NFR-04 | partial | Residue: `PaletteBenchmarks` keystroke, p95 at most 50 ms; the span covers app results only (stated in the checklist row). |
| NFR-05 | partial | Reduced-motion work removed more per-frame loops when animations are off. Residue: gfxinfo and `FrameTimingMetric` run, at most 1% janky. |
| NFR-06 | partial | Residue: `tools/measure_pss.sh` with 10 widgets, 180 MB. |
| NFR-07 | partial | Residue: overnight `tools/measure_battery.sh`, 0.5% per day. |
| NFR-09 | partial | Needs the beta cohort; the local figure and share path exist (wave 2). |
| NFR-10 | partial | Residue: 2 h `tools/soak.sh` (`tools/device_nfr.sh --soak`). |
| NFR-12 | partial | Audits unchanged and green. Known remaining, unavoidable: the first `LauncherStores.get` in `MainActivity.onCreate` waits for the preference files (home needs the theme and layout to draw; `PrefsWarmup` starts earlier in `Application.onCreate`). Residue: debug run with StrictMode logging (checklist row). |
| NFR-13 | partial | New: Filmstrip tiles had no name for a screen reader (only the centred caption was shown); each tile is now a merged node with its caption. Flat section headers are headings. New Robolectric tests `OrbitA11yTest`: Orbit reads best match first with names; Filmstrip tiles carry names in strip order. Source audit pins all three Radar layouts. With wave 2 (XMB, puck, Continuous, DSi) every home style and Radar layout now has a tested order or names. Residue: a TalkBack run (checklist row). |
| NFR-14 | partial | New and gated by the preference: Orbit rings, Radar sweep and Picture backdrop drift, blinking caret (stays solid), the settings and XMB wave field (one frame), the indicator layer (still frame), home page paging (jumps, `pageTo`), XMB spring (snaps), DSi press spring. New audit `NFR_14_everyInfiniteAnimation...` fails for any file that uses an infinite transition without reading the motion preference or the animator scale. Not done: the Launchpad slide is still a short linear slide rather than a cross-fade, the host-driven `goToPage(animate = true)` path, and the many one-shot `animate*AsState` micro-transitions (knob, bar alpha), which are short and not motion-heavy. Residue: device run with animations removed. |
| NFR-15 | partial | New: textures are sampled from their rendered tile for the home scrim (was the mid-grey fallback). New `SurfaceContrastTest` (core-design): ink and bright ink reach 4.5:1 on background and surface in Dark, Amoled and Light; dim ink reaches 3:1; text on the accent reaches 3:1 for the sampled accents. Still not sampled: live scenes and the bundled Horizon picture (procedural, mid-grey fallback); `Plan.reachesAa` is not surfaced in the UI. Residue: contrast check on a device over each background kind. |
| NFR-17 | partial | Unchanged from wave 2 plus a checklist step that confirms there is no traffic. Residue: device exercise; strings are translated (wave 2). |
| NFR-19 | partial | Unchanged; section 9 of the checklist is the closing step. Residue: both navigation modes on a notch device; Launchpad's handler stays enabled during its exit animation. |

Not in my list: NFR-11 gained a pass/fail row in the checklist table only.

## Shared-file edits (small, additive)

`GridHome.kt` (paging helper), `XmbHome.kt`, `ContinuousHome.kt` (press spring), `SearchLayouts.kt` (Orbit parameter renamed `motionRequested`, Filmstrip semantics, Flat headings), `SearchOverlay.kt` (one argument name), `SearchChrome.kt`, `SettingsSpace.kt` (wave field), `IndicatorLayer.kt`, `Backgrounds.kt` (`textureSampleColors`), `HomeLegibility.kt`, `settings.gradle.kts` (opt-in include), `benchmark/build.gradle.kts`. No new strings, so no L10n table was needed.
