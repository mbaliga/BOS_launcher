# Wave 48: workProfile (HOM-06) and test robustness

Decision record: `docs/decisions/workProfile.md`. Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| HOM-06 | partial | The pause is back in the disc app drawer: Work label, work-only scope, pause and resume pill, paused apps dimmed and marked. Built and tested headless (`WorkProfileW48Test`). Device-only: the real quiet-mode request and broadcast, the label fit, the pill position. |

## Built
- `core-catalog/WorkDrawerRules.kt` (pure rules), `feature-grid/WheelDrawer.kt` (`WheelWork`, the fourth label, the pill, the paused icon look), `app/HomeScreen.kt` wiring (the home chip steps aside while the disc is open), `core-data` pref `wheelWorkOnly`, `core-design/L10nW48workProfile.kt` (es fr de pt hi zh ja ar).

## Tests added
`feature-grid/WorkProfileW48Test` (7): pure scope and pill rules, no Work label or pill without a work profile, the Work label reachable and switching the scope, work-only listing with the pill, paused apps dimmed/announced/not launchable and the pill resuming, resumed apps launching. Wording of the screen and the real quiet-mode request are not covered (device-only). No screenshot was taken, so the fit of the fourth label beside the other three at small sizes is unchecked.

## Test robustness (B)
- **DockDialogsW24Test.INP_20** ("dialog stays isShowing after open=false"): reproduces at the base commit (one test per run, the last of the class, in this class order). Instrumenting it showed the host's recomposer holding pending work with no change applied while the dialog stayed composed: it is not a dialog or a production bug. These tests host content in a plain ComposeView with no compose rule and the Robolectric sandbox shares Compose's process-wide state between tests. Fix in the test only: every looper round applies pending snapshot writes by hand and yields a few real milliseconds, the end assertions wait up to 10 s for the removal, leaked dialogs are dismissed and the looper runs once after the scenario closes. Alone and in the class it passed 3 of 3 after the fix and failed every run before it.
- **DockWiringTest "Compose did not get idle" (3.5 million attempts)**: reproduced as a consequence of the leak above (a failing INP_20 left a dialog behind; every later compose-rule test in the JVM then never idled, 8 of 8 failed, 480 s). Fixed by the same change (dialogs are dismissed in `@After`). I could not reproduce a load-only flake of it; none of the changes touches load behaviour.
- **PackageRemovedW26SmokeTest** failed in every full-module run and passed alone: a real order-dependent bug in production code. `DockPinBinding.ensure` bound the dock pins to the home's dock list once per process with a plain flag, so a second workspace repository and extras store in the same process (every later test; in the app there is one of each) never had its pins connected, and the removed app's pin simply vanished instead of staying as unavailable. It is now keyed by the identity of the manager and the store (`app/ui/DockLayer.kt`); in the app it still binds once.
- Results: app module 511 tests, 0 failures in the whole-module run; the three classes pass alone. Other modules green: core-design, core-data, core-catalog, feature-grid, core-quality.
