# Macrobenchmark and baseline profile sources (NFR-01 to NFR-05)

**This directory is not part of the Gradle build** (it is not in `settings.gradle.kts`), because `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator` and the `androidx.baselineprofile` Gradle plugin are not in the offline Gradle cache of the build machine (checked: `ls ~/.gradle/caches/modules-2/files-2.1 | grep -i -E "benchmark|uiautomator|baselineprofile"` returns nothing). Nothing in it has been compiled or run.

## Enabling it

1. On a machine with network access, add `include(":benchmark")` to `settings.gradle.kts`.
2. Add to `gradle/libs.versions.toml`: `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`, `androidx-test-ext-junit`, and the plugin `androidx.baselineprofile` (versions current at that time; the sources use only the stable `MacrobenchmarkRule`, `BaselineProfileRule`, `StartupTimingMetric`, `FrameTimingMetric`, `TraceSectionMetric` APIs).
3. Apply the plugin and a `benchmark` build type to `:app` (`profileable`, signed with the debug key, same R8 settings as release), as `build.gradle.kts` here shows.
4. Run on the mid-range reference device (not an emulator): `./gradlew :benchmark:connectedBenchmarkAndroidTest`, and `./gradlew :app:generateBaselineProfile` to regenerate the profile.

## What is here

| File | Covers |
| --- | --- |
| `HomeBenchmarks.kt` | NFR-01 warm home press (150 ms p95), NFR-02 cold start (700 ms p95), NFR-05 janky frames on home and drawer scroll (at most 1%) |
| `PaletteBenchmarks.kt` | NFR-03 palette open to first results (100 ms p95), NFR-04 keystroke to updated results (50 ms p95) |
| `BaselineProfileGenerator.kt` | Generates the profile for the same journeys (start, home, drawer, palette open and type) |
| `build.gradle.kts` | Module build file (inactive) |

The budgets are in `core-quality` (`Budgets`), and `BudgetsTest` pins their values to the spec. Palette timing uses trace sections named `Liftoff.paletteOpen`, `Liftoff.paletteFirstResults` and `Liftoff.paletteKeystroke`; the palette code does not emit them yet (the palette is owned by another stream), so NFR-03 and NFR-04 need those `Trace.beginSection` calls added there first.

Until this module can run, `tools/perf.sh` gives adb-only `am start -W` and `gfxinfo` numbers for NFR-01, NFR-02 and NFR-05.

The hand-written `app/src/main/baseline-prof.txt` is already shipped in the release build and is replaced by the generated one when step 4 has been done.
