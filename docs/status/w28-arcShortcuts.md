# Wave 28 arcShortcuts: alphabet wheel, app fan, up to four shortcuts

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the rendered screenshots (`feature-grid/build/screenshots/w28-*.png`) against the owner's screenshot.

| ID | Status | Note |
|---|---|---|
| ARC-01 | built (device untested) | Looped alphabet ring A-Z and `#` between Z and A, bucketing = the drawer's letter index (`ArcDrawerMathTest.ARC_01_*`, `ArcDrawerUiTest.ARC_10_*`: X Y Z # A B). |
| ARC-02 | built | Mirror for the right corner (`ARC_02`, `ARC_15`), RTL does not move the physical corner (`ARC_15_right_to_left...`). |
| ARC-03 | built | App fan: plain 48 dp icons along radial chains, nearest first, no overlap, 0..60 apps, scroll for overflow (`ARC_03`, `ARC_12`, `ARC_17`, bounds at 360x640, 411x891, 891x411, 1280x800, 280x653 in `ARC_18`). |
| ARC-04 | built | Drag rotates and loops, tap a letter shows its apps, launch, long press menu, Back/Esc close, search wedge opens Radar, reduced motion snaps, TalkBack actions (`ARC_11` to `ARC_16`). |
| ARC-05 | built | Shortcut placement at rest never overlaps dock reserve, page indicator zone, hotspots (`ArcDrawerMathTest.ARC_05`, `ArcDrawerUiTest.ARC_19` at the five sizes). |
| ARC-06 | built | Shortcuts On + Always: shortcuts on the home, the four squares open the wheel, shortcuts stay (`ARC_06`). |
| ARC-07 | built | Only with the app wheel: nothing at rest, bottom row with the wheel, gone when it closes (`ARC_07`). |
| ARC-08 | built | Shortcuts Off: the four squares just open the wheel, no row (`ARC_08`). |
| ARC-09 | built | No four-squares corner hotspot: nothing at rest, wheel still has its row (`ARC_09`). Shortcut chord, gestures and Radar glyph routes are unchanged (`openAllApps`, `OpenPuck` untouched). |
| ARC-10 to ARC-12 | built | Rules, clamp at four, visibility table, persistence and retired key (`core-data ArcShortcutsTest`); picker limits to four, add, move, remove (`ArcShortcutsPickerUiTest.ARC_20_*`). |

Owner correction applied: apps are plain launcher icons only (`AppIcon`), no disc, clip, ring or caption; `ARC_12_apps_are_plain_icons_not_circles...` fails with a disc clip (a point 10 percent in from the icon's corner must be painted).

## Removed (code, prefs, tests)
- Code: the wave 26 `ArcDrawer.kt` switcher wedges and colour-picker cells, `ArcMath` rings/wedges/cells (`wrap`, `step`, `wedgeSections`, `rings`, `offsetBounds`, `cellCorners`...), the `WheelDrawer` parameters `grouping`, `onGroupingChange`, `recentKeys`, `actions`, `switcherStyle`, `startExpanded`, `SwitcherStyle` enum, `LauncherPrefs.switcherStyle`, the settings rows "Switcher: Compact or Expanded" and "Wheel groups", `L10nW26arcDrawer.kt`, the `puckActionIds` read in `HomeScreen` (the puck ring actions are not drawn any more; `PuckActionsStore` and its pane are untouched and unused by the wheel).
- Pref: `switcherStyle` (retired: `RetiredPrefKeys` launcher_prefs, `DROPPED_ON_IMPORT`; marker file renamed to `retired_pref_keys_w28` so it purges once more).
- Tests deleted: `core-data SwitcherStyleTest` (Compact/Expanded pref), `feature-grid ArcDrawerMathTest` (rings, wedges, cell corners, offsets: replaced by the new `ArcDrawerMathTest`), `ArcDrawerUiTest` (wave 26 `ARC_01_*` section looping by wedge keys/swipe, `ARC_02_compact_switcher...`, the switcher/colour-picker screenshots: replaced by the new `ArcDrawerUiTest`).
- Tests changed: `UpgradeSmokeTest` (garbage enum key list: `switcherStyle` replaced by `drawerShortcutsShown`), `InputTouchPathsUiTest.INP_07`, `ReducedMotionUiTest.NFR_14_drawerPaintsAtFullSize...`, `ScreenshotTest.wheelDrawer`: only the `WheelDrawer` call (dropped parameters).
- Kept: `INP_18` looping in `core-input` (`PuckKeys`), the grouping helpers and `WheelGeometryTest`.

## Device-only / not verified
Fling and touch ergonomics, TalkBack speech order, real hardware keyboard focus, the dock and indicator at rest on a real phone, the user's icon shape setting on the fan.
