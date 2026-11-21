# Wave 7: whole-app smoke tests (the real MainActivity and HomeScreen)

Nothing here is a device run. Robolectric has no compositor, no real input method, no system Back animation and no real launcher-apps service.

## Can MainActivity start headless?

Yes. `AppSmokeHarness.launch()` runs `ActivityScenario.launch(MainActivity::class.java)` under Robolectric (SDK 34, 360 x 720 dp, native graphics) and the whole `HomeScreen` composes: corners, clock, page grid, dock rail, Launchpad handle, the drawer and Radar. Exactly one thing blocked it:

* `Activity.getDisplay()` threw `UnsupportedOperationException` from `MainActivity.onStart` (the Robolectric activity context is not tied to a display). `onStart` now reads the display id with `runCatching` and falls back to the default display (0). On a device nothing changes.

What the harness fakes, and nothing else:

* the launcher-apps service: `AppSmokeHarness.seedApps` fills Robolectric's `ShadowLauncherApps` (the platform `LauncherActivityInfo` constructors are hidden, so the test reaches them by reflection; app code never does). The app list is the real `LauncherAppsAppRepository`.
* Health Connect, the widget host, the notification listener and usage stats have no provider; the home reads them through code that already tolerates their absence (a "healthconnect service not found" line in the log).
* the "process": Robolectric keeps statics alive across the tests of a class, so `resetProcess` clears the launcher's singletons by reflection, and `restartProcess` marks the first start stable and rebuilds them over the same preference files (what a reboot or a swipe-away does).

Everything else is real: `MainActivity.onCreate`, key dispatch (`LauncherInput.onKeyEvent` through `dispatchKeyEvent`), the activity's Back dispatcher, `onNewIntent`, the stores, the open-surface store, the crash-loop guard, the dock controller and the input hub.

## A real defect the suite exposed (test written first)

* `CrashLoopGuard.onStart` ran in every `MainActivity.onCreate`, so a recreate of the activity in the same process (a system locale change, or anything the manifest's `configChanges` does not list) within 30 seconds of the first start counted as a second rapid start and put the launcher into safe mode (widgets off, the minimal safe-mode screen). Test `NFR_11_aRecreateOfTheActivityInTheSameProcessIsNotTakenForACrashLoop` failed first; the fix counts a start only when no saved state is handed back (`savedInstanceState == null`). A start after a crash always has none (`android:stateNotNeeded="true"`), so crash loops are still detected (`NFR_11_aRestartInSafeModeNeverRestoresAndKeepsTheRecord` drives a real second start through the guard).

## Dialog note for future tests

A `Dialog` that holds a `BasicTextField` (the folder sheet) never lets Robolectric's Compose test report idle, also for a bare `Dialog` + `BasicTextField` outside the launcher (checked). The folder test therefore drives the clock by hand and reads the dialog through `ShadowDialog`. It is a test-environment property, not a launcher loop.

## Tests

`app/src/test/kotlin/com/bos/sphere/AppSmokeTest.kt` (20 tests, about 10 to 15 seconds of test time after start-up), `AppSmokeHarness.kt`, and the source audit `core-quality/.../AppSmokeW7AuditTest.kt`. An `@After` check fails any test that leaves a pending global Compose snapshot write; the one write the suite makes outside a Compose host goes through `Snapshot.withMutableSnapshot`.

| ID | Status | Note |
| --- | --- | --- |
| PAL-01 | built | `PAL_01_aPrintableKeyOnTheHomeOpensRadarWithThatCharacter` sends a real key event to the real activity and finds Radar open with the typed character in its field; `PAL_01_aPrintableKeyDoesNotRestartRadarWhileItIsOpenAndTheDrawerKeepsItsKeys` shows a key with the drawer open does not open Radar. Device entry through a real IME is not run. |
| INP-05 | partial | The composed branch is now run: `INP_05_focusDockClosesAnOpenDrawerAndTheRailTakesFocus`, `..ClosesRadarAndTheRailTakesFocus` (Ctrl+H through the activity: the overlay closes, the rail's focus node holds focus) and `..WithNoOverlayOpen..` (nothing closed). Still open, device only: a physical keyboard on a device. |
| INP-01 | partial | `INP_01_theComposedHomeRegistersItsRailAndCornersOnTheFocusGraphAndTheDrawerRaisesALayer`: rail items and corners sit on layer 0 of the live graph, the drawer's items on layer 2. Still open, device only: a keyboard and controller pass over every surface. |
| NFR-19 | partial | `NFR_19_backClosesRadarThenTheDrawerThenDoesNothingOnTheHome` (Back through the activity's dispatcher: Radar closes, then the drawer, then no handler is registered on the plain home and `MainActivity` has no `onBackPressed` override), plus a home intent closing the drawer and Radar, and rotation keeping the open drawer in the same activity instance. Still open, device only: the gesture-progress animation, both navigation modes on a notch device, and what the system does with a Back on a home app (Robolectric's emulation of that fallback finishes the activity, so the test does not send it). |
| NFR-11 | partial | Composed restart tests: drawer and Radar come back after a restart with the setting on (`..theDrawerComesBack..`, `..radarComesBack..`), not with it off, never in safe mode (a real second rapid start; the record survives), and a closed surface is not reopened by a recreate. Defect fixed: a same-process recreate no longer counts as a crash-loop start. Still open, device only: reboot, package update, profile toggle and display hot-plug. A recreate while the drawer is open returns to the home (state is not saved across a recreate; restore is for process death only), which is the existing design and is not changed here. |
| HOM-01 | built | The page grid shows apps and a folder from the real stores (`HOM_01_thePageGridShowsAppsAndAFolderAndTheFolderOpens`), the apps on a fresh "all apps" home and the dock (`HOM_01_theAppStartsAndTheHomeShowsTheSeededAppsAndTheDock`), and a folder-open intent closes the drawer and opens the folder sheet (`HOM_01_aFolderOpenIntentWhileTheDrawerIsOpenClosesItAndOpensTheFolder`). Frame budget and feel are not device-checked. |

## Residue (device or owner)

* INP-01, INP-05: with a physical keyboard and a controller on a device, walk every surface (home, drawer, Radar, Settings, dock, Launchpad) by keys only and confirm each element is reachable and the rail takes focus after Ctrl+H.
* NFR-19: on a device, in 3-button and gesture navigation, Back with Radar, the drawer and Settings open, and Back on the plain home (the system keeps the home app); watch the predictive-back shrink on a notch device.
* NFR-11: with the setting on, restart the launcher process (reboot, package update, profile toggle, display hot-plug) with the drawer open and see it return; with it off or after two quick crashes, see the home.
