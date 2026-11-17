# Startup crashes from old persisted state (wave 18, part 2)

Owner report: after upgrading over older builds, Liftoff "stopped twice in a row right after starting" and showed Safe mode. This page is the hunt for a
real startup crash caused by state an EARLIER build wrote, what was found, and the honest answer about the owner's two crashes.

## Method

`app/src/test/kotlin/com/bos/sphere/UpgradeSmokeTest.kt` (the `AppSmokeHarness` pattern: the real `MainActivity` and `HomeScreen` under Robolectric) seeds the
stores with the state shapes earlier builds wrote, starts `MainActivity`, and asserts that nothing is thrown on any thread (an uncaught-exception hook is installed for
the test) and that the state is sensible. The shapes come from git history, not from memory:

| Era | Source in git | What is seeded |
|---|---|---|
| build 31 | `core-data/src/test/resources/build31/*.xml`, the real files of an installed build 31, copied byte for byte | six preference files; the build-31 bridge runs |
| build 40, waves 1 to 12 | `LauncherStores.kt` at `3135231` (every `put*` key and its type) | the whole `launcher_prefs` key set, `style` Sphere / Bubbles, `drawerView` Bubbles, `bgKind` Gradient / Live / Picture with `bgPreset`, `bgA`, `bgB`, `bgPicture`, `wallpaper`, a `leftPage` Widget with `leftWidgetId`, an explicit `homeLanding` (`Selected` included, and no landing marker), `home_layout` with `widget:<id>` keys, launch history, corner widget slots, a removed action name; no recents file |
| builds 43 to 46 | `LauncherStores.kt` at `fe24bb9^` (the last build before the backgrounds removal) | `homeScroll`, `lpTheme`, `searchBdStyle`, `radarSweep`, `radarSignalView`, `radarComic`, `radarIntensity`, `radarSignalForce`, `homeLanding` Minimal, `recentsLimit`, groups and Launchpad rules files |
| builds 47 to 49 | current `LauncherStores.kt` | `bgSlot1` / `bgSlot2` with known, equal, two-wallpaper and unknown ids; `landingChosen` absent, false and true with every stored landing name; Glance keys of every damaged shape; the Recents landing with no recents file |
| state exports | `core-data/src/test/resources/state_v1.json`, `state_v2.json`, a v3 export written by the current build, and hand-damaged variants (removed enum names, wrong section types, a future version) | `StateService.import` then a start |
| damage | invented from the codecs | truncated and malformed JSON in every JSON-bearing store (layout, history, corners, groups, rules, theming, workspaces file, recents file, timeline log), and keys holding the wrong type |

Removed enum names that the git history shows (checked with a scan of every commit): `BackgroundKind` (Black, Gradient, Live, Picture, Solid, System, Texture),
`SearchBackdropStyle` (None, Picture, Radar), `HomeStyle.Bubbles` and `.Sphere`, `DrawerView.Bubbles`, and the stored landing name `Minimal` (now shown as Glance).
Nothing else was removed from an enum that persists by name. A scan of every commit also found no preference key whose stored TYPE changed between builds.

## Crashes found

| # | Crash | Failing test (written first) | Root cause | Fix |
|---|---|---|---|---|
| 1 | `ClassCastException` out of `LauncherStores.get`, thrown on the first frame, on every start, when any key in `launcher_prefs`, `home_layout`, `shortcuts`, `launch_history` or `device_bound` held a value of another type than the one now read | `UpgradeSmokeTest.NFR_11_everyPreferenceFileHoldingTheWrongTypeForAKeyStartsWithTheDefaultForThatKey` (failed with `ClassCastException: Integer cannot be cast to String`) | the platform's typed getters throw on a type mismatch; every store reads its file in its constructor | `TolerantPrefs` (core-data): a `SharedPreferences` whose getters return the default for a key of the wrong type, used by every launcher store that reads at start (the four above, hidden apps, icon colours, open surfaces, puck actions, action apps, groups, Launchpad rules, loop guard, the catalog stores and the desktop controller). `TolerantPrefsTest` covers it |
| 2 | (hardening, not reproduced) an exception inside the Recents flow (a failing usage source) would propagate out of the home's first composition | none failed; the flow had no error handling and `usageReader` was only guarded inside `UsageRecents` | `HomeRecents.flow` and `entriesFlow` had no `catch` | the usage reader is wrapped in `runCatching` and both flows `catch` to an empty list |

