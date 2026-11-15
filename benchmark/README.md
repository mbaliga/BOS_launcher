# Macrobenchmark and baseline profile sources (NFR-01 to NFR-05)

**This module is opt-in.** `settings.gradle.kts` includes it only with `-Pliftoff.benchmark=true`, because `androidx.benchmark:benchmark-macro-junit4` and `androidx.test.uiautomator` are not in the offline Gradle cache (needs network once). It has never been compiled here.

## Running it

On the mid-range reference device (not an emulator), with network access: `tools/device_nfr.sh` (runs everything), or `./gradlew -Pliftoff.benchmark=true :benchmark:connectedBenchmarkAndroidTest`. The `:app` `benchmark` build type (release code, debug key, profileable) already exists. The baseline-profile Gradle plugin is not applied; the generator uses the plain `BaselineProfileRule`.

## What is here

| File | Covers |
| --- | --- |
| `HomeBenchmarks.kt` | NFR-01 warm home press (150 ms p95), NFR-02 cold start (700 ms p95), NFR-05 janky frames on home and drawer scroll (at most 1%) |
| `PaletteBenchmarks.kt` | NFR-03 palette open to first results (100 ms p95), NFR-04 keystroke to updated results (50 ms p95) |
| `BaselineProfileGenerator.kt` | Generates the profile for the same journeys (start, home, drawer, palette open and type) |
| `build.gradle.kts` | Module build file (opt-in) |

The budgets are in `core-quality` (`Budgets`), and `BudgetsTest` pins their values to the spec. Palette timing uses trace sections named `Liftoff.paletteOpen`, `Liftoff.paletteFirstResults` and `Liftoff.paletteKeystroke`; the palette emits them (`PaletteTrace.kt`).

Until this module can run, `tools/perf.sh` gives adb-only `am start -W` and `gfxinfo` numbers for NFR-01, NFR-02 and NFR-05.

The hand-written `app/src/main/baseline-prof.txt` is already shipped in the release build and is replaced by the generated one when step 4 has been done.
