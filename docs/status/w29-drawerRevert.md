# Wave 29, drawerRevert

Owner (build 58): the app drawer disc was better before wave 26; revert to it, with the shortcuts.

| ID | Status | Note |
|---|---|---|
| HOM-01 | done | The apps hotspot (Glance, a page, right to left order) opens the restored wheel disc, Back closes it: `DrawerRevertW29Test`. |
| HOM-23 | done | Right to left: the hotspot roles still reach the disc. |
| INP-18 | done | Puck keyboard contract back to the clamped rotation (`InputStreamTest`). |
| CFG-15 | done | Ring actions (Puck actions pane/store) run from the restored disc: `CFG_15_w29_aPuckRingActionOnTheRestoredDiscStillRuns`. |
| CFG-03 | done | `switcherStyle`, `drawerShortcutsOn`, `drawerShortcuts`, `drawerShortcutsShown` retired (purged once, dropped on import). |

## Restored (content of commit 98474ec)
- `feature-grid/.../WheelDrawer.kt` (two lines adapted: `rememberLauncherHaptics`), `feature-grid/src/test/.../WheelGeometryTest.kt`
- `core-input/.../Puck.kt`, `core-input/src/test/.../InputStreamTest.kt` (clamped rotation)
- Settings row "Wheel groups" in `SettingsCategories.kt`; `ScreenshotTest`, `ReducedMotionUiTest`, `InputTouchPathsUiTest` call the old signature; `W4MotionAuditTest` WheelDrawer.kt count 2.

## Removed
- `ArcDrawer.kt`, `ArcDrawerMath.kt`, `ArcShortcutsRow.kt`, `ArcShortcutsPicker.kt`, `ArcDrawerMathTest.kt`, `ArcDrawerUiTest.kt`, `ArcShortcutsPickerUiTest.kt` (feature-grid)
- `ArcShortcuts.kt`, `ArcShortcutsTest.kt` (core-data); `ShortcutsShown` enum and the three prefs
- `L10nW28arcShortcuts.kt` and its two references in `L10n.kt`
- Settings rows Shortcuts, Shortcuts are shown, Choose shortcuts; the `ArcWheelHost` call in `HomeScreen.kt`

## Added
- `app/src/test/.../DrawerRevertW29Test.kt`, `feature-grid/src/test/.../WheelDiscW29ScreenshotTest.kt`, `docs/decisions/drawerRevert.md`

## Verified
Screenshots `feature-grid/build/screenshots/w29-disc-{portrait,landscape}-{left,right}.png` were read: hub with A-Z / Category / Recent chips, search button, the ring actions of the test (3 in the screenshot test, up to the capacity of the window in the touch test; the shipped default is `PuckActions.DEFAULT`), section bands and app spokes, all inside the screen apart from the natural quarter cut. The file is identical to 98474ec (apart from the haptics helper), so this is the look of builds 54 and before; a side-by-side render of that commit was not made (no extra worktree).
Device-only: touch feel and the haptic strength of the disc.

## Adversarial review findings
| # | Finding | Result |
|---|---|---|
| 1 | The grouping chips' invisible touch boxes (fixed 72 x 40 dp) overlapped each other on small windows, so a tap on Category could land on Recent: a dead/ambiguous band, the owner's "touch area" complaint | Fixed: `WheelGeometry.chipTouchBoxes`; test `WheelDiscTouchAreaW29Test` failed first (chips overlap) |
| 2 | No test guarded the touch areas or clipping of the restored disc (ring actions, search button, chips, app icons) | Added `WheelDiscTouchAreaW29Test`: all inside the window, no overlap, portrait and landscape, every corner, font scale 2.0, nine actions |
| 3 | The right to left test passed whichever corner opened the disc, so a wrong anchor could not fail it | `HOM_23_w29_theDiscFansOutOfTheTappedCorner*` assert the hub sits on the tapped side in both orders |
| 4 | Opening from the apps hotspot was not tested in landscape | `HOM_01_w29_theAppsHotspotOpensTheWheelDiscInLandscape...` (also asserts the hub is fully on screen) |
| 5 | The chord route (Ctrl+P opens the disc, Esc closes it; Ctrl+D still opens the list) had no test | `INP_18_w29_*Chord*` |
| 6 | `DrawerRevertW29Test` declared a private `key(Int)` that shadowed the base class `key(code, ctrl)`; a first attempt at the Esc test pressed no key and passed silently | Helper renamed `appKeyOf` |
| 7 | The restored Wheel groups row had no test and the removal of the arc rows was not pinned; the decision doc said the row is under Apps (it is App drawer) | `DrawerRowsW29Test`; doc corrected |
| 8 | Status note said three ring actions and that two of four screenshots were read | All four PNGs read now (hub, chips, ring, bands, spokes; only the natural quarter cut at the screen edge); wording fixed |
Not done / device-only: the Puck actions pane computes its capacity from the configuration size while the disc uses the laid-out size (differs by the system bars); not changed, to keep the restored behaviour; the haptic feel and a side by side of the 98474ec render.