| 3 | (hardening, found by the suite) `IllegalArgumentException: Key ... was already used` out of the safe mode grid when the app list holds one key twice | the first run of `CFG_09_twoRapidStartsWithoutAnyCrashAreSafeModeToo` (the test's own double seeding of the launcher-apps shadow produced the duplicate); now `StateScreensTest.CFG_09_aDuplicatedAppKeyNeverCrashesTheEscapeHatch` | `SafeModeView` keyed its lazy grid by app key without de-duplicating | `distinctBy { it.key }`: the escape hatch must not be able to throw. A real `LauncherApps` list has one entry per component and user, so this is not believed to happen on a device |

Crash 1 was NOT shown to happen in practice: the scan of history found no key that changed type, so no earlier build wrote one. It is the one startup crash class the suite could
reach, and it is closed.

## Everything else that was reviewed and found safe

Read at every cold start, each already guarded (and covered by the suite where it can run headless): `LandingMigration` (unknown, empty and absent values all give Glance or the kept landing), the
background pair migration (`BackgroundChoice.fromStored`, unknown ids fall back, an invalid pair empties slot 2), `enumOr` for every enum key, the strip and indicator-effect lists (unknown names dropped),
`HomeLayoutCodec.decode`, `HomeStateCodec.decode`, `LaunchHistoryStore.read`, `ShortcutCodec` (all in `runCatching` or line by line), `UsageRecents` (the usage-access check, `queryEvents` and the loop all in
`runCatching`; usage access not granted gives an empty list, and the reads run on `Dispatchers.Default` from `HomeRecents`), `AndroidSignalSource` (null service, `registerDefaultNetworkCallback` and the
first read in `runCatching`), `rememberBatteryPercent` (scale guarded against zero; `registerReceiver` and `unregisterReceiver` guarded), `MainActivity.onCreate`'s own platform calls (`bridgeBuild31`, `guardPlatformRestore`,
the shortcut overrides and the destructive-edit hook in `runCatching`; `display?.displayId` guarded). `ZeroSizeStartSmokeTest` starts every landing in a 2 dp x 2 dp window: no division by zero or other exception.
Not covered by any test (cannot run headless): `RECEIVER_NOT_EXPORTED` registration on a real device, the real widget host with a stale widget id (the seeded stale id only shows that the Robolectric host tolerates it), real `UsageStatsManager`.

Main-thread I/O at start that remains (NFR-12, unchanged by this wave): the stores' first `SharedPreferences` read (warmed on a background thread by `PrefsWarmup`), `CrashLoopGuard.onStart`'s `apply`, `bridgeBuild31` on an upgrade (one snapshot file, guarded), and the shared
library's `maybeShowRecovery` and `captureExitDeath` (a few small files; it is the library's documented contract to call them on the main thread, and they run once per start).

## Which crash is the likely cause of the owner's two crashes

**None was reproduced.** No state shape any earlier build wrote makes Liftoff crash on start, apart from the wrong-type read above, which no earlier build wrote.

What the evidence does support, stated as a likely explanation and not a proven one:

* `CrashLoopGuard` counts STARTS that did not stay up for 30 seconds, not crashes. `UpgradeSmokeTest.CFG_09_twoRapidStartsWithoutAnyCrashAreSafeModeToo` starts the launcher twice with no exception anywhere and gets
  safe mode on the second. An install over the running launcher kills the process and starts it again; installing two builds within half a minute, or the system reclaiming a launcher that had been up for less than 30 s, does the same. The screen's wording
  ("stopped twice in a row right after starting") is the guard's, whatever stopped it.
* The screenshot showed safe mode with a snapshot from the same day (17/11/2026 14:10), which a stable start writes; before this wave there was no crash report path at all, so a real crash would also have shown only that screen.

What would settle it: on the owner's phone, after this build, if safe mode appears again: "View crash report". A real crash shows the splash first and a report; safe mode with the plain line "No crash report was saved for this start" means two quick starts without a crash (the guard), or a kill with no record (Android 11 and up record native crashes, ANRs and crashes through the exit history, which the shared utility now reads).

Recommendation, not done (it changes CFG-09 and needs the owner's call): make the guard count crashes (a recovery-screen report or a crash-shaped exit record) instead of starts, so that installs and system kills cannot put the launcher in safe mode.
