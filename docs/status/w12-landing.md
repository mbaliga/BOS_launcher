# Status: wave 12, home landing (step 1 of 2: data and logic)

Nothing here is device-confirmed. Step 2 (the UI) is not built yet.

| ID | Status | Note |
| --- | --- | --- |
| HOM-22 | partial | Step 1 done: `HomeLanding` enum and rules (`HomeLandingRules`), prefs `homeLanding`, `recentsLimit`, `recentsUsage`, `minimalHints` (plain, exported, round-trip tested), recents rules and live Flow (`HomeRecents`, `UsageRecents.sightings`), private removals file (`RecentsStateStore`, in `NEVER_EXPORTED_FILES`), Minimal hotspot defaults (`MinimalHotspots`), command `CycleLanding` (Ctrl+Shift+L) and corner/gesture actions `CycleLanding`, `OpenLaunchpad` (also wired in app `HomeScreen.kt`, two small additive branches). Missing until step 2: every screen (Recents, All apps, Minimal, Ground Control row, quick sheet control, empty-home hint line), the Flow's wiring to the home, translations, UI tests, smoke test |
| HOM-01 | partial | Spec row updated; Selected apps is the unchanged default. Hint extension is step 2 |
| HOM-05 | partial | Spec row updated; All apps landing UI is step 2 |
| INP-16 | partial | Minimal default hotspot assignments and hint rule are pure and tested; the Minimal surface is step 2 |
| INP-17 | partial | Same; keyboard nodes on Minimal are step 2 |
| INP-07 | built | The new command and two corner actions have touch paths in `TouchPaths` (audit green); the quick-sheet and Ground Control rows they name arrive in step 2 |

Decisions: `docs/decisions/homeLanding.md`, spec changes: `docs/decisions/landing.md`. Making Recents the default for new installs is a one-line change (`LauncherPrefs.homeLanding` default) and an owner choice.
Shared-file edits: `HomeScreen.kt` (two branches), `Input.kt`, `HomeModel.kt`, `LauncherStores.kt`, `StateSchema.kt`, `TouchPaths.kt`, `Glyphs.kt`, `Recents.kt` in core-signals.
