# Wave 55: dockSheet (the dock's share-sheet style options, in-place Edit dock, the complete Settings Dock page)

Requirement rows used for test naming: DCK-01 (pinned items, edited in place), DCK-04 (dock settings, style, the sheet, the Settings page), INP-20 (the dock dialog hook). No Spec ID was added (the rows DCK-01 and DCK-04 got a wave 55 sentence); `docs/SPEC_STATUS.md` was not edited: its DCK-04 note still describes wave 41 and should get the line "Wave 55: sheet and Settings page, docs/status/w55-dockSheet.md". Decision note: `docs/decisions/dockSheet.md`.

| ID | Status | Note |
| --- | --- | --- |
| DCK-04 (dock sheet) | built, not device-verified | A long press on the dock background, the menu key / Shift+F10 or the TalkBack action "Dock options" opens a bottom sheet (grabber, centred "Dock", 28 dp corners, themed background, at most 640 dp and 62% high). The whole home is pushed up by the sheet's height (a spring, a snap with reduced motion; the sheet and the home share one progress so a drag pulls the home back). Landscape with a side dock: a side sheet on the dock's side and the home pushed sideways. Cards: Dock style, Position, Mode; switches: Show the dock, group chips, Desktop item; tiles: Edit dock, Workspaces and groups, About the dock. Everything applies live to the real dock. Tests `DockSheetW55Test`. |
| DCK-01 (edit in place) | built, not device-verified | Edit dock shrinks the sheet to a bar with Done; on the dock: wiggle (still with reduced motion), plain drag reorders, x removes, a plus tile (first after Home) opens the app picker page, an empty dock shows only the plus, icons do not launch, the home behind takes no touch except the dock. Tests `DCK_01_edit_01..06`. |
| DCK-04 (close and input) | built | Drag the header down (or toward the side edge) past 70% or a fast fling; short drag springs back; Back, Esc, controller B (in edit mode they first leave editing); a tap on the home above closes it without pressing what is under it; `DockInput.dialogHook` composed in the sheet and the About dialog (INP-20); pane title "Dock". Tests `DCK_04_close_01..05`, `INP_20_...`. |
| DCK-04 (geometry) | built, not device-verified | Pure `DockSheetGeometry` (LTR and RTL, side or bottom, nav-bar inset), landscape left and right docks (the sheet follows a Position change to the bottom), bottom dock in landscape (capped height), tablet (640 dp, centred), RTL portrait and landscape. Tests `DCK_04_geo_01..07`. |
| DCK-04 (Settings page) | built, not device-verified | Settings > Desktop & dock > Dock: live preview of the dock, then the same cards and switches, Edit dock (list page), Add apps, Desktop item, Use the default. Dock style removed from Home screen. Tests `DCK_04_page_30..34`, `DCK_04_style_15/40`. |
| INP-20 | built | The panel dialog test now closes the Workspaces page with B. |

## Audit of the dock options (what exists in the code)

`DockSettings` = `enabled` (null = device default), `mode`, `edge`, `groupShelf`, `style`. Nothing else is persisted for the dock: no labels (icons only, wave 25), no size or icon size (40 dp, 52 dp slots), no recents count (constant 6), no auto-hide (the collapsed band is the mode), no dock-specific haptics. Plus the pins (per workspace) and the Desktop item pin. Missing from the Settings page before: Dock style, group chips, Edit dock. Now shown: all of them. `DCK_04_page_34` fails when a field is added without a control.

## Removed (owner standing rule: only the owner's option stays)

| What | Why |
| --- | --- |
| `DockMenuModel.dock`, the dock-level popup, `MENU_DOCK`, `DockStyleDialog`, `DockStyleChooser`, the rail's `HelpDialog` (now the sheet's About), `DockActions.onHide/onSetEdge/onSetStyle` | replaced by the sheet |
| `DockPanelPage.Dock` and the panel's `tier`/`extra` | the panel is the Workspaces and groups page; its Dock page is the sheet and the Settings page |
| `dockStyleSettingItem`, `DockStylePane`, the Home screen "Dock style" row | one place for the style |
| Tests: `DockIconsOnlyW25Test` menu tests (long press opens the menu and each entry runs, About as a menu entry, the menu without Hide), `DockStyleTokensW39Test.DCK_04_style_04`, `DockStyleUiW39Test` 14 (menu to style dialog), 15 (chooser) and 32 (chooser screenshot), `DockFixW26Test.DCK_09_theDockPanelOpensOnlyFromTheLongPressMenu`, `W4RadarInputDockTest.INP_20_theRailsHelpDialogInstallsTheDialogHook`, `WorkspacePagesW41Test` 30, 31, 33 (rewritten for the new page and the Workspaces-first panel), `ThemesW39Test.DCK_04_style_40` and the Home-screen assertion | each drove the old popup, chooser or page; replaced by `DockSheetW55Test` and the rewritten page tests |

## Fixed on the way

`AppPicker` (wide) returns package names; the dock stored them as pin ids, so "Add apps to the dock" dropped every existing key pin and pinned by package (unresolvable by the rail). `DockPinPicking.apply` maps back to keys (`WorkspacePagesW41Test.DCK_04_page_32` now expects the key `kAlpha`; `DCK_01_edit_04`).

## Verified (Robolectric, no device)

`--offline --max-workers=2`, one build at a time: core-design 32 tests, core-quality 220 (debug and release), feature-dock 251 (29 new in `DockSheetW55Test`, 5 page tests added or rewritten), feature-grid 1606, app 606: all green except one failure in the full app run, `TourFreshSmokeTest.STR_14_tourRecentsOpensBySwipeHandleAndShortcutClearAllAndCloses` ("a global snapshot write is pending"), which passes when that class is run alone (load-sensitive, unrelated to the dock). `assembleRelease` was not run. Screenshots read: `feature-dock/build/screenshots/w55-dock-sheet-portrait.png`, `-portrait-light`, `-portrait-3d`, `-edit-portrait`, `-landscape-left`, `-landscape-bottom`, `-tablet`, `-rtl`, and `w55-dock-page-{dark,light}-portrait`, `-fontscale2`, `-landscape`.

Found and fixed from the screenshots: in the light theme the first draft drew white ink on the theme's white cards (the sheet was a fixed black); the sheet and its cards now take the theme's colours. The plus tile was cut off at the end of a long rail; it is now the first tile. A Robolectric RTL test showed `Alignment.CenterEnd` and `RoundedCornerShape(topEnd)` flip in RTL; the sheet uses physical edges and corners.

## Not matched / limits

- The owner's wording "sheet shrinks to a compact bar with Done" is built; the push follows the measured size, with the spring only on open and close (a resize just follows the measured size per frame).
- Show labels: no such setting exists in the code, so none is offered (see the audit).
- Dragging from the scrolled body does not close the sheet, only the header.
- No device run: touch feel of the drag and spring, the wiggle at 60 fps next to a real wallpaper, TalkBack announcing the pane title and the x badges, controller B on a real pad, gesture navigation with the sheet at the bottom edge, DeX or a secondary display, and the push over a live wallpaper (Glance background agent) are device-only.
