# Performance, memory, battery, size and soak (NFR-01 to NFR-10)

How each budget is measured and what exists today. Nothing has been measured on a device: every number below is a target. Budgets are in `core-quality` `Budgets`, pinned to the spec by `BudgetsTest`.

| ID | Budget | Measured by | State |
| --- | --- | --- | --- |
| NFR-01 | Warm home 150 ms p95 | `benchmark/HomeBenchmarks` (inactive, see below); `tools/perf.sh` (`am start -W`) | script written, not run |
| NFR-02 | Cold start 700 ms p95 | same | same; hand-written baseline profile ships in `app/src/main/baseline-prof.txt` |
| NFR-03 | Palette open 100 ms p95 | `benchmark/PaletteBenchmarks` | needs trace sections in the palette (other stream) |
| NFR-04 | Keystroke 50 ms p95 | same | same |
| NFR-05 | At most 1% janky frames | `benchmark/HomeBenchmarks`; `tools/perf.sh` (gfxinfo) | script written, not run |
| NFR-06 | 180 MB PSS with 10 widgets | `tools/measure_pss.sh` | script written, not run |
| NFR-07 | No wake locks, 0.5% battery per day | `tools/measure_battery.sh`; `NFR_07_noWakeLocksInProductionSources` | wake-lock check built; battery run not done |
| NFR-08 | Under 25 MB download | `./gradlew :app:checkReleaseApkSize --offline` (fails at 25 MB or more) | built; the release APK is the stand-in for download size (no AAB is built) |
| NFR-09 | 99.8% crash-free sessions | local ledger in `CrashReports` (session start and crash counts); aggregated by the owner from shared reports | built (local only), no cohort yet |
| NFR-10 | No ANR in a 2-hour soak | `tools/soak.sh` (default 7200 s, run nightly) | script written, not run |

## Why the macrobenchmark module is inactive

`androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator` and the baseline-profile Gradle plugin cannot be resolved offline here, so `benchmark/` holds the sources and a build file that is not in `settings.gradle.kts`. See `benchmark/README.md` for enabling it. The "enforced by macrobenchmark tests in CI" sentence of the spec section 13 is therefore not met yet.

## Crash reporting policy (NFR-09, NFR-17)

Crash reports are written by `CrashReports` to `files/crash-reports/` in app-private storage, chained in front of whatever uncaught-exception handler already exists. There is no upload. A report leaves the device only if the user shares its text by an explicit action. The share control in Ground Control is not built yet (status: partial). A per-session ledger in the same directory gives the crash-free percentage locally.

## Soak loop (Soak row of the test plan)

`tools/soak.sh [seconds]`: Home, drawer swipe, typing, back, rotation, launching another app, recents round trip and a process kill every 50 iterations, for 7200 s by default, with an ANR and crash check every 10 iterations. On failure it leaves `tools/soak-out/` (logcat, process dump, `/data/anr`). Schedule it nightly on the mid-range device from a host with adb.
