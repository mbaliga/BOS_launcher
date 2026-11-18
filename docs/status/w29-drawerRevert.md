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
Screenshots `feature-grid/build/screenshots/w29-disc-{portrait,landscape}-{left,right}.png` were read: hub with A-Z / Category / Recent chips, search button, three ring actions, section bands and app spokes, all inside the screen apart from the natural quarter cut. The file is identical to 98474ec (apart from the haptics helper), so this is the look of builds 54 and before; a side-by-side render of that commit was not made (no extra worktree).
Device-only: touch feel and the haptic strength of the disc.
